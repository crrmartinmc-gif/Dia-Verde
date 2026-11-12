const {test}=require('node:test');
const assert=require('node:assert/strict');
const vm=require('node:vm');
const fs=require('node:fs');
const script=fs.readFileSync('android-studio/app/src/main/assets/native-bridge.js','utf8');
function setup(path='/Dia-Verde/'){
 const sent=[];const c={location:{origin:'https://crrmartinmc-gif.github.io',pathname:path},localStorage:{getItem:k=>k==='diaVerdeUsuarioV1'?JSON.stringify({usuario:'fixture',nombre:'Operador'}):'fixture-token'},document:{addEventListener(){}},setTimeout,clearTimeout,Map,Date,Math,recorridoId:'route-fixture',recorridoCgm:'Banfield',recorridoZona:'Este',recorridoInicio:new Date().toISOString(),APPS_SCRIPT_URL:'https://example.test/exec'};
 c.window=c;c.DiaVerdeNative={postMessage:m=>sent.push(JSON.parse(m))};c.onDiaVerdeNativeState=s=>c.state=s;vm.runInNewContext(script,c);return {c,sent};
}
test('native bridge is restricted to Día Verde and sends authenticated route metadata',()=>{
 assert.equal(setup('/other/').c.AndroidGPS,undefined);const {c,sent}=setup();c.AndroidGPS.startTracking();assert.equal(sent[0].action,'start');assert.equal(sent[0].data.idRecorrido,'route-fixture');assert.equal(sent[0].data.usuario,'fixture');assert.equal(sent[0].data.contractVersion,1);assert.equal(c.AndroidGPS.ownsGpsUploads,true);
});
test('native history preserves old points and exact coordinates across chunks',()=>{
 const {c}=setup();const a=c.AndroidGPS;a._receive('history_start',{route:{id:'route-fixture'}});a._receive('history_chunk',{points:[{lat:-34.7,lng:-58.4,timestamp:1}]});a._receive('history_chunk',{points:[{lat:-34.8,lng:-58.5,timestamp:2}]});a._receive('history_end',{});assert.equal(c.state.points.length,2);assert.equal(c.state.points[1].lat,-34.8);
});
test('native capture waits for actual position and stop uses native service',async()=>{
 const {c,sent}=setup();const p=c.AndroidGPS.getCurrentPosition();c.AndroidGPS._receive('capture',{id:sent[0].data.id,coords:{latitude:-34.7,longitude:-58.4}});assert.equal((await p).coords.latitude,-34.7);c.AndroidGPS.stopTracking();assert.equal(sent.at(-1).action,'stop');
});
