/* Contrato de coordenadas, identidad y fechas compartido por Día Verde y Panel. */
(function(root, factory){
  const api = factory();
  if(typeof module === 'object' && module.exports) module.exports = api;
  else root.DVContract = api;
})(typeof globalThis === 'object' ? globalThis : this, function(){
  'use strict';
  const zone = 'America/Argentina/Buenos_Aires';
  function number(value){
    if(value == null || typeof value === 'boolean') return null;
    if(typeof value !== 'number' && typeof value !== 'string') return null;
    const text = String(value).trim().replace(/\s/g, '').replace(',', '.');
    if(!text) return null;
    const n = Number(text);
    return Number.isFinite(n) ? n : null;
  }
  function pick(o, keys){
    for(const k of keys){ if(o && o[k] != null && String(o[k]).trim() !== '') return o[k]; }
    return null;
  }
  function normalize(value){
    return String(value ?? '').normalize('NFD').replace(/[\u0300-\u036f]/g, '')
      .replace(/\s+/g, ' ').trim().toLowerCase();
  }
  function coords(o){
    const lat = number(pick(o, ['latitud','LATITUD','Latitud','latitude','Latitude','LATITUDE','lat','Lat','LAT']));
    const lng = number(pick(o, ['longitud','LONGITUD','Longitud','longitude','Longitude','LONGITUDE','lng','Lng','LNG','lon','Lon','LON']));
    return lat !== null && lng !== null && Math.abs(lat) <= 90 && Math.abs(lng) <= 180 &&
      (lat !== 0 || lng !== 0) ? [lat, lng] : null;
  }
  function timestamp(value){
    if(value == null || value === '') return null;
    if(value instanceof Date) return Number.isFinite(value.getTime()) ? value.getTime() : null;
    const s = String(value).trim();
    if(/^\d{10}(?:\.\d+)?$/.test(s)) return Number(s) * 1000;
    if(/^\d{13}$/.test(s)) return Number(s);
    const local = s.match(/^(\d{1,2})\/(\d{1,2})\/(\d{4})(?:[ T,]+(\d{1,2}):(\d{2})(?::(\d{2}))?)?$/);
    const isoLocal = s.match(/^\d{4}-\d{2}-\d{2}(?:[ T]\d{2}:\d{2}(?::\d{2}(?:\.\d+)?)?)?$/);
    const text = local
      ? `${local[3]}-${local[2].padStart(2,'0')}-${local[1].padStart(2,'0')}T${(local[4]||'00').padStart(2,'0')}:${local[5]||'00'}:${local[6]||'00'}-03:00`
      : isoLocal ? (s.length === 10 ? s + 'T00:00:00' : s.replace(' ','T')) + '-03:00' : s;
    const t = Date.parse(text);
    return Number.isFinite(t) && t > 0 ? t : null;
  }
  function dayKey(value = Date.now()){
    const t = timestamp(value);
    if(t === null) return '';
    const parts = new Intl.DateTimeFormat('en-CA', {timeZone:zone,year:'numeric',month:'2-digit',day:'2-digit'}).formatToParts(new Date(t));
    const part = key => parts.find(p => p.type === key)?.value;
    return `${part('year')}-${part('month')}-${part('day')}`;
  }
  function date(o){ return pick(o, ['timestamp','TIMESTAMP','Timestamp','fecha_hora','FECHA_HORA','fechaHora','fecha','FECHA','Fecha']); }
  function userKey(o){
    const user = pick(o, ['usuario','USUARIO','Usuario']);
    return user && String(user).trim() !== '-' ? 'u:' + normalize(user)
      : 'n:' + normalize(pick(o, ['nombre','NOMBRE','Nombre']) || 'sin nombre');
  }
  function active(o){
    const value = pick(o, ['activo','ACTIVO','Activo']);
    if(value == null) return null;
    return !['0','false','no','inactivo','finalizado'].includes(normalize(value));
  }
  function routeId(o){ return String(pick(o, ['idRecorrido','ID_RECORRIDO','recorridoId']) || ''); }
  function distance(a,b){
    const rad = x => x * Math.PI / 180;
    const h = Math.sin(rad(b[0]-a[0])/2)**2 + Math.cos(rad(a[0])) * Math.cos(rad(b[0])) * Math.sin(rad(b[1]-a[1])/2)**2;
    return 6371000 * 2 * Math.asin(Math.sqrt(Math.min(1,h)));
  }
  function gpsSignature(list){
    return JSON.stringify(list.map(p=>[userKey(p), coords(p), timestamp(date(p)), active(p), routeId(p), pick(p,['zona','ZONA','Zona']),pick(p,['precision','PRECISION','Precision'])]));
  }
  function claimKey(d){
    const id = pick(d, ['id','ID','idReclamo','IdReclamo','codigo','Codigo','nro','Nro']);
    return id ? 'ID|' + normalize(id)
      : [d?.localidad ?? d?.cgm ?? d?.CGM, d?.zona ?? d?.Zona, d?.calle, d?.numero].map(normalize).join('|');
  }
  function completedStatus(value){
    const s = normalize(value);
    if(!s) return null;
    if(/no\s+(realiz|resuelt|complet|finaliz|atendid|levantad)|pendient|abiert|ingres|asignad|en proceso|suspend/.test(s)) return false;
    if(/realiz|resuelt|complet|finaliz|atendid|levantad/.test(s)) return true;
    return null;
  }
  function address(d){
    const text = pick(d,['direccion','Direccion','DIRECCION','domicilio']) ||
      `${pick(d,['calle','Calle','CALLE']) || ''} ${pick(d,['numero','Numero','NUMERO']) || ''}`;
    return normalize(text).replace(/\bavenida\b/g,'av').replace(/\bav\./g,'av')
      .replace(/[.,;:#º°]+/g,' ').replace(/\s+/g,' ').trim();
  }
  function evidenceMatches(d,e,claims = []){
    if(!d || !e || completedStatus(pick(e,['estado','ESTADO'])) === false) return false;
    const key = pick(e,['claveReclamo','CLAVE_RECLAMO','idReclamo','ID_RECLAMO']);
    if(key) return normalize(key) === normalize(d.clave || claimKey(d)) ||
      normalize(key) === normalize(pick(d,['id','ID','idReclamo','ID_RECLAMO']));
    if(!address(d) || address(d)!==address(e)) return false;
    const city = normalize(pick(d,['localidad','LOCALIDAD','cgm','CGM']));
    const cityE = normalize(pick(e,['localidad','LOCALIDAD','cgm','CGM']));
    const z = normalize(pick(d,['zona','Zona','ZONA']));
    let ze = normalize(pick(e,['zona','Zona','ZONA']));
    // Compatibilidad con históricos que guardaban "CGM — zona".
    if(city && ze === city+' — '+z) return true;
    if(ze && z && ze!==z) return false;
    if(cityE) return city===cityE;
    const candidates = claims.filter(c=>address(c)===address(d) && normalize(pick(c,['zona','Zona','ZONA']))===z);
    const cities = new Set(candidates.map(c=>normalize(pick(c,['localidad','LOCALIDAD','cgm','CGM']))));
    return cities.size <= 1;
  }
  return {number,pick,normalize,coords,timestamp,dayKey,date,userKey,active,routeId,distance,gpsSignature,claimKey,completedStatus,address,evidenceMatches,zone};
});
