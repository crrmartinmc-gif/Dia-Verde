package com.ambientelomas.diaverde;

import org.json.JSONObject;

public final class RouteInfo {
    public String id,user,name,cgm,zone;
    public long started,ended;
    public boolean active;
    public RouteInfo(JSONObject j) {
        id=j.optString("idRecorrido");user=j.optString("usuario");name=j.optString("nombre",user);
        cgm=j.optString("cgm");zone=j.optString("zona");started=j.optLong("inicio",System.currentTimeMillis());
        ended=j.optLong("fin",0);active=j.optBoolean("activo",true);
    }
    public JSONObject json() {
        try{return new JSONObject().put("idRecorrido",id).put("usuario",user).put("nombre",name).put("cgm",cgm)
                .put("zona",zone).put("inicio",started).put("fin",ended).put("activo",active);}
        catch(Exception e){throw new IllegalStateException(e);}
    }
    public boolean valid(){return !id.isEmpty()&&!user.isEmpty()&&!cgm.isEmpty()&&!zone.isEmpty()&&id.length()<=150&&user.length()<=150;}
}
