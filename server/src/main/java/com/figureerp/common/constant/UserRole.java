package com.figureerp.common.constant;

import lombok.Getter;

import java.util.Arrays;
import java.util.List;

/**
 * 员工角色（使用权限 + 部门归属）。
 * <p>
 * 角色决定「能操作什么」以及「能看哪些数据」。
 * <p>
 * 每个角色带两个附加信息：
 * <ul>
 *   <li>{@code group}：所属板块，前端下拉按它分组（系统 / 管理层 / 财务 / 销售 / 物流 / 人事 / 通用）；</li>
 *   <li>{@code level}：权限层级，后端所有权限判断只认这个值，不再逐个角色写 if：
 *     <ul>
 *       <li>{@link #LEVEL_ADMIN} 管理级——管理员、总经理：可维护员工账号 + 全部业务；</li>
 *       <li>{@link #LEVEL_MANAGER} 主管级——各部门主管：可维护业务数据（商品、仓库、货架、物流价格、ACG、发消息）；</li>
 *       <li>{@link #LEVEL_STAFF} 执行级——各部门专员：只读业务数据。</li>
 *     </ul>
 *   </li>
 * </ul>
 * 新增角色时只要在下面加一行并指定 group / level，字典接口和前端下拉会自动带上，
 * 不用改表（role 是 varchar），也不用改权限代码。
 * <p>
 * {@code MANAGER} / {@code STAFF} 是旧版通用角色，保留是为了兼容已经建好的账号，
 * 新录入一律从具体角色里选。
 */
@Getter
public enum UserRole {

    /** 管理员：系统维护者，全部功能，含员工账号增删改 */
    ADMIN("ADMIN", "管理员", "系统", 3),

    /** 总经理：公司管理层，权限与管理员同级，但没有「系统管理员」这个身份标签 */
    GM("GM", "总经理", "管理层", 3),

    FINANCE_MANAGER("FINANCE_MANAGER", "财务主管", "财务", 2),
    FINANCE_STAFF("FINANCE_STAFF", "财务专员", "财务", 1),

    SALES_MANAGER("SALES_MANAGER", "销售主管", "销售", 2),
    SALES_STAFF("SALES_STAFF", "销售专员", "销售", 1),

    LOGISTICS_MANAGER("LOGISTICS_MANAGER", "物流主管", "物流", 2),
    LOGISTICS_STAFF("LOGISTICS_STAFF", "物流专员", "物流", 1),

    HR_MANAGER("HR_MANAGER", "人事主管", "人事", 2),
    HR_STAFF("HR_STAFF", "人事专员", "人事", 1),

    /** 主管（通用）：旧版角色，权限等同主管级，仅用于兼容存量账号 */
    MANAGER("MANAGER", "主管（通用）", "通用", 2),

    /** 员工（通用）：旧版角色，权限等同执行级，仅用于兼容存量账号 */
    STAFF("STAFF", "员工（通用）", "通用", 1);

    /** 管理级：可维护员工账号 */
    public static final int LEVEL_ADMIN = 3;
    /** 主管级：可维护业务数据 */
    public static final int LEVEL_MANAGER = 2;
    /** 执行级：只读业务数据 */
    public static final int LEVEL_STAFF = 1;

    private final String code;
    private final String label;
    private final String group;
    private final int level;

    UserRole(String code, String label, String group, int level) {
        this.code = code;
        this.label = label;
        this.group = group;
        this.level = level;
    }

    public static String labelOf(String code) {
        UserRole role = of(code);
        return role == null ? "未知" : role.label;
    }

    /** 按编码找角色，找不到返回 null（历史脏数据或前端传错） */
    public static UserRole of(String code) {
        if (code == null) {
            return null;
        }
        for (UserRole role : values()) {
            if (role.code.equals(code)) {
                return role;
            }
        }
        return null;
    }

    public static boolean isValid(String code) {
        return of(code) != null;
    }

    /** 权限层级，未知角色按执行级处理（宁可少给权限） */
    public static int levelOf(String code) {
        UserRole role = of(code);
        return role == null ? LEVEL_STAFF : role.level;
    }

    /** 管理级：管理员、总经理 —— 可维护员工账号 */
    public static boolean isAdmin(String code) {
        return levelOf(code) >= LEVEL_ADMIN;
    }

    /** 主管级及以上 —— 可维护业务数据 */
    public static boolean canManage(String code) {
        return levelOf(code) >= LEVEL_MANAGER;
    }

    /**
     * 销售岗：销售主管 / 销售专员。
     * <p>
     * 考勤上只录上班时间，下班卡免打（外勤、跑客户，下班时间不固定）。
     */
    public static boolean isSales(String code) {
        return SALES_MANAGER.code.equals(code) || SALES_STAFF.code.equals(code);
    }

    /**
     * 人事岗：人事主管 / 人事专员。
     * <p>
     * 人事专员是执行级（level 1），但考勤、请假、加班这些单据本来就归人事管，
     * 所以「能不能看全部员工的数据」对他们单独放行 —— 只放开查看，增删改仍按各自规则的权限走。
     */
    public static boolean isHr(String code) {
        return HR_MANAGER.code.equals(code) || HR_STAFF.code.equals(code);
    }

    /** 所有管理级角色编码，用于「至少保留一个管理级账号」这类查询 */
    public static List<String> adminCodes() {
        return Arrays.stream(values())
                .filter(role -> role.level >= LEVEL_ADMIN)
                .map(UserRole::getCode)
                .toList();
    }
}
