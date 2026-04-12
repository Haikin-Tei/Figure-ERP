<!-- src/components/common/BaseTable.vue -->
<template>
  <div class="base-table">
    <el-table :data="data" v-bind="$attrs" style="width: 100%" v-loading="loading">
      <!-- 1. 默认插槽：用于插入 el-table-column (如 selection, index 等) -->
      <slot></slot>

      <!-- 2. 循环渲染配置的列 -->
      <el-table-column v-for="col in columns" :key="col.prop" :prop="col.prop" :label="col.label" :width="col.width"
        :min-width="col.minWidth" :align="col.align || 'left'" :fixed="col.fixed">
        <!--
          3. 作用域插槽透传
          如果父组件提供了对应 prop 的插槽，就使用父组件的模板；
          否则显示默认的文本内容。
        -->
        <template #default="scope">
          <!-- 默认显示文本 (如果配置了formatter则调用formatter) -->
          <slot v-if="$slots[col.prop]" :name="col.prop" v-bind="scope"></slot>
          <span v-else>
            {{ col.formatter ? col.formatter(scope.row, col) : scope.row[col.prop] }}
          </span>
        </template>
      </el-table-column>
    </el-table>

    <!-- 4. 内置分页器 (可选) -->
    <div v-if="pagination" class="pagination-wrapper">
      <el-pagination v-model:current-page="currentPage" v-model:page-size="pageSize" :page-sizes="[10, 20, 50, 100]"
        :total="total" layout="total, sizes, prev, pager, next, jumper" @size-change="handleSizeChange"
        @current-change="handleCurrentChange" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'

// --- 定义 Props ---
interface Column {
  prop: string        // 字段名
  label: string       // 表头名
  width?: string | number
  minWidth?: string | number
  align?: 'left' | 'center' | 'right'
  fixed?: boolean | 'left' | 'right'
  formatter?: (row: any, col: Column) => string // 格式化函数
}

const props = withDefaults(defineProps<{
  data: any[]               // 表格数据
  columns: Column[]         // 列配置
  loading?: boolean         // 加载状态
  pagination?: boolean      // 是否显示分页
  total?: number            // 总条数
  currentPage?: number      // 当前页
  pageSize?: number         // 每页条数
}>(), {
  loading: false,
  pagination: false,
  total: 0,
  currentPage: 1,
  pageSize: 10
})

// --- 定义 Emits ---
const emit = defineEmits(['update:currentPage', 'update:pageSize', 'pagination-change'])

// --- 分页逻辑 ---
const currentPage = ref(props.currentPage)
const pageSize = ref(props.pageSize)

const handleSizeChange = (val: number) => {
  emit('update:pageSize', val)
  emit('pagination-change', { page: currentPage.value, limit: val })
}

const handleCurrentChange = (val: number) => {
  emit('update:currentPage', val)
  emit('pagination-change', { page: val, limit: pageSize.value })
}
</script>

<style scoped>
.base-table {
  background: #fff;
  padding: 20px;
  border-radius: 4px;
}

.pagination-wrapper {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}
</style>
