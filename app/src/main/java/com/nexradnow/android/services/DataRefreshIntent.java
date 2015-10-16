package com.nexradnow.android.services;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import com.google.inject.Inject;
import com.nexradnow.android.app.R;
import com.nexradnow.android.app.SettingsActivity;
import com.nexradnow.android.exception.NexradNowException;
import com.nexradnow.android.model.LatLongCoordinates;
import com.nexradnow.android.model.NexradProduct;
import com.nexradnow.android.model.NexradStation;
import roboguice.service.RoboIntentService;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Created by hobsonm on 9/15/15.
 */
public class DataRefreshIntent extends RoboIntentService {

    protected class DataRefreshIntentException extends Exception {
        public DataRefreshIntentException(String detailMessage) {
            super(detailMessage);
        }
    };

    public static final int STATUS_RUNNING = 0;
    public static final int STATUS_FINISHED = 1;
    public static final int STATUS_ERROR = 2;

    private static final String TAG = "DownloadService";

    public static final String GETWXACTION = "com.nexradnow.android.newproduct";
    public static final String GETSTATIONACTION = "com.nexradnow.android.stationlisting";

    @Inject
    protected Context ctx;

    @Inject
    protected NexradDataManager nexradDataManager;

    public DataRefreshIntent() {
        super(DataRefreshIntent.class.getName());
    }

    @Override

    protected void onHandleIntent(Intent intent) {
        if (GETWXACTION.equals(intent.getAction())) {
            wxAction(intent);
        }
        if (GETSTATIONACTION.equals(intent.getAction())) {
            getStationAction(intent);
        }
    }

    private void getStationAction(Intent intent) {
        intent.setAction(GETSTATIONACTION);
        try {
            List<NexradStation> srcStations = nexradDataManager.getNexradStations();
            Bundle stations = new Bundle();
            stations.putSerializable("list",(Serializable)srcStations);
            intent.putExtra("com.nexradnow.android.stationlist", stations);
            intent.putExtra("com.nexradnow.android.status", STATUS_FINISHED);
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
        } catch (Exception ex) {
            notifyException(intent, ex);
        }
    }

    protected void wxAction(Intent intent) {
        intent.setAction(GETWXACTION);
        try {
            LatLongCoordinates coords = (LatLongCoordinates) intent.getSerializableExtra("com.nexradnow.android.coords");
            intent.putExtra("com.nexradnow.android.status", STATUS_RUNNING);
            intent.putExtra("com.nexradnow.android.statusmsg",getResources().getString(R.string.msg_getting_station_list));
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
            List<NexradStation> srcStations = nexradDataManager.getNexradStations();
            // TODO: handle null or empty list due to download/cache failure
            List<NexradStation> stations = nexradDataManager.sortClosest(srcStations, coords);
            SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(ctx);
            int maxDistance = Integer.parseInt(sharedPref.getString(SettingsActivity.KEY_PREF_NEXRAD_STATIONDISTANCE,"160"));
            // Pick the four closest stations IF the distance to the nearest is less than 350 km.
            if (coords.distanceTo(stations.get(0).getCoords()) > maxDistance) {
                throw new DataRefreshIntentException("No Nexrad stations within "+maxDistance+" km");
            }
            HashMap<NexradStation, List<NexradProduct>> productMap = new HashMap<NexradStation, List<NexradProduct>>();
            for (NexradStation station : stations) {
                if (coords.distanceTo(station.getCoords()) > maxDistance) {
                    continue;
                }
                intent.putExtra("com.nexradnow.android.statusmsg",
                        getResources().getString(R.string.msg_getting_data_for_station) + " "
                                + station.getIdentifier());
                LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
                try {
                    List<NexradProduct> products = nexradDataManager.getNexradProducts(
                            intent.getStringExtra("com.nexradnow.android.productcode"), station, 30);
                    productMap.put(station, products);
                } catch (NexradNowException nex) {
                    notifyException(intent, nex);
                    productMap.put(station,new ArrayList<NexradProduct>());
                }
            }
            intent.putExtra("com.nexradnow.android.productmap", productMap);
            intent.putExtra("com.nexradnow.android.status", STATUS_FINISHED);
            intent.putExtra("com.nexradnow.android.coords", coords);
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
        } catch (Exception ex) {
            notifyException(intent, ex);
        }
    }

    private void notifyException(Intent intent, Exception ex) {
        if (ex instanceof DataRefreshIntentException) {
            intent.putExtra("com.nexradnow.android.errmsg", ex.getMessage());
        } else if (ex instanceof NexradNowException ){
            intent.putExtra("com.nexradnow.android.errmsg", ex.getMessage());
        } else {
            intent.putExtra("com.nexradnow.android.errmsg", ex.toString());
        }
        intent.putExtra("com.nexradnow.android.status", STATUS_ERROR);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }
}
