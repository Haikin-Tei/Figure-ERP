/**
 * 端到端验证：库存管理 → 仓库设置 → 区域配置 页面已接入后端，并顺带验证货架页的「所属区域」。
 *
 * 覆盖「实时更改」：筛选走服务端 → 新增后列表立即出现 → 编辑后立即显示新值
 * → 删除后列表立即消失；每步都用接口回查确认真的落库。
 * 测试数据自建自清。最后检查货架页的区域列与区域下拉。
 */
const { chromium } = require('playwright-core')

const BASE = 'http://localhost:5173'
const API = 'http://localhost:8080'
const RUN = Date.now().toString().slice(-8)
const CODE = `EA-UI-${RUN}`
const NAME = `E2E区_${RUN}`
const NAME_EDITED = `E2E区_${RUN}_改名`

async function loginApi() {
  const r = await fetch(`${API}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: 'admin', password: 'admin123' })
  })
  const j = await r.json()
  return j?.data?.token
}

async function findByCode(token, code) {
  const r = await fetch(`${API}/api/area/page?page=1&size=50&keyword=${encodeURIComponent(code)}`, {
    headers: { Authorization: `Bearer ${token}` }
  })
  const j = await r.json()
  return j?.data?.records?.find((x) => x.areaCode === code) || null
}

async function areaTotal(token) {
  const r = await fetch(`${API}/api/area/page?page=1&size=1`, {
    headers: { Authorization: `Bearer ${token}` }
  })
  const j = await r.json()
  return j?.data?.total ?? -1
}

;(async () => {
  const browser = await chromium.launch({ headless: true })
  const page = await browser.newPage({ viewport: { width: 1440, height: 900 } })
  const logs = []
  page.on('pageerror', (e) => logs.push(`[pageerror] ${e.message}`))

  let token = ''
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
    const total0 = await areaTotal(token)

    // ---- 前端登录 ----
    await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' })
    await page.fill('input[placeholder="登录账号"]', 'admin')
    await page.fill('input[placeholder="登录密码"]', 'admin123')
    await page.click('button.submit-btn')
    await page.waitForURL(/\/(home|dashboard)$/, { timeout: 10000 })

    // ---- 区域配置页 ----
    await page.goto(`${BASE}/stock/area`, { waitUntil: 'networkidle' })
    await page.waitForSelector('.el-table__body tr', { timeout: 15000 })
    await page.waitForTimeout(600)

    const rowCount0 = await page.locator('.el-table__body tr').count()
    console.log(`· 初始表格行数 = ${rowCount0}`)
    check(rowCount0 === 3, `区域列表从后端加载到 3 条（实际 ${rowCount0}）`)

    const bodyText = await page.locator('.el-table').innerText()
    check(bodyText.includes('A区-核心展示柜'), '列表显示种子数据「A区-核心展示柜」')
    check(bodyText.includes('上海') || bodyText.includes('3101'), '列表显示所属仓库')
    await page.screenshot({ path: '/tmp/shot-area-list.png' })

    // ---- 筛选：状态 = 停用（初始数据只有 C区 是停用）----
    await page.locator('.filter-bar .el-select').nth(1).click()
    await page.click('.el-select-dropdown:visible .el-select-dropdown__item:has-text("停用")')
    await page.click('.filter-bar button:has-text("查询")')
    await page.waitForTimeout(800)
    const disabledRows = await page.locator('.el-table__body tr').count()
    check(disabledRows === 1, `筛选「停用」后只剩 1 条（实际 ${disabledRows}）`)

    await page.click('.filter-bar button:has-text("重置")')
    await page.waitForTimeout(800)
    const afterReset = await page.locator('.el-table__body tr').count()
    check(afterReset === 3, `重置后回到 3 条（实际 ${afterReset}）`)

    // ---- 新增 ----
    await page.click('button:has-text("新增区域")')
    const dlg = page.locator('.el-dialog:visible')
    await dlg.waitFor({ timeout: 8000 })
    await dlg.locator('input[placeholder="如 A，同一仓库内唯一"]').fill(CODE)
    await dlg.locator('input[placeholder="如 A区-核心展示柜"]').fill(NAME)
    await dlg.locator('input[placeholder="例如：一层-主墙面"]').fill('一层-UI测试位')

    // 所属仓库：第一个下拉
    await dlg.locator('.el-select').first().click()
    await page.locator('.el-select-dropdown:visible .el-select-dropdown__item').first().click()
    await page.waitForTimeout(200)

    await dlg.locator('button:has-text("保存")').click()
    await page.waitForSelector(`.el-table__body tr:has-text("${CODE}")`, { timeout: 10000 })
    check(true, '新增后列表立即出现新区域')
    const created = await findByCode(token, CODE)
    check(!!created, '新增数据已落库（接口可查到）')
    check(created?.location === '一层-UI测试位', `物理位置落库正确（${created?.location}）`)
    await page.screenshot({ path: '/tmp/shot-area-created.png' })

    // ---- 给新区域挂一个货架，验证「已放货架」与删除保护 ----
    const shelfResp = await fetch(`${API}/api/shelf`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
      body: JSON.stringify({
        shelfCode: `SH-UI-${RUN}`,
        warehouseId: created.warehouseId,
        areaId: created.id,
        location: 'UI区-1层',
        status: 'A'
      })
    })
    const shelfJson = await shelfResp.json()
    check(shelfJson.code === 200, '接口为区域挂一个货架成功')

    await page.reload({ waitUntil: 'networkidle' })
    await page.waitForSelector('.el-table__body tr', { timeout: 15000 })
    await page.waitForTimeout(500)
    const newRowText = await page.locator(`.el-table__body tr:has-text("${CODE}")`).innerText()
    check(/1 \/ \d+/.test(newRowText), `列表显示「已放货架 / 容量」（行文本：${newRowText.replace(/\s+/g, ' ')}）`)

    // ---- 编辑 ----
    await page.locator(`.el-table__body tr:has-text("${CODE}") button:has-text("编辑")`).click()
    const editDlg = page.locator('.el-dialog:visible')
    await editDlg.waitFor({ timeout: 8000 })
    await editDlg.locator('input[placeholder="如 A区-核心展示柜"]').fill(NAME_EDITED)
    await editDlg.locator('button:has-text("保存")').click()
    await page.waitForSelector(`.el-table__body tr:has-text("${NAME_EDITED}")`, { timeout: 10000 })
    check(true, '编辑后列表立即显示新名称')
    const edited = await findByCode(token, CODE)
    check(edited?.areaName === NAME_EDITED, `改名已落库（${edited?.areaName}）`)

    // ---- 删除保护：区域下有货架时接口拦住，页面给出错误提示 ----
    await page.locator(`.el-table__body tr:has-text("${CODE}") button:has-text("删除")`).click()
    await page.locator('.el-message-box__btns button:has-text("删除")').click()
    await page.waitForTimeout(1000)
    const stillThere = await findByCode(token, CODE)
    check(!!stillThere, '区域下还有货架时删除被拦截（数据仍在）')

    // ---- 清掉货架后再删区域 ----
    await fetch(`${API}/api/shelf/${shelfJson.data}`, {
      method: 'DELETE',
      headers: { Authorization: `Bearer ${token}` }
    })
    await page.locator(`.el-table__body tr:has-text("${CODE}") button:has-text("删除")`).click()
    await page.locator('.el-message-box__btns button:has-text("删除")').click()
    await page.waitForSelector(`.el-table__body tr:has-text("${CODE}")`, { state: 'detached', timeout: 10000 })
    check(true, '删除后列表立即消失')
    check(!(await findByCode(token, CODE)), '删除已落库（接口查不到）')

    // ---- 货架页：区域列与区域下拉 ----
    await page.goto(`${BASE}/stock/shelves`, { waitUntil: 'networkidle' })
    await page.waitForSelector('.el-table__body tr', { timeout: 15000 })
    await page.waitForTimeout(500)
    const shelfHeader = await page.locator('.el-table__header').innerText()
    check(shelfHeader.includes('所属区域'), '货架列表新增「所属区域」列')

    await page.click('button:has-text("新增货架")')
    const shelfDlg = page.locator('.el-dialog:visible')
    await shelfDlg.waitFor({ timeout: 8000 })
    // 选仓库 → 区域下拉应可用且能选到该仓库的区域
    await shelfDlg.locator('.el-select').first().click()
    await page.locator('.el-select-dropdown:visible .el-select-dropdown__item').first().click()
    // 等仓库下拉的收起动画结束，否则紧接着点区域下拉会被之前的 popper 抢焦点
    await page.waitForTimeout(700)
    const areaSelectDisabled = await shelfDlg.locator('.el-select').nth(1).getAttribute('class')
    check(!String(areaSelectDisabled).includes('is-disabled'), '选了仓库后区域下拉可用')
    await shelfDlg.locator('.el-select').nth(1).click()
    await page.waitForTimeout(700)
    const areaOptionCount = await page.locator('.el-select-dropdown:visible .el-select-dropdown__item').count()
    check(areaOptionCount > 0, `区域下拉有 ${areaOptionCount} 个选项（按仓库过滤）`)
    await page.locator('.el-select-dropdown:visible .el-select-dropdown__item').first().click()
    await shelfDlg.locator('button:has-text("取消")').click()
    await page.screenshot({ path: '/tmp/shot-shelf-area.png' })

    check(logs.length === 0, `页面无 JS 报错${logs.length ? '：' + logs.join(' | ') : ''}`)

    const totalEnd = await areaTotal(token)
    check(totalEnd === total0, `区域总数回到初始值（${total0} → ${totalEnd}）`)
  } catch (e) {
    console.error('脚本异常：', e)
    fails.push(String(e))
    await page.screenshot({ path: '/tmp/shot-area-error.png' }).catch(() => {})
  } finally {
    await browser.close()
  }

  console.log(`\n结果：通过 ${fails.length === 0 ? '全部' : '部分'}，失败 ${fails.length} 项`)
  if (fails.length) {
    fails.forEach((f) => console.error(' - ' + f))
    process.exit(1)
  }
})()
