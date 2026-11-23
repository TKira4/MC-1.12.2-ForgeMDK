package com.imba.undyingarcana;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Client-side renderer of every skill effect. Effects are built from FxParticle (tinted vanilla particle sprites)
 * plus a few vanilla particles (sweep, explosion, block debris). Multi-tick effects run in the "running" list.
 * Every effect uses its element's three-colour palette: core (bright), main, dark.
 */
@SideOnly(Side.CLIENT)
public class FxClient {
    private static final Random RNG = new Random();
    private static float shakePower;
    private static int shakeLeft;
    private static int shakeTotal = 1;

    private interface Running {
        /** Returns true when the effect is finished. */
        boolean tick(int age);
    }

    private static final class Job {
        final Running r;
        int age;

        Job(Running r) {
            this.r = r;
        }
    }

    private static final List<Job> JOBS = new ArrayList<>();

    // ------------------------------------------------------------------ palettes
    private static final float[][][] PAL = {
            {{1F, 1F, 1F}, {0.82F, 0.9F, 1F}, {0.5F, 0.56F, 0.68F}},          // steel
            {{1F, 0.95F, 0.6F}, {1F, 0.48F, 0.08F}, {0.35F, 0.1F, 0.04F}},    // fire
            {{1F, 1F, 1F}, {0.55F, 0.86F, 1F}, {0.22F, 0.48F, 0.95F}},        // ice
            {{1F, 1F, 1F}, {1F, 0.95F, 0.45F}, {0.5F, 0.6F, 1F}},            // lightning
            {{0.75F, 1F, 0.65F}, {0.32F, 0.85F, 0.4F}, {0.22F, 0.05F, 0.28F}}, // necromancy
            {{1F, 0.9F, 0.6F}, {0.72F, 0.56F, 0.34F}, {0.34F, 0.24F, 0.14F}}, // earth
            {{1F, 0.8F, 1F}, {0.75F, 0.33F, 1F}, {0.2F, 0.04F, 0.35F}},       // sorcery
            {{1F, 1F, 0.85F}, {1F, 0.85F, 0.35F}, {0.5F, 1F, 0.5F}},          // healing
            {{0.95F, 0.85F, 1F}, {0.58F, 0.45F, 1F}, {0.24F, 0.14F, 0.6F}},   // arcane
            {{1F, 0.6F, 0.6F}, {0.9F, 0.08F, 0.1F}, {0.35F, 0F, 0F}},         // blood
            {{0.72F, 0.6F, 0.92F}, {0.35F, 0.24F, 0.5F}, {0.05F, 0.03F, 0.1F}}, // shadow
            {{1F, 1F, 1F}, {0.78F, 1F, 0.95F}, {0.45F, 0.75F, 0.7F}},         // wind
            {{1F, 1F, 0.9F}, {1F, 0.88F, 0.45F}, {1F, 0.66F, 0.18F}},         // holy
            {{0.85F, 1F, 0.5F}, {0.45F, 0.85F, 0.15F}, {0.15F, 0.35F, 0.05F}}, // poison
    };

    private static float[] pal(int el, int which) {
        el = RNG.nextBoolean() ? Fx.primary(el) : Fx.secondary(el);
        float[][] p = PAL[el >= 0 && el < PAL.length ? el : 0];
        float[] c = p[which];
        float j = 0.08F;
        return new float[]{clamp(c[0] + (RNG.nextFloat() - 0.5F) * j), clamp(c[1] + (RNG.nextFloat() - 0.5F) * j),
                clamp(c[2] + (RNG.nextFloat() - 0.5F) * j)};
    }

    /** Palette colour without jitter (0 core, 1 main, 2 dark) - used by the glow meshes. */
    public static float[] palette(int el, int which) {
        el = which == 1 ? Fx.secondary(el) : Fx.primary(el);
        return PAL[el >= 0 && el < PAL.length ? el : 0][which];
    }

    private static float clamp(float v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }

    private static float[] core(int el) {
        return pal(el, 0);
    }

    private static float[] main(int el) {
        return pal(el, 1);
    }

    private static float[] dark(int el) {
        return pal(el, 2);
    }

    // ------------------------------------------------------------------ helpers
    private static World world() {
        return Minecraft.getMinecraft().world;
    }

    /** Particle density from the video setting (All / Decreased / Minimal). */
    private static float dens() {
        int s = Minecraft.getMinecraft().gameSettings.particleSetting;
        return s == 0 ? 1.0F : (s == 1 ? 0.55F : 0.25F);
    }

    private static int n(double count) {
        return Math.max(1, (int) Math.round(count * dens() * distMul));
    }

    // ---- performance guards: a per-tick particle budget and distance falloff for far effects
    private static int spawnedThisTick;
    private static double distMul = 1.0;

    private static int budget() {
        return (int) (900 * dens());
    }

    private static boolean overBudget() {
        return ++spawnedThisTick > budget();
    }

    private static double rnd(double s) {
        return (RNG.nextDouble() - 0.5) * 2.0 * s;
    }

    private static FxParticle P(double x, double y, double z, double vx, double vy, double vz, int sheet) {
        World w = world();
        FxParticle p = new FxParticle(w, x, y, z, vx, vy, vz, sheet);
        if (!Double.isFinite(x + y + z + vx + vy + vz)) {
            DebugToggles.report("non-finite particle at " + x + " " + y + " " + z);
            return p;
        }
        if (DebugToggles.skipParticle(sheet == FxParticle.GLOW, x, y, z)) {
            return p;
        }
        if (!overBudget()) {
            Minecraft.getMinecraft().effectRenderer.addEffect(p);
        }
        return p;
    }

    private static void vanilla(EnumParticleTypes t, double x, double y, double z, double vx, double vy, double vz, int... args) {
        if (DebugToggles.noVanilla || DebugToggles.skipParticle(false, x, y, z) || overBudget()) {
            return;
        }
        world().spawnParticle(t, true, x, y, z, vx, vy, vz, args);
    }

    /** Element-flavoured secondary particle (flames for fire, swirls for necromancy, dust for earth...). */
    private static void puff(int el, double x, double y, double z, double vx, double vy, double vz, float scale) {
        switch (RNG.nextBoolean() ? Fx.primary(el) : Fx.secondary(el)) {
            case Fx.FIRE:
                if (RNG.nextInt(3) == 0) {
                    P(x, y, z, vx * 0.5, vy * 0.5 + 0.02, vz * 0.5, FxParticle.SMOKE).color(dark(el)).scale(scale * 1.6F).life(18 + RNG.nextInt(10)).glow(false).alpha(0.7F).drag(0.92F);
                } else {
                    P(x, y, z, vx, vy + 0.01, vz, FxParticle.FLAME).color(1F, 1F, 1F).scale(scale).life(10 + RNG.nextInt(8)).shrink(0.2F);
                }
                break;
            case Fx.ICE:
                P(x, y, z, vx, vy, vz, FxParticle.SPARK).color(core(el)).toColor(main(el)).scale(scale * 0.9F).life(14 + RNG.nextInt(8)).gravity(0.15F);
                break;
            case Fx.NECRO:
            case Fx.POISON:
                P(x, y, z, vx, vy + 0.01, vz, Fx.is(el, Fx.POISON) && RNG.nextBoolean() ? FxParticle.BUBBLE : FxParticle.SPELL).color(main(el)).toColor(dark(el)).scale(scale).life(16 + RNG.nextInt(8));
                break;
            case Fx.EARTH:
                P(x, y, z, vx, vy, vz, FxParticle.SMOKE).color(main(el)).toColor(dark(el)).scale(scale * 1.5F).life(18 + RNG.nextInt(10)).glow(false).gravity(0.05F);
                break;
            case Fx.SHADOW:
                P(x, y, z, vx, vy, vz, FxParticle.SMOKE).color(dark(el)).scale(scale * 1.6F).life(16 + RNG.nextInt(8)).glow(false).alpha(0.85F);
                break;
            case Fx.WIND:
                P(x, y, z, vx, vy, vz, FxParticle.SMOKE).color(core(el)).scale(scale * 1.3F).life(10 + RNG.nextInt(6)).alpha(0.6F);
                break;
            case Fx.SORCERY:
            case Fx.ARCANE:
                P(x, y, z, vx, vy, vz, RNG.nextInt(4) == 0 ? FxParticle.RUNE : FxParticle.SPELL).color(main(el)).toColor(core(el)).scale(scale).life(14 + RNG.nextInt(8));
                break;
            case Fx.HEALING:
            case Fx.HOLY:
                P(x, y, z, vx, vy + 0.015, vz, FxParticle.SPARK).color(core(el)).toColor(main(el)).scale(scale).life(16 + RNG.nextInt(8));
                break;
            case Fx.BLOOD:
                P(x, y, z, vx, vy, vz, FxParticle.DOT).color(main(el)).toColor(dark(el)).scale(scale * 0.8F).life(14 + RNG.nextInt(6)).gravity(0.6F).glow(false);
                break;
            default:
                P(x, y, z, vx, vy, vz, FxParticle.SPARK).color(main(el)).toColor(dark(el)).scale(scale).life(10 + RNG.nextInt(6));
                break;
        }
    }

    /** Bright glowing point (the "core" of every effect). */
    private static FxParticle glow(int el, double x, double y, double z, float scale, int life) {
        return P(x, y, z, 0, 0, 0, FxParticle.GLOW).color(core(el)).toColor(main(el)).scale(scale).life(life).shrink(0.3F);
    }

    private static void spark(int el, double x, double y, double z, double vx, double vy, double vz, float scale, int life) {
        P(x, y, z, vx, vy, vz, FxParticle.SPARK).color(main(el)).toColor(dark(el)).scale(scale).life(life);
    }

    private static void debrisAt(double x, double y, double z, double r, int count) {
        World w = world();
        BlockPos bp = new BlockPos(x, y - 0.5, z);
        IBlockState st = w.getBlockState(bp);
        if (st.getMaterial() == Material.AIR) {
            st = w.getBlockState(bp.down());
        }
        if (st.getMaterial() == Material.AIR) {
            return;
        }
        int id = Block.getStateId(st);
        for (int i = 0; i < n(count); i++) {
            double a = RNG.nextDouble() * Math.PI * 2;
            double d = RNG.nextDouble() * r;
            vanilla(EnumParticleTypes.BLOCK_CRACK, x + Math.cos(a) * d, y + 0.1, z + Math.sin(a) * d,
                    Math.cos(a) * 0.15, 0.3 + RNG.nextDouble() * 0.4, Math.sin(a) * 0.15, id);
        }
    }

    // ------------------------------------------------------------------ entry points
    public static void play(SkillNet.MsgFx m) {
        if (world() == null || DebugToggles.noFx) {
            return;
        }
        Entity view = Minecraft.getMinecraft().getRenderViewEntity();
        double far = view == null ? 0 : view.getDistance(m.x, m.y, m.z);
        if (far > 72 && m.kind != Fx.SCREEN_FLASH) {
            return; // too far to matter: skip geometry and particles
        }
        distMul = far > 36 ? 0.5 : 1.0;
        Entity follow = m.entity >= 0 ? world().getEntityByID(m.entity) : null;
        if (!DebugToggles.noMeshFx) {
            FxMesh.fromMsg(m, follow);
        }
        RNG.setSeed(m.seed);
        double x = m.x, y = m.y, z = m.z;
        int el = m.element;
        switch (m.kind) {
            case Fx.SLASH: slash(el, x, y, z, m.ax, m.az, m.size, m.extra > 0 ? m.extra : 2.4, (m.seed & 1) == 0 ? 0.35 : -0.35); break;
            case Fx.XSLASH: xslash(el, x, y, z, m.ax, m.az, m.size); break;
            case Fx.RING: ring(el, x, y, z, m.size, Math.max(3, m.dur)); break;
            case Fx.NOVA: nova(el, x, y, z, m.size); break;
            case Fx.BEAM: beam(el, x, y, z, m.ax, m.ay, m.az, m.size, Math.max(2, m.dur)); break;
            case Fx.EXPLOSION: explosion(el, x, y, z, m.size); break;
            case Fx.PILLAR: pillar(el, x, y, z, m.size, m.extra > 0 ? m.extra : 1.0); break;
            case Fx.SPIRAL: spiral(el, x, y, z, m.size, m.extra > 0 ? m.extra : 2.5, Math.max(4, m.dur)); break;
            case Fx.GATHER: gather(el, follow, x, y, z, m.size, (int) m.extra); break;
            case Fx.DASH: dash(el, x, y, z, m.ax, m.ay, m.az); break;
            case Fx.IMPACT: impact(el, x, y, z, m.size); break;
            case Fx.METEOR: meteor(el, x, y, z, m.size, Math.max(4, m.dur)); break;
            case Fx.RUNES: runes(el, x, y, z, m.size, Math.max(4, m.dur)); break;
            case Fx.FIELD: field(el, x, y, z, m.size, Math.max(4, m.dur)); break;
            case Fx.CRESCENT: crescent(el, x, y, z, m.ax, m.az, m.size, m.extra > 0 ? m.extra : 0.8, Math.max(2, m.dur)); break;
            case Fx.LIGHTNING: lightning(el, x, y, z, m.ax, m.ay, m.az, m.size > 0 ? m.size : 1.0); break;
            case Fx.SPIKES: spikes(el, x, y, z, m.ax, m.az, m.size, m.extra, Math.max(1, m.dur)); break;
            case Fx.DEBRIS: debrisAt(x, y, z, m.size, (int) (12 * m.size)); break;
            case Fx.HEAL: heal(el, x, y, z, m.size); break;
            case Fx.AURA: aura(el, follow, m.size, Math.max(4, m.dur)); break;
            case Fx.TIERUP: tierUp(el, follow, x, y, z, (int) m.extra); break;
            case Fx.SPIN: spin(el, follow, x, y, z, m.size, Math.max(4, m.dur)); break;
            case Fx.BARRAGE: barrage(el, x, y, z, m.size); break;
            case Fx.FLASH: flash(el, x, y, z, m.size); break;
            default: break;
        }
    }

    public static void shake(float power, int ticks) {
        if (power > shakePower * ((float) shakeLeft / shakeTotal)) {
            shakePower = Math.min(power, 5.0F);
            shakeLeft = ticks;
            shakeTotal = Math.max(1, ticks);
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null) {
            JOBS.clear();
            return;
        }
        if (mc.isGamePaused()) {
            return;
        }
        spawnedThisTick = 0;
        distMul = 1.0;
        if (shakeLeft > 0) {
            shakeLeft--;
        }
        List<Job> snapshot = new ArrayList<>(JOBS);
        JOBS.clear();
        for (Job j : snapshot) {
            boolean done;
            try {
                done = j.r.tick(j.age++);
            } catch (RuntimeException ex) {
                done = true;
            }
            if (!done) {
                JOBS.add(j);
            }
        }
    }

    @SubscribeEvent
    public void onCamera(EntityViewRenderEvent.CameraSetup e) {
        if (shakeLeft <= 0 || DebugToggles.noShake) {
            return;
        }
        float s = shakePower * ((float) shakeLeft / (float) shakeTotal);
        e.setPitch(e.getPitch() + (RNG.nextFloat() - 0.5F) * 2.0F * s);
        e.setYaw(e.getYaw() + (RNG.nextFloat() - 0.5F) * 2.0F * s);
        e.setRoll(e.getRoll() + (RNG.nextFloat() - 0.5F) * s);
    }

    private static void run(Running r) {
        if (!DebugToggles.noJobs && JOBS.size() < 200) {
            JOBS.add(new Job(r));
        }
    }

    // ------------------------------------------------------------------ effects
    /** Sweeping crescent slash drawn over 3 ticks. (dx, dz) = facing, span = arc width in radians, tilt = diagonal lean. */
    private static void slash(final int el, final double x, final double y, final double z, double dx, double dz,
                              final double r, final double span, final double tilt) {
        double len = Math.sqrt(dx * dx + dz * dz);
        final double fx = len < 1e-4 ? 0 : dx / len;
        final double fz = len < 1e-4 ? 1 : dz / len;
        final int steps = 3;
        final int per = n(10 + r * 5);
        run(age -> {
            if (age >= steps) {
                return true;
            }
            for (int i = 0; i < per; i++) {
                double t = (age + (double) i / per) / steps;
                double a = -span / 2 + span * t;
                double ca = Math.cos(a), sa = Math.sin(a);
                double ox = fx * ca - fz * sa;
                double oz = fz * ca + fx * sa;
                double py = y + sa * tilt * r;
                double px = x + ox * r, pz = z + oz * r;
                P(px, py, pz, ox * 0.02, 0, oz * 0.02, FxParticle.GLOW).color(core(el)).toColor(main(el)).scale(1.4F).life(5 + RNG.nextInt(3)).shrink(0.2F);
                spark(el, x + ox * r * 0.86, py, z + oz * r * 0.86, ox * 0.06, 0.01, oz * 0.06, 0.9F, 8 + RNG.nextInt(4));
                if (RNG.nextInt(3) == 0) {
                    puff(el, px, py, pz, ox * 0.05, 0.02, oz * 0.05, 0.9F);
                }
                if (Fx.is(el, Fx.STEEL) && i % 4 == 0) {
                    vanilla(EnumParticleTypes.SWEEP_ATTACK, x + ox * r * 0.7, py, z + oz * r * 0.7, 0, 0, 0);
                }
            }
            return false;
        });
    }

    /** Two crossing diagonal cuts in front of (x, y, z). */
    private static void xslash(final int el, final double x, final double y, final double z, double dx, double dz, final double s) {
        double len = Math.sqrt(dx * dx + dz * dz);
        final double rx = len < 1e-4 ? 1 : -dz / len;
        final double rz = len < 1e-4 ? 0 : dx / len;
        run(age -> {
            if (age == 0 || age == 2) {
                double sign = age == 0 ? 1 : -1;
                int c = n(18 * s);
                for (int i = 0; i < c; i++) {
                    double t = (double) i / (c - 1) * 2 - 1;
                    double px = x + rx * t * s, pz = z + rz * t * s, py = y + sign * t * s;
                    P(px, py, pz, 0, 0, 0, FxParticle.GLOW).color(core(el)).toColor(main(el)).scale(1.5F).life(6).shrink(0.2F);
                    spark(el, px, py, pz, rnd(0.05), rnd(0.05), rnd(0.05), 0.8F, 10);
                }
            }
            if (age == 3) {
                flash(el, x, y, z, s * 0.8);
                return true;
            }
            return false;
        });
    }

    /** Expanding ground shockwave ring. */
    private static void ring(final int el, final double x, final double y, final double z, final double rMax, final int dur) {
        run(age -> {
            if (age > dur) {
                return true;
            }
            double t = (double) (age + 1) / (dur + 1);
            double r = rMax * (1 - (1 - t) * (1 - t));
            int c = n(10 + r * 7);
            for (int i = 0; i < c; i++) {
                double a = Math.PI * 2 * i / c + RNG.nextDouble() * 0.1;
                double ca = Math.cos(a), sa = Math.sin(a);
                puff(el, x + ca * r, y + 0.15, z + sa * r, ca * 0.08, 0.04, sa * 0.08, 1.1F);
                if (i % 2 == 0) {
                    P(x + ca * r, y + 0.35, z + sa * r, ca * 0.05, 0.02, sa * 0.05, FxParticle.GLOW).color(core(el)).toColor(main(el)).scale(1.1F).life(5).shrink(0.2F);
                }
            }
            if ((Fx.is(el, Fx.STEEL) || Fx.is(el, Fx.EARTH)) && age % 2 == 0) {
                for (int i = 0; i < n(r * 1.5); i++) {
                    double a = RNG.nextDouble() * Math.PI * 2;
                    debrisAt(x + Math.cos(a) * r, y, z + Math.sin(a) * r, 0.3, 1);
                }
            }
            return false;
        });
    }

    /** Spherical burst. */
    private static void nova(int el, double x, double y, double z, double r) {
        int c = n(30 + r * 14);
        for (int i = 0; i < c; i++) {
            double u = RNG.nextDouble() * 2 - 1;
            double a = RNG.nextDouble() * Math.PI * 2;
            double s = Math.sqrt(1 - u * u);
            double vx = s * Math.cos(a), vy = u * 0.6, vz = s * Math.sin(a);
            double sp = r * 0.1 * (0.6 + RNG.nextDouble() * 0.5);
            P(x, y, z, vx * sp, vy * sp, vz * sp, FxParticle.GLOW).color(core(el)).toColor(main(el)).scale(1.2F).life(8 + RNG.nextInt(4)).drag(0.86F);
            if (i % 2 == 0) {
                puff(el, x, y, z, vx * sp * 0.8, vy * sp * 0.8, vz * sp * 0.8, 1.2F);
            }
        }
        flash(el, x, y, z, r * 0.5);
    }

    /** Beam from (x, y, z) to (tx, ty, tz): glowing core, two twisting strands, impact sparks at the end. */
    private static void beam(final int el, final double x, final double y, final double z, final double tx, final double ty,
                             final double tz, final double width, final int dur) {
        final double dx = tx - x, dy = ty - y, dz = tz - z;
        final double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 0.1) {
            return;
        }
        final double ux = dx / len, uy = dy / len, uz = dz / len;
        // perpendicular basis
        double px0 = -uz, pz0 = ux, py0 = 0;
        double pl = Math.sqrt(px0 * px0 + pz0 * pz0);
        if (pl < 1e-3) {
            px0 = 1;
            pz0 = 0;
            pl = 1;
        }
        final double ax = px0 / pl, ay = py0, az = pz0 / pl;
        final double bx = uy * az - uz * ay, by = uz * ax - ux * az, bz = ux * ay - uy * ax;
        flash(el, x, y, z, width);
        run(age -> {
            if (age >= dur) {
                // dissipate
                for (int i = 0; i < n(len * 3); i++) {
                    double d = RNG.nextDouble() * len;
                    puff(el, x + ux * d, y + uy * d, z + uz * d, rnd(0.05), rnd(0.05), rnd(0.05), 0.9F);
                }
                return true;
            }
            double step = 0.4;
            for (double d = 0; d < len; d += step) {
                double cx = x + ux * d, cy = y + uy * d, cz = z + uz * d;
                P(cx, cy, cz, 0, 0, 0, FxParticle.GLOW).color(core(el)).toColor(main(el)).scale((float) (1.6 * width)).life(3).shrink(0.5F);
                double ang = d * 1.6 + age * 0.9;
                for (int s = 0; s < 2; s++) {
                    double a = ang + s * Math.PI;
                    double ox = (ax * Math.cos(a) + bx * Math.sin(a)) * width * 0.55;
                    double oy = (ay * Math.cos(a) + by * Math.sin(a)) * width * 0.55;
                    double oz = (az * Math.cos(a) + bz * Math.sin(a)) * width * 0.55;
                    if (RNG.nextInt(2) == 0) {
                        spark(el, cx + ox, cy + oy, cz + oz, 0, 0, 0, 0.7F, 4);
                    }
                }
            }
            for (int i = 0; i < n(4); i++) {
                puff(el, tx, ty, tz, rnd(0.15), rnd(0.15), rnd(0.15), 1.0F);
            }
            return false;
        });
    }

    private static void explosion(final int el, final double x, final double y, final double z, final double r) {
        P(x, y, z, 0, 0, 0, FxParticle.GLOW).color(core(el)).scale((float) (4 + r * 2)).life(5).shrink(0.1F);
        int c = n(20 + r * 10);
        for (int i = 0; i < c; i++) {
            double u = RNG.nextDouble() * 1.4 - 0.4;
            double a = RNG.nextDouble() * Math.PI * 2;
            double s = Math.sqrt(Math.max(0, 1 - u * u));
            double sp = r * 0.09 * (0.5 + RNG.nextDouble());
            double vx = s * Math.cos(a) * sp, vy = Math.abs(u) * sp, vz = s * Math.sin(a) * sp;
            puff(el, x, y + 0.3, z, vx, vy, vz, 2.0F);
            if (i % 2 == 0) {
                P(x, y + 0.3, z, vx * 1.6, vy * 1.6 + 0.1, vz * 1.6, FxParticle.SPARK).color(core(el)).toColor(main(el)).scale(0.9F).life(16 + RNG.nextInt(10)).gravity(0.5F).drag(0.95F);
            }
        }
        for (int i = 0; i < n(8 + r * 3); i++) {
            P(x + rnd(r * 0.4), y + 0.3 + RNG.nextDouble(), z + rnd(r * 0.4), rnd(0.02), 0.04 + RNG.nextDouble() * 0.03, rnd(0.02), FxParticle.SMOKE)
                    .color(dark(el)).scale((float) (2.5 + r * 0.4)).life(30 + RNG.nextInt(20)).glow(false).alpha(0.75F).drag(0.96F);
        }
        if (Fx.is(el, Fx.STEEL) || Fx.is(el, Fx.FIRE) || Fx.is(el, Fx.EARTH)) {
            vanilla(EnumParticleTypes.EXPLOSION_LARGE, x, y + 0.5, z, 0, 0, 0);
        }
        ring(el, x, y, z, r * 1.3, 6);
        debrisAt(x, y, z, r * 0.8, (int) (10 + r * 6));
    }

    private static void pillar(final int el, final double x, final double y, final double z, final double h, final double w) {
        run(age -> {
            if (age > 8) {
                return true;
            }
            double top = h * Math.min(1.0, (age + 1) / 4.0);
            for (double d = 0; d < top; d += 0.35) {
                double a = d * 2.2 + age * 0.6;
                P(x + Math.cos(a) * w * 0.4, y + d, z + Math.sin(a) * w * 0.4, 0, 0.03, 0, FxParticle.GLOW).color(core(el)).toColor(main(el)).scale((float) (1.2 * w)).life(4).shrink(0.3F);
                if (RNG.nextInt(3) == 0) {
                    puff(el, x + rnd(w * 0.5), y + d, z + rnd(w * 0.5), rnd(0.03), 0.06, rnd(0.03), 1.0F);
                }
            }
            return false;
        });
    }

    private static void spiral(final int el, final double x, final double y, final double z, final double r, final double h, final int dur) {
        run(age -> {
            if (age >= dur) {
                return true;
            }
            for (int s = 0; s < 2; s++) {
                for (int k = 0; k < 3; k++) {
                    double t = (age * 3 + k) / (dur * 3.0);
                    double a = t * Math.PI * 6 + s * Math.PI;
                    double rr = r * (1 - t * 0.5);
                    P(x + Math.cos(a) * rr, y + t * h, z + Math.sin(a) * rr, 0, 0.01, 0, FxParticle.GLOW).color(core(el)).toColor(main(el)).scale(1.1F).life(10).shrink(0.2F);
                    if (RNG.nextBoolean()) {
                        puff(el, x + Math.cos(a) * rr, y + t * h, z + Math.sin(a) * rr, 0, 0.02, 0, 0.8F);
                    }
                }
            }
            return false;
        });
    }

    /** Charge-up: particles stream into the caster's hands; tier brightens it. */
    private static void gather(int el, Entity e, double x, double y, double z, double r, int tier) {
        double cx = e != null ? e.posX : x, cy = (e != null ? e.posY : y) + 1.1, cz = e != null ? e.posZ : z;
        int c = n(6 + tier * 4);
        for (int i = 0; i < c; i++) {
            double u = RNG.nextDouble() * 2 - 1;
            double a = RNG.nextDouble() * Math.PI * 2;
            double s = Math.sqrt(1 - u * u);
            double ox = s * Math.cos(a) * r, oy = u * r * 0.7, oz = s * Math.sin(a) * r;
            int life = 7;
            P(cx + ox, cy + oy, cz + oz, -ox / life, -oy / life, -oz / life, tier >= 2 ? FxParticle.GLOW : FxParticle.SPARK)
                    .color(tier >= 3 ? core(el) : main(el)).scale(0.9F + tier * 0.2F).life(life).drag(1.0F).shrink(0.4F);
        }
        if (tier >= 1) {
            double a0 = RNG.nextDouble() * Math.PI * 2;
            double gy = e != null ? e.posY + 0.05 : y;
            for (int i = 0; i < n(6); i++) {
                double a = a0 + Math.PI * 2 * i / 6;
                P(cx + Math.cos(a) * 1.2, gy, cz + Math.sin(a) * 1.2, 0, 0.005, 0, FxParticle.RUNE).color(main(el)).scale(1.0F).life(10);
            }
        }
    }

    private static void dash(int el, double x, double y, double z, double tx, double ty, double tz) {
        double dx = tx - x, dy = ty - y, dz = tz - z;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 0.1) {
            return;
        }
        for (double d = 0; d < len; d += 0.3) {
            double t = d / len;
            double px = x + dx * t, py = y + dy * t, pz = z + dz * t;
            for (int k = 0; k < 2; k++) {
                P(px + rnd(0.3), py + 0.3 + RNG.nextDouble() * 1.4, pz + rnd(0.3), dx / len * 0.05, 0, dz / len * 0.05, FxParticle.GLOW)
                        .color(main(el)).toColor(dark(el)).scale(0.8F).life(6 + RNG.nextInt(5)).shrink(0.2F);
            }
            if (RNG.nextInt(2) == 0) {
                puff(el, px, py + 0.2, pz, 0, 0.01, 0, 1.0F);
            }
        }
    }

    private static void impact(int el, double x, double y, double z, double s) {
        P(x, y, z, 0, 0, 0, FxParticle.GLOW).color(core(el)).scale((float) (2.0 * Math.max(0.6, s))).life(3).shrink(0.2F);
        for (int i = 0; i < n(8 * Math.max(0.6, s)); i++) {
            spark(el, x, y, z, rnd(0.25), rnd(0.2) + 0.05, rnd(0.25), 0.8F, 8 + RNG.nextInt(4));
        }
        vanilla(EnumParticleTypes.CRIT, x, y, z, rnd(0.3), 0.2, rnd(0.3));
    }

    /** A meteor falls from the sky onto (x, y, z) over dur ticks, then explodes. */
    private static void meteor(final int el, final double x, final double y, final double z, final double r, final int dur) {
        final double sx = x - 6, sy = y + 22, sz = z - 3;
        run(age -> {
            double t = (double) age / dur;
            if (age >= dur) {
                explosion(el, x, y, z, r);
                return true;
            }
            double hx = sx + (x - sx) * t, hy = sy + (y - sy) * t, hz = sz + (z - sz) * t;
            P(hx, hy, hz, 0, 0, 0, FxParticle.GLOW).color(core(el)).scale(5.0F).life(3).shrink(0.4F);
            for (int i = 0; i < n(10); i++) {
                puff(el, hx + rnd(0.6), hy + rnd(0.6), hz + rnd(0.6), rnd(0.05), 0.05, rnd(0.05), 1.8F);
            }
            // warning circle on the ground
            int c = n(14);
            double rr = r * (1.0 - t * 0.3);
            for (int i = 0; i < c; i++) {
                double a = Math.PI * 2 * i / c + age * 0.2;
                P(x + Math.cos(a) * rr, y + 0.1, z + Math.sin(a) * rr, 0, 0, 0, FxParticle.SPARK).color(main(el)).scale(0.8F).life(3);
            }
            return false;
        });
    }

    /** Rotating rune circle on the ground. */
    private static void runes(final int el, final double x, final double y, final double z, final double r, final int dur) {
        run(age -> {
            if (age >= dur) {
                return true;
            }
            int c = n(10 + r * 3);
            for (int i = 0; i < c; i++) {
                double a = Math.PI * 2 * i / c + age * 0.12;
                P(x + Math.cos(a) * r, y + 0.1, z + Math.sin(a) * r, 0, 0.004, 0, FxParticle.SPARK).color(main(el)).scale(0.7F).life(3);
                if (i % 3 == 0) {
                    double b = -a * 1.3;
                    P(x + Math.cos(b) * r * 0.6, y + 0.1, z + Math.sin(b) * r * 0.6, 0, 0.01, 0, FxParticle.RUNE).color(core(el)).scale(1.1F).life(6);
                }
            }
            return false;
        });
    }

    /** Lingering area (snares, auras, burning ground): edge ring and element ambience. */
    private static void field(final int el, final double x, final double y, final double z, final double r, final int dur) {
        run(age -> {
            if (age >= dur) {
                return true;
            }
            int c = n(4 + r * 2);
            for (int i = 0; i < c; i++) {
                double a = RNG.nextDouble() * Math.PI * 2;
                double d = Math.sqrt(RNG.nextDouble()) * r;
                double px = x + Math.cos(a) * d, pz = z + Math.sin(a) * d;
                if (Fx.is(el, Fx.ICE)) {
                    P(px, y + 2.5 + RNG.nextDouble(), pz, rnd(0.01), -0.08, rnd(0.01), FxParticle.SPARK).color(core(el)).scale(0.7F).life(30);
                } else if (Fx.is(el, Fx.SORCERY) || Fx.is(el, Fx.ARCANE)) {
                    P(px, y + 0.2 + RNG.nextDouble() * 1.5, pz, (x - px) * 0.05, 0, (z - pz) * 0.05, FxParticle.SPELL).color(main(el)).toColor(core(el)).scale(1.0F).life(18);
                } else {
                    puff(el, px, y + 0.1, pz, rnd(0.01), 0.03 + RNG.nextDouble() * 0.04, rnd(0.01), 1.1F);
                }
            }
            if (age % 4 == 0) {
                int e = n(12 + r * 4);
                for (int i = 0; i < e; i++) {
                    double a = Math.PI * 2 * i / e + age * 0.05;
                    P(x + Math.cos(a) * r, y + 0.12, z + Math.sin(a) * r, 0, 0.02, 0, FxParticle.GLOW).color(main(el)).scale(0.9F).life(8).shrink(0.3F);
                }
            }
            if (Fx.is(el, Fx.LIGHTNING_E) && age % 6 == 0) {
                double a = RNG.nextDouble() * Math.PI * 2, b = a + 1 + RNG.nextDouble() * 2;
                lightning(el, x + Math.cos(a) * r, y + 0.2, z + Math.sin(a) * r, x + Math.cos(b) * r * 0.5, y + 1.2, z + Math.sin(b) * r * 0.5, 0.7);
            }
            return false;
        });
    }

    /** Travelling crescent wave (sword beams, ground waves). */
    private static void crescent(final int el, final double x, final double y, final double z, double dx, double dz,
                                 final double width, final double speed, final int dur) {
        double len = Math.sqrt(dx * dx + dz * dz);
        final double fx = len < 1e-4 ? 0 : dx / len;
        final double fz = len < 1e-4 ? 1 : dz / len;
        final double rx = -fz, rz = fx;
        run(age -> {
            if (age >= dur) {
                return true;
            }
            double cx = x + fx * speed * age, cz = z + fz * speed * age;
            int c = n(10 + width * 5);
            for (int i = 0; i < c; i++) {
                double t = (double) i / (c - 1) * 2 - 1;
                double back = -t * t * width * 0.35;
                double px = cx + rx * t * width / 2 + fx * back, pz = cz + rz * t * width / 2 + fz * back;
                double py = y + (1 - t * t) * 0.4;
                P(px, py, pz, fx * 0.1, 0, fz * 0.1, FxParticle.GLOW).color(core(el)).toColor(main(el)).scale((float) (1.5 - Math.abs(t) * 0.6)).life(4).shrink(0.3F);
                if (RNG.nextInt(3) == 0) {
                    puff(el, px, py - 0.3, pz, fx * 0.04, 0.02, fz * 0.04, 1.0F);
                }
            }
            if (Fx.is(el, Fx.STEEL) && age % 2 == 0) {
                vanilla(EnumParticleTypes.SWEEP_ATTACK, cx, y, cz, 0, 0, 0);
            }
            if ((Fx.is(el, Fx.EARTH) || Fx.is(el, Fx.STEEL)) && age % 2 == 0) {
                debrisAt(cx, y - 1.0, cz, width * 0.4, 4);
            }
            return false;
        });
    }

    /** Jagged lightning bolt with a fork. */
    private static void lightning(int el, double x, double y, double z, double tx, double ty, double tz, double thick) {
        int segs = 8 + RNG.nextInt(5);
        double px = x, py = y, pz = z;
        double dx = tx - x, dy = ty - y, dz = tz - z;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double jit = Math.min(1.2, len * 0.12);
        for (int i = 1; i <= segs; i++) {
            double t = (double) i / segs;
            double nx = x + dx * t + (i < segs ? rnd(jit) : 0);
            double ny = y + dy * t + (i < segs ? rnd(jit) : 0);
            double nz = z + dz * t + (i < segs ? rnd(jit) : 0);
            line(el, px, py, pz, nx, ny, nz, thick);
            if (i == segs / 2 && RNG.nextBoolean()) {
                line(el, nx, ny, nz, nx + rnd(1.5), ny + rnd(1.0), nz + rnd(1.5), thick * 0.6);
            }
            px = nx;
            py = ny;
            pz = nz;
        }
        impact(el, tx, ty, tz, 1.0);
    }

    private static void line(int el, double x, double y, double z, double tx, double ty, double tz, double thick) {
        double dx = tx - x, dy = ty - y, dz = tz - z;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        for (double d = 0; d <= len; d += 0.22) {
            double t = len < 1e-4 ? 0 : d / len;
            P(x + dx * t, y + dy * t, z + dz * t, 0, 0, 0, FxParticle.GLOW).color(core(el)).toColor(main(el)).scale((float) (0.9 * thick)).life(4 + RNG.nextInt(2)).shrink(0.4F);
        }
    }

    /** Stone / ice spikes erupting in a ring (len <= 0) or along a line of length len (appearing over dur ticks). */
    private static void spikes(final int el, final double x, final double y, final double z, double dx, double dz,
                               final double r, final double lineLen, final int dur) {
        double l = Math.sqrt(dx * dx + dz * dz);
        final double fx = l < 1e-4 ? 0 : dx / l, fz = l < 1e-4 ? 1 : dz / l;
        if (lineLen > 0) {
            run(age -> {
                if (age >= dur) {
                    return true;
                }
                double from = lineLen * age / dur, to = lineLen * (age + 1) / dur;
                for (double d = from; d < to; d += 1.0) {
                    spike(el, x + fx * d, y, z + fz * d, 1.0 + RNG.nextDouble() * 0.8);
                }
                return false;
            });
        } else {
            int c = Math.max(5, (int) (r * 3));
            for (int i = 0; i < c; i++) {
                double a = Math.PI * 2 * i / c + rnd(0.15);
                spike(el, x + Math.cos(a) * r, y, z + Math.sin(a) * r, 1.2 + RNG.nextDouble());
            }
        }
    }

    private static void spike(int el, double x, double y, double z, double h) {
        for (double d = 0; d < h; d += 0.2) {
            double w = (1 - d / h) * 0.35;
            P(x + rnd(w), y + d, z + rnd(w), 0, 0.02, 0, Fx.is(el, Fx.ICE) ? FxParticle.GLOW : FxParticle.SMOKE)
                    .color(Fx.is(el, Fx.ICE) ? core(el) : main(el)).toColor(dark(el)).scale((float) (1.4 * (1 - d / h) + 0.4)).life(12 + RNG.nextInt(6))
                    .glow(Fx.is(el, Fx.ICE)).drag(0.8F);
        }
        debrisAt(x, y, z, 0.5, 5);
    }

    private static void heal(int el, double x, double y, double z, double r) {
        for (int i = 0; i < n(16 + r * 4); i++) {
            double a = RNG.nextDouble() * Math.PI * 2, d = RNG.nextDouble() * r;
            P(x + Math.cos(a) * d, y + RNG.nextDouble() * 0.5, z + Math.sin(a) * d, 0, 0.05 + RNG.nextDouble() * 0.04, 0, FxParticle.SPARK)
                    .color(core(el)).toColor(main(el)).scale(1.0F).life(20 + RNG.nextInt(10)).drag(0.95F);
        }
        for (int i = 0; i < n(4); i++) {
            P(x + rnd(r * 0.5), y + 1 + RNG.nextDouble(), z + rnd(r * 0.5), 0, 0.03, 0, FxParticle.HEART).color(1F, 1F, 1F).scale(1.2F).life(16).glow(false);
        }
    }

    /** Spiral aura that follows an entity. */
    private static void aura(final int el, final Entity e, final double r, final int dur) {
        if (e == null) {
            return;
        }
        run(age -> {
            if (age >= dur || e.isDead) {
                return true;
            }
            for (int s = 0; s < 3; s++) {
                double a = age * 0.35 + s * Math.PI * 2 / 3;
                double h = (age % 20) / 20.0 * 2.0;
                P(e.posX + Math.cos(a) * r, e.posY + h, e.posZ + Math.sin(a) * r, 0, 0.01, 0, FxParticle.GLOW).color(main(el)).toColor(core(el)).scale(0.9F).life(8).shrink(0.2F);
            }
            if (age % 3 == 0) {
                puff(el, e.posX + rnd(r), e.posY + 0.1, e.posZ + rnd(r), 0, 0.04, 0, 1.0F);
            }
            return false;
        });
    }

    private static void tierUp(int el, Entity e, double x, double y, double z, int tier) {
        double cx = e != null ? e.posX : x, cy = (e != null ? e.posY : y) + 1.0, cz = e != null ? e.posZ : z;
        float[] col = tier <= 1 ? new float[]{1F, 1F, 1F} : (tier == 2 ? new float[]{1F, 0.85F, 0.3F} : core(el));
        int c = n(16 + tier * 6);
        for (int i = 0; i < c; i++) {
            double a = Math.PI * 2 * i / c;
            P(cx, cy, cz, Math.cos(a) * 0.35, 0, Math.sin(a) * 0.35, FxParticle.GLOW).color(col).toColor(main(el)).scale(1.0F + tier * 0.2F).life(7).drag(0.8F).shrink(0.2F);
        }
        P(cx, cy, cz, 0, 0, 0, FxParticle.GLOW).color(col).scale(3.0F + tier).life(4).shrink(0.2F);
    }

    /** Whirlwind of blades around an entity. */
    private static void spin(final int el, final Entity e, final double x, final double y, final double z, final double r, final int dur) {
        run(age -> {
            if (age >= dur) {
                return true;
            }
            double cx = e != null ? e.posX : x, cy = (e != null ? e.posY : y) + 1.0, cz = e != null ? e.posZ : z;
            for (int s = 0; s < 2; s++) {
                double base = age * 1.1 + s * Math.PI;
                for (int k = 0; k < n(8); k++) {
                    double a = base + k * 0.18;
                    double tilt = Math.sin(a * 0.5 + s) * 0.4;
                    P(cx + Math.cos(a) * r, cy + tilt, cz + Math.sin(a) * r, 0, 0, 0, FxParticle.GLOW).color(core(el)).toColor(main(el)).scale((float) (1.3 - k * 0.1)).life(4).shrink(0.2F);
                }
            }
            if (age % 2 == 0) {
                double a = age * 1.1;
                if (Fx.is(el, Fx.STEEL)) {
                    vanilla(EnumParticleTypes.SWEEP_ATTACK, cx + Math.cos(a) * r * 0.7, cy, cz + Math.sin(a) * r * 0.7, 0, 0, 0);
                } else {
                    puff(el, cx + Math.cos(a) * r, cy, cz + Math.sin(a) * r, 0, 0.02, 0, 1.2F);
                }
            }
            return false;
        });
    }

    /** Three quick random cuts through a point (barrage hits). */
    private static void barrage(int el, double x, double y, double z, double s) {
        for (int k = 0; k < 3; k++) {
            double a = RNG.nextDouble() * Math.PI, b = RNG.nextDouble() * Math.PI * 2;
            double dx = Math.cos(b) * Math.sin(a), dy = Math.cos(a), dz = Math.sin(b) * Math.sin(a);
            line(el, x - dx * s, y - dy * s, z - dz * s, x + dx * s, y + dy * s, z + dz * s, 1.0);
        }
        impact(el, x, y, z, 0.8);
    }

    private static void flash(int el, double x, double y, double z, double s) {
        P(x, y, z, 0, 0, 0, FxParticle.GLOW).color(core(el)).scale((float) (3.0 + s * 2.0)).life(3).shrink(0.1F);
        for (int i = 0; i < n(6 + s * 4); i++) {
            P(x, y, z, rnd(0.3), rnd(0.3), rnd(0.3), FxParticle.SPARK).color(core(el)).toColor(main(el)).scale(0.8F).life(8).drag(0.85F);
        }
    }

    /** Trail of a flying skill bolt (kind 0 orb, 1 arrow / needle, 2 big orb). */
    public static void boltTrail(Entity b, int el, float size, int kind) {
        if (world() == null) {
            return;
        }
        if (b.isInvisible()) {
            return;
        }
        double x = b.posX, y = b.posY, z = b.posZ;
        Entity view = Minecraft.getMinecraft().getRenderViewEntity();
        if (view != null && view.getDistanceSq(x, y, z) > 48 * 48) {
            return;
        }
        if (kind >= 3) {
            // skill arrow / thrown weapon: the renderer draws the arrow and its streak, particles only add a light sparkle
            if (b.ticksExisted % 2 == 0) {
                spark(el, x - b.motionX * 0.5, y - b.motionY * 0.5, z - b.motionZ * 0.5, rnd(0.03), rnd(0.03), rnd(0.03), 0.5F, 5);
            }
            return;
        }
        if (kind == 1) {
            P(x, y, z, 0, 0, 0, FxParticle.GLOW).color(core(el)).toColor(main(el)).scale(size * 1.5F).life(4).shrink(0.3F);
            double mx = b.motionX, my = b.motionY, mz = b.motionZ;
            for (int i = 1; i <= 3; i++) {
                double t = i / 3.0;
                spark(el, x - mx * t, y - my * t, z - mz * t, rnd(0.02), rnd(0.02), rnd(0.02), 0.6F, 6);
            }
            return;
        }
        P(x, y, z, 0, 0, 0, FxParticle.GLOW).color(core(el)).toColor(main(el)).scale(size * (kind == 2 ? 4.5F : 3.0F)).life(3).shrink(0.5F);
        for (int i = 0; i < n(kind == 2 ? 6 : 3); i++) {
            puff(el, x + rnd(size * 0.3), y + rnd(size * 0.3), z + rnd(size * 0.3), rnd(0.03), rnd(0.03), rnd(0.03), size * (kind == 2 ? 1.8F : 1.2F));
        }
    }
}
