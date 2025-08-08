package com.vital.vvitalapp.activity

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.app.ListActivity
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.*
import android.widget.BaseAdapter
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.vvvital.vital_smartvs.VVitalScanner
import com.vvvital.vital_smartvs.interfaces.PeripheralScanCallback
import com.vital.vvitalapp.R


/**
 * Activity for scanning and displaying available Bluetooth LE devices.
 */
class DeviceScanActivity : ListActivity(), PeripheralScanCallback {

    private var mLeDeviceListAdapter: LeDeviceListAdapter? = null
    private var mScanning: Boolean = false
    private var mHandler: Handler? = null
    private lateinit var smartVS: VVitalScanner
    private val REQUEST_LOCATION = 2
    private var alertDialog: AlertDialog? = null

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        actionBar!!.setTitle(R.string.title_devices)
        mHandler = Handler()

        smartVS = VVitalScanner(this@DeviceScanActivity, this)

        /* Ensures Bluetooth and Location is enabled on the device.  If Bluetooth and Location is not currently enabled,
       fire an intent to display a dialog asking the user to grant permission to enable it.*/
        // Initializes list view adapter.
        mLeDeviceListAdapter = LeDeviceListAdapter()
        listAdapter = mLeDeviceListAdapter

    }

    private fun startFunction() {
        //Check location permission
        if (!checkPermission()) {
            requestPermission()
        } else {
            if (isLocationEnabled(this)) {
                scanDevices(true)
            }
        }
    }

    override fun onResume() {
        super.onResume()

        if (mLeDeviceListAdapter != null) {
            mLeDeviceListAdapter!!.clear()
            mLeDeviceListAdapter!!.notifyDataSetChanged()
            startFunction()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        if (!mScanning) {
            menu.findItem(R.id.menu_stop).isVisible = false
            menu.findItem(R.id.menu_scan).isVisible = true
//            menu.findItem(R.id.menu_refresh).actionView = null
        } else {
            menu.findItem(R.id.menu_stop).isVisible = true
            menu.findItem(R.id.menu_scan).isVisible = false
           /* menu.findItem(R.id.menu_refresh).setActionView(
                R.layout.actionbar_indeterminate_progress
            )*/
        }
        return true
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        // User chose not to enable Bluetooth.
        if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED) {
            finish()
            return
        } else {
            //Check location is enabled
            if (!isLocationEnabled(this)) {
                showLocationNotifyDialog(this)
            } else {
                startFunction()
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == PERMISSION_REQUEST_COARSE_LOCATION) {
            val deniedPermissions = mutableListOf<String>()

            for (i in permissions.indices) {
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    deniedPermissions.add(permissions[i])
                }
            }

            if (deniedPermissions.isEmpty()) {
                startFunction()
                Log.d("Permissions", "All permissions granted")
                return
            }

            if (alertDialog?.isShowing == true) return

            // Build message based on denied permissions
            val message = when {
                deniedPermissions.contains(Manifest.permission.BLUETOOTH_SCAN) ||
                        deniedPermissions.contains(Manifest.permission.BLUETOOTH_CONNECT) -> {
                    "Bluetooth permissions are required to scan and connect to nearby devices."
                }

                deniedPermissions.contains(Manifest.permission.ACCESS_FINE_LOCATION) -> {
                    "Since location access has not been granted, this app will not be able to discover beacons when in the background."
                }

                else -> "Some required permissions were not granted. The app may not function correctly."
            }

            // Show dialog
            val builder = AlertDialog.Builder(this)
            builder.setTitle("Functionality limited")
            builder.setMessage(message)
            builder.setPositiveButton(android.R.string.ok, null)
            builder.setOnDismissListener { alertDialog = null }

            alertDialog = builder.create()
            alertDialog?.show()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_scan -> {
                if (mLeDeviceListAdapter != null) {
                    mLeDeviceListAdapter!!.clear()
                    startFunction()
                }
            }
            R.id.menu_stop -> scanDevices(false)
        }
        return true
    }


    private fun requestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val permissions = arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
            ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_COARSE_LOCATION)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val permissions = arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION
            )
            ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_COARSE_LOCATION)
        }
    }

    private fun showLocationNotifyDialog(activity: Activity): Dialog {
        val builder = AlertDialog.Builder(activity)

        builder.setTitle(activity.getString(R.string.app_name))
        builder.setMessage(activity.resources.getString(R.string.str_location_dialog))
        builder.setPositiveButton("OK") { dialog, _ ->
            dialog.dismiss()
            val myIntent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            activity.startActivityForResult(myIntent, REQUEST_LOCATION)
        }
        builder.setNegativeButton("Cancel") { dialog, _ ->
            dialog.dismiss()
            Toast.makeText(this, "Please turn on location", Toast.LENGTH_LONG).show()
        }
        builder.setCancelable(false)
        return builder.show()
    }

    @SuppressLint("ObsoleteSdkInt")
    private fun isLocationEnabled(context: Context): Boolean {
        var locationMode = 0
        val locationProviders: String

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            try {
                locationMode =
                    Settings.Secure.getInt(context.contentResolver, Settings.Secure.LOCATION_MODE)

            } catch (e: Settings.SettingNotFoundException) {
                e.printStackTrace()
                return false
            }

            return locationMode != Settings.Secure.LOCATION_MODE_OFF

        } else {
            locationProviders = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.LOCATION_PROVIDERS_ALLOWED
            )
            return !TextUtils.isEmpty(locationProviders)
        }
    }

    private fun checkPermission(): Boolean {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                // Android 12+ (API 31+): Need Bluetooth and Location permissions
                val scan = ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_SCAN
                ) == PackageManager.PERMISSION_GRANTED
                val connect = ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED
                val location = ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
                scan && connect && location
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                // Android 6-11: Need Location permission
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            }
            else -> true // Pre-Marshmallow: permissions granted at install time
        }
    }

    override fun onPause() {
        super.onPause()
        if (mLeDeviceListAdapter != null) {
            scanDevices(false)
            mLeDeviceListAdapter!!.clear()
        }
    }

    override fun onListItemClick(l: ListView, v: View, position: Int, id: Long) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
        }
        val device = mLeDeviceListAdapter!!.getDevice(position) ?: return
        val intent = Intent(this, DeviceDetailActivity::class.java)
        intent.putExtra(DeviceDetailActivity.EXTRAS_DEVICE_NAME, device.name)
        intent.putExtra(DeviceDetailActivity.EXTRAS_DEVICE_ADDRESS, device.address)
        if (mScanning) {
            scanDevices(false)
            mScanning = false
        }
        startActivity(intent)
    }

    // Adapter for holding devices found through scanning.
    private inner class LeDeviceListAdapter : BaseAdapter() {
        private val mLeDevices: ArrayList<BluetoothDevice> = ArrayList()
        private val mInflator: LayoutInflater = this@DeviceScanActivity.layoutInflater

        fun addDevice(device: BluetoothDevice) {
            if (!mLeDevices.contains(device)) {
                mLeDevices.add(device)
            }
        }

        fun getDevice(position: Int): BluetoothDevice? {
            return mLeDevices[position]
        }

        fun clear() {
            mLeDevices.clear()
        }

        override fun getCount(): Int {
            return mLeDevices.size
        }

        override fun getItem(i: Int): Any {
            return mLeDevices[i]
        }

        override fun getItemId(i: Int): Long {
            return i.toLong()
        }

        override fun getView(i: Int, view: View?, viewGroup: ViewGroup): View {
            var view = view
            val viewHolder: ViewHolder
            // General ListView optimization code.
            if (view == null) {
                view = mInflator.inflate(R.layout.listitem_device, null)
                viewHolder = ViewHolder()
                viewHolder.deviceAddress = view!!.findViewById(R.id.device_address) as TextView
                viewHolder.deviceName = view.findViewById(R.id.device_name) as TextView
                view.tag = viewHolder
            } else {
                viewHolder = view.tag as ViewHolder
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ActivityCompat.checkSelfPermission(
                        this@DeviceScanActivity,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    return view
                }
            }

            val device = mLeDevices[i]
            val deviceName = device.name
            if (deviceName != null && deviceName.isNotEmpty())
                viewHolder.deviceName!!.text = deviceName
            else
                viewHolder.deviceName!!.setText(R.string.unknown_device)
            viewHolder.deviceAddress!!.text = device.address

            return view
        }
    }

    internal class ViewHolder {
        var deviceName: TextView? = null
        var deviceAddress: TextView? = null
    }

    companion object {
        private val REQUEST_ENABLE_BT = 1
        private val PERMISSION_REQUEST_COARSE_LOCATION = 1
    }

    override fun scanPeripheral(peripheral: BluetoothDevice) {

        mLeDeviceListAdapter!!.addDevice(peripheral)
        mLeDeviceListAdapter!!.notifyDataSetChanged()
    }

    private fun scanDevices(isStartScan: Boolean) {
        if (isStartScan) {
            mLeDeviceListAdapter?.clear()
        }
        smartVS.scanVSDevice(isStartScan)
    }
}