/**
 * 接口级验证：库存管理 → 仓库设置 → 区域配置 已接入后端。
 *
 * 覆盖：
 *   1. 列表 / 关键字 / 仓库 / 状态筛选
 *   2. 新增、编辑、改状态、删除（含编号同仓库唯一、跨仓库可重号）
 *   3. 参数校验：非法状态、缺仓库、仓库不存在、容量为负
 *   4. 区域与货架的联动：货架挂区域、跨仓库区域被拦、区域内还有货架不许删
 *   5. 仓库删除保护：仓库下还有区域时返回 1000
 *   6. 执行级账号只读：新增 / 删除返回 403
 *
 * 所有测试数据自建自清，结束后库里区域数量与开始时一致。
 */
const API = 'http://127.0.0.1:8080'
const RUN = Date.now().toString().slice(-8)
const CODE = `EA-${RUN}`
const CODE_OTHER = `EB-${RUN}`
const WH_CODE = `W9${RUN.slice(-4)}`
const STAFF = `area_e2e_${RUN.slice(-6)}`

let pass = 0
let fail = 0
const failures = []

function check(name, ok, extra = '') {
  if (ok) {
    pass++
    console.log(`  ✓ ${name}`)
  } else {
    fail++
    failures.push(name + (extra ? ` | ${extra}` : ''))
    console.log(`  ✗ ${name}${extra ? ' | ' + extra : ''}`)
  }
}

async function call(method, path, token, body) {
  const headers = { 'Content-Type': 'application/json' }
  if (token) headers.Authorization = `Bearer ${token}`
  const r = await fetch(API + path, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body)
  })
  let json = null
  try {
    json = await r.json()
  } catch {
    /* 204 等无 body */
  }
  return { status: r.status, ...(json || {}) }
}

async function login(username, password) {
  const r = await call('POST', '/api/auth/login', null, { username, password })
  return r?.data?.token || null
}

async function areaTotal(token) {
  const r = await call('GET', '/api/area/page?page=1&size=1', token)
  return r?.data?.total ?? -1
}

async function findArea(token, code) {
  const r = await call(
    'GET',
    `/api/area/page?page=1&size=50&keyword=${encodeURIComponent(code)}`,
    token
  )
  return (r?.data?.records || []).find((x) => x.areaCode === code) || null
}

async function main() {
  const admin = await login('admin', 'admin123')
  if (!admin) throw new Error('管理员登录失败')
  const startTotal = await areaTotal(admin)
  console.log(`\n初始化：区域总数 ${startTotal}`)

  /* ---------- 1. 列表与筛选 ---------- */
  console.log('\n[1] 列表 / 筛选')
  const page1 = await call('GET', '/api/area/page?page=1&size=10', admin)
  check('分页返回 200 且带 records/total', page1.code === 200 && Array.isArray(page1.data.records))
  check('初始 3 条区域落在库中', page1.data.total >= 3, `total=${page1.data.total}`)

  const sample = page1.data.records[0]
  check('出参含仓库代码/仓库名称（后端关联）', !!sample.warehouseCode && !!sample.warehouseName)
  check('出参含中文状态文案', ['使用中', '停用'].includes(sample.statusText), sample.statusText)
  check('出参含已放货架数 shelfCount', typeof sample.shelfCount === 'number')

  const whId = sample.warehouseId
  const byWh = await call('GET', `/api/area/page?page=1&size=50&warehouseId=${whId}`, admin)
  check(
    '按仓库筛选只返回该仓库的区域',
    byWh.data.records.every((x) => x.warehouseId === whId) && byWh.data.total >= 1
  )

  const byStatus = await call('GET', '/api/area/page?page=1&size=50&status=D', admin)
  check('按状态筛选只返回停用区域', byStatus.data.records.every((x) => x.status === 'D'))

  const byKeyword = await call('GET', '/api/area/page?page=1&size=50&keyword=' + encodeURIComponent('展示柜'), admin)
  check('关键字模糊匹配区域名称', byKeyword.data.total >= 1, `total=${byKeyword.data.total}`)

  /* ---------- 2. 新增 ---------- */
  console.log('\n[2] 新增区域')
  const created = await call('POST', '/api/area', admin, {
    areaCode: CODE,
    areaName: `E2E区-${RUN}`,
    warehouseId: whId,
    location: '一层-测试位',
    capacity: 12,
    status: 'A',
    remark: '自动化测试'
  })
  check('新增返回 200 + 新 ID', created.code === 200 && typeof created.data === 'number', JSON.stringify(created))
  const areaId = created.data

  const detail = await call('GET', `/api/area/${areaId}`, admin)
  check('详情字段落库正确', detail.data?.areaCode === CODE && detail.data?.capacity === 12)
  check('新建区域 shelfCount = 0', detail.data?.shelfCount === 0)

  const dup = await call('POST', '/api/area', admin, {
    areaCode: CODE,
    areaName: '重号测试',
    warehouseId: whId,
    status: 'A'
  })
  check('同仓库区域编号重复被拦（1002）', dup.code === 1002, JSON.stringify(dup))

  // 换一个仓库，同编号应该可以建
  const whPage = await call('GET', '/api/warehouse/page?page=1&size=200', admin)
  const otherWh = whPage.data.records.find((w) => w.id !== whId)
  const crossWh = await call('POST', '/api/area', admin, {
    areaCode: CODE,
    areaName: '跨仓库重号',
    warehouseId: otherWh.id,
    status: 'A'
  })
  check('不同仓库可以重号（200）', crossWh.code === 200, JSON.stringify(crossWh))
  if (crossWh.data) await call('DELETE', `/api/area/${crossWh.data}`, admin)

  /* ---------- 3. 参数校验 ---------- */
  console.log('\n[3] 参数校验')
  const badStatus = await call('POST', '/api/area', admin, {
    areaCode: CODE_OTHER,
    areaName: '非法状态',
    warehouseId: whId,
    status: 'X'
  })
  check('非法状态被拦（1003）', badStatus.code === 1003, JSON.stringify(badStatus))

  const noWh = await call('POST', '/api/area', admin, {
    areaCode: CODE_OTHER,
    areaName: '缺仓库',
    status: 'A'
  })
  check('缺所属仓库被拦（1003）', noWh.code === 1003, JSON.stringify(noWh))

  const noName = await call('POST', '/api/area', admin, {
    areaCode: CODE_OTHER,
    warehouseId: whId
  })
  check('缺区域名称被拦（1003）', noName.code === 1003, JSON.stringify(noName))

  const badWh = await call('POST', '/api/area', admin, {
    areaCode: CODE_OTHER,
    areaName: '仓库不存在',
    warehouseId: 999999,
    status: 'A'
  })
  check('仓库不存在被拦（1001）', badWh.code === 1001, JSON.stringify(badWh))

  const badCapacity = await call('POST', '/api/area', admin, {
    areaCode: CODE_OTHER,
    areaName: '负容量',
    warehouseId: whId,
    capacity: -5,
    status: 'A'
  })
  check('容量为负被拦（1003）', badCapacity.code === 1003, JSON.stringify(badCapacity))

  /* ---------- 4. 编辑 / 改状态 ---------- */
  console.log('\n[4] 编辑 / 改状态')
  const updated = await call('PUT', `/api/area/${areaId}`, admin, {
    areaCode: CODE,
    areaName: `E2E区-改后-${RUN}`,
    warehouseId: whId,
    location: '二层-测试位',
    capacity: 30,
    status: 'A'
  })
  check('编辑返回 200', updated.code === 200, JSON.stringify(updated))

  const afterUpdate = await call('GET', `/api/area/${areaId}`, admin)
  check(
    '编辑后名称/容量/位置都更新',
    afterUpdate.data.areaName === `E2E区-改后-${RUN}` &&
      afterUpdate.data.capacity === 30 &&
      afterUpdate.data.location === '二层-测试位'
  )

  const toDisabled = await call('PUT', `/api/area/${areaId}/status?status=D`, admin)
  check('改状态为停用返回 200', toDisabled.code === 200)
  check('状态已落库为 D', (await call('GET', `/api/area/${areaId}`, admin)).data.status === 'D')

  const badStatus2 = await call('PUT', `/api/area/${areaId}/status?status=B`, admin)
  check('改状态传 B 被拦（1003）', badStatus2.code === 1003, JSON.stringify(badStatus2))

  /* ---------- 5. 货架联动 ---------- */
  console.log('\n[5] 货架挂区域')
  const otherArea = whPage.data.records.length
    ? (await call('GET', '/api/area/page?page=1&size=50', admin)).data.records.find(
        (a) => a.warehouseId !== whId
      )
    : null
  if (otherArea) {
    const crossShelf = await call('POST', '/api/shelf', admin, {
      shelfCode: `SH-A${RUN}`,
      warehouseId: whId,
      areaId: otherArea.id,
      location: '跨仓库区域',
      status: 'A'
    })
    check('货架挂别仓库的区域被拦（1003）', crossShelf.code === 1003, JSON.stringify(crossShelf))
  } else {
    check('货架挂别仓库的区域被拦（1003）', false, '缺少其他仓库的区域，跳过')
  }

  const shelf = await call('POST', '/api/shelf', admin, {
    shelfCode: `SH-A${RUN}`,
    warehouseId: whId,
    areaId,
    location: '区域测试位',
    status: 'A'
  })
  check('货架挂同仓库区域可创建（200）', shelf.code === 200, JSON.stringify(shelf))
  const shelfId = shelf.data

  const shelfPage = await call('GET', `/api/shelf/page?page=1&size=50&areaId=${areaId}`, admin)
  check('按区域筛选货架能查到', shelfPage.data.total === 1, `total=${shelfPage.data.total}`)
  check(
    '货架列表回填了区域名称',
    shelfPage.data.records[0]?.areaName === `E2E区-改后-${RUN}`,
    shelfPage.data.records[0]?.areaName
  )

  const areaWithShelf = await call('GET', `/api/area/${areaId}`, admin)
  check('区域的已放货架数变成 1', areaWithShelf.data.shelfCount === 1, `shelfCount=${areaWithShelf.data.shelfCount}`)

  const delBusyArea = await call('DELETE', `/api/area/${areaId}`, admin)
  check('区域内还有货架时不许删（1000）', delBusyArea.code === 1000, JSON.stringify(delBusyArea))

  /* ---------- 6. 仓库删除保护 ---------- */
  console.log('\n[6] 仓库删除保护')
  const whCreated = await call('POST', '/api/warehouse', admin, {
    warehouseCode: WH_CODE,
    warehouseName: `E2E临时仓-${RUN}`,
    isReturn: 'N',
    status: 'A'
  })
  check('新建临时仓库（200）', whCreated.code === 200, JSON.stringify(whCreated))
  const tempWhId = whCreated.data

  const tempArea = await call('POST', '/api/area', admin, {
    areaCode: 'T1',
    areaName: '临时区域',
    warehouseId: tempWhId,
    status: 'A'
  })
  check('临时仓库下建区域（200）', tempArea.code === 200, JSON.stringify(tempArea))

  const delWh = await call('DELETE', `/api/warehouse/${tempWhId}`, admin)
  check('仓库下还有区域时不许删（1000）', delWh.code === 1000, JSON.stringify(delWh))

  /* ---------- 7. 执行级只读 ---------- */
  console.log('\n[7] 执行级账号只读')
  const userCreated = await call('POST', '/api/system/users', admin, {
    username: STAFF,
    realName: '区域只读测试',
    password: 'Test@123456',
    role: 'FINANCE_STAFF',
    status: 1
  })
  check('创建执行级测试账号', userCreated.code === 200, JSON.stringify(userCreated))
  const staffToken = await login(STAFF, 'Test@123456')
  check('执行级账号可登录', !!staffToken)

  const staffList = await call('GET', '/api/area/page?page=1&size=10', staffToken)
  check('执行级可以查询区域', staffList.code === 200)

  const staffCreate = await call('POST', '/api/area', staffToken, {
    areaCode: 'ZZ1',
    areaName: '越权新增',
    warehouseId: whId,
    status: 'A'
  })
  check('执行级新增被拦（403）', staffCreate.code === 403, JSON.stringify(staffCreate))

  const staffDelete = await call('DELETE', `/api/area/${areaId}`, staffToken)
  check('执行级删除被拦（403）', staffDelete.code === 403, JSON.stringify(staffDelete))

  /* ---------- 8. 清理 ---------- */
  console.log('\n[8] 清理测试数据')
  if (shelfId) await call('DELETE', `/api/shelf/${shelfId}`, admin)
  const delArea = await call('DELETE', `/api/area/${areaId}`, admin)
  check('无货架后区域可删除（200）', delArea.code === 200, JSON.stringify(delArea))
  if (tempArea.data) await call('DELETE', `/api/area/${tempArea.data}`, admin)
  await call('DELETE', `/api/warehouse/${tempWhId}`, admin)
  await call('DELETE', `/api/system/users/${userCreated.data}`, admin)

  const endTotal = await areaTotal(admin)
  check('区域总数回到初始值', endTotal === startTotal, `start=${startTotal} end=${endTotal}`)

  console.log(`\n结果：通过 ${pass} 项，失败 ${fail} 项`)
  if (fail) {
    console.log('失败项：')
    failures.forEach((f) => console.log(' - ' + f))
    process.exit(1)
  }
}

main().catch((e) => {
  console.error('脚本异常：', e)
  process.exit(1)
})
