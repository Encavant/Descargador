package com.example.descargador

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ItemAdapter(
    private val alAbrir: (Item) -> Unit,
    private val alCompartir: (Item) -> Unit,
    private val alBorrar: (Item) -> Unit
) : RecyclerView.Adapter<ItemAdapter.VH>() {

    private var datos: List<Item> = emptyList()

    init {
        setHasStableIds(true)
    }

    fun actualizar(nuevos: List<Item>) {
        datos = nuevos
        notifyDataSetChanged()
    }

    override fun getItemId(position: Int): Long = datos[position].id

    override fun getItemCount(): Int = datos.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val vista = LayoutInflater.from(parent.context).inflate(R.layout.item_descarga, parent, false)
        return VH(vista)
    }

    override fun onBindViewHolder(h: VH, position: Int) {
        val item = datos[position]

        h.titulo.text = item.titulo

        h.detalle.text = when (item.estado) {
            Estado.EN_COLA -> "${item.etiquetaCalidad()} · En cola"
            Estado.DESCARGANDO ->
                if (item.progreso >= 100) "${item.etiquetaCalidad()} · Procesando..."
                else "${item.etiquetaCalidad()} · Descargando ${item.progreso}%"
            Estado.COMPLETADA -> "${item.etiquetaCalidad()} · Completada"
            Estado.ERROR -> "${item.etiquetaCalidad()} · Error: ${item.error ?: "desconocido"}"
        }

        h.barra.visibility = if (item.estado == Estado.DESCARGANDO) View.VISIBLE else View.GONE
        h.barra.progress = item.progreso

        val completada = item.estado == Estado.COMPLETADA
        h.btnAbrir.visibility = if (completada) View.VISIBLE else View.GONE
        h.btnCompartir.visibility = if (completada) View.VISIBLE else View.GONE
        // No se puede borrar mientras se está descargando
        h.btnBorrar.visibility = if (item.estado == Estado.DESCARGANDO) View.GONE else View.VISIBLE
        h.btnBorrar.text = if (completada) "Borrar" else "Quitar"

        h.btnAbrir.setOnClickListener { alAbrir(item) }
        h.btnCompartir.setOnClickListener { alCompartir(item) }
        h.btnBorrar.setOnClickListener { alBorrar(item) }
    }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val titulo: TextView = v.findViewById(R.id.tvItemTitulo)
        val detalle: TextView = v.findViewById(R.id.tvItemDetalle)
        val barra: ProgressBar = v.findViewById(R.id.pbItem)
        val btnAbrir: Button = v.findViewById(R.id.btnAbrir)
        val btnCompartir: Button = v.findViewById(R.id.btnCompartir)
        val btnBorrar: Button = v.findViewById(R.id.btnBorrar)
    }
}
