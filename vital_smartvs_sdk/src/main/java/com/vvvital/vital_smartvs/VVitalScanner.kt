package com.vvvital.vital_smartvs

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import android.widget.Toast
import com.vvvital.vital_smartvs.interfaces.PeripheralScanCallback

class VVitalScanner(private val mContext: Activity, private val smartVSDeviceScan: PeripheralScanCallback) {

    private val mBluetoothAdapter: BluetoothAdapter?
    private var mScanning: Boolean = false


    // Device scan callback.
    private val mLeScanCallback =
        BluetoothAdapter.LeScanCallback { device, _, _ ->
            mContext.runOnUiThread {
                if (device?.name != null && (device.name.startsWith("Vital SMVS", true)
                            || device.name.startsWith("TYSA-B", true))
                ) {
                    smartVSDeviceScan.scanPeripheral(device)
                }
            }
        }

    init {

        // Use this check to determine whether BLE is supported on the device.  Then you can
        // selectively disable BLE-related features.
        if (!mContext.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(mContext, R.string.ble_not_supported, Toast.LENGTH_SHORT).show()
        }

        // Initializes a Bluetooth adapter.  For API level 18 and above, get a reference to
        // BluetoothAdapter through BluetoothManager.
        val bluetoothManager =
            mContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        mBluetoothAdapter = bluetoothManager.adapter

        // Checks if Bluetooth is supported on the device.
        if (mBluetoothAdapter == null) {
            Toast.makeText(mContext, R.string.error_bluetooth_not_supported, Toast.LENGTH_SHORT)
                .show()
        }
        checkBluetoothEnabled()
    }

    private fun checkBluetoothEnabled() {
        if (!mBluetoothAdapter!!.isEnabled) {
            if (!mBluetoothAdapter.isEnabled) {
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                mContext.startActivityForResult(enableBtIntent,
                    REQUEST_ENABLE_BT
                )
            }
        }
    }


    private fun isLocationEnabled(): Boolean {
        var locationMode = 0
        val locationProviders: String

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            try {
                locationMode =
                    Settings.Secure.getInt(mContext.contentResolver, Settings.Secure.LOCATION_MODE)

            } catch (e: Settings.SettingNotFoundException) {
                e.printStackTrace()
                return false
            }

            return locationMode != Settings.Secure.LOCATION_MODE_OFF

        } else {
            locationProviders = Settings.Secure.getString(
                mContext.contentResolver,
                Settings.Secure.LOCATION_PROVIDERS_ALLOWED
            )
            return !TextUtils.isEmpty(locationProviders)
        }
    }

    fun scanVSDevice(enable: Boolean) {
        if (enable) {
            if (isLocationEnabled()) {
                mScanning = true
                mBluetoothAdapter!!.startLeScan(mLeScanCallback)
            } else {
                Toast.makeText(mContext, "Please turn on location", Toast.LENGTH_LONG).show()
            }
        } else {
            mScanning = false
            mBluetoothAdapter!!.stopLeScan(mLeScanCallback)
        }
    }

    companion object {
        private const val REQUEST_ENABLE_BT = 1
    }
}
