(function(){
  'use strict';
  if(location.origin!=='https://crrmartinmc-gif.github.io'||!/^\/Dia-Verde(?:\/|$)/.test(location.pathname))return;
  let history=null;
  const captures=new Map();
  function user(){try{return JSON.parse(localStorage.getItem('diaVerdeUsuarioV1')||'{}')||{};}catch(e){return {};}}
  function post(action,data){window.DiaVerdeNative.postMessage(JSON.stringify({action,data:data||{}}));}
  function config(){const u=user();return {usuario:u.usuario||'',nombre:u.nombre||'',token:localStorage.getItem('diaVerdeAuthV2')||'',
    idRecorrido:typeof recorridoId!=='undefined'?recorridoId:'',cgm:typeof recorridoCgm!=='undefined'?recorridoCgm:'',
    zona:typeof recorridoZona!=='undefined'?recorridoZona:'',inicio:typeof recorridoInicio!=='undefined'?Date.parse(recorridoInicio):Date.now(),
    contractVersion:typeof window.onDiaVerdeNativeState==='function'?1:0,backend:typeof APPS_SCRIPT_URL!=='undefined'?APPS_SCRIPT_URL:''};}
  window.AndroidGPS={ownsGpsUploads:true,bridgeVersion:1,
    startTracking:function(){post('start',config());},stopTracking:function(){post('stop');},
    keepScreenOn:function(){},releaseScreenOn:function(){},
    requestState:function(){post('state',{usuario:user().usuario||''});},
    getCurrentPosition:function(){return new Promise(resolve=>{const id=String(Date.now())+'-'+Math.random();const timer=setTimeout(()=>{captures.delete(id);resolve(null);},10000);captures.set(id,result=>{clearTimeout(timer);resolve(result);});post('capture',{id});});},
    _receive:function(kind,data){
      if(kind==='capture'){const resolve=captures.get(data.id);if(resolve){captures.delete(data.id);resolve(data.coords?{coords:data.coords}:null);}return;}
      if(kind==='history_start'){history={...data,points:[]};return;}
      if(kind==='history_chunk'){if(history)history.points.push(...data.points);return;}
      if(kind==='history_end'){if(history&&typeof window.onDiaVerdeNativeState==='function')window.onDiaVerdeNativeState(history);history=null;return;}
      if(kind==='location'&&typeof window.onNativeLocation==='function')window.onNativeLocation({lat:data.latitud,lng:data.longitud,accuracy:data.precision,bearing:data.rumbo,timestamp:Date.parse(data.timestamp)});
      if((kind==='sync'||kind==='error'||kind==='start_rejected')&&typeof window.onDiaVerdeNativeStatus==='function')window.onDiaVerdeNativeStatus(kind,data);
    }
  };
  document.addEventListener('DOMContentLoaded',()=>{document.body.classList.add('dv-native-app');const help=document.querySelector('.background-help');if(help)help.textContent='En la APK, el recorrido continúa con pantalla bloqueada mientras la notificación GPS esté activa.';setTimeout(()=>window.AndroidGPS.requestState(),0);});
})();
