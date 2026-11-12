const {test}=require('node:test');
const assert=require('node:assert/strict');
const vm=require('node:vm');
const fs=require('node:fs');
const html=fs.readFileSync('index.html','utf8');
const start=html.indexOf('let dvCapturaPendiente=null;');
const source=html.slice(start,html.indexOf('sendEvidence.addEventListener(',start));
function setup(){
 const timers=[],events={},inputEvents={},calls=[];
 const cameraInput={value:'anterior',files:[],addEventListener:(e,f)=>inputEvents[e]=f,click(){calls.push('camera');}};
 const c={cameraInput,evidenciaGeneracion:1,evidenciaBase64:'',gpsBox:{},sendEvidence:{disabled:true},takeEvidence:{disabled:false,addEventListener:(e,f)=>events.take=f},document:{hidden:false,addEventListener:(e,f)=>events[e]=f},window:{addEventListener:(e,f)=>events[e]=f},setTimeout:(fn,delay)=>timers.push({fn,delay}),prepararEvidencia:file=>{calls.push(file);c.evidenciaGeneracion++;}};
 vm.createContext(c);vm.runInContext(source,c);return {c,events,inputEvents,timers,calls};
}
test('input y change reciben una sola foto y muestran estado inmediato',()=>{
 const {c,events,inputEvents,calls}=setup();events.take();assert.equal(c.cameraInput.value,'');assert.match(c.gpsBox.textContent,/Abriendo cámara/);const file={name:'repetida.jpg'};c.cameraInput.files=[file];inputEvents.input();inputEvents.change();assert.equal(calls.filter(v=>v===file).length,1);
});
test('recupera foto al volver al foco sin change',()=>{
 const {c,events,timers,calls}=setup();events.take();const file={};c.cameraInput.files=[file];events.focus();timers.forEach(t=>t.fn());assert.ok(calls.includes(file));
});
test('acepta entrega tardía después del aviso',()=>{
 const {c,events,inputEvents,timers,calls}=setup();events.take();events.visibilitychange();timers.forEach(t=>t.fn());assert.match(c.gpsBox.textContent,/Todavía no recibimos/);const file={};c.cameraInput.files=[file];inputEvents.change();assert.ok(calls.includes(file));
});
test('descarta captura después de cambiar de reclamo',()=>{
 const {c,events,inputEvents,calls}=setup();events.take();c.evidenciaGeneracion++;c.cameraInput.files=[{}];inputEvents.change();assert.equal(calls.length,1);
});
test('cancelar permite reintentar con explicación',()=>{
 const {c,events,inputEvents}=setup();events.take();inputEvents.cancel();assert.match(c.gpsBox.textContent,/No se recibió/);assert.equal(c.sendEvidence.disabled,true);assert.equal(c.takeEvidence.disabled,false);
});
test('error tardío de compresión no sobrescribe otro reclamo',async()=>{
 const a=html.indexOf('async function prepararEvidencia(file){'),b=html.indexOf('/* ===',a);let rejectPhoto;
 const c={comprimirImagen:()=>new Promise((_,r)=>rejectPhoto=r),evidenciaGeneracion:0,evidenciaBase64:'vieja',evidenciaLat:'',evidenciaLng:'',evidenciaPrecision:null,sendEvidence:{},takeEvidence:{},gpsBox:{},evidencePreview:{style:{}},Date,setTimeout,console};vm.createContext(c);vm.runInContext(html.slice(a,b),c);
 const pending=c.prepararEvidencia({});await new Promise(r=>setTimeout(r,5));assert.equal(c.evidenciaBase64,'');c.evidenciaGeneracion++;c.gpsBox.textContent='Otro reclamo';rejectPhoto(Error('cancelada'));await pending;assert.equal(c.gpsBox.textContent,'Otro reclamo');
});
