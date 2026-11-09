/**
 * 员工考勤接口回归（员工权限管理 → 员工考勤记录）
 *   1) 字典 / 列表 / 详情 / 统计口径与列表一致
 *   2) 新增：迟到 / 早退判定、工时计算
 *   3) 业务校验：一人一天一条、未来日期、时间与考勤日期不一致、下班早于上班
 *   4) 编辑：把下班时间清空要真正写成 NULL，下班状态变「未打卡」
 *   5) 销售岗：只记上班卡，下班时间强制清空、状态「免打卡」
 *   6) 数据范围：执行级只看自己、不能给他人代录
 *
 * 跑法：MYSQL_PWD='xxx' node scripts/verify-attendance.cjs
 *   MYSQL_PWD 用于跑完物理清理测试数据（不设则跳过清理，会留下带【自动验证】标记的记录）
 * 依赖：后端 8080 已启动，admin/admin123 可登录
 */
const { execSync } = require('child_process')

const BASE = 'http://localhost:8080'
const TEST_TAG = '【自动验证】'
const RUN = Date.now().toString().slice(-6)
const SALES_USERNAME = `hr_e2e_sales_${RUN}`

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
const today = formatDate(new Date())
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

async function main() {
  /* ---------- 登录 ---------- */
  const token = await loginAs('admin', 'admin123')
  check(!!token, '管理员登录成功')
  if (!token) throw new Error('无 token，终止')

  /* ---------- 1. 字典 ---------- */
  const dicts = await call('GET', '/api/attendance/dicts', token)
  check(dicts?.code === 200, '字典接口可用', JSON.stringify(dicts).slice(0, 120))
  check(dicts?.data?.checkInStatuses?.length === 2, '上班状态字典 2 项（正常 / 迟到）', String(dicts?.data?.checkInStatuses?.length))
  check(dicts?.data?.checkOutStatuses?.length === 4, '下班状态字典 4 项（正常/早退/未打卡/免打卡）', String(dicts?.data?.checkOutStatuses?.length))

  /* ---------- 2. 列表与统计口径 ---------- */
  const page = await call('GET', '/api/attendance/page?page=1&size=5', token)
  check(page?.code === 200 && page.data.records.length > 0, '列表有数据', `total=${page?.data?.total}`)
  const first = page?.data?.records?.[0]
  check(!!first?.attendDate && !!first?.checkInStatusText, '列表行带日期与状态文案', JSON.stringify(first)?.slice(0, 100))
  check(typeof first?.exemptCheckOut === 'boolean', '列表行带「是否免下班卡」标记')

  const summary = await call('GET', '/api/attendance/summary', token)
  const monthPage = await call(
    'GET',
    `/api/attendance/page?page=1&size=500&startDate=${monthFirst}&endDate=${monthLast}`,
    token
  )
  check(
    summary?.data?.monthRecords === monthPage?.data?.total,
    '统计「本月考勤」与列表按同口径查询一致',
    `${summary?.data?.monthRecords} vs ${monthPage?.data?.total}`
  )
  check(Number(summary?.data?.lateCount) >= 1, '本月有迟到记录（演示种子）', String(summary?.data?.lateCount))

  /* ---------- 3. 新增：迟到 + 早退 + 工时 ---------- */
  const d1 = daysAgo(30)
  const created = await call('POST', '/api/attendance', token, {
    attendDate: d1,
    checkInTime: `${d1}T09:23:00`,
    checkOutTime: `${d1}T17:30:00`,
    remark: TEST_TAG + '迟到早退'
  })
  check(created?.code === 200 && created.data > 0, '新增考勤成功', JSON.stringify(created))
  const id1 = created?.data

  const detail = await call('GET', `/api/attendance/${id1}`, token)
  check(detail?.data?.checkInStatus === 'L', '上班 09:23 判定为迟到', detail?.data?.checkInStatus)
  check(detail?.data?.checkOutStatus === 'E', '下班 17:30 判定为早退', detail?.data?.checkOutStatus)
  check(detail?.data?.workMinutes === 487, '工时按分钟计算（09:23→17:30 = 487 分钟）', String(detail?.data?.workMinutes))
  check(Number(detail?.data?.workHours) === 8.1, '工时小时数四舍五入到 1 位（8.1）', String(detail?.data?.workHours))

  /* ---------- 4. 业务校验 ---------- */
  const dup = await call('POST', '/api/attendance', token, {
    attendDate: d1,
    checkInTime: `${d1}T09:00:00`
  })
  check(dup?.code === 1002 && /已有考勤记录/.test(dup.message || ''), '同一员工同一天重复录入被拦截（1002）', JSON.stringify(dup))

  const future = await call('POST', '/api/attendance', token, {
    attendDate: daysAgo(-3),
    checkInTime: `${daysAgo(-3)}T09:00:00`
  })
  check(future?.code === 1003 && /不能晚于今天/.test(future.message || ''), '未来日期被拦截（1003）', JSON.stringify(future))

  const mismatch = await call('POST', '/api/attendance', token, {
    attendDate: daysAgo(31),
    checkInTime: `${daysAgo(32)}T09:00:00`
  })
  check(mismatch?.code === 1003 && /日期必须与考勤日期一致/.test(mismatch.message || ''), '上班时间与考勤日期不一致被拦截（1003）', JSON.stringify(mismatch))

  const reversed = await call('POST', '/api/attendance', token, {
    attendDate: daysAgo(31),
    checkInTime: `${daysAgo(31)}T18:00:00`,
    checkOutTime: `${daysAgo(31)}T09:00:00`
  })
  check(reversed?.code === 1003 && /必须晚于上班时间/.test(reversed.message || ''), '下班早于上班被拦截（1003）', JSON.stringify(reversed))

  /* ---------- 5. 编辑：清空下班时间要真正写 NULL ---------- */
  const cleared = await call('PUT', `/api/attendance/${id1}`, token, {
    attendDate: d1,
    checkInTime: `${d1}T08:55:00`,
    checkOutTime: null,
    remark: TEST_TAG + '清空下班'
  })
  check(cleared?.code === 200, '编辑考勤成功', JSON.stringify(cleared))
  const afterClear = await call('GET', `/api/attendance/${id1}`, token)
  check(afterClear?.data?.checkOutTime == null, '下班时间被真正清空（不是保留旧值）', String(afterClear?.data?.checkOutTime))
  check(afterClear?.data?.checkOutStatus === 'M', '下班状态变为「未打卡」', afterClear?.data?.checkOutStatus)
  check(afterClear?.data?.checkInStatus === 'N', '上班 08:55 判定为正常', afterClear?.data?.checkInStatus)
  check(afterClear?.data?.workMinutes == null, '未打卡时工时为 null', String(afterClear?.data?.workMinutes))

  /* ---------- 6. 销售岗免下班卡 ---------- */
  const salesUser = await call('POST', '/api/system/users', token, {
    username: SALES_USERNAME,
    realName: '自动验证销售',
    password: 'test1234',
    role: 'SALES_STAFF',
    status: 1,
    remark: TEST_TAG + '临时账号'
  })
  check(salesUser?.code === 200, '创建临时销售账号', JSON.stringify(salesUser))
  const salesId = salesUser?.data

  const d2 = daysAgo(31)
  const salesRecord = await call('POST', '/api/attendance', token, {
    userId: salesId,
    attendDate: d2,
    checkInTime: `${d2}T09:40:00`,
    checkOutTime: `${d2}T20:00:00`, // 故意传下班时间，后端应忽略
    remark: TEST_TAG + '销售免打卡'
  })
  check(salesRecord?.code === 200, '为销售员工录入考勤', JSON.stringify(salesRecord))

  const salesDetail = await call('GET', `/api/attendance/${salesRecord?.data}`, token)
  check(salesDetail?.data?.checkOutStatus === 'X', '销售岗下班状态为「免打卡」', salesDetail?.data?.checkOutStatus)
  check(salesDetail?.data?.checkOutTime == null, '销售岗下班时间被强制清空', String(salesDetail?.data?.checkOutTime))
  check(salesDetail?.data?.exemptCheckOut === true, '出参 exemptCheckOut = true')
  check(salesDetail?.data?.checkInStatus === 'L', '销售岗上班 09:40 仍判迟到', salesDetail?.data?.checkInStatus)

  /* ---------- 7. 数据范围与越权 ---------- */
  const salesToken = await loginAs(SALES_USERNAME, 'test1234')
  check(!!salesToken, '临时销售账号可登录')

  const salesPage = await call('GET', '/api/attendance/page?page=1&size=100', salesToken)
  const onlySelf = (salesPage?.data?.records || []).every((r) => r.userId === salesId)
  check(
    salesPage?.code === 200 && onlySelf && salesPage.data.total === 1,
    '执行级账号只能看到自己的考勤（1 条）',
    `total=${salesPage?.data?.total}`
  )

  const salesSummary = await call('GET', '/api/attendance/summary', salesToken)
  check(
    salesSummary?.data?.monthRecords === 0 || salesSummary?.data?.monthRecords === 1,
    '执行级账号的统计只算自己',
    JSON.stringify(salesSummary?.data)
  )

  const crossRecord = await call('POST', '/api/attendance', salesToken, {
    userId: 1,
    attendDate: daysAgo(33),
    checkInTime: `${daysAgo(33)}T09:00:00`
  })
  check(
    crossRecord?.code === 403 && /只能录入自己的考勤/.test(crossRecord.message || ''),
    '执行级账号不能给他人代录（403）',
    JSON.stringify(crossRecord)
  )

  /* ---------- 8. 删除 ---------- */
  const del = await call('DELETE', `/api/attendance/${id1}`, token)
  check(del?.code === 200, '删除考勤成功', JSON.stringify(del))
  const gone = await call('GET', `/api/attendance/${id1}`, token)
  check(gone?.code === 1001, '删除后详情返回 1001', JSON.stringify(gone))

  /* ---------- 9. 筛选 ---------- */
  const latePage = await call('GET', '/api/attendance/page?page=1&size=200&checkInStatus=L', token)
  const allLate = (latePage?.data?.records || []).every((r) => r.checkInStatus === 'L')
  check(latePage?.code === 200 && allLate && latePage.data.total >= 1, '按「迟到」筛选结果正确', `total=${latePage?.data?.total}`)

  const exemptPage = await call('GET', '/api/attendance/page?page=1&size=200&checkOutStatus=X', token)
  const allExempt = (exemptPage?.data?.records || []).every((r) => r.checkOutStatus === 'X')
  check(exemptPage?.code === 200 && allExempt && exemptPage.data.total >= 1, '按「免打卡」筛选结果正确', `total=${exemptPage?.data?.total}`)

  const keywordPage = await call('GET', '/api/attendance/page?page=1&size=10&keyword=' + encodeURIComponent('自动验证销售'), token)
  check(keywordPage?.code === 200 && keywordPage.data.total === 1, '按员工姓名关键字筛选命中 1 条', `total=${keywordPage?.data?.total}`)

  /* ---------- 10. 清理 ---------- */
  await call('DELETE', `/api/attendance/${salesRecord?.data}`, token)
  if (salesId) await call('DELETE', `/api/system/users/${salesId}`, token)

  cleanup()

  console.log('\n' + (failed === 0 ? '全部通过' : `失败 ${failed} 项`))
  process.exit(failed === 0 ? 0 : 1)
}

/** 物理清理测试残渣：逻辑删除的行和临时账号都会留在库里，用 MYSQL_PWD 直连清掉 */
function cleanup() {
  if (!process.env.MYSQL_PWD) {
    console.log('（未设置 MYSQL_PWD，跳过物理清理；库中会残留带【自动验证】标记的记录）')
    return
  }
  const sql = [
    `DELETE FROM attendance_record WHERE remark LIKE '${TEST_TAG}%' OR user_name = '自动验证销售'`,
    `DELETE FROM sys_user WHERE username = '${SALES_USERNAME}'`
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
