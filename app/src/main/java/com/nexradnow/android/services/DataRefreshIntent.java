package com.nexradnow.android.services;

import android.app.IntentService;
import android.content.Intent;
import android.support.v4.content.LocalBroadcastManager;
import com.google.inject.Inject;
import com.nexradnow.android.app.R;
import com.nexradnow.android.model.LatLongCoordinates;
import com.nexradnow.android.model.NexradProduct;
import com.nexradnow.android.model.NexradStation;
import roboguice.RoboGuice;
import roboguice.service.RoboIntentService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    @Inject
    protected NexradDataManager nexradDataManager;

    public DataRefreshIntent() {
        super(DataRefreshIntent.class.getName());
    }

    @Override

    protected void onHandleIntent(Intent intent) {
        try {
            intent.setAction("com.nexradnow.android.newproduct");

            LatLongCoordinates coords = (LatLongCoordinates) intent.getSerializableExtra("com.nexradnow.android.coords");
            intent.putExtra("com.nexradnow.android.status", STATUS_RUNNING);
            intent.putExtra("com.nexradnow.android.statusmsg",getResources().getString(R.string.msg_getting_station_list));
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
            List<NexradStation> srcStations = nexradDataManager.getNexradStations();
            // TODO: handle null or empty list due to download/cache failure
            List<NexradStation> stations = nexradDataManager.sortClosest(srcStations, coords);
            // Pick the four closest stations IF the distance to the nearest is less than 350 km.
            if (coords.distanceTo(stations.get(0).getCoords()) > 350.0) {
                throw new DataRefreshIntentException("No Nexrad stations within 350 km");
            }
            HashMap<NexradStation, List<NexradProduct>> productMap = new HashMap<NexradStation, List<NexradProduct>>();
            for (int index = 0; index < 4; index++) {
                intent.putExtra("com.nexradnow.android.statusmsg",
                        getResources().getString(R.string.msg_getting_data_for_station)+" "
                                +stations.get(index).getIdentifier());
                LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
                NexradStation station = stations.get(index);
                List<NexradProduct> products = nexradDataManager.getNexradProducts("p38cr", station, 30);
                productMap.put(station, products);
            }
            intent.putExtra("com.nexradnow.android.productmap", productMap);
            intent.putExtra("com.nexradnow.android.status", STATUS_FINISHED);
            intent.putExtra("com.nexradnow.android.coords", coords);
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
        } catch (Exception ex) {
            // Report upstream
            intent.setAction("com.nexradnow.android.newproduct");
            if (ex instanceof DataRefreshIntentException) {
                intent.putExtra("com.nexradnow.android.errmsg", ex.getMessage());
            } else {
                intent.putExtra("com.nexradnow.android.errmst", ex.toString());
            }
            intent.putExtra("com.nexradnow.android.status", STATUS_ERROR);
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
        }
    }
}
