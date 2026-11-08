package com.figureerp.module.product.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.figureerp.common.exception.BusinessException;
import com.figureerp.common.result.PageResult;
import com.figureerp.common.result.ResultCode;
import com.figureerp.module.product.converter.FigureProductConverter;
import com.figureerp.module.product.dto.FigureProductQuery;
import com.figureerp.module.product.dto.FigureProductSaveRequest;
import com.figureerp.module.product.entity.FigureProduct;
import com.figureerp.module.product.mapper.FigureProductMapper;
import com.figureerp.module.product.vo.FigureProductVO;
import com.figureerp.module.system.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 手办商品档案的查询与维护。
 * <p>
 * 关键业务规则：
 * <ul>
 *   <li>带盒重量必须大于 0；</li>
 *   <li>商品图片必须 3~4 张（容量要求）；</li>
 *   <li>货号（code）填写后不可与其他商品重复；</li>
 *   <li>查询所有登录用户可用；增删改需要管理员或主管。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FigureProductService {

    private static final int STATUS_ENABLED = 1;
    private static final int STATUS_DISABLED = 0;

    private final FigureProductMapper mapper;
    private final CurrentUserService currentUserService;

    /* ================= 查询 ================= */

    public PageResult<FigureProductVO> page(FigureProductQuery q) {
        LambdaQueryWrapper<FigureProduct> w = Wrappers.lambdaQuery();

        if (StringUtils.hasText(q.getName())) {
            w.like(FigureProduct::getName, q.getName().trim());
        }
        if (StringUtils.hasText(q.getCode())) {
            w.like(FigureProduct::getCode, q.getCode().trim());
        }
        if (StringUtils.hasText(q.getBrand())) {
            w.like(FigureProduct::getBrand, q.getBrand().trim());
        }
        if (StringUtils.hasText(q.getSeries())) {
            w.like(FigureProduct::getSeries, q.getSeries().trim());
        }
        if (StringUtils.hasText(q.getCharacterName())) {
            w.like(FigureProduct::getCharacterName, q.getCharacterName().trim());
        }
        if (q.getStatus() != null) {
            w.eq(FigureProduct::getStatus, q.getStatus());
        }

        // 默认按创建时间倒序，新录入的商品排在前面
        w.orderByDesc(FigureProduct::getCreateTime);

        Page<FigureProduct> page = mapper.selectPage(new Page<>(q.getPage(), q.getSize()), w);
        return PageResult.of(page, FigureProductConverter::toVO);
    }

    public FigureProductVO detail(Long id) {
        return FigureProductConverter.toVO(getExisting(id));
    }

    /* ================= 维护 ================= */

    @Transactional(rollbackFor = Exception.class)
    public Long create(FigureProductSaveRequest req) {
        currentUserService.requireManager("维护手办商品");
        validate(req);
        checkCodeUnique(req.getCode(), null);

        FigureProduct p = new FigureProduct();
        apply(p, req);
        mapper.insert(p);
        log.info("新增手办商品: id={} name={}", p.getId(), req.getName());
        return p.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, FigureProductSaveRequest req) {
        currentUserService.requireManager("维护手办商品");
        validate(req);
        getExisting(id);
        checkCodeUnique(req.getCode(), id);

        FigureProduct p = new FigureProduct();
        p.setId(id);
        apply(p, req);
        mapper.updateById(p);
        log.info("编辑手办商品: id={} name={}", id, req.getName());
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        currentUserService.requireManager("删除手办商品");
        getExisting(id);
        mapper.deleteById(id);
        log.info("删除手办商品: id={}", id);
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, Integer status) {
        currentUserService.requireManager("修改手办商品状态");
        if (!Integer.valueOf(STATUS_ENABLED).equals(status) && !Integer.valueOf(STATUS_DISABLED).equals(status)) {
            throw new BusinessException("状态值只能是 0 或 1");
        }
        getExisting(id);

        FigureProduct p = new FigureProduct();
        p.setId(id);
        p.setStatus(status);
        mapper.updateById(p);
    }

    /* ================= 内部方法 ================= */

    private void validate(FigureProductSaveRequest req) {
        if (req.getWeightWithBox() == null || req.getWeightWithBox() <= 0) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "带盒重量必须大于 0");
        }
        List<String> imgs = req.getImages();
        if (imgs == null || imgs.size() < 3 || imgs.size() > 4) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "商品图片需 3~4 张");
        }
    }

    private void checkCodeUnique(String code, Long id) {
        if (!StringUtils.hasText(code)) {
            return;
        }
        String trimmed = code.trim();
        LambdaQueryWrapper<FigureProduct> w = Wrappers.<FigureProduct>lambdaQuery()
                .eq(FigureProduct::getCode, trimmed);
        if (id != null) {
            w.ne(FigureProduct::getId, id);
        }
        w.last("LIMIT 1");
        FigureProduct exist = mapper.selectOne(w);
        if (exist != null) {
            throw new BusinessException(ResultCode.DATA_ALREADY_EXISTS, "货号已存在：" + trimmed);
        }
    }

    private void apply(FigureProduct p, FigureProductSaveRequest req) {
        p.setName(req.getName().trim());
        p.setCode(StringUtils.hasText(req.getCode()) ? req.getCode().trim() : null);
        p.setBrand(blankToNull(req.getBrand()));
        p.setSeries(blankToNull(req.getSeries()));
        p.setCharacterName(blankToNull(req.getCharacterName()));
        p.setMaterial(blankToNull(req.getMaterial()));
        p.setLengthCm(req.getLengthCm());
        p.setWidthCm(req.getWidthCm());
        p.setHeightCm(req.getHeightCm());
        p.setWeightWithBox(req.getWeightWithBox());
        p.setImages(FigureProductConverter.join(req.getImages()));
        p.setStatus(req.getStatus() == null ? STATUS_ENABLED : req.getStatus());
        p.setRemark(blankToNull(req.getRemark()));
    }

    private FigureProduct getExisting(Long id) {
        FigureProduct p = mapper.selectById(id);
        if (p == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "商品不存在或已被删除");
        }
        return p;
    }

    private static String blankToNull(String value) {
        return value == null ? null : value.trim();
    }
}
