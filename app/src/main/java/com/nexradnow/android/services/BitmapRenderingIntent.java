package com.nexradnow.android.services;

import android.app.IntentService;
import android.content.Intent;
import android.graphics.*;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import com.nexradnow.android.app.NexradApp;
import com.nexradnow.android.app.R;
import com.nexradnow.android.exception.NexradNowException;
import com.nexradnow.android.model.*;
import com.nexradnow.android.nexradproducts.NexradRenderer;
import com.nexradnow.android.nexradproducts.RendererInventory;
import com.nexradnow.android.util.NexradNowFileUtils;
import org.nocrala.tools.gis.data.esri.shapefile.ShapeFileReader;
import org.nocrala.tools.gis.data.esri.shapefile.header.ShapeFileHeader;
import org.nocrala.tools.gis.data.esri.shapefile.shape.AbstractShape;
import org.nocrala.tools.gis.data.esri.shapefile.shape.PointData;
import org.nocrala.tools.gis.data.esri.shapefile.shape.shapes.AbstractPolyShape;
import toothpick.Toothpick;

import javax.inject.Inject;
import java.io.File;
import java.io.InputStream;
import java.util.*;

/**
 * Create a bitmap from the supplied Nexrad products and desired display geometry.
 * Created by hobsonm on 10/23/15.
 */
public class BitmapRenderingIntent extends IntentService {

    public static final int STATUS_RUNNING = 0;
    public static final int STATUS_FINISHED = 1;
    public static final int STATUS_ERROR = 2;

    private static final String TAG = "BitmapRenderingIntent";

    public static String RENDERACTION = "com.nexradnow.android.renderBitmap";

    @Inject
    protected RendererInventory rendererInventory;

    public BitmapRenderingIntent() {
        super(BitmapRenderingIntent.class.getName());
        Toothpick.inject(this,Toothpick.openScope(NexradApp.APPSCOPE));
    }

    /**
     * Given a point in lat/long coordinates, and a pixel area of size pixelRect that covers
     * the region described by lat/long region coordRect, compute the pixel location of this
     * point in that space.
     * @param coords
     * @param coordRect
     * @param pixelRect
     * @return
     */
    private static Point scaleCoordinate (LatLongCoordinates coords, LatLongRect coordRect, Rect pixelRect) {
        double latOffset = coords.getLatitude() - coordRect.bottom;
        double longOffset = coords.getLongitude() - coordRect.left;
        int pixY = (int)((latOffset*(double)pixelRect.height())/coordRect.height());
        // Transform to correct Y-scale
        pixY = pixelRect.height() - pixY;
        int pixX = (int)((longOffset*(double)pixelRect.width())/coordRect.width());
        return new Point(pixX,pixY);
    }

    private static LatLongCoordinates scalePoint(Point point, LatLongRect coordRect, Rect pixelRect) {
        int xOffset = point.x - pixelRect.left;
        int yOffset = point.y - pixelRect.top;
        double longValue = coordRect.left + (float)xOffset/(float)pixelRect.width()* coordRect.width();
        double latValue = coordRect.top - (float)yOffset/(float)pixelRect.height() * coordRect.height();
        return new LatLongCoordinates(latValue, longValue);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Log.d(TAG,"Starting rendering");
        if (RENDERACTION.equals(intent.getAction()) ){
            renderBitmap(intent);
        }
        Log.d(TAG,"Rendering Complete");
    }

    private void renderBitmap(Intent intent) {
        // read update from cache file and then delete
        File updateFile = (File)intent.getSerializableExtra("com.nexradnow.android.nexradUpdate");
        NexradUpdate update = null;
        try {
            update = (NexradUpdate)NexradNowFileUtils.readObjectFromFile(updateFile);
            updateFile.delete();
            NexradNowFileUtils.clearCacheFiles(this.getApplicationContext(),"wxdat","tmp");
        } catch (Exception ioex) {
            intent.putExtra("com.nexradnow.android.status", STATUS_ERROR);
            intent.putExtra("com.nexradnow.android.errmsg", "update read error: "+ioex.toString());
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
            return;
        }

        LatLongRect desiredRegion = (LatLongRect)intent.getSerializableExtra("com.nexradnow.android.latLongRect");
        Rect bitmapSize = (Rect)intent.getParcelableExtra("com.nexradnow.android.bitmapRect");
        float displayDensity = intent.getFloatExtra("com.nexradnow.android.displayDensity", 1.0f);

        if ((desiredRegion==null)||(bitmapSize==null)||(update==null)) {
            notifyException(intent,new NexradNowException("Missing required input data for rendering"));
        }
        notifyProgress(intent, "Rendering...");
        File bitmapFile = null;
        try {
            Bitmap bitmap = computeRegion(intent, desiredRegion, bitmapSize, update, displayDensity);
            if (bitmap != null) {
                bitmapFile = NexradNowFileUtils.writeBitmapToCacheFile(this, "bitmap", "tmp", bitmap);
                // Drop the bitmap because it has been written to disk!
                bitmap.recycle();
            } else {
                notifyException(intent, new NexradNowException("No Bitmap Computed"));
            }
        } catch (Exception ex) {
            notifyException(intent, ex);
            bitmapFile = null;
        }
        if (bitmapFile != null) {
            intent.putExtra("com.nexradnow.android.status", STATUS_FINISHED);
            intent.putExtra("com.nexradnow.android.bitmap", bitmapFile);
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
        }
    }

    private void notifyProgress(Intent intent, String progressMsg) {
        intent.putExtra("com.nexradnow.android.status", STATUS_RUNNING);
        intent.putExtra("com.nexradnow.android.statusmsg",progressMsg);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private void notifyException(Intent intent, Exception ex) {
        if (ex instanceof NexradNowException) {
            intent.putExtra("com.nexradnow.android.errmsg", ex.getMessage());
        } else {
            intent.putExtra("com.nexradnow.android.errmsg", ex.toString());
        }
        intent.putExtra("com.nexradnow.android.status", STATUS_ERROR);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    protected Bitmap computeRegion(Intent intent, LatLongRect region, Rect bitmapSize, NexradUpdate nexradData,
                                   float displayDensity ) {
        Bitmap bitmap = createBackingBitmap(bitmapSize);
        if (bitmap == null) {
            // No bitmap created!
            Log.e(TAG, "no backing bitmap created - out of memory?");
            return null;
        }

        Canvas bitmapCanvas = new Canvas(bitmap);
        Calendar oldestTimestamp = null;
        Collection<NexradStation> productStations = nexradData.getUpdateProduct()==null?null:nexradData.getUpdateProduct().keySet();
        Map<NexradStation,List<NexradProduct>> nexradProduct = nexradData.getUpdateProduct();
        if ((productStations!=null)&&(!productStations.isEmpty())) {
            // Compute smallest inter-station distance
            Collection<NexradStation> validStations = findStationsWithData(nexradData.getUpdateProduct());
            Collection<NexradStation> containedStations = new ArrayList<NexradStation>();
            for (NexradStation station : validStations) {
                if (!region.contains(station.getCoords())) {
                    continue;
                }
                containedStations.add(station);
            }
            double interStationDistanceMin = computeMinInterstationDistance(containedStations);
            int stationCount = containedStations.size();
            int stationIndex = 0;
            for (NexradStation station: containedStations) {
                stationIndex++;
                int percentComplete = (stationIndex*100)/stationCount;
                String progressMessage = "Rendering ["+percentComplete+"%]";
                notifyProgress(intent, progressMessage);
                if ((nexradProduct.get(station) != null)
                        && (!nexradProduct.get(station).isEmpty())) {
                    Calendar thisTimestamp = nexradData.getUpdateProduct().get(station).get(0).getTimestamp();
                    if ((oldestTimestamp == null) || (thisTimestamp.before(oldestTimestamp))) {
                        oldestTimestamp = thisTimestamp;
                    }
                    plotProduct(bitmapCanvas, region, bitmapSize, nexradProduct.get(station).get(0),
                            interStationDistanceMin/2.0, station, containedStations);
                }
            }
            for (NexradStation station : nexradProduct.keySet()) {
                if (!region.contains(station.getCoords())) {
                    continue;
                }
                boolean stationHasData = (nexradProduct.get(station) != null) && (!nexradProduct.get(station).isEmpty());
                plotStation(bitmapCanvas, region, bitmapSize, station, stationHasData, displayDensity);
            }
        } else {
            // No products or empty product set
        }
        drawMap(bitmapCanvas, region, bitmapSize, displayDensity);

        drawHomePoint(bitmapCanvas, region, bitmapSize, nexradData.getCenterPoint(), displayDensity);
        Calendar dataTimestamp = oldestTimestamp;

        return bitmap;
    }

    private void drawHomePoint(Canvas canvas, LatLongRect latLongRect, Rect pixelSize, LatLongCoordinates location,
                               float displayDensity) {
        Paint brush = new Paint();
        Point locationPoint = scaleCoordinate(location, latLongRect, pixelSize);
        canvas.drawCircle(locationPoint.x,locationPoint.y,scalePixels(3, displayDensity),brush);
    }

    /**
     * Do some simple bounds checking to see if the polygon might possibly lie within our view window
     * @param polygon
     * @return
     */
    private boolean shapeExcluded(AbstractPolyShape polygon, LatLongRect latLongRect) {
        double maxViewLongitude = latLongRect.right;
        double minViewLongitude = latLongRect.left;
        double maxViewLatitude = latLongRect.top;
        double minViewLatitude = latLongRect.bottom;
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

    private void plotPolygonPoints(Canvas canvas, LatLongRect latLongRect, Rect pixelSize, PointData[] points,
                                   float displayDensity) {
        Paint    mapPaint = new Paint();
        mapPaint.setStrokeWidth(scalePixels(2, displayDensity));
        mapPaint.setColor(Color.GRAY);
        mapPaint.setAlpha(50);
        Point from = null;
        Point to = null;
        for (PointData eachSrcPoint : points) {
            to = scaleCoordinate(new LatLongCoordinates(eachSrcPoint.getY(), eachSrcPoint.getX()), latLongRect, pixelSize);
            if (from != null) {
                canvas.drawLine(from.x,from.y,to.x,to.y,mapPaint);
            }
            from = to;
        }
    }

    private void drawMap(Canvas canvas, LatLongRect latLongRect, Rect pixelSize, float displayDensity) {
        try {
            InputStream is = this.getApplicationContext().getResources().openRawResource(R.raw.cb_2014_us_state_20m);
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
                if (!shapeExcluded(polygon, latLongRect)) {
                    includedShapes++;
                    for (int part = 0; part < polygon.getNumberOfParts(); part++) {
                        PointData[] points = polygon.getPointsOfPart(part);
                        plotPolygonPoints(canvas, latLongRect, pixelSize, points, displayDensity);
                    }
                }
            }
            Log.d(TAG,"total shapes: "+shapeCount+" included: "+includedShapes);
            is.close();
        } catch (Exception ex) {
            Log.e(TAG,"error while reading outline shape file", ex);
        }
    }

    private void plotStation(Canvas canvas, LatLongRect latLongRect, Rect pixelSize,
                             NexradStation station, boolean hasData, float displayDensity) {
        Paint stationPaint = new Paint();
        stationPaint.setTextSize(scalePixels(10, displayDensity));
        stationPaint.setStyle(Paint.Style.FILL);

        Paint brush = stationPaint;
        Point stationPoint = scaleCoordinate(station.getCoords(), latLongRect, pixelSize);

        stationPaint.setColor(Color.DKGRAY);
        stationPaint.setAlpha(128);
        if (hasData) {
            canvas.drawCircle(stationPoint.x, stationPoint.y, scalePixels(7, displayDensity), brush);
        } else {
            // Draw special symbol for station that has no data associated with it.
            canvas.drawCircle(stationPoint.x, stationPoint.y, scalePixels(7, displayDensity), brush);
            stationPaint.setColor(Color.WHITE);
            canvas.drawCircle(stationPoint.x, stationPoint.y, scalePixels(5, displayDensity), brush);
            stationPaint.setStyle(Paint.Style.STROKE);
            stationPaint.setColor(Color.RED);
            float defaultStrokeWidth = stationPaint.getStrokeWidth();
            stationPaint.setStrokeWidth(scalePixels(2, displayDensity));
            canvas.drawCircle(stationPoint.x, stationPoint.y, scalePixels(5, displayDensity), brush);
            canvas.drawLine(stationPoint.x-scalePixels(3, displayDensity),stationPoint.y-scalePixels(3, displayDensity),
                    stationPoint.x+scalePixels(3, displayDensity),stationPoint.y+scalePixels(3, displayDensity),stationPaint);
            stationPaint.setStyle(Paint.Style.FILL);
            stationPaint.setStrokeWidth(defaultStrokeWidth);
        }
        stationPaint.setColor(Color.BLACK);
        stationPaint.setAlpha(180);
        canvas.drawText(station.getIdentifier(),
                stationPoint.x + scalePixels(12, displayDensity), stationPoint.y + scalePixels(4, displayDensity), brush);
    }

    /**
     * Convenience function to convert device-independent pixels to correct underlying pixel count
     * @param dp
     * @return
     */
    public int scalePixels (float dp, float displayDensity) {
        return (int)((float)dp * displayDensity + 0.5f);
    }

    private void plotProduct(Canvas canvas, final LatLongRect latLongRect, final Rect pixelSize, NexradProduct product,
                             double safeDistance, NexradStation creator, Collection<NexradStation> otherSources) {
        LatLongScaler scaler = new LatLongScaler() {
            @Override
            public PointF scaleLatLong(LatLongCoordinates coordinates) {
                return new PointF(BitmapRenderingIntent.scaleCoordinate(coordinates, latLongRect, pixelSize));
            }
            @Override
            public float distanceForPixels(int pixelCount) {
                double lonSpan = latLongRect.width();
                double lonPerPixel = lonSpan/pixelSize.width();
                double distancePerLon = 111132;
                double distance = lonPerPixel * (double)pixelCount * distancePerLon;
                return (float)distance;
            }
            @Override
            public LatLongCoordinates scalePoint(PointF point) {
                return BitmapRenderingIntent.scalePoint(new Point((int) point.x, (int) point.y), latLongRect, pixelSize);
            }
            @Override
            public int pixelsForDistance(float distanceMeters) {
                double lonSpan = latLongRect.width();
                double lonPerPixel = lonSpan/pixelSize.width();
                double distancePerLon = 111132;
                double pixels = (double)distanceMeters/distancePerLon/lonPerPixel;
                return (int)pixels;
            }
        };
        Paint productPaint = new Paint();
        NexradRenderer renderer = rendererInventory.getRenderer(product.getProductCode());
        if (renderer != null) {
            try {
                renderer.renderToCanvas(canvas, product, productPaint, scaler, 4, safeDistance, creator, otherSources);
            } catch (Exception ex) {
                Log.e(TAG,"error rendering product for station "+product.getStation().getIdentifier(),ex);
            }
        }
    }


    private Collection<NexradStation> findStationsWithData(Map<NexradStation, List<NexradProduct>> productDisplay) {
        Collection<NexradStation> stationsWithData = new ArrayList<NexradStation>();
        for (NexradStation station : productDisplay.keySet()) {
            boolean stationHasData = (productDisplay.get(station) != null) && (!productDisplay.get(station).isEmpty());
            if (stationHasData) {
                stationsWithData.add(station);
            }
        }
        return stationsWithData;
    }

    /**
     * Figure out the smallest distance between any two stations in the product display set. We'll need this later
     * when we figure out whether to plot data from a particular station (we only want to plot a data point if the
     * associated station is the closest station).
     * @param nexradStations
     * @return smallest distance between two stations
     */
    private double computeMinInterstationDistance(Collection<NexradStation> nexradStations) {
        boolean initialized = false;
        double smallestDistance = 0.0;
        for (NexradStation parentStation : nexradStations) {
            for (NexradStation childStation : nexradStations) {
                if (childStation.getIdentifier().equals(parentStation.getIdentifier())) {
                    continue;
                }
                // We have a station pair - compute distance
                double pairDistance = childStation.getCoords().distanceTo(parentStation.getCoords());
                if (!initialized) {
                    initialized = true;
                    smallestDistance = pairDistance;
                } else {
                    if (pairDistance < smallestDistance) {
                        smallestDistance = pairDistance;
                    }
                }
            }
        }
        return smallestDistance;
    }

    protected Bitmap createBackingBitmap(Rect region) {
        Bitmap bitmap = Bitmap.createBitmap(region.width(), region.height(), Bitmap.Config.ARGB_4444);
        return bitmap;
    }
}
