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
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.support.v4.view.GestureDetectorCompat;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import com.nexradnow.android.app.NexradApp;
import com.nexradnow.android.app.NexradView;
import com.nexradnow.android.exception.NexradNowException;
import com.nexradnow.android.model.AppMessage;
import com.nexradnow.android.model.BitmapEvent;
import com.nexradnow.android.model.LatLongCoordinates;
import com.nexradnow.android.model.LatLongRect;
import com.nexradnow.android.model.LocationChangeEvent;
import com.nexradnow.android.model.NexradProduct;
import com.nexradnow.android.model.NexradStation;
import com.nexradnow.android.model.NexradUpdate;
import com.nexradnow.android.nexradproducts.NexradRenderer;
import com.nexradnow.android.nexradproducts.RendererInventory;

import java.io.Serializable;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Iterator;
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
public class RadarBitmapView extends View implements GestureDetector.OnGestureListener,
    ScaleGestureDetector.OnScaleGestureListener {

    public static String TAG = "RadarBitmapView";


    // Helper to detect gestures
    private GestureDetectorCompat mDetector;
    private ScaleGestureDetector sDetector;

    /**
     * Collection of Nexrad data that is to be displayed
     */
    private Map<NexradStation, List<NexradProduct>> productDisplay;

    private RendererInventory rendererInventory;
    /**
     * Computed oldest timestamp of data on display
     */
    private Calendar dataTimestamp;

    /**
     * Current (last) message shown on display
     */
    private AppMessage appMessage;

    /**
     * Time when last message was generated
     */
    private long appMessageTimestamp;

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
     * Point that display is currently centered on.
     */
    protected Point viewCenter;

    /**
     * The current display density, used to scale pixel sizes
     */
    protected float displayDensity;

    // Paint objects for various operations
    protected Paint drawPaint; // onDraw


    protected String productDescription;

    protected boolean inhibitRegenerate = false;

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
        Log.d(TAG,"creating view instance");
        setSaveEnabled(true);
        rendererInventory = new RendererInventory();
        mDetector = new GestureDetectorCompat(getContext(),this);
        sDetector = new ScaleGestureDetector(getContext(),this);
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
        // Shift the center point as needed
        if ( viewCenter != null) {
            viewCenter.x += distanceX;
            viewCenter.y += distanceY;
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
        if (selectedLocation!=null) {
            if ((viewWidth > 0)&&(viewHeight>0)) {
                regenerateBitmap(null);
                this.invalidate();
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        boolean retval = this.sDetector.onTouchEvent(event);
        retval = this.mDetector.onTouchEvent(event) || retval;
        retval = super.onTouchEvent(event) || retval;
        return retval;
    }

    protected boolean scaling = false;
    protected float cumulativeScale = 1;
    @Override
    public boolean onScale(ScaleGestureDetector detector) {
        // resize bitmap clipping rect and redraw
        cumulativeScale *= detector.getScaleFactor();
        if (cumulativeScale < 0.5f) {
            cumulativeScale = 0.5f;
        }
        this.invalidate();
        return true;
    }

    @Override
    public boolean onScaleBegin(ScaleGestureDetector detector) {
        // set SCALING in progress
        cumulativeScale = 1.0f;
        scaling = true;
        return true;
    }

    @Override
    public void onScaleEnd(ScaleGestureDetector detector) {
        // re-render bitmap at new scale factor
        scaling = true;
        if ((viewCenter != null)&&(bitmapLatLongRect!=null)&&(bitmapPixelSize!=null)) {
            LatLongCoordinates centerLatLong = scalePoint(viewCenter, bitmapLatLongRect, bitmapPixelSize);
            float latSpan = (float) bitmapLatLongRect.height() * 1.0f / cumulativeScale;
            float longSpan = (float) bitmapLatLongRect.width() * 1.0f / cumulativeScale;
            LatLongRect scaledLatLongRect = new LatLongRect(0, 0, 0, 0);
            scaledLatLongRect.left = centerLatLong.getLongitude() - longSpan / 2.0;
            scaledLatLongRect.right = scaledLatLongRect.left + longSpan;
            scaledLatLongRect.bottom = centerLatLong.getLatitude() - latSpan / 2.0;
            scaledLatLongRect.top = scaledLatLongRect.bottom + latSpan;
            viewCenter = scaleCoordinate(centerLatLong, bitmapLatLongRect, bitmapPixelSize);
            regenerateBitmap(scaledLatLongRect);
        } else {
            regenerateBitmap(null);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (backingBitmap != null) {
            renderBitmap(canvas);
        }

        if (appMessage != null) {
            if (System.currentTimeMillis()-appMessageTimestamp > TimeUnit.MILLISECONDS.convert(10,TimeUnit.SECONDS)) {
                appMessage = null;
            } else {
                drawMessage(canvas, appMessage);
            }
        }


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
            String productCode = null;
            Iterator<Map.Entry<NexradStation, List<NexradProduct>>> iterator = productDisplay.entrySet().iterator();
            while (iterator.hasNext()&&(productCode==null)) {
                try {
                    productCode = iterator.next().getValue().get(0).getProductCode();
                    if (productCode != null) {
                        break;
                    }
                } catch (Exception ex) {
                    productCode = null;
                }
            }
            if (productCode != null) {
                productDescription = rendererInventory.getRenderer(productCode).getProductDescription();
                drawTimestamp(canvas, timestamp, productDescription, color);
            }
        } else {
            drawTimestamp(canvas, "No Data", null, Color.RED);
        }

    }

    private void renderBitmap(Canvas canvas) {
        Rect destRect = new Rect(0,0,viewWidth,viewHeight);
        if (drawPaint == null) {
            drawPaint = new Paint();
        }
        Paint brush = drawPaint;


        if (viewCenter == null) {
            viewCenter = new Point(bitmapPixelSize.centerX(), bitmapPixelSize.centerY());
        }
        Rect bitmapClipRect = new Rect();
        bitmapClipRect.left = viewCenter.x - viewWidth/2;
        bitmapClipRect.right = bitmapClipRect.left+viewWidth;
        bitmapClipRect.top = viewCenter.y - viewHeight/2;
        bitmapClipRect.bottom = bitmapClipRect.top + viewHeight;

        if (scaling) {
            float sizeFactor = 1.0f/cumulativeScale;
            int newHeight = (int)(bitmapClipRect.height() * sizeFactor);
            int newWidth = (int)(bitmapClipRect.width() * sizeFactor);
            int heightAdjust = (newHeight - bitmapClipRect.height())/2;
            int widthAdjust = (newWidth - bitmapClipRect.width())/2;
            bitmapClipRect.left -= widthAdjust;
            bitmapClipRect.right += widthAdjust;
            bitmapClipRect.top -= heightAdjust;
            bitmapClipRect.bottom += heightAdjust;
        }
        if (!scaling) {
            if (bitmapClipRect.left < 0) {
                bitmapClipRect.left = 0;
                bitmapClipRect.right = viewWidth;
            }
            if (bitmapClipRect.right > bitmapPixelSize.width()) {
                bitmapClipRect.right = bitmapPixelSize.width();
                bitmapClipRect.left = bitmapClipRect.right - viewWidth;
            }
            if (bitmapClipRect.top < 0) {
                bitmapClipRect.top = 0;
                bitmapClipRect.bottom = viewHeight;
            }
            if (bitmapClipRect.bottom > bitmapPixelSize.height()) {
                bitmapClipRect.bottom = bitmapPixelSize.height();
                bitmapClipRect.top = bitmapClipRect.bottom - viewHeight;
            }
        }
        viewCenter.x = bitmapClipRect.centerX();
        viewCenter.y = bitmapClipRect.centerY();

        canvas.drawBitmap(backingBitmap, bitmapClipRect, destRect, brush);
    }

    private void drawMessage(Canvas canvas, AppMessage message) {
        String text = message.getMessage();
        int color = Color.GRAY;
        Paint messagePaint = new Paint();
        messagePaint.setColor(color);
        Rect textBounds = new Rect();
        messagePaint.setTextSize(scalePixels(15));
        messagePaint.getTextBounds(text,0,text.length(),textBounds);
        textBounds.bottom += scalePixels(10);
        textBounds.right += scalePixels(10);
        textBounds.offsetTo(scalePixels(10),scalePixels(10));
        canvas.drawRoundRect(new RectF(textBounds), scalePixels(5), scalePixels(5), messagePaint);
        messagePaint.setColor(Color.BLACK);
        canvas.drawText(text, (float)(textBounds.left+scalePixels(5)),(float)(textBounds.bottom-scalePixels(5)),messagePaint);
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
        Log.d(TAG, "update received: " + updateProducts.toString()+" -> this:"+this.toString());
        appMessage = new AppMessage("Rendering", AppMessage.Type.INFO);
        appMessageTimestamp = System.currentTimeMillis();
        this.invalidate();
        this.productDisplay = updateProducts.getUpdateProduct();
        this.selectedLocation = updateProducts.getCenterPoint();
        regenerateBitmap(null);
        appMessage = null;
        this.invalidate();
    }

    public void onEvent(LocationChangeEvent locationChangeEvent) {
        Log.d(TAG, "location changed: "+locationChangeEvent);
        if ((selectedLocation!=null)&&
                (locationChangeEvent.getCoordinates().distanceTo(selectedLocation)>100)) {
            this.productDisplay = null;
        }
        this.selectedLocation = locationChangeEvent.getCoordinates();
        regenerateBitmap(null);
        this.invalidate();
    }

    public void onEvent(AppMessage appMessage) {
        Log.d(TAG,"appMessage: "+appMessage.getType().toString()+" = "+appMessage.getMessage());
        this.appMessage = appMessage;
        this.appMessageTimestamp = System.currentTimeMillis();
        this.invalidate();
    }


    public void onEvent(BitmapEvent bitmapEvent) {
        Log.d(TAG,"received bitmap from renderer!");
        releaseBitmap();
        backingBitmap = bitmapEvent.getBitmap();
        inhibitRegenerate = false;
        scaling = false;
        appMessage = null;
        this.invalidate();
    }

    public void releaseBitmap() {
        if (backingBitmap != null) {
            backingBitmap.recycle();
            backingBitmap = null;
            System.gc();
        }
    }

    protected void regenerateBitmap(LatLongRect bitmapRegion) {
        if (inhibitRegenerate) {
            return;
        }

        if ((viewWidth <= 0)||(viewHeight <= 0)) {
            return;
        }
        inhibitRegenerate = true;
        bitmapLatLongRect = computeBitmapLatLongRect(bitmapRegion);
        // Request creation via activity
        if (getContext() instanceof NexradView) {
            Log.d(TAG,"successfully got parent activity");
            NexradView nexradView = (NexradView)getContext();
            if (nexradView.getApplication() instanceof NexradApp) {
                NexradApp nexradApp = (NexradApp)nexradView.getApplication();
                bitmapPixelSize = new Rect(0,0,viewWidth*2,viewHeight*2);
                nexradApp.startRendering(bitmapLatLongRect,
                        bitmapPixelSize, new NexradUpdate(productDisplay,selectedLocation),
                        displayDensity
                        );
            }
        }

        Calendar oldestTimestamp = null;
        if ((productDisplay!=null)&&(!productDisplay.isEmpty())) {
            // Compute smallest inter-station distance
            Collection<NexradStation> validStations = findStationsWithData(productDisplay);
            for (NexradStation station : validStations) {
                if (!bitmapLatLongRect.contains(station.getCoords())) {
                    continue;
                }
                if ((productDisplay.get(station) != null) && (!productDisplay.get(station).isEmpty())) {
                    Calendar thisTimestamp = productDisplay.get(station).get(0).getTimestamp();
                    if ((oldestTimestamp == null) || (thisTimestamp.before(oldestTimestamp))) {
                        oldestTimestamp = thisTimestamp;
                    }
                }
            }
        }
        dataTimestamp = oldestTimestamp;

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
     * Given a point in lat/long coordinates, and a pixel area of size pixelRect that covers
     * the region described by lat/long region coordRect, compute the pixel location of this
     * point in that space.
     * @param coords
     * @param coordRect
     * @param pixelRect
     * @return
     */
    private Point scaleCoordinate (LatLongCoordinates coords, LatLongRect coordRect, Rect pixelRect) {
        double latOffset = coords.getLatitude() - coordRect.bottom;
        double longOffset = coords.getLongitude() - coordRect.left;
        int pixY = (int)((latOffset*(double)pixelRect.height())/coordRect.height());
        // Transform to correct Y-scale
        pixY = pixelRect.height() - pixY;
        int pixX = (int)((longOffset*(double)pixelRect.width())/coordRect.width());
        return new Point(pixX,pixY);
    }

    private LatLongCoordinates scalePoint(Point point, LatLongRect coordRect, Rect pixelRect) {
        int xOffset = point.x - pixelRect.left;
        int yOffset = point.y - pixelRect.top;
        double longValue = coordRect.left + (float)xOffset/(float)pixelRect.width()* coordRect.width();
        double latValue = coordRect.top - (float)yOffset/(float)pixelRect.height() * coordRect.height();
        return new LatLongCoordinates(latValue, longValue);
    }



    /**
     * Determine the adjusted LatLong coverage of the bitmap that will back the view. The actual span
     * will almost certainly be different from the desired span due to display geometries and data coverage.
     *
     * @param span
     * @return the best fit lat long dimensions that the bitmap should cover
     */
    protected LatLongRect computeBitmapLatLongRect (LatLongRect span) {
        LatLongRect computedLatLongRect;
        if (span==null) {
            // Determine what actual lat/long region is covered by our data
            computedLatLongRect = new LatLongRect(selectedLocation);
            if ((productDisplay != null) && (!productDisplay.isEmpty())) {
                for (NexradStation station : productDisplay.keySet()) {
                    computedLatLongRect.union(station.getCoords());
                    List<NexradProduct> radarData = productDisplay.get(station);
                    if (!radarData.isEmpty()) {
                        for (NexradProduct radarProduct : radarData) {
                            LatLongRect radarDataRect = computeEnclosingRect(radarProduct);
                            if (radarDataRect != null) {
                                computedLatLongRect.union(radarDataRect);
                            }
                        }
                    }
                }
            } else {
                // Estimate something reasonable so that when the map is drawn, you can see some
                // surrounding info (such as some state lines). I'm guessing that offsetting 10 degrees lat/long
                // in all directions should do it.
                computedLatLongRect.union(selectedLocation.getLongitude() - 10.0f, selectedLocation.getLatitude() - 10.0f);
                computedLatLongRect.union(selectedLocation.getLongitude() + 10.0f, selectedLocation.getLatitude() + 10.0f);
            }
            // Now we should know the max that we can display.
            // We want this to respect the aspect ratio of the view. It should be a bit larger than the view,
            // with the same aspect ratio. It should also be centered on the "selected location".
            // First make sure that we include the selected location itself...
            computedLatLongRect.union((float) selectedLocation.getLongitude(), (float) selectedLocation.getLatitude());
            // Now re-center
            double centerX = computedLatLongRect.centerX();
            double xDiff = centerX - selectedLocation.getLongitude();
            if (xDiff < 0) {
                computedLatLongRect.right += 2*xDiff;
            } else {
                computedLatLongRect.left -= 2*xDiff;
            }
            double centerY = computedLatLongRect.centerY();
            double yDiff = centerY - selectedLocation.getLatitude();
            if (yDiff < 0) {
                computedLatLongRect.top += 2*yDiff;
            } else {
                computedLatLongRect.bottom -= 2*yDiff;
            }
        } else {
            // If we've been explicitly told which area to map, then...
            computedLatLongRect = span;
        }
        // Compute the aspect ratio of the display
        // Respect the aspect ratio of the display
        float targetAspectRatio = (float)viewHeight/(float)viewWidth;
        double bitmapAspectRatio = (computedLatLongRect.height()/computedLatLongRect.width());
        if (bitmapAspectRatio < targetAspectRatio) {
            // We need to grow the height of the bitmap
            double newExtentHeight = computedLatLongRect.width()*targetAspectRatio;
            double heightAdjustment = (newExtentHeight-computedLatLongRect.height())/2.0;
            computedLatLongRect.top += heightAdjustment;
            computedLatLongRect.bottom -= heightAdjustment;
        } else {
            // We need to grow the width of the bitmap
            double newExtentWidth = computedLatLongRect.height()/targetAspectRatio;
            double widthAdjustment = (newExtentWidth-computedLatLongRect.width())/2.0;
            computedLatLongRect.left -= widthAdjustment;
            computedLatLongRect.right += widthAdjustment;
        }
        return computedLatLongRect;
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
        LatLongRect result = null;
        try {
            result = renderer.findExtents(radarData);
        } catch (Exception ex) {
            Log.e(TAG,"error computing enclosing rect for "+radarData.getStation().getIdentifier(),ex);
            result = null;
        }
        return result;
    }



    private Bundle writeBundle() {
        Bundle savedState = new Bundle();
        if (dataTimestamp != null) {
            savedState.putSerializable("timestamp", dataTimestamp);
        }
        if (productDescription != null) {
            savedState.putString("productDescription", productDescription);
        }
        if (productDisplay != null) {
            savedState.putSerializable("productDisplay", (Serializable) productDisplay);
        }
        if (viewCenter != null) {
            savedState.putInt("viewCenterX", viewCenter.x);
            savedState.putInt("viewCenterY", viewCenter.y);
        }
        if (selectedLocation != null) {
            savedState.putSerializable("selectedLocation", selectedLocation);
        }

        return savedState;
    }

    private void readBundle(Bundle savedState) {
        selectedLocation = (LatLongCoordinates)savedState.getSerializable("selectedLocation");
        dataTimestamp = (Calendar)savedState.getSerializable("timestamp");
        productDisplay = (Map)savedState.getSerializable("productDisplay");
        productDescription = savedState.getString("productDescription");
        if ((selectedLocation!=null)&&(productDisplay!=null)&&(!productDisplay.isEmpty())) {
            if ((viewWidth > 0)&&(viewHeight>0)) {
                regenerateBitmap(null);
                this.invalidate();
            }
        }
        int x = savedState.getInt("viewCenter.x", -1);
        if (x != -1) {
            viewCenter = new Point();
            viewCenter.x = x;
            int y = savedState.getInt("viewCenter.y", -1);
            if (y != -1) {
                viewCenter.y = y;
            }
        }
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        Log.e(TAG,"onSaveInstanceState()");
        Parcelable state = super.onSaveInstanceState();
        Bundle localState = writeBundle();
        localState.putParcelable("superState", state);
        return localState;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        Log.e(TAG,"onRestoreInstanceState()");
        if (state instanceof Bundle) {
            readBundle((Bundle)state);
            state = ((Bundle) state).getParcelable("superState");
        }
        super.onRestoreInstanceState(state);
    }

}
