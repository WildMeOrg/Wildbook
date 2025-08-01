package org.ecocean;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.json.JSONObject;

public class Measurement extends DataCollectionEvent {
    private static final long serialVersionUID = -7934850478287322048L;

    private Double value;

    private String units;

    public Measurement() {}

    public Measurement(String correspondingEncounterNumber, String type, Double value, String units,
        String samplingProtocol) {
        super(correspondingEncounterNumber, type);
        super.setSamplingProtocol(samplingProtocol);
        this.value = value;
        this.units = units;
    }

    public Double getValue() {
        return value;
    }

    public void setValue(Double value) {
        if (value == null) { this.value = null; } else { this.value = value; }
    }

    public String getUnits() {
        return units;
    }

    public void setUnits(String u) {
        if (u == null) { this.units = null; } else { this.units = u; }
    }

    public JSONObject toJSONObject() {
        JSONObject rtn = new JSONObject();

        rtn.put("value", value);
        rtn.put("units", units);
        rtn.put("samplingProtocol", getSamplingProtocol());
        return rtn;
    }

    public String toString() {
        return new ToStringBuilder(this)
                   .append("type", getType())
                   .append("value", value)
                   .append("units", units)
                   .toString();
    }

    public String toExcelFormat() {
        return "type: " + getType().toString() + "; value: " + value.toString() + "; units: " +
                   units;
    }
}
