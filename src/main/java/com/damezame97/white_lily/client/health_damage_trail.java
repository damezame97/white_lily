package com.damezame97.white_lily.client;

/** Client display state only; times are measured in game ticks. */
public final class health_damage_trail {
    private double health = Double.NaN;
    private double maximum;
    private double start;
    private double changedAt;

    public double update(double current, double max, double now) {
        max = Math.max(0.001, max);
        current = Math.max(0, Math.min(current, max));
        double previous = value(now);
        if (Double.isNaN(health) || max != maximum || current <= 0) {
            start = current / max;
            changedAt = now - 18;
        } else if (current < health) {
            start = Math.max(previous, health / max);
            changedAt = now;
        } else if (current > health) {
            // Healing must never leave a damage trail below the live hearts.
            start = Math.max(previous, current / max);
            changedAt = now - 6;
        }
        health = current;
        maximum = max;
        return value(now);
    }

    private double value(double now) {
        if (Double.isNaN(health)) return 0;
        double target = health / maximum;
        double progress = Math.max(0, Math.min(1, (now - changedAt - 6) / 12));
        return Math.max(target, start + (target - start) * progress);
    }
}
