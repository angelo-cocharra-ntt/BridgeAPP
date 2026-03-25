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
    private lateinit var generator: SensorDataGenerator
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
                    val reading = generator.currentReading
                    val history = generator.history
                    call.respondText(ContentType.Text.Html) {
                        buildSensorHtml(reading, history)
                    }
                }

                // ---------------------------------------------------------
                // GET /api/sensors — JSON para integração futura
                // ---------------------------------------------------------
                get("/api/sensors") {
                    val payload = mapOf(
                        "current" to generator.currentReading,
                        "history" to generator.history,
                        "serverVersion" to "1.0.0"
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
        generator = SensorDataGenerator(serviceScope)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        generator.start()
        server.start(wait = false)
        return START_STICKY   // Reinicia automaticamente se o SO matar o serviço
    }

    override fun onDestroy() {
        super.onDestroy()
        generator.stop()
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
            "Sensor Service",
            NotificationManager.IMPORTANCE_MIN   // Sem som, sem pop-up
        ).apply {
            description = "Serviço de dados em segundo plano"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)

        return Notification.Builder(this, channelId)
            .setContentTitle("Sensor Service")
            .setContentText("A recolher dados...")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setOngoing(true)
            .build()
    }

    // ------------------------------------------------------------------
    // HTML servido ao WebBrowser control da App A
    // Design limpo, sem qualquer referência à "App B" ou "BridgeApp"
    // ------------------------------------------------------------------
    private fun buildSensorHtml(reading: SensorReading, history: List<SensorReading>): String {
        val historyRows = history.drop(1).joinToString("") { r ->
            """
            <tr>
                <td>${r.formattedTimestamp()}</td>
                <td>${r.temperature} °C</td>
                <td>${r.humidity} %</td>
                <td>${r.pressure} hPa</td>
            </tr>
            """.trimIndent()
        }

        return """
        <!DOCTYPE html>
        <html lang="pt">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <meta http-equiv="refresh" content="10">
            <title>Dados dos Sensores</title>
            <style>
                * { margin: 0; padding: 0; box-sizing: border-box; }
                body {
                    font-family: 'Segoe UI', Roboto, Arial, sans-serif;
                    background: #f4f6f9;
                    color: #2c3e50;
                    padding: 16px;
                }
                .header {
                    text-align: center;
                    margin-bottom: 20px;
                }
                .header h1 {
                    font-size: 1.4rem;
                    font-weight: 600;
                    color: #2c3e50;
                }
                .header .timestamp {
                    font-size: 0.8rem;
                    color: #7f8c8d;
                    margin-top: 4px;
                }
                .cards {
                    display: flex;
                    gap: 12px;
                    margin-bottom: 20px;
                    flex-wrap: wrap;
                }
                .card {
                    flex: 1;
                    min-width: 100px;
                    background: white;
                    border-radius: 12px;
                    padding: 16px;
                    box-shadow: 0 2px 8px rgba(0,0,0,0.08);
                    text-align: center;
                }
                .card .label {
                    font-size: 0.75rem;
                    color: #7f8c8d;
                    text-transform: uppercase;
                    letter-spacing: 0.5px;
                    margin-bottom: 8px;
                }
                .card .value {
                    font-size: 2rem;
                    font-weight: 700;
                    line-height: 1;
                }
                .card .unit {
                    font-size: 0.9rem;
                    color: #7f8c8d;
                    margin-top: 4px;
                }
                .card .badge {
                    display: inline-block;
                    margin-top: 8px;
                    padding: 2px 8px;
                    border-radius: 99px;
                    font-size: 0.7rem;
                    color: white;
                    font-weight: 600;
                }
                .history-section h2 {
                    font-size: 0.95rem;
                    font-weight: 600;
                    margin-bottom: 10px;
                    color: #7f8c8d;
                    text-transform: uppercase;
                    letter-spacing: 0.5px;
                }
                table {
                    width: 100%;
                    border-collapse: collapse;
                    background: white;
                    border-radius: 12px;
                    overflow: hidden;
                    box-shadow: 0 2px 8px rgba(0,0,0,0.08);
                }
                th {
                    background: #f8f9fa;
                    padding: 10px 12px;
                    font-size: 0.75rem;
                    font-weight: 600;
                    color: #7f8c8d;
                    text-align: left;
                    text-transform: uppercase;
                }
                td {
                    padding: 10px 12px;
                    font-size: 0.85rem;
                    border-top: 1px solid #f0f0f0;
                }
                .status-dot {
                    display: inline-block;
                    width: 8px;
                    height: 8px;
                    border-radius: 50%;
                    background: #27ae60;
                    margin-right: 6px;
                    animation: pulse 2s infinite;
                }
                @keyframes pulse {
                    0%, 100% { opacity: 1; }
                    50% { opacity: 0.4; }
                }
            </style>
        </head>
        <body>
            <div class="header">
                <h1><span class="status-dot"></span>Monitorização de Sensores</h1>
                <div class="timestamp">Última leitura: ${reading.formattedTimestamp()} · ID: ${reading.deviceId}</div>
            </div>

            <div class="cards">
                <div class="card">
                    <div class="label">Temperatura</div>
                    <div class="value" style="color: ${reading.temperatureColor()}">${reading.temperature}</div>
                    <div class="unit">°C</div>
                    <div class="badge" style="background: ${reading.temperatureColor()}">
                        ${if (reading.temperature < 18) "Frio" else if (reading.temperature < 26) "Confortável" else if (reading.temperature < 32) "Quente" else "Muito Quente"}
                    </div>
                </div>
                <div class="card">
                    <div class="label">Humidade</div>
                    <div class="value" style="color: #3498db">${reading.humidity}</div>
                    <div class="unit">%</div>
                    <div class="badge" style="background: #3498db">${reading.humidityStatus()}</div>
                </div>
                <div class="card">
                    <div class="label">Pressão</div>
                    <div class="value" style="color: #9b59b6">${reading.pressure}</div>
                    <div class="unit">hPa</div>
                    <div class="badge" style="background: #9b59b6">Normal</div>
                </div>
            </div>

            ${if (historyRows.isNotEmpty()) """
            <div class="history-section">
                <h2>Histórico</h2>
                <table>
                    <thead>
                        <tr>
                            <th>Hora</th>
                            <th>Temp.</th>
                            <th>Humidade</th>
                            <th>Pressão</th>
                        </tr>
                    </thead>
                    <tbody>
                        $historyRows
                    </tbody>
                </table>
            </div>
            """ else ""}
        </body>
        </html>
        """.trimIndent()
    }

    companion object {
        const val SERVER_PORT = 8080
        private const val NOTIFICATION_ID = 1001
    }
}
