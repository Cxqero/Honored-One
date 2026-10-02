package com.gojolimitless.destruction;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/** Cached integer offsets inside a sphere, sorted from the centre outwards. Radius is quantised to 0.25 blocks. */
public final class SphereOffsets {
    private static final ConcurrentHashMap<Integer, int[]> CACHE = new ConcurrentHashMap<>();

    private SphereOffsets() {}

    /** @return packed [dx,dy,dz, dx,dy,dz, ...] */
    public static int[] of(double radius) {
        int key = (int) Math.round(radius * 4);
        return CACHE.computeIfAbsent(key, k -> build(k / 4.0));
    }

    private static int[] build(double r) {
        int R = (int) Math.ceil(r);
        double r2 = r * r;
        List<int[]> pts = new ArrayList<>();
        for (int x = -R; x <= R; x++)
            for (int y = -R; y <= R; y++)
                for (int z = -R; z <= R; z++)
                    if (x * x + y * y + z * z <= r2) pts.add(new int[]{x, y, z});
        pts.sort(Comparator.comparingInt(p -> p[0] * p[0] + p[1] * p[1] + p[2] * p[2]));
        int[] out = new int[pts.size() * 3];
        for (int i = 0; i < pts.size(); i++) {
            int[] p = pts.get(i);
            out[i * 3] = p[0]; out[i * 3 + 1] = p[1]; out[i * 3 + 2] = p[2];
        }
        return out;
    }
}
