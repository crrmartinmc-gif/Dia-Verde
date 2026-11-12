package com.ambientelomas.diaverde;

import android.content.Context;
import android.content.Intent;
import org.json.JSONObject;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

public final class QueueUploader {
    private static final ReentrantLock LOCK=new ReentrantLock();
    public static boolean drain(Context context){
        if(!LOCK.tryLock())return false;
        try{
            RouteStore store=RouteStore.get(context);BackendClient client=new BackendClient();
            for(int i=0;i<20;i++){
                List<JSONObject> batch=store.pending(5);if(batch.isEmpty())return true;
                client.send(batch);store.confirm(batch);
                context.getSharedPreferences("native",Context.MODE_PRIVATE).edit().putLong("lastConfirmed",System.currentTimeMillis()).apply();
                context.sendBroadcast(new Intent(TrackingService.EVENT).setPackage(context.getPackageName()).putExtra("kind","sync"),context.getPackageName()+".INTERNAL_EVENTS");
            }
            return store.pendingCount()==0;
        }catch(Exception e){return false;}finally{LOCK.unlock();}
    }
}
