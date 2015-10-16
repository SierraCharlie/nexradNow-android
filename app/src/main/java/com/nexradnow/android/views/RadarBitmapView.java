package com.nexradnow.android.views;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Handler;
import android.support.v4.view.GestureDetectorCompat;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import com.google.inject.Inject;
import com.nexradnow.android.app.R;
import com.nexradnow.android.exception.NexradNowException;
import com.nexradnow.android.model.AppMessage;
import com.nexradnow.android.model.LatLongCoordinates;
import com.nexradnow.android.model.LatLongRect;
import com.nexradnow.android.model.LatLongScaler;
import com.nexradnow.android.model.LocationChangeEvent;
import com.nexradnow.android.model.NexradProduct;
import com.nexradnow.android.model.NexradStation;
import com.nexradnow.android.model.NexradUpdate;
import com.nexradnow.android.model.ProductRequestMessage;
import com.nexradnow.android.nexradproducts.NexradRenderer;
import com.nexradnow.android.nexradproducts.RendererInventory;
import com.nexradnow.android.services.EventBusProvider;
import org.nocrala.tools.gis.data.esri.shapefile.ShapeFileReader;
import org.nocrala.tools.gis.data.esri.shapefile.header.ShapeFileHeader;
import org.nocrala.tools.gis.data.esri.shapefile.shape.AbstractShape;
import org.nocrala.tools.gis.data.esri.shapefile.shape.PointData;
import org.nocrala.tools.gis.data.esri.shapefile.shape.shapes.AbstractPolyShape;
import roboguice.RoboGuice;
import ucar.ma2.ArrayFloat;
import ucar.nc2.Dimension;
import ucar.nc2.NetcdfFile;
import ucar.nc2.Variable;

import java.io.InputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * This version of the content display renders its graphics to a backing bitmap,
 * and then displays a portion of the bitmap on the view. Scroll/pan operations are
 * then carried out using the bitmap, with a significant performance improvement.
 *
 * Created by hobsonm on 9/29/15.
 */
public class RadarBitmapView extends View implements GestureDetector.OnGestureListener {

    public static String TAG = "RADARBITMAPVIEW";

    @Inject
    protected Context ctx;

    @Inject
    protected EventBusProvider eventBusProvider;

    @Inject
    protected RendererInventory rendererInventory;

    // Helper to detect gestures
    private GestureDetectorCompat mDetector;

    /**
     * Collection of Nexrad data that is to be displayed
     */
    private Map<NexradStation, List<NexradProduct>> productDisplay;

    /**
     * Computed oldest timestamp of data on display
     */
    private Calendar dataTimestamp;

    /**
     * Location selected by user (typically current physical location)
     */
    private LatLongCoordinates selectedLocation;

    /**
     * Last known height of the view area
     */
    private int viewHeight;

    /**
     * Last known width of the view area
     */
    private int viewWidth;

    /**
     * Backing bitmap, where the graphics are rendered
     */
    protected Bitmap backingBitmap;

    /**
     * The lat/long area covered by the backing bitmap
     */
    protected LatLongRect bitmapLatLongRect;

    /**
     * The size (in pixels) of the entire backing bitmap
     */
    protected Rect bitmapPixelSize;

    /**
     * The subsection of the backing bitmap that is currently being displayed
     */
    protected Rect bitmapClipRect;

    /**
     * The current display density, used to scale pixel sizes
     */
    protected float displayDensity;

    // Paint objects for various operations
    protected Paint drawPaint; // onDraw

    protected Paint homePointPaint; // used to draw home point

    protected Paint productPaint; // used to draw product

    protected Paint stationPaint; // used to draw station locations

    protected Paint mapPaint; // used to draw map

    protected NexradRenderer lastRenderer;

    public RadarBitmapView(Context context) {
        super(context);
        init();
    }

    public RadarBitmapView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public RadarBitmapView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        RoboGuice.getInjector(getContext()).injectMembers(this);
        eventBusProvider.getEventBus().register(this);
        mDetector = new GestureDetectorCompat(ctx,this);
        displayDensity = getResources().getDisplayMetrics().density;
        // Invalidate the display every minute (or so) to allow timestamp to repaint
        final Handler handler = new Handler();
        handler.postDelayed( new Runnable() {
            public void run() {
                RadarBitmapView.this.invalidate();
                // Re-run the handler
                handler.postDelayed( this, TimeUnit.MILLISECONDS.convert(1, TimeUnit.MINUTES ));
            }
        }, TimeUnit.MILLISECONDS.convert(1, TimeUnit.MINUTES ));
    }

    @Override
    public boolean onDown(MotionEvent e) { return true; }

    @Override
    public void onShowPress(MotionEvent e) {

    }

    @Override
    public boolean onSingleTapUp(MotionEvent e) {
        return false;
    }

    @Override
    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
        // Shift the clipping rect around to force a different part of the backing bitmap to be displayed
        if (bitmapClipRect==null) { return true; }
        if (distanceX > 0) {
            // move to the right
            bitmapClipRect.right += distanceX;
            if (bitmapClipRect.right > bitmapPixelSize.right) {
                bitmapClipRect.right = bitmapPixelSize.right;
            }
            bitmapClipRect.left = bitmapClipRect.right - viewWidth;
        } else {
            bitmapClipRect.left += distanceX;
            if (bitmapClipRect.left < 0) {
                bitmapClipRect.left = 0;
            }
            bitmapClipRect.right = bitmapClipRect.left + viewWidth;
        }
        if (distanceY < 0) {
            // move up - is this counterintuitive or what?
            bitmapClipRect.top += distanceY;
            if (bitmapClipRect.top < 0) {
                bitmapClipRect.top = 0;
            }
            bitmapClipRect.bottom = bitmapClipRect.top + viewHeight;
        } else {
            bitmapClipRect.bottom += distanceY;
            if (bitmapClipRect.bottom > bitmapPixelSize.bottom) {
                bitmapClipRect.bottom = bitmapPixelSize.bottom;
            }
            bitmapClipRect.top = bitmapClipRect.bottom - viewHeight;
        }
        this.invalidate();
        return true;
    }

    @Override
    public void onLongPress(MotionEvent e) {
        // TODO: maybe use this as a way to choose a new centerpoint?
    }

    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        return false;
    }

    /**
     * Capture the current view size for use elsewhere.
     * @param w
     * @param h
     * @param oldw
     * @param oldh
     */
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        viewHeight = h;
        viewWidth = w;
        eventBusProvider.getEventBus().post(new ProductRequestMessage());
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if(this.mDetector.onTouchEvent(event)) {
            return true;
        }
        return super.onTouchEvent(event);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (backingBitmap == null) {
            return;
        }
        Rect destRect = new Rect(0,0,viewWidth,viewHeight);
        if (drawPaint == null) {
            drawPaint = new Paint();
        }
        Paint brush = drawPaint;

        if (bitmapClipRect == null) {
            int viewXOffset = (bitmapPixelSize.width() - viewWidth) / 2;
            int viewYOffset = (bitmapPixelSize.height() - viewHeight) / 2;

            bitmapClipRect = new Rect(viewXOffset, viewYOffset, viewXOffset + viewWidth, viewYOffset + viewHeight);
        }
        canvas.drawBitmap(backingBitmap, bitmapClipRect, destRect, brush);

        // Layer timestamp on graphic
        DateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        if (dataTimestamp != null) {
            String timestamp = fmt.format(dataTimestamp.getTime());
            int color = Color.GREEN;
            if (System.currentTimeMillis()-dataTimestamp.getTime().getTime() >
                    TimeUnit.MILLISECONDS.convert(5,TimeUnit.MINUTES)) {
                color = Color.YELLOW;
            }
            if (System.currentTimeMillis()-dataTimestamp.getTime().getTime() >
                    TimeUnit.MILLISECONDS.convert(15,TimeUnit.MINUTES)) {
                color = Color.RED;
            }
            String productDescription = null;
            if (lastRenderer != null) {
                productDescription = lastRenderer.getProductDescription();
            }
            drawTimestamp(canvas, timestamp, productDescription, color);
        } else {
            drawTimestamp(canvas, "No Data", null, Color.RED);
        }

    }

    /**
     * Paint the data timestamp on the bottom left of the graphic
     *
     * @param canvas object to render to
     * @param text timestamp text to display
     * @param backgroundColor color to use for the timestamp box
     */
    private void drawTimestamp(Canvas canvas, String text, String productDesc, int backgroundColor) {
        Paint timestampPaint = new Paint();
        timestampPaint.setTextSize(scalePixels(20));
        timestampPaint.setColor(backgroundColor);
        Rect stampBounds = new Rect();
        timestampPaint.getTextBounds(text,0,text.length(),stampBounds);
        stampBounds.bottom += scalePixels(20);
        stampBounds.right += scalePixels(20);
        if ((productDesc != null)&&(!productDesc.isEmpty())){
            Rect descBounds = new Rect();
            timestampPaint.setTextSize(scalePixels(10));
            timestampPaint.getTextBounds(productDesc,0,productDesc.length(),descBounds);
            stampBounds.bottom+=descBounds.height();
            descBounds.right += scalePixels(20);
            if (descBounds.right > stampBounds.right) {
                stampBounds.right = descBounds.right;
            }
        }

        RectF roundRect = new RectF(stampBounds);
        int yPos = viewHeight - stampBounds.height() - scalePixels(30);
        roundRect.offsetTo(scalePixels(10),yPos);
        canvas.drawRoundRect(roundRect,scalePixels(10),scalePixels(10),timestampPaint);
        timestampPaint.setColor(Color.parseColor("black"));
        timestampPaint.setStyle(Paint.Style.STROKE);
        float defaultStrokeWidth = timestampPaint.getStrokeWidth();
        timestampPaint.setStrokeWidth(scalePixels(2));
        canvas.drawRoundRect(roundRect,scalePixels(10),scalePixels(10),timestampPaint);
        timestampPaint.setStrokeWidth(defaultStrokeWidth);
        timestampPaint.setStyle(Paint.Style.FILL_AND_STROKE);
        float textYPos = roundRect.bottom - scalePixels(10);
        timestampPaint.setTextSize(scalePixels(20));
        canvas.drawText(text,roundRect.left+scalePixels(10),textYPos,timestampPaint);
        if ((productDesc != null)&&(!productDesc.isEmpty())){
            timestampPaint.setTextSize(scalePixels(10));
            canvas.drawText(productDesc,roundRect.left+scalePixels(10),textYPos-scalePixels(23),timestampPaint);
        }
    }

    /**
     * Convenience function to convert device-independent pixels to correct underlying pixel count
     * @param dp
     * @return
     */
    public int scalePixels (float dp) {
        return (int)((float)dp * displayDensity + 0.5f);
    }


    /**
     * Called from event bus when a new collection of radar data is received for display
     * @param updateProducts
     */
    public void onEvent(NexradUpdate updateProducts) {
        Log.d(TAG, "update received: " + updateProducts.toString());
        this.productDisplay = updateProducts.getUpdateProduct();
        this.selectedLocation = updateProducts.getCenterPoint();
        regenerateBitmap();
        this.invalidate();
    }

    public void onEvent(LocationChangeEvent locationChangeEvent) {
        Log.d(TAG, "location changed: "+locationChangeEvent);
        this.productDisplay = null;
        this.selectedLocation = locationChangeEvent.getCoordinates();
        regenerateBitmap();
        this.invalidate();
    }
    protected void regenerateBitmap() {
        createBackingBitmap();

        Canvas bitmapCanvas = new Canvas(backingBitmap);
        Calendar oldestTimestamp = null;
        if ((productDisplay!=null)&&(!productDisplay.isEmpty())) {
            for (NexradStation station : productDisplay.keySet()) {
                if ((productDisplay.get(station) != null) && (!productDisplay.get(station).isEmpty())) {
                    Calendar thisTimestamp = productDisplay.get(station).get(0).getTimestamp();
                    if ((oldestTimestamp == null) || (thisTimestamp.before(oldestTimestamp))) {
                        oldestTimestamp = thisTimestamp;
                    }
                    plotProduct(bitmapCanvas, bitmapLatLongRect, bitmapPixelSize, productDisplay.get(station).get(0));
                }
            }
            for (NexradStation station : productDisplay.keySet()) {
                boolean stationHasData = (productDisplay.get(station) != null) && (!productDisplay.get(station).isEmpty());
                plotStation(bitmapCanvas, bitmapLatLongRect, bitmapPixelSize, station, stationHasData);
            }
        } else {
            // No products or empty product set
        }
        drawMap(bitmapCanvas, bitmapLatLongRect, bitmapPixelSize);

        drawHomePoint(bitmapCanvas, bitmapLatLongRect, bitmapPixelSize, selectedLocation);
        dataTimestamp = oldestTimestamp;
    }

    private void drawHomePoint(Canvas canvas, LatLongRect latLongRect, Rect pixelSize, LatLongCoordinates location) {
        if (homePointPaint == null) {
            homePointPaint = new Paint();
        }
        Paint brush = homePointPaint;
        Point locationPoint = scaleCoordinate(location, latLongRect, pixelSize);
        canvas.drawCircle(locationPoint.x,locationPoint.y,scalePixels(3),brush);
    }

    private Point scaleCoordinate (LatLongCoordinates coords, LatLongRect coordRect, Rect pixelRect) {
        double latOffset = coords.getLatitude() - coordRect.bottom;
        double longOffset = coords.getLongitude() - coordRect.left;
        int pixY = (int)((latOffset*(double)pixelRect.height())/coordRect.height());
        // Transform to correct Y-scale
        pixY = pixelRect.height() - pixY;
        int pixX = (int)((longOffset*(double)pixelRect.width())/coordRect.width());
        return new Point(pixX,pixY);
    }

    private void plotStation(Canvas canvas, LatLongRect latLongRect, Rect pixelSize,
                             NexradStation station, boolean hasData) {
        if (stationPaint == null) {
            stationPaint = new Paint();
            stationPaint.setTextSize(scalePixels(10));
            stationPaint.setStyle(Paint.Style.FILL);
        }
        Paint brush = stationPaint;
        Point stationPoint = scaleCoordinate(station.getCoords(), latLongRect, pixelSize);

        stationPaint.setColor(Color.DKGRAY);
        if (hasData) {
            canvas.drawCircle(stationPoint.x, stationPoint.y, scalePixels(7), brush);
        } else {
            // Draw special symbol for station that has no data associated with it.
            canvas.drawCircle(stationPoint.x, stationPoint.y, scalePixels(7), brush);
            stationPaint.setColor(Color.WHITE);
            canvas.drawCircle(stationPoint.x, stationPoint.y, scalePixels(5), brush);
            stationPaint.setStyle(Paint.Style.STROKE);
            stationPaint.setColor(Color.RED);
            float defaultStrokeWidth = stationPaint.getStrokeWidth();
            stationPaint.setStrokeWidth(scalePixels(2));
            canvas.drawCircle(stationPoint.x, stationPoint.y, scalePixels(5), brush);
            canvas.drawLine(stationPoint.x-scalePixels(3),stationPoint.y-scalePixels(3),
                    stationPoint.x+scalePixels(3),stationPoint.y+scalePixels(3),stationPaint);
            stationPaint.setStyle(Paint.Style.FILL);
            stationPaint.setStrokeWidth(defaultStrokeWidth);
        }
        stationPaint.setColor(Color.BLACK);
        canvas.drawText(station.getIdentifier(),
                stationPoint.x + scalePixels(12), stationPoint.y + scalePixels(4), brush);
    }


    /**
     * Generate the backing bitmap for the display.
     */
    private void createBackingBitmap() {
        // Determine what actual lat/long region is covered by our data
        bitmapLatLongRect = new LatLongRect(selectedLocation);
        if ((productDisplay!=null)&&(!productDisplay.isEmpty())) {
            for (NexradStation station : productDisplay.keySet()) {
                bitmapLatLongRect.union(station.getCoords());
                List<NexradProduct> radarData = productDisplay.get(station);
                if (!radarData.isEmpty()) {
                    for (NexradProduct radarProduct : radarData) {
                        LatLongRect radarDataRect = computeEnclosingRect(radarProduct);
                        if (radarDataRect != null) {
                            bitmapLatLongRect.union(radarDataRect);
                        }
                    }
                }
            }
        } else {
            // Estimate something reasonable so that when the map is drawn, you can see some
            // surrounding info (such as some state lines). I'm guessing that offsetting 10 degrees lat/long
            // in all directions should do it.
            bitmapLatLongRect.union(selectedLocation.getLongitude()-10.0f,selectedLocation.getLatitude()-10.0f);
            bitmapLatLongRect.union(selectedLocation.getLongitude()+10.0f,selectedLocation.getLatitude()+10.0f);
        }
        // Now we should know the max that we can display.
        // We want this to respect the aspect ratio of the view. It should be a bit larger than the view,
        // with the same aspect ratio. It should also be centered on the "selected location".
        // First make sure that we include the selected location itself...
        bitmapLatLongRect.union((float) selectedLocation.getLongitude(), (float) selectedLocation.getLatitude());
        // Compute the aspect ratio of the display
        double centerX = bitmapLatLongRect.centerX();
        double xDiff = centerX - selectedLocation.getLongitude();
        if (xDiff < 0) {
            bitmapLatLongRect.right += 2*xDiff;
        } else {
            bitmapLatLongRect.left -= 2*xDiff;
        }
        double centerY = bitmapLatLongRect.centerY();
        double yDiff = centerY - selectedLocation.getLatitude();
        if (yDiff < 0) {
            bitmapLatLongRect.top += 2*yDiff;
        } else {
            bitmapLatLongRect.bottom -= 2*yDiff;
        }
        // Respect the aspect ratio of the display
        float targetAspectRatio = (float)viewHeight/(float)viewWidth;
        double bitmapAspectRatio = (bitmapLatLongRect.height()/bitmapLatLongRect.width());
        if (bitmapAspectRatio < targetAspectRatio) {
            // We need to grow the height of the bitmap
            double newExtentHeight = bitmapLatLongRect.width()*targetAspectRatio;
            double heightAdjustment = (newExtentHeight-bitmapLatLongRect.height())/2.0;
            bitmapLatLongRect.top += heightAdjustment;
            bitmapLatLongRect.bottom -= heightAdjustment;
        } else {
            // We need to grow the width of the bitmap
            double newExtentWidth = bitmapLatLongRect.height()/targetAspectRatio;
            double widthAdjustment = (newExtentWidth-bitmapLatLongRect.width())/2.0;
            bitmapLatLongRect.left -= widthAdjustment;
            bitmapLatLongRect.right += widthAdjustment;
        }
        // Compute the bitmap size in pixels
        bitmapPixelSize = new Rect(0,0,viewWidth*2, viewHeight*2);
        // Re-use if size hasn't changed
        if (backingBitmap != null) {
            if ((backingBitmap.getHeight()!=bitmapPixelSize.height())||
                    (backingBitmap.getWidth()!=bitmapPixelSize.width())) {
                // Size difference
                backingBitmap.recycle();
                backingBitmap = null;
                System.gc();
            } else {
                // clear contents
                backingBitmap.eraseColor(Color.WHITE);
            }
        }
        if (backingBitmap == null) {
            backingBitmap = Bitmap.createBitmap(bitmapPixelSize.width(), bitmapPixelSize.height(), Bitmap.Config.ARGB_8888);
        }

    }

    /**
     * Compute the enclosing bounds (lat/long) for the radar data item.
     * @param radarData
     * @return
     */
    private LatLongRect computeEnclosingRect(NexradProduct radarData) {
        NexradRenderer renderer = rendererInventory.getRenderer(radarData.getProductCode());
        if (renderer == null) {
            throw new NexradNowException("no renderer found for product code "+radarData.getProductCode());
        }

        LatLongRect result = renderer.findExtents(radarData);
        return result;
    }

    private void plotProduct(Canvas canvas, final LatLongRect latLongRect, final Rect pixelSize, NexradProduct product) {
        LatLongScaler scaler = new LatLongScaler() {
            @Override
            public PointF scaleLatLong(LatLongCoordinates coordinates) {
                return new PointF(scaleCoordinate(coordinates, latLongRect, pixelSize));
            }
        };
        if (productPaint == null) {
            productPaint = new Paint();
        }
        NexradRenderer renderer = rendererInventory.getRenderer(product.getProductCode());
        renderer.renderToCanvas(canvas, product, productPaint, scaler);
        lastRenderer = renderer;
    }

    private void drawMap(Canvas canvas, LatLongRect latLongRect, Rect pixelSize) {
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
                if (!shapeExcluded(polygon, latLongRect)) {
                    includedShapes++;
                    for (int part = 0; part < polygon.getNumberOfParts(); part++) {
                        PointData[] points = polygon.getPointsOfPart(part);
                        plotPolygonPoints(canvas, latLongRect, pixelSize, points);
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

    private void plotPolygonPoints(Canvas canvas, LatLongRect latLongRect, Rect pixelSize, PointData[] points) {
        if (mapPaint == null) {
            mapPaint = new Paint();
            mapPaint.setStrokeWidth(scalePixels(2));
            mapPaint.setColor(Color.GRAY);
            mapPaint.setAlpha(50);
        }
        Paint brush = mapPaint;
        Point from = null;
        Point to = null;
        for (PointData eachSrcPoint : points) {
            to = scaleCoordinate(new LatLongCoordinates(eachSrcPoint.getY(), eachSrcPoint.getX()), latLongRect, pixelSize);
            if (from != null) {
                canvas.drawLine(from.x,from.y,to.x,to.y,brush);
            }
            from = to;
        }
    }

}
