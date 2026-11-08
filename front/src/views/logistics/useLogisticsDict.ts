import { ref } from 'vue'
import {
  getLogisticsDictApi,
  type OriginDict,
  type ProvinceDict,
  type SupplierDict,
  type ZoneDict
} from '@/api/logistics'

/**
 * 物流模块共用的字典。
 * <p>
 * 两个页面（价格维护、价格查询）都依赖供应商 / 省份 / 区域 / 出发地，
 * 字典只拉一次放在模块级 ref 里，页面来回切换不会重复请求。
 */
const suppliers = ref<SupplierDict[]>([])
const provinces = ref<ProvinceDict[]>([])
const origins = ref<OriginDict[]>([])
const zones = ref<ZoneDict[]>([])
let loaded = false
let loading: Promise<void> | null = null

export function useLogisticsDict() {
  async function loadDicts(force = false) {
    if (loaded && !force) return
    if (loading && !force) return loading

    loading = (async () => {
      const data = await getLogisticsDictApi()
      suppliers.value = data.suppliers
      provinces.value = data.provinces
      origins.value = data.origins
      zones.value = data.zones
      loaded = true
      loading = null
    })()

    return loading
  }

  return { suppliers, provinces, origins, zones, loadDicts }
}

/** 省份 → 城市列表 */
export function citiesOf(provinceName: string): string[] {
  const province = provinces.value.find((p) => p.name === provinceName)
  return province ? province.cities : []
}

/** 省份 → 所属运费区域 */
export function zoneOf(provinceName: string): ZoneDict | undefined {
  const province = provinces.value.find((p) => p.name === provinceName)
  if (!province) return undefined
  return zones.value.find((z) => z.code === province.zone)
}

/** 供应商编码 → 名称 */
export function supplierLabel(code: string): string {
  const supplier = suppliers.value.find((s) => s.code === code)
  return supplier ? supplier.label : code
}

/** 金额展示：12 → 12.00 */
export function money(value?: number | string | null): string {
  if (value === null || value === undefined || value === '') return '—'
  const num = typeof value === 'string' ? Number(value) : value
  return Number.isNaN(num) ? '—' : num.toFixed(2)
}

/** 后端返回 ISO 时间，转成 2026-11-01 17:03:00 */
export function formatTime(value?: string): string {
  if (!value) return '—'
  return value.replace('T', ' ').slice(0, 19)
}
