package com.nexradnow.android.model;

import java.util.Calendar;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Created by hobsonm on 10/12/15.
 */
public class NexradProductCache {
    protected NexradFTPContents ftpContents;
    protected List<NexradProduct> products;

    public NexradProductCache(NexradFTPContents ftpContents, List<NexradProduct> products) {
        this.ftpContents = ftpContents;
        this.products = products;
    }

    public NexradFTPContents getFtpContents() {
        return ftpContents;
    }

    public void setFtpContents(NexradFTPContents ftpContents) {
        this.ftpContents = ftpContents;
    }

    public List<NexradProduct> getProducts() {
        return products;
    }

    public void setProducts(List<NexradProduct> products) {
        this.products = products;
    }

    public void ageCache(long time, TimeUnit units) {
        // Remove any entries older than the specified time
        long ageTime = System.currentTimeMillis() - TimeUnit.MILLISECONDS.convert(time, units);
        if ((products!=null)&&(!products.isEmpty())) {
            Iterator<NexradProduct> iterator = products.iterator();
            while (iterator.hasNext()) {
                NexradProduct product = iterator.next();
                if (product.getTimestamp().getTimeInMillis() < ageTime) {
                    iterator.remove();
                }
            }
        }
    }
}
