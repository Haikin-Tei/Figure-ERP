/**
 * 端到端验证：库存管理 → 仓库设置 → 货架管理 页面已接入后端。
 *
 * 覆盖「实时更改」：筛选走服务端 → 新增后列表立即出现 → 编辑后立即显示新值
 * → 状态改为维护中 → 删除后列表立即消失；每步都用接口回查确认真的落库。
 * 测试数据自建自清，结束后库里数量与开始时一致。
 */
const { chromium } = require('playwright-core')

const BASE = 'http://localhost:5173'
const API = 'http://localhost:8080'
const RUN = Date.now().toString().slice(-8)
const CODE = `SH-${RUN}`
const LOCATION = `E2E区-1层-${RUN}`
const LOCATION_EDITED = `E2E区-2层-${RUN}`

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
  const r = await fetch(`${API}/api/shelf/page?page=1&size=50&keyword=${encodeURIComponent(code)}`, {
    headers: { Authorization: `Bearer ${token}` }
  })
  const j = await r.json()
  return j?.data?.records?.find((x) => x.shelfCode === code) || null
}

async function totalRows(token) {
  const r = await fetch(`${API}/api/shelf/page?page=1&size=1`, {
    headers: { Authorization: `Bearer ${token}` }
  })
  const j = await r.json()
  return j?.data?.total ?? 0
}

;(async () => {
  const browser = await chromium.launch({ headless: true })
  const page = await browser.newPage({ viewport: { width: 1440, height: 900 } })
  const logs = []
  page.on('pageerror', (e) => logs.push(`[pageerror] ${e.message}`))

  let token = ''
  let initialTotal = 0
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
    initialTotal = await totalRows(token)
    console.log(`· 初始货架总数 = ${initialTotal}`)

    // 前端登录
    await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' })
    await page.fill('input[placeholder="登录账号"]', 'admin')
    await page.fill('input[placeholder="登录密码"]', 'admin123')
    await page.click('button.submit-btn')
    await page.waitForURL(/\/(home|dashboard)$/, { timeout: 10000 })

    await page.goto(`${BASE}/stock/shelves`, { waitUntil: 'networkidle' })
    await page.waitForSelector('.el-table__body tr', { timeout: 15000 })
    await page.waitForTimeout(600)

    // fixed 列会复制一份 tbody，取多份里最大的行数才是真实行数
    const rowCount = async () => {
      const counts = await page.locator('.el-table__body').evaluateAll((els) =>
        els.map((el) => el.querySelectorAll('tr').length)
      )
      return counts.length ? Math.max(...counts) : 0
    }

    check((await rowCount()) === initialTotal, `列表从后端加载到 ${initialTotal} 条货架`)
    check(
      (await page.locator('.el-table__body').first().innerText()).includes('3101'),
      '所属仓库代码由后端关联返回（3101 上海库）'
    )
    await page.screenshot({ path: '/tmp/shot-shelf-list.png' })

    // ---- 筛选：关键字（库位信息） ----
    await page.fill('.filter-bar input[placeholder="货架编号 / 库位信息"]', '01区')
    await page.click('.filter-bar button:has-text("查询")')
    await page.waitForTimeout(800)
    const kwText = await page.locator('.el-table__body').first().innerText()
    check(kwText.includes('01区') && !kwText.includes('02区'), '关键字匹配库位信息（01区 命中，02区 不命中）')

    // ---- 筛选：仓库 ----
    await page.click('.filter-bar button:has-text("重置")')
    await page.waitForTimeout(500)
    await page.locator('.filter-bar .el-select').first().click()
    await page.click('.el-select-dropdown:visible .el-select-dropdown__item:has-text("上海库 (3101)")')
    await page.waitForTimeout(800)
    const whText = await page.locator('.el-table__body').first().innerText()
    check(whText.includes('3101') && !whText.includes('3102'), '按仓库筛选生效（只有 3101 上海库）')

    await page.click('.filter-bar button:has-text("重置")')
    await page.waitForTimeout(600)

    // ---- 新增 ----
    await page.click('button:has-text("新增货架")')
    await page.waitForSelector('.el-dialog', { timeout: 5000 })
    await page.fill('.el-dialog .el-form-item:has-text("货架编号") input', CODE)
    await page.locator('.el-dialog .el-form-item:has-text("所属仓库") .el-select').click()
    await page.click('.el-select-dropdown:visible .el-select-dropdown__item:has-text("上海库 (3101)")')
    await page.fill('.el-dialog .el-form-item:has-text("库位信息") input', LOCATION)
    await page.fill('.el-dialog .el-form-item:has-text("状态描述") textarea', 'E2E 自动创建')
    await page.click('.el-dialog button:has-text("保存")')

    await page.waitForFunction(
      (code) => document.querySelector('.el-table__body')?.innerText.includes(code),
      CODE,
      { timeout: 10000 }
    )
    check((await rowCount()) === initialTotal + 1, '新增后列表立即多出一行（无需手动刷新）')

    const created = await findByCode(token, CODE)
    check(!!created && created.location === LOCATION, '新货架已真实落库（含所属仓库与库位信息）')
    check(created?.warehouseCode === '3101', '落库的所属仓库关联正确')

    // ---- 同仓库重号：应被后端拦下 ----
    await page.click('button:has-text("新增货架")')
    await page.waitForSelector('.el-dialog', { timeout: 5000 })
    await page.fill('.el-dialog .el-form-item:has-text("货架编号") input', CODE)
    await page.locator('.el-dialog .el-form-item:has-text("所属仓库") .el-select').click()
    await page.click('.el-select-dropdown:visible .el-select-dropdown__item:has-text("上海库 (3101)")')
    await page.fill('.el-dialog .el-form-item:has-text("库位信息") input', '重复号测试')
    await page.click('.el-dialog button:has-text("保存")')
    await page.waitForSelector('.el-message--error', { timeout: 6000 })
    const errText = await page.locator('.el-message--error').first().innerText()
    check(errText.includes('已存在'), `同仓库货架编号重复被拒绝（提示：${errText.trim()}）`)
    await page.click('.el-dialog button:has-text("取消")')

    // ---- 编辑：改库位信息 + 改成维护中 ----
    const row = page.locator('.el-table__body tr', { hasText: CODE }).first()
    await row.locator('button:has-text("编辑")').click()
    await page.waitForSelector('.el-dialog', { timeout: 5000 })
    await page.fill('.el-dialog .el-form-item:has-text("库位信息") input', LOCATION_EDITED)
    await page.locator('.el-dialog .el-form-item:has-text("当前状态") .el-select').click()
    await page.click('.el-select-dropdown:visible .el-select-dropdown__item:has-text("维护中")')
    await page.click('.el-dialog button:has-text("保存")')

    await page.waitForFunction(
      (loc) => document.querySelector('.el-table__body')?.innerText.includes(loc),
      LOCATION_EDITED,
      { timeout: 10000 }
    )
    check(true, '编辑后列表立即显示新库位信息')

    const edited = await findByCode(token, CODE)
    check(edited?.status === 'B' && edited?.statusText === '维护中', '状态改为维护中已落库，文案由后端翻译')

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
    check((await rowCount()) === initialTotal, `删除后列表立即恢复 ${initialTotal} 行`)
    check(!(await findByCode(token, CODE)), '删除已落库（逻辑删除，列表不再返回）')

    await page.screenshot({ path: '/tmp/shot-shelf-after.png' })
  } catch (e) {
    fails.push(e.message)
    console.error('✗ 执行异常:', e.message)
    if (logs.length) console.error(logs.join('\n'))
  } finally {
    // 兜底清理：万一中途失败留下测试数据
    try {
      const leftover = await findByCode(token, CODE)
      if (leftover) {
        await fetch(`${API}/api/shelf/${leftover.id}`, {
          method: 'DELETE',
          headers: { Authorization: `Bearer ${token}` }
        })
        console.log(`· 已清理残留测试数据 id=${leftover.id}`)
      }
      const finalTotal = await totalRows(token)
      if (finalTotal !== initialTotal) {
        console.error(`✗ 数据未还原：初始 ${initialTotal} 条，现在 ${finalTotal} 条`)
        fails.push('数据未还原')
      }
    } catch {
      /* 忽略清理异常 */
    }
    await browser.close()
  }

  console.log(fails.length === 0 ? '\n✓ 全部通过（货架数据全部走后端，增删改实时生效）' : `\n✗ 失败 ${fails.length} 项`)
  process.exitCode = fails.length === 0 ? 0 : 1
})()
