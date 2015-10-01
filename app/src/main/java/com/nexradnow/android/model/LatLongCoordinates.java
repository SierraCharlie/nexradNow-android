package com.nexradnow.android.model;

import android.os.Parcel;
import android.os.Parcelable;

import java.io.Serializable;

/**
 * Created by hobsonm on 9/14/15.
 */
public class LatLongCoordinates implements Serializable {
    protected double latitude;
    protected double longitude;

    public LatLongCoordinates(double latitude, double longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public double distanceTo(LatLongCoordinates other) {
        // =ACOS(SIN(Lat1)*SIN(Lat2)+COS(Lat1)*COS(Lat2)*COS(Lon2-Lon1))*6371
        double distanceKm = Math.acos(
                Math.sin(Math.toRadians(latitude))*Math.sin(Math.toRadians(other.latitude))
                +Math.cos(Math.toRadians(latitude))*Math.cos(Math.toRadians(other.latitude))*Math.cos(Math.toRadians(other.longitude-longitude))
                )*6371;
        return Math.abs(distanceKm);
    }

    /**
     * Shift this point by the specified amounts
     * @param latitudeChange
     * @param longitudeChange
     */
    public void translate(double latitudeChange, double longitudeChange) {
        this.latitude += latitudeChange;
        this.longitude += longitudeChange;
    }

    @Override
    public String toString() {
        return "LatLongCoordinates{" +
                "latitude=" + latitude +
                ", longitude=" + longitude +
                '}';
    }
}
