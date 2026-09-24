package org.ecocean.queue;

import java.io.IOException;

/**
 * Signals an INTENTIONAL consumer stop from {@link Queue#getNext()} — the operator STOP file or a
 * SHUTDOWN queue message. This is the ONLY condition that shuts a consumer executor down.
 *
 * <p>Transient failures (unreadable spool file, disk hiccup) must NOT use this type: the poll loop
 * logs those and retries on the next tick. Before this distinction existed, ANY exception from
 * getNext() permanently killed every consumer on the queue — a single transient I/O error silently
 * stopped detection/IA processing until the next Tomcat restart (observed as a 2-day production
 * outage on GiraffeSpotter, 2026-08).</p>
 */
public class QueueStopException extends IOException {
    public QueueStopException(String message) {
        super(message);
    }
}
