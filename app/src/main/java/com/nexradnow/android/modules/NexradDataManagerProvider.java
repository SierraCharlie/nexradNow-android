package com.nexradnow.android.modules;

import com.nexradnow.android.services.NexradDataManager;

import javax.inject.Provider;

public class NexradDataManagerProvider implements Provider<NexradDataManager> {
    protected NexradDataManager mgrInstance;

    @Override
    public NexradDataManager get() {
        if (mgrInstance == null) {
            mgrInstance = new NexradDataManager();
        }
        return mgrInstance;
    }
}
