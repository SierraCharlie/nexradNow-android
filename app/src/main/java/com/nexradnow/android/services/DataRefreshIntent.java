package com.nexradnow.android.services;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import com.nexradnow.android.app.NexradApp;
import com.nexradnow.android.app.R;
import com.nexradnow.android.app.SettingsActivity;
import com.nexradnow.android.exception.NexradNowException;
import com.nexradnow.android.model.LatLongCoordinates;
import com.nexradnow.android.model.NexradProduct;
import com.nexradnow.android.model.NexradStation;
import toothpick.Toothpick;

import javax.inject.Inject;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Created by hobsonm on 9/15/15.
 */

public class DataRefreshIntent extends IntentService {

    protected class DataRefreshIntentException extends Exception {
        public DataRefreshIntentException(String detailMessage) {
            super(detailMessage);
        }
    };

    protected class NexradResult {
        protected NexradStation station;
        protected List<NexradProduct> products;
    }

    public static final int STATUS_RUNNING = 0;
    public static final int STATUS_FINISHED = 1;
    public static final int STATUS_ERROR = 2;

    private static final String TAG = "DownloadService";

    public static final String GETWXACTION = "com.nexradnow.android.newproduct";
    public static final String GETSTATIONACTION = "com.nexradnow.android.stationlisting";

    @Inject
    protected NexradDataManager nexradDataManager;

    public DataRefreshIntent() {
        super(DataRefreshIntent.class.getName());
        Toothpick.inject(this,Toothpick.openScope(NexradApp.APPSCOPE));
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
            notifyException(this,intent, ex);
        }
    }

    protected void wxAction(final Intent intent) {
        intent.setAction(GETWXACTION);
        try {
            LatLongCoordinates coords = (LatLongCoordinates) intent.getSerializableExtra("com.nexradnow.android.coords");
            intent.putExtra("com.nexradnow.android.status", STATUS_RUNNING);
            intent.putExtra("com.nexradnow.android.statusmsg",getResources().getString(R.string.msg_getting_station_list));
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
            List<NexradStation> srcStations = nexradDataManager.getNexradStations();
            // TODO: handle null or empty list due to download/cache failure
            List<NexradStation> stations = nexradDataManager.sortClosest(srcStations, coords);
            SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
            int maxDistance = Integer.parseInt(sharedPref.getString(SettingsActivity.KEY_PREF_NEXRAD_STATIONDISTANCE,"160"));
            // Pick the four closest stations IF the distance to the nearest is less than 350 km.
            if (coords.distanceTo(stations.get(0).getCoords()) > maxDistance) {
                throw new DataRefreshIntentException("No Nexrad stations within "+maxDistance+" km");
            }
            intent.putExtra("com.nexradnow.android.statusmsg",
                    getResources().getString(R.string.msg_getting_station_data));
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
            HashMap<NexradStation, List<NexradProduct>> productMap = new HashMap<NexradStation, List<NexradProduct>>();
            Collection<Callable<NexradResult>> tasks = new ArrayList<Callable<NexradResult>>();
            for (final NexradStation station : stations) {
                if (coords.distanceTo(station.getCoords()) > maxDistance) {
                    continue;
                }
                Callable<NexradResult> retriever = new Callable<NexradResult>() {
                    NexradDataManager dataManager = nexradDataManager;
                    NexradStation stationCode = station;
                    String productCode = intent.getStringExtra("com.nexradnow.android.productcode");
                    int ageMaxMinutes = 30;
                    Intent parentIntent = intent;
                    Context ctx = DataRefreshIntent.this.getApplicationContext();

                    @Override
                    public NexradResult call() throws Exception {
                        NexradResult results = null;
                        try {
                            List<NexradProduct> radarData = nexradDataManager.getNexradProducts(productCode, stationCode,
                                    ageMaxMinutes);
                            results = new NexradResult();
                            results.products = radarData;
                            results.station = stationCode;
                            synchronized (parentIntent) {
                                parentIntent.putExtra("com.nexradnow.android.statusmsg",
                                        getResources().getString(R.string.msg_received_data_for_station) +
                                                " " + stationCode.getIdentifier());
                                LocalBroadcastManager.getInstance(ctx).sendBroadcast(parentIntent);
                            }
                        } catch (Exception ex) {
                            synchronized (parentIntent) {
                                notifyException(ctx, parentIntent, ex);
                            }
                        }
                        return results;
                    }
                };
                tasks.add(retriever);
            }
            ExecutorService executor = new ThreadPoolExecutor(4, 8, 10, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(tasks.size()));
            try {
                List<Future<NexradResult>> results = executor.invokeAll(tasks);
                executor.shutdown();
                for (Future<NexradResult> eachResult : results) {
                    productMap.put(eachResult.get().station,eachResult.get().products);
                }
            } catch (Exception ex) {
                notifyException(this,intent,ex);
            }
            intent.putExtra("com.nexradnow.android.productmap", productMap);
            intent.putExtra("com.nexradnow.android.status", STATUS_FINISHED);
            intent.putExtra("com.nexradnow.android.coords", coords);
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
        } catch (Exception ex) {
            notifyException(this, intent, ex);
        }
    }

    protected static void notifyException(Context ctx, Intent intent, Exception ex) {
        if (ex instanceof DataRefreshIntentException) {
            intent.putExtra("com.nexradnow.android.errmsg", ex.getMessage());
        } else if (ex instanceof NexradNowException ){
            intent.putExtra("com.nexradnow.android.errmsg", ex.getMessage());
        } else {
            intent.putExtra("com.nexradnow.android.errmsg", ex.toString());
        }
        intent.putExtra("com.nexradnow.android.status", STATUS_ERROR);
        LocalBroadcastManager.getInstance(ctx).sendBroadcast(intent);
    }
}
