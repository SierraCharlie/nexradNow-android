package com.nexradnow.android.nexradproducts;

/**
 * Created by hobsonm on 10/16/15.
 */
public class P19r0Renderer extends NexradRadialRenderer {
    @Override
    public String getProductCode() {
        return "p19r0";
    }

    @Override
    public String getProductDescription() {
        return "Base reflectivity - 124 nmi Range";
    }
}
