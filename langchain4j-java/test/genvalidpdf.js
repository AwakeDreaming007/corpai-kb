// 生成一个 PDFBox 可解析的最小合法 PDF
function makeValidPdf() {
  // 用占位符 xref offset，先生成内容再回填
  const parts = [];
  parts.push('%PDF-1.4\n');
  parts.push('1 0 obj<</Type/Catalog/Pages 2 0 R>>\nendobj\n');
  parts.push('2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>\nendobj\n');
  parts.push('3 0 obj<</Type/Page/Parent 2 0 R/Resources<</Font<</F1 4 0 R>>>>>>/MediaBox[0 0 612 792]>>\nendobj\n');
  parts.push('4 0 obj<</Type/Font/Subtype/Type1/BaseFont/Helvetica>>\nendobj\n');
  parts.push('5 0 obj\n<< /Length 44 >>\nstream\nBT /F1 12 Tf 50 700 Td (link test doc for qa validation) Tj ET\nendstream\nendobj\n');
  parts.push('6 0 obj<</Type/Page/Parent 2 0 R/Resources<</Font<</F1 4 0 R>>>>/Contents 5 0 R/MediaBox[0 0 612 792]>>\nendobj\n');
  parts.push('xref\n0 7\n');
  const zeros = '0000000000 65535 f \n';
  const offsets = [];
  let off = 0;
  // placeholder xref block will be sized after
  const header = parts.join('');
  // build final by laying out: header, then objects, then xref
  // simpler: write header + xref with computed offsets
  const body = parts.join('');
  const total = body.length + zeros.length + 6 * 20 + 'trailer<</Size 7 /Root 1 0 R>>\nstartxref\n'.length + 6 + 6;
  // compute object start offsets in final layout
  let acc = 0;
  const objOffsets = [0]; // object 0 = 0
  const segs = body.split('obj\n');
  let running = 0;
  const objs = [];
  let cur = 0;
  const full = body;
  // find each "N 0 obj" start
  const re = /(\d+) 0 obj/g;
  let m;
  while ((m = re.exec(full)) !== null) {
    objs.push({ n: parseInt(m[1]), off: m.index });
  }
  // final layout = body + xref
  const xrefStart = body.length;
  const objStarts = objs.map(o => o.off);
  const xrefLines = [zeros];
  for (const s of objStarts) xrefLines.push(String(s).padStart(10, '0') + ' 00000 n \n');
  const trailer = 'trailer<</Size ' + (objs.length + 1) + ' /Root 1 0 R>>\nstartxref\n' + String(xrefStart).padStart(6, '0') + '\n%%%%EOF\n';
  const final = body + xrefLines.join('') + trailer;
  return Buffer.from(final, 'latin1');
}
const b = makeValidPdf();
require('fs').writeFileSync('/tmp/valid.pdf', b);
console.log('written /tmp/valid.pdf bytes=', b.length);
