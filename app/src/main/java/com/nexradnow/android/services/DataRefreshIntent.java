package com.nexradnow.android.services;

import android.app.IntentService;
import android.content.Intent;
import android.support.v4.content.LocalBroadcastManager;
import com.google.inject.Inject;
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
        LatLongCoordinates coords = (LatLongCoordinates) intent.getSerializableExtra("com.nexradnow.android.coords");
        List<NexradStation> srcStations = nexradDataManager.getNexradStations();
        // TODO: handle null or empty list due to download/cache failure
        List<NexradStation> stations = nexradDataManager.sortClosest(srcStations, coords);
        // Pick the four closest stations IF the distance to the nearest is less than 350 km.
        if (coords.distanceTo(stations.get(0).getCoords())>350.0) {
            // TODO: Error - no nearby station(s)
        }
        HashMap<NexradStation,List<NexradProduct>> productMap = new HashMap<NexradStation,List<NexradProduct>>();
        for (int index = 0; index < 4; index++ ) {
            NexradStation station = stations.get(index);
            List<NexradProduct> products = nexradDataManager.getNexradProducts("p38cr", station, 30);
            productMap.put(station,products);
        }
        intent.setAction("com.nexradnow.android.newproduct");
        intent.putExtra("com.nexradnow.android.productmap",productMap);
        intent.putExtra("com.nexradnow.android.status",STATUS_FINISHED);
        intent.putExtra("com.nexradnow.android.coords", coords);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }
}
