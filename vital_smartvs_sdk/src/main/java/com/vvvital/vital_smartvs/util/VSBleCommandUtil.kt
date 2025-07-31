package com.vvvital.vital_smartvs.util

import java.math.BigInteger
import java.util.*

/**
 * SmartVSApp
 *
 * Created By Administrator on 10/9/2019
 *
 * Copyright Softweb Solutions Inc. 2019,  All rights reserved.
 */
internal class VSBleCommandUtil {

    companion object {
        // message type definitions
        val TYPE_CMD = 'C'
        val TYPE_GET = 'G'
        val TYPE_SET = 'S'
        val TYPE_RESP = 'R'

        // message type property
        var Type: String? = null

        // Firmware update
        val FIRMWARE_MAIN_VERSION = "10.7.54" //Old - 10.7.46 // new - smvs_main_v100754
        val FIRMWARE_SENSOR_VERSION = "06.00.06"

        // message code property
        var Code: String? = null

        // message sender definitions
        val SENDER_MASTER = 'M'
        val SENDER_SLAVE = 'S'

        // message sender (master) property
        val Master: String? = null

        // general message payload definitions
        val SOP = ':'
        val CR = '\r'
        val NL = '\n'
        val EOP = "\n\r"
        val PAYLOAD_SEPARATOR = ','
        val PAYLOAD_MASTER_LENGTH = 74
        val PAYLOAD_SLAVE_LENGTH = 64
        val PAYLOAD_LENGTH_FORMAT = "X2"
        val PAYLOAD_PAD = 0.toChar()

        val PAYLOAD_FWDATA_LENGTH = 242
        val PAYLOAD_FWDATA_MASTER_LENGTH = 252


        val EXT_TEMPORARY = ".tmp"
        val EXT_INTERMEDIATE = ".val"
        val EXT_FINISH = ""

        val EXT_ENC = ".enc"
        val EXT_BIN = ".bin"

        fun hexToInteger(hexString: String): Int {
            return Integer.parseInt(hexString, 16)
        }


        fun integerToHex(data: Int): String {
            return Integer.toHexString(data)
        }

        fun hexToAscii(hexStr: String): String {
            val output = StringBuilder("")

            var i = 0
            while (i < hexStr.length) {
                val str = hexStr.substring(i, i + 2)
                output.append(Integer.parseInt(str, 16).toChar())
                i += 2
            }
            return output.toString()
        }

        fun hexToByteArray(hex: String): ByteArray {

            val hex = if (hex.length % 2 != 0) "0$hex" else hex

            val b = ByteArray(hex.length / 2)

            for (i in b.indices) {
                val index = i * 2
                val v = Integer.parseInt(hex.substring(index, index + 2), 16)
                b[i] = v.toByte()
            }
            return b
        }

        fun convertCelsiusToFahrenheit(celsius: Double): Float {
            return (((celsius * 9) / 5) + 32).toFloat()
        }


        fun hexToBinary(Hex: String): String {
            var bin = BigInteger(Hex, 16).toString(2)
            val inb = Integer.parseInt(bin)
            bin = String.format(Locale.getDefault(), "%08d", inb)
            return bin
        }
    }
}