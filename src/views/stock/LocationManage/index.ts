/**
 * 目前预留当前页面需要的interface
 */

// --- 1. 仓库实体接口 ---
export interface Warehouse {
  warehouseCode: string           // 仓库代码
  warehouseName: string           // 仓库名称
  warehouseAddress: string        // 仓库地址
  isReturn: 'N' | 'Y'             // 是否退货库
  status: 'A' | 'C' | 'D'         // 状态
}
