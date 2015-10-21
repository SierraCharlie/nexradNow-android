package com.nexradnow.android.model;

import android.graphics.PointF;

/**
 * Created by hobsonm on 10/14/15.
 */
public interface LatLongScaler {
    PointF scaleLatLong(LatLongCoordinates coordinates);
    LatLongCoordinates scalePoint(PointF point);
    float distanceForPixels(int pixelCount);
    int pixelsForDistance(float distanceMeters);
}
