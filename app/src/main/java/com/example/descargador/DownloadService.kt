package com.example.descargador

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.IBinder
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.core.app.NotificationCompat
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File
import java.io.IOException

/**
 * Servicio en primer plano: descarga uno a uno los elementos de la cola
 * aunque la app esté cerrada, mostrando una notificación con el progreso.
 */
class DownloadService : Service() {

    companion object {
        private const val CANAL_PROGRESO = "progreso"
        private const val CANAL_FIN = "fin"
        private const val ID_PRIMER_PLANO = 1
    }

    private val candado = Any()
    private var trabajando = false
    private var ultimoStartId = 0
    private var ultimaNotificacion = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        crearCanales()
        startForeground(
            ID_PRIMER_PLANO,
            notificacionProgreso("Preparando descargas...", -1),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
        synchronized(candado) {
            ultimoStartId = startId
            if (!trabajando) {
                trabajando = true
                Thread { procesarCola() }.start()
            }
        }
        return START_NOT_STICKY
    }

    private fun procesarCola() {
        var idSalida = 0
        while (true) {
            var item: Item? = null
            synchronized(candado) {
                item = Descargas.siguienteEnCola()
                if (item == null) {
                    trabajando = false
                    idSalida = ultimoStartId
                }
            }
            val actual = item ?: break
            descargar(actual)
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf(idSalida)
    }

    private fun descargar(item: Item) {
        item.estado = Estado.DESCARGANDO
        item.progreso = 0
        Descargas.guardarCambios()
        notificar(ID_PRIMER_PLANO, notificacionProgreso(item.titulo, -1))

        // Carpeta temporal privada de la app
        val tmp = File(getExternalFilesDir(null) ?: filesDir, "tmp/${item.id}")
        tmp.deleteRecursively()
        tmp.mkdirs()

        try {
            val request = YoutubeDLRequest(item.url)
            request.addOption("--no-playlist")
            request.addOption("-o", "${tmp.absolutePath}/%(title).100s.%(ext)s")

            when (item.calidad) {
                "audio" -> {
                    request.addOption("-x")
                    request.addOption("--audio-format", "mp3")
                    request.addOption("--audio-quality", "0")
                }
                "mejor" -> {
                    request.addOption("-f", "bv*[ext=mp4]+ba[ext=m4a]/bv*+ba/b")
                    request.addOption("--merge-output-format", "mp4")
                }
                else -> {
                    val h = item.calidad
                    request.addOption(
                        "-f",
                        "bv*[height<=$h][ext=mp4]+ba[ext=m4a]/bv*[height<=$h]+ba/b[height<=$h]/b"
                    )
                    request.addOption("--merge-output-format", "mp4")
                }
            }

            YoutubeDL.getInstance().execute(request, item.id.toString()) { porcentaje, _, _ ->
                val p = porcentaje.toInt().coerceIn(0, 100)
                if (p != item.progreso) {
                    item.progreso = p
                    Descargas.avisar()
                    val ahora = System.currentTimeMillis()
                    if (ahora - ultimaNotificacion > 1000) {
                        ultimaNotificacion = ahora
                        notificar(ID_PRIMER_PLANO, notificacionProgreso(item.titulo, p))
                    }
                }
            }

            // El archivo final es el más grande de la carpeta temporal
            val archivo = tmp.listFiles()
                ?.filter { it.extension.lowercase() !in listOf("part", "ytdl", "temp", "json") }
                ?.maxByOrNull { it.length() }
                ?: throw IOException("No se encontró el archivo descargado")

            val (uri, mime) = guardarEnGaleria(archivo)
            item.uri = uri.toString()
            item.mime = mime
            item.progreso = 100
            item.estado = Estado.COMPLETADA
            Descargas.guardarCambios()
            notificarFin(item, true)
        } catch (e: Exception) {
            item.estado = Estado.ERROR
            item.error = (e.message ?: "Error desconocido").trim().lines().last().take(200)
            Descargas.guardarCambios()
            notificarFin(item, false)
        } finally {
            tmp.deleteRecursively()
        }
    }

    // Copia el archivo a Movies/Descargas (video) o Music/Descargas (audio). No requiere permisos.
    private fun guardarEnGaleria(archivo: File): Pair<Uri, String> {
        val ext = archivo.extension.lowercase()
        val esAudio = ext in listOf("mp3", "m4a", "aac", "opus", "ogg", "wav", "flac")
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
            ?: if (esAudio) "audio/mpeg" else "video/mp4"
        val coleccion =
            if (esAudio) MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            else MediaStore.Video.Media.EXTERNAL_CONTENT_URI

        val valores = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, archivo.name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                if (esAudio) "Music/Descargas" else "Movies/Descargas"
            )
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        val resolver = contentResolver
        val uri = resolver.insert(coleccion, valores)
            ?: throw IOException("No se pudo guardar el archivo")
        resolver.openOutputStream(uri)?.use { salida ->
            archivo.inputStream().use { entrada -> entrada.copyTo(salida) }
        }
        valores.clear()
        valores.put(MediaStore.MediaColumns.IS_PENDING, 0)
        resolver.update(uri, valores, null, null)
        return Pair(uri, mime)
    }

    // ---------- Notificaciones ----------

    private fun crearCanales() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CANAL_PROGRESO, "Descargas en curso", NotificationManager.IMPORTANCE_LOW)
        )
        nm.createNotificationChannel(
            NotificationChannel(CANAL_FIN, "Descargas terminadas", NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    private fun abrirApp(): PendingIntent = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun notificacionProgreso(texto: String, progreso: Int): Notification {
        val b = NotificationCompat.Builder(this, CANAL_PROGRESO)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Descargando")
            .setContentText(texto)
            .setContentIntent(abrirApp())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
        if (progreso >= 0) b.setProgress(100, progreso, false) else b.setProgress(0, 0, true)
        return b.build()
    }

    private fun notificarFin(item: Item, ok: Boolean) {
        val n = NotificationCompat.Builder(this, CANAL_FIN)
            .setSmallIcon(
                if (ok) android.R.drawable.stat_sys_download_done
                else android.R.drawable.stat_notify_error
            )
            .setContentTitle(if (ok) "Descarga completa" else "Error en la descarga")
            .setContentText(item.titulo)
            .setContentIntent(abrirApp())
            .setAutoCancel(true)
            .build()
        notificar(item.id.toInt(), n)
    }

    private fun notificar(id: Int, n: Notification) {
        getSystemService(NotificationManager::class.java).notify(id, n)
    }
}
