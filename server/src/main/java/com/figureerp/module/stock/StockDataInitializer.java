package com.figureerp.module.stock;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.figureerp.module.stock.converter.InboundStatus;
import com.figureerp.module.stock.entity.InboundItem;
import com.figureerp.module.stock.entity.InboundOrder;
import com.figureerp.module.stock.mapper.InboundItemMapper;
import com.figureerp.module.stock.mapper.InboundOrderMapper;
import com.figureerp.module.stock.mapper.StockMapper;
import com.figureerp.module.stock.service.StockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 首次启动时用「已入库」的入库单回填一遍库存，让手办列表一打开就有数据可看。
 * <p>
 * 只在库存表为空时执行：已经有库存行（正常使用后一定会有）就不再跑，
 * 否则重启会把作废 / 出库后的数量重新加回来。
 * 依赖链：仓库（5）→ 区域（10）→ 货架（20）→ 手办（30）→ 入库单（40）→ 这里（50）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(50)
@ConditionalOnProperty(name = "app.init-stock.enabled", havingValue = "true", matchIfMissing = true)
public class StockDataInitializer implements ApplicationRunner {

    private final StockMapper stockMapper;
    private final InboundOrderMapper orderMapper;
    private final InboundItemMapper itemMapper;
    private final StockService stockService;

    @Override
    public void run(ApplicationArguments args) {
        try {
            Long count = stockMapper.selectCount(null);
            if (count != null && count > 0) {
                log.info("库存已有 {} 行记录，跳过回填", count);
                return;
            }

            // 按入库时间正序回填：同一库位多次入库时，最后一次的时间才是「最近入库时间」
            List<InboundOrder> orders = orderMapper.selectList(Wrappers.<InboundOrder>lambdaQuery()
                    .eq(InboundOrder::getStatus, InboundStatus.CONFIRMED)
                    .orderByAsc(InboundOrder::getInboundTime)
                    .orderByAsc(InboundOrder::getId));
            if (orders.isEmpty()) {
                log.info("没有「已入库」的入库单，库存表保持为空");
                return;
            }

            int handled = 0;
            for (InboundOrder order : orders) {
                List<InboundItem> items = itemMapper.selectList(Wrappers.<InboundItem>lambdaQuery()
                        .eq(InboundItem::getInboundId, order.getId())
                        .orderByAsc(InboundItem::getSortNo));
                if (items.isEmpty()) {
                    continue;
                }
                stockService.applyInbound(order, items);
                handled++;
            }
            log.warn("已按 {} 张「已入库」入库单回填库存（共 {} 张符合条件的单据）", handled, orders.size());
        } catch (Exception e) {
            // 表没建时不影响应用启动，只提示怎么处理
            log.error("回填库存数据失败，请确认已执行 db/schema.sql 建表: {}", e.getMessage());
        }
    }
}
