package com.figureerp.module.acg.converter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ACG 作品用到的枚举字典与校验。
 * <p>
 * 类型和连载状态都用英文码落库、中文文案出参，前端只认码值，展示文案由后端或前端字典统一给，
 * 避免出现「动画 / 番剧 / TV动画」这种同义不同写法。
 */
public final class AcgEnums {

    /* ---------- 作品类型 ---------- */

    public static final String TYPE_ANIME = "ANIME";
    public static final String TYPE_MANGA = "MANGA";
    public static final String TYPE_GAME = "GAME";
    public static final String TYPE_NOVEL = "NOVEL";
    public static final String TYPE_MOVIE = "MOVIE";
    public static final String TYPE_OTHER = "OTHER";

    private static final Map<String, String> TYPE_TEXT = new LinkedHashMap<>();

    /* ---------- 连载状态 ---------- */

    public static final String STATUS_ONGOING = "ONGOING";
    public static final String STATUS_FINISHED = "FINISHED";
    public static final String STATUS_UPCOMING = "UPCOMING";

    private static final Map<String, String> RELEASE_STATUS_TEXT = new LinkedHashMap<>();

    static {
        TYPE_TEXT.put(TYPE_ANIME, "动画");
        TYPE_TEXT.put(TYPE_MANGA, "漫画");
        TYPE_TEXT.put(TYPE_GAME, "游戏");
        TYPE_TEXT.put(TYPE_NOVEL, "轻小说");
        TYPE_TEXT.put(TYPE_MOVIE, "剧场版");
        TYPE_TEXT.put(TYPE_OTHER, "其他");

        RELEASE_STATUS_TEXT.put(STATUS_ONGOING, "连载中");
        RELEASE_STATUS_TEXT.put(STATUS_FINISHED, "已完结");
        RELEASE_STATUS_TEXT.put(STATUS_UPCOMING, "待播出");
    }

    private AcgEnums() {
    }

    public static boolean isValidType(String type) {
        return type != null && TYPE_TEXT.containsKey(type);
    }

    public static String typeText(String type) {
        return TYPE_TEXT.getOrDefault(type, type);
    }

    public static boolean isValidReleaseStatus(String status) {
        return status != null && RELEASE_STATUS_TEXT.containsKey(status);
    }

    public static String releaseStatusText(String status) {
        return RELEASE_STATUS_TEXT.getOrDefault(status, status);
    }

    public static Map<String, String> typeDict() {
        return new LinkedHashMap<>(TYPE_TEXT);
    }

    public static Map<String, String> releaseStatusDict() {
        return new LinkedHashMap<>(RELEASE_STATUS_TEXT);
    }
}
