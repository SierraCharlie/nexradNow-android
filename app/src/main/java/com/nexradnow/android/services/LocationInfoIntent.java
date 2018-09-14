package com.nexradnow.android.services;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.location.Address;
import android.location.Geocoder;
import android.support.v4.content.LocalBroadcastManager;
import com.nexradnow.android.app.NexradApp;
import com.nexradnow.android.exception.NexradNowException;
import com.nexradnow.android.model.LatLongCoordinates;
import toothpick.Toothpick;

import javax.inject.Inject;
import java.util.List;
import java.util.Locale;

/**
 * This intent handles long-running interactions with the location services. These interactions include
 * finding the device's current location, and geocoding a location.
 *
 * Created by hobsonm on 10/8/15.
 */
public class LocationInfoIntent extends IntentService {

    public static final int STATUS_RUNNING = 0;
    public static final int STATUS_FINISHED = 1;
    public static final int STATUS_ERROR = 2;

    private static final String TAG = "DownloadService";

    public static final String GPSLOCATIONACTION = "com.nexradnow.android.newlocation";
    public static final String GEOCODELOCATIONACTION = "com.nexradnow.android.geocodelocation";

    @Inject
    protected Context ctx;


    public LocationInfoIntent() {
        super(LocationInfoIntent.class.getName());
        Toothpick.inject(this,Toothpick.openScope(NexradApp.APPSCOPE));
    }


    @Override
    protected void onHandleIntent(Intent intent) {
        if (GEOCODELOCATIONACTION.equals(intent.getAction())) {
            Geocoder coder = new Geocoder(ctx, Locale.US);
            LatLongCoordinates results = null;
            try {
                List<Address> addressList =
                        coder.getFromLocationName(intent.getStringExtra("com.nexradnow.android.citystate"), 1);
                if ((addressList==null)||(addressList.isEmpty())) {
                    throw new NexradNowException("no results returned from geocoder");
                }
                results = new LatLongCoordinates(addressList.get(0).getLatitude(),addressList.get(0).getLongitude());
            } catch (Exception ex) {
                intent.putExtra("com.nexradnow.android.errmsg", "geocoding failed: "+ex.toString());
                intent.putExtra("com.nexradnow.android.status", STATUS_ERROR);
                LocalBroadcastManager.getInstance(ctx).sendBroadcast(intent);
            }
            intent.putExtra("com.nexradnow.android.status", STATUS_FINISHED);
            intent.putExtra("com.nexradnow.android.coords", results);
            LocalBroadcastManager.getInstance(ctx).sendBroadcast(intent);
        }
    }

}
