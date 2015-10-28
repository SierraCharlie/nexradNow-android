package com.nexradnow.android.model;

import android.graphics.Bitmap;

/**
 * Created by hobsonm on 10/26/15.
 */
public class BitmapEvent {
    protected LatLongRect region;
    protected Bitmap bitmap;

    public BitmapEvent(Bitmap bitmap, LatLongRect region) {
        this.bitmap = bitmap;
        this.region = region;
    }

    public Bitmap getBitmap() {
        return bitmap;
    }

    public void setBitmap(Bitmap bitmap) {
        this.bitmap = bitmap;
    }

    public LatLongRect getRegion() {
        return region;
    }

    public void setRegion(LatLongRect region) {
        this.region = region;
    }
}
