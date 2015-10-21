package com.nexradnow.android.nexradproducts;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.util.Log;
import com.nexradnow.android.exception.NexradNowException;
import com.nexradnow.android.model.AppMessage;
import com.nexradnow.android.model.LatLongCoordinates;
import com.nexradnow.android.model.LatLongRect;
import com.nexradnow.android.model.LatLongScaler;
import com.nexradnow.android.model.NexradProduct;
import roboguice.RoboGuice;
import ucar.ma2.ArrayFloat;
import ucar.nc2.Dimension;
import ucar.nc2.NetcdfFile;
import ucar.nc2.Variable;

import java.util.List;

/**
 * Created by hobsonm on 10/15/15.
 */
public class P38crRenderer extends NexradGridRenderer {

    @Override
    public String getProductCode() {
        return "p38cr";
    }

    @Override
    public String getProductDescription() {
        return "Composite Reflectivity - 248 nmi range";
    }

}
