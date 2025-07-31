package com.vvvital.vital_smartvs.util

import java.util.*

/**
 * This class includes a small subset of standard GATT attributes for demonstration purposes.
 */
object VSGattAttributes {
    private var attributes: HashMap<String, String> = HashMap()
    private var HEART_RATE_MEASUREMENT = "00002a37-0000-1000-8000-00805f9b34fb"
    internal var VVITAL_CHARACTERISTIC_DESCRIPTOR_TWO = "00002902-0000-1000-8000-00805f9b34fb"

    internal var VVITAL_CHARACTERISTIC_CONFIG =
        UUID.fromString("442F1570-8A00-9A28-CBE1-E1D4212D53EB")
    internal var VVITAL_SERVICE_UUID_ONE = UUID.fromString("442F1571-8A00-9A28-CBE1-E1D4212D53EB")
    internal var VVITAL_SERVICE_UUID_TWO = UUID.fromString("442F1572-8A00-9A28-CBE1-E1D4212D53EB")

    internal const val PAYLOAD_SEPARATOR = ","

    internal const val END_OF_PACKET = "0A0D"
    internal const val END_OF_PACKET_LAST_BYTE = "0D"

    internal const val CURRENT_DATE_FORMAT = "ddMMyyyy"
    internal const val CURRENT_TIME_FORMAT = "HHmmss"

    init {
        // Sample Services.
        attributes["0000180d-0000-1000-8000-00805f9b34fb"] = "Heart Rate Service"
        attributes["0000180a-0000-1000-8000-00805f9b34fb"] = "Device Information Service"
        // Sample Characteristics.
        attributes[HEART_RATE_MEASUREMENT] = "Heart Rate Measurement"
        attributes["00002a29-0000-1000-8000-00805f9b34fb"] = "Manufacturer Name String"

        // Using unknown GATT profile, must debug other end
        attributes["19B10000-E8F2-537E-4F6C-D104768A1214"] = "ioTank"
    }

    private fun lookup(uuid: String, defaultName: String): String {
        val name = attributes[uuid]
        return name ?: defaultName
    }
}
