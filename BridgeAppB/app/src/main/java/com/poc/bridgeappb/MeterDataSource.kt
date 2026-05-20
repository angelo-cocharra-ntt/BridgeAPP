package com.poc.bridgeappb

import android.content.Context
import android.util.Log
import android.dlms.protocol.FTDISerial
import android.zivapi.ApiibLib
import android.zivapi.Client
import android.zivapi.DataSession
import android.zivapi.Identifier
import android.zivapi.InstantaneousValues
import android.zivapi.ServiceReturnValue
import android.zivapi.SessionReturnValue
import com.ftdi.j2xx.D2xxManager
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Substitui o SensorDataGenerator com leituras reais do contador ZIV via USB/FTDI.
 *
 * Protocolo:
 *   1. IEC 62056-21 handshake (300 baud sign-on → ACK para 9600 baud)
 *   2. DLMS/COSEM DataSession.Open (Client.Reading, logicalDevice=1)
 *   3. Identifier_Get + InstantaneousValues.Get
 *   4. DataSession.Close
 *
 * Cada ciclo abre e fecha a sessão (mais seguro, evita timeouts DLMS).
 * Em caso de erro mantém o último MeterReading com status="ERROR".
 */
class MeterDataSource(
    private val context: Context,
    private val scope: CoroutineScope,
    private val password: String = DEFAULT_PASSWORD
) {

    @Volatile
    var currentReading: MeterReading = MeterReading(status = "CONNECTING")
        private set

    // Synchronized: escrito pelo coroutine IO, lido pelos handlers Ktor em threads diferentes
    private val _history = ArrayDeque<MeterReading>(MAX_HISTORY)
    val history: List<MeterReading> get() = synchronized(_history) { _history.toList() }

    private var job: Job? = null
    private var ftD2xx: D2xxManager? = null

    fun start() {
        initD2xx()
        job = scope.launch(Dispatchers.IO) {
            while (true) {
                val reading = readMeter()
                currentReading = reading
                synchronized(_history) {
                    _history.addFirst(reading)
                    if (_history.size > MAX_HISTORY) _history.removeLast()
                }
                delay(INTERVAL_MS)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private fun initD2xx() {
        try {
            ftD2xx = D2xxManager.getInstance(context)
        } catch (e: D2xxManager.D2xxException) {
            Log.e(TAG, "D2xxManager init failed", e)
        } catch (e: Exception) {
            Log.e(TAG, "D2xxManager init error", e)
        }
    }

    // ────────────────────────────────────────────────────────────────
    // Ciclo completo de leitura: handshake → sessão → leitura → fecho
    // ────────────────────────────────────────────────────────────────
    private suspend fun readMeter(): MeterReading {
        // Modo mock: devolve dados simulados sem tocar no USB
        if (MockConfig.enabled) return generateMockReading()

        val manager = ftD2xx ?: return errorReading("D2xxManager não inicializado")

        // 1. Verificar se há dispositivo FTDI ligado
        val deviceCount = try {
            manager.createDeviceInfoList(context)
        } catch (e: Exception) {
            return errorReading("Erro ao detetar dispositivo USB: ${e.message}")
        }
        if (deviceCount == 0) {
            return errorReading("Nenhum adaptador USB FTDI detetado")
        }

        // 2. Handshake IEC 62056-21 (300 → 9600 baud)
        val handshakeOk = performIecHandshake(manager)
        if (!handshakeOk) {
            return errorReading("Handshake IEC 62056-21 falhou")
        }

        // 3. Abrir porta FTDI para DLMS
        val serial = try {
            FTDISerial(SESSION_TIMEOUT_MS, context, manager)
        } catch (e: Exception) {
            return errorReading("FTDISerial init: ${e.message}")
        }

        val portOpen = try {
            serial.ftdi_con.isOpen || serial.OpenPort()
        } catch (e: Exception) {
            return errorReading("OpenPort: ${e.message}")
        }
        if (!portOpen) {
            return errorReading("Não foi possível abrir a porta USB")
        }

        // 4. Sessão DLMS
        val ds = DataSession(serial)
        ds.DataSession_Retries(SESSION_RETRIES)
        val openRet = try {
            ds.Open("", 1, 16, Client.Reading.toByte(), password.toByteArray())
        } catch (e: Exception) {
            return errorReading("DataSession.Open excepção: ${e.message}")
        }

        if (openRet != SessionReturnValue.OK) {
            val msg = try { ApiibLib.InterpretServiceReturnValue(openRet) } catch (_: Exception) { "$openRet" }
            return errorReading("DataSession.Open: $msg")
        }

        // 5. Leitura
        return try {
            buildReading(ds)
        } catch (e: Exception) {
            errorReading("Leitura: ${e.message}")
        } finally {
            // 6. Fecho da sessão (sempre, mesmo em erro)
            try { ds.Close() } catch (_: Exception) {}
        }
    }

    private fun buildReading(ds: DataSession): MeterReading {
        // Identificação
        val id = Identifier(ds)
        id.Identifier_Get()

        // Valores instantâneos
        val iv = InstantaneousValues(ds)
        val ret = iv.Get()
        if (ret != ServiceReturnValue.OK) {
            val msg = try { ApiibLib.InterpretServiceReturnValue(ret) } catch (_: Exception) { "$ret" }
            return errorReading("InstantaneousValues.Get: $msg").copy(
                serialNumber = id.numeroserie?.toString() ?: "",
                model = id.modelo?.toString() ?: "",
                firmware = id.firmware?.toString() ?: "",
                manufacturer = id.fabricante?.toString() ?: ""
            )
        }

        val v = iv.iv() ?: return errorReading("iv() devolveu null").copy(
            serialNumber = id.numeroserie?.toString() ?: "",
            model = id.modelo?.toString() ?: "",
            firmware = id.firmware?.toString() ?: "",
            manufacturer = id.fabricante?.toString() ?: ""
        )

        return MeterReading(
            serialNumber  = id.numeroserie?.toString()  ?: "",
            model         = id.modelo?.toString()       ?: "",
            firmware      = id.firmware?.toString()     ?: "",
            manufacturer  = id.fabricante?.toString()   ?: "",
            voltageL1     = v.VoltageL1,
            voltageL2     = v.VoltageL2,
            voltageL3     = v.VoltageL3,
            currentL1     = v.CurrentL1    * CURRENT_SCALE,
            currentL2     = v.CurrentL2    * CURRENT_SCALE,
            currentL3     = v.CurrentL3    * CURRENT_SCALE,
            currentTotal  = v.CurrentTotal * CURRENT_SCALE,
            activePowerP  = v.ActivepowerPp  * POWER_SCALE,
            activePowerN  = v.ActivepowerPn  * POWER_SCALE,
            reactivePowerQp = v.ReactivepowerQp * POWER_SCALE,
            reactivePowerQn = v.ReactivepowerQn * POWER_SCALE,
            powerFactor   = v.PowerFactor  * PF_SCALE,
            status        = "OK",
            lastError     = null
        )
    }

    // ────────────────────────────────────────────────────────────────
    // Handshake IEC 62056-21: sign-on a 300 baud → ACK para 9600
    // Lógica extraída de TesteContadores/MainActivity.kt
    // ────────────────────────────────────────────────────────────────
    private suspend fun performIecHandshake(manager: D2xxManager): Boolean {
        val device = try {
            manager.openByIndex(context, 0)
        } catch (e: Exception) {
            Log.w(TAG, "Handshake openByIndex: ${e.message}")
            return false
        } ?: return false

        return try {
            device.setBaudRate(300)
            device.setDataCharacteristics(7.toByte(), 0.toByte(), 2.toByte()) // 7-N-1 par
            device.purge(3.toByte())
            delay(100)

            val signOn = "/?!\r\n".toByteArray(Charsets.US_ASCII)
            device.write(signOn, signOn.size)
            delay(500)

            val buffer = ByteArray(128)
            val bytesRead = device.read(buffer, buffer.size)
            if (bytesRead <= 0) {
                Log.w(TAG, "Handshake: sem resposta do contador")
                return false
            }

            // ACK: 0x06 + '0' + '5' + '0' + \r\n  ('5' = 9600 baud no protocolo IEC)
            val ack = byteArrayOf(0x06, '0'.code.toByte(), '5'.code.toByte(), '0'.code.toByte(),
                '\r'.code.toByte(), '\n'.code.toByte())
            device.write(ack, ack.size)
            delay(300)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Handshake erro: ${e.message}")
            false
        } finally {
            try { device.close() } catch (_: Exception) {}
        }
    }

    private fun errorReading(msg: String): MeterReading {
        Log.w(TAG, "MeterDataSource error: $msg")
        // Preservar identificação da última leitura bem-sucedida se existir
        val last = currentReading
        return last.copy(
            timestamp = System.currentTimeMillis(),
            status    = "ERROR",
            lastError = msg
        )
    }

    // ────────────────────────────────────────────────────────────────
    // Dados simulados — usados quando MockConfig.enabled = true
    // Valores típicos de um contador ZIV em rede trifásica 230V/400V
    // ────────────────────────────────────────────────────────────────
    private fun generateMockReading(): MeterReading {
        val v1 = 230 + Random.nextInt(-2, 3)
        val v2 = 230 + Random.nextInt(-2, 3)
        val v3 = 230 + Random.nextInt(-2, 3)
        val i1 = 4.1 + Random.nextDouble(-0.1, 0.1)
        val i2 = 4.2 + Random.nextDouble(-0.1, 0.1)
        val i3 = 4.3 + Random.nextDouble(-0.1, 0.1)
        return MeterReading(
            serialNumber     = "MOCK-ZIV-001",
            model            = "ZIV 5CTD",
            firmware         = "M1.0",
            manufacturer     = "ZIV (MOCK)",
            voltageL1        = v1,
            voltageL2        = v2,
            voltageL3        = v3,
            currentL1        = i1,
            currentL2        = i2,
            currentL3        = i3,
            currentTotal     = i1 + i2 + i3,
            activePowerP     = 8500.0 + Random.nextDouble(-80.0, 80.0),
            activePowerN     = 0.0,
            reactivePowerQp  = 420.0  + Random.nextDouble(-10.0, 10.0),
            reactivePowerQn  = 0.0,
            powerFactor      = 0.948  + Random.nextDouble(-0.005, 0.005),
            deviceId         = "CONTADOR-MOCK",
            status           = "OK",
            lastError        = null
        )
    }

    companion object {
        private const val TAG = "MeterDataSource"
        private const val DEFAULT_PASSWORD = "00000001"
        private const val SESSION_TIMEOUT_MS = 20000
        private const val SESSION_RETRIES = 2
        private const val INTERVAL_MS = 30_000L  // Ciclo de 30s (handshake IEC leva ~5-10s)
        private const val MAX_HISTORY = 10
        private const val CURRENT_SCALE = 0.1    // raw → A
        private const val POWER_SCALE   = 10.0   // raw → W / var
        private const val PF_SCALE      = 0.001  // raw → adimensional
    }
}
