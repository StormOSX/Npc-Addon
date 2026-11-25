package dev.storm.npcaddon.group;

import net.minecraft.world.phys.Vec3;

/**
 * Slot layouts. Local coordinates: x = to the right of the facing direction, z = forward.
 * Slot 0 is the front/apex/first position, so with a leader the first NPC takes the lead.
 */
public enum Formation {
    LINE, COLUMN, WEDGE, CIRCLE, SQUARE;

    /** Local (right, forward) offset of slot i out of n, with the given spacing between neighbours. */
    private double[] local(int i, int n, double s) {
        switch (this) {
            case LINE:
                return new double[]{(i - (n - 1) / 2.0) * s, 0};
            case COLUMN:
            {
                // Two abreast: rows of two, front row first.
                int rows = (int) Math.ceil(n / 2.0);
                return new double[]{(i % 2 - 0.5) * s, ((rows - 1) / 2.0 - i / 2) * s};
            }
            case WEDGE: {
                if (i == 0) return new double[]{0, 0};
                int k = (i + 1) / 2;
                return new double[]{(i % 2 == 1 ? -k : k) * s, -k * s};
            }
            case CIRCLE: {
                double r = Math.max(s, n * s / (2 * Math.PI));
                double a = 2 * Math.PI * i / n;
                return new double[]{Math.sin(a) * r, Math.cos(a) * r};
            }
            case SQUARE:
            default: {
                int cols = (int) Math.ceil(Math.sqrt(n));
                int rows = (int) Math.ceil(n / (double) cols);
                return new double[]{(i % cols - (cols - 1) / 2.0) * s, ((rows - 1) / 2.0 - i / cols) * s};
            }
        }
    }

    /** Where one squad member goes and which way (Minecraft yaw) it faces there. */
    public record Spot(Vec3 pos, float yaw) {}

    /**
     * A spot for every member, in the members' order. In a squad with both kinds, melee members take the front and
     * ranged members the back: a second rank for a line, an inner ring for a circle, the rear slots otherwise.
     *
     * @param ranged per member, whether it fights at range
     * @param yawDeg Minecraft yaw the formation faces (0 = +Z, 90 = -X)
     */
    public Spot[] arrange(Vec3 centre, float yawDeg, boolean[] ranged, double spacing) {
        int n = ranged.length;
        int back = 0;
        for (boolean r : ranged) if (r) back++;
        int front = n - back;
        boolean mixed = front > 0 && back > 0;
        Spot[] out = new Spot[n];
        double inner = back == 1 ? 0 : Math.max(spacing * 0.5, back * spacing / (2 * Math.PI));
        double outer = Math.max(Math.max(spacing, front * spacing / (2 * Math.PI)), inner + spacing);
        int nextFront = 0;
        int nextBack = 0;
        // Melee members first, then ranged ones: slot order runs front to back.
        for (int pass = 0; pass < 2; pass++) {
            for (int m = 0; m < n; m++) {
                if (mixed && ranged[m] != (pass == 1)) continue;
                if (!mixed && pass == 1) break;
                int k = ranged[m] ? nextBack++ : nextFront++;
                int of = ranged[m] ? back : front;
                float yaw = yawDeg;
                double[] l;
                if (mixed && this == CIRCLE) {
                    double a = 2 * Math.PI * k / of;
                    double r = ranged[m] ? inner : outer;
                    l = new double[]{Math.sin(a) * r, Math.cos(a) * r};
                    if (r > 0) yaw += (float) (360.0 * k / of);
                } else if (mixed && this == LINE) {
                    l = local(k, of, spacing);
                    l[1] = (ranged[m] ? -0.5 : 0.5) * spacing;
                } else {
                    int i = mixed && ranged[m] ? front + k : k;
                    l = local(i, n, spacing);
                    if (this == CIRCLE) yaw += (float) (360.0 * i / n);
                }
                out[m] = new Spot(world(centre, yawDeg, l), yaw);
            }
        }
        return out;
    }

    private static Vec3 world(Vec3 centre, float yawDeg, double[] l) {
        double yaw = Math.toRadians(yawDeg);
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);   // forward
        double rx = -Math.cos(yaw), rz = -Math.sin(yaw);  // right
        return centre.add(rx * l[0] + fx * l[1], 0, rz * l[0] + fz * l[1]);
    }
}
