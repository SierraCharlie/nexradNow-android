package com.nexradnow.android.model;

import java.io.Serializable;

/**
 * Created by hobsonm on 9/15/15.
 */
public class NexradStation implements Serializable {

    protected String identifier;
    protected LatLongCoordinates coords;
    protected String location;

    public NexradStation(String identifier, LatLongCoordinates coords, String location) {
        this.identifier = identifier;
        this.coords = coords;
        this.location = location;
    }

    public String getIdentifier() {
        return identifier;
    }

    public LatLongCoordinates getCoords() {
        return coords;
    }

    public String getLocation() { return location; }

    @Override
    public String toString() {
        return "NexradStation{" +
                "identifier='" + identifier + '\'' +
                ", coords=" + coords +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof NexradStation) {
            return this.identifier.equals(((NexradStation)o).identifier);
        }
        return super.equals(o);
    }

    @Override
    public int hashCode() {
        return identifier.hashCode();
    }
}
