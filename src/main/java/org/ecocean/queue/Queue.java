package org.ecocean.queue;

/*

this is just a silly placeholder for now... the idea that we could abstract Queue to allow
for both (say) RabbitMQ and a simple directory/file-based queue if the install didnt want to go full RabbitMQ
... but for now let RabbitMQ live on as its own....

*/

public abstract class Queue {
/*
    private static Logger logger = LoggerFactory.getLogger(AssetStore.class);

    private static Map<Integer, AssetStore> stores;

    protected Integer id;
    protected String name;
*/

    protected Queue(final String type, final String name) {
    }

    public static synchronized void init() {}

    public abstract void publish();
    public abstract void consume();
}
