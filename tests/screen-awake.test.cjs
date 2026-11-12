const test=require('node:test'),assert=require('node:assert/strict');
const ScreenAwake=require('../dv-screen-awake.js');
function sentinel(){const callbacks=[];return {released:false,addEventListener:(n,fn)=>callbacks.push(fn),async release(){this.released=true;callbacks.forEach(fn=>fn());}};}
function setup(request){const states=[],timers=[];const document={visibilityState:'visible'};const controller=new ScreenAwake({navigator:{wakeLock:{request}},document,onState:s=>states.push(s),schedule:fn=>{timers.push(fn);return timers.length;},cancel:()=>{}});return {controller,states,timers,document};}
test('adquiere una sola vez, libera al ocultarse y recupera al volver',async()=>{
 const locks=[];let count=0;const {controller,document,states}=setup(async()=>{count++;const lock=sentinel();locks.push(lock);return lock;});
 await Promise.all([controller.start(),controller.start()]);assert.equal(count,1);assert.equal(states.at(-1),'active');
 document.visibilityState='hidden';controller.visibilityChanged();assert.equal(locks[0].released,true);assert.equal(states.at(-1),'hidden');
 document.visibilityState='visible';await controller.visibilityChanged();assert.equal(count,2);assert.equal(states.at(-1),'active');
 await controller.stop();assert.equal(locks[1].released,true);assert.equal(states.at(-1),'idle');
});
test('finalizar durante una solicitud pendiente no deja la pantalla bloqueada',async()=>{
 let resolve;const {controller,states}=setup(()=>new Promise(r=>resolve=r));const pending=controller.start();await Promise.resolve();
 await controller.stop();const lock=sentinel();resolve(lock);await pending;assert.equal(lock.released,true);assert.equal(controller.lock,null);assert.equal(states.at(-1),'idle');
});
test('rechazo y liberación del sistema se informan y reintentan sin bucle inmediato',async()=>{
 let mode='deny';const locks=[];const {controller,states,timers}=setup(async()=>{if(mode==='deny')throw Error('Battery saver');const lock=sentinel();locks.push(lock);return lock;});
 await controller.start();assert.equal(states.at(-1),'denied');assert.equal(timers.length,1);
 mode='ok';timers.shift()();await controller.pending;assert.equal(states.at(-1),'active');
 await locks[0].release();assert.equal(states.at(-1),'released');assert.equal(timers.length,1);await controller.stop();
});
test('sin soporte o con la página oculta no anuncia seguimiento en segundo plano',async()=>{
 const states=[],document={visibilityState:'visible'};const controller=new ScreenAwake({navigator:{},document,onState:s=>states.push(s)});await controller.start();assert.equal(states.at(-1),'unsupported');await controller.stop();
 let count=0;const x=setup(async()=>{count++;return sentinel();});x.document.visibilityState='hidden';await x.controller.start();assert.equal(count,0);assert.equal(x.states.at(-1),'hidden');
});
