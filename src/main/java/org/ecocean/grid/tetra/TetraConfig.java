package org.ecocean.grid.tetra;

import java.util.Properties;

/**
 * Configuration for TETRA matching parameters.
 * Loadable from commonConfiguration.properties or a dedicated properties file.
 */
public class TetraConfig implements java.io.Serializable {
    static final long serialVersionUID = 6382917405829173042L;

    private int numBins = 25;
    private boolean toleranceEnabled = true;
    private double maxRatioDistance = 0.1;
    private int topK = 100;
    private int minVotes = 2;
    private int minSpots = 4;
    private int maxPatternsPerEncounter = 500;
    private int maxQueryPatterns = 1000;

    public TetraConfig() {}

    public static TetraConfig fromProperties(Properties props) {
        TetraConfig config = new TetraConfig();
        if (props.containsKey("tetraNumBins"))
            config.numBins = Integer.parseInt(props.getProperty("tetraNumBins"));
        if (props.containsKey("tetraToleranceEnabled"))
            config.toleranceEnabled = Boolean.parseBoolean(
                props.getProperty("tetraToleranceEnabled"));
        if (props.containsKey("tetraMaxRatioDistance"))
            config.maxRatioDistance = Double.parseDouble(
                props.getProperty("tetraMaxRatioDistance"));
        if (props.containsKey("tetraTopK"))
            config.topK = Integer.parseInt(props.getProperty("tetraTopK"));
        if (props.containsKey("tetraMinVotes"))
            config.minVotes = Integer.parseInt(props.getProperty("tetraMinVotes"));
        if (props.containsKey("tetraMinSpots"))
            config.minSpots = Integer.parseInt(props.getProperty("tetraMinSpots"));
        if (props.containsKey("tetraMaxPatternsPerEncounter"))
            config.maxPatternsPerEncounter = Integer.parseInt(
                props.getProperty("tetraMaxPatternsPerEncounter"));
        if (props.containsKey("tetraMaxQueryPatterns"))
            config.maxQueryPatterns = Integer.parseInt(
                props.getProperty("tetraMaxQueryPatterns"));
        return config;
    }

    public int getNumBins() { return numBins; }
    public void setNumBins(int numBins) { this.numBins = numBins; }

    public boolean isToleranceEnabled() { return toleranceEnabled; }
    public void setToleranceEnabled(boolean toleranceEnabled) {
        this.toleranceEnabled = toleranceEnabled;
    }

    public double getMaxRatioDistance() { return maxRatioDistance; }
    public void setMaxRatioDistance(double maxRatioDistance) {
        this.maxRatioDistance = maxRatioDistance;
    }

    public int getTopK() { return topK; }
    public void setTopK(int topK) { this.topK = topK; }

    public int getMinVotes() { return minVotes; }
    public void setMinVotes(int minVotes) { this.minVotes = minVotes; }

    public int getMinSpots() { return minSpots; }
    public void setMinSpots(int minSpots) { this.minSpots = minSpots; }

    public int getMaxPatternsPerEncounter() { return maxPatternsPerEncounter; }
    public void setMaxPatternsPerEncounter(int max) { this.maxPatternsPerEncounter = max; }

    public int getMaxQueryPatterns() { return maxQueryPatterns; }
    public void setMaxQueryPatterns(int max) { this.maxQueryPatterns = max; }
}
