# piOca® Empleado IA
Empleado digital que aprende los procesos de piOca® a partir de tus indicaciones, ejemplos y correcciones. Prepara respuestas para que las revises y autorices su envío.

## Crear el proyecto y compilar
1. Creá un repositorio nuevo llamado pioca-empleado-ia en https://github.com/new. Recomendamos visibilidad privada. Podés inicializarlo con un README.
2. Descomprimí el ZIP. En la raíz del repositorio subí las carpetas app e ia, settings.gradle y la guía. Conservá las carpetas con sus contenidos.
3. Subí también .github/workflows/compilar-empleado-ia.yml en esa ruta exacta. Si tu explorador oculta .github, descargá el YAML entregado por separado y usá Add file → Create new file en GitHub. Escribí .github/workflows/compilar-empleado-ia.yml como nombre y pegá el contenido. Confirmá con Commit changes.
4. En Actions abrí Compilar piOca Empleado IA, esperá el resultado verde y descargá el artefacto piOca-Empleado-IA. Descomprimilo e instalá la APK en el Android con WhatsApp Business. Si preferís compilar a mano, usá Run workflow. Para Android Studio usá JDK 17, SDK 35 y Gradle 8.9.
## Primera prueba, sin configurar IA
1. Abrí la app y tocá Acceso a notificaciones. Habilitá piOca® Empleado IA. Algunos teléfonos piden habilitar ajustes restringidos desde la información de la aplicación instalada.
2. Con WhatsApp Business en segundo plano, enviá mensajes de prueba desde dos números. Tocá uno, escribí o dictá desde el teclado y elegí OK y enviar. Comprobá la recepción en el otro número y repetí con el segundo.
3. En Historial verificá el mensaje y la respuesta aprobada. Cerrá y abrí la app. Probá también retirar una notificación mientras editás: se deshabilita el envío y podés copiar el borrador.
4. En Procesos y ejemplos editá un criterio. Dentro de una charla, Enseñar una regla agrega una indicación al proceso. Guardar como ejemplo permite editar una respuesta para sacar nombres, montos o excepciones antes de reutilizarla en otras charlas.
## Activar sugerencias de IA
1. En tu cuenta Cloudflare creá un Worker NUEVO para esta aplicación. En el editor reemplazá el código inicial por ia/worker.mjs y desplegalo. Copiá su URL HTTPS. No uses el Worker de seguimiento.
2. En Settings → Variables and Secrets del Worker agregá dos secretos: OPENAI_API_KEY (clave de un proyecto de OpenAI API con crédito/facturación habilitada) y APP_TOKEN (valor aleatorio privado de al menos 32 caracteres). Guardá y desplegá los cambios. Opcionalmente OPENAI_MODEL permite configurar otro modelo compatible con Responses. El valor inicial es gpt-4.1-mini. El servicio utiliza la facturación de la API.
3. En la app tocá Conectar IA. Pegá la URL y el valor de APP_TOKEN. La clave de OpenAI queda en el Worker. Mantené los secretos fuera del repositorio.
4. Abrí una notificación de prueba, tocá Sugerir o mejorar con IA, elegí el proceso y dictá una instrucción si querés. Revisá o corregí el borrador y tocá OK y enviar. Verificá en Historial la sugerencia original y el resultado final cuando difieren. La conexión se usa únicamente cuando pedís una sugerencia.
5. Probá enseñándole al proceso una regla y pedile otra sugerencia en una consulta parecida. Cada pedido incluye el criterio actual, hasta 16 ejemplos de ese proceso y hasta 12 eventos recientes de la conversación. Las correcciones del mismo hilo quedan como contexto y vos elegís qué reglas o ejemplos se generalizan a otros clientes.
## Memoria y respaldo
Exportar memoria guarda un JSON con conversaciones, propuestas, respuestas, criterios, ejemplos y borradores. Guardalo en una ubicación privada fuera del teléfono para recuperarlo después. Restaurar memoria incorpora el archivo y actualiza registros con la misma clave. Configurá otra vez la conexión de IA en el teléfono nuevo. No hay sincronización automática entre teléfonos. La app registra lo que le muestra Android, puede perder turnos y no importa chats antiguos. En algunos dispositivos una conversación puede dividirse si cambia el identificador de la notificación. La acción de envío utiliza exclusivamente la notificación vigente. El registro de aprobación no es un comprobante de entrega.
## Actualizaciones conservando los datos
El paquete es ar.com.pioca.respuestas y es independiente de Seguimiento. Para actualizaciones Android exige la misma firma. Si ya instalaste V1 y Android rechaza la nueva firma, conservá antes los ejemplos de V1 que necesites; V1 no tenía exportación. En V2 exportá la memoria antes de desinstalar o cambiar de equipo.
Para mantener una firma de prueba estable entre compilaciones, antes de la primera instalación guardá una clave debug propia como secreto de GitHub Actions PIOCA_RESPUESTAS_DEBUG_KEYSTORE_B64. Desde una terminal con JDK:
```sh
keytool -genkeypair -keystore pioca-debug.keystore -storepass android -keypass android -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=piOca Respuestas"
openssl base64 -A -in pioca-debug.keystore -out firma-base64.txt
```
Copiá el contenido de firma-base64.txt al secreto. Guardá ambos archivos en privado. Sin ese secreto el workflow compila igual con una firma debug generada por el runner. No se puede garantizar que la siguiente ejecución conserve esa firma.
