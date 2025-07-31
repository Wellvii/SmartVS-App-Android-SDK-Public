package com.vvvital.vital_smartvs.interfaces

import android.bluetooth.BluetoothDevice

interface PeripheralScanCallback {
    fun scanPeripheral(peripheral: BluetoothDevice)
}