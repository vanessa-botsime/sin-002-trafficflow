package co.wethinkcode.trafficflow;

import io.javalin.Javalin;
import io.javalin.http.BadRequestResponse;
 
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class CongestionServiceApp {
     private static final int MIN_LEVEL = 0;
    private static final int MAX_LEVEL = 8;
 

    public static void main(String[] args) {
        AtomicInteger congestionLevel = new AtomicInteger(0);
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
            ctx.json(Map.of("level", congestionLevel.get()));
        });

    }
}

