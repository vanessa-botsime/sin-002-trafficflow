package co.wethinkcode.trafficflow;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import co.wethinkcode.trafficflow.mq.MqConfig;
import org.apache.activemq.ActiveMQConnectionFactory;
import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;
import io.javalin.Javalin;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.atomic.AtomicInteger;


public class RoutingServiceApp {

    private static final String INTERSECTION_SERVICE_URL = "http://localhost:7021";



    private static final int BASE_MINUTES = 5;
    private static final int MINUTES_PER_CONGESTION_LEVEL = 2;
 
    private static final HttpClient client = HttpClient.newHttpClient();
    private static final ObjectMapper mapper = new ObjectMapper();

    // Stage 3: kept up to date by subscribing to congestion-topic instead of
    // polling GET /congestion on congestion-service per-request.
    private static final AtomicInteger cachedCongestionLevel = new AtomicInteger(0);

    public static void main(String[] args) {
        subscribeToCongestionUpdates();

        Javalin app = Javalin.create().start(7023);

        app.get("/health", ctx -> ctx.result("OK"));

        app.get("/routes/estimate", ctx -> {
            String fromId = ctx.queryParam("from");
            String toId = ctx.queryParam("to");

            if (fromId == null || toId == null) {
                ctx.status(400).json(Map.of("error", "Both 'from' and 'to' query parameters are required"));
                return;
            }

            if (!intersectionExists(fromId)) {
                ctx.status(404).json(Map.of("error", "Unknown intersection: " + fromId));
                return;
            }
            if (!intersectionExists(toId)) {
                ctx.status(404).json(Map.of("error", "Unknown intersection: " + toId));
                return;
            }

            int congestionLevel = cachedCongestionLevel.get();
            int estimatedMinutes = BASE_MINUTES + (congestionLevel * MINUTES_PER_CONGESTION_LEVEL);

            ctx.json(Map.of(
                    "from", fromId,
                    "to", toId,
                    "congestionLevel", congestionLevel,
                    "estimatedMinutes", estimatedMinutes
            ));
        });
    }

    private static boolean intersectionExists(String id) {
        try {
            HttpRequest request = HttpRequest.newBuilder(
                    URI.create(INTERSECTION_SERVICE_URL + "/intersections/" + id)).GET().build();
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            return response.statusCode() == 200;
        } catch (IOException | InterruptedException e) {
            System.err.println("Could not reach intersection-service: " + e.getMessage());
            return false;
        }
    }

    private static void subscribeToCongestionUpdates() {
        try {
            ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(MqConfig.BROKER_URL);
            Connection connection = factory.createConnection();
            connection.start();
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Topic topic = session.createTopic(MqConfig.TOPIC);
            MessageConsumer consumer = session.createConsumer(topic);
 
            consumer.setMessageListener(message -> {
                try {
                    if (message instanceof TextMessage textMessage) {
                        JsonNode node = mapper.readTree(textMessage.getText());
                        cachedCongestionLevel.set(node.get("level").asInt());
                        System.out.println("Received congestion update: level=" + cachedCongestionLevel.get());
                    }
                } catch (JMSException | IOException e) {
                    System.err.println("Failed to process congestion update: " + e.getMessage());
                }
            });
 
            System.out.println("Subscribed to " + MqConfig.TOPIC + " at " + MqConfig.BROKER_URL);
        } catch (JMSException e) {
            System.err.println("Could not connect to ActiveMQ broker at " + MqConfig.BROKER_URL
                    + " - routing-service will use the default congestion level. Cause: " + e.getMessage());
        }
    }
}

