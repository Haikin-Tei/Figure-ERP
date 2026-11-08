package com.figureerp.module.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.figureerp.common.constant.UserRole;
import com.figureerp.common.context.UserContext;
import com.figureerp.common.exception.BusinessException;
import com.figureerp.common.result.PageResult;
import com.figureerp.common.result.ResultCode;
import com.figureerp.module.system.converter.UserConverter;
import com.figureerp.module.system.dto.UserQuery;
import com.figureerp.module.system.dto.UserSaveRequest;
import com.figureerp.module.system.entity.SysUser;
import com.figureerp.module.system.mapper.SysUserMapper;
import com.figureerp.module.system.vo.UserVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 员工（账号）管理。
 * <p>
 * 权限规则写在这一层而不是 Controller：
 * <ul>
 *   <li>管理级（管理员 / 总经理）：查看 + 增删改 + 重置密码</li>
 *   <li>主管级（各部门主管 / 通用主管）：只能查看列表</li>
 *   <li>执行级（各部门专员 / 通用员工）：无权访问</li>
 * </ul>
 * 每次都从库里读当前用户，保证「刚被降级的人」立刻失去权限，
 * 而不是等令牌 8 小时后过期。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private static final int STATUS_ENABLED = 1;
    private static final int STATUS_DISABLED = 0;
    private static final int MIN_PASSWORD_LENGTH = 6;

    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    /** 分页查询员工 */
    public PageResult<UserVO> page(UserQuery query) {
        requireViewPermission();

        LambdaQueryWrapper<SysUser> wrapper = Wrappers.lambdaQuery();

        String keyword = StringUtils.hasText(query.getKeyword()) ? query.getKeyword().trim() : null;
        if (keyword != null) {
            // 账号或姓名任意匹配，整体加括号保证 OR 不会破坏其他条件
            wrapper.and(w -> w.like(SysUser::getUsername, keyword).or().like(SysUser::getRealName, keyword));
        }
        if (StringUtils.hasText(query.getRole())) {
            wrapper.eq(SysUser::getRole, query.getRole());
        }
        if (query.getStatus() != null) {
            wrapper.eq(SysUser::getStatus, query.getStatus());
        }
        wrapper.orderByAsc(SysUser::getId);

        Page<SysUser> page = userMapper.selectPage(new Page<>(query.getPage(), query.getSize()), wrapper);
        return PageResult.of(page, UserConverter::toVO);
    }

    /** 员工详情 */
    public UserVO detail(Long id) {
        requireViewPermission();
        return UserConverter.toVO(getExisting(id));
    }

    /** 新增员工 */
    @Transactional(rollbackFor = Exception.class)
    public Long create(UserSaveRequest request) {
        requireManagePermission();
        validateRole(request.getRole());

        String username = request.getUsername() == null ? "" : request.getUsername().trim();
        if (!StringUtils.hasText(username)) {
            throw new BusinessException("请输入登录账号");
        }
        String password = request.getPassword();
        if (!StringUtils.hasText(password) || password.length() < MIN_PASSWORD_LENGTH) {
            throw new BusinessException("初始密码至少 6 位");
        }

        Long exists = userMapper.selectCount(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUsername, username));
        if (exists != null && exists > 0) {
            throw new BusinessException(ResultCode.DATA_ALREADY_EXISTS, "账号已存在：" + username);
        }

        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        applyEditableFields(user, request);

        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            // 逻辑删除的账号仍占用唯一索引，这里给出比「系统内部错误」更有用的提示
            throw new BusinessException(ResultCode.DATA_ALREADY_EXISTS,
                    "账号已被占用（可能属于已删除的员工）：" + username);
        }

        log.info("新增员工: {} ({})，角色 {}", username, request.getRealName(), request.getRole());
        return user.getId();
    }

    /** 编辑员工（账号不可改） */
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, UserSaveRequest request) {
        requireManagePermission();
        validateRole(request.getRole());

        SysUser existing = getExisting(id);
        boolean isSelf = existing.getId().equals(UserContext.requireId());

        // 防止把自己锁在门外：改自己的角色 / 停用自己都直接拒绝
        if (isSelf && !UserRole.isAdmin(request.getRole())) {
            throw new BusinessException("不能修改自己的角色，请让其他管理员或总经理操作");
        }
        if (isSelf && Integer.valueOf(STATUS_DISABLED).equals(request.getStatus())) {
            throw new BusinessException("不能停用自己的账号");
        }

        // 最后一个启用状态的管理员不能被降级或停用，否则系统再没人能管账号
        boolean losingAdmin = UserRole.isAdmin(existing.getRole())
                && (!UserRole.isAdmin(request.getRole())
                || Integer.valueOf(STATUS_DISABLED).equals(request.getStatus()));
        if (losingAdmin) {
            ensureNotLastEnabledAdmin(existing.getId(), "系统至少需要保留一个启用状态的管理员或总经理");
        }

        SysUser update = new SysUser();
        update.setId(id);
        applyEditableFields(update, request);
        userMapper.updateById(update);

        log.info("编辑员工: {}，角色 {}，状态 {}", existing.getUsername(),
                request.getRole(), request.getStatus());
    }

    /** 删除员工（逻辑删除，数据仍留在库里） */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        requireManagePermission();

        SysUser existing = getExisting(id);
        if (existing.getId().equals(UserContext.requireId())) {
            throw new BusinessException("不能删除自己的账号");
        }
        if (UserRole.isAdmin(existing.getRole())) {
            ensureNotLastEnabledAdmin(existing.getId(), "系统至少需要保留一个管理员或总经理");
        }

        userMapper.deleteById(id);
        log.info("删除员工: {} ({})", existing.getUsername(), existing.getRealName());
    }

    /** 重置密码（管理员操作，不需要原密码） */
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(Long id, String newPassword) {
        requireManagePermission();
        if (!StringUtils.hasText(newPassword) || newPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new BusinessException("新密码至少 6 位");
        }

        SysUser existing = getExisting(id);

        SysUser update = new SysUser();
        update.setId(id);
        update.setPassword(passwordEncoder.encode(newPassword));
        userMapper.updateById(update);

        log.info("重置密码: {}", existing.getUsername());
    }

    /** 启用 / 停用（列表页的快捷开关） */
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, Integer status) {
        requireManagePermission();
        if (!Integer.valueOf(STATUS_ENABLED).equals(status) && !Integer.valueOf(STATUS_DISABLED).equals(status)) {
            throw new BusinessException("状态值只能是 0 或 1");
        }

        SysUser existing = getExisting(id);
        if (existing.getId().equals(UserContext.requireId()) && status == STATUS_DISABLED) {
            throw new BusinessException("不能停用自己的账号");
        }
        if (UserRole.isAdmin(existing.getRole()) && status == STATUS_DISABLED) {
            ensureNotLastEnabledAdmin(existing.getId(), "系统至少需要保留一个启用状态的管理员或总经理");
        }

        SysUser update = new SysUser();
        update.setId(id);
        update.setStatus(status);
        userMapper.updateById(update);
    }

    /* ---------------- 内部方法 ---------------- */

    private void applyEditableFields(SysUser target, UserSaveRequest request) {
        target.setRealName(request.getRealName().trim());
        target.setRole(request.getRole());
        target.setStatus(request.getStatus());
        // 传空字符串表示「清空」，避免 null 被 MyBatis-Plus 当成「不更新」
        target.setIntro(request.getIntro() == null ? "" : request.getIntro());
        target.setRemark(request.getRemark() == null ? "" : request.getRemark());
    }

    private void validateRole(String role) {
        if (!UserRole.isValid(role)) {
            throw new BusinessException(ResultCode.PARAM_INVALID,
                    "角色不合法：" + role + "，可选值见 GET /api/system/dicts");
        }
    }

    private SysUser getExisting(Long id) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "员工不存在或已被删除");
        }
        return user;
    }

    /** 当前登录用户（每次都读库，权限变更立即生效） */
    private SysUser currentUser() {
        Long currentId = UserContext.requireId();
        SysUser user = userMapper.selectById(currentId);
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "当前账号不存在或已被删除");
        }
        return user;
    }

    private void requireManagePermission() {
        if (!UserRole.isAdmin(currentUser().getRole())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只有管理员或总经理可以管理员工账号");
        }
    }

    private void requireViewPermission() {
        String role = currentUser().getRole();
        if (!UserRole.canManage(role)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "没有查看员工列表的权限");
        }
    }

    /** 除自己以外，必须还有至少一个启用状态的管理级账号（管理员 / 总经理） */
    private void ensureNotLastEnabledAdmin(Long excludeId, String message) {
        Long count = userMapper.selectCount(Wrappers.<SysUser>lambdaQuery()
                .in(SysUser::getRole, UserRole.adminCodes())
                .eq(SysUser::getStatus, STATUS_ENABLED)
                .ne(SysUser::getId, excludeId));
        if (count == null || count == 0) {
            throw new BusinessException(message);
        }
    }
}
