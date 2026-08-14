package co.wethinkcode.trafficflow;

import io.javalin.Javalin;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public class IngestionServiceApp {

    public static void main(String[] args) throws IOException {
        List<IntersectionRecord> cleanedRecords =  loadCleanedRecords();
        Javalin app = Javalin.create().start(7020);

        app.get("/health", ctx -> ctx.result("OK"));

        // TODO: read and clean src/main/resources/intersections-legacy.csv (intersections, districts, signal types data —
        // trim whitespace, fix casing, normalize dates/booleans) and expose the
        // cleaned records here for the other services to consume.
        app.get("/intersections", ctx -> ctx.json(cleanedRecords));
    }

    private static List<IntersectionRecord> loadCleanedRecords() throws IOException{
        try (InputStream csv = IngestionServiceApp.class.getResourceAsStream("/intersections-legacy.csv")){
            if (csv == null) {
                throw new IOException("intersections-legacy.csv not found on classpath");
            }
            return new CsvCleaner().clean(csv);
        }
    }
}
