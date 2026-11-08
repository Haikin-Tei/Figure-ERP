/**
 * 端到端验证：员工权限管理 → 员工维护 的「角色必选 + 落库」。
 *
 * 覆盖：
 *  1. 新增员工时不选角色 → 表单拦住（请选择角色），不会提交；
 *  2. 角色下拉按板块分组（系统 / 管理层 / 财务 / 销售 / 物流 / 人事 / 通用），12 个角色都在；
 *  3. 选「财务主管」保存 → 列表出现、标签正确、库里 role=FINANCE_STAFF、roleLevel=2；
 *  4. 编辑改成「销售专员」→ 标签与库里同步变化；
 *  5. 角色筛选生效；
 *  6. 结束时删掉测试账号（测试数据自建自清）。
 */
const { chromium } = require('playwright-core')

const BASE = 'http://localhost:5173'
const API = 'http://localhost:8080'
const RUN = Date.now().toString().slice(-8)
const USERNAME = `role_e2e_${RUN}`
const REAL_NAME = `角色测试_${RUN}`

const ROLE_GROUPS = ['系统', '管理层', '财务', '销售', '物流', '人事', '通用']

async function loginApi(username = 'admin', password = 'admin123') {
  const r = await fetch(`${API}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password })
  })
  const j = await r.json()
  return j?.data?.token
}

async function findByUsername(token, username) {
  const r = await fetch(
    `${API}/api/system/users?page=1&size=20&keyword=${encodeURIComponent(username)}`,
    { headers: { Authorization: `Bearer ${token}` } }
  )
  const j = await r.json()
  return j?.data?.records?.find((u) => u.username === username) || null
}

;(async () => {
  const browser = await chromium.launch({ headless: true })
  const page = await browser.newPage({ viewport: { width: 1440, height: 900 } })
  const logs = []
  page.on('pageerror', (e) => logs.push(`[pageerror] ${e.message}`))

  let token = ''
  let createdId = null
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

    await page.goto(`${BASE}/staff/maintain`, { waitUntil: 'networkidle' })
    await page.waitForSelector('.el-table__body tr', { timeout: 15000 })
    await page.waitForTimeout(500)

    // ---- 1. 新增弹窗：角色默认空、必选 ----
    await page.click('button:has-text("新增员工")')
    await page.waitForSelector('.el-dialog', { timeout: 5000 })
    await page.fill('.el-dialog .el-form-item:has-text("登录账号") input', USERNAME)
    await page.fill('.el-dialog .el-form-item:has-text("员工姓名") input', REAL_NAME)
    await page.fill('.el-dialog .el-form-item:has-text("初始密码") input', 'admin123')

    const roleSelectPlaceholder = await page
      .locator('.el-dialog .el-form-item:has-text("角色") .el-select__placeholder')
      .first()
      .innerText()
    check(roleSelectPlaceholder.includes('请选择角色'), '角色下拉默认空，提示「请选择角色（必填）」')

    await page.click('.el-dialog button:has-text("保存")')
    await page.waitForSelector('.el-dialog .el-form-item__error', { timeout: 5000 })
    const roleError = await page
      .locator('.el-dialog .el-form-item:has-text("角色") .el-form-item__error')
      .innerText()
    check(roleError.includes('请选择角色'), `不选角色被表单拦下（提示：${roleError.trim()}）`)
    check(!(await findByUsername(token, USERNAME)), '未选角色时不会提交到后端')

    // ---- 2. 角色下拉按板块分组 ----
    await page.locator('.el-dialog .el-form-item:has-text("角色") .el-select').click()
    await page.waitForSelector('.el-select-dropdown:visible .el-select-group__title', { timeout: 5000 })
    const groupTitles = await page
      .locator('.el-select-dropdown:visible .el-select-group__title')
      .allInnerTexts()
    const missingGroups = ROLE_GROUPS.filter((g) => !groupTitles.some((t) => t.includes(g)))
    check(missingGroups.length === 0, `角色下拉按板块分组（${groupTitles.join(' / ')}）`)

    const optionCount = await page.locator('.el-select-dropdown:visible .el-select-dropdown__item').count()
    check(optionCount === 12, `共 12 个角色可选（实际 ${optionCount}）`)

    // ---- 3. 选中财务主管并保存 ----
    await page.click('.el-select-dropdown:visible .el-select-dropdown__item:has-text("财务主管")')
    await page.click('.el-dialog button:has-text("保存")')
    await page.waitForSelector('.el-dialog', { state: 'hidden', timeout: 10000 })

    await page.waitForFunction(
      (name) => document.querySelector('.el-table__body')?.innerText.includes(name),
      REAL_NAME,
      { timeout: 10000 }
    )
    check(true, '新增后列表立即出现该员工')

    const saved = await findByUsername(token, USERNAME)
    createdId = saved?.id ?? null
    check(saved?.role === 'FINANCE_MANAGER', '角色已落库：role=FINANCE_MANAGER')
    check(saved?.roleLabel === '财务主管', '后端返回角色文案：财务主管')
    check(saved?.roleLevel === 2, '权限层级正确：主管级 2')
    check(saved?.roleGroup === '财务', '角色板块正确：财务')

    const rowText = await page.locator('.el-table__body tr', { hasText: REAL_NAME }).first().innerText()
    check(rowText.includes('财务主管'), '列表角色列显示「财务主管」')

    await page.screenshot({ path: '/tmp/shot-staff-role.png' })

    // ---- 4. 编辑：改成销售专员 ----
    const row = page.locator('.el-table__body tr', { hasText: REAL_NAME }).first()
    await row.locator('button:has-text("编辑")').click()
    await page.waitForSelector('.el-dialog', { timeout: 5000 })
    await page.locator('.el-dialog .el-form-item:has-text("角色") .el-select').click()
    await page.click('.el-select-dropdown:visible .el-select-dropdown__item:has-text("销售专员")')
    await page.click('.el-dialog button:has-text("保存")')
    await page.waitForSelector('.el-dialog', { state: 'hidden', timeout: 10000 })
    await page.waitForTimeout(600)

    const edited = await findByUsername(token, USERNAME)
    check(edited?.role === 'SALES_STAFF' && edited?.roleLevel === 1, '编辑后角色改为 SALES_STAFF（执行级 1）')

    // ---- 5. 角色筛选 ----
    await page.locator('.filter-bar .el-select').first().click()
    await page.click('.el-select-dropdown:visible .el-select-dropdown__item:has-text("销售专员")')
    await page.click('.filter-bar button:has-text("查询")')
    await page.waitForTimeout(800)
    const filteredText = await page.locator('.el-table__body').first().innerText()
    check(filteredText.includes(REAL_NAME), '按「销售专员」筛选能查到该员工')

    await page.click('.filter-bar button:has-text("重置")')
    await page.waitForTimeout(600)

    await page.screenshot({ path: '/tmp/shot-staff-role-after.png' })
  } catch (e) {
    fails.push(e.message)
    console.error('✗ 执行异常:', e.message)
    if (logs.length) console.error(logs.join('\n'))
  } finally {
    // 兜底清理测试账号
    try {
      const leftover = createdId ? { id: createdId } : await findByUsername(token, USERNAME)
      if (leftover?.id) {
        await fetch(`${API}/api/system/users/${leftover.id}`, {
          method: 'DELETE',
          headers: { Authorization: `Bearer ${token}` }
        })
        console.log(`· 已删除测试账号 id=${leftover.id}（username=${USERNAME}）`)
      }
    } catch {
      /* 忽略清理异常 */
    }
    await browser.close()
  }

  console.log(fails.length === 0 ? '\n✓ 全部通过（角色必选、分组下拉、落库与筛选均正常）' : `\n✗ 失败 ${fails.length} 项`)
  process.exitCode = fails.length === 0 ? 0 : 1
})()
