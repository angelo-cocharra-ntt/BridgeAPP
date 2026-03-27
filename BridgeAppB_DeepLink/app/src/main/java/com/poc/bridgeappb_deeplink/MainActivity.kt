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
            val resultUri = Uri.parse(returnUrl).buildUpon()
                .appendQueryParameter("temperature", sensor.temperature.toString())
                .appendQueryParameter("humidity", sensor.humidity.toString())
                .appendQueryParameter("pressure", sensor.pressure.toString())
                .appendQueryParameter("updatedAt", System.currentTimeMillis().toString())
                .build()

            // Abre directamente no PowerApps nativo (com.microsoft.msapps),
            // mantendo o URL https://apps.powerapps.com — garante que Param() funciona
            // e evita o interstitial "Abrir a aplicação?" do Chrome.
            val powerAppsIntent = Intent(Intent.ACTION_VIEW, resultUri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                setPackage("com.microsoft.msapps")
            }
            try {
                startActivity(powerAppsIntent)
            } catch (e: Exception) {
                // PowerApps nativo não instalado — fallback para Chrome
                val chromeIntent = Intent(Intent.ACTION_VIEW, resultUri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    setPackage("com.android.chrome")
                }
                try {
                    startActivity(chromeIntent)
                } catch (e2: Exception) {
                    // Chrome também não disponível — usa browser por defeito
                    startActivity(Intent(Intent.ACTION_VIEW, resultUri).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
                }
            }
        } catch (e: Exception) {
            // Se falhar, termina silenciosamente
        }
        finish()
    }
}
