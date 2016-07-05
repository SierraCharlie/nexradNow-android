package com.nexradnow.android.model;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * Created by hobsonm on 9/16/15.
 */
public class NexradUpdate implements Serializable {
    protected Map<NexradStation,List<NexradProduct>> updateProduct;
    protected LatLongCoordinates centerPoint;

    public NexradUpdate(Map<NexradStation, List<NexradProduct>> updateProduct, LatLongCoordinates centerPoint) {
        this.updateProduct = updateProduct;
        this.centerPoint = centerPoint;
    }

    public Map<NexradStation, List<NexradProduct>> getUpdateProduct() {
        return updateProduct;
    }

    public void setUpdateProduct(Map<NexradStation, List<NexradProduct>> updateProduct) {
        this.updateProduct = updateProduct;
    }

    public LatLongCoordinates getCenterPoint() {
        return centerPoint;
    }

    public void setCenterPoint(LatLongCoordinates centerPoint) {
        this.centerPoint = centerPoint;
    }
}
