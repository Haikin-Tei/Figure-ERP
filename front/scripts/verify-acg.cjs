/**
 * ACG 作品目录 E2E 回归测试。
 *
 * 覆盖：
 *  1. 左侧一级菜单「目录维护」+ 二级「作品目录及维护」可进入，面包屑正确
 *  2. 列表从后端加载 14 部作品，KPI 卡片有值
 *  3. 类型筛选生效（游戏 = 3 部），重置恢复
 *  4. 新增作品 → 列表出现（真实落库）
 *  5. 录入热度 → 列表立即显示新热度（真实落库，趋势快照更新）
 *  6. 分析页：四象限图 / 类型分布图渲染、潜力排行有数据、结论有文字
 *  7. 单个作品热度趋势弹窗能画图
 *  8. 删除测试作品，数据清理干净
 *
 * 用 API 做前置/兜底清理，避免上一次失败导致货号/名称冲突。
 */
const { chromium } = require('playwright-core')

const BASE = 'http://localhost:5173'
const API = 'http://localhost:8080'
const TEST_NAME = 'E2E-ACG-测试作品'
const TEST_CODE = 'E2E-ACG-01'

let failed = 0
function check(ok, label, extra = '') {
  if (ok) {
    console.log(`✓ ${label}${extra ? ' — ' + extra : ''}`)
  } else {
    failed++
    console.error(`✗ ${label}${extra ? ' — ' + extra : ''}`)
  }
}

async function login() {
  const resp = await fetch(`${API}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: 'admin', password: 'admin123' })
  })
  const body = await resp.json()
  return body?.data?.token ?? ''
}

/** 用接口删掉历史残留的测试作品 */
async function cleanupByApi(token) {
  const resp = await fetch(`${API}/api/acg/page?name=E2E-ACG&size=50`, {
    headers: { Authorization: `Bearer ${token}` }
  })
  const body = await resp.json()
  const rows = body?.data?.records ?? []
  for (const row of rows) {
    await fetch(`${API}/api/acg/${row.id}`, {
      method: 'DELETE',
      headers: { Authorization: `Bearer ${token}` }
    })
    console.log(`· 清理历史测试数据：${row.name} (id=${row.id})`)
  }
  return rows.length
}

;(async () => {
  const token = await login()
  if (!token) {
    console.error('✗ 登录失败，无法执行测试')
    process.exit(1)
  }
  await cleanupByApi(token)

  const browser = await chromium.launch({ headless: true })
  const page = await browser.newPage({ viewport: { width: 1600, height: 950 } })
  const pageErrors = []
  page.on('pageerror', (e) => pageErrors.push(e.message))

  /** 表格行数：取主表与固定列表里最大的那个（固定列会复制一份 DOM） */
  async function rowCount() {
    return page.evaluate(() => {
      const bodies = document.querySelectorAll('.acg-table .el-table__body')
      let max = 0
      bodies.forEach((b) => {
        max = Math.max(max, b.querySelectorAll('tr').length)
      })
      return max
    })
  }

  try {
    /* ---------- 登录 ---------- */
    await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' })
    await page.fill('input[placeholder="登录账号"]', 'admin')
    await page.fill('input[placeholder="登录密码"]', 'admin123')
    await page.click('button.submit-btn')
    await page.waitForURL(/\/(home|dashboard)$/, { timeout: 15000 })

    /* ---------- 1. 菜单与入口 ---------- */
    const menuText = await page.locator('.sidebar-menu').innerText()
    check(menuText.includes('目录维护'), '一级菜单「目录维护」已出现')

    await page.locator('.el-sub-menu__title:has-text("目录维护")').click()
    await page.waitForTimeout(400)
    await page.locator('.el-menu-item:has-text("作品目录及维护")').click()
    await page.waitForURL('**/acg/catalog', { timeout: 10000 })
    check(true, '二级菜单「作品目录及维护」可进入', page.url())

    const crumb = (await page.locator('.breadcrumb-bar').innerText()).replace(/\s+/g, ' ').trim()
    check(crumb.includes('目录维护') && crumb.includes('作品目录及维护'), '面包屑层级正确', crumb)

    /* ---------- 2. 列表与 KPI ---------- */
    await page.waitForSelector('.acg-table .el-table__body tr', { timeout: 10000 })
    await page.waitForTimeout(500)
    const rows0 = await rowCount()
    const totalText = await page.locator('.el-pagination__total').innerText()
    check(rows0 === 10 && totalText.includes('14'), '列表从后端加载 14 部（首页 10 条）',
      `实际 ${rows0} 行 / ${totalText}`)

    const kpiText = await page.locator('.kpi-row').innerText()
    check(kpiText.includes('作品总数') && kpiText.includes('14'), 'KPI 卡片显示作品总数 14')
    check(kpiText.includes('平均热度') && kpiText.includes('近30天销量'), 'KPI 含平均热度与销量指标')

    const firstRowText = await page.locator('.acg-table .el-table__body tr').first().innerText()
    check(firstRowText.includes('咒术回战'), '默认按热度倒序，首行为《咒术回战》')
    check(/S/.test(firstRowText), '首行显示潜力等级 S')
    await page.screenshot({ path: '/tmp/shot-acg-list.png' })

    /* ---------- 3. 类型筛选 ---------- */
    await page.locator('.filter-bar .el-select').nth(0).click()
    await page.locator('.el-select-dropdown:visible .el-select-dropdown__item', { hasText: '游戏' }).first().click()
    await page.locator('button:has-text("查询")').click()
    await page.waitForTimeout(900)
    const gameRows = await rowCount()
    check(gameRows === 3, '筛选「游戏」后只剩 3 部', `实际 ${gameRows} 行`)

    await page.locator('button:has-text("重置")').click()
    await page.waitForTimeout(900)
    const resetRows = await rowCount()
    const resetTotal = await page.locator('.el-pagination__total').innerText()
    check(resetRows === 10 && resetTotal.includes('14'), '重置筛选后恢复全部 14 部',
      `实际 ${resetRows} 行 / ${resetTotal}`)

    /* ---------- 4. 新增作品 ---------- */
    await page.click('button:has-text("新增作品")')
    await page.waitForSelector('.el-dialog:visible', { timeout: 8000 })
    const dialog = page.locator('.el-dialog:visible').first()

    await dialog.locator('.el-form-item:has-text("作品名称") input').first().fill(TEST_NAME)
    await dialog.locator('.el-form-item:has-text("作品编号") input').first().fill(TEST_CODE)
    await dialog.locator('.el-form-item:has-text("原名") input').first().fill('E2E Test Anime')

    const heatInput = dialog.locator('.el-form-item:has-text("当前热度") .el-input-number input').first()
    await heatInput.fill('77')
    await heatInput.press('Enter')

    const salesInput = dialog.locator('.el-form-item:has-text("近30天销量") .el-input-number input').first()
    await salesInput.fill('1500')
    await salesInput.press('Enter')

    // 标签：输入后回车创建，随后点弹窗标题让下拉收起（否则会挡住保存按钮）
    const tagSelect = dialog.locator('.el-form-item:has-text("标签") .el-select input').first()
    await tagSelect.fill('E2E标签')
    await tagSelect.press('Enter')
    await dialog.locator('.el-dialog__title').click()
    await page.waitForTimeout(400)

    await dialog.locator('button:has-text("保存")').click()
    await page.waitForTimeout(1500)

    // 搜索确认已落库
    await page.fill('.filter-bar input[placeholder="作品名 / 原名"]', 'E2E-ACG')
    await page.locator('button:has-text("查询")').click()
    await page.waitForTimeout(900)
    const afterCreate = await rowCount()
    check(afterCreate === 1, '新增后按名称搜索到 1 条', `实际 ${afterCreate} 行`)
    const createdRow = await page.locator('.acg-table .el-table__body tr').first().innerText()
    check(createdRow.includes(TEST_NAME) && createdRow.includes('77'), '新作品落库且热度为 77')

    /* ---------- 5. 录入热度 ---------- */
    await page.locator('.acg-table .el-table__body tr').first().locator('button:has-text("录热度")').click()
    await page.waitForSelector('.el-dialog:visible', { timeout: 8000 })
    const heatDialog = page.locator('.el-dialog:visible').last()
    const newHeat = heatDialog.locator('.el-form-item:has-text("新热度") .el-input-number input').first()
    await newHeat.fill('90')
    await newHeat.press('Enter')
    await heatDialog.locator('.el-form-item:has-text("备注") input').first().fill('E2E 提热度')
    await heatDialog.locator('button:has-text("保存")').click()
    await page.waitForTimeout(1500)

    const heatRow = await page.locator('.acg-table .el-table__body tr').first().innerText()
    check(heatRow.includes('90'), '录入热度后列表立即显示 90', heatRow.replace(/\s+/g, ' ').slice(0, 60))

    /* ---------- 6. 分析页 ---------- */
    await page.locator('.el-tabs__item:has-text("热度与选品分析")').click()
    await page.waitForTimeout(1800)

    const canvasCount = await page.locator('.chart canvas').count()
    check(canvasCount >= 2, '四象限图与类型分布图已渲染', `canvas = ${canvasCount}`)

    const rankRows = await page.locator('.rank-table .el-table__body tr').count()
    check(rankRows >= 5, '潜力排行有数据', `${rankRows} 行`)

    const insightCount = await page.locator('.insights li').count()
    check(insightCount >= 3, '分析结论有文字输出', `${insightCount} 条`)

    const formulaText = await page.locator('.formula-body').innerText()
    check(formulaText.includes('潜力分') && formulaText.includes('45%'), '潜力分口径对使用者可见')

    await page.screenshot({ path: '/tmp/shot-acg-analysis.png' })

    /* ---------- 7. 趋势弹窗 ---------- */
    await page.locator('.el-tabs__item:has-text("作品目录及维护")').click()
    await page.waitForTimeout(500)
    await page.fill('.filter-bar input[placeholder="作品名 / 原名"]', 'E2E-ACG')
    await page.locator('button:has-text("查询")').click()
    await page.waitForTimeout(900)
    await page.locator('.acg-table .el-table__body tr').first().locator('button:has-text("趋势")').click()
    await page.waitForTimeout(1200)
    const trendCanvas = await page.locator('.trend-chart canvas').count()
    check(trendCanvas >= 1, '作品热度趋势弹窗已画图', `canvas = ${trendCanvas}`)
    await page.screenshot({ path: '/tmp/shot-acg-trend.png' })
    await page.keyboard.press('Escape')
    await page.waitForTimeout(400)

    /* ---------- 8. 删除 ---------- */
    await page.locator('.acg-table .el-table__body tr').first().locator('button:has-text("删除")').click()
    await page.waitForSelector('.el-message-box', { timeout: 5000 })
    await page.locator('.el-message-box__btns button:has-text("删除")').click()
    await page.waitForTimeout(1200)
    const afterDelete = await rowCount()
    check(afterDelete === 0, '删除后搜索结果为空', `实际 ${afterDelete} 行`)

    /* ---------- 9. 收尾 ---------- */
    check(pageErrors.length === 0, '页面无 JS 异常', pageErrors.join(' | ') || '无')
  } catch (e) {
    failed++
    console.error('✗ 执行中断：', e.message)
  } finally {
    // 兜底清理，保证下次可重复跑
    const cleaned = await cleanupByApi(await login())
    if (cleaned > 0) console.log(`· 兜底清理 ${cleaned} 条测试数据`)
    await browser.close()
  }

  console.log(failed === 0 ? '\n✅ 全部通过' : `\n❌ ${failed} 项失败`)
  process.exit(failed === 0 ? 0 : 1)
})()
