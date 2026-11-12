package com.ambientelomas.diaverde;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.*;
import android.net.Uri;
import android.net.http.SslError;
import android.os.*;
import android.provider.MediaStore;
import android.provider.Settings;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import androidx.core.content.FileProvider;
import androidx.webkit.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int PERMISSIONS=20,CAMERA=21,FILE=22;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private WebView web;private TextView status;
    private JSONObject pendingStart;private String owner="",captureId=null;
    private boolean resumed=false,permissionDialog=false,validating=false,receiverRegistered=false,destroyed=false;
    private int startGeneration=0;private String startingId="";private Runnable captureTimeout;
    private ValueCallback<Uri[]> fileCallback;private Uri photo;private File photoFile;
    private LocationListener captureListener;
    private String lastSync="";
    private final Runnable syncTask=this::readSync;
    private final BroadcastReceiver receiver=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent intent){
        String kind=intent.getStringExtra("kind");
        if("location".equals(kind)){
            try{JSONObject p=new JSONObject(intent.getStringExtra("data"));if(owner.equalsIgnoreCase(p.optString("usuario")))send("location",p);}catch(Exception ignored){}
            sendSync();
        }else if("error".equals(kind)){
            try{send("error",new JSONObject(intent.getStringExtra("data")));}catch(Exception ignored){}
        }else if("state".equals(kind))sendState();else sendSync();
    }};

    @SuppressLint("SetJavaScriptEnabled") @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(0xfff3fcf6);
        web=new WebView(this);root.addView(web,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);bar.setPadding(dp(12),dp(4),dp(8),dp(4));
        status=new TextView(this);status.setTextColor(0xff185739);status.setTextSize(12);status.setText("Día Verde · GPS nativo listo");bar.addView(status,new LinearLayout.LayoutParams(0,-2,1));
        Button settings=new Button(this);settings.setText("Ajustes");settings.setTextSize(12);settings.setOnClickListener(v->showSettings());bar.addView(settings,new LinearLayout.LayoutParams(-2,dp(44)));root.addView(bar);setContentView(root);
        if(Build.VERSION.SDK_INT>=30)root.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());android.graphics.Insets ime=insets.getInsets(WindowInsets.Type.ime());v.setPadding(bars.left,bars.top,bars.right,Math.max(bars.bottom,ime.bottom));return insets;});else root.setFitsSystemWindows(true);
        WebSettings settingsWeb=web.getSettings();settingsWeb.setJavaScriptEnabled(true);settingsWeb.setDomStorageEnabled(true);settingsWeb.setGeolocationEnabled(true);
        settingsWeb.setAllowFileAccess(false);settingsWeb.setAllowContentAccess(true);settingsWeb.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settingsWeb.setSupportMultipleWindows(true);settingsWeb.setJavaScriptCanOpenWindowsAutomatically(false);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web,false);
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG);
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest r){
                if(!r.isForMainFrame())return false;
                if(trusted(r.getUrl()))return false;
                if(r.hasGesture())openExternal(r.getUrl());return true;
            }
            @Override public void onReceivedSslError(WebView view,SslErrorHandler handler,SslError error){handler.cancel();status.setText("No se pudo verificar la conexión segura.");}
            @Override public void onReceivedError(WebView view,WebResourceRequest r,WebResourceError error){if(r.isForMainFrame())status.setText("No se pudo abrir la página. Ajustes → Recargar. El GPS activo sigue en Android.");}
            @Override public void onPageFinished(WebView view,String url){if(trusted(Uri.parse(url)))sendSync();}
        });
        web.setWebChromeClient(new WebChromeClient(){
            @Override public boolean onShowFileChooser(WebView view,ValueCallback<Uri[]> callback,FileChooserParams params){
                if(!trustedCurrent()){callback.onReceiveValue(null);return true;}
                if(fileCallback!=null)fileCallback.onReceiveValue(null);fileCallback=callback;
                try{
                    if(params.isCaptureEnabled()){
                        File dir=new File(getCacheDir(),"camera");if(!dir.exists()&&!dir.mkdirs())throw new IOException();
                        File[] oldPhotos=dir.listFiles();if(oldPhotos!=null)for(File f:oldPhotos)if(f.lastModified()<System.currentTimeMillis()-86400000L)f.delete();
                        photoFile=File.createTempFile("evidencia-",".jpg",dir);photo=FileProvider.getUriForFile(MainActivity.this,getPackageName()+".files",photoFile);
                        Intent camera=new Intent(MediaStore.ACTION_IMAGE_CAPTURE).putExtra(MediaStore.EXTRA_OUTPUT,photo).addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        camera.setClipData(ClipData.newRawUri("foto",photo));startActivityForResult(camera,CAMERA);
                    }else startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE),FILE);
                }catch(Exception e){fileCallback.onReceiveValue(null);fileCallback=null;toast("No se pudo abrir la cámara o el selector.");}
                return true;
            }
            @Override public void onGeolocationPermissionsShowPrompt(String origin,GeolocationPermissions.Callback callback){callback.invoke(origin,trustedCurrent()&&finePermission(),false);}
            @Override public void onPermissionRequest(PermissionRequest request){request.deny();}
            @Override public boolean onCreateWindow(WebView view,boolean dialog,boolean gesture,Message result){
                if(!gesture)return false;
                WebView popup=new WebView(MainActivity.this);popup.setWebViewClient(new WebViewClient(){@Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){openExternal(r.getUrl());v.destroy();return true;}});
                ((WebView.WebViewTransport)result.obj).setWebView(popup);result.sendToTarget();return true;
            }
        });
        if(!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)||!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)){
            new AlertDialog.Builder(this).setTitle("Actualizar Android System WebView").setMessage("Actualizá Android System WebView o Chrome desde Play Store para usar la integración GPS segura.").setPositiveButton("Cerrar",(d,w)->finish()).setCancelable(false).show();return;
        }
        WebViewCompat.addWebMessageListener(web,"DiaVerdeNative",Collections.singleton("https://crrmartinmc-gif.github.io"),(view,message,sourceOrigin,isMainFrame,reply)->{
            if(!isMainFrame||!trustedCurrent())return;
            try{JSONObject request=new JSONObject(message.getData());handle(request.optString("action"),request.optJSONObject("data"));}catch(Exception e){toast("Mensaje de la app inválido.");}
        });
        try(InputStream in=getAssets().open("native-bridge.js")){
            String script=new String(readAll(in),StandardCharsets.UTF_8);
            WebViewCompat.addDocumentStartJavaScript(web,script,Collections.singleton("https://crrmartinmc-gif.github.io"));
        }catch(IOException e){throw new IllegalStateException(e);}
        UploadWorker.ensurePeriodic(this);UploadWorker.schedule(this);
        if(saved==null||web.restoreState(saved)==null)web.loadUrl(BuildConfig.WEB_URL);
    }
    private static byte[] readAll(InputStream in)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return out.toByteArray();}
    private int dp(int value){return (int)(getResources().getDisplayMetrics().density*value);}
    private boolean trusted(Uri u){return u!=null&&"https".equals(u.getScheme())&&"crrmartinmc-gif.github.io".equals(u.getHost())&&(u.getPort()==-1||u.getPort()==443)&&("/Dia-Verde".equals(u.getPath())||u.getPath()!=null&&u.getPath().startsWith("/Dia-Verde/"));}
    private boolean trustedCurrent(){return web!=null&&web.getUrl()!=null&&trusted(Uri.parse(web.getUrl()));}
    private void openExternal(Uri uri){if(!"https".equals(uri.getScheme())&&!"http".equals(uri.getScheme()))return;try{startActivity(new Intent(Intent.ACTION_VIEW,uri));}catch(Exception e){toast("No hay una aplicación para abrir ese enlace.");}}
    private void toast(String text){Toast.makeText(this,text,Toast.LENGTH_LONG).show();}
    private void handle(String action,JSONObject data)throws Exception{
        if(data==null)data=new JSONObject();
        switch(action){
            case "start":if(validating&&startingId.equals(data.optString("idRecorrido")))break;pendingStart=data;startGeneration++;status.setText("Comprobando sesión y permisos. Esperá la notificación GPS.");ensurePermissions();break;
            case "stop":updateScreen(false);startGeneration++;pendingStart=null;startService(new Intent(this,TrackingService.class).setAction(TrackingService.STOP));break;
            case "state":owner=data.optString("usuario");sendState();break;
            case "capture":captureId=data.optString("id");ensurePermissions();break;
        }
    }
    private boolean finePermission(){return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED;}
    private boolean notifications(){return Build.VERSION.SDK_INT<33||checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED;}
    private void ensurePermissions(){
        if(!resumed||permissionDialog)return;
        if(!finePermission()||(pendingStart!=null&&!notifications())){
            java.util.ArrayList<String> p=new java.util.ArrayList<>();p.add(Manifest.permission.ACCESS_FINE_LOCATION);p.add(Manifest.permission.ACCESS_COARSE_LOCATION);
            if(pendingStart!=null&&Build.VERSION.SDK_INT>=33)p.add(Manifest.permission.POST_NOTIFICATIONS);
            permissionDialog=true;requestPermissions(p.toArray(new String[0]),PERMISSIONS);return;
        }
        if(pendingStart!=null)prepareStart();if(captureId!=null)capture();
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){
        super.onRequestPermissionsResult(request,permissions,results);if(request!=PERMISSIONS)return;permissionDialog=false;
        if(!finePermission()||(pendingStart!=null&&!notifications())){
            pendingStart=null;reject("Permití ubicación precisa y notificaciones en Ajustes para iniciar el recorrido.");finishCapture(null);return;
        }ensurePermissions();
    }
    private void prepareStart(){
        if(validating||pendingStart==null)return;
        JSONObject request=pendingStart;pendingStart=null;validating=true;startingId=request.optString("idRecorrido");final int generation=startGeneration;
        io.execute(()->{
            try{
                if(request.optInt("contractVersion")!=1)throw new IllegalStateException("Recargá la interfaz de Día Verde para habilitar la integración GPS nativa.");
                if(!BuildConfig.BACKEND_URL.equals(request.optString("backend")))throw new IllegalStateException("La URL del backend cambió. Actualizá la configuración de la APK.");
                JSONObject user=new BackendClient().validateSession(request.optString("token"));
                if(!user.optString("usuario").equalsIgnoreCase(request.optString("usuario")))throw new IllegalStateException("La sesión no coincide con el usuario del recorrido.");
                RouteInfo desired=new RouteInfo(request);desired.user=user.getString("usuario");desired.name=user.optString("nombre",desired.user);
                if(!desired.valid())throw new IllegalStateException("Elegí CGM y zona antes de iniciar el recorrido.");
                RouteInfo active=RouteStore.get(this).active();
                if(active!=null){if(!active.user.equalsIgnoreCase(desired.user))throw new IllegalStateException("Finalizá el recorrido anterior desde la notificación.");desired=active;}
                RouteInfo route=desired;
                main.post(()->{
                    validating=false;
                    if(destroyed)return;
                    if(generation!=startGeneration){ensurePermissions();return;}
                    if(!resumed){reject("Volvé a Día Verde para confirmar el inicio del GPS.");return;}
                    if(!finePermission()||!notifications()){reject("Revisá los permisos de ubicación precisa y notificaciones.");return;}
                    owner=route.user;
                    try{startForegroundService(new Intent(this,TrackingService.class).setAction(TrackingService.START).putExtra("route",route.json().toString()));}
                    catch(Exception e){reject("No se pudo iniciar el servicio GPS. Abrí los ajustes de la aplicación.");}
                });
            }catch(Exception e){main.post(()->{validating=false;if(destroyed)return;if(generation!=startGeneration){ensurePermissions();return;}reject(e.getMessage()==null?"No se pudo comprobar la sesión.":e.getMessage());});}
        });
    }
    private void reject(String text){updateScreen(false);status.setText(text);try{send("start_rejected",new JSONObject().put("message",text));}catch(Exception ignored){}toast(text);}
    private void send(String kind,JSONObject data){if(destroyed||!trustedCurrent())return;web.evaluateJavascript("window.AndroidGPS&&window.AndroidGPS._receive("+JSONObject.quote(kind)+","+data+");",null);}
    private void updateScreen(boolean active){
        if(resumed&&active)getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
    private void sendSync(){
        main.removeCallbacks(syncTask);main.postDelayed(syncTask,150);
    }
    private void readSync(){
        if(destroyed||io.isShutdown())return;
        io.execute(()->{try{RouteInfo active=RouteStore.get(this).active();boolean keepAwake=TrackingService.running&&active!=null;JSONObject s=new JSONObject().put("running",TrackingService.running).put("pending",RouteStore.get(this).pendingCount())
                .put("lastConfirmed",getSharedPreferences("native",MODE_PRIVATE).getLong("lastConfirmed",0));main.post(()->{updateScreen(keepAwake&&TrackingService.running);String current=s.toString();if(!current.equals(lastSync)){lastSync=current;status.setText(TrackingService.running?"GPS activo":"Día Verde");send("sync",s);}});}catch(Exception ignored){}});
    }
    private void sendState(){
        if(destroyed||io.isShutdown())return;
        final String expectedOwner=owner;
        io.execute(()->{
            try{
                RouteInfo r=expectedOwner.isEmpty()?null:RouteStore.get(this).latest(expectedOwner);
                JSONArray history=r==null?new JSONArray():RouteStore.get(this).history(r.id);
                JSONObject metadata=new JSONObject().put("route",r==null?JSONObject.NULL:r.json()).put("running",TrackingService.running)
                        .put("pending",RouteStore.get(this).pendingCount()).put("lastConfirmed",getSharedPreferences("native",MODE_PRIVATE).getLong("lastConfirmed",0));
                main.post(()->{
                    if(!owner.equalsIgnoreCase(expectedOwner))return;
                    send("history_start",metadata);
                    for(int i=0;i<history.length();i+=200){JSONArray batch=new JSONArray();for(int j=i;j<Math.min(i+200,history.length());j++)batch.put(history.opt(j));try{send("history_chunk",new JSONObject().put("points",batch));}catch(Exception ignored){}}
                    send("history_end",new JSONObject());sendSync();
                });
            }catch(Exception e){main.post(()->status.setText("No se pudo leer el recorrido guardado. La copia local se conserva."));}
        });
    }
    @SuppressLint("MissingPermission") private void capture(){
        if(captureId==null||!finePermission())return;
        LocationManager location=(LocationManager)getSystemService(LOCATION_SERVICE);
        cancelCaptureListener();
        for(String provider:location.getProviders(true)){
            Location cached=location.getLastKnownLocation(provider);
            if(validCapture(cached)&&System.currentTimeMillis()-cached.getTime()<5000){finishCapture(cached);return;}
        }
        final String expectedCapture=captureId;
        captureListener=new LocationListener(){@Override public void onLocationChanged(Location l){if(expectedCapture.equals(captureId)&&validCapture(l))finishCapture(l);}@Override public void onProviderDisabled(String p){}@Override public void onProviderEnabled(String p){}@Override public void onStatusChanged(String p,int s,Bundle b){}};
        try{location.requestSingleUpdate(LocationManager.GPS_PROVIDER,captureListener,Looper.getMainLooper());captureTimeout=()->{if(expectedCapture.equals(captureId))finishCapture(null);};main.postDelayed(captureTimeout,8000);}catch(Exception e){finishCapture(null);}
    }
    private boolean validCapture(Location l){return l!=null&&l.hasAccuracy()&&l.getAccuracy()<=50&&System.currentTimeMillis()-l.getTime()<30000;}
    private void cancelCaptureListener(){if(captureTimeout!=null){main.removeCallbacks(captureTimeout);captureTimeout=null;}if(captureListener!=null){((LocationManager)getSystemService(LOCATION_SERVICE)).removeUpdates(captureListener);captureListener=null;}}
    private void finishCapture(Location l){
        if(captureId==null)return;String id=captureId;captureId=null;cancelCaptureListener();
        try{JSONObject data=new JSONObject().put("id",id);if(l!=null)data.put("coords",new JSONObject().put("latitude",l.getLatitude()).put("longitude",l.getLongitude()).put("accuracy",l.getAccuracy()));send("capture",data);}catch(Exception ignored){}
    }
    private void showSettings(){new AlertDialog.Builder(this).setTitle("Día Verde · Ajustes").setItems(new String[]{"Permisos de ubicación y notificaciones","Batería: permitir actividad sin restricciones","Recargar interfaz"},(d,index)->{
        try{if(index==0)startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())));
        else if(index==1){PowerManager power=(PowerManager)getSystemService(POWER_SERVICE);if(power.isIgnoringBatteryOptimizations(getPackageName()))toast("Día Verde ya tiene permitida la actividad sin optimización de batería.");else startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,Uri.parse("package:"+getPackageName())));}
        else web.reload();}catch(Exception e){toast("Abrí los ajustes de batería de Android y elegí Día Verde → Sin restricciones.");}
    }).setNegativeButton("Cerrar",null).show();}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if((request==CAMERA||request==FILE)&&fileCallback!=null){Uri uri=request==CAMERA?photo:data==null?null:data.getData();boolean valid=result==RESULT_OK&&uri!=null&&(request!=CAMERA||photoFile!=null&&photoFile.length()>0);fileCallback.onReceiveValue(valid?new Uri[]{uri}:null);fileCallback=null;}}
    @Override protected void onStart(){super.onStart();IntentFilter filter=new IntentFilter(TrackingService.EVENT);androidx.core.content.ContextCompat.registerReceiver(this,receiver,filter,getPackageName()+".INTERNAL_EVENTS",null,androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED);receiverRegistered=true;}
    @Override protected void onResume(){super.onResume();resumed=true;if(web!=null)web.onResume();ensurePermissions();sendState();}
    @Override protected void onPause(){resumed=false;updateScreen(false);super.onPause();if(web!=null)web.onPause();}
    @Override protected void onStop(){if(receiverRegistered){unregisterReceiver(receiver);receiverRegistered=false;}super.onStop();}
    @Override protected void onSaveInstanceState(Bundle out){if(web!=null)web.saveState(out);super.onSaveInstanceState(out);}
    @Override public void onBackPressed(){if(web!=null&&web.canGoBack())web.goBack();else super.onBackPressed();}
    @Override protected void onDestroy(){finishCapture(null);if(fileCallback!=null)fileCallback.onReceiveValue(null);destroyed=true;if(web!=null)web.destroy();io.shutdown();super.onDestroy();}
}
