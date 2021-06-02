package com.nexradnow.android.nexradproducts;

/**
 * Created by hobsonm on 10/16/15.
 */
public class P94r0Renderer extends NexradRadialRenderer {
    @Override
    public String getProductCode() {
        return "p94r0";
    }

    @Override
    public String getProductDescription() {
        return "Base reflectivity - 248 nmi Range";
    }
}
