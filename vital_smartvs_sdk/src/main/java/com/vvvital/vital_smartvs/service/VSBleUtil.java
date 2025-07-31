package com.vvvital.vital_smartvs.service;

/**
 * SmartVSApp
 * <p>
 * Created By Administrator on 10/23/2019
 * <p>
 * Copyright Softweb Solutions Inc. 2019,  All rights reserved.
 */
class VSBleUtil {

    protected static int calCrc16(final byte[] byteData) {
        int crc = 0xFFFF;
        for (byte byteDatum : byteData) {
            crc = ((crc >>> 8) | (crc << 8)) & 0xffff;
            crc ^= (byteDatum & 0xff); //Convert result into unsigned int
            crc ^= ((crc & 0xff) >> 4);
            crc ^= (crc << 12) & 0xffff;
            crc ^= ((crc & 0xFF) << 5) & 0xffff;
        }
        crc &= 0xffff;
        return crc;
    }

}
