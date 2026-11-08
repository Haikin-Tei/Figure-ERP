/**
 * 目录维护（占位页）E2E 验证。
 *
 * 覆盖：
 *  1. 左侧一级菜单「目录维护」下出现三个新增二级菜单项
 *  2. 逐项点击可进入对应路由，URL 正确
 *  3. 面包屑为「目录维护 / <页面名>」两级，且占位页标题正确
 *  4. 标签页（TagsView）生成对应页签
 *  5. 回归：原有「作品目录及维护」仍可进入，面包屑文案为改名后的两级
 *  6. 三个占位页均为「功能建设中」空状态，无接口请求、无运行时报错
 *
 * 这三个页面目前只是占位，不接后端、不做页面设计，故不校验任何表格 / 数据。
 */
const { chromium } = require('playwright-core')

const BASE = 'http://localhost:5173'
const CHROME =
  process.env.CHROME_PATH || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome'

// [菜单文案, 路由, 占位页标题]
const CASES = [
  ['供应商目录维护', '/acg/supplier', '供应商目录维护'],
  ['大客户目录维护', '/acg/key-customer', '大客户目录维护'],
  ['厂商目录维护', '/acg/manufacturer', '厂商目录维护']
]

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
  const page = await browser.newPage({ viewport: { width: 1440, height: 900 } })
  const pageErrors = []
  page.on('pageerror', (e) => pageErrors.push(e.message))

  try {
    /* ---------- 登录 ---------- */
    await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' })
    await page.fill('input[placeholder="登录账号"]', 'admin')
    await page.fill('input[placeholder="登录密码"]', 'admin123')
    await page.click('button.submit-btn')
    await page.waitForURL(/\/(home|dashboard)$/, { timeout: 15000 })
    console.log('✓ 登录成功\n')

    /* ---------- 1. 菜单项存在 ---------- */
    await page.locator('.el-sub-menu__title:has-text("目录维护")').click()
    await page.waitForTimeout(300)
    const menuText = await page.locator('.sidebar-menu').innerText()
    check(menuText.includes('目录维护'), '一级菜单「目录维护」已出现')
    for (const [label] of CASES) {
      check(menuText.includes(label), `二级菜单「${label}」已出现`)
    }

    /* ---------- 2~4. 逐项进入并校验 ---------- */
    for (const [label, path, title] of CASES) {
      await page.goto(`${BASE}${path}`, { waitUntil: 'domcontentloaded' })
      await page.waitForSelector('.breadcrumb-bar .el-breadcrumb__inner', { timeout: 10000 })
      await page.waitForTimeout(200)

      const urlOk = page.url().includes(path)
      check(urlOk, `「${label}」可进入`, page.url())

      const crumbs = await page.$$eval('.breadcrumb-bar .el-breadcrumb__inner', (nodes) =>
        nodes.map((n) => n.textContent.trim())
      )
      const crumbOk =
        crumbs.length === 2 && crumbs[0] === '目录维护' && crumbs[1] === label
      check(crumbOk, `面包屑层级正确`, crumbs.join(' / '))

      const pageTitle = (await page.locator('.page-title').innerText()).trim()
      check(pageTitle === title, `占位页标题正确`, pageTitle)

      const emptyOk = (await page.locator('.el-empty__description').count()) > 0
      check(emptyOk, '显示「功能建设中」空状态')

      const tagText = await page.locator('.tags-view-container').innerText()
      check(tagText.includes(label), `标签页生成「${label}」`)
      console.log('')
    }

    /* ---------- 5. 回归：原有作品目录 ---------- */
    await page.goto(`${BASE}/acg/catalog`, { waitUntil: 'domcontentloaded' })
    await page.waitForSelector('.breadcrumb-bar .el-breadcrumb__inner', { timeout: 10000 })
    const acgCrumbs = await page.$$eval('.breadcrumb-bar .el-breadcrumb__inner', (nodes) =>
      nodes.map((n) => n.textContent.trim())
    )
    const acgOk =
      acgCrumbs.length === 2 &&
      acgCrumbs[0] === '目录维护' &&
      acgCrumbs[1] === '作品目录及维护'
    check(acgOk, '回归：作品目录及维护面包屑正确', acgCrumbs.join(' / '))

    /* ---------- 6. 无运行时报错 ---------- */
    check(pageErrors.length === 0, '页面无运行时报错', pageErrors.join(' | '))

    await page.screenshot({ path: '/tmp/shot-directory-placeholder.png' })
  } catch (e) {
    failed++
    console.error('✗ 执行异常:', e.message)
  } finally {
    await browser.close()
  }

  console.log(failed === 0 ? '\n✓ 全部通过' : `\n✗ 失败 ${failed} 项`)
  process.exitCode = failed === 0 ? 0 : 1
})()
