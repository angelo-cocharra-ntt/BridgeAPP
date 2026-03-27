package com.poc.bridgeappb_deeplink

import kotlin.math.sin

object SensorDataGenerator {

    private fun round1(value: Double): Double =
        Math.round(value * 10.0) / 10.0

    fun generate(): SensorReading {
        val t = System.currentTimeMillis() / 1000.0
        return SensorReading(
            temperature = round1(22.0 + 3.0 * sin(t / 30.0)),
            humidity    = round1(60.0 + 10.0 * sin(t / 45.0 + 1.0)),
            pressure    = round1(1013.0 + 5.0 * sin(t / 60.0 + 2.0))
        )
    }
}
