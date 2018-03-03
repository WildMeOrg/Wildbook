package org.ecocean.queue;

import java.util.Properties;
import org.ecocean.ShepherdProperties;
import org.ecocean.Util;
import org.ecocean.servlet.ServletUtilities;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Envelope;

/*

A light wrapper to RabbitMQ libs.  possibly(?) allow for exansion later to support an underlying base abstract class
(e.g. Queue.java)

*/

public class RabbitMQQueue extends Queue {
    private static String TYPE_NAME = "RabbitMQ";
    private static ConnectionFactory factory = null;
    private static Connection connection = null;
    private static String EXCHANGE_NAME = "";  //default... i think this is kosher?
    private String consumerTag = null;
    private Channel channel = null;
    private boolean wantsShutdown = false;

    public static boolean isAvailable(String context) {
        Properties props = ShepherdProperties.getProperties("queue.properties", "", context);
        if (props == null) return false;
        /*
            the "rabbitmq_enabled" is *optional* for general use but provided in the event all other rabbitmq_* 
            properties are unnecessary because the defaults are sufficient
        */
        if ("true".equals(props.getProperty("rabbitmq_enabled"))) return true;
        //otherwise, i guess we will assume we need one of these??
        return (
            (props.getProperty("rabbitmq_virtualhost") != null) ||
            (props.getProperty("rabbitmq_host") != null) ||
            (props.getProperty("rabbitmq_username") != null) ||
            (props.getProperty("rabbitmq_password") != null)
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
        Properties props = ShepherdProperties.getProperties("queue.properties", "", context);
        if (props == null) throw new java.io.IOException("no queue.properties");
        try {
            factory = new ConnectionFactory();
            factory.setUsername(props.getProperty("rabbitmq_username", "guest"));
            factory.setPassword(props.getProperty("rabbitmq_password", "guest"));
            factory.setVirtualHost(props.getProperty("rabbitmq_virtualhost", "/"));
            factory.setHost(props.getProperty("rabbitmq_host", "localhost"));
            factory.setPort(Integer.parseInt(props.getProperty("rabbitmq_port", "5672")));
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

}
