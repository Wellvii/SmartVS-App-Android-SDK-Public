package com.vvvital.vital_smartvs

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import android.widget.Toast
import androidx.core.app.ActivityCompat
import com.vvvital.vital_smartvs.interfaces.PeripheralScanCallback

class VVitalScanner(private val mContext: Activity, private val smartVSDeviceScan: PeripheralScanCallback) {

    private val mBluetoothAdapter: BluetoothAdapter?
    private var mScanning: Boolean = false

    private val mScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.device?.let { device ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (ActivityCompat.checkSelfPermission(
                            mContext,
                            Manifest.permission.BLUETOOTH_CONNECT
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        return
                    }
                }
                if (device.name != null && (device.name.startsWith("Vital SMVS", true)
                            || device.name.startsWith("TYSA-B", true))
                ) {
                    smartVSDeviceScan.scanPeripheral(device)
                }
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
    }

    private fun checkBluetoothEnabled() {
        if (!mBluetoothAdapter!!.isEnabled) {
            if (!mBluetoothAdapter.isEnabled) {
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ActivityCompat.checkSelfPermission(
                        mContext,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    return
                }}
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

    private fun hasScanPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.checkSelfPermission(mContext, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(mContext, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ActivityCompat.checkSelfPermission(mContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun requestScanPermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        ActivityCompat.requestPermissions(
            mContext as Activity,
            permissions.toTypedArray(),
            1001 // You can define your own request code
        )
    }

    @SuppressLint("SuspiciousIndentation", "ObsoleteSdkInt")
    fun scanVSDevice(enable: Boolean) {
        if (enable) {
            if (!mBluetoothAdapter?.isEnabled!!) {
              checkBluetoothEnabled()
                return
            }

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                if (!isLocationEnabled()) {
                    Toast.makeText(mContext, "Please turn on location", Toast.LENGTH_LONG).show()
                    return
                }
            }

            if (!hasScanPermissions()) {
                requestScanPermissions()
                return
            }

            mScanning = true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ActivityCompat.checkSelfPermission(
                        mContext,
                        Manifest.permission.BLUETOOTH_SCAN
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    return
                }
            }
            // Use deprecated API for legacy support (optional)
                mBluetoothAdapter?.bluetoothLeScanner?.startScan(mScanCallback)
        } else {
            mScanning = false
                mBluetoothAdapter?.bluetoothLeScanner?.stopScan(mScanCallback)
        }
    }

    companion object {
        private const val REQUEST_ENABLE_BT = 1
    }
}
