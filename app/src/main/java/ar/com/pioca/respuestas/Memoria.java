package ar.com.pioca.respuestas;

import android.content.*;
import org.json.*;
import java.util.*;

final class Memoria {
    static SharedPreferences datos(Context c) { return c.getSharedPreferences("archivo",0); }
    static JSONObject objeto(String... pares) {
        JSONObject j=new JSONObject();
        try { for(int i=0;i<pares.length;i+=2) j.put(pares[i],pares[i+1]); }
        catch(JSONException e){ throw new IllegalArgumentException(e); }
        return j;
    }
    static void entrada(Context c,Bandeja.Ficha f,long t){
        String k="e."+Panel.hash(f.id+"\n"+f.texto);
        datos(c).edit().putString(k,objeto("hilo",f.hilo,"nombre",f.nombre,"mensaje",f.texto,
            "hora",Long.toString(t),"tipo","recibido").toString()).apply();
    }
    static void aprobada(Context c,Bandeja.Ficha f,String propuesta,String fin,int proceso,String indicacion){
        JSONObject evento=objeto("hilo",f.hilo,"nombre",f.nombre,"mensaje",fin,"consulta",f.texto,
            "hora",Long.toString(System.currentTimeMillis()),"tipo","aprobado","propuesta",propuesta,
            "proceso",Panel.procesos[proceso],"indicacion",indicacion);
        datos(c).edit().putString("a."+UUID.randomUUID(),evento.toString()).apply();
    }
    static List<JSONObject> eventos(Context c,String hilo){
        List<JSONObject> lista=new ArrayList<>();
        for(Object v:datos(c).getAll().values()) try {
            JSONObject j=new JSONObject((String)v);
            if(hilo==null || hilo.equals(j.optString("hilo"))) lista.add(j);
        } catch(JSONException ignored){}
        lista.sort(Comparator.comparingLong(j->j.optLong("hora")));
        return lista;
    }
    static JSONArray recientes(Context c,String hilo){
        List<JSONObject> lista=eventos(c,hilo); JSONArray r=new JSONArray();
        for(int i=Math.max(0,lista.size()-12);i<lista.size();i++){
            JSONObject e=lista.get(i);
            JSONObject turno=objeto("turno",e.optString("tipo"),"texto",e.optString("mensaje"));
            if(e.has("propuesta") && !e.optString("propuesta").equals(e.optString("mensaje"))){
                try{turno.put("propuesta_anterior_corregida",e.optString("propuesta"));}catch(JSONException ex){throw new IllegalStateException(ex);}
            }
            r.put(turno);
        }
        return r;
    }
    static final String[] grupos={"procesos","archivo","borradores"};
    static String respaldo(Context c) throws JSONException {
        JSONObject doc=new JSONObject().put("formato","piOca-respuestas-2");
        for(String g:grupos) doc.put(g,new JSONObject(c.getSharedPreferences(g,0).getAll()));
        return doc.toString();
    }
    static void restaurar(Context c,String json) throws JSONException {
        JSONObject doc=new JSONObject(json);
        if(!"piOca-respuestas-2".equals(doc.optString("formato"))) throw new JSONException("Formato incompatible");
        for(String g:grupos){
            JSONObject obj=doc.getJSONObject(g);
            for(Iterator<String> it=obj.keys();it.hasNext();) {
                String k=it.next();
                if(!(obj.get(k) instanceof String)) throw new JSONException("Dato inválido");
                if(g.equals("archivo")){JSONObject e=new JSONObject(obj.getString(k));e.getString("hilo");e.getString("mensaje");e.getLong("hora");}
            }
        }
        for(String g:grupos){
            JSONObject obj=doc.getJSONObject(g); SharedPreferences.Editor ed=c.getSharedPreferences(g,0).edit();
            for(Iterator<String> it=obj.keys();it.hasNext();){String k=it.next();ed.putString(k,obj.getString(k));}
            ed.apply();
        }
    }
}
