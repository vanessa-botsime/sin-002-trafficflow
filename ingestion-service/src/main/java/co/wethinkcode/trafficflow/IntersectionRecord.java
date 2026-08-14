package co.wethinkcode.trafficflow;

/**
 * IntersectionRecord
 */
public class IntersectionRecord {

    public final String id;
    public final Object district;
    public final Object signalType;
    public final Object active;

    public IntersectionRecord(String id, String district, String signalType, Boolean active) {
        this.id = id;
        this.district = district;
        this.signalType = signalType;
        this.active = active;
    }

    public IntersectionRecord(String id2, Object district2, Object signalType2, Object active2) {
        this.id = id2;
        this.district = district2;
        this.signalType = signalType2;
        this.active = active2;
    }

}
