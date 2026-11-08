package com.figureerp.module.acg;

import com.figureerp.module.acg.converter.AcgEnums;
import com.figureerp.module.acg.entity.AcgHeatLog;
import com.figureerp.module.acg.entity.AcgWork;
import com.figureerp.module.acg.mapper.AcgHeatLogMapper;
import com.figureerp.module.acg.mapper.AcgWorkMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 首次启动时写入一批 ACG 作品种子数据，方便直接看到列表、趋势与分析的完整效果。
 * <p>
 * 只在作品表为空时执行；已有数据不会覆盖，页面上改过/删过的数据也不会因重启还原。
 * 每部作品同时写 3 个热度快照（60 天前 / 30 天前 / 今天），这样趋势线和环比立即有意义。
 * <p>
 * 说明：热度与销量是按作品量级给的演示估算值，不是官方数据；实际使用请在页面上按真实情况录入。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.init-acg.enabled", havingValue = "true", matchIfMissing = true)
public class AcgDataInitializer implements ApplicationRunner {

    private final AcgWorkMapper workMapper;
    private final AcgHeatLogMapper heatLogMapper;

    /**
     * @param heat60 60 天前热度
     * @param heat30 30 天前热度
     * @param heatNow 当前热度
     */
    private record Seed(String code, String name, String originalName, String type,
                        String ipOwner, String studio, String author, int year, String releaseStatus,
                        int heat60, int heat30, int heatNow,
                        int monthlySales, int totalSales, String tags) {
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void run(ApplicationArguments args) {
        try {
            Long count = workMapper.selectCount(null);
            if (count != null && count > 0) {
                log.info("ACG 作品已有 {} 条记录，跳过初始化", count);
                return;
            }

            List<Seed> seeds = List.of(
                    new Seed("ACG-001", "初音未来", "初音ミク", AcgEnums.TYPE_OTHER,
                            "Crypton Future Media", "Crypton Future Media", "—", 2007, AcgEnums.STATUS_ONGOING,
                            88, 90, 92, 8600, 186000, "虚拟歌手,演唱会,常青IP"),
                    new Seed("ACG-002", "新世纪福音战士", "新世紀エヴァンゲリオン", AcgEnums.TYPE_ANIME,
                            "khara", "khara", "庵野秀明", 1995, AcgEnums.STATUS_FINISHED,
                            72, 72, 70, 2900, 98000, "机甲,经典,剧场版"),
                    new Seed("ACG-003", "鬼灭之刃", "鬼滅の刃", AcgEnums.TYPE_ANIME,
                            "Aniplex", "ufotable", "吾峠呼世晴", 2019, AcgEnums.STATUS_FINISHED,
                            92, 92, 88, 7400, 210000, "热血,和风,少年漫"),
                    new Seed("ACG-004", "咒术回战", "呪術廻戦", AcgEnums.TYPE_ANIME,
                            "集英社", "MAPPA", "芥见下下", 2020, AcgEnums.STATUS_ONGOING,
                            88, 88, 95, 9200, 168000, "热血,战斗,咒术"),
                    new Seed("ACG-005", "原神", "Genshin Impact", AcgEnums.TYPE_GAME,
                            "米哈游", "米哈游", "—", 2020, AcgEnums.STATUS_ONGOING,
                            88, 88, 90, 6800, 152000, "开放世界,二次元,手游"),
                    new Seed("ACG-006", "葬送的芙莉莲", "葬送のフリーレン", AcgEnums.TYPE_ANIME,
                            "小学馆", "MADHOUSE", "山田钟人", 2023, AcgEnums.STATUS_ONGOING,
                            78, 78, 86, 5200, 64000, "奇幻,治愈,新番"),
                    new Seed("ACG-007", "我推的孩子", "推しの子", AcgEnums.TYPE_ANIME,
                            "集英社", "动画工房", "赤坂明", 2023, AcgEnums.STATUS_ONGOING,
                            74, 74, 78, 4100, 52000, "偶像,悬疑,新番"),
                    new Seed("ACG-008", "赛马娘 Pretty Derby", "ウマ娘 プリティーダービー", AcgEnums.TYPE_GAME,
                            "Cygames", "Cygames", "—", 2021, AcgEnums.STATUS_ONGOING,
                            70, 70, 74, 3600, 88000, "养成,竞速,手游"),
                    new Seed("ACG-009", "进击的巨人", "進撃の巨人", AcgEnums.TYPE_MANGA,
                            "讲谈社", "WIT STUDIO / MAPPA", "谏山创", 2009, AcgEnums.STATUS_FINISHED,
                            64, 64, 58, 1500, 76000, "黑暗,战争,完结"),
                    new Seed("ACG-010", "间谍过家家", "SPY×FAMILY", AcgEnums.TYPE_MANGA,
                            "集英社", "WIT STUDIO × CloverWorks", "远藤达哉", 2019, AcgEnums.STATUS_ONGOING,
                            68, 68, 66, 2100, 59000, "喜剧,家庭,日常"),
                    new Seed("ACG-011", "孤独摇滚！", "ぼっち・ざ・ろっく！", AcgEnums.TYPE_MANGA,
                            "芳文社", "CloverWorks", "滨路晶", 2022, AcgEnums.STATUS_FINISHED,
                            64, 64, 62, 800, 21000, "音乐,日常,乐队"),
                    new Seed("ACG-012", "蓝色监狱", "ブルーロック", AcgEnums.TYPE_MANGA,
                            "讲谈社", "8bit", "金城宗幸", 2022, AcgEnums.STATUS_ONGOING,
                            60, 60, 72, 4800, 47000, "足球,群像,热血"),
                    new Seed("ACG-013", "电锯人", "チェンソーマン", AcgEnums.TYPE_MANGA,
                            "集英社", "MAPPA", "藤本树", 2018, AcgEnums.STATUS_ONGOING,
                            62, 62, 55, 600, 33000, "暗黑,血腥,邪道"),
                    new Seed("ACG-014", "宝可梦", "ポケットモンスター", AcgEnums.TYPE_GAME,
                            "The Pokémon Company", "Game Freak", "—", 1996, AcgEnums.STATUS_ONGOING,
                            80, 80, 84, 7000, 320000, "童年,IP常青,游戏")
            );

            LocalDate today = LocalDate.now();
            LocalDateTime now = LocalDateTime.now();

            for (Seed s : seeds) {
                AcgWork work = new AcgWork();
                work.setWorkCode(s.code);
                work.setName(s.name);
                work.setOriginalName(s.originalName);
                work.setWorkType(s.type);
                work.setIpOwner(s.ipOwner);
                work.setStudio(s.studio);
                work.setAuthor(s.author);
                work.setReleaseYear(s.year);
                work.setReleaseStatus(s.releaseStatus);
                work.setHeatScore(s.heatNow);
                work.setMonthlySales(s.monthlySales);
                work.setTotalSales(s.totalSales);
                work.setTags(s.tags);
                work.setHeatUpdatedAt(now);
                work.setRemark("演示数据，热度与销量为估算值");
                work.setStatus(1);
                workMapper.insert(work);

                insertLog(work.getId(), today.minusDays(60), s.heat60, s.monthlySales, "基线快照");
                insertLog(work.getId(), today.minusDays(30), s.heat30, s.monthlySales, "30 天前快照");
                insertLog(work.getId(), today, s.heatNow, s.monthlySales, "期初录入");
            }
            log.warn("已生成 {} 条 ACG 作品初始数据（含热度快照）", seeds.size());
        } catch (Exception e) {
            // 表没建时不影响应用启动，只提示怎么处理
            log.error("初始化 ACG 作品数据失败，请确认已执行 db/schema.sql 建表: {}", e.getMessage());
        }
    }

    private void insertLog(Long workId, LocalDate date, int heat, int sales, String remark) {
        AcgHeatLog log = new AcgHeatLog();
        log.setWorkId(workId);
        log.setRecordDate(date);
        log.setHeatScore(heat);
        log.setMonthlySales(sales);
        log.setRemark(remark);
        heatLogMapper.insert(log);
    }
}
