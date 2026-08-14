package co.wethinkcode.trafficflow;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CsvCleanerTest {

    private final CsvCleaner cleaner = new CsvCleaner();

    private List<IntersectionRecord> clean(String csv) throws IOException {
        InputStream in = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));
        return cleaner.clean(in);
    }

    private IntersectionRecord findById(List<IntersectionRecord> records, String id) {
        return records.stream()
                .filter(r -> r.id.equals(id))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No record found with id " + id));
    }

    @Test
    void collapsesDuplicateIdsRegardlessOfCasing() throws IOException {
        String csv = "intersection_id,District ,signal_type,active_flag\n"
                + "INT-1005,Downtown,Roundabout,true\n"
                + "int-1005,downtown ,ROUNDABOUT,TRUE\n";

        List<IntersectionRecord> records = clean(csv);

        assertEquals(1, records.size(), "Duplicate id casing should collapse to a single record");
        IntersectionRecord record = records.get(0);
        assertEquals("INT-1005", record.id);
        assertEquals("Downtown", record.district);
        assertEquals("roundabout", record.signalType);
        assertEquals(true, record.active);
    }

    @Test
    void blankSignalTypeBecomesExplicitNull() throws IOException {
        String csv = "intersection_id,District ,signal_type,active_flag\n"
                + "INT-1007,Eastside,,1\n";

        List<IntersectionRecord> records = clean(csv);

        assertEquals(1, records.size());
        assertNull(records.get(0).signalType, "Blank signal_type should be null, not dropped or guessed");
        assertEquals("Eastside", records.get(0).district);
    }

    @Test
    void unknownActiveFlagBecomesNullNotFalse() throws IOException {
        String csv = "intersection_id,District ,signal_type,active_flag\n"
                + "INT-1013,Westside,4-way,unknown\n";

        List<IntersectionRecord> records = clean(csv);

        assertNull(records.get(0).active, "Unrecognized boolean representation should be null, not guessed as false");
    }

    @Test
    void recognizesAllTrueBooleanRepresentations() throws IOException {
        String csv = "intersection_id,District ,signal_type,active_flag\n"
                + "INT-2001,Downtown,4-way,Y\n"
                + "INT-2002,Downtown,4-way,yes\n"
                + "INT-2003,Downtown,4-way,true\n"
                + "INT-2004,Downtown,4-way,1\n";

        List<IntersectionRecord> records = clean(csv);

        for (String id : List.of("INT-2001", "INT-2002", "INT-2003", "INT-2004")) {
            assertEquals(true, findById(records, id).active, id + " should normalize to true");
        }
    }

    @Test
    void recognizesAllFalseBooleanRepresentations() throws IOException {
        String csv = "intersection_id,District ,signal_type,active_flag\n"
                + "INT-3001,Downtown,4-way,N\n"
                + "INT-3002,Downtown,4-way,no\n"
                + "INT-3003,Downtown,4-way,FALSE\n"
                + "INT-3004,Downtown,4-way,0\n";

        List<IntersectionRecord> records = clean(csv);

        for (String id : List.of("INT-3001", "INT-3002", "INT-3003", "INT-3004")) {
            assertEquals(false, findById(records, id).active, id + " should normalize to false");
        }
    }

    @Test
    void districtCasingCollapsesToSameValue() throws IOException {
        String csv = "intersection_id,District ,signal_type,active_flag\n"
                + "INT-4001,downtown,4-way,Y\n"
                + "INT-4002,DOWNTOWN,4-way,Y\n"
                + "INT-4003,Downtown,4-way,Y\n";

        List<IntersectionRecord> records = clean(csv);

        assertTrue(records.stream().allMatch(r -> r.district.equals("Downtown")),
                "All casing variants of 'downtown' should normalize to the same value");
    }

    @Test
    void missingDistrictBecomesExplicitNull() throws IOException {
        String csv = "intersection_id,District ,signal_type,active_flag\n"
                + "INT-1015,,4-way,Y\n";

        List<IntersectionRecord> records = clean(csv);

        assertNull(records.get(0).district, "Missing district should be null, not dropped or guessed");
    }
}
