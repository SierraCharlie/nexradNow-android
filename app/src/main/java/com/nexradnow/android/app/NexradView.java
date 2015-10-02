package com.nexradnow.android.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;
import com.google.inject.Inject;
import com.nexradnow.android.model.AppMessage;
import com.nexradnow.android.model.LatLongCoordinates;
import com.nexradnow.android.model.LocationChangeEvent;
import com.nexradnow.android.model.NexradProduct;
import com.nexradnow.android.model.NexradStation;
import com.nexradnow.android.model.NexradUpdate;
import com.nexradnow.android.services.DataRefreshIntent;
import com.nexradnow.android.services.EventBusProvider;
import com.nexradnow.android.services.LocationFinder;
import com.nexradnow.android.services.NexradDataManager;
import roboguice.RoboGuice;
import roboguice.activity.RoboActionBarActivity;

import java.util.List;
import java.util.Map;


public class NexradView extends RoboActionBarActivity {

    @Inject
    protected LocationFinder locationFinder;

    @Inject
    protected EventBusProvider eventBusProvider;

    @Inject
    protected NexradDataManager nexradDataManager;

    /**
     * Last valid location used by the app
     */
    protected LatLongCoordinates lastKnownLocation;
    // TODO: save/restore this last known location via long-term app storage

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        RoboGuice.injectMembers(this, this);
        setContentView(R.layout.activity_nexrad_view);
        eventBusProvider.getEventBus().register(this);
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                int status = intent.getIntExtra("com.nexradnow.android.status",-1);
                if (status==DataRefreshIntent.STATUS_FINISHED) {
                    Map<NexradStation,List<NexradProduct>> products =
                            (Map<NexradStation,List<NexradProduct>>)intent.getSerializableExtra("com.nexradnow.android.productmap");
                    LatLongCoordinates centerPoint = (LatLongCoordinates)intent.getSerializableExtra("com.nexradnow.android.coords");
                    NexradUpdate updateEvent = new NexradUpdate(products, centerPoint);
                    eventBusProvider.getEventBus().post(updateEvent);
                } else if (status == DataRefreshIntent.STATUS_ERROR) {
                    postMessage(intent.getStringExtra("com.nexradnow.android.errmsg"), AppMessage.Type.ERROR);
                } else if (status == DataRefreshIntent.STATUS_RUNNING) {
                    String msgText = intent.getStringExtra("com.nexradnow.android.statusmsg");
                    if (msgText != null) {
                        postMessage(msgText, AppMessage.Type.PROGRESS);
                    }
                }
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction("com.nexradnow.android.newproduct");
        LocalBroadcastManager.getInstance(this).registerReceiver(receiver, filter);
        LatLongCoordinates coords = locationFinder.getCurrentCoords();
        if (coords != null) {
            eventBusProvider.getEventBus().post(new LocationChangeEvent(coords));
        }

    }


    protected Toast prevToast;
    protected AppMessage.Type prevToastType;

    public void onEvent(AppMessage message) {
        // TODO: use different styles for errors vs. other types of messages
        int duration = Toast.LENGTH_LONG;
        if (message.getType() == AppMessage.Type.ERROR) {
            duration = Toast.LENGTH_LONG;
        } else if (message.getType() == AppMessage.Type.INFO) {
            duration = Toast.LENGTH_SHORT;
        } else if (message.getType() == AppMessage.Type.PROGRESS) {
            if (prevToast != null) {
                if (prevToastType == AppMessage.Type.PROGRESS) {
                    prevToast.cancel();
                }
            }
        }

        Toast msgToast = Toast.makeText(this, message.getMessage(), duration);
        msgToast.show();
        prevToast = msgToast;
        prevToastType = message.getType();
    }

    public void onEvent(LocationChangeEvent locationChangeEvent) {
        // Start the initial refresh
        postMessage (R.string.msg_refreshing_wx, AppMessage.Type.INFO);
        lastKnownLocation = locationChangeEvent.getCoordinates();
        requestWxForLocation(locationChangeEvent.getCoordinates());
    }

    public void requestWxForLocation (LatLongCoordinates coords) {
        Intent intent = new Intent(Intent.ACTION_SYNC, null, this, DataRefreshIntent.class);
        intent.putExtra("com.nexradnow.android.coords",coords);
        startService(intent);
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
            return true;
        }

        if (id == R.id.action_wxrefresh) {
            refreshWeather();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void refreshWeather() {
        if (lastKnownLocation != null) {
            postMessage (R.string.msg_refreshing_wx, AppMessage.Type.INFO);
            requestWxForLocation(lastKnownLocation);
        } else {
            postMessage (R.string.err_no_location, AppMessage.Type.ERROR);
        }

    }

    private void postMessage(int msgId, AppMessage.Type msgType) {
        String msgText = getResources().getString(msgId);
        postMessage(msgText,msgType);
    }

    private void postMessage(String msgText, AppMessage.Type msgType) {
        eventBusProvider.getEventBus().post(new AppMessage(msgText, msgType));
    }
}
