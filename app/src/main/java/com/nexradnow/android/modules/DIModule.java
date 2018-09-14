package com.nexradnow.android.modules;

import android.content.Context;
import com.nexradnow.android.nexradproducts.RendererInventory;
import com.nexradnow.android.services.NexradDataManager;
import de.greenrobot.event.EventBus;
import toothpick.config.Module;

public class DIModule extends Module {
    public DIModule() {
        bind(EventBus.class).toInstance(new EventBus());
        bind(RendererInventory.class).toInstance(new RendererInventory());
        bind(NexradDataManager.class).toProviderInstance(new NexradDataManagerProvider());
    }
    public void addCtx(Context ctx) {
        bind(Context.class).toInstance(ctx);
    }
}
