package org.ecocean.grid.tetra;

import java.util.HashMap;
import java.util.Map;

/**
 * Accumulates pattern match votes for a single candidate encounter.
 * Tracks both total vote count and individual spot-pair correspondences
 * (needed to build VertexPointMatch scores for MatchObject output).
 */
public class VoteAccumulator {
    private int voteCount = 0;

    // "querySpotIdx:catalogSpotIdx" -> number of patterns that matched this pair
    private final Map<String, Integer> spotPairVotes = new HashMap<>();

    public void addVote(TetraPattern queryPattern, TetraHashEntry catalogEntry) {
        voteCount++;
        int[] qIdx = queryPattern.getSpotIndices();
        int[] cIdx = catalogEntry.getSpotIndices();
        for (int i = 0; i < 4; i++) {
            String key = qIdx[i] + ":" + cIdx[i];
            spotPairVotes.merge(key, 1, Integer::sum);
        }
    }

    public int getVoteCount() { return voteCount; }

    public Map<String, Integer> getSpotPairVotes() { return spotPairVotes; }
}
