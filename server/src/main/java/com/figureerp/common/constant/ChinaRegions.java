package com.figureerp.common.constant;

import lombok.Getter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 全国省级行政区与主要城市，以及它们所属的运费区域。
 * <p>
 * 用途：
 * <ul>
 *   <li>前端下拉框的数据源（通过 {@code GET /api/logistics/dicts} 下发，前端不写死）</li>
 *   <li>初始化全国价格时按省份所属区域套用基准价</li>
 * </ul>
 * 城市列表只收省会与主要地级市，够下单填地址用；确实需要用到的城市可以在维护页手工补。
 */
public final class ChinaRegions {

    private ChinaRegions() {
    }

    /**
     * 一个省级行政区。
     *
     * @param name   省名，与数据库 origin_province / dest_province 一致
     * @param zone   所属运费区域
     * @param cities 主要城市，第一个默认是省会/首府
     */
    public record Province(String name, FreightZone zone, List<String> cities) {

        public String zoneLabel() {
            return zone.getLabel();
        }

        /** 直辖市与特别行政区没有「地级市」概念，城市下拉直接用自身 */
        public boolean isMunicipality() {
            return name.endsWith("市") && (name.startsWith("北京") || name.startsWith("上海")
                    || name.startsWith("天津") || name.startsWith("重庆"));
        }
    }

    /** 34 个省级行政区（23 省 + 5 自治区 + 4 直辖市 + 2 特别行政区），按地理大区排序 */
    private static final List<Province> PROVINCES = List.of(
            // 华东
            new Province("上海市", FreightZone.EAST, List.of("上海市")),
            new Province("江苏省", FreightZone.EAST, List.of("南京市", "无锡市", "徐州市", "常州市", "苏州市", "南通市", "扬州市", "泰州市")),
            new Province("浙江省", FreightZone.EAST, List.of("杭州市", "宁波市", "温州市", "嘉兴市", "绍兴市", "金华市", "台州市")),
            new Province("安徽省", FreightZone.EAST, List.of("合肥市", "芜湖市", "蚌埠市", "阜阳市", "安庆市")),
            new Province("福建省", FreightZone.EAST, List.of("福州市", "厦门市", "泉州市", "漳州市", "莆田市")),
            new Province("江西省", FreightZone.EAST, List.of("南昌市", "九江市", "赣州市", "上饶市", "宜春市")),
            new Province("山东省", FreightZone.EAST, List.of("济南市", "青岛市", "淄博市", "烟台市", "潍坊市", "临沂市")),

            // 华北
            new Province("北京市", FreightZone.NORTH, List.of("北京市")),
            new Province("天津市", FreightZone.NORTH, List.of("天津市")),
            new Province("河北省", FreightZone.NORTH, List.of("石家庄市", "唐山市", "秦皇岛市", "邯郸市", "保定市", "廊坊市")),
            new Province("山西省", FreightZone.NORTH, List.of("太原市", "大同市", "长治市", "临汾市")),
            new Province("内蒙古自治区", FreightZone.NORTH, List.of("呼和浩特市", "包头市", "鄂尔多斯市", "赤峰市")),

            // 华中
            new Province("河南省", FreightZone.CENTRAL, List.of("郑州市", "洛阳市", "开封市", "新乡市", "南阳市")),
            new Province("湖北省", FreightZone.CENTRAL, List.of("武汉市", "宜昌市", "襄阳市", "荆州市")),
            new Province("湖南省", FreightZone.CENTRAL, List.of("长沙市", "株洲市", "湘潭市", "衡阳市", "岳阳市")),

            // 华南
            new Province("广东省", FreightZone.SOUTH, List.of("广州市", "深圳市", "珠海市", "佛山市", "东莞市", "中山市", "惠州市")),
            new Province("广西壮族自治区", FreightZone.SOUTH, List.of("南宁市", "柳州市", "桂林市", "北海市")),
            new Province("海南省", FreightZone.SOUTH, List.of("海口市", "三亚市")),

            // 东北
            new Province("辽宁省", FreightZone.NORTHEAST, List.of("沈阳市", "大连市", "鞍山市", "锦州市")),
            new Province("吉林省", FreightZone.NORTHEAST, List.of("长春市", "吉林市", "四平市")),
            new Province("黑龙江省", FreightZone.NORTHEAST, List.of("哈尔滨市", "齐齐哈尔市", "大庆市", "牡丹江市")),

            // 西南
            new Province("重庆市", FreightZone.SOUTHWEST, List.of("重庆市")),
            new Province("四川省", FreightZone.SOUTHWEST, List.of("成都市", "绵阳市", "德阳市", "宜宾市", "南充市")),
            new Province("贵州省", FreightZone.SOUTHWEST, List.of("贵阳市", "遵义市", "六盘水市")),
            new Province("云南省", FreightZone.SOUTHWEST, List.of("昆明市", "曲靖市", "大理市", "丽江市")),
            new Province("西藏自治区", FreightZone.TIBET, List.of("拉萨市", "日喀则市", "林芝市")),

            // 西北
            new Province("陕西省", FreightZone.NORTHWEST, List.of("西安市", "宝鸡市", "咸阳市", "渭南市")),
            new Province("甘肃省", FreightZone.NORTHWEST, List.of("兰州市", "天水市", "酒泉市")),
            new Province("青海省", FreightZone.NORTHWEST, List.of("西宁市", "海东市")),
            new Province("宁夏回族自治区", FreightZone.NORTHWEST, List.of("银川市", "石嘴山市", "吴忠市")),
            new Province("新疆维吾尔自治区", FreightZone.XINJIANG, List.of("乌鲁木齐市", "克拉玛依市", "喀什市", "伊宁市")),

            // 港澳台
            new Province("香港特别行政区", FreightZone.HMT, List.of("香港")),
            new Province("澳门特别行政区", FreightZone.HMT, List.of("澳门")),
            new Province("台湾省", FreightZone.HMT, List.of("台北市", "高雄市", "台中市"))
    );

    private static final Map<String, Province> INDEX = buildIndex();

    private static Map<String, Province> buildIndex() {
        Map<String, Province> map = new LinkedHashMap<>();
        for (Province province : PROVINCES) {
            map.put(province.name(), province);
        }
        return map;
    }

    public static List<Province> all() {
        return PROVINCES;
    }

    public static List<String> names() {
        List<String> names = new ArrayList<>(PROVINCES.size());
        for (Province province : PROVINCES) {
            names.add(province.name());
        }
        return names;
    }

    public static Province get(String name) {
        return name == null ? null : INDEX.get(name);
    }

    public static boolean exists(String name) {
        return name != null && INDEX.containsKey(name);
    }

    /** 省份所属区域，未知省份按华东处理（自定义出发地时不至于炸掉） */
    public static FreightZone zoneOf(String provinceName) {
        Province province = get(provinceName);
        return province == null ? FreightZone.EAST : province.zone();
    }

    public static String zoneLabelOf(String provinceName) {
        return zoneOf(provinceName).getLabel();
    }

    /** 该省的城市列表，未知省份返回空列表 */
    public static List<String> citiesOf(String provinceName) {
        Province province = get(provinceName);
        return province == null ? List.of() : province.cities();
    }
}
