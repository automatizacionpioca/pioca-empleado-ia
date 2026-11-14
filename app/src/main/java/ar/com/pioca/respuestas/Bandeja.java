package ar.com.pioca.respuestas;

import android.app.*;
import android.content.*;
import android.os.Bundle;
import android.os.Build;
import android.service.notification.*;
import java.util.*;

/** Captura solamente WhatsApp Business; no consulta la base privada de WhatsApp. */
public class Bandeja extends NotificationListenerService {
    static final Map<String,Ficha> activas = new LinkedHashMap<>();
    static Runnable cambio;
    static volatile boolean conectado=false;
    static volatile long ultimoAviso=0;
    static volatile Bandeja instancia;
    static volatile long ultimaReconexion=0;

    static boolean accesoConcedido(Context ctx){
        ComponentName cn=new ComponentName(ctx,Bandeja.class);
        if(Build.VERSION.SDK_INT>=27){
            NotificationManager n=(NotificationManager)ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            return n!=null&&n.isNotificationListenerAccessGranted(cn);
        }
        String enabled=android.provider.Settings.Secure.getString(ctx.getContentResolver(),
            "enabled_notification_listeners");
        return enabled!=null&&enabled.contains(cn.flattenToString());
    }
    /** Recuperación al abrir la app; no depende de que la pantalla quede encendida. */
    static void recuperar(Context ctx){
        if(!accesoConcedido(ctx)){conectado=false;avisar();return;}
        Bandeja listener=instancia;
        if(listener!=null&&conectado){listener.recuperarActivas();return;}
        long ahora=System.currentTimeMillis();
        if(ahora-ultimaReconexion<15000)return;
        ultimaReconexion=ahora;
        try{requestRebind(new ComponentName(ctx,Bandeja.class));}
        catch(Exception ignored){}
    }
    void recuperarActivas(){
        if(!conectado)return;
        try{
            StatusBarNotification[] todas=getActiveNotifications();
            if(todas!=null)for(StatusBarNotification sb:todas)leer(sb);
            avisar();
        }catch(Exception ignored){}
    }

    static class Ficha {
        final String id,hilo,nombre,texto;
        final Notification.Action accion;
        final RemoteInput campo;
        Ficha(StatusBarNotification sb,String nombre,String texto,Notification.Action accion,RemoteInput campo){
            this.id=sb.getKey();
            String shortcut=sb.getNotification().getShortcutId();
            // El identificador ya no incluye postTime: sigue siendo el mismo al actualizar la notificación.
            this.hilo=Panel.hash(sb.getUser().toString()+"\n"+
                ((shortcut==null||shortcut.isEmpty())?sb.getKey()+"\n"+nombre:shortcut));
            this.nombre=nombre;this.texto=texto;this.accion=accion;this.campo=campo;
        }
        boolean puedeResponder(){return accion!=null && campo!=null && accion.actionIntent!=null;}
        void responder(Context ctx,String contenido) throws PendingIntent.CanceledException {
            if(!puedeResponder())throw new PendingIntent.CanceledException("Notificación sin respuesta directa");
            Intent datos=new Intent();
            Bundle r=new Bundle();r.putCharSequence(campo.getResultKey(),contenido);
            RemoteInput.addResultsToIntent(new RemoteInput[]{campo},datos,r);
            accion.actionIntent.send(ctx,0,datos);
        }
    }
    static Ficha actual(String hilo){
        synchronized(activas){
            Ficha ultima=null;
            for(Ficha f:activas.values())if(f.hilo.equals(hilo))ultima=f;
            return ultima;
        }
    }
    static void avisar(){Runnable r=cambio;if(r!=null)r.run();}
    @Override public void onListenerConnected(){
        instancia=this;conectado=true;
        synchronized(activas){activas.clear();}
        recuperarActivas();
    }
    @Override public void onListenerDisconnected(){
        conectado=false;
        instancia=null;
        synchronized(activas){activas.clear();}
        avisar();
        if(accesoConcedido(this)){
            try{requestRebind(new ComponentName(this,Bandeja.class));}
            catch(Exception ignored){}
        }
    }
    @Override public void onDestroy(){
        if(instancia==this)instancia=null;
        conectado=false;
        super.onDestroy();
    }
    @Override public void onNotificationRemoved(StatusBarNotification sb){
        synchronized(activas){activas.remove(sb.getKey());}
        avisar();
    }
    @Override public void onNotificationPosted(StatusBarNotification sb){
        if("com.whatsapp.w4b".equals(sb.getPackageName())){ultimoAviso=System.currentTimeMillis();leer(sb);avisar();}
    }
    private void leer(StatusBarNotification sb){
        if(!"com.whatsapp.w4b".equals(sb.getPackageName()))return;
        Notification n=sb.getNotification();
        synchronized(activas){activas.remove(sb.getKey());}
        if((n.flags & Notification.FLAG_GROUP_SUMMARY)!=0 || n.extras==null)return;
        CharSequence titulo=n.extras.getCharSequence(Notification.EXTRA_TITLE);
        CharSequence msg=n.extras.getCharSequence(Notification.EXTRA_TEXT);
        if(titulo==null)return;
        String nombre=titulo.toString();
        Notification.Action accion=null;RemoteInput campo=null;
        if(n.actions!=null)for(Notification.Action a:n.actions){
            if(a==null||a.actionIntent==null||a.getRemoteInputs()==null)continue;
            for(RemoteInput ri:a.getRemoteInputs())if(ri.getAllowFreeFormInput()){accion=a;campo=ri;break;}
            if(campo!=null)break;
        }
        String texto=msg==null?"":msg.toString();
        Ficha ficha=new Ficha(sb,nombre,texto,accion,campo);
        boolean capturoEstilo=false;
        try{
            android.os.Parcelable[] bundles=n.extras.getParcelableArray(Notification.EXTRA_MESSAGES);
            if(bundles!=null){
                List<Notification.MessagingStyle.Message> mensajes=Notification.MessagingStyle.Message.getMessagesFromBundleArray(bundles);
                if(mensajes!=null)for(Notification.MessagingStyle.Message m:mensajes){
                    CharSequence cuerpo=m.getText();
                    if(cuerpo==null||cuerpo.length()==0)continue;
                    capturoEstilo=true;
                    // MessagingStyle: sender nulo representa un mensaje propio, no del cliente.
                    // WhatsApp a veces reanexa el mensaje del operador a la notificación.
                    if(android.os.Build.VERSION.SDK_INT>=28){
                        if(m.getSenderPerson()==null)continue;
                    }else if(m.getSender()==null)continue;
                    Memoria.entrada(this,ficha,cuerpo.toString(),m.getTimestamp()>0?m.getTimestamp():sb.getPostTime());
                }
            }
        }catch(Exception ignored){}
        if(!capturoEstilo && !texto.trim().isEmpty())Memoria.entrada(this,ficha,texto,sb.getPostTime());
        synchronized(activas){activas.put(sb.getKey(),ficha);}
    }
}
