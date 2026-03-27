package com.poc.bridgeappb_deeplink

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uri = intent?.data
        if (uri != null && uri.scheme == "bridgeappb" && uri.host == "getsensors") {
            // Invocado via deep link — gera dados e regressa imediatamente
            handleDeepLink(uri)
        } else {
            // Aberto normalmente pelo launcher — mostra ecrã informativo
            setContentView(R.layout.activity_main)
        }
    }

    private fun handleDeepLink(uri: Uri) {
        val returnUrl = uri.getQueryParameter("returnUrl")

        if (returnUrl.isNullOrBlank()) {
            finish()
            return
        }

        val sensor = SensorDataGenerator.generate()

        try {
            // Usa Uri.Builder para anexar parâmetros de forma segura (evita crashes com URLs complexos)
            val resultUri = Uri.parse(returnUrl).buildUpon()
                .appendQueryParameter("temperature", sensor.temperature.toString())
                .appendQueryParameter("humidity", sensor.humidity.toString())
                .appendQueryParameter("pressure", sensor.pressure.toString())
                .appendQueryParameter("updatedAt", System.currentTimeMillis().toString())
                .build()

            startActivity(Intent(Intent.ACTION_VIEW, resultUri))
        } catch (e: Exception) {
            // Se falhar, termina silenciosamente
        }
        finish()
    }
}
