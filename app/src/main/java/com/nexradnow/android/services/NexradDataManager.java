package com.nexradnow.android.services;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;
import com.nexradnow.android.app.NexradApp;
import com.nexradnow.android.app.R;
import com.nexradnow.android.app.SettingsActivity;
import com.nexradnow.android.exception.NexradNowException;
import com.nexradnow.android.model.LatLongCoordinates;
import com.nexradnow.android.model.NexradFTPContents;
import com.nexradnow.android.model.NexradProduct;
import com.nexradnow.android.model.NexradProductCache;
import com.nexradnow.android.model.NexradStation;
import org.apache.commons.io.IOUtils;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPClientConfig;
import org.apache.commons.net.ftp.FTPFile;
import toothpick.Toothpick;

import javax.inject.Inject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Created by hobsonm on 9/15/15.
 *
 * This service handles the interface with the Nexrad data provider. It gets station lists,
 * product types, and actual recent station data as needed.
 *
 */
public class NexradDataManager {


    public final static String TAG="NEXRADDATA";
    protected final static String CACHESTATIONFILE = "nxstations.obj";

    protected Map<NexradStation,Map<String,NexradProductCache>> cache =
            new HashMap<NexradStation, Map<String, NexradProductCache>>();

    @Inject
    protected Context ctx;

    public NexradDataManager() {
        Toothpick.inject(this,Toothpick.openScope(NexradApp.APPSCOPE));
    }

    public List<NexradStation> getNexradStations () {
        List<NexradStation> results = null;
        File cachedList = new File(ctx.getCacheDir(),CACHESTATIONFILE);
        if (cachedList.exists()&&cachedList.canRead()&&
                (System.currentTimeMillis()<((long)cachedList.lastModified()+TimeUnit.DAYS.toMillis(30)))) {
            // Load from cache
            try {
                InputStream fis = new FileInputStream(cachedList);
                ObjectInputStream ois = new ObjectInputStream(fis);
                results = (List<NexradStation>)ois.readObject();
                ois.close();
                fis.close();
            } catch (Exception ex) {
                Log.e(TAG,"error reading cached station list",ex);
                results = null;
            }
        }
        if (results == null) {
            SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(ctx);
            // Fetch list of stations at: http://www.ncdc.noaa.gov/homr/file/nexrad-stations.txt
            String urlString = sharedPref.getString(SettingsActivity.KEY_PREF_NEXRAD_STATIONLIST_URL,
                    ctx.getString(R.string.pref_nexrad_stationlist_default));
            try {
                URL url = new URL(urlString);
                HttpURLConnection conn = (HttpURLConnection)url.openConnection();
                conn.setInstanceFollowRedirects(true);
                conn.connect();
                int response = conn.getResponseCode();
                if ((response == HttpURLConnection.HTTP_MOVED_TEMP)||
                        (response == HttpURLConnection.HTTP_MOVED_PERM)) {
                    String location = conn.getHeaderField("Location");
                    url = new URL(location);
                    conn.disconnect();
                    conn = (HttpURLConnection)url.openConnection();
                    response = conn.getResponseCode();
                }
                if (response != HttpURLConnection.HTTP_OK) {
                    throw new IOException("Bad HTTP response: "+response);
                }
                URL targetUrl = conn.getURL();
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
                        if (line.endsWith("TDWR")) {
                            continue;
                        }
                        throw new IOException("unexpected NEXRAD station list file format");
                    }
                    String identifier = line.substring(9, 13);
                    String latitude = line.substring(106, 115);
                    String longitude = line.substring(116, 126);
                    String location = line.substring(20,50).trim()+" "+line.substring(72,74).trim();
                    Log.d(TAG, "station [" + identifier + "] lat[" + latitude + "] long[" + longitude + "]");
                    LatLongCoordinates coords = new LatLongCoordinates(Float.parseFloat(latitude), Float.parseFloat(longitude));
                    NexradStation station = new NexradStation(identifier, coords, location);
                    if ("KOUN".equals(station.getIdentifier())) { continue; }
                    if ("KCRI".equals(station.getIdentifier())) { continue; }
                    results.add(station);
                }
                if (results.isEmpty()) {
                    throw new IOException("No NEXRAD stations retrieved");
                }
            } catch (IOException ioex) {
                Log.e(TAG, "error fetching list of NEXRAD stations", ioex);
                throw new RuntimeException("error fetching list of NEXRAD stations: "+ioex.toString(), ioex);
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
                // Isn't necessarily a failure, so we don't push the notification out
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
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(ctx);
        List<NexradProduct> results = new ArrayList<NexradProduct>();
        String ftpHost = sharedPref.getString(SettingsActivity.KEY_PREF_NEXRAD_FTPHOST,
                ctx.getString(R.string.pref_nexrad_ftphost_default));
        String ftpDir = sharedPref.getString(SettingsActivity.KEY_PREF_NEXRAD_FTPDIR,
                ctx.getString(R.string.pref_nexrad_ftpdir_default));
        String ftpProductStationPath = "/DS."
                +productCode+"/SI."+station.getIdentifier().toLowerCase();
        String eMailAddress = sharedPref.getString(SettingsActivity.KEY_PREF_NEXRAD_EMAILADDRESS,
                ctx.getString(R.string.pref_nexrad_emailaddress_default));
        if ((eMailAddress == null)||(eMailAddress.isEmpty())) {
            throw new NexradNowException("No email address configured in settings");
        }
        // Get listing of available items
        FTPClientConfig conf = new FTPClientConfig(FTPClientConfig.SYST_UNIX);
        conf.setServerTimeZoneId("UTC");
        FTPClient ftpClient = new FTPClient();
        ftpClient.configure(conf);
        ftpClient.setDataTimeout(20000);
        ftpClient.setConnectTimeout(20000);
        ftpClient.setDefaultTimeout(20000);
        try {
            // Check cache for file list
            Calendar nowCal = Calendar.getInstance();
            Map<String,NexradProductCache> stationCache = cache.get(station);
            if (stationCache == null) {
                stationCache = new HashMap<String,NexradProductCache>();
                cache.put(station,stationCache);
            }
            NexradProductCache productCache = stationCache.get(productCode);
            NexradFTPContents productContents = null;
            if (productCache != null) {
                productCache.ageCache(60, TimeUnit.MINUTES);
                productContents = productCache.getFtpContents();
                if ((productContents != null)&&(productContents.getTimestamp().getTimeInMillis()+TimeUnit.MILLISECONDS.convert(5,TimeUnit.MINUTES)<nowCal.getTimeInMillis())){
                    // Invalidate
                    productContents = null;
                }
            }
            if (productContents == null) {
                // We'll need to get a fresh directory listing
                initFtp(ftpClient, ftpHost, eMailAddress);
                if (!ftpClient.changeWorkingDirectory(ftpDir+ftpProductStationPath)) {
                    // Unsuccessful completion
                    throw new NexradNowException("cannot select product download directory on FTP server");
                }
                productContents = new NexradFTPContents(productCode,ftpClient.listFiles(),station);
            }
            if (productCache == null) {
                productCache = new NexradProductCache(productContents,new ArrayList<NexradProduct>());
            }
            productCache.setFtpContents(productContents);
            stationCache.put(productCode,productCache);
            for (FTPFile file : productContents.getFileList()) {
                if ((file.getTimestamp().getTimeInMillis()>nowCal.getTimeInMillis()-ageMaxMinutes*60*1000)&&
                        (!"sn.last".equals(file.getName()))&&(file.getName().startsWith("sn."))) {
                    // qualifies!
                    // First see if we have it in our cache. NB: cache has already been aged
                    NexradProduct product = null;
                    for (NexradProduct cachedProduct : productCache.getProducts()) {
                        if (cachedProduct.getFileInfo().getName().equals(file.getName()) &&
                                cachedProduct.getFileInfo().getTimestamp().equals(file.getTimestamp())) {
                            // cached object found
                            product = cachedProduct;
                            break;
                        }
                    }
                    if (product == null) {
                        // Not found in cache - must retrieve:
                        if (initFtp(ftpClient,ftpHost,eMailAddress)) {
                            if(!ftpClient.changeWorkingDirectory(ftpDir+ftpProductStationPath)) {
                                throw new NexradNowException("cannot select product download directory on FTP server");
                            }
                        }
                        InputStream stream = ftpClient.retrieveFileStream(file.getName());
                        if (stream == null) {
                            Log.e(TAG,"data transfer error for "+ftpDir+ftpProductStationPath+" "+file.getName()+" reply:"+ ftpClient.getReplyString());
                            ftpClient.completePendingCommand();
                        } else {
                            byte[] productBytes = null;
                            try {
                                productBytes = IOUtils.toByteArray(stream);
                                stream.close();
                            } catch (Exception ex) {
                                Log.e(TAG, "data transfer error for " + ftpDir + "/" + file.getName(), ex);
                                ftpClient.completePendingCommand();
                                continue;
                            }
                            if (!ftpClient.completePendingCommand()) {
                                String status = ftpClient.getStatus();
                                throw new IOException("FTPClient completePendingCommmand() returned error:" + status);
                            }
                            product = new NexradProduct(station, file, productCode, file.getTimestamp(), productBytes);
                            // add to cache
                            productCache.getProducts().add(product);
                        }
                    }
                    if (product != null) {
                        results.add(product);
                    }
                }
            }
            // Sort so that items in list are in order of most recent -> least recent
            Comparator<NexradProduct> comparator = new Comparator<NexradProduct>() {
                @Override
                public int compare(NexradProduct lhs, NexradProduct rhs) {
                    if (lhs.getTimestamp().before(rhs.getTimestamp())) {
                        return 1;
                    } else if (lhs.getTimestamp().after(rhs.getTimestamp())) {
                        return -1;
                    }
                    return 0;
                }
            };
            Collections.sort(results,comparator);
        } catch (Exception ex) {
            Log.e(TAG,"data transfer error["+station+":"+productCode+"]",ex);
            throw new NexradNowException("tgftp data transfer error ["+station.getIdentifier()+":"+productCode+"]"+ex.toString());
        }
        return results;
    }

    private boolean initFtp(FTPClient ftpClient, String ftpHost, String eMailAddress) throws Exception {
        if (ftpClient.isConnected()) {
            return false;
        }
        ftpClient.connect(InetAddress.getByName(ftpHost));
        ftpClient.enterLocalPassiveMode();
        ftpClient.login("anonymous", eMailAddress);
        ftpClient.setFileType(FTP.BINARY_FILE_TYPE);
        return true;
    }
}
