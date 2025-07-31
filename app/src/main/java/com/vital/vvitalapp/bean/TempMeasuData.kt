package com.vital.vvitalapp.bean

/**
 * SmartVSApp
 *
 * Created By Administrator on 10/15/2019
 *
 * Copyright Softweb Solutions Inc. 2019,  All rights reserved.
 */

internal class TempMeasData  {
   /* var status: Int = 0
    var errorReason: String = ""
    var errorDesc: String = ""
    var bodyTemp: Float = 0.0F
    var surfaceTemp: Float = 0.0F
    var ambientTemp: Float = 0.0F*/

    var ACK: Boolean = false
    // Status
    var status: Int = 0
    //temperror
    var tempErrorReason: String = ""
    var errorDesc: String = ""
    // values
    var bodyTemperature: String = ""
    var bodyTemperature_Unit: String = ""
    //var surfaceTemperature: String = ""
    //var ambientTemperature: String = ""
}