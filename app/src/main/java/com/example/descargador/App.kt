package com.example.descargador

import android.app.Application
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL

class App : Application() {

    companion object {
        var errorInicio: String? = null
    }

    override fun onCreate() {
        super.onCreate()
        // ... (lo que ya hay: Descargas.iniciar y modo oscuro, sin tocar)

        try {
            YoutubeDL.getInstance().init(this)
            FFmpeg.getInstance().init(this)
        } catch (e: Throwable) {
            errorInicio = e.toString()
            Log.e("Descargador", "No se pudo inicializar yt-dlp/ffmpeg", e)
        }
    }
}
