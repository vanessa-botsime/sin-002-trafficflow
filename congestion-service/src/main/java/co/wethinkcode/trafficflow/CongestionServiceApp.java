package co.wethinkcode.trafficflow;

import io.javalin.Javalin;
import io.javalin.http.BadRequestResponse;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;

import org.apache.activemq.ActiveMQConnectionFactory;

import co.wethinkcode.trafficflow.mq.MqConfig;

import javax.jms.Connection;
import javax.jms.JMSException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class CongestionServiceApp {

    private static final int MIN_LEVEL = 0;
    private static final int MAX_LEVEL = 8;

    private static MessageProducer mqProducer;
    private static Session mqSession;

    public static void main(String[] args) {

        AtomicInteger congestionLevel = new AtomicInteger(0);
        initMq();
        Javalin app = Javalin.create().start(7022);

        app.get("/health", ctx -> ctx.result("OK"));

        app.get("/congestion", ctx -> ctx.json(Map.of("level", congestionLevel.get())));
 
        app.post("/congestion", ctx -> {
            Map<?, ?> body = ctx.bodyAsClass(Map.class);
            Object rawLevel = body.get("level");
            if (!(rawLevel instanceof Integer)) {
                throw new BadRequestResponse("level must be an integer between " + MIN_LEVEL + " and " + MAX_LEVEL);
            }
            int level = (Integer) rawLevel;
            if (level < MIN_LEVEL || level > MAX_LEVEL) {
                throw new BadRequestResponse("level must be between " + MIN_LEVEL + " and " + MAX_LEVEL);
            }
            congestionLevel.set(level);
            publishCongestionLevel(level);
            ctx.json(Map.of("level", congestionLevel.get()));
        });

    }

    private static void initMq() {
        try {
            ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(MqConfig.BROKER_URL);
            Connection connection = factory.createConnection();
            connection.start();
            mqSession = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Topic topic = mqSession.createTopic(MqConfig.TOPIC);
            mqProducer = mqSession.createProducer(topic);
            System.out.println("Connected to ActiveMQ broker at " + MqConfig.BROKER_URL
                    + " - publishing to " + MqConfig.TOPIC);
        } catch (JMSException e) {
            System.err.println("Could not connect to ActiveMQ broker at " + MqConfig.BROKER_URL
                    + " - congestion changes will not be published. Cause: " + e.getMessage());
        }
    }
 
    private static void publishCongestionLevel(int level) {
        if (mqProducer == null) {
            return;
        }
        try {
            TextMessage message = mqSession.createTextMessage("{\"level\":" + level + "}");
            mqProducer.send(message);
        } catch (JMSException e) {
            System.err.println("Failed to publish congestion level to MQ: " + e.getMessage());
        }
    }

}

