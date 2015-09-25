package com.nexradnow.android.model;

import org.apache.commons.net.ftp.FTPFile;

import java.io.Serializable;
import java.util.Calendar;

/**
 * Created by hobsonm on 9/16/15.
 */
public class NexradProduct implements Serializable {

    protected NexradStation station;
    protected FTPFile fileInfo;
    protected String productCode;
    protected Calendar timestamp;
    protected byte[] binaryData;


    public NexradProduct(NexradStation station, FTPFile fileInfo, String productCode,
                         Calendar timestamp, byte[] binaryData) {
        this.station = station;
        this.fileInfo = fileInfo;
        this.productCode = productCode;
        this.timestamp = timestamp;
        this.binaryData = binaryData;
    }

    public NexradStation getStation() {
        return station;
    }

    public void setStation(NexradStation station) {
        this.station = station;
    }

    public FTPFile getFileInfo() {
        return fileInfo;
    }

    public void setFileInfo(FTPFile fileInfo) {
        this.fileInfo = fileInfo;
    }

    public String getProductCode() {
        return productCode;
    }

    public void setProductCode(String productCode) {
        this.productCode = productCode;
    }

    public Calendar getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Calendar timestamp) {
        this.timestamp = timestamp;
    }

    public byte[] getBinaryData() {
        return binaryData;
    }

    public void setBinaryData(byte[] binaryData) {
        this.binaryData = binaryData;
    }

}
