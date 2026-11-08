/**
 * 厂商目录维护 E2E 回归测试（目录维护 → 厂商目录维护）。
 *
 * 覆盖：
 *  1. 菜单入口、面包屑「目录维护 / 厂商目录维护」、统计卡
 *  2. 列表从后端加载种子数据（13 家），关键字搜索生效
 *  3. 新增厂商（含国家地区、备注、Logo 上传，真实落库）
 *  4. 编辑厂商（改名后列表更新）
 *  5. 停用 / 启用（状态标签变化）
 *  6. 删除厂商（数据清理干净）
 *
 * 用接口做前置清理，避免上次失败残留导致重名冲突。
 */
const fs = require('fs')
const path = require('path')
const { chromium } = require('playwright-core')

const BASE = 'http://localhost:5173'
const API = 'http://localhost:8080'
const CHROME =
  process.env.CHROME_PATH || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome'

const NAME = `E2E厂商-UI-${Date.now()}`
const NAME2 = `${NAME}-改`
const LOGO_FILE = path.join('/tmp', 'mfg-e2e-logo.png')

let failed = 0
function check(ok, label, extra = '') {
  if (ok) {
    console.log(`✓ ${label}${extra ? ' — ' + extra : ''}`)
  } else {
    failed++
    console.error(`✗ ${label}${extra ? ' — ' + extra : ''}`)
  }
}

async function apiLogin() {
  const resp = await fetch(`${API}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: 'admin', password: 'admin123' })
  })
  const body = await resp.json()
  return body?.data?.token ?? ''
}

async function cleanupByApi(token) {
  const resp = await fetch(`${API}/api/manufacturer/page?keyword=${encodeURIComponent('E2E厂商-UI')}&size=100`, {
    headers: { Authorization: `Bearer ${token}` }
  })
  const body = await resp.json()
  const rows = body?.data?.records ?? []
  for (const r of rows) {
    await fetch(`${API}/api/manufacturer/${r.id}`, {
      method: 'DELETE',
      headers: { Authorization: `Bearer ${token}` }
    })
  }
  return rows.length
}

/** 生成一个 1x1 PNG，用于测试 Logo 上传 */
function ensurePng() {
  const b64 =
    'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg=='
  fs.writeFileSync(LOGO_FILE, Buffer.from(b64, 'base64'))
}

;(async () => {
  const token = await apiLogin()
  if (!token) {
    console.error('✗ 登录失败，无法执行测试')
    process.exit(1)
  }
  const cleaned = await cleanupByApi(token)
  if (cleaned) console.log(`· 已清理历史残留 ${cleaned} 条\n`)
  ensurePng()

  const browser = await chromium.launch({ executablePath: CHROME, headless: true })
  const page = await browser.newPage({ viewport: { width: 1600, height: 950 } })
  const pageErrors = []
  page.on('pageerror', (e) => pageErrors.push(e.message))

  const rowOf = (text) => page.locator('.mfg-table .el-table__row', { hasText: text }).first()

  try {
    /* ---------- 登录 ---------- */
    await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' })
    await page.fill('input[placeholder="登录账号"]', 'admin')
    await page.fill('input[placeholder="登录密码"]', 'admin123')
    await page.click('button.submit-btn')
    await page.waitForURL(/\/(home|dashboard)$/, { timeout: 15000 })
    console.log('✓ 登录成功\n')

    /* ---------- 1. 入口与面包屑 ---------- */
    await page.goto(`${BASE}/acg/manufacturer`, { waitUntil: 'domcontentloaded' })
    await page.waitForSelector('.breadcrumb-bar .el-breadcrumb__inner', { timeout: 10000 })
    const crumbs = await page.$$eval('.breadcrumb-bar .el-breadcrumb__inner', (n) => n.map((x) => x.textContent.trim()))
    check(crumbs.join(' / ') === '目录维护 / 厂商目录维护', '面包屑层级正确', crumbs.join(' / '))

    await page.waitForSelector('.mfg-table .el-table__row', { timeout: 10000 })
    await page.waitForTimeout(400)

    /* ---------- 2. 统计卡与列表 ---------- */
    const kpiCount = await page.locator('.kpi').count()
    check(kpiCount === 4, '统计卡 4 张', String(kpiCount))
    const totalText = (await page.locator('.kpi').first().innerText()).replace(/\s+/g, ' ')
    check(/13\s*家/.test(totalText), '厂商总数为 13 家', totalText)

    const rows0 = await page.locator('.mfg-table .el-table__row').count()
    check(rows0 >= 10, '首屏加载厂商列表', `rows=${rows0}`)

    /* ---------- 3. 新增（含国家地区 + 备注 + Logo 上传） ---------- */
    await page.click('button:has-text("新增厂商")')
    await page.waitForSelector('.el-dialog', { state: 'visible', timeout: 5000 })
    await page.fill('.el-dialog input[placeholder="如：万代 BANDAI、BANPRESTO 眼镜厂"]', NAME)

    // 国家/地区：点开下拉，选「日本」
    await page.locator('.el-dialog .el-select').first().click()
    await page.waitForTimeout(300)
    await page.locator('.el-select-dropdown:visible .el-select-dropdown__item:has-text("日本")').first().click()

    // 备注
    await page.fill('.el-dialog textarea', 'E2E 新增备注')

    // Logo 上传
    await page.locator('.el-dialog input[type="file"]').setInputFiles(LOGO_FILE)
    await page.waitForSelector('.logo-uploader .el-image img', { timeout: 10000 })
    check(true, 'Logo 上传并显示预览')

    await page.locator('.el-dialog__footer button:has-text("保存")').click()
    await page.waitForSelector('.el-dialog', { state: 'hidden', timeout: 10000 })

    // 搜索确认落库
    await page.fill('.filter-bar input[placeholder="公司名称 / 国家地区"]', NAME)
    await page.click('.filter-bar button:has-text("查询")')
    await page.waitForTimeout(800)
    const created = rowOf(NAME)
    check((await created.count()) > 0, '新增厂商出现在列表', NAME)
    const createdText = (await created.innerText()).replace(/\s+/g, ' ')
    check(createdText.includes('日本'), '国家地区已保存', createdText)
    check((await created.locator('img').count()) > 0, '列表显示上传的 Logo')

    /* ---------- 4. 编辑 ---------- */
    await created.locator('button:has-text("编辑")').first().click()
    await page.waitForSelector('.el-dialog', { state: 'visible', timeout: 5000 })
    await page.fill('.el-dialog input[placeholder="如：万代 BANDAI、BANPRESTO 眼镜厂"]', NAME2)
    await page.locator('.el-dialog__footer button:has-text("保存")').click()
    await page.waitForSelector('.el-dialog', { state: 'hidden', timeout: 10000 })
    await page.fill('.filter-bar input[placeholder="公司名称 / 国家地区"]', NAME2)
    await page.click('.filter-bar button:has-text("查询")')
    await page.waitForTimeout(800)
    check((await rowOf(NAME2).count()) > 0, '编辑后名称更新', NAME2)

    /* ---------- 5. 停用 / 启用 ---------- */
    await rowOf(NAME2).locator('button:has-text("停用")').first().click()
    await page.waitForSelector('.el-message-box', { state: 'visible', timeout: 5000 })
    await page.locator('.el-message-box__btns button:has-text("停用")').click()
    await page.waitForTimeout(900)
    check((await rowOf(NAME2).innerText()).includes('停用'), '停用后状态标签为「停用」')

    await rowOf(NAME2).locator('button:has-text("启用")').first().click()
    await page.waitForSelector('.el-message-box', { state: 'visible', timeout: 5000 })
    await page.locator('.el-message-box__btns button:has-text("启用")').click()
    await page.waitForTimeout(900)
    check((await rowOf(NAME2).innerText()).includes('启用'), '启用后状态标签为「启用」')

    /* ---------- 6. 删除 ---------- */
    await rowOf(NAME2).locator('button:has-text("删除")').first().click()
    await page.waitForSelector('.el-message-box', { state: 'visible', timeout: 5000 })
    await page.locator('.el-message-box__btns button:has-text("删除")').click()
    await page.waitForTimeout(900)
    check((await rowOf(NAME2).count()) === 0, '删除后列表不再出现', NAME2)

    /* ---------- 7. 无运行时报错 ---------- */
    check(pageErrors.length === 0, '页面无运行时报错', pageErrors.join(' | '))

    await page.screenshot({ path: '/tmp/shot-manufacturer.png' })
  } catch (e) {
    failed++
    console.error('✗ 执行异常:', e.message)
  } finally {
    // 兜底清理（UI 流程中断时也能删干净）
    try {
      await cleanupByApi(token)
    } catch {
      /* ignore */
    }
    await browser.close()
  }

  console.log(failed === 0 ? '\n✓ 全部通过' : `\n✗ 失败 ${failed} 项`)
  process.exitCode = failed === 0 ? 0 : 1
})()
