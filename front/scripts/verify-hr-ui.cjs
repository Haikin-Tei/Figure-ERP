/**
 * 员工考勤 / 请假 / 加班三个页面的 E2E 回归（员工权限管理）。
 *
 * 覆盖：
 *  1. 侧边栏「员工权限管理」下新增三个菜单项，均可进入
 *  2. 三个页面的面包屑层级正确
 *  3. 统计卡 4 张都有值，列表从后端加载到数据
 *  4. 考勤页：录入弹窗字段齐全；选中销售员工后下班时间自动禁用（免打卡规则）
 *  5. 考勤页：列表能展示「免打卡」「迟到」「早退」标签
 *  6. 请假页：提交弹窗字段齐全；待审批单据可打开审批弹窗
 *  7. 加班页：提交弹窗字段齐全；表头与加班类型筛选存在
 *  8. 全程无运行时报错
 *
 * 本脚本只读 + 只开关弹窗，不提交任何单据，跑完库里不留脏数据。
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

    /* ---------- 1. 侧边栏菜单 ---------- */
    const staffMenu = page.locator('.el-sub-menu__title:has-text("员工权限管理")')
    await staffMenu.click()
    await page.waitForTimeout(400)
    const sidebarText = await page.locator('.sidebar').innerText()
    for (const label of ['员工列表', '员工维护', '员工考勤记录', '员工请假申请', '员工加班申请']) {
      check(sidebarText.includes(label), `侧边栏有「${label}」`)
    }

    /* ---------- 2. 考勤记录页 ---------- */
    await page.locator('.el-menu-item:has-text("员工考勤记录")').click()
    await page.waitForURL('**/staff/attendance', { timeout: 15000 })
    await page.waitForSelector('.base-table .el-table__row', { timeout: 15000 })

    const attCrumb = (await page.locator('.breadcrumb-bar').innerText()).replace(/\s+/g, ' ').trim()
    check(
      attCrumb.includes('员工权限管理') && attCrumb.includes('员工考勤记录'),
      '考勤页面包屑正确',
      attCrumb
    )

    const attStats = await page.locator('.stat-card').allInnerTexts()
    check(attStats.length === 4, '考勤页 4 张统计卡', String(attStats.length))
    check(
      attStats[0].includes('本月考勤') && attStats[1].includes('迟到') && attStats[2].includes('早退'),
      '统计卡标题正确',
      attStats.map((t) => t.split('\n')[0]).join(' | ')
    )

    const attRows = await page.locator('.base-table .el-table__body tr').count()
    check(attRows > 0, `考勤列表有 ${attRows} 条数据`)

    const attText = await page.locator('.base-table').innerText()
    check(attText.includes('免打卡'), '考勤列表展示销售岗「免打卡」标签')
    check(/迟到|早退/.test(attText), '考勤列表展示迟到 / 早退标签')
    check(attText.includes('销售专员') || attText.includes('销售主管'), '考勤列表展示角色标签')

    /* ---------- 3. 考勤录入弹窗 + 销售免下班卡 ---------- */
    await page.locator('button:has-text("录入考勤")').click()
    const attDialog = page.locator('.el-dialog:has-text("录入考勤记录")')
    await attDialog.waitFor({ state: 'visible', timeout: 10000 })
    check(true, '「录入考勤」弹窗打开')

    const attDialogText = await attDialog.innerText()
    for (const label of ['员工', '考勤日期', '上班时间', '下班时间', '备注']) {
      check(attDialogText.includes(label), `录入弹窗有「${label}」字段`)
    }

    // 选择销售员工（王楚楚），下班时间应被禁用
    // 注意：页面上筛选栏也有员工下拉，隐藏的 dropdown 里同样有「王楚楚」，
    // 所以必须限定在 :visible，否则会点到不可见的那个选项上
    await attDialog.locator('.el-form-item:has-text("员工") .el-select').click()
    await page.waitForTimeout(600)
    const salesOption = page.locator('.el-select-dropdown__item:has-text("王楚楚"):visible').first()
    if (await salesOption.count()) {
      await salesOption.click()
      await page.waitForTimeout(500)
      const checkOutInput = attDialog.locator('.el-form-item:has-text("下班时间") input').first()
      check(await checkOutInput.isDisabled(), '选中销售员工后下班时间自动禁用（免打卡）')
      const hintText = await attDialog.innerText()
      check(hintText.includes('销售岗'), '弹窗提示销售岗免打下班卡')
    } else {
      check(false, '下拉里找不到销售员工王楚楚')
    }

    await attDialog.locator('button:has-text("取消")').click()
    await page.waitForTimeout(400)

    /* ---------- 4. 请假申请页 ---------- */
    await page.locator('.el-menu-item:has-text("员工请假申请")').click()
    await page.waitForURL('**/staff/leave', { timeout: 15000 })
    await page.waitForSelector('.base-table .el-table__row', { timeout: 15000 })

    const leaveCrumb = (await page.locator('.breadcrumb-bar').innerText()).replace(/\s+/g, ' ').trim()
    check(
      leaveCrumb.includes('员工权限管理') && leaveCrumb.includes('员工请假申请'),
      '请假页面包屑正确',
      leaveCrumb
    )
    const leaveStats = await page.locator('.stat-card').allInnerTexts()
    check(leaveStats.length === 4, '请假页 4 张统计卡', String(leaveStats.length))

    const leaveText = await page.locator('.base-table').innerText()
    check(/^LV\d{12}/.test(leaveText.match(/LV\d{12}/)?.[0] || ''), '请假列表展示 LV 单号')
    check(leaveText.includes('待审批') && leaveText.includes('已通过'), '请假列表有多状态单据')

    // 提交弹窗
    await page.locator('button:has-text("提交请假申请")').click()
    const leaveDialog = page.locator('.el-dialog:has-text("提交请假申请")')
    await leaveDialog.waitFor({ state: 'visible', timeout: 10000 })
    const leaveDialogText = await leaveDialog.innerText()
    for (const label of ['申请人', '请假类型', '请假时间', '请假事由']) {
      check(leaveDialogText.includes(label), `请假弹窗有「${label}」字段`)
    }
    await leaveDialog.locator('button:has-text("取消")').click()
    await page.waitForTimeout(400)

    // 审批弹窗
    const approveBtn = page.locator('.base-table button:has-text("审批")').first()
    if (await approveBtn.count()) {
      await approveBtn.click()
      const approveDialog = page.locator('.el-dialog:has-text("审批请假申请")')
      await approveDialog.waitFor({ state: 'visible', timeout: 10000 })
      const approveText = await approveDialog.innerText()
      check(approveText.includes('通过') && approveText.includes('驳回'), '审批弹窗有通过 / 驳回选项')
      check(approveText.includes('请假事由'), '审批弹窗展示请假事由')
      await approveDialog.locator('button:has-text("取消")').click()
      await page.waitForTimeout(400)
    } else {
      check(false, '列表里找不到可审批的请假单')
    }

    /* ---------- 5. 加班申请页 ---------- */
    await page.locator('.el-menu-item:has-text("员工加班申请")').click()
    await page.waitForURL('**/staff/overtime', { timeout: 15000 })
    await page.waitForSelector('.base-table .el-table__row', { timeout: 15000 })

    const otCrumb = (await page.locator('.breadcrumb-bar').innerText()).replace(/\s+/g, ' ').trim()
    check(
      otCrumb.includes('员工权限管理') && otCrumb.includes('员工加班申请'),
      '加班页面包屑正确',
      otCrumb
    )
    const otStats = await page.locator('.stat-card').allInnerTexts()
    check(otStats.length === 4, '加班页 4 张统计卡', String(otStats.length))
    check(otStats[2].includes('本月加班时长'), '加班页统计卡含「本月加班时长」', otStats[2].split('\n')[0])

    const otText = await page.locator('.base-table').innerText()
    check(/^OT\d{12}/.test(otText.match(/OT\d{12}/)?.[0] || ''), '加班列表展示 OT 单号')
    check(/工作日加班|休息日加班|节假日加班/.test(otText), '加班列表展示加班类型')

    await page.locator('button:has-text("提交加班申请")').click()
    const otDialog = page.locator('.el-dialog:has-text("提交加班申请")')
    await otDialog.waitFor({ state: 'visible', timeout: 10000 })
    const otDialogText = await otDialog.innerText()
    for (const label of ['申请人', '加班类型', '加班时间', '加班事由']) {
      check(otDialogText.includes(label), `加班弹窗有「${label}」字段`)
    }
    await otDialog.locator('button:has-text("取消")').click()
    await page.waitForTimeout(400)

    /* ---------- 6. 运行时报错 ---------- */
    const realErrors = errors.filter((e) => !e.includes('favicon'))
    check(realErrors.length === 0, '全程无 console 报错', realErrors.slice(0, 3).join(' | '))

    await page.screenshot({ path: '/tmp/shot-hr-overtime.png', fullPage: true })
  } catch (e) {
    failed++
    console.error('✗ 脚本异常：', e.message)
    await page.screenshot({ path: '/tmp/shot-hr-error.png', fullPage: true }).catch(() => {})
  } finally {
    await browser.close()
    console.log('\n' + (failed === 0 ? '全部通过' : `失败 ${failed} 项`))
    process.exit(failed === 0 ? 0 : 1)
  }
})()
