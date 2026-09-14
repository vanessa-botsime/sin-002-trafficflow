package co.wethinkcode.trafficflow;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import io.javalin.Javalin;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class RoutingServiceApp {

    private static final String INTERSECTION_SERVICE_URL = "http://localhost:7021";
    private static final String CONGESTION_SERVICE_URL = "http://localhost:7022/congestion";

    private static final int BASE_MINUTES = 5;
    private static final int MINUTES_PER_CONGESTION_LEVEL = 2;
 
    private static final HttpClient client = HttpClient.newHttpClient();
    private static final ObjectMapper mapper = new ObjectMapper();
    public static void main(String[] args) {
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

            Integer congestionLevel = fetchCongestionLevel();
            if (congestionLevel == null) {
                ctx.status(502).json(Map.of("error", "Could not reach congestion-service"));
                return;
            }

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

    private static Integer fetchCongestionLevel() {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(CONGESTION_SERVICE_URL)).GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return null;
            }
            JsonNode node = mapper.readTree(response.body());
            return node.get("level").asInt();
        } catch (IOException | InterruptedException e) {
            System.err.println("Could not reach congestion-service: " + e.getMessage());
            return null;
        }
    }
}

