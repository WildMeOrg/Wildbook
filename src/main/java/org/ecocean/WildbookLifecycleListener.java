package org.ecocean;

import java.io.IOException;
import javax.jdo.listener.*;
import org.datanucleus.enhancement.Persistable;
import org.ecocean.Base;
import org.ecocean.OpenSearch;

// https://www.datanucleus.org/products/accessplatform_4_1/jdo/lifecycle_callbacks.html#listeners

public class WildbookLifecycleListener implements StoreLifecycleListener, DeleteLifecycleListener,
    CreateLifecycleListener, LoadLifecycleListener {
    public void preDelete(InstanceLifecycleEvent event) {
        Persistable obj = (Persistable)event.getSource();

        System.out.println("WildbookLifecycleListener preDelete() event type=" +
            event.getEventType() + "; source=" + obj + "; target=" + event.getTarget() +
            "; detachedInstance=" + event.getDetachedInstance() + "; persistentInstance=" +
            event.getPersistentInstance());
        if (Base.class.isInstance(obj)) {
            Base base = (Base)obj;
            try {
                base.opensearchUnindexDeep();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    public void postDelete(InstanceLifecycleEvent event) {
        Persistable obj = (Persistable)event.getSource();

        // cannot actually use obj, as it will throw: javax.jdo.JDOUserException: Cannot read fields from a deleted object
        System.out.println("WildbookLifecycleListener postDelete() event type=" +
            event.getEventType() + "; source id=" + obj.dnGetObjectId());
        // System.out.println("WildbookLifecycleListener postDelete() event type=" + event.getEventType() + "; source=" + obj + "; target=" + event.getTarget() + "; detachedInstance=" + event.getDetachedInstance() + "; persistentInstance=" + event.getPersistentInstance());
    }

    public void preStore(InstanceLifecycleEvent event) {}

    public void postStore(InstanceLifecycleEvent event) {
        Persistable obj = (Persistable)event.getSource();

        if (OpenSearch.skipAutoIndexing()) {
            System.out.println("WildbookLifecycleListener skipAutoIndexing set");
            return;
        }

        System.out.println("WildbookLifecycleListener postStore() event type=" +
            event.getEventType() + "; source=" + obj + "; target=" + event.getTarget() +
            "; detachedInstance=" + event.getDetachedInstance() + "; persistentInstance=" +
            event.getPersistentInstance());
        if (Base.class.isInstance(obj)) {
            Base base = (Base)obj;
            try {
                base.opensearchIndexDeep();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    // required but we do not use

    public void postCreate(InstanceLifecycleEvent event) {}

    public void postLoad(InstanceLifecycleEvent event) {}
}
