package ar.com.pioca.respuestas;

import android.content.*;
import org.json.*;
import java.util.*;

final class Procesos {
    static final String[] BASE={"Presupuestos nuevos","Servicio técnico","Dudas sobre el servicio","Cotización de controles","Seguimiento del servicio","Coordinación de visita","Otro proceso"};
    static final String PROMPT="Sos el redactor profesional de piOca®, empresa especializada en automatización de portones. "+
      "RESPETÁ ESTRICTAMENTE el texto o dictado del dueño: sus palabras técnicas, nombres de piezas, objetos, cantidades, fechas, "+
      "destinatarios, condiciones y solicitudes. Solo corregí ortografía, puntuación, gramática y claridad, con tono serio y profesional. "+
      "Nunca reemplaces conceptos específicos por genéricos; 'portón' no significa 'producto', 'motor' no significa 'equipo' si el dueño no lo indicó. "+
      "Nunca inventes información, promesas, precios, saludos, beneficios, requisitos ni condiciones. Nunca elimines detalles. "+
      "No respondas preguntas por iniciativa propia: redactá exactamente la intención del dueño. "+
      "No uses emoticones ni textos explicativos. Si hay duda mantené las palabras del dueño. Devolvé únicamente la respuesta final.";
    static final String ORTO="Modo REVALIDACIÓN: corregí EXCLUSIVAMENTE tildes, ortografía, signos de puntuación y errores gramaticales inequívocos. " +
      "No cambies palabras, conceptos, secuencia, formalidad, condiciones ni el sentido. No agregues ni quites contenido.";
    static List<String> nombres(Context c){
        LinkedHashSet<String> r=new LinkedHashSet<>(Arrays.asList(BASE));
        for(Object s:c.getSharedPreferences("categorias_v3",0).getAll().values())if(s instanceof String)r.add((String)s);
        return new ArrayList<>(r);
    }
    static boolean agregar(Context c,String nombre){
        nombre=nombre.trim();if(nombre.length()<3||nombre.length()>80)return false;
        for(String v:nombres(c))if(v.equalsIgnoreCase(nombre))return false;
        c.getSharedPreferences("categorias_v3",0).edit().putString(UUID.randomUUID().toString(),nombre).apply();return true;
    }
    static String regla(Context c,String categoria){
        SharedPreferences p=c.getSharedPreferences("procesos",0);
        String regla=p.getString("regla.v3."+categoria,"");
        // Se mantiene la compatibilidad con reglas aprendidas en V2.
        String[] viejos={"Cotización de controles","Seguimiento del servicio","Coordinación de visita","Otro proceso"};
        for(int i=0;i<viejos.length;i++)if(viejos[i].equals(categoria))regla += "\n"+p.getString("regla."+i,"");
        return regla;
    }
    static void guardarRegla(Context c,String categoria,String regla){
        c.getSharedPreferences("procesos",0).edit().putString("regla.v3."+categoria,regla).apply();
    }
    static class Aprendida {
        final String consulta,respuesta,proceso;final double similitud;
        Aprendida(String c,String r,String p,double s){consulta=c;respuesta=r;proceso=p;similitud=s;}
    }
    static Set<String> tokens(String s){
        HashSet<String> set=new HashSet<>();
        s=s.toLowerCase(Locale.ROOT).replaceAll("[^a-záéíóúüñ0-9]+"," ");
        Set<String> stop=new HashSet<>(Arrays.asList("de","la","el","los","las","un","una","a","en","y","por","para","que","me","te","se","lo","con","hola","buen","día","buenas","quiero","quería","como","cómo","estás","consulta","consultar"));
        for(String t:s.split(" +"))if(t.length()>2&&!stop.contains(t))set.add(t);
        return set;
    }
    static Aprendida sugerencia(Context c,String consulta){
        Set<String> a=tokens(consulta);if(a.size()<2)return null;
        Aprendida mejor=null;
        for(JSONObject e:Memoria.eventos(c,null)){
            if(!"aprobado".equals(e.optString("tipo")))continue;
            String anterior=e.optString("consulta"),respuesta=e.optString("mensaje");
            if(anterior.isEmpty()||respuesta.isEmpty())continue;
            Set<String> b=tokens(anterior);if(b.isEmpty())continue;
            int comunes=0;for(String t:a)if(b.contains(t))comunes++;
            double score=(2.0*comunes)/(a.size()+b.size());
            if(score>=0.43&&(mejor==null||score>mejor.similitud))
                mejor=new Aprendida(anterior,respuesta,e.optString("proceso"),score);
        }
        return mejor;
    }
}
