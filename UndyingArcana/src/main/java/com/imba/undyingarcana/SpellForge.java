package com.imba.undyingarcana;

import java.util.List;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.MobEffects;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.math.Vec3d;

import static com.imba.undyingarcana.SkillServer.repeat;
import static com.imba.undyingarcana.SkillServer.schedule;
import static com.imba.undyingarcana.SkillShapes.hit;

/**
 * Grimoire spell casting: the 13 shapes, the 11 runes, the second element and "on hit, cast spell X".
 * A cast either starts at the player (normal) or at a point (a triggered spell, origin != null).
 */
public final class SpellForge {
    private SpellForge() {
    }

    private static final int PIERCE = 0, HOMING = 1, SPLIT = 2, BOUNCE = 3, ECHO = 4, LINGER = 5, LIFESTEAL = 6, PULL = 7,
            KNOCKBACK = 8, DELAY = 9, CLUSTER = 10;

    /** One cast: the spell, its context and where it comes from. */
    private static final class S {
        SkillCtx c;
        Magic.Spell sp;
        String el2 = "";
        int depth;          // 0 = cast by the player, 1 = triggered by another spell
        Vec3d origin;       // null = from the player
        Vec3d aimAt;        // aim point of a triggered spell
        float scale = 1.0F; // Echo recasts at half damage
        boolean echo;
        boolean[] fired = {false};
        SpellVM.Hooks hooks; // Spell Weave callbacks (on hit / on end), or null

        boolean has(int r) {
            return sp.has(r);
        }
    }

    /** One Cast Shape node of a Spell Weave graph: from a point (or the player when null) toward a point, with callbacks. */
    public static void castShape(SkillCtx c, Magic.Spell sp, String el2, Vec3d origin, Vec3d aimAt, SpellVM.Hooks hooks) {
        Magic.Spell one = sp.copy();
        one.trig = -1;
        S s = new S();
        s.c = c;
        s.sp = one;
        s.el2 = el2 == null ? "" : el2;
        s.depth = 0;
        s.origin = origin;
        s.aimAt = aimAt;
        s.hooks = hooks;
        run(s);
        if (s.has(ECHO)) {
            final S e = copy(s);
            e.scale = 0.5F;
            e.echo = true;
            schedule(20, () -> {
                if (!c.p.isDead) {
                    run(e);
                }
            });
        }
    }

    public static void cast(SkillCtx c, Magic.Spell sp, String el2, int depth, Vec3d origin, Vec3d aimAt) {
        S s = new S();
        s.c = c;
        s.sp = sp;
        s.el2 = el2 == null ? "" : el2;
        s.depth = depth;
        s.origin = origin;
        s.aimAt = aimAt;
        run(s);
        if (s.has(ECHO) && depth == 0) {
            final S e = copy(s);
            e.scale = 0.5F;
            e.echo = true;
            schedule(20, () -> {
                if (!c.p.isDead) {
                    run(e);
                }
            });
        }
    }

    private static S copy(S s) {
        S o = new S();
        o.c = s.c;
        o.sp = s.sp;
        o.el2 = s.el2;
        o.depth = 1;
        o.origin = s.origin;
        o.aimAt = s.aimAt;
        o.fired = s.fired;
        o.hooks = s.hooks;
        return o;
    }

    // ------------------------------------------------------------------ geometry
    private static Vec3d eye(S s) {
        return s.origin != null ? s.origin : new Vec3d(s.c.p.posX, s.c.eyeY() - 0.2, s.c.p.posZ);
    }

    private static Vec3d feet(S s) {
        return s.origin != null ? s.origin : new Vec3d(s.c.p.posX, s.c.p.posY, s.c.p.posZ);
    }

    private static Vec3d look(S s) {
        if (s.origin != null && s.aimAt != null) {
            Vec3d d = s.aimAt.subtract(s.origin);
            if (d.lengthSquared() > 1e-4) {
                return d.normalize();
            }
        }
        return s.c.p.getLookVec();
    }

    private static Vec3d aim(S s, double range) {
        return s.origin != null ? (s.aimAt != null ? s.aimAt : s.origin) : SkillShapes.aim(s.c.p, range);
    }

    private static Vec3d ground(S s, double range) {
        Vec3d a = aim(s, range);
        return new Vec3d(a.x, SkillShapes.ground(s.c.w, a.x, a.y + 0.5, a.z), a.z);
    }

    private static float dmg(S s, boolean perHit) {
        return s.c.dmg(s.sp.damage(), perHit) * s.scale;
    }

    // ------------------------------------------------------------------ hits
    private static boolean hitOne(S s, EntityLivingBase t, float dmg, Vec3d from, double push) {
        SkillShapes.Hit h = hit();
        h.push = s.has(KNOCKBACK) ? 1.2 : push;
        h.from = from;
        if (!SkillShapes.damage(s.c, t, dmg, h)) {
            return false;
        }
        if (!s.el2.isEmpty()) {
            String old = s.c.status;
            s.c.status = s.el2;
            SkillStatus.addStatus(s.c, t, dmg);
            s.c.status = old;
        }
        if (s.has(LIFESTEAL)) {
            s.c.p.heal(dmg * 0.15F);
        }
        SpellArts.onHit(s.c, t, dmg);
        SpellArts.fusionHit(s.c, t, dmg);
        if (s.has(PULL) && from != null) {
            SkillShapes.pull(t, from.x, from.z, 0.6);
        }
        trigger(s, new Vec3d(t.posX, t.posY + t.height * 0.5, t.posZ));
        if (s.hooks != null) {
            s.hooks.onHit(t, new Vec3d(t.posX, t.posY + t.height * 0.5, t.posZ));
        }
        return true;
    }

    private static int area(S s, Vec3d at, double r, float dmg, double push) {
        int n = 0;
        for (EntityLivingBase t : SkillShapes.around(s.c.w, at.x, at.y, at.z, r)) {
            if (hitOne(s, t, dmg, at, push)) {
                n++;
            }
        }
        return n;
    }

    /** Explosion / landing: Delay, Linger and Cluster apply here. */
    private static void impact(final S s, final Vec3d at, final double r, final float dmg, final double push) {
        Runnable go = () -> {
            float d = s.has(DELAY) ? dmg * 1.3F : dmg;
            Fx.at(s.c.w, r >= 2 ? Fx.EXPLOSION : Fx.IMPACT, s.c.el, at.x, at.y, at.z, r);
            area(s, at, r, d, push);
            SpellArts.onImpact(s.c, at, r);
            SpellArts.fusionImpact(s.c, at, r);
            if (s.hooks != null) {
                s.hooks.onEnd(at);
            }
            if (s.has(LINGER)) {
                Fx.send(s.c.w, Fx.FIELD, s.c.el, at.x, at.y, at.z, 0, 0, 0, r * 0.8, 0, 60, -1);
                repeat(10, 10, 6, k -> area(s, at, r, d * 0.2F, 0));
            }
            if (s.has(CLUSTER)) {
                for (int k = 0; k < 4; k++) {
                    final double a = k * Math.PI / 2 + 0.4;
                    final Vec3d p = at.addVector(Math.cos(a) * r * 0.9, 0, Math.sin(a) * r * 0.9);
                    schedule(4 + k * 2, () -> {
                        Fx.at(s.c.w, Fx.IMPACT, s.c.el, p.x, p.y, p.z, r * 0.5);
                        area(s, p, r * 0.5, d * 0.35F, 0.2);
                    });
                }
            }
        };
        if (s.has(DELAY)) {
            Fx.send(s.c.w, Fx.RUNES, s.c.el, at.x, at.y + 0.05, at.z, 0, 0, 0, r, 0, 20, -1);
            schedule(20, go);
        } else {
            go.run();
        }
    }

    /** "On hit, cast spell X": once per cast, free, from where the first hit landed, aimed at the nearest other enemy. */
    private static void trigger(S s, Vec3d at) {
        if (s.sp.trig < 0 || s.depth > 0 || s.fired[0]) {
            return;
        }
        s.fired[0] = true;
        EntityPlayerMP p = s.c.p;
        Magic.Spell t = Magic.spell(p, s.sp.trig);
        if (!t.valid() || s.sp.trig >= Magic.spellSlots(p)) {
            return;
        }
        SkillCtx c2 = Magic.prepare(p, s.sp.trig, t);
        if (c2 == null) {
            return;
        }
        Vec3d aimAt = at;
        double best = 144;
        for (EntityLivingBase o : SkillShapes.around(p.world, at.x, at.y, at.z, 12)) {
            double d = o.getDistanceSq(at.x, at.y, at.z);
            if (d > 1.5 && d < best) {
                best = d;
                aimAt = new Vec3d(o.posX, o.posY + o.height * 0.5, o.posZ);
            }
        }
        int e2 = t.which2 >= 0 ? Magic.element(p, t.which2) : -1;
        final Vec3d to = aimAt;
        schedule(2, () -> cast(c2, t, e2 >= 0 ? Magic.ELEMENT[e2] : "", 1, at, to));
    }

    // ------------------------------------------------------------------ projectiles
    private static EntitySkillBolt bolt(final S s, Vec3d from, Vec3d dir, float size, double speed, float dmg, boolean child) {
        Vec3d d = dir.normalize();
        speed *= SpellArts.speed(s.c) * (s.c.fusion == 5 ? 1.5 : 1.0);
        final EntitySkillBolt b = new EntitySkillBolt(s.c, "spell", 0, size, from.x + d.x * 0.6, from.y + d.y * 0.6, from.z + d.z * 0.6, d.scale(speed));
        b.damage = dmg;
        b.maxLife = (int) (34 / Math.max(0.4, speed)) + 6;
        b.radius = 1.4 * s.c.size * (child ? 0.6 : 1.0);
        if (s.has(PIERCE)) {
            b.pierce += 3;
        }
        if (s.c.fusion == 5) {
            b.pierce += 2; // Tempest
        }
        if (s.has(HOMING)) {
            b.homing = 0.35;
        }
        final int[] bounces = {s.has(BOUNCE) ? 2 : 0};
        b.onHit = (bb, t) -> {
            hitOne(s, t, bb.damage, new Vec3d(bb.posX, bb.posY, bb.posZ), 0.2);
            if (bounces[0] > 0) {
                EntityLivingBase next = null;
                double nd = 64;
                for (EntityLivingBase o : SkillShapes.around(bb.world, t.posX, t.posY, t.posZ, 8)) {
                    double q = o.getDistanceSq(t);
                    if (o != t && q < nd) {
                        nd = q;
                        next = o;
                    }
                }
                if (next != null) {
                    bounces[0]--;
                    bb.pierce++;
                    Vec3d v = new Vec3d(next.posX - bb.posX, next.posY + next.height * 0.5 - bb.posY, next.posZ - bb.posZ).normalize()
                            .scale(Math.sqrt(bb.motionX * bb.motionX + bb.motionY * bb.motionY + bb.motionZ * bb.motionZ));
                    bb.motionX = v.x;
                    bb.motionY = v.y;
                    bb.motionZ = v.z;
                    bb.target = next;
                    bb.velocityChanged = true;
                }
            }
        };
        b.onEnd = (bb, at) -> {
            impact(s, at, bb.radius, bb.damage * 0.4F, 0.2);
            if (s.has(SPLIT) && !child) {
                Vec3d m = new Vec3d(bb.motionX, 0, bb.motionZ);
                if (m.lengthSquared() < 1e-4) {
                    m = new Vec3d(1, 0, 0);
                }
                for (int k = -1; k <= 1; k++) {
                    Vec3d dd = m.normalize().rotateYaw((float) (Math.PI + k * 0.6)).addVector(0, 0.15, 0);
                    bolt(s, at.subtract(m.normalize().scale(0.5)), dd, size * 0.6F, 0.9, bb.damage * 0.4F, true);
                }
            }
        };
        s.c.w.spawnEntity(b);
        return b;
    }

    // ------------------------------------------------------------------ shapes
    private static void run(final S s) {
        final SkillCtx c = s.c;
        final EntityPlayerMP p = c.p;
        switch (s.sp.shape) {
            case 0: // Bolt
                bolt(s, eye(s), look(s), 0.35F * c.size, 1.4 * c.speed, dmg(s, false), false);
                break;
            case 1: // Blast
                impact(s, aim(s, 8), 2.5 * c.size, dmg(s, false), 0.6);
                Fx.shake(c.w, p.posX, p.posY, p.posZ, 10, 0.3F, 5);
                break;
            case 2: { // Beam
                final double len = 14 + 6 * c.size, width = 0.7 * c.size;
                final float d = dmg(s, true);
                repeat(1, 3, 4, i -> {
                    if (p.isDead) {
                        return;
                    }
                    Vec3d from = eye(s), dir = look(s), end = from.add(dir.scale(len));
                    Fx.send(c.w, Fx.BEAM, c.el, from.x, from.y, from.z, end.x, end.y, end.z, 0.35 * c.size, 0, 4, -1);
                    for (EntityLivingBase t : SkillShapes.lineTargets(c.w, from, dir, len, width)) {
                        hitOne(s, t, d, from, 0.1);
                    }
                });
                break;
            }
            case 3: { // Nova
                Vec3d at = feet(s);
                double r = 4.0 * c.size;
                Fx.at(c.w, Fx.NOVA, c.el, at.x, at.y + 0.5, at.z, r);
                Fx.at(c.w, Fx.RING, c.el, at.x, at.y + 0.1, at.z, r);
                impactNoFx(s, at, r, dmg(s, false), 1.0 * c.speed);
                break;
            }
            case 13: { // Shards: pieces fly out in a ring from a point (an enemy there is not hit by its own shards)
                final Vec3d at = s.origin != null ? s.origin : new Vec3d(p.posX, p.posY + 1.0, p.posZ);
                int n = Math.max(4, Math.min(16, (int) Math.round(6 * c.size)));
                float d = dmg(s, true);
                java.util.List<EntityLivingBase> inside = c.w.getEntitiesWithinAABB(EntityLivingBase.class,
                        new net.minecraft.util.math.AxisAlignedBB(at.x - 0.8, at.y - 0.8, at.z - 0.8, at.x + 0.8, at.y + 0.8, at.z + 0.8));
                Fx.at(c.w, Fx.IMPACT, c.el, at.x, at.y, at.z, 0.8);
                double off = p.getRNG().nextDouble() * Math.PI * 2;
                for (int k = 0; k < n; k++) {
                    double a = off + k * Math.PI * 2 / n;
                    Vec3d dir = new Vec3d(Math.cos(a), 0.12, Math.sin(a));
                    EntitySkillBolt b = bolt(s, at, dir, 0.18F * (float) Math.sqrt(c.size), 1.0 * c.speed, d, false);
                    b.maxLife = (int) (11 / Math.max(0.4, c.speed)) + 3;
                    b.skip(p);
                    for (EntityLivingBase e : inside) {
                        b.skip(e);
                    }
                }
                break;
            }
            case 4: { // Barrage
                float d = dmg(s, true);
                Vec3d l = look(s);
                for (int i = -2; i <= 2; i++) {
                    bolt(s, eye(s), l.rotateYaw((float) Math.toRadians(9 * i)), 0.25F * c.size, 1.4 * c.speed, d, false);
                }
                break;
            }
            case 5: { // Wall
                final Vec3d at = ground(s, 8).addVector(0, 0.1, 0);
                Vec3d l = look(s);
                final Vec3d f = new Vec3d(l.x, 0, l.z).lengthSquared() < 1e-4 ? new Vec3d(1, 0, 0) : new Vec3d(l.x, 0, l.z).normalize();
                final Vec3d side = new Vec3d(-f.z, 0, f.x);
                final double half = 3 * c.size;
                final float d = dmg(s, true);
                Fx.send(c.w, Fx.M_WALL, c.el, at.x, at.y, at.z, f.x, 0, f.z, half * 2, 0, 60, -1);
                final Vec3d me = feet(s);
                repeat(0, 5, 12, k -> {
                    Vec3d a = at.subtract(side.scale(half)).addVector(0, 1, 0);
                    for (EntityLivingBase t : SkillShapes.lineTargets(c.w, a, side, half * 2, 1.2)) {
                        hitOne(s, t, d, me, 0.8);
                    }
                });
                break;
            }
            case 6: // Orbit
                for (int k = 0; k < 3; k++) {
                    EntitySkillBolt b = bolt(s, eye(s), look(s), 0.3F * c.size, 0.3, dmg(s, true), false);
                    orbit(b, 2.2 * c.size, k * Math.PI * 2 / 3, 80);
                }
                break;
            case 7: { // Trap
                final Vec3d at = ground(s, 14);
                final float d = dmg(s, false);
                final double r = 2.5 * c.size;
                Fx.send(c.w, Fx.RUNES, c.el, at.x, at.y + 0.05, at.z, 0, 0, 0, 1.4, 0, 200, -1);
                final boolean[] done = {false};
                repeat(10, 5, 40, k -> {
                    if (!done[0] && !SkillShapes.around(c.w, at.x, at.y, at.z, 1.6).isEmpty()) {
                        done[0] = true;
                        impact(s, at.addVector(0, 0.5, 0), r, d, 0.4);
                    }
                });
                break;
            }
            case 8: { // Rain
                final Vec3d at = ground(s, 20);
                final float d = dmg(s, true);
                final double r = 3 * c.size;
                Fx.send(c.w, Fx.RUNES, c.el, at.x, at.y + 0.05, at.z, 0, 0, 0, r, 0, 40, -1);
                repeat(4, 3, 12, k -> {
                    double a = SkillMoves.RNG.nextDouble() * Math.PI * 2, rr = Math.sqrt(SkillMoves.RNG.nextDouble()) * r;
                    Vec3d top = at.addVector(Math.cos(a) * rr, 9, Math.sin(a) * rr);
                    bolt(s, top, new Vec3d(0, -1, 0), 0.3F * c.size, 1.3, d, false);
                });
                break;
            }
            case 9: { // Chain
                final float d = dmg(s, false);
                Vec3d from = eye(s);
                EntityLivingBase first = null;
                if (s.origin == null) {
                    List<EntityLivingBase> l = SkillMoves.aimTargets(c, 16, 0.6);
                    first = l.isEmpty() ? null : l.get(0);
                } else {
                    double best = 256;
                    for (EntityLivingBase o : SkillShapes.around(c.w, from.x, from.y, from.z, 12)) {
                        double q = o.getDistanceSq(from.x, from.y, from.z);
                        if (q > 1.5 && q < best) {
                            best = q;
                            first = o;
                        }
                    }
                }
                if (first == null) {
                    Vec3d end = from.add(look(s).scale(10));
                    Fx.send(c.w, Fx.LIGHTNING, c.el, from.x, from.y, from.z, end.x, end.y, end.z, 0.7, 0, 4, -1);
                    break;
                }
                java.util.Set<Integer> done = SkillShapes.set();
                EntityLivingBase cur = first;
                Vec3d at = from;
                float dd = d;
                int jumps = 4 + s.sp.sz / 4;
                for (int j = 0; j < jumps && cur != null; j++) {
                    done.add(cur.getEntityId());
                    Vec3d to = new Vec3d(cur.posX, cur.posY + cur.height * 0.6, cur.posZ);
                    Fx.send(c.w, Fx.LIGHTNING, c.el, at.x, at.y, at.z, to.x, to.y, to.z, 0.7, 0, 4, -1);
                    hitOne(s, cur, dd, at, 0.1);
                    at = to;
                    dd *= 0.8F;
                    EntityLivingBase next = null;
                    double nd = 49;
                    for (EntityLivingBase o : SkillShapes.around(c.w, cur.posX, cur.posY, cur.posZ, 7)) {
                        double q = o.getDistanceSq(cur);
                        if (!done.contains(o.getEntityId()) && q < nd) {
                            nd = q;
                            next = o;
                        }
                    }
                    cur = next;
                }
                break;
            }
            case 10: { // Blink
                if (s.origin == null) {
                    Vec3d from = feet(s);
                    SkillShapes.blink(p, c.lx, c.lz, 8 * c.size);
                    Fx.send(c.w, Fx.DASH, c.el, from.x, from.y, from.z, p.posX, p.posY, p.posZ, 0, 0, 4, -1);
                }
                final S fs = s;
                schedule(1, () -> impact(fs, feet(fs), 2.5 * c.size, dmg(fs, false), 0.6));
                break;
            }
            case 11: { // Shield
                int amp = Math.min(3, 1 + s.sp.pw / 4);
                Vec3d at = feet(s);
                Fx.send(c.w, Fx.M_DOME, c.el, at.x, at.y, at.z, 0, 0, 0, 2.5 * c.size, 0, 16, -1);
                for (EntityPlayer o : c.w.getEntitiesWithinAABB(EntityPlayer.class, p.getEntityBoundingBox().grow(4 * c.size, 3, 4 * c.size))) {
                    o.addPotionEffect(new PotionEffect(MobEffects.ABSORPTION, 160, amp, false, true));
                    Fx.on(o, Fx.HEAL, c.el, 1.2, 0, 8);
                }
                break;
            }
            case 12: { // Summon
                final float d = dmg(s, true);
                final EntitySkillBolt spirit = new EntitySkillBolt(c, "spell", 2, 0.4F * c.size, p.posX, p.posY + 2, p.posZ, new Vec3d(0, 0, 0));
                spirit.maxLife = 160 + 10 * s.sp.sz;
                spirit.ghost = true;
                spirit.pierce = 999;
                spirit.onHit = (bb, t) -> { };
                spirit.onEnd = (bb, at) -> Fx.at(bb.world, Fx.FLASH, c.el, at.x, at.y, at.z, 1.0);
                spirit.onTick = bb -> {
                    double a = bb.ticksExisted * 0.08;
                    Vec3d want = new Vec3d(p.posX + Math.cos(a) * 1.6, p.posY + 2.2, p.posZ + Math.sin(a) * 1.6);
                    bb.motionX = (want.x - bb.posX) * 0.5;
                    bb.motionY = (want.y - bb.posY) * 0.5;
                    bb.motionZ = (want.z - bb.posZ) * 0.5;
                    bb.velocityChanged = true;
                    if (bb.ticksExisted % 15 == 0) {
                        EntityLivingBase best = null;
                        double bd = 144;
                        for (EntityLivingBase o : SkillShapes.around(bb.world, bb.posX, bb.posY, bb.posZ, 12)) {
                            double q = o.getDistanceSq(bb);
                            if (q < bd && o.canEntityBeSeen(bb)) {
                                bd = q;
                                best = o;
                            }
                        }
                        if (best != null) {
                            Vec3d from = new Vec3d(bb.posX, bb.posY, bb.posZ);
                            Vec3d to = new Vec3d(best.posX, best.posY + best.height * 0.5, best.posZ);
                            EntitySkillBolt shot = bolt(s, from, to.subtract(from), 0.2F * c.size, 1.2, d, false);
                            shot.target = best;
                            shot.homing = 0.3;
                        }
                    }
                };
                c.w.spawnEntity(spirit);
                break;
            }
            default:
                break;
        }
    }

    /** Area hit without the explosion effect (Nova draws its own ring); Delay / Linger / Cluster still apply. */
    private static void impactNoFx(S s, Vec3d at, double r, float dmg, double push) {
        if (s.has(DELAY) || s.has(LINGER) || s.has(CLUSTER)) {
            impact(s, at, r, dmg, push);
        } else {
            area(s, at, r, dmg, push);
            if (s.hooks != null) {
                s.hooks.onEnd(at);
            }
        }
    }

    private static void orbit(EntitySkillBolt b, final double r, final double phase, int life) {
        b.ghost = true;
        b.maxLife = life;
        b.pierce = 999;
        b.homing = 0;
        b.onTick = bb -> {
            EntityPlayer p = bb.ctx.p;
            double a = phase + bb.ticksExisted * 0.22;
            bb.motionX = p.posX + Math.cos(a) * r - bb.posX;
            bb.motionY = p.posY + 1.0 - bb.posY;
            bb.motionZ = p.posZ + Math.sin(a) * r - bb.posZ;
            bb.velocityChanged = true;
            if (bb.ticksExisted % 10 == 0) {
                bb.clearHits();
            }
        };
        b.onEnd = (bb, at) -> { };
    }
}
