package ar.com.pioca.respuestas;

import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Dos respaldos alternos mediante Storage Access Framework, fuera de los datos de la app. */
final class Resguardo {
    private static final String PREF="proteccion_aprendizajes_v9";
    private static final ExecutorService cola=Executors.newSingleThreadExecutor();
    private static final Object candado=new Object();
    private static volatile boolean inicializando=false;
    private Resguardo(){}

    static boolean configurado(Context c){return !p(c).getString("carpeta","").isEmpty();}
    private static SharedPreferences p(Context c){return c.getSharedPreferences(PREF,0);}
    static String estado(Context c){
        if(!configurado(c))return "Activá una carpeta externa. Borrar datos de Android elimina el aprendizaje local.";
        SharedPreferences prefs=p(c);
        long t=prefs.getLong("ultima_copia",0);
        if(t==0)return "Carpeta elegida. Preparando la primera copia A/B.";
        String f=new SimpleDateFormat("dd/MM HH:mm",new Locale("es","AR")).format(new Date(t));
        String error=prefs.getString("error","");
        return error.isEmpty()?"Último respaldo: "+f+" · Se actualiza tras cada respuesta aprobada.":
            "Última copia: "+f+" · Advertencia: "+error;
    }
    static void configurar(Context c,Uri carpeta){
        Context app=c.getApplicationContext();
        p(app).edit().putString("carpeta",carpeta.toString())
            .putLong("ultima_copia",0).putString("error","").putString("slot","").apply();
        // Crear ambas copias al activar, sin depender de una respuesta futura.
        cola.execute(()->{
            guardar(app,"A");
            guardar(app,"B");
        });
    }
    static void programar(Context c){
        Context app=c.getApplicationContext();
        if(!configurado(app))return;
        cola.execute(()->{
            String ultimo=p(app).getString("slot","");
            guardar(app,"A".equals(ultimo)?"B":"A");
        });
    }
    private static Uri archivo(Context c,Uri arbol,String nombre)throws Exception{
        ContentResolver cr=c.getContentResolver();
        String raiz=DocumentsContract.getTreeDocumentId(arbol);
        Uri hijos=DocumentsContract.buildChildDocumentsUriUsingTree(arbol,raiz);
        try(Cursor cursor=cr.query(hijos,new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME},null,null,null)){
            if(cursor!=null)while(cursor.moveToNext()){
                if(nombre.equals(cursor.getString(1)))
                    return DocumentsContract.buildDocumentUriUsingTree(arbol,cursor.getString(0));
            }
        }
        Uri padre=DocumentsContract.buildDocumentUriUsingTree(arbol,raiz);
        Uri creado=DocumentsContract.createDocument(cr,padre,"application/json",nombre);
        if(creado==null)throw new IOException("El proveedor de archivos no permitió crear "+nombre);
        return creado;
    }
    private static void guardar(Context c,String slot){
        String str=p(c).getString("carpeta","");if(str.isEmpty())return;
        try{
            String contenido=Memoria.respaldoSoloAprendizajes(c);
            Uri destino=archivo(c,Uri.parse(str),"piOca-aprendizajes-"+slot+".json");
            try(OutputStream os=c.getContentResolver().openOutputStream(destino,"wt")){
                if(os==null)throw new IOException("No se puede escribir la copia "+slot);
                os.write(contenido.getBytes(StandardCharsets.UTF_8));
                os.flush();
            }
            // Confirmar que la copia es legible y tiene el formato correcto.
            try(InputStream is=c.getContentResolver().openInputStream(destino)){
                if(is==null)throw new IOException("No se puede verificar la copia "+slot);
                ByteArrayOutputStream bytes=new ByteArrayOutputStream();
                byte[] buffer=new byte[8192];int n;
                while((n=is.read(buffer))!=-1){bytes.write(buffer,0,n);
                    if(bytes.size()>24*1024*1024)throw new IOException("Copia demasiado grande");}
                JSONObject comprobacion=new JSONObject(new String(bytes.toByteArray(),StandardCharsets.UTF_8));
                if(!"pioca-aprendizajes-1".equals(comprobacion.optString("formato")) ||
                    !comprobacion.has("aprendizaje_v5"))
                    throw new IOException("La copia no pudo validarse");
            }
            p(c).edit().putString("slot",slot)
                .putLong("ultima_copia",System.currentTimeMillis()).putString("error","").apply();
        }catch(Exception e){
            android.util.Log.e("piOca-Resguardo","Error al guardar aprendizaje",e);
            String detalle=e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();
            if(detalle.length()>140)detalle=detalle.substring(0,140);
            p(c).edit().putString("error",detalle).apply();
        }
    }
}
