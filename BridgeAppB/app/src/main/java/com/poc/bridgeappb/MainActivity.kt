package com.poc.bridgeappb

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Activity principal da App B — usada apenas para:
 * 1. Iniciar o SensorServerService manualmente (primeira vez)
 * 2. Mostrar o estado do servidor (para debug/setup da POC)
 *
 * Nota: O utilizador da App A (Power Apps) NUNCA interage com esta Activity.
 * O fluxo normal é:
 *   - Tablet liga → BootReceiver → SensorServerService arranca automaticamente
 *   - App A abre → WebBrowser → http://localhost:8080/sensors → dados disponíveis
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val statusText = findViewById<TextView>(R.id.tvStatus)
        val btnStart   = findViewById<Button>(R.id.btnStartService)
        val btnStop    = findViewById<Button>(R.id.btnStopService)

        btnStart.setOnClickListener {
            val intent = Intent(this, SensorServerService::class.java)
            startForegroundService(intent)
            statusText.text = "✅ Servidor a correr em http://localhost:8080\n\nAbra a App A (Power Apps) no mesmo dispositivo."
        }

        btnStop.setOnClickListener {
            val intent = Intent(this, SensorServerService::class.java)
            stopService(intent)
            statusText.text = "⏹ Servidor parado."
        }

        // Estado inicial
        statusText.text = "Pressione 'Iniciar Servidor' para arrancar o serviço de dados.\n\n" +
            "Após iniciar, o servidor fica em background — pode fechar esta app."
    }
}
