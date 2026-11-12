package com.ambientelomas.diaverde;
import org.json.*;
import org.junit.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

public class BackendClientTest {
    @Test public void requiresEveryExactIdBeforeRemovingPendingPoints()throws Exception{
        List<JSONObject> points=Arrays.asList(new JSONObject().put("idPunto","a"),new JSONObject().put("idPunto","b"));
        JSONObject response=new JSONObject().put("ok",true).put("accion","registrarGPSLote").put("recibidos",2).put("idsConfirmados",new JSONArray().put("a").put("b"));
        assertTrue(BackendClient.confirmed(response,points));
        response.put("idsConfirmados",new JSONArray().put("a"));assertFalse(BackendClient.confirmed(response,points));
        response.put("idsConfirmados",new JSONArray().put("a").put("b")).put("ok",false);assertFalse(BackendClient.confirmed(response,points));
        response.put("ok",true).put("recibidos",1);assertFalse(BackendClient.confirmed(response,points));
    }
}
