package com.nexradnow.android.services;

import com.google.inject.Singleton;
import de.greenrobot.event.EventBus;

/**
 * Created by hobsonm on 9/14/15.
 */
@Singleton
public class EventBusProvider {

    protected EventBus eventBus;

    public EventBusProvider() {
        eventBus = new EventBus();
    }

    public EventBus getEventBus() {
        return eventBus;
    }

}
