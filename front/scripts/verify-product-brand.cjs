/**
 * 商品列表「品牌」接入厂商目录 E2E 回归（商品维护 → 商品列表）。
 *
 * 覆盖：
 *  1. 筛选栏品牌改为厂商下拉，选项来自厂商目录（启用厂商）
 *  2. 表格品牌列显示厂商名称（含 Logo 时展示厂牌图标）
 *  3. 按厂商过滤生效（有结果 / 无结果）
 *  4. 新增弹窗里的「品牌」是下拉框，不再是自由输入
 *  5. 编辑商品换厂商 → 表格品牌随之变化 → 再改回原厂商（保证数据复原）
 */
const { chromium } = require('playwright-core')

const BASE = 'http://localhost:5173'
const CHROME =
  process.env.CHROME_PATH || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome'

const ORIGIN_BRAND = 'BANPRESTO 眼镜厂'
const OTHER_BRAND = '万代 BANDAI'

let failed = 0
function check(ok, label, extra = '') {
  if (ok) {
    console.log(`✓ ${label}${extra ? ' — ' + extra : ''}`)
  } else {
    failed++
    console.error(`✗ ${label}${extra ? ' — ' + extra : ''}`)
  }
}

/** 选中 el-select 下拉里指定文案的选项 */
async function pickOption(page, selectLocator, text) {
  await selectLocator.click()
  const item = page.locator('.el-select-dropdown__item:visible', { hasText: text }).first()
  await item.waitFor({ state: 'visible', timeout: 5000 })
  await item.click()
  await page.waitForTimeout(300)
}

;(async () => {
  const browser = await chromium.launch({ executablePath: CHROME, headless: true })
  const page = await browser.newPage({ viewport: { width: 1600, height: 950 } })
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

    /* ---------- 1. 页面与筛选栏 ---------- */
    await page.goto(`${BASE}/product/list`, { waitUntil: 'domcontentloaded' })
    await page.waitForSelector('.el-table__row', { timeout: 15000 })
    await page.waitForTimeout(600)

    const crumb = (await page.locator('.breadcrumb-bar').innerText()).replace(/\s+/g, ' ').trim()
    check(crumb.includes('商品维护') && crumb.includes('商品列表'), '面包屑正确', crumb)

    // 筛选栏里的品牌输入框已换成下拉
    const brandFilter = page.locator('.filter-bar .brand-filter').first()
    check((await brandFilter.count()) > 0, '筛选栏出现「全部厂商」下拉')

    await brandFilter.click()
    await page.waitForTimeout(400)
    const optCount = await page.locator('.el-select-dropdown__item:visible').count()
    check(optCount >= 10, '下拉选项来自厂商目录', `${optCount} 个`)
    const hasGsc = await page
      .locator('.el-select-dropdown__item:visible', { hasText: 'Good Smile Company' })
      .count()
    check(hasGsc > 0, '选项包含厂商目录中的厂商（Good Smile Company）')
    await page.keyboard.press('Escape')
    await page.waitForTimeout(300)

    /* ---------- 2. 表格品牌列 ---------- */
    const firstRowText = (await page.locator('.el-table__row').first().innerText()).replace(/\s+/g, ' ')
    check(firstRowText.includes(ORIGIN_BRAND), '表格品牌列显示厂商名称', firstRowText.slice(0, 60))

    /* ---------- 3. 按厂商过滤 ---------- */
    await pickOption(page, brandFilter, ORIGIN_BRAND)
    await page.waitForTimeout(600)
    const rowsHit = await page.locator('.el-table__row').count()
    const hitText = rowsHit
      ? (await page.locator('.el-table__row').first().innerText()).replace(/\s+/g, ' ')
      : ''
    check(rowsHit >= 1 && hitText.includes(ORIGIN_BRAND), '按厂商过滤有结果且匹配', `rows=${rowsHit}`)

    await pickOption(page, brandFilter, 'Good Smile Company')
    await page.waitForTimeout(600)
    const rowsMiss = await page.locator('.el-table__row').count()
    const emptyText = await page.locator('.el-table__empty-text').count()
    check(rowsMiss === 0 || emptyText > 0, '按无商品的厂商过滤为空', `rows=${rowsMiss}`)

    // 清空筛选
    await page.locator('.filter-bar button:has-text("重置")').click()
    await page.waitForTimeout(700)
    const rowsAll = await page.locator('.el-table__row').count()
    check(rowsAll >= 1, '重置后恢复完整列表', `rows=${rowsAll}`)

    /* ---------- 4. 新增弹窗品牌为下拉 ---------- */
    await page.click('button:has-text("新增商品")')
    await page.waitForSelector('.el-dialog', { state: 'visible', timeout: 5000 })
    const brandItem = page.locator('.el-dialog .el-form-item:has-text("品牌")').first()
    const dialogBrandSelect = brandItem.locator('.el-select').first()
    check((await dialogBrandSelect.count()) > 0, '新增弹窗「品牌」为下拉框')
    const brandItemText = (await brandItem.innerText()).replace(/\s+/g, ' ')
    check(!brandItemText.includes('如 Good Smile Company'), '旧的自由文本输入提示已移除', brandItemText.slice(0, 50))

    await dialogBrandSelect.click()
    await page.waitForTimeout(400)
    const dlgOpts = await page.locator('.el-select-dropdown__item:visible').count()
    check(dlgOpts >= 10, '弹窗下拉选项来自厂商目录', `${dlgOpts} 个`)
    await page.keyboard.press('Escape')
    await page.waitForTimeout(200)
    await page.locator('.el-dialog__footer button:has-text("取消")').click()
    await page.waitForTimeout(400)

    /* ---------- 5. 编辑换厂商 ---------- */
    const row = page.locator('.el-table__row', { hasText: ORIGIN_BRAND }).first()
    await row.locator('button:has-text("编辑")').click()
    await page.waitForSelector('.el-dialog', { state: 'visible', timeout: 6000 })
    await page.waitForTimeout(500)

    const editSelect = page.locator('.el-dialog .el-form-item:has-text("品牌") .el-select').first()
    const selectedBefore = (await page.locator('.el-dialog .el-form-item:has-text("品牌")').first().innerText())
      .replace(/\s+/g, ' ')
      .trim()
    check(selectedBefore.includes(ORIGIN_BRAND), '编辑时回显当前厂商', selectedBefore.slice(0, 40))

    await pickOption(page, editSelect, OTHER_BRAND)
    await page.locator('.el-dialog__footer button:has-text("保存")').click()
    await page.waitForSelector('.el-dialog', { state: 'hidden', timeout: 10000 })
    await page.waitForTimeout(900)

    const afterSave = (await page.locator('.el-table__row').first().innerText()).replace(/\s+/g, ' ')
    check(afterSave.includes(OTHER_BRAND), '保存后表格品牌更新为新厂商', afterSave.slice(0, 60))

    /* ---------- 6. 改回原厂商，恢复数据 ---------- */
    const row2 = page.locator('.el-table__row', { hasText: OTHER_BRAND }).first()
    await row2.locator('button:has-text("编辑")').click()
    await page.waitForSelector('.el-dialog', { state: 'visible', timeout: 6000 })
    await page.waitForTimeout(500)
    const editSelect2 = page.locator('.el-dialog .el-form-item:has-text("品牌") .el-select').first()
    await pickOption(page, editSelect2, ORIGIN_BRAND)
    await page.locator('.el-dialog__footer button:has-text("保存")').click()
    await page.waitForSelector('.el-dialog', { state: 'hidden', timeout: 10000 })
    await page.waitForTimeout(900)

    const restored = (await page.locator('.el-table__row').first().innerText()).replace(/\s+/g, ' ')
    check(restored.includes(ORIGIN_BRAND), '已改回原厂商（数据复原）', restored.slice(0, 60))

    /* ---------- 7. 无运行时报错 ---------- */
    check(pageErrors.length === 0, '页面无 JS 运行时报错', pageErrors.join(' | ').slice(0, 120))
  } catch (e) {
    failed++
    console.error('✗ 用例执行异常：', e.message)
    await page.screenshot({ path: '/tmp/verify-product-brand-fail.png' }).catch(() => {})
  } finally {
    await browser.close()
  }

  console.log(failed === 0 ? '\n全部通过' : `\n${failed} 项失败`)
  process.exit(failed === 0 ? 0 : 1)
})()
