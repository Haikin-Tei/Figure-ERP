/**
 * 仓库管理模块的数据类型定义
 */

// --- 1. 表格列配置接口 ---
export interface Column {
  prop: string                    // 字段名
  label: string                   // 表头名
  width?: string | number
  minWidth?: string | number
  align?: 'left' | 'center' | 'right'
  fixed?: boolean | 'left' | 'right'
  formatter?: (row: any, col: Column) => string // 格式化函数
}
