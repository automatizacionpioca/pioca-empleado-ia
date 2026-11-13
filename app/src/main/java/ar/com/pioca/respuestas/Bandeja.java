package ar.com.pioca.respuestas;

import android.app.*;
import android.content.*;
import android.os.Bundle;
import android.service.notification.*;
import java.util.*;

public class Bandeja extends NotificationListenerService {
    static final Map<String,Ficha> activas = new LinkedHashMap<>();
    static Runnable cambio;
    static class Ficha {
        final String id, hilo, nombre, texto;
        final Notification.Action accion;
        final RemoteInput campo;
        Ficha(StatusBarNotification sb, String nombre, String texto, Notification.Action accion, RemoteInput campo) {
            // An updated notification keeps its draft only while the same conversation and message remain visible.
            this.id = sb.getKey() + "\n" + nombre + "\n" + sb.getPostTime();
            String shortcut = sb.getNotification().getShortcutId();
            this.hilo = Panel.hash(sb.getUser().toString() + "\n" + (shortcut == null || shortcut.isEmpty() ? sb.getKey() + "\n" + nombre : shortcut));
            this.nombre = nombre;
            this.texto = texto;
            this.accion = accion;
            this.campo = campo;
        }
        void responder(Context ctx, String texto) throws PendingIntent.CanceledException {
            Intent datos = new Intent();
            Bundle resultado = new Bundle();
            resultado.putCharSequence(campo.getResultKey(), texto);
            RemoteInput.addResultsToIntent(new RemoteInput[]{campo}, datos, resultado);
            accion.actionIntent.send(ctx, 0, datos);
        }
    }
    static void avisar() { if (cambio != null) cambio.run(); }
    @Override public void onListenerConnected() {
        activas.clear();
        StatusBarNotification[] disponibles = getActiveNotifications();
        if (disponibles != null) for (StatusBarNotification sb : disponibles) leer(sb);
        avisar();
    }
    @Override public void onListenerDisconnected() { activas.clear(); avisar(); }
    @Override public void onNotificationRemoved(StatusBarNotification sb) {
        if (activas.remove(sb.getKey()) != null) avisar();
    }
    @Override public void onNotificationPosted(StatusBarNotification sb) {
        if ("com.whatsapp.w4b".equals(sb.getPackageName())) { leer(sb); avisar(); }
    }
    private void leer(StatusBarNotification sb) {
        if (!"com.whatsapp.w4b".equals(sb.getPackageName())) return;
        activas.remove(sb.getKey());
        Notification n = sb.getNotification();
        if ((n.flags & Notification.FLAG_GROUP_SUMMARY) != 0 || n.extras == null) return;
        CharSequence nombre = n.extras.getCharSequence(Notification.EXTRA_TITLE);
        CharSequence mensaje = n.extras.getCharSequence(Notification.EXTRA_TEXT);
        if (nombre == null || mensaje == null || mensaje.length() == 0) return;
        Notification.Action accion = null;
        RemoteInput campo = null;
        if (n.actions != null) for (Notification.Action a : n.actions) {
            if (a == null || a.actionIntent == null || a.getRemoteInputs() == null) continue;
            for (RemoteInput ri : a.getRemoteInputs()) {
                if (ri.getAllowFreeFormInput()) { accion = a; campo = ri; break; }
            }
            if (campo != null) break;
        }
        Ficha ficha = new Ficha(sb, nombre.toString(), mensaje.toString(), accion, campo);
        activas.put(sb.getKey(), ficha);
        Memoria.entrada(this,ficha,sb.getPostTime());
    }
}
