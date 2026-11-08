/* 临时验证脚本：真实走一遍登录流程并截图（不进 git，验证完可删） */
const { chromium } = require('playwright-core')

const CHROME = '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome'

;(async () => {
  const browser = await chromium.launch({ executablePath: CHROME, headless: true })
  const page = await browser.newPage({ viewport: { width: 1440, height: 900 } })

  // 1. 打开受保护页面，应被守卫踢到 /login
  await page.goto('http://localhost:5173/dashboard', { waitUntil: 'networkidle' })
  console.log('访问 /dashboard 后 URL =', page.url())

  // 2. 输错密码，应提示「账号或密码不正确」
  await page.fill('input[placeholder="登录账号"]', 'admin')
  await page.fill('input[placeholder="登录密码"]', 'wrong-password')
  await page.click('button:has-text("登 录")')
  await page.waitForTimeout(1200)
  const errToast = await page.locator('.el-message').textContent().catch(() => '(无提示)')
  console.log('错误密码提示 =', errToast)

  // 3. 输对密码，应进入 dashboard
  await page.fill('input[placeholder="登录密码"]', 'admin123')
  await page.click('button:has-text("登 录")')
  await page.waitForURL(/\/(home|dashboard)$/, { timeout: 10000 })
  await page.waitForTimeout(1500)
  console.log('登录成功后 URL =', page.url())

  // 4. 右上角应显示员工姓名
  const name = await page.locator('.username').textContent().catch(() => '(未找到)')
  console.log('右上角显示 =', name && name.trim())

  // 5. 刷新页面，登录态应保持
  await page.reload({ waitUntil: 'networkidle' })
  await page.waitForTimeout(1000)
  const nameAfterReload = await page.locator('.username').textContent().catch(() => '(未找到)')
  console.log('刷新后右上角 =', nameAfterReload && nameAfterReload.trim())

  await page.screenshot({ path: '/tmp/after-login.png' })

  // 6. 打开个人信息抽屉
  await page.click('.user-trigger')
  await page.waitForTimeout(600)
  await page.click('.el-dropdown-menu__item:has-text("个人信息")')
  await page.waitForTimeout(800)
  await page.screenshot({ path: '/tmp/profile-drawer.png' })

  await browser.close()
  console.log('DONE')
})().catch((e) => {
  console.error('FAILED:', e.message)
  process.exit(1)
})
