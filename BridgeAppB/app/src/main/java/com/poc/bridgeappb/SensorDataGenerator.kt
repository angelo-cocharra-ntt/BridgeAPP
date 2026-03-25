package com.poc.bridgeappb

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sin
import kotlin.random.Random

/**
 * Gera leituras de sensores simuladas periodicamente.
 *
 * Os dados simulam um sensor real com variação gradual (não puramente aleatória),
 * para que a experiência do utilizador na App A pareça autêntica.
 * Corre numa coroutine e actualiza [currentReading] que é lido pelo servidor Ktor.
 */
class SensorDataGenerator(private val scope: CoroutineScope) {

    @Volatile
    var currentReading: SensorReading = generateInitial()
        private set

    // Histórico das últimas leituras (para o dashboard da App A)
    private val _history = ArrayDeque<SensorReading>(MAX_HISTORY)
    val history: List<SensorReading> get() = _history.toList()

    private var job: Job? = null

    // Variáveis de estado para simular deriva gradual dos sensores
    private var tempBase = 22.0
    private var humBase = 55.0
    private var pressBase = 1013.0
    private var tick = 0

    fun start() {
        job = scope.launch(Dispatchers.Default) {
            while (true) {
                delay(INTERVAL_MS)
                val reading = generate()
                currentReading = reading
                _history.addFirst(reading)
                if (_history.size > MAX_HISTORY) _history.removeLast()
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    // Simula variação gradual com ruído — parece um sensor real
    private fun generate(): SensorReading {
        tick++
        // Deriva lenta sinusoidal + ruído gaussiano pequeño
        val tempDelta = sin(tick * 0.1) * 2.0 + Random.nextDouble(-0.3, 0.3)
        val humDelta  = sin(tick * 0.07 + 1.0) * 5.0 + Random.nextDouble(-0.5, 0.5)
        val pressDelta = sin(tick * 0.05 + 2.0) * 2.0 + Random.nextDouble(-0.2, 0.2)

        return SensorReading(
            temperature = (tempBase + tempDelta).coerceIn(10.0, 45.0).roundTo(1),
            humidity    = (humBase + humDelta).coerceIn(10.0, 95.0).roundTo(1),
            pressure    = (pressBase + pressDelta).coerceIn(980.0, 1040.0).roundTo(1)
        )
    }

    private fun generateInitial() = SensorReading(
        temperature = 22.0,
        humidity    = 55.0,
        pressure    = 1013.0
    )

    companion object {
        private const val INTERVAL_MS = 5_000L   // Leitura a cada 5 segundos
        private const val MAX_HISTORY = 10        // Últimas 10 leituras no dashboard
    }
}

private fun Double.roundTo(decimals: Int): Double {
    val factor = Math.pow(10.0, decimals.toDouble())
    return Math.round(this * factor) / factor
}
