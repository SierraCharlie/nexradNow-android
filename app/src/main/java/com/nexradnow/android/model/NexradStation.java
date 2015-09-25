package com.nexradnow.android.model;

import java.io.Serializable;

/**
 * Created by hobsonm on 9/15/15.
 */
public class NexradStation implements Serializable {

    protected String identifier;
    protected LatLongCoordinates coords;

    public NexradStation(String identifier, LatLongCoordinates coords) {
        this.identifier = identifier;
        this.coords = coords;
    }

    public String getIdentifier() {
        return identifier;
    }

    public LatLongCoordinates getCoords() {
        return coords;
    }

    @Override
    public String toString() {
        return "NexradStation{" +
                "identifier='" + identifier + '\'' +
                ", coords=" + coords +
                '}';
    }
}
