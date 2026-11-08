/**
 * 回归测试：商品列表的图片预览层（el-image-viewer）不能被表格遮挡。
 *
 * 背景：el-image 默认把预览层渲染在表格单元格内部，会受 .el-table 的
 * overflow / 层叠上下文影响（td 有 position: relative + z-index），表现为
 * 「点开图片被表格盖住 / 穿模」。修复方式是给 el-image 加 preview-teleported，
 * 让预览层 teleport 到 body。
 *
 * 本脚本自包含：自动登录 → 上传 3 张图 → 创建商品 → 打开列表点图 →
 * 校验预览层 → 删除商品与图片。
 *
 * 判定标准：
 *  1. .el-image-viewer__wrapper 的父节点是 <body>（teleport 生效）
 *  2. 预览层 position: fixed 且铺满视口
 *  3. 视口中心点 elementFromPoint 命中的元素在预览层内（未被表格遮挡）
 */
const fs = require('fs')
const os = require('os')
const path = require('path')
const { chromium } = require('playwright-core')

const BASE = 'http://localhost:5173'
const API = 'http://localhost:8080'
const RUN = Date.now().toString().slice(-8)
const CODE = `PVT-${RUN}`
const NAME = `预览层级测试_${RUN}`

// 1x1 透明 PNG，仅用于占位；层级验证与图片内容无关
const PNG_B64 =
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=='

function testPng(index) {
  const p = path.join(os.tmpdir(), `pv-test-${index}.png`)
  if (!fs.existsSync(p)) fs.writeFileSync(p, Buffer.from(PNG_B64, 'base64'))
  return p
}

async function login() {
  const r = await fetch(`${API}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: 'admin', password: 'admin123' }),
  })
  const j = await r.json()
  if (!j?.data?.token) throw new Error(`登录失败: ${JSON.stringify(j)}`)
  return j.data.token
}

async function uploadImage(token, filePath) {
  const fd = new FormData()
  fd.append('file', new Blob([fs.readFileSync(filePath)], { type: 'image/png' }), path.basename(filePath))
  const r = await fetch(`${API}/api/product/upload`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
    body: fd,
  })
  const j = await r.json()
  if (j?.code !== 200) throw new Error(`上传失败: ${JSON.stringify(j)}`)
  return j.data
}

async function createProduct(token, images) {
  const r = await fetch(`${API}/api/product`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify({
      name: NAME,
      code: CODE,
      brand: '测试品牌',
      series: '测试系列',
      characterName: '测试角色',
      material: 'PVC',
      lengthCm: 10,
      widthCm: 10,
      heightCm: 10,
      weightWithBox: 500,
      images,
      status: 1,
      remark: '图片预览层级回归测试数据，脚本结束自动删除',
    }),
  })
  const j = await r.json()
  if (j?.code !== 200) throw new Error(`创建商品失败: ${JSON.stringify(j)}`)
  return j.data
}

async function deleteProduct(token, id) {
  await fetch(`${API}/api/product/${id}`, { method: 'DELETE', headers: { Authorization: `Bearer ${token}` } })
}

;(async () => {
  const browser = await chromium.launch({ headless: true })
  const page = await browser.newPage({ viewport: { width: 1440, height: 900 } })
  const logs = []
  page.on('console', (m) => logs.push(`[console.${m.type()}] ${m.text()}`))
  page.on('pageerror', (e) => logs.push(`[pageerror] ${e.message}`))

  let token = ''
  let productId = null
  let imageUrls = []

  try {
    // ---------- 准备数据 ----------
    token = await login()
    console.log('✓ 登录成功拿到 token')

    imageUrls = []
    for (let i = 1; i <= 3; i++) imageUrls.push(await uploadImage(token, testPng(i)))
    console.log(`✓ 上传 3 张测试图: ${imageUrls.map((u) => u.split('/').pop()).join(', ')}`)

    productId = await createProduct(token, imageUrls)
    console.log(`✓ 创建测试商品 id=${productId} code=${CODE}`)

    // ---------- UI 验证 ----------
    await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' })
    await page.fill('input[placeholder="登录账号"]', 'admin')
    await page.fill('input[placeholder="登录密码"]', 'admin123')
    await page.click('button.submit-btn')
    await page.waitForURL(/\/(home|dashboard)$/, { timeout: 10000 })

    await page.goto(`${BASE}/product/list`, { waitUntil: 'networkidle' })
    await page.waitForSelector('.el-table', { timeout: 10000 })
    await page.waitForTimeout(800)

    const row = page.locator('.el-table__body tr', { hasText: NAME }).first()
    if ((await row.count()) === 0) throw new Error('列表未找到刚创建的商品行（可能不在第一页）')
    const thumbCount = await row.locator('.thumb').count()
    console.log(`✓ 找到商品行，缩略图数量 = ${thumbCount}`)

    await row.locator('.thumb').first().click()
    await page.waitForSelector('.el-image-viewer__wrapper', { timeout: 5000 })
    await page.waitForTimeout(600) // 等展开动画

    const info = await page.evaluate(() => {
      const viewer = document.querySelector('.el-image-viewer__wrapper')
      if (!viewer) return { found: false }
      const cs = getComputedStyle(viewer)
      const r = viewer.getBoundingClientRect()
      const hit = document.elementFromPoint(window.innerWidth / 2, window.innerHeight / 2)
      let p = hit
      let hitInViewer = false
      while (p) {
        if (p === viewer) { hitInViewer = true; break }
        p = p.parentElement
      }
      const cell = document.querySelector('.el-table__body td')
      const cellCs = cell ? getComputedStyle(cell) : null
      return {
        found: true,
        parentIsBody: viewer.parentElement === document.body,
        parentTag: viewer.parentElement ? viewer.parentElement.tagName : 'null',
        position: cs.position,
        zIndex: cs.zIndex,
        rect: {
          width: Math.round(r.width),
          height: Math.round(r.height),
        },
        viewport: { w: window.innerWidth, h: window.innerHeight },
        hitTag: hit ? `${hit.tagName}.${(hit.className || '').toString().trim().split(/\s+/).join('.')}` : 'null',
        hitInViewer,
        cellPosition: cellCs ? cellCs.position : null,
        cellZIndex: cellCs ? cellCs.zIndex : null,
      }
    })

    console.log('\n=== 预览层检测 ===')
    console.log(JSON.stringify(info, null, 2))
    await page.screenshot({ path: '/tmp/shot-preview.png' })

    const fails = []
    if (!info.found) fails.push('未渲染 .el-image-viewer__wrapper')
    if (!info.parentIsBody) fails.push(`预览层未 teleport 到 body（当前父节点 ${info.parentTag}），会被表格裁剪/遮挡`)
    if (info.position !== 'fixed') fails.push(`预览层 position=${info.position}，应为 fixed`)
    if (info.rect.width < info.viewport.w * 0.95 || info.rect.height < info.viewport.h * 0.95) {
      fails.push(`预览层未铺满视口：${info.rect.width}x${info.rect.height} vs ${info.viewport.w}x${info.viewport.h}`)
    }
    if (!info.hitInViewer) fails.push(`视口中心点命中的是 ${info.hitTag}，不在预览层内 —— 预览被表格遮挡`)

    if (fails.length) {
      console.error('\n✗ 验证失败：')
      fails.forEach((f) => console.error('  - ' + f))
      if (logs.length) console.error('\n浏览器日志:\n' + logs.join('\n'))
      process.exitCode = 1
    } else {
      console.log('\n✓ 通过：预览层已 teleport 到 body、铺满视口、位于最上层（未被表格遮挡）')
      console.log('  截图: /tmp/shot-preview.png')
    }
  } catch (e) {
    console.error('✗ 执行异常:', e.message)
    if (logs.length) console.error('浏览器日志:\n' + logs.join('\n'))
    process.exitCode = 1
  } finally {
    // ---------- 清理 ----------
    if (productId && token) {
      await deleteProduct(token, productId).catch(() => {})
      console.log(`\n· 已删除测试商品 id=${productId}`)
    }
    const uploadsRoot = path.join(__dirname, '..', '..', 'server')
    imageUrls.forEach((u) => {
      if (!u.startsWith('/uploads/')) return
      const f = path.join(uploadsRoot, u.replace(/^\//, ''))
      try { fs.unlinkSync(f) } catch { /* 文件可能已不存在 */ }
    })
    if (imageUrls.length) console.log(`· 已清理 ${imageUrls.length} 张测试图`)
    await browser.close()
  }
})()
