const test=require('node:test'),assert=require('node:assert/strict');
const fs=require('node:fs'),vm=require('node:vm');
const C=require('../dv-contract.js');
const html=fs.readFileSync(require('node:path').join(__dirname,'../index.html'),'utf8');
const code=html.slice(html.indexOf('let dvEvidenceCache='),html.indexOf('function claveDireccion('));
test('reuses evidence results and invalidates after refresh, local insertion and day change',()=>{
 let matches=0,today=C.dayKey();
 const item={id:'1',calle:'Boedo',numero:'2223',zona:'Sur',localidad:'Lomas'};
 const evidence={claveReclamo:C.claimKey(item),fecha:new Date().toISOString()};
 const ctx=vm.createContext({DVContract:{...C,dayKey:v=>v===undefined?today:C.dayKey(v),
   evidenceMatches:(...args)=>{matches++;return C.evidenceMatches(...args)}},
   data:[item],evidenciasHoy:[evidence]});
 vm.runInContext(code,ctx);
 for(let i=0;i<20;i++)assert.equal(ctx.dvEvidenciaDeItem(item),evidence);
 assert.equal(matches,1);
 ctx.evidenciasHoy=[];assert.equal(ctx.dvEvidenciaDeItem(item),null);
 ctx.evidenciasHoy.push(evidence);assert.equal(ctx.dvEvidenciaDeItem(item),evidence);
 ctx.data=[{...item}];assert.equal(ctx.dvEvidenciaDeItem(ctx.data[0]),evidence);
 today='2099-01-01';assert.equal(ctx.dvEvidenciaDeItem(ctx.data[0]),null);
});
test('every inline script parses',()=>{
 for(const m of html.matchAll(/<script\b[^>]*>([\s\S]*?)<\/script>/g))new vm.Script(m[1]);
});
