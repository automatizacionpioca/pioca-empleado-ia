package ar.com.pioca.respuestas;

import android.content.*;
import org.json.*;
import java.util.*;

/** Datos de la versión 2 se conservan en las mismas preferencias. */
final class Memoria {
    static SharedPreferences datos(Context c){return c.getSharedPreferences("archivo",0);}
    static JSONObject objeto(String... pares) {
        JSONObject j=new JSONObject();
        try{for(int i=0;i<pares.length;i+=2)j.put(pares[i],pares[i+1]);}
        catch(JSONException e){throw new IllegalArgumentException(e);}
        return j;
    }
    static void entrada(Context c,Bandeja.Ficha f,String texto,long t) {
        if(texto==null||texto.trim().isEmpty())return;
        String k="e3."+Panel.hash(f.hilo+"\n"+t+"\n"+texto);
        if(datos(c).contains(k))return;
        JSONObject j=objeto("hilo",f.hilo,"nombre",f.nombre,"mensaje",texto,"hora",Long.toString(t),"tipo","recibido");
        datos(c).edit().putString(k,j.toString()).apply();
        SharedPreferences arch=c.getSharedPreferences("archivados_v3",0);
        long at=arch.getLong(f.hilo,0);
        if(at>0 && t>at)arch.edit().remove(f.hilo).apply();
    }
    static String aprobada(Context c,String hilo,String nombre,String consulta,String primerBorrador,
                         String propuesta,String respuesta,String proceso,String indicacion) {
        String k="a."+UUID.randomUUID();
        JSONObject evento=objeto("hilo",hilo,"nombre",nombre,"mensaje",respuesta,"consulta",consulta,
            "hora",Long.toString(System.currentTimeMillis()),"tipo","aprobado",
            "propuesta",propuesta==null?"":propuesta,"borrador_original",primerBorrador==null?"":primerBorrador,
            "proceso",proceso,"indicacion",indicacion==null?"":indicacion);
        datos(c).edit().putString(k,evento.toString()).apply();
        return k;
    }
    static void procesoDeRespuesta(Context c,String clave,String categoria) {
        if(clave==null)return;
        String raw=datos(c).getString(clave,null);
        if(raw==null)return;
        try{JSONObject evento=new JSONObject(raw);evento.put("proceso",categoria);
            datos(c).edit().putString(clave,evento.toString()).apply();}
        catch(JSONException ignored){}
    }
    static void archivar(Context c,String hilo){c.getSharedPreferences("archivados_v3",0).edit().putLong(hilo,System.currentTimeMillis()).apply();}
    static void reabrir(Context c,String hilo){c.getSharedPreferences("archivados_v3",0).edit().remove(hilo).apply();}
    static boolean archivado(Context c,String hilo){return c.getSharedPreferences("archivados_v3",0).contains(hilo);}
    static List<JSONObject> eventos(Context c,String hilo){
        List<JSONObject> lista=new ArrayList<>();
        for(Object v:datos(c).getAll().values())if(v instanceof String)try{
            JSONObject j=new JSONObject((String)v);
            if(hilo==null||hilo.equals(j.optString("hilo")))lista.add(j);
        }catch(JSONException ignored){}
        lista.sort(Comparator.comparingLong(j->j.optLong("hora")));
        return lista;
    }
    static class Hilo {
        String id,nombre="Cliente",ultimo="";
        long recibido=0,respondido=0;int recibidos=0;
        Hilo(String id){this.id=id;}
    }
    static List<Hilo> hilos(Context c,boolean archivados){
        Map<String,Hilo> mapa=new LinkedHashMap<>();
        for(JSONObject e:eventos(c,null)){
            String id=e.optString("hilo");if(id.isEmpty())continue;
            Hilo h=mapa.get(id);if(h==null){h=new Hilo(id);mapa.put(id,h);}
            String nombre=e.optString("nombre");if(!nombre.isEmpty())h.nombre=nombre;
            long hora=e.optLong("hora");
            if("recibido".equals(e.optString("tipo"))){
                if(hora>=h.recibido)h.ultimo=e.optString("mensaje");
                h.recibido=Math.max(h.recibido,hora);h.recibidos++;
            }else if("aprobado".equals(e.optString("tipo")))h.respondido=Math.max(h.respondido,hora);
        }
        List<Hilo> lista=new ArrayList<>();
        for(Hilo h:mapa.values()){
            boolean esArchivo=archivado(c,h.id);
            boolean espera=h.recibido>h.respondido;
            if(archivados?esArchivo:espera&&!esArchivo)lista.add(h);
        }
        lista.sort((a,b)->Long.compare(Math.max(b.recibido,b.respondido),Math.max(a.recibido,a.respondido)));
        return lista;
    }
    static JSONArray recientes(Context c,String hilo){
        List<JSONObject> eventos=eventos(c,hilo);JSONArray a=new JSONArray();
        for(int i=Math.max(0,eventos.size()-24);i<eventos.size();i++){
            JSONObject e=eventos.get(i);
            JSONObject turno=objeto("turno",e.optString("tipo"),"texto",e.optString("mensaje"));
            if(e.has("propuesta")&&!e.optString("propuesta").equals(e.optString("mensaje")))try{
                turno.put("propuesta_anterior_corregida",e.optString("propuesta"));
            }catch(JSONException ignored){}
            a.put(turno);
        }
        return a;
    }
    static final String[] grupos={"procesos","archivo","borradores","categorias_v3","archivados_v3"};
    static String respaldo(Context c)throws JSONException {
        JSONObject doc=new JSONObject().put("formato","piOca-respuestas-3");
        for(String g:grupos)doc.put(g,new JSONObject(c.getSharedPreferences(g,0).getAll()));
        return doc.toString();
    }
    static void restaurar(Context c,String json)throws JSONException {
        JSONObject doc=new JSONObject(json);
        String formato=doc.optString("formato");
        if(!formato.equals("piOca-respuestas-2")&&!formato.equals("piOca-respuestas-3"))throw new JSONException("Formato incompatible");
        for(String g:grupos){
            JSONObject obj=doc.optJSONObject(g);if(obj==null)continue;
            for(Iterator<String> it=obj.keys();it.hasNext();){String k=it.next();Object v=obj.get(k);
                if(g.equals("archivados_v3")&&v instanceof Number)continue;
                if(!(v instanceof String))throw new JSONException("Dato inválido");
            }
        }
        for(String g:grupos){
            JSONObject obj=doc.optJSONObject(g);if(obj==null)continue;
            SharedPreferences.Editor ed=c.getSharedPreferences(g,0).edit();
            for(Iterator<String> it=obj.keys();it.hasNext();){String k=it.next();Object v=obj.get(k);
                if(g.equals("archivados_v3")&&v instanceof Number)ed.putLong(k,((Number)v).longValue());
                else ed.putString(k,(String)v);
            }ed.apply();
        }
    }
}
