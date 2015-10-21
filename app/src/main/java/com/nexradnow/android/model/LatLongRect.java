package com.nexradnow.android.model;

import android.graphics.RectF;
import android.os.Parcelable;

import java.io.Serializable;

/**
 * Manipulate a region defined by latitude and longitudes.
 *
 * Created by hobsonm on 9/29/15.
 */
public class LatLongRect implements Serializable {

    public double top;
    public double bottom;
    public double left;
    public double right;

    public  LatLongRect(double left, double top, double right, double bottom) {
        this.top = top;
        this.bottom = bottom;
        this.left = left;
        this.right = right;
    }

    public LatLongRect(LatLongCoordinates coords) {
        this.top = coords.getLatitude();
        this.bottom = coords.getLatitude();
        this.left = coords.getLongitude();
        this.right = coords.getLongitude();
    }

    public boolean contains (LatLongCoordinates coords) {
        boolean result = true;
        if ((coords.getLatitude()>this.top)||
                (coords.getLatitude()<this.bottom)||
                (coords.getLongitude()<this.left)||
                (coords.getLongitude()>this.right)) {
            result = false;
        }
        return result;
    }

    public LatLongRect union(double x, double y) {
        if (y > top) {
            top = y;
        } else if (y < bottom) {
            bottom = y;
        }
        if (x < left) {
            left = x;
        } else if (x > right) {
            right = x;
        }
        return this;
    }


    public LatLongRect union(LatLongCoordinates coords) {
        return union(coords.getLongitude(),coords.getLatitude());
    }

    public LatLongRect union(LatLongRect latLongRect) {
        return union(latLongRect.left, latLongRect.top).union(latLongRect.right,latLongRect.bottom);
    }

    public double centerX() {
        return left+(right-left)/2.0;
    }

    public double centerY() {
        return bottom+(top-bottom)/2.0;
    }

    public double height() {
        return (top-bottom);
    }

    public double width() {
        return (right-left);
    }

    @Override
    public String toString() {
        return "LatLongRect{" +
                "bottom=" + bottom +
                ", top=" + top +
                ", left=" + left +
                ", right=" + right +
                '}';
    }

}
