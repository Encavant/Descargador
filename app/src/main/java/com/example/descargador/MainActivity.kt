package com.example.descargador

import android.Manifest
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.yausername.youtubedl_android.YoutubeDL

class MainActivity : AppCompatActivity() {

    private lateinit var etUrl: EditText
    private lateinit var btnPegar: Button
    private lateinit var btnBuscar: Button
    private lateinit var panelCalidad: LinearLayout
    private lateinit var tvTitulo: TextView
    private lateinit var spCalidad: Spinner
    private lateinit var btnAnadir: Button
    private lateinit var tvVacio: TextView
    private lateinit var rvLista: RecyclerView
    private lateinit var swOscuro: SwitchCompat
    private lateinit var adaptador: ItemAdapter

    private var urlActual = ""
    private var tituloActual = ""
    private var codigos = listOf<String>() // "720", "mejor", "audio" (mismo orden que el desplegable)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etUrl = findViewById(R.id.etUrl)
        btnPegar = findViewById(R.id.btnPegar)
        btnBuscar = findViewById(R.id.btnBuscar)
        panelCalidad = findViewById(R.id.panelCalidad)
        tvTitulo = findViewById(R.id.tvTitulo)
        spCalidad = findViewById(R.id.spCalidad)
        btnAnadir = findViewById(R.id.btnAnadir)
        tvVacio = findViewById(R.id.tvVacio)
        rvLista = findViewById(R.id.rvLista)
        swOscuro = findViewById(R.id.swOscuro)

        adaptador = ItemAdapter(
            alAbrir = { abrir(it) },
            alCompartir = { compartir(it) },
            alBorrar = { borrar(it) }
        )
        rvLista.layoutManager = LinearLayoutManager(this)
        rvLista.adapter = adaptador

        // Modo oscuro
        val oscuroAhora = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        swOscuro.isChecked = oscuroAhora
        swOscuro.setOnCheckedChangeListener { _, marcado ->
            getSharedPreferences("ajustes", MODE_PRIVATE).edit().putBoolean("oscuro", marcado).apply()
            AppCompatDelegate.setDefaultNightMode(
                if (marcado) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
            )
        }

        btnPegar.setOnClickListener { pegarDelPortapapeles() }
        btnBuscar.setOnClickListener { buscarCalidades() }
        btnAnadir.setOnClickListener { anadirACola() }

        // Permiso para mostrar notificaciones (Android 13+)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }

        manejarIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        manejarIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        Descargas.alCambiar = { refrescar() }
        refrescar()
    }

    override fun onStop() {
        super.onStop()
        Descargas.alCambiar = null
    }

    private fun refrescar() {
        val lista = Descargas.lista()
        adaptador.actualizar(lista)
        tvVacio.visibility = if (lista.isEmpty()) View.VISIBLE else View.GONE
    }

    // ---------- Entrada del enlace ----------

    private fun extraerEnlace(texto: String): String =
        Regex("https?://\\S+").find(texto)?.value ?: texto.trim()

    private fun pegarDelPortapapeles() {
        val cm = getSystemService(ClipboardManager::class.java)
        val texto = cm.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString()
        if (texto.isNullOrBlank()) {
            Toast.makeText(this, "El portapapeles está vacío", Toast.LENGTH_SHORT).show()
        } else {
            etUrl.setText(extraerEnlace(texto))
        }
    }

    // Cuando se comparte un enlace desde otra app (YouTube, TikTok, Instagram...)
    private fun manejarIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val texto = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
            etUrl.setText(extraerEnlace(texto))
            buscarCalidades()
        }
    }

    // ---------- Buscar calidades ----------

    // Actualiza yt-dlp (como mucho una vez al día)
    private fun actualizarYtDlp() {
        val ajustes = getSharedPreferences("ajustes", MODE_PRIVATE)
        val ultima = ajustes.getLong("ultimaActualizacion", 0L)
        if (System.currentTimeMillis() - ultima < 24L * 60 * 60 * 1000) return

        runOnUiThread { btnBuscar.text = "Actualizando yt-dlp (solo tarda la 1ª vez)..." }
        try {
            YoutubeDL.getInstance().updateYoutubeDL(applicationContext, YoutubeDL.UpdateChannel.STABLE)
            ajustes.edit().putLong("ultimaActualizacion", System.currentTimeMillis()).apply()
        } catch (e: Throwable) {
            android.util.Log.e("Descargador", "No se pudo actualizar yt-dlp", e)
        }
        runOnUiThread { btnBuscar.text = "Buscando..." }
    }
    private fun buscarCalidades() {
        val enlace = extraerEnlace(etUrl.text.toString())
        if (enlace.isEmpty()) {
            Toast.makeText(this, "Pega primero un enlace", Toast.LENGTH_SHORT).show()
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

                runOnUiThread {
                    tituloActual = titulo
                    codigos = cods
                    tvTitulo.text = titulo
                    spCalidad.adapter = ArrayAdapter(
                        this@MainActivity,
                        android.R.layout.simple_spinner_dropdown_item,
                        textos
                    )
                    panelCalidad.visibility = View.VISIBLE
                    btnBuscar.isEnabled = true
                    btnBuscar.text = "Buscar calidades"
                }
            } catch (e: Throwable) {
                android.util.Log.e("Descargador", "Error al buscar calidades", e)
                runOnUiThread {
                    btnBuscar.isEnabled = true
                    btnBuscar.text = "Buscar calidades"
                    AlertDialog.Builder(this@MainActivity)
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

    // ---------- Cola ----------

    private fun anadirACola() {
        val pos = spCalidad.selectedItemPosition
        if (pos < 0 || pos >= codigos.size) return

        val item = Item(
            id = System.currentTimeMillis(),
            url = urlActual,
            titulo = tituloActual,
            calidad = codigos[pos]
        )
        Descargas.agregar(item)
        ContextCompat.startForegroundService(this, Intent(this, DownloadService::class.java))
        Toast.makeText(this, "Añadida a la cola", Toast.LENGTH_SHORT).show()
    }

    // ---------- Acciones del historial ----------

    private fun abrir(item: Item) {
        val uri = Uri.parse(item.uri ?: return)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, item.mime ?: "*/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "No hay ninguna app para abrir este archivo", Toast.LENGTH_SHORT).show()
        }
    }

    private fun compartir(item: Item) {
        val uri = Uri.parse(item.uri ?: return)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = item.mime ?: "*/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Compartir"))
    }

    private fun borrar(item: Item) {
        val completada = item.estado == Estado.COMPLETADA
        AlertDialog.Builder(this)
            .setTitle(if (completada) "Borrar descarga" else "Quitar de la lista")
            .setMessage(
                if (completada) "Se borrará el archivo del móvil."
                else "Se quitará de la lista."
            )
            .setPositiveButton(if (completada) "Borrar" else "Quitar") { _, _ ->
                item.uri?.let {
                    try {
                        contentResolver.delete(Uri.parse(it), null, null)
                    } catch (e: Exception) {
                        // Si el archivo ya no existe o no se puede borrar, se quita igualmente de la lista
                    }
                }
                Descargas.quitar(item)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}
