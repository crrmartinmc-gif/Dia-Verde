package com.ambientelomas.diaverde;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import org.json.JSONArray;
import org.json.JSONObject;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** SQLite es la fuente persistente; ni WebView ni localStorage sostienen el servicio. */
public final class RouteStore extends SQLiteOpenHelper {
    private static volatile RouteStore instance;
    public static RouteStore get(Context context){
        if(instance==null)synchronized(RouteStore.class){if(instance==null)instance=new RouteStore(context.getApplicationContext());}
        return instance;
    }
    private RouteStore(Context c){super(c,"diaverde-native.db",null,1);setWriteAheadLoggingEnabled(true);}
    @Override public void onCreate(SQLiteDatabase db){
        db.execSQL("CREATE TABLE routes(id TEXT PRIMARY KEY,owner TEXT NOT NULL,started INTEGER NOT NULL,active INTEGER NOT NULL,data TEXT NOT NULL)");
        db.execSQL("CREATE TABLE points(id TEXT PRIMARY KEY,route_id TEXT NOT NULL,captured INTEGER NOT NULL,payload TEXT NOT NULL,sent INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE INDEX pending_points ON points(sent,captured)");
        db.execSQL("CREATE INDEX route_points ON points(route_id,captured)");
    }
    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion){throw new IllegalStateException("Se requiere una migración explícita de los recorridos.");}
    private static JSONObject parse(String s){try{return new JSONObject(s);}catch(Exception e){throw new IllegalStateException("Registro local inválido",e);}}
    public RouteInfo active(){return route("active=1",null);}
    public RouteInfo latest(String owner){return route("lower(owner)=lower(?)",new String[]{owner});}
    private RouteInfo route(String where,String[] args){
        try(Cursor c=getReadableDatabase().query("routes",new String[]{"data"},where,args,null,null,"started DESC","1")){
            return c.moveToFirst()?new RouteInfo(parse(c.getString(0))):null;
        }
    }
    private void saveRoute(SQLiteDatabase db,RouteInfo route){
        ContentValues v=new ContentValues();v.put("id",route.id);v.put("owner",route.user);v.put("started",route.started);v.put("active",route.active?1:0);v.put("data",route.json().toString());
        db.insertWithOnConflict("routes",null,v,SQLiteDatabase.CONFLICT_REPLACE);
    }
    public void begin(RouteInfo route){
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try{RouteInfo existing=active();if(existing!=null&&!existing.id.equals(route.id))throw new IllegalStateException("Ya hay un recorrido activo.");saveRoute(db,route);db.setTransactionSuccessful();}
        finally{db.endTransaction();}
    }
    public JSONObject last(String routeId){
        try(Cursor c=getReadableDatabase().query("points",new String[]{"payload"},"route_id=?",new String[]{routeId},null,null,"captured DESC","1")){
            return c.moveToFirst()?parse(c.getString(0)):null;
        }
    }
    public static GeoPolicy.Fix fix(JSONObject p){
        if(p==null)return null;
        return new GeoPolicy.Fix(p.optDouble("latitud"),p.optDouble("longitud"),p.isNull("precision")?null:p.optDouble("precision"),p.isNull("rumbo")?null:p.optDouble("rumbo"),Instant.parse(p.optString("timestamp")).toEpochMilli());
    }
    public JSONObject insert(RouteInfo route,GeoPolicy.Fix fix,boolean active,String provider){
        try{
            JSONObject p=new JSONObject().put("idPunto",UUID.randomUUID().toString()).put("idRecorrido",route.id)
                    .put("usuario",route.user).put("nombre",route.name).put("cgm",route.cgm).put("zona",route.zone)
                    .put("latitud",fix.lat).put("longitud",fix.lng).put("precision",fix.accuracy==null?JSONObject.NULL:fix.accuracy)
                    .put("rumbo",fix.bearing==null?JSONObject.NULL:fix.bearing).put("timestamp",Instant.ofEpochMilli(fix.time).toString())
                    .put("activo",active?1:0).put("fuente","ANDROID_"+provider);
            ContentValues v=new ContentValues();v.put("id",p.getString("idPunto"));v.put("route_id",route.id);v.put("captured",fix.time);v.put("payload",p.toString());v.put("sent",0);
            getWritableDatabase().insertOrThrow("points",null,v);return p;
        }catch(Exception e){throw new IllegalStateException("No se pudo guardar el punto GPS",e);}
    }
    public void finish(){
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try{
            RouteInfo r=active();if(r!=null){GeoPolicy.Fix last=fix(last(r.id));
                if(last!=null)insert(r,new GeoPolicy.Fix(last.lat,last.lng,last.accuracy,last.bearing,Math.max(System.currentTimeMillis(),last.time+1)),false,"FIN");
                r.active=false;r.ended=System.currentTimeMillis();saveRoute(db,r);
            }
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    public List<JSONObject> pending(int limit){
        List<JSONObject> list=new ArrayList<>();
        try(Cursor c=getReadableDatabase().query("points",new String[]{"payload"},"sent=0",null,null,null,"captured ASC",String.valueOf(limit))){while(c.moveToNext())list.add(parse(c.getString(0)));}
        return list;
    }
    public int pendingCount(){try(Cursor c=getReadableDatabase().rawQuery("SELECT count(*) FROM points WHERE sent=0",null)){c.moveToFirst();return c.getInt(0);}}
    public void confirm(List<JSONObject> points){
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try{ContentValues v=new ContentValues();v.put("sent",1);for(JSONObject p:points)db.update("points",v,"id=?",new String[]{p.optString("idPunto")});db.setTransactionSuccessful();}
        finally{db.endTransaction();}
    }
    public JSONArray history(String routeId){
        JSONArray out=new JSONArray();
        try(Cursor c=getReadableDatabase().query("points",new String[]{"payload"},"route_id=?",new String[]{routeId},null,null,"captured ASC")){
            while(c.moveToNext()){JSONObject p=parse(c.getString(0));try{out.put(new JSONObject().put("lat",p.getDouble("latitud")).put("lng",p.getDouble("longitud"))
                    .put("hora",p.getString("timestamp")).put("precision",p.opt("precision")).put("heading",p.opt("rumbo")).put("idPunto",p.getString("idPunto")));}catch(Exception e){throw new IllegalStateException(e);}}
        }return out;
    }
}
