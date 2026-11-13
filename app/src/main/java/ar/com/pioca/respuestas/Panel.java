package ar.com.pioca.respuestas;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.RecognizerIntent;
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

/** piOca® Empleado IA V3. Se conserva el applicationId de las versiones anteriores. */
public class Panel extends Activity {
    static final int NAVY=Color.rgb(20,33,51),GREEN=Color.rgb(11,117,90),BG=Color.rgb(246,248,250);
    static final int MUTED=Color.rgb(101,115,127),BORDER=Color.rgb(220,228,234);
    LinearLayout root,body,container;
    ScrollView scroll;
    EditText indicacion,resultado;
    TextView estado;
    Button enviar, botonProceso;
    String hiloActual,clienteActual,consultaActual,procesoActual="Presupuestos nuevos";
    String primerBorrador="",propuestaAI="",ultimaIndicacion="";
    boolean mostrandoArchivados=false;
    int ultimaSolicitud=0;
    final int VOZ=200;

    @Override public void onCreate(Bundle state){super.onCreate(state);mostrarBandeja(false);}
    @Override protected void onResume(){super.onResume();Bandeja.cambio=()->runOnUiThread(()->{
        if(hiloActual==null)mostrarBandeja(mostrandoArchivados);else actualizarEnvio();
    }); if(hiloActual!=null)actualizarEnvio();}
    @Override protected void onPause(){Bandeja.cambio=null;super.onPause();}

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
        marca.addView(texto("piOca®",15,NAVY,true));marca.addView(texto("Empleado IA",12,MUTED,false));
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
        hiloActual=null;indicacion=null;resultado=null;enviar=null;
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
        TextView encabezado=texto(archivados?"Conversaciones archivadas":"Mensajes por responder",21,NAVY,true);
        container.addView(encabezado,margen(-1,-2,2,20,2,5));
        TextView detalle=texto(archivados?"Podés recuperar cualquier conversación deslizando a la derecha.":
            "Deslizá una conversación hacia la izquierda para archivarla.",12,MUTED,false);
        container.addView(detalle,margen(-1,-2,2,0,2,18));
        List<Memoria.Hilo> lista=Memoria.hilos(this,archivados);
        if(lista.isEmpty()){
            LinearLayout c=card();c.addView(texto(archivados?"No hay conversaciones archivadas":"No hay mensajes pendientes",17,NAVY,true));
            c.addView(texto("Las conversaciones nuevas de WhatsApp Business aparecerán aquí cuando Android entregue su notificación.",13,MUTED,false),margen(-1,-2,0,8,0,0));
            container.addView(c,lp(-1,-2));return;
        }
        for(Memoria.Hilo h:lista)tarjetaHilo(h,archivados);
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
        container.addView(c,margen(-1,-2,0,0,0,9));
    }
    void abrirHilo(Memoria.Hilo h){
        hiloActual=h.id;clienteActual=h.nombre;consultaActual=h.ultimo;
        primerBorrador="";propuestaAI="";ultimaIndicacion="";procesoActual="Presupuestos nuevos";
        mostrarConversacion();
    }
    void mostrarConversacion(){
        empezar("‹  "+clienteActual+"  ·  Conversación disponible");
        Button atras=boton("‹  Volver a pendientes",NAVY,false,()->mostrarBandeja(false));
        container.addView(atras,margen(-1,43,0,0,0,12));
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
        Procesos.Aprendida aprendida=Procesos.sugerencia(this,consultaActual);
        if(aprendida!=null){
            LinearLayout c=card();c.setBackground(fondo(Color.rgb(239,248,246),16,0));
            c.addView(texto("✦  Hay una respuesta aprendida similar",14,GREEN,true));
            c.addView(texto(aprendida.respuesta,13,NAVY,false),margen(-1,-2,0,9,0,8));
            Button usar=boton("Usar como borrador",GREEN,true,()->{
                resultado.setText(aprendida.respuesta);propuestaAI=aprendida.respuesta;procesoActual=aprendida.proceso;
                ultimaIndicacion="Respuesta aprendida";estado.setText("Respuesta aprendida cargada. Podés corregirla.");
            });c.addView(usar,lp(-1,43));container.addView(c,margen(-1,-2,0,0,0,14));
        }
        LinearLayout indicaciones=card();
        indicaciones.addView(texto("Tu indicación",16,NAVY,true));
        indicaciones.addView(texto("Dictá o escribí qué querés responder. La IA solamente dará formato profesional.",12,MUTED,false),margen(-1,-2,0,5,0,8));
        LinearLayout fila=linear(false);fila.setGravity(Gravity.BOTTOM);
        indicacion=new EditText(this);indicacion.setTextSize(15);indicacion.setTextColor(NAVY);
        indicacion.setBackground(fondo(BG,11,BORDER));indicacion.setPadding(dp(11),dp(10),dp(11),dp(10));
        indicacion.setHint("Dictá tu respuesta o escribila...");indicacion.setMinLines(3);indicacion.setMaxLines(7);
        fila.addView(indicacion,new LinearLayout.LayoutParams(0,-2,1));
        Button voz=boton("🎙",GREEN,true,this::dictar);voz.setContentDescription("Dictar indicación por voz");
        fila.addView(voz,margen(54,56,7,0,0,0));indicaciones.addView(fila);
        Button mejorar=boton("✦  Redactar profesionalmente",GREEN,true,()->generar(false));
        indicaciones.addView(mejorar,margen(-1,48,0,12,0,0));
        container.addView(indicaciones,margen(-1,-2,0,0,0,14));
        LinearLayout editor=card();editor.addView(texto("Respuesta para el cliente",16,NAVY,true));
        editor.addView(texto("Podés editar cada palabra. Al enviar, guardamos tu versión definitiva.",12,MUTED,false),margen(-1,-2,0,5,0,8));
        resultado=new EditText(this);resultado.setTextColor(NAVY);resultado.setTextSize(15);
        resultado.setBackground(fondo(BG,11,BORDER));resultado.setMinLines(5);resultado.setMaxLines(14);
        resultado.setGravity(Gravity.TOP);resultado.setPadding(dp(12),dp(12),dp(12),dp(12));
        resultado.setHint("Aquí aparecerá la redacción profesional...");
        String memoriaBorrador=getSharedPreferences("borradores",0).getString("texto.v3."+hash(hiloActual),"");
        if(!memoriaBorrador.isEmpty())resultado.setText(memoriaBorrador);
        editor.addView(resultado,lp(-1,-2));
        resultado.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count){
                getSharedPreferences("borradores",0).edit().putString("texto.v3."+hash(hiloActual),s.toString()).apply();
                actualizarEnvio();
            }
            public void afterTextChanged(Editable s){}
        });
        Button orto=boton("✓  Revalidar ortografía",NAVY,false,()->generar(true));
        editor.addView(orto,margen(-1,44,0,12,0,8));
        // La acción principal va inmediatamente debajo del mensaje y su corrección opcional.
        enviar=boton("ENVIAR AL CLIENTE  ➤",GREEN,true,this::mandar);
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
        estado.setText(puede?"Respuesta directa disponible desde la notificación de WhatsApp Business.":
          "WhatsApp no ofrece respuesta directa en este momento. Podés copiar el texto, abrir WhatsApp y archivar manualmente.");
        // Fallback explícito sin prometer una entrega que no ocurrió.
        if(!puede){enviar.setText("COPIAR Y ABRIR WHATSAPP  ↗");enviar.setEnabled(!resultado.getText().toString().trim().isEmpty());enviar.setAlpha(1f);}
        else enviar.setText("ENVIAR AL CLIENTE  ➤");
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
            f.responder(this,fin); // Android acepta el PendingIntent; no es un recibo de entrega al destinatario.
            String clave=Memoria.aprobada(this,hiloActual,clienteActual,consultaActual,primerBorrador,propuestaAI,fin,procesoActual,ultimaIndicacion);
            Memoria.archivar(this,hiloActual);
            getSharedPreferences("borradores",0).edit().remove("texto.v3."+hash(hiloActual)).apply();
            aviso("Orden de envío aceptada por WhatsApp. Conversación archivada.");
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
    void generar(boolean soloOrtografia){
        if(indicacion==null||resultado==null)return;
        String texto=soloOrtografia?resultado.getText().toString().trim():indicacion.getText().toString().trim();
        if(texto.isEmpty()){aviso(soloOrtografia?"Primero redactá una respuesta.":"Primero escribí o dictá tu indicación.");return;}
        SharedPreferences conf=getSharedPreferences("conexion",0);
        String url=conf.getString("url","");String token=conf.getString("token","");
        if(!url.startsWith("https://")||token.isEmpty()){aviso("Configurá primero la conexión con IA.");ajustes();return;}
        if(!soloOrtografia)primerBorrador=texto;
        String antes=resultado.getText().toString();
        ultimaIndicacion=texto;
        String prompt=Procesos.PROMPT+"\n"+(soloOrtografia?Procesos.ORTO:"")+"\n"+Procesos.regla(this,procesoActual);
        JSONObject data=Memoria.objeto("consulta",consultaActual,"indicacion",texto,"proceso",procesoActual,
           "criterio",prompt,"borrador_actual",soloOrtografia?texto:antes,"modo",soloOrtografia?"ortografia":"estricto");
        try{data.put("historial",Memoria.recientes(this,hiloActual));
            JSONArray ejemplos=new JSONArray();
            for(JSONObject e:Memoria.eventos(this,null))if("aprobado".equals(e.optString("tipo"))&&e.optString("proceso").equals(procesoActual)){
                ejemplos.put(e.optString("mensaje"));if(ejemplos.length()>=12)break;
            }
            data.put("ejemplos",ejemplos);
        }catch(JSONException ignored){}
        int solicitud=++ultimaSolicitud;String hilo=hiloActual;
        estado.setText(soloOrtografia?"Revalidando ortografía...":"Redactando con IA...");
        new Thread(()->{
            HttpsURLConnection con=null;
            try{
                con=(HttpsURLConnection)new URL(url).openConnection();con.setConnectTimeout(12000);con.setReadTimeout(45000);
                con.setRequestMethod("POST");con.setRequestProperty("Authorization","Bearer "+token);
                con.setRequestProperty("Content-Type","application/json");con.setDoOutput(true);
                try(OutputStream os=con.getOutputStream()){os.write(data.toString().getBytes(StandardCharsets.UTF_8));}
                int status=con.getResponseCode();
                if(status!=200)throw new IOException("HTTP "+status);
                String generado;try(InputStream is=con.getInputStream()){
                    generado=new JSONObject(leer(is,128000)).getString("draft").trim();
                }
                if(generado.isEmpty())throw new IOException("Respuesta vacía");
                final String fin=generado;
                runOnUiThread(()->{
                    if(isDestroyed()||solicitud!=ultimaSolicitud||!hilo.equals(hiloActual)||resultado==null)return;
                    if(!antes.equals(resultado.getText().toString())){aviso("Conservamos tu edición: no se reemplazó el texto mientras lo escribías.");return;}
                    if(!respetaTerminos(texto,fin)){
                        estado.setText("Se descartó una sugerencia porque cambió palabras técnicas o cantidades. Conservamos tu texto.");
                        aviso("La sugerencia cambió términos de tu indicación. No la aplicamos.");
                        return;
                    }
                    if(!soloOrtografia)propuestaAI=fin;
                    resultado.setText(fin);resultado.setSelection(fin.length());
                    estado.setText("Revisá el contenido. Se guardará tu versión final cuando la envíes.");
                });
            }catch(Exception ex){runOnUiThread(()->{if(!isDestroyed()&&hilo.equals(hiloActual))aviso("No se pudo generar: "+ex.getMessage());});}
            finally{if(con!=null)con.disconnect();}
        }).start();
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
    void dictar(){
        Intent voz=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        voz.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        voz.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"es-AR");voz.putExtra(RecognizerIntent.EXTRA_PROMPT,"Dictá tu respuesta para el cliente");
        try{startActivityForResult(voz,VOZ);}catch(Exception ex){aviso("No hay servicio de dictado disponible en este teléfono.");}
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(result!=RESULT_OK||data==null)return;
        if(request==VOZ){
            ArrayList<String> frases=data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if(frases!=null&&!frases.isEmpty()&&indicacion!=null){
                String prev=indicacion.getText().toString().trim();
                indicacion.setText(prev.isEmpty()?frases.get(0):prev+" "+frases.get(0));
                indicacion.setSelection(indicacion.length());
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
        String[] opciones={"Conexión con OpenAI","Acceso a notificaciones","Procesos y reglas","Exportar memoria","Restaurar memoria"};
        new AlertDialog.Builder(this).setTitle("Configuración").setItems(opciones,(d,n)->{
            if(n==0)conectar();else if(n==1)startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
            else if(n==2)reglas();else if(n==3)respaldo(true);else respaldo(false);
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
        if(exportar)i.putExtra(Intent.EXTRA_TITLE,"pioca-memoria-v3-"+System.currentTimeMillis()+".json");
        startActivityForResult(i,exportar?10:11);
    }
    static String hash(String s){try{
        return android.util.Base64.encodeToString(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)),android.util.Base64.NO_WRAP);
    }catch(Exception e){throw new IllegalStateException(e);}}
    @Override public void onBackPressed(){if(hiloActual!=null)mostrarBandeja(false);else super.onBackPressed();}
}
