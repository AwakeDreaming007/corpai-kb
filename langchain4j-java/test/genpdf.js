const mk = require('./makeValidPdf');
const b = mk();
require('fs').writeFileSync('./valid.pdf', b);
console.log('bytes', b.length);
