package com.example.descargador

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

class MainActivity : AppCompatActivity() {

    private lateinit var tabLayout: TabLayout
    private lateinit var viewPager: ViewPager2
    private lateinit var swOscuro: SwitchCompat

    private val descargarFragment = DescargarFragment()
    private val historialFragment = HistorialFragment()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tabLayout = findViewById(R.id.tabLayout)
        viewPager = findViewById(R.id.viewPager)
        swOscuro = findViewById(R.id.swOscuro)

        // Configurar ViewPager2 y Pestañas
        val adapter = MainPagerAdapter(this)
        viewPager.adapter = adapter

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            when (position) {
                0 -> {
                    tab.text = "Descargar"
                    tab.setIcon(R.drawable.ic_tab_download)
                }
                1 -> {
                    tab.text = "Historial"
                    tab.setIcon(R.drawable.ic_history)
                }
            }
        }.attach()

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

    fun irAHistorial() {
        viewPager.currentItem = 1
        historialFragment.refrescar()
    }

    private fun extraerEnlace(texto: String): String =
        Regex("https?://\\S+").find(texto)?.value ?: texto.trim()

    private fun manejarIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val texto = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
            val enlace = extraerEnlace(texto)
            viewPager.currentItem = 0
            descargarFragment.setUrlTextAndSearch(enlace)
        }
    }

    inner class MainPagerAdapter(activity: AppCompatActivity) : FragmentStateAdapter(activity) {
        override fun getItemCount(): Int = 2

        override fun createFragment(position: Int): Fragment {
            return if (position == 0) descargarFragment else historialFragment
        }
    }
}
