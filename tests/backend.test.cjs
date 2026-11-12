const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),vm=require('node:vm'),crypto=require('node:crypto');
function sheet(data){return {data,getLastRow:()=>data.length,getLastColumn:()=>Math.max(0,...data.map(r=>r.length)),appendRow:r=>data.push(r),getDataRange:()=>({getValues:()=>data,getDisplayValues:()=>data.map(row=>row.map(String))}),getRange:(r,c,n=1,m=1)=>({getValue:()=>data[r-1]?.[c-1]??'',setValue:v=>{data[r-1]??=[];data[r-1][c-1]=v;},getValues:()=>Array.from({length:n},(_,i)=>Array.from({length:m},(_,j)=>data[r-1+i]?.[c-1+j]??'')),getDisplayValues:()=>Array.from({length:n},(_,i)=>Array.from({length:m},(_,j)=>String(data[r-1+i]?.[c-1+j]??''))),setValues:values=>values.forEach((row,i)=>{data[r-1+i]??=[];row.forEach((v,j)=>data[r-1+i][c-1+j]=v);})})}}
function setup(){
 const gps=sheet([]),historico=sheet([['FECHA','HORA','ZONA','DIRECCION','LATITUD','LONGITUD','FOTO','ESTADO','ID_EVIDENCIA']]);let files=0;
 const props={ID_HOJA_DE_DISPERSION:'sheet-fixture',CARPETA_DRIVE_ID:'folder-fixture'};
 const folder={getFoldersByName:()=>({hasNext:()=>true,next:()=>folder}),createFile:()=>{files++;return {getUrl:()=> 'https://example.test/photo'}}};
 const ctx=vm.createContext({console:{log:()=>{},error:()=>{},warn:()=>{}},Date,Set,PropertiesService:{getScriptProperties:()=>({getProperty:k=>props[k]||null})},LockService:{getScriptLock:()=>({waitLock:()=>{},releaseLock:()=>{}})},SpreadsheetApp:{openById:()=>({getSheetByName:n=>n==='Hoja 1'?historico:gps,insertSheet:()=>gps}),flush:()=>{}},DriveApp:{getFolderById:()=>folder},Utilities:{formatDate:(d,z,f)=>({'dd/MM/yyyy':'11/11/2026','yyyy':'2026','MM - MMMM':'11 - noviembre','dd-MM-yyyy':'11-11-2026'})[f],base64Decode:s=>Buffer.from(s,'base64'),newBlob:()=>({}),base64EncodeWebSafe:b=>Buffer.from(b).toString('base64url'),DigestAlgorithm:{SHA_256:'sha256'},Charset:{UTF_8:'utf8'},computeDigest:(a,s)=>crypto.createHash('sha256').update(s).digest()},ContentService:{MimeType:{JSON:'JSON'},createTextOutput:s=>({setMimeType:()=>JSON.parse(s)})}});
 vm.runInContext(fs.readFileSync(path.join(__dirname,'../backend/Codigo-Completo-Dia-Verde.txt'),'utf8'),ctx);return {ctx,gps,historico,files:()=>files};
}
test('reintento del mismo GPS confirma el lote sin duplicar; conserva coordenadas, captura y recorrido',()=>{
 const {ctx,gps}=setup();const p={usuario:'prueba',nombre:'Prueba',latitud:'-34,77',longitud:'-58,4',timestamp:'2026-11-12T01:00:00Z',precision:null,rumbo:null,activo:1,idPunto:'p-1',idRecorrido:'r-1',cgm:'Lomas'};
 let r=ctx.registrarGPSLote({parameter:{lote:JSON.stringify([p])}});assert.equal(r.ok,true);assert.equal(r.guardados,1);
 r=ctx.registrarGPSLote({parameter:{lote:JSON.stringify([p])}});assert.equal(r.guardados,0);assert.equal(r.duplicados,1);assert.equal(r.recibidos,1);assert.equal(gps.data.length,2);
 assert.equal(gps.data[1][4],-34.77);assert.equal(gps.data[1][5],-58.4);assert.equal(gps.data[1][0].getTime(),Date.parse(p.timestamp));assert.equal(gps.data[1][6],'');assert.equal(gps.data[1][10],'r-1');assert.equal(gps.data[1][11],'Lomas');
 const end=ctx.registrarGPSLote({parameter:{lote:JSON.stringify([{...p,idPunto:'p-2',activo:0}])}});assert.equal(end.guardados,1);assert.equal(gps.data[2][8],0);
 const bad=ctx.registrarGPSLote({parameter:{lote:JSON.stringify([{...p,latitud:''}])}});assert.equal(bad.ok,false);assert.equal(gps.data.length,3);
});
test('evidencia conserva identidad y ubicación de la toma; mismo ID no duplica la foto',()=>{
 const {ctx,historico,files}=setup();const data={fecha:'11/11/2026',hora:'22:00',zona:'Este',cgm:'Banfield',direccion:'Prueba 123',latitud:-34.77,longitud:-58.4,idEvidencia:'ev-1',usuario:'prueba',nombre:'Prueba',idRecorrido:'r-1',claveReclamo:'banfield|este|prueba|123',precision:8,timestamp:'2026-11-12T01:00:00Z',origenUbicacion:'GPS_TOMA',foto:'data:image/jpeg;base64,dGVzdA=='};
 function post(d){return ctx.doPost({postData:{contents:JSON.stringify(d)}})}
 assert.equal(post(data).ok,true);assert.equal(post(data).duplicado,true);assert.equal(historico.data.length,2);assert.equal(files(),1);
 const ev=ctx.obtenerEvidenciasHoy().evidencias[0];assert.equal(ev.claveReclamo,data.claveReclamo);assert.equal(ev.cgm,'Banfield');assert.equal(ev.usuario,'prueba');assert.equal(ev.latitud,String(data.latitud));
 // La misma calle en otro CGM no es la misma evidencia.
 assert.equal(post({...data,idEvidencia:'ev-2',cgm:'Temperley'}).ok,true);assert.equal(historico.data.length,3);
});
