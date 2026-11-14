package ar.com.pioca.respuestas;

import android.content.*;
import org.json.*;
import java.util.*;
import java.text.Normalizer;

/** Guías empresariales editables y recuperación contextual local. */
final class Procesos {
    static final String[] BASE={"Presupuestos nuevos","Servicio técnico","Dudas sobre el servicio","Cotización de controles","Seguimiento del servicio","Coordinación de visita","Garantías","Pagos y condiciones comerciales","Productos y automatizaciones","Otro proceso"};

    static final String PROMPT=
      "Sos el responsable de atención comercial de piOca®, empresa especializada en automatización de portones en Argentina. " +
      "Transformá el dictado informal del operador en una respuesta empresarial verdaderamente profesional, cálida sin exceso de confianza, precisa y lista para WhatsApp Business. " +
      "Redactá en español argentino, sin emojis ni viñetas decorativas. Usá puntuación correcta y SEPARÁ en párrafos legibles (habitualmente saludo breve, información o requisitos y cierre, solo cuando corresponda). " +
      "Actuá como un editor comercial senior: no te limites a pasar en limpio. Reconstruí oraciones para que las ideas tengan una progresión lógica, un lenguaje seguro y fluido y una comunicación de categoría superior. " +
      "Podés reformular oraciones, ordenar ideas, unir conceptos y mejorar fluidez; no copies el dictado como un bloque continuo. " +
      "Cada párrafo debe cumplir una función: contexto, explicación o requisito, y siguiente paso. Vinculá la explicación técnica con su motivo o resultado cuando el operador lo haya indicado. " +
      "Elegí verbos concretos y frases naturales; evitá el tono plano, repetitivo, telegráfico o excesivamente burocrático. " +
      "No infles mensajes simples: para consultas breves respondé breve; desarrollá un poco más solo si el contenido técnico lo necesita. " +
      "Conservá todos los hechos, nombres, cantidades, términos técnicos, condiciones, destinatarios y la intención del operador. " +
      "No confundas 'foto del portón' con 'foto del producto'; no sustituyas motores, piezas ni trabajos por genéricos. " +
      "Nunca inventes precios, visitas, plazos, garantías, requisitos nuevos, datos de un cliente, promociones ni compromisos. " +
      "No agregues saludos si la conversación ya continúa y no corresponde. Si no se autorizó una respuesta concreta, no inventes una solución. " +
      "Los ejemplos aprobados orientan el estilo y el procedimiento, pero no se deben copiar automáticamente; usá solamente los pertinentes a la intención ACTUAL. " +
      "En explicaciones técnicas, transmití autoridad profesional y criterio mecánico real: explicá solo lo esencial, con precisión, términos correctos y consecuencias concretas para el cliente. " +
      "Preferí mensajes CONCISOS y párrafos cortos, sin repeticiones, relleno, exageraciones ni autopromoción. " +
      "Demostrá dominio técnico con claridad, no con slogans o afirmaciones de liderazgo no comprobadas. Nunca inventes especificaciones ni cambies datos técnicos. " +
      "Si hace falta explicar causa, solución y resultado, hacelo en ese orden y con la menor extensión útil. " +
      "Devolvé ÚNICAMENTE el mensaje final, sin comentarios, sin títulos y sin comillas.";

    /** Prompt editorial completo aportado por piOca®, sin abreviar ni reescribir. */
    static String prompt(Context c){
        try(java.io.InputStream input=c.getAssets().open("pioca_redactor.txt");
            java.io.ByteArrayOutputStream output=new java.io.ByteArrayOutputStream()){
            byte[] bytes=new byte[4096];int n;
            while((n=input.read(bytes))!=-1)output.write(bytes,0,n);
            String textoOriginal=output.toString("UTF-8");
            return "INTERPRETACIÓN DE ÓRDENES DEL OPERADOR: El dueño te habla como a su empleado. " +
                "Puede decir 'decile al cliente', 'respondamos a este tipo', hablar en tercera persona o dictar directamente " +
                "el mensaje final. Interpretá esas expresiones como instrucciones internas. " +
                "Entregá solamente la respuesta dirigida al cliente en nombre de piOca®, " +
                "sin reproducir órdenes, insultos ni comentarios internos. " +
                "En modo corrección u ortografía aplicá únicamente el cambio solicitado en ese modo.\n\n" +
                textoOriginal;
        }catch(java.io.IOException ex){
            android.util.Log.e("piOcaEmpleadoIA", "No pudo cargarse el prompt editorial", ex);
            return PROMPT; // mantener redacción disponible ante un problema con assets
        }
    }

    static final String CORRECCION=
      "MODO CORREGIR RESPUESTA YA ARMADA: borrador_actual es la respuesta en edición y 'indicacion' contiene una orden nueva del operador, NO un nuevo mensaje del cliente. " +
      "Aplicá EXACTAMENTE las correcciones solicitadas al borrador existente, conservá todo lo que no te pidieron modificar, incluidos precios, hechos y detalles técnicos. " +
      "Volvé a entregar la respuesta COMPLETA, profesionalmente redactada y dividida en párrafos. " +
      "Si el operador ordena suprimir o cambiar algo, esa orden prevalece sobre el texto anterior. No incluyas las instrucciones del operador en el mensaje al cliente.";

    static final String ORTO=
      "MODO ORTOGRAFÍA: solo corregí tildes, ortografía, puntuación y errores gramaticales inequívocos del texto de borrador_actual. " +
      "Conservá EXACTAMENTE las ideas, vocabulario, información, orden, párrafos y tono. No agregues saludos ni cierres. No omitas ni introduzcas requisitos.";

    static List<String> nombres(Context c){
        LinkedHashSet<String> r=new LinkedHashSet<>(Arrays.asList(BASE));
        for(Object v:c.getSharedPreferences("categorias_v3",0).getAll().values())if(v instanceof String)r.add((String)v);
        return new ArrayList<>(r);
    }
    static boolean agregar(Context c,String nombre){
        nombre=nombre.trim();if(nombre.length()<3||nombre.length()>80)return false;
        for(String v:nombres(c))if(v.equalsIgnoreCase(nombre))return false;
        c.getSharedPreferences("categorias_v3",0).edit().putString(UUID.randomUUID().toString(),nombre).apply();return true;
    }
    static String limpiar(String t){
        return Normalizer.normalize(t.toLowerCase(Locale.ROOT),Normalizer.Form.NFD).replaceAll("\\p{M}","");
    }
    static boolean tiene(String s,String... raices){for(String v:raices)if(s.contains(v))return true;return false;}
    /** Detecta la intención actual. No confunde saludos con procedimientos. */
    static String clasificar(String mensaje){
        String s=limpiar(mensaje);
        if(tiene(s,"garantia","garantiz","cubierto por la garantia"))return "Garantías";
        if(tiene(s,"servicio tecnico","service","reparaci","revisar el motor","no funciona","dejo de funcionar","no abre","no cierra","se traba","esta fallando","se rompio","tecnico para"))return "Servicio técnico";
        if(tiene(s,"seguimiento","en camino","cuando vienen","donde esta el tecnico"))return "Seguimiento del servicio";
        if(tiene(s,"visita","turno","coordinar","agendar","fecha de instalacion"))return "Coordinación de visita";
        if(tiene(s,"control remoto","controles","control adicional","duplicar control","copiar control"))return "Cotización de controles";
        if(tiene(s,"factura","transferencia","efectivo","iva","pago","financi","cheque"))return "Pagos y condiciones comerciales";
        if(tiene(s,"precio","presupuest","cotiz","cuanto cuesta","cuanto sale","valor","automatizar mi porton","automatizacion del porton","poner motor","instalar un motor"))return "Presupuestos nuevos";
        if(tiene(s,"brushless","bldc","potencia del motor","motor corredizo","motor levadizo","motor batiente","modelo de motor"))return "Productos y automatizaciones";
        return "Otro proceso";
    }
    static String regla(Context c,String categoria){
        SharedPreferences p=c.getSharedPreferences("procesos",0);
        String regla=p.getString("regla.v3."+categoria,"");
        String[] viejos={"Cotización de controles","Seguimiento del servicio","Coordinación de visita","Otro proceso"};
        for(int i=0;i<viejos.length;i++)if(viejos[i].equals(categoria))regla += "\n"+p.getString("regla."+i,"");
        return regla;
    }
    static void guardarRegla(Context c,String categoria,String regla){c.getSharedPreferences("procesos",0).edit().putString("regla.v3."+categoria,regla).apply();}
    /** Conocimiento oficial: puede editarse en ajustes, nunca se aprende de una única corrección por defecto. */
    static String conocimientoBase(String categoria){
        switch(categoria){
            case "Presupuestos nuevos": return "Para presupuestar una automatización se necesitan detalles técnicos del portón. La fotografía del portón permite evaluar el trabajo. No confundir esta consulta con servicio técnico. Nunca elegir o prometer un equipo sin evaluación. Solo pedir otros datos si están indicados por el operador o por una regla aprobada.";
            case "Servicio técnico": return "El servicio técnico se coordina durante la semana; piOca® no ofrece atención de urgencia. No diagnosticar ni prometer reparación sin comprobar el problema. Pedir foto del motor cuando se requiera para cotizar o evaluar un control. No enviar automáticamente instrucciones de presupuesto de una automatización nueva.";
            case "Dudas sobre el servicio": return "Responder concretamente a la pregunta. Si falta un dato, indicarlo sin inventarlo. Priorizar claridad técnica y profesionalidad.";
            case "Cotización de controles": return "Para cotizar un control puede ser importante identificar el motor o receptor mediante una fotografía. No prometer compatibilidades ni existencia sin comprobarlas.";
            case "Seguimiento del servicio": return "No prometer horarios de llegada ni movimientos del técnico sin información confirmada. Distinguir seguimiento de coordinación.";
            case "Coordinación de visita": return "Las visitas se coordinan; no prometer disponibilidad, fecha ni horario que no hayan sido confirmados. La visita técnica puede requerir pago previo según el caso.";
            case "Garantías": return "No atribuir cobertura, vigencia ni condiciones que no figuren en la documentación del cliente.";
            case "Pagos y condiciones comerciales": return "No inventar precios, descuentos, financiación, alícuotas ni condiciones. Usar solamente datos autorizados del caso y reglas aprobadas.";
            case "Productos y automatizaciones": return "Usar terminología técnica precisa y respetar los datos comprobados. No reemplazar portón, motor ni piezas por términos genéricos.";
            default: return "Responder según la intención más reciente del cliente y las instrucciones expresas del operador, sin inventar datos.";
        }
    }
    static String conocimiento(Context c,String categoria){
        return c.getSharedPreferences("conocimiento_v4",0).getString(categoria,conocimientoBase(categoria));
    }
    static void guardarConocimiento(Context c,String categoria,String texto){
        c.getSharedPreferences("conocimiento_v4",0).edit().putString(categoria,texto).apply();
    }
    static String instrucciones(Context c,String categoria){return "SEGMENTO ACTUAL: "+categoria+".\nCONOCIMIENTO EMPRESARIAL APROBADO:\n"+conocimiento(c,categoria)+"\nREGLAS PERSONALIZADAS:\n"+regla(c,categoria);}

    static Set<String> tokens(String s){
        Set<String> stop=new HashSet<>(Arrays.asList("de","la","el","los","las","un","una","a","en","y","por","para","que","me","te","se","lo","con","hola","buen","dia","buenas","quiero","queria","como","estas","consulta","consultar","saber","favor","gracias","saludos"));
        Set<String> r=new HashSet<>();for(String t:limpiar(s).split("[^a-z0-9ñ]+"))if(t.length()>2&&!stop.contains(t))r.add(t);
        return r;
    }
    static double similitud(String a,String b){
        Set<String> x=tokens(a),y=tokens(b);if(x.isEmpty()||y.isEmpty())return 0;
        int comunes=0;for(String t:x)if(y.contains(t))comunes++;
        return (2.0*comunes)/(x.size()+y.size());
    }
    static class Aprendida {
        final String consulta,respuesta,proceso;final double similitud;
        Aprendida(String c,String r,String p,double s){consulta=c;respuesta=r;proceso=p;similitud=s;}
    }
    static boolean respuestaUtil(String respuesta,String categoria){
        if(respuesta==null||respuesta.trim().length()<80)return false;
        if(tokens(respuesta).size()<8)return false;
        String s=limpiar(respuesta);
        if(categoria.equals("Presupuestos nuevos"))return tiene(s,"presupuest","cotiz","porton","automatiz","foto");
        if(categoria.equals("Servicio técnico"))return tiene(s,"servicio","tecnico","motor","repar","revis","foto");
        return true;
    }
    /** Solo ejemplos de la MISMA intención, con contenido sustantivo. */
    static List<Aprendida> ejemplos(Context c,String consulta,String categoria,int limite){
        List<Aprendida> candidatos=new ArrayList<>();
        for(JSONObject e:Memoria.aprendizajes(c)){
            if(!"aprobado".equals(e.optString("tipo")))continue;
            String anterior=e.optString("consulta"),respuesta=e.optString("mensaje");
            String clase=e.optString("proceso");
            if(!categoria.equals(clase)&&!categoria.equals(clasificar(anterior)))continue;
            if(!respuestaUtil(respuesta,categoria))continue;
            double score=similitud(consulta,anterior);
            // Intención compartida basta para el contexto del modelo; la interfaz exige similitud mayor.
            candidatos.add(new Aprendida(anterior,respuesta,categoria,score));
        }
        candidatos.sort((a,b)->Double.compare(b.similitud,a.similitud));
        if(candidatos.size()>limite)return new ArrayList<>(candidatos.subList(0,limite));
        return candidatos;
    }
    static Aprendida sugerencia(Context c,String consulta,String categoria){
        List<Aprendida> opciones=ejemplos(c,consulta,categoria,1);
        if(opciones.isEmpty())return null;
        Aprendida mejor=opciones.get(0);
        if(mejor.similitud<0.54)return null;
        return mejor;
    }
}
