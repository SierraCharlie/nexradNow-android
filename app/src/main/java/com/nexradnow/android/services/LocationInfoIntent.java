package com.nexradnow.android.services;

import android.content.Context;
import android.content.Intent;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationServices;
import com.google.inject.Inject;
import com.nexradnow.android.exception.NexradNowException;
import com.nexradnow.android.model.LatLongCoordinates;
import roboguice.service.RoboIntentService;

import java.util.List;
import java.util.Locale;

/**
 * This intent handles long-running interactions with the location services. These interactions include
 * finding the device's current location, and geocoding a location.
 *
 * Created by hobsonm on 10/8/15.
 */
public class LocationInfoIntent extends RoboIntentService {

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
    }

    protected class CurrentLocationFinder implements Runnable, GoogleApiClient.ConnectionCallbacks,
            GoogleApiClient.OnConnectionFailedListener {
        Intent srcIntent;
        int status = STATUS_ERROR;
        LatLongCoordinates location;

        protected GoogleApiClient apiClient;
        @Override
        public void run() {
            apiClient = new GoogleApiClient.Builder(ctx)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(LocationServices.API)
                    .build();
            apiClient.connect();
        }

        @Override
        public void onConnected(Bundle bundle) {
            Location lastLocation = LocationServices.FusedLocationApi.getLastLocation(
                    apiClient);
            if (lastLocation != null) {
                location = new LatLongCoordinates(lastLocation.getLatitude(), lastLocation.getLongitude());
                status = STATUS_FINISHED;
                srcIntent.putExtra("com.nexradnow.android.status", STATUS_FINISHED);
                srcIntent.putExtra("com.nexradnow.android.coords", location);
            } else {
                srcIntent.putExtra("com.nexradnow.android.errmsg", "no current location could be found");
                srcIntent.putExtra("com.nexradnow.android.status", STATUS_ERROR);
            }
            LocalBroadcastManager.getInstance(ctx).sendBroadcast(srcIntent);
        }

        @Override
        public void onConnectionSuspended(int i) {
            location = null;
            status = STATUS_ERROR;
            srcIntent.putExtra("com.nexradnow.android.errmsg", "connection suspended");
            srcIntent.putExtra("com.nexradnow.android.status", STATUS_ERROR);
            LocalBroadcastManager.getInstance(ctx).sendBroadcast(srcIntent);
       }

        @Override
        public void onConnectionFailed(ConnectionResult connectionResult) {
            location = null;
            status = STATUS_ERROR;
            srcIntent.putExtra("com.nexradnow.android.errmsg", "connection failed");
            srcIntent.putExtra("com.nexradnow.android.status", STATUS_ERROR);
            LocalBroadcastManager.getInstance(ctx).sendBroadcast(srcIntent);
        }
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (GPSLOCATIONACTION.equals(intent.getAction())) {
            CurrentLocationFinder finder = new CurrentLocationFinder();
            finder.srcIntent = intent;
            finder.run();
        } else if (GEOCODELOCATIONACTION.equals(intent.getAction())) {
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
