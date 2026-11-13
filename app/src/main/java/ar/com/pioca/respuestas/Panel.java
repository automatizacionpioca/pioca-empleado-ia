package ar.com.pioca.respuestas;

import android.app.*;
import android.content.*;
import android.os.Bundle;
import android.provider.Settings;
import android.text.*;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

public class Panel extends Activity {
    static final String[] procesos = {"Cotización de controles","Seguimiento del servicio","Coordinación de visita","Otro proceso"};
    static final String[] criterios = {"Pedir foto del motor y localidad para poder cotizar el control.","Primero consultar cómo quedó el servicio. Esperar la respuesta antes de pedir una reseña.","Después del presupuesto, pedir la dirección exacta para coordinar la visita.","Usar el tono de piOca® y respetar lo acordado con el cliente."};
    LinearLayout hoja;
    EditText respuesta;
    Button enviar;
    Bandeja.Ficha seleccion;
    SharedPreferences memoria;
    String claveBorrador;
    int ultimaSolicitud;
    @Override public void onCreate(Bundle estado) {
        super.onCreate(estado);
        memoria = getSharedPreferences("procesos", MODE_PRIVATE);
        if (estado != null && estado.containsKey("id")) {
            for (Bandeja.Ficha f : Bandeja.activas.values()) if (f.id.equals(estado.getString("id"))) seleccion = f;
        }
        dibujar();
    }
    @Override protected void onSaveInstanceState(Bundle b) {
        super.onSaveInstanceState(b);
        if (seleccion != null) b.putString("id", seleccion.id);
    }
    @Override protected void onResume() { super.onResume(); Bandeja.cambio = this::actualizar; actualizar(); }
    @Override protected void onPause() { Bandeja.cambio = null; super.onPause(); }
    int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    void texto(String s, int tam) {
        TextView t = new TextView(this); t.setText(s); t.setTextSize(tam); t.setPadding(0,dp(6),0,dp(6)); hoja.addView(t);
    }
    Button boton(String s, Runnable r) {
        Button b = new Button(this); b.setAllCaps(false); b.setText(s); b.setOnClickListener(v -> r.run()); hoja.addView(b); return b;
    }
    void aviso(String s) { Toast.makeText(this,s,Toast.LENGTH_LONG).show(); }
    void actualizar() {
        if (seleccion == null) { dibujar(); return; }
        enviar.setEnabled(vigente() != null && !respuesta.getText().toString().trim().isEmpty());
    }
    Bandeja.Ficha vigente() {
        for (Bandeja.Ficha f : Bandeja.activas.values())
            if (f.id.equals(seleccion.id) && f.texto.equals(seleccion.texto) && f.campo != null) return f;
        return null;
    }
    void dibujar() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFitsSystemWindows(true);
        hoja = new LinearLayout(this); hoja.setOrientation(1); hoja.setPadding(dp(18),dp(20),dp(18),dp(24));
        scroll.addView(hoja); setContentView(scroll);
        if (seleccion == null) {
            texto("piOca® Empleado IA",26);
            boton("Acceso a notificaciones",()->startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
            boton("Procesos y ejemplos",()->elegir(false));
            boton("Historial",this::historial);
            boton("Conectar IA",this::configurar);
            boton("Exportar memoria",()->respaldo(true));
            boton("Restaurar memoria",()->respaldo(false));
            texto("Mensajes disponibles",21);
            if (Bandeja.activas.isEmpty()) texto("Esperando una notificación de WhatsApp Business. Para probar, enviá un mensaje desde otro número.",16);
            List<Bandeja.Ficha> fichas = new ArrayList<>(Bandeja.activas.values());
            Collections.reverse(fichas);
            for (Bandeja.Ficha f : fichas) boton(f.nombre+"\n"+f.texto,()->{seleccion=f; dibujar();});
            for (String k : getSharedPreferences("borradores",0).getAll().keySet()) {
                if (!k.startsWith("texto.")) continue;
                String id = k.substring(6);
                if (fichas.stream().anyMatch(f -> hash(f.id).equals(id))) continue;
                SharedPreferences d = getSharedPreferences("borradores",0);
                boton("Borrador · "+d.getString("nombre."+id,""),()->new AlertDialog.Builder(this)
                    .setTitle("Borrador guardado").setMessage(d.getString(k,""))
                    .setPositiveButton("Copiar",(a,b)->copiar(d.getString(k,"")))
                    .setNegativeButton("Cerrar",null).setNeutralButton("Eliminar",(a,b)->{borrar(id);dibujar();}).show());
            }
            return;
        }
        boton("‹ Bandeja",this::volver);
        texto(seleccion.nombre,24); texto(seleccion.texto,18);
        claveBorrador = hash(seleccion.id);
        SharedPreferences d = getSharedPreferences("borradores",0);
        respuesta = new EditText(this); respuesta.setHint("Escribí o usá el micrófono del teclado"); respuesta.setMinLines(3);
        respuesta.setText(d.getString("texto."+claveBorrador,"")); hoja.addView(respuesta);
        boton("Sugerir o mejorar con IA",this::sugerir);
        boton("Ver historial",()->verHilo(seleccion.hilo));
        boton("Enseñar una regla",()->{
            EditText e=new EditText(this);e.setHint("Ej.: si ya saludamos, continuar sin repetir el saludo.");
            new AlertDialog.Builder(this).setTitle("Regla para futuras respuestas").setView(e)
              .setPositiveButton("Elegir proceso",(a,b)->{
                String regla=e.getText().toString().trim(); if(regla.isEmpty())return;
                new AlertDialog.Builder(this).setTitle("Aplicar regla a").setItems(procesos,(x,i)->{
                    String anterior=memoria.getString("regla."+i,criterios[i]);
                    memoria.edit().putString("regla."+i,anterior+"\n"+regla).apply();aviso("Regla guardada");
                }).show();
              }).setNegativeButton("Cancelar",null).show();
        });
        boton("Consultar proceso o usar ejemplo",()->elegir(true));
        boton("Guardar como ejemplo de piOca®",()->{
            if (respuesta.getText().toString().trim().isEmpty()) { aviso("Primero escribí una respuesta."); return; }
            new AlertDialog.Builder(this).setTitle("Guardar en el proceso").setItems(procesos,(dlg,i)->ejemplo(i)).show();
        });
        enviar = boton("OK y enviar a "+seleccion.nombre,this::mandar);
        boton("Copiar respuesta",()->copiar(respuesta.getText().toString()));
        respuesta.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int a,int c,int f){}
            public void afterTextChanged(Editable e){}
            public void onTextChanged(CharSequence s,int a,int b,int c){
                if (s.length()==0) d.edit().remove("texto."+claveBorrador).apply();
                else d.edit().putString("nombre."+claveBorrador,seleccion.nombre).putString("texto."+claveBorrador,s.toString()).apply();
                actualizar();
            }
        });
        actualizar();
    }
    static String hash(String s) {
        try { return android.util.Base64.encodeToString(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)), android.util.Base64.NO_WRAP); }
        catch(java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    void borrar(String id) { getSharedPreferences("borradores",0).edit().remove("nombre."+id).remove("texto."+id).remove("p."+id).remove("i."+id).remove("categoria."+id).apply(); }
    void copiar(String s) { ((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Respuesta",s)); aviso("Copiado"); }
    void mandar() {
        Bandeja.Ficha f = vigente();
        if (f == null) { aviso("La notificación cambió. Conservamos tu borrador."); return; }
        try { String fin=respuesta.getText().toString().trim();
            if(fin.isEmpty())return;
            SharedPreferences d=getSharedPreferences("borradores",0);
            int categoria=Integer.parseInt(d.getString("categoria."+claveBorrador,"3"));
            f.responder(this,fin);
            Memoria.aprobada(this,f,d.getString("p."+claveBorrador,""),fin,categoria,d.getString("i."+claveBorrador,""));
            borrar(claveBorrador); aviso("Respuesta pasada a WhatsApp"); volver(); }
        catch (PendingIntent.CanceledException e) { aviso("La acción venció. Podés copiar el borrador."); }
    }
    void ejemplo(int i) {
        EditText campo=new EditText(this);
        campo.setText(respuesta.getText());
        new AlertDialog.Builder(this).setTitle("Editar ejemplo reutilizable").setMessage("Revisá nombres, precios y datos particulares antes de guardar.").setView(campo)
          .setPositiveButton("Guardar",(d,w)->{
            String s=campo.getText().toString().trim();
            if (!s.isEmpty()) memoria.edit().putString("ej."+i+"."+UUID.randomUUID(),s).apply();
          }).setNegativeButton("Cancelar",null).show();
    }
    void elegir(boolean editor) {
        new AlertDialog.Builder(this).setTitle("Procesos de piOca®").setItems(procesos,(d,i)->proceso(i,editor)).show();
    }
    void proceso(int i, boolean editor) {
        String regla=memoria.getString("regla."+i,criterios[i]);
        List<String> claves=new ArrayList<>();
        for(String k:memoria.getAll().keySet()) if(k.startsWith("ej."+i+".")) claves.add(k);
        String[] items=new String[claves.size()+1]; items[0]="Editar criterio · "+regla;
        for(int j=0;j<claves.size();j++) items[j+1]=memoria.getString(claves.get(j),"");
        new AlertDialog.Builder(this).setTitle(procesos[i]).setItems(items,(d,n)->{
            if(n==0){
                EditText e=new EditText(this);e.setText(regla);
                new AlertDialog.Builder(this).setTitle("Criterio del proceso").setView(e).setPositiveButton("Guardar",(a,b)->memoria.edit().putString("regla."+i,e.getText().toString()).apply()).setNegativeButton("Cancelar",null).show();
            } else {
                AlertDialog.Builder ab=new AlertDialog.Builder(this).setTitle(procesos[i]).setMessage(items[n]).setNegativeButton("Cerrar",null)
                  .setNeutralButton("Eliminar ejemplo",(a,b)->memoria.edit().remove(claves.get(n-1)).apply());
                if(editor) ab.setPositiveButton("Usar en el borrador",(a,b)->{procedencia(i,"Ejemplo reutilizable",items[n]);respuesta.setText(items[n]);});
                ab.show();
            }
        }).show();
    }
    void historial() {
        List<org.json.JSONObject> lista=Memoria.eventos(this,null);
        LinkedHashMap<String,String> hilos=new LinkedHashMap<>();
        Collections.reverse(lista);
        for(org.json.JSONObject j:lista) hilos.putIfAbsent(j.optString("hilo"),j.optString("nombre"));
        if(hilos.isEmpty()){ aviso("Todavía no hay mensajes registrados."); return; }
        List<String> ids=new ArrayList<>(hilos.keySet());
        new AlertDialog.Builder(this).setTitle("Conversaciones registradas").setItems(hilos.values().toArray(new String[0]),(d,i)->verHilo(ids.get(i))).show();
    }
    void verHilo(String id){
        StringBuilder s=new StringBuilder();
        for(org.json.JSONObject e:Memoria.eventos(this,id)){
            boolean aprobado=e.optString("tipo").equals("aprobado");
            s.append(aprobado?"piOca® · aprobado":"Cliente").append(" · ").append(android.text.format.DateFormat.format("dd/MM HH:mm",e.optLong("hora"))).append("\n");
            if(aprobado && !e.optString("propuesta").isEmpty() && !e.optString("propuesta").equals(e.optString("mensaje")))
                s.append("Sugerencia original: ").append(e.optString("propuesta")).append("\nRespuesta final: ");
            s.append(e.optString("mensaje")).append("\n\n");
        }
        new AlertDialog.Builder(this).setTitle("Historial").setMessage(s.toString()).setPositiveButton("Cerrar",null).show();
    }
    void configurar(){
        SharedPreferences cfg=getSharedPreferences("conexion",0);
        LinearLayout box=new LinearLayout(this);box.setOrientation(1);
        EditText url=new EditText(this);url.setSingleLine();url.setHint("https://tu-worker.workers.dev");url.setText(cfg.getString("url",""));box.addView(url);
        EditText token=new EditText(this);token.setInputType(129);token.setSingleLine();token.setHint("Token del teléfono");token.setText(cfg.getString("token",""));box.addView(token);
        new AlertDialog.Builder(this).setTitle("Conectar IA").setView(box).setMessage("Al pedir una sugerencia se envían la consulta, turnos recientes, criterios y ejemplos aprobados a tu Worker y al proveedor de IA.")
            .setPositiveButton("Guardar",(a,b)->cfg.edit().putString("url",url.getText().toString().trim()).putString("token",token.getText().toString().trim()).apply())
            .setNegativeButton("Cerrar",null).show();
    }
    void sugerir(){
        new AlertDialog.Builder(this).setTitle("Proceso para esta respuesta").setItems(procesos,(d,i)->{
            EditText e=new EditText(this);e.setHint("Opcional: dictá qué querés responder");
            new AlertDialog.Builder(this).setTitle(procesos[i]).setView(e).setPositiveButton("Sugerir",(a,b)->pedir(i,e.getText().toString())).setNegativeButton("Cancelar",null).show();
        }).show();
    }
    void pedir(int i,String indicacion){
        SharedPreferences cfg=getSharedPreferences("conexion",0);
        String url=cfg.getString("url",""),token=cfg.getString("token","");
        if(!url.startsWith("https://") || token.isEmpty()){configurar();return;}
        Bandeja.Ficha ficha=seleccion;
        EditText editorOriginal=respuesta;
        String anterior=respuesta.getText().toString();
        org.json.JSONObject j=Memoria.objeto("consulta",ficha.texto,"indicacion",indicacion,"proceso",procesos[i],
            "criterio",memoria.getString("regla."+i,criterios[i]),"borrador_actual",anterior);
        try{
            j.put("historial",Memoria.recientes(this,ficha.hilo));
            org.json.JSONArray ejemplos=new org.json.JSONArray();
            for(Map.Entry<String,?> e:memoria.getAll().entrySet())
                if(e.getKey().startsWith("ej."+i+".") && ejemplos.length()<16) ejemplos.put(e.getValue());
            j.put("ejemplos",ejemplos);
        }catch(org.json.JSONException e){aviso("Error al preparar el contexto");return;}
        int solicitud=++ultimaSolicitud;
        aviso("Preparando sugerencia…");
        new Thread(()->{
            javax.net.ssl.HttpsURLConnection con=null;
            try{
                con=(javax.net.ssl.HttpsURLConnection)new java.net.URL(url).openConnection();
                con.setInstanceFollowRedirects(false);con.setConnectTimeout(12000);con.setReadTimeout(45000);
                con.setRequestMethod("POST");con.setRequestProperty("Authorization","Bearer "+token);
                con.setRequestProperty("Content-Type","application/json");con.setDoOutput(true);
                try(java.io.OutputStream os=con.getOutputStream()){os.write(j.toString().getBytes(StandardCharsets.UTF_8));}
                int status=con.getResponseCode();
                if(status!=200)throw new java.io.IOException("HTTP "+status);
                String draft;
                try(java.io.InputStream in=con.getInputStream()){draft=new org.json.JSONObject(leer(in,64000)).getString("draft").trim();}
                if(draft.isEmpty())throw new java.io.IOException("Borrador vacío");
                runOnUiThread(()->{
                    if(isDestroyed() || seleccion!=ficha || respuesta!=editorOriginal || solicitud!=ultimaSolicitud)return;
                    if(!anterior.equals(respuesta.getText().toString())){aviso("Conservamos tu edición. Pedí otra sugerencia si la necesitás.");return;}
                    procedencia(i,indicacion,draft);respuesta.setText(draft);
                });
            }catch(Exception e){runOnUiThread(()->{if(!isDestroyed())aviso("No se pudo generar: "+e.getMessage());});}
            finally{if(con!=null)con.disconnect();}
        }).start();
    }
    void procedencia(int i,String instruccion,String propuesta){
        getSharedPreferences("borradores",0).edit().putString("p."+claveBorrador,propuesta)
            .putString("i."+claveBorrador,instruccion).putString("categoria."+claveBorrador,Integer.toString(i)).apply();
    }
    static String leer(java.io.InputStream in,int max) throws java.io.IOException {
        java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();
        byte[] buf=new byte[8192];int n;
        while((n=in.read(buf))!=-1){if(out.size()+n>max)throw new java.io.IOException("Archivo demasiado grande");out.write(buf,0,n);}
        return out.toString("UTF-8");
    }
    void respaldo(boolean exportar){
        Intent it=new Intent(exportar?Intent.ACTION_CREATE_DOCUMENT:Intent.ACTION_OPEN_DOCUMENT);
        it.addCategory(Intent.CATEGORY_OPENABLE);it.setType("application/json");
        if(exportar)it.putExtra(Intent.EXTRA_TITLE,"pioca-memoria-"+System.currentTimeMillis()+".json");
        startActivityForResult(it,exportar?10:11);
    }
    @Override protected void onActivityResult(int r,int code,Intent data){
        super.onActivityResult(r,code,data);
        if(code!=RESULT_OK || data==null || data.getData()==null)return;
        try{
            if(r==10){
                try(java.io.OutputStream os=getContentResolver().openOutputStream(data.getData(),"wt")){os.write(Memoria.respaldo(this).getBytes(StandardCharsets.UTF_8));}
                aviso("Memoria exportada");
            }else if(r==11){
                try(java.io.InputStream is=getContentResolver().openInputStream(data.getData())){Memoria.restaurar(this,leer(is,64*1024*1024));}
                aviso("Respaldo incorporado");dibujar();
            }
        }catch(Exception e){aviso("No se pudo completar: "+e.getMessage());}
    }
    void volver(){ seleccion=null; dibujar(); }
    @Override public void onBackPressed(){ if(seleccion!=null) volver(); else super.onBackPressed(); }
}
