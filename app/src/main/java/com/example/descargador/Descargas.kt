package com.example.descargador

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject

enum class Estado { EN_COLA, DESCARGANDO, COMPLETADA, ERROR }

class Item(
    val id: Long,
    val url: String,
    var titulo: String,
    val calidad: String, // "720", "mejor" o "audio"
    var estado: Estado = Estado.EN_COLA,
    var progreso: Int = 0,
    var uri: String? = null,
    var mime: String? = null,
    var error: String? = null
) {
    fun etiquetaCalidad(): String = when (calidad) {
        "mejor" -> "Mejor calidad"
        "audio" -> "Audio MP3"
        else -> "${calidad}p"
    }
}

/** Cola + historial compartidos entre la pantalla y el servicio de descarga. */
object Descargas {

    private val items = mutableListOf<Item>() // el más nuevo primero
    private var prefs: SharedPreferences? = null
    private val handler = Handler(Looper.getMainLooper())

    @Volatile
    var alCambiar: (() -> Unit)? = null

    fun iniciar(ctx: Context) {
        prefs = ctx.getSharedPreferences("descargas", Context.MODE_PRIVATE)
        cargar()
    }

    @Synchronized
    fun lista(): List<Item> = items.toList()

    @Synchronized
    fun agregar(item: Item) {
        items.add(0, item)
        persistir()
        avisar()
    }

    @Synchronized
    fun quitar(item: Item) {
        items.remove(item)
        persistir()
        avisar()
    }

    /** La descarga pendiente más antigua (la cola es first-in, first-out). */
    @Synchronized
    fun siguienteEnCola(): Item? = items.lastOrNull { it.estado == Estado.EN_COLA }

    @Synchronized
    fun guardarCambios() {
        persistir()
        avisar()
    }

    /** Avisa a la pantalla (en el hilo principal) de que hay que repintar. */
    fun avisar() {
        handler.post { alCambiar?.invoke() }
    }

    private fun persistir() {
        val arr = JSONArray()
        items.forEach {
            val o = JSONObject()
            o.put("id", it.id)
            o.put("url", it.url)
            o.put("titulo", it.titulo)
            o.put("calidad", it.calidad)
            o.put("estado", it.estado.name)
            o.put("uri", it.uri ?: "")
            o.put("mime", it.mime ?: "")
            o.put("error", it.error ?: "")
            arr.put(o)
        }
        prefs?.edit()?.putString("lista", arr.toString())?.apply()
    }

    private fun cargar() {
        val texto = prefs?.getString("lista", null) ?: return
        try {
            val arr = JSONArray(texto)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                var estado = Estado.valueOf(o.getString("estado"))
                var error: String? = o.optString("error", "").ifEmpty { null }
                // Lo que se quedó a medias en la sesión anterior se marca como interrumpido
                if (estado == Estado.EN_COLA || estado == Estado.DESCARGANDO) {
                    estado = Estado.ERROR
                    error = "Interrumpida"
                }
                items.add(
                    Item(
                        id = o.getLong("id"),
                        url = o.getString("url"),
                        titulo = o.getString("titulo"),
                        calidad = o.getString("calidad"),
                        estado = estado,
                        progreso = if (estado == Estado.COMPLETADA) 100 else 0,
                        uri = o.optString("uri", "").ifEmpty { null },
                        mime = o.optString("mime", "").ifEmpty { null },
                        error = error
                    )
                )
            }
        } catch (e: Exception) {
            items.clear()
        }
    }
}
