package com.nexradnow.android.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import com.google.inject.Inject;
import com.nexradnow.android.model.LatLongCoordinates;
import com.nexradnow.android.model.LocationChangeEvent;
import com.nexradnow.android.model.NexradProduct;
import com.nexradnow.android.model.NexradStation;
import com.nexradnow.android.model.NexradUpdate;
import com.nexradnow.android.services.DataRefreshIntent;
import com.nexradnow.android.services.EventBusProvider;
import com.nexradnow.android.services.LocationFinder;
import com.nexradnow.android.services.NexradDataManager;
import com.nexradnow.android.views.RadarView;
import de.greenrobot.event.EventBus;
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

    public void onEvent(LocationChangeEvent locationChangeEvent) {
        // Start the initial refresh
        Intent intent = new Intent(Intent.ACTION_SYNC, null, this, DataRefreshIntent.class);
        intent.putExtra("com.nexradnow.android.coords",locationChangeEvent.getCoordinates());
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

        return super.onOptionsItemSelected(item);
    }
}
