package com.nexradnow.android.nexradproducts;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.Rect;
import com.nexradnow.android.exception.NexradNowException;
import com.nexradnow.android.model.LatLongCoordinates;
import com.nexradnow.android.model.LatLongRect;
import com.nexradnow.android.model.LatLongScaler;
import com.nexradnow.android.model.NexradProduct;
import com.nexradnow.android.model.NexradStation;
import ucar.ma2.ArrayFloat;
import ucar.nc2.Dimension;
import ucar.nc2.NetcdfFile;
import ucar.nc2.Variable;

import java.util.Collection;
import java.util.List;

/**
 * Created by hobsonm on 10/15/15.
 */
public abstract class NexradGridRenderer implements NexradRenderer {
    @Override
    public LatLongRect findExtents(NexradProduct product) throws NexradNowException {
        LatLongRect result = null;
        try {
            NetcdfFile netcdfFile = NetcdfFile.openInMemory("sn.last", product.getBinaryData());
            float latMin = netcdfFile.findGlobalAttribute("geospatial_lat_min").getNumericValue().floatValue();
            float latMax = netcdfFile.findGlobalAttribute("geospatial_lat_max").getNumericValue().floatValue();
            float lonMin = netcdfFile.findGlobalAttribute("geospatial_lon_min").getNumericValue().floatValue();
            float lonMax = netcdfFile.findGlobalAttribute("geospatial_lon_max").getNumericValue().floatValue();
            if (lonMin > lonMax) {
                float temp = lonMax;
                lonMax = lonMin;
                lonMin = temp;
            }
            if (latMin > latMax) {
                float temp = latMax;
                latMax = latMin;
                latMin = temp;
            }
            result = new LatLongRect(lonMin,latMax,lonMax,latMin);
            netcdfFile.close();
        } catch (Exception ex) {
            String message = "error computing bounds of radar data for station "+product.getStation().getIdentifier();
            throw new NexradNowException(message);
        }
        return result;
    }

    @Override
    public abstract String getProductCode();

    @Override
    public abstract String getProductDescription();

    @Override
    public void renderToCanvas(Canvas canvas, NexradProduct product, Paint paint, LatLongScaler scaler, int minFeatureSize)
            throws NexradNowException {
        renderToCanvas(canvas, product, paint, scaler, minFeatureSize, 0.0, null, null);
    }

    @Override
    public void renderToCanvas(Canvas canvas, NexradProduct product, Paint paint, LatLongScaler scaler, int minFeatureSize,
                               double safeDistance, NexradStation dataOwner, Collection<NexradStation> otherStations)
            throws NexradNowException {
        Paint productPaint = paint;
        byte[] rawData = product.getBinaryData();

        try {
            NetcdfFile netcdfFile = NetcdfFile.openInMemory("sn.last", rawData);
            float latMin = netcdfFile.findGlobalAttribute("geospatial_lat_min").getNumericValue().floatValue();
            float latMax = netcdfFile.findGlobalAttribute("geospatial_lat_max").getNumericValue().floatValue();
            float lonMin = netcdfFile.findGlobalAttribute("geospatial_lon_min").getNumericValue().floatValue();
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
            // TODO search file for a square array of floating point values, regardless of name
            ArrayFloat.D2 floatArray = (ArrayFloat.D2)valueVariable.read();
            List<Dimension> dimensions = valueVariable.getDimensions();
            if ((dimensions.size() != 2)||(dimensions.get(0).getLength()!=dimensions.get(1).getLength())) {
                StringBuilder message = new StringBuilder();
                message.append("[");
                for (int index=0; index < dimensions.size(); index++) {
                    message.append(Integer.toString(dimensions.get(index).getLength()));
                    if (index != dimensions.size()-1) {
                        message.append(",");
                    }
                }
                message.append("]");
                throw new NexradNowException("unexpected data dimensionality: "+message.toString());
            }
            int numCells = dimensions.get(0).getLength();
            for (int y=0; y< numCells; y++) {
                for (int x=0; x<numCells; x++) {
                    float cellValue = floatArray.get(y, x);
                    if ((cellValue <= 0)||(Float.isNaN(cellValue))) {
                        continue;
                    }
                    // Translate to location and fill rectangle with value
                    int color = getColorFromTable(cellValue / 100.0f);
                    float ptLatStart = (float)((float)numCells-y)/(float)numCells*latSpan + latOrigin;
                    float ptLonStart = (float)x/(float)numCells*lonSpan + lonOrigin;
                    LatLongCoordinates origin = new LatLongCoordinates(ptLatStart, ptLonStart);
                    // check distance against min distance
                    if (dataOwner != null) {
                        if (origin.distanceTo(dataOwner.getCoords()) > safeDistance) {
                            // Need to check against all other stations
                            NexradStation closestStation = null;
                            double closestDistance = 0.0;
                            for (NexradStation station:otherStations) {
                                if ((closestStation == null)||(station.getCoords().distanceTo(origin) < closestDistance)) {
                                    closestStation = station;
                                    closestDistance = station.getCoords().distanceTo(origin);
                                }
                            }
                            if (!closestStation.getIdentifier().equals(dataOwner.getIdentifier())) {
                                continue;
                            }
                        }
                    }
                    LatLongCoordinates extent = new LatLongCoordinates(ptLatStart+latSpan/(float)numCells,ptLonStart+lonSpan/(float)numCells);
                    if (productPaint == null) {
                        productPaint = new Paint();
                    }
                    Paint cellBrush = productPaint;
                    cellBrush.setColor(color);
                    Rect paintRect = new Rect();
                    PointF ptOrigin = scaler.scaleLatLong(origin);
                    PointF ptExtent = scaler.scaleLatLong(extent);
                    paintRect.set((int)ptOrigin.x,(int)ptExtent.y,(int)ptExtent.x,(int)ptOrigin.y);
                    canvas.drawRect(paintRect,cellBrush);
                }
            }
            netcdfFile.close();
        } catch (Exception e) {
            String message = "cannot read Nexrad product data: "+e.toString();
            throw new NexradNowException(message);
        }


    }

    /**
     * Generate a color from green->red, depending on value of input. For 1.0, create pure red,
     * for 0.0, create a green.
     *
     * @param power value ranging from 1.0 to 0.0
     * @return color value ranging from a light green to a pure red
     */
    protected int getColor(float power)
    {
        // TODO: create different strategies for generating colors so we can (hopefully) plot other products someday
        float H = (1.0f-power) * 120f; // Hue (note 0.4 = Green, see huge chart below)
        float S = 0.9f; // Saturation
        float B = 0.9f; // Brightness
        float[] hsv = new float[3];
        hsv[0] = H;
        hsv[1] = S;
        hsv[2] = B;
        return Color.HSVToColor(hsv);
    }

    static int[] colorTable = {
            Color.rgb(93,225,117), // 5 db
            Color.rgb(80,197,65), // 10 db
            Color.rgb(65,164,49), // 15 db
            Color.rgb(53,136,40), // 20 db
            Color.rgb(41,109,37), // 25 db
            Color.rgb(34,94,31), // 30 db
            Color.rgb(249,239,0), // 35 db
            Color.rgb(238,188,0), // 40 db
            Color.rgb(240,144,0), // 45 db
            Color.rgb(228,108,0), // 50 db
            Color.rgb(214,42,0), // 55 db
            Color.rgb(164,29,0), // 60 db
            Color.rgb(217,35,143), // 65 db
            Color.rgb(159,0,211), // 70 db
            Color.rgb(106,2,118) // 75 db and up
    };

    protected int getColorFromTable(float power)
    {
        int index = ((int)(power * 100.0)/5)-1;
        if (index < 0) { index = 0; }
        if (index >= colorTable.length) { index = colorTable.length-1;}
        return colorTable[index];
    }
}
