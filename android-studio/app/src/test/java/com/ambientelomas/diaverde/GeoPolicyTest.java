package com.ambientelomas.diaverde;
import org.junit.Test;
import static org.junit.Assert.*;

public class GeoPolicyTest {
    private final long now=1800000000000L;
    @Test public void rejectsStaleInaccurateInvalidAndOutOfOrder(){
        assertNull(GeoPolicy.accept(new GeoPolicy.Fix(0,0,5d,null,now),null,now));
        assertNull(GeoPolicy.accept(new GeoPolicy.Fix(-34,-58,90d,null,now),null,now));
        assertNull(GeoPolicy.accept(new GeoPolicy.Fix(-34,-58,5d,null,now-61000),null,now));
        GeoPolicy.Fix previous=new GeoPolicy.Fix(-34,-58,5d,null,now);
        assertNull(GeoPolicy.accept(new GeoPolicy.Fix(-34.1,-58,5d,null,now-1),previous,now));
    }
    @Test public void acceptsMovementAndUsesIdenticalStationaryCoordinate(){
        GeoPolicy.Fix previous=new GeoPolicy.Fix(-34.745,-58.39,5d,null,now-20000);
        GeoPolicy.Fix result=GeoPolicy.accept(new GeoPolicy.Fix(-34.745001,-58.390001,8d,null,now),previous,now);
        assertNotNull(result);assertEquals(previous.lat,result.lat,0);assertEquals(previous.lng,result.lng,0);assertEquals(now,result.time);
        GeoPolicy.Fix moving=GeoPolicy.accept(new GeoPolicy.Fix(-34.7451,-58.3901,8d,45d,now),previous,now);
        assertNotNull(moving);assertEquals(-34.7451,moving.lat,0);
    }
    @Test public void rejectsImpossibleJumpAndEarlyHeartbeat(){
        GeoPolicy.Fix previous=new GeoPolicy.Fix(-34,-58,5d,null,now-1000);
        assertNull(GeoPolicy.accept(new GeoPolicy.Fix(-35,-58,5d,null,now),previous,now));
        assertNull(GeoPolicy.accept(new GeoPolicy.Fix(-34.000001,-58,5d,null,now),previous,now));
    }
}
