package ar.com.pioca.respuestas;

import android.content.*;
import org.json.*;
import java.util.*;

/** V5: historiales y memoria de aprendizaje independientes, compatibles con V2/V3/V4. */
final class Memoria {
    static SharedPreferences datos(Context c){return c.getSharedPreferences("archivo",0);}
    static SharedPreferences memoriaAprendida(Context c){return c.getSharedPreferences("aprendizaje_v5",0);}
    /** El conocimiento aprobado sobrevive a la limpieza de chats archivados. */
    static JSONObject resumenAprendido(JSONObject evento){
        JSONObject ejemplo=new JSONObject();
        try{
            ejemplo.put("tipo","aprobado");
            ejemplo.put("hilo",evento.optString("hilo")); // identificador interno para deduplicar, no teléfono
            ejemplo.put("consulta",evento.optString("consulta"));
            ejemplo.put("mensaje",evento.optString("mensaje"));
            ejemplo.put("proceso",evento.optString("proceso"));
            ejemplo.put("hora",evento.optString("hora"));
            ejemplo.put("indicacion",evento.optString("indicacion"));
        }catch(JSONException ex){throw new IllegalStateException(ex);}
        return ejemplo;
    }
    /** Migra respuestas V2-V4 a un registro independiente; falla antes de permitir borrados. */
    static void asegurarAprendizajes(Context c){
        Map<String,?> existentes=memoriaAprendida(c).getAll();
        SharedPreferences.Editor edit=memoriaAprendida(c).edit();
        boolean cambios=false;
        for(Map.Entry<String,?> e:datos(c).getAll().entrySet()){
            if(existentes.containsKey(e.getKey()) || !(e.getValue() instanceof String))continue;
            try{
                JSONObject mensaje=new JSONObject((String)e.getValue());
                if(!"aprobado".equals(mensaje.optString("tipo")))continue;
                edit.putString(e.getKey(),resumenAprendido(mensaje).toString());
                cambios=true;
            }catch(JSONException ignored){}
        }
        if(cambios&&!edit.commit())throw new IllegalStateException("No se pudo preservar la memoria aprendida.");
    }
    static List<JSONObject> aprendizajes(Context c){
        asegurarAprendizajes(c);
        List<JSONObject> lista=new ArrayList<>();
        for(Object valor:memoriaAprendida(c).getAll().values())if(valor instanceof String)try{
            JSONObject j=new JSONObject((String)valor);
            if("aprobado".equals(j.optString("tipo")))lista.add(j);
        }catch(JSONException ignored){}
        lista.sort((a,b)->Long.compare(b.optLong("hora"),a.optLong("hora")));
        return lista;
    }
    /** Elimina solamente conversaciones archivadas, nunca ejemplos de aprendizaje. */
    static int borrarArchivados(Context c){
        asegurarAprendizajes(c); // primero garantizar el respaldo del conocimiento
        SharedPreferences archivados=c.getSharedPreferences("archivados_v3",0);
        Set<String> hilos=new HashSet<>(archivados.getAll().keySet());
        if(hilos.isEmpty())return 0;
        SharedPreferences.Editor registros=datos(c).edit();
        SharedPreferences.Editor borradores=c.getSharedPreferences("borradores",0).edit();
        for(Map.Entry<String,?> e:datos(c).getAll().entrySet()){
            if(!(e.getValue() instanceof String))continue;
            try{
                JSONObject evento=new JSONObject((String)e.getValue());
                String hilo=evento.optString("hilo");
                if(!hilos.contains(hilo))continue;
                registros.remove(e.getKey());
                // Los borradores son por consulta; limpiar los de esos mensajes archivados.
                if("recibido".equals(evento.optString("tipo")))
                    borradores.remove("texto.v4."+Panel.hash(hilo+"\n"+evento.optLong("hora")));
                borradores.remove("texto.v3."+Panel.hash(hilo));
            }catch(JSONException ignored){}
        }
        if(!registros.commit())throw new IllegalStateException("No se pudo borrar el historial; no se modificó el archivo.");
        if(!borradores.commit())throw new IllegalStateException("No se pudieron limpiar los borradores.");
        if(!archivados.edit().clear().commit())throw new IllegalStateException("No se pudo actualizar la bandeja de archivados.");
        return hilos.size();
    }
    static JSONObject objeto(String... pares) {
        JSONObject j=new JSONObject();
        try{for(int i=0;i<pares.length;i+=2)j.put(pares[i],pares[i+1]);}
        catch(JSONException e){throw new IllegalArgumentException(e);}
        return j;
    }
    /** Evita registrar ecos de mensajes propios que WhatsApp reanexa a una notificación. */
    static boolean ecoPropio(Context c,String hilo,String texto,long t){
        String enviado=texto.trim();if(enviado.isEmpty())return false;
        for(Object v:datos(c).getAll().values())if(v instanceof String)try{
            JSONObject e=new JSONObject((String)v);
            if(!"aprobado".equals(e.optString("tipo"))||!hilo.equals(e.optString("hilo")))continue;
            long delta=t-e.optLong("hora");
            if(delta>=-120000L&&delta<=600000L&&enviado.equals(e.optString("mensaje").trim()))return true;
        }catch(Exception ignored){}
        return false;
    }
    static void entrada(Context c,Bandeja.Ficha f,String texto,long t) {
        if(texto==null||texto.trim().isEmpty()||ecoPropio(c,f.hilo,texto,t))return;
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
        // Se guarda tanto en el historial del chat como en la memoria de aprendizaje.
        // El aprendizaje permanece aunque después se borre el chat archivado.
        // El envío ya fue solicitado a WhatsApp; no fallar la pantalla por un error de guardado.
        datos(c).edit().putString(k,evento.toString()).apply();
        memoriaAprendida(c).edit().putString(k,resumenAprendido(evento).toString()).apply();
        return k;
    }
    static void procesoDeRespuesta(Context c,String clave,String categoria) {
        if(clave==null)return;
        String raw=datos(c).getString(clave,null);
        if(raw==null)return;
        try{JSONObject evento=new JSONObject(raw);evento.put("proceso",categoria);
            datos(c).edit().putString(clave,evento.toString()).apply();
            memoriaAprendida(c).edit().putString(clave,resumenAprendido(evento).toString()).apply();}
        catch(JSONException ignored){}
    }
    static void archivar(Context c,String hilo){c.getSharedPreferences("archivados_v3",0).edit().putLong(hilo,System.currentTimeMillis()).apply();}
    static void reabrir(Context c,String hilo){c.getSharedPreferences("archivados_v3",0).edit().remove(hilo).apply();}
    static boolean archivado(Context c,String hilo){return c.getSharedPreferences("archivados_v3",0).contains(hilo);}
    static List<JSONObject> eventos(Context c,String hilo){
        // Una sola lectura de SharedPreferences para evitar N*N lecturas en bandejas grandes.
        List<JSONObject> registros=new ArrayList<>(),filtrados=new ArrayList<>();
        Map<String,List<JSONObject>> salientes=new HashMap<>();
        for(Object v:datos(c).getAll().values())if(v instanceof String)try{
            JSONObject j=new JSONObject((String)v);
            String chat=j.optString("hilo");
            if(hilo!=null&&!hilo.equals(chat))continue;
            registros.add(j);
            if("aprobado".equals(j.optString("tipo"))){
                if(!salientes.containsKey(chat))salientes.put(chat,new ArrayList<>());
                salientes.get(chat).add(j);
            }
        }catch(JSONException ignored){}
        for(JSONObject j:registros){
            if("recibido".equals(j.optString("tipo"))){
                List<JSONObject> delChat=salientes.get(j.optString("hilo"));
                boolean eco=false;
                if(delChat!=null)for(JSONObject salida:delChat){
                    long delta=j.optLong("hora")-salida.optLong("hora");
                    if(delta>=-120000L&&delta<=600000L&&j.optString("mensaje").trim().equals(salida.optString("mensaje").trim())){
                        eco=true;break;
                    }
                }
                if(eco)continue;
            }
            filtrados.add(j);
        }
        filtrados.sort(Comparator.comparingLong(j->j.optLong("hora")));
        return filtrados;
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
    static final String[] grupos={"procesos","archivo","borradores","categorias_v3","archivados_v3","conocimiento_v4","aprendizaje_v5"};
    static String respaldo(Context c)throws JSONException {
        asegurarAprendizajes(c);
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
        asegurarAprendizajes(c);
    }
}
