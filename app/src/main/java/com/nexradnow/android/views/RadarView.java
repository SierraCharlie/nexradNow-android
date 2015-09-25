package com.nexradnow.android.views;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import com.google.inject.Inject;
import com.nexradnow.android.model.AppMessage;
import com.nexradnow.android.model.LatLongCoordinates;
import com.nexradnow.android.model.NexradProduct;
import com.nexradnow.android.model.NexradStation;
import com.nexradnow.android.model.NexradUpdate;
import com.nexradnow.android.services.EventBusProvider;
import roboguice.RoboGuice;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import ucar.ma2.ArrayByte;
import ucar.ma2.ArrayFloat;
import ucar.nc2.Dimension;
import ucar.nc2.NetcdfFile;
import ucar.nc2.Variable;


/**
 * Created by hobsonm on 9/14/15.
 */
public class RadarView extends View {

    public static String TAG = "RADARVIEW";

    @Inject
    protected EventBusProvider eventBusProvider;

    // Just a refresher: Latitude = (+ = N), (- = S), Longitude = (- = W), (+ = E)
    // Upper left (0,0) point (north-most, west-most)
    protected LatLongCoordinates origin;
    // Lower right (w,h) point (south-most, east-most)
    protected LatLongCoordinates max;

    protected int viewHeight;
    protected int viewWidth;

    protected Map<NexradStation,List<NexradProduct>> productDisplay;
    protected LatLongCoordinates centerPoint;


    public RadarView(Context context) {
        super(context);
        RoboGuice.getInjector(getContext()).injectMembers(this);
        eventBusProvider.getEventBus().register(this);
    }

    public RadarView(Context context, AttributeSet attrs) {
        super(context, attrs);
        RoboGuice.getInjector(getContext()).injectMembers(this);
        eventBusProvider.getEventBus().register(this);
    }

    public RadarView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        RoboGuice.getInjector(getContext()).injectMembers(this);
        eventBusProvider.getEventBus().register(this);
    }

    public void onEvent(AppMessage message) {
        // TODO: show message in some useful way
        Log.d(TAG, message.getMessage());
    }

    public void onEvent(NexradUpdate updateProducts) {
        Log.d(TAG, "update received: "+updateProducts.toString());
        this.productDisplay = updateProducts.getUpdateProduct();
        this.centerPoint = updateProducts.getCenterPoint();
        computeScales();
        this.invalidate();
    }

    public interface ValueGetter<T> {
        T getValue(Object obj);
    }

    // Compute initial scales given a set of stations and a location.
    private void computeScales() {
        float maxLatitude = this.findValue(productDisplay.keySet(), new ValueGetter<Float>() {
            public Float getValue(Object obj) {
                return (float) ((NexradStation) obj).getCoords().getLatitude();
            }
        }, true);
        float maxLongitude = this.findValue(productDisplay.keySet(), new ValueGetter<Float>() {
            public Float getValue(Object obj) {
                return (float) ((NexradStation) obj).getCoords().getLongitude();
            }
        }, true );
        float minLatitude = this.findValue(productDisplay.keySet(), new ValueGetter<Float>() {
            public Float getValue(Object obj) {
                return (float) ((NexradStation) obj).getCoords().getLatitude();
            }
        }, false );
        float minLongitude = this.findValue(productDisplay.keySet(), new ValueGetter<Float>() {
            public Float getValue(Object obj) {
                return (float) ((NexradStation) obj).getCoords().getLongitude();
            }
        }, false );

        maxLatitude = Math.max((float)centerPoint.getLatitude(),maxLatitude);
        maxLongitude = Math.max((float)centerPoint.getLongitude(),maxLongitude);
        minLatitude = Math.min((float)centerPoint.getLatitude(),minLatitude);
        minLongitude = Math.min((float)centerPoint.getLongitude(),minLongitude);


        float latitudeSpan = maxLatitude - minLatitude;
        float longitudeSpan = maxLongitude - minLongitude;

        float latPixelsPerDegree = viewHeight/latitudeSpan;
        float longPixelsPerDegree = viewWidth/longitudeSpan;
        // Force X- and Y-scales to be the same
        float pixelsPerDegree = Math.min(latPixelsPerDegree,longPixelsPerDegree);
        // Shrink scale a bit so everything fits within the view
        pixelsPerDegree *= 0.75;

        // Recompute spans
        float newLatitudeSpan = viewHeight/pixelsPerDegree;
        float newLongitudeSpan = viewWidth/pixelsPerDegree;

        // Adjust max/min values
        float latSpanDifference = newLatitudeSpan-latitudeSpan;
        float longSpanDifference = newLongitudeSpan-longitudeSpan;

        maxLatitude += latSpanDifference/2;
        minLatitude -= latSpanDifference/2;

        maxLongitude += longSpanDifference/2;
        minLongitude -= longSpanDifference/2;

        origin = new LatLongCoordinates(maxLatitude,minLongitude);
        max = new LatLongCoordinates(minLatitude,maxLongitude);

    }

    protected Point scaleLatLong(LatLongCoordinates coords) {
        double latitudeOffset = origin.getLatitude() - coords.getLatitude();
        double longitudeOffset = coords.getLongitude()-origin.getLongitude();

        int yOffset = (int)((float)viewHeight*latitudeOffset/(origin.getLatitude()-max.getLatitude()));
        int xOffset = (int)((float)viewWidth*longitudeOffset/(max.getLongitude()-origin.getLongitude()));
        return new Point(xOffset,yOffset);
    }

    protected<T extends Number> T findValue (Collection items, ValueGetter<T> getter, boolean findMax ) {
        T maxValue = null;
        for (Object item:items) {
            T value = getter.getValue(item);
            if (maxValue == null) {
                maxValue = value;
            } else if (findMax&&(value.doubleValue()>maxValue.doubleValue())) {
                maxValue = value;
            } else if (!findMax && (value.doubleValue()<maxValue.doubleValue())) {
                // actually finding the MINIMUM value
                maxValue = value;
            }
        }
        return maxValue;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        viewHeight = h;
        viewWidth = w;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if ((centerPoint == null)||(productDisplay==null)){
            return;
        }

        Paint brush = new Paint();
        Point origin = scaleLatLong(centerPoint);
        for (NexradStation station : productDisplay.keySet()) {
            List<NexradProduct> stationProducts = productDisplay.get(station);
            if ((stationProducts==null)||stationProducts.isEmpty()) {
                continue;
            }
            NexradProduct product = stationProducts.get(0);
            plotProduct(canvas, product);

        }
        for (NexradStation station : productDisplay.keySet()) {
            Point stationPoint = scaleLatLong(station.getCoords());
            canvas.drawCircle(stationPoint.x, stationPoint.y, 10, brush);
            canvas.drawText(station.getIdentifier(), stationPoint.x + 12, stationPoint.y + 4, brush);
        }
        canvas.drawCircle(origin.x,origin.y,4,brush);


        // TODO: decode Nexrad Level 3 data using netCDF library

        // TODO: map raster to display using min/max lat/long values
    }

    private void plotProduct(Canvas canvas, NexradProduct product) {
        byte[] rawData = product.getBinaryData();

        try {
            NetcdfFile netcdfFile = NetcdfFile.openInMemory("sn.last", rawData);
// [Attribute: geospatial_lat_min = (float)34.84564
            float latMin = netcdfFile.findGlobalAttribute("geospatial_lat_min").getNumericValue().floatValue();
// [Attribute: geospatial_lat_max = (float)43.10636
            float latMax = netcdfFile.findGlobalAttribute("geospatial_lat_max").getNumericValue().floatValue();
// [Attribute: geospatial_lon_min = (float)-72.16875
            float lonMin = netcdfFile.findGlobalAttribute("geospatial_lon_min").getNumericValue().floatValue();
// [Attribute: geospatial_lon_max = (float)-82.80525
            float lonMax = netcdfFile.findGlobalAttribute("geospatial_lon_max").getNumericValue().floatValue();
            float latSpan = latMax - latMin;
            if (latSpan < 0) {
                latSpan = -latSpan;
            }
            float lonSpan = lonMax - lonMin;
            if (lonSpan < 0) {
                lonSpan = -lonSpan;
            }
            float latOrigin = Math.min(latMin, latMax);
            float lonOrigin = Math.min(lonMin, lonMax);
            float valMax = netcdfFile.findGlobalAttribute("data_max").getNumericValue().floatValue();
            Variable valueVariable = netcdfFile.findVariable("BaseReflectivityComp");
            ArrayFloat.D2 floatArray = (ArrayFloat.D2)valueVariable.read();
            List<Dimension> dimensions = valueVariable.getDimensions();
            // TODO: verify that dimensions are y=232, x=232
            for (int y=0; y< 232; y++) {
                for (int x=0; x<232; x++) {
                    float cellValue = floatArray.get(y, x);
                    if ((cellValue <= 0)||(Float.isNaN(cellValue))) {
                        continue;
                    }
                    // Translate to location and fill rectangle with value
                    float plotValue = (float)cellValue/50.0f;
                    if (plotValue > 1) {
                        plotValue = 1.0f;
                    }
                    int color = getColor(1.0f-plotValue);
                    float ptLatStart = (float)(232.0f-y)/232.0f*latSpan + latOrigin;
                    float ptLonStart = (float)x/232.0f*lonSpan + lonOrigin;
                    LatLongCoordinates origin = new LatLongCoordinates(ptLatStart, ptLonStart);
                    LatLongCoordinates extent = new LatLongCoordinates(ptLatStart+latSpan/232.0f,ptLonStart+lonSpan/232.0f);
                    Paint cellBrush = new Paint();
                    cellBrush.setColor(color);
                    Rect paintRect = new Rect();
                    Point ptOrigin = scaleLatLong(origin);
                    Point ptExtent = scaleLatLong(extent);
                    paintRect.set(ptOrigin.x,ptExtent.y,ptExtent.x,ptOrigin.y);
                    canvas.drawRect(paintRect,cellBrush);
                }
            }
       } catch (Exception e) {
            Log.e(TAG,"cannot read input data", e);
            // TODO: send event to bus to show TOAST error
        }
    }

    public int getColor(float power)
    {
        float H = power * 120f; // Hue (note 0.4 = Green, see huge chart below)
        float S = 0.9f; // Saturation
        float B = 0.9f; // Brightness
        float[] hsv = new float[3];
        hsv[0] = H;
        hsv[1] = S;
        hsv[2] = B;
        return Color.HSVToColor(hsv);
    }


}