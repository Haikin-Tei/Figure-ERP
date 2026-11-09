/**
 * 验证面包屑：每个页面左上角显示的 1/2/3 级目录是否正确。
 *
 * 断言两层：
 *  1. 面包屑文本层级（含二级分组这种没有独立路由的节点，如「仓库设置」）
 *  2. 面包屑位置在内容区左上方
 *  3. 点击一级目录能回到该模块默认页
 */
const { chromium } = require('playwright-core')

const BASE = 'http://localhost:5173'

// [路由, 期望的层级文本数组]
const CASES = [
  ['/home', ['工作台']],
  ['/dashboard', ['数据概览']],
  ['/product/list', ['商品维护', '商品列表']],
  ['/stock/figure', ['库存管理', '手办列表']],
  ['/stock/location', ['库存管理', '仓库设置', '库位管理']],
  ['/stock/shelves', ['库存管理', '仓库设置', '货架管理']],
  ['/logistics/query', ['物流管理', '物流价格查询']],
  ['/order/list', ['订单管理', '所有订单']],
  ['/staff/maintain', ['员工权限管理', '员工维护']],
  ['/staff/attendance', ['员工权限管理', '员工考勤记录']],
  ['/staff/leave', ['员工权限管理', '员工请假申请']],
  ['/staff/overtime', ['员工权限管理', '员工加班申请']],
  ['/system/logs', ['系统设置', '操作日志']],
  ['/acg/catalog', ['目录维护', '作品目录及维护']],
  ['/acg/supplier', ['目录维护', '供应商目录维护']],
  ['/acg/key-customer', ['目录维护', '大客户目录维护']],
  ['/acg/manufacturer', ['目录维护', '厂商目录维护']],
]

;(async () => {
  const browser = await chromium.launch({ headless: true })
  const page = await browser.newPage({ viewport: { width: 1440, height: 900 } })
  const logs = []
  page.on('pageerror', (e) => logs.push(`[pageerror] ${e.message}`))

  let failed = 0

  try {
    // 登录
    await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' })
    await page.fill('input[placeholder="登录账号"]', 'admin')
    await page.fill('input[placeholder="登录密码"]', 'admin123')
    await page.click('button.submit-btn')
    await page.waitForURL(/\/(home|dashboard)$/, { timeout: 10000 })
    console.log('✓ 登录成功\n')

    for (const [path, expected] of CASES) {
      await page.goto(`${BASE}${path}`, { waitUntil: 'domcontentloaded' })
      await page.waitForSelector('.breadcrumb-bar .el-breadcrumb__inner', { timeout: 10000 })
      await page.waitForTimeout(150)

      const actual = await page.$$eval('.breadcrumb-bar .el-breadcrumb__inner', (nodes) =>
        nodes.map((n) => n.textContent.trim())
      )

      const ok = actual.length === expected.length && actual.every((t, i) => t === expected[i])
      if (ok) {
        console.log(`✓ ${path.padEnd(20)} → ${actual.join(' / ')}`)
      } else {
        failed++
        console.error(`✗ ${path}`)
        console.error(`    期望: ${expected.join(' / ')}`)
        console.error(`    实际: ${actual.join(' / ')}`)
      }
    }

    // 位置检查：面包屑应在内容区左上（x 起点小于视口一半，且在 tags-view 下方）
    await page.goto(`${BASE}/stock/location`, { waitUntil: 'domcontentloaded' })
    await page.waitForSelector('.breadcrumb-bar', { timeout: 10000 })
    const pos = await page.evaluate(() => {
      const bar = document.querySelector('.breadcrumb-bar')
      const tags = document.querySelector('.tags-view-container')
      const r = bar.getBoundingClientRect()
      const tr = tags ? tags.getBoundingClientRect() : null
      return {
        left: Math.round(r.left),
        top: Math.round(r.top),
        width: Math.round(r.width),
        tagsBottom: tr ? Math.round(tr.bottom) : null,
      }
    })
    console.log('\n=== 位置 ===')
    console.log(JSON.stringify(pos))
    if (pos.left > 400) {
      failed++
      console.error('✗ 面包屑不在内容区左侧')
    } else if (pos.tagsBottom !== null && Math.abs(pos.top - pos.tagsBottom) > 2) {
      failed++
      console.error('✗ 面包屑没有紧贴在标签栏下方')
    } else {
      console.log('✓ 位于内容区左上角、紧贴标签栏下方')
    }

    await page.screenshot({ path: '/tmp/shot-breadcrumb-3level.png' })

    // 点击一级目录 → 应回到模块默认页（Element Plus 渲染为 span.is-link + router.push，不是 <a>）
    const firstInner = page.locator('.breadcrumb-bar .el-breadcrumb__inner').first()
    const isLink = await firstInner.evaluate((n) => n.classList.contains('is-link'))
    console.log(`\n· 一级目录 is-link = ${isLink}`)
    if (isLink) {
      await firstInner.click()
      await page.waitForTimeout(700)
      const url = page.url()
      console.log(`· 点击一级目录「库存管理」后 URL = ${url}`)
      if (!url.includes('/stock/')) {
        failed++
        console.error('✗ 点击一级目录未进入库存模块')
      } else {
        console.log('✓ 一级目录可点击并跳转到模块默认页')
      }
    } else {
      failed++
      console.error('✗ 一级目录未渲染为可点击状态')
    }

    await page.goto(`${BASE}/product/list`, { waitUntil: 'domcontentloaded' })
    await page.waitForSelector('.breadcrumb-bar', { timeout: 10000 })
    await page.screenshot({ path: '/tmp/shot-breadcrumb-2level.png' })

    // 区域配置已下线：菜单里不该再有这一项，库位管理 / 货架管理要还在
    await page.goto(`${BASE}/stock/location`, { waitUntil: 'domcontentloaded' })
    await page.waitForSelector('.sidebar', { timeout: 10000 })
    await page.waitForTimeout(400)
    const sidebarText = await page.locator('.sidebar').innerText()
    if (sidebarText.includes('区域配置')) {
      failed++
      console.error('✗ 侧边栏仍存在「区域配置」菜单')
    } else {
      console.log('✓ 侧边栏已无「区域配置」菜单')
    }
    if (sidebarText.includes('库位管理') && sidebarText.includes('货架管理')) {
      console.log('✓ 仓库设置下仍保留「库位管理」「货架管理」')
    } else {
      failed++
      console.error('✗ 仓库设置下的库位管理 / 货架管理菜单缺失')
    }

    // 直接访问旧地址应落到 404 页（路由已删除）
    await page.goto(`${BASE}/stock/area`, { waitUntil: 'domcontentloaded' })
    await page.waitForTimeout(600)
    const areaPageText = await page.locator('body').innerText()
    if (areaPageText.includes('区域编号') || areaPageText.includes('新增区域')) {
      failed++
      console.error('✗ /stock/area 仍渲染区域配置页面')
    } else {
      console.log('✓ /stock/area 已不再渲染区域配置页面')
    }
  } catch (e) {
    failed++
    console.error('✗ 执行异常:', e.message)
  } finally {
    if (logs.length) console.error('\n浏览器日志:\n' + logs.join('\n'))
    await browser.close()
  }

  console.log(failed === 0 ? '\n✓ 全部通过' : `\n✗ 失败 ${failed} 项`)
  process.exitCode = failed === 0 ? 0 : 1
})()
