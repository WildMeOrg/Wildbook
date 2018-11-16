package org.ecocean.queue;

import org.ecocean.Util;
import org.ecocean.servlet.ServletUtilities;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Envelope;


public class RabbitMQQueue extends Queue {
    private static String TYPE_NAME = "RabbitMQ";
    private static ConnectionFactory factory = null;
    private static Connection connection = null;
    private static String EXCHANGE_NAME = "";  //default... i think this is kosher?
    private String consumerTag = null;
    private Channel channel = null;
    private boolean wantsShutdown = false;

    public static boolean isAvailable(String context) {
        /*
            the "rabbitmq_enabled" is *optional* for general use but provided in the event all other rabbitmq_* 
            properties are unnecessary because the defaults are sufficient
        */
        if ("true".equals(Queue.getProperty(context, "rabbitmq_enabled"))) return true;
        //otherwise, i guess we will assume we need one of these??
        return (
            (getVirtualHost(context, null) != null) ||
            (getHost(context, null) != null) ||
            (getUsername(context, null) != null) ||
            (getPassword(context, null) != null)
        );
    }

    public RabbitMQQueue(final String name) throws java.io.IOException {
        super(name);
        if (factory == null) throw new java.io.IOException("RabbitMQQueue.init() has not yet been called!");
        this.type = TYPE_NAME;
        consumerTag = name + "-" + Util.generateUUID();
        try {
            channel = getChannel();
            //channel.exchangeDeclare(EXCHANGE_NAME, "direct", true); //lets use "default" exchange?
            channel.queueDeclare(name, true, false, false, null);
            //channel.queueBind(queueName, EXCHANGE_NAME, ROUTING_KEY);
        } catch (java.util.concurrent.TimeoutException toex) {
            throw new java.io.IOException("RabbitMQQueue(" + name + ") TimeoutException: " + toex.toString());
        }
    }

    public static synchronized void init(String context) throws java.io.IOException {
        if (factory != null) return;
        try {
            factory = new ConnectionFactory();
            factory.setUsername(getUsername(context, "guest"));
            factory.setPassword(getPassword(context, "guest"));
            factory.setVirtualHost(getVirtualHost(context, "/"));
            factory.setHost(getHost(context, "localhost"));
            factory.setPort(getPort(context, "5672"));
            checkConnection();
        } catch (java.util.concurrent.TimeoutException toex) {
            throw new java.io.IOException("RabbitMQ.init() TimeoutException: " + toex.toString());
        }
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
System.out.println("[INFO] " + this.toString() + " published to {" + this.queueName + "}: " + msg);
    }

    //i think this never returns?
    public void consume(final QueueMessageHandler msgHandler) throws java.io.IOException {
System.out.println("RabbitMQQueue.consume() started with consumerTag=" + consumerTag);
        //boolean is auto-ack.  false means we manually ack
        messageHandler = msgHandler;
        channel.basicConsume(this.queueName, false, consumerTag,
            new DefaultConsumer(channel) {
                @Override
                public void handleDelivery(String conTag, Envelope envelope, AMQP.BasicProperties properties, byte[] bodyB) throws java.io.IOException {
                    String body = new String(bodyB);
                    String routingKey = envelope.getRoutingKey();
                    String contentType = properties.getContentType();
                    long deliveryTag = envelope.getDeliveryTag();
                    // (process the message components here ...)
                    boolean success = msgHandler.handler(body);
System.out.println("RabbitMQQueue.consume(): " + deliveryTag + "; " + contentType + "; " + routingKey + " = {" + body + "} => " + success);
                    if (success) channel.basicAck(deliveryTag, false);
                    if (wantsShutdown || isConsumerShutdownMessage(body)) {
                        System.out.println(">>> RabbitMQQueue shutdown message received on " + conTag);
                        channel.basicCancel(conTag);
                    }
                }
            }
        );
    }


    public void shutdown() {
        wantsShutdown = true;
    }

    @Override
    public String toString() {
        return super.toString() + " [TAG " + consumerTag + "]";
    }


    public String getNext() throws java.io.IOException { return null; }  //NOOP


    private static String getVirtualHost(String context, String def) {
        return Queue.getProperty(context, "rabbitmq_virtualhost", def);
    }
    private static String getHost(String context, String def) {
        return Queue.getProperty(context, "rabbitmq_host", def);
    }
    private static String getUsername(String context, String def) {
        return Queue.getProperty(context, "rabbitmq_username", def);
    }
    private static String getPassword(String context, String def) {
        return Queue.getProperty(context, "rabbitmq_password", def);
    }
    private static int getPort(String context, String def) {
        try {
            return Integer.parseInt(Queue.getProperty(context, "rabbitmq_port", def));
        } catch (Exception ex) { }
        return -1;
    }

}
