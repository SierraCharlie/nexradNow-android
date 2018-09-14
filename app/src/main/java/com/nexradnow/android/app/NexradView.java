package com.nexradnow.android.app;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.nexradnow.android.model.*;
import com.nexradnow.android.views.RadarBitmapView;
import de.greenrobot.event.EventBus;
import toothpick.Toothpick;

import javax.inject.Inject;

public class NexradView extends AppCompatActivity implements
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        LocationListener {

    private static final int REQUEST_RESOLVE_ERROR = 1001;
    private final static int CONNECTION_FAILURE_RESOLUTION_REQUEST = 9000;
    private static final String TAG="NexradView";

    private static final String[] INITIAL_PERMS={
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.INTERNET
    };
    private static final int INITIAL_REQUEST=37;

    @Inject
    protected EventBus eventBus;

    protected Toast prevToast;
    protected AppMessage.Type prevToastType;
    protected boolean mResolvingError = false;


    protected GoogleApiClient mGoogleApiClient;
    private LocationRequest mLocationRequest;

    protected BroadcastReceiver receiver;

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        //RadarBitmapView radarView = (RadarBitmapView)findViewById(R.id.radarView);
        //outState.putBundle("com.nexradnow.android.radarViewState", radarView.writeBundle());
    }
    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        //Bundle viewBundle = savedInstanceState.getBundle("com.nexradnow.android.radarViewState");
        //if (viewBundle != null) {
        //    RadarBitmapView radarView = (RadarBitmapView)findViewById(R.id.radarView);
        //    radarView.readBundle(viewBundle);
        //}
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Toothpick.inject(this,Toothpick.openScope(NexradApp.APPSCOPE));
        Log.d(TAG,"onCreate() + "+this.hashCode());
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_nexrad_view);
        eventBus.register(this);
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
        // Create the LocationRequest object
        mLocationRequest = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_LOW_POWER)
                .setInterval(10 * 60 * 1000)        // 10 seconds, in milliseconds
                .setFastestInterval(1 * 60 * 1000); // 1 second, in milliseconds
        if (PackageManager.PERMISSION_GRANTED != ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
            ActivityCompat.requestPermissions(this, INITIAL_PERMS, INITIAL_REQUEST);
        }
        if (PreferenceManager.getDefaultSharedPreferences(this).getString(SettingsActivity.KEY_PREF_NEXRAD_EMAILADDRESS, null) == null) {
            startSettings(true);
        } else {
            if (((NexradApp)getApplication()).getLastKnownLocation() != null) {
                RadarBitmapView radarView = (RadarBitmapView)findViewById(R.id.radarView);
                radarView.onEvent(new LocationChangeEvent(((NexradApp)getApplication()).getLastKnownLocation()));
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (mGoogleApiClient.isConnected()&&PackageManager.PERMISSION_GRANTED==
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
            onConnected(null);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mGoogleApiClient.isConnected()) {
            mGoogleApiClient.disconnect();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    public void onResume() {
        super.onResume();
        mGoogleApiClient.connect();
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG,"onDestroy() + "+this.hashCode());
        super.onDestroy();
        eventBus.unregister(this);
        RadarBitmapView radarView = (RadarBitmapView)findViewById(R.id.radarView);
        if (radarView != null) {
            radarView.releaseBitmap();
        }
    }

    @Override
    public void onConnected(Bundle bundle) {
        // Register for location updates
        Location location = null;
        try {
            location = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
        } catch (SecurityException ex) {
            //TODO: Log failure to get location
        }
        if (location == null) {
            if (PackageManager.PERMISSION_GRANTED==ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)) {
                // enable location updates
                LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
            }
        }
        else {
            onLocationChanged(location);
        }

    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        if (connectionResult.hasResolution()) {
            try {
                // Start an Activity that tries to resolve the error
                connectionResult.startResolutionForResult(this, CONNECTION_FAILURE_RESOLUTION_REQUEST);
            } catch (IntentSender.SendIntentException e) {
                Log.e(TAG,"Resolution of connection error failed",e);
                eventBus.post(new AppMessage("error resolving connection failure", AppMessage.Type.ERROR));
            }
        } else {
            Log.i(TAG, "Location services connection failed with code " + connectionResult.getErrorCode());
            eventBus.post(new AppMessage("error connecting to GooglePlay services: "
                    +connectionResult.getErrorCode(), AppMessage.Type.ERROR));
        }

    }

    @Override
    public void onLocationChanged(Location location) {
        LatLongCoordinates locationLatLong = new LatLongCoordinates(location.getLatitude(), location.getLongitude());
        if (((NexradApp)getApplication()).getLocationMode()== NexradApp.LocationMode.GPS) {
            LocationChangeEvent event = new LocationChangeEvent(locationLatLong);
            eventBus.post(event);
        }
    }

    public void onEvent(BitmapEvent bitmapEvent) {
        RadarBitmapView radarView = (RadarBitmapView)findViewById(R.id.radarView);
        radarView.onEvent(bitmapEvent);
    }

    public void onEvent(LocationChangeEvent location) {
        RadarBitmapView radarView = (RadarBitmapView)findViewById(R.id.radarView);
        radarView.onEvent(location);
    }

    public void onEvent(AppMessage message) {
        // TODO: use different styles for errors vs. other types of messages
        int duration = Toast.LENGTH_LONG;
        if (message.getType() == AppMessage.Type.ERROR) {
            duration = Toast.LENGTH_LONG;
            Toast msgToast = Toast.makeText(this, message.getMessage(), duration);
            msgToast.show();
        } else {
            // Forward to the view for display
            RadarBitmapView radarView = (RadarBitmapView)findViewById(R.id.radarView);
            radarView.onEvent(message);
        }
    }

    public void onEvent(NexradUpdate update) {
        Log.d(TAG,"onEvent(NexradUpdate "+update.hashCode()+") + "+this.hashCode());
        RadarBitmapView radarView = (RadarBitmapView)findViewById(R.id.radarView);
        radarView.onEvent(update);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_nexrad_view, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            startSettings(false);
            return true;
        }

        if (id == R.id.action_setlocation) {
            startSetLocation();
            return true;
        }

        if (id == R.id.action_wxrefresh) {
            ((NexradApp)getApplication()).refreshWeather();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void startSettings(boolean forceEmail) {
        Intent intent = new Intent(this, SettingsActivity.class);
        if (forceEmail) {
            intent.putExtra("com.nexradnow.android.forceEmailPreference", true);
        }
        startActivity(intent);
    }

    private void startSetLocation() {
        Intent intent = new Intent(this, LocationSelectionActivity.class);
        startActivity(intent);
    }
}
