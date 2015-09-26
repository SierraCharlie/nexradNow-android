package com.nexradnow.android.services;

import android.content.Context;
import android.util.Log;
import com.google.inject.Inject;
import com.nexradnow.android.model.LatLongCoordinates;
import com.nexradnow.android.model.NexradProduct;
import com.nexradnow.android.model.NexradStation;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.ListUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPClientConfig;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPHTTPClient;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.TimeZone;

/**
 * Created by hobsonm on 9/15/15.
 *
 * This service handles the interface with the Nexrad data provider. It gets station lists,
 * product types, and actual recent station data as needed.
 *
 */
public class NexradDataManager {

    @Inject
    protected Context ctx;

    public final static String TAG="NEXRADDATA";
    protected final static String CACHESTATIONFILE = "nxstations.obj";

    public List<NexradStation> getNexradStations () {
        List<NexradStation> results = null;
        File cachedList = new File(ctx.getCacheDir(),CACHESTATIONFILE);
        if (cachedList.exists()&&cachedList.canRead()) {
            // Load from cache
            try {
                InputStream fis = new FileInputStream(cachedList);
                ObjectInputStream ois = new ObjectInputStream(new FileInputStream(cachedList));
                results = (List<NexradStation>)ois.readObject();
                ois.close();
                fis.close();
            } catch (Exception ex) {
                Log.e(TAG,"error reading cached station list",ex);
                results = null;
            }
        }
        if (results == null) {
            // Fetch list of stations at: http://www.ncdc.noaa.gov/homr/file/nexrad-stations.txt
            String urlString = "http://www.ncdc.noaa.gov/homr/file/nexrad-stations.txt";
            try {
                URL url = new URL(urlString);
                URLConnection conn = url.openConnection();
                InputStream is = conn.getInputStream();
                List<String> stationList = IOUtils.readLines(is);
                results = new ArrayList<NexradStation>();
                boolean inHeader = true;
                for (String line : stationList) {
                    if (line.startsWith("---")) {
                        inHeader = false;
                        continue;
                    } else if (inHeader) {
                        continue;
                    }
                    // Fixed-length lines (verify length is as expected!)
                    line = line.trim();
                    if ((line.length() != 146) || (!line.endsWith("NEXRAD"))) {
                        throw new IOException("unexpected NEXRAD station list file format");
                    }
                    String identifier = line.substring(9, 13);
                    String latitude = line.substring(106, 115);
                    String longitude = line.substring(116, 126);
                    Log.d(TAG, "station [" + identifier + "] lat[" + latitude + "] long[" + longitude + "]");
                    LatLongCoordinates coords = new LatLongCoordinates(Float.parseFloat(latitude), Float.parseFloat(longitude));
                    NexradStation station = new NexradStation(identifier, coords);
                    results.add(station);
                }
            } catch (IOException ioex) {
                Log.e(TAG, "error fetching list of NEXRAD stations", ioex);
                // TODO: propagate error to app for user to see
            }
            // Write to cache
            try {
                OutputStream os = new FileOutputStream(cachedList);
                ObjectOutputStream oos = new ObjectOutputStream(os);
                oos.writeObject(results);
                oos.close();
                os.close();
            } catch (Exception ex) {
                Log.e(TAG, "error writing cached station list file", ex);
            }
        }
        return results;
    }

    public List<NexradStation> sortClosest(List<NexradStation> stationList, final LatLongCoordinates coords) {
        Comparator<NexradStation> closeComparator = new Comparator<NexradStation>() {
            @Override
            public int compare(NexradStation lhs, NexradStation rhs) {
                if (lhs.getCoords().distanceTo(coords)>rhs.getCoords().distanceTo(coords)) {
                    return 1;
                } else {
                    return -1;
                }
            }
        };
        Collections.sort(stationList, closeComparator);
        return stationList;
    }

    public List<NexradProduct> getNexradProducts(String productCode, NexradStation station, int ageMaxMinutes) {
        List<NexradProduct> results = new ArrayList<NexradProduct>();
        // TODO: configure base FTP path/pattern dynamically
        String ftpPath = "ftp://tgftp.nws.noaa.gov/SL.us008001/DF.of/DC.radar/DS."
                +productCode+"/SI."+station.getIdentifier().toLowerCase();
        String ftpHost = "tgftp.nws.noaa.gov";
        String ftpDir = "/SL.us008001/DF.of/DC.radar/DS."
                +productCode+"/SI."+station.getIdentifier().toLowerCase();
        // Get listing of available items
        FTPClientConfig conf = new FTPClientConfig(FTPClientConfig.SYST_UNIX);
        conf.setServerTimeZoneId("UTC");
        FTPClient ftpClient = new FTPClient();
        ftpClient.configure(conf);
        try {
            ftpClient.connect(InetAddress.getByName("tgftp.nws.noaa.gov"));
            ftpClient.enterLocalPassiveMode();
            ftpClient.login("anonymous", "nexradnow");
            ftpClient.changeWorkingDirectory(ftpDir);
            ftpClient.setFileType(FTP.BINARY_FILE_TYPE);
            FTPFile[] files = ftpClient.listFiles();
            Calendar nowCal = Calendar.getInstance();
            for (FTPFile file : files) {
                if (file.getTimestamp().getTimeInMillis()>nowCal.getTimeInMillis()-ageMaxMinutes*60*1000) {
                        // qualifies!
                        InputStream stream = ftpClient.retrieveFileStream(file.getName());
                        byte[] productBytes = null;
                        try {
                            productBytes = IOUtils.toByteArray(stream);
                            stream.close();
                        } catch (Exception ex) {
                            Log.e(TAG, "data transfer error for "+ftpDir+"/"+file.getName(), ex);
                            ftpClient.completePendingCommand();
                            continue;
                        }
                        if (!ftpClient.completePendingCommand()) {
                            String status = ftpClient.getStatus();
                            throw new IOException("FTPClient completePendingCommmand() returned error:" + status);
                        }
                        NexradProduct product = new NexradProduct(station, file, productCode, file.getTimestamp(), productBytes);
                        results.add(product);
                }
            }
        } catch (Exception ex) {
            Log.e(TAG,"data transfer error",ex);
            throw new IllegalStateException("tgftp data transfer error", ex);
        }
        return results;
    }
}
