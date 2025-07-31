package com.vvvital.vital_smartvs.bean

/**
 * SmartVSApp
 *
 * Created By Administrator on 10/22/2019
 *
 * Copyright Softweb Solutions Inc. 2019,  All rights reserved.
 */
internal class FWUpgradeData : ResponseObject() {
    var state: Int = 0
    var fileName: String = ""
    var size: Int = 0
    var crc:  Int = 0

}