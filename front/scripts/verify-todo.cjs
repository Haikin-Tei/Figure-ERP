/**
 * 接口级验证：工作台「今日工作计划 / 待办事项」已接入后端。
 *
 * 覆盖：
 *   1. 今日汇总：字段齐全、总数 = 已完成 + 未完成、完成率口径
 *   2. 新增：默认今天、默认普通优先级、高优先级排在前面
 *   3. 编辑：标题 / 说明 / 优先级
 *   4. 勾选完成 / 取消完成：完成时间写入与清空
 *   5. 参数校验：空标题、非法优先级、非法状态
 *   6. 逾期统计：昨天未完成的待办只进 overdue，不进今日列表
 *   7. 数据隔离：别人的待办看不到、改不了、删不了（403）
 *   8. 分页筛选：按状态 / 关键字
 *
 * 所有测试数据自建自清，结束后库里待办数量与开始时一致。
 */
const API = 'http://127.0.0.1:8080'
const RUN = Date.now().toString().slice(-8)
const STAFF_USER = `todo_e2e_${RUN.slice(-6)}`
const STAFF_PASSWORD = 'Staff' + RUN.slice(-6)

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

async function todayOf(token) {
  const r = await call('GET', '/api/todo/today', token)
  return r?.data || null
}

async function totalOf(token, query = '') {
  const r = await call('GET', `/api/todo/page?page=1&size=1${query}`, token)
  return r?.data?.total ?? -1
}

function todayStr(offset = 0) {
  const d = new Date()
  d.setDate(d.getDate() + offset)
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${d.getFullYear()}-${m}-${day}`
}

async function main() {
  const admin = await login('admin', 'admin123')
  if (!admin) throw new Error('管理员登录失败')

  const startTotal = await totalOf(admin)
  const before = await todayOf(admin)
  console.log(`\n初始化：我的待办总数 ${startTotal}，今日 ${before.total} 条（已完成 ${before.done}）`)

  /* ---------- 1. 今日汇总 ---------- */
  console.log('\n[1] 今日汇总')
  check('返回 200 且带 items', Array.isArray(before.items))
  check('带今天日期', before.date === todayStr(), `${before.date} vs ${todayStr()}`)
  check('带星期文案', typeof before.weekdayText === 'string' && before.weekdayText.startsWith('星期'), before.weekdayText)
  check('总数 = 已完成 + 未完成', before.total === before.done + before.pending, JSON.stringify(before.done + '/' + before.pending))
  check(
    '完成率按 已完成/总数 取整',
    before.rate === (before.total === 0 ? 0 : Math.round((before.done * 100) / before.total)),
    `rate=${before.rate}`
  )
  check('条数不超过今日总数', before.items.length <= before.total)
  const firstPendingIndex = before.items.findIndex((x) => !x.done)
  const lastDoneIndex = before.items.map((x) => x.done).lastIndexOf(true)
  check(
    '未完成排在已完成之前',
    firstPendingIndex === -1 || lastDoneIndex === -1 || firstPendingIndex < lastDoneIndex
  )

  /* ---------- 2. 新增 ---------- */
  console.log('\n[2] 新增待办')
  const created = await call('POST', '/api/todo', admin, { title: `E2E待办-${RUN}` })
  check('新增返回 200 与新 id', created.code === 200 && !!created.data, JSON.stringify(created))
  const todoId = created.data

  const afterCreate = await todayOf(admin)
  const createdItem = (afterCreate.items || []).find((x) => x.id === todoId)
  check('新增的待办出现在今日列表', !!createdItem)
  check('计划日期默认今天', createdItem?.planDate === todayStr(), createdItem?.planDate)
  check('优先级默认普通（N）', createdItem?.priority === 'N', createdItem?.priority)
  check('状态默认待办（P）', createdItem?.status === 'P' && createdItem?.done === false)
  check('今日未完成 +1', afterCreate.pending === before.pending + 1, `${before.pending} → ${afterCreate.pending}`)

  const high = await call('POST', '/api/todo', admin, {
    title: `E2E高优-${RUN}`,
    content: '来自自动化测试',
    priority: 'H'
  })
  const highId = high.data
  const withHigh = await todayOf(admin)
  const pendingTitles = (withHigh.items || []).filter((x) => !x.done).map((x) => x.id)
  check(
    '高优先级排在未完成的最前面',
    pendingTitles[0] === highId,
    `order=${pendingTitles.slice(0, 3).join(',')}`
  )

  /* ---------- 3. 编辑 ---------- */
  console.log('\n[3] 编辑待办')
  const edited = await call('PUT', `/api/todo/${todoId}`, admin, {
    title: `E2E待办改名-${RUN}`,
    content: '改过说明',
    priority: 'L',
    planDate: todayStr()
  })
  check('编辑返回 200', edited.code === 200, JSON.stringify(edited))
  const detail = await call('GET', `/api/todo/${todoId}`, admin)
  check('标题已更新', detail.data?.title === `E2E待办改名-${RUN}`, detail.data?.title)
  check('优先级改为低（L）', detail.data?.priority === 'L', detail.data?.priority)
  check('说明已保存', detail.data?.content === '改过说明', detail.data?.content)

  /* ---------- 4. 勾选完成 ---------- */
  console.log('\n[4] 勾选完成 / 恢复待办')
  const doneRes = await call('PUT', `/api/todo/${todoId}/status?status=D`, admin)
  check('标记完成返回 200', doneRes.code === 200, JSON.stringify(doneRes))
  const afterDone = await call('GET', `/api/todo/${todoId}`, admin)
  check('状态变为已完成（D）', afterDone.data?.status === 'D' && afterDone.data?.done === true)
  check('写入完成时间', !!afterDone.data?.finishTime, afterDone.data?.finishTime)

  const backRes = await call('PUT', `/api/todo/${todoId}/status?status=P`, admin)
  const afterBack = await call('GET', `/api/todo/${todoId}`, admin)
  check('恢复待办返回 200', backRes.code === 200)
  check('状态回到待办（P）', afterBack.data?.status === 'P' && afterBack.data?.done === false)
  check('完成时间被清空', !afterBack.data?.finishTime, String(afterBack.data?.finishTime))

  /* ---------- 5. 参数校验 ---------- */
  console.log('\n[5] 参数校验')
  const emptyTitle = await call('POST', '/api/todo', admin, { title: '   ' })
  check('空标题被拦（1003）', emptyTitle.code === 1003, JSON.stringify(emptyTitle))

  const badPriority = await call('POST', '/api/todo', admin, { title: '越权优先级', priority: 'Z' })
  check('非法优先级被拦（1003）', badPriority.code === 1003, JSON.stringify(badPriority))

  const badStatus = await call('PUT', `/api/todo/${todoId}/status?status=X`, admin)
  check('非法状态被拦（1003）', badStatus.code === 1003, JSON.stringify(badStatus))

  const missing = await call('GET', '/api/todo/99999999', admin)
  check('不存在的待办返回 1001', missing.code === 1001, JSON.stringify(missing))

  /* ---------- 6. 逾期统计 ---------- */
  console.log('\n[6] 逾期统计')
  const overdueTodo = await call('POST', '/api/todo', admin, {
    title: `E2E逾期-${RUN}`,
    planDate: todayStr(-1)
  })
  const overdueId = overdueTodo.data
  const withOverdue = await todayOf(admin)
  check('逾期未完成 +1', withOverdue.overdue === before.overdue + 1, `${before.overdue} → ${withOverdue.overdue}`)
  check('昨日待办不进今日列表', !(withOverdue.items || []).some((x) => x.id === overdueId))
  check('今日总数只算今天', withOverdue.total === before.total + 2, `total=${withOverdue.total}`)

  /* ---------- 7. 数据隔离 ---------- */
  console.log('\n[7] 数据隔离')
  const userCreated = await call('POST', '/api/system/users', admin, {
    username: STAFF_USER,
    realName: '待办测试专员',
    role: 'HR_STAFF',
    status: 1,
    password: STAFF_PASSWORD
  })
  const staffId = userCreated.data
  check('创建执行级账号成功', userCreated.code === 200 && !!staffId, JSON.stringify(userCreated))

  const staffToken = await login(STAFF_USER, STAFF_PASSWORD)
  check('执行级账号可登录', !!staffToken)

  const staffToday = await todayOf(staffToken)
  check('新账号的今日待办为空', staffToday.total === 0 && staffToday.items.length === 0, JSON.stringify(staffToday.items))
  check('新账号待办总数与原账号无关', (await totalOf(staffToken)) === 0)

  const staffDetail = await call('GET', `/api/todo/${todoId}`, staffToken)
  check('看不到别人的待办（403）', staffDetail.code === 403, JSON.stringify(staffDetail))

  const staffUpdate = await call('PUT', `/api/todo/${todoId}/status?status=D`, staffToken)
  check('改不了别人的待办（403）', staffUpdate.code === 403, JSON.stringify(staffUpdate))

  const staffDelete = await call('DELETE', `/api/todo/${todoId}`, staffToken)
  check('删不了别人的待办（403）', staffDelete.code === 403, JSON.stringify(staffDelete))

  const staffOwn = await call('POST', '/api/todo', staffToken, { title: '专员的今日事项' })
  const staffAfter = await todayOf(staffToken)
  check('执行级可以建自己的待办', staffOwn.code === 200 && staffAfter.total === 1, JSON.stringify(staffAfter))

  /* ---------- 8. 分页筛选 ---------- */
  console.log('\n[8] 分页筛选')
  const donePage = await call('GET', '/api/todo/page?page=1&size=50&status=D', admin)
  check(
    '按状态筛选只返回已完成',
    donePage.code === 200 && donePage.data.records.every((x) => x.status === 'D')
  )
  const kwPage = await call('GET', `/api/todo/page?page=1&size=50&keyword=${encodeURIComponent('E2E待办改名')}`, admin)
  check('关键字命中标题', kwPage.data.total >= 1, `total=${kwPage.data.total}`)
  const rangePage = await call(
    'GET',
    `/api/todo/page?page=1&size=50&startDate=${todayStr(-1)}&endDate=${todayStr(-1)}`,
    admin
  )
  check(
    '按日期区间只返回昨天',
    rangePage.data.records.every((x) => x.planDate === todayStr(-1)),
    `total=${rangePage.data.total}`
  )

  /* ---------- 9. 清理 ---------- */
  console.log('\n[9] 清理测试数据')
  for (const id of [todoId, highId, overdueId, staffOwn.data]) {
    if (id) await call('DELETE', `/api/todo/${id}`, id === staffOwn.data ? staffToken : admin)
  }
  const endTotal = await totalOf(admin)
  check('待办总数回到初始值', endTotal === startTotal, `start=${startTotal} end=${endTotal}`)

  await call('DELETE', `/api/system/users/${staffId}`, admin)
  const staffGone = await login(STAFF_USER, STAFF_PASSWORD)
  check('测试账号已删除且无法登录', !staffGone)

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
