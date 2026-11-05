package com.example.descargador

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import coil.load
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.yausername.youtubedl_android.YoutubeDL

class DescargarFragment : Fragment() {

    private lateinit var etUrl: EditText
    private lateinit var btnPegar: MaterialButton
    private lateinit var btnBuscar: MaterialButton
    private lateinit var panelCalidad: MaterialCardView
    private lateinit var ivPreviewThumb: ImageView
    private lateinit var tvPreviewDuracion: TextView
    private lateinit var tvTitulo: TextView
    private lateinit var cgCalidad: ChipGroup
    private lateinit var btnAnadir: MaterialButton

    private var urlActual = ""
    private var tituloActual = ""
    private var thumbnailActual: String? = null
    private var duracionActual: String? = null
    private var codigos = listOf<String>() // "720", "mejor", "audio"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_descargar, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        etUrl = view.findViewById(R.id.etUrl)
        btnPegar = view.findViewById(R.id.btnPegar)
        btnBuscar = view.findViewById(R.id.btnBuscar)
        panelCalidad = view.findViewById(R.id.panelCalidad)
        ivPreviewThumb = view.findViewById(R.id.ivPreviewThumb)
        tvPreviewDuracion = view.findViewById(R.id.tvPreviewDuracion)
        tvTitulo = view.findViewById(R.id.tvTitulo)
        cgCalidad = view.findViewById(R.id.cgCalidad)
        btnAnadir = view.findViewById(R.id.btnAnadir)

        btnPegar.setOnClickListener { pegarDelPortapapeles() }
        btnBuscar.setOnClickListener { buscarCalidades() }
        btnAnadir.setOnClickListener { anadirACola() }
    }

    fun setUrlTextAndSearch(url: String) {
        if (::etUrl.isInitialized) {
            etUrl.setText(url)
            buscarCalidades()
        }
    }

    private fun extraerEnlace(texto: String): String =
        Regex("https?://\\S+").find(texto)?.value ?: texto.trim()

    private fun pegarDelPortapapeles() {
        val ctx = context ?: return
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val texto = cm.primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString()
        if (texto.isNullOrBlank()) {
            Toast.makeText(ctx, "El portapapeles está vacío", Toast.LENGTH_SHORT).show()
        } else {
            etUrl.setText(extraerEnlace(texto))
        }
    }

    private fun formatDuracion(segundos: Int): String {
        if (segundos <= 0) return ""
        val h = segundos / 3600
        val m = (segundos % 3600) / 60
        val s = segundos % 60
        return if (h > 0) {
            String.format("%d:%02d:%02d", h, m, s)
        } else {
            String.format("%02d:%02d", m, s)
        }
    }

    private fun actualizarYtDlp() {
        val ctx = context ?: return
        val ajustes = ctx.getSharedPreferences("ajustes", Context.MODE_PRIVATE)
        val ultima = ajustes.getLong("ultimaActualizacion", 0L)
        if (System.currentTimeMillis() - ultima < 24L * 60 * 60 * 1000) return

        activity?.runOnUiThread { btnBuscar.text = "Actualizando yt-dlp..." }
        try {
            YoutubeDL.getInstance().updateYoutubeDL(ctx.applicationContext, YoutubeDL.UpdateChannel.STABLE)
            ajustes.edit().putLong("ultimaActualizacion", System.currentTimeMillis()).apply()
        } catch (e: Throwable) {
            Log.e("Descargador", "No se pudo actualizar yt-dlp", e)
        }
    }

    private fun buscarCalidades() {
        val enlace = extraerEnlace(etUrl.text.toString())
        if (enlace.isEmpty()) {
            Toast.makeText(context, "Pega primero un enlace", Toast.LENGTH_SHORT).show()
            return
        }
        urlActual = enlace
        panelCalidad.visibility = View.GONE
        btnBuscar.isEnabled = false
        btnBuscar.text = "Buscando..."

        Thread {
            try {
                actualizarYtDlp()
                val info = YoutubeDL.getInstance().getInfo(enlace)
                val alturas = (info.formats ?: emptyList())
                    .filter { it.height > 0 && it.vcodec != "none" }
                    .map { it.height }
                    .distinct()
                    .sortedDescending()

                val textos = alturas.map { "${it}p" } + listOf("Mejor calidad", "Solo audio (MP3)")
                val cods = alturas.map { it.toString() } + listOf("mejor", "audio")
                val titulo = info.title ?: enlace
                val thumb = info.thumbnail
                val durSec = info.duration
                val durFormatted = formatDuracion(durSec)

                activity?.runOnUiThread {
                    tituloActual = titulo
                    thumbnailActual = thumb
                    duracionActual = durFormatted
                    codigos = cods

                    tvTitulo.text = titulo

                    if (!thumb.isNullOrBlank()) {
                        ivPreviewThumb.load(thumb) {
                            placeholder(R.drawable.ic_video_placeholder)
                            error(R.drawable.ic_video_placeholder)
                            crossfade(true)
                        }
                    } else {
                        ivPreviewThumb.setImageResource(R.drawable.ic_video_placeholder)
                    }

                    if (!durFormatted.isNullOrBlank()) {
                        tvPreviewDuracion.text = durFormatted
                        tvPreviewDuracion.visibility = View.VISIBLE
                    } else {
                        tvPreviewDuracion.visibility = View.GONE
                    }

                    // Crear píldoras (Chips) para cada calidad
                    cgCalidad.removeAllViews()
                    for (i in textos.indices) {
                        val chip = Chip(requireContext()).apply {
                            text = textos[i]
                            isCheckable = true
                            id = View.generateViewId()
                            setChipBackgroundColorResource(R.color.blue_container_light)
                            setTextColor(ContextCompat.getColor(context, R.color.blue_primary))
                        }
                        cgCalidad.addView(chip)
                        if (i == 0) {
                            cgCalidad.check(chip.id)
                        }
                    }

                    panelCalidad.visibility = View.VISIBLE
                    btnBuscar.isEnabled = true
                    btnBuscar.text = "Buscar calidades"
                }
            } catch (e: Throwable) {
                Log.e("Descargador", "Error al buscar calidades", e)
                activity?.runOnUiThread {
                    btnBuscar.isEnabled = true
                    btnBuscar.text = "Buscar calidades"
                    val ctx = context ?: return@runOnUiThread
                    AlertDialog.Builder(ctx)
                        .setTitle("Error")
                        .setMessage(
                            "Inicio: ${App.errorInicio ?: "correcto"}\n\n" +
                                    (e.message ?: e.toString()).take(1500)
                        )
                        .setPositiveButton("Cerrar", null)
                        .show()
                }
            }
        }.start()
    }

    private fun anadirACola() {
        val selectedChipId = cgCalidad.checkedChipId
        if (selectedChipId == View.NO_ID) {
            Toast.makeText(context, "Selecciona una calidad", Toast.LENGTH_SHORT).show()
            return
        }
        val pos = cgCalidad.indexOfChild(cgCalidad.findViewById(selectedChipId))
        if (pos < 0 || pos >= codigos.size) return

        val item = Item(
            id = System.currentTimeMillis(),
            url = urlActual,
            titulo = tituloActual,
            calidad = codigos[pos],
            thumbnailUrl = thumbnailActual,
            duracion = duracionActual
        )
        Descargas.agregar(item)

        val ctx = context ?: return
        ContextCompat.startForegroundService(ctx, Intent(ctx, DownloadService::class.java))
        Toast.makeText(ctx, "Añadida a la cola", Toast.LENGTH_SHORT).show()

        // Cambiar a la pestaña de Historial para ver el progreso de descarga
        (activity as? MainActivity)?.irAHistorial()
    }
}
