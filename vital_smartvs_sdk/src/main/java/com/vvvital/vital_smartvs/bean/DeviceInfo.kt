package com.vvvital.vital_smartvs.bean

/**
 * SmartVSApp
 *
 * Created By Administrator on 10/15/2019
 *
 * Copyright Softweb Solutions Inc. 2019,  All rights reserved.
 */

internal class DeviceInfo {
    var uniqueID: String = ""
    var fingerCuffSerialnumber: String = "0"
    // Software
    var softwareMainMajor: Int = 0
    var softwareMainMinor: Int = 0
    var softwareMainBuild: Int = 0
    var softwareSensorMajor: Int = 0
    var softwareSensorMinor: Int = 0
    var softwareSensorBuild: Int = 0

    // Hardware
    var hardwareSensorMajor: Int = 0
    var hardwareSensorMinor: Int = 0
    var hardwareSensorBuild: Int = 0
    var hardwareMainMajor: Int = 0
    var hardwareMainMinor: Int = 0
    var hardwareMainBuild: Int = 0

    // Capacity
    var totalCapacity: Int = 0
    var availSpace: Int = 0

    // status
    var batteryStatus: Int = 0
    var batteryStatus_Unit: String = "%"

    // Temperature
    //var ambientTemp: Int = 0
   // var protocolRevision: Int = 0

    var noOfBladderInflation: String = ""
    var errorCode: String = ""
    var errorDesc: String = ""

}