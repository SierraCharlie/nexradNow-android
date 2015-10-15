package com.nexradnow.android.model;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPFile;

import java.util.Calendar;

/**
 * This class encapsulates the FTP file list for a directory on the Nexrad data server.
 *
 * Created by hobsonm on 10/12/15.
 */
public class NexradFTPContents {
    protected NexradStation station;
    protected String productCode;
    protected Calendar timestamp;
    protected FTPFile[] fileList;

    public NexradFTPContents(String productCode, FTPFile[] fileList, NexradStation station) {
        this.productCode = productCode;
        this.fileList = fileList;
        this.station = station;
        setFileList(fileList);
    }

    public void setFileList(FTPFile[] fileList) {
        this.fileList = fileList;
        Calendar newestTimestamp = null;
        for (FTPFile file : fileList) {
            if ((newestTimestamp==null)||(file.getTimestamp().after(newestTimestamp))) {
                newestTimestamp = file.getTimestamp();
            }
        }
        timestamp = newestTimestamp;
    }

    public FTPFile[] getFileList() {

        return fileList;
    }

    public String getProductCode() {
        return productCode;
    }

    public NexradStation getStation() {
        return station;
    }

    public Calendar getTimestamp() {
        return timestamp;
    }
}
