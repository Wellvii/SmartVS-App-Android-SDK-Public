package com.vvvital.vital_smartvs.bean

internal open class VersionInformationBean {

    var isPeripheralConnected: Boolean = false
    var msg: String = ""
    var isUpdateAvailable: Boolean? = null
    var available_sensorBoard_version: String? = null
    var available_mainBoard_version: String? = null
    var currentSDKVersion: String? = null

}