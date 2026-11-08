/* 临时验证脚本：走一遍「员工列表 / 员工维护」的真实操作流程并截图 */
const { chromium } = require('playwright-core')

const CHROME = '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome'
const BASE = 'http://localhost:5173'

async function pickOption(page, selectLocator, text) {
  await selectLocator.click()
  await page.waitForTimeout(300)
  const option = page.locator('.el-select-dropdown:visible .el-select-dropdown__item', { hasText: text }).first()
  await option.click()
  await page.waitForTimeout(200)
}

;(async () => {
  const browser = await chromium.launch({ executablePath: CHROME, headless: true })
  const page = await browser.newPage({ viewport: { width: 1440, height: 900 } })

  page.on('console', (msg) => {
    if (msg.type() === 'error') console.log('  [console error]', msg.text())
  })

  // 1. 登录
  await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' })
  await page.fill('input[placeholder="登录账号"]', 'admin')
  await page.fill('input[placeholder="登录密码"]', 'admin123')
  await page.click('button:has-text("登 录")')
  await page.waitForURL(/\/(home|dashboard)$/, { timeout: 10000 })

  // 2. 侧边栏菜单：展开「员工权限管理」→ 点「员工列表」
  await page.click('.el-sub-menu__title:has-text("员工权限管理")')
  await page.waitForTimeout(400)
  await page.click('.el-menu-item:has-text("员工列表")')
  await page.waitForTimeout(1200)
  console.log('员工列表 URL =', page.url())
  const rowCount = await page.locator('.el-table__body tbody tr').count()
  console.log('列表行数 =', rowCount)
  await page.screenshot({ path: '/tmp/staff-list.png' })

  // 3. 切到「员工维护」
  await page.click('.el-menu-item:has-text("员工维护")')
  await page.waitForTimeout(1200)
  console.log('员工维护 URL =', page.url())
  const maintainText = await page.locator('.page-container').innerText()
  console.log('维护页仍含「涉密等级」=', maintainText.includes('涉密等级'))

  // 4. 新增员工
  await page.click('button:has-text("新增员工")')
  await page.waitForTimeout(600)
  const dlg = page.locator('.el-dialog:visible')
  await dlg.locator('input[placeholder="3-20 位字母、数字或下划线"]').fill('zhaoliu')
  await dlg.locator('input[placeholder="请输入真实姓名"]').fill('赵六')
  await dlg.locator('input[placeholder="至少 6 位"]').fill('test123456')
  await pickOption(page, dlg.locator('.el-select').nth(0), '主管')
  await dlg.locator('textarea[placeholder="岗位、负责范围等"]').fill('负责订单核对')
  await dlg.locator('textarea[placeholder="选填"]').fill('由自动化脚本创建')
  await page.screenshot({ path: '/tmp/staff-form.png' })
  await dlg.locator('button:has-text("保存")').click()
  await page.waitForTimeout(1500)
  const createdToast = await page.locator('.el-message').textContent().catch(() => '(无)')
  console.log('新增提示 =', createdToast && createdToast.trim())

  // 5. 搜索刚建的账号，确认落库
  await page.fill('input[placeholder="账号 / 姓名"]', 'zhaoliu')
  await page.click('button:has-text("查询")')
  await page.waitForTimeout(1200)
  const rowText = await page.locator('.el-table__body tbody tr').first().innerText()
  console.log('查询结果行 =', rowText.replace(/\s+/g, ' ').trim())

  // 6. 编辑：把角色改成「管理员」
  await page.click('.el-table__body tbody tr:first-child button:has-text("编辑")')
  await page.waitForTimeout(600)
  const editDlg = page.locator('.el-dialog:visible')
  await pickOption(page, editDlg.locator('.el-select').nth(0), '管理员')
  await editDlg.locator('button:has-text("保存")').click()
  await page.waitForTimeout(1500)
  const editToast = await page.locator('.el-message').textContent().catch(() => '(无)')
  console.log('编辑提示 =', editToast && editToast.trim())
  const rowAfterEdit = await page.locator('.el-table__body tbody tr').first().innerText()
  console.log('编辑后行 =', rowAfterEdit.replace(/\s+/g, ' ').trim())
  await page.screenshot({ path: '/tmp/staff-maintain.png' })

  // 7. 重置密码
  await page.click('.el-table__body tbody tr:first-child button:has-text("重置密码")')
  await page.waitForTimeout(600)
  const pwdDlg = page.locator('.el-dialog:visible')
  await pwdDlg.locator('input[placeholder="至少 6 位"]').fill('newpass123')
  await pwdDlg.locator('input[placeholder="再次输入新密码"]').fill('newpass123')
  await pwdDlg.locator('button:has-text("确定重置")').click()
  await page.waitForTimeout(1500)
  const pwdToast = await page.locator('.el-message').textContent().catch(() => '(无)')
  console.log('重置密码提示 =', pwdToast && pwdToast.trim())

  // 8. 删除
  await page.click('.el-table__body tbody tr:first-child button:has-text("删除")')
  await page.waitForTimeout(600)
  await page.locator('.el-message-box__btns button:has-text("确定删除")').click()
  await page.waitForTimeout(1500)
  const delToast = await page.locator('.el-message').textContent().catch(() => '(无)')
  console.log('删除提示 =', delToast && delToast.trim())
  const rowsAfterDelete = await page.locator('.el-table__body tbody tr').count()
  console.log('删除后行数 =', rowsAfterDelete)

  await browser.close()
  console.log('DONE')
})().catch((e) => {
  console.error('FAILED:', e.message)
  process.exit(1)
})
