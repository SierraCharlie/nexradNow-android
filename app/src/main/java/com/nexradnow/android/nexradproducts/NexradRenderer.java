package com.nexradnow.android.nexradproducts;

import android.graphics.Bitmap;
import com.nexradnow.android.model.LatLongRect;
import com.nexradnow.android.model.LatLongScaler;
import com.nexradnow.android.model.NexradProduct;

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
     * @param bitmap bitmap
     * @param scaler translation between lat/long values and bitmap pixel coordinates
     */
    void renderToBitmap(NexradProduct product, Bitmap bitmap, LatLongScaler scaler);

    /**
     * Identify the area covered by a product
     * @param product
     * @return lat/long area covered by the product
     */
    LatLongRect findExtents(NexradProduct product);
}
