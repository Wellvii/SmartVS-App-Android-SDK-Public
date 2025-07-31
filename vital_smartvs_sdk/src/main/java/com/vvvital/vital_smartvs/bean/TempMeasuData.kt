package com.vvvital.vital_smartvs.bean

/**
 * SmartVSApp
 *
 * Created By Administrator on 10/15/2019
 *
 * Copyright Softweb Solutions Inc. 2019,  All rights reserved.
 */

internal class TempMeasData {

    // Status
    var status: Int = 0
    //temperror
    var tempErrorReason: String = ""
    var errorDesc: String = ""
    // values
    var bodyTemperature: String = "No valid result available"
    var bodyTemperature_Unit: String = "℃"
    //var surfaceTemperature: String = ""
    //var ambientTemperature: String = ""
}