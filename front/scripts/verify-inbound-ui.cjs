/**
 * 端到端验证：库存管理 → 入库记录 已接入后端。
 *
 * 覆盖：统计卡 → 列表 → 状态筛选 → 关键字筛选 → 新增（含两行手办明细、自动合计）
 * → 编辑（改数量、改供应商）→ 详情抽屉 → 确认入库 → 作废 → 删除。
 * 每一步都用接口回查确认真的落库。测试数据自建，单据由 SQL 清理（见脚本末尾提示）。
 */
const { chromium } = require('playwright-core')

const CHROME = '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome'
const BASE = 'http://localhost:5173'
const API = 'http://localhost:8080'
const RUN = Date.now().toString().slice(-6)
const SUPPLIER = `E2E入库_${RUN}`
const SUPPLIER_EDITED = `E2E入库_${RUN}_改`
const DELETE_SUPPLIER = `E2E删除_${RUN}`

async function loginApi() {
  const r = await fetch(`${API}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: 'admin', password: 'admin123' })
  })
  const j = await r.json()
  return j?.data?.token
}

async function findOrder(token, supplier) {
  const r = await fetch(`${API}/api/inbound/page?page=1&size=20&keyword=${encodeURIComponent(supplier)}`, {
    headers: { Authorization: `Bearer ${token}` }
  })
  const j = await r.json()
  return j?.data?.records?.find((x) => x.supplier === supplier) || null
}

async function apiCall(method, path, token, body) {
  const r = await fetch(API + path, {
    method,
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    ...(body !== undefined ? { body: JSON.stringify(body) } : {})
  })
  return r.json()
}

async function pickOption(page, selectLocator, text) {
  await selectLocator.click()
  await page.waitForTimeout(350)
  const option = page.locator('.el-select-dropdown:visible .el-select-dropdown__item', { hasText: text }).first()
  await option.click()
  await page.waitForTimeout(300)
}

/** 填 el-input-number 并让它失焦提交值 */
async function fillNumber(locator, value) {
  const input = locator.locator('input')
  await input.click()
  await input.fill(String(value))
  await input.press('Tab')
  await new Promise((r) => setTimeout(r, 200))
}

;(async () => {
  const browser = await chromium.launch({ executablePath: CHROME, headless: true })
  const page = await browser.newPage({ viewport: { width: 1560, height: 950 } })
  const logs = []
  page.on('pageerror', (e) => logs.push(`[pageerror] ${e.message}`))
  page.on('console', (m) => {
    if (m.type() === 'error') logs.push(`[console] ${m.text()}`)
  })

  let token = ''
  const fails = []
  const check = (ok, msg, extra = '') => {
    if (ok) console.log(`✓ ${msg}`)
    else {
      fails.push(msg)
      console.error(`✗ ${msg}${extra ? ' → ' + extra : ''}`)
    }
  }

  try {
    token = await loginApi()
    if (!token) throw new Error('接口登录失败')

    const products = await apiCall('GET', '/api/product/page?page=1&size=5', token)
    const plist = products?.data?.records || []
    if (plist.length < 2) throw new Error('手办不足 2 款，无法验证明细')
    const p1 = plist[0]
    const p2 = plist[1]

    // ---- 登录 ----
    await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' })
    await page.fill('input[placeholder="登录账号"]', 'admin')
    await page.fill('input[placeholder="登录密码"]', 'admin123')
    await page.click('button.submit-btn')
    await page.waitForURL(/\/(home|dashboard)$/, { timeout: 10000 })

    // ---- 入库记录页 ----
    await page.goto(`${BASE}/stock/inbound`, { waitUntil: 'networkidle' })
    await page.waitForSelector('.base-table .el-table__body tr', { timeout: 15000 })
    await page.waitForTimeout(800)

    const rowSel = '.base-table .el-table__body tr'
    const rowCount0 = await page.locator(rowSel).count()
    check(rowCount0 === 4, `列表加载到 4 条演示单据（实际 ${rowCount0}）`)

    const tableText = await page.locator('.base-table').innerText()
    check(/RK\d{12}/.test(tableText), '列表显示入库单号')
    check(tableText.includes('采购入库') && tableText.includes('已入库'), '列表显示类型与状态文案')
    check(tableText.includes('上海库'), '列表显示仓库名称')

    // ---- 统计卡 ----
    const stat0 = await page.locator('.stat-card').nth(0).innerText()
    const stat1 = await page.locator('.stat-card').nth(1).innerText()
    const stat2 = await page.locator('.stat-card').nth(2).innerText()
    check(stat0.includes('待入库单') && /\b1\b/.test(stat0), `统计卡：待入库 1 单（${stat0.replace(/\n/g, ' ')}）`)
    check(stat1.includes('本月入库单') && /\b2\b/.test(stat1), `统计卡：本月入库 2 单（${stat1.replace(/\n/g, ' ')}）`)
    check(stat2.includes('本月入库件数') && stat2.includes('27'), `统计卡：本月 27 件（${stat2.replace(/\n/g, ' ')}）`)

    // ---- 状态筛选 ----
    await pickOption(page, page.locator('.filter-bar .el-select').nth(2), '已入库')
    await page.waitForTimeout(900)
    const confirmedRows = await page.locator(rowSel).count()
    check(confirmedRows === 3, `按状态筛选「已入库」得到 3 条（实际 ${confirmedRows}）`)

    await page.click('.filter-bar button:has-text("重置")')
    await page.waitForTimeout(900)
    const resetRows = await page.locator(rowSel).count()
    check(resetRows === 4, `重置后恢复 4 条（实际 ${resetRows}）`)

    // ---- 新增入库单 ----
    await page.click('.filter-bar button:has-text("新增入库单")')
    await page.waitForSelector('.el-dialog', { state: 'visible', timeout: 8000 })
    await page.waitForTimeout(500)

    const dialog = page.locator('.el-dialog').last()
    const formSelects = dialog.locator('.el-form .el-select')
    // 0 入库类型 / 1 入库仓库 / 2 入库区域 / 3 入库货架
    await pickOption(page, formSelects.nth(1), '上海库')
    await page.waitForTimeout(400)
    await pickOption(page, formSelects.nth(2), 'A区')
    await page.fill('input[placeholder="如 Good Smile Company 中国代理"]', SUPPLIER)
    await page.fill('input[placeholder="选填"]', `${SUPPLIER} 备注`)

    // 第一行明细
    await pickOption(page, dialog.locator('.items-table .el-select').nth(0), p1.name)
    const numbers1 = dialog.locator('.items-table .el-input-number')
    await fillNumber(numbers1.nth(0), 3)
    await fillNumber(numbers1.nth(1), 100)

    // 添加第二行
    await page.click('.items-head button:has-text("添加手办")')
    await page.waitForTimeout(400)
    await pickOption(page, dialog.locator('.items-table .el-select').nth(1), p2.name)
    const numbers2 = dialog.locator('.items-table .el-input-number')
    await fillNumber(numbers2.nth(2), 2)
    await fillNumber(numbers2.nth(3), 50)

    const totalText = await page.locator('.items-total').innerText()
    check(totalText.includes('2') && totalText.includes('5') && totalText.includes('400.00'), `表单合计实时计算（${totalText.replace(/\s+/g, ' ')}）`)

    await page.click('.el-dialog__footer button:has-text("保存")')
    await page.waitForTimeout(1600)

    const created = await findOrder(token, SUPPLIER)
    check(!!created, '新增后接口能查到该单据')
    check(created?.status === 'D', '新增单据状态为待入库', created?.status)
    check(created?.totalQuantity === 5, `数量合计 = 5（实际 ${created?.totalQuantity}）`)
    check(Number(created?.totalAmount) === 400, `金额合计 = 400（实际 ${created?.totalAmount}）`)
    check(created?.areaName?.includes('A区'), '区域关联成功', created?.areaName)

    const detail = await apiCall('GET', `/api/inbound/${created.id}`, token)
    check(detail?.data?.items?.length === 2, '明细 2 行已落库')
    check(detail?.data?.operator === '系统管理员', '经办人默认取当前登录用户', detail?.data?.operator)

    // 列表里按供应商搜索到刚建的单
    await page.fill('input[placeholder="单号 / 供应商 / 手办名称"]', SUPPLIER)
    await page.keyboard.press('Enter')
    await page.waitForTimeout(900)
    const searchedRows = await page.locator(rowSel).count()
    check(searchedRows === 1, `按供应商搜索命中 1 条（实际 ${searchedRows}）`)
    const listText = await page.locator(rowSel).first().innerText()
    check(listText.includes('待入库'), '列表状态显示待入库')
    check(listText.includes('5') && listText.includes('400.00'), `列表显示数量与金额（${listText.replace(/\s+/g, ' ')}）`)

    // ---- 编辑 ----
    await page.click(`${rowSel} button:has-text("编辑")`)
    await page.waitForSelector('.el-dialog', { state: 'visible', timeout: 8000 })
    await page.waitForTimeout(1200)
    const editDialog = page.locator('.el-dialog').last()
    await editDialog.locator('input[placeholder="如 Good Smile Company 中国代理"]').fill(SUPPLIER_EDITED)
    await fillNumber(editDialog.locator('.items-table .el-input-number').nth(0), 4)
    await page.click('.el-dialog__footer button:has-text("保存")')
    await page.waitForTimeout(1600)

    const edited = await findOrder(token, SUPPLIER_EDITED)
    check(!!edited, '编辑后接口能查到新供应商')
    check(edited?.totalQuantity === 6, `编辑后数量 = 6（实际 ${edited?.totalQuantity}）`)
    check(Number(edited?.totalAmount) === 500, `编辑后金额 = 500（实际 ${edited?.totalAmount}）`)

    // ---- 详情抽屉 ----
    await page.fill('input[placeholder="单号 / 供应商 / 手办名称"]', SUPPLIER_EDITED)
    await page.keyboard.press('Enter')
    await page.waitForTimeout(900)
    await page.click(`${rowSel} button:has-text("详情")`)
    // 页面上还有导航栏的「个人信息」抽屉（隐藏状态），用 :visible 只取当前打开的那个
    await page.waitForSelector('.el-drawer:visible', { timeout: 8000 })
    await page.waitForTimeout(900)
    const drawerText = await page.locator('.el-drawer:visible').innerText()
    check(drawerText.includes('入库单详情'), '详情抽屉打开')
    check(drawerText.includes(p1.name) && drawerText.includes(p2.name), '详情展示两行手办名称')
    check(drawerText.includes('500.00'), '详情显示金额合计 500.00')
    await page.keyboard.press('Escape')
    await page.waitForTimeout(700)

    // ---- 确认入库（收在「更多」下拉里） ----
    await page.click(`${rowSel} button:has-text("更多")`)
    await page.waitForTimeout(400)
    await page.click('.el-dropdown-menu:visible .el-dropdown-menu__item:has-text("确认入库")')
    await page.waitForSelector('.el-message-box', { state: 'visible', timeout: 8000 })
    await page.click('.el-message-box button:has-text("确认入库")')
    await page.waitForTimeout(1500)
    const confirmed = await apiCall('GET', `/api/inbound/${edited.id}`, token)
    check(confirmed?.data?.status === 'C', '确认后状态变为已入库', confirmed?.data?.status)

    await page.fill('input[placeholder="单号 / 供应商 / 手办名称"]', SUPPLIER_EDITED)
    await page.keyboard.press('Enter')
    await page.waitForTimeout(900)
    const confirmedRowText = await page.locator(rowSel).first().innerText()
    check(confirmedRowText.includes('已入库'), '列表状态同步为已入库')
    check(
      (await page.locator(`${rowSel} button:has-text("编辑")`).count()) === 0,
      '已入库单据不再显示编辑按钮'
    )

    // ---- 作废 ----
    await page.click(`${rowSel} button:has-text("更多")`)
    await page.waitForTimeout(400)
    await page.click('.el-dropdown-menu:visible .el-dropdown-menu__item:has-text("作废")')
    await page.waitForSelector('.el-message-box', { state: 'visible', timeout: 8000 })
    await page.click('.el-message-box button:has-text("作废")')
    await page.waitForTimeout(1500)
    const cancelled = await apiCall('GET', `/api/inbound/${edited.id}`, token)
    check(cancelled?.data?.status === 'X', '作废后状态变为已作废', cancelled?.data?.status)

    await page.fill('input[placeholder="单号 / 供应商 / 手办名称"]', SUPPLIER_EDITED)
    await page.keyboard.press('Enter')
    await page.waitForTimeout(900)
    const cancelledRowText = await page.locator(rowSel).first().innerText()
    check(cancelledRowText.includes('已作废'), '列表状态同步为已作废')

    // ---- 删除（接口建一张草稿，再走页面删除） ----
    const draft = await apiCall('POST', '/api/inbound', token, {
      inboundType: 'O',
      warehouseId: 1,
      supplier: DELETE_SUPPLIER,
      items: [{ productId: p1.id, quantity: 1, unitPrice: 10 }]
    })
    check(draft?.code === 200, '接口创建待删除草稿单')

    await page.fill('input[placeholder="单号 / 供应商 / 手办名称"]', DELETE_SUPPLIER)
    await page.keyboard.press('Enter')
    await page.waitForTimeout(900)
    await page.click(`${rowSel} button:has-text("更多")`)
    await page.waitForTimeout(400)
    await page.click('.el-dropdown-menu:visible .el-dropdown-menu__item:has-text("删除")')
    await page.waitForSelector('.el-message-box', { state: 'visible', timeout: 8000 })
    await page.click('.el-message-box button:has-text("删除")')
    await page.waitForTimeout(1500)

    const afterDelete = await apiCall('GET', `/api/inbound/${draft.data}`, token)
    check(afterDelete?.code === 1001, '页面删除后接口查不到该单（1001）')

    check(logs.length === 0, '页面无 JS 报错', logs.slice(0, 3).join(' | '))
  } catch (e) {
    console.error('脚本异常:', e.message)
    fails.push(e.message)
  } finally {
    await browser.close()
  }

  console.log('\n' + (fails.length === 0 ? '全部通过' : `失败 ${fails.length} 项`))
  console.log(`清理 SQL：DELETE FROM inbound_item WHERE inbound_id IN (SELECT id FROM inbound_order WHERE supplier LIKE 'E2E%_${RUN}%');`)
  console.log(`         DELETE FROM inbound_order WHERE supplier LIKE 'E2E%_${RUN}%';`)
  process.exit(fails.length === 0 ? 0 : 1)
})()
