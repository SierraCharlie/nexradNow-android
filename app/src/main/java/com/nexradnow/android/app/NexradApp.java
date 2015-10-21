package com.nexradnow.android.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.preference.PreferenceManager;
import android.support.multidex.MultiDexApplication;
import android.support.v4.content.LocalBroadcastManager;
import com.google.inject.Inject;
import com.nexradnow.android.model.AppMessage;
import com.nexradnow.android.model.LatLongCoordinates;
import com.nexradnow.android.model.LocationChangeEvent;
import com.nexradnow.android.model.LocationSelectionEvent;
import com.nexradnow.android.model.NexradProduct;
import com.nexradnow.android.model.NexradStation;
import com.nexradnow.android.model.NexradUpdate;
import com.nexradnow.android.services.DataRefreshIntent;
import com.nexradnow.android.services.EventBusProvider;
import com.nexradnow.android.services.LocationInfoIntent;
import roboguice.RoboGuice;

import java.util.List;
import java.util.Map;

/**
 * Application class that contains the intent launcher for the data refresh.
 *
 * Created by hobsonm on 10/2/15.
 */
public class NexradApp  extends MultiDexApplication {

    protected static final String TAG="NexradApp";

    @Inject
    protected EventBusProvider eventBusProvider;

    /**
     * Last valid location used by the app
     */
    protected LatLongCoordinates lastKnownLocation;
    protected LatLongCoordinates lastGpsLocation;
    // TODO: save/restore this last known location via long-term app storage

    public enum LocationMode {GPS, NEXRAD, GEOCODE};
    protected LocationMode locationMode = LocationMode.GPS;

    @Override
    public void onCreate() {
        super.onCreate();
        RoboGuice.getInjector(getApplicationContext()).injectMembers(this);
        eventBusProvider.getEventBus().register(this);
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                int status = intent.getIntExtra("com.nexradnow.android.status",-1);
                if (intent.getAction().equals(DataRefreshIntent.GETWXACTION)) {
                    if (status == DataRefreshIntent.STATUS_FINISHED) {
                        Map<NexradStation, List<NexradProduct>> products =
                                (Map<NexradStation, List<NexradProduct>>) intent.getSerializableExtra("com.nexradnow.android.productmap");
                        LatLongCoordinates centerPoint = (LatLongCoordinates) intent.getSerializableExtra("com.nexradnow.android.coords");
                        NexradUpdate nexradUpdate = new NexradUpdate(products, centerPoint);
                        eventBusProvider.getEventBus().post(nexradUpdate);
                    }
                } else if (status == DataRefreshIntent.STATUS_ERROR) {
                    postMessage(intent.getStringExtra("com.nexradnow.android.errmsg"), AppMessage.Type.ERROR);
                } else if (status == DataRefreshIntent.STATUS_RUNNING) {
                    String msgText = intent.getStringExtra("com.nexradnow.android.statusmsg");
                    if (msgText != null) {
                        postMessage(msgText, AppMessage.Type.PROGRESS);
                    }
                } else  if (intent.getAction().equals(LocationInfoIntent.GEOCODELOCATIONACTION)){
                    // TODO: handle location events
                    if (status == LocationInfoIntent.STATUS_FINISHED) {
                        LatLongCoordinates coords = (LatLongCoordinates)intent.getSerializableExtra("com.nexradnow.android.coords");
                        eventBusProvider.getEventBus().post(new LocationChangeEvent(coords));
                    } else if (status == LocationInfoIntent.STATUS_ERROR) {
                        postMessage(intent.getStringExtra("com.nexradnow.android.errmsg"), AppMessage.Type.ERROR);
                    } else if (status == LocationInfoIntent.STATUS_RUNNING) {
                        String msgText = intent.getStringExtra("com.nexradnow.android.statusmsg");
                        if (msgText != null) {
                            postMessage(msgText, AppMessage.Type.PROGRESS);
                        }
                    }
                }
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(LocationInfoIntent.GEOCODELOCATIONACTION);
        filter.addAction(DataRefreshIntent.GETWXACTION);
        LocalBroadcastManager.getInstance(this).registerReceiver(receiver, filter);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    private void postMessage(int msgId, AppMessage.Type msgType) {
        String msgText = getResources().getString(msgId);
        postMessage(msgText,msgType);
    }

    private void postMessage(String msgText, AppMessage.Type msgType) {
        eventBusProvider.getEventBus().post(new AppMessage(msgText, msgType));
    }

    public void refreshWeather() {
        if (lastKnownLocation != null) {
            postMessage (R.string.msg_refreshing_wx, AppMessage.Type.INFO);
            requestWxForLocation(lastKnownLocation);
        } else {
            postMessage (R.string.err_no_location, AppMessage.Type.ERROR);
        }

    }

    public void requestWxForLocation (LatLongCoordinates coords) {
        Intent intent = new Intent(DataRefreshIntent.GETWXACTION, null, this, DataRefreshIntent.class);
        intent.putExtra("com.nexradnow.android.coords",coords);
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        String productCode = sharedPref.getString(SettingsActivity.KEY_PREF_NEXRAD_PRODUCT,"p37cr");
        intent.putExtra("com.nexradnow.android.productcode",productCode);
        startService(intent);
    }

    public void requestCurrentLocation() {
        if (lastGpsLocation != null) {
            eventBusProvider.getEventBus().post(new LocationChangeEvent(lastGpsLocation));
        }
    }

    public void requestCityStateLocation(String cityState) {
        Intent intent = new Intent(this, LocationInfoIntent.class);
        intent.setAction(LocationInfoIntent.GEOCODELOCATIONACTION);
        intent.putExtra("com.nexradnow.android.citystate", cityState);
        startService(intent);
    }

    public void onEvent(LocationChangeEvent locationChangeEvent) {
        // Start the initial refresh
        lastKnownLocation = locationChangeEvent.getCoordinates();
        if (locationMode==LocationMode.GPS) {
            if (lastGpsLocation != null) {
                if (lastGpsLocation.distanceTo(lastKnownLocation) < 1.0) {
                    return;
                }
            }
            lastGpsLocation = locationChangeEvent.getCoordinates();
        }
        requestWxForLocation(locationChangeEvent.getCoordinates());
    }

    public void onEvent(LocationSelectionEvent locationSelection) {
        switch (locationSelection.getType()) {
            case GPS:
                locationMode = LocationMode.GPS;
                requestCurrentLocation();
                break;
            case NEXRADSTATION:
                locationMode = LocationMode.NEXRAD;
                LocationChangeEvent locationChangeEvent = new LocationChangeEvent(locationSelection.getStation().getCoords());
                eventBusProvider.getEventBus().post(locationChangeEvent);
                break;
            case CITYSTATE:
                locationMode = LocationMode.GEOCODE;
                requestCityStateLocation(locationSelection.getCityState());
                break;
        }
    }

    public LocationMode getLocationMode() {
        return locationMode;
    }

    public void setLocationMode(LocationMode locationMode) {
        this.locationMode = locationMode;
    }
}
