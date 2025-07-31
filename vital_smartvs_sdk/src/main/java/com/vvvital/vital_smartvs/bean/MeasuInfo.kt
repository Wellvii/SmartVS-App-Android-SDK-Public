package com.vvvital.vital_smartvs.bean

internal class MeasuInfo {
    // Status
    var status: Int = 0
    var reserved1: String = "No valid result available"
    var measurmentDuration: String = "No valid result available"
    var reserved2: String = "No valid result available"
    //SNR
    var SNR: String = "No valid result available"
    var perfusionIndexResult: String = "No valid result available"
    var perfusionIndexResult_Unit: String = "%"
    var SNRmDLS_1: String = "No valid result available"
    var SNRmDLS_2: String = "No valid result available"
    var pulseRate: String = "No valid result available"
    var pulseRate_Unit: String = " BPM"
    var spo2: String = "No valid result available"
    var spo2_Unit:String ="%"
    var BP_Diastolic: String = "No valid result available"
    var BP_Diastolic_Unit: String = " mmHg"
    var BP_Systolic: String = "No valid result available"
    var BP_Systolic_Unit: String = " mmHg"
    var respiration: String = "No valid result available"
    var respiration_Unit: String = " BPM"
    var PRV: String = "No valid result available"
    var PRV_Unit: String = " ms"
    var PVI: String = "No valid result available"
    var PVI_Unit: String = "%"
    //var pneumaticSystemPressure: String = ""
    // status
    var batteryStatus: String = "No valid result available"
    var batteryStatus_Unit: String = "%"
    // Temprature
    //var ambientTempSensorBoard: String = ""
    //var ambientTempMotherBoard: String = ""
    var errorCode: String = ""
    var errorDesc: String = ""
}