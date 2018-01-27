package org.ecocean.queue;

import java.util.Properties;
import org.ecocean.ShepherdProperties;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Channel;

/*

A light wrapper to RabbitMQ libs.  possibly(?) allow for exansion later to support an underlying base abstract class
(e.g. Queue.java)

*/

public class RabbitMQQueue {
    private static ConnectionFactory factory = null;
    private static Connection connection = null;
    private static String EXCHANGE_NAME = "";  //default... i think this is kosher?

    private Channel channel = null;
    private String queueName = null;

/*
    private static Logger logger = LoggerFactory.getLogger(AssetStore.class);

    private static Map<Integer, AssetStore> stores;

    protected Integer id;
    protected String name;
*/

    public RabbitMQQueue(final String qName) throws java.io.IOException, java.util.concurrent.TimeoutException {
        if (factory == null) throw new java.io.IOException("RabbitMQQueue.init() has not yet been called!");
        queueName = qName;
        channel = getChannel();
        //channel.exchangeDeclare(EXCHANGE_NAME, "direct", true); //lets use "default" exchange?
        channel.queueDeclare(queueName, true, false, false, null);
        //channel.queueBind(queueName, EXCHANGE_NAME, ROUTING_KEY);
    }

    public static synchronized void init(String context) throws java.io.IOException, java.util.concurrent.TimeoutException {
        if (factory != null) return;
        Properties props = ShepherdProperties.getProperties("queue.properties", "", context);
        if (props == null) throw new java.io.IOException("no queue.properties");
        factory = new ConnectionFactory();
        factory.setUsername(props.getProperty("rabbitmq_username", "guest"));
        factory.setPassword(props.getProperty("rabbitmq_password", "guest"));
        factory.setVirtualHost(props.getProperty("rabbitmq_virtualhost", "/"));
        factory.setHost(props.getProperty("rabbitmq_host", "localhost"));
        factory.setPort(Integer.parseInt(props.getProperty("rabbitmq_port", "5672")));
        checkConnection();
System.out.println("[INFO] RabbitMQQueue.init() complete");
    }

    public static void checkConnection() throws java.io.IOException, java.util.concurrent.TimeoutException {
        if ((connection != null) && connection.isOpen()) return;
        connection = factory.newConnection();
    }

    public Channel getChannel() throws java.io.IOException, java.util.concurrent.TimeoutException {
        checkConnection();
        return connection.createChannel();
    }

    
    public void publish(String msg) throws java.io.IOException {
//TODO check connection *and* channel??
        //channel.basicPublish(EXCHANGE_NAME, ROUTING_KEY, null, msg.getBytes());
        channel.basicPublish(EXCHANGE_NAME, this.queueName, null, msg.getBytes());
System.out.println("[INFO] published to {" + this.queueName + "}: " + msg);
    }

    //public abstract void consume();
}
