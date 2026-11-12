package com.ambientelomas.diaverde;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class BackendClient {
    /** TLS y verificación de hostname estándar; redirecciones solo de Apps Script. */
    private JSONObject execute(String address,String method,String body)throws Exception{
        URL url=new URL(address);
        for(int redirect=0;redirect<6;redirect++){
            if(!"https".equals(url.getProtocol())||!("script.google.com".equals(url.getHost())||"script.googleusercontent.com".equals(url.getHost())))throw new IllegalStateException("Destino del backend no permitido.");
            HttpURLConnection c=(HttpURLConnection)url.openConnection();
            c.setInstanceFollowRedirects(false);c.setConnectTimeout(15000);c.setReadTimeout(45000);c.setRequestMethod(method);c.setUseCaches(false);
            try{
                if(body!=null){c.setDoOutput(true);c.setRequestProperty("Content-Type","text/plain;charset=utf-8");try(java.io.OutputStream out=c.getOutputStream()){out.write(body.getBytes(StandardCharsets.UTF_8));}}
                int status=c.getResponseCode();
                if(status>=300&&status<400){String next=c.getHeaderField("Location");if(next==null)throw new IllegalStateException("Redirección inválida.");url=new URL(url,next);if(status==301||status==302||status==303){method="GET";body=null;}continue;}
                if(status!=200)throw new IllegalStateException("Backend HTTP "+status);
                try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                    byte[] buf=new byte[8192];int n;while((n=in.read(buf))!=-1){if(out.size()+n>2*1024*1024)throw new IllegalStateException("Respuesta demasiado grande.");out.write(buf,0,n);}
                    return new JSONObject(out.toString("UTF-8"));
                }
            }finally{c.disconnect();}
        }throw new IllegalStateException("Demasiadas redirecciones.");
    }
    public JSONObject validateSession(String token)throws Exception{
        JSONObject body=new JSONObject().put("accion","diaVerdeSesion").put("token",token);
        JSONObject r=execute(BuildConfig.BACKEND_URL,"POST",body.toString());
        if(!r.optBoolean("ok")||r.optInt("authVersion")!=2||r.optJSONObject("usuario")==null)throw new IllegalStateException("Ingresá nuevamente a Día Verde para iniciar el GPS.");
        return r.getJSONObject("usuario");
    }
    public void send(List<JSONObject> points)throws Exception{
        JSONArray data=new JSONArray();for(JSONObject p:points)data.put(p);
        String url=BuildConfig.BACKEND_URL+"?accion=registrarGPSLote&lote="+URLEncoder.encode(data.toString(),"UTF-8")+"&_ts="+System.currentTimeMillis();
        if(url.length()>14000)throw new IllegalStateException("El lote supera el tamaño admitido.");
        JSONObject r=execute(url,"GET",null);
        if(!confirmed(r,points))throw new IllegalStateException("El servidor no confirmó todos los puntos. Se conservan para reintento.");
    }
    static boolean confirmed(JSONObject response,List<JSONObject> points){
        if(!response.optBoolean("ok")||!"registrarGPSLote".equals(response.optString("accion"))||response.optInt("recibidos",-1)!=points.size())return false;
        JSONArray ids=response.optJSONArray("idsConfirmados");if(ids==null)return false;
        Set<String> confirmed=new HashSet<>();for(int i=0;i<ids.length();i++)confirmed.add(ids.optString(i));
        for(JSONObject p:points)if(!confirmed.contains(p.optString("idPunto")))return false;
        return true;
    }
}
