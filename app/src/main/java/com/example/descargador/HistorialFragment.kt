package com.example.descargador

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class HistorialFragment : Fragment() {

    private lateinit var rvLista: RecyclerView
    private lateinit var panelVacio: LinearLayout
    private lateinit var adaptador: ItemAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_historial, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        rvLista = view.findViewById(R.id.rvLista)
        panelVacio = view.findViewById(R.id.panelVacio)

        adaptador = ItemAdapter(
            alAbrir = { abrir(it) },
            alCompartir = { compartir(it) },
            alBorrar = { borrar(it) }
        )
        rvLista.layoutManager = LinearLayoutManager(requireContext())
        rvLista.adapter = adaptador
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

    fun refrescar() {
        val lista = Descargas.lista()
        if (::adaptador.isInitialized && ::panelVacio.isInitialized) {
            adaptador.actualizar(lista)
            panelVacio.visibility = if (lista.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun abrir(item: Item) {
        val uri = Uri.parse(item.uri ?: return)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, item.mime ?: "*/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "No hay ninguna app para abrir este archivo", Toast.LENGTH_SHORT).show()
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
        val ctx = context ?: return
        val completada = item.estado == Estado.COMPLETADA
        AlertDialog.Builder(ctx)
            .setTitle(if (completada) "Borrar descarga" else "Quitar de la lista")
            .setMessage(
                if (completada) "Se borrará el archivo del móvil."
                else "Se quitará de la lista."
            )
            .setPositiveButton(if (completada) "Borrar" else "Quitar") { _, _ ->
                item.uri?.let {
                    try {
                        ctx.contentResolver.delete(Uri.parse(it), null, null)
                    } catch (e: Exception) {
                        // Si el archivo ya no existe, se quita igualmente
                    }
                }
                Descargas.quitar(item)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}
