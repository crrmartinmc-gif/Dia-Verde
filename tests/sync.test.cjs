const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),vm=require('node:vm');
const C=require('../dv-contract.js');
const html=fs.readFileSync(path.join(__dirname,'../index.html'),'utf8');
function chunk(start,end){return html.slice(html.indexOf(start),html.indexOf(end,html.indexOf(start)));}
test('coordenadas estrictas, coma decimal, precisión ausente y mismo día de Argentina',()=>{
 assert.deepEqual(C.coords({latitud:'-34,77',longitud:'-58,4'}),[-34.77,-58.4]);
 for(const v of [null,'','  ',undefined,true])assert.equal(C.number(v),null);
 assert.equal(C.coords({latitud:'',longitud:''}),null);assert.equal(C.coords({lat:0,lng:0}),null);
 assert.equal(C.dayKey('2026-11-12T01:00:00Z'),'2026-11-11');
 assert.equal(C.dayKey('11/11/2026'),'2026-11-11');
 assert.equal(C.timestamp(1794445200),1794445200000);
 assert.equal(C.completedStatus('NO REALIZADO'),false);
 assert.notEqual(C.userKey({usuario:'Pablo',nombre:'Pablo Vazquez'}),C.userKey({usuario:'Javi',nombre:'Pablo Vazquez'}));
 assert.notEqual(C.gpsSignature([{lat:-34,lng:-58,activo:1}]),C.gpsSignature([{lat:-34,lng:-58,activo:0}]));
});
test('la cola GPS solo retira el lote confirmado; conserva errores, falta de confirmación y puntos agregados durante el envío',async()=>{
 let resolve,mode='deferred',calls=0;const saved=[];
 const ctx=vm.createContext({DVContract:C,URLSearchParams,AbortController,Set,Date,Math,console:{warn:()=>{}},
  navigator:{onLine:true},document:{getElementById:()=>null},setTimeout:()=>1,clearTimeout:()=>{},
  dvPanelGpsQueueBusy:false,dvPanelGpsQueue:[{idPunto:'a',lat:-34,lng:-58,usuario:'prueba',nombre:'Prueba',timestamp:new Date().toISOString(),activo:true}],
  dvPanelGpsStorageError:false,dvPanelGpsSendCount:0,dvPanelGpsLastSentAt:0,dvPanelGpsLastError:'',APPS_SCRIPT_URL:'https://example.test/exec',
  dvPanelGpsProgramarCola:()=>{},dvPanelGpsGuardarCola:()=>{saved.push(true);return true;},
  fetch:async()=>{calls++;if(mode==='error')throw Error('offline');if(mode==='bad')return {ok:true,json:async()=>({ok:false})};return await new Promise(r=>resolve=r);}
 });vm.runInContext(chunk('let dvPanelGpsReintentos','function registrarGPSPanel('),ctx);
 const first=ctx.dvPanelGpsProcesarCola();ctx.dvPanelGpsQueue.push({idPunto:'b',lat:-34.01,lng:-58,usuario:'prueba'});
 resolve({ok:true,json:async()=>({ok:true,accion:'registrarGPSLote',recibidos:1})});await first;
 assert.equal(ctx.dvPanelGpsQueue.length,1);assert.equal(ctx.dvPanelGpsQueue[0].idPunto,'b');assert.equal(ctx.dvPanelGpsSendCount,1);
 mode='error';await ctx.dvPanelGpsProcesarCola();assert.equal(ctx.dvPanelGpsQueue.length,1);assert.ok(ctx.dvPanelGpsLastError);
 mode='bad';await ctx.dvPanelGpsProcesarCola();assert.equal(ctx.dvPanelGpsQueue.length,1);
 ctx.navigator.onLine=false;const before=calls;await ctx.dvPanelGpsProcesarCola();assert.equal(calls,before);assert.equal(ctx.dvPanelGpsQueue.length,1);
});
test('no hay geocodificación independiente; el cliente usa ubicaciones compartidas',()=>{
 assert(!html.includes('nominatim.openstreetmap.org'));
 assert(html.includes('accion=reclamosDiaVerde'));
 assert(!chunk('function dvPanelGpsProcesarCola','function registrarGPSPanel').includes('new Image'));
 for(const m of html.matchAll(/<script\b[^>]*>([\s\S]*?)<\/script>/g))new vm.Script(m[1]);
});
test('misma evidencia y mismo criterio de realización en ambas apps; no mezcla CGM ni días',()=>{
 const a={localidad:'Lomas',zona:'Este',calle:'Av. Prueba',numero:'123'},b={...a,localidad:'Banfield'};
 const e={direccion:'Avenida Prueba 123',zona:'Este',estado:'Realizado Hoy',cgm:'Lomas'};
 assert.equal(C.evidenceMatches(a,e,[a,b]),true);assert.equal(C.evidenceMatches(b,e,[a,b]),false);
 assert.equal(C.evidenceMatches(a,{...e,cgm:''},[a,b]),false);
 assert.equal(C.evidenceMatches(a,{...e,estado:'No realizado'},[a]),false);
});
