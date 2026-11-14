package ar.com.pioca.respuestas;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaRecorder;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.inputmethod.InputMethodManager;
import android.provider.Settings;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.text.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.net.*;
import javax.net.ssl.HttpsURLConnection;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.text.Normalizer;
import java.util.regex.*;
import java.util.*;

/** piOca® Empleado IA V9. Editor visual V6 estable; resguardo externo de aprendizajes. */
public class Panel extends Activity {
    static final int NAVY=Color.rgb(20,33,51),GREEN=Color.rgb(11,117,90),BG=Color.rgb(246,248,250);
    static final int MUTED=Color.rgb(101,115,127),BORDER=Color.rgb(220,228,234);
    LinearLayout root,body,container;
    ScrollView scroll;
    EditText indicacion,resultado,correccion;
    TextView estado;
    Button enviar, botonProceso, botonLectura;
    TextToSpeech lector;
    boolean lectorListo=false;
    String lecturaPendiente=null;
    static final String ID_LECTURA="pioca-v9-respuesta";
    static final int PERMISO_MIC=310;
    static final long AUDIO_MAX_BYTES=20L*1024*1024;
    MediaRecorder grabador;
    File grabacionActual;
    AlertDialog dialogoGrabacion;
    TextView contadorGrabacion;
    long inicioGrabacion;
    final Handler relojGrabacion=new Handler(Looper.getMainLooper());
    Runnable tareaReloj;
    int tipoGrabacion=200, tipoPendiente=200;
    boolean grabacionFinalizando=false;
    String hiloActual,clienteActual,consultaActual,procesoActual="Presupuestos nuevos";
    String primerBorrador="",propuestaAI="",ultimaIndicacion="",historialCorrecciones="";
    long marcaConsulta=0;
    boolean mostrandoArchivados=false;
    int ultimaSolicitud=0;
    final int VOZ=200, VOZ_CORRECCION=201;

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        instalarDiagnostico();
        mostrarBandeja(false);
        Resguardo.programar(this);
    }
    void instalarDiagnostico(){
        final Thread.UncaughtExceptionHandler anterior=Thread.getDefaultUncaughtExceptionHandler();
        if(anterior!=null && anterior.getClass().getName().contains("pioca"))return;
        Thread.setDefaultUncaughtExceptionHandler((h,error)->{
            try{
                getSharedPreferences("diagnostico_errores",0).edit()
                    .putString("ultimo",android.util.Log.getStackTraceString(error))
                    .putLong("hora",System.currentTimeMillis()).commit();
            }catch(Throwable ignored){}
            if(anterior!=null)anterior.uncaughtException(h,error);
        });
    }
    void mostrarUltimoError(){
        String error=getSharedPreferences("diagnostico_errores",0).getString("ultimo","");
        if(error.isEmpty()){aviso("No hay un error registrado desde la última instalación.");return;}
        String detalle=error.length()>12000?error.substring(0,12000):error;
        new AlertDialog.Builder(this).setTitle("Diagnóstico de cierre")
           .setMessage(detalle)
           .setPositiveButton("Copiar diagnóstico",(d,n)->{
               android.content.ClipboardManager cb=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
               if(cb!=null)cb.setPrimaryClip(ClipData.newPlainText("Error Empleado IA",detalle));
               aviso("Diagnóstico copiado. Podés enviármelo para revisar el error exacto.");
           }).setNegativeButton("Cerrar",null).show();
    }
    @Override protected void onResume(){
        super.onResume();
        Bandeja.cambio=()->runOnUiThread(()->{
            if(hiloActual==null)mostrarBandeja(mostrandoArchivados);
            else if(!revisarNuevaConsulta())actualizarEnvio();
        });
        // El servicio de notificaciones es independiente de esta pantalla.
        // Si Android lo desconectó, pedimos recuperación y leemos avisos aún activos.
        Bandeja.recuperar(this);
        if(hiloActual!=null){if(!revisarNuevaConsulta())actualizarEnvio();}
    }
    @Override protected void onPause(){Bandeja.cambio=null;super.onPause();}
    @Override protected void onStop(){
        // Nunca dejar un micrófono grabando inadvertidamente al abandonar la pantalla.
        if(grabador!=null){cerrarPanelGrabacion();finalizarDictado(false);}
        super.onStop();
    }
    @Override protected void onDestroy(){
        detenerContador();
        if(grabador!=null)finalizarDictado(false);
        if(lector!=null){lector.stop();lector.shutdown();lector=null;}
        super.onDestroy();
    }
    void detenerLectura(){
        lecturaPendiente=null;
        if(lector!=null)lector.stop();
        if(botonLectura!=null)botonLectura.setText("▶ Escuchar respuesta");
    }
    void escucharRespuesta(){
        if(resultado==null)return;
        String textoParaLeer=resultado.getText().toString().trim();
        if(textoParaLeer.isEmpty()){aviso("Primero prepará una respuesta para escuchar.");return;}
        if(lector!=null&&lector.isSpeaking()){detenerLectura();return;}
        if(lectorListo){reproducirTexto(textoParaLeer);return;}
        lecturaPendiente=textoParaLeer;
        if(lector!=null){aviso("Preparando la voz del teléfono...");return;}
        if(botonLectura!=null)botonLectura.setText("Preparando voz...");
        lector=new TextToSpeech(getApplicationContext(),estadoTts->runOnUiThread(()->{
            if(isDestroyed()||lector==null)return;
            if(estadoTts!=TextToSpeech.SUCCESS){
                lectorListo=false;lecturaPendiente=null;
                if(botonLectura!=null)botonLectura.setText("▶ Escuchar respuesta");
                aviso("No se pudo iniciar la voz. Revisá la síntesis de voz de Android.");
                return;
            }
            int idioma=lector.setLanguage(new Locale("es","AR"));
            if(idioma==TextToSpeech.LANG_NOT_SUPPORTED||idioma==TextToSpeech.LANG_MISSING_DATA)
                idioma=lector.setLanguage(new Locale("es","ES"));
            if(idioma==TextToSpeech.LANG_NOT_SUPPORTED||idioma==TextToSpeech.LANG_MISSING_DATA){
                lecturaPendiente=null;
                if(botonLectura!=null)botonLectura.setText("▶ Escuchar respuesta");
                aviso("Instalá una voz en español en los ajustes de texto a voz del teléfono.");
                return;
            }
            lector.setSpeechRate(0.94f);
            lector.setOnUtteranceProgressListener(new UtteranceProgressListener(){
                @Override public void onStart(String id){}
                @Override public void onDone(String id){runOnUiThread(()->{
                    if(botonLectura!=null)botonLectura.setText("▶ Escuchar respuesta");
                });}
                @Override public void onError(String id){runOnUiThread(()->{
                    if(botonLectura!=null)botonLectura.setText("▶ Escuchar respuesta");
                    aviso("No se pudo reproducir la respuesta.");
                });}
            });
            lectorListo=true;
            if(lecturaPendiente!=null){String pendiente=lecturaPendiente;lecturaPendiente=null;reproducirTexto(pendiente);}
        }));
    }
    void reproducirTexto(String texto){
        if(lector==null||!lectorListo)return;
        if(botonLectura!=null)botonLectura.setText("■ Detener lectura");
        int res=lector.speak(texto,TextToSpeech.QUEUE_FLUSH,null,ID_LECTURA);
        if(res==TextToSpeech.ERROR){
            if(botonLectura!=null)botonLectura.setText("▶ Escuchar respuesta");
            aviso("No se pudo iniciar la reproducción.");
        }
    }

    int dp(float x){return Math.round(x*getResources().getDisplayMetrics().density);}
    GradientDrawable fondo(int color,int r,int stroke){
        GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(r));
        if(stroke!=0)d.setStroke(dp(1),stroke);return d;
    }
    LinearLayout linear(boolean vertical){LinearLayout l=new LinearLayout(this);l.setOrientation(vertical?1:0);return l;}
    LinearLayout.LayoutParams lp(int w,int h){return new LinearLayout.LayoutParams(w<0?w:dp(w),h<0?h:dp(h));}
    LinearLayout.LayoutParams margen(int w,int h,int a,int b,int c,int d){
        LinearLayout.LayoutParams p=lp(w,h);p.setMargins(dp(a),dp(b),dp(c),dp(d));return p;
    }
    TextView texto(String s,int size,int color,boolean bold){
        TextView t=new TextView(this);t.setText(s);t.setTextColor(color);t.setTextSize(size);
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;
    }
    Button boton(String s,int color,boolean full,Runnable click){
        Button b=new Button(this);b.setAllCaps(false);b.setText(s);b.setTextSize(14);
        b.setTextColor(full?Color.WHITE:NAVY);b.setBackground(fondo(full?color:Color.WHITE,12,full?0:BORDER));
        b.setPadding(dp(12),dp(5),dp(12),dp(5));b.setOnClickListener(v->click.run());return b;
    }
    void aviso(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    LinearLayout card(){LinearLayout c=linear(true);c.setPadding(dp(14),dp(13),dp(14),dp(13));
        c.setBackground(fondo(Color.WHITE,16,BORDER));return c;}
    void empezar(String subtitulo){
        root=linear(true);root.setBackgroundColor(BG);setContentView(root);
        LinearLayout barra=linear(false);barra.setGravity(Gravity.CENTER_VERTICAL);
        barra.setPadding(dp(16),dp(12),dp(14),dp(12));barra.setBackgroundColor(Color.WHITE);
        ImageView logo=new ImageView(this);logo.setImageResource(R.drawable.marca);logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        barra.addView(logo,lp(39,39));
        LinearLayout marca=linear(true);
        marca.addView(texto("piOca®",15,NAVY,true));marca.addView(texto("Empleado IA · V9 · Memoria protegida",12,MUTED,false));
        barra.addView(marca,margen(0,-2,7,0,0,0));
        Space espacio=new Space(this);barra.addView(espacio,new LinearLayout.LayoutParams(0,1,1));
        TextView options=texto("⚙",23,NAVY,false);options.setGravity(Gravity.CENTER);options.setContentDescription("Configuración");
        options.setOnClickListener(v->ajustes());barra.addView(options,lp(40,40));
        root.addView(barra,lp(-1,64));
        if(subtitulo!=null){TextView sub=texto(subtitulo,12,MUTED,false);sub.setPadding(dp(18),dp(8),dp(18),dp(3));root.addView(sub);}
        scroll=new ScrollView(this);scroll.setFillViewport(false);
        container=linear(true);container.setPadding(dp(14),dp(12),dp(14),dp(24));
        scroll.addView(container);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
    }
    void mostrarBandeja(boolean archivados){
        detenerLectura();
        botonLectura=null;
        hiloActual=null;indicacion=null;resultado=null;correccion=null;enviar=null;
        mostrandoArchivados=archivados;
        empezar("Bandeja de atención · WhatsApp Business");
        LinearLayout tabs=linear(false);tabs.setBackground(fondo(Color.rgb(233,239,242),14,0));tabs.setPadding(dp(4),dp(4),dp(4),dp(4));
        int count=Memoria.hilos(this,false).size();
        TextView t1=texto("Pendientes ("+count+")",14,archivados?MUTED:NAVY,!archivados);
        TextView t2=texto("Archivados",14,archivados?NAVY:MUTED,archivados);
        t1.setGravity(Gravity.CENTER);t2.setGravity(Gravity.CENTER);
        t1.setPadding(dp(3),dp(12),dp(3),dp(12));t2.setPadding(dp(3),dp(12),dp(3),dp(12));
        if(!archivados)t1.setBackground(fondo(Color.WHITE,11,0));else t2.setBackground(fondo(Color.WHITE,11,0));
        tabs.addView(t1,new LinearLayout.LayoutParams(0,-2,1));tabs.addView(t2,new LinearLayout.LayoutParams(0,-2,1));
        t1.setOnClickListener(v->mostrarBandeja(false));t2.setOnClickListener(v->mostrarBandeja(true));
        container.addView(tabs,lp(-1,-2));
        LinearLayout memoria=card();
        boolean respaldoConfigurado=Resguardo.configurado(this);
        String estadoCopia=Resguardo.estado(this);
        memoria.addView(texto(respaldoConfigurado?"Memoria protegida · Copias automáticas":"Memoria sin respaldo externo",14,
            respaldoConfigurado?GREEN:Color.rgb(153,61,35),true));
        memoria.addView(texto(estadoCopia,12,MUTED,false),margen(-1,-2,0,5,0,7));
        LinearLayout botonesMemoria=linear(false);
        Button proteger=boton(respaldoConfigurado?"Cambiar carpeta":"Activar respaldo",GREEN,false,this::elegirCarpetaProteccion);
        botonesMemoria.addView(proteger,new LinearLayout.LayoutParams(0,dp(44),1));
        Button recuperar=boton("Restaurar",NAVY,false,this::elegirArchivoAprendizajes);
        botonesMemoria.addView(recuperar,margen(112,44,8,0,0,0));
        memoria.addView(botonesMemoria,lp(-1,-2));
        container.addView(memoria,margen(-1,-2,0,8,0,12));
        String estadoCaptura=Bandeja.conectado?
            (Bandeja.ultimoAviso>0?"✓ Notificaciones activas · Última recepción: "+new SimpleDateFormat("HH:mm",new Locale("es","AR")).format(new Date(Bandeja.ultimoAviso)):
            "✓ Acceso a notificaciones conectado"):
            "⚠ Acceso a notificaciones sin conexión · Tocá acá para revisar";
        TextView captura=texto(estadoCaptura,12,Bandeja.conectado?GREEN:MUTED,true);
        captura.setPadding(dp(7),dp(12),dp(7),dp(2));
        captura.setOnClickListener(v->startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        container.addView(captura);
        if(!archivados){
            TextView bateria=texto("¿No aparecen mensajes con la pantalla bloqueada? Tocá para revisar Batería → Sin restricciones.",12,MUTED,false);
            bateria.setPadding(dp(7),dp(6),dp(7),dp(8));
            bateria.setOnClickListener(v->{
                try{startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:"+getPackageName())));}
                catch(Exception ex){aviso("Abrí Ajustes → Aplicaciones → Empleado IA → Batería.");}
            });
            container.addView(bateria);
        }
        LinearLayout cabecera=linear(false);cabecera.setGravity(Gravity.CENTER_VERTICAL);
        TextView encabezado=texto(archivados?"Conversaciones archivadas":"Mensajes por responder",21,NAVY,true);
        cabecera.addView(encabezado,new LinearLayout.LayoutParams(0,-2,1));
        if(archivados){
            Button borrarTodos=boton("Borrar todos",Color.rgb(146,48,48),false,this::confirmarBorradoArchivados);
            borrarTodos.setTextColor(Color.rgb(146,48,48));
            cabecera.addView(borrarTodos,margen(132,42,8,0,0,0));
        }
        container.addView(cabecera,margen(-1,-2,2,20,2,5));
        TextView detalle=texto(archivados?"Podés recuperar conversaciones deslizando a la derecha. Borrar todos elimina el historial, no los aprendizajes.":
            "Deslizá una conversación hacia la izquierda para archivarla.",12,MUTED,false);
        container.addView(detalle,margen(-1,-2,2,0,2,18));
        if(archivados){
            TextView aprendizaje=texto("🧠 Respuestas aprendidas: "+Memoria.aprendizajes(this).size()+" · Ver registro",13,GREEN,true);
            aprendizaje.setPadding(dp(8),dp(2),dp(8),dp(13));
            aprendizaje.setOnClickListener(v->mostrarAprendizajes());
            container.addView(aprendizaje);
        }
        List<Memoria.Hilo> lista=Memoria.hilos(this,archivados);
        if(lista.isEmpty()){
            LinearLayout c=card();c.addView(texto(archivados?"No hay conversaciones archivadas":"No hay mensajes pendientes",17,NAVY,true));
            c.addView(texto("Las conversaciones nuevas de WhatsApp Business aparecerán aquí cuando Android entregue su notificación.",13,MUTED,false),margen(-1,-2,0,8,0,0));
            container.addView(c,lp(-1,-2));return;
        }
        for(Memoria.Hilo h:lista)tarjetaHilo(h,archivados);
    }
    void mostrarAprendizajes(){
        try{
            List<JSONObject> guardadas=Memoria.aprendizajes(this);
            if(guardadas.isEmpty()){aviso("Todavía no hay respuestas aprobadas aprendidas.");return;}
            int n=Math.min(50,guardadas.size());
            String[] opciones=new String[n];
            for(int i=0;i<n;i++){
                JSONObject e=guardadas.get(i);
                String pregunta=e.optString("consulta").replace('\n',' ');
                if(pregunta.length()>65)pregunta=pregunta.substring(0,65)+"…";
                opciones[i]=e.optString("proceso","Otro proceso")+" · "+pregunta;
            }
            new AlertDialog.Builder(this).setTitle("Respuestas aprendidas ("+guardadas.size()+")")
                .setMessage("Se guardan aparte del historial. Borrar Archivados no afecta esta memoria.")
                .setItems(opciones,(d,which)->{
                    JSONObject e=guardadas.get(which);
                    new AlertDialog.Builder(this).setTitle(e.optString("proceso"))
                        .setMessage("Consulta original:\n"+e.optString("consulta")+"\n\nRespuesta aprobada:\n"+e.optString("mensaje"))
                        .setPositiveButton("Cerrar",null).show();
                }).setNegativeButton("Cerrar",null).show();
        }catch(Exception ex){aviso("No se pudo abrir el registro de aprendizajes.");}
    }
    void confirmarBorradoArchivados(){
        int cantidad=Memoria.hilos(this,true).size();
        if(cantidad==0){aviso("No hay conversaciones archivadas para borrar.");return;}
        new AlertDialog.Builder(this)
            .setTitle("¿Borrar todas las conversaciones archivadas?")
            .setMessage("Se eliminará el historial de "+cantidad+" conversaciones archivadas de Empleado IA. " +
                "Las respuestas aprobadas pasarán a la memoria de aprendizaje permanente; " +
                "no se borrarán los procesos, reglas ni conocimientos. " +
                "Esto no elimina los mensajes originales de WhatsApp Business.")
            .setNegativeButton("Cancelar",null)
            .setPositiveButton("Borrar archivados",(d,w)->{
                try{
                    int borrados=Memoria.borrarArchivados(this);
                    mostrarBandeja(true);
                    aviso("Se borraron "+borrados+" conversaciones. Aprendizajes conservados.");
                }catch(Exception ex){aviso("No se borró el historial: "+ex.getMessage());}
            }).show();
    }
    void tarjetaHilo(Memoria.Hilo h,boolean archivados){
        LinearLayout c=card();c.setElevation(dp(1));
        LinearLayout linea=linear(false);linea.setGravity(Gravity.CENTER_VERTICAL);
        TextView avatar=texto(h.nombre.isEmpty()?"?":h.nombre.substring(0,1).toUpperCase(Locale.ROOT),17,GREEN,true);
        avatar.setGravity(Gravity.CENTER);avatar.setBackground(fondo(Color.rgb(227,245,239),40,0));linea.addView(avatar,lp(46,46));
        LinearLayout cols=linear(true);cols.addView(texto(h.nombre,16,NAVY,true));
        TextView m=texto(h.ultimo,13,MUTED,false);m.setMaxLines(2);m.setEllipsize(android.text.TextUtils.TruncateAt.END);
        cols.addView(m,margen(-1,-2,0,4,0,0));linea.addView(cols,new LinearLayout.LayoutParams(0,-2,1));
        TextView hora=texto(new SimpleDateFormat("HH:mm",new Locale("es","AR")).format(new Date(h.recibido)),11,MUTED,false);
        linea.addView(hora);c.addView(linea);
        c.setOnClickListener(v->abrirHilo(h));
        c.setOnTouchListener(new View.OnTouchListener(){float x,y;boolean swiped;
            @Override public boolean onTouch(View v,android.view.MotionEvent e){
                switch(e.getActionMasked()){
                case MotionEvent.ACTION_DOWN:x=e.getX();y=e.getY();swiped=false;return true;
                case MotionEvent.ACTION_UP:
                    float dx=e.getX()-x,dy=e.getY()-y;
                    if(Math.abs(dx)>dp(65)&&Math.abs(dx)>Math.abs(dy)*1.5f){
                        swiped=true;
                        if(!archivados&&dx<0){Memoria.archivar(Panel.this,h.id);mostrarBandeja(false);}
                        else if(archivados&&dx>0){Memoria.reabrir(Panel.this,h.id);mostrarBandeja(true);}
                        return true;
                    }
                    if(Math.abs(dx)<dp(15)&&Math.abs(dy)<dp(15))v.performClick();
                    return true;
                case MotionEvent.ACTION_CANCEL:return true;
                }return true;
            }
        });
        Button accion=boton(archivados?"↩  Volver a pendientes":"✓  Archivar",NAVY,false,()->{
            if(archivados)Memoria.reabrir(Panel.this,h.id);else Memoria.archivar(Panel.this,h.id);
            mostrarBandeja(archivados);
        });
        c.addView(accion,margen(-1,40,0,10,0,0));
        container.addView(c,margen(-1,-2,0,0,0,9));
    }
    void abrirHilo(Memoria.Hilo h){
        hiloActual=h.id;clienteActual=h.nombre;consultaActual=h.ultimo;
        primerBorrador="";propuestaAI="";ultimaIndicacion="";historialCorrecciones="";
        marcaConsulta=h.recibido;
        procesoActual=Procesos.clasificar(consultaActual);
        mostrarConversacion();
    }
    /** Si llega una pregunta nueva durante el editor, abrir un borrador específico para ella. */
    boolean revisarNuevaConsulta(){
        if(hiloActual==null)return false;
        for(JSONObject e:Memoria.eventos(this,hiloActual)){
            if(!"recibido".equals(e.optString("tipo")))continue;
            long t=e.optLong("hora");
            if(t>marcaConsulta){
                marcaConsulta=t;consultaActual=e.optString("mensaje");
                clienteActual=e.optString("nombre",clienteActual);
                procesoActual=Procesos.clasificar(consultaActual);
                primerBorrador="";propuestaAI="";ultimaIndicacion="";historialCorrecciones="";
                ultimaSolicitud++;
                mostrarConversacion();
                aviso("Llegó una consulta nueva. Editor preparado para esta pregunta.");
                return true;
            }
        }
        return false;
    }
    String claveBorrador(){return "texto.v4."+hash(hiloActual+"\n"+marcaConsulta);}
    void mostrarConversacion(){
        detenerLectura();
        botonLectura=null;
        empezar("‹  "+clienteActual+"  ·  Conversación disponible");
        LinearLayout acciones=linear(false);acciones.setGravity(Gravity.CENTER_VERTICAL);
        Button atras=boton("‹ Volver",NAVY,false,()->mostrarBandeja(false));
        acciones.addView(atras,new LinearLayout.LayoutParams(0,dp(43),1));
        boolean archivado=Memoria.archivado(this,hiloActual);
        Button archivar=boton(archivado?"↩ Desarchivar":"✓ Archivar",GREEN,false,()->{
            if(Memoria.archivado(this,hiloActual)){Memoria.reabrir(this,hiloActual);mostrarBandeja(false);}
            else {Memoria.archivar(this,hiloActual);mostrarBandeja(false);}
        });
        acciones.addView(archivar,margen(145,43,8,0,0,0));
        container.addView(acciones,margen(-1,43,0,0,0,12));
        LinearLayout historial=card();
        historial.addView(texto("Conversación registrada",16,NAVY,true),margen(-1,-2,0,0,0,11));
        List<JSONObject> eventos=Memoria.eventos(this,hiloActual);
        int start=0;
        for(int i=start;i<eventos.size();i++){
            JSONObject e=eventos.get(i);boolean esMia="aprobado".equals(e.optString("tipo"));
            TextView cab=texto(esMia?"piOca® · enviada":"Cliente",11,esMia?GREEN:MUTED,true);
            TextView msg=texto(e.optString("mensaje"),14,NAVY,false);
            LinearLayout burbuja=linear(true);burbuja.setPadding(dp(12),dp(8),dp(12),dp(9));
            burbuja.setBackground(fondo(esMia?Color.rgb(227,245,239):Color.rgb(242,245,247),12,0));
            burbuja.addView(cab);burbuja.addView(msg,margen(-1,-2,0,4,0,0));
            LinearLayout wrapper=linear(false);wrapper.setGravity(esMia?Gravity.RIGHT:Gravity.LEFT);
            wrapper.addView(burbuja,new LinearLayout.LayoutParams(dp(300),-2));
            historial.addView(wrapper,margen(-1,-2,0,0,0,8));
        }
        container.addView(historial,margen(-1,-2,0,0,0,14));
        List<Procesos.Aprendida> aprobadas=Procesos.ejemplos(this,consultaActual,procesoActual,4);
        if(!aprobadas.isEmpty()){
            LinearLayout aprendidas=card();
            aprendidas.setBackground(fondo(Color.rgb(239,248,246),16,0));
            aprendidas.addView(texto("✦  Memoria contextual: "+aprobadas.size()+" respuestas aprobadas",13,GREEN,true));
            aprendidas.addView(texto("La IA usará ejemplos pertinentes de "+procesoActual+" para redactar; no copiará saludos aislados ni datos particulares.",12,MUTED,false),margen(-1,-2,0,5,0,0));
            container.addView(aprendidas,margen(-1,-2,0,0,0,12));
        }
        LinearLayout indicaciones=card();
        indicaciones.addView(texto("Tu indicación",16,NAVY,true));
        indicaciones.addView(texto("Dictá o escribí qué querés responder. La IA transformará tus requisitos en un mensaje profesional, dividido en párrafos.",12,MUTED,false),margen(-1,-2,0,5,0,8));
        LinearLayout fila=linear(false);fila.setGravity(Gravity.BOTTOM);
        indicacion=new EditText(this);indicacion.setTextSize(15);indicacion.setTextColor(NAVY);
        indicacion.setBackground(fondo(BG,11,BORDER));indicacion.setPadding(dp(11),dp(10),dp(11),dp(10));
        indicacion.setHint("Dictá tu respuesta o escribila...");indicacion.setMinLines(3);indicacion.setMaxLines(7);
        fila.addView(indicacion,new LinearLayout.LayoutParams(0,-2,1));
        Button voz=boton("🎙",GREEN,true,()->dictar(VOZ));voz.setContentDescription("Dictar indicación por voz");
        fila.addView(voz,margen(54,56,7,0,0,0));indicaciones.addView(fila);
        Button mejorar=boton("✦  Redactar profesionalmente",GREEN,true,()->generar("redactar"));
        indicaciones.addView(mejorar,margen(-1,48,0,12,0,0));
        container.addView(indicaciones,margen(-1,-2,0,0,0,14));
        LinearLayout editor=card();editor.addView(texto("Respuesta para el cliente",16,NAVY,true));
        editor.addView(texto("Podés editar cada palabra. Al enviar, guardamos tu versión definitiva.",12,MUTED,false),margen(-1,-2,0,5,0,8));
        resultado=new EditText(this);resultado.setTextColor(NAVY);resultado.setTextSize(15);
        resultado.setBackground(fondo(BG,11,BORDER));resultado.setMinLines(5);resultado.setMaxLines(14);
        resultado.setGravity(Gravity.TOP);resultado.setPadding(dp(12),dp(12),dp(12),dp(12));
        resultado.setHint("Aquí aparecerá la redacción profesional...");
        String memoriaBorrador=getSharedPreferences("borradores",0).getString(claveBorrador(),"");
        if(!memoriaBorrador.isEmpty())resultado.setText(memoriaBorrador);
        editor.addView(resultado,lp(-1,-2));
        botonLectura=boton("▶ Escuchar respuesta",NAVY,false,this::escucharRespuesta);
        botonLectura.setContentDescription("Reproducir en voz alta la respuesta para el cliente");
        editor.addView(botonLectura,margen(-1,44,0,10,0,0));
        resultado.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count){
                getSharedPreferences("borradores",0).edit().putString(claveBorrador(),s.toString()).apply();
                if(lector!=null&&lector.isSpeaking())detenerLectura();
                actualizarEnvio();
            }
            public void afterTextChanged(Editable s){}
        });
        LinearLayout ajuste=card();
        ajuste.addView(texto("Corregir una respuesta ya redactada",16,NAVY,true));
        ajuste.addView(texto("Dictá qué querés cambiar. La IA conservará el resto y rearmará el mensaje completo.",12,MUTED,false),margen(-1,-2,0,5,0,8));
        LinearLayout filaCorreccion=linear(false);filaCorreccion.setGravity(Gravity.BOTTOM);
        correccion=new EditText(this);correccion.setTextSize(15);correccion.setTextColor(NAVY);
        correccion.setBackground(fondo(BG,11,BORDER));correccion.setPadding(dp(11),dp(10),dp(11),dp(10));
        correccion.setHint("Ej.: Sacá el último párrafo y aclará que necesitamos una foto del portón completo.");
        correccion.setMinLines(3);correccion.setMaxLines(6);
        filaCorreccion.addView(correccion,new LinearLayout.LayoutParams(0,-2,1));
        Button micCorreccion=boton("🎙",GREEN,true,()->dictar(VOZ_CORRECCION));
        micCorreccion.setContentDescription("Dictar correcciones al mensaje ya armado");
        filaCorreccion.addView(micCorreccion,margen(54,56,7,0,0,0));
        ajuste.addView(filaCorreccion);
        Button aplicar=boton("✦ Aplicar correcciones con IA",GREEN,true,()->generar("correccion"));
        ajuste.addView(aplicar,margen(-1,48,0,12,0,0));
        editor.addView(ajuste,margen(-1,-2,0,12,0,6));
        Button orto=boton("✓ Revalidar solo ortografía",NAVY,false,()->generar("ortografia"));
        editor.addView(orto,margen(-1,44,0,12,0,8));
        // La acción principal va inmediatamente debajo del mensaje y su corrección opcional.
        enviar=boton("ENVIAR WHATSAPP  ➤",GREEN,true,this::mandar);
        enviar.setTextSize(17);enviar.setContentDescription("Enviar al cliente por WhatsApp Business");
        editor.addView(enviar,lp(-1,58));
        estado=texto("",12,MUTED,false);editor.addView(estado,margen(-1,-2,0,8,0,0));
        botonProceso=boton("Proceso: "+procesoActual+"  ▾",NAVY,false,()->elegirProceso(false,null));
        editor.addView(botonProceso,margen(-1,43,0,11,0,0));
        container.addView(editor,lp(-1,-2));
        actualizarEnvio();
    }
    void actualizarEnvio(){
        if(enviar==null||resultado==null||estado==null||hiloActual==null)return;
        Bandeja.Ficha f=Bandeja.actual(hiloActual);
        boolean puede=f!=null&&f.puedeResponder();
        enviar.setEnabled(puede&&!resultado.getText().toString().trim().isEmpty());
        enviar.setAlpha(puede?1f:0.50f);
        estado.setText(puede?"Envío desde Empleado IA mediante la respuesta de la notificación de WhatsApp Business.":
          "La notificación actual no permite enviar directamente. Al tocar se abrirá WhatsApp con el texto copiado para pegar y enviar.");
        // No prometer envío directo si WhatsApp no suministra RemoteInput vigente.
        if(!puede){enviar.setText("ABRIR WHATSAPP PARA ENVIAR ↗");enviar.setEnabled(!resultado.getText().toString().trim().isEmpty());enviar.setAlpha(1f);}
        else enviar.setText("ENVIAR WHATSAPP  ➤");
    }
    void mandar(){
        String fin=resultado.getText().toString().trim();if(fin.isEmpty())return;
        Bandeja.Ficha f=Bandeja.actual(hiloActual);
        if(f==null||!f.puedeResponder()){
            ((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Respuesta piOca",fin));
            Intent abrir=getPackageManager().getLaunchIntentForPackage("com.whatsapp.w4b");
            if(abrir!=null)startActivity(abrir);
            aviso("Copiado. Envialo desde WhatsApp Business y archivá la conversación manualmente.");
            return;
        }
        try{
            detenerLectura();
            f.responder(this,fin); // Android acepta el PendingIntent; no es un recibo de entrega al destinatario.
            String clave=Memoria.aprobada(this,hiloActual,clienteActual,consultaActual,primerBorrador,propuestaAI,fin,procesoActual,(historialCorrecciones.isEmpty()?ultimaIndicacion:ultimaIndicacion+"\nCorrecciones: "+historialCorrecciones));
            Memoria.archivar(this,hiloActual);
            // Primero vaciar el editor. El TextWatcher ya guarda el valor vacío;
            // después borrar el borrador para evitar reaparecer en el siguiente turno.
            ultimaSolicitud++;
            resultado.setText("");
            if(indicacion!=null)indicacion.setText("");
            if(correccion!=null)correccion.setText("");
            primerBorrador="";propuestaAI="";ultimaIndicacion="";historialCorrecciones="";
            getSharedPreferences("borradores",0).edit().remove(claveBorrador()).remove("texto.v3."+hash(hiloActual)).apply();
            aviso("Orden de envío aceptada. Editor vacío; conversación archivada.");
            elegirProceso(true,clave);
        }catch(PendingIntent.CanceledException ex){
            aviso("La notificación venció. Conservamos el borrador; no se archivó.");actualizarEnvio();
        }catch(Exception ex){aviso("No se pudo enviar; conservamos tu borrador.");}
    }
    void elegirProceso(boolean luegoDeEnviar,String clave){
        List<String> nombres=Procesos.nombres(this);nombres.add("+ Crear un proceso nuevo");
        new AlertDialog.Builder(this).setTitle(luegoDeEnviar?"¿Guardar la respuesta en un proceso?":"Elegir proceso")
        .setItems(nombres.toArray(new String[0]),(d,n)->{
            if(n==nombres.size()-1){crearProceso(luegoDeEnviar,clave);return;}
            procesoActual=nombres.get(n);
            if(luegoDeEnviar){Memoria.procesoDeRespuesta(this,clave,procesoActual);mostrarBandeja(false);}
            else {if(botonProceso!=null)botonProceso.setText("Proceso: "+procesoActual+"  ▾");aviso("Proceso seleccionado: "+procesoActual);}
        }).setNegativeButton(luegoDeEnviar?"Ahora no":"Cerrar",(d,n)->{if(luegoDeEnviar)mostrarBandeja(false);}).show();
    }
    void crearProceso(boolean despues,String clave){
        EditText e=new EditText(this);e.setHint("Nombre del nuevo proceso");e.setSingleLine(true);
        new AlertDialog.Builder(this).setTitle("Nuevo proceso").setView(e)
          .setPositiveButton("Guardar",(d,n)->{
              String nom=e.getText().toString().trim();
              if(Procesos.agregar(this,nom)){
                  procesoActual=nom;
                  if(despues){Memoria.procesoDeRespuesta(this,clave,nom);mostrarBandeja(false);}
                  else {if(botonProceso!=null)botonProceso.setText("Proceso: "+procesoActual+"  ▾");aviso("Proceso creado: "+nom);}
              }else{
                  aviso("Nombre demasiado corto o ya existente.");
                  if(despues)mostrarBandeja(false);
              }
          }).setNegativeButton("Cancelar",(d,n)->{if(despues)mostrarBandeja(false);}).show();
    }
    void generar(String modo){
        if(indicacion==null||resultado==null||correccion==null)return;
        boolean ortografia="ortografia".equals(modo),editar="correccion".equals(modo);
        String texto=(ortografia?resultado:(editar?correccion:indicacion)).getText().toString().trim();
        String antes=resultado.getText().toString();
        if(texto.isEmpty()){aviso(ortografia?"Primero redactá la respuesta.":editar?"Dictá qué querés corregir del mensaje ya armado.":"Dictá qué querés responder al cliente.");return;}
        if(editar&&antes.trim().isEmpty()){aviso("Primero hay que armar la respuesta que querés corregir.");return;}
        SharedPreferences conf=getSharedPreferences("conexion",0);
        String url=conf.getString("url",""),token=conf.getString("token","");
        if(!url.startsWith("https://")||token.isEmpty()){aviso("Configurá primero la conexión con IA.");ajustes();return;}
        if(!editar&&!ortografia)primerBorrador=texto;
        // Ortografía no recibe el prompt creativo: así nunca se reescribe una edición manual.
        String instrucciones=(ortografia?Procesos.ORTO:Procesos.prompt(this)+(editar?"\n"+Procesos.CORRECCION:""))
            +"\n"+Procesos.instrucciones(this,procesoActual);
        JSONObject data=Memoria.objeto("consulta",consultaActual,"indicacion",texto,"proceso",procesoActual,
           "criterio",instrucciones,"borrador_actual",antes,"modo",modo);
        try{
            data.put("historial",Memoria.recientes(this,hiloActual));
            JSONArray ejemplos=new JSONArray();
            if(!ortografia)for(Procesos.Aprendida e:Procesos.ejemplos(this,consultaActual,procesoActual,5)){
                ejemplos.put(Memoria.objeto("consulta_original",e.consulta,"respuesta_aprobada",e.respuesta));
            }
            data.put("ejemplos",ejemplos);
        }catch(JSONException ignored){}
        int solicitud=++ultimaSolicitud;
        String hilo=hiloActual;long marca=marcaConsulta;
        estado.setText(ortografia?"Revalidando ortografía...":editar?"Aplicando tu corrección...":"Redactando respuesta profesional por párrafos...");
        new Thread(()->{
            HttpsURLConnection con=null;
            try{
                con=(HttpsURLConnection)new URL(url).openConnection();con.setConnectTimeout(12000);con.setReadTimeout(45000);
                con.setRequestMethod("POST");con.setRequestProperty("Authorization","Bearer "+token);
                con.setRequestProperty("Content-Type","application/json");con.setDoOutput(true);
                try(OutputStream os=con.getOutputStream()){os.write(data.toString().getBytes(StandardCharsets.UTF_8));}
                int status=con.getResponseCode();
                if(status!=200)throw new IOException("HTTP "+status);
                String generado;
                try(InputStream is=con.getInputStream()){
                    generado=new JSONObject(leer(is,128000)).getString("draft").trim();
                }
                if(generado.isEmpty())throw new IOException("Respuesta vacía");
                final String fin=ortografia?generado.replace("\r\n","\n"):
                    presentar(generado);
                runOnUiThread(()->{
                    if(isDestroyed()||solicitud!=ultimaSolicitud||!hilo.equals(hiloActual)||marca!=marcaConsulta||resultado==null)return;
                    if(!antes.equals(resultado.getText().toString())){aviso("Conservamos tu edición; no reemplazamos el texto mientras escribías.");return;}
                    // En una corrección explícita se pueden quitar detalles autorizados por el operador.
                    // Para la revalidación solo se permite modificar ortografía/gramática.
                    if(!editar&&!respetaTerminos(texto,fin)){
                        estado.setText("La IA cambió algún término técnico o cantidad. Se conservó el texto original.");
                        aviso("Revisá tu indicación: la IA no respetó términos o cantidades.");return;
                    }
                    if(!ortografia){propuestaAI=fin;ultimaIndicacion=texto;}
                    if(editar){historialCorrecciones+=(historialCorrecciones.isEmpty()?"":"\n")+texto;correccion.setText("");}
                    resultado.setText(fin);resultado.setSelection(fin.length());
                    estado.setText("Respuesta lista para revisar. Podés dictar otra corrección o enviar.");
                });
            }catch(Exception ex){runOnUiThread(()->{if(!isDestroyed()&&hilo.equals(hiloActual))aviso("No se pudo generar: "+ex.getMessage());});}
            finally{if(con!=null)con.disconnect();}
        }).start();
    }
    /** Refuerzo visual si el modelo devolvió una respuesta larga en un solo bloque. */
    static String presentar(String texto){
        String limpio=texto.trim().replace("\r\n","\n");
        if(limpio.contains("\n"))return limpio.replaceAll("[ \t]*\n+[ \t]*","\n\n");
        if(limpio.length()<135)return limpio;
        String[] frases=limpio.split("(?<=[.!?])\\s+(?=[A-ZÁÉÍÓÚÑ¿¡])");
        if(frases.length<3)return limpio;
        StringBuilder b=new StringBuilder();int desdeCorte=0;
        for(int i=0;i<frases.length;i++){
            String f=frases[i].trim();
            if(f.isEmpty())continue;
            if(b.length()>0){
                boolean saludoCorto=i==1 && desdeCorte<55;
                boolean cortar=saludoCorto||desdeCorte>=125;
                b.append(cortar?"\n\n":" ");
                if(cortar)desdeCorte=0;else desdeCorte++;
            }
            b.append(f);desdeCorte+=f.length();
        }
        return b.toString();
    }
    static String leer(InputStream is,int max)throws IOException{
        ByteArrayOutputStream o=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;
        while((n=is.read(b))!=-1){if(o.size()+n>max)throw new IOException("Respuesta demasiado grande");o.write(b,0,n);}
        return o.toString("UTF-8");
    }
    static String normalizado(String texto){
        return Normalizer.normalize(texto.toLowerCase(Locale.ROOT),Normalizer.Form.NFD)
             .replaceAll("\\p{M}","");
    }
    static boolean respetaTerminos(String original,String resultado){
        String a=normalizado(original),b=normalizado(resultado);
        String[] protegidos={"porton","motor","producto","control","central","remoto","foto","zona",
             "localidad","domicilio","direccion","instalacion","presupuesto","cotizacion","visita",
             "garantia","tecnico","levadizo","batiente","corredizo","rueda","guia","herreria","reparacion"};
        for(String palabra:protegidos){
            Pattern p=Pattern.compile("\\b"+palabra+"[a-z]*\\b");
            if(p.matcher(a).find()&&!p.matcher(b).find())return false;
        }
        Matcher m=Pattern.compile("(?<![\\p{L}\\p{N}])\\d+(?:[.,]\\d+)*(?![\\p{L}\\p{N}])").matcher(a);
        while(m.find())if(!b.contains(m.group()))return false;
        return true;
    }
    /** Grabación local continua: el silencio no finaliza el dictado. */
    void dictar(int tipo){
        if(grabador!=null){aviso("Ya estás grabando. Tocá Detener y transcribir.");return;}
        if(checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
            tipoPendiente=tipo;
            requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO},PERMISO_MIC);
            return;
        }
        iniciarDictadoContinuo(tipo);
    }
    @Override public void onRequestPermissionsResult(int codigo,String[] permisos,int[] resultados){
        super.onRequestPermissionsResult(codigo,permisos,resultados);
        if(codigo!=PERMISO_MIC)return;
        if(resultados.length>0&&resultados[0]==PackageManager.PERMISSION_GRANTED)
            iniciarDictadoContinuo(tipoPendiente);
        else aviso("Para dictar hay que permitir el micrófono en Android.");
    }
    void iniciarDictadoContinuo(int tipo){
        if(grabador!=null)return;
        File archivo=null;MediaRecorder mr=null;
        try{
            archivo=File.createTempFile("pioca-dictado-", ".m4a",getCacheDir());
            mr=new MediaRecorder();
            mr.setAudioSource(MediaRecorder.AudioSource.MIC);
            mr.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mr.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            mr.setAudioChannels(1);
            mr.setAudioSamplingRate(16000);
            mr.setAudioEncodingBitRate(48000);
            mr.setMaxFileSize(AUDIO_MAX_BYTES);
            mr.setOutputFile(archivo.getAbsolutePath());
            mr.prepare();mr.start();
            grabador=mr;grabacionActual=archivo;tipoGrabacion=tipo;grabacionFinalizando=false;
            // Mantener pantalla encendida durante el dictado, sin finalizar por pausas.
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            mostrarPanelGrabacion();
            mr.setOnInfoListener((rec,what,extra)->{
                if(what==MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED){
                    runOnUiThread(()->{
                        cerrarPanelGrabacion();
                        aviso("Se alcanzó el límite de audio. Vamos a transcribir lo grabado.");
                        finalizarDictado(true);
                    });
                }
            });
        }catch(Exception ex){
            if(mr!=null)try{mr.release();}catch(Exception ignored){}
            if(archivo!=null)archivo.delete();
            grabador=null;grabacionActual=null;
            cerrarPanelGrabacion();
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            aviso("No se pudo iniciar el micrófono: "+ex.getMessage());
        }
    }
    void finalizarDictado(boolean transcribir){
        if(grabador==null||grabacionFinalizando)return;
        grabacionFinalizando=true;
        File archivo=grabacionActual;
        int tipo=tipoGrabacion;
        try{
            grabador.stop();
        }catch(Exception ex){
            transcribir=false;
            aviso("El audio es demasiado breve o la grabación se interrumpió.");
        }finally{
            try{grabador.release();}catch(Exception ignored){}
            grabador=null;grabacionActual=null;
            cerrarPanelGrabacion();
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            grabacionFinalizando=false;
        }
        if(!transcribir){if(archivo!=null)archivo.delete();return;}
        if(archivo==null||archivo.length()<512){
            if(archivo!=null)archivo.delete();
            aviso("No se detectó una grabación válida.");return;
        }
        enviarAudioTranscripcion(archivo,tipo);
    }
    /** El teléfono no almacena OPENAI_API_KEY; el audio va autenticado a Cloudflare. */
    void enviarAudioTranscripcion(File archivo,int tipo){
        SharedPreferences conf=getSharedPreferences("conexion",0);
        String url=conf.getString("url","").trim(),token=conf.getString("token","");
        if(!url.startsWith("https://")||token.isEmpty()){
            archivo.delete();aviso("Configurá la conexión de OpenAI en Empleado IA.");return;
        }
        if(archivo.length()>AUDIO_MAX_BYTES){archivo.delete();aviso("El audio excede el tamaño admitido.");return;}
        final String hilo=hiloActual;
        final long marca=marcaConsulta;
        final EditText destino=tipo==VOZ?indicacion:correccion;
        if(destino==null){archivo.delete();return;}
        aviso("Grabación finalizada. Transcribiendo tu dictado...");
        new Thread(()->{
            HttpsURLConnection con=null;
            try{
                String endpoint=url.replaceAll("/+$","")+"/transcribir";
                con=(HttpsURLConnection)new URL(endpoint).openConnection();
                con.setConnectTimeout(12000);con.setReadTimeout(90000);
                con.setRequestMethod("POST");con.setDoOutput(true);
                con.setRequestProperty("Authorization","Bearer "+token);
                String boundary="pioca-"+UUID.randomUUID();
                con.setRequestProperty("Content-Type","multipart/form-data; boundary="+boundary);
                con.setChunkedStreamingMode(8192);
                try(OutputStream o=con.getOutputStream();InputStream audio=new FileInputStream(archivo)){
                    String inicio="--"+boundary+"\r\nContent-Disposition: form-data; name=\"audio\"; filename=\"dictado.m4a\"\r\nContent-Type: audio/mp4\r\n\r\n";
                    o.write(inicio.getBytes(StandardCharsets.UTF_8));
                    byte[] buffer=new byte[8192];int leidos;
                    while((leidos=audio.read(buffer))!=-1)o.write(buffer,0,leidos);
                    o.write(("\r\n--"+boundary+"--\r\n").getBytes(StandardCharsets.UTF_8));
                }
                int code=con.getResponseCode();
                if(code!=200)throw new IOException("HTTP "+code+". Verificá que actualizaste worker.mjs en Cloudflare.");
                String texto;
                try(InputStream is=con.getInputStream()){
                    texto=new JSONObject(leer(is,128000)).optString("text","").trim();
                }
                if(texto.isEmpty())throw new IOException("La transcripción volvió vacía.");
                runOnUiThread(()->{
                    if(isDestroyed()||hiloActual==null||!hiloActual.equals(hilo)||marca!=marcaConsulta
                        ||(tipo==VOZ?indicacion:correccion)!=destino)return;
                    String anterior=destino.getText().toString().trim();
                    String finalTexto=anterior.isEmpty()?texto:anterior+" "+texto;
                    destino.setText(finalTexto);
                    destino.setSelection(destino.length());
                    aviso("Dictado transcripto. Revisalo y pedile a la IA que redacte.");
                });
            }catch(Exception ex){
                runOnUiThread(()->{if(!isDestroyed())aviso("No se pudo transcribir: "+ex.getMessage());});
            }finally{
                if(con!=null)con.disconnect();
                archivo.delete(); // El audio no se conserva después del intento.
            }
        }).start();
    }
    /** El diseño de grabación no se crea hasta que el usuario toca el micrófono. */
    void mostrarPanelGrabacion(){
        View foco=getCurrentFocus();
        if(foco!=null){
            InputMethodManager teclado=(InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
            if(teclado!=null)teclado.hideSoftInputFromWindow(foco.getWindowToken(),0);
        }
        LinearLayout panel=linear(true);panel.setPadding(dp(22),dp(18),dp(22),dp(18));
        panel.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView titulo=texto("Grabación en curso",20,NAVY,true);
        titulo.setGravity(Gravity.CENTER);panel.addView(titulo,margen(-1,-2,0,0,0,9));
        TextView activo=texto("● MICRÓFONO ACTIVO",13,Color.rgb(211,55,65),true);
        activo.setGravity(Gravity.CENTER);panel.addView(activo,margen(-1,-2,0,0,0,10));
        contadorGrabacion=texto("00:00",35,NAVY,true);
        contadorGrabacion.setGravity(Gravity.CENTER);
        contadorGrabacion.setTypeface(Typeface.MONOSPACE,Typeface.BOLD);
        panel.addView(contadorGrabacion,margen(-1,-2,0,0,0,12));
        TextView simbolo=texto("🎙",52,Color.WHITE,true);simbolo.setGravity(Gravity.CENTER);
        simbolo.setBackground(fondo(Color.rgb(211,55,65),100,0));
        simbolo.setOnClickListener(v->terminarDesdePanel(true));
        panel.addView(simbolo,margen(98,98,0,0,0,12));
        TextView mensaje=texto("Podés hacer pausas largas. Tocá el micrófono rojo o Detener para transcribir.",13,MUTED,false);
        mensaje.setGravity(Gravity.CENTER);panel.addView(mensaje,margen(-1,-2,0,0,0,15));
        panel.addView(boton("■ DETENER Y TRANSCRIBIR",NAVY,true,()->terminarDesdePanel(true)),lp(-1,52));
        panel.addView(boton("Cancelar grabación",NAVY,false,()->terminarDesdePanel(false)),margen(-1,44,0,9,0,0));
        dialogoGrabacion=new AlertDialog.Builder(this).setView(panel).setCancelable(false).create();
        dialogoGrabacion.show();
        inicioGrabacion=SystemClock.elapsedRealtime();
        tareaReloj=new Runnable(){
            @Override public void run(){
                if(grabador==null||contadorGrabacion==null)return;
                long segundos=(SystemClock.elapsedRealtime()-inicioGrabacion)/1000;
                contadorGrabacion.setText(String.format(Locale.ROOT,"%02d:%02d",segundos/60,segundos%60));
                relojGrabacion.postDelayed(this,300);
            }
        };
        relojGrabacion.post(tareaReloj);
    }
    void detenerContador(){
        if(tareaReloj!=null){relojGrabacion.removeCallbacks(tareaReloj);tareaReloj=null;}
        contadorGrabacion=null;
    }
    void cerrarPanelGrabacion(){
        detenerContador();
        if(dialogoGrabacion!=null){AlertDialog anterior=dialogoGrabacion;dialogoGrabacion=null;anterior.dismiss();}
    }
    void terminarDesdePanel(boolean transcribir){cerrarPanelGrabacion();finalizarDictado(transcribir);}
    void elegirCarpetaProteccion(){
        new AlertDialog.Builder(this).setTitle("Proteger aprendizajes")
            .setMessage("Elegí una carpeta fuera de los datos de la aplicación, por ejemplo Documentos/piOca-IA. " +
                "Empleado IA guardará copias alternadas A y B cada vez que apruebes una respuesta. " +
                "Esa carpeta puede contener información de clientes: no la compartas públicamente. " +
                "Si Android borra los datos de la app, podrás restaurar los archivos desde aquí.")
            .setNegativeButton("Cancelar",null)
            .setPositiveButton("Elegir carpeta",(d,n)->{
                Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION |
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                startActivityForResult(i,60);
            }).show();
    }
    void elegirArchivoAprendizajes(){
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/json");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(i,61);
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(result!=RESULT_OK||data==null)return;
        if(request==60){
            try{
                Uri carpeta=data.getData();
                if(carpeta==null)throw new IOException("No se eligió ninguna carpeta.");
                getContentResolver().takePersistableUriPermission(carpeta,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                Resguardo.configurar(this,carpeta);
                mostrarBandeja(false);
                aviso("Carpeta configurada. Creando copias de aprendizaje A y B.");
            }catch(Exception ex){aviso("No se pudo activar la protección: "+ex.getMessage());}
        }else if(request==61){
            try(InputStream is=getContentResolver().openInputStream(data.getData())){
                if(is==null)throw new IOException("No se pudo leer el respaldo.");
                int nuevos=Memoria.restaurarSoloAprendizajes(this,leer(is,24*1024*1024));
                Resguardo.programar(this);
                mostrarBandeja(false);
                aviso("Aprendizajes restaurados: "+nuevos+" respuestas nuevas.");
            }catch(Exception ex){aviso("Error de restauración: "+ex.getMessage());}
        }else if(request==VOZ||request==VOZ_CORRECCION){
            ArrayList<String> frases=data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            EditText destino=request==VOZ?indicacion:correccion;
            if(frases!=null&&!frases.isEmpty()&&destino!=null){
                String prev=destino.getText().toString().trim();
                destino.setText(prev.isEmpty()?frases.get(0):prev+" "+frases.get(0));
                destino.setSelection(destino.length());
            }
        }else if(request==10||request==11){
            try{
                if(request==10){try(OutputStream os=getContentResolver().openOutputStream(data.getData(),"wt")){
                    os.write(Memoria.respaldo(this).getBytes(StandardCharsets.UTF_8));
                }aviso("Memoria exportada.");}
                else{try(InputStream is=getContentResolver().openInputStream(data.getData())){
                    Memoria.restaurar(this,leer(is,64*1024*1024));
                }aviso("Memoria restaurada.");mostrarBandeja(false);}
            }catch(Exception ex){aviso("No se pudo completar: "+ex.getMessage());}
        }
    }
    void ajustes(){
        String[] opciones={"Conexión con OpenAI","Acceso a notificaciones","Conocimiento piOca® por segmento","Procesos y reglas","Exportar memoria","Restaurar memoria","Protección automática de aprendizajes","Recuperar aprendizajes","Ver último cierre (diagnóstico)"};
        new AlertDialog.Builder(this).setTitle("Configuración").setItems(opciones,(d,n)->{
            if(n==0)conectar();else if(n==1)startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
            else if(n==2)conocimiento();else if(n==3)reglas();else if(n==4)respaldo(true);else if(n==5)respaldo(false);
            else if(n==6)elegirCarpetaProteccion();else if(n==7)elegirArchivoAprendizajes();else mostrarUltimoError();
        }).show();
    }
    void conectar(){
        SharedPreferences p=getSharedPreferences("conexion",0);
        LinearLayout campos=linear(true);campos.setPadding(dp(20),0,dp(20),0);
        EditText url=new EditText(this);url.setSingleLine();url.setHint("https://tu-worker.workers.dev");url.setText(p.getString("url",""));
        EditText token=new EditText(this);token.setSingleLine();token.setInputType(129);token.setHint("Token del teléfono");token.setText(p.getString("token",""));
        campos.addView(url);campos.addView(token);
        new AlertDialog.Builder(this).setTitle("Conexión IA").setView(campos).setMessage("La clave de OpenAI permanece en Cloudflare, no en la APK.")
            .setPositiveButton("Guardar",(d,n)->p.edit().putString("url",url.getText().toString().trim()).putString("token",token.getText().toString().trim()).apply())
            .setNegativeButton("Cerrar",null).show();
    }
    void conocimiento(){
        List<String> lista=Procesos.nombres(this);
        new AlertDialog.Builder(this).setTitle("Conocimiento empresarial por segmento")
        .setMessage("Indicaciones que usa la IA según la consulta actual. Editá solamente procedimientos confirmados.")
        .setItems(lista.toArray(new String[0]),(d,n)->{
            String categoria=lista.get(n);
            EditText edit=new EditText(this);edit.setMinLines(6);edit.setMaxLines(16);
            edit.setPadding(dp(20),dp(14),dp(20),dp(14));
            edit.setText(Procesos.conocimiento(this,categoria));
            new AlertDialog.Builder(this).setTitle("Conocimiento · "+categoria).setView(edit)
                .setPositiveButton("Guardar",(a,b)->{
                    Procesos.guardarConocimiento(this,categoria,edit.getText().toString().trim());
                    aviso("Conocimiento del segmento guardado.");
                }).setNegativeButton("Cancelar",null).show();
        }).setNegativeButton("Cerrar",null).show();
    }
    void reglas(){
        List<String> lista=Procesos.nombres(this);lista.add("+ Crear proceso");
        new AlertDialog.Builder(this).setTitle("Procesos y reglas").setItems(lista.toArray(new String[0]),(d,n)->{
            if(n==lista.size()-1){crearProceso(false,null);return;}
            String nombre=lista.get(n);EditText edit=new EditText(this);edit.setMinLines(3);
            edit.setText(Procesos.regla(this,nombre));
            new AlertDialog.Builder(this).setTitle("Reglas · "+nombre).setView(edit)
                .setPositiveButton("Guardar",(a,b)->{Procesos.guardarRegla(this,nombre,edit.getText().toString());aviso("Regla guardada");})
                .setNegativeButton("Cerrar",null).show();
        }).show();
    }
    void respaldo(boolean exportar){
        Intent i=new Intent(exportar?Intent.ACTION_CREATE_DOCUMENT:Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/json");
        if(exportar)i.putExtra(Intent.EXTRA_TITLE,"pioca-memoria-v5-"+System.currentTimeMillis()+".json");
        startActivityForResult(i,exportar?10:11);
    }
    static String hash(String s){try{
        return android.util.Base64.encodeToString(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)),android.util.Base64.NO_WRAP);
    }catch(Exception e){throw new IllegalStateException(e);}}
    @Override public void onBackPressed(){if(hiloActual!=null)mostrarBandeja(false);else super.onBackPressed();}
}
