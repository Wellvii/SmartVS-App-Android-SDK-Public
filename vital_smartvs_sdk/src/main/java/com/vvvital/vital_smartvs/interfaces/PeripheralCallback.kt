package com.vvvital.vital_smartvs.interfaces

import com.vvvital.vital_smartvs.bean.VSError

/**
 * SmartVSApp
 *
 * Created By Administrator on 10/11/2019
 *
 * Copyright Softweb Solutions Inc. 2019,  All rights reserved.
 */
interface PeripheralCallback {
    fun onSuccess(jsonResponse: String, command: String)
    fun onFailure(error: VSError, command: String)
    fun onException(errorMsg: String, command: String)
    fun onPeripheralStateChange(state: Int)
}