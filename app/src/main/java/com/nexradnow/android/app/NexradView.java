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

import java.io.Serializable;
import java.util.List;
import java.util.Map;


public class NexradView extends RoboActionBarActivity {

    @Inject
    protected LocationFinder locationFinder;

    @Inject
    protected EventBusProvider eventBusProvider;

    protected Toast prevToast;
    protected AppMessage.Type prevToastType;

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
    }
    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        RoboGuice.injectMembers(this, this);
        setContentView(R.layout.activity_nexrad_view);
        eventBusProvider.getEventBus().register(this);
        LatLongCoordinates coords = locationFinder.getCurrentCoords();
        if (coords != null) {
            eventBusProvider.getEventBus().post(new LocationChangeEvent(coords));
        }

    }


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
            ((NexradApp)getApplication()).refreshWeather();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
