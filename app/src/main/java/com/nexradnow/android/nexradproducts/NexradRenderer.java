package com.nexradnow.android.nexradproducts;

import android.graphics.Canvas;
import android.graphics.Paint;
import com.nexradnow.android.exception.NexradNowException;
import com.nexradnow.android.model.LatLongRect;
import com.nexradnow.android.model.LatLongScaler;
import com.nexradnow.android.model.NexradProduct;
import com.nexradnow.android.model.NexradStation;

import java.util.Collection;

/**
 * Interface for product renderers that understand how to paint a Nexrad product into
 * a bitmap object.
 *
 * Created by hobsonm on 10/14/15.
 */
public interface NexradRenderer {
    /**
     * Return the simple product code (e.g. "p38cr") that this renderer is built for
     * @return short Nexrad product code
     */
    String getProductCode();

    /**
     * Return a longer description of the product that is human-readable
     * @return human-readable description of the product
     */
    String getProductDescription();

    /**
     * Paint the product into the supplied bitmap.
     * @param product product to be painted
     * @param canvas canvas to paint on (could be a bitmap)
     * @param scaler translation between lat/long values and bitmap pixel coordinates
     * @param paint Paint object to use when rendering (so the caller can cache/reuse)
     * @param minFeatureSize minimum item size to render (in pixels)
     */
    void renderToCanvas( Canvas canvas, NexradProduct product, Paint paint, LatLongScaler scaler, int minFeatureSize) throws NexradNowException;

    /**
     * Paint the product into the supplied bitmap, recognizing other station boundaries. Only plot points
     * if the owning station is the closest station.
     *
     * @param product product to be painted
     * @param canvas canvas to paint on (could be a bitmap)
     * @param scaler translation between lat/long values and bitmap pixel coordinates
     * @param paint Paint object to use when rendering (so the caller can cache/reuse)
     * @param minFeatureSize minimum item size to render (in pixels)
     * @param safeDistance any point within this distance is safe to plot without checking other stations
     * @param dataOwner this is the owning station for this data set
     * @param otherStations these are all other stations with valid data
     * @throws NexradNowException
     */
    void renderToCanvas(Canvas canvas, NexradProduct product, Paint paint, LatLongScaler scaler, int minFeatureSize,
                        double safeDistance, NexradStation dataOwner, Collection<NexradStation> otherStations)
            throws NexradNowException;

    /**
     * Identify the area covered by a product
     * @param product
     * @return lat/long area covered by the product
     */
    LatLongRect findExtents(NexradProduct product) throws NexradNowException;
}
