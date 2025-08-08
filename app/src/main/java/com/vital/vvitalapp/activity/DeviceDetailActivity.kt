package com.vital.vvitalapp.activity

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.transition.TransitionManager
import android.util.Log
import android.view.*
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager

import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.google.gson.Gson
import com.vvvital.vital_smartvs.bean.VSError
import com.vvvital.vital_smartvs.interfaces.PeripheralCallback
import com.vvvital.vital_smartvs.service.VVitalManager
import com.vvvital.vital_smartvs.util.*
import com.vital.vvitalapp.R
import com.vital.vvitalapp.adapter.InfoAdapter
import com.vital.vvitalapp.bean.*
import com.vital.vvitalapp.databinding.ActivityDeviceDetailBinding
import java.util.*


/**
 * For a given BLE device, this Activity provides the user interface to connectPeripheral, display data,
 * and display GATT services and characteristics supported by the device.  The Activity
 * communicates with `SmartVSBleService`, which in turn interacts with the
 * Bluetooth LE API.
 */
class DeviceDetailActivity : Activity(), InfoAdapter.OnItemClick, PeripheralCallback {
    private lateinit var binding: ActivityDeviceDetailBinding
    private var mDeviceName: String? = null
    private var mDeviceAddress: String? = null
    private var mVVitalManager: VVitalManager? = null
    private var mConnected = false

    private val LIST_NAME = "NAME"
    private val LIST_UUID = "UUID"
    private var infoList = ArrayList<InfoBean>()
    private var measuInfoList = ArrayList<InfoBean>()
    private var infoAdapter: InfoAdapter? = null

    private var bytes: ByteArray? = null
    private var fileName = "smvs_main.enc"
    private var isFirmwareFinished = false
	// Code to manage Service lifecycle.
    private val mServiceConnection = object : ServiceConnection {

        override fun onServiceConnected(componentName: ComponentName, service: IBinder) {
            mVVitalManager = (service as VVitalManager.LocalBinder).service
            if (!mVVitalManager!!.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth")
                finish()
            }

            mVVitalManager!!.setSmartVSCMDListener(this@DeviceDetailActivity)
            // Automatically connects to the device upon successful start-up initialization.
            mVVitalManager!!.connectPeripheral(mDeviceAddress)
        }

        override fun onServiceDisconnected(componentName: ComponentName) {
            mVVitalManager = null
        }
    }

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDeviceDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val intent = intent
        mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME)
        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS)

        actionBar!!.title = mDeviceName
        actionBar!!.setDisplayHomeAsUpEnabled(true)
        val gattServiceIntent = Intent(this, VVitalManager::class.java)
        bindService(gattServiceIntent, mServiceConnection, Context.BIND_AUTO_CREATE)

        setUI()
    }

    private fun setUI() {
        binding.tvAddress.text = getString(R.string.str_device_address, mDeviceAddress)
        if (mConnected) {
            binding.tvState.text = getString(R.string.str_state, getString(R.string.connected))
        } else {
            binding.tvState.text = getString(R.string.str_state, getString(R.string.disconnected))
        }

        binding.rvInfo.layoutManager = LinearLayoutManager(this, RecyclerView.VERTICAL, false)
        binding.rvMeasuInfo.layoutManager = LinearLayoutManager(this, RecyclerView.VERTICAL, false)

        setDeviceInfoList(null)

        setAdapter()

        binding.tvInfo.performClick()
        binding.tvInfo.isSelected = true
        binding.tvInfo.setOnClickListener {
            binding.tvInfo.isSelected = true
            binding.tvMeasurement.isSelected = false
            binding.tvFirmware.isSelected = false

            binding.rvInfo.visibility = View.VISIBLE
            binding.llMeasurement.visibility = View.GONE
            binding.llFirmware.visibility = View.GONE
        }

        binding.tvMeasurement.setOnClickListener {
            binding.tvInfo.isSelected = false
            binding.tvMeasurement.isSelected = true
            binding.tvFirmware.isSelected = false

            binding.rvInfo.visibility = View.GONE
            binding.llMeasurement.visibility = View.VISIBLE
            binding.llFirmware.visibility = View.GONE
        }

        binding.tvFirmware.setOnClickListener {
            binding.tvInfo.isSelected = false
            binding.tvMeasurement.isSelected = false
            binding.tvFirmware.isSelected = true

            binding.rvInfo.visibility = View.GONE
            binding.llMeasurement.visibility = View.GONE
            binding.llFirmware.visibility = View.VISIBLE
            binding.llFirmware.invalidate()
        }

        binding.tvGetTemp.setOnClickListener {
            binding.tvBodyTemp.text = ""
            //tvSurfaceTemp.text = ""
            //tvAmbientTemp.text = ""

            if (binding.rvMeasuInfo.visibility == View.VISIBLE) {
                binding.rvMeasuInfo.visibility = View.GONE // hide
            }

            mVVitalManager!!.readyToStartTemperature()
        }

        binding.tvSetDate.setOnClickListener {
            Log.e("isPeripheralConnected", "" + mVVitalManager!!.isDeviceConnected())
            mVVitalManager!!.setCurrentDate()
        }

        binding.tvMesuCommand.setOnClickListener {
            popupWindow()
        }

        binding.tvStart.setOnClickListener {

            if ("Stop".equals(binding.tvStart.text.toString(), true)) {
                mVVitalManager!!.stopMeasurement(selectedCommand)
            } else if ("Start".equals(binding.tvStart.text.toString(), true)) {
                mVVitalManager!!.startMeasurement(selectedCommand)
            }
        }

        binding.tvUpdateFW.setOnClickListener {
            binding.tvFirmwareType.text = "Downloading..."
            mVVitalManager!!.updateFirmware()

        }

        binding.versionInformation.setOnClickListener {
            var versionInformation = mVVitalManager!!.versionInformation()
            Log.e("VersionInformation",versionInformation)

        }
    }

    private var selectedCommand: String = ""
    private var selectedText: String = ""

    private fun popupWindow() {
        // Initialize a new layout inflater instance
        val inflater: LayoutInflater =
            getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater

        // Inflate a custom view using layout inflater
        val view = inflater.inflate(R.layout.popup_layout, null)

        // Initialize a new instance of popup window
        val popupWindow = PopupWindow(
            view, // Custom view to show in popup window
            LinearLayout.LayoutParams.WRAP_CONTENT, // Width of popup window
            LinearLayout.LayoutParams.WRAP_CONTENT // Window height
        )

        val tvShortPPG = view.findViewById<TextView>(R.id.tvShortPPG)
        val tvLongPPG = view.findViewById<TextView>(R.id.tvLongPPG)
        val tvBloodPressure = view.findViewById<TextView>(R.id.tvBloodPressure)


        tvShortPPG.setOnClickListener {
            selectedCommand = MeasuType.SpO2_PI_PR
            selectedText = "SpO2, PI, PR"

            tvShortPPG.setCompoundDrawablesWithIntrinsicBounds(
                0,
                0,
                R.drawable.ic_check_black_24dp,
                0
            )
            tvLongPPG.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
            tvBloodPressure.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
        }

        tvLongPPG.setOnClickListener {
            selectedCommand = MeasuType.RR_PVi_PRV_SpO2_PI_PR
            selectedText = "RR, PVi, PRV, SpO2, PI, PR"

            tvShortPPG.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
            tvLongPPG.setCompoundDrawablesWithIntrinsicBounds(
                0,
                0,
                R.drawable.ic_check_black_24dp,
                0
            )
            tvBloodPressure.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)

        }


        tvBloodPressure.setOnClickListener {
            selectedCommand = MeasuType.BLOOD_PRESSURE
            selectedText = "Blood Pressure"

            tvShortPPG.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
            tvLongPPG.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
            tvBloodPressure.setCompoundDrawablesWithIntrinsicBounds(
                0,
                0,
                R.drawable.ic_check_black_24dp,
                0
            )
        }

        val tvReady = view.findViewById<TextView>(R.id.tvReady)
        val tvClose = view.findViewById<TextView>(R.id.tvClose)

        tvReady.setOnClickListener {
            popupWindow.dismiss()

            //mVVitalManager!!.stopMeasurement(selectedCommand)
            if (mVVitalManager != null) {
                binding.tvMesuCommand.text = selectedText
                mVVitalManager!!.readyToStartMeasurement(selectedCommand)
            }
        }

        tvClose.setOnClickListener {
            popupWindow.dismiss()
        }

        // Finally, show the popup window on app
        TransitionManager.beginDelayedTransition(binding.root)
        popupWindow.showAtLocation(
            binding.root, // Location to display popup window
            Gravity.CENTER, // Exact position of layout to display popup
            0, // X offset
            0 // Y offset
        )
    }

    private fun setAdapter() {
        if (infoAdapter == null) {
            infoAdapter = InfoAdapter(infoList)
            infoAdapter!!.setOnItemClick(this)
            binding.rvInfo.adapter = infoAdapter
        } else {
            runOnUiThread { infoAdapter!!.notifyDataSetChanged() }
        }
    }

    private fun setDeviceInfoList(deviceInfo: DeviceInfo?) {
        infoList.clear()
        infoList.add(InfoBean("Slave unique identification number", deviceInfo?.uniqueID ?: "0"))
        infoList.add(
            InfoBean(
                "Finger Cuff serial number(future)",
                deviceInfo?.fingerCuffSerialnumber ?: "0"
            )
        )
        infoList.add(
            InfoBean(
                "SW Main board",
                deviceInfo?.softwareMainMajor?.toString()?.plus(".")?.plus(deviceInfo.softwareMainMinor)?.plus(
                    "."
                )?.plus(deviceInfo.softwareMainBuild) ?: "0"
            )
        )
        infoList.add(
            InfoBean(
                "SW Sensor board",
                deviceInfo?.softwareSensorMajor?.toString()?.plus(".")?.plus(deviceInfo.softwareSensorMinor)?.plus(
                    "."
                )?.plus(deviceInfo.softwareSensorBuild) ?: "0"
            )
        )
        infoList.add(
            InfoBean(
                "HW Sensor board",
                deviceInfo?.hardwareSensorMajor?.toString()?.plus(".")?.plus(deviceInfo.hardwareSensorMinor)?.plus(
                    "."
                )?.plus(deviceInfo.hardwareSensorBuild) ?: "0"
            )
        )
        infoList.add(
            InfoBean(
                "HW Main board",
                deviceInfo?.hardwareMainMajor?.toString()?.plus(".")?.plus(deviceInfo.hardwareMainMinor)?.plus(
                    "."
                )?.plus(deviceInfo.hardwareMainBuild) ?: "0"
            )
        )
        infoList.add(
            InfoBean(
                "Total SD card capacity",
                deviceInfo?.totalCapacity?.toString() ?: "0"
            )
        )
        infoList.add(
            InfoBean(
                "Available SD card capacity",
                deviceInfo?.availSpace?.toString() ?: "0"
            )
        )
        infoList.add(
            InfoBean(
                "Battery Status",
                (deviceInfo?.batteryStatus?.toString() ?:"0").plus(deviceInfo?.batteryStatus_Unit?:"%")
            )
        )
        // infoList.add(InfoBean("Ambient temp", deviceInfo?.ambientTemp?.toString() ?: "0"))
    }


    private fun setMeasuInfoAdapter() {
        infoAdapter = null

        infoAdapter = InfoAdapter(measuInfoList)
        infoAdapter!!.setOnItemClick(this)
        binding.rvMeasuInfo.adapter = infoAdapter
    }

    private fun setMeasuInfoList(measuInfo: MeasuInfo?) {
        measuInfoList.clear()

        measuInfoList.add(InfoBean("SNR", measuInfo!!.SNR))
        measuInfoList.add(
            InfoBean(
                "Perfusion Index Result",
                if (measuInfo.perfusionIndexResult.length > 10) measuInfo.perfusionIndexResult else measuInfo.perfusionIndexResult.plus(
                    measuInfo.perfusionIndexResult_Unit
                )
            )
        )
        measuInfoList.add(InfoBean("SNR_mDLS_1", measuInfo.SNRmDLS_1))
        measuInfoList.add(InfoBean("SNR_mDLS_2", measuInfo.SNRmDLS_2))
        measuInfoList.add(
            InfoBean(
                "PulseRate",
                if (measuInfo.pulseRate.length > 10) measuInfo.pulseRate else measuInfo.pulseRate.plus(
                    measuInfo.pulseRate_Unit
                )
            )
        )
        measuInfoList.add(
            InfoBean(
                "SPo2",
                if (measuInfo.spo2.length > 10) measuInfo.spo2 else measuInfo.spo2.plus(
                    measuInfo.spo2_Unit
                )
            )
        )
        measuInfoList.add(
            InfoBean(
                "BP_Diastolic",
                if (measuInfo.BP_Diastolic.length > 10) measuInfo.BP_Diastolic else measuInfo.BP_Diastolic.plus(
                    measuInfo.BP_Diastolic_Unit
                )
            )
        )
        measuInfoList.add(
            InfoBean(
                "BP_Systolic",
                if (measuInfo.BP_Systolic.length > 10) measuInfo.BP_Systolic else measuInfo.BP_Systolic.plus(
                    measuInfo.BP_Systolic_Unit
                )
            )
        )
        measuInfoList.add(
            InfoBean(
                "Respiration",
                if (measuInfo.respiration.length > 10) measuInfo.respiration else measuInfo.respiration.plus(
                    measuInfo.respiration_Unit
                )
            )
        )
        measuInfoList.add(
            InfoBean(
                "PRV",
                if (measuInfo.PRV.length > 10) measuInfo.PRV else measuInfo.PRV.plus(measuInfo.PRV_Unit)
            )
        )
        measuInfoList.add(
            InfoBean(
                "PVI",
                if (measuInfo.PVI.length > 10) measuInfo.PVI else measuInfo.PVI.plus(measuInfo.PVI_Unit)
            )
        )
        /* measuInfoList.add(
             InfoBean(
                 "Pneumatic System Pressure",
                 measuInfo.pneumaticSystemPressure
             )
         )*/
        measuInfoList.add(
            InfoBean(
                "BatteryStatus",
                measuInfo.batteryStatus.plus(measuInfo.batteryStatus_Unit)
            )
        )
        /* measuInfoList.add(InfoBean("AmbientTempSensorBoard", measuInfo.ambientTempSensorBoard))
         measuInfoList.add(InfoBean("AmbientTempMotherBoard", measuInfo.ambientTempMotherBoard))*/
    }

    override fun onViewClick(position: Int) {

    }

    override fun onResume() {
        super.onResume()
        if (mVVitalManager != null) {
            val result = mVVitalManager!!.connectPeripheral(mDeviceAddress)
            Log.d(TAG, "Connect request result=$result")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unbindService(mServiceConnection)
        mVVitalManager = null
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.gatt_services, menu)
        if (mConnected) {
            menu.findItem(R.id.menu_connect).isVisible = false
            menu.findItem(R.id.menu_disconnect).isVisible = true
        } else {
            menu.findItem(R.id.menu_connect).isVisible = true
            menu.findItem(R.id.menu_disconnect).isVisible = false
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_connect -> {
                mVVitalManager!!.connectPeripheral(mDeviceAddress)
                return true
            }
            R.id.menu_disconnect -> {
                mVVitalManager!!.disconnectPeripheral()
                return true
            }
            android.R.id.home -> {
                onBackPressed()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun updateConnectionState(resourceId: Int) {
        runOnUiThread {
            binding.tvState.text = getString(R.string.str_state, getString(resourceId))
        }
    }

    companion object {
        private val TAG = DeviceDetailActivity::class.java.simpleName

        @JvmField
        var EXTRAS_DEVICE_NAME = "DEVICE_NAME"
        @JvmField
        var EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS"
    }

    @SuppressLint("SetTextI18n")
    override fun onSuccess(jsonResponse: String, command: String) {

        if (command.equals(VSCommandName.CODE_DINFO, true)) {

            val response = Gson().fromJson(jsonResponse, DeviceInfo::class.java)
            Log.e("onSuccess", "CODE_DINFO response: $jsonResponse")

            if (response is DeviceInfo) {
                setDeviceInfoList(response)
                setAdapter()
            }
        } else if (command.equals(VSCommandName.CODE_CURDT, true)) {
            val response = Gson().fromJson(jsonResponse, CurrentTimeBean::class.java)
            Log.e("onSuccess", "CODE_CURDT response: $jsonResponse")
        } else if (command.equals(VSCommandName.CODE_TEMPM, true)) {

            val response = Gson().fromJson(jsonResponse, TempMeasData::class.java)
            Log.e("onSuccess", "CODE_TEMPM response: $jsonResponse")

            if (response is TempMeasData) {
                when {
                    response.status == ResponseStatus.STOPPED -> {
                        runOnUiThread {

                            Log.e(
                                "onSuccess",
                                "CODE_TEMPM status: STOPPED - " + ResponseStatus.STOPPED
                            )
                        }
                    }
                    response.status == ResponseStatus.READY -> {
                        Log.e("onSuccess", "CODE_TEMPM status: READY - " + ResponseStatus.READY)
                    }
                    response.status == ResponseStatus.IN_PROCESS -> {
                        Log.e(
                            "onSuccess",
                            "CODE_TEMPM status: IN_PROCESS - " + ResponseStatus.IN_PROCESS
                        )
                    }
                    response.status == ResponseStatus.COMPLETE -> {
                        Log.e(
                            "onSuccess",
                            "CODE_TEMPM status: COMPLETE - " + ResponseStatus.COMPLETE
                        )
                        Log.e("onSuccess", "CODE_TEMPM bodyTemp: " + response.bodyTemperature)

                        runOnUiThread {

                            if (binding.tvMessage.visibility == View.VISIBLE) {
                                binding.tvMessage.visibility = View.INVISIBLE
                            }

                            if (response.bodyTemperature.matches("-?\\d+(\\.\\d+)?".toRegex())) {
                                binding.tvBodyTemp.text =
                                    response.bodyTemperature + response.bodyTemperature_Unit
                            } else {
                                binding.tvBodyTemp.text = response.bodyTemperature
                            }

                            if (mVVitalManager != null) {
                                mVVitalManager!!.stopTemperature()
                            }
                        }
                    }
                    response.status == ResponseStatus.ERROR -> {//Error OR TimeOut
                        Log.e("onSuccess", "CODE_TEMPM status: ERROR - " + ResponseStatus.ERROR)

                        runOnUiThread {
                            binding.tvBodyTemp.text = getString(R.string.str_error_timeout)

                            if (binding.tvMessage.visibility == View.INVISIBLE) {
                                binding.tvMessage.visibility = View.VISIBLE //set error message.
                            }
                            binding.tvMessage.text =
                                resources.getString(R.string.str_error_timeout).plus("\n")
                                    .plus(response.errorDesc)

                            if (mVVitalManager != null) {
                                mVVitalManager!!.stopTemperature()
                            }
                        }
                    }
                }
            }
        } else if (command.equals(VSCommandName.CODE_FWUPGRADE, true)) {
            val response = Gson().fromJson(jsonResponse, FWData::class.java)
            Log.e("onSuccess", "FWDAT response: $jsonResponse")
            if (response is FWData) {
                runOnUiThread {
                    if (response.fwStatus == 1) {
                        binding.tvProgressCount.text =
                            "" + response.installedPacketCount + "/" + response.totalPackets
                        if (response.firmwareType.equals(mVVitalManager?.firmwareMainBoard, true)) {
                            binding.tvFirmwareType.text = "Downloading Main board firmware"
                        } else {
                            binding.tvFirmwareType.text = "Downloading Sensor board firmware"
                        }
                    } else if (response.fwStatus == 3) {
                        val dialogBuilder = AlertDialog.Builder(this)
                        // set message of alert dialog
                        dialogBuilder.setMessage("Firmware update successful")
                            // if the dialog is cancelable
                            .setCancelable(false)
                            // positive button text and action
                            .setPositiveButton("Ok") { dialog, _ ->
                                dialog.dismiss()
                            }

                        // create dialog box
                        val alert = dialogBuilder.create()
                        // set title for alert dialog box
                        alert.setTitle(getString(R.string.app_name))
                        // show alert dialog
                        alert.show()
                    }
                }
            }
        } else if (command.equals(VSCommandName.CODE_MEASU, true)) {

            val response = Gson().fromJson(jsonResponse, MeasuInfo::class.java)

            Log.e("onSuccess", "CODE_MEASU response: $jsonResponse")

            if (response is MeasuInfo) {
                when {
                    response.status == FWUpgradeState.IDLE -> {
                        Log.e("onSuccess", "CODE_MEASU status: IDLE - " + FWUpgradeState.IDLE)
                    }
                    response.status == FWUpgradeState.READY -> {
                        Log.e("onSuccess", "CODE_MEASU status: READY - " + FWUpgradeState.READY)
                        runOnUiThread {
                            //show start button
                            binding.tvStart.visibility = View.VISIBLE
                            binding.tvStart.invalidate()
                            binding.tvStart.text = "Start"

                            binding.rvMeasuInfo.visibility = View.GONE


                        }
                    }
                    response.status == FWUpgradeState.BUSY -> {
                        Log.e("onSuccess", "CODE_MEASU status: BUSY - " + FWUpgradeState.BUSY)
                        runOnUiThread {
                            //stop text in start button
                            //stop text in start button
                            binding.tvStart.text = "Stop"
                            binding.tvMessage.visibility = View.VISIBLE
                            binding.tvMessage.text = "In Progress.."
                        }
                    }
                    response.status == FWUpgradeState.COMPLETE -> {
                        Log.e(
                            "onSuccess",
                            "CODE_MEASU status: COMPLETE - " + FWUpgradeState.COMPLETE
                        )
                        runOnUiThread {

                            if (mVVitalManager != null) {
                                mVVitalManager!!.stopMeasurement(selectedCommand)
                            }

                            binding.rvMeasuInfo.visibility = View.VISIBLE //
                            binding.tvMessage.visibility = View.INVISIBLE //hide message
                            binding.tvStart.visibility = View.GONE //hide start button

                            setMeasuInfoList(response) //Create list
                            setMeasuInfoAdapter() // set adapter
                        }
                    }
                    response.status == FWUpgradeState.ERROR -> {//Error OR TimeOut
                        Log.e("onSuccess", "CODE_MEASU status: ERROR - " + FWUpgradeState.ERROR)

                        runOnUiThread {
                            binding.rvMeasuInfo.visibility = View.GONE
                            binding.tvStart.visibility = View.GONE //hide start button on error.
                            binding.tvMessage.visibility = View.VISIBLE //set error message.
                            binding.tvMessage.text =
                                resources.getString(R.string.str_error_timeout).plus("\n")
                                    .plus(response.errorDesc)

                            if (mVVitalManager != null) {
                                mVVitalManager!!.stopMeasurement(selectedCommand)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onFailure(error: VSError, command: String) {
        Log.e(
            "onFailure", "command:" + command + ", error code:" + error.code
                    + ", error type:" + error.errorType + ", generatedBy:" + error.generatedBy
                    + ", generatedBy:" + error.generatedBy + ", localizedDescription:" + error.localizedDescription
                    + ", reporting:" + error.reporting
        )
    }

    override fun onException(errorMsg: String, command: String) {
        Log.e("onException", "command:$command, errorMsg:$errorMsg")
        runOnUiThread {
            Snackbar.make(binding.root, errorMsg, Snackbar.LENGTH_LONG).show()

            if (command.equals(VSCommandName.CODE_FWUPGRADE, true)) {
                binding.tvFirmwareType.text = ""
            }
        }

    }

    override fun onPeripheralStateChange(state: Int) {
        Log.e("onDeviceConnection", "state:$state")

        when (state) {
            ConnectionState.CONNECTED -> {//Connected
                mConnected = true
                updateConnectionState(R.string.connected)
                invalidateOptionsMenu()

                runOnUiThread {
                    mVVitalManager!!.getDeviceDuration()
                }
            }
                ConnectionState.DISCONNECTED -> {//Disconnected
                mConnected = false
                updateConnectionState(R.string.disconnected)
                invalidateOptionsMenu()
            }
                    ConnectionState.CONNECTING -> {//Connecting
                Log.e("state", "connecting...")
            }
        }
    }
}