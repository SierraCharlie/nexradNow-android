package com.nexradnow.android.nexradproducts;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.Rect;
import com.nexradnow.android.exception.NexradNowException;
import com.nexradnow.android.model.LatLongCoordinates;
import com.nexradnow.android.model.LatLongRect;
import com.nexradnow.android.model.LatLongScaler;
import com.nexradnow.android.model.NexradProduct;
import ucar.ma2.ArrayFloat;
import ucar.nc2.Dimension;
import ucar.nc2.NetcdfFile;
import ucar.nc2.Variable;

import java.util.List;

/**
 * Created by hobsonm on 10/15/15.
 */
public class P37crRenderer extends NexradGridRenderer {

    @Override
    public String getProductCode() {
        return "p37cr";
    }

    @Override
    public String getProductDescription() {
        return "Composite Reflectivity - 124 nm range";
    }

}
