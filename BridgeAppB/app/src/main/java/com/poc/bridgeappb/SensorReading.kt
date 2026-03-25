package com.poc.bridgeappb

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Representa uma leitura de sensor simulada.
 * Estes dados são gerados pelo SensorDataGenerator e servidos
 * pelo Ktor HTTP server para a App A (Power Apps).
 */
data class SensorReading(
    val id: String = UUID.randomUUID().toString().substring(0, 8),
    val deviceId: String = "TABLET-001",
    val timestamp: Long = System.currentTimeMillis(),
    val temperature: Double,   // Celsius
    val humidity: Double,      // Percentagem 0-100
    val pressure: Double,      // hPa
    val status: String = "OK"
) {
    /** Timestamp formatado para apresentação no HTML */
    fun formattedTimestamp(): String {
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    /** Cor do badge de temperatura (feedback visual para o utilizador da App A) */
    fun temperatureColor(): String = when {
        temperature < 18.0 -> "#3498db"   // Azul — frio
        temperature < 26.0 -> "#27ae60"   // Verde — confortável
        temperature < 32.0 -> "#f39c12"   // Laranja — quente
        else               -> "#e74c3c"   // Vermelho — muito quente
    }

    /** Ícone de humidade */
    fun humidityStatus(): String = when {
        humidity < 30.0 -> "Seco"
        humidity < 60.0 -> "Confortável"
        else            -> "Húmido"
    }
}
