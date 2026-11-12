package com.ambientelomas.diaverde;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.location.*;
import android.os.*;
import org.json.JSONObject;
import java.util.concurrent.*;

public final class TrackingService extends Service implements LocationListener {
    public static final String START="dv.START",STOP="dv.STOP",EVENT="com.ambientelomas.diaverde.EVENT";
    private static final String CHANNEL="dia-verde-recorrido";
    private static final int NOTIFICATION=21;
    public static volatile boolean running=false;
    private final ExecutorService locations=Executors.newSingleThreadExecutor();
    private final ScheduledExecutorService sender=Executors.newSingleThreadScheduledExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private LocationManager manager;private PowerManager.WakeLock wake;
    private volatile boolean accepting=false;private volatile RouteInfo route;
    private String message="Esperando ubicación precisa";private long lastAttempt=0;

    @Override public void onCreate(){
        super.onCreate();manager=(LocationManager)getSystemService(LOCATION_SERVICE);
        ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(new NotificationChannel(CHANNEL,"Recorrido Día Verde",NotificationManager.IMPORTANCE_LOW));
        wake=((PowerManager)getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,getPackageName()+":gps");wake.setReferenceCounted(false);
        UploadWorker.ensurePeriodic(this);
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent!=null&&STOP.equals(intent.getAction())){stopRoute();return START_NOT_STICKY;}
        try{
            if(running)return START_STICKY;
            RouteInfo saved=RouteStore.get(this).active();
            route=saved!=null?saved:(intent!=null&&intent.hasExtra("route")?new RouteInfo(new JSONObject(intent.getStringExtra("route"))):null);
            if(route==null||!route.valid()||checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){stopSelf();return START_NOT_STICKY;}
            if(Build.VERSION.SDK_INT>=29)startForeground(NOTIFICATION,notification(),ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);else startForeground(NOTIFICATION,notification());
            RouteStore.get(this).begin(route);running=true;accepting=true;
            requestLocations();renewWake();
            sender.scheduleWithFixedDelay(()->{if(accepting){renewWake();upload();}},0,10,TimeUnit.SECONDS);
            event("state",new JSONObject());
            return START_STICKY;
        }catch(Exception e){message="No se pudo iniciar el GPS. Abrí Día Verde y revisá los permisos.";event("error",jsonMessage(message));stopSelf();return START_NOT_STICKY;}
    }
    @SuppressLint("MissingPermission") private void requestLocations(){
        boolean found=false;
        for(String provider:new String[]{LocationManager.GPS_PROVIDER,LocationManager.NETWORK_PROVIDER}){
            if(manager.getAllProviders().contains(provider)){manager.requestLocationUpdates(provider,5000,0,this,Looper.getMainLooper());found=true;}
        }
        if(!found)throw new IllegalStateException("No hay proveedor GPS.");
    }
    private synchronized void renewWake(){if(accepting)wake.acquire(10*60*1000L);}
    @Override public void onLocationChanged(Location location){
        if(!accepting)return;
        Location copy=new Location(location);
        locations.execute(()->{
            try{
                RouteInfo r=route;if(r==null)return;
                if(copy.getElapsedRealtimeNanos()>0&&(SystemClock.elapsedRealtimeNanos()-copy.getElapsedRealtimeNanos())>60000000000L)return;
                GeoPolicy.Fix raw=new GeoPolicy.Fix(copy.getLatitude(),copy.getLongitude(),copy.hasAccuracy()?(double)copy.getAccuracy():null,copy.hasBearing()?(double)copy.getBearing():null,copy.getTime());
                GeoPolicy.Fix accepted=GeoPolicy.accept(raw,RouteStore.fix(RouteStore.get(this).last(r.id)),System.currentTimeMillis());
                if(accepted==null){if(copy.hasAccuracy()&&copy.getAccuracy()>50){message="Buscando mejor precisión GPS";refreshNotification();}return;}
                JSONObject point=RouteStore.get(this).insert(r,accepted,true,copy.getProvider()==null?"GPS":copy.getProvider().toUpperCase(java.util.Locale.ROOT));
                message="GPS activo · puntos guardados en el teléfono";event("location",point);refreshNotification();
            }catch(Exception e){message="No se pudo guardar el GPS en el teléfono. Revisá el almacenamiento.";event("error",jsonMessage(message));refreshNotification();}
        });
    }
    private void upload(){
        if(SystemClock.elapsedRealtime()-lastAttempt<9000)return;lastAttempt=SystemClock.elapsedRealtime();
        boolean ok=QueueUploader.drain(this);if(!ok)UploadWorker.schedule(this);
        event("sync",new JSONObject());refreshNotification();
    }
    private void stopRoute(){
        accepting=false;try{manager.removeUpdates(this);}catch(Exception ignored){}
        locations.execute(()->{
            try{RouteStore.get(this).finish();UploadWorker.schedule(this);event("state",new JSONObject());}
            finally{main.post(()->{stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();});}
        });
    }
    private Notification notification(){
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,TrackingService.class).setAction(STOP),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        int pending=0;try{pending=RouteStore.get(this).pendingCount();}catch(Exception ignored){}
        Notification publicView=new Notification.Builder(this,CHANNEL).setSmallIcon(R.drawable.ic_location).setContentTitle("Día Verde").setContentText("Recorrido activo").build();
        return new Notification.Builder(this,CHANNEL).setSmallIcon(R.drawable.ic_location).setColor(0xff25834f).setContentTitle("Día Verde · Recorrido activo")
                .setContentText(message+" · "+pending+" por enviar").setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
                .setVisibility(Notification.VISIBILITY_PRIVATE).setPublicVersion(publicView).addAction(new Notification.Action.Builder(null,"Finalizar recorrido",stop).build()).build();
    }
    private void refreshNotification(){if(running)main.post(()->((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(NOTIFICATION,notification()));}
    private static JSONObject jsonMessage(String text){try{return new JSONObject().put("message",text);}catch(Exception e){return new JSONObject();}}
    private void event(String kind,JSONObject data){sendBroadcast(new Intent(EVENT).setPackage(getPackageName()).putExtra("kind",kind).putExtra("data",data.toString()),getPackageName()+".INTERNAL_EVENTS");}
    @Override public void onProviderDisabled(String p){message="Activá la ubicación del teléfono";refreshNotification();}
    @Override public void onProviderEnabled(String p){message="Esperando ubicación precisa";refreshNotification();}
    @Override public void onStatusChanged(String p,int s,Bundle extras){}
    @Override public IBinder onBind(Intent intent){return null;}
    @Override public void onDestroy(){accepting=false;running=false;try{manager.removeUpdates(this);}catch(Exception ignored){}if(wake!=null&&wake.isHeld())wake.release();sender.shutdownNow();locations.shutdown();super.onDestroy();}
}
