package com.vvvital.vital_smartvs.service

import android.Manifest
import android.app.Service
import android.bluetooth.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.*
import androidx.core.app.ActivityCompat
import com.google.gson.Gson
import com.vvvital.vital_smartvs.BuildConfig
import com.vvvital.vital_smartvs.R

import com.vvvital.vital_smartvs.bean.*
import com.vvvital.vital_smartvs.enums.StatusOptions
import com.vvvital.vital_smartvs.interfaces.PeripheralCallback
import com.vvvital.vital_smartvs.log.VSLog
import com.vvvital.vital_smartvs.util.FWUpgradeState
import com.vvvital.vital_smartvs.util.VSBleCommandUtil
import com.vvvital.vital_smartvs.util.VSCommandName
import com.vvvital.vital_smartvs.util.VSGattAttributes
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList

/**
 * Service for managing connection and data communication with a GATT server hosted on a
 * given Bluetooth LE device.
 */
class VVitalManager : Service() {

    private var mBluetoothManager: BluetoothManager? = null
    private var mBluetoothAdapter: BluetoothAdapter? = null
    private var mBluetoothDeviceAddress: String? = null
    private var mBluetoothGatt: BluetoothGatt? = null
    private val STATE_DISCONNECTED = 0
    private val STATE_CONNECTING = 1
    private val STATE_CONNECTED = 2
    private var mConnectionState = STATE_DISCONNECTED
    private var batteryServiceOne: BluetoothGattService? = null
    private var gattChar: BluetoothGattCharacteristic? = null
    private var lastWriteCharacteristic: BluetoothGattCharacteristic? = null

    private var strCommandResponse = ""
    private var cTimer: CountDownTimer? = null

    private var smartVSCMDCallback: PeripheralCallback? = null
    private var isTimerRequire: Boolean = false
    private var isTmpError: Boolean = false
    private var isFirmwareFinished = false

    private var fileData: ByteArray? = null
    private var fileName: String = ""

    private var filDataList: ArrayList<ByteArray>? = null

    private var packetCount = 1
    private var currentPacket = 1
    private var totalPackets = 0

    private var crcEnc: Int = 0
    private var crcBin: Int = 0

    private var encFile = "smvs_main.enc"
    private var binFile = "smvs_sb.bin"
    var firmwareMainBoard = "Mainboard"
    private var firmwareSensorBoard = "Sensorboard"

    private var deviceInfo = DeviceInfo()

    private var latestDataArray: ByteArray? = null

    private val TAG = VVitalManager::class.java.simpleName

    private val mHandler = Handler(Looper.getMainLooper()) { msg ->
        if (currentPacket < totalPackets) {
            currentPacket++
            if (packetCount == 255) {
                packetCount = 0
            } else {
                packetCount++
            }
            setStartFirmwareDataTransfer()
        }
        true // Indicates the message is handled
    }

    // Implements callback methods for GATT events that the app cares about.  For example,
    // connection change and services discovered.
    private val mGattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (ActivityCompat.checkSelfPermission(
                            this@VVitalManager,
                            Manifest.permission.BLUETOOTH_CONNECT
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        return
                    }
                }
                VSLog.e(TAG, gatt.device.name)

                mConnectionState = STATE_CONNECTED
                VSLog.i(TAG, "Connected to GATT server.")
                // Attempts to discover services after successful connection.
                smartVSCMDCallback!!.onPeripheralStateChange(mConnectionState)
                VSLog.i(
                    TAG,
                    "Attempting to start service discovery:" + mBluetoothGatt!!.discoverServices()
                )
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                mConnectionState =
                    STATE_DISCONNECTED
                VSLog.i(TAG, "Disconnected from GATT server.")
                smartVSCMDCallback!!.onPeripheralStateChange(mConnectionState)
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                notifyGattCharacteristics()
            } else {
                VSLog.w(TAG, "onServicesDiscovered received: $status")
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                VSLog.e("onCharacteristicRead", "GATT_SUCCESS")
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            val stringBuilder = java.lang.StringBuilder("")

            for (byteChar in characteristic.value) {
                stringBuilder.append(String.format("%02X", byteChar))
            }

            strCommandResponse += stringBuilder.toString()

            if (stringBuilder.contains(VSGattAttributes.END_OF_PACKET_LAST_BYTE)) {
                if (strCommandResponse.endsWith(VSGattAttributes.END_OF_PACKET)) {
                    val payload = String(VSBleCommandUtil.hexToByteArray(strCommandResponse))
                    VSLog.d(
                        TAG,
                        "<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<< $payload"
                    )
                    strCommandResponse = ""
                    when {
                        payload.contains(VSCommandName.CODE_DINFO) -> {
                            //Device info Response
                            processDeviceInfoData(payload)
                        }
                        payload.contains(VSCommandName.SMDUR) -> {
                            //SMDUR Data Response
                            processSMDURData(payload)
                        }
                        payload.contains(VSCommandName.CODE_CURDT) -> {
                            //CODE_CURDT Data Response
                            val ack = payload[10]
                            if (ack.toString().equals("1", true)) { //Success

                                val responseObject = CurrentTimeBean()
                                responseObject.ACK = true

                                val jsonResponseObject: String = Gson().toJson(responseObject)
                                smartVSCMDCallback!!.onSuccess(
                                    jsonResponseObject,
                                    VSCommandName.CODE_CURDT
                                )
                            } else {//Failure
                                var offset = 12
                                offset += 2
                                val errorCode = payload[offset].toString().plus(payload[offset + 1])

                                val error = VSError.init(errorCode)
                                smartVSCMDCallback!!.onFailure(
                                    error,
                                    VSCommandName.CODE_CURDT
                                )
                            }
                        }
                        payload.contains(VSCommandName.CODE_TEMPM) -> {
                            //CODE_TEMPM Data Response
                            processTEMPMeas(payload)
                        }
                        payload.contains(VSCommandName.FWUPG) -> {
                            //Firmware Upgrade Data Response
                            processFWUPGData(payload)
                        }
                        payload.contains(VSCommandName.FWDAT) -> {
                            //Firmware Data send Response
                            processFWDATData(payload)


                        }
                        payload.contains(VSCommandName.FWFIN) -> {
                            //Firmware Data send Response
                            processFWFINData(payload)
                        }
                        payload.contains(VSCommandName.RESET) -> {
                            //RESET command response
                            processResetData(payload)
                        }
                        payload.contains(VSCommandName.CODE_MEASU) -> {
                            processMEASUData(payload)
                        }
                    }
                }
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt?,
            descriptor: BluetoothGattDescriptor?,
            status: Int
        ) {
            super.onDescriptorWrite(gatt, descriptor, status)

            gattChar = descriptor!!.characteristic
            if (gattChar != null && gattChar!!.uuid == VSGattAttributes.VVITAL_SERVICE_UUID_ONE) {
                writeDescriptor(batteryServiceOne!!, VSGattAttributes.VVITAL_SERVICE_UUID_TWO)
            }
            if (gattChar != null && gattChar!!.uuid == VSGattAttributes.VVITAL_SERVICE_UUID_TWO) {
                mConnectionState = STATE_CONNECTED
                smartVSCMDCallback!!.onPeripheralStateChange(mConnectionState)
            }

            /* if (isTimerRequire) {
                 startTimer()
             }*/
        }
    }

    private fun startTimer() {
        cTimer = object : CountDownTimer(8000, 2000) {
            override fun onTick(millisUntilFinished: Long) {
//                writeDataToDevice(latestDataArray)
            }

            override fun onFinish() {

            }
        }
    }

    private fun processResetData(payload: String) {
        val ack = payload[10]

        if (ack.toString().equals("1", true)) { //Success
            val responseObject = ResponseObject()
            responseObject.isSuccess = true

//            smartVSCMDCallback!!.onSuccess(responseObject, VSGattAttributes.RESET)
        } else {//Failure
            var offset = 12
            offset += 2
            val errorCode = payload[offset].toString().plus(payload[offset + 1])
            val error = VSError.init(errorCode)
            smartVSCMDCallback!!.onFailure(error, VSCommandName.RESET)
        }
    }

    private fun processFWFINData(payload: String) {
        val ack = payload[10]

        if (ack.toString().equals("1", true)) { //Success
            val responseObject = ResponseObject()
            responseObject.isSuccess = true
//            smartVSCMDCallback!!.onSuccess(responseObject, VSGattAttributes.FWFIN)

//            runOnUiThread {
//                tvProgress.text = "100 %"


            Handler(Looper.getMainLooper()).postDelayed({
                /* if (!isFirmwareFinished) {// firmware update not finish yet
                         mBluetoothLeService!!.setFinishFirmwareUpgrade(2)
                         isFirmwareFinished = true
                     } else {// firmware update finish, time to reset
//                            mBluetoothLeService!!.resetDevice()
                     }*/
                if (!isFirmwareFinished && fileName.equals(encFile, true)) {
                    fileName = binFile
                    getFirmwareFileData(fileName)
                } else {
                    if (!isFirmwareFinished) {// firmware update not finish yet
                        isFirmwareFinished = true
                        fileName = encFile
                        setFinishFirmwareUpgrade(fileName, 2)
                    } else if (isFirmwareFinished && fileName.equals(encFile, true)) {
                        fileName = binFile
                        setFinishFirmwareUpgrade(fileName, 2)

                    } else {// firmware update finish, time to reset
                        resetDevice()
                        val fwData = FWData()
                        fwData.fwStatus = 3

                        val jsonFwData: String = Gson().toJson(fwData)
                        smartVSCMDCallback!!.onSuccess(
                            jsonFwData,
                            VSCommandName.CODE_FWUPGRADE
                        )
                    }
                }

            }, 15)


        } else {//Failure
            var offset = 12
            offset += 2
            val errorCode = payload[offset].toString().plus(payload[offset + 1])
            val error = VSError.init(errorCode)
            smartVSCMDCallback!!.onFailure(error, VSCommandName.FWFIN)
        }
    }

    private fun processFWDATData(payload: String) {
        val ack = payload[10]

        if (ack.toString().equals("1", true)) { //Success
            var offset = 12

            val fwData = FWData()
            fwData.FWUpgradeState = VSBleCommandUtil.hexToInteger(payload[offset].toString())
            offset += 1
            fwData.installedPacketCount =
                VSBleCommandUtil.hexToInteger(payload[offset].toString().plus(payload[offset + 1]))
            offset += 2
            /*fwData.batteryStatus =
                VSBleCommandUtil.hexToInteger(payload[offset].toString().plus(payload[offset + 1]))*/

            when {
                fwData.FWUpgradeState == FWUpgradeState.IDLE -> {
                    VSLog.e("onSuccess", "FWDAT status: " + FWUpgradeState.IDLE)
                }
                fwData.FWUpgradeState == FWUpgradeState.READY -> {
                    VSLog.e("onSuccess", "FWDAT status: " + FWUpgradeState.READY)
                    /* runOnUiThread {
                         Handler().postDelayed({
                             if (mBluetoothLeService != null) {
                                 mBluetoothLeService!!.setStartFirmwareDataTransfer()
                             }
                         }, 2000)
                     }*/

                    fwData.totalPackets = totalPackets
                    fwData.installedPacketCount = currentPacket
                    fwData.fwStatus = 1

                    if (fileName.equals(encFile, true)) {
                        fwData.firmwareType = firmwareMainBoard
                    } else {
                        fwData.firmwareType = firmwareSensorBoard
                    }
                    val jsonFwData: String = Gson().toJson(fwData)
                    smartVSCMDCallback!!.onSuccess(
                        jsonFwData,
                        VSCommandName.CODE_FWUPGRADE
                    )
                    mHandler.sendEmptyMessage(0)
                }
                fwData.FWUpgradeState == FWUpgradeState.BUSY -> {
                    VSLog.e("onSuccess", "FWDAT status: " + FWUpgradeState.BUSY)
                    VSLog.e("onSuccess", "packetCount: " + fwData.installedPacketCount)

                    /*  runOnUiThread {
                          *//* tvProgress.text =
                                 ((response.packetCount * 100) / bytes!!.size).toString() + " %"*//*
                        }*/
                }
                fwData.FWUpgradeState == FWUpgradeState.COMPLETE -> {
                    VSLog.e("onSuccess", "FWDAT status: " + FWUpgradeState.COMPLETE)
//                            tvProgress.text = "100 %"+
                    fwData.totalPackets = totalPackets
                    fwData.installedPacketCount = currentPacket
                    fwData.fwStatus = 1




                    val jsonFwData: String = Gson().toJson(fwData)
                    smartVSCMDCallback!!.onSuccess(
                        jsonFwData,
                        VSCommandName.CODE_FWUPGRADE
                    )

                    Handler(Looper.getMainLooper()).postDelayed({
                        isFirmwareFinished = false
                        setFinishFirmwareUpgrade(fileName, 1)
                    }, 15)
                }
                fwData.FWUpgradeState == FWUpgradeState.ERROR -> {//Error OR TimeOut
                    VSLog.e("onSuccess", "FWDAT status: " + FWUpgradeState.ERROR)
//                        runOnUiThread {
//                            tvProgress.text = "0 %"
//                        }
                    smartVSCMDCallback!!.onException(
                        getString(R.string.str_firmware_failed),
                        VSCommandName.CODE_FWUPGRADE
                    )

                }
            }

        } else {//Failure
            var offset = 12
            offset += 2
            val errorCode = payload[offset].toString().plus(payload[offset + 1])
            val error = VSError.init(errorCode)
            smartVSCMDCallback!!.onFailure(error, VSCommandName.FWDAT)
        }
    }

    private fun processFWUPGData(payload: String) {
        //4- Error => :SRFWUPG1C1,4,smvs_main.enc,0000000000....................................
        //0- Idol => :SRFWUPG201,0,smvs_main.enc.val,03D5505903................................
        val ack = payload[10]

        if (ack.toString().equals("1", true)) { //Success

            var offset = 12

            val fwUpgradeData = FWUpgradeData()
            fwUpgradeData.state = VSBleCommandUtil.hexToInteger(payload[offset].toString())
            offset += 1
            offset += 1

            val cropStr = payload.substring(offset, payload.length)
            fwUpgradeData.fileName = cropStr.substring(0, cropStr.indexOf(","))

            offset += fwUpgradeData.fileName.length
            offset += 1
            fwUpgradeData.size = VSBleCommandUtil.hexToInteger(
                payload[offset].toString().plus(payload[offset + 1]).plus(payload[offset + 2])
                    .plus(payload[offset + 3]).plus(payload[offset + 4]).plus(payload[offset + 5])
            )
            offset += 6
            fwUpgradeData.crc = VSBleCommandUtil.hexToInteger(
                payload[offset].toString().plus(payload[offset + 1])
                    .plus(payload[offset + 2]).plus(payload[offset + 3])
            )

//            smartVSCMDCallback!!.onSuccess(fwUpgradeData, VSGattAttributes.FWUPG)

            VSLog.e("onSuccess", "FWUPG response: $fwUpgradeData")

            when {
                fwUpgradeData.state == FWUpgradeState.IDLE -> {
                    VSLog.e("onSuccess", "FWUPG status: " + FWUpgradeState.IDLE)

                    Handler(Looper.getMainLooper()).postDelayed({
                        if (fwUpgradeData.fileName.contains(VSBleCommandUtil.EXT_INTERMEDIATE)) {//.val
                            setStartFirmwareUpgrade(
                                fileName,
                                fileName.plus(VSBleCommandUtil.EXT_TEMPORARY),
                                0
                            )

                        } else if (fwUpgradeData.fileName.contains(VSBleCommandUtil.EXT_TEMPORARY)) {//.tmp
                            //                                        if(response.size)
                            setStartFirmwareUpgrade(
                                fileName,
                                fileName.plus(VSBleCommandUtil.EXT_TEMPORARY),
                                1
                            )
                        }

                    }, 15)
                }
                fwUpgradeData.state == FWUpgradeState.READY -> {
                    VSLog.e("onSuccess", "FWUPG status: " + FWUpgradeState.READY)
                    Handler(Looper.getMainLooper()).postDelayed({
                        setStartFirmwareDataTransfer()
                        /*if (mBluetoothLeService != null) {
                            isFirmwareFinished = false
                            mBluetoothLeService!!.setFinishFirmwareUpgrade(1)
                        }*/
                    }, 15)

                }
                fwUpgradeData.state == FWUpgradeState.BUSY -> {
                    VSLog.e("onSuccess", "FWUPG status: " + FWUpgradeState.BUSY)
                }
                fwUpgradeData.state == FWUpgradeState.COMPLETE -> {
                    VSLog.e("onSuccess", "FWUPG status: " + FWUpgradeState.COMPLETE)
                }
                fwUpgradeData.state == FWUpgradeState.ERROR -> {//Error OR TimeOut
                    VSLog.e("onSuccess", "FWUPG status: " + FWUpgradeState.ERROR)
                    Handler(Looper.getMainLooper()).postDelayed({
                        if (fwUpgradeData.fileName.contains(VSBleCommandUtil.EXT_INTERMEDIATE)) {//.val
                            isTmpError = false
                            setStartFirmwareUpgrade(
                                fileName,
                                fileName.plus(VSBleCommandUtil.EXT_TEMPORARY),
                                0
                            )

                        } else if (fwUpgradeData.fileName.contains(VSBleCommandUtil.EXT_TEMPORARY)) {//.temp
                            if (isTmpError) {
                                smartVSCMDCallback!!.onException(
                                    getString(R.string.str_firmware_failed_reboot_device),
                                    VSCommandName.CODE_FWUPGRADE
                                )
                            } else {
                                isTmpError = true
                                setStartFirmwareUpgrade(
                                    fileName,
                                    fileName.plus(VSBleCommandUtil.EXT_TEMPORARY),
                                    1
                                )
                            }


                        } else {//.val
                            setStartFirmwareUpgrade(
                                fileName,
                                fileName.plus(VSBleCommandUtil.EXT_INTERMEDIATE),
                                0
                            )
                        }
                    }, 15)

                }
            }

        } else {//Failure
            var offset = 12
            offset += 2
            val errorCode = payload[offset].toString().plus(payload[offset + 1])
            val error = VSError.init(errorCode)
            smartVSCMDCallback!!.onFailure(error, VSCommandName.FWUPG)
        }
    }

    /**
     * method to parse the SMDUR Data
     **/
    private fun processTEMPMeas(payload: String) {
        //:SRTEMPM101,10000000000000 - ready to start
        //:SRTEMPM101,30016D01560115 - result
        //:SRTEMPM101,45012D011E0117 - Error

        val ack = payload[10]

        if (ack.toString().equals("1", true)) { //Success

            var offset = 12

            val tempMeasData = TempMeasData()
            tempMeasData.status = VSBleCommandUtil.hexToInteger(payload[offset].toString())
            offset += 1
            tempMeasData.tempErrorReason = payload[offset].toString().plus(payload[offset + 1])

            val error = VSError.init(tempMeasData.tempErrorReason)
            tempMeasData.errorDesc = error.localizedDescription.toString()
            offset += 2

            /*tempMeasData.bodyTemperature = VSBleCommandUtil.convertCelsiusToFahrenheit(
                0.1 * (VSBleCommandUtil.hexToInteger(
                    payload[offset].toString().plus(payload[offset + 1])
                        .plus(payload[offset + 2]).plus(payload[offset + 3])
                ))
            ).toString()*/

            val tempVal = VSBleCommandUtil.hexToInteger(
                payload[offset].toString().plus(payload[offset + 1]).plus(payload[offset + 2]).plus(
                    payload[offset + 3]
                )
            )
            tempMeasData.bodyTemperature =
                if (checkAllZeros(tempVal.toString())) getString(R.string.no_valid_result) else ((tempVal / 10.00).toString())

            offset += 4
            /*tempMeasData.surfaceTemperature = VSBleCommandUtil.convertCelsiusToFahrenheit(
                0.1 * (VSBleCommandUtil.hexToInteger(
                    payload[offset].toString().plus(payload[offset + 1])
                        .plus(payload[offset + 2]).plus(payload[offset + 3])
                ))
            ).toString()*/
            offset += 4

            /*tempMeasData.ambientTemperature = VSBleCommandUtil.convertCelsiusToFahrenheit(
                0.1 * (VSBleCommandUtil.hexToInteger(
                    payload[offset].toString().plus(payload[offset + 1])
                        .plus(payload[offset + 2]).plus(payload[offset + 3])
                ))
            ).toString()*/

            val jsonTempMeasData: String = Gson().toJson(tempMeasData)
            smartVSCMDCallback!!.onSuccess(jsonTempMeasData, VSCommandName.CODE_TEMPM)
        } else {//Failure
            var offset = 12
            offset += 2
            val errorCode = payload[offset].toString().plus(payload[offset + 1])
            val error = VSError.init(errorCode)
            smartVSCMDCallback!!.onFailure(error, VSCommandName.CODE_TEMPM)
        }
    }

    /**
     * method to parse the SMDUR Data
     **/
    private fun processSMDURData(payload: String) {
        val ack = payload[10]

        if (ack.toString().equals("1", true)) { //Success
            val offset = 12
            val slaveDuration = VSBleCommandUtil.hexToInteger(
                payload[offset].toString().plus(payload[offset + 1]).plus(payload[offset + 2]).plus(
                    payload[offset + 3]
                )
            )

            val sMDURData = SMDURData()
            sMDURData.timeOut = slaveDuration

            //Getting device info

            Handler(Looper.getMainLooper()).postDelayed({
                getDeviceInformation()
            }, 50)

            val jsonSMDURData: String = Gson().toJson(sMDURData)
            smartVSCMDCallback!!.onSuccess(jsonSMDURData, VSCommandName.SMDUR)
        } else {//Failure
            var offset = 12
            offset += 2
            val errorCode = payload[offset].toString().plus(payload[offset + 1])
            val error = VSError.init(errorCode)
            smartVSCMDCallback!!.onFailure(error, VSCommandName.SMDUR)
        }
    }

    /**
     * isPeripheralConnected() : Function is used to check if any device is connected
     **/
    fun isDeviceConnected(): Boolean {
        return mConnectionState == STATE_CONNECTED
    }

    private fun processMEASUData(payload: String) {
        VSLog.e("processMEASUData", "payload:$payload")//bytes

        val ack = payload[10]

        if (ack.toString().equals("1", true)) { //Success

            val measuInfo = MeasuInfo()

            var offset = 12

            //:SRMEASU371,1 0000 000F 0000 0 000 00 00 00 00 00 00 00 00 0000 0000 00 00DE 0000 00.........
            measuInfo.status = VSBleCommandUtil.hexToInteger(payload[offset].toString())

            offset += 1
            measuInfo.reserved1 = checkInvalidResult(payload.substring((offset), (offset + 4)))
            offset += 4
            measuInfo.measurmentDuration =
                checkInvalidResult(payload.substring((offset), (offset + 4)))
            offset += 4
            measuInfo.reserved2 = checkInvalidResult(payload.substring((offset), (offset + 4)))
            offset += 4
            measuInfo.SNR = checkInvalidResult(payload[offset].toString())
            offset += 1
            measuInfo.perfusionIndexResult = checkInvalidResult(
                payload.substring((offset), (offset + 3)),
                percentage = true,
                checkRange = true
            )
            offset += 3
            measuInfo.SNRmDLS_1 =  getString(R.string.no_valid_result)//checkInvalidResult(payload.substring((offset), (offset + 2)))
            offset += 2
            measuInfo.SNRmDLS_2 =  getString(R.string.no_valid_result)//checkInvalidResult(payload.substring((offset), (offset + 2)))
            offset += 2
            measuInfo.pulseRate = checkInvalidResult(payload.substring((offset), (offset + 2)))
            offset += 2
            measuInfo.spo2 = checkInvalidResult(payload.substring((offset), (offset + 2)))
            offset += 2
            measuInfo.BP_Diastolic =
                checkInvalidResult(payload.substring((offset), (offset + 2)))
            offset += 2
            measuInfo.BP_Systolic =
                checkInvalidResult(payload.substring((offset), (offset + 2)))
            offset += 2
            measuInfo.respiration =
                checkInvalidResult(payload.substring((offset), (offset + 2)))
            offset += 2
            measuInfo.PRV = checkInvalidResult(payload.substring((offset), (offset + 2)))
            offset += 2
            measuInfo.PVI =
                checkInvalidResult(
                    payload.substring((offset), (offset + 4)),
                    percentage = true,
                    checkRange = true
                )
            offset += 4
            /*  measuInfo.pneumaticSystemPressure =
              VSBleCommandUtil.hexToInteger(payload.substring((offset), (offset + 4))).toString()*/
            offset += 4
            measuInfo.batteryStatus =
                VSBleCommandUtil.hexToInteger(payload.substring((offset), (offset + 2)))
                    .toString()
            offset += 2
            /*measuInfo.ambientTempSensorBoard =
            VSBleCommandUtil.hexToInteger(payload.substring((offset), (offset + 4))).toString()*/
            offset += 4
            /* measuInfo.ambientTempMotherBoard =
             VSBleCommandUtil.hexToInteger(payload.substring((offset), (offset + 4))).toString()*/
            offset += 4
            measuInfo.errorCode = payload.substring((offset), (offset + 2))

            //on status 4 - ERROR status
            if (measuInfo.status == FWUpgradeState.ERROR) {
                val error = VSError.init(measuInfo.errorCode)
                measuInfo.errorDesc = error.localizedDescription.toString()
            }

            val jsonMeasuResponse: String = Gson().toJson(measuInfo)
            smartVSCMDCallback!!.onSuccess(jsonMeasuResponse, VSCommandName.CODE_MEASU)

        } else {//Failure
            var offset = 12
            offset += 2
            val errorCode = payload[offset].toString().plus(payload[offset + 1])
            val error = VSError.init(errorCode)
            smartVSCMDCallback!!.onFailure(error, VSCommandName.CODE_MEASU)
        }
    }

    /*
    * 1.check string contains all zeros and F's.
    * 2.check range 0-2000.
    * 3.convert valid result to percentage.
    * */

    private fun checkInvalidResult(
        str: String,
        convertToDecimalFormat: Boolean = true,
        percentage: Boolean = false,
        checkRange: Boolean = false
    ): String {

        if (checkAllZeros(str)) {
            //return error when string has all zeros.
            return getString(R.string.no_valid_result)
        }

        if (checkAllF(str)) {
            //return error when string has all F's.
            return getString(R.string.measurement_not_selected)
        }

        val intStr: Int = Integer.parseInt(str, 16)
        /* Range: 0-2000 (0.02% to 20%) i.e. each step is 0.01%.Reported once.
            * Below 0.02%, a Finger Not Present error will occur.
            * Above 20%, TBC SNR failure.
            * */
        if (checkRange) {
            if (intStr <= 0) {
                return getString(R.string.finger_not_present)
            } else if (intStr >= 2000) {
                return getString(R.string.SNR_failure)
            }
        }

        //create percentage and return
        if (percentage) {
            return (intStr / 100.00).toString()
        }

        //convert to decimal and return.
        if (convertToDecimalFormat) {
            return intStr.toBigDecimal().toString()
        }

        return str
    }

    /*Returns true if has all zeros*/
    private fun checkAllZeros(str: String): Boolean {
        /*check string has all zeros.*/
        var hasAllZeros = true
        for (c in str.toCharArray()) {
            if ((c != '0')) {
                hasAllZeros = false
                break
            }
        }
        return hasAllZeros
    }

    private fun checkAllF(str: String): Boolean {
        /*check string has all F's*/
        var hasAllF = true
        for (c in str.toCharArray()) {
            if ((c != 'F')) {
                hasAllF = false
                break
            }
        }
        return hasAllF
    }

    /**
     * method to parse the device information properties from the payload data
     *
     *  valid reponses include:
     *  NACK: '0,xxxx' where xxxx is the 4 digit error code
     *  ACK: '1,<device information>' (see spec for details)
     */
    private fun processDeviceInfoData(payload: String) {

        val ack = payload[10]

        if (ack.toString().equals("1", true)) { //Success

            if ((payload.length > 11) && (!payload[11].toString().equals(
                    VSGattAttributes.PAYLOAD_SEPARATOR,
                    true
                ))
            ) {
                smartVSCMDCallback!!.onException(
                    getString(R.string.str_invalid_info_exception),
                    VSCommandName.CODE_DINFO
                )
                return
            }

            //:SRDINFO3E1, 10000025 00000000 A72B 5004 0001 0030 7FFFFFFF 7FFFFFFF 1B 01 2427 9300
            deviceInfo = DeviceInfo()

            when {
                payload.length == 76 -> {
                    var offset = 12
                    deviceInfo.uniqueID =
                        payload[offset].toString().plus(payload[offset + 1])
                            .plus(payload[offset + 2])
                            .plus(payload[offset + 3])
                            .plus(payload[offset + 4]).plus(payload[offset + 5])
                            .plus(payload[offset + 6]).plus(payload[offset + 7])
                    offset += 8
                    deviceInfo.fingerCuffSerialnumber =
                        payload[offset].toString().plus(payload[offset + 1])
                            .plus(payload[offset + 2])
                            .plus(payload[offset + 3])
                            .plus(payload[offset + 4]).plus(payload[offset + 5])
                            .plus(payload[offset + 6]).plus(payload[offset + 7])
                    offset += 8
                    deviceInfo.softwareMainMajor =
                        VSBleCommandUtil.hexToInteger(payload[offset].toString())
                    offset += 1
                    deviceInfo.softwareMainMinor =
                        VSBleCommandUtil.hexToInteger(payload[offset].toString())
                    offset += 1
                    deviceInfo.softwareMainBuild =
                        VSBleCommandUtil.hexToInteger(payload[offset].toString().plus(payload[offset + 1]))
                    offset += 2
                    deviceInfo.softwareSensorMajor =
                        VSBleCommandUtil.hexToInteger(payload[offset].toString())
                    offset += 1
                    deviceInfo.softwareSensorMinor =
                        VSBleCommandUtil.hexToInteger(payload[offset].toString())
                    offset += 1
                    deviceInfo.softwareSensorBuild =
                        VSBleCommandUtil.hexToInteger(payload[offset].toString().plus(payload[offset + 1]))
                    offset += 2
                    deviceInfo.hardwareSensorMajor =
                        VSBleCommandUtil.hexToInteger(payload[offset].toString())
                    offset += 1
                    deviceInfo.hardwareSensorMinor =
                        VSBleCommandUtil.hexToInteger(payload[offset].toString())
                    offset += 1
                    deviceInfo.hardwareSensorBuild =
                        VSBleCommandUtil.hexToInteger(payload[offset].toString().plus(payload[offset + 1]))
                    offset += 2
                    deviceInfo.hardwareMainMajor =
                        VSBleCommandUtil.hexToInteger(payload[offset].toString())
                    offset += 1
                    deviceInfo.hardwareMainMinor =
                        VSBleCommandUtil.hexToInteger(payload[offset].toString())
                    offset += 1
                    deviceInfo.hardwareMainBuild =
                        VSBleCommandUtil.hexToInteger(payload[offset].toString().plus(payload[offset + 1]))
                    offset += 2
                    deviceInfo.totalCapacity = VSBleCommandUtil.hexToInteger(
                        payload[offset].toString().plus(payload[offset + 1]).plus(payload[offset + 2])
                            .plus(payload[offset + 3]).plus(payload[offset + 4]).plus(payload[offset + 5]).plus(
                                payload[offset + 6]
                            ).plus(payload[offset + 7])
                    )
                    offset += 8
                    deviceInfo.availSpace = VSBleCommandUtil.hexToInteger(
                        payload[offset].toString().plus(payload[offset + 1]).plus(payload[offset + 2])
                            .plus(payload[offset + 3]).plus(payload[offset + 4]).plus(payload[offset + 5]).plus(
                                payload[offset + 6]
                            ).plus(payload[offset + 7])
                    )
                    offset += 8

                    val batteryBinary =
                        VSBleCommandUtil.hexToBinary(payload[offset].toString().plus(payload[offset + 1]))
                    if (batteryBinary.length == 8) {
                        batteryBinary.substring(1, 7)
                    }
                    deviceInfo.batteryStatus = Integer.parseInt(batteryBinary, 2)

                    offset += 2

                    /*deviceInfo.ambientTemp = VSBleCommandUtil.hexToInteger(
                        payload[offset].toString().plus(payload[offset + 1])
                            .plus(payload[offset + 2]).plus(payload[offset + 3])
                    )*/

                    offset += 4
                    deviceInfo.noOfBladderInflation = VSBleCommandUtil.hexToInteger(
                        payload[offset].toString().plus(payload[offset + 1])
                            .plus(payload[offset + 2]).plus(payload[offset + 3])
                    ).toString()

                    val jsonDeviceInfo: String = Gson().toJson(deviceInfo)
                    smartVSCMDCallback!!.onSuccess(jsonDeviceInfo, VSCommandName.CODE_DINFO)

                }
                /*payload.length == 76 -> {

                    var offset = 12
                    deviceInfo.uniqueID =
                        payload[offset].toString().plus(payload[offset + 1]).plus(payload[offset + 2])
                            .plus(payload[offset + 3])
                            .plus(payload[offset + 4]).plus(payload[offset + 5])
                            .plus(payload[offset + 6]).plus(payload[offset + 7])
                    offset += 8
                    deviceInfo.softwareMainMajor =
                        VSBleCommandUtil.hexToInteger(payload[offset].toString())
                    offset += 1
                    deviceInfo.softwareMainMinor =
                        VSBleCommandUtil.hexToInteger(payload[offset].toString())
                    offset += 1
                    deviceInfo.softwareMainBuild =
                        VSBleCommandUtil.hexToInteger(payload[offset].toString().plus(payload[offset + 1]))
                    offset += 2
                    deviceInfo.softwareSensorMajor =
                        VSBleCommandUtil.hexToInteger(payload[offset].toString())
                    offset += 1
                    deviceInfo.softwareSensorMinor =
                        VSBleCommandUtil.hexToInteger(payload[offset].toString())
                    offset += 1
                    deviceInfo.softwareSensorBuild =
                        VSBleCommandUtil.hexToInteger(payload[offset].toString().plus(payload[offset + 1]))
                    offset += 2
                    deviceInfo.hardwareSensorMajor =
                        VSBleCommandUtil.hexToInteger(payload[offset].toString())
                    offset += 1
                    deviceInfo.hardwareSensorMinor =
                        VSBleCommandUtil.hexToInteger(payload[offset].toString())
                    offset += 1
                    deviceInfo.hardwareSensorBuild =
                        VSBleCommandUtil.hexToInteger(payload[offset].toString().plus(payload[offset + 1]))
                    offset += 2
                    deviceInfo.hardwareMainMajor =
                        VSBleCommandUtil.hexToInteger(payload[offset].toString())
                    offset += 1
                    deviceInfo.hardwareMainMinor =
                        VSBleCommandUtil.hexToInteger(payload[offset].toString())
                    offset += 1
                    deviceInfo.hardwareMainBuild =
                        VSBleCommandUtil.hexToInteger(payload[offset].toString().plus(payload[offset + 1]))
                    offset += 2
                    deviceInfo.totalCapacity = VSBleCommandUtil.hexToInteger(
                        payload[offset].toString().plus(payload[offset + 1]).plus(payload[offset + 2])
                            .plus(payload[offset + 3]).plus(payload[offset + 4]).plus(payload[offset + 5]).plus(
                                payload[offset + 6]
                            ).plus(payload[offset + 7])
                    )
                    offset += 8
                    deviceInfo.availSpace = VSBleCommandUtil.hexToInteger(
                        payload[offset].toString().plus(payload[offset + 1]).plus(payload[offset + 2])
                            .plus(payload[offset + 3]).plus(payload[offset + 4]).plus(payload[offset + 5]).plus(
                                payload[offset + 6]
                            ).plus(payload[offset + 7])
                    )
                    offset += 8
                    deviceInfo.batteryStatus =
                        VSBleCommandUtil.hexToInteger(payload[offset].toString().plus(payload[offset + 1]))
                    offset += 2
                    deviceInfo.ambientTemp = VSBleCommandUtil.hexToInteger(
                        payload[offset].toString().plus(payload[offset + 1])
                            .plus(payload[offset + 2]).plus(payload[offset + 3])
                    )
                    offset += 4
                    deviceInfo.protocolRevision = VSBleCommandUtil.hexToInteger(
                        payload[offset].toString().plus(payload[offset + 1])
                            .plus(payload[offset + 2]).plus(payload[offset + 3])
                    )

                    *//* SmartVSLog.e(
                             "DeviceInfo:",
                             "uniqueID:" + deviceInfo.uniqueID + ", totalCapacity:" + deviceInfo.totalCapacity
                                     + ", Battery Status:" + deviceInfo.batteryStatus + ", Ambient temp" + deviceInfo.ambientTemp
                         )*//*

                    smartVSCMDCallback!!.onSuccess(deviceInfo, VSGattAttributes.CODE_DINFO)
                }*/
                payload.length == 80 -> {
                    var offset = 12
                    deviceInfo.uniqueID =
                        payload[offset].toString().plus(payload[offset + 1])
                            .plus(payload[offset + 2])
                            .plus(payload[offset + 3])
                            .plus(payload[offset + 4]).plus(payload[offset + 5])
                            .plus(payload[offset + 6]).plus(payload[offset + 7])
                    offset += 8
                    deviceInfo.softwareMainMajor =
                        VSBleCommandUtil.hexToInteger(payload[offset].toString().plus(payload[offset + 1]))
                    offset += 2
                    deviceInfo.softwareMainMinor =
                        VSBleCommandUtil.hexToInteger(payload[offset].toString().plus(payload[offset + 1]))
                    offset += 2
                    deviceInfo.softwareMainBuild =
                        VSBleCommandUtil.hexToInteger(payload[offset].toString().plus(payload[offset + 1]))
                    offset += 2
                    deviceInfo.softwareSensorMajor =
                        VSBleCommandUtil.hexToInteger(payload[offset].toString().plus(payload[offset + 1]))
                    offset += 2
                    deviceInfo.softwareSensorMinor =
                        VSBleCommandUtil.hexToInteger(payload[offset].toString().plus(payload[offset + 1]))
                    offset += 2
                    deviceInfo.softwareSensorBuild =
                        VSBleCommandUtil.hexToInteger(payload[offset].toString().plus(payload[offset + 1]))
                    offset += 2
                    deviceInfo.hardwareSensorMajor =
                        VSBleCommandUtil.hexToInteger(payload[offset].toString())
                    offset += 1
                    deviceInfo.hardwareSensorMinor =
                        VSBleCommandUtil.hexToInteger(payload[offset].toString())
                    offset += 1
                    deviceInfo.hardwareSensorBuild =
                        VSBleCommandUtil.hexToInteger(payload[offset].toString().plus(payload[offset + 1]))
                    offset += 2
                    deviceInfo.hardwareMainMajor =
                        VSBleCommandUtil.hexToInteger(payload[offset].toString())
                    offset += 1
                    deviceInfo.hardwareMainMinor =
                        VSBleCommandUtil.hexToInteger(payload[offset].toString())
                    offset += 1
                    deviceInfo.hardwareMainBuild =
                        VSBleCommandUtil.hexToInteger(payload[offset].toString().plus(payload[offset + 1]))
                    offset += 2
                    deviceInfo.totalCapacity = VSBleCommandUtil.hexToInteger(
                        payload[offset].toString().plus(payload[offset + 1]).plus(payload[offset + 2])
                            .plus(payload[offset + 3]).plus(payload[offset + 4]).plus(payload[offset + 5]).plus(
                                payload[offset + 6]
                            ).plus(payload[offset + 7])
                    )
                    offset += 8
                    deviceInfo.availSpace = VSBleCommandUtil.hexToInteger(
                        payload[offset].toString().plus(payload[offset + 1]).plus(payload[offset + 2])
                            .plus(payload[offset + 3]).plus(payload[offset + 4]).plus(payload[offset + 5]).plus(
                                payload[offset + 6]
                            ).plus(payload[offset + 7])
                    )
                    offset += 8
                    deviceInfo.batteryStatus =
                        VSBleCommandUtil.hexToInteger(payload[offset].toString().plus(payload[offset + 1]))
                    offset += 2

                    /*deviceInfo.ambientTemp = VSBleCommandUtil.hexToInteger(
                        payload[offset].toString().plus(payload[offset + 1])
                            .plus(payload[offset + 2]).plus(payload[offset + 3])
                    )*/

                    offset += 4
                    deviceInfo.noOfBladderInflation = VSBleCommandUtil.hexToInteger(
                        payload[offset].toString().plus(payload[offset + 1])
                            .plus(payload[offset + 2]).plus(payload[offset + 3])
                    ).toString()

                    /*  SmartVSLog.e(
                              "DeviceInfo:",
                              "uniqueID:" + deviceInfo.uniqueID + ", totalCapacity:" + deviceInfo.totalCapacity
                                      + ", Battery Status:" + deviceInfo.batteryStatus + ", Ambient temp" + deviceInfo.ambientTemp
                          )*/
                    val jsonDeviceInfo: String = Gson().toJson(deviceInfo)
                    smartVSCMDCallback!!.onSuccess(jsonDeviceInfo, VSCommandName.CODE_DINFO)
                }
            }
        } else {
            //Failure
            var offset = 12
            offset += 2
            val errorCode = payload[offset].toString().plus(payload[offset + 1])
            val error = VSError.init(errorCode)
            smartVSCMDCallback!!.onFailure(error, VSCommandName.CODE_DINFO)
        }
    }

    private fun notifyGattCharacteristics() {
        batteryServiceOne =
            mBluetoothGatt!!.getService(VSGattAttributes.VVITAL_CHARACTERISTIC_CONFIG)

        if (batteryServiceOne != null) {
            writeDescriptor(batteryServiceOne!!, VSGattAttributes.VVITAL_SERVICE_UUID_ONE)
        } else {
            VSLog.d(TAG, "Battery service not found!")
        }
    }

    private fun writeDescriptor(
        batteryServiceOne: BluetoothGattService,
        vvitalServiceUuidTwo: UUID
    ) {
        val characteristicTwo =
            batteryServiceOne.getCharacteristic(vvitalServiceUuidTwo)

        if (characteristicTwo != null) {
            //notify
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    return
                }
            }
            mBluetoothGatt!!.setCharacteristicNotification(characteristicTwo, true)

            val descriptor = characteristicTwo.getDescriptor(
                UUID.fromString(VSGattAttributes.VVITAL_CHARACTERISTIC_DESCRIPTOR_TWO)
            )
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            //write descriptor
            mBluetoothGatt!!.writeDescriptor(descriptor)

        }
    }

    inner class LocalBinder : Binder() {
        val service: VVitalManager
            get() = this@VVitalManager
    }

    override fun onBind(intent: Intent): IBinder? {
        return mBinder
    }

    override fun onUnbind(intent: Intent): Boolean {
        /* After using a given device, you should make sure that BluetoothGatt.close() is called such that resources are cleaned up properly.
         In this particular example, close() is invoked when the Contacts.Intents.UI is disconnected from the Service.*/
        close()
        return super.onUnbind(intent)
    }

    private val mBinder = LocalBinder()

    /**
     * Initializes a reference to the local Bluetooth adapter.

     * @return Return true if the initialization is successful.
     */
    fun initialize(): Boolean {
        // For API level 18 and above, get a reference to BluetoothAdapter through BluetoothManager.
        if (mBluetoothManager == null) {
            mBluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            if (mBluetoothManager == null) {
                VSLog.e(TAG, "Unable to initialize BluetoothManager.")
                return false
            }
        }

        mBluetoothAdapter = mBluetoothManager!!.adapter
        if (mBluetoothAdapter == null) {
            VSLog.e(TAG, "Unable to obtain a BluetoothAdapter.")
            return false
        }

        return true
    }

    /**
     * Connects to the GATT server hosted on the Bluetooth LE device.

     * @param address The device address of the destination device.
     * *
     * *
     * @return Return true if the connection is initiated successfully. The connection result
     * *         is reported asynchronously through the
     * *         `BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)`
     * *         callback.
     */
    fun connectPeripheral(address: String?): Boolean {
        if (mBluetoothAdapter == null || address == null) {
            VSLog.w(TAG, "BluetoothAdapter not initialized or unspecified address.")
            return false
        }

        // Previously connected device.  Try to reconnect.
        if (mBluetoothDeviceAddress != null && address == mBluetoothDeviceAddress
            && mBluetoothGatt != null
        ) {
            VSLog.d(TAG, "Trying to use an existing mBluetoothGatt for connection.")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    return false
                }
            }
            return if (mBluetoothGatt!!.connect()) {
                mConnectionState =
                    STATE_CONNECTING

                smartVSCMDCallback!!.onPeripheralStateChange(mConnectionState)
                true
            } else {
                false
            }
        }

        val device = mBluetoothAdapter!!.getRemoteDevice(address)
        if (device == null) {
            VSLog.w(TAG, "Device not found.  Unable to connectPeripheral.")
            return false
        }
        // We want to directly connectPeripheral to the device, so we are setting the autoConnect
        // parameter to false.
        mBluetoothGatt = device.connectGatt(this, false, mGattCallback)
        VSLog.d(TAG, "Trying to create a new connection.")
        mBluetoothDeviceAddress = address
        mConnectionState = STATE_CONNECTING
        smartVSCMDCallback!!.onPeripheralStateChange(mConnectionState)
        return true
    }

    /**
     * Disconnects an existing connection or cancel a pending connection. The disconnection result
     * is reported asynchronously through the
     * `BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)`
     * callback.
     */
    fun disconnectPeripheral() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            VSLog.w(TAG, "BluetoothAdapter not initialized")
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
        }
        mBluetoothGatt!!.disconnect()
    }

    /**
     * After using a given BLE device, the app must call this method to ensure resources are
     * released properly.
     */
    private fun close() {
        if (mBluetoothGatt == null) {
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
        }
        mBluetoothGatt!!.close()
        mBluetoothGatt = null
    }

    fun setSmartVSCMDListener(cMDCallback: PeripheralCallback) {
        smartVSCMDCallback = cMDCallback
    }

    /**
     * stopTemperature() : Function is used to stop taking Temperature Measurement
     */
    fun stopTemperature() {
        if (mConnectionState == STATE_CONNECTED) {

            var payload = ""
            payload += VSBleCommandUtil.SOP
            payload += VSBleCommandUtil.SENDER_MASTER
            payload += VSBleCommandUtil.TYPE_SET
            payload += VSCommandName.CODE_TEMPM

            var requestPayload = ""

            requestPayload += StatusOptions.STOP.type  // get opt value 0 || 1 || 2

            payload += String.format("%02X", requestPayload.length) // 05 length of request command

            payload += requestPayload.padEnd( //0520700
                VSBleCommandUtil.PAYLOAD_MASTER_LENGTH,
                VSBleCommandUtil.PAYLOAD_PAD
            )
            payload += VSBleCommandUtil.EOP

            VSLog.d("TempMeas Command", "payload: $payload")

            ///////////////////////////////
            sendPayloadToDevice(payload.toByteArray(Charsets.UTF_8), 10)
        } else {
            smartVSCMDCallback!!.onException(
                getString(R.string.str_not_connected), VSCommandName.CODE_TEMPM
            )
        }
    }

    /**
     * readyToStartTemperature() : Function is used to make device ready to start taking Temperature Measurement
     */
    fun readyToStartTemperature() {

        if (mConnectionState == STATE_CONNECTED) {
            var payload = ""
            payload += VSBleCommandUtil.SOP
            payload += VSBleCommandUtil.SENDER_MASTER
            payload += VSBleCommandUtil.TYPE_SET
            payload += VSCommandName.CODE_TEMPM

            var requestPayload = ""

            requestPayload += StatusOptions.READY_TO_START.type  // get opt value 0 || 1 || 2

            payload += String.format("%02X", requestPayload.length) // 05 length of request command

            payload += requestPayload.padEnd( //0520700
                VSBleCommandUtil.PAYLOAD_MASTER_LENGTH,
                VSBleCommandUtil.PAYLOAD_PAD
            )
            payload += VSBleCommandUtil.EOP

            VSLog.d("TempMeas Command", "payload: $payload")

            ///////////////////////////////
            sendPayloadToDevice(payload.toByteArray(Charsets.UTF_8), 10)
        } else {
            smartVSCMDCallback!!.onException(
                getString(R.string.str_not_connected), VSCommandName.CODE_TEMPM
            )
        }
    }

    /**
     * startTemperature() : Function is used to start taking Temperature Measurement
     */
    fun startTemperature() {
        if (mConnectionState == STATE_CONNECTED) {

            var payload = ""
            payload += VSBleCommandUtil.SOP
            payload += VSBleCommandUtil.SENDER_MASTER
            payload += VSBleCommandUtil.TYPE_SET
            payload += VSCommandName.CODE_TEMPM

            var requestPayload = ""

            requestPayload += StatusOptions.START.type  // get opt value 0 || 1 || 2

            payload += String.format("%02X", requestPayload.length) // 05 length of request command

            payload += requestPayload.padEnd( //0520700
                VSBleCommandUtil.PAYLOAD_MASTER_LENGTH,
                VSBleCommandUtil.PAYLOAD_PAD
            )
            payload += VSBleCommandUtil.EOP

            VSLog.d("TempMeas Command", "payload: $payload")

            ///////////////////////////////
            sendPayloadToDevice(payload.toByteArray(Charsets.UTF_8), 10)
        } else {
            smartVSCMDCallback!!.onException(
                getString(R.string.str_not_connected), VSCommandName.CODE_TEMPM
            )
        }
    }

    private fun setStartFirmwareUpgrade(fName: String, tempName: String, query: Int) {
        if (mConnectionState == STATE_CONNECTED) {

            fileName = fName
            val requestPayload = ""
            var payload = ""
            payload += VSBleCommandUtil.SOP
            payload += VSBleCommandUtil.SENDER_MASTER
            payload += VSBleCommandUtil.TYPE_SET
            payload += VSCommandName.FWUPG

            /*  val totalPackets = (fileData!!.size / 240) + 1
              val byteSize = totalPackets * 240*/

            val byteSize = fileData!!.size
            payload += String.format(
                "%02X", tempName.length + VSBleCommandUtil.PAYLOAD_SEPARATOR.toString().length +
                        6 + 1
            )

            payload += tempName
            payload += VSBleCommandUtil.PAYLOAD_SEPARATOR
            payload += integerToHex(byteSize, 6)
            payload += query

            //var payload=":MSFWUPG15smvs_main.enc,03D5500"
            payload += requestPayload.padEnd(
                VSBleCommandUtil.PAYLOAD_MASTER_LENGTH -
                        (tempName.length + VSBleCommandUtil.PAYLOAD_SEPARATOR.toString().length +
                                byteSize.toString().length + 1),
                VSBleCommandUtil.PAYLOAD_PAD
            )
            payload += VSBleCommandUtil.EOP

            VSLog.e("FWDAT writePayload", ">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>$payload")

            sendPayloadToDevice(addBytesAsChar(payload).toByteArray(), false)
        } else {
            smartVSCMDCallback!!.onException(
                getString(R.string.str_not_connected), VSCommandName.FWUPG
            )
        }
    }

    private fun integerToHex(data: Int, capacity: Int): String {
        return String.format("%0" + capacity + "X", data)

    }

    private fun decodeBase64(coded: String): String {
        var valueDecoded = ByteArray(0)
        try {
            valueDecoded = android.util.Base64.decode(
                coded.toByteArray(charset("UTF-8")),
                android.util.Base64.DEFAULT
            )
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
        return String(valueDecoded)
    }

    private fun setStartFirmwareDataTransfer() {
        if (mConnectionState == STATE_CONNECTED) {

//            Thread.sleep(15)

            if (fileData != null) {

                // MIT - Success Request Packet

                VSLog.e("FWDAT currentPacket", "currentPacket:$currentPacket")

                var payload = ""
                payload += VSBleCommandUtil.SOP
                payload += VSBleCommandUtil.SENDER_MASTER
                payload += VSBleCommandUtil.TYPE_SET
                payload += VSCommandName.FWDAT

                if (currentPacket == 1) {
                    filDataList = divideArray(fileData!!, 240)
                    totalPackets = filDataList!!.size
                }

                payload += String.format(
                    "%02X", ((filDataList!![currentPacket - 1].size) +
                            String.format("%02X", packetCount).length)
                )

                payload += String.format("%02X", packetCount)


                val byArray = filDataList!![currentPacket - 1]


                VSLog.e("FWDAT writePayload", ">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>$payload")
                //Load MSB Data
                var byteArray = payload.toByteArray(Charsets.UTF_8)

                //
                if (byArray != null) {
                    byteArray += byArray
                }


                if (currentPacket == totalPackets) {
                    val tempPayload = ""
                    val paddingEnd = tempPayload.padEnd(
                        VSBleCommandUtil.PAYLOAD_FWDATA_MASTER_LENGTH -
                                (byArray.size + payload.length),
                        VSBleCommandUtil.PAYLOAD_PAD
                    )
                    byteArray += paddingEnd.toByteArray(Charsets.UTF_8)
                }


                byteArray += "\n".toByteArray(Charsets.UTF_8)
                byteArray += "\r".toByteArray(Charsets.UTF_8)

                sendPayloadToDevice(byteArray, 15)
            } else {
                VSLog.e("ERROR ------>", "FileData not found")
            }
        }
    }

    private fun setFinishFirmwareUpgrade(fileName: String, cmdCount: Int) {

        if (mConnectionState == STATE_CONNECTED) {
            this.fileName = fileName
            val requestPayload = ""

            var payload = ""
            payload += VSBleCommandUtil.SOP
            payload += VSBleCommandUtil.SENDER_MASTER
            payload += VSBleCommandUtil.TYPE_SET
            payload += VSCommandName.FWFIN

            var dataCrcBytes: String = ""

            if (fileName.contains(VSBleCommandUtil.EXT_ENC)) {
                dataCrcBytes = VSBleCommandUtil.integerToHex(crcEnc)
            } else {
                dataCrcBytes = VSBleCommandUtil.integerToHex(crcBin)
            }
//       crc16 = VSBleUtil.calCrc16(fileData)
            //CRC of header+data
//        SmartVSLog.d("FinishFirmwareUpgrade", "dataCrcBytes: $dataCrcBytes")

            // payload = ":MSFWFIN28smvs_main.enc.tmp,smvs_main.enc.val,e14e"
            // payload = ":MSFWFIN24smvs_main.enc.val,smvs_main.enc,e14e"

            //val fileName="smvs_main.enc"

            var dataPayload = ""
            if (cmdCount == 1) {
                dataPayload += fileName.plus(VSBleCommandUtil.EXT_TEMPORARY)
                dataPayload += VSGattAttributes.PAYLOAD_SEPARATOR
                dataPayload += fileName.plus(VSBleCommandUtil.EXT_INTERMEDIATE)
                dataPayload += VSGattAttributes.PAYLOAD_SEPARATOR
                dataPayload += dataCrcBytes
            } else {
                dataPayload += fileName.plus(VSBleCommandUtil.EXT_INTERMEDIATE)
                dataPayload += VSGattAttributes.PAYLOAD_SEPARATOR
                dataPayload += fileName
                dataPayload += VSGattAttributes.PAYLOAD_SEPARATOR
                dataPayload += dataCrcBytes
            }

            payload += String.format("%02x", dataPayload.length)


            payload += dataPayload
            /* for(i in 0..22)
             {
                 payload +=VSBleCommandUtil.PAYLOAD_PAD
             }*/

            payload += requestPayload.padEnd(
                VSBleCommandUtil.PAYLOAD_MASTER_LENGTH - dataPayload.length,
                VSBleCommandUtil.PAYLOAD_PAD
            )

            payload += VSBleCommandUtil.EOP

            VSLog.d("FinishFirmwareUpgrade", ">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>: $payload")

            val charset = Charsets.UTF_8
            val byteArray = payload.toByteArray(charset)
            sendPayloadToDevice(byteArray, false)
        }
    }

    private fun resetDevice() {
        if (mConnectionState == STATE_CONNECTED) {

            var payload = ""
            payload += VSBleCommandUtil.SOP
            payload += VSBleCommandUtil.SENDER_MASTER
            payload += VSBleCommandUtil.TYPE_SET
            payload += VSCommandName.RESET

            val requestPayload = "1"//reset device

            payload += String.format("%02X", requestPayload.length)

            payload += requestPayload.padEnd(
                VSBleCommandUtil.PAYLOAD_MASTER_LENGTH - 25/*(payload.length+requestPayload.length)*/,
                VSBleCommandUtil.PAYLOAD_PAD
            )

            payload += VSBleCommandUtil.EOP

            VSLog.d("resetDevice", "payload: $payload")

            sendPayloadToDevice(addBytesAsChar(payload).toByteArray(), false)
        }
    }

    private fun writeDataToDevice(byteArray: ByteArray) {
        if (gattChar != null) {
            this.isTimerRequire = false
            gattChar!!.value = byteArray
            writeCharData(gattChar!!)
        }
    }

    private fun writeDataToDevice(byteArray: ByteArray, isTimerRequire: Boolean) {
        if (gattChar != null) {
            this.isTimerRequire = isTimerRequire
            this.latestDataArray = byteArray
            gattChar!!.value = byteArray
            writeCharData(gattChar!!)
        }
    }

    private fun writeCharData(characteristic: BluetoothGattCharacteristic) {
        this.lastWriteCharacteristic = characteristic
        characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
        }
        mBluetoothGatt!!.writeCharacteristic(characteristic)
    }

    /**
     * getDeviceDuration() : Function is used to get SMDUR Data and get the timeout duration
     */
    fun getDeviceDuration() {
        if (mConnectionState == STATE_CONNECTED) {
            /* Slave device duration in msecs for the worst-case slave action for a MASTER request.
Note this does not mean the quality or measurement process that are >10 seconds, rather atomic actions such
as save SMR or set time etc. The Master will query this parameter on connection and then use the value for time- out on all communications with the Slave.*/
            val requestPayload = ""

            var payload = ""
            payload += VSBleCommandUtil.SOP
            payload += VSBleCommandUtil.SENDER_MASTER
            payload += VSBleCommandUtil.TYPE_GET
            payload += VSCommandName.SMDUR

            // MIT - Instead of adding direct lenght we need to give converted HEX value in payload, Code is dynamic for lenght in Xamarin - please do accordingly - ( ArcMessages.cs -- public string GetPacket(int payloadLength) )
            // MIT - I have taken blank string count for now...
            //    payload += Payload.PAYLOAD_LENGTH_FORMAT
            payload += String.format("%02X", requestPayload.length)

            // MIT -  Swift by default function for padding but here I added blank string padding from 0 to 64 and add it into main payload
//            payload += requestPayload.padding(toLength: PAYLOAD_MASTER_LENGTH, withPad: "\0", startingAt: 0)
            payload += requestPayload.padEnd(
                VSBleCommandUtil.PAYLOAD_MASTER_LENGTH,
                VSBleCommandUtil.PAYLOAD_PAD
            )

            payload += VSBleCommandUtil.EOP

            // MIT -  Debug this code for more information
            //        Xcode
            //        ":MGSMDUR00\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\n\r"
            //        Xamarin
            //        ":MGSMDUR00\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\n\r"

            // MIT -  Converting result into Array
            /*let requestDataBytes = Array(payload.data(using: .utf8)!)

   // MIT -  Array to 20 data chunk from 74 Count
   let requestDataBytesChunked = requestDataBytes.chunked(into: 20)*/

            /*val chunks = payload.chunked(20)

    for (item in chunks) {
        Log.e("chunks", item)
        writeDataToDevice(item.toByteArray())
    }*/

            sendPayloadToDevice(payload.toByteArray(Charsets.UTF_8), true)

        } else {
            smartVSCMDCallback!!.onException(
                getString(R.string.str_not_connected), VSCommandName.SMDUR
            )
        }
    }

    /**
     * setCurrentDate() : Function is used to set Current Date to device
     */
    fun setCurrentDate() {
        if (mConnectionState == STATE_CONNECTED) {

            val requestPayload = ""

            var payload = ""
            val c = Calendar.getInstance().time
            VSLog.e("Current time =>", "" + c)

            val df = SimpleDateFormat(VSGattAttributes.CURRENT_DATE_FORMAT)
            val formattedDate = df.format(c)

            val dfTime = SimpleDateFormat(VSGattAttributes.CURRENT_TIME_FORMAT)
            val formattedTime = dfTime.format(c)

            payload += VSBleCommandUtil.SOP
            payload += VSBleCommandUtil.SENDER_MASTER
            payload += VSBleCommandUtil.TYPE_SET
            payload += VSCommandName.CODE_CURDT

            payload += String.format(
                "%02X",
                formattedDate.length + VSBleCommandUtil.PAYLOAD_SEPARATOR.toString().length + formattedTime.length
            )
            payload += formattedDate
            payload += VSBleCommandUtil.PAYLOAD_SEPARATOR
            payload += formattedTime
            payload += requestPayload.padEnd(
                VSBleCommandUtil.PAYLOAD_MASTER_LENGTH - 15,
                VSBleCommandUtil.PAYLOAD_PAD
            )
            payload += VSBleCommandUtil.EOP

            val charset = Charsets.UTF_8
            sendPayloadToDevice(payload.toByteArray(charset), false)
        } else {
            smartVSCMDCallback!!.onException(
                getString(R.string.str_not_connected), VSCommandName.CODE_CURDT
            )
        }
    }

    /**
     * getDeviceInformation() : Function is used to get information related to device
     * e.g. batteryStatus, mainBoardVersion
     */
    fun getDeviceInformation() {

        if (mConnectionState == STATE_CONNECTED) {

            /* Get the device info of the slave, i.e. SmartVS-Device.
     Each change in the slave h/w SW will generate a unique device revision, also a change to the finger cuff sensor boards or mechanical structure would also case this number to increase, these cannot be reported in the HWREV and SWREV properties.*/

            val requestPayload = ""
            var payload = ""

            payload = ""
            payload += VSBleCommandUtil.SOP
            payload += VSBleCommandUtil.SENDER_MASTER
            payload += VSBleCommandUtil.TYPE_GET
            payload += VSCommandName.CODE_DINFO

            // MIT - Instead of adding direct lenght we need to give converted HEX value in payload, Code is dynamic for lenght in Xamarin - please do accordingly - ( ArcMessages.cs -- public string GetPacket(int payloadLength) )
            // MIT - I have taken blank string count for now...
            //    payload += Payload.PAYLOAD_LENGTH_FORMAT
            payload += String.format("%02x", requestPayload.length)


            payload += requestPayload.padEnd(
                VSBleCommandUtil.PAYLOAD_MASTER_LENGTH,
                VSBleCommandUtil.PAYLOAD_PAD
            )
            payload += VSBleCommandUtil.EOP

            sendPayloadToDevice(payload.toByteArray(Charsets.UTF_8), true)

        } else {
            smartVSCMDCallback!!.onException(
                getString(R.string.str_not_connected), VSCommandName.CODE_DINFO
            )
        }

    }

    private fun divideArray(source: ByteArray, chunksize: Int): ArrayList<ByteArray> {

        val chunkPackets = (source.size / chunksize) + 1
        var start = 0
        val byteArrayList = ArrayList<ByteArray>()

        for (i in 1..chunkPackets) {

            if (i == chunkPackets) {
                byteArrayList.add(source.copyOfRange(start, source.size))
            } else {
                byteArrayList.add(source.copyOfRange(start, start + chunksize))
            }

            start += chunksize
        }
        return byteArrayList
    }

    private fun addBytesAsChar(payload: String): ArrayList<Byte> {
        val byteList = ArrayList<Byte>()
        payload.forEach {
            byteList.add(it.toInt().toByte())
        }
        return byteList
    }

    /**
     * startFirmwareUpdate() : Function is used for Firmware upgrade
     */
    fun updateFirmware() {

        getDeviceInformation()

        Handler(Looper.getMainLooper()).postDelayed({

            if (isFirmwareUpdateRequired()) {
                if (deviceInfo.batteryStatus > 5) {
                    fileName = encFile
                    getFirmwareFileData(fileName)
                } else {
                    smartVSCMDCallback!!.onException(
                        getString(R.string.str_battery_low_for_firmware),
                        VSCommandName.CODE_FWUPGRADE
                    )
                }
            } else {
                smartVSCMDCallback!!.onException(
                    getString(R.string.str_firmware_already_update),
                    VSCommandName.CODE_FWUPGRADE
                )
            }
        }, 100)

    }

    private fun isFirmwareUpdateRequired(): Boolean {

        val firmwareFileVersion = VSBleCommandUtil.FIRMWARE_MAIN_VERSION.split(".")

        var newMainMajor = 0
        var newMainMinor = 0
        var newMainBuild = 0
        if (firmwareFileVersion[0] != null) {
            newMainMajor = firmwareFileVersion[0].toInt()
        }
        if (firmwareFileVersion[1] != null) {
            newMainMinor = firmwareFileVersion[1].toInt()
        }
        if (firmwareFileVersion[2] != null) {
            newMainBuild = firmwareFileVersion[2].toInt()
        }

        if (deviceInfo.softwareMainMajor != null && deviceInfo.softwareMainMajor < newMainMajor) {
            return true
        } else if (deviceInfo.softwareMainMinor != null && deviceInfo.softwareMainMinor < newMainMinor) {
            return true
        } else if (deviceInfo.softwareMainBuild != null && deviceInfo.softwareMainBuild < newMainBuild) {
            return true
        }

        return false
    }

    private fun getFirmwareFileData(fileName: String) {
        currentPacket = 1
        //FW Upgrade
//        val inputStream = assets.open("smvs_main.enc")
        val inputStream = assets.open(fileName)

        val buffer = ByteArray(8192)
        val output = ByteArrayOutputStream()

        var bytesRead: Int
        while (inputStream.read(buffer).also { bytesRead = it } >= 0) {
            output.write(buffer, 0, bytesRead)
        }

        fileData = output.toByteArray()
        VSLog.e("bytes size", "" + fileData!!.size)
        try {
            output.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }

        if (fileName.contains(VSBleCommandUtil.EXT_ENC)) {
            crcEnc = VSBleUtil.calCrc16(fileData)
        } else {
            crcBin = VSBleUtil.calCrc16(fileData)
        }

        setStartFirmwareUpgrade(fileName, fileName, 0)
    }

    /**
     * stopMeasurement() : Function is used for Firmware upgrade
     * @command : SHORT_PPG,LONG_PPG,BLOOD_PRESSURE_ONLY
     */
    fun stopMeasurement(measuType: String) {
        if (mConnectionState == STATE_CONNECTED) {

            var payload = ""
            payload += VSBleCommandUtil.SOP
            payload += VSBleCommandUtil.SENDER_MASTER
            payload += VSBleCommandUtil.TYPE_SET
            payload += VSCommandName.CODE_MEASU

            var requestPayload = ""

            requestPayload += StatusOptions.STOP.type    // get opt value 0 || 1 || 2
            requestPayload += measuType // 0700 || 7700 || 8000

            payload += String.format(
                "%02X",
                requestPayload.length
            ) // 05 length of request command
            // :MSMEASU0520700

            payload += requestPayload.padEnd(
                VSBleCommandUtil.PAYLOAD_MASTER_LENGTH - requestPayload.length,
                VSBleCommandUtil.PAYLOAD_PAD
            )
            payload += VSBleCommandUtil.EOP

            VSLog.d("CODE_MEASU Command", "payload: $payload")

            ///////////////////////////////

            sendPayloadToDevice(payload.toByteArray(Charsets.UTF_8), 10)

        } else {
            smartVSCMDCallback!!.onException(
                getString(R.string.str_not_connected),
                VSCommandName.CODE_MEASU
            )
        }
    }

    /**
     * readyToStartMeasurement() : Function is used for making device ready for taking measurements
     * @command : SHORT_PPG,LONG_PPG,BLOOD_PRESSURE_ONLY
     **/
    fun readyToStartMeasurement(measuType: String) {

        if (mConnectionState == STATE_CONNECTED) {

            var payload = ""
            payload += VSBleCommandUtil.SOP
            payload += VSBleCommandUtil.SENDER_MASTER
            payload += VSBleCommandUtil.TYPE_SET
            payload += VSCommandName.CODE_MEASU

            var requestPayload = ""

            requestPayload += StatusOptions.READY_TO_START.type  // get opt value 0 || 1 || 2
            requestPayload += measuType // 0700 || 7700 || 8000

            payload += String.format(
                "%02X",
                requestPayload.length
            ) // 05 length of request command

            payload += requestPayload.padEnd( //0520700
                VSBleCommandUtil.PAYLOAD_MASTER_LENGTH - requestPayload.length,
                VSBleCommandUtil.PAYLOAD_PAD
            )
            payload += VSBleCommandUtil.EOP

            VSLog.d("CODE_MEASU Command", "payload: $payload")

            ///////////////////////////////
            sendPayloadToDevice(payload.toByteArray(Charsets.UTF_8), 10)
        } else {
            smartVSCMDCallback!!.onException(
                getString(R.string.str_not_connected),
                VSCommandName.CODE_MEASU
            )
        }

    }

    /**
     * startMeasurement() : Function is used for start taking measurements
     * @command : SHORT_PPG,LONG_PPG,BLOOD_PRESSURE_ONLY
     **/
    fun startMeasurement(measuType: String) {
        if (mConnectionState == STATE_CONNECTED) {

            var payload = ""
            payload += VSBleCommandUtil.SOP
            payload += VSBleCommandUtil.SENDER_MASTER
            payload += VSBleCommandUtil.TYPE_SET
            payload += VSCommandName.CODE_MEASU


            var requestPayload = ""

            requestPayload += StatusOptions.START.type    // get opt value 0 || 1 || 2
            requestPayload += measuType // 0700 || 7700 || 8000

            payload += String.format(
                "%02X",
                requestPayload.length
            ) // 05 length of request command

            // :MSMEASU0520700

            payload += requestPayload.padEnd(
                VSBleCommandUtil.PAYLOAD_MASTER_LENGTH - requestPayload.length,
                VSBleCommandUtil.PAYLOAD_PAD
            )
            payload += VSBleCommandUtil.EOP

            VSLog.d("CODE_MEASU Command", "payload: $payload")

            sendPayloadToDevice(payload.toByteArray(Charsets.UTF_8), 10)
        } else {
            smartVSCMDCallback!!.onException(
                getString(R.string.str_not_connected),
                VSCommandName.CODE_MEASU
            )
        }
    }

    /**
     * Return firmware data and value for firmware available or not
     */
    fun versionInformation(): String {
        var checkFirmwareAvailable = VersionInformationBean()
        if (mConnectionState == STATE_CONNECTED) {
            var isFirmwareUpdateRequired = isFirmwareUpdateRequired()
            checkFirmwareAvailable.isPeripheralConnected = true
            checkFirmwareAvailable.isUpdateAvailable = isFirmwareUpdateRequired()
            checkFirmwareAvailable.available_mainBoard_version = VSBleCommandUtil.FIRMWARE_MAIN_VERSION
            checkFirmwareAvailable.available_sensorBoard_version = VSBleCommandUtil.FIRMWARE_SENSOR_VERSION
            checkFirmwareAvailable.currentSDKVersion = BuildConfig.VERSION_NAME
            if(isFirmwareUpdateRequired)
            {
                checkFirmwareAvailable.msg = getString(R.string.str_new_firmware_available)
            }
            else
            {
                checkFirmwareAvailable.msg = getString(R.string.str_firmware_already_update)
            }
        }
        else
        {
            checkFirmwareAvailable.isPeripheralConnected = false
            checkFirmwareAvailable.msg = getString(R.string.str_not_connected)

        }
        return Gson().toJson(checkFirmwareAvailable)
    }

    private fun sendPayloadToDevice(byteArray: ByteArray, isTimerRequire: Boolean) {
        val arraySize = (byteArray.size / 20)
        var countSize = 0
        for (i in 0..arraySize) {
            Thread.sleep(5)
            val array = byteArray.copyOfRange(
                countSize,
                if (byteArray.size < countSize + 20)
                    byteArray.size
                else countSize + 20
            )
            writeDataToDevice(array, isTimerRequire)
            countSize += 20
        }
    }

    private fun sendPayloadToDevice(byteArray: ByteArray, delay: Long) {
        val arraySize = (byteArray.size / 20)
        var countSize = 0
        for (i in 0..arraySize) {
            Thread.sleep(delay)
            val array = byteArray.copyOfRange(
                countSize,
                if (byteArray.size < countSize + 20)
                    byteArray.size
                else countSize + 20
            )
            writeDataToDevice(array)
            countSize += 20
        }
    }
}