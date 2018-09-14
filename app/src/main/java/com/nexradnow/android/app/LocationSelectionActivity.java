package com.nexradnow.android.app;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.Spinner;
import android.widget.TextView;
import com.nexradnow.android.model.LocationSelectionEvent;
import com.nexradnow.android.model.NexradStation;
import com.nexradnow.android.services.DataRefreshIntent;
import de.greenrobot.event.EventBus;
import toothpick.Toothpick;

import javax.inject.Inject;
import java.io.Serializable;
import java.util.List;

/**
 * Created by hobsonm on 10/14/15.
 */
public class LocationSelectionActivity extends Activity {

    public static final String TAG = "LocationSelActivity";

    @Inject
    protected EventBus eventBus;

    protected List<NexradStation> stationList = null;

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable("stationList",(Serializable)stationList);
    }
    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        stationList = (List)savedInstanceState.getSerializable("stationList");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Toothpick.inject(this,Toothpick.openScope(NexradApp.APPSCOPE));
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_choose_location);
        // eventBusProvider.getEventBus().register(this);

        Button setLocationButton = (Button)this.findViewById(R.id.setLocationButton);
        setLocationButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                LocationSelectionActivity.this.setLocationClicked();
            }
        });
        View.OnClickListener radioClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ((RadioButton)v).setChecked(true);
                switch (v.getId()) {
                    case R.id.radioCityStateLocation:
                        setRadioChecked(false, R.id.radioGPSLocation, R.id.radioNexradStationLocation);
                        setItemEnabled(false, R.id.spinnerNexradStation);
                        setItemEnabled(true, R.id.editCityStateText);
                        break;
                    case R.id.radioGPSLocation:
                        setRadioChecked(false, R.id.radioCityStateLocation, R.id.radioNexradStationLocation);
                        setItemEnabled(false, R.id.spinnerNexradStation, R.id.editCityStateText);
                        break;
                    case R.id.radioNexradStationLocation:
                        setRadioChecked(false, R.id.radioCityStateLocation, R.id.radioGPSLocation);
                        setItemEnabled(true, R.id.spinnerNexradStation);
                        setItemEnabled(false, R.id.editCityStateText);
                        break;
                    default:
                        Log.e(TAG,"received click for unknown radio button");
                        break;
                }
            }
        };
        RadioButton radioGPSLocation = (RadioButton)this.findViewById(R.id.radioGPSLocation);
        radioGPSLocation.setOnClickListener(radioClickListener);
        radioGPSLocation.setChecked(true);
        setItemEnabled(false, R.id.spinnerNexradStation, R.id.editCityStateText);
        RadioButton radioCityStateLocation = (RadioButton)this.findViewById(R.id.radioCityStateLocation);
        radioCityStateLocation.setOnClickListener(radioClickListener);
        RadioButton radioNexradStationLocation = (RadioButton)this.findViewById(R.id.radioNexradStationLocation);
        radioNexradStationLocation.setOnClickListener(radioClickListener);
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getAction().equals(DataRefreshIntent.GETSTATIONACTION)) {
                    int status = intent.getIntExtra("com.nexradnow.android.status",-1);
                    if (status == DataRefreshIntent.STATUS_FINISHED) {
                        // Extract station list
                        stationList = (List<NexradStation>) ((Bundle)intent.getBundleExtra(
                                "com.nexradnow.android.stationlist")).get("list");
                        // push station codes to selection spinner
                        updateStationCodes(stationList);
                    }
                }
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(DataRefreshIntent.GETSTATIONACTION);
        LocalBroadcastManager.getInstance(this).registerReceiver(receiver, filter);

        if ((stationList==null)||(stationList.isEmpty())) {
            // request fresh station list
            Intent intent = new Intent(this, DataRefreshIntent.class);
            intent.setAction(DataRefreshIntent.GETSTATIONACTION);
            startService(intent);
        } else {
            updateStationCodes(stationList);
        }
    }

    protected class StationSpinAdapter extends ArrayAdapter<NexradStation> {

        private Context context;
        private NexradStation[] values;

        public StationSpinAdapter(Context context, int resource, NexradStation[] values) {
            super(context, resource, values);
            this.context = context;
            this.values = values;
        }
        public int getCount(){
            return values.length;
        }

        public NexradStation getItem(int position){
            return values[position];
        }

        public long getItemId(int position){
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            TextView label = new TextView(context);
            label.setTextColor(Color.BLACK);
            label.setText(values[position].getIdentifier()+" - "+values[position].getLocation());
            return label;
        }

        @Override
        public View getDropDownView(int position, View convertView,
                                    ViewGroup parent) {
            TextView label = new TextView(context);
            label.setTextColor(Color.BLACK);
            label.setText(values[position].getIdentifier()+" - "+values[position].getLocation());
            return label;
        }
    }

    private void updateStationCodes(List<NexradStation> stationList) {
        Spinner spinStations = (Spinner)this.findViewById(R.id.spinnerNexradStation);
        spinStations.setAdapter(new StationSpinAdapter(this,android.R.layout.simple_spinner_item,
                stationList.toArray(new NexradStation[0])));
    }

    protected void setRadioChecked(boolean checked, int... buttonId) {
        for (int id : buttonId) {
            ((RadioButton)this.findViewById(id)).setChecked(checked);
        }
    }

    protected boolean getRadioChecked(int id) {
        return ((RadioButton)this.findViewById(id)).isChecked();
    }

    protected void setItemEnabled(boolean enabled, int... itemId) {
        for (int id: itemId) {
            this.findViewById(id).setEnabled(enabled);
        }
    }

    private void setLocationClicked() {
        LocationSelectionEvent event = null;
        if (getRadioChecked(R.id.radioGPSLocation)) {
            event = new LocationSelectionEvent(LocationSelectionEvent.SelectionType.GPS);
        } else if (getRadioChecked(R.id.radioNexradStationLocation)) {
            Spinner spinStations = (Spinner)this.findViewById(R.id.spinnerNexradStation);
            NexradStation station = (NexradStation)spinStations.getSelectedItem();
            event = new LocationSelectionEvent(LocationSelectionEvent.SelectionType.NEXRADSTATION);
            event.setStation(station);
        } else if (getRadioChecked(R.id.radioCityStateLocation)) {
            EditText text = (EditText)this.findViewById(R.id.editCityStateText);
            String cityState = text.getText().toString();
            event = new LocationSelectionEvent(LocationSelectionEvent.SelectionType.CITYSTATE);
            event.setCityState(cityState);
        }
        if (event != null) {
            eventBus.post(event);
        }
        finish();
    }


}
