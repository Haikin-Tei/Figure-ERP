/**
 * 员工加班申请接口回归（员工权限管理 → 员工加班申请）
 *   1) 字典 / 列表 / 详情 / 统计口径与列表一致
 *   2) 提交：单号格式、时长计算（小时，保留 1 位）、允许跨天
 *   3) 业务校验：结束早于开始、少于 30 分钟、超过 24 小时
 *   4) 审批：主管通过 / 驳回必须填原因 / 不能审批自己的单 / 执行级不能审批
 *   5) 撤销：只能本人、仅待审批
 *   6) 数据范围：执行级只看自己提交的单
 *
 * 跑法：MYSQL_PWD='xxx' node scripts/verify-overtime.cjs
 * 依赖：后端 8080 已启动，admin/admin123 可登录
 */
const { execSync } = require('child_process')

const BASE = 'http://localhost:8080'
const TEST_TAG = '【自动验证】'
const RUN = Date.now().toString().slice(-6)
const STAFF_USERNAME = `hr_e2e_ot_${RUN}`

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

const body = (start, end, reason) => ({
  overtimeType: 'W',
  startTime: start,
  endTime: end,
  reason: TEST_TAG + reason
})

async function main() {
  const token = await loginAs('admin', 'admin123')
  check(!!token, '管理员登录成功')
  if (!token) throw new Error('无 token，终止')

  /* ---------- 1. 字典 ---------- */
  const dicts = await call('GET', '/api/overtime/dicts', token)
  check(dicts?.code === 200, '字典接口可用')
  check(dicts?.data?.overtimeTypes?.length === 3, '加班类型 3 项（工作日/休息日/节假日）', String(dicts?.data?.overtimeTypes?.length))
  check(dicts?.data?.statuses?.length === 4, '单据状态 4 项', String(dicts?.data?.statuses?.length))

  /* ---------- 2. 列表与统计口径 ---------- */
  const page = await call('GET', '/api/overtime/page?page=1&size=5', token)
  check(page?.code === 200 && page.data.total > 0, '列表有数据', `total=${page?.data?.total}`)
  const first = page?.data?.records?.[0]
  check(/^OT\d{12}$/.test(first?.applyNo || ''), '单号格式 OT + 8 位日期 + 4 位序号', first?.applyNo)
  check(typeof first?.canApprove === 'boolean', '列表行带 canApprove 权限标记')

  const summary = await call('GET', '/api/overtime/summary', token)
  const pendingPage = await call('GET', '/api/overtime/page?page=1&size=1&status=P', token)
  check(
    summary?.data?.pendingCount === pendingPage?.data?.total,
    '统计「待审批」与列表按同状态查询一致',
    `${summary?.data?.pendingCount} vs ${pendingPage?.data?.total}`
  )
  const monthPage = await call(
    'GET',
    `/api/overtime/page?page=1&size=200&status=A&startDate=${monthFirst}&endDate=${monthLast}`,
    token
  )
  check(
    summary?.data?.monthApprovedCount === monthPage?.data?.total,
    '统计「本月已通过」与列表一致',
    `${summary?.data?.monthApprovedCount} vs ${monthPage?.data?.total}`
  )

  /* ---------- 3. 临时执行级账号 ---------- */
  const staffUser = await call('POST', '/api/system/users', token, {
    username: STAFF_USERNAME,
    realName: '自动验证加班员',
    password: 'test1234',
    role: 'LOGISTICS_STAFF',
    status: 1,
    remark: TEST_TAG + '临时账号'
  })
  check(staffUser?.code === 200, '创建临时执行级账号', JSON.stringify(staffUser))
  const staffId = staffUser?.data
  const staffToken = await loginAs(STAFF_USERNAME, 'test1234')
  check(!!staffToken, '临时账号可登录')

  /* ---------- 4. 时长计算 ---------- */
  const normal = await call('POST', '/api/overtime', token, body(`${daysAgo(40)}T18:30:00`, `${daysAgo(40)}T21:30:00`, '工作日3小时'))
  check(normal?.code === 200, '提交加班申请成功', JSON.stringify(normal))
  const normalDetail = await call('GET', `/api/overtime/${normal?.data}`, token)
  check(Number(normalDetail?.data?.durationHours) === 3, '18:30→21:30 时长 3.0 小时', String(normalDetail?.data?.durationHours))
  check(normalDetail?.data?.status === 'P', '新提交的单状态为待审批', normalDetail?.data?.status)

  // 跨天：22:00 → 次日 02:00 = 4 小时
  const overnight = await call(
    'POST',
    '/api/overtime',
    staffToken,
    body(`${daysAgo(41)}T22:00:00`, `${daysAgo(40)}T02:00:00`, '跨天4小时')
  )
  check(overnight?.code === 200, '允许跨天加班', JSON.stringify(overnight))
  const overnightDetail = await call('GET', `/api/overtime/${overnight?.data}`, token)
  check(Number(overnightDetail?.data?.durationHours) === 4, '跨天加班时长 4.0 小时', String(overnightDetail?.data?.durationHours))
  check(overnightDetail?.data?.userId === staffId, '申请人正确', String(overnightDetail?.data?.userId))

  // 半小时：18:00 → 18:30 = 0.5 小时
  const halfHour = await call('POST', '/api/overtime', token, body(`${daysAgo(42)}T18:00:00`, `${daysAgo(42)}T18:30:00`, '半小时'))
  const halfDetail = await call('GET', `/api/overtime/${halfHour?.data}`, token)
  check(Number(halfDetail?.data?.durationHours) === 0.5, '半小时加班记 0.5 小时', String(halfDetail?.data?.durationHours))

  /* ---------- 5. 时长校验 ---------- */
  const reversed = await call('POST', '/api/overtime', token, body(`${daysAgo(43)}T21:00:00`, `${daysAgo(43)}T18:00:00`, '时间倒挂'))
  check(reversed?.code === 1003 && /必须晚于开始时间/.test(reversed.message || ''), '结束早于开始被拦截（1003）', JSON.stringify(reversed))

  const tooShort = await call('POST', '/api/overtime', token, body(`${daysAgo(43)}T18:00:00`, `${daysAgo(43)}T18:20:00`, '20分钟'))
  check(tooShort?.code === 1003 && /不能少于 30 分钟/.test(tooShort.message || ''), '不足 30 分钟被拦截（1003）', JSON.stringify(tooShort))

  const tooLong = await call('POST', '/api/overtime', token, body(`${daysAgo(44)}T08:00:00`, `${daysAgo(42)}T09:00:00`, '超过24小时'))
  check(tooLong?.code === 1003 && /不能超过 24 小时/.test(tooLong.message || ''), '超过 24 小时被拦截（1003）', JSON.stringify(tooLong))

  /* ---------- 6. 编辑 ---------- */
  const edited = await call('PUT', `/api/overtime/${normal?.data}`, token, {
    overtimeType: 'O',
    startTime: `${daysAgo(40)}T10:00:00`,
    endTime: `${daysAgo(40)}T15:00:00`,
    reason: TEST_TAG + '改成休息日5小时'
  })
  check(edited?.code === 200, '编辑待审批的单成功', JSON.stringify(edited))
  const afterEdit = await call('GET', `/api/overtime/${normal?.data}`, token)
  check(afterEdit?.data?.overtimeType === 'O', '编辑后类型变为休息日', afterEdit?.data?.overtimeType)
  check(Number(afterEdit?.data?.durationHours) === 5, '编辑后时长重算为 5.0 小时', String(afterEdit?.data?.durationHours))

  /* ---------- 7. 审批 ---------- */
  const selfApprove = await call('PUT', `/api/overtime/${halfHour?.data}/approve`, token, { result: 'A' })
  check(
    selfApprove?.code === 403 && /不能审批自己提交/.test(selfApprove.message || ''),
    '不能审批自己提交的申请（403）',
    JSON.stringify(selfApprove)
  )

  const rejectNoReason = await call('PUT', `/api/overtime/${overnight?.data}/approve`, token, { result: 'R' })
  check(rejectNoReason?.code === 1003 && /驳回原因/.test(rejectNoReason.message || ''), '驳回未填原因被拦截（1003）', JSON.stringify(rejectNoReason))

  const rejected = await call('PUT', `/api/overtime/${overnight?.data}/approve`, token, { result: 'R', remark: '测试驳回' })
  check(rejected?.code === 200, '驳回成功', JSON.stringify(rejected))
  const rejectedDetail = await call('GET', `/api/overtime/${overnight?.data}`, token)
  check(rejectedDetail?.data?.status === 'R', '驳回后状态为 R', rejectedDetail?.data?.status)
  check(rejectedDetail?.data?.approverName === '系统管理员', '审批人已留痕', rejectedDetail?.data?.approverName)

  /* ---------- 8. 执行级账号 ---------- */
  const staffOt = await call('POST', '/api/overtime', staffToken, {
    overtimeType: 'H',
    startTime: `${daysAgo(45)}T09:00:00`,
    endTime: `${daysAgo(45)}T15:00:00`,
    reason: TEST_TAG + '专员节假日加班'
  })
  check(staffOt?.code === 200, '执行级账号可提交自己的加班', JSON.stringify(staffOt))

  const staffApprove = await call('PUT', `/api/overtime/${staffOt?.data}/approve`, staffToken, { result: 'A' })
  check(staffApprove?.code === 403, '执行级账号不能审批（403）', JSON.stringify(staffApprove))

  const staffPage = await call('GET', '/api/overtime/page?page=1&size=100', staffToken)
  const onlySelf = (staffPage?.data?.records || []).every((r) => r.userId === staffId)
  check(
    staffPage?.code === 200 && onlySelf && staffPage.data.total >= 1,
    '执行级只看自己提交的单',
    `total=${staffPage?.data?.total}`
  )

  const approved = await call('PUT', `/api/overtime/${staffOt?.data}/approve`, token, { result: 'A', remark: '测试通过' })
  check(approved?.code === 200, '主管审批通过成功', JSON.stringify(approved))
  const approvedDetail = await call('GET', `/api/overtime/${staffOt?.data}`, token)
  check(approvedDetail?.data?.status === 'A', '通过后状态为 A', approvedDetail?.data?.status)

  /* ---------- 9. 撤销 ---------- */
  const cancelTarget = await call('POST', '/api/overtime', staffToken, {
    overtimeType: 'W',
    startTime: `${daysAgo(46)}T18:00:00`,
    endTime: `${daysAgo(46)}T20:00:00`,
    reason: TEST_TAG + '待撤销加班'
  })
  const cancelRes = await call('PUT', `/api/overtime/${cancelTarget?.data}/cancel`, staffToken)
  check(cancelRes?.code === 200, '本人撤销成功', JSON.stringify(cancelRes))
  const cancelled = await call('GET', `/api/overtime/${cancelTarget?.data}`, staffToken)
  check(cancelled?.data?.status === 'C', '撤销后状态为 C', cancelled?.data?.status)

  const otherCancel = await call('PUT', `/api/overtime/${normal?.data}/cancel`, staffToken)
  check(otherCancel?.code === 403, '不能撤销他人的单（403）', JSON.stringify(otherCancel))

  /* ---------- 10. 删除 ---------- */
  const del = await call('DELETE', `/api/overtime/${normal?.data}`, token)
  check(del?.code === 200, '删除待审批的单成功', JSON.stringify(del))
  const gone = await call('GET', `/api/overtime/${normal?.data}`, token)
  check(gone?.code === 1001, '删除后详情返回 1001', JSON.stringify(gone))

  const delFinal = await call('DELETE', `/api/overtime/${staffOt?.data}`, token)
  check(delFinal?.code === 1000, '终态单据不可删除（1000）', JSON.stringify(delFinal))

  /* ---------- 11. 筛选 ---------- */
  const holidayPage = await call('GET', '/api/overtime/page?page=1&size=100&overtimeType=H', token)
  const allHoliday = (holidayPage?.data?.records || []).every((r) => r.overtimeType === 'H')
  check(holidayPage?.code === 200 && allHoliday && holidayPage.data.total >= 1, '按「节假日加班」筛选正确', `total=${holidayPage?.data?.total}`)

  const approvedFilter = await call('GET', '/api/overtime/page?page=1&size=100&status=A', token)
  const allApproved = (approvedFilter?.data?.records || []).every((r) => r.status === 'A')
  check(approvedFilter?.code === 200 && allApproved && approvedFilter.data.total >= 1, '按「已通过」筛选正确', `total=${approvedFilter?.data?.total}`)

  /* ---------- 12. 清理 ---------- */
  if (staffId) await call('DELETE', `/api/system/users/${staffId}`, token)
  cleanup()

  console.log('\n' + (failed === 0 ? '全部通过' : `失败 ${failed} 项`))
  process.exit(failed === 0 ? 0 : 1)
}

function cleanup() {
  if (!process.env.MYSQL_PWD) {
    console.log('（未设置 MYSQL_PWD，跳过物理清理；库中会残留带【自动验证】标记的加班单）')
    return
  }
  const sql = [
    `DELETE FROM overtime_apply WHERE reason LIKE '${TEST_TAG}%'`,
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
