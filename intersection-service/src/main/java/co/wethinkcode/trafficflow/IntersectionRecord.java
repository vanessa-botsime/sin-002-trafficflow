package co.wethinkcode.trafficflow;

/**
 * IntersectionRecord
 */
public class IntersectionRecord {

    public Object district;
    public String id;
     public String signalType;
    public Boolean active;
 
    public IntersectionRecord() {
    }
 
    public IntersectionRecord(String id, String district, String signalType, Boolean active) {
        this.id = id;
        this.district = district;
        this.signalType = signalType;
        this.active = active;
    }

}
