function makeValidPdf() {
  const parts = [
    '%PDF-1.4\n',
    '1 0 obj<</Type/Catalog/Pages 2 0 R>>\nendobj\n',
    '2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>\nendobj\n',
    '3 0 obj<</Type/Page/Parent 2 0 R/Resources<</Font<</F1 4 0 R>>>>/Contents 5 0 R/MediaBox[0 0 612 792]>>\nendobj\n',
    '4 0 obj<</Type/Font/Subtype/Type1/BaseFont/Helvetica>>\nendobj\n',
    '5 0 obj\n<< /Length 44 >>\nstream\nBT /F1 12 Tf 50 700 Td (link test doc for qa validation) Tj ET\nendstream\nendobj\n'
  ];
  const body = parts.join('');
  const re = /(\d+) 0 obj/g;
  const objs = []; let m;
  while ((m = re.exec(body)) !== null) objs.push(m.index);
  const xrefStart = body.length;
  const xrefLines = ['0000000000 65535 f \n'];
  for (const s of objs) xrefLines.push(String(s).padStart(10, '0') + ' 00000 n \n');
  const trailer = 'trailer<</Size ' + (objs.length + 1) + ' /Root 1 0 R>>\nstartxref\n' + String(xrefStart).padStart(6, '0') + '\n%%EOF\n';
  return Buffer.from(body + xrefLines.join('') + trailer, 'latin1');
}
module.exports = makeValidPdf;
