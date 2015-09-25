package com.nexradnow.android.model;

/**
 * Created by hobsonm on 9/14/15.
 *
 * Emitted when the app wants to re-center on a new location
 */
public class LocationChangeEvent {
    protected LatLongCoordinates coordinates;

    public LocationChangeEvent(LatLongCoordinates coordinates) {
        this.coordinates = coordinates;
    }

    public LatLongCoordinates getCoordinates() {
        return coordinates;
    }

    public void setCoordinates(LatLongCoordinates coordinates) {
        this.coordinates = coordinates;
    }
}
