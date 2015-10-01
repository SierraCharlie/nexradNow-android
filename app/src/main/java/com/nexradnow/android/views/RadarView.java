package com.nexradnow.android.views;

import android.content.Context;
import android.graphics.*;
import android.support.v4.view.GestureDetectorCompat;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import com.google.inject.Inject;
import com.nexradnow.android.app.R;
import com.nexradnow.android.model.AppMessage;
import com.nexradnow.android.model.LatLongCoordinates;
import com.nexradnow.android.model.NexradProduct;
import com.nexradnow.android.model.NexradStation;
import com.nexradnow.android.model.NexradUpdate;
import com.nexradnow.android.services.EventBusProvider;
import org.nocrala.tools.gis.data.esri.shapefile.ShapeFileReader;
import org.nocrala.tools.gis.data.esri.shapefile.header.ShapeFileHeader;
import org.nocrala.tools.gis.data.esri.shapefile.shape.AbstractShape;
import org.nocrala.tools.gis.data.esri.shapefile.shape.PointData;
import org.nocrala.tools.gis.data.esri.shapefile.shape.ShapeType;
import org.nocrala.tools.gis.data.esri.shapefile.shape.shapes.AbstractPolyShape;
import org.nocrala.tools.gis.data.esri.shapefile.shape.shapes.PolygonShape;
import roboguice.RoboGuice;

import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import ucar.ma2.ArrayFloat;
import ucar.nc2.Dimension;
import ucar.nc2.NetcdfFile;
import ucar.nc2.Variable;


/**
 * This view has been superseded by a view that renders to an internal bitmap, to
 * speed up pan/scroll operations.
 *
 * @deprecated
 *
 * Created by hobsonm on 9/14/15.
 */
public class RadarView extends View implements GestureDetector.OnGestureListener {

    public static String TAG = "RADARVIEW";

    @Inject
    protected Context ctx;

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
    protected LatLongCoordinates selectedLocation;

    // Helper to detect gestures
    private GestureDetectorCompat mDetector;


    public RadarView(Context context) {
        super(context);
        init();
    }


    public RadarView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public RadarView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        RoboGuice.getInjector(getContext()).injectMembers(this);
        eventBusProvider.getEventBus().register(this);
        mDetector = new GestureDetectorCompat(ctx,this);
    }

    public void onEvent(AppMessage message) {
        // TODO: show message in some useful way
        Log.d(TAG, message.getMessage());
    }

    public void onEvent(NexradUpdate updateProducts) {
        Log.d(TAG, "update received: "+updateProducts.toString());
        this.productDisplay = updateProducts.getUpdateProduct();
        this.selectedLocation = updateProducts.getCenterPoint();
        computeInitialScales();
        this.invalidate();
    }

    @Override
    public boolean onDown(MotionEvent e) {
        return true;
    }

    @Override
    public void onShowPress(MotionEvent e) {
    }

    @Override
    public boolean onSingleTapUp(MotionEvent e) {
        return false;
    }

    @Override
    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
        Log.d(TAG,"onScroll(["+e1.toString()+"],["+e2.toString()+"],"+distanceX+","+distanceY+")");
        // adjust view center point, invalidate, and hope that things re-draw
        float latSpan = (float)Math.abs(max.getLatitude() - origin.getLatitude());
        float longSpan = (float)Math.abs(max.getLongitude() - origin.getLongitude());
        float latTranslate = -distanceY/(float)viewHeight * latSpan;
        float longTranslate = distanceX/(float)viewWidth * longSpan;
        // viewCenterPoint.translate(latTranslate, longTranslate);
        // TODO: translate max and origin lat/longs as well, prior to the redraw
        max.translate(latTranslate,longTranslate);
        origin.translate(latTranslate,longTranslate);
        this.invalidate();
        return true;
    }

    @Override
    public void onLongPress(MotionEvent e) {
    }

    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if(this.mDetector.onTouchEvent(event)) {
            return true;
        }
        return super.onTouchEvent(event);
    }

    public interface ValueGetter<T> {
        T getValue(Object obj);
    }

    // Compute initial scales given a set of stations and a location.
    private void computeInitialScales() {
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

        // Adjust so that the center falls within the max/min values (for sure)
        maxLatitude = Math.max((float) selectedLocation.getLatitude(),maxLatitude);
        maxLongitude = Math.max((float) selectedLocation.getLongitude(),maxLongitude);
        minLatitude = Math.min((float) selectedLocation.getLatitude(),minLatitude);
        minLongitude = Math.min((float) selectedLocation.getLongitude(),minLongitude);

        // Adjust min/max (and resulting span) values so that selectedLocation is in fact centered.
        float halfLatSpan = maxLatitude - (float) selectedLocation.getLatitude();
        halfLatSpan = Math.max(halfLatSpan, (float) selectedLocation.getLatitude()-minLatitude);
        maxLatitude = (float) selectedLocation.getLatitude() + halfLatSpan;
        minLatitude = (float) selectedLocation.getLatitude() - halfLatSpan;

        float halfLongSpan = maxLongitude - (float) selectedLocation.getLongitude();
        halfLongSpan = Math.max((float) selectedLocation.getLongitude() - minLongitude, halfLongSpan);
        maxLongitude = (float) selectedLocation.getLongitude() + halfLongSpan;
        minLongitude = (float) selectedLocation.getLongitude() - halfLongSpan;

        // And now we return you to your regular programming
        float latitudeSpan = maxLatitude - minLatitude;
        float longitudeSpan = maxLongitude - minLongitude;

        float latPixelsPerDegree = viewHeight/latitudeSpan;
        float longPixelsPerDegree = viewWidth/longitudeSpan;
        // Force X- and Y-scales to be the same
        float pixelsPerDegree = Math.min(latPixelsPerDegree,longPixelsPerDegree);
        // Shrink scale a bit so everything fits within the view (This is how we get our "margins", so that
        // things don't sit exactly on the edges of the display
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

        if ((selectedLocation == null)||(productDisplay==null)){
            return;
        }

        Paint brush = new Paint();
        Point origin = scaleLatLong(selectedLocation);
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
        plotMap(canvas);
    }

    private void plotMap(Canvas canvas) {
        try {
            InputStream is = ctx.getResources().openRawResource(R.raw.cb_2014_us_state_20m);
            ShapeFileReader sr = new ShapeFileReader(is);
            ShapeFileHeader hdr = sr.getHeader();
            switch (hdr.getShapeType()) {
                case POLYGON:
                case POLYGON_Z:
                    break;
                default:
                    throw new IllegalStateException("shape file of unsupported type encountered: "
                            + hdr.getShapeType().toString());
            }
            AbstractShape s;
            int shapeCount = 0;
            int includedShapes = 0;
            while ((s = sr.next()) != null) {
                AbstractPolyShape polygon = (AbstractPolyShape)s;
                shapeCount++;
                if (!shapeExcluded(polygon)) {
                    includedShapes++;
                    for (int part = 0; part < polygon.getNumberOfParts(); part++) {
                        PointData[] points = polygon.getPointsOfPart(part);
                        plotPolygonPoints(canvas, points);
                    }
                }
            }
            Log.d(TAG,"total shapes: "+shapeCount+" included: "+includedShapes);
            is.close();
        } catch (Exception ex) {
            Log.e(TAG,"error while reading outline shape file", ex);
            AppMessage message = new AppMessage(ex.toString(), AppMessage.Type.ERROR);
            eventBusProvider.getEventBus().post(message);
        }
    }

    /**
     * Do some simple bounds checking to see if the polygon might possibly lie within our view window
     * @param polygon
     * @return
     */
    private boolean shapeExcluded(AbstractPolyShape polygon) {
        double maxViewLongitude = max.getLongitude();
        double minViewLongitude = origin.getLongitude();
        double maxViewLatitude = origin.getLatitude();
        double minViewLatitude = max.getLatitude();
        boolean exclude = false;
        if (polygon.getBoxMinX() > maxViewLongitude) {
            // excluded
            exclude =  true;
        } else if (polygon.getBoxMaxX() < minViewLongitude) {
            // excluded
            exclude = true;
        } else  if (polygon.getBoxMaxY() < minViewLatitude) {
            // excluded
            exclude = true;
        } else if (polygon.getBoxMinY() > maxViewLatitude) {
            exclude = true;
        }
        // This isn't completely true, but hopefully we will exclude the majority of shapes
        return exclude;

    }

    private void plotPolygonPoints(Canvas canvas, PointData[] points) {
        Paint brush = new Paint();
        Point from = null;
        Point to = null;
        for (PointData eachSrcPoint : points) {
            to = scaleLatLong(new LatLongCoordinates(eachSrcPoint.getY(),eachSrcPoint.getX()));
            if (from != null) {
                canvas.drawLine(from.x,from.y,to.x,to.y,brush);
            }
            from = to;
        }
    }

    private void plotProduct(Canvas canvas, NexradProduct product) {
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

    /**
     * Generate a color from green->red, depending on value of input. For 1.0, create pure red,
     * for 0.0, create a green.
     *
     * @param power value ranging from 1.0 to 0.0
     * @return color value ranging from a light green to a pure red
     */
    public int getColor(float power)
    {
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