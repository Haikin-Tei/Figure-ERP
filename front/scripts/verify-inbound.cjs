/**
 * 入库记录接口回归（库存管理 → 入库记录）
 *   1) 列表 / 详情 / 统计 / 关键字 / 状态 / 仓库 / 日期筛选
 *   2) 新增（自动单号）、编辑（明细整体替换、可清空可空字段）
 *   3) 业务校验：重复手办、跨仓库区域、数量 0、单号重复
 *   4) 状态流转：确认入库 → 不可编辑/删除；作废 → 不可再确认
 *   5) 权限：执行级账号不能新增
 *
 * 跑法：node scripts/verify-inbound.cjs
 * 依赖：后端 8080 + 前端代理不参与（直连后端），admin/admin123 可登录
 */
const BASE = 'http://localhost:8080'

let failed = 0
const check = (ok, label, extra = '') => {
  console.log((ok ? '✓' : '✗') + ' ' + label + (ok ? '' : ' → ' + extra))
  if (!ok) failed++
}

async function call(method, path, token, body) {
  const r = await fetch(BASE + path, {
    method,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: 'Bearer ' + token } : {})
    },
    ...(body !== undefined ? { body: JSON.stringify(body) } : {})
  })
  return r.json()
}

const pad = (n) => String(n).padStart(2, '0')
const today = (() => {
  const d = new Date()
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
})()

const TEST_TAG = '自动验证'
const createdIds = []
let tempUserId = null

async function main() {
  /* ---------- 登录 ---------- */
  const login = await call('POST', '/api/auth/login', null, { username: 'admin', password: 'admin123' })
  check(login?.code === 200, '管理员登录成功', JSON.stringify(login))
  const token = login?.data?.token
  if (!token) throw new Error('无 token，终止')

  /* ---------- 基础字典 ---------- */
  const products = await call('GET', '/api/product/page?page=1&size=5', token)
  const plist = products?.data?.records || []
  check(plist.length >= 2, '手办数据可读（至少 2 款）', JSON.stringify(products?.data?.total))
  const p1 = plist[0]
  const p2 = plist[1]

  const areas = await call('GET', '/api/area/page?page=1&size=50', token)
  const areaList = areas?.data?.records || []
  const areaSame = areaList.find((a) => a.warehouseId === 1)
  const areaOther = areaList.find((a) => a.warehouseId !== 1)
  check(!!areaSame, '存在上海库(1)下的区域', JSON.stringify(areaList.map((a) => [a.id, a.warehouseId])))
  check(!!areaOther, '存在其他仓库下的区域，用于跨仓库校验')

  /* ---------- 列表 / 统计 ---------- */
  const page1 = await call('GET', '/api/inbound/page?page=1&size=10', token)
  const list = page1?.data?.records || []
  check(page1?.code === 200 && page1.data.total >= 4, '列表返回演示单据', JSON.stringify(page1?.data?.total))
  check(
    list.every((o) => o.inboundNo && o.statusText && o.warehouseName),
    '列表带出单号 / 状态文案 / 仓库名称'
  )
  check(
    list.some((o) => o.productSummary),
    '列表带出手办摘要',
    JSON.stringify(list.map((o) => o.productSummary))
  )
  check(
    list.every((o) => !o.items),
    '列表不返回明细（避免一次拉回几百行）'
  )

  const summary = await call('GET', '/api/inbound/summary', token)
  const s = summary?.data
  check(summary?.code === 200 && s.pendingCount === 1, '统计：待入库 1 单', JSON.stringify(s))
  check(s?.monthOrderCount === 2, '统计：本月已入库 2 单', JSON.stringify(s))
  check(s?.monthQuantity === 27, '统计：本月入库 27 件', JSON.stringify(s))
  check(Number(s?.monthAmount) === 7960, '统计：本月入库金额 7960.00', JSON.stringify(s))

  const draft = list.find((o) => o.status === 'D')
  check(!!draft, '列表里存在待入库单')

  const detail = await call('GET', `/api/inbound/${draft.id}`, token)
  check(detail?.code === 200 && detail.data.items?.length === 2, '详情返回 2 条明细', JSON.stringify(detail?.data?.items?.length))
  check(detail?.data?.itemCount === 2, '详情明细行数正确')

  /* ---------- 筛选 ---------- */
  const byStatus = await call('GET', '/api/inbound/page?page=1&size=10&status=C', token)
  check(byStatus?.data?.total === 3, '按状态筛选：已入库 3 单', JSON.stringify(byStatus?.data?.total))

  const byWarehouse = await call('GET', '/api/inbound/page?page=1&size=10&warehouseId=1', token)
  check(byWarehouse?.data?.total >= 1, '按仓库筛选可用', JSON.stringify(byWarehouse?.data?.total))

  const byType = await call('GET', '/api/inbound/page?page=1&size=10&inboundType=R', token)
  check(byType?.data?.total === 1, '按类型筛选：退货入库 1 单', JSON.stringify(byType?.data?.total))

  const byDate = await call('GET', `/api/inbound/page?page=1&size=10&startDate=${today}&endDate=${today}`, token)
  check(byDate?.data?.total === 1, '按日期筛选：今天 1 单', JSON.stringify(byDate?.data?.total))

  const byProduct = await call('GET', '/api/inbound/page?page=1&size=10&keyword=' + encodeURIComponent(p2.name.slice(0, 4)), token)
  check(byProduct?.data?.total >= 0, '按手办名称模糊查询可用', JSON.stringify(byProduct?.data))

  const bySupplier = await call('GET', '/api/inbound/page?page=1&size=10&keyword=Good', token)
  check(bySupplier?.data?.total >= 1, '按供应商模糊查询可用', JSON.stringify(bySupplier?.data?.total))

  const noHit = await call('GET', '/api/inbound/page?page=1&size=10&keyword=' + encodeURIComponent('不存在的关键字xyz'), token)
  check(noHit?.data?.total === 0, '关键字无命中返回空列表', JSON.stringify(noHit?.data))

  /* ---------- 字典 ---------- */
  const dicts = await call('GET', '/api/inbound/dicts', token)
  check(
    dicts?.code === 200 && dicts.data.types?.length === 4 && dicts.data.statuses?.length === 3,
    '字典返回 4 种类型 + 3 种状态',
    JSON.stringify(dicts?.data)
  )

  /* ---------- 新增 ---------- */
  const createBody = () => ({
    inboundType: 'P',
    warehouseId: 1,
    areaId: areaSame?.id ?? null,
    shelfId: null,
    supplier: TEST_TAG + '供应商',
    operator: TEST_TAG + '员',
    remark: TEST_TAG,
    items: [
      { productId: p1.id, quantity: 2, unitPrice: 300, remark: TEST_TAG },
      { productId: p2.id, quantity: 1, unitPrice: 460 }
    ]
  })
  const created = await call('POST', '/api/inbound', token, createBody())
  check(created?.code === 200 && created.data > 0, '新增入库单成功', JSON.stringify(created))
  const testId = created?.data
  if (testId) createdIds.push(testId)

  const createdDetail = await call('GET', `/api/inbound/${testId}`, token)
  const cd = createdDetail?.data
  check(/^RK\d{12}$/.test(cd?.inboundNo || ''), '单号自动生成（RK + 8 位日期 + 4 位序号）', cd?.inboundNo)
  check(cd?.status === 'D' && cd?.statusText === '待入库', '新增默认状态为待入库', JSON.stringify(cd?.status))
  check(cd?.totalQuantity === 3, '数量合计由后端按明细计算 = 3', String(cd?.totalQuantity))
  check(Number(cd?.totalAmount) === 1060, '金额合计由后端计算 = 1060.00', String(cd?.totalAmount))
  check(cd?.areaName === areaSame?.areaName, '区域名称关联正确', String(cd?.areaName))
  check(cd?.items?.length === 2, '明细保存成功')

  // 回归：区域 / 货架可空，一页单据都没填区域时列表不能 500（Map.of().get(null) 曾导致 NPE）
  const noLoc = await call('POST', '/api/inbound', token, {
    ...createBody(),
    areaId: null,
    shelfId: null,
    supplier: TEST_TAG + '无区域'
  })
  check(noLoc?.code === 200, '新增不选区域 / 货架的单据成功', JSON.stringify(noLoc))
  if (noLoc?.data) createdIds.push(noLoc.data)
  const pageNoLoc = await call(
    'GET',
    '/api/inbound/page?page=1&size=10&keyword=' + encodeURIComponent(TEST_TAG + '无区域'),
    token
  )
  check(
    pageNoLoc?.code === 200 && pageNoLoc.data.total === 1,
    '不选区域 / 货架的单据能正常出现在列表（无 NPE）',
    JSON.stringify(pageNoLoc)
  )

  /* ---------- 业务校验 ---------- */
  const dupBody = createBody()
  dupBody.items = [
    { productId: p1.id, quantity: 1, unitPrice: 10 },
    { productId: p1.id, quantity: 2, unitPrice: 10 }
  ]
  const dup = await call('POST', '/api/inbound', token, dupBody)
  check(dup?.code === 1003, '同一单重复手办被拦截（1003）', JSON.stringify(dup))

  const crossBody = createBody()
  crossBody.areaId = areaOther?.id
  const cross = await call('POST', '/api/inbound', token, crossBody)
  check(cross?.code === 1003 && /不属于该仓库/.test(cross.message || ''), '跨仓库区域被拦截（1003）', JSON.stringify(cross))

  const zeroBody = createBody()
  zeroBody.items = [{ productId: p1.id, quantity: 0, unitPrice: 10 }]
  const zero = await call('POST', '/api/inbound', token, zeroBody)
  check(zero?.code === 400 || zero?.code === 1003, '数量为 0 被参数校验拦截', JSON.stringify(zero))

  const emptyItems = createBody()
  emptyItems.items = []
  const empty = await call('POST', '/api/inbound', token, emptyItems)
  check(empty?.code === 400 || empty?.code === 1003, '明细为空被拦截', JSON.stringify(empty))

  const noWarehouse = createBody()
  noWarehouse.warehouseId = 999999
  const noWh = await call('POST', '/api/inbound', token, noWarehouse)
  check(noWh?.code === 1001, '仓库不存在被拦截（1001）', JSON.stringify(noWh))

  const dupNo = await call('POST', '/api/inbound', token, { ...createBody(), inboundNo: cd.inboundNo })
  check(dupNo?.code === 1002, '重复单号被拦截（1002）', JSON.stringify(dupNo))

  /* ---------- 编辑 ---------- */
  const updateBody = {
    inboundNo: cd.inboundNo,
    inboundType: 'R',
    warehouseId: 1,
    areaId: null,
    shelfId: null,
    supplier: '',
    operator: '',
    remark: '',
    items: [{ productId: p2.id, quantity: 4, unitPrice: 125.5 }]
  }
  const upd = await call('PUT', `/api/inbound/${testId}`, token, updateBody)
  check(upd?.code === 200, '编辑入库单成功', JSON.stringify(upd))

  const afterUpd = await call('GET', `/api/inbound/${testId}`, token)
  const au = afterUpd?.data
  check(au?.items?.length === 1 && au?.totalQuantity === 4, '明细整体替换为 1 行 4 件', JSON.stringify(au?.items?.length))
  check(Number(au?.totalAmount) === 502, '金额随明细重算 = 502.00', String(au?.totalAmount))
  check(au?.supplier == null && au?.remark == null, '供应商 / 备注可被清空', JSON.stringify([au?.supplier, au?.remark]))
  check(au?.operator === cd.operator, '经办人留空时保留原值', String(au?.operator))
  check(au?.inboundType === 'R' && au?.inboundTypeText === '退货入库', '类型改为退货入库', String(au?.inboundTypeText))
  check(au?.areaId == null, '区域可被清空', String(au?.areaId))

  /* ---------- 确认入库 ---------- */
  const confirm = await call('PUT', `/api/inbound/${testId}/confirm`, token)
  check(confirm?.code === 200, '确认入库成功', JSON.stringify(confirm))

  const afterConfirm = await call('GET', `/api/inbound/${testId}`, token)
  check(afterConfirm?.data?.status === 'C' && afterConfirm.data.statusText === '已入库', '状态变为已入库', JSON.stringify(afterConfirm?.data?.status))

  const editConfirmed = await call('PUT', `/api/inbound/${testId}`, token, updateBody)
  check(editConfirmed?.code === 1000 && /只有「待入库」/.test(editConfirmed.message || ''), '已入库不可编辑（1000）', JSON.stringify(editConfirmed))

  const delConfirmed = await call('DELETE', `/api/inbound/${testId}`, token)
  check(delConfirmed?.code === 1000, '已入库不可删除（1000）', JSON.stringify(delConfirmed))

  const confirmAgain = await call('PUT', `/api/inbound/${testId}/confirm`, token)
  check(confirmAgain?.code === 1000, '已入库不能重复确认（1000）', JSON.stringify(confirmAgain))

  // 回归：确认入库只能改状态，不能把供应商 / 区域 / 备注清空
  // （可空字段标了 FieldStrategy.ALWAYS，用空实体 updateById 会把它们一起写成 NULL）
  const keepBody = { ...createBody(), supplier: TEST_TAG + '保留字段', remark: TEST_TAG + '备注' }
  const keepCreated = await call('POST', '/api/inbound', token, keepBody)
  const keepId = keepCreated?.data
  if (keepId) createdIds.push(keepId)
  await call('PUT', `/api/inbound/${keepId}/confirm`, token)
  const kept = await call('GET', `/api/inbound/${keepId}`, token)
  check(kept?.data?.supplier === TEST_TAG + '保留字段', '确认入库后供应商仍保留', String(kept?.data?.supplier))
  check(kept?.data?.areaId === (areaSame?.id ?? null), '确认入库后区域仍保留', String(kept?.data?.areaId))
  check(kept?.data?.remark === TEST_TAG + '备注', '确认入库后备注仍保留', String(kept?.data?.remark))
  await call('PUT', `/api/inbound/${keepId}/cancel`, token)
  const keptAfterCancel = await call('GET', `/api/inbound/${keepId}`, token)
  check(keptAfterCancel?.data?.supplier === TEST_TAG + '保留字段', '作废后供应商仍保留', String(keptAfterCancel?.data?.supplier))

  /* ---------- 作废 ---------- */
  const cancel = await call('PUT', `/api/inbound/${testId}/cancel`, token)
  check(cancel?.code === 200, '作废成功', JSON.stringify(cancel))

  const afterCancel = await call('GET', `/api/inbound/${testId}`, token)
  check(afterCancel?.data?.status === 'X' && afterCancel.data.statusText === '已作废', '状态变为已作废')

  const confirmCancelled = await call('PUT', `/api/inbound/${testId}/confirm`, token)
  check(confirmCancelled?.code === 1000, '已作废不能再确认（1000）', JSON.stringify(confirmCancelled))

  const cancelAgain = await call('PUT', `/api/inbound/${testId}/cancel`, token)
  check(cancelAgain?.code === 1000, '重复作废被拦截（1000）', JSON.stringify(cancelAgain))

  /* ---------- 删除（新建一张草稿再删） ---------- */
  const toDelete = await call('POST', '/api/inbound', token, createBody())
  const deleteId = toDelete?.data
  if (deleteId) createdIds.push(deleteId)
  const del = await call('DELETE', `/api/inbound/${deleteId}`, token)
  check(del?.code === 200, '删除草稿单成功', JSON.stringify(del))

  const afterDel = await call('GET', `/api/inbound/${deleteId}`, token)
  check(afterDel?.code === 1001, '删除后详情返回 1001', JSON.stringify(afterDel))

  /* ---------- 权限：执行级账号只读 ---------- */
  const staffName = 'inbound_staff_' + Date.now().toString().slice(-6)
  const staff = await call('POST', '/api/system/users', token, {
    username: staffName,
    realName: '入库验证专员',
    password: 'Test@123456',
    role: 'FINANCE_STAFF',
    status: 1
  })
  check(staff?.code === 200, '创建执行级测试账号', JSON.stringify(staff))
  tempUserId = staff?.data

  if (tempUserId) {
    const staffLogin = await call('POST', '/api/auth/login', null, { username: staffName, password: 'Test@123456' })
    const staffToken = staffLogin?.data?.token
    check(!!staffToken, '执行级账号登录成功')

    const staffPage = await call('GET', '/api/inbound/page?page=1&size=5', staffToken)
    check(staffPage?.code === 200, '执行级账号可以查看入库记录')

    const staffCreate = await call('POST', '/api/inbound', staffToken, createBody())
    check(staffCreate?.code === 403, '执行级账号不能新增入库单（403）', JSON.stringify(staffCreate))
  }

  console.log('\n' + (failed === 0 ? '全部通过' : `失败 ${failed} 项`))
  console.log('测试单据 id:', createdIds.join(', '))
  console.log('临时用户 id:', tempUserId)
  return failed === 0 ? 0 : 1
}

main()
  .then((code) => process.exit(code))
  .catch((e) => {
    console.error('异常:', e)
    process.exit(1)
  })
