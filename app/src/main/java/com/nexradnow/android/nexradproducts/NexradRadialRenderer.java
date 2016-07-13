package com.nexradnow.android.nexradproducts;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import com.nexradnow.android.exception.NexradNowException;
import com.nexradnow.android.model.LatLongCoordinates;
import com.nexradnow.android.model.LatLongScaler;
import com.nexradnow.android.model.NexradProduct;
import com.nexradnow.android.model.NexradStation;
import ucar.ma2.Array;
import ucar.ma2.ArrayFloat;
import ucar.nc2.NetcdfFile;
import ucar.nc2.Variable;

import java.util.Collection;

/**
 * Created by hobsonm on 10/16/15.
 */
public abstract class NexradRadialRenderer extends NexradGridRenderer {

    protected static String TAG = "NxRadRenderer";

    @Override
    public abstract String getProductCode();

    @Override
    public abstract String getProductDescription();

    /**
     * Compute the coordinates of a point that is a given distance (in meters) and angle (in degrees) from
     * the origin point. From: http://stackoverflow.com/questions/7222382/get-lat-long-given-current-point-distance-and-bearing
     *
     * @param origin point of origin
     * @param heading heading, clockwise from north, in degrees
     * @param meterDistance distance from origin, in meters
     * @return
     */
    protected LatLongCoordinates computeRadialFine(LatLongCoordinates origin, double heading, double meterDistance) {
        double earthRadiusMeters = 6371000.0;
        /*
        lat2 = asin(sin(lat1)*cos(d/R) + cos(lat1)*sin(d/R)*cos(θ))

lon2 = lon1 + atan2(sin(θ)*sin(d/R)*cos(lat1), cos(d/R)−sin(lat1)*sin(lat2))
         */
        double headingRad = Math.toRadians(heading);
        double latOriginRad = Math.toRadians(origin.getLatitude());
        double lonOriginRad = Math.toRadians(origin.getLongitude());
        double latDestination = Math.asin(Math.sin(latOriginRad)*Math.cos(meterDistance/earthRadiusMeters)+
        Math.cos(latOriginRad)*Math.sin(meterDistance/earthRadiusMeters)*Math.cos(headingRad));
        double lonDestination = lonOriginRad + Math.atan2(
          Math.sin(headingRad)*Math.sin(meterDistance/earthRadiusMeters)*Math.cos(latOriginRad),
                Math.cos(meterDistance/earthRadiusMeters)-Math.sin(latOriginRad)*Math.sin(latDestination)
        );
        LatLongCoordinates result = new LatLongCoordinates(Math.toDegrees(latDestination),Math.toDegrees(lonDestination));
        return result;
    }

    protected LatLongCoordinates computeRadial(LatLongCoordinates origin, float heading, float meterDistance) {
        double cosHdg = Math.cos(Math.toRadians(heading));
        double sinHdg = Math.sin(Math.toRadians(heading));
        double cosLat = Math.cos(Math.toRadians(origin.getLatitude()));
        double meterLatOffset = cosHdg * meterDistance;
        double meterLonOffset = sinHdg * meterDistance;
        double newLat = origin.getLatitude()+meterLatOffset/111132;
        double newLong = origin.getLongitude()+meterLonOffset/(111132*cosLat);
        return new LatLongCoordinates(newLat,newLong);
    }


    public void renderToCanvas(Canvas canvas, NexradProduct product, Paint paint, LatLongScaler scaler, int minFeatureSize,
                               double safeDistance, NexradStation dataOwner, Collection<NexradStation> otherStations)
            throws NexradNowException {
        // General flow:
        // Analyze data to find radial array
        // Determine angle values and range values
        // Iterate over angle/gate array and build polygons with values (use 0 as origin). Paint each polygon as
        // it is built

        Paint productPaint = paint;
        byte[] rawData = product.getBinaryData();
        if (productPaint == null) {
            productPaint = new Paint();
        }
        try {
            NetcdfFile netcdfFile = NetcdfFile.openInMemory("sn.last", rawData);
            float latOrigin = Float.NaN;
            float lonOrigin = Float.NaN;
            float gateDistancesMeters[] = null;
            float azimuthAngles[] = null;
            ArrayFloat.D2 radialArray = null;
            for (Variable variable: netcdfFile.getVariables() ) {
                String name = variable.getShortName();
                if ("latitude".equals(name)) {
                    latOrigin = variable.read().getFloat(0); // Assuming all values are the same
                } else if ("longitude".equals(name)) {
                    lonOrigin = variable.read().getFloat(0);
                } else if ("gate".equals(name)) {
                    if (variable.getDimensions().size()==1) {
                        gateDistancesMeters = new float[variable.getDimensions().get(0).getLength()];
                        Array gateArray = variable.read();
                        for (int index = 0; index < gateDistancesMeters.length; index++) {
                            gateDistancesMeters[index] = gateArray.getFloat(index);
                        }
                    }
                } else if ("azimuth".equals(name)) {
                    if (variable.getDimensions().size()==1) {
                        azimuthAngles = new float[variable.getDimensions().get(0).getLength()];
                        Array azimuthArray = variable.read();
                        for (int index = 0; index < azimuthAngles.length; index++) {
                            azimuthAngles[index] = azimuthArray.getFloat(index);
                        }
                    }
                }
                else if ((variable.getDimensions().size()==2)&&(variable.getDataType().isFloatingPoint())) {
                    radialArray = (ArrayFloat.D2) variable.read();
                }
            }
            if (Double.isNaN(latOrigin)||Double.isNaN(lonOrigin)||(gateDistancesMeters==null)||(radialArray==null)||
                    (azimuthAngles==null)) {
                throw new NexradNowException("missing data item from radial data file");
            }
            LatLongCoordinates origin = new LatLongCoordinates(latOrigin,lonOrigin);
            Path polyPath = new Path();
            // Estimate how big of a "chunk" we need in order to reach our minimum feature size
            int stepSize = 1;
            float distanceForFeature = scaler.distanceForPixels(minFeatureSize);
            while ((gateDistancesMeters[stepSize]<distanceForFeature)&&(stepSize<gateDistancesMeters.length)) {
                stepSize++;
            }
            // Now everything is loaded! Have at it.
            PointF stashedPts[] = new PointF[gateDistancesMeters.length];
            stashedPts[0] = scaler.scaleLatLong(origin);
            for (int angleIndex = 0; angleIndex < azimuthAngles.length; angleIndex += stepSize) {
                float startAngle = azimuthAngles[angleIndex];
                float endAngle = azimuthAngles[(angleIndex + stepSize)<azimuthAngles.length?(angleIndex+stepSize):
                        (angleIndex+stepSize-azimuthAngles.length)];
                PointF prevPt2 = stashedPts[0];
                PointF prevPt3 = stashedPts[0];
                for (int gateIndex = 0; gateIndex + stepSize < gateDistancesMeters.length; gateIndex+= stepSize) {
                    float startGateDistance = gateDistancesMeters[gateIndex];
                    float endGateDistance = gateDistancesMeters[gateIndex+stepSize];
                    // At this point we know all four points of the polygon. Compute and put in path IFF the value
                    // is a number
                    float cellValue = radialArray.get(angleIndex, gateIndex);
                    if (stepSize > 1) {
                        for (int searchGateIndex = gateIndex; searchGateIndex < gateIndex+stepSize; searchGateIndex++) {
                            for (int searchAngleIndex = angleIndex; searchAngleIndex < angleIndex+stepSize; searchAngleIndex++) {
                                float testValue = radialArray.get((searchAngleIndex<azimuthAngles.length)?searchAngleIndex:(searchAngleIndex-azimuthAngles.length),
                                        searchGateIndex);
                                if (Float.isNaN(testValue)) {
                                    continue;
                                }
                                if (Float.isNaN(cellValue)) {
                                    cellValue = testValue;
                                }
                                if (testValue > cellValue) {
                                    cellValue = testValue;
                                }
                            }
                        }
                    }
                    // TODO - find max value in the area covered by this step size
                    if (Float.isNaN(cellValue)||(cellValue <= 0)) {
                        stashedPts[gateIndex] = null;
                        prevPt2 = null;
                        prevPt3 = null;
                        continue;
                    }
                    // It *is* a number so we have to deal with it
                    // TODO we really should only need to compute a few of these (maybe as few as 1?) - optimize!
                    PointF pt1 = prevPt2;
                    if (pt1 == null) {
                        pt1 = scaler.scaleLatLong(
                                computeRadial(origin,startAngle,startGateDistance)
                        );
                    }
                    PointF pt4 = prevPt3;
                    if (pt4 == null) {
                        pt4 = scaler.scaleLatLong(
                                computeRadial(origin,endAngle,startGateDistance)
                        );
                    }
                    LatLongCoordinates p2 = computeRadial(origin, startAngle, endGateDistance);
                    LatLongCoordinates p3 = computeRadial(origin, endAngle, endGateDistance);
                    PointF pt2 = scaler.scaleLatLong(p2);
                    PointF pt3 = scaler.scaleLatLong(p3);
                    prevPt2 = pt2;
                    prevPt3 = pt3;
                    stashedPts[gateIndex] = pt2;
                    // Validate that we are plotting closest points
                    if (dataOwner != null) {
                        if (p2.distanceTo(dataOwner.getCoords()) > safeDistance) {
                            // Need to check against all other stations
                            NexradStation closestStation = null;
                            double closestDistance = 0.0;
                            for (NexradStation station:otherStations) {
                                double stationDistance = station.getCoords().distanceTo(p2);
                                if (station.getIdentifier().equals(dataOwner.getIdentifier())) {
                                    // Be generous and give the "owning" station a little "discount"
                                    stationDistance *= 0.9;
                                }
                                if ((closestStation == null)||(stationDistance < closestDistance)) {
                                    closestStation = station;
                                    closestDistance = station.getCoords().distanceTo(p2);
                                }
                            }
                            if (!closestStation.getIdentifier().equals(dataOwner.getIdentifier())) {
                                continue;
                            }
                        }
                    }

                    // Do the drawing deed.
                    int color = getColorFromTable(cellValue / 100.0f);
                    productPaint.setColor(color);

                    productPaint.setStrokeWidth(1.5f);
                    productPaint.setStyle(Paint.Style.FILL_AND_STROKE);
                    polyPath.reset();
                    polyPath.moveTo(pt1.x,pt1.y);
                    polyPath.lineTo(pt2.x,pt2.y);
                    polyPath.lineTo(pt3.x,pt3.y);
                    polyPath.lineTo(pt4.x,pt4.y);
                    polyPath.lineTo(pt1.x,pt1.y);
                    canvas.drawPath(polyPath,productPaint);
                }
            }
            netcdfFile.close();
        } catch (Exception e) {
            String message = "cannot read Nexrad product data: "+e.toString();
            throw new NexradNowException(message);
        }

    }
}
