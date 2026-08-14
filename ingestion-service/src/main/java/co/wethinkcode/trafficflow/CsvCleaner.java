package co.wethinkcode.trafficflow;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;


public class CsvCleaner {

    private static final Set<String> PLACEHOLDER_VALUES = Set.of(
            "", "n/a", "na", "tbd", "unknown", "-", "nan", "null"
    );

    private static final Set<String> TRUE_VALUES = Set.of("y", "yes", "true", "1");
    private static final Set<String> FALSE_VALUES = Set.of("n", "no", "false", "0");

    // Naming/spelling variants that mean the same real-world signal type.
    private static final Map<String, String> SIGNAL_TYPE_SYNONYMS = Map.of(
            "traffic circle", "roundabout",
            "circle", "roundabout"
    );

    
    public List<IntersectionRecord> clean(InputStream csvStream) throws IOException {
        Map<String, IntersectionRecord> byId = new LinkedHashMap<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(csvStream, StandardCharsets.UTF_8))) {

            String headerLine = reader.readLine();
            if (headerLine == null) {
                return new ArrayList<>();
            }
            List<String> headers = splitAndTrim(headerLine);
            int idCol = indexOfHeader(headers, "intersection_id");
            int districtCol = indexOfHeader(headers, "district");
            int signalTypeCol = indexOfHeader(headers, "signal_type");
            int activeCol = indexOfHeader(headers, "active_flag");

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                List<String> fields = splitAndTrim(line);

                String rawId = get(fields, idCol);
                if (rawId == null || rawId.isBlank()) {

                    continue;
                }

                String id = rawId.toUpperCase();
                String district = normalizeDistrict(get(fields, districtCol));
                String signalType = normalizeSignalType(get(fields, signalTypeCol));
                Boolean active = normalizeBoolean(get(fields, activeCol));

                IntersectionRecord incoming = new IntersectionRecord(id, district, signalType, active);
                IntersectionRecord existing = byId.get(id);

                if (existing == null) {
                    byId.put(id, incoming);
                } else {
                    byId.put(id, merge(existing, incoming));
                }
            }
        }

        return new ArrayList<>(byId.values());
    }

    private IntersectionRecord merge(IntersectionRecord a, IntersectionRecord b) {
        return new IntersectionRecord(
                a.id,
                a.district != null ? a.district : b.district,
                a.signalType != null ? a.signalType : b.signalType,
                a.active != null ? a.active : b.active
        );
    }

    private String normalizeDistrict(String raw) {
        String cleaned = cleanText(raw);
        if (cleaned == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (String word : cleaned.split(" ")) {
            if (word.isEmpty()) continue;
            if (sb.length() > 0) sb.append(" ");
            sb.append(Character.toUpperCase(word.charAt(0)))
              .append(word.substring(1).toLowerCase());
        }
        return sb.toString();
    }

    private String normalizeSignalType(String raw) {
        String cleaned = cleanText(raw);
        if (cleaned == null) {
            return null;
        }
        String lower = cleaned.toLowerCase();
        return SIGNAL_TYPE_SYNONYMS.getOrDefault(lower, lower);
    }
    private String cleanText(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim().replaceAll("\\s+", " ");
        if (PLACEHOLDER_VALUES.contains(trimmed.toLowerCase())) {
            return null;
        }
        return trimmed;
    }

    private Boolean normalizeBoolean(String raw) {
        String cleaned = cleanText(raw);
        if (cleaned == null) {
            return null;
        }
        String lower = cleaned.toLowerCase();
        if (TRUE_VALUES.contains(lower)) {
            return true;
        }
        if (FALSE_VALUES.contains(lower)) {
            return false;
        }

        return null;
    }

    private List<String> splitAndTrim(String line) {
        String[] parts = line.split(",", -1);
        List<String> out = new ArrayList<>(parts.length);
        for (String part : parts) {
            out.add(part.trim());
        }
        return out;
    }

    private int indexOfHeader(List<String> headers, String name) {
        for (int i = 0; i < headers.size(); i++) {
            if (headers.get(i).trim().equalsIgnoreCase(name)) {
                return i;
            }
        }
        return -1;
    }

    private String get(List<String> fields, int col) {
        if (col < 0 || col >= fields.size()) {
            return null;
        }
        return fields.get(col);
    }
}