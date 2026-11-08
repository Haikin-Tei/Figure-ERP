<template>
  <div class="page-container">
    <div class="card">
      <!-- 头部 -->
      <div class="card-head">
        <h2 class="page-title">
          <el-icon><Reading /></el-icon>
          目录维护
        </h2>
        <div class="head-actions">
          <el-button :icon="Refresh" :loading="loading" @click="refreshAll">刷新</el-button>
          <el-button v-if="canManage" type="primary" :icon="Plus" @click="openCreate">新增作品</el-button>
        </div>
      </div>
      <p class="page-desc">
        维护 ACG 作品主数据（供商品、订单等业务作为可选项），并通过热度快照判断哪些作品更值得投入。
      </p>

      <!-- 关键指标 -->
      <div class="kpi-row">
        <div v-for="k in kpis" :key="k.label" class="kpi" :class="k.tone">
          <div class="kpi-label">{{ k.label }}</div>
          <div class="kpi-value">
            {{ k.value }}<span v-if="k.unit" class="kpi-unit">{{ k.unit }}</span>
          </div>
          <div class="kpi-sub">{{ k.sub }}</div>
        </div>
      </div>

      <el-tabs v-model="activeTab" class="acg-tabs">
        <!-- ============ 作品目录 ============ -->
        <el-tab-pane label="作品目录及维护" name="list">
          <div class="filter-bar">
            <el-input
              v-model="query.name"
              placeholder="作品名 / 原名"
              clearable
              style="width: 180px"
              :prefix-icon="Search"
              @keyup.enter="handleSearch"
            />
            <el-select v-model="query.workType" placeholder="作品类型" clearable style="width: 120px">
              <el-option v-for="t in WORK_TYPES" :key="t.value" :label="t.label" :value="t.value" />
            </el-select>
            <el-select v-model="query.releaseStatus" placeholder="连载状态" clearable style="width: 120px">
              <el-option v-for="s in RELEASE_STATUSES" :key="s.value" :label="s.label" :value="s.value" />
            </el-select>
            <el-select v-model="query.status" placeholder="启用状态" clearable style="width: 120px">
              <el-option label="可选" :value="1" />
              <el-option label="停用" :value="0" />
            </el-select>
            <el-select v-model="query.sortBy" style="width: 170px">
              <el-option v-for="s in SORT_OPTIONS" :key="s.value" :label="s.label" :value="s.value" />
            </el-select>
            <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
            <el-button :icon="Refresh" @click="handleReset">重置</el-button>
          </div>

          <el-table v-loading="loading" :data="list" stripe border row-key="id" class="acg-table">
            <el-table-column label="封面" width="72">
              <template #default="{ row }">
                <el-image
                  v-if="row.coverImage"
                  :src="row.coverImage"
                  :preview-src-list="[row.coverImage]"
                  preview-teleported
                  fit="cover"
                  class="cover"
                />
                <div v-else class="cover cover-empty">{{ row.name.slice(0, 1) }}</div>
              </template>
            </el-table-column>

            <el-table-column label="作品" min-width="170">
              <template #default="{ row }">
                <div class="name-cell">
                  <span class="w-name">{{ row.name }}</span>
                  <span class="muted ellipsis">{{ row.originalName || '—' }}</span>
                  <span class="muted">
                    {{ row.workCode || '未编号' }}<template v-if="row.releaseYear"> · {{ row.releaseYear }} 年</template>
                  </span>
                </div>
              </template>
            </el-table-column>

            <el-table-column label="类型" width="92">
              <template #default="{ row }">
                <el-tag :type="tagType(row.workType)" size="small" effect="light">
                  {{ row.workTypeText }}
                </el-tag>
                <div class="muted mt4">{{ row.releaseStatusText }}</div>
              </template>
            </el-table-column>

            <el-table-column label="版权 / 制作" min-width="150">
              <template #default="{ row }">
                <div class="sub-cell">
                  <span class="ellipsis">{{ row.ipOwner || '—' }}</span>
                  <span class="muted ellipsis">{{ row.studio || '—' }}</span>
                </div>
              </template>
            </el-table-column>

            <el-table-column label="热度" width="142">
              <template #default="{ row }">
                <div class="heat-cell">
                  <div class="heat-top">
                    <span class="heat-value">{{ row.heatScore }}</span>
                    <span class="trend" :class="trendClass(row.trendRate)">
                      <el-icon v-if="row.trendRate > 0.5"><Top /></el-icon>
                      <el-icon v-else-if="row.trendRate < -0.5"><Bottom /></el-icon>
                      <el-icon v-else><Minus /></el-icon>
                      {{ Math.abs(row.trendRate) }}%
                    </span>
                  </div>
                  <el-progress
                    :percentage="row.heatScore"
                    :show-text="false"
                    :stroke-width="6"
                    :color="heatColor(row.heatScore)"
                  />
                </div>
              </template>
            </el-table-column>

            <el-table-column label="月销 / 累计" width="116">
              <template #default="{ row }">
                <div class="sub-cell">
                  <span class="sales">{{ formatNumber(row.monthlySales) }}</span>
                  <span class="muted">累计 {{ formatNumber(row.totalSales) }}</span>
                </div>
              </template>
            </el-table-column>

            <el-table-column label="选品潜力" width="118">
              <template #default="{ row }">
                <div class="potential">
                  <span class="score">{{ row.potentialScore }}</span>
                  <span
                    class="level-badge"
                    :style="levelStyle(row.potentialLevel)"
                    :title="LEVEL_META[row.potentialLevel]?.desc"
                  >
                    {{ row.potentialLevel }} · {{ row.potentialLevelText }}
                  </span>
                </div>
              </template>
            </el-table-column>

            <el-table-column label="标签" min-width="130">
              <template #default="{ row }">
                <div class="tags">
                  <el-tag v-for="t in row.tags" :key="t" size="small" type="info" effect="plain">{{ t }}</el-tag>
                  <span v-if="!row.tags.length" class="muted">—</span>
                </div>
              </template>
            </el-table-column>

            <el-table-column label="状态" width="76" align="center">
              <template #default="{ row }">
                <el-tag
                  :type="row.status === 1 ? 'success' : 'info'"
                  size="small"
                  :class="{ 'status-clickable': canManage }"
                  :title="canManage ? '点击切换可选 / 停用' : ''"
                  @click="canManage && toggleStatus(row)"
                >
                  {{ row.status === 1 ? '可选' : '停用' }}
                </el-tag>
              </template>
            </el-table-column>

            <el-table-column label="操作" width="212" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="openTrend(row)">趋势</el-button>
                <el-button v-if="canManage" link type="warning" size="small" @click="openHeat(row)">录热度</el-button>
                <el-button v-if="canManage" link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
                <el-button v-if="canManage" link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>

          <div class="pager">
            <el-pagination
              :current-page="query.page"
              :page-size="query.size"
              :page-sizes="[10, 20, 50]"
              :total="total"
              layout="total, sizes, prev, pager, next, jumper"
              @current-change="handlePageChange"
              @size-change="handleSizeChange"
            />
          </div>
        </el-tab-pane>

        <!-- ============ 分析 ============ -->
        <el-tab-pane label="热度与选品分析" name="analysis">
          <div v-loading="analysisLoading">
            <div class="chart-grid">
              <div class="chart-card">
                <div class="chart-title">热度 × 销量 四象限</div>
                <div class="chart-sub">右上角是既火又好卖的主推位；左上角热度不高但卖得动，值得加推</div>
                <div ref="heatSalesChartRef" class="chart"></div>
              </div>
              <div class="chart-card">
                <div class="chart-title">类型分布</div>
                <div class="chart-sub">柱状为近 30 天销量，折线为作品数量</div>
                <div ref="typeChartRef" class="chart"></div>
              </div>
            </div>

            <div class="analysis-row">
              <div class="rank-card">
                <div class="rank-head">
                  <span class="chart-title">潜力排行</span>
                  <el-radio-group v-model="rankBy" size="small">
                    <el-radio-button value="potential">潜力分</el-radio-button>
                    <el-radio-button value="heat">热度</el-radio-button>
                    <el-radio-button value="sales">月销量</el-radio-button>
                  </el-radio-group>
                </div>
                <el-table :data="rankedTop" size="small" stripe class="rank-table">
                  <el-table-column label="#" width="46" align="center">
                    <template #default="{ $index }">
                      <span class="rank-no" :class="{ top3: $index < 3 }">{{ $index + 1 }}</span>
                    </template>
                  </el-table-column>
                  <el-table-column label="作品" min-width="130">
                    <template #default="{ row }">
                      <div class="name-cell">
                        <span class="w-name">{{ row.name }}</span>
                        <span class="muted">{{ row.workTypeText }}</span>
                      </div>
                    </template>
                  </el-table-column>
                  <el-table-column label="热度" width="66" align="right">
                    <template #default="{ row }">{{ row.heatScore }}</template>
                  </el-table-column>
                  <el-table-column label="月销" width="78" align="right">
                    <template #default="{ row }">{{ formatNumber(row.monthlySales) }}</template>
                  </el-table-column>
                  <el-table-column label="潜力" width="80" align="right">
                    <template #default="{ row }">
                      <span class="score" :style="{ color: LEVEL_META[row.potentialLevel]?.color }">
                        {{ row.potentialScore }}
                      </span>
                    </template>
                  </el-table-column>
                  <el-table-column label="" width="56" align="center">
                    <template #default="{ row }">
                      <el-button link type="primary" size="small" @click="openTrendById(row.id)">趋势</el-button>
                    </template>
                  </el-table-column>
                </el-table>
              </div>

              <div class="insight-card">
                <div class="chart-title">分析结论</div>
                <ul v-if="analysis?.insights.length" class="insights">
                  <li v-for="(t, i) in analysis.insights" :key="i">{{ t }}</li>
                </ul>
                <div v-else class="muted">暂无数据</div>

                <template v-if="analysis">
                  <div class="level-summary">
                    <div v-for="lv in levelList" :key="lv" class="level-item">
                      <span class="level-dot" :style="{ background: LEVEL_META[lv]?.color }"></span>
                      <span class="level-text">{{ lv }} 级 · {{ LEVEL_META[lv]?.text }}</span>
                      <span class="level-count">{{ levelCount(lv) }}</span>
                    </div>
                  </div>
                  <div class="formula">
                    <div class="formula-title">潜力分口径（可解释、可调整）</div>
                    <div class="formula-body">{{ analysis.formula }}</div>
                  </div>
                </template>
              </div>
            </div>
          </div>
        </el-tab-pane>
      </el-tabs>
    </div>

    <!-- ============ 新增 / 编辑 ============ -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑作品' : '新增作品'"
      width="720px"
      top="6vh"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="92px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="作品名称" prop="name">
              <el-input v-model="form.name" placeholder="如：葬送的芙莉莲" maxlength="120" show-word-limit />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="作品编号">
              <el-input v-model="form.workCode" placeholder="如：ACG-006，可留空" maxlength="50" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="原名">
              <el-input v-model="form.originalName" placeholder="日文 / 英文原名" maxlength="160" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="类型" prop="workType">
              <el-select v-model="form.workType" style="width: 100%">
                <el-option v-for="t in WORK_TYPES" :key="t.value" :label="t.label" :value="t.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="连载状态">
              <el-select v-model="form.releaseStatus" style="width: 100%">
                <el-option v-for="s in RELEASE_STATUSES" :key="s.value" :label="s.label" :value="s.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="发行年份">
              <el-input-number v-model="form.releaseYear" :min="1950" :max="2100" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="版权方">
              <el-input v-model="form.ipOwner" placeholder="如：集英社 / 米哈游" maxlength="100" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="制作公司">
              <el-input v-model="form.studio" placeholder="如：MAPPA / ufotable" maxlength="100" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="作者 / 原作">
              <el-input v-model="form.author" placeholder="如：芥见下下" maxlength="100" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态">
              <el-radio-group v-model="form.status">
                <el-radio :value="1">可选</el-radio>
                <el-radio :value="0">停用</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">热度与销量</el-divider>
        <el-row :gutter="16">
          <el-col :span="24">
            <el-form-item label="当前热度">
              <div class="heat-input">
                <el-slider v-model="form.heatScore" :max="100" :step="1" style="flex: 1" />
                <el-input-number v-model="form.heatScore" :min="0" :max="100" style="width: 110px" />
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="近30天销量">
              <el-input-number v-model="form.monthlySales" :min="0" :step="100" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="累计销量">
              <el-input-number v-model="form.totalSales" :min="0" :step="1000" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <div class="form-tip">
          日常更新热度建议用列表里的「录热度」，这样会留下快照、能算出涨跌趋势；这里改只影响当前值。
        </div>

        <el-divider content-position="left">其他</el-divider>
        <el-form-item label="标签">
          <el-select
            v-model="form.tags"
            multiple
            filterable
            allow-create
            default-first-option
            placeholder="输入后回车创建，如：热血、战斗"
            style="width: 100%"
          >
            <el-option v-for="t in tagOptions" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="封面图">
          <div class="cover-uploader">
            <el-image
              v-if="form.coverImage"
              :src="form.coverImage"
              :preview-src-list="[form.coverImage]"
              preview-teleported
              fit="cover"
              class="cover-preview"
            />
            <div v-else class="cover-preview cover-empty">无封面</div>
            <div class="cover-actions">
              <el-upload
                :show-file-list="false"
                :http-request="uploadCover"
                :before-upload="beforeCoverUpload"
                accept="image/jpeg,image/png,image/gif,image/webp"
              >
                <el-button size="small" :loading="coverUploading">
                  {{ form.coverImage ? '更换封面' : '上传封面' }}
                </el-button>
              </el-upload>
              <el-button v-if="form.coverImage" size="small" text type="danger" @click="form.coverImage = ''">
                移除
              </el-button>
            </div>
            <div class="form-tip">JPG / PNG / WEBP，单张不超过 5MB</div>
          </div>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">保存</el-button>
      </template>
    </el-dialog>

    <!-- ============ 录入热度 ============ -->
    <el-dialog v-model="heatDialogVisible" title="录入当期热度" width="480px" destroy-on-close>
      <div v-if="heatTarget" class="heat-target">
        <span class="w-name">{{ heatTarget.name }}</span>
        <span class="muted">当前 {{ heatTarget.heatScore }} 分 · 月销 {{ formatNumber(heatTarget.monthlySales) }} 件</span>
      </div>
      <el-form label-width="88px">
        <el-form-item label="新热度">
          <div class="heat-input">
            <el-slider v-model="heatForm.heatScore" :max="100" style="flex: 1" />
            <el-input-number v-model="heatForm.heatScore" :min="0" :max="100" style="width: 104px" />
          </div>
        </el-form-item>
        <el-form-item label="近30天销量">
          <el-input-number v-model="heatForm.monthlySales" :min="0" :step="100" style="width: 100%" />
        </el-form-item>
        <el-form-item label="记录日期">
          <el-date-picker
            v-model="heatForm.recordDate"
            type="date"
            value-format="YYYY-MM-DD"
            :disabled-date="disableFuture"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="heatForm.remark" placeholder="如：新番开播 / 剧场版上映" maxlength="255" />
        </el-form-item>
      </el-form>
      <div class="form-tip">同一天重复录入会覆盖当天快照；主列表的当前热度会同步更新。</div>
      <template #footer>
        <el-button @click="heatDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="heatSubmitting" @click="handleSubmitHeat">保存</el-button>
      </template>
    </el-dialog>

    <!-- ============ 热度趋势 ============ -->
    <el-dialog v-model="trendDialogVisible" :title="trendTitle" width="700px" destroy-on-close>
      <div ref="trendChartRef" class="trend-chart"></div>
      <div class="form-tip">近 90 天热度快照，共 {{ trendPoints.length }} 个记录点</div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Bottom, Minus, Plus, Reading, Refresh, Search, Top } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import axios from 'axios'
import {
  createAcgWorkApi,
  deleteAcgWorkApi,
  getAcgAnalysisApi,
  getAcgTrendApi,
  pageAcgWorksApi,
  recordAcgHeatApi,
  updateAcgStatusApi,
  updateAcgWorkApi,
  type AcgAnalysis,
  type AcgHeatPoint,
  type AcgWork,
  type AcgWorkQueryParams,
  type AcgWorkSaveParams
} from '@/api/acg'
import { useUserStore } from '@/stores/user'
import { canManageBusiness } from '@/utils/role'
import { getToken } from '@/utils/auth'
import {
  LEVEL_META,
  RELEASE_STATUSES,
  SORT_OPTIONS,
  WORK_TYPES,
  formatNumber,
  tagType
} from './acgDict'
import { buildQuadrantOption, buildTrendOption, buildTypeOption } from './analysisOptions'

const userStore = useUserStore()
/** 管理员和主管可以维护作品与热度，普通员工只读 */
const canManage = computed(() => canManageBusiness(userStore.userInfo))

const activeTab = ref<'list' | 'analysis'>('list')

/* ==================== 列表 ==================== */

const loading = ref(false)
const list = ref<AcgWork[]>([])
const total = ref(0)

const query = reactive({
  page: 1,
  size: 10,
  name: '',
  workType: '',
  releaseStatus: '',
  status: undefined as number | undefined,
  sortBy: 'heatDesc'
})

async function fetchList() {
  loading.value = true
  try {
    const params: AcgWorkQueryParams = {
      page: query.page,
      size: query.size,
      name: query.name || undefined,
      workType: query.workType || undefined,
      releaseStatus: query.releaseStatus || undefined,
      status: query.status,
      sortBy: query.sortBy
    }
    const data = await pageAcgWorksApi(params)
    list.value = data.records
    total.value = data.total
  } catch {
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  query.page = 1
  fetchList()
}

function handleReset() {
  query.name = ''
  query.workType = ''
  query.releaseStatus = ''
  query.status = undefined
  query.sortBy = 'heatDesc'
  query.page = 1
  fetchList()
}

function handlePageChange(page: number) {
  query.page = page
  fetchList()
}

function handleSizeChange(size: number) {
  query.size = size
  query.page = 1
  fetchList()
}

async function refreshAll() {
  await Promise.all([fetchList(), fetchAnalysis()])
  if (activeTab.value === 'analysis') {
    renderAnalysisCharts()
  }
}

/* ==================== 分析 ==================== */

const analysis = ref<AcgAnalysis | null>(null)
const analysisLoading = ref(false)
const rankBy = ref<'potential' | 'heat' | 'sales'>('potential')

async function fetchAnalysis() {
  analysisLoading.value = true
  try {
    analysis.value = await getAcgAnalysisApi()
  } catch {
    analysis.value = null
  } finally {
    analysisLoading.value = false
  }
}

const kpis = computed(() => {
  const k = analysis.value?.kpi
  if (!k) {
    return [
      { label: '作品总数', value: '—', unit: '', sub: '等待加载', tone: 'tone-blue' },
      { label: '平均热度', value: '—', unit: '', sub: '0~100 分', tone: 'tone-red' },
      { label: '近30天销量', value: '—', unit: '', sub: '全部作品合计', tone: 'tone-orange' },
      { label: 'S 级主推', value: '—', unit: '', sub: '重点备货对象', tone: 'tone-purple' },
      { label: '平均潜力分', value: '—', unit: '', sub: '选品参考', tone: 'tone-green' }
    ]
  }
  return [
    {
      label: '作品总数',
      value: k.workCount,
      unit: ' 部',
      sub: `可选 ${k.selectableCount} 部 · 主力赛道「${k.topTypeText}」`,
      tone: 'tone-blue'
    },
    {
      label: '平均热度',
      value: k.avgHeat,
      unit: '',
      sub: '满分 100，来源：人工录入快照',
      tone: 'tone-red'
    },
    {
      label: '近30天销量',
      value: formatNumber(k.monthlySalesTotal),
      unit: ' 件',
      sub: '全部作品合计',
      tone: 'tone-orange'
    },
    {
      label: 'S 级主推',
      value: k.levelSCount,
      unit: ' 部',
      sub: '热度与销量双高且趋势向好',
      tone: 'tone-purple'
    },
    {
      label: '平均潜力分',
      value: k.avgPotential,
      unit: '',
      sub: `A 级 ${k.levelACount} · B 级 ${k.levelBCount} · C 级 ${k.levelCCount}`,
      tone: 'tone-green'
    }
  ]
})

const rankedTop = computed(() => {
  const rows = [...(analysis.value?.ranking ?? [])]
  const key = rankBy.value
  rows.sort((a, b) => {
    if (key === 'heat') return b.heatScore - a.heatScore
    if (key === 'sales') return b.monthlySales - a.monthlySales
    return b.potentialScore - a.potentialScore
  })
  return rows.slice(0, 10)
})

function levelCount(level: 'S' | 'A' | 'B' | 'C') {
  const k = analysis.value?.kpi
  if (!k) return 0
  return { S: k.levelSCount, A: k.levelACount, B: k.levelBCount, C: k.levelCCount }[level]
}

/* ---------- 图表 ---------- */

const heatSalesChartRef = ref<HTMLElement>()
const typeChartRef = ref<HTMLElement>()
let heatChart: echarts.ECharts | null = null
let typeChart: echarts.ECharts | null = null

function renderAnalysisCharts() {
  if (!analysis.value) return
  const data = analysis.value
  nextTick(() => {
    if (heatSalesChartRef.value) {
      heatChart?.dispose()
      heatChart = echarts.init(heatSalesChartRef.value)
      heatChart.setOption(buildQuadrantOption(data), true)
    }
    if (typeChartRef.value) {
      typeChart?.dispose()
      typeChart = echarts.init(typeChartRef.value)
      typeChart.setOption(buildTypeOption(data), true)
    }
  })
}

watch(activeTab, async (tab) => {
  if (tab === 'analysis') {
    if (!analysis.value) await fetchAnalysis()
    renderAnalysisCharts()
  }
})

function handleResize() {
  heatChart?.resize()
  typeChart?.resize()
  trendChart?.resize()
}

/* ==================== 新增 / 编辑 ==================== */

const dialogVisible = ref(false)
const isEdit = ref(false)
const editingId = ref<number | null>(null)
const submitting = ref(false)
const formRef = ref<FormInstance>()
const coverUploading = ref(false)

const form = reactive({
  name: '',
  workCode: '',
  originalName: '',
  workType: 'ANIME',
  releaseStatus: 'ONGOING',
  ipOwner: '',
  studio: '',
  author: '',
  releaseYear: undefined as number | undefined,
  heatScore: 50,
  monthlySales: 0,
  totalSales: 0,
  tags: [] as string[],
  coverImage: '',
  remark: '',
  status: 1
})

const rules: FormRules = {
  name: [{ required: true, message: '请输入作品名称', trigger: 'blur' }],
  workType: [{ required: true, message: '请选择作品类型', trigger: 'change' }]
}

/** 已用过的标签，作为输入建议 */
const tagOptions = computed(() => Array.from(new Set(list.value.flatMap((w) => w.tags))).sort())

function resetForm() {
  Object.assign(form, {
    name: '',
    workCode: '',
    originalName: '',
    workType: 'ANIME',
    releaseStatus: 'ONGOING',
    ipOwner: '',
    studio: '',
    author: '',
    releaseYear: undefined,
    heatScore: 50,
    monthlySales: 0,
    totalSales: 0,
    tags: [],
    coverImage: '',
    remark: '',
    status: 1
  })
}

function openCreate() {
  isEdit.value = false
  editingId.value = null
  resetForm()
  dialogVisible.value = true
}

function openEdit(row: AcgWork) {
  isEdit.value = true
  editingId.value = row.id
  Object.assign(form, {
    name: row.name,
    workCode: row.workCode ?? '',
    originalName: row.originalName ?? '',
    workType: row.workType,
    releaseStatus: row.releaseStatus,
    ipOwner: row.ipOwner ?? '',
    studio: row.studio ?? '',
    author: row.author ?? '',
    releaseYear: row.releaseYear ?? undefined,
    heatScore: row.heatScore,
    monthlySales: row.monthlySales,
    totalSales: row.totalSales,
    tags: [...(row.tags ?? [])],
    coverImage: row.coverImage ?? '',
    remark: row.remark ?? '',
    status: row.status
  })
  dialogVisible.value = true
}

async function handleSubmit() {
  if (!formRef.value) return
  const ok = await formRef.value.validate().catch(() => false)
  if (!ok) return

  const params: AcgWorkSaveParams = {
    name: form.name.trim(),
    workCode: form.workCode || undefined,
    originalName: form.originalName || undefined,
    workType: form.workType,
    releaseStatus: form.releaseStatus,
    ipOwner: form.ipOwner || undefined,
    studio: form.studio || undefined,
    author: form.author || undefined,
    releaseYear: form.releaseYear,
    heatScore: form.heatScore,
    monthlySales: form.monthlySales,
    totalSales: form.totalSales,
    tags: form.tags,
    coverImage: form.coverImage || undefined,
    remark: form.remark || undefined,
    status: form.status
  }

  submitting.value = true
  try {
    if (isEdit.value && editingId.value) {
      await updateAcgWorkApi(editingId.value, params)
      ElMessage.success('保存成功')
    } else {
      await createAcgWorkApi(params)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    await refreshAll()
  } catch {
    // 错误提示由 request 拦截器统一弹出
  } finally {
    submitting.value = false
  }
}

async function handleDelete(row: AcgWork) {
  try {
    await ElMessageBox.confirm(
      `确定删除《${row.name}》吗？删除后不再出现在下拉选项中，其热度历史也会一并失效。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await deleteAcgWorkApi(row.id)
    ElMessage.success('已删除')
    if (list.value.length === 1 && query.page > 1) query.page -= 1
    await refreshAll()
  } catch {
    /* 拦截器已提示 */
  }
}

async function toggleStatus(row: AcgWork) {
  const next = row.status === 1 ? 0 : 1
  try {
    await updateAcgStatusApi(row.id, next)
    ElMessage.success(next === 1 ? '已设为可选' : '已停用')
    await fetchList()
  } catch {
    /* 拦截器已提示 */
  }
}

/* ---------- 封面上传 ---------- */

function beforeCoverUpload(file: File) {
  if (!file.type.startsWith('image/')) {
    ElMessage.error('只能上传图片文件')
    return false
  }
  if (file.size > 5 * 1024 * 1024) {
    ElMessage.error('图片不能超过 5MB')
    return false
  }
  return true
}

async function uploadCover(options: any) {
  const fd = new FormData()
  fd.append('file', options.file)
  coverUploading.value = true
  try {
    const resp = await axios.post('/api/acg/upload', fd, {
      headers: { Authorization: `Bearer ${getToken() ?? ''}` }
    })
    const body = resp.data
    if (body && body.code === 200) {
      form.coverImage = body.data
      options.onSuccess?.(body.data)
      ElMessage.success('封面上传成功')
    } else {
      options.onError?.(new Error(body?.message || '上传失败'))
      ElMessage.error(body?.message || '封面上传失败')
    }
  } catch {
    options.onError?.(new Error('上传失败'))
    ElMessage.error('封面上传失败')
  } finally {
    coverUploading.value = false
  }
}

/* ==================== 录入热度 ==================== */

const heatDialogVisible = ref(false)
const heatTarget = ref<AcgWork | null>(null)
const heatSubmitting = ref(false)
const heatForm = reactive({
  heatScore: 50,
  monthlySales: 0,
  recordDate: '',
  remark: ''
})

function todayStr() {
  const d = new Date()
  const m = `${d.getMonth() + 1}`.padStart(2, '0')
  const day = `${d.getDate()}`.padStart(2, '0')
  return `${d.getFullYear()}-${m}-${day}`
}

function disableFuture(date: Date) {
  return date.getTime() > Date.now()
}

function openHeat(row: AcgWork) {
  heatTarget.value = row
  heatForm.heatScore = row.heatScore
  heatForm.monthlySales = row.monthlySales
  heatForm.recordDate = todayStr()
  heatForm.remark = ''
  heatDialogVisible.value = true
}

async function handleSubmitHeat() {
  if (!heatTarget.value) return
  heatSubmitting.value = true
  try {
    await recordAcgHeatApi(heatTarget.value.id, {
      heatScore: heatForm.heatScore,
      monthlySales: heatForm.monthlySales,
      recordDate: heatForm.recordDate || undefined,
      remark: heatForm.remark || undefined
    })
    ElMessage.success('热度已记录')
    heatDialogVisible.value = false
    await refreshAll()
  } catch {
    /* 拦截器已提示 */
  } finally {
    heatSubmitting.value = false
  }
}

/* ==================== 热度趋势 ==================== */

const trendDialogVisible = ref(false)
const trendWork = ref<AcgWork | null>(null)
const trendPoints = ref<AcgHeatPoint[]>([])
const trendChartRef = ref<HTMLElement>()
let trendChart: echarts.ECharts | null = null

const trendTitle = computed(() =>
  trendWork.value ? `《${trendWork.value.name}》热度趋势` : '热度趋势'
)

async function openTrend(row: AcgWork) {
  trendWork.value = row
  trendPoints.value = []
  trendDialogVisible.value = true
  try {
    trendPoints.value = await getAcgTrendApi(row.id, 90)
  } catch {
    trendPoints.value = []
  }
  await nextTick()
  if (trendChartRef.value) {
    trendChart?.dispose()
    trendChart = echarts.init(trendChartRef.value)
    trendChart.setOption(buildTrendOption(trendPoints.value), true)
  }
}

function openTrendById(id: number) {
  const row = list.value.find((w) => w.id === id)
  if (row) {
    openTrend(row)
    return
  }
  const rank = analysis.value?.ranking.find((r) => r.id === id)
  if (!rank) return
  openTrend({
    id: rank.id,
    name: rank.name,
    coverImage: rank.coverImage,
    heatScore: rank.heatScore,
    monthlySales: rank.monthlySales
  } as AcgWork)
}

/* ==================== 展示辅助 ==================== */

function trendClass(rate: number) {
  if (rate > 0.5) return 'trend-up'
  if (rate < -0.5) return 'trend-down'
  return 'trend-flat'
}

/** 潜力等级列表，模板里用于渲染等级分布 */
const levelList: Array<'S' | 'A' | 'B' | 'C'> = ['S', 'A', 'B', 'C']

function heatColor(score: number) {
  if (score >= 85) return '#f56c6c'
  if (score >= 70) return '#e6a23c'
  if (score >= 50) return '#409eff'
  return '#909399'
}

function levelStyle(level: string) {
  const color = LEVEL_META[level]?.color ?? '#909399'
  return { color, borderColor: `${color}66`, background: `${color}14` }
}

/* ==================== 生命周期 ==================== */

onMounted(async () => {
  await Promise.all([fetchList(), fetchAnalysis()])
  window.addEventListener('resize', handleResize)
  // 支持从其他页面带 ?tab=analysis 直接进入分析
  if (window.location.search.includes('tab=analysis')) {
    activeTab.value = 'analysis'
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  heatChart?.dispose()
  typeChart?.dispose()
  trendChart?.dispose()
  heatChart = null
  typeChart = null
  trendChart = null
})
</script>

<style scoped>
.page-container {
  padding: 20px;
}

.card {
  background-color: #fff;
  padding: 24px;
  border-radius: 8px;
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.05);
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.page-title {
  margin: 0;
  font-weight: 600;
  color: #303133;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 18px;
}

.head-actions {
  display: flex;
  gap: 8px;
}

.page-desc {
  margin: 8px 0 18px;
  font-size: 13px;
  color: #909399;
  line-height: 1.6;
}

/* 指标卡 */
.kpi-row {
  display: grid;
  grid-template-columns: repeat(5, minmax(150px, 1fr));
  gap: 12px;
  margin-bottom: 8px;
}

.kpi {
  border-radius: 8px;
  padding: 14px 16px;
  border: 1px solid #f0f2f5;
  background: #fafcff;
}

.kpi-label {
  font-size: 12px;
  color: #909399;
}

.kpi-value {
  font-size: 24px;
  font-weight: 700;
  color: #303133;
  margin: 6px 0 4px;
  line-height: 1.1;
}

.kpi-unit {
  font-size: 12px;
  font-weight: 400;
  color: #909399;
  margin-left: 2px;
}

.kpi-sub {
  font-size: 11px;
  color: #a8abb2;
  line-height: 1.5;
}

.tone-red .kpi-value {
  color: #f56c6c;
}
.tone-orange .kpi-value {
  color: #e6a23c;
}
.tone-purple .kpi-value {
  color: #8b5cf6;
}
.tone-green .kpi-value {
  color: #67c23a;
}
.tone-blue .kpi-value {
  color: #409eff;
}

.acg-tabs {
  margin-top: 8px;
}

/* 筛选栏 */
.filter-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 16px;
}

/* 表格 */
.acg-table {
  width: 100%;
}

.name-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.w-name {
  font-weight: 500;
  color: #303133;
}

.sub-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.muted {
  font-size: 12px;
  color: #909399;
}

.mt4 {
  margin-top: 4px;
}

.ellipsis {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  display: block;
}

.cover {
  width: 48px;
  height: 62px;
  border-radius: 4px;
  overflow: hidden;
  border: 1px solid #ebeef5;
}

.cover-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f5f7fa;
  color: #c0c4cc;
  font-size: 18px;
  font-weight: 600;
}

.heat-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.heat-top {
  display: flex;
  align-items: baseline;
  gap: 4px;
  white-space: nowrap;
}

.heat-value {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}

.trend {
  display: inline-flex;
  align-items: center;
  gap: 1px;
  font-size: 11px;
  white-space: nowrap;
}

.trend-up {
  color: #f56c6c;
}
.trend-down {
  color: #67c23a;
}
.trend-flat {
  color: #909399;
}

.sales {
  font-weight: 600;
  color: #303133;
}

.potential {
  display: flex;
  flex-direction: column;
  gap: 4px;
  align-items: flex-start;
}

.score {
  font-size: 16px;
  font-weight: 700;
  color: #303133;
}

.level-badge {
  font-size: 11px;
  padding: 1px 6px;
  border-radius: 10px;
  border: 1px solid;
  white-space: nowrap;
}

.tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.status-clickable {
  cursor: pointer;
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 18px;
}

/* 分析 */
.chart-grid {
  display: grid;
  grid-template-columns: 1.2fr 1fr;
  gap: 16px;
}

.chart-card,
.rank-card,
.insight-card {
  border: 1px solid #f0f2f5;
  border-radius: 8px;
  padding: 16px;
  background: #fff;
}

.chart-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.chart-sub {
  font-size: 12px;
  color: #a8abb2;
  margin: 2px 0 4px;
}

.chart {
  height: 300px;
  width: 100%;
}

.analysis-row {
  display: grid;
  grid-template-columns: 1.2fr 1fr;
  gap: 16px;
  margin-top: 16px;
}

.rank-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
  gap: 8px;
}

.rank-no {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: #f4f4f5;
  color: #909399;
  font-size: 12px;
}

.rank-no.top3 {
  background: #fdf6ec;
  color: #e6a23c;
  font-weight: 600;
}

.insights {
  margin: 8px 0 0;
  padding-left: 18px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.insights li {
  font-size: 13px;
  color: #606266;
  line-height: 1.7;
}

.level-summary {
  margin-top: 14px;
  border-top: 1px dashed #ebeef5;
  padding-top: 12px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.level-item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: #606266;
}

.level-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.level-text {
  flex: 1;
}

.level-count {
  font-weight: 600;
  color: #303133;
}

.formula {
  margin-top: 12px;
  background: #f8f9fb;
  border-radius: 6px;
  padding: 10px 12px;
}

.formula-title {
  font-size: 12px;
  font-weight: 600;
  color: #606266;
  margin-bottom: 4px;
}

.formula-body {
  font-size: 12px;
  color: #909399;
  line-height: 1.7;
}

/* 表单 */
.heat-input {
  display: flex;
  align-items: center;
  gap: 14px;
  width: 100%;
}

.cover-uploader {
  display: flex;
  align-items: flex-end;
  gap: 14px;
  flex-wrap: wrap;
}

.cover-preview {
  width: 72px;
  height: 96px;
  border-radius: 6px;
  border: 1px dashed #dcdfe6;
  overflow: hidden;
}

.cover-actions {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.form-tip {
  font-size: 12px;
  color: #a8abb2;
  line-height: 1.6;
  margin-top: 4px;
}

.heat-target {
  display: flex;
  flex-direction: column;
  gap: 4px;
  background: #f8f9fb;
  border-radius: 6px;
  padding: 10px 12px;
  margin-bottom: 16px;
}

.trend-chart {
  width: 100%;
  height: 300px;
}

/* 窄屏适配 */
@media (max-width: 1100px) {
  .kpi-row {
    grid-template-columns: repeat(2, minmax(140px, 1fr));
  }
  .chart-grid,
  .analysis-row {
    grid-template-columns: 1fr;
  }
}
</style>
