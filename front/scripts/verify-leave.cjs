/**
 * 员工请假申请接口回归（员工权限管理 → 员工请假申请）
 *   1) 字典 / 列表 / 详情 / 统计口径与列表一致
 *   2) 提交：单号格式、天数计算（跨天 / 同一天 4 小时为界）、状态从待审批开始
 *   3) 编辑：仅待审批可改、不能换人、起止时间校验
 *   4) 审批：主管通过 / 驳回必须填原因 / 不能审批自己的单 / 执行级不能审批
 *   5) 撤销：只能本人、仅待审批、终态不能再操作
 *   6) 数据范围：执行级只看自己提交的单
 *
 * 跑法：MYSQL_PWD='xxx' node scripts/verify-leave.cjs
 * 依赖：后端 8080 已启动，admin/admin123 可登录
 */
const { execSync } = require('child_process')

const BASE = 'http://localhost:8080'
const TEST_TAG = '【自动验证】'
const RUN = Date.now().toString().slice(-6)
const STAFF_USERNAME = `hr_e2e_staff_${RUN}`

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
const formatDate = (d) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
const daysAgo = (n) => {
  const d = new Date()
  d.setDate(d.getDate() - n)
  return formatDate(d)
}
const monthFirst = (() => {
  const d = new Date()
  return formatDate(new Date(d.getFullYear(), d.getMonth(), 1))
})()
const monthLast = (() => {
  const d = new Date()
  return formatDate(new Date(d.getFullYear(), d.getMonth() + 1, 0))
})()

async function loginAs(username, password) {
  const res = await call('POST', '/api/auth/login', null, { username, password })
  return res?.data?.token
}

/** 构造一条请假：start/end 传 ISO，原因是测试标记 + 用途 */
const body = (start, end, reason, userId) => ({
  ...(userId ? { userId } : {}),
  leaveType: 'A',
  startTime: start,
  endTime: end,
  reason: TEST_TAG + reason
})

async function main() {
  const token = await loginAs('admin', 'admin123')
  check(!!token, '管理员登录成功')
  if (!token) throw new Error('无 token，终止')

  /* ---------- 1. 字典 ---------- */
  const dicts = await call('GET', '/api/leave/dicts', token)
  check(dicts?.code === 200, '字典接口可用')
  check(dicts?.data?.leaveTypes?.length === 7, '请假类型 7 项', String(dicts?.data?.leaveTypes?.length))
  check(dicts?.data?.statuses?.length === 4, '单据状态 4 项', String(dicts?.data?.statuses?.length))

  /* ---------- 2. 列表与统计口径 ---------- */
  const page = await call('GET', '/api/leave/page?page=1&size=5', token)
  check(page?.code === 200 && page.data.total > 0, '列表有数据', `total=${page?.data?.total}`)
  const first = page?.data?.records?.[0]
  check(/^LV\d{12}$/.test(first?.applyNo || ''), '单号格式 LV + 8 位日期 + 4 位序号', first?.applyNo)
  check(typeof first?.canApprove === 'boolean', '列表行带 canApprove 权限标记')

  const summary = await call('GET', '/api/leave/summary', token)
  const pendingPage = await call('GET', '/api/leave/page?page=1&size=1&status=P', token)
  check(
    summary?.data?.pendingCount === pendingPage?.data?.total,
    '统计「待审批」与列表按同状态查询一致',
    `${summary?.data?.pendingCount} vs ${pendingPage?.data?.total}`
  )

  const monthPage = await call(
    'GET',
    `/api/leave/page?page=1&size=200&status=A&startDate=${monthFirst}&endDate=${monthLast}`,
    token
  )
  check(
    summary?.data?.monthApprovedCount === monthPage?.data?.total,
    '统计「本月已通过」与列表一致',
    `${summary?.data?.monthApprovedCount} vs ${monthPage?.data?.total}`
  )

  /* ---------- 3. 临时执行级账号（后面既测越权，也用来造「别人提交的单」） ---------- */
  const staffUser = await call('POST', '/api/system/users', token, {
    username: STAFF_USERNAME,
    realName: '自动验证专员',
    password: 'test1234',
    role: 'FINANCE_STAFF',
    status: 1,
    remark: TEST_TAG + '临时账号'
  })
  check(staffUser?.code === 200, '创建临时执行级账号', JSON.stringify(staffUser))
  const staffId = staffUser?.data
  const staffToken = await loginAs(STAFF_USERNAME, 'test1234')
  check(!!staffToken, '临时账号可登录')

  /* ---------- 4. 天数计算 ---------- */
  // 跨天：3 个自然日
  const cross = await call('POST', '/api/leave', token, body(`${daysAgo(40)}T09:00:00`, `${daysAgo(38)}T18:00:00`, '跨天3天'))
  check(cross?.code === 200, '提交跨天请假成功', JSON.stringify(cross))
  const crossDetail = await call('GET', `/api/leave/${cross?.data}`, token)
  check(Number(crossDetail?.data?.durationDays) === 3, '跨天请假按含首尾天数计算（3 天）', String(crossDetail?.data?.durationDays))
  check(crossDetail?.data?.status === 'P', '新提交的单状态为待审批', crossDetail?.data?.status)

  // 同一天超过 4 小时 → 1 天（用专员的身份提交，预留后面给 admin 审批）
  const fullDay = await call('POST', '/api/leave', staffToken, body(`${daysAgo(41)}T09:00:00`, `${daysAgo(41)}T14:00:00`, '同天5小时'))
  const fullDetail = await call('GET', `/api/leave/${fullDay?.data}`, token)
  check(Number(fullDetail?.data?.durationDays) === 1, '同一天请假 5 小时记 1 天', String(fullDetail?.data?.durationDays))
  check(fullDetail?.data?.userId === staffId, '代提/自提单的申请人正确', String(fullDetail?.data?.userId))

  // 同一天不超过 4 小时 → 0.5 天
  const halfDay = await call('POST', '/api/leave', token, body(`${daysAgo(42)}T09:00:00`, `${daysAgo(42)}T11:00:00`, '同天2小时'))
  const halfDetail = await call('GET', `/api/leave/${halfDay?.data}`, token)
  check(Number(halfDetail?.data?.durationDays) === 0.5, '同一天请假 2 小时记 0.5 天', String(halfDetail?.data?.durationDays))

  /* ---------- 5. 时间校验 ---------- */
  const reversed = await call('POST', '/api/leave', token, body(`${daysAgo(43)}T18:00:00`, `${daysAgo(43)}T09:00:00`, '时间倒挂'))
  check(reversed?.code === 1003 && /必须晚于开始时间/.test(reversed.message || ''), '结束早于开始被拦截（1003）', JSON.stringify(reversed))

  const tooShort = await call('POST', '/api/leave', token, body(`${daysAgo(43)}T09:00:00`, `${daysAgo(43)}T09:30:00`, '半小时'))
  check(tooShort?.code === 1003 && /不能少于 1 小时/.test(tooShort.message || ''), '不足 1 小时被拦截（1003）', JSON.stringify(tooShort))

  /* ---------- 6. 编辑 ---------- */
  const edited = await call('PUT', `/api/leave/${cross?.data}`, token, {
    leaveType: 'C',
    startTime: `${daysAgo(40)}T09:00:00`,
    endTime: `${daysAgo(39)}T18:00:00`,
    reason: TEST_TAG + '跨天2天改类型'
  })
  check(edited?.code === 200, '编辑待审批的单成功', JSON.stringify(edited))
  const afterEdit = await call('GET', `/api/leave/${cross?.data}`, token)
  check(afterEdit?.data?.leaveType === 'C', '编辑后类型已更新（年假）', afterEdit?.data?.leaveType)
  check(Number(afterEdit?.data?.durationDays) === 2, '编辑后天数重算为 2 天', String(afterEdit?.data?.durationDays))
  check(afterEdit?.data?.applyNo === crossDetail?.data?.applyNo, '编辑不会改单号', afterEdit?.data?.applyNo)

  /* ---------- 7. 审批 ---------- */
  // admin 自己提交的单不能自己审批
  const selfApprove = await call('PUT', `/api/leave/${halfDay?.data}/approve`, token, { result: 'A', remark: '自审测试' })
  check(
    selfApprove?.code === 403 && /不能审批自己提交/.test(selfApprove.message || ''),
    '不能审批自己提交的申请（403）',
    JSON.stringify(selfApprove)
  )

  // 驳回必须填原因
  const rejectNoReason = await call('PUT', `/api/leave/${fullDay?.data}/approve`, token, { result: 'R' })
  check(rejectNoReason?.code === 1003 && /驳回原因/.test(rejectNoReason.message || ''), '驳回未填原因被拦截（1003）', JSON.stringify(rejectNoReason))

  const rejected = await call('PUT', `/api/leave/${fullDay?.data}/approve`, token, { result: 'R', remark: '测试驳回' })
  check(rejected?.code === 200, '驳回成功', JSON.stringify(rejected))
  const rejectedDetail = await call('GET', `/api/leave/${fullDay?.data}`, token)
  check(rejectedDetail?.data?.status === 'R', '驳回后状态为 R', rejectedDetail?.data?.status)
  check(rejectedDetail?.data?.approverName === '系统管理员', '审批人已留痕', rejectedDetail?.data?.approverName)
  check(rejectedDetail?.data?.canEdit === false, '已驳回的单不可编辑')

  const editAfterReject = await call('PUT', `/api/leave/${fullDay?.data}`, token, {
    leaveType: 'A',
    startTime: `${daysAgo(41)}T09:00:00`,
    endTime: `${daysAgo(41)}T14:00:00`,
    reason: TEST_TAG + '终态编辑'
  })
  check(editAfterReject?.code === 1000 && /只有待审批/.test(editAfterReject.message || ''), '终态单据编辑被拦截（1000）', JSON.stringify(editAfterReject))

  /* ---------- 8. 执行级账号：提交自己的单、撤销、不能审批 ---------- */
  const staffLeave = await call('POST', '/api/leave', staffToken, {
    leaveType: 'B',
    startTime: `${daysAgo(44)}T09:00:00`,
    endTime: `${daysAgo(44)}T18:00:00`,
    reason: TEST_TAG + '专员病假'
  })
  check(staffLeave?.code === 200, '执行级账号可提交自己的请假', JSON.stringify(staffLeave))

  const staffDetail = await call('GET', `/api/leave/${staffLeave?.data}`, staffToken)
  check(staffDetail?.data?.userId === staffId, '申请人自动取当前登录用户', String(staffDetail?.data?.userId))
  check(staffDetail?.data?.canCancel === true && staffDetail?.data?.canApprove === false, '本人可撤销、不可审批')

  const staffApprove = await call('PUT', `/api/leave/${staffLeave?.data}/approve`, staffToken, { result: 'A' })
  check(staffApprove?.code === 403, '执行级账号不能审批（403）', JSON.stringify(staffApprove))

  const staffPage = await call('GET', '/api/leave/page?page=1&size=100', staffToken)
  const onlySelf = (staffPage?.data?.records || []).every((r) => r.userId === staffId)
  // 不写死条数：专员在上面还提交过一条「同天 5 小时」的单，写死 1 条会误报
  check(
    staffPage?.code === 200 && onlySelf && staffPage.data.total >= 1,
    '执行级只看自己提交的单（列表里全部都是自己的）',
    `total=${staffPage?.data?.total}`
  )

  // 主管审批通过
  const approved = await call('PUT', `/api/leave/${staffLeave?.data}/approve`, token, { result: 'A', remark: '测试通过' })
  check(approved?.code === 200, '主管审批通过成功', JSON.stringify(approved))
  const approvedDetail = await call('GET', `/api/leave/${staffLeave?.data}`, token)
  check(approvedDetail?.data?.status === 'A', '通过后状态为 A', approvedDetail?.data?.status)
  check(approvedDetail?.data?.canCancel === false, '通过后本人也不能撤销')

  const reApprove = await call('PUT', `/api/leave/${staffLeave?.data}/approve`, token, { result: 'R', remark: '重复审批' })
  check(reApprove?.code === 1000, '已审批的单不能再次审批（1000）', JSON.stringify(reApprove))

  /* ---------- 9. 撤销 ---------- */
  const cancelTarget = await call('POST', '/api/leave', staffToken, {
    leaveType: 'D',
    startTime: `${daysAgo(45)}T09:00:00`,
    endTime: `${daysAgo(45)}T18:00:00`,
    reason: TEST_TAG + '待撤销调休'
  })
  const cancelRes = await call('PUT', `/api/leave/${cancelTarget?.data}/cancel`, staffToken)
  check(cancelRes?.code === 200, '本人撤销成功', JSON.stringify(cancelRes))
  const cancelled = await call('GET', `/api/leave/${cancelTarget?.data}`, staffToken)
  check(cancelled?.data?.status === 'C', '撤销后状态为 C', cancelled?.data?.status)

  const cancelAgain = await call('PUT', `/api/leave/${cancelTarget?.data}/cancel`, staffToken)
  check(cancelAgain?.code === 1000, '已撤销的单不能再次撤销（1000）', JSON.stringify(cancelAgain))

  // 别人不能撤销我的单
  const otherCancel = await call('PUT', `/api/leave/${cross?.data}/cancel`, staffToken)
  check(otherCancel?.code === 403 || otherCancel?.code === 400 || otherCancel?.code === 1001, '不能撤销他人的单', JSON.stringify(otherCancel))

  /* ---------- 10. 删除 ---------- */
  const del = await call('DELETE', `/api/leave/${cross?.data}`, token)
  check(del?.code === 200, '删除待审批的单成功', JSON.stringify(del))
  const gone = await call('GET', `/api/leave/${cross?.data}`, token)
  check(gone?.code === 1001, '删除后详情返回 1001', JSON.stringify(gone))

  const delFinal = await call('DELETE', `/api/leave/${staffLeave?.data}`, token)
  check(delFinal?.code === 1000 && /只有待审批/.test(delFinal.message || ''), '终态单据不可删除（1000）', JSON.stringify(delFinal))

  /* ---------- 11. 筛选 ---------- */
  const rejectedPage = await call('GET', '/api/leave/page?page=1&size=100&status=R', token)
  const allRejected = (rejectedPage?.data?.records || []).every((r) => r.status === 'R')
  check(rejectedPage?.code === 200 && allRejected && rejectedPage.data.total >= 1, '按「已驳回」筛选正确', `total=${rejectedPage?.data?.total}`)

  const typePage = await call('GET', '/api/leave/page?page=1&size=100&leaveType=B', token)
  const allSick = (typePage?.data?.records || []).every((r) => r.leaveType === 'B')
  check(typePage?.code === 200 && allSick, '按「病假」筛选正确', `total=${typePage?.data?.total}`)

  const keywordPage = await call('GET', '/api/leave/page?page=1&size=10&keyword=' + encodeURIComponent('自动验证专员'), token)
  check(keywordPage?.code === 200 && keywordPage.data.total >= 1, '按申请人姓名关键字筛选命中', `total=${keywordPage?.data?.total}`)

  /* ---------- 12. 清理 ---------- */
  if (staffId) await call('DELETE', `/api/system/users/${staffId}`, token)
  cleanup()

  console.log('\n' + (failed === 0 ? '全部通过' : `失败 ${failed} 项`))
  process.exit(failed === 0 ? 0 : 1)
}

function cleanup() {
  if (!process.env.MYSQL_PWD) {
    console.log('（未设置 MYSQL_PWD，跳过物理清理；库中会残留带【自动验证】标记的请假单）')
    return
  }
  const sql = [
    `DELETE FROM leave_apply WHERE reason LIKE '${TEST_TAG}%'`,
    `DELETE FROM sys_user WHERE username = '${STAFF_USERNAME}'`
  ].join('; ')
  try {
    execSync(`mysql -uroot figure_erp -e "${sql}"`, { env: process.env, stdio: 'pipe' })
    console.log('✓ 测试数据已物理清理')
  } catch (e) {
    console.log('✗ 物理清理失败：' + (e.stderr ? e.stderr.toString() : e.message))
  }
}

main().catch((e) => {
  console.error(e)
  cleanup()
  process.exit(1)
})
