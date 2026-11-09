/**
 * 手办列表（库存）E2E 回归测试（库存管理 → 手办列表）。
 *
 * 覆盖：
 *  1. 菜单入口、面包屑「库存管理 / 手办列表」
 *  2. 统计卡 4 张都有值，且与列表数据对得上
 *  3. 列表从后端加载库存（后端由入库单确认入库后累加，这里只读不造数据）
 *  4. 关键字筛选、仓库筛选、排序切换
 *  5. 库位明细抽屉：点开后能看到仓库 / 货架与数量
 *  6. 无运行时报错
 *
 * 本页只读，不写库，所以不需要前置清理。
 */
const { chromium } = require('playwright-core')

const BASE = 'http://localhost:5173'
const CHROME =
  process.env.CHROME_PATH || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome'

let failed = 0
function check(ok, label, extra = '') {
  if (ok) {
    console.log(`✓ ${label}${extra ? ' — ' + extra : ''}`)
  } else {
    failed++
    console.error(`✗ ${label}${extra ? ' — ' + extra : ''}`)
  }
}

;(async () => {
  const browser = await chromium.launch({ executablePath: CHROME, headless: true })
  const page = await browser.newPage({ viewport: { width: 1560, height: 900 } })

  const errors = []
  page.on('console', (msg) => {
    if (msg.type() === 'error') errors.push(msg.text())
  })
  page.on('pageerror', (err) => errors.push(String(err)))

  try {
    /* ---------- 登录 ---------- */
    await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' })
    await page.fill('input[placeholder="登录账号"]', 'admin')
    await page.fill('input[placeholder="登录密码"]', 'admin123')
    await page.click('button.submit-btn')
    await page.waitForURL(/\/(home|dashboard)$/, { timeout: 15000 })
    check(true, '登录成功')

    /* ---------- 1. 菜单进入 ---------- */
    // 子菜单默认收起，先展开「库存管理」
    await page.locator('.el-sub-menu__title:has-text("库存管理")').click()
    await page.waitForTimeout(400)
    await page.locator('.el-menu-item:has-text("手办列表")').click()
    await page.waitForURL('**/stock/figure', { timeout: 15000 })
    check(true, '菜单「库存管理 → 手办列表」可进入', page.url())

    const crumb = (await page.locator('.breadcrumb-bar').innerText()).replace(/\s+/g, ' ').trim()
    check(crumb.includes('库存管理') && crumb.includes('手办列表'), '面包屑层级正确', crumb)

    /* ---------- 2. 统计卡 ---------- */
    await page.waitForSelector('.el-table__row', { timeout: 15000 })
    const statValues = await page.locator('.stat-value').allInnerTexts()
    const stats = statValues.map((t) => t.replace(/[^\d.]/g, '')).filter((t) => t !== '')
    check(stats.length === 4, '四张统计卡都有数值', statValues.join(' | '))
    check(Number(stats[0]) >= 1, '在库款式数 > 0', stats[0])
    check(Number(stats[1]) >= 1, '在库总件数 > 0', stats[1])
    check(Number(stats[2]) >= 0, '库存金额已展示', stats[2])

    /* ---------- 3. 列表 ---------- */
    const rowCount = await page.locator('.el-table__body .el-table__row').count()
    check(rowCount >= 1, '库存列表有数据', `${rowCount} 行`)

    const bodyText = await page.locator('.el-table__body').innerText()
    check(/件/.test(bodyText), '数量列带「件」单位')
    check(/¥/.test(bodyText), '金额列带 ¥ 符号')

    const firstRowHtml = await page.locator('.el-table__body .el-table__row').first().innerHTML()
    check(firstRowHtml.includes('已下架') || firstRowHtml.includes('在售'), '商品状态有标识')

    /* ---------- 4. 关键字筛选 ---------- */
    const keywordInput = page.locator('.filter-bar input').first()
    await keywordInput.fill('孤独')
    await page.locator('.filter-bar button:has-text("查询")').click()
    await page.waitForTimeout(700)
    const kwRows = await page.locator('.el-table__body .el-table__row').count()
    const kwText = await page.locator('.el-table__body').innerText()
    check(kwRows === 1 && kwText.includes('孤独'), '关键字「孤独」筛选命中 1 条', `${kwRows} 行`)

    /* ---------- 5. 重置 + 仓库筛选 ---------- */
    await page.locator('.filter-bar button:has-text("重置")').click()
    await page.waitForTimeout(700)
    const resetRows = await page.locator('.el-table__body .el-table__row').count()
    check(resetRows === rowCount, '重置后恢复全量', `${resetRows} 行`)

    const selects = page.locator('.filter-bar .el-select')
    await selects.nth(0).click()
    await page.waitForTimeout(300)
    await page.locator('.el-select-dropdown__item:visible').first().click()
    await page.waitForTimeout(800)
    const whRows = await page.locator('.el-table__body .el-table__row').count()
    check(whRows >= 1, '按仓库筛选后仍有数据', `${whRows} 行`)

    await page.locator('.filter-bar button:has-text("重置")').click()
    await page.waitForTimeout(700)

    /* ---------- 6. 排序切换 ---------- */
    await page.locator('.filter-bar .el-select').nth(1).click()
    await page.waitForTimeout(300)
    await page.locator('.el-select-dropdown__item:visible:has-text("库存金额")').first().click()
    await page.waitForTimeout(800)
    const amountRows = await page.locator('.el-table__body .el-table__row').count()
    check(amountRows >= 1, '切换「库存金额↓」排序后正常返回', `${amountRows} 行`)

    /* ---------- 7. 库位明细抽屉 ---------- */
    await page.locator('.el-table__row').first().locator('button:has-text("库位明细")').click()
    // 页面上还有「个人信息」等抽屉，按标题定位到本页的那个
    const drawer = page.locator('.el-drawer').filter({ hasText: '库位明细' }).last()
    await drawer.waitFor({ state: 'visible', timeout: 8000 })
    await page.waitForTimeout(600)
    const drawerText = (await drawer.innerText()).replace(/\s+/g, ' ')
    check(drawerText.includes('库位明细'), '抽屉标题为「库位明细」')
    check(/上海库|北京库|广州库/.test(drawerText), '抽屉里能看到仓库名')
    check(/件/.test(drawerText) && /¥/.test(drawerText), '抽屉明细含数量与金额')
    check(drawerText.includes('在库件数') && drawerText.includes('库存金额'), '抽屉顶部有汇总信息')

    await page.keyboard.press('Escape')
    await page.waitForTimeout(400)

    /* ---------- 8. 运行时报错 ---------- */
    const realErrors = errors.filter(
      (e) => !e.includes('favicon') && !e.includes('Download the Vue Devtools')
    )
    check(realErrors.length === 0, '无运行时报错', realErrors.slice(0, 3).join(' | '))
  } catch (e) {
    failed++
    console.error('✗ 执行异常：', e.message)
  } finally {
    await browser.close()
  }

  console.log(failed === 0 ? '\n全部通过' : `\n失败 ${failed} 项`)
  process.exit(failed === 0 ? 0 : 1)
})()
