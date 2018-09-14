package com.nexradnow.android.nexradproducts;



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
