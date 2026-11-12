const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');
test('la foto queda lista sin pedir ubicación nativa ni del navegador',async()=>{
 const html=fs.readFileSync('index.html','utf8');
 const start=html.indexOf('async function prepararEvidencia(file){');
 const end=html.indexOf('/* ===',start);
 const noLocation=()=>{throw Error('La foto no debe consultar GPS');};
 const c={window:{AndroidGPS:{getCurrentPosition:noLocation}},navigator:{geolocation:{getCurrentPosition:noLocation}},comprimirImagen:async()=> 'data:image/jpeg;base64,fixture',evidenciaGeneracion:0,evidenciaLat:-34,evidenciaLng:-58,evidenciaPrecision:8,sendEvidence:{disabled:true},gpsBox:{},evidencePreview:{style:{}},Date,console};
 vm.createContext(c);vm.runInContext(html.slice(start,end),c);await c.prepararEvidencia({name:'foto.jpg'});
 assert.equal(c.sendEvidence.disabled,false);assert.equal(c.evidenciaLat,'');assert.equal(c.evidenciaLng,'');assert.equal(c.evidenciaPrecision,null);assert.equal(c.evidencePreview.src,'data:image/jpeg;base64,fixture');
});
