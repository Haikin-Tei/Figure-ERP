/**
 * 端到端验证：库存管理 → 仓库设置 → 库位管理 页面已接入后端。
 *
 * 覆盖「实时更改」：新增 → 列表立即出现；编辑 → 列表立即显示新值；
 * 删除 → 列表立即消失。全部通过接口落库，最后清理测试数据。
 */
const { chromium } = require('playwright-core')

const BASE = 'http://localhost:5173'
const API = 'http://localhost:8080'
const RUN = Date.now().toString().slice(-8)
const CODE = `WMS-${RUN}`
const NAME = `E2E测试仓_${RUN}`
const NAME_EDITED = `E2E测试仓_${RUN}_改名`

async function loginApi() {
  const r = await fetch(`${API}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: 'admin', password: 'admin123' })
  })
  const j = await r.json()
  return j?.data?.token
}

async function findByCode(token, code) {
  const r = await fetch(`${API}/api/warehouse/page?page=1&size=50&warehouseCode=${encodeURIComponent(code)}`, {
    headers: { Authorization: `Bearer ${token}` }
  })
  const j = await r.json()
  return j?.data?.records?.[0] || null
}

;(async () => {
  const browser = await chromium.launch({ headless: true })
  const page = await browser.newPage({ viewport: { width: 1440, height: 900 } })
  const logs = []
  page.on('pageerror', (e) => logs.push(`[pageerror] ${e.message}`))

  let token = ''
  const fails = []
  const check = (ok, msg) => {
    if (ok) console.log(`✓ ${msg}`)
    else {
      fails.push(msg)
      console.error(`✗ ${msg}`)
    }
  }

  try {
    token = await loginApi()
    if (!token) throw new Error('接口登录失败')

    // 前端登录
    await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' })
    await page.fill('input[placeholder="登录账号"]', 'admin')
    await page.fill('input[placeholder="登录密码"]', 'admin123')
    await page.click('button.submit-btn')
    await page.waitForURL(/\/(home|dashboard)$/, { timeout: 10000 })

    await page.goto(`${BASE}/stock/location`, { waitUntil: 'networkidle' })
    await page.waitForSelector('.el-table__body tr', { timeout: 15000 })
    await page.waitForTimeout(600)

    const rowCount0 = await page.locator('.el-table__body tr').count()
    console.log(`· 初始表格行数 = ${rowCount0}`)
    check(rowCount0 === 12, '列表从后端加载到 12 条仓库')
    await page.screenshot({ path: '/tmp/shot-warehouse-list.png' })

    // ---- 筛选：仓库类型 = 退货仓库（初始数据里只有 3102 是退货仓）----
    await page.locator('.filter-bar .el-select').first().click()
    await page.click('.el-select-dropdown:visible .el-select-dropdown__item:has-text("退货仓库")')
    await page.click('.filter-bar button:has-text("查询")')
    await page.waitForFunction(
      () => document.querySelectorAll('.el-table__body tr').length === 1,
      null,
      { timeout: 8000 }
    ).catch(() => {})
    const returnRows = await page.locator('.el-table__body tr').count()
    check(returnRows === 1, `筛选「退货仓库」后只剩 1 条（实际 ${returnRows}）`)

    // ---- 重置回全部 ----
    await page.click('.filter-bar button:has-text("重置")')
    await page.waitForFunction(
      () => document.querySelectorAll('.el-table__body tr').length === 12,
      null,
      { timeout: 8000 }
    ).catch(() => {})
    const resetRows = await page.locator('.el-table__body tr').count()
    check(resetRows === 12, `重置后恢复 12 条（实际 ${resetRows}）`)

    // ---- 新增 ----
    await page.click('button:has-text("新增仓库")')
    await page.waitForSelector('.el-dialog', { timeout: 5000 })
    await page.fill('.el-dialog .el-form-item:has-text("仓库代码") input', CODE)
    await page.fill('.el-dialog .el-form-item:has-text("仓库名称") input', NAME)
    await page.fill('.el-dialog .el-form-item:has-text("仓库地址") input', 'E2E 测试地址 100 号')
    await page.click('.el-dialog button:has-text("保存")')

    await page.waitForFunction(
      (code) => document.querySelector('.el-table__body')?.innerText.includes(code),
      CODE,
      { timeout: 10000 }
    )
    const rowCount1 = await page.locator('.el-table__body tr').count()
    check(rowCount1 === 13, '新增后列表立即多出一行（无需手动刷新）')

    const dbCreated = await findByCode(token, CODE)
    check(!!dbCreated && dbCreated.warehouseName === NAME, '新仓库已真实落库')

    // ---- 编辑 ----
    const row = page.locator('.el-table__body tr', { hasText: CODE }).first()
    await row.locator('button:has-text("编辑")').click()
    await page.waitForSelector('.el-dialog', { timeout: 5000 })
    const nameInput = page.locator('.el-dialog .el-form-item:has-text("仓库名称") input')
    await nameInput.fill(NAME_EDITED)
    await page.click('.el-dialog button:has-text("保存")')

    await page.waitForFunction(
      (name) => document.querySelector('.el-table__body')?.innerText.includes(name),
      NAME_EDITED,
      { timeout: 10000 }
    )
    check(true, '编辑后列表立即显示新名称')

    const dbEdited = await findByCode(token, CODE)
    check(dbEdited?.warehouseName === NAME_EDITED, '编辑结果已真实落库')

    // ---- 删除 ----
    const row2 = page.locator('.el-table__body tr', { hasText: CODE }).first()
    await row2.locator('button:has-text("删除")').click()
    await page.waitForSelector('.el-message-box', { timeout: 5000 })
    await page.click('.el-message-box button:has-text("删除")')

    await page.waitForFunction(
      (code) => !document.querySelector('.el-table__body')?.innerText.includes(code),
      CODE,
      { timeout: 10000 }
    )
    const rowCount2 = await page.locator('.el-table__body tr').count()
    check(rowCount2 === 12, '删除后列表立即恢复 12 行')

    const dbAfterDelete = await findByCode(token, CODE)
    const allPage = await (
      await fetch(`${API}/api/warehouse/page?page=1&size=50`, { headers: { Authorization: `Bearer ${token}` } })
    ).json()
    const stillListed = allPage?.data?.records?.some((r) => r.warehouseCode === CODE)
    check(!stillListed && !dbAfterDelete, '删除已落库（逻辑删除，列表不再返回）')

    await page.screenshot({ path: '/tmp/shot-warehouse-after.png' })
  } catch (e) {
    fails.push(e.message)
    console.error('✗ 执行异常:', e.message)
    if (logs.length) console.error(logs.join('\n'))
  } finally {
    // 兜底清理：万一中途失败留下测试数据
    try {
      const leftover = await findByCode(token, CODE)
      if (leftover) {
        await fetch(`${API}/api/warehouse/${leftover.id}`, {
          method: 'DELETE',
          headers: { Authorization: `Bearer ${token}` }
        })
        console.log(`· 已清理残留测试数据 id=${leftover.id}`)
      }
    } catch {
      /* 忽略清理异常 */
    }
    await browser.close()
  }

  console.log(fails.length === 0 ? '\n✓ 全部通过（新增/编辑/删除均实时反映并落库）' : `\n✗ 失败 ${fails.length} 项`)
  process.exitCode = fails.length === 0 ? 0 : 1
})()
