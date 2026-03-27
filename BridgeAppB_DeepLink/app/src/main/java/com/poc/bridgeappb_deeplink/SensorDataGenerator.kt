package com.poc.bridgeappb_deeplink

import kotlin.math.sin

object SensorDataGenerator {

    fun generate(): SensorReading {
        val t = System.currentTimeMillis() / 1000.0
        return SensorReading(
            temperature = "%.1f".format(22.0 + 3.0 * sin(t / 30.0)).toDouble(),
            humidity    = "%.1f".format(60.0 + 10.0 * sin(t / 45.0 + 1.0)).toDouble(),
            pressure    = "%.1f".format(1013.0 + 5.0 * sin(t / 60.0 + 2.0)).toDouble()
        )
    }
}
