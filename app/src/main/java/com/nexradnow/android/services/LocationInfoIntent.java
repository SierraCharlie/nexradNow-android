package com.nexradnow.android.services;

import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationServices;
import com.google.inject.Inject;
import com.nexradnow.android.model.LatLongCoordinates;
import roboguice.service.RoboIntentService;

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

    public static final String ACTION = "com.nexradnow.android.newlocation";

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
        intent.setAction(ACTION);
        // TODO: inspect intent content for the specific operation to perform
        CurrentLocationFinder finder = new CurrentLocationFinder();
        finder.srcIntent = intent;
        finder.run();
    }

}
