package com.poc.bridgeappb

import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.widget.Button
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.ftdi.j2xx.D2xxManager

/**
 * Activity principal da App B — usada para:
 * 1. Autorizar o acesso USB ao adaptador FTDI (obrigatório na primeira utilização)
 * 2. Iniciar/parar o SensorServerService manualmente (setup/debug)
 *
 * O Android lança esta Activity automaticamente quando o cabo USB FTDI é ligado
 * (via USB_DEVICE_ATTACHED no manifest + usb_device_filter.xml).
 * O utilizador autoriza uma vez — a permissão fica persistente.
 *
 * Fluxo normal após setup:
 *   Tablet liga → BootReceiver → SensorServerService arranca automaticamente
 *   App A abre → WebBrowser → http://localhost:8080/sensors → dados do contador
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val statusText   = findViewById<TextView>(R.id.tvStatus)
        val btnStart     = findViewById<Button>(R.id.btnStartService)
        val btnStop      = findViewById<Button>(R.id.btnStopService)
        val switchMock   = findViewById<Switch>(R.id.switchMockMode)

        // Restaurar preferência de mock guardada anteriormente
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        MockConfig.enabled = prefs.getBoolean(PREF_MOCK_MODE, false)
        switchMock.isChecked = MockConfig.enabled

        switchMock.setOnCheckedChangeListener { _, isChecked ->
            MockConfig.enabled = isChecked
            prefs.edit().putBoolean(PREF_MOCK_MODE, isChecked).apply()
            if (isChecked) {
                statusText.text = "🧪 Modo Mock ativo — dados simulados a ser gerados.\n\n" +
                    "O servidor em http://localhost:8080 devolve leituras ZIV fictícias."
            } else {
                statusText.text = "Modo Mock desativado.\nLigue o cabo USB e inicie o servidor para ler o contador real."
            }
        }

        // Inicializa o D2xxManager — se o cabo USB já estiver ligado, o Android
        // pode mostrar aqui o diálogo de autorização de permissão USB
        try {
            D2xxManager.getInstance(this)
        } catch (_: Exception) { }

        btnStart.setOnClickListener {
            val intent = Intent(this, SensorServerService::class.java)
            startForegroundService(intent)
            val mockNote = if (MockConfig.enabled) " (⚠️ MOCK ativo)" else ""
            statusText.text = "✅ Servidor a correr em http://localhost:8080$mockNote\n\n" +
                "Ligue o cabo USB ao contador e autorize o acesso quando solicitado.\n" +
                "O serviço lê o contador a cada 30 segundos."
        }

        btnStop.setOnClickListener {
            val intent = Intent(this, SensorServerService::class.java)
            stopService(intent)
            statusText.text = "⏹ Servidor parado."
        }

        // Se a Activity foi lançada pelo USB_DEVICE_ATTACHED, informa o utilizador
        @Suppress("DEPRECATION")
        val usbDevice = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
        } else {
            intent?.getParcelableExtra(UsbManager.EXTRA_DEVICE)
        }
        val initialMsg = if (usbDevice != null) {
            "🔌 Adaptador USB detetado.\n\nInicie o servidor e autorize o acesso USB quando o Android solicitar."
        } else if (MockConfig.enabled) {
            "🧪 Modo Mock ativo.\n\nPressione 'Iniciar Servidor' para servir dados simulados."
        } else {
            "Pressione 'Iniciar Servidor' para arrancar o serviço de leitura do contador.\n\n" +
            "Ligue o cabo USB ao contador ZIV antes de iniciar."
        }
        statusText.text = initialMsg
    }

    companion object {
        private const val PREFS_NAME    = "bridge_prefs"
        private const val PREF_MOCK_MODE = "mock_mode"
    }
}
