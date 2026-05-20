package com.poc.bridgeappb

/**
 * Configuração global de mock — partilhada entre MainActivity e MeterDataSource
 * (ambos correm no mesmo processo Android).
 *
 * [enabled] = true  → MeterDataSource devolve dados simulados (sem USB/ZIV)
 * [enabled] = false → leitura real via FTDI/DLMS (comportamento normal)
 */
object MockConfig {
    @Volatile
    var enabled: Boolean = false
}
