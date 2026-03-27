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
            // Sem returnUrl — apenas termina
            finish()
            return
        }

        val sensor = SensorDataGenerator.generate()

        // Anexa os dados ao returnUrl como query parameters
        val separator = if (returnUrl.contains("?")) "&" else "?"
        val resultUrl = buildString {
            append(returnUrl)
            append(separator)
            append("temperature=").append(sensor.temperature)
            append("&humidity=").append(sensor.humidity)
            append("&pressure=").append(sensor.pressure)
            append("&updatedAt=").append(System.currentTimeMillis())
        }

        // Abre o browser de volta para a Canvas App com os dados
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(resultUrl)))
        finish()
    }
}
