package com.vvvital.vital_smartvs.util

/*
* CODE_MEASU
*
* */
class MeasuType {
    companion object {
        const val SpO2_PI_PR = "0700" //Short PPG: 0x0700
        const val RR_PVi_PRV_SpO2_PI_PR = "7700" //Long PPG: 0x7700
        const val BLOOD_PRESSURE = "8000" //Blood Pressure Only: 0x8000
    }
}