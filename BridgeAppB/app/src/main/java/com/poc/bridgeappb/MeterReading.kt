package com.poc.bridgeappb

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class MeterReading(
    // ── Identificação ────────────────────────────────────────────────
    val serialNumber: String = "",
    val model: String = "",
    val firmware: String = "",
    val manufacturer: String = "",

    // ── Tensões (V) ──────────────────────────────────────────────────
    val voltageL1: Int = 0,
    val voltageL2: Int = 0,
    val voltageL3: Int = 0,

    // ── Correntes (A) — valor raw do SDK × 0.1 ──────────────────────
    val currentL1: Double = 0.0,
    val currentL2: Double = 0.0,
    val currentL3: Double = 0.0,
    val currentTotal: Double = 0.0,

    // ── Potências (W / var) — valor raw do SDK × 10 ─────────────────
    val activePowerP: Double = 0.0,   // P+ consumo
    val activePowerN: Double = 0.0,   // P- injeção
    val reactivePowerQp: Double = 0.0, // Q+ indutivo
    val reactivePowerQn: Double = 0.0, // Q- capacitivo

    // ── Fator de Potência — valor raw do SDK × 0.001 ─────────────────
    val powerFactor: Double = 0.0,

    // ── Metadados ────────────────────────────────────────────────────
    val deviceId: String = "CONTADOR-ZIV",
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = "CONNECTING", // "OK" | "ERROR" | "CONNECTING"
    val lastError: String? = null
) {
    fun formattedTimestamp(): String {
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    fun statusColor(): String = when (status) {
        "OK"         -> "#27ae60"  // verde
        "ERROR"      -> "#e74c3c"  // vermelho
        else         -> "#f39c12"  // amarelo — CONNECTING / desconhecido
    }
}
