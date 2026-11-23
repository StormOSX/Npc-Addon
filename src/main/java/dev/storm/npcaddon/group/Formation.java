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

    /** Minecraft yaw NPC i should face: the formation's facing, or straight outwards from the centre for a circle. */
    public float facing(float yawDeg, int i, int n) {
        if (this != CIRCLE) return yawDeg;
        return yawDeg + (float) (360.0 * i / n);
    }

    /** World position of slot i. yawDeg is Minecraft yaw (0 = +Z, 90 = -X). */
    public Vec3 slot(Vec3 centre, float yawDeg, int i, int n, double spacing) {
        double[] l = local(i, n, spacing);
        double yaw = Math.toRadians(yawDeg);
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);   // forward
        double rx = -Math.cos(yaw), rz = -Math.sin(yaw);  // right
        return centre.add(rx * l[0] + fx * l[1], 0, rz * l[0] + fz * l[1]);
    }
}
