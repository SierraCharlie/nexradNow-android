package com.nexradnow.android.nexradproducts;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.util.Log;
import com.nexradnow.android.exception.NexradNowException;
import com.nexradnow.android.model.AppMessage;
import com.nexradnow.android.model.LatLongCoordinates;
import com.nexradnow.android.model.LatLongRect;
import com.nexradnow.android.model.LatLongScaler;
import com.nexradnow.android.model.NexradProduct;
import roboguice.RoboGuice;
import ucar.ma2.ArrayFloat;
import ucar.nc2.Dimension;
import ucar.nc2.NetcdfFile;
import ucar.nc2.Variable;

import java.util.List;

/**
 * Created by hobsonm on 10/15/15.
 */
public class P38crRenderer implements NexradRenderer {
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
    public String getProductCode() {
        return "p38cr";
    }

    @Override
    public String getProductDescription() {
        return "Composite Reflectivity - 248 nmi range";
    }

    @Override
    public void renderToCanvas(Canvas canvas, NexradProduct product, Paint paint, LatLongScaler scaler)
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
                    int color = getColor(plotValue);
                    float ptLatStart = (float)(232.0f-y)/232.0f*latSpan + latOrigin;
                    float ptLonStart = (float)x/232.0f*lonSpan + lonOrigin;
                    LatLongCoordinates origin = new LatLongCoordinates(ptLatStart, ptLonStart);
                    LatLongCoordinates extent = new LatLongCoordinates(ptLatStart+latSpan/232.0f,ptLonStart+lonSpan/232.0f);
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
    public int getColor(float power)
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

}
