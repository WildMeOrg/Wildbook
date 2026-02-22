package org.ecocean.grid.tetra;

/**
 * Accumulates pattern match votes for a single candidate encounter.
 * Tracks only the total vote count — TETRA's edge-sorted hashing does
 * not preserve spot correspondence, so individual spot-pair tracking
 * would produce incorrect results.
 */
public class VoteAccumulator {
    private int voteCount = 0;

    public void addVote(TetraPattern queryPattern, TetraHashEntry catalogEntry) {
        voteCount++;
    }

    public int getVoteCount() { return voteCount; }
}
