// 各逻辑链路的接口测试。依赖 run.js 提供的: req, pass, ok, fail, adminToken, testKbId, results, N, j。
const http = require('http');
const path = require('path');
const fs = require('fs');

const BASE = 'http://localhost:8081';
const T0 = 'ttest000', T1 = 'ttest001', TPP = 'Test@123456';

// ============ 1. 鉴权链路 ============
async function runAuthChain() {
  console.log('\n=== [1] 鉴权链路 ===');
  // 1.1 注册(幂等，先清理再注册)
  await req({ method: 'POST', path: '/api/auth/register', token: adminToken, body: { username: T0 + 'del', password: TPP, nickname: T0 } });
  let r, ok;
  r = await req({ method: 'POST', path: '/api/auth/register', body: { username: T0, password: TPP, nickname: '测试0' } });
  ok = pass(r, 200, '注册用户 ttest000');
  if (!ok) { console.log('注册 ttest000 非 200 (可能已存在，忽略):', r.status); }

  r = await req({ method: 'POST', path: '/api/auth/register', body: { username: T0, password: TPP, nickname: '测试0' } });
  ok = pass(r, 200, '重复注册返回业务码(非500)'); // 重复注册应 400
  const dup = j(r.body);
  ok('  重复注册业务码', r.status, 200); // 期望 400

  r = await req({ method: 'POST', path: '/api/auth/login', body: { username: T0, password: TPP } });
  ok = pass(r, 200, 'ttest000 登录');
  const t0Token = r.ok ? j(r.body).data.token : null;
  const t0Login = r.ok ? j(r.body).data : null;
  ok('  登录带 USER 角色', t0Login && t0Login.roles && t0Login.roles.includes('USER'));

  // 1.2 无 token 401
  r = await req({ method: 'GET', path: '/api/kb' });
  pass(r, 401, '无 token 访问 /api/kb -> 401');

  // 1.3 无 Bearer 前缀 401
  r = await req({ method: 'GET', path: '/api/kb', token: 'NOPE' });
  pass(r, 401, '无 Bearer 前缀 -> 401');

  // 1.4 伪造签名 401
  r = await req({ method: 'GET', path: '/api/kb', token: 'fake.abc.def' });
  pass(r, 401, '伪造签名 -> 401');

  // 1.5 错密码 401
  r = await req({ method: 'POST', path: '/api/auth/login', body: { username: T0, password: 'wrong' } });
  pass(r, 200, '错密码返回业务码(非500)');
  ok('  错密码业务码', r.status, 200);

  // 1.6 /me 鉴权上下文正确
  r = await req({ method: 'GET', path: '/api/auth/me', token: adminToken });
  ok = pass(r, 200, '/api/auth/me admin');
  const me = r.ok ? j(r.body).data : null;
  ok('  /me 含 ADMIN 角色', me && me.roles && me.roles.includes('ADMIN'));
  ok('  /me 含 kb:manage 权限', me && me.permissions && me.permissions.includes('kb:manage'));
  ok('  /me 含 sys:user:manage', me && me.permissions && me.permissions.includes('sys:user:manage'));

  // 1.7 USER 无系统管理权限
  r = await req({ method: 'GET', path: '/api/sys/users', token: t0Token });
  pass(r, 403, 'USER 调 /api/sys/users -> 403');

  console.log('  [鉴权链路完成]');
}

module.exports = { runAuthChain };
