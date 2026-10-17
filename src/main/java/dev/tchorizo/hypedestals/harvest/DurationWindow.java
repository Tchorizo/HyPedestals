package dev.tchorizo.hypedestals.harvest;

import java.util.Arrays;

/**
 * The last {@code capacity} durations (any unit; the mod uses microseconds), for percentiles and
 * the maximum over that window. Older samples are overwritten. Pure and not thread-safe.
 */
public final class DurationWindow {

    private final long[] samples;
    private int next;
    private int size;

    public DurationWindow(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be at least 1, got " + capacity);
        }
        samples = new long[capacity];
    }

    public void add(long duration) {
        samples[next] = duration;
        next = (next + 1) % samples.length;
        size = Math.min(size + 1, samples.length);
    }

    /** Samples currently in the window ({@code <= capacity}). */
    public int size() {
        return size;
    }

    public void clear() {
        next = 0;
        size = 0;
    }

    /**
     * Nearest-rank percentile over the window: the smallest sample with at least {@code p} percent
     * of the samples at or below it ({@code p} in {@code (0, 100]}); {@code 0} when empty.
     */
    public long percentile(double p) {
        if (!(p > 0 && p <= 100)) {
            throw new IllegalArgumentException("percentile must be in (0, 100], got " + p);
        }
        if (size == 0) {
            return 0;
        }
        long[] sorted = Arrays.copyOf(samples, size);
        Arrays.sort(sorted);
        int rank = (int) Math.ceil(p / 100.0 * size);
        return sorted[Math.max(rank, 1) - 1];
    }

    /** Largest sample in the window; {@code 0} when empty. */
    public long max() {
        long max = 0;
        for (int i = 0; i < size; i++) {
            max = Math.max(max, samples[i]);
        }
        return max;
    }
}
