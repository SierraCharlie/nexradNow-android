package com.nexradnow.android.model;

import com.google.android.gms.common.ConnectionResult;

/**
 * Created by hobsonm on 10/19/15.
 */
public class ConnectionResolveErrorMessage {
    ConnectionResult connectionResult;

    public ConnectionResolveErrorMessage(ConnectionResult connectionResult) {
        this.connectionResult = connectionResult;
    }

    public ConnectionResult getConnectionResult() {
        return connectionResult;
    }
}
