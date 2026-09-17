package co.wethinkcode.trafficflow;
import co.wethinkcode.trafficflow.mq.MqConfig;
import io.javalin.Javalin;
import org.apache.activemq.ActiveMQConnectionFactory;

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.Queue;
import javax.jms.Session;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class IntersectionWatchdogApp {

    private static final long HEARTBEAT_TIMEOUT_MS = 15_000;

    private static final AtomicLong lastHeartbeatAt = new AtomicLong(0);
    private static final AtomicBoolean alerting = new AtomicBoolean(false);
    public static void main(String[] args) {
        
        subscribeToHeartbeats();

        ScheduledExecutorService checker = Executors.newSingleThreadScheduledExecutor();
        checker.scheduleAtFixedRate(IntersectionWatchdogApp::checkHeartbeat, 5, 5, TimeUnit.SECONDS);

        Javalin app = Javalin.create().start(7024);

        app.get("/health", ctx -> ctx.result("OK"));
        app.get("/alert", ctx -> {
            long last = lastHeartbeatAt.get();
            long agoMs = last == 0 ? -1 : System.currentTimeMillis() - last;
            ctx.json(Map.of(
                    "alerting", alerting.get(),
                    "lastHeartbeatAgoMs", agoMs
            ));
        });
    }

    private static void subscribeToHeartbeats() {
        try {
            ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(MqConfig.BROKER_URL);
            Connection connection = factory.createConnection();
            connection.start();
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Queue queue = session.createQueue(MqConfig.HEARTBEAT_QUEUE);
            MessageConsumer consumer = session.createConsumer(queue);

            consumer.setMessageListener(message -> {
                lastHeartbeatAt.set(System.currentTimeMillis());
                if (alerting.compareAndSet(true, false)) {
                    System.out.println("Heartbeat received again - clearing alert.");
                }
            });

            System.out.println("Subscribed to " + MqConfig.HEARTBEAT_QUEUE + " at " + MqConfig.BROKER_URL);
        } catch (JMSException e) {
            System.err.println("Could not connect to ActiveMQ broker at " + MqConfig.BROKER_URL
                    + " - watchdog cannot monitor heartbeats. Cause: " + e.getMessage());
        }
    }

    private static void checkHeartbeat() {
        long last = lastHeartbeatAt.get();
        if (last == 0) {
            // Haven't received a first heartbeat yet - nothing to compare against.
            return;
        }
        long sinceMs = System.currentTimeMillis() - last;
        if (sinceMs > HEARTBEAT_TIMEOUT_MS) {
            if (alerting.compareAndSet(false, true)) {
                System.err.println("ALERT: no heartbeat from intersection-service in "
                        + sinceMs + "ms - it may be down.");
            }
        }

    }
}


