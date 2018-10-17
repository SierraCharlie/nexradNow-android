package com.nexradnow.android.modules;

import android.content.Context;
import com.jakewharton.disklrucache.DiskLruCache;
import com.nexradnow.android.nexradproducts.RendererInventory;
import com.nexradnow.android.services.NexradDataManager;
import de.greenrobot.event.EventBus;
import toothpick.config.Module;

public class DIModule extends Module {
    public DIModule(Context ctx) {
        bind(Context.class).toInstance(ctx);
        bind(EventBus.class).toInstance(new EventBus());
        bind(RendererInventory.class).toInstance(new RendererInventory());
        bind(NexradDataManager.class).toProviderInstance(new SingletonProvider<NexradDataManager>(NexradDataManager.class));
        try {
            bind(DiskLruCache.class).toInstance(DiskLruCache.open(ctx.getCacheDir(), 1, 1, 10 * 1024 * 1024));
        } catch (Exception ex) {
            throw new IllegalStateException("Can't initialize cache", ex);
        }
    }
}
