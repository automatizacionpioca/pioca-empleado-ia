package ar.com.pioca.respuestas;

import android.app.*;
import android.content.*;
import android.os.Bundle;
import android.service.notification.*;
import java.util.*;

/** Captura solamente WhatsApp Business; no consulta la base privada de WhatsApp. */
public class Bandeja extends NotificationListenerService {
    static final Map<String,Ficha> activas = new LinkedHashMap<>();
    static Runnable cambio;
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
        synchronized(activas){activas.clear();}
        StatusBarNotification[] all=getActiveNotifications();
        if(all!=null)for(StatusBarNotification sb:all)leer(sb);
        avisar();
    }
    @Override public void onListenerDisconnected(){synchronized(activas){activas.clear();}avisar();}
    @Override public void onNotificationRemoved(StatusBarNotification sb){
        synchronized(activas){activas.remove(sb.getKey());}
        avisar();
    }
    @Override public void onNotificationPosted(StatusBarNotification sb){
        if("com.whatsapp.w4b".equals(sb.getPackageName())){leer(sb);avisar();}
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
                    if(cuerpo!=null && cuerpo.length()>0){
                        // El timestamp del mensaje permite deduplicar actualizaciones de la notificación.
                        Memoria.entrada(this,ficha,cuerpo.toString(),m.getTimestamp());
                        capturoEstilo=true;
                    }
                }
            }
        }catch(Exception ignored){}
        if(!capturoEstilo && !texto.trim().isEmpty())Memoria.entrada(this,ficha,texto,sb.getPostTime());
        synchronized(activas){activas.put(sb.getKey(),ficha);}
    }
}
