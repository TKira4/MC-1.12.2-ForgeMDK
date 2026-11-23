package com.imba.undyingarcana;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.lwjgl.opengl.GL11;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Glow geometry for skill VFX, drawn with additive blending and no textures: soft slash ribbons, shockwave rings,
 * billboard beams, pillars, spectral weapons (sword, spears), ground cracks, rune circles, vortices, domes, orbs,
 * travelling crescents, impact rays and lightning. Also the screen flash and the Art vignette.
 * Every soft band is drawn as two quads (edge alpha 0 -> centre alpha a -> edge alpha 0), so it looks like light.
 */
@SideOnly(Side.CLIENT)
public class FxMesh {
    private static final List<Mesh> MESHES = new ArrayList<>();
    private static final Random RNG = new Random();
    private static final Vec3d UP = new Vec3d(0, 1, 0);
    private static float[] flashCol = {1, 1, 1};
    private static float flashPow;
    private static int flashLeft, flashDur = 1;
    private static int vigLeft, vigDur = 1;
    private static float vigPow;

    abstract static class Mesh {
        int age;
        int life = 10;
        Entity follow;
        int el;

        float[] core() {
            return FxClient.palette(el, 0);
        }

        float[] main() {
            return FxClient.palette(el, 1);
        }

        float[] dark() {
            return FxClient.palette(el, 2);
        }

        /** Overall fade: fade-in over 'in' ticks, fade-out over the last 'out' ticks. */
        double fade(double t, double in, double out) {
            double a = in <= 0 ? 1 : Math.min(1, t / in);
            double b = Math.min(1, Math.max(0, (life - t) / out));
            return a * b;
        }

        abstract void draw(BufferBuilder b, double t, Vec3d cam);
    }

    public static void add(Mesh m) {
        if (MESHES.size() < 400) {
            MESHES.add(m);
        }
    }

    // ------------------------------------------------------------------ geometry helpers
    private static void v(BufferBuilder b, Vec3d p, float[] c, double a) {
        b.pos(p.x, p.y, p.z).color(c[0], c[1], c[2], (float) Math.max(0, Math.min(1, a))).endVertex();
    }

    private static void quad(BufferBuilder b, Vec3d p1, Vec3d p2, Vec3d p3, Vec3d p4, float[] c, double a1, double a2, double a3, double a4) {
        v(b, p1, c, a1);
        v(b, p2, c, a2);
        v(b, p3, c, a3);
        v(b, p4, c, a4);
    }

    /** Soft band along points p[i] with half-width w[i] in direction d[i] (unit), alpha a[i]. */
    private static void band(BufferBuilder b, Vec3d[] p, Vec3d[] d, double[] w, double[] a, float[] c) {
        for (int i = 0; i < p.length - 1; i++) {
            Vec3d o0 = d[i].scale(w[i]), o1 = d[i + 1].scale(w[i + 1]);
            quad(b, p[i], p[i + 1], p[i + 1].add(o1), p[i].add(o0), c, a[i], a[i + 1], 0, 0);
            quad(b, p[i], p[i + 1], p[i + 1].subtract(o1), p[i].subtract(o0), c, a[i], a[i + 1], 0, 0);
        }
    }

    /** Solid (not soft) band: full alpha across the width. */
    private static void strip(BufferBuilder b, Vec3d[] p, Vec3d[] d, double[] w, double[] a, float[] c) {
        for (int i = 0; i < p.length - 1; i++) {
            Vec3d o0 = d[i].scale(w[i]), o1 = d[i + 1].scale(w[i + 1]);
            quad(b, p[i].subtract(o0), p[i + 1].subtract(o1), p[i + 1].add(o1), p[i].add(o0), c, a[i], a[i + 1], a[i + 1], a[i]);
        }
    }

    /** Glow band + bright core, the standard "light ribbon". */
    private static void ribbon(BufferBuilder b, Vec3d[] p, Vec3d[] d, double[] w, double[] a, float[] glow, float[] core) {
        band(b, p, d, w, a, glow);
        double[] w2 = new double[w.length];
        double[] a2 = new double[a.length];
        for (int i = 0; i < w.length; i++) {
            w2[i] = w[i] * 0.3;
            a2[i] = Math.min(1, a[i] * 1.3);
        }
        band(b, p, d, w2, a2, core);
    }

    /** Box centred at o with half extents along the unit axes x, y, z. */
    private static void box(BufferBuilder b, Vec3d o, Vec3d x, Vec3d y, Vec3d z, double hx, double hy, double hz, float[] c, double a) {
        Vec3d[] k = new Vec3d[8];
        for (int i = 0; i < 8; i++) {
            k[i] = o.add(x.scale((i & 1) == 0 ? -hx : hx)).add(y.scale((i & 2) == 0 ? -hy : hy)).add(z.scale((i & 4) == 0 ? -hz : hz));
        }
        int[][] f = {{0, 1, 3, 2}, {4, 5, 7, 6}, {0, 1, 5, 4}, {2, 3, 7, 6}, {0, 2, 6, 4}, {1, 3, 7, 5}};
        for (int[] q : f) {
            quad(b, k[q[0]], k[q[1]], k[q[2]], k[q[3]], c, a, a, a, a);
        }
    }

    /** Glowing box: solid core plus a larger faint shell. */
    private static void glowBox(BufferBuilder b, Vec3d o, Vec3d x, Vec3d y, Vec3d z, double hx, double hy, double hz, float[] core, float[] glow, double a) {
        box(b, o, x, y, z, hx, hy, hz, core, a * 0.55);
        double g = Math.max(0.06, Math.min(hx, Math.min(hy, hz)) * 0.8 + 0.05);
        box(b, o, x, y, z, hx + g, hy + g, hz + g, glow, a * 0.18);
    }

    /** Camera-facing disc with a radial gradient. */
    private static void disc(BufferBuilder b, Vec3d c, double r, Vec3d cam, float[] centre, float[] edge, double a) {
        Vec3d dir = c.subtract(cam).normalize();
        Vec3d right = dir.crossProduct(UP);
        if (right.lengthSquared() < 1e-4) {
            right = new Vec3d(1, 0, 0);
        }
        right = right.normalize();
        Vec3d up = right.crossProduct(dir).normalize();
        int n = 18;
        for (int i = 0; i < n; i++) {
            double a0 = Math.PI * 2 * i / n, a1 = Math.PI * 2 * (i + 1) / n;
            Vec3d e0 = c.add(right.scale(Math.cos(a0) * r)).add(up.scale(Math.sin(a0) * r));
            Vec3d e1 = c.add(right.scale(Math.cos(a1) * r)).add(up.scale(Math.sin(a1) * r));
            v(b, c, centre, a);
            v(b, e0, edge, 0);
            v(b, e1, edge, 0);
            v(b, e1, edge, 0);
        }
    }

    /** Unit vector perpendicular to 'along' that faces the camera (for billboard ribbons). */
    private static Vec3d side(Vec3d p, Vec3d along, Vec3d cam) {
        Vec3d s = along.crossProduct(p.subtract(cam));
        if (s.lengthSquared() < 1e-6) {
            s = along.crossProduct(UP);
        }
        if (s.lengthSquared() < 1e-6) {
            s = new Vec3d(1, 0, 0);
        }
        return s.normalize();
    }

    /** Billboard ribbon along a polyline. */
    private static void beamLine(BufferBuilder b, Vec3d[] p, double w, double a, Vec3d cam, float[] glow, float[] core) {
        Vec3d[] d = new Vec3d[p.length];
        double[] ws = new double[p.length], as = new double[p.length];
        for (int i = 0; i < p.length; i++) {
            Vec3d along = p[Math.min(p.length - 1, i + 1)].subtract(p[Math.max(0, i - 1)]);
            d[i] = side(p[i], along.normalize(), cam);
            ws[i] = w;
            as[i] = a;
        }
        ribbon(b, p, d, ws, as, glow, core);
    }

    private static Vec3d hdir(double x, double z) {
        double l = Math.sqrt(x * x + z * z);
        return l < 1e-4 ? new Vec3d(0, 0, 1) : new Vec3d(x / l, 0, z / l);
    }

    // ------------------------------------------------------------------ meshes
    /** Sweeping crescent slash in a tilted plane. */
    static final class Slash extends Mesh {
        Vec3d c, f, s;
        double r, span, w;

        Slash(Vec3d c, Vec3d facing, double r, double span, double tilt, double w, int life) {
            this.c = c;
            this.f = facing;
            Vec3d right = new Vec3d(-facing.z, 0, facing.x);
            this.s = right.scale(Math.cos(tilt)).add(UP.scale(Math.sin(tilt)));
            this.r = r;
            this.span = span;
            this.w = w;
            this.life = life;
        }

        @Override
        void draw(BufferBuilder b, double t, Vec3d cam) {
            double sweep = Math.min(1, t / 3.0);
            double fade = t < 3 ? 1 : Math.max(0, 1 - (t - 3) / (life - 3));
            if (fade <= 0) {
                return;
            }
            int n = 36;
            double head = -span / 2 + span * sweep;
            Vec3d[] p = new Vec3d[n + 1], d = new Vec3d[n + 1];
            double[] ws = new double[n + 1], as = new double[n + 1];
            for (int i = 0; i <= n; i++) {
                double u = (double) i / n;
                double th = -span / 2 + (head + span / 2) * u;
                Vec3d rad = f.scale(Math.cos(th)).add(s.scale(Math.sin(th)));
                p[i] = c.add(rad.scale(r));
                d[i] = rad;
                ws[i] = w * Math.sin(Math.PI * Math.min(0.999, 0.08 + u * 0.92)) * (0.55 + 0.45 * u);
                as[i] = fade * (0.15 + 0.85 * u);
            }
            ribbon(b, p, d, ws, as, main(), core());
            // second sheet across the arc plane, so the slash reads from every angle
            Vec3d nrm = f.crossProduct(s).normalize();
            Vec3d[] d2 = new Vec3d[n + 1];
            double[] w2 = new double[n + 1], a2 = new double[n + 1];
            for (int i = 0; i <= n; i++) {
                d2[i] = nrm;
                w2[i] = ws[i] * 0.75;
                a2[i] = as[i] * 0.75;
            }
            ribbon(b, p, d2, w2, a2, main(), core());
        }
    }

    /** Expanding shockwave ring on the ground with a short glowing wall. */
    static final class Ring extends Mesh {
        Vec3d c;
        double rMax;
        boolean wall;

        Ring(Vec3d c, double rMax, int life, boolean wall) {
            this.c = c;
            this.rMax = rMax;
            this.life = life;
            this.wall = wall;
        }

        @Override
        void draw(BufferBuilder b, double t, Vec3d cam) {
            double k = Math.min(1, t / life);
            double r = rMax * (1 - (1 - k) * (1 - k));
            double a = (1 - k);
            double bw = 0.35 + rMax * 0.08;
            int n = 56;
            float[] m = main(), co = core();
            Vec3d base = follow != null ? new Vec3d(follow.posX, c.y, follow.posZ) : c;
            for (int i = 0; i < n; i++) {
                double a0 = Math.PI * 2 * i / n, a1 = Math.PI * 2 * (i + 1) / n;
                Vec3d d0 = new Vec3d(Math.cos(a0), 0, Math.sin(a0)), d1 = new Vec3d(Math.cos(a1), 0, Math.sin(a1));
                Vec3d m0 = base.add(d0.scale(r)), m1 = base.add(d1.scale(r));
                Vec3d i0 = base.add(d0.scale(Math.max(0, r - bw))), i1 = base.add(d1.scale(Math.max(0, r - bw)));
                Vec3d o0 = base.add(d0.scale(r + bw * 0.35)), o1 = base.add(d1.scale(r + bw * 0.35));
                quad(b, i0, i1, m1, m0, m, 0, 0, a * 0.8, a * 0.8);
                quad(b, m0, m1, o1, o0, co, a, a, 0, 0);
                if (wall) {
                    double h = 0.9 * (1 - k) + 0.2;
                    quad(b, m0, m1, m1.addVector(0, h, 0), m0.addVector(0, h, 0), m, a * 0.6, a * 0.6, 0, 0);
                }
            }
        }
    }

    /** Billboard beam with a white core and two twisting strands. */
    static final class Beam extends Mesh {
        Vec3d a0, a1;
        double w;

        Beam(Vec3d a0, Vec3d a1, double w, int life) {
            this.a0 = a0;
            this.a1 = a1;
            this.w = w;
            this.life = life;
        }

        @Override
        void draw(BufferBuilder b, double t, Vec3d cam) {
            double fade = fade(t, 1, Math.max(2, life * 0.4));
            Vec3d ax = a1.subtract(a0);
            double len = ax.lengthVector();
            if (len < 0.1) {
                return;
            }
            Vec3d u = ax.normalize();
            int n = Math.max(2, (int) (len / 0.5));
            Vec3d[] p = new Vec3d[n + 1];
            for (int i = 0; i <= n; i++) {
                p[i] = a0.add(u.scale(len * i / n));
            }
            double pulse = 1 + 0.15 * Math.sin(t * 2.7);
            beamLine(b, p, w * pulse, fade, cam, main(), core());
            // strands
            Vec3d px = u.crossProduct(UP);
            if (px.lengthSquared() < 1e-4) {
                px = new Vec3d(1, 0, 0);
            }
            px = px.normalize();
            Vec3d py = u.crossProduct(px).normalize();
            for (int s = 0; s < 2; s++) {
                Vec3d[] q = new Vec3d[n + 1];
                for (int i = 0; i <= n; i++) {
                    double ang = i * 0.7 + t * 0.8 + s * Math.PI;
                    q[i] = p[i].add(px.scale(Math.cos(ang) * w * 0.9)).add(py.scale(Math.sin(ang) * w * 0.9));
                }
                beamLine(b, q, w * 0.22, fade * 0.8, cam, main(), core());
            }
            disc(b, a0, w * 2.2 * pulse, cam, core(), main(), fade);
            disc(b, a1, w * 2.8 * pulse, cam, core(), main(), fade);
        }
    }

    /** Vertical light pillar growing upwards. */
    static final class Pillar extends Mesh {
        Vec3d c;
        double h, r;

        Pillar(Vec3d c, double h, double r, int life) {
            this.c = c;
            this.h = h;
            this.r = r;
            this.life = life;
        }

        @Override
        void draw(BufferBuilder b, double t, Vec3d cam) {
            double fade = fade(t, 0, life * 0.5);
            double hh = h * Math.min(1, t / 3.0);
            int n = 20;
            Vec3d base = follow != null ? new Vec3d(follow.posX, follow.posY, follow.posZ) : c;
            for (int layer = 0; layer < 2; layer++) {
                double rr = layer == 0 ? r : r * 0.45;
                float[] col = layer == 0 ? main() : core();
                double a = fade * (layer == 0 ? 0.55 : 0.9);
                for (int i = 0; i < n; i++) {
                    double a0 = Math.PI * 2 * i / n, a1 = Math.PI * 2 * (i + 1) / n;
                    Vec3d p0 = base.addVector(Math.cos(a0) * rr, 0, Math.sin(a0) * rr), p1 = base.addVector(Math.cos(a1) * rr, 0, Math.sin(a1) * rr);
                    quad(b, p0, p1, p1.addVector(0, hh, 0), p0.addVector(0, hh, 0), col, a, a, 0, 0);
                }
            }
            disc(b, base.addVector(0, 0.15, 0), r * 2.5, cam, core(), main(), fade * 0.8);
        }
    }

    /** Giant spectral sword falling from the sky and staying stuck in the ground. */
    static final class Sword extends Mesh {
        Vec3d tip, face;
        double len;
        int fall;

        Sword(Vec3d tip, Vec3d facing, double len, int fall, int life) {
            this.tip = tip;
            this.face = facing;
            this.len = len;
            this.fall = fall;
            this.life = life;
        }

        @Override
        void draw(BufferBuilder b, double t, Vec3d cam) {
            double k = Math.min(1, t / fall);
            double drop = 18 * (1 - k * k);
            double fade = fade(t, 2, life * 0.35);
            Vec3d x = new Vec3d(-face.z, 0, face.x), y = UP, z = face;
            Vec3d t0 = tip.addVector(0, drop, 0);
            double W = len * 0.11, T = W * 0.22;
            float[] co = core(), m = main();
            // blade (with a narrower tip section)
            glowBox(b, t0.add(y.scale(len * 0.5)), x, y, z, W / 2, len * 0.42, T, co, m, fade);
            glowBox(b, t0.add(y.scale(len * 0.06)), x, y, z, W * 0.28, len * 0.07, T, co, m, fade);
            // guard, grip, pommel
            glowBox(b, t0.add(y.scale(len * 0.93)), x, y, z, len * 0.2, len * 0.025, T * 2.2, co, m, fade);
            glowBox(b, t0.add(y.scale(len * 1.05)), x, y, z, W * 0.18, len * 0.1, W * 0.18, m, m, fade);
            glowBox(b, t0.add(y.scale(len * 1.17)), x, y, z, W * 0.3, W * 0.3, W * 0.3, co, m, fade);
            // falling trail and landing glow
            if (k < 1) {
                Vec3d[] p = {t0.add(y.scale(len)), t0.add(y.scale(len + 8 * (1 - k)))};
                beamLine(b, p, W * 1.2, fade * 0.6, cam, m, co);
            } else {
                disc(b, tip.addVector(0, 0.2, 0), len * 0.45, cam, co, m, fade * 0.7);
            }
        }
    }

    /** Spectral spear flying from a0 along dir (blocks per tick = speed), with a light trail. */
    static final class Spear extends Mesh {
        Vec3d a0, dir;
        double speed, len, maxDist;

        Spear(Vec3d a0, Vec3d dir, double speed, double len, double maxDist, int life) {
            this.a0 = a0;
            this.dir = dir.normalize();
            this.speed = speed;
            this.len = len;
            this.maxDist = maxDist;
            this.life = life;
        }

        @Override
        void draw(BufferBuilder b, double t, Vec3d cam) {
            double dist = Math.min(maxDist, speed * t);
            double fade = fade(t, 0, 4);
            Vec3d head = a0.add(dir.scale(dist));
            Vec3d x = dir.crossProduct(UP);
            if (x.lengthSquared() < 1e-4) {
                x = new Vec3d(1, 0, 0);
            }
            x = x.normalize();
            Vec3d y = x.crossProduct(dir).normalize();
            double th = len * 0.035;
            float[] co = core(), m = main();
            glowBox(b, head.subtract(dir.scale(len * 0.5)), x, y, dir, th, th, len * 0.42, co, m, fade);
            glowBox(b, head.subtract(dir.scale(len * 0.05)), x, y, dir, th * 3, th * 1.2, len * 0.08, co, m, fade);
            glowBox(b, head.subtract(dir.scale(len * 0.16)), x, y, dir, th * 4.5, th * 1.2, th * 1.2, co, m, fade);
            Vec3d tail = head.subtract(dir.scale(len + Math.min(dist, 10)));
            beamLine(b, new Vec3d[]{tail, head.subtract(dir.scale(len * 0.8))}, th * 6, fade * 0.7, cam, m, co);
        }
    }

    /** Glowing fissure growing along the ground, with side branches. */
    static final class Crack extends Mesh {
        Vec3d[] pts;
        Vec3d[][] branches;

        Crack(Vec3d start, Vec3d dir, double len, long seed, int life) {
            Random r = new Random(seed);
            Vec3d f = hdir(dir.x, dir.z), s = new Vec3d(-f.z, 0, f.x);
            int n = Math.max(4, (int) (len / 0.8));
            pts = new Vec3d[n + 1];
            for (int i = 0; i <= n; i++) {
                double off = i == 0 ? 0 : (r.nextDouble() - 0.5) * 0.9;
                pts[i] = start.add(f.scale(len * i / n)).add(s.scale(off)).addVector(0, 0.05, 0);
            }
            branches = new Vec3d[Math.max(1, n / 4)][];
            for (int k = 0; k < branches.length; k++) {
                Vec3d o = pts[1 + r.nextInt(n - 1)];
                Vec3d bd = f.scale(0.6).add(s.scale(r.nextBoolean() ? 1 : -1)).normalize();
                branches[k] = new Vec3d[]{o, o.add(bd.scale(1.0 + r.nextDouble())), o.add(bd.scale(2.0 + r.nextDouble() * 1.5)).add(s.scale((r.nextDouble() - 0.5)))};
            }
            this.life = life;
        }

        private void line(BufferBuilder b, Vec3d[] p, int upTo, double fade, double w) {
            int m = Math.max(2, Math.min(p.length, upTo));
            Vec3d[] q = new Vec3d[m];
            Vec3d[] d = new Vec3d[m];
            double[] ws = new double[m], as = new double[m];
            for (int i = 0; i < m; i++) {
                q[i] = p[i];
                Vec3d along = p[Math.min(p.length - 1, i + 1)].subtract(p[Math.max(0, i - 1)]);
                d[i] = new Vec3d(-along.z, 0, along.x).normalize();
                ws[i] = w * (1 - 0.6 * i / (double) m);
                as[i] = fade;
            }
            ribbon(b, q, d, ws, as, main(), core());
            Vec3d[] up = new Vec3d[m];
            double[] wu = new double[m], au = new double[m];
            for (int i = 0; i < m; i++) {
                up[i] = UP;
                wu[i] = w * 0.6;
                au[i] = fade * 0.55;
            }
            band(b, q, up, wu, au, main());
        }

        @Override
        void draw(BufferBuilder b, double t, Vec3d cam) {
            double grow = Math.min(1, t / (life * 0.25));
            double fade = fade(t, 0, life * 0.45);
            line(b, pts, (int) Math.ceil(pts.length * grow), fade, 0.5);
            if (grow >= 1) {
                for (Vec3d[] br : branches) {
                    line(b, br, br.length, fade * 0.8, 0.3);
                }
            }
        }
    }

    /** Magic circle on the ground: rings, ticks and a rotating hexagram. */
    static final class Rune extends Mesh {
        Vec3d c;
        double r;

        Rune(Vec3d c, double r, int life) {
            this.c = c;
            this.r = r;
            this.life = life;
        }

        private void circle(BufferBuilder b, Vec3d o, double rr, double w, double a, float[] col) {
            int n = 48;
            for (int i = 0; i < n; i++) {
                double a0 = Math.PI * 2 * i / n, a1 = Math.PI * 2 * (i + 1) / n;
                Vec3d d0 = new Vec3d(Math.cos(a0), 0, Math.sin(a0)), d1 = new Vec3d(Math.cos(a1), 0, Math.sin(a1));
                quad(b, o.add(d0.scale(rr - w)), o.add(d1.scale(rr - w)), o.add(d1.scale(rr + w)), o.add(d0.scale(rr + w)), col, a, a, a, a);
            }
        }

        private void seg(BufferBuilder b, Vec3d p0, Vec3d p1, double w, double a, float[] col) {
            Vec3d along = p1.subtract(p0);
            Vec3d d = new Vec3d(-along.z, 0, along.x).normalize().scale(w);
            quad(b, p0.subtract(d), p1.subtract(d), p1.add(d), p0.add(d), col, a, a, a, a);
        }

        @Override
        void draw(BufferBuilder b, double t, Vec3d cam) {
            double fade = fade(t, 4, 6);
            Vec3d o = (follow != null ? new Vec3d(follow.posX, follow.posY, follow.posZ) : c).addVector(0, 0.06, 0);
            float[] m = main(), co = core();
            double rr = r * (0.85 + 0.15 * Math.min(1, t / 4));
            circle(b, o, rr, 0.05 + r * 0.008, fade * 0.9, co);
            circle(b, o, rr * 0.86, 0.035, fade * 0.7, m);
            circle(b, o, rr * 0.3, 0.035, fade * 0.7, m);
            double rot = t * 0.06;
            for (int i = 0; i < 24; i++) {
                double a = rot + Math.PI * 2 * i / 24;
                Vec3d d = new Vec3d(Math.cos(a), 0, Math.sin(a));
                seg(b, o.add(d.scale(rr * 0.87)), o.add(d.scale(rr * (i % 3 == 0 ? 0.99 : 0.94))), 0.03, fade * 0.8, co);
            }
            for (int tri = 0; tri < 2; tri++) {
                for (int i = 0; i < 3; i++) {
                    double a0 = -rot * 1.5 + tri * Math.PI / 3 + Math.PI * 2 * i / 3, a1 = a0 + Math.PI * 2 / 3;
                    seg(b, o.addVector(Math.cos(a0) * rr * 0.84, 0, Math.sin(a0) * rr * 0.84),
                            o.addVector(Math.cos(a1) * rr * 0.84, 0, Math.sin(a1) * rr * 0.84), 0.035, fade * 0.75, m);
                }
            }
            disc(b, o.addVector(0, 0.05, 0), rr * 0.5, cam, m, m, fade * 0.25);
        }
    }

    /** Rising spiral ribbons (tornado / aura). */
    static final class Vortex extends Mesh {
        Vec3d c;
        double r, h;

        Vortex(Vec3d c, double r, double h, int life) {
            this.c = c;
            this.r = r;
            this.h = h;
            this.life = life;
        }

        @Override
        void draw(BufferBuilder b, double t, Vec3d cam) {
            double fade = fade(t, 3, 6);
            Vec3d o = follow != null ? new Vec3d(follow.posX, follow.posY, follow.posZ) : c;
            int n = 28;
            for (int s = 0; s < 3; s++) {
                Vec3d[] p = new Vec3d[n + 1], d = new Vec3d[n + 1];
                double[] ws = new double[n + 1], as = new double[n + 1];
                for (int i = 0; i <= n; i++) {
                    double u = (double) i / n;
                    double ang = s * Math.PI * 2 / 3 + u * 5 + t * 0.45;
                    double rr = r * (0.55 + 0.45 * u);
                    p[i] = o.addVector(Math.cos(ang) * rr, u * h, Math.sin(ang) * rr);
                    d[i] = UP;
                    ws[i] = 0.18 + 0.12 * u;
                    as[i] = fade * (0.9 - 0.7 * u);
                }
                ribbon(b, p, d, ws, as, main(), core());
            }
        }
    }

    /** Glowing dome of latitude rings (shield, bell). */
    static final class Dome extends Mesh {
        Vec3d c;
        double r;
        boolean full;

        Dome(Vec3d c, double r, boolean full, int life) {
            this.c = c;
            this.r = r;
            this.full = full;
            this.life = life;
        }

        @Override
        void draw(BufferBuilder b, double t, Vec3d cam) {
            double fade = fade(t, 3, 6);
            Vec3d o = follow != null ? new Vec3d(follow.posX, follow.posY + (full ? 1.0 : 0), follow.posZ) : c;
            double pulse = 1 + 0.04 * Math.sin(t * 0.8);
            int lat = 7, n = 32;
            float[] m = main();
            for (int j = full ? -lat + 1 : 0; j < lat; j++) {
                double phi = Math.PI / 2 * j / lat;
                double rr = r * pulse * Math.cos(phi), y = r * pulse * Math.sin(phi);
                double w = 0.06;
                for (int i = 0; i < n; i++) {
                    double a0 = Math.PI * 2 * i / n + t * 0.02 * (j % 2 == 0 ? 1 : -1), a1 = a0 + Math.PI * 2 / n;
                    Vec3d p0 = o.addVector(Math.cos(a0) * rr, y, Math.sin(a0) * rr), p1 = o.addVector(Math.cos(a1) * rr, y, Math.sin(a1) * rr);
                    quad(b, p0, p1, p1.addVector(0, w, 0), p0.addVector(0, w, 0), m, fade * 0.6, fade * 0.6, fade * 0.6, fade * 0.6);
                }
            }
            disc(b, o, r * 1.1, cam, m, m, fade * 0.18);
        }
    }

    /** Glowing orb, optionally travelling from a0 to a1 (meteor) with a trail. */
    static final class Orb extends Mesh {
        Vec3d a0, a1;
        double r;

        Orb(Vec3d a0, Vec3d a1, double r, int life) {
            this.a0 = a0;
            this.a1 = a1;
            this.r = r;
            this.life = life;
        }

        @Override
        void draw(BufferBuilder b, double t, Vec3d cam) {
            double k = Math.min(1, t / life);
            Vec3d p = a0.add(a1.subtract(a0).scale(k));
            boolean moving = a1.squareDistanceTo(a0) > 0.01;
            double fade = moving ? 1 : 1 - k;
            double rr = moving ? r : r * (0.6 + 0.8 * k);
            disc(b, p, rr * 2.2, cam, main(), dark(), fade * 0.5);
            disc(b, p, rr, cam, core(), main(), fade);
            if (moving) {
                Vec3d back = a0.subtract(a1).normalize();
                beamLine(b, new Vec3d[]{p, p.add(back.scale(4 + r * 2))}, rr * 0.8, fade * 0.7, cam, main(), core());
            }
        }
    }

    /** Crescent wave travelling forward. */
    static final class Crescent extends Mesh {
        Vec3d c, f;
        double w, speed;

        Crescent(Vec3d c, Vec3d f, double w, double speed, int life) {
            this.c = c;
            this.f = hdir(f.x, f.z);
            this.w = w;
            this.speed = speed;
            this.life = life;
        }

        @Override
        void draw(BufferBuilder b, double t, Vec3d cam) {
            double fade = fade(t, 1, Math.max(2, life * 0.3));
            Vec3d s = new Vec3d(-f.z, 0, f.x);
            Vec3d o = c.add(f.scale(speed * t));
            int n = 24;
            Vec3d[] p = new Vec3d[n + 1], d = new Vec3d[n + 1];
            double[] ws = new double[n + 1], as = new double[n + 1];
            for (int i = 0; i <= n; i++) {
                double u = (double) i / n * 2 - 1;
                p[i] = o.add(s.scale(u * w / 2)).add(f.scale(-u * u * w * 0.35)).addVector(0, (1 - u * u) * 0.35, 0);
                d[i] = f;
                ws[i] = 0.55 * (1 - u * u) + 0.05;
                as[i] = fade * (1 - Math.abs(u) * 0.6);
            }
            ribbon(b, p, d, ws, as, main(), core());
            // vertical sheen
            Vec3d[] q = new Vec3d[n + 1];
            for (int i = 0; i <= n; i++) {
                q[i] = p[i];
                d[i] = UP;
                ws[i] *= 1.4;
                as[i] *= 0.5;
            }
            band(b, q, d, ws, as, main());
        }
    }

    /** A row of spectral spears rising from the ground (phalanx wall). */
    static final class Wall extends Mesh {
        Vec3d c, f;
        double width;

        Wall(Vec3d c, Vec3d f, double width, int life) {
            this.c = c;
            this.f = hdir(f.x, f.z);
            this.width = width;
            this.life = life;
        }

        @Override
        void draw(BufferBuilder b, double t, Vec3d cam) {
            double rise = Math.min(1, t / 4.0);
            double fade = fade(t, 0, 8);
            Vec3d s = new Vec3d(-f.z, 0, f.x);
            int n = Math.max(5, (int) (width * 1.6));
            Vec3d dir = f.scale(0.75).add(UP.scale(0.66)).normalize();
            Vec3d x = s, y = x.crossProduct(dir).normalize();
            for (int i = 0; i < n; i++) {
                double u = (double) i / (n - 1) - 0.5;
                double len = 2.4 + 0.4 * Math.sin(i * 1.7);
                Vec3d base = c.add(s.scale(u * width)).add(f.scale(Math.abs(u) * -0.6));
                Vec3d mid = base.add(dir.scale(len * 0.5 * rise - len * 0.5 * (1 - rise)));
                glowBox(b, mid, x, y, dir, 0.05, 0.05, len * 0.5, core(), main(), fade);
                glowBox(b, mid.add(dir.scale(len * 0.5)), x, y, dir, 0.14, 0.04, 0.22, core(), main(), fade);
            }
        }
    }

    /** Radial light rays (impact star). */
    static final class Shock extends Mesh {
        Vec3d c;
        double r;
        Vec3d[] rays;

        Shock(Vec3d c, double r, long seed, int life) {
            this.c = c;
            this.r = r;
            Random rr = new Random(seed);
            rays = new Vec3d[12];
            for (int i = 0; i < rays.length; i++) {
                double a = Math.PI * 2 * i / rays.length + rr.nextDouble() * 0.3;
                rays[i] = new Vec3d(Math.cos(a), (rr.nextDouble() - 0.3) * 0.6, Math.sin(a)).normalize().scale(0.6 + rr.nextDouble() * 0.6);
            }
            this.life = life;
        }

        @Override
        void draw(BufferBuilder b, double t, Vec3d cam) {
            double k = Math.min(1, t / life);
            double fade = 1 - k;
            for (Vec3d ray : rays) {
                Vec3d p0 = c.add(ray.scale(r * (0.15 + 0.5 * k))), p1 = c.add(ray.scale(r * (0.4 + 0.9 * k)));
                beamLine(b, new Vec3d[]{p0, p1}, 0.07 + r * 0.02, fade, cam, main(), core());
            }
            disc(b, c, r * 0.6 * (1 - k * 0.5), cam, core(), main(), fade);
        }
    }

    /** Flickering jagged lightning between two points. */
    static final class Bolt extends Mesh {
        Vec3d a0, a1;
        double w;
        long seed;

        Bolt(Vec3d a0, Vec3d a1, double w, long seed, int life) {
            this.a0 = a0;
            this.a1 = a1;
            this.w = w;
            this.seed = seed;
            this.life = life;
        }

        @Override
        void draw(BufferBuilder b, double t, Vec3d cam) {
            Random r = new Random(seed + (int) (t / 2));
            double fade = fade(t, 0, life * 0.5) * (0.7 + 0.3 * r.nextDouble());
            Vec3d d = a1.subtract(a0);
            double len = d.lengthVector();
            int n = Math.max(4, (int) (len / 0.8));
            Vec3d[] p = new Vec3d[n + 1];
            double jit = Math.min(1.1, len * 0.1);
            for (int i = 0; i <= n; i++) {
                double u = (double) i / n;
                Vec3d o = (i == 0 || i == n) ? Vec3d.ZERO : new Vec3d((r.nextDouble() - 0.5) * jit, (r.nextDouble() - 0.5) * jit, (r.nextDouble() - 0.5) * jit);
                p[i] = a0.add(d.scale(u)).add(o);
            }
            beamLine(b, p, w, fade, cam, main(), core());
            disc(b, a1, w * 4, cam, core(), main(), fade);
        }
    }

    /** Two blades orbiting an entity (whirlwind). */
    static final class Spin extends Mesh {
        Vec3d c;
        double r;

        Spin(Vec3d c, double r, int life) {
            this.c = c;
            this.r = r;
            this.life = life;
        }

        @Override
        void draw(BufferBuilder b, double t, Vec3d cam) {
            double fade = fade(t, 1, 4);
            Vec3d o = (follow != null ? new Vec3d(follow.posX, follow.posY, follow.posZ) : c).addVector(0, 1.0, 0);
            int n = 18;
            for (int s = 0; s < 2; s++) {
                double head = t * 1.1 + s * Math.PI;
                Vec3d[] p = new Vec3d[n + 1], d = new Vec3d[n + 1];
                double[] ws = new double[n + 1], as = new double[n + 1];
                for (int i = 0; i <= n; i++) {
                    double u = (double) i / n;
                    double a = head - 2.2 * (1 - u);
                    Vec3d rad = new Vec3d(Math.cos(a), 0, Math.sin(a));
                    p[i] = o.add(rad.scale(r)).addVector(0, Math.sin(a * 0.5 + s) * 0.25, 0);
                    d[i] = rad;
                    ws[i] = 0.5 * u + 0.05;
                    as[i] = fade * u;
                }
                ribbon(b, p, d, ws, as, main(), core());
            }
        }
    }

    /** Afterimage wall left by a dash. */
    static final class Trail extends Mesh {
        Vec3d a0, a1;

        Trail(Vec3d a0, Vec3d a1, int life) {
            this.a0 = a0;
            this.a1 = a1;
            this.life = life;
        }

        @Override
        void draw(BufferBuilder b, double t, Vec3d cam) {
            double fade = 1 - Math.min(1, t / life);
            int n = 10;
            Vec3d[] p = new Vec3d[n + 1], d = new Vec3d[n + 1];
            double[] ws = new double[n + 1], as = new double[n + 1];
            for (int i = 0; i <= n; i++) {
                double u = (double) i / n;
                p[i] = a0.add(a1.subtract(a0).scale(u)).addVector(0, 0.95, 0);
                d[i] = UP;
                ws[i] = 0.95;
                as[i] = fade * u * 0.7;
            }
            band(b, p, d, ws, as, main());
        }
    }

    // ------------------------------------------------------------------ entry from FxClient
    public static void fromMsg(SkillNet.MsgFx m, Entity follow) {
        Vec3d pos = new Vec3d(m.x, m.y, m.z);
        Vec3d a = new Vec3d(m.ax, m.ay, m.az);
        int el = m.element;
        Mesh mesh = null;
        switch (m.kind) {
            case Fx.SLASH:
                mesh = new Slash(pos, hdir(m.ax, m.az), m.size, m.extra > 0 ? m.extra : 2.4, (m.seed & 1) == 0 ? 0.45 : -0.45, 0.55 + m.size * 0.25, 9);
                break;
            case Fx.XSLASH: {
                Vec3d f = hdir(m.ax, m.az);
                add(withEl(new Slash(pos.subtract(f.scale(m.size)), f, m.size * 1.4, 1.1, 0.9, 0.3, 8), el));
                mesh = new Slash(pos.subtract(f.scale(m.size)), f, m.size * 1.4, 1.1, -0.9, 0.3, 9);
                break;
            }
            case Fx.RING:
                mesh = new Ring(pos.addVector(0, 0.08, 0), m.size, Math.max(4, m.dur + 2), true);
                break;
            case Fx.NOVA:
                add(withEl(new Shock(pos, m.size, m.seed, 7), el));
                mesh = new Orb(pos, pos, m.size * 0.5, 6);
                break;
            case Fx.BEAM:
                mesh = new Beam(pos, a, Math.max(0.15, m.size * 0.5), Math.max(3, m.dur + 2));
                break;
            case Fx.EXPLOSION:
                add(withEl(new Orb(pos.addVector(0, 0.5, 0), pos.addVector(0, 0.5, 0), m.size * 0.8, 7), el));
                add(withEl(new Shock(pos.addVector(0, 0.5, 0), m.size * 1.2, m.seed, 7), el));
                mesh = new Ring(pos.addVector(0, 0.08, 0), m.size * 1.4, 8, true);
                break;
            case Fx.PILLAR:
                mesh = new Pillar(pos, m.size, Math.max(0.3, m.extra * 0.5), Math.max(6, m.dur + 4));
                break;
            case Fx.DASH:
                mesh = new Trail(pos, a, 8);
                break;
            case Fx.IMPACT:
                mesh = new Shock(pos, Math.max(0.6, m.size), m.seed, 4);
                break;
            case Fx.METEOR:
                add(withEl(new Orb(pos.addVector(-6, 22, -3), pos, 1.0 + m.size * 0.15, Math.max(4, m.dur)), el));
                mesh = new Rune(pos, m.size, Math.max(4, m.dur));
                break;
            case Fx.RUNES:
                mesh = new Rune(pos, m.size, Math.max(6, m.dur));
                break;
            case Fx.FIELD:
                mesh = new Rune(pos, m.size, Math.max(6, m.dur));
                break;
            case Fx.CRESCENT:
                mesh = new Crescent(pos, a, m.size, m.extra > 0 ? m.extra : 0.8, Math.max(3, m.dur));
                break;
            case Fx.LIGHTNING:
                mesh = new Bolt(pos, a, 0.12 * (m.size > 0 ? m.size : 1), m.seed, 5);
                break;
            case Fx.HEAL:
                mesh = new Pillar(pos, 2.5, Math.max(0.4, m.size * 0.4), 10);
                break;
            case Fx.AURA:
                mesh = new Vortex(pos, Math.max(0.6, m.size), 2.0, Math.max(6, m.dur));
                break;
            case Fx.TIERUP:
                mesh = new Ring(pos.addVector(0, 1.0, 0), 1.6 + m.extra * 0.4, 5, false);
                break;
            case Fx.SPIN:
                mesh = new Spin(pos, Math.max(1.0, m.size), Math.max(4, m.dur));
                break;
            case Fx.BARRAGE: {
                Vec3d f = hdir(RNG.nextDouble() - 0.5, RNG.nextDouble() - 0.5);
                mesh = new Slash(pos.subtract(f.scale(m.size)), f, m.size * 1.3, 1.0, (RNG.nextDouble() - 0.5) * 2.4, 0.22, 6);
                break;
            }
            case Fx.FLASH:
                mesh = new Orb(pos, pos, 0.4 + m.size * 0.6, 4);
                break;
            case Fx.M_SWORD:
                mesh = new Sword(pos, hdir(m.ax, m.az), m.size, Math.max(2, (int) m.extra), Math.max(8, m.dur));
                break;
            case Fx.M_SPEAR:
                mesh = new Spear(pos, a, m.extra > 0 ? m.extra : 1.5, m.size, m.size * 0 + 40, Math.max(4, m.dur));
                break;
            case Fx.M_CRACK:
                mesh = new Crack(pos, a, m.size, m.seed, Math.max(10, m.dur));
                break;
            case Fx.M_VORTEX:
                mesh = new Vortex(pos, m.size, m.extra > 0 ? m.extra : 3, Math.max(6, m.dur));
                break;
            case Fx.M_DOME:
                mesh = new Dome(pos, m.size, m.extra > 0, Math.max(6, m.dur));
                break;
            case Fx.M_WALL:
                mesh = new Wall(pos, a, m.size, Math.max(8, m.dur));
                break;
            case Fx.M_ORB:
                mesh = new Orb(pos, a.lengthSquared() > 0 ? a : pos, m.size, Math.max(3, m.dur));
                break;
            case Fx.M_BOLT:
                mesh = new Bolt(pos, a, Math.max(0.1, m.size), m.seed, Math.max(3, m.dur));
                break;
            case Fx.SCREEN_FLASH:
            case Fx.VIGNETTE:
                return; // full-screen overlays removed (player feedback: they covered the screen)
            default:
                return;
        }
        if (mesh != null) {
            mesh.el = el;
            if (follow != null && (m.kind == Fx.AURA || m.kind == Fx.SPIN || m.kind == Fx.M_VORTEX || m.kind == Fx.M_DOME || m.kind == Fx.HEAL)) {
                mesh.follow = follow;
            }
            add(mesh);
        }
    }

    private static Mesh withEl(Mesh m, int el) {
        m.el = el;
        return m;
    }

    // ------------------------------------------------------------------ tick and render
    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null) {
            MESHES.clear();
            return;
        }
        if (mc.isGamePaused()) {
            return;
        }
        MESHES.removeIf(m -> ++m.age >= m.life || (m.follow != null && m.follow.isDead && m.age > 2));
        if (flashLeft > 0) {
            flashLeft--;
        }
        if (vigLeft > 0) {
            vigLeft--;
        }
    }

    @SubscribeEvent
    public void onRender(RenderWorldLastEvent e) {
        if (MESHES.isEmpty() || DebugToggles.noMesh) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        Entity view = mc.getRenderViewEntity();
        if (view == null) {
            return;
        }
        float pt = e.getPartialTicks();
        double cx = view.lastTickPosX + (view.posX - view.lastTickPosX) * pt;
        double fy = view.lastTickPosY + (view.posY - view.lastTickPosY) * pt;
        double cz = view.lastTickPosZ + (view.posZ - view.lastTickPosZ) * pt;
        Vec3d cam = new Vec3d(cx, fy + view.getEyeHeight(), cz);
        // Remember the GL state to put back exactly: forcing fog on here left it on for the framebuffer blit, which
        // then drew the whole screen in the fog colour (screen flashed sky blue / black on every skill).
        boolean wasFog = GL11.glIsEnabled(GL11.GL_FOG), wasAlpha = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
        boolean wasCull = GL11.glIsEnabled(GL11.GL_CULL_FACE), wasTex = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
        boolean wasLight = GL11.glIsEnabled(GL11.GL_LIGHTING), wasBlend = GL11.glIsEnabled(GL11.GL_BLEND);
        GlStateManager.setActiveTexture(net.minecraft.client.renderer.OpenGlHelper.lightmapTexUnit);
        boolean wasLightmap = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
        GlStateManager.setActiveTexture(net.minecraft.client.renderer.OpenGlHelper.defaultTexUnit);
        GlStateManager.pushMatrix();
        // the world is drawn relative to the viewer's feet position
        GlStateManager.translate(-cx, -fy, -cz);
        mc.entityRenderer.disableLightmap();
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.disableCull();
        GlStateManager.disableAlpha();
        GlStateManager.disableFog();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        GlStateManager.depthMask(false);
        GlStateManager.shadeModel(GL11.GL_SMOOTH);
        Tessellator tes = Tessellator.getInstance();
        BufferBuilder b = tes.getBuffer();
        // Each mesh is drawn on its own and its vertices are checked first: a non-finite or far-away vertex made the
        // driver drop the whole frame (screen flashed to the fog colour on skill use), so such a mesh is skipped.
        int stride = DefaultVertexFormats.POSITION_COLOR.getNextOffset();
        for (Mesh m : MESHES) {
            b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
            boolean ok = true;
            try {
                m.draw(b, m.age + pt, cam);
            } catch (RuntimeException ex) {
                ok = false;
            }
            int n = b.getVertexCount();
            if (ok && n % 4 != 0) {
                ok = false;
            }
            java.nio.ByteBuffer bb = b.getByteBuffer();
            for (int i = 0; ok && i < n; i++) {
                int o = i * stride;
                float x = bb.getFloat(o), y = bb.getFloat(o + 4), z = bb.getFloat(o + 8);
                if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)
                        || Math.abs(x - cam.x) > 256 || Math.abs(y - cam.y) > 256 || Math.abs(z - cam.z) > 256) {
                    ok = false;
                    DebugToggles.report("bad mesh " + m.getClass().getSimpleName() + " vertex " + x + " " + y + " " + z);
                }
            }
            if (ok) {
                tes.draw();
            } else {
                b.finishDrawing();
                b.reset();
                m.age = m.life;
            }
        }
        GlStateManager.shadeModel(GL11.GL_FLAT);
        GlStateManager.depthMask(true);
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        if (!wasBlend) {
            GlStateManager.disableBlend();
        }
        if (wasFog) {
            GlStateManager.enableFog();
        }
        if (wasAlpha) {
            GlStateManager.enableAlpha();
        }
        if (wasCull) {
            GlStateManager.enableCull();
        }
        if (wasTex) {
            GlStateManager.enableTexture2D();
        }
        if (wasLight) {
            GlStateManager.enableLighting();
        }
        if (wasLightmap) {
            mc.entityRenderer.enableLightmap();
        }
        GlStateManager.popMatrix();
    }

    @SubscribeEvent
    public void onOverlay(RenderGameOverlayEvent.Post e) {
        if (e.getType() != RenderGameOverlayEvent.ElementType.ALL || (flashLeft <= 0 && vigLeft <= 0)) {
            return;
        }
        ScaledResolution sr = e.getResolution();
        double w = sr.getScaledWidth_double(), h = sr.getScaledHeight_double();
        float pt = e.getPartialTicks();
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.disableAlpha();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        GlStateManager.shadeModel(GL11.GL_SMOOTH);
        Tessellator tes = Tessellator.getInstance();
        BufferBuilder b = tes.getBuffer();
        b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        if (vigLeft > 0) {
            float a = vigPow * Math.min(1, (vigLeft - pt) / 4F) * Math.min(1, (vigDur - vigLeft + pt) / 3F);
            double ex = w * 0.28, ey = h * 0.28;
            // four gradient strips from the edges towards the centre
            b.pos(0, 0, 0).color(0F, 0F, 0F, a).endVertex();
            b.pos(0, h, 0).color(0F, 0F, 0F, a).endVertex();
            b.pos(ex, h, 0).color(0F, 0F, 0F, 0F).endVertex();
            b.pos(ex, 0, 0).color(0F, 0F, 0F, 0F).endVertex();
            b.pos(w - ex, 0, 0).color(0F, 0F, 0F, 0F).endVertex();
            b.pos(w - ex, h, 0).color(0F, 0F, 0F, 0F).endVertex();
            b.pos(w, h, 0).color(0F, 0F, 0F, a).endVertex();
            b.pos(w, 0, 0).color(0F, 0F, 0F, a).endVertex();
            b.pos(0, 0, 0).color(0F, 0F, 0F, a).endVertex();
            b.pos(0, ey, 0).color(0F, 0F, 0F, 0F).endVertex();
            b.pos(w, ey, 0).color(0F, 0F, 0F, 0F).endVertex();
            b.pos(w, 0, 0).color(0F, 0F, 0F, a).endVertex();
            b.pos(0, h - ey, 0).color(0F, 0F, 0F, 0F).endVertex();
            b.pos(0, h, 0).color(0F, 0F, 0F, a).endVertex();
            b.pos(w, h, 0).color(0F, 0F, 0F, a).endVertex();
            b.pos(w, h - ey, 0).color(0F, 0F, 0F, 0F).endVertex();
        }
        if (flashLeft > 0) {
            float a = flashPow * Math.max(0, (flashLeft - pt) / flashDur);
            b.pos(0, 0, 0).color(flashCol[0], flashCol[1], flashCol[2], a).endVertex();
            b.pos(0, h, 0).color(flashCol[0], flashCol[1], flashCol[2], a).endVertex();
            b.pos(w, h, 0).color(flashCol[0], flashCol[1], flashCol[2], a).endVertex();
            b.pos(w, 0, 0).color(flashCol[0], flashCol[1], flashCol[2], a).endVertex();
        }
        tes.draw();
        GlStateManager.shadeModel(GL11.GL_FLAT);
        GlStateManager.enableAlpha();
        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();
    }
}
