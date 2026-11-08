/**
 * 端到端验证：首页按角色分流 + 工作台「今日工作计划 / 待办事项」。
 *
 * 覆盖：
 *   1. 管理员登录后进「工作台」，菜单里同时有工作台与数据概览
 *   2. 工作台首屏：问候语、四个统计卡、今日待办列表、完成率
 *   3. 待办实时增删改：输入框回车新增 → 勾选完成 → 删除，每步都回查接口确认落库
 *   4. 常用入口跳转
 *   5. 角色分流：总经理 / 销售主管 → 数据概览；其他角色 → 工作台
 *   6. 无权限的角色直接访问 /dashboard 会被送回 /home，且菜单里没有数据概览
 *
 * 测试账号自建自清。
 */
const { chromium } = require('playwright-core')

const BASE = 'http://localhost:5173'
const API = 'http://localhost:8080'
const RUN = Date.now().toString().slice(-8)
const PWD = 'Test' + RUN.slice(-6)
const TITLE = `E2E工作台待办-${RUN}`

/** 每个角色的期望首页 */
const ACCOUNTS = [
  { key: 'gm', role: 'GM', label: '总经理', home: '/dashboard', seesDashboard: true },
  { key: 'sm', role: 'SALES_MANAGER', label: '销售主管', home: '/dashboard', seesDashboard: true },
  { key: 'fs', role: 'FINANCE_STAFF', label: '财务专员', home: '/home', seesDashboard: false }
].map((a) => ({ ...a, username: `hm_${a.key}_${RUN.slice(-5)}` }))

async function call(method, path, token, body) {
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

async function loginApi(username, password) {
  const r = await call('POST', '/api/auth/login', null, { username, password })
  return r?.data?.token || null
}

;(async () => {
  const browser = await chromium.launch({ headless: true })
  const page = await browser.newPage({ viewport: { width: 1440, height: 900 } })
  const logs = []
  page.on('pageerror', (e) => logs.push(`[pageerror] ${e.message}`))

  const fails = []
  let total = 0
  const check = (ok, msg) => {
    total++
    if (ok) console.log(`✓ ${msg}`)
    else {
      fails.push(msg)
      console.error(`✗ ${msg}`)
    }
  }

  const createdUsers = []
  let adminToken = ''

  async function uiLogin(username, password) {
    await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' })
    await page.fill('input[placeholder="登录账号"]', username)
    await page.fill('input[placeholder="登录密码"]', password)
    await page.click('button.submit-btn')
    await page.waitForURL(/\/(home|dashboard)$/, { timeout: 15000 })
    await page.waitForTimeout(700)
    return new URL(page.url()).pathname
  }

  /** 侧边栏是否出现「数据概览」 */
  async function hasDashboardMenu() {
    return (await page.locator('.sidebar .el-menu-item', { hasText: '数据概览' }).count()) > 0
  }

  try {
    adminToken = await loginApi('admin', 'admin123')
    if (!adminToken) throw new Error('接口登录失败')

    /* 先建三个测试角色账号 */
    for (const a of ACCOUNTS) {
      const r = await call('POST', '/api/system/users', adminToken, {
        username: a.username,
        realName: `首页测试${a.label}`,
        role: a.role,
        status: 1,
        password: PWD
      })
      if (r?.code === 200) createdUsers.push({ id: r.data, ...a })
    }
    check(createdUsers.length === ACCOUNTS.length, `创建 ${ACCOUNTS.length} 个测试账号`)

    /* ---------- 1. 管理员进工作台 ---------- */
    console.log('\n[1] 管理员登录 → 工作台')
    const adminPath = await uiLogin('admin', 'admin123')
    check(adminPath === '/home', `管理员首页是工作台（实际 ${adminPath}）`)
    check(await hasDashboardMenu(), '管理员菜单里保留了「数据概览」入口')
    check(
      (await page.locator('.sidebar .el-menu-item', { hasText: '工作台' }).count()) > 0,
      '菜单里有「工作台」'
    )

    /* ---------- 2. 首屏内容 ---------- */
    console.log('\n[2] 工作台首屏')
    await page.waitForSelector('.todo-list .todo-item', { timeout: 10000 })
    const greeting = (await page.locator('.hero-greeting').innerText()).trim()
    check(greeting.includes('系统管理员'), `问候语带上姓名：${greeting}`)
    check((await page.locator('.hero-date').innerText()).includes('年'), '顶部显示今天日期')

    const statLabels = await page.locator('.stat-label').allInnerTexts()
    check(
      ['今日待办', '今日已完成', '逾期未完成', '未读消息'].every((t) => statLabels.includes(t)),
      `四张统计卡：${statLabels.join(' / ')}`
    )
    const rateText = (await page.locator('.progress-value').innerText()).trim()
    check(/^\d+%$/.test(rateText), `完成率圆环显示 ${rateText}`)
    const todoCount = await page.locator('.todo-list .todo-item').count()
    check(todoCount >= 3, `今日待办列表 ${todoCount} 条`)

    const msgCount = await page.locator('.msg-list .msg-item').count()
    check(msgCount > 0, `待办事项（未处理消息）${msgCount} 条`)
    check((await page.locator('.quick-item').count()) === 6, '常用入口 6 个')

    /* ---------- 3. 新增 / 勾选 / 删除 ---------- */
    console.log('\n[3] 待办新增 → 完成 → 删除')
    await page.fill('.quick-add input', TITLE)
    await page.press('.quick-add input', 'Enter')
    await page.waitForTimeout(1200)
    check(
      (await page.locator('.todo-item', { hasText: TITLE }).count()) === 1,
      '回车新增后列表立即出现该待办'
    )

    const listApi = await call('GET', '/api/todo/page?page=1&size=50&keyword=' + encodeURIComponent(TITLE), adminToken)
    const createdTodo = listApi?.data?.records?.[0]
    check(!!createdTodo && createdTodo.title === TITLE, '接口回查确认已落库')

    // 勾选完成
    const todoRow = page.locator('.todo-item', { hasText: TITLE })
    await todoRow.locator('.el-checkbox').click()
    await page.waitForTimeout(1200)
    const doneApi = await call('GET', `/api/todo/${createdTodo.id}`, adminToken)
    check(doneApi?.data?.status === 'D', '勾选后状态变为已完成（D）')
    check(
      ((await todoRow.getAttribute('class')) || '').includes('is-done'),
      '勾选后标题显示为划线完成态'
    )

    // 删除
    await todoRow.hover()
    await todoRow.locator('.todo-del').click()
    await page.waitForSelector('.el-message-box', { timeout: 5000 })
    await page.click('.el-message-box__btns .el-button--primary')
    await page.waitForTimeout(1200)
    check(
      (await page.locator('.todo-item', { hasText: TITLE }).count()) === 0,
      '删除后列表立即消失'
    )
    const goneApi = await call('GET', `/api/todo/${createdTodo.id}`, adminToken)
    check(goneApi?.code === 1001, '接口回查确认已逻辑删除')

    /* ---------- 4. 常用入口 ---------- */
    console.log('\n[4] 常用入口')
    await page.locator('.quick-item', { hasText: '手办列表' }).click()
    await page.waitForTimeout(900)
    check(new URL(page.url()).pathname === '/stock/figure', '点「手办列表」进入手办列表页')

    /* ---------- 5. 管理员可访问数据概览 ---------- */
    console.log('\n[5] 管理员仍可查看数据概览')
    await page.goto(`${BASE}/dashboard`, { waitUntil: 'domcontentloaded' })
    await page.waitForTimeout(800)
    check(new URL(page.url()).pathname === '/dashboard', '管理员访问 /dashboard 不被拦截')

    /* ---------- 6. 角色分流 ---------- */
    console.log('\n[6] 角色分流')
    for (const a of createdUsers) {
      await page.evaluate(() => localStorage.clear())
      const path = await uiLogin(a.username, PWD)
      check(path === a.home, `${a.label}登录后进入 ${a.home}（实际 ${path}）`)

      const menu = await hasDashboardMenu()
      check(
        menu === a.seesDashboard,
        `${a.label}菜单${a.seesDashboard ? '有' : '没有'}「数据概览」`
      )
    }

    /* ---------- 7. 越权直连 /dashboard ---------- */
    console.log('\n[7] 无权角色直连 /dashboard')
    const staff = createdUsers.find((x) => !x.seesDashboard)
    if (staff) {
      await page.goto(`${BASE}/dashboard`, { waitUntil: 'domcontentloaded' })
      await page.waitForTimeout(1000)
      check(
        new URL(page.url()).pathname === '/home',
        `「${staff.label}」访问 /dashboard 被送回工作台`
      )
      await page.waitForSelector('.hero-greeting', { timeout: 8000 })
      check(true, '工作台内容正常渲染')
    }

    await page.screenshot({ path: '/tmp/shot-workbench.png' })
    await page.setViewportSize({ width: 390, height: 844 })
    await page.reload({ waitUntil: 'networkidle' })
    await page.waitForTimeout(900)
    await page.screenshot({ path: '/tmp/shot-workbench-mobile.png' })

    check(logs.length === 0, `无前端运行时报错${logs.length ? '：' + logs.join(' | ') : ''}`)
  } catch (e) {
    fails.push(e.message)
    console.error('✗ 执行异常:', e.message)
  } finally {
    /* ---------- 清理 ---------- */
    for (const u of createdUsers) {
      const token = await loginApi(u.username, PWD)
      if (token) {
        const list = await call('GET', '/api/todo/page?page=1&size=100', token)
        for (const t of list?.data?.records || []) {
          await call('DELETE', `/api/todo/${t.id}`, token)
        }
      }
      await call('DELETE', `/api/system/users/${u.id}`, adminToken)
    }
    const leftovers = await call('GET', '/api/todo/page?page=1&size=50&keyword=' + encodeURIComponent('E2E工作台待办'), adminToken)
    if (leftovers?.data?.records) {
      for (const t of leftovers.data.records) await call('DELETE', `/api/todo/${t.id}`, adminToken)
    }

    await browser.close()
    console.log(`\n结果：通过 ${total - fails.length} 项，失败 ${fails.length} 项`)
    if (fails.length) {
      fails.forEach((f) => console.error(' - ' + f))
      process.exit(1)
    }
  }
})()
