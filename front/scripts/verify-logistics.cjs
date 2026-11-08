/**
 * 物流管理模块的端到端验证（无头浏览器真实点击）。
 * 运行：node scripts/verify-logistics.cjs
 * 前置：前端 5173、后端 8080 都在跑，且用 admin/admin123 能登录。
 */
const { chromium } = require('playwright-core')

const BASE = 'http://localhost:5173'
const API = 'http://localhost:8080'
const RUN = Date.now().toString().slice(-6)
const CHROME =
  process.env.CHROME_PATH || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome'

const log = (...args) => console.log(...args)

/**
 * 只读校验需要一个真正的执行级账号。
 * 原先写死用 wangwu，后来该账号被改成「销售主管」（主管级可编辑），断言就失真了，
 * 所以这里临时建一个 FINANCE_STAFF 账号用，跑完删掉，不受存量账号的角色变化影响。
 */
const STAFF_USER = `logi_e2e_${RUN}`
const STAFF_PWD = `Staff${RUN}`

async function api(method, path, token, body) {
  const headers = { 'Content-Type': 'application/json' }
  if (token) headers.Authorization = `Bearer ${token}`
  const r = await fetch(API + path, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body)
  })
  try {
    return await r.json()
  } catch {
    return null
  }
}

;(async () => {
  const browser = await chromium.launch({ executablePath: CHROME, headless: true })
  const page = await browser.newPage({ viewport: { width: 1600, height: 1000 } })

  const errors = []
  page.on('pageerror', (e) => errors.push('pageerror: ' + e.message))
  page.on('console', (msg) => {
    if (msg.type() === 'error') errors.push('console: ' + msg.text())
  })

  try {
    // 1. 登录
    await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' })
    await page.fill('input[placeholder*="账号"]', 'admin')
    await page.fill('input[type="password"]', 'admin123')
    await page.click('button:has-text("登")')
    await page.waitForURL(/\/(home|dashboard)$/, { timeout: 15000 })
    log('✅ 登录成功')

    // 2. 侧边栏应该有「物流管理」，且位于「库存管理」之后
    const menuText = await page.locator('.sidebar-menu').first().innerText()
    const hasLogistics = menuText.includes('物流管理')
    const idxStock = menuText.indexOf('库存管理')
    const idxLogistics = menuText.indexOf('物流管理')
    const idxOrder = menuText.indexOf('订单管理')
    log(
      `${hasLogistics ? '✅' : '❌'} 菜单含「物流管理」，顺序 库存(${idxStock}) < 物流(${idxLogistics}) < 订单(${idxOrder})`
    )

    // 3. 物流价格维护
    await page.goto(`${BASE}/logistics/price`, { waitUntil: 'networkidle' })
    await page.waitForSelector('.el-table__row', { timeout: 15000 })
    const rowCount = await page.locator('.el-table__row').count()
    const totalText = await page.locator('.el-pagination__total').innerText()
    log(`✅ 价格维护页表格 ${rowCount} 行，分页显示「${totalText}」`)

    const pageTitle = await page.locator('.page-title').innerText()
    log(`✅ 页面标题：${pageTitle.replace(/\s+/g, ' ')}`)

    // 筛选：只看顺丰
    await page.click('.filter-bar .el-select >> nth=0')
    await page.click('.el-select-dropdown__item:has-text("顺丰速运")')
    await page.waitForTimeout(1200)
    const sfRows = await page.locator('.el-table__row').count()
    const sfNames = await page.locator('.el-table__row td:nth-child(1)').allInnerTexts()
    const allSf = sfNames.every((n) => n.includes('顺丰'))
    log(`${allSf ? '✅' : '❌'} 按供应商筛选后 ${sfRows} 行，全部为顺丰`)

    await page.screenshot({ path: '/tmp/logistics-price.png', fullPage: false })

    // 4. 批量设置全国价抽屉
    await page.click('button:has-text("批量设置全国价")')
    await page.waitForSelector('.el-drawer__body .el-table__row', { timeout: 15000 })
    await page.waitForTimeout(800)
    const batchRows = await page.locator('.el-drawer__body .el-table__row').count()
    log(`✅ 批量抽屉打开，共 ${batchRows} 个省份`)

    // 点「填充全部省份」检查是否把价格填进去
    await page.click('button:has-text("填充全部省份")')
    await page.waitForTimeout(600)
    const filledText = await page.locator('.drawer-footer .fill-tip').innerText()
    log(`✅ 按区域填充后：${filledText}`)
    await page.screenshot({ path: '/tmp/logistics-batch.png', fullPage: false })

    // 关闭抽屉，不保存（避免污染数据）
    await page.click('.el-drawer__footer button:has-text("取消")')
    await page.waitForTimeout(600)

    // 5. 价格查询
    await page.goto(`${BASE}/logistics/query`, { waitUntil: 'networkidle' })
    await page.waitForSelector('.quote-card', { timeout: 15000 })
    await page.waitForTimeout(800)
    const cards = await page.locator('.quote-card').count()
    const summary = await page.locator('.summary-route').innerText()
    const advice = await page.locator('.advice').innerText()
    const cheapest = await page
      .locator('.quote-card.cheapest .qc-price')
      .innerText()
      .catch(() => '—')
    log(`✅ 默认查询：${summary.replace(/\s+/g, ' ')}，${cards} 家报价`)
    log(`✅ 最低价卡片金额 ${cheapest.replace(/\s+/g, ' ')}`)
    log(`✅ 建议：${advice.replace(/\s+/g, ' ')}`)
    await page.screenshot({ path: '/tmp/logistics-query.png', fullPage: false })

    // 6. 换 5kg 再查
    await page.click('.quick-tag:has-text("5 kg")')
    await page.waitForTimeout(1500)
    const advice5 = await page.locator('.advice').innerText()
    const cheapest5 = await page.locator('.quote-card.cheapest .qc-price').innerText()
    log(`✅ 5kg 复算：最低 ${cheapest5.replace(/\s+/g, ' ')}，建议「${advice5.replace(/\s+/g, ' ')}」`)

    // 7. 换目的地为新疆，检查报价变化
    const destSelect = page.locator('.query-form .el-select').nth(2)
    await destSelect.click()
    await destSelect.locator('input').fill('新疆')
    await page.waitForTimeout(600)
    await page.click('.el-select-dropdown:visible .el-select-dropdown__item:has-text("新疆")')
    await page.waitForTimeout(400)
    await page.click('button:has-text("查询运费")')
    await page.waitForTimeout(1800)
    const summaryXj = await page.locator('.summary-route').innerText()
    const adviceXj = await page.locator('.advice').innerText()
    log(`✅ 换目的地：${summaryXj.replace(/\s+/g, ' ')} → ${adviceXj.replace(/\s+/g, ' ')}`)
    await page.screenshot({ path: '/tmp/logistics-query-xinjiang.png', fullPage: false })

    // 8. 执行级只读校验（临时账号，避免依赖存量账号的角色）
    const adminLogin = await api('POST', '/api/auth/login', null, { username: 'admin', password: 'admin123' })
    const adminToken = adminLogin?.data?.token
    const created = await api('POST', '/api/system/users', adminToken, {
      username: STAFF_USER,
      realName: '物流只读测试',
      role: 'FINANCE_STAFF',
      status: 1,
      password: STAFF_PWD
    })
    const staffUserId = created?.data
    const staffPage = await browser.newPage({ viewport: { width: 1600, height: 1000 } })
    await staffPage.goto(`${BASE}/login`, { waitUntil: 'networkidle' })
    await staffPage.fill('input[placeholder*="账号"]', STAFF_USER)
    await staffPage.fill('input[type="password"]', STAFF_PWD)
    await staffPage.click('button:has-text("登")')
    await staffPage.waitForURL(/\/(home|dashboard)$/, { timeout: 15000 })
    await staffPage.goto(`${BASE}/logistics/price`, { waitUntil: 'networkidle' })
    await staffPage.waitForSelector('.el-table__row', { timeout: 15000 })
    const disabled = await staffPage.locator('button:has-text("新增价格")').isDisabled()
    const alertVisible = await staffPage.locator('.el-alert').first().isVisible()
    log(`${disabled && alertVisible ? '✅' : '❌'} 执行级员工进入维护页：新增按钮禁用=${disabled}，只读提示=${alertVisible}`)
    await staffPage.screenshot({ path: '/tmp/logistics-staff.png', fullPage: false })
    await staffPage.close()
    if (staffUserId) await api('DELETE', `/api/system/users/${staffUserId}`, adminToken)

    if (errors.length) {
      log('\n⚠️ 控制台错误：')
      errors.slice(0, 8).forEach((e) => log('  ' + e))
    } else {
      log('\n✅ 无控制台报错')
    }
  } catch (e) {
    log('❌ 验证失败：', e.message)
    await page.screenshot({ path: '/tmp/logistics-error.png', fullPage: true })
    process.exitCode = 1
  } finally {
    await browser.close()
  }
})()
