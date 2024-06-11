package org.ecocean;

import javax.jdo.listener.*;
// import javax.jdo.listener.InstanceLifecycleEvent;
// import javax.jdo.listener.DeleteLifecycleListener;
import org.datanucleus.enhancement.Persistable;

// https://www.datanucleus.org/products/accessplatform_4_1/jdo/lifecycle_callbacks.html#listeners

public class WildbookLifecycleListener implements StoreLifecycleListener, DeleteLifecycleListener,
    CreateLifecycleListener, LoadLifecycleListener {
    public void preDelete(InstanceLifecycleEvent event) {
        System.out.println("........................................ Lifecycle : preDelete for " +
            ((Persistable)event.getSource()).dnGetObjectId() + " / " +
            ((Persistable)event.getSource()));
    }

    public void postDelete(InstanceLifecycleEvent event) {}

    public void preStore(InstanceLifecycleEvent event) {}

    public void postStore(InstanceLifecycleEvent event) {
        System.out.println("........................................ Lifecycle : postStore for " +
            ((Persistable)event.getSource()).dnGetObjectId() + " / " +
            ((Persistable)event.getSource()));
    }

    // required but we do not use

    public void postCreate(InstanceLifecycleEvent event) {}

    public void postLoad(InstanceLifecycleEvent event) {}
}
