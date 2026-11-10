package com.yash.upsc400.engine;

/** One plan slot as seen by the scheduling logic. Pure Java, no Android types. */
public final class PlanSlot {
    public enum Kind { MATH, GS, REVISION, OTHER }

    public final String key;
    public final int day;
    public final Kind kind;
    /** Subject stream: a Mathematics unit code or a GS block code. Slots in one stream depend on each other in day order. */
    public final String stream;
    public final String[] prereqStreams;
    public final int plannedMin;
    public final int[] pieceMin;
    /** 0 core, 1 standard, 2 extended, one entry per piece. */
    public final int[] pieceTier;

    public PlanSlot(String key, int day, Kind kind, String stream, String[] prereqStreams,
                    int plannedMin, int[] pieceMin, int[] pieceTier) {
        this.key = key;
        this.day = day;
        this.kind = kind;
        this.stream = stream;
        this.prereqStreams = prereqStreams == null ? new String[0] : prereqStreams;
        this.plannedMin = plannedMin;
        this.pieceMin = pieceMin == null ? new int[0] : pieceMin;
        this.pieceTier = pieceTier == null ? new int[0] : pieceTier;
    }

    /** Best (lowest) tier among the pieces; core = 0. A slot with no pieces counts as core. */
    public int tierRank() {
        if (pieceTier.length == 0) return 0;
        int best = 2;
        for (int t : pieceTier) if (t < best) best = t;
        return best;
    }

    public boolean hasExtendedPieces() {
        for (int t : pieceTier) if (t == 2) return true;
        return false;
    }

    /** Minutes still needed. If skim is true, extended pieces cost half; core and standard pieces never shrink. */
    public int effectiveMinutes(boolean skim) {
        if (pieceMin.length == 0) return plannedMin;
        double sum = 0;
        for (int i = 0; i < pieceMin.length; i++) {
            int tier = i < pieceTier.length ? pieceTier[i] : 0;
            sum += (skim && tier == 2) ? pieceMin[i] * 0.5 : pieceMin[i];
        }
        return (int) Math.round(sum);
    }
}
