package com.nexradnow.android.services;

import android.content.Context;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationServices;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.nexradnow.android.model.AppMessage;
import com.nexradnow.android.model.LatLongCoordinates;
import com.nexradnow.android.model.LocationChangeEvent;

/**
 * Created by hobsonm on 9/14/15.
 *
 * Handle the interface with the device's location services to get various types
 * of locations
 */
@Singleton
public class LocationFinder implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener
{

    protected static final String TAG = "LocationFinder";

    protected Context ctx;
    protected GoogleApiClient apiClient;

    @Inject
    protected EventBusProvider eventBusProvider;

    @Inject
    public LocationFinder(Context ctx) {
        this.ctx = ctx;
        apiClient = new GoogleApiClient.Builder(ctx)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
        apiClient.connect();
    }

    public LatLongCoordinates getCurrentCoords() {
        return null;
    }

    @Override
    public void onConnected(Bundle bundle) {
        Location lastLocation = LocationServices.FusedLocationApi.getLastLocation(
                apiClient);
        if (lastLocation != null) {
            LatLongCoordinates coords = new LatLongCoordinates(lastLocation.getLatitude(),lastLocation.getLongitude());
            //coords = new LatLongCoordinates(37.76,-99.96);
            eventBusProvider.getEventBus().post(new LocationChangeEvent(coords));
        } else {
            // TODO: wrap code block below in a helper object for reuse
            String errText = "no location returned from location services";
            Log.e(TAG,errText);
            AppMessage message = new AppMessage(errText, AppMessage.Type.ERROR);
            eventBusProvider.getEventBus().post(message);
        }
    }

    @Override
    public void onConnectionSuspended(int i) {
        String errText = "location services connection suspended ("+Integer.toString(i)+")";
        Log.e(TAG,errText);
        AppMessage message = new AppMessage(errText, AppMessage.Type.ERROR);
        eventBusProvider.getEventBus().post(message);
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        String errText = "location services connection failed: "+connectionResult.toString();
        Log.e(TAG, "location services connection failed: "+connectionResult.toString());
        AppMessage message = new AppMessage(errText, AppMessage.Type.ERROR);
        eventBusProvider.getEventBus().post(message);
        // TODO: fall back to default coordinates ?
    }
}
