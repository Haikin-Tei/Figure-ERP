/**
 * 验证「个人消息」：右上角用户菜单 → 个人消息 → 查看 / 处理 / 全部已读 / 消息中心页。
 *
 * 覆盖：
 *  1. 用户下拉里出现「个人消息」并带未读数
 *  2. 抽屉列表、类型 Tab、未读数与后端 summary 一致
 *  3. 点开消息自动已读，未读数减少
 *  4. 填写处理备注 → 去处理 → 跳转关联页面，消息变已处理
 *  5. 全部已读后红点消失
 *  6. /message/list 页面的统计、表格、面包屑
 *
 * 测试会改动演示数据，结束前按备份把未读状态恢复回去。
 */
const { chromium } = require('playwright-core')
const { execSync } = require('child_process')

const BASE = 'http://localhost:5173'
const MYSQL = '/usr/local/mysql/bin/mysql -h127.0.0.1 -uroot -N'

function sql(statement) {
  return execSync(`${MYSQL} -e "use figure_erp; ${statement}"`, {
    env: { ...process.env, MYSQL_PWD: 'Genshin20200915' }
  })
    .toString()
    .trim()
}

let failed = 0
function check(ok, label, extra = '') {
  if (ok) {
    console.log(`✓ ${label}`)
  } else {
    failed++
    console.error(`✗ ${label}${extra ? ' → ' + extra : ''}`)
  }
}

async function openMessageMenu(page) {
  // 抽屉还开着的话先关掉，否则遮罩会挡住右上角的用户菜单
  const drawer = page.locator('.el-drawer.msg-drawer')
  if (await drawer.isVisible().catch(() => false)) {
    await page.keyboard.press('Escape')
    await page.waitForTimeout(700)
  }
  await page.click('.user-trigger')
  await page.waitForSelector('.user-dropdown .el-dropdown-menu__item', { state: 'visible' })
  await page.waitForTimeout(150)
  const pill = page.locator('.user-dropdown .unread-pill').first()
  const count = (await pill.count()) > 0 ? (await pill.textContent()).trim() : '0'
  await page.click('.user-dropdown .el-dropdown-menu__item:has-text("个人消息")')
  await page.waitForSelector('.el-drawer .mc-list, .el-drawer .mc-detail', { timeout: 8000 })
  await page.waitForTimeout(400)
  return count
}

;(async () => {
  // 备份未读消息，跑完还原，避免把演示数据弄成「全部已处理」
  const unreadIds = sql("select id from sys_message where deleted=0 and status='U'")
    .split('\n')
    .filter(Boolean)

  const browser = await chromium.launch({ headless: true })
  const page = await browser.newPage({ viewport: { width: 1440, height: 900 } })
  const errors = []
  page.on('pageerror', (e) => errors.push(`[pageerror] ${e.message}`))

  try {
    /* ---------- 登录 ---------- */
    await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' })
    await page.fill('input[placeholder="登录账号"]', 'admin')
    await page.fill('input[placeholder="登录密码"]', 'admin123')
    await page.click('button.submit-btn')
    await page.waitForURL(/\/(home|dashboard)$/, { timeout: 10000 })
    await page.waitForTimeout(600)
    console.log('✓ 登录成功')

    /* ---------- 1. 下拉菜单 ---------- */
    await page.click('.user-trigger')
    await page.waitForSelector('.user-dropdown .el-dropdown-menu__item', { state: 'visible' })
    const menuText = await page.locator('.user-dropdown .el-dropdown-menu').innerText()
    check(menuText.includes('个人消息'), '用户下拉出现「个人消息」', menuText.replace(/\n/g, ' | '))
    const unreadPill = await page.locator('.user-dropdown .unread-pill').first().textContent()
    check(unreadPill.trim() === String(unreadIds.length), `未读徽标 = ${unreadIds.length}`, unreadPill)

    /* ---------- 2. 抽屉列表 ---------- */
    await page.click('.user-dropdown .el-dropdown-menu__item:has-text("个人消息")')
    await page.waitForSelector('.el-drawer .mc-list', { timeout: 8000 })
    await page.waitForTimeout(500)

    const totalRows = await page.locator('.mc-list .msg').count()
    check(totalRows > 0, `抽屉列表渲染 ${totalRows} 条消息`)

    const tabsText = await page.locator('.mc-tabs').innerText()
    check(tabsText.includes('审批') && tabsText.includes('订单'), '类型 Tab 完整', tabsText.replace(/\n/g, ' '))
    check(
      tabsText.replace(/\s/g, '').includes(`未读${unreadIds.length}`),
      `未读 Tab 计数 = ${unreadIds.length}`,
      tabsText.replace(/\n/g, ' ')
    )

    /* ---------- 3. 详情自动已读 ---------- */
    await page.click('.mc-list .msg >> nth=0')
    await page.waitForSelector('.mc-detail .detail-title', { timeout: 8000 })
    const detailTitle = (await page.locator('.mc-detail .detail-title').innerText()).trim()
    check(detailTitle.length > 0, `打开详情：${detailTitle}`)

    const statusAfterOpen = (await page.locator('.mc-detail .detail-tags').innerText()).replace(/\s/g, '')
    check(statusAfterOpen.includes('已读'), '进入详情后状态变为已读', statusAfterOpen)

    /* ---------- 4. 处理并跳转 ---------- */
    const hasBizUrl = (await page.locator('.mc-detail .detail-tip').count()) > 0
    if (hasBizUrl) {
      await page.fill('.mc-detail textarea', '自动化验证：已核对，同意处理')
      await page.click('.mc-detail .detail-actions button:has-text("去处理")')
      await page.waitForURL((url) => !url.pathname.startsWith('/dashboard'), { timeout: 8000 })
      await page.waitForTimeout(600)
      const path = new URL(page.url()).pathname
      check(path !== '/dashboard', `「去处理」跳转到关联页面：${path}`)
    } else {
      console.log('· 首条消息没有关联页面，跳过跳转断言')
    }

    /* ---------- 5. 未读数减少 ---------- */
    const unreadAfter = Number(await openMessageMenu(page))
    check(
      unreadAfter === unreadIds.length - 1,
      `处理后未读数 ${unreadIds.length} → ${unreadAfter}`,
      String(unreadAfter)
    )

    const handledBanner = await page.locator('.el-drawer .mc-list').count()
    check(handledBanner > 0, '抽屉可再次打开')

    await page.click('.mc-tabs .mc-tab:has-text("未读")')
    await page.waitForTimeout(600)
    const unreadRows = await page.locator('.mc-list .msg').count()
    check(unreadRows === unreadAfter, `未读 Tab 列表条数 = ${unreadAfter}`, String(unreadRows))

    /* ---------- 6. 全部已读 ---------- */
    await page.click('.mc-head-actions button:has-text("全部已读")')
    await page.waitForTimeout(900)
    const emptyUnread = await page.locator('.mc-list .el-empty').count()
    check(emptyUnread > 0, '全部已读后未读列表为空')

    await page.keyboard.press('Escape')
    await page.waitForTimeout(500)
    await page.click('.user-trigger')
    await page.waitForSelector('.user-dropdown .el-dropdown-menu__item', { state: 'visible' })
    const pillGone = await page.locator('.user-dropdown .unread-pill').count()
    check(pillGone === 0, '未读归零后徽标消失')
    await page.keyboard.press('Escape')

    /* ---------- 7. 消息中心页 ---------- */
    await page.goto(`${BASE}/message/list`, { waitUntil: 'domcontentloaded' })
    await page.waitForSelector('.message-table .el-table__row', { timeout: 10000 })
    await page.waitForTimeout(500)

    const statValues = await page.locator('.stat-card .stat-value').allInnerTexts()
    check(statValues[0] === '0', `统计卡未读数 = 0`, statValues.join(','))

    const rowCounts = await page.locator('.message-table .el-table__body').evaluateAll((nodes) =>
      nodes.map((n) => n.querySelectorAll('tr').length)
    )
    check(Math.max(...rowCounts) > 0, `消息中心表格 ${Math.max(...rowCounts)} 行`)

    const crumbs = await page.locator('.breadcrumb-bar .el-breadcrumb__inner').allInnerTexts()
    check(
      crumbs.join('/') === '个人中心/个人消息',
      `面包屑 = ${crumbs.join(' / ')}`,
      crumbs.join('/')
    )

    // 筛选「已处理」
    await page.click('.filters .el-select')
    await page.waitForTimeout(300)
    await page.click('.el-select-dropdown__item:has-text("已处理")')
    await page.waitForTimeout(700)
    const handledCounts = await page.locator('.message-table .el-table__body').evaluateAll((nodes) =>
      nodes.map((n) => n.querySelectorAll('tr').length)
    )
    check(Math.max(...handledCounts) > 0, `筛选已处理后 ${Math.max(...handledCounts)} 行`)

    await page.screenshot({ path: '/tmp/shot-message-list.png' })

    /* ---------- 8. 窄屏抽屉 ---------- */
    await page.setViewportSize({ width: 390, height: 844 })
    await page.goto(`${BASE}/dashboard`, { waitUntil: 'domcontentloaded' })
    await page.waitForTimeout(400)
    await page.click('.user-trigger')
    await page.waitForSelector('.user-dropdown .el-dropdown-menu__item', { state: 'visible' })
    await page.click('.user-dropdown .el-dropdown-menu__item:has-text("个人消息")')
    await page.waitForSelector('.el-drawer .mc-list', { timeout: 8000 })
    await page.waitForTimeout(500)
    const drawerWidth = await page.locator('.el-drawer.msg-drawer').evaluate((n) => Math.round(n.getBoundingClientRect().width))
    check(drawerWidth >= 380, `窄屏抽屉宽度 ${drawerWidth}px（铺满视口）`)
    await page.screenshot({ path: '/tmp/shot-message-mobile.png' })
  } catch (e) {
    failed++
    console.error('✗ 执行异常:', e.message)
  } finally {
    await browser.close()
    // 还原演示数据：备份过的未读消息恢复未读，清掉本次写入的读取/处理痕迹
    if (unreadIds.length) {
      sql(
        `update sys_message set status='U', read_time=null, handle_time=null, handle_remark=null ` +
          `where id in (${unreadIds.join(',')})`
      )
    }
    const restored = sql("select count(*) from sys_message where deleted=0 and status='U'")
    console.log(`\n· 演示数据已还原，未读 ${restored} 条`)
    if (errors.length) console.error('\n浏览器错误:\n' + errors.join('\n'))
  }

  console.log(failed === 0 ? '\n✓ 全部通过' : `\n✗ 失败 ${failed} 项`)
  process.exitCode = failed === 0 ? 0 : 1
})()
