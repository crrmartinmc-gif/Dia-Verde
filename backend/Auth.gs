/** Agregar al MISMO proyecto de Apps Script que Code.gs. Sin contraseñas públicas. */
function dvAuthJSON_(datos) {
  datos.authVersion = 2;
  return ContentService.createTextOutput(JSON.stringify(datos))
    .setMimeType(ContentService.MimeType.JSON);
}

function dvAuthHash_(texto, secreto) {
  return Utilities.base64EncodeWebSafe(
    Utilities.computeHmacSha256Signature(texto, secreto, Utilities.Charset.UTF_8)
  );
}

function dvAuthIguales_(a, b) {
  a = String(a); b = String(b);
  var diferencia = a.length ^ b.length;
  for (var i = 0; i < Math.max(a.length, b.length); i++) {
    diferencia |= (a.charCodeAt(i) || 0) ^ (b.charCodeAt(i) || 0);
  }
  return diferencia === 0;
}

/** Ejecutar desde el editor, nunca desde doGet/doPost.
 * Antes: cargar DV_AUTH_IMPORT en Propiedades de la secuencia de comandos.
 * Formato JSON: [{"usuario":"...","password":"...","nombre":"..."}]
 * Se conserva cada cuenta y clave; se elimina el JSON original tras importar.
 */
function importarUsuariosDiaVerde() {
  var lock = LockService.getScriptLock();
  lock.waitLock(20000);
  try {
    var props = PropertiesService.getScriptProperties();
    var lista = JSON.parse(props.getProperty('DV_AUTH_IMPORT') || 'null');
    if (!Array.isArray(lista) || !lista.length) throw new Error('Falta DV_AUTH_IMPORT.');
    var secreto = props.getProperty('DV_AUTH_SECRET') ||
      Utilities.getUuid() + Utilities.getUuid() + Utilities.getUuid();
    var usuarios = {};
    lista.forEach(function(u) {
      var nombreUsuario = String(u.usuario || '').trim();
      var clave = nombreUsuario.toLowerCase();
      if (!clave || !u.password || Object.prototype.hasOwnProperty.call(usuarios, clave) ||
          ['__proto__', 'constructor', 'prototype'].indexOf(clave) >= 0) {
        throw new Error('La lista contiene cuentas inválidas o repetidas.');
      }
      var sal = Utilities.getUuid();
      usuarios[clave] = { usuario: nombreUsuario, nombre: String(u.nombre || nombreUsuario),
        sal: sal, hash: dvAuthHash_(sal + '\n' + String(u.password), secreto) };
    });
    var serializado = JSON.stringify(usuarios);
    if (Utilities.newBlob(serializado).getBytes().length > 8500) {
      throw new Error('La lista supera el tamaño admitido para esta configuración.');
    }
    props.setProperties({DV_AUTH_SECRET: secreto, DV_AUTH_USERS: serializado});
    props.deleteProperty('DV_AUTH_IMPORT');
    console.log('Importación terminada: ' + lista.length + ' cuentas.');
  } finally { lock.releaseLock(); }
}

/** Devuelve null para las operaciones existentes de evidencias. */
function dvProcesarAuth_(e) {
  var datos;
  try { datos = JSON.parse(e && e.postData && e.postData.contents || '{}'); }
  catch (error) { return null; }
  if (['diaVerdeLogin', 'diaVerdeSesion', 'diaVerdeLogout'].indexOf(datos.accion) < 0) return null;
  try {
    var cache = CacheService.getScriptCache();
    var token = String(datos.token || '');
    if (datos.accion !== 'diaVerdeLogin') {
      if (!/^[a-f0-9-]{72}$/.test(token)) return dvAuthJSON_({ok:false});
      var sesionKey = 'dvSession:' + token;
      if (datos.accion === 'diaVerdeLogout') {
        cache.remove(sesionKey);
        return dvAuthJSON_({ok:true});
      }
      var sesion = cache.get(sesionKey);
      return dvAuthJSON_(sesion ? {ok:true, usuario:JSON.parse(sesion)} : {ok:false});
    }
    var usuario = String(datos.usuario || '').trim().toLowerCase();
    var password = String(datos.password || '');
    if (!usuario || usuario.length > 150 || !password || password.length > 512) {
      return dvAuthJSON_({ok:false, error:'Usuario o contraseña incorrectos.'});
    }
    var lock = LockService.getScriptLock();
    lock.waitLock(10000);
    try {
      var props = PropertiesService.getScriptProperties();
      var secreto = props.getProperty('DV_AUTH_SECRET');
      var usuariosJSON = props.getProperty('DV_AUTH_USERS');
      if (!secreto || !usuariosJSON) {
        return dvAuthJSON_({ok:false, error:'El ingreso todavía no fue configurado en el servidor.'});
      }
      var intentosKey = 'dvAttempts:' + dvAuthHash_(usuario, secreto);
      var registro = JSON.parse(cache.get(intentosKey) || '{"cantidad":0,"hasta":0}');
      if (registro.hasta > Date.now()) {
        return dvAuthJSON_({ok:false, error:'Demasiados intentos. Esperá 15 minutos y volvé a intentar.'});
      }
      var usuarios = JSON.parse(usuariosJSON);
      var u = Object.prototype.hasOwnProperty.call(usuarios, usuario) ? usuarios[usuario] : null;
      var hash = dvAuthHash_((u ? u.sal : 'cuenta-ausente') + '\n' + password, secreto);
      if (!u || !dvAuthIguales_(u.hash, hash)) {
        registro.cantidad++;
        if (registro.cantidad >= 10) registro.hasta = Date.now() + 900000;
        cache.put(intentosKey, JSON.stringify(registro), 900);
        return dvAuthJSON_({ok:false, error:'Usuario o contraseña incorrectos.'});
      }
      cache.remove(intentosKey);
      var perfil = {usuario:u.usuario, nombre:u.nombre};
      token = Utilities.getUuid() + Utilities.getUuid();
      cache.put('dvSession:' + token, JSON.stringify(perfil), 21600);
      return dvAuthJSON_({ok:true, token:token, usuario:perfil});
    } finally { lock.releaseLock(); }
  } catch (error) {
    return dvAuthJSON_({ok:false, error:'No se pudo validar el ingreso. Volvé a intentar.'});
  }
}
