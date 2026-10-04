package com.prtsnote.airdrop.world.entity;

import java.util.ArrayList;
import java.util.List;

/** Immutable block-style aircraft geometry; local +Z is forward and +X is port. */
public final class AircraftAppearance {
    public enum Material { BODY, FRAME, GLASS, RUBBER }
    public enum Pattern { NAVIGATION, STROBE, BEACON }
    public record Point(float x, float y, float z) {}
    public record UV(float u, float v) {}
    public record Quad(Point a, Point b, Point c, Point d, Point normal, UV ua, UV ub, UV uc, UV ud) {}
    public record Part(Material material, List<Quad> faces) {}
    public record Lamp(String name, Point position, int rgb, float size, Pattern pattern, double offset) {}

    public static final float ENGINE_X = 4.2F, ENGINE_Y = 0.12F, PROPELLER_Z = 3.48F;
    public static final int FULL_BRIGHT = 0xF000F0;
    private static final float[][] FUSELAGE = {{-7.0F, 0.55F, 0.65F, 0.10F}, {-5.5F, 1.2F, 1.2F, 0.02F},
            {-3.5F, 2.0F, 1.8F, 0}, {2.6F, 2.4F, 2.1F, 0}, {4.2F, 2.05F, 1.8F, 0},
            {5.5F, 1.4F, 1.1F, -0.15F}, {6.1F, 0.6F, 0.6F, -0.12F}};
    public static final List<Part> BODY = body();
    public static final List<Part> PROPELLER = propeller();
    public static final List<Lamp> LAMPS = List.of(
            new Lamp("port_navigation", new Point(8.83F, 0.72F, -0.46F), 0xFF3024, 0.20F, Pattern.NAVIGATION, 0),
            new Lamp("starboard_navigation", new Point(-8.83F, 0.72F, -0.46F), 0x35FF78, 0.20F, Pattern.NAVIGATION, 0),
            new Lamp("port_strobe", new Point(8.83F, 0.76F, -0.92F), 0xFFFFFF, 0.24F, Pattern.STROBE, 0),
            new Lamp("starboard_strobe", new Point(-8.83F, 0.76F, -0.92F), 0xFFFFFF, 0.24F, Pattern.STROBE, 0),
            new Lamp("tail_strobe", new Point(0, 3.21F, -7.16F), 0xFFFFFF, 0.23F, Pattern.STROBE, 2),
            new Lamp("upper_beacon", new Point(0, 1.19F, -0.4F), 0xFF3524, 0.24F, Pattern.BEACON, 0),
            new Lamp("lower_beacon", new Point(0, -1.17F, 0.4F), 0xFF3524, 0.24F, Pattern.BEACON, 10));
    public static final Part LIGHT_CORE = box(Material.FRAME, -0.5F, -0.5F, -0.5F, 1, 1, 1);
    public static final Quad LIGHT_HALO = quad(p(-1,-1,0), p(1,-1,0), p(1,1,0), p(-1,1,0));

    public static double intensity(Lamp lamp, double flightAge) {
        if (!Double.isFinite(flightAge)) return 0;
        if (lamp.pattern == Pattern.NAVIGATION) return 1;
        double period = lamp.pattern == Pattern.STROBE ? 30 : 20;
        double age = flightAge - lamp.offset;
        double phase = age - Math.floor(age / period) * period;
        if (lamp.pattern == Pattern.STROBE) return phase < 2 || (phase >= 4 && phase < 6) ? 1 : 0;
        if (phase >= 4) return 0;
        double pulse = Math.sin(Math.PI * phase / 4);
        return pulse * pulse;
    }

    private static List<Part> body() {
        var parts = new ArrayList<Part>();
        // Octagonal cross-sections taper the nose and cargo tail instead of one rectangular slab.
        parts.add(tube(Material.BODY, 0, FUSELAGE));
        parts.add(box(Material.FRAME, -0.31F, -0.44F, 6.08F, 0.62F, 0.61F, 0.16F));
        // A wraparound cockpit follows the actual top and side chamfers of the nose.
        cockpitWindow(parts, 4, 4, 0.03F, 0.475F, 0.045F, 0.79F);
        cockpitWindow(parts, 4, 4, 0.525F, 0.97F, 0.045F, 0.79F);
        cockpitWindow(parts, 4, 3, 0.04F, 0.96F, 0.045F, 0.79F);
        cockpitWindow(parts, 4, 5, 0.04F, 0.96F, 0.045F, 0.79F);
        cockpitWindow(parts, 3, 3, 0.04F, 0.96F, 0.15F, 0.96F);
        cockpitWindow(parts, 3, 5, 0.04F, 0.96F, 0.15F, 0.96F);
        for (int side : new int[]{-1, 1}) {
            for (int window = 0; window < 4; window++) {
                float z = 1.65F - window * 1.13F;
                float halfWidth = 1 + 0.2F * (z + 3.5F) / 6.1F;
                parts.add(box(Material.FRAME, side > 0 ? halfWidth : -halfWidth - 0.03F,
                        -0.04F, z - 0.05F, 0.03F, 0.51F, 0.62F));
                parts.add(box(Material.GLASS, side > 0 ? halfWidth + 0.03F : -halfWidth - 0.046F,
                        0.02F, z, 0.016F, 0.39F, 0.52F));
            }
            parts.add(wing(Material.FRAME, side, 1.05F, 8.8F, 0.40F, 0.67F, 2.4F, -1.4F, 0.7F, -1.1F, 0.22F, 0.12F));
            // Outer flap and aileron strips sit below the trailing edge.
            parts.add(wing(Material.BODY, side, 1.5F, 6.2F, 0.36F, 0.51F, -0.94F, -1.44F, -0.88F, -1.23F, 0.06F, 0.04F));
            parts.add(wing(Material.BODY, side, 6.24F, 8.63F, 0.55F, 0.63F, -0.78F, -1.21F, -0.80F, -1.13F, 0.05F, 0.04F));
            parts.add(wing(Material.FRAME, side, 0.22F, 3.8F, 0.68F, 1.02F, -5.1F, -7.0F, -5.8F, -7.1F, 0.18F, 0.09F));
            float x = side * ENGINE_X;
            parts.add(tube(Material.BODY, x, new float[][]{{-1.32F, 0.57F, 0.63F, ENGINE_Y},
                    {-0.65F, 1.05F, 1.1F, ENGINE_Y}, {2.8F, 1.05F, 1.1F, ENGINE_Y}, {3.38F, 0.76F, 0.82F, ENGINE_Y}}));
            parts.add(tube(Material.FRAME, x, new float[][]{{3.39F, 0.48F, 0.48F, ENGINE_Y}, {3.85F, 0.15F, 0.15F, ENGINE_Y}}));
            parts.add(box(Material.RUBBER, x - 0.23F, -0.54F, 2.76F, 0.46F, 0.15F, 0.56F));
            parts.add(box(Material.FRAME, x - 0.24F, -0.12F, -1.58F, 0.48F, 0.48F, 0.30F));
            parts.add(box(Material.RUBBER, x - 0.17F, -0.05F, -1.61F, 0.34F, 0.34F, 0.035F));
            parts.add(box(Material.FRAME, side > 0 ? 1.08F : -1.53F, -1.00F, -1.65F, 0.45F, 0.36F, 2.55F));
            // Separate dark wingtip housings retain a visible shape between flashes.
            parts.add(box(Material.RUBBER, side > 0 ? 8.66F : -8.96F, 0.60F, -1.08F, 0.30F, 0.20F, 0.82F));
        }
        parts.add(fin());
        parts.add(box(Material.FRAME, -0.70F, -0.965F, -2.8F, 1.4F, 0.055F, 3.2F));
        for (int rib = 0; rib < 5; rib++) parts.add(box(Material.RUBBER, -0.66F, -0.989F, -2.56F + rib * 0.62F, 1.32F, 0.025F, 0.04F));
        parts.add(box(Material.FRAME, -0.045F, 1.01F, -2.4F, 0.09F, 0.44F, 0.31F));
        parts.add(box(Material.FRAME, -0.04F, 0.72F, 2.96F, 0.08F, 0.37F, 0.28F));
        parts.add(box(Material.RUBBER, -0.16F, 1.035F, -0.57F, 0.32F, 0.08F, 0.34F));
        parts.add(box(Material.RUBBER, -0.16F, -1.095F, 0.23F, 0.32F, 0.08F, 0.34F));
        return List.copyOf(parts);
    }

    private static List<Part> propeller() {
        var parts = new ArrayList<Part>();
        for (int side : new int[]{-1, 1}) {
            parts.add(box(Material.RUBBER, side > 0 ? 0.18F : -1.40F, -0.10F, -0.035F, 1.22F, 0.20F, 0.10F));
            parts.add(box(Material.FRAME, side > 0 ? 1.40F : -1.57F, -0.10F, -0.035F, 0.17F, 0.20F, 0.10F));
            parts.add(box(Material.RUBBER, -0.10F, side > 0 ? 0.18F : -1.40F, -0.035F, 0.20F, 1.22F, 0.10F));
            parts.add(box(Material.FRAME, -0.10F, side > 0 ? 1.40F : -1.57F, -0.035F, 0.20F, 0.17F, 0.10F));
        }
        return List.copyOf(parts);
    }

    private static Part fin() {
        Point[] vertices = {p(-0.16F, 0.60F, -4.50F), p(-0.16F, 0.44F, -7.03F), p(-0.12F, 3.12F, -7.20F), p(-0.12F, 3.12F, -5.82F),
                p(0.16F, 0.60F, -4.50F), p(0.16F, 0.44F, -7.03F), p(0.12F, 3.12F, -7.20F), p(0.12F, 3.12F, -5.82F)};
        return solid(Material.BODY, vertices);
    }

    private static Part wing(Material material, int side, float rootX, float tipX, float rootY, float tipY,
                             float rootFront, float rootBack, float tipFront, float tipBack, float rootThickness, float tipThickness) {
        var part = solid(material, new Point[]{p(side * rootX, rootY, rootBack), p(side * tipX, tipY, tipBack),
                p(side * tipX, tipY + tipThickness, tipBack), p(side * rootX, rootY + rootThickness, rootBack),
                p(side * rootX, rootY, rootFront), p(side * tipX, tipY, tipFront),
                p(side * tipX, tipY + tipThickness, tipFront), p(side * rootX, rootY + rootThickness, rootFront)});
        // Mirroring coordinates reverses winding; keep outward normals on both wings.
        if (side < 0) return new Part(material, part.faces.stream().map(face -> quad(face.a,face.d,face.c,face.b)).toList());
        return part;
    }

    private static Part tube(Material material, float centreX, float[][] sections) {
        var faces = new ArrayList<Quad>();
        Point[] previous = ring(centreX, sections[0]);
        Point centre = p(centreX, sections[0][3], sections[0][0]);
        for (int edge = 0; edge < 8; edge++) faces.add(quad(centre, previous[(edge + 1) % 8], previous[edge], previous[edge]));
        for (int section = 1; section < sections.length; section++) {
            Point[] next = ring(centreX, sections[section]);
            for (int edge = 0; edge < 8; edge++) faces.add(quad(previous[edge], previous[(edge + 1) % 8], next[(edge + 1) % 8], next[edge]));
            previous = next;
        }
        centre = p(centreX, sections[sections.length - 1][3], sections[sections.length - 1][0]);
        for (int edge = 0; edge < 8; edge++) faces.add(quad(centre, previous[edge], previous[(edge + 1) % 8], previous[(edge + 1) % 8]));
        return new Part(material, List.copyOf(faces));
    }

    private static Point[] ring(float x, float[] section) {
        float z = section[0], w = section[1], h = section[2], y = section[3];
        return new Point[]{p(x - w * 0.32F, y - h / 2, z), p(x + w * 0.32F, y - h / 2, z),
                p(x + w / 2, y - h * 0.28F, z), p(x + w / 2, y + h * 0.28F, z),
                p(x + w * 0.32F, y + h / 2, z), p(x - w * 0.32F, y + h / 2, z),
                p(x - w / 2, y + h * 0.28F, z), p(x - w / 2, y - h * 0.28F, z)};
    }

    private static Part box(Material material, float x, float y, float z, float w, float h, float d) {
        return solid(material, new Point[]{p(x,y,z), p(x+w,y,z), p(x+w,y+h,z), p(x,y+h,z),
                p(x,y,z+d), p(x+w,y,z+d), p(x+w,y+h,z+d), p(x,y+h,z+d)});
    }

    private static Part solid(Material material, Point[] points) {
        int[][] indices = {{0,3,2,1}, {5,6,7,4}, {4,7,3,0}, {1,2,6,5}, {3,7,6,2}, {4,0,1,5}};
        var faces = new ArrayList<Quad>();
        for (int[] face : indices) faces.add(quad(points[face[0]], points[face[1]], points[face[2]], points[face[3]]));
        return new Part(material, List.copyOf(faces));
    }

    private static void cockpitWindow(List<Part> parts, int section, int edge, float u0, float u1, float t0, float t1) {
        // The same section rings define both the hull and glazing; a small normal offset avoids z-fighting.
        cockpitPatch(parts, Material.FRAME, section, edge, u0, u1, t0, t1, 0.012F);
        cockpitPatch(parts, Material.GLASS, section, edge, u0 + 0.035F, u1 - 0.035F, t0 + 0.025F, t1 - 0.025F, 0.020F);
    }

    private static void cockpitPatch(List<Part> parts, Material material, int section, int edge,
                                     float u0, float u1, float t0, float t1, float offset) {
        Point a = hullPoint(section, edge, u0, t0, offset), b = hullPoint(section, edge, u1, t0, offset),
                c = hullPoint(section, edge, u1, t1, offset), d = hullPoint(section, edge, u0, t1, offset);
        // Tapered sections can twist a quad slightly. Separate planar triangles retain correct normals.
        parts.add(new Part(material, List.of(quad(a,b,c,c))));
        parts.add(new Part(material, List.of(quad(a,c,d,d))));
    }

    private static Point hullPoint(int section, int edge, float u, float t, float offset) {
        Point[] back = ring(0, FUSELAGE[section]), front = ring(0, FUSELAGE[section + 1]);
        Point a = back[edge], b = back[(edge + 1) % 8], c = front[(edge + 1) % 8], d = front[edge];
        Point normal;
        float wa, wb, wc, wd;
        if (u >= t) {
            wa = 1 - u; wb = u - t; wc = t; wd = 0;
            normal = quad(a,b,c,c).normal;
        } else {
            wa = 1 - t; wb = 0; wc = u; wd = t - u;
            normal = quad(a,c,d,d).normal;
        }
        return p(a.x*wa + b.x*wb + c.x*wc + d.x*wd + normal.x*offset,
                a.y*wa + b.y*wb + c.y*wc + d.y*wd + normal.y*offset,
                a.z*wa + b.z*wb + c.z*wc + d.z*wd + normal.z*offset);
    }

    private static Quad quad(Point a, Point b, Point c, Point d) {
        float ux = b.x-a.x, uy = b.y-a.y, uz = b.z-a.z, vx = c.x-a.x, vy = c.y-a.y, vz = c.z-a.z;
        float nx = uy*vz-uz*vy, ny = uz*vx-ux*vz, nz = ux*vy-uy*vx;
        float length = (float) Math.sqrt(nx*nx + ny*ny + nz*nz);
        Point normal = p(nx/length, ny/length, nz/length);
        return new Quad(a,b,c,d,normal,uv(a,normal),uv(b,normal),uv(c,normal),uv(d,normal));
    }

    private static UV uv(Point point, Point normal) {
        // One tile per four model blocks: long panels repeat instead of stretching a whole texture.
        // Model-space projection also gives shared triangle vertices the same texture coordinates.
        float x = Math.abs(normal.x), y = Math.abs(normal.y), z = Math.abs(normal.z);
        if (y >= x && y >= z) return new UV(point.x * 0.25F, point.z * 0.25F);
        if (x >= z) return new UV(point.z * 0.25F, -point.y * 0.25F);
        return new UV(point.x * 0.25F, -point.y * 0.25F);
    }

    private static Point p(float x, float y, float z) { return new Point(x,y,z); }
    private AircraftAppearance() {}
}
