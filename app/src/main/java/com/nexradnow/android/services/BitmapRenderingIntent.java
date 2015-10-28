package com.nexradnow.android.services;

import android.content.Intent;
import android.support.v4.content.LocalBroadcastManager;
import com.nexradnow.android.app.R;
import com.nexradnow.android.exception.NexradNowException;
import com.nexradnow.android.model.LatLongRect;
import com.nexradnow.android.model.NexradUpdate;
import roboguice.service.RoboIntentService;

/**
 * Create a bitmap from the supplied Nexrad products and desired display geometry.
 * Created by hobsonm on 10/23/15.
 */
public class BitmapRenderingIntent extends RoboIntentService {

    public static final int STATUS_RUNNING = 0;
    public static final int STATUS_FINISHED = 1;
    public static final int STATUS_ERROR = 2;

    private static final String TAG = "BitmapRendering";

    public static String RENDERACTION = "com.nexradnow.android.renderBitmap";

    public BitmapRenderingIntent(String name) {
        super(name);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (RENDERACTION.equals(intent.getAction()) ){
            renderBitmap(intent);
        }
    }



    private void renderBitmap(Intent intent) {
        NexradUpdate update = (NexradUpdate)intent.getSerializableExtra("com.nexradnow.android.nexradUpdate");
        int viewX = intent.getIntExtra("com.nexradnow.android.viewX", 0);
        int viewY = intent.getIntExtra("com.nexradnow.android.viewY", 0);
        LatLongRect desiredRegion = (LatLongRect)intent.getSerializableExtra("com.nexradnow.android.latLongRect");
        if ((viewX<=0)||(viewY<=0)||(update==null)) {
            notifyException(intent,new NexradNowException("Missing required input data for rendering"));
        }
        // TODO - actually implement!
        if (desiredRegion == null) {
            desiredRegion = computeRegion (update);
        }
    }

    private void notifyProgress(Intent intent, String progressMsg) {
        intent.putExtra("com.nexradnow.android.status", STATUS_RUNNING);
        intent.putExtra("com.nexradnow.android.statusmsg",progressMsg);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private void notifyException(Intent intent, Exception ex) {
        if (ex instanceof NexradNowException) {
            intent.putExtra("com.nexradnow.android.errmsg", ex.getMessage());
        } else {
            intent.putExtra("com.nexradnow.android.errmsg", ex.toString());
        }
        intent.putExtra("com.nexradnow.android.status", STATUS_ERROR);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    protected LatLongRect computeRegion(NexradUpdate nexradUpdate) {

        return null;
    }
}
