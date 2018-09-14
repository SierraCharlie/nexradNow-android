package com.nexradnow.android.app;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import com.nexradnow.android.model.AppMessage;
import com.nexradnow.android.model.BitmapEvent;
import com.nexradnow.android.model.LatLongCoordinates;
import com.nexradnow.android.model.LatLongRect;
import com.nexradnow.android.model.LocationChangeEvent;
import com.nexradnow.android.model.LocationSelectionEvent;
import com.nexradnow.android.model.NexradProduct;
import com.nexradnow.android.model.NexradStation;
import com.nexradnow.android.model.NexradUpdate;
import com.nexradnow.android.modules.DIModule;
import com.nexradnow.android.services.BitmapRenderingIntent;
import com.nexradnow.android.services.DataRefreshIntent;
import com.nexradnow.android.services.LocationInfoIntent;
import com.nexradnow.android.util.NexradNowFileUtils;
import de.greenrobot.event.EventBus;
import toothpick.Scope;
import toothpick.Toothpick;
import toothpick.config.Module;

import javax.inject.Inject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;

/**
 * Application class that contains the intent launcher for the data refresh.
 *
 * Created by hobsonm on 10/2/15.
 */
public class NexradApp  extends Application {

    protected static final String TAG="NexradApp";

    protected static final String CACHELOCATIONFILE="LocationData";

    public static final String APPSCOPE="NexradApp";
    @Inject
    protected EventBus eventBus;

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
        Module diModule = new DIModule();
        ((DIModule) diModule).addCtx(this);
        Scope appScope = Toothpick.openScope(APPSCOPE);
        appScope.installModules(diModule);
        Toothpick.inject(this, appScope);
        super.onCreate();
        eventBus.register(this);
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
                        eventBus.post(nexradUpdate);
                    } else if (status == DataRefreshIntent.STATUS_ERROR) {
                        postMessage(intent.getStringExtra("com.nexradnow.android.errmsg"), AppMessage.Type.ERROR);
                    } else if (status == DataRefreshIntent.STATUS_RUNNING) {
                        String msgText = intent.getStringExtra("com.nexradnow.android.statusmsg");
                        if (msgText != null) {
                            postMessage(msgText, AppMessage.Type.PROGRESS);
                        }
                    }
                } else  if (intent.getAction().equals(LocationInfoIntent.GEOCODELOCATIONACTION)){
                    // TODO: handle location events
                    if (status == LocationInfoIntent.STATUS_FINISHED) {
                        LatLongCoordinates coords = (LatLongCoordinates)intent.getSerializableExtra("com.nexradnow.android.coords");
                        eventBus.post(new LocationChangeEvent(coords));
                    } else if (status == LocationInfoIntent.STATUS_ERROR) {
                        postMessage(intent.getStringExtra("com.nexradnow.android.errmsg"), AppMessage.Type.ERROR);
                    } else if (status == LocationInfoIntent.STATUS_RUNNING) {
                        String msgText = intent.getStringExtra("com.nexradnow.android.statusmsg");
                        if (msgText != null) {
                            postMessage(msgText, AppMessage.Type.PROGRESS);
                        }
                    }
                } else if (intent.getAction().equals(BitmapRenderingIntent.RENDERACTION)) {
                    if (status == BitmapRenderingIntent.STATUS_FINISHED) {
                        File bitmapFile = (File)intent.getSerializableExtra("com.nexradnow.android.bitmap");
                        Bitmap result = null;
                        try {
                            result = NexradNowFileUtils.readBitmapFromFile(bitmapFile);
                        } catch (IOException ioex) {
                            postMessage("Error receiving bitmap: "+ioex.toString(), AppMessage.Type.ERROR);
                        }
                        bitmapFile.delete();
                        NexradNowFileUtils.clearCacheFiles(NexradApp.this,"bitmap","tmp");
                        if (result != null) {
                            LatLongRect resultRect = (LatLongRect) intent.getSerializableExtra("com.nexradnow.android.latLongRect");
                            BitmapEvent event = new BitmapEvent(result, resultRect);
                            eventBus.post(event);
                        }
                    } else if (status == BitmapRenderingIntent.STATUS_ERROR) {
                        postMessage(intent.getStringExtra("com.nexradnow.android.errmsg"), AppMessage.Type.ERROR);
                    } else if (status == BitmapRenderingIntent.STATUS_RUNNING) {
                        String msgText = intent.getStringExtra("com.nexradnow.android.statusmsg");
                        if (msgText != null) {
                            postMessage(msgText, AppMessage.Type.PROGRESS);
                        }
                    }

                }
            }
        };
        NexradNowFileUtils.clearCacheDir(this);
        IntentFilter filter = new IntentFilter();
        filter.addAction(LocationInfoIntent.GEOCODELOCATIONACTION);
        filter.addAction(DataRefreshIntent.GETWXACTION);
        filter.addAction(BitmapRenderingIntent.RENDERACTION);
        LocalBroadcastManager.getInstance(this).registerReceiver(receiver, filter);
        lastKnownLocation = getCachedLocation();
        if (lastKnownLocation == null) {
            // come up with a fixed default location!
            lastKnownLocation = new LatLongCoordinates(32.77,-96.79);
        }
    }

    /**
     * Stash the specified coordinates in the cache so they can be restored on app startup
     * @param coords
     */
    protected void cacheLocation(LatLongCoordinates coords) {
        File cachedLocation = new File(this.getCacheDir(),CACHELOCATIONFILE);
        try {
            OutputStream fos = new FileOutputStream(cachedLocation);
            ObjectOutputStream oos = new ObjectOutputStream(fos);
            oos.writeObject(coords);
            oos.close();
            fos.close();
        } catch (Exception ex) {
            Log.e(TAG, "error writing current location to cache", ex);
        }
    }

    /**
     * Retrieve the current location from the cache file, if it exists and we can read it.
     * @return cached location
     */
    protected LatLongCoordinates getCachedLocation() {
        LatLongCoordinates coords = null;
        File cachedLocation = new File(this.getCacheDir(),CACHELOCATIONFILE);
        if (cachedLocation.exists()&&cachedLocation.canRead()) {
            try {
                InputStream fis = new FileInputStream(cachedLocation);
                ObjectInputStream ois = new ObjectInputStream(fis);
                coords = (LatLongCoordinates)ois.readObject();
                ois.close();
                fis.close();
            } catch (Exception ex) {
                Log.e(TAG, "error reading current location from cache", ex);
            }
        }
        return coords;
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
        eventBus.post(new AppMessage(msgText, msgType));
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
            eventBus.post(new LocationChangeEvent(lastGpsLocation));
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
        cacheLocation(locationChangeEvent.getCoordinates());
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
                eventBus.post(locationChangeEvent);
                break;
            case CITYSTATE:
                locationMode = LocationMode.GEOCODE;
                requestCityStateLocation(locationSelection.getCityState());
                break;
        }
    }

    /**
     * Request generation of a product bitmap via an async activity.
     *
     * @param latLongRect lat/long of the bitmap borders
     * @param bitmapSize physical size of the bitmap
     * @param update product(s) to be drawn on the bitmap.
     * @param displayDensity display density value
     */
    public void startRendering(LatLongRect latLongRect, Rect bitmapSize, NexradUpdate update, float displayDensity) {
        Intent intent = new Intent(BitmapRenderingIntent.RENDERACTION, null, this, BitmapRenderingIntent.class);
        intent.putExtra("com.nexradnow.android.latLongRect",latLongRect);
        intent.putExtra("com.nexradnow.android.bitmapRect", bitmapSize);
        intent.putExtra("com.nexradnow.android.displayDensity", displayDensity);
        // pass the "update" via a cache file and delete when received
        // ref: http://stackoverflow.com/questions/3425906/creating-temporary-files-in-android
        // ref: http://stackoverflow.com/questions/36909374/crash-on-passing-large-sets-of-data-through-intents-is-there-a-size-limit-on-t
        File updateFile = null;
        try {
            updateFile = NexradNowFileUtils.writeObjectToCacheFile(this, "wxdat", "tmp", update);
        } catch (IOException ioex) {
            postMessage(ioex.toString(), AppMessage.Type.ERROR);
        }
        intent.putExtra("com.nexradnow.android.nexradUpdate", updateFile);
        startService(intent);
    }

    public LocationMode getLocationMode() {
        return locationMode;
    }

    public void setLocationMode(LocationMode locationMode) {
        this.locationMode = locationMode;
    }

    public LatLongCoordinates getLastKnownLocation() {
        return lastKnownLocation;
    }
}
