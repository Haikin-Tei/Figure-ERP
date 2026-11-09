const { chromium } = require('playwright-core')
const https = require('https')
const http = require('http')
const fs = require('fs')

/** 生成 3 张 1x1 PNG 供上传用例使用（不依赖 /tmp 里已有的临时文件） */
function ensureTestImages() {
  const png =
    'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg=='
  const buf = Buffer.from(png, 'base64')
  for (const f of ['/tmp/t1.png', '/tmp/t2.png', '/tmp/t3.png']) {
    fs.writeFileSync(f, buf)
  }
}

const BASE = 'http://localhost:5173'
const API = 'http://localhost:8080'

// 生成唯一货号，避免重复跑 E2E 触发唯一约束冲突
const RUN_ID = Date.now().toString().slice(-8)
const TEST_CODE = `E2E-${RUN_ID}`
const TEST_NAME = `E2E测试手办_${RUN_ID}`

function api(method, path, token, body) {
  return new Promise((resolve, reject) => {
    const data = body ? JSON.stringify(body) : null
    const url = new URL(API + path)
    const req = http.request(
      {
        hostname: url.hostname,
        port: url.port,
        path: url.pathname + url.search,
        method,
        headers: {
          'Content-Type': 'application/json',
          ...(token ? { Authorization: `Bearer ${token}` } : {}),
          ...(data ? { 'Content-Length': Buffer.byteLength(data) } : {}),
        },
      },
      (res) => {
        let raw = ''
        res.on('data', (c) => (raw += c))
        res.on('end', () => {
          try {
            resolve(JSON.parse(raw))
          } catch {
            resolve(raw)
          }
        })
      }
    )
    req.on('error', reject)
    if (data) req.write(data)
    req.end()
  })
}

;(async () => {
  ensureTestImages()
  const browser = await chromium.launch({ headless: true })
  const page = await browser.newPage({ viewport: { width: 1440, height: 900 } })
  const logs = []
  page.on('console', (m) => logs.push(`[console.${m.type()}] ${m.text()}`))
  page.on('pageerror', (e) => logs.push(`[pageerror] ${e.message}`))

  let token = ''
  let createdId = null

  try {
    // 0) 登录拿 token（用于事后 API 校验 + 清理）
    const login = await api('POST', '/api/auth/login', '', { username: 'admin', password: 'admin123' })
    token = login?.data?.token
    if (!token) throw new Error('登录获取 token 失败')
    console.log('✓ 登录成功拿到 token（用于 API 校验与清理）')

    // 1) 登录前端
    await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' })
    await page.fill('input[placeholder="登录账号"]', 'admin')
    await page.fill('input[placeholder="登录密码"]', 'admin123')
    await page.click('button.submit-btn')
    await page.waitForURL(/\/(home|dashboard)$/, { timeout: 10000 })
    console.log('✓ 前端登录成功，跳转到 dashboard')

    // 2) 菜单里应有「商品维护」
    await page.waitForSelector('.sidebar-menu', { timeout: 10000 })
    const menuText = await page.locator('.sidebar-menu').innerText()
    if (!menuText.includes('商品维护')) throw new Error('左侧菜单未出现「商品维护」')
    console.log('✓ 菜单出现「商品维护 / 商品列表」')
    await page.screenshot({ path: '/tmp/shot-menu.png' })

    // 3) 进入商品列表页
    await page.goto(`${BASE}/product/list`, { waitUntil: 'networkidle' })
    await page.waitForSelector('.el-table', { timeout: 10000 })
    await page.waitForTimeout(800)
    const rowCount = await page.locator('.el-table__body tr').count()
    console.log(`✓ 商品列表加载，当前行数 = ${rowCount}`)

    // 4) 验证「带盒重量」列展示（任意商品都应有「xxx g」）
    const weightText = await page.locator('.el-table__body').innerText()
    const weightHit = weightText.match(/(\d+)\s*g/)
    if (!weightHit) throw new Error('表格未展示带盒重量（xxx g）')
    console.log(`✓ 表格「带盒重量」列正常展示（${weightHit[0].trim()}）`)
    await page.screenshot({ path: '/tmp/shot-list.png' })

    // 5) 新增商品
    await page.click('button:has-text("新增商品")')
    await page.waitForSelector('.el-dialog', { timeout: 5000 })
    await page.fill('.el-dialog .el-form-item:has-text("商品名称") input', TEST_NAME)
    await page.fill('.el-dialog .el-form-item:has-text("货号") input', TEST_CODE)
    const weightInput = page.locator('.el-dialog .el-form-item:has-text("带盒重量") input')
    await weightInput.fill('1500')
    const fileInput = page.locator('.el-dialog .el-upload input[type="file"]')
    await fileInput.setInputFiles(['/tmp/t1.png', '/tmp/t2.png', '/tmp/t3.png'])
    await page.waitForFunction(
      () => document.querySelectorAll('.el-dialog .el-upload-list--picture-card li.is-success').length >= 3,
      { timeout: 15000 }
    )
    console.log('✓ 3 张图片上传成功（el-upload 自定义 http-request）')
    await page.screenshot({ path: '/tmp/shot-dialog.png' })

    // 6) 保存
    await page.click('.el-dialog button:has-text("保存")')
    await page.waitForTimeout(1500)

    const dialogStillOpen = await page.locator('.el-dialog').count()
    const bodyHasNew = await page.locator('body').innerText().then((t) => t.includes(TEST_NAME))
    console.log(`[diag] dialogOpen=${dialogStillOpen} bodyHasNew=${bodyHasNew}`)
    await page.screenshot({ path: '/tmp/shot-after-save.png' })

    if (dialogStillOpen || !bodyHasNew) {
      throw new Error('保存后新商品未出现在列表（见诊断信息），可能表单校验未通过')
    }
    console.log('✓ 新商品已出现在列表')

    // 7) 通过 API 校验落库数据：图片必须是真实 /uploads/ 地址（验证 imageUrls 修复）
    const list = await api('GET', `/api/product/page?code=${TEST_CODE}&page=1&size=10`, token)
    const record = list?.data?.records?.[0]
    if (!record) throw new Error('API 未查到刚创建的问题商品')
    createdId = record.id
    const imgs = record.images || []
    console.log(`[diag] 落库 images = ${JSON.stringify(imgs)}`)
    if (imgs.length !== 3) throw new Error(`落库图片数量应为 3，实际 ${imgs.length}`)
    if (!imgs.every((u) => u.startsWith('/uploads/'))) {
      throw new Error('落库图片含非 /uploads/ 的真实地址（blob: 或相对路径 bug 未修复）')
    }
    console.log('✓ 落库图片均为真实 /uploads/ 地址（imageUrls 修复生效，无 blob: bug）')
    // 校验图片可直接访问
    const img0 = imgs[0]
    const code = await new Promise((r) =>
      http.get(`${API}${img0}`, (res) => r(res.statusCode)).on('error', () => r(-1))
    )
    if (code !== 200) throw new Error(`图片不可访问，HTTP ${code}: ${img0}`)
    console.log(`✓ 落库图片可访问 HTTP 200：${img0}`)

    // 8) 验证新行带 3 张缩略图
    const newRow = page.locator('.el-table__body tr', { hasText: TEST_NAME }).first()
    const thumbs = await newRow.locator('.img-gallery .thumb').count()
    if (thumbs !== 3) throw new Error(`新商品行缩略图应为 3，实际 ${thumbs}`)
    console.log(`✓ 新商品行图片缩略图数量 = ${thumbs}`)
    await page.screenshot({ path: '/tmp/shot-after-create.png' })

    console.log('\n==== E2E 全部通过 ====')
  } catch (e) {
    console.error('E2E 失败:', e.message)
    try {
      await page.screenshot({ path: '/tmp/shot-error.png', fullPage: true })
    } catch {}
    console.error('最近日志:\n' + logs.slice(-25).join('\n'))
    process.exitCode = 1
  } finally {
    // 9) 清理：删除本次创建的商品，避免污染数据库与后续 E2E 冲突
    if (token && createdId) {
      try {
        const del = await api('DELETE', `/api/product/${createdId}`, token)
        console.log(`[cleanup] 删除测试商品 id=${createdId} -> ${JSON.stringify(del?.message || del)}`)
      } catch (e) {
        console.error('[cleanup] 删除失败:', e.message)
      }
    } else if (token) {
      // 已保存但拿不到 id 时也尝试按货号删除
      try {
        const list = await api('GET', `/api/product/page?code=${TEST_CODE}&page=1&size=10`, token)
        const rec = list?.data?.records?.[0]
        if (rec) {
          const del = await api('DELETE', `/api/product/${rec.id}`, token)
          console.log(`[cleanup] 按货号删除测试商品 id=${rec.id} -> ${JSON.stringify(del?.message || del)}`)
        }
      } catch (e) {
        console.error('[cleanup] 按货号删除失败:', e.message)
      }
    }
    await browser.close()
  }
})()
