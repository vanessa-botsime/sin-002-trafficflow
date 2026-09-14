package co.wethinkcode.trafficflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class IntersectionServiceApp {

    private static final String INGESTION_URL = "http://localhost:7020/intersections";

    public static void main(String[] args) {
        Map<String, IntersectionRecord> byId = new ConcurrentHashMap<>();
        loadFromIngestion(byId);

        Javalin app = Javalin.create().start(7021);

        app.get("/health", ctx -> ctx.result("OK"));

        // Source of truth for whether an intersection id is valid.
        // routing-service calls this before estimating a travel time.
        app.get("/intersections/{id}", ctx -> {
            String id = ctx.pathParam("id").toUpperCase();
            IntersectionRecord record = byId.get(id);
            if (record == null) {
                ctx.status(404).json(Map.of("error", "Unknown intersection id: " + id));
                return;
            }
            ctx.json(record);
        });

        // Source of truth for whether a district name is valid.
        app.get("/districts/{name}", ctx -> {
            String name = ctx.pathParam("name");
            boolean valid = byId.values().stream()
                    .anyMatch(r -> r.district != null && ((String) r.district).equalsIgnoreCase(name));
            if (!valid) {
                ctx.status(404).json(Map.of("error", "Unknown district: " + name));
                return;
            }
            ctx.status(200).result("OK");
        });
    }

    /**
     * Loads the cleaned dataset from ingestion-service at startup. If ingestion-service
     * is unreachable, this service starts anyway with an empty dataset rather than
     * crashing - every lookup will just 404 until ingestion-service is back up. This is
     * a deliberate choice (fail soft, not fail fast) - see README/commit notes for why.
     */
    private static void loadFromIngestion(Map<String, IntersectionRecord> byId) {
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder(URI.create(INGESTION_URL)).GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                System.err.println("ingestion-service returned status " + response.statusCode()
                        + " - starting intersection-service with an empty dataset.");
                return;
            }

            ObjectMapper mapper = new ObjectMapper();
            List<IntersectionRecord> records = mapper.readValue(
                    response.body(),
                    mapper.getTypeFactory().constructCollectionType(List.class, IntersectionRecord.class));

            for (IntersectionRecord record : records) {
                byId.put(record.id, record);
            }
            System.out.println("Loaded " + byId.size() + " intersections from ingestion-service.");
        } catch (IOException | InterruptedException e) {
            System.err.println("Could not reach ingestion-service at " + INGESTION_URL
                    + " - starting intersection-service with an empty dataset. Cause: " + e.getMessage());
        }
    }
}