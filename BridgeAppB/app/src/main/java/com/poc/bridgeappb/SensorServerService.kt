package com.poc.bridgeappb

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.google.gson.Gson
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * ForegroundService que mantém o servidor Ktor HTTP a correr em background.
 *
 * O utilizador da App A (Power Apps) nunca interage com esta app directamente.
 * A notificação persistente é obrigatória pelo Android para ForegroundService,
 * mas pode ser discreta (ícone pequeno, sem som).
 *
 * Porta: localhost:8080
 * Rotas:
 *   GET /sensors      → HTML dashboard (usado pelo WebBrowser control da App A)
 *   GET /api/sensors  → JSON (extensibilidade futura)
 *   GET /health       → "OK" (verificação rápida)
 */
class SensorServerService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var dataSource: MeterDataSource
    private val gson = Gson()

    private val server by lazy {
        embeddedServer(Netty, port = SERVER_PORT, host = "0.0.0.0") {
            // CORS: permite que o WebView do Power Apps aceda ao servidor local
            install(CORS) {
                anyHost()
                allowHeader(HttpHeaders.ContentType)
                allowMethod(HttpMethod.Get)
            }

            routing {
                // ---------------------------------------------------------
                // GET /sensors — HTML dashboard elegante para o WebBrowser
                // O utilizador da App A vê este conteúdo sem saber da App B
                // ---------------------------------------------------------
                get("/sensors") {
                    val reading = dataSource.currentReading
                    val history = dataSource.history
                    call.respondText(ContentType.Text.Html) {
                        buildMeterHtml(reading, history)
                    }
                }

                // ---------------------------------------------------------
                // GET /api/sensors — JSON para o PCF / integração futura
                // Aceita ?counterId=... para identificar o contador pretendido.
                // Hoje o hardware só suporta um contador físico de cada vez,
                // pelo que o counterId é apenas registado e devolvido no payload.
                // ---------------------------------------------------------
                get("/api/sensors") {
                    val requestedCounterId = call.request.queryParameters["counterId"]
                    val payload = mapOf(
                        "current" to dataSource.currentReading,
                        "history" to dataSource.history,
                        "requestedCounterId" to requestedCounterId,
                        "serverVersion" to "2.1.0"
                    )
                    call.respondText(
                        gson.toJson(payload),
                        ContentType.Application.Json
                    )
                }

                // GET /health — verificação rápida do servidor
                get("/health") {
                    call.respondText("OK", ContentType.Text.Plain)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        dataSource = MeterDataSource(applicationContext, serviceScope)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        dataSource.start()
        server.start(wait = false)
        return START_STICKY   // Reinicia automaticamente se o SO matar o serviço
    }

    override fun onDestroy() {
        super.onDestroy()
        dataSource.stop()
        server.stop(gracePeriodMillis = 500, timeoutMillis = 1000)
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ------------------------------------------------------------------
    // Notificação discreta (obrigatória para ForegroundService no Android)
    // ------------------------------------------------------------------
    private fun buildNotification(): Notification {
        val channelId = "bridge_sensor_channel"
        val manager = getSystemService(NotificationManager::class.java)

        val channel = NotificationChannel(
            channelId,
            "Contador ZIV",
            NotificationManager.IMPORTANCE_MIN   // Sem som, sem pop-up
        ).apply {
            description = "Serviço de leitura do contador em segundo plano"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)

        return Notification.Builder(this, channelId)
            .setContentTitle("Contador ZIV")
            .setContentText("A ler contador via USB...")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setOngoing(true)
            .build()
    }

    // ------------------------------------------------------------------
    // HTML servido ao WebBrowser control da App A — dados reais do contador
    // ------------------------------------------------------------------
    private fun buildMeterHtml(reading: MeterReading, history: List<MeterReading>): String {
        val statusLabel = when (reading.status) {
            "OK"         -> "Ligado"
            "ERROR"      -> "Erro"
            else         -> "A ligar..."
        }
        val pulseCss = if (reading.status == "OK") "animation: pulse 2s infinite;" else ""

        val historyRows = history.drop(1).joinToString("") { r ->
            "<tr><td>${r.formattedTimestamp()}</td><td>${r.voltageL1} V</td>" +
            "<td>${"%.3f".format(r.currentTotal)} A</td>" +
            "<td>${"%.1f".format(r.activePowerP)} W</td>" +
            "<td><span style='padding:2px 7px;border-radius:99px;font-size:0.7rem;color:white;background:${r.statusColor()}'>${r.status}</span></td></tr>"
        }

        return """<!DOCTYPE html>
<html lang="pt">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width,initial-scale=1.0">
<meta http-equiv="refresh" content="30">
<title>Contador ZIV</title>
<style>
*{margin:0;padding:0;box-sizing:border-box}
body{font-family:'Segoe UI',Roboto,Arial,sans-serif;background:#f4f6f9;color:#2c3e50;padding:16px}
.header{text-align:center;margin-bottom:16px}
.header h1{font-size:1.3rem;font-weight:600}
.header .meta{font-size:0.78rem;color:#7f8c8d;margin-top:4px}
.status-dot{display:inline-block;width:9px;height:9px;border-radius:50%;margin-right:6px;background:${reading.statusColor()};$pulseCss}
.status-badge{display:inline-block;padding:2px 10px;border-radius:99px;font-size:0.72rem;color:white;font-weight:600;background:${reading.statusColor()}}
.section{margin-bottom:14px}
.section-title{font-size:0.7rem;font-weight:700;color:#95a5a6;text-transform:uppercase;letter-spacing:.6px;margin-bottom:8px}
.cards{display:flex;gap:10px;flex-wrap:wrap}
.card{flex:1;min-width:90px;background:white;border-radius:10px;padding:12px 10px;box-shadow:0 2px 6px rgba(0,0,0,0.07);text-align:center}
.card .lbl{font-size:0.68rem;color:#95a5a6;text-transform:uppercase;letter-spacing:.4px;margin-bottom:6px}
.card .val{font-size:1.55rem;font-weight:700;line-height:1}
.card .unit{font-size:0.78rem;color:#95a5a6;margin-top:3px}
.id-box{background:white;border-radius:10px;padding:12px 14px;box-shadow:0 2px 6px rgba(0,0,0,0.07)}
.id-row{display:flex;justify-content:space-between;padding:4px 0;font-size:0.82rem;border-bottom:1px solid #f0f0f0}
.id-row:last-child{border-bottom:none}
.id-key{color:#95a5a6}
.id-val{font-weight:600}
table{width:100%;border-collapse:collapse;background:white;border-radius:10px;overflow:hidden;box-shadow:0 2px 6px rgba(0,0,0,0.07)}
th{background:#f8f9fa;padding:8px 10px;font-size:0.7rem;font-weight:600;color:#95a5a6;text-align:left;text-transform:uppercase}
td{padding:8px 10px;font-size:0.82rem;border-top:1px solid #f0f0f0}
.error-box{background:#fdf2f2;border:1px solid #f5c6cb;border-radius:10px;padding:12px;font-size:0.82rem;color:#c0392b}
@keyframes pulse{0%,100%{opacity:1}50%{opacity:.35}}
</style>
</head>
<body>

<div class="header">
  <h1><span class="status-dot"></span>Contador ZIV</h1>
  <div class="meta">
    ${reading.formattedTimestamp()} &nbsp;·&nbsp; ${reading.deviceId}
    &nbsp;&nbsp;<span class="status-badge">$statusLabel</span>
  </div>
  ${if (reading.status == "ERROR" && reading.lastError != null) "<div class='error-box' style='margin-top:10px'>${reading.lastError}</div>" else ""}
</div>

${if (reading.serialNumber.isNotEmpty()) """
<div class="section">
  <div class="section-title">Identificação</div>
  <div class="id-box">
    <div class="id-row"><span class="id-key">N.º Série</span><span class="id-val">${reading.serialNumber}</span></div>
    <div class="id-row"><span class="id-key">Modelo</span><span class="id-val">${reading.model}</span></div>
    <div class="id-row"><span class="id-key">Firmware</span><span class="id-val">${reading.firmware}</span></div>
    <div class="id-row"><span class="id-key">Fabricante</span><span class="id-val">${reading.manufacturer}</span></div>
  </div>
</div>
""" else ""}

<div class="section">
  <div class="section-title">Tensões</div>
  <div class="cards">
    <div class="card"><div class="lbl">L1</div><div class="val" style="color:#2980b9">${reading.voltageL1}</div><div class="unit">V</div></div>
    <div class="card"><div class="lbl">L2</div><div class="val" style="color:#2980b9">${reading.voltageL2}</div><div class="unit">V</div></div>
    <div class="card"><div class="lbl">L3</div><div class="val" style="color:#2980b9">${reading.voltageL3}</div><div class="unit">V</div></div>
  </div>
</div>

<div class="section">
  <div class="section-title">Correntes</div>
  <div class="cards">
    <div class="card"><div class="lbl">L1</div><div class="val" style="color:#27ae60">${"%.2f".format(reading.currentL1)}</div><div class="unit">A</div></div>
    <div class="card"><div class="lbl">L2</div><div class="val" style="color:#27ae60">${"%.2f".format(reading.currentL2)}</div><div class="unit">A</div></div>
    <div class="card"><div class="lbl">L3</div><div class="val" style="color:#27ae60">${"%.2f".format(reading.currentL3)}</div><div class="unit">A</div></div>
    <div class="card"><div class="lbl">Total</div><div class="val" style="color:#27ae60">${"%.2f".format(reading.currentTotal)}</div><div class="unit">A</div></div>
  </div>
</div>

<div class="section">
  <div class="section-title">Potências</div>
  <div class="cards">
    <div class="card"><div class="lbl">P+ Consumo</div><div class="val" style="color:#e67e22">${"%.1f".format(reading.activePowerP)}</div><div class="unit">W</div></div>
    <div class="card"><div class="lbl">P− Injeção</div><div class="val" style="color:#8e44ad">${"%.1f".format(reading.activePowerN)}</div><div class="unit">W</div></div>
    <div class="card"><div class="lbl">Q+</div><div class="val" style="color:#c0392b">${"%.1f".format(reading.reactivePowerQp)}</div><div class="unit">var</div></div>
    <div class="card"><div class="lbl">Q−</div><div class="val" style="color:#c0392b">${"%.1f".format(reading.reactivePowerQn)}</div><div class="unit">var</div></div>
  </div>
</div>

<div class="section">
  <div class="section-title">Fator de Potência</div>
  <div class="cards">
    <div class="card" style="max-width:160px;margin:0 auto">
      <div class="lbl">cos φ</div>
      <div class="val" style="color:#16a085">${"%.3f".format(reading.powerFactor)}</div>
      <div class="unit">—</div>
    </div>
  </div>
</div>

${if (historyRows.isNotEmpty()) """
<div class="section">
  <div class="section-title">Histórico</div>
  <table>
    <thead><tr><th>Hora</th><th>V L1</th><th>I Total</th><th>P+</th><th>Estado</th></tr></thead>
    <tbody>$historyRows</tbody>
  </table>
</div>
""" else ""}

</body>
</html>"""
    }

    companion object {
        const val SERVER_PORT = 8080
        private const val NOTIFICATION_ID = 1001
    }
}
