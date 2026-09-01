const http = require('http');
const login = () => new Promise((ok) => {
  http.request({ hostname: 'localhost', port: 8081, method: 'POST', path: '/api/auth/login', headers: { 'Content-Type': 'application/json' } }, (rr) => {
    let b = ''; rr.on('data', c => b += c); rr.on('end', () => ok(JSON.parse(b).data.token));
  }).end(JSON.stringify({ username: 'admin', password: 'Admin@123456' }));
});
login().then(t => {
  console.log('token len:', t.length);
  // create session
  const post = (p, body) => new Promise((ok) => {
    http.request({ hostname: 'localhost', port: 8081, method: 'POST', path: p, headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${t}` } }, (rr) => {
      let b = ''; rr.on('data', c => b += c); rr.on('end', () => ok({ s: rr.statusCode, body: b }));
    }).end(JSON.stringify(body));
  });
  post('/api/kb/6/sessions', {}).then(r => {
    const sid = JSON.parse(r.body).data;
    console.log('sessionId:', sid);
    // SSE chat
    const r = http.request({
      hostname: 'localhost', port: 8081, method: 'POST',
      path: '/api/kb/6/chat/stream',
      headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${t}`, 'Accept': 'text/event-stream' }
    }, (rr) => {
      let b = ''; let doneAt = null;
      rr.on('data', c => b += c);
      rr.on('end', () => { doneAt = Date.now(); console.log('SSE END status=' + rr.statusCode + ' len=' + b.length + ' events=' + b.split('\n\n').length); console.log('raw:', JSON.stringify(b.slice(0, 600))); });
      rr.on('close', () => { if (!doneAt) console.log('SSE close no-end'); });
    });
    const t0 = Date.now();
    r.on('error', e => console.log('SSE ERR', e.message));
    r.write(JSON.stringify({ sessionId: sid, question: '链路测试问答' }));
    r.end();
    setTimeout(() => { console.log('SSE TIMEOUT 45s'); r.destroy(); process.exit(0); }, 46000);
  });
});
