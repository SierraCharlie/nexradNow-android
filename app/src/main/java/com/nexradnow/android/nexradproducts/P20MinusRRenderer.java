package com.nexradnow.android.nexradproducts;

/**
 * Created by hobsonm on 10/16/15.
 */
public class P20MinusRRenderer extends NexradRadialRenderer {
    @Override
    public String getProductCode() {
        return "p20-r";
    }

    @Override
    public String getProductDescription() {
        return "Base reflectivity - 248 nmi Range";
    }
}
