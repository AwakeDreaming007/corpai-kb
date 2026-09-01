const http = require('http');
http.request({ hostname: 'localhost', port: 8081, method: 'POST', path: '/api/auth/login', headers: { 'Content-Type': 'application/json', 'Content-Length': 33 } }, (rr) => {
  let b = '';
  rr.on('data', c => b += c);
  rr.on('end', () => {
    console.log('STATUS', rr.statusCode);
    console.log('HEADS', JSON.stringify(rr.headers));
    try {
      const o = JSON.parse(b);
      console.log('TOKEN', o.data && o.data.token ? 'LEN=' + o.data.token.length : 'MISSING');
      console.log('ROLES', JSON.stringify(o.data && o.data.roles));
    } catch (e) { console.log('PARSE_ERR', e.message, 'RAW:', b.slice(0, 200)); }
  });
}).end(JSON.stringify({ username: 'admin', password: 'Admin@123456' }));
