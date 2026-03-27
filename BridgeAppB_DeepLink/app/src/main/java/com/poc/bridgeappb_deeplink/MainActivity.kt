package com.poc.bridgeappb_deeplink

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        val uri = intent?.data
        val isDeepLink = uri != null && uri.scheme == "bridgeappb" && uri.host == "getsensors"

        // Aplica tema transparente ANTES de super.onCreate() para que a janela
        // nunca seja visível — elimina o flash da App B durante o deep link.
        if (isDeepLink) {
            setTheme(R.style.Theme_BridgeAppBDeepLink_Transparent)
        }

        super.onCreate(savedInstanceState)

        if (isDeepLink) {
            // Invocado via deep link — gera dados e regressa imediatamente
            handleDeepLink(uri!!)
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

            // FLAG_ACTIVITY_CLEAR_TASK força o PowerApps nativo a reiniciar completamente
            // com o novo URL — garante que Param() relê os query parameters dos sensores.
            // Sem este flag, o PowerApps fica em cache e Param() não se reavalia.
            val powerAppsIntent = Intent(Intent.ACTION_VIEW, resultUri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
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
                    startActivity(Intent(Intent.ACTION_VIEW, resultUri).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
                }
            }
        } catch (e: Exception) {
            // Se falhar, termina silenciosamente
        }
        finish()
        // Remove animação de saída — transição completamente invisível
        overridePendingTransition(0, 0)
    }
}
