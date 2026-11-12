package com.ambientelomas.diaverde;

/** La coordenada aceptada es la misma que se guarda, se sube y se muestra. */
public final class GeoPolicy {
    private GeoPolicy() {}
    public static final class Fix {
        public final double lat, lng;
        public final Double accuracy, bearing;
        public final long time;
        public Fix(double lat,double lng,Double accuracy,Double bearing,long time) {
            this.lat=lat; this.lng=lng; this.accuracy=accuracy; this.bearing=bearing; this.time=time;
        }
    }
    public static Fix accept(Fix fix,Fix previous,long now) {
        if(!Double.isFinite(fix.lat)||!Double.isFinite(fix.lng)||Math.abs(fix.lat)>90||Math.abs(fix.lng)>180||(fix.lat==0&&fix.lng==0))return null;
        if(fix.accuracy!=null&&(!Double.isFinite(fix.accuracy)||fix.accuracy<0||fix.accuracy>50))return null;
        if(fix.time<now-60000||fix.time>now+60000)return null;
        if(previous==null)return fix;
        long delta=fix.time-previous.time;
        if(delta<=0)return null;
        double distance=distance(previous.lat,previous.lng,fix.lat,fix.lng);
        if(distance<3)return delta>=15000?new Fix(previous.lat,previous.lng,fix.accuracy,fix.bearing,fix.time):null;
        return distance/(delta/1000d)>50?null:fix;
    }
    public static double distance(double lat1,double lng1,double lat2,double lng2) {
        double dLat=Math.toRadians(lat2-lat1),dLng=Math.toRadians(lng2-lng1);
        double h=Math.pow(Math.sin(dLat/2),2)+Math.cos(Math.toRadians(lat1))*Math.cos(Math.toRadians(lat2))*Math.pow(Math.sin(dLng/2),2);
        return 6371000*2*Math.asin(Math.sqrt(Math.min(1,h)));
    }
}
