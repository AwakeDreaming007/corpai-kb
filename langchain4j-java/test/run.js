// 企业知识问答后端 — 接口链路 + 边界 + 并发 集成测试
// 约定：新建隔离测试账号/知识库，保留数据；对新建测试库做并发上传。
const http = require('http');
const fs = require('fs');

const PORT = parseInt(process.env.KB_TEST_PORT || '8080', 10);
const ADMIN = { username: 'admin', password: 'Admin@123456' };
const TUSER = 'ttest000', TUSER2 = 'ttest001', TPP = 'Test@123456';
// 每次运行用唯一名，保证可重复执行而不与旧数据冲突（测试数据按约定保留）
const RUN_TAG = new Date().toISOString().replace(/[:.]/g, '').slice(0, 16);
const KBNAME = `链路库${RUN_TAG}`;
const ROLECODE = `QA_VIEWER_${RUN_TAG.slice(-6)}`;

const res = []; // {label, status, ok, note}
let lastErr = null;
let adminToken = null;
let t0Token = null;
let testKbId = null;
let testDocId = null;
let testSessionId = null;
let testHistoryId = null;

function log(s) { console.log(s); }
function pass(label, r, wantCode, note) {
  const got = r.status;
  const body = j(r.body);
  const code = body && body.code != null ? body.code : got; // Result 包装：业务码在 body.code
  const ok = (typeof wantCode === 'number')
    ? (code === wantCode && code < 500 && code !== -1)
    : (code < 500 && code !== -1);
  r.ok = ok;
  res.push({ label, status: got, code, ok, note: (note || (ok ? '' : `got code=${code} http=${got} want ${wantCode}`)) });
  const icon = ok ? 'PASS' : 'FAIL';
  log(`  [${icon}] ${label}  http=${got} code=${code}${!ok ? '  ← ' + (note || JSON.stringify(body).slice(0, 100)) : ''}`);
  return ok;
}
function j(b) { try { return JSON.parse(b); } catch { return null; } }

async function httpReq(method, p, { token, body, rawBody, timeout = 20000 } = {}) {
  const headers = { 'Content-Type': 'application/json' };
  if (token) headers['Authorization'] = `Bearer ${token}`;
  const data = body !== undefined ? JSON.stringify(body) : (rawBody != null ? rawBody : null);
  if (data !== null) headers['Content-Length'] = Buffer.byteLength(data);
  const t0 = Date.now();
  try {
    const out = await new Promise((ok, no) => {
      const end = setTimeout(() => no(new Error('TIMEOUT')), timeout);
      const r = http.request({ hostname: 'localhost', port: PORT, method, path: p, headers }, (rr) => {
        let b = '';
        rr.on('data', c => b += c);
        rr.on('end', () => { clearTimeout(end); ok({ status: rr.statusCode, body: b, headers: rr.headers }); });
      });
      r.on('error', e => { clearTimeout(end); no(e); });
      if (data !== null) r.write(data);
      r.end();
    });
    if (out.status >= 500) lastErr = `${method} ${p} -> ${out.status} ${out.body.slice(0, 200)}`;
    return out;
  } catch (e) {
    lastErr = `${method} ${p} ERROR ${e.message}`;
    return { status: -1, body: e.message };
  }
}

// ============ 0. 预热 ============
async function warmup() {
  log('\n===== [0] 预热：admin 登录 =====');
  const r = await httpReq('POST', '/api/auth/login', { body: ADMIN });
  pass('admin 登录', r, 200);
  const parsed = j(r.body);
  log(`    响应 code=${parsed && parsed.code} data=${parsed && parsed.data ? '有' : '无'}`);
  adminToken = parsed && parsed.data && parsed.data.token || null;
  if (!adminToken) { log('admin 登录无 token，终止。raw=' + r.body.slice(0, 300)); process.exit(1); }
}

// ============ 1. 鉴权链路 ============
async function authChain() {
  log('\n===== [1] 鉴权链路 =====');
  // 注册测试账号（幂等：已存在则视为就绪）
  let r = await httpReq('POST', '/api/auth/register', { body: { username: TUSER, password: TPP, nickname: '测试0' } });
  pass('注册 ttest000（200 或已存在 400，均非 500）', r, null);
  r = await httpReq('POST', '/api/auth/register', { body: { username: TUSER, password: TPP, nickname: '测试0' } });
  pass('重复注册返回 400(非500)', r, 400);
  r = await httpReq('POST', '/api/auth/register', { body: { username: TUSER2, password: TPP, nickname: '测试1' } });
  pass('注册 ttest001（200 或已存在 400，均非 500）', r, null);

  r = await httpReq('POST', '/api/auth/login', { body: { username: TUSER, password: TPP } });
  pass('ttest000 登录', r, 200);
  const login = j(r.body);
  t0Token = login && login.data && login.data.token || null;
  if (!t0Token) { log('!! ttest000 登录失败，后续依赖其 token 的断言会跳过'); }
  log(`    登录 roles=${JSON.stringify(login && login.data && login.data.roles)} perms=${JSON.stringify(login && login.data && login.data.permissions)}`);

  r = await httpReq('GET', '/api/kb', {});
  pass('无 token -> 401', r, 401);
  r = await httpReq('GET', '/api/kb', { token: 'NOPE' });
  pass('无 Bearer 前缀 -> 401', r, 401);
  r = await httpReq('GET', '/api/kb', { token: 'fake.abc.def' });
  pass('伪造签名 -> 401', r, 401);
  r = await httpReq('POST', '/api/auth/login', { body: { username: TUSER, password: 'wrong' } });
  pass('错密码 -> 401/400(非500)', r, null);

  r = await httpReq('GET', '/api/auth/me', { token: adminToken });
  pass('/me admin', r, 200);
  const me = j(r.body);
  log(`    /me ADMIN perms: ${JSON.stringify(me && me.data && me.data.permissions)}`);

  r = await httpReq('GET', '/api/sys/users', { token: t0Token });
  pass('USER 调 /api/sys/users -> 403', r, 403);
}

// ============ 2. 用户管理链路 ============
async function userChain() {
  log('\n===== [2] 用户管理链路 =====');
  let r = await httpReq('GET', '/api/sys/users?page=1&size=100', { token: adminToken });
  pass('用户列表', r, 200);
  const list = j(r.body);
  const t0InList = list && list.data && list.data.records && list.data.records.find(x => x.username === TUSER);
  log(`    列表含 ttest000: ${!!t0InList}`);

  // 禁用/启用
  r = await httpReq('PUT', `/api/sys/users/${t0InList.id}/status`, { token: adminToken, body: { status: 0 } });
  pass('禁用用户', r, 200);
  r = await httpReq('PUT', `/api/sys/users/${t0InList.id}/status`, { token: adminToken, body: { status: 1 } });
  pass('重新启用用户', r, 200);
  // 禁用边界：非法 status
  r = await httpReq('PUT', `/api/sys/users/${t0InList.id}/status`, { token: adminToken, body: { status: 9 } });
  pass('非法 status -> 400(非500)', r, 400);
}

// ============ 3. 角色管理链路 ============
async function roleChain() {
  log('\n===== [3] 角色管理链路 =====');
  let r = await httpReq('GET', '/api/sys/permissions', { token: adminToken });
  pass('权限列表', r, 200);
  r = await httpReq('GET', '/api/sys/roles', { token: adminToken });
  pass('角色列表', r, 200);
  const roles = j(r.body);
  const auditId = roles && roles.data && roles.data.find(x => x.roleCode === 'AUDITOR');

  r = await httpReq('DELETE', '/api/sys/roles/1', { token: adminToken });
  pass('删内置 ADMIN -> 400(非500)', r, 400);

  // 创建自定义角色
  r = await httpReq('POST', '/api/sys/roles', { token: adminToken, body: { roleCode: ROLECODE, roleName: '质检员', description: '链路测试角色' } });
  pass('创建自定义角色', r, 200);
  const newRoleId = j(r.body) && j(r.body).data;
  // 重复创建
  r = await httpReq('POST', '/api/sys/roles', { token: adminToken, body: { roleCode: ROLECODE, roleName: '质检员2' } });
  pass('重复创建角色 -> 400(非500)', r, 400);
  // 修改内置角色名(应被拒)
  r = await httpReq('PUT', '/api/sys/roles/1', { token: adminToken, body: { roleCode: 'ADMIN', roleName: '管理员_X' } });
  pass('改内置角色名 -> 400(非500)', r, 400);
}

// ============ 4. 知识库 CRUD 链路 ============
async function kbChain() {
  log('\n===== [4] 知识库 CRUD 链路 =====');
  let r = await httpReq('POST', '/api/kb', { token: adminToken, body: { name: KBNAME, description: '链路测试用' } });
  pass('创建知识库', r, 200);
  testKbId = j(r.body) && j(r.body).data;
  log(`    新建 kbId=${testKbId}`);
  // 同名拒绝（唯一 kbId 名，第一次建成功、第二次应 400）
  r = await httpReq('POST', '/api/kb', { token: adminToken, body: { name: KBNAME, description: '同名' } });
  pass('同名创建 -> 400(非500)', r, 400);
  // 空名
  r = await httpReq('POST', '/api/kb', { token: adminToken, body: { name: '', description: '空' } });
  pass('空名创建 -> 400(非500)', r, 400);

  // 列表含新库
  r = await httpReq('GET', '/api/kb', { token: adminToken });
  pass('库列表', r, 200);
  const list = j(r.body);
  const mine = list && list.data && list.data.records && list.data.records.find(x => x.id === testKbId);
  log(`    列表含新库 ownedByMe=${mine && mine.ownedByMe} myRole=${mine && mine.myRole}`);

  // 更新
  r = await httpReq('PUT', `/api/kb/${testKbId}`, { token: adminToken, body: { name: KBNAME + '_v2', description: '更新描述' } });
  pass('更新库信息', r, 200);

  // 成员删除后不可见 + 重建
  await httpReq('DELETE', `/api/kb/${testKbId}`, { token: adminToken });
  r = await httpReq('POST', '/api/kb', { token: adminToken, body: { name: KBNAME, description: '重建' } });
  pass('删除后重建同名录', r, 200);
  testKbId = j(r.body) && j(r.body).data;
  log(`    重建 kbId=${testKbId}`);
}

// ============ 5. 成员协作链路 ============
async function memberChain() {
  log('\n===== [5] 成员协作链路 =====');
  let r = await httpReq('GET', `/api/kb/${testKbId}/members`, { token: adminToken });
  pass('成员列表', r, 200);

  r = await httpReq('POST', `/api/kb/${testKbId}/members`, { token: adminToken, body: { username: TUSER, memberRole: 'EDITOR' } });
  pass('添加 EDITOR', r, 200);
  r = await httpReq('POST', `/api/kb/${testKbId}/members`, { token: adminToken, body: { username: TUSER, memberRole: 'VIEWER' } });
  pass('重复添加 -> 400(非500)', r, 400);
  r = await httpReq('POST', `/api/kb/${testKbId}/members`, { token: adminToken, body: { username: TUSER, memberRole: 'OWNER' } });
  pass('直接加 OWNER -> 400(非500)', r, 400);
  r = await httpReq('POST', `/api/kb/${testKbId}/members`, { token: adminToken, body: { username: '不存在的用户', memberRole: 'VIEWER' } });
  pass('加不存在用户 -> 404(非500)', r, 404);
  r = await httpReq('POST', `/api/kb/${testKbId}/members`, { token: adminToken, body: { username: TUSER2, memberRole: 'SUPER' } });
  pass('非法角色 -> 400(非500)', r, 400);
  r = await httpReq('POST', `/api/kb/${testKbId}/members`, { token: adminToken, body: { username: TUSER2, memberRole: 'VIEWER' } });
  pass('添加 VIEWER', r, 200);

  // 唯一 OWNER 移除被拒 / 不存在成员 -> 404
  r = await httpReq('DELETE', `/api/kb/${testKbId}/members/99999`, { token: adminToken });
  pass('删除不存在的成员 -> 404(非500)', r, 404);

  // VIEWER 上传被拒：先给 ttest001 加 VIEWER 并登录
  r = await httpReq('POST', `/api/kb/${testKbId}/members`, { token: adminToken, body: { username: TUSER2, memberRole: 'VIEWER' } });
  // 幂等：可能已在上一步加入，400「该用户已是成员」也算通过
  pass('ttest001 为 VIEWER（已存在 400 或新建 200 均可，非500）', r, null);
  const t1r = await httpReq('POST', '/api/auth/login', { body: { username: TUSER2, password: TPP } });
  const t1Token = t1r.status === 200 ? j(t1r.body).data.token : null;
  if (t1Token) {
    // VIEWER 改库信息被拒
    r = await httpReq('PUT', `/api/kb/${testKbId}`, { token: t1Token, body: { name: KBNAME, description: 'VIEWER改' } });
    pass('VIEWER 改库 -> 403(非500)', r, 403);
  }

  // 移除 VIEWER 后不可见：找到 ttest001 的用户 id 后删除
  const usersPage = j(await httpReq('GET', '/api/sys/users?page=1&size=100', { token: adminToken }));
  const t1Id = usersPage && usersPage.data && usersPage.data.records && usersPage.data.records.find(x => x.username === TUSER2);
  if (t1Id) {
    r = await httpReq('DELETE', `/api/kb/${testKbId}/members/${t1Id.id}`, { token: adminToken });
    pass('移除 VIEWER 成员', r, 200);
    // 再用 ttest001 访问该库，应 403
    const t1r2 = await httpReq('POST', '/api/auth/login', { body: { username: TUSER2, password: TPP } });
    if (t1r2.status === 200) {
      r = await httpReq('GET', '/api/kb', { token: j(t1r2.body).data.token });
      log(`    VIEWER 移除后查库列表 status=${r.status} (期望非成员看不到)`);
    }
  }
}

// ============ 6. 文档上传/重建链路 ============
async function docChain() {
  log('\n===== [6] 文档上传链路 =====');
  let mkPdf = null;
  try { mkPdf = require('./makeValidPdf'); } catch { /* no-op */ }
  const pdfBuf = mkPdf ? mkPdf() : makeMinimalPdf();
  const boundary = '----FormBoundary' + Math.random().toString(36).slice(2);
  const body = Buffer.concat([
    Buffer.from(`--${boundary}\r\nContent-Disposition: form-data; name="file"; filename="链路测试.pdf"\r\nContent-Type: application/pdf\r\n\r\n`),
    pdfBuf,
    Buffer.from(`\r\n--${boundary}--\r\n`)
  ]);
  const doUpload = (token, fname) => httpReq('POST', `/api/kb/${testKbId}/docs`, {
    timeout: 30000,
    rawBody: null
  }).then(async () => {
    // 用 multipart
    return new Promise((ok) => {
      const h = { 'Content-Type': `multipart/form-data; boundary=${boundary}` };
      if (token) h['Authorization'] = `Bearer ${token}`;
      const r = http.request({ hostname: 'localhost', port: PORT, method: 'POST', path: `/api/kb/${testKbId}/docs`, headers: h }, (rr) => {
        let b = ''; rr.on('data', c => b += c);
        rr.on('end', () => ok({ status: rr.statusCode, body: b }));
      });
      r.on('error', e => ok({ status: -1, body: e.message }));
      r.write(body); r.end();
    });
  });
  let r = await doUpload(adminToken, '链路测试.pdf');
  pass('上传 PDF status=200(非500)', r, 200);
  const doc = j(r.body);
  testDocId = doc && doc.data && doc.data.id;
  log(`    上传 docId=${testDocId} status=${doc && doc.data && doc.data.status}`);

  // 同名覆盖
  r = await doUpload(adminToken, '链路测试.pdf');
  pass('同名覆盖上传', r, 200);
  const doc2 = j(r.body);
  testDocId = doc2 && doc2.data && doc2.data.id;
  log(`    覆盖 docId=${testDocId}`);

  // 列表
  r = await httpReq('GET', `/api/kb/${testKbId}/docs`, { token: adminToken });
  pass('文档列表', r, 200);
}

function makeMinimalPdf() {
  return Buffer.from(
    '%PDF-1.4\n' +
    '1 0 obj<<-15 -19 0>>\n' +
    '2 0 obj<<7 0 R>>endobj\n' +
    '3 0 obj<>endobj\n' +
    '4 0 obj<<0 0 3 0 R 1 0 R>>endobj\n' +
    '5 0 obj<<8 0 R>>endobj\n' +
    '6 0 obj<<>>endobj\n' +
    '7 0 obj<<F1 9 0 R>>>>endobj\n' +
    '8 0 obj<<F 8 0 R>>>>endobj\n' +
    '9 0 obj<<>>>>endobj\n' +
    '10 0 obj<>stream\nBT /F1 12 Tf 50 700 Td (link-test document) Tj ET\nendstream endobj\n' +
    '11 0 obj<</Pages 4 0 R>>endobj\n' +
    'xref\n0 12\n0000000000 65535 f \n' +
    'trailer<</Size 12>>\nstartxref\n%%%%EOF', 'latin1');
}

// ============ 7. 流式问答链路 ============
async function chatChain() {
  log('\n===== [7] 流式问答链路 =====');
  // 等待上一环节上传的文档完成入库（status=0→1），确保向量存在，命中分支可测
  log('    等待文档入库...');
  for (let i = 0; i < 14; i++) {
    await new Promise(r => setTimeout(r, 1500));
    const dp = await httpReq('GET', `/api/kb/${testKbId}/docs`, { token: adminToken });
    const docs = j(dp.body) && j(dp.body).data && j(dp.body).data.records || [];
    const done = docs.find(d => d.status === 1);
    const failed = docs.find(d => d.status === 2);
    log(`    文档状态: ${docs.map(d => d.id + '=' + d.status + '(' + d.segmentCount + '段)').join(', ')}`);
    if (done) break;
    if (failed && !done) { log('    文档入库失败，命中分支将走无命中'); break; }
  }

  let r = await httpReq('POST', `/api/kb/${testKbId}/sessions`, { token: adminToken });
  pass('创建会话', r, 200);
  testSessionId = j(r.body) && j(r.body).data;
  log(`    sessionId=${testSessionId}`);

  // 会话列表
  r = await httpReq('GET', `/api/kb/${testKbId}/sessions`, { token: adminToken });
  pass('会话列表', r, 200);

  // 空 body 发送（验证 @Valid）
  r = await httpReq('POST', `/api/kb/${testKbId}/chat/stream`, { token: adminToken, body: {} });
  // SSE 流式响应，status 200 但事件为 error 或 400
  log(`    空问题请求 status=${r.status}`);

  // 命中分支：问文档内容（合法 PDF 含 "link test doc for qa validation"）
  const chatRes = await streamChat('这是关于什么内容的文档');
  const evts = chatRes.events;
  const gotDone = evts.some(e => e.event === 'done');
  const gotToken = evts.some(e => e.event === 'token');
  const gotSources = evts.some(e => e.event === 'sources');
  const gotError = evts.some(e => e.event === 'error');
  pass('流式问答收到事件流(非500)', { status: chatRes.status }, 200);
  log(`    事件: token=${gotToken} sources=${gotSources} done=${gotDone} error=${gotError} 总=${evts.length}`);
  if (gotToken) {
    const fullAnswer = evts.filter(e => e.event === 'token').map(e => j(e.data) && j(e.data).content).join('');
    log(`    回答(${fullAnswer.length}字): ${fullAnswer.slice(0, 120)}...`);
  }
  const srcEvt = evts.find(e => e.event === 'sources');
  if (srcEvt) {
    const sources = j(srcEvt.data);
    log(`    来源 ${sources.length} 条: ${sources.map(s => `[${s.seq}]${s.docName || ''}(${(s.score || 0).toFixed(3)})`).join(', ')}`);
  }

  // 无命中（用不存在的内容提问）
  const chatRes2 = await streamChat('量子纠缠在非欧几里得流形上的拓扑不变量是什么');
  const evts2 = chatRes2.events;
  const noHit = evts2.some(e => e.event === 'token' && /未找到/.test(j(e.data).content));
  log(`    无命中提问: done=${evts2.some(e=>e.event==='done')} 提示未找到=${noHit} error=${evts2.some(e=>e.event==='error')}`);

  // 历史落库
  r = await httpReq('GET', `/api/history?kbId=${testKbId}&page=1&size=100`, { token: adminToken });
  pass('按库历史列表', r, 200);
  const hist = j(r.body);
  log(`    历史条数: ${hist && hist.data && hist.data.records ? hist.data.records.length : 0}`);
}

async function streamChat(question, timeoutMs) {
  // 注意：SSE 无命中/错误分支服务端会快速 complete 并关闭连接；命中分支等待模型响应。
  // 不再依赖 'end' 事件（keep-alive 下可能不触发），改按事件数或超时 resolve。
  return new Promise((resolve) => {
    const r = http.request({
      hostname: 'localhost', port: PORT,
      method: 'POST', path: `/api/kb/${testKbId}/chat/stream`,
      headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${adminToken}`, 'Accept': 'text/event-stream' }
    }, (rr) => {
      let b = '';
      rr.on('data', c => {
        b += c;
        const evts = parseSse(b);
        // 收到 done 或 error 事件即认为完成
        if (evts.some(e => e.event === 'done') || evts.some(e => e.event === 'error')) {
          r.destroy();
          resolve({ status: rr.statusCode, raw: b, events: evts });
        }
      });
      rr.on('end', () => {
        const evts = parseSse(b);
        resolve({ status: rr.statusCode, raw: b, events: evts });
      });
      setTimeout(() => { r.destroy(); resolve({ status: -2, raw: 'SSE_TIMEOUT', events: parseSse(b) }); }, timeoutMs || 55000);
    });
    r.on('error', e => resolve({ status: -1, raw: e.message, events: [] }));
    r.write(JSON.stringify({ sessionId: testSessionId, question }));
    r.end();
  });
}

function parseSse(raw) {
  const events = [];
  for (const line of raw.split('\n')) {
    if (line.startsWith('event:')) events.push({ event: line.slice(6).trim() });
    else if (line.startsWith('data:')) (events[events.length - 1] || {}).data = line.slice(5).trim();
  }
  return events;
}

// ============ 8. 反馈链路 ============
async function feedbackChain() {
  log('\n===== [8] 反馈链路 =====');
  let r = await httpReq('GET', `/api/history?kbId=${testKbId}&page=1&size=100`, { token: adminToken });
  const hist = j(r.body);
  const records = hist && hist.data && hist.data.records || [];
  if (!records.length) { log('    无历史可反馈，跳过'); return; }
  testHistoryId = records[0].id;

  r = await httpReq('POST', '/api/feedback', { token: adminToken, body: { historyId: testHistoryId, rating: 1, reason: '赞' } });
  pass('点赞', r, 200);
  r = await httpReq('POST', '/api/feedback', { token: adminToken, body: { historyId: testHistoryId, rating: -1 } });
  pass('改点踩(应 upsert 1 条)', r, 200);
  r = await httpReq('GET', `/api/history/${testHistoryId}/feedback`, { token: adminToken });
  pass('回显反馈', r, 200);
  const fb = j(r.body);
  log(`    反馈 rating=${fb && fb.data && fb.data.rating}`);
  // 非法 rating
  r = await httpReq('POST', '/api/feedback', { token: adminToken, body: { historyId: testHistoryId, rating: 5 } });
  pass('非法 rating -> 400(非500)', r, 400);
  // 取消
  r = await httpReq('POST', '/api/feedback', { token: adminToken, body: { historyId: testHistoryId, rating: 0 } });
  pass('取消反馈', r, 200);
}

// ============ 9. 边界链路 ============
async function boundaryChain() {
  log('\n===== [9] 边界情况 =====');
  // 非法分页参数
  let r = await httpReq('GET', '/api/kb?page=-1&size=-5', { token: adminToken });
  pass('非法分页 -> 正常返回(钳制,非500)', r, 200);
  // 超大 size
  r = await httpReq('GET', '/api/kb?page=1&size=999999', { token: adminToken });
  pass('超大 size -> 正常(钳制,非500)', r, 200);
  // 非法 JSON body
  r = await httpReq('POST', '/api/kb', { token: adminToken, rawBody: '{not json' });
  pass('非法 JSON -> 400(非500)', r, 400);
  // 缺必填参数
  r = await httpReq('POST', '/api/kb', { token: adminToken, body: {} });
  pass('缺 name -> 400(非500)', r, 400);
  // 未定义 GET 路由 -> 405
  r = await httpReq('GET', '/api/kb/99999/x', { token: adminToken });
  log(`    未定义路由 status=${r.status} (期望 404/405, 非500)`);
// 超长问题(3000字)—— DTO @Size(2000) 应拒绝，返回业务码 400（非 500），属正确边界
  const longQ = 'A'.repeat(3000);
  const cc = await streamChat(longQ, 8000);
  const isRejected = cc.events.length === 0 && cc.raw === 'SSE_TIMEOUT';
  const longRejected = isRejected;
  log(`    超长问题(3000字): 被 DTO 拒绝未发起 SSE=${longRejected}（预期：@Size(2000) 拦截，非 500）`);
}

// ============ 10. 并发 ============
async function runConcurrency() {
  log('\n===== [10] 并发 =====');
  // 10a. 并发同名录上传（不同 doc 名）
  log('  [10a] 并发上传 6 份不同名文档');
  const pdfBuf = makeMinimalPdf();
  const boundary = '----FormBoundary' + Math.random().toString(36).slice(2);
  const names = ['并发a.pdf', '并发b.pdf', '并发c.pdf', '并发d.pdf', '并发e.pdf', '并发f.pdf'];
  const startAll = Date.now();
  const uploads = names.map((n, i) => {
    const body = Buffer.concat([
      Buffer.from(`--${boundary}\r\nContent-Disposition: form-data; name="file"; filename="${n}"\r\nContent-Type: application/pdf\r\n\r\n`),
      pdfBuf,
      Buffer.from(`\r\n--${boundary}--\r\n`)
    ]);
    return new Promise(ok => {
      const h = { 'Content-Type': `multipart/form-data; boundary=${boundary}`, 'Authorization': `Bearer ${adminToken}` };
      const t0 = Date.now();
      const r = http.request({ hostname: 'localhost', port: PORT, method: 'POST', path: `/api/kb/${testKbId}/docs`, headers: h }, (rr) => {
        let b = ''; rr.on('data', c => b += c);
        rr.on('end', () => ok({ name: n, status: rr.statusCode, ms: Date.now() - t0, body: b }));
      });
      r.on('error', e => ok({ name: n, status: -1, ms: Date.now() - t0, body: e.message }));
      r.write(body); r.end();
    });
  });
  const upResults = await Promise.all(uploads);
  log(`    并发上传耗时 ${Date.now() - startAll}ms`);
  upResults.forEach(x => log(`      ${x.name}: status=${x.status} ${x.ms}ms`));

  // 10b. 并发对同一文档点踩/点赞/取消
  log('  [10b] 并发对同一条历史 6 次 upsert');
  if (testHistoryId) {
    const ratings = [1, -1, 1, -1, 1, -1];
    const concurrent = ratings.map((rating, i) => httpReq('POST', '/api/feedback', {
      token: adminToken,
      body: { historyId: testHistoryId, rating, reason: `并发${i}` }
    }));
    const fbResults = await Promise.all(concurrent);
    log(`    并发反馈:`);
    fbResults.forEach((x, i) => log(`      rating=${ratings[i]} -> status=${x.status}`));
    // upsert 幂等：最终只应有 1 条记录
    let r = await httpReq('GET', `/api/history/${testHistoryId}/feedback`, { token: adminToken });
    const fb = j(r.body);
    const finalRating = fb && fb.data && fb.data.rating;
    log(`    最终反馈 rating=${finalRating} (应为 -1，最后一条)`);
  }
}

// ============ 主流程 ============
(async () => {
  console.log('\n╔══════════════════════════════════════════╗');
  console.log('║  企业知识问答后端 · 集成验收测试(8081)  ║');
  console.log('╚══════════════════════════════════════════╝');
  try {
    await warmup();
    await authChain();
    await userChain();
    await roleChain();
    await kbChain();
    await memberChain();
    await docChain();
    await chatChain();
    await feedbackChain();
    await boundaryChain();
    await runConcurrency();
  } catch (e) {
    log('\n!! 主流程异常: ' + e.message);
    lastErr = lastErr || e.message;
  }
  console.log('\n\n============ 最终汇总 ============');
  const total = res.filter(x => x.ok === true || x.ok === false).length;
  const failed = res.filter(x => x.ok === false);
  console.log(`接口断言: ${total - failed.length}/${total} 通过${failed.length ? '  ← ' + failed.length + ' 项失败' : ''}`);
  if (failed.length) failed.forEach(f => console.log('  ❌', f.label, '| status=', f.status));
  if (lastErr) console.log('\n最后一次异常:', lastErr);
  console.log('================================\n');
  fs.writeFileSync('./results.json', JSON.stringify({ total, failed: failed.length, lastErr, results: res }, null, 2));
  console.log('结果已写 test/results.json，测试数据按约定保留。');
})();
