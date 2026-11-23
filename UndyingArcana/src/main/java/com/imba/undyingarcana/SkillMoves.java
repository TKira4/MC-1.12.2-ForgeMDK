package com.imba.undyingarcana;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.MobEffects;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;

import static com.imba.undyingarcana.SkillServer.repeat;
import static com.imba.undyingarcana.SkillServer.schedule;
import static com.imba.undyingarcana.SkillShapes.hit;

/**
 * Every skill move. Quick and Art are per weapon family (wands: per element); Heavy is each weapon type's signature
 * skill (charged, tier I-III). Shapes: dash, slash, cone, line / beam, ring waves, bolts, rains, fields, auras.
 */
public final class SkillMoves {
    static final Random RNG = new Random();

    private SkillMoves() {
    }

    public static void cast(SkillCtx c) {
        if (ArmoryMoves.cast(c)) {
            return;
        }
        switch (c.slot) {
            case 0:
                quick(c);
                break;
            case 2:
                art(c);
                break;
            default:
                heavy(c);
                if (!c.legend.isEmpty()) {
                    legend(c);
                }
                int ev = Math.max(c.er, c.rr);
                if (ev >= 2) {
                    Fx.send(c.w, Fx.RUNES, Fx.HOLY, c.p.posX, c.p.posY + 0.05, c.p.posZ, 0, 0, 0, ev >= 4 ? 4.0 : 2.8, 0, 16, -1);
                }
                if (ev >= 4) {
                    Fx.send(c.w, Fx.SPIRAL, Fx.HOLY, c.p.posX, c.p.posY, c.p.posZ, 0, 0, 0, 1.4, 2.6, 12, -1);
                    snd(c, "minecraft:entity.firework.twinkle", 1.0F, 0.8F);
                }
                break;
        }
    }

    // ------------------------------------------------------------------ small helpers
    static void snd(SkillCtx c, String id, float v, float pitch) {
        Fx.sound(c.p, id, v, pitch);
    }

    static void at(SkillCtx c, int kind, double x, double y, double z, double size) {
        Fx.at(c.w, kind, c.el, x, y, z, size);
    }

    static void fx(SkillCtx c, int kind, int el, double x, double y, double z, double ax, double ay, double az, double size, double extra, int dur) {
        Fx.send(c.w, kind, el, x, y, z, ax, ay, az, size, extra, dur, -1);
    }

    static Vec3d hdir(SkillCtx c) {
        return new Vec3d(c.lx, 0, c.lz);
    }

    static Vec3d chest(EntityPlayer p) {
        return new Vec3d(p.posX, p.posY + 1.1, p.posZ);
    }

    static void shake(SkillCtx c, double radius, float power, int ticks) {
        Fx.shake(c.w, c.p.posX, c.p.posY, c.p.posZ, radius, power, ticks);
    }

    static void effect(EntityLivingBase e, Potion pot, int ticks, int amp) {
        if (pot != null) {
            e.addPotionEffect(new PotionEffect(pot, ticks, amp, false, true));
        }
    }

    static void debuffArea(SkillCtx c, double x, double y, double z, double r, Potion pot, int ticks, int amp) {
        for (EntityLivingBase t : SkillShapes.around(c.w, x, y, z, r)) {
            t.addPotionEffect(new PotionEffect(pot, ticks, amp, false, false));
        }
    }

    static List<EntityPlayer> allies(SkillCtx c, double r) {
        return c.w.getEntitiesWithinAABB(EntityPlayer.class, c.p.getEntityBoundingBox().grow(r, 3, r));
    }

    /** Polls every tick until the player lands (after minDelay) or maxDelay passes; no fall damage meanwhile. */
    static void onLanding(final EntityPlayerMP p, final int minDelay, final int maxDelay, final Runnable r) {
        final int[] k = {0};
        final Runnable[] poll = new Runnable[1];
        poll[0] = () -> {
            k[0]++;
            p.fallDistance = 0;
            if (p.isDead) {
                return;
            }
            if ((k[0] >= minDelay && p.onGround) || k[0] >= maxDelay) {
                r.run();
            } else {
                schedule(1, poll[0]);
            }
        };
        schedule(1, poll[0]);
    }

    /** Ground point the player aims at (max range). */
    static Vec3d aimGround(SkillCtx c, double range) {
        Vec3d a = SkillShapes.aim(c.p, range);
        double gy = SkillShapes.ground(c.w, a.x, a.y + 0.5, a.z);
        return new Vec3d(a.x, gy, a.z);
    }

    /** Skill arrow: a glowing homing arrow that deals arrow damage (Archery, Ranger, Focus, Power and headshots apply). */
    static EntitySkillBolt arrow(SkillCtx c, double x, double y, double z, double vx, double vy, double vz, double dmg, boolean fire) {
        return arrow(c, x, y, z, vx, vy, vz, dmg, fire, null, 0.2);
    }

    static EntitySkillBolt arrow(SkillCtx c, double x, double y, double z, double vx, double vy, double vz, double dmg, boolean fire,
            EntityLivingBase target, double homing) {
        EntitySkillBolt b = new EntitySkillBolt(c, "arrow", 3, 0.3F, x, y, z, new Vec3d(vx, vy, vz));
        b.damage = (float) dmg;
        b.maxLife = 40;
        b.homing = homing;
        b.target = target;
        b.fire = fire || c.flame ? 4 : 0;
        c.w.spawnEntity(b);
        return b;
    }

    /** Visible enemies within range in front of the player, the one nearest the crosshair first. */
    static List<EntityLivingBase> aimTargets(SkillCtx c, double range, double minDot) {
        final Vec3d eye = new Vec3d(c.p.posX, c.eyeY(), c.p.posZ);
        final Vec3d look = c.p.getLookVec();
        List<EntityLivingBase> out = new ArrayList<>();
        final Map<EntityLivingBase, Double> score = new HashMap<>();
        for (EntityLivingBase t : SkillShapes.around(c.w, c.p.posX, c.p.posY, c.p.posZ, range, 8, 8)) {
            Vec3d to = new Vec3d(t.posX, t.posY + t.height * 0.5, t.posZ).subtract(eye);
            double d = to.lengthVector();
            if (d < 0.5) {
                continue;
            }
            double dot = look.dotProduct(to.scale(1.0 / d));
            if (dot >= minDot && c.p.canEntityBeSeen(t)) {
                out.add(t);
                score.put(t, dot - d * 0.004);
            }
        }
        out.sort((a, b) -> Double.compare(score.get(b), score.get(a)));
        return out;
    }

    /** Picks the target of arrow i: the crosshair target gets every other arrow, the rest are shared out. */
    static EntityLivingBase pick(List<EntityLivingBase> list, int i) {
        if (list.isEmpty()) {
            return null;
        }
        if (list.size() == 1 || i % 2 == 0) {
            return list.get(0);
        }
        return list.get(1 + (i / 2) % (list.size() - 1));
    }

    static void rain(SkillCtx c, double cx, double cy, double cz, double spread, int count, double dmg, boolean fire) {
        for (int i = 0; i < count; i++) {
            double ox = (RNG.nextDouble() * 2 - 1) * spread, oz = (RNG.nextDouble() * 2 - 1) * spread;
            arrow(c, cx + ox, cy + 9 + RNG.nextDouble() * 3, cz + oz, (RNG.nextDouble() - 0.5) * 0.2, -2.8, (RNG.nextDouble() - 0.5) * 0.2, dmg, fire, null, 0.12);
        }
    }

    public static EntitySkillBolt bolt(SkillCtx c, String move, int look, float size, double speed, Vec3d dir, float dmg) {
        Vec3d d = dir.normalize();
        double x = c.p.posX + d.x * 0.8, y = c.eyeY() - 0.15 + d.y * 0.8, z = c.p.posZ + d.z * 0.8;
        EntitySkillBolt b = new EntitySkillBolt(c, move, look, size, x, y, z, d.scale(speed));
        b.damage = dmg;
        b.maxLife = (int) (34 / Math.max(0.4, speed)) + 6;
        c.w.spawnEntity(b);
        Fx.at(c.w, Fx.FLASH, c.el, x, y, z, 0.4);
        return b;
    }

    /** Gun projectile: bullet / pellet / rifle round / shell (EntitySkillBolt). */
    public static EntitySkillBolt bullet(SkillCtx c, String move, Vec3d from, Vec3d dir, double speed, float dmg, int pierce, int life) {
        int look = "shell".equals(move) ? 2 : 1;
        float size = "shell".equals(move) ? 0.45F : ("pellet".equals(move) ? 0.14F : 0.2F);
        EntitySkillBolt b = new EntitySkillBolt(c, move, look, size, from.x, from.y, from.z, dir.normalize().scale(speed));
        b.damage = dmg;
        b.pierce = pierce;
        b.maxLife = life;
        c.w.spawnEntity(b);
        return b;
    }

    static Vec3d muzzle(SkillCtx c) {
        Vec3d l = c.p.getLookVec();
        return new Vec3d(c.p.posX + l.x * 0.9, c.eyeY() - 0.15 + l.y * 0.9, c.p.posZ + l.z * 0.9);
    }

    static boolean head(Entity b, EntityLivingBase t) {
        return t.height >= 0.9F && b.posY >= t.posY + t.getEyeHeight() - 0.35;
    }

    private static Entity byId(SkillCtx c, int id) {
        return c.w.getEntityByID(id);
    }

    // ================================================================== QUICK
    private static void quick(final SkillCtx c) {
        double[] st = SkillCatalog.quickStats(c.in);
        final EntityPlayerMP p = c.p;
        float base = c.dmg(st[0], false);
        switch (c.in.family) {
            case "blade": {
                boolean rapier = "rapier".equals(c.in.type);
                final float dmg = rapier ? base * 1.2F : base;
                double dist = st[1] * c.size * (rapier ? 1.4 : 1.0);
                final Vec3d from = new Vec3d(p.posX, p.posY + 1.0, p.posZ);
                final double go = Math.max(1.0, SkillShapes.freeDistance(p, c.lx, c.lz, dist));
                double v = SkillShapes.dashSpeed(p, go);
                SkillShapes.launch(p, c.lx * v, p.onGround ? 0.12 : p.motionY, c.lz * v);
                final Set<Integer> once = SkillShapes.set();
                SkillShapes.line(c, from, hdir(c), go + 1.2, rapier ? 1.0 : 1.4, dmg, hit().push(0.2), once);
                fx(c, Fx.DASH, c.el, p.posX, p.posY, p.posZ, p.posX + c.lx * go, p.posY, p.posZ + c.lz * go, 0, 0, 4);
                snd(c, "minecraft:entity.player.attack.sweep", 1.0F, 1.4F);
                snd(c, "minecraft:entity.enderdragon.flap", 0.5F, 1.8F);
                schedule(3, () -> {
                    fx(c, Fx.SLASH, c.el, p.posX, p.posY + 1.1, p.posZ, c.lx, 0, c.lz, 2.6, 2.6, 3);
                    SkillShapes.areaOnce(c, p.posX, p.posY, p.posZ, 2.5, dmg * 0.6F, hit(), once);
                    if ("katana".equals(c.in.type)) {
                        fx(c, Fx.XSLASH, c.el, p.posX + c.lx * 1.5, p.posY + 1.1, p.posZ + c.lz * 1.5, c.lx, 0, c.lz, 1.3, 0, 4);
                    }
                    snd(c, "minecraft:entity.player.attack.strong", 0.8F, 1.3F);
                });
                if ("dagger".equals(c.in.type)) {
                    effect(p, MobEffects.INVISIBILITY, 20, 0);
                }
                if ("parrying_dagger".equals(c.in.type)) {
                    effect(p, MobEffects.RESISTANCE, 30, 0);
                }
                if ("saber".equals(c.in.type)) {
                    schedule(7, () -> {
                        fx(c, Fx.SLASH, c.el, p.posX, p.posY + 1.0, p.posZ, c.lx, 0, c.lz, 2.4, 2.4, 3);
                        SkillShapes.area(c, p.posX, p.posY, p.posZ, 2.5, dmg * 0.4F, hit().noFx());
                        snd(c, "minecraft:entity.player.attack.sweep", 0.8F, 1.7F);
                    });
                }
                break;
            }
            case "heavy": {
                final double r = st[1] * c.size;
                final float dmg = base;
                final double x = p.posX, y = p.posY, z = p.posZ;
                fx(c, Fx.RING, c.el, x, y, z, 0, 0, 0, r, 0, 6);
                Fx.at(c.w, Fx.DEBRIS, c.el, x, y, z, 2.0);
                shake(c, 12, 0.8F, 6);
                snd(c, "minecraft:entity.generic.explode", 0.6F, 1.4F);
                snd(c, "minecraft:block.anvil.land", 0.5F, 0.6F);
                final Set<Integer> once = SkillShapes.set();
                repeat(0, 2, 3, i -> SkillShapes.areaOnce(c, x, y, z, r * (i + 1) / 3.0, dmg, hit().lift(0.45).push(0.25), once));
                break;
            }
            case "polearm": {
                boolean staff = "staff".equals(c.in.type);
                double len = st[1] * c.size;
                Vec3d from = new Vec3d(p.posX, p.posY + 1.2, p.posZ);
                Vec3d dir = c.look.normalize();
                Vec3d end = from.add(dir.scale(len));
                RayTraceResult rt = c.w.rayTraceBlocks(from, end, false, true, false);
                if (rt != null) {
                    end = rt.hitVec;
                    len = end.distanceTo(from);
                }
                SkillShapes.line(c, from, dir, len, 1.0, base, hit().push(staff ? 1.1 : 0.35), null);
                SkillShapes.launch(p, c.lx * 0.6, p.motionY, c.lz * 0.6);
                fx(c, Fx.BEAM, c.el, from.x, from.y, from.z, end.x, end.y, end.z, 0.45, 0, 3);
                at(c, Fx.IMPACT, end.x, end.y, end.z, 1.2);
                snd(c, "minecraft:entity.player.attack.strong", 1.0F, 1.2F);
                snd(c, staff ? "minecraft:entity.enderdragon.flap" : "minecraft:entity.arrow.shoot", 0.6F, staff ? 1.4F : 0.5F);
                break;
            }
            case "bow": {
                EntitySkillBolt b = bolt(c, "snap", 3, 0.45F, 3.4 * c.speed, c.look, base);
                b.homing = 0.08;
                b.fire = c.flame ? 4 : 0;
                b.pierce = 3;
                SkillShapes.launch(p, -c.lx * 0.7, p.onGround ? 0.28 : p.motionY, -c.lz * 0.7);
                snd(c, "minecraft:entity.arrow.shoot", 1.0F, 1.5F);
                snd(c, "minecraft:entity.firework.blast", 0.6F, 1.6F);
                break;
            }
            case "gun": {
                double side = RNG.nextBoolean() ? 1 : -1;
                double ang = Math.PI + side * 0.8;
                double ca = Math.cos(ang), sa = Math.sin(ang);
                double dx = c.lx * ca - c.lz * sa, dz = c.lx * sa + c.lz * ca;
                double v = SkillShapes.dashSpeed(p, 4.5);
                SkillShapes.launch(p, dx * v, p.onGround ? 0.2 : p.motionY, dz * v);
                fx(c, Fx.DASH, Fx.WIND, p.posX, p.posY, p.posZ, p.posX + dx * 4, p.posY, p.posZ + dz * 4, 0, 0, 3);
                GunLogic.instantReload(p, p.getHeldItemMainhand(), c.in.type);
                effect(p, MobEffects.SPEED, 30, 1);
                snd(c, "minecraft:entity.player.attack.knockback", 1.0F, 1.5F);
                break;
            }
            default:
                wandQuick(c, st, base);
                break;
        }
    }

    private static void wandQuick(final SkillCtx c, double[] st, final float dmg) {
        final EntityPlayerMP p = c.p;
        switch (c.in.wandElement()) {
            case "fire": {
                EntitySkillBolt b = bolt(c, "fireball", 2, 0.5F, 1.25 * c.speed, c.look, dmg);
                b.radius = st[1] * c.size;
                snd(c, "minecraft:entity.blaze.shoot", 1.0F, 1.0F);
                break;
            }
            case "ice": {
                EntitySkillBolt b = bolt(c, "shard", 1, 0.35F, 2.0 * c.speed, c.look, dmg);
                b.homing = 0.12;
                snd(c, "minecraft:entity.snowball.throw", 1.0F, 0.7F);
                snd(c, "minecraft:block.glass.break", 0.3F, 2.0F);
                break;
            }
            case "lightning": {
                EntitySkillBolt b = bolt(c, "spark", 1, 0.3F, 3.2 * c.speed, c.look, dmg);
                b.radius = st[1];
                snd(c, "minecraft:entity.lightning.impact", 0.3F, 2.0F);
                snd(c, "minecraft:entity.firework.blast", 0.5F, 1.8F);
                break;
            }
            case "necromancy": {
                EntitySkillBolt b = bolt(c, "soul", 0, 0.45F, 1.3 * c.speed, c.look, dmg);
                b.homing = 0.08;
                snd(c, "minecraft:entity.wither.shoot", 0.4F, 1.6F);
                break;
            }
            case "earth": {
                final double len = st[1] * c.size;
                final double x = p.posX, y = p.posY, z = p.posZ;
                final double lx = c.lx, lz = c.lz;
                fx(c, Fx.SPIKES, Fx.EARTH, x + lx, y, z + lz, lx, 0, lz, 0, len, 6);
                final Set<Integer> once = SkillShapes.set();
                repeat(0, 1, 6, i -> {
                    double a = 1 + len * i / 6.0;
                    Vec3d from = new Vec3d(x + lx * a, y + 0.5, z + lz * a);
                    SkillShapes.line(c, from, new Vec3d(lx, 0, lz), len / 6.0 + 0.5, 1.2, dmg, hit().lift(0.55).stagger(), once);
                });
                snd(c, "minecraft:entity.generic.explode", 0.4F, 0.6F);
                snd(c, "minecraft:block.stone.break", 1.0F, 0.6F);
                break;
            }
            case "sorcery": {
                EntitySkillBolt b = bolt(c, "void", 0, 0.5F, 1.2 * c.speed, c.look, dmg);
                b.radius = 4;
                snd(c, "minecraft:entity.endermen.teleport", 0.6F, 1.6F);
                break;
            }
            case "healing": {
                float heal = 4.0F * c.tierDmg;
                p.heal(heal);
                Fx.on(p, Fx.HEAL, Fx.HOLY, 1.5, 0, 10);
                EntityPlayer best = null;
                double bd = 10;
                for (EntityPlayer o : allies(c, 10)) {
                    if (o != p && o.getDistance(p) < bd && o.getHealth() < o.getMaxHealth()) {
                        bd = o.getDistance(p);
                        best = o;
                    }
                }
                if (best != null) {
                    best.heal(heal);
                    Fx.on(best, Fx.HEAL, Fx.HOLY, 1.5, 0, 10);
                    fx(c, Fx.LIGHTNING, Fx.HOLY, p.posX, p.posY + 1.2, p.posZ, best.posX, best.posY + 1.2, best.posZ, 0.6, 0, 4);
                }
                List<EntityLivingBase> foes = SkillShapes.around(c.w, p.posX, p.posY, p.posZ, 8);
                if (!foes.isEmpty()) {
                    EntityLivingBase t = foes.get(RNG.nextInt(foes.size()));
                    fx(c, Fx.PILLAR, Fx.HOLY, t.posX, t.posY, t.posZ, 0, 0, 0, 4, 0.8, 8);
                    SkillShapes.damage(c, t, dmg * 1.5F, hit());
                }
                snd(c, "minecraft:block.note.chime", 1.0F, 1.4F);
                break;
            }
            default: {
                EntitySkillBolt b = bolt(c, "arcane", 0, 0.4F, 1.5 * c.speed, c.look, dmg);
                b.homing = 0.18;
                snd(c, "minecraft:entity.illusion_illager.cast_spell", 0.6F, 1.5F);
                break;
            }
        }
    }

    // ------------------------------------------------------------------ bolt callbacks
    public static void boltHit(EntitySkillBolt b, EntityLivingBase t) {
        SkillCtx c = b.ctx;
        if (b.onHit != null) {
            b.onHit.accept(b, t);
            return;
        }
        switch (b.move) {
            case "spark": {
                SkillShapes.damage(c, t, b.damage, hit());
                int chains = (int) Math.max(1, b.radius);
                for (EntityLivingBase o : SkillShapes.around(c.w, t.posX, t.posY, t.posZ, 6)) {
                    if (o == t || chains <= 0) {
                        continue;
                    }
                    chains--;
                    Fx.send(c.w, Fx.LIGHTNING, c.el, t.posX, t.posY + t.height * 0.6, t.posZ, o.posX, o.posY + o.height * 0.6, o.posZ, 0.8, 0, 4, -1);
                    SkillShapes.damage(c, o, b.damage * 0.7F, hit().noFx());
                }
                break;
            }
            case "soul":
                if (SkillShapes.damage(c, t, b.damage, hit())) {
                    c.p.heal(b.damage * 0.3F);
                    Fx.send(c.w, Fx.LIGHTNING, Fx.NECRO, t.posX, t.posY + t.height * 0.6, t.posZ, c.p.posX, c.p.posY + 1.1, c.p.posZ, 0.5, 0, 4, -1);
                }
                break;
            case "bullet":
            case "pellet":
            case "rifle":
            case "shell": {
                float d = b.damage;
                if ("rifle".equals(b.move) && head(b, t)) {
                    d *= "widowmaker".equals(c.legend) ? 2.0F : 1.33F;
                }
                c.direct = b;
                SkillShapes.damage(c, t, d, hit().push("pellet".equals(b.move) ? 0.2 : 0.1).fire(b.fire).noFx());
                c.direct = null;
                Fx.at(c.w, Fx.IMPACT, c.el, b.posX, b.posY, b.posZ, "rifle".equals(b.move) ? 1.2 : 0.6);
                if ("peacemaker".equals(c.legend) && "bullet".equals(b.move) && RNG.nextFloat() < 0.6F) {
                    EntityLivingBase best = null;
                    double bd = 10;
                    for (EntityLivingBase o : SkillShapes.around(c.w, t.posX, t.posY, t.posZ, 9)) {
                        double dd = o.getDistance(t);
                        if (o != t && dd < bd) {
                            bd = dd;
                            best = o;
                        }
                    }
                    if (best != null) {
                        Vec3d from = new Vec3d(t.posX, t.posY + t.height * 0.6, t.posZ);
                        Vec3d to = new Vec3d(best.posX, best.posY + best.height * 0.6, best.posZ);
                        bullet(c, "ricochet", from, to.subtract(from), 3.5, b.damage * 0.8F, 0, 6);
                        Fx.sound(t, "minecraft:entity.arrow.hit", 0.6F, 2.0F);
                    }
                }
                break;
            }
            case "arrow":
            case "snap":
            case "hydra": {
                c.direct = b;
                double push = ("snap".equals(b.move) ? 0.35 : 0.12) + c.punch * 0.3;
                boolean ok = SkillShapes.damage(c, t, b.damage, hit().push(push).fire(b.fire).noFx());
                c.direct = null;
                if (!ok) {
                    break;
                }
                long now = c.w.getTotalWorldTime();
                if (c.lastDing != now) {
                    c.lastDing = now;
                    SkillServer.privateSound(c.p, "minecraft:entity.arrow.hit_player", 0.35F, 1.3F + RNG.nextFloat() * 0.4F);
                }
                if ("hydra".equals(b.move)) {
                    // Hydra's Fan: each hit splits into two small arrows that seek other enemies
                    List<EntityLivingBase> near = SkillShapes.around(c.w, t.posX, t.posY, t.posZ, 8);
                    near.remove(t);
                    for (int k = 0; k < 2; k++) {
                        EntityLivingBase o = near.isEmpty() ? null : near.get(k % near.size());
                        double a = RNG.nextDouble() * Math.PI * 2;
                        EntitySkillBolt s2 = arrow(c, t.posX, t.posY + t.height * 0.7, t.posZ, Math.cos(a) * 1.2, 0.5, Math.sin(a) * 1.2,
                                b.damage * 0.45F, b.fire > 0, o, 0.35);
                        s2.maxLife = 20;
                    }
                }
                break;
            }
            default:
                c.direct = b;
                SkillShapes.damage(c, t, b.damage, hit().push(0.15).fire(b.fire));
                c.direct = null;
                break;
        }
    }

    public static void boltEnd(EntitySkillBolt b, Vec3d at) {
        SkillCtx c = b.ctx;
        if (b.onEnd != null) {
            b.onEnd.accept(b, at);
            return;
        }
        switch (b.move) {
            case "fireball":
                Fx.at(c.w, Fx.EXPLOSION, c.el, at.x, at.y, at.z, b.radius);
                SkillShapes.area(c, at.x, at.y, at.z, b.radius, b.damage * 0.7F, hit().fire(3).push(0.4).from(at).noFx());
                Fx.shake(c.w, at.x, at.y, at.z, 10, 0.6F, 5);
                Fx.sound(c.w, at.x, at.y, at.z, "minecraft:entity.generic.explode", 0.8F, 1.3F);
                break;
            case "shell": {
                Fx.at(c.w, Fx.EXPLOSION, c.el == Fx.STEEL ? Fx.FIRE : c.el, at.x, at.y, at.z, b.radius);
                c.direct = b;
                SkillShapes.area(c, at.x, at.y, at.z, b.radius, b.damage * 0.7F, hit().push(0.5).lift(0.25).from(at).noFx());
                c.direct = null;
                Fx.shake(c.w, at.x, at.y, at.z, 12, 0.8F, 5);
                Fx.sound(c.w, at.x, at.y, at.z, "minecraft:entity.generic.explode", 0.9F, 1.1F);
                if ("thunderclap".equals(c.legend)) {
                    int k = 0;
                    for (EntityLivingBase o : SkillShapes.around(c.w, at.x, at.y, at.z, 7)) {
                        if (k++ >= 3) {
                            break;
                        }
                        Fx.send(c.w, Fx.LIGHTNING, Fx.LIGHTNING_E, at.x, at.y + 0.5, at.z, o.posX, o.posY + o.height * 0.6, o.posZ, 1.0, 0, 4, -1);
                        SkillShapes.damage(c, o, b.damage * 0.5F, hit().noFx());
                    }
                }
                break;
            }
            case "void":
                Fx.at(c.w, Fx.EXPLOSION, c.el, at.x, at.y, at.z, 1.6);
                for (EntityLivingBase t : SkillShapes.around(c.w, at.x, at.y, at.z, b.radius)) {
                    SkillShapes.pull(t, at.x, at.z, 0.6);
                }
                SkillShapes.area(c, at.x, at.y, at.z, 2.0, b.damage * 0.8F, hit().noFx());
                Fx.sound(c.w, at.x, at.y, at.z, "minecraft:entity.endermen.teleport", 0.8F, 0.6F);
                break;
            case "arcane":
                Fx.at(c.w, Fx.NOVA, c.el, at.x, at.y, at.z, 1.2);
                break;
            case "shard":
                Fx.at(c.w, Fx.NOVA, c.el, at.x, at.y, at.z, 0.8);
                Fx.sound(c.w, at.x, at.y, at.z, "minecraft:block.glass.break", 0.6F, 1.6F);
                break;
            case "soul":
                Fx.at(c.w, Fx.NOVA, c.el, at.x, at.y, at.z, 1.0);
                break;
            case "arrow":
            case "hydra":
                Fx.at(c.w, Fx.IMPACT, c.el, at.x, at.y, at.z, 0.55);
                break;
            default:
                Fx.at(c.w, Fx.IMPACT, c.el, at.x, at.y, at.z, 1.0);
                break;
        }
    }

    // ================================================================== HEAVY
    private static void heavy(final SkillCtx c) {
        double[] st = SkillCatalog.heavyStats(c.in.type);
        final float dmg = c.dmg(st[0], false);
        final double r = Math.max(4.0, st[1]) * c.size;
        final EntityPlayerMP p = c.p;
        final double px = p.posX, py = p.posY, pz = p.posZ;
        switch (c.in.type) {
            case "dagger": {
                double go = SkillShapes.freeDistance(p, c.lx, c.lz, 3 * c.size);
                SkillShapes.launch(p, c.lx * SkillShapes.dashSpeed(p, go), 0.1, c.lz * SkillShapes.dashSpeed(p, go));
                fx(c, Fx.DASH, c.el, px, py, pz, px + c.lx * go, py, pz + c.lz * go, 0, 0, 4);
                schedule(3, () -> {
                    SkillShapes.area(c, p.posX, p.posY, p.posZ, r, dmg, hit());
                    Fx.send(c.w, Fx.SPIN, c.el, p.posX, p.posY, p.posZ, 0, 0, 0, 2.0, 0, 6, p.getEntityId());
                    at(c, Fx.NOVA, p.posX, p.posY + 1.0, p.posZ, 2.0);
                    effect(p, MobEffects.INVISIBILITY, 60, 0);
                    snd(c, "minecraft:entity.player.attack.crit", 1.0F, 1.4F);
                    snd(c, "minecraft:entity.enderdragon.flap", 0.6F, 1.8F);
                });
                break;
            }
            case "parrying_dagger":
                effect(p, MobEffects.RESISTANCE, 40, 2);
                effect(p, MobEffects.STRENGTH, 60, 1);
                Fx.send(c.w, Fx.SPIN, c.el, px, py, pz, 0, 0, 0, 1.4, 0, 8, p.getEntityId());
                fx(c, Fx.RING, c.el, px, py, pz, 0, 0, 0, 2.5, 0, 4);
                snd(c, "minecraft:item.shield.block", 1.0F, 1.0F);
                snd(c, "minecraft:block.anvil.place", 0.4F, 2.0F);
                break;
            case "longsword":
            case "vanilla_sword":
                Fx.send(c.w, Fx.SPIN, c.el, px, py, pz, 0, 0, 0, Math.min(3.5, r * 0.7), 0, 10, p.getEntityId());
                repeat(0, 5, 2, i -> {
                    SkillShapes.area(c, p.posX, p.posY, p.posZ, r, dmg * 0.6F, hit().push(0.4));
                    snd(c, "minecraft:entity.player.attack.sweep", 1.0F, i == 0 ? 1.0F : 0.7F);
                });
                break;
            case "katana": {
                final Vec3d from = chest(p);
                final double go = Math.max(1.0, SkillShapes.freeDistance(p, c.lx, c.lz, 6 * c.size));
                double v = SkillShapes.dashSpeed(p, go);
                SkillShapes.launch(p, c.lx * v, 0.1, c.lz * v);
                final Set<Integer> cut = SkillShapes.set();
                SkillShapes.line(c, from, hdir(c), go + 1, 2.0, dmg * 0.6F, hit(), cut);
                fx(c, Fx.DASH, c.el, px, py, pz, px + c.lx * go, py, pz + c.lz * go, 0, 0, 4);
                snd(c, "minecraft:entity.player.attack.strong", 1.0F, 1.2F);
                schedule(3, () -> SkillShapes.areaOnce(c, p.posX, p.posY, p.posZ, r * 0.6, dmg * 0.6F, hit(), cut));
                schedule(10, () -> {
                    // the delayed draw cut: every enemy that was passed gets slashed again
                    for (int id : cut) {
                        Entity e = byId(c, id);
                        if (e instanceof EntityLivingBase && !e.isDead) {
                            fx(c, Fx.XSLASH, c.el, e.posX, e.posY + e.height * 0.6, e.posZ, c.lx, 0, c.lz, 1.2, 0, 4);
                            SkillShapes.damage(c, (EntityLivingBase) e, dmg * 0.7F, hit().noFx());
                        }
                    }
                    snd(c, "minecraft:entity.player.attack.sweep", 1.0F, 1.7F);
                    snd(c, "minecraft:entity.player.attack.crit", 1.0F, 0.8F);
                    if (!cut.isEmpty()) {
                        shake(c, 10, 0.6F, 5);
                    }
                });
                break;
            }
            case "saber": {
                effect(p, MobEffects.HASTE, 100, 2);
                effect(p, MobEffects.STRENGTH, 100, 0);
                final float cutDmg = c.dmg(3, true);
                repeat(0, 2, 4, i -> {
                    fx(c, Fx.SLASH, c.el, p.posX, p.posY + 1.0 + (i % 2) * 0.2, p.posZ, c.lx, 0, c.lz, 2.8, 2.4, 3);
                    SkillShapes.cone(c, 3.5, 1.2, cutDmg, hit().noFx());
                    snd(c, "minecraft:entity.player.attack.sweep", 0.8F, 1.5F + i * 0.1F);
                });
                break;
            }
            case "rapier": {
                double go = Math.max(1.0, SkillShapes.freeDistance(p, c.lx, c.lz, 4 * c.size));
                SkillShapes.launch(p, c.lx * SkillShapes.dashSpeed(p, go), 0.05, c.lz * SkillShapes.dashSpeed(p, go));
                fx(c, Fx.DASH, c.el, px, py, pz, px + c.lx * go, py, pz + c.lz * go, 0, 0, 3);
                schedule(3, () -> {
                    Vec3d from = chest(p);
                    Vec3d dir = c.look.normalize();
                    double len = 5 * c.size;
                    SkillShapes.line(c, from, dir, len, 0.9, dmg, hit(), null);
                    Vec3d end = from.add(dir.scale(len));
                    fx(c, Fx.BEAM, c.el, from.x, from.y, from.z, end.x, end.y, end.z, 0.3, 0, 3);
                    at(c, Fx.FLASH, end.x, end.y, end.z, 1.2);
                    snd(c, "minecraft:entity.player.attack.crit", 1.0F, 1.6F);
                    snd(c, "minecraft:entity.arrow.hit_player", 0.6F, 1.5F);
                });
                break;
            }
            case "greatsword":
            case "vanilla_axe":
                fx(c, Fx.SLASH, c.el, px, py + 1.0, pz, c.lx, 0, c.lz, Math.min(4.5, r * 0.8), 3.0, 3);
                SkillShapes.cone(c, r, 1.75, dmg, hit().lift(st[3]).push(st[4]));
                Fx.at(c.w, Fx.DEBRIS, c.el, c.fx(2.5), py, c.fz(2.5), 1.6);
                shake(c, 10, 0.6F, 5);
                snd(c, "minecraft:entity.player.attack.knockback", 1.0F, 0.7F);
                snd(c, "minecraft:entity.zombie.attack_iron_door", 0.5F, 1.2F);
                break;
            case "hammer": {
                final double lift = st[3];
                SkillShapes.launch(p, c.lx * 0.5, 0.95, c.lz * 0.5);
                at(c, Fx.RING, px, py, pz, 1.5);
                snd(c, "minecraft:entity.player.attack.knockback", 1.0F, 0.6F);
                onLanding(p, 5, 30, () -> {
                    double x = p.posX, y = p.posY, z = p.posZ;
                    Fx.at(c.w, Fx.EXPLOSION, c.el, x, y, z, r * 0.5);
                    fx(c, Fx.RING, c.el, x, y, z, 0, 0, 0, r, 0, 6);
                    SkillShapes.area(c, x, y, z, r, dmg, hit().lift(lift).push(0.3));
                    Fx.shake(c.w, x, y, z, 14, 1.4F, 10);
                    Fx.sound(c.w, x, y, z, "minecraft:entity.generic.explode", 1.0F, 0.8F);
                    Fx.sound(c.w, x, y, z, "minecraft:block.anvil.land", 0.7F, 0.6F);
                });
                break;
            }
            case "warhammer": {
                debuffArea(c, px, py, pz, r, MobEffects.SLOWNESS, 80, 2);
                final double[] radii = {r * 0.45, r * 0.75, r};
                final double[] lifts = {0.9, 0.6, 0.45};
                final float[] mult = {0.6F, 0.45F, 0.35F};
                repeat(0, 6, 3, i -> {
                    fx(c, Fx.RING, c.el, px, py, pz, 0, 0, 0, radii[i], 0, 5);
                    fx(c, Fx.SPIKES, Fx.EARTH, px, py, pz, 0, 0, 0, radii[i], 0, 1);
                    SkillShapes.area(c, px, py, pz, radii[i], dmg * mult[i] * 2.2F, hit().lift(lifts[i]));
                    Fx.shake(c.w, px, py, pz, 16, 1.2F - i * 0.3F, 6);
                    Fx.sound(c.w, px, py, pz, "minecraft:entity.generic.explode", 0.9F - i * 0.2F, 0.7F - i * 0.1F);
                });
                break;
            }
            case "mace":
                Fx.at(c.w, Fx.EXPLOSION, c.el, c.fx(1.5), py + 0.5, c.fz(1.5), 1.6);
                SkillShapes.area(c, px, py, pz, r, dmg, hit().stagger());
                debuffArea(c, px, py, pz, r, MobEffects.WEAKNESS, 100, 1);
                debuffArea(c, px, py, pz, r, MobEffects.SLOWNESS, 60, 1);
                shake(c, 10, 0.7F, 5);
                snd(c, "minecraft:block.anvil.land", 1.0F, 0.8F);
                break;
            case "battleaxe":
                effect(p, MobEffects.STRENGTH, 160, 1);
                effect(p, MobEffects.SPEED, 160, 0);
                if (p.getHealth() > 5.0F) {
                    p.setHealth(p.getHealth() - 4.0F);
                }
                Fx.on(p, Fx.AURA, c.el, 0.9, 0, 160);
                fx(c, Fx.SPIRAL, c.el, px, py, pz, 0, 0, 0, 1.0, 2.2, 12);
                snd(c, "minecraft:entity.polar_bear.warning", 1.0F, 0.8F);
                snd(c, "minecraft:entity.enderdragon.growl", 0.4F, 1.4F);
                break;
            case "spear": {
                Vec3d from = chest(p);
                Vec3d dir = c.look.normalize();
                double len = st[1] * c.size;
                SkillShapes.line(c, from, dir, len, 1.2, dmg, hit().push(0.3), null);
                Vec3d end = from.add(dir.scale(len));
                fx(c, Fx.BEAM, c.el, from.x, from.y, from.z, end.x, end.y, end.z, 0.55, 0, 4);
                at(c, Fx.IMPACT, end.x, end.y, end.z, 1.4);
                SkillShapes.launch(p, c.lx * 0.5, p.motionY, c.lz * 0.5);
                snd(c, "minecraft:entity.player.attack.strong", 1.0F, 1.0F);
                snd(c, "minecraft:entity.arrow.shoot", 0.8F, 0.5F);
                break;
            }
            case "pike":
                fx(c, Fx.RING, c.el, px, py, pz, 0, 0, 0, r, 0, 4);
                SkillShapes.area(c, px, py, pz, r, dmg, hit().lift(st[3]).push(st[4]));
                snd(c, "minecraft:entity.player.attack.knockback", 1.0F, 0.9F);
                schedule(4, () -> {
                    fx(c, Fx.RING, Fx.WIND, p.posX, p.posY, p.posZ, 0, 0, 0, r * 1.2, 0, 5);
                    SkillShapes.area(c, p.posX, p.posY, p.posZ, r * 1.2, 1.0F, hit().push(1.2).noFx().noStatus());
                    snd(c, "minecraft:entity.player.attack.knockback", 1.0F, 0.6F);
                });
                break;
            case "lance": {
                effect(p, MobEffects.SPEED, 60, 2);
                effect(p, MobEffects.RESISTANCE, 60, 0);
                SkillShapes.launch(p, c.lx * 1.6, 0.1, c.lz * 1.6);
                final Set<Integer> once = SkillShapes.set();
                final double[] last = {px, py, pz};
                repeat(0, 1, 10, i -> {
                    SkillShapes.areaOnce(c, p.posX, p.posY, p.posZ, 2.2, dmg, hit().push(0.9).lift(0.2), once);
                    fx(c, Fx.DASH, Fx.WIND, last[0], last[1], last[2], p.posX, p.posY, p.posZ, 0, 0, 2);
                    last[0] = p.posX;
                    last[1] = p.posY;
                    last[2] = p.posZ;
                });
                snd(c, "minecraft:entity.horse.gallop", 1.0F, 1.0F);
                snd(c, "minecraft:entity.player.attack.strong", 1.0F, 0.8F);
                break;
            }
            case "halberd":
                fx(c, Fx.SLASH, c.el, px, py + 1.0, pz, c.lx, 0, c.lz, Math.min(4.5, r * 0.8), Math.PI * 2, 4);
                SkillShapes.area(c, px, py, pz, r, dmg, hit().push(st[4]));
                snd(c, "minecraft:entity.player.attack.sweep", 1.0F, 0.8F);
                snd(c, "minecraft:entity.player.attack.sweep", 1.0F, 1.2F);
                break;
            case "glaive": {
                final int fire = (int) st[5];
                fx(c, Fx.SLASH, c.el, px, py + 1.0, pz, c.lx, 0, c.lz, 3.2, 2.8, 3);
                SkillShapes.area(c, px, py, pz, r * 0.7, dmg, hit().fire(fire).push(st[4]));
                fx(c, Fx.CRESCENT, c.el, px + c.lx, py + 0.9, pz + c.lz, c.lx, 0, c.lz, 4.0, 0.9, 10);
                final Set<Integer> once = SkillShapes.set();
                final double lx = c.lx, lz = c.lz;
                repeat(1, 2, 5, i -> {
                    Vec3d from = new Vec3d(px + lx * (1 + 1.8 * i), py + 1.0, pz + lz * (1 + 1.8 * i));
                    SkillShapes.line(c, from, new Vec3d(lx, 0, lz), 2.0, 2.0, dmg * 0.6F, hit().fire(fire), once);
                });
                schedule(5, () -> {
                    fx(c, Fx.RING, c.el, p.posX, p.posY, p.posZ, 0, 0, 0, r * 1.2, 0, 5);
                    SkillShapes.area(c, p.posX, p.posY, p.posZ, r * 1.2, dmg * 0.5F, hit().fire(fire).noFx());
                });
                snd(c, "minecraft:item.firecharge.use", 1.0F, 0.9F);
                snd(c, "minecraft:entity.blaze.shoot", 0.8F, 0.8F);
                break;
            }
            case "staff": {
                SkillShapes.launch(p, c.lx * 0.3, 1.1, c.lz * 0.3);
                effect(p, MobEffects.JUMP_BOOST, 50, 3);
                effect(p, MobEffects.RESISTANCE, 60, 0);
                effect(p, Potion.REGISTRY.getObject(new ResourceLocation("potioncore", "slow_fall")), 100, 0);
                fx(c, Fx.RING, c.el, px, py, pz, 0, 0, 0, 3.5, 0, 5);
                SkillShapes.area(c, px, py, pz, 3.5, c.dmg(2, false), hit().push(1.0));
                onLanding(p, 4, 60, () -> at(c, Fx.RING, p.posX, p.posY, p.posZ, 2.0));
                snd(c, "minecraft:entity.player.attack.knockback", 1.0F, 1.3F);
                snd(c, "minecraft:entity.enderdragon.flap", 0.6F, 1.2F);
                break;
            }
            case "longbow": {
                // Arrow Rain: a dense, fast downpour on the aimed area (warning circle first)
                int count = "skirmisher".equals(c.path) ? 40 : 32;
                if (c.rr >= 2) {
                    count += 12;
                }
                if (c.rr >= 4) {
                    count += 16;
                }
                final boolean fire = c.rr >= 2;
                final Vec3d aim = aimGround(c, 26);
                final double spread = 3.5 * c.size;
                final int waves = 8;
                final int per = Math.max(1, count / waves);
                fx(c, Fx.RUNES, fire ? Fx.FIRE : c.el, aim.x, aim.y + 0.05, aim.z, 0, 0, 0, spread + 0.5, 0, 22);
                fx(c, Fx.RING, fire ? Fx.FIRE : c.el, aim.x, aim.y + 0.1, aim.z, 0, 0, 0, spread + 0.8, 0, 10);
                for (int k = 0; k < 6; k++) {
                    arrow(c, px + c.lx * 0.5, c.eyeY(), pz + c.lz * 0.5, c.lx * 0.8 + (RNG.nextDouble() - 0.5) * 0.4, 2.6, c.lz * 0.8 + (RNG.nextDouble() - 0.5) * 0.4,
                            0, false, null, 0).maxLife = 6;
                }
                repeat(3, 2, waves, i -> {
                    rain(c, aim.x, aim.y, aim.z, spread, per, dmg, fire);
                    if (i % 2 == 0) {
                        Fx.sound(c.w, aim.x, aim.y + 6, aim.z, "minecraft:entity.arrow.shoot", 0.8F, 0.7F + RNG.nextFloat() * 0.5F);
                    }
                });
                if (c.rr >= 4) {
                    schedule(22, () -> {
                        Fx.at(c.w, Fx.EXPLOSION, Fx.FIRE, aim.x, aim.y, aim.z, 3.0);
                        SkillShapes.area(c, aim.x, aim.y, aim.z, 3.5, dmg * 3, hit().fire(4).from(aim).push(0.5));
                        Fx.shake(c.w, aim.x, aim.y, aim.z, 14, 0.6F, 6);
                    });
                }
                snd(c, "minecraft:entity.arrow.shoot", 1.0F, 0.6F);
                snd(c, "minecraft:entity.firework.launch", 0.8F, 0.8F);
                break;
            }
            case "crossbow": {
                Vec3d from = chest(p);
                Vec3d dir = c.look.normalize();
                double len = (st[1] + (c.rr >= 2 ? 3 : 0)) * c.size;
                SkillShapes.Hit h = hit().push(st[4]);
                if (c.rr >= 4) {
                    h.stagger();
                }
                SkillShapes.line(c, from, dir, len, 1.0, dmg, h, null);
                Vec3d end = from.add(dir.scale(len));
                fx(c, Fx.BEAM, c.rr >= 4 ? Fx.LIGHTNING_E : c.el, from.x, from.y, from.z, end.x, end.y, end.z, 0.6, 0, 5);
                SkillShapes.launch(p, -c.lx * 0.4, p.motionY, -c.lz * 0.4);
                snd(c, "minecraft:entity.arrow.shoot", 1.0F, 0.5F);
                snd(c, "minecraft:entity.firework.blast", 0.8F, 1.2F);
                break;
            }
            case "bow": {
                // Hunter's Volley: a burst of homing arrows that curve out and converge on what you aim at
                int n = "skirmisher".equals(c.path) ? 16 : 12;
                if (c.rr >= 2) {
                    n += 6;
                }
                if (c.rr >= 4) {
                    n += 6;
                }
                final boolean hydra = c.rr >= 4;
                final boolean fire = c.rr >= 4;
                final List<EntityLivingBase> targets = aimTargets(c, 28, 0.55);
                final int total = n;
                final int bursts = 3;
                repeat(0, 2, bursts, w -> {
                    Vec3d look = p.getLookVec();
                    int from = w * total / bursts, to = (w + 1) * total / bursts;
                    for (int k = from; k < to; k++) {
                        double sp = 2.4 * c.speed;
                        double ang, up;
                        if (targets.isEmpty()) {
                            ang = (RNG.nextDouble() - 0.5) * 0.25;
                            up = (RNG.nextDouble() - 0.5) * 0.08;
                        } else {
                            ang = ((k % 2 == 0) ? 1 : -1) * (0.35 + RNG.nextDouble() * 0.5);
                            up = 0.15 + RNG.nextDouble() * 0.3;
                        }
                        double ca = Math.cos(ang), sa = Math.sin(ang);
                        double vx = (look.x * ca - look.z * sa) * sp, vz = (look.x * sa + look.z * ca) * sp;
                        EntitySkillBolt b2 = arrow(c, p.posX + look.x * 1.0, c.eyeY() - 0.1, p.posZ + look.z * 1.0, vx, (look.y + up) * sp, vz, dmg,
                                fire, pick(targets, k), targets.isEmpty() ? 0.1 : 0.32);
                        if (hydra) {
                            b2.move = "hydra";
                        }
                    }
                    at(c, Fx.FLASH, p.posX + look.x * 1.2, c.eyeY(), p.posZ + look.z * 1.2, 0.8);
                    snd(c, "minecraft:entity.arrow.shoot", 1.0F, 0.8F + w * 0.2F);
                    snd(c, "minecraft:entity.arrow.shoot", 0.8F, 1.3F + w * 0.1F);
                });
                SkillShapes.launch(p, -c.lx * 0.3, p.motionY, -c.lz * 0.3);
                break;
            }
            case "switch":
                SkillShapes.launch(p, -c.lx * 1.2, 0.3, -c.lz * 1.2);
                effect(p, MobEffects.SPEED, 40, 2);
                effect(p, MobEffects.INVISIBILITY, 40, 0);
                effect(p, MobEffects.JUMP_BOOST, 40, 1);
                fx(c, Fx.DASH, Fx.WIND, px, py, pz, px - c.lx * 3, py, pz - c.lz * 3, 0, 0, 3);
                snd(c, "minecraft:entity.player.attack.knockback", 1.0F, 1.4F);
                break;
            case "pistol":
                repeat(0, 2, 6, i -> {
                    Vec3d mz = muzzle(c);
                    bullet(c, "bullet", mz, GunLogic.spread(p, p.getLookVec(), 0.05), 4.5, dmg, 0, 10);
                    Fx.at(c.w, Fx.FLASH, c.el == Fx.STEEL ? Fx.FIRE : c.el, mz.x, mz.y, mz.z, 0.5);
                    snd(c, "minecraft:entity.firework.blast", 0.9F, 1.5F + i * 0.05F);
                    SkillNet.NET.sendTo(new SkillNet.MsgShake(0.5F, 3), p);
                });
                break;
            case "shotgun": {
                if (c.status.isEmpty()) {
                    c.status = "fire";
                }
                Vec3d mz = muzzle(c);
                for (int i = 0; i < 14; i++) {
                    EntitySkillBolt b = bullet(c, "pellet", mz, GunLogic.spread(p, c.look, 0.22), 3.4, dmg, 0, 7);
                    b.fire = 5;
                }
                fx(c, Fx.CRESCENT, Fx.FIRE, mz.x, mz.y - 0.6, mz.z, c.lx, 0, c.lz, 4.0, 1.4, 5);
                Fx.at(c.w, Fx.EXPLOSION, Fx.FIRE, mz.x + c.lx, mz.y - 0.5, mz.z + c.lz, 1.2);
                snd(c, "minecraft:entity.blaze.shoot", 1.0F, 0.6F);
                snd(c, "minecraft:entity.firework.large_blast", 1.0F, 0.7F);
                GunLogic.recoil(p, 1.4F, -0.4);
                break;
            }
            case "rifle": {
                Vec3d mz = muzzle(c);
                bullet(c, "rifle", mz, c.look, 7.0, dmg, 8, 12);
                Vec3d end = mz.add(c.look.normalize().scale(40));
                fx(c, Fx.BEAM, c.el, mz.x, mz.y, mz.z, end.x, end.y, end.z, 0.3, 0, 5);
                snd(c, "minecraft:entity.firework.large_blast", 1.0F, 1.2F);
                snd(c, "minecraft:entity.lightning.impact", 0.4F, 2.0F);
                GunLogic.recoil(p, 1.8F, -0.3);
                break;
            }
            case "cannon": {
                Vec3d mz = muzzle(c);
                for (int i = 0; i < 5; i++) {
                    double a = (i - 2) * 0.15;
                    double ca = Math.cos(a), sa = Math.sin(a);
                    Vec3d d = new Vec3d(c.look.x * ca - c.look.z * sa, c.look.y + 0.05, c.look.x * sa + c.look.z * ca);
                    EntitySkillBolt b = bullet(c, "shell", mz, d, 1.9, dmg, 0, 30);
                    b.radius = 2.2;
                }
                Fx.at(c.w, Fx.EXPLOSION, Fx.FIRE, mz.x, mz.y - 0.5, mz.z, 1.0);
                snd(c, "minecraft:entity.generic.explode", 1.0F, 0.8F);
                GunLogic.recoil(p, 2.0F, -0.6);
                break;
            }
            case "wand_magic":
                fx(c, Fx.RUNES, c.el, px, py + 0.05, pz, 0, 0, 0, 2.0, 0, 16);
                at(c, Fx.NOVA, px, py + 1.0, pz, r * 0.6);
                fx(c, Fx.RING, c.el, px, py, pz, 0, 0, 0, r, 0, 6);
                SkillShapes.area(c, px, py, pz, r, dmg, hit().push(st[4]));
                schedule(6, () -> {
                    at(c, Fx.NOVA, px, py + 1.0, pz, r * 0.8);
                    SkillShapes.area(c, px, py, pz, r * 1.2, dmg * 0.5F, hit().noFx());
                    snd(c, "minecraft:entity.illusion_illager.cast_spell", 0.7F, 1.6F);
                });
                snd(c, "minecraft:entity.illusion_illager.cast_spell", 1.0F, 1.2F);
                break;
            case "wand_fire":
                fx(c, Fx.RING, c.el, px, py, pz, 0, 0, 0, r, 0, 6);
                fx(c, Fx.FIELD, c.el, px, py, pz, 0, 0, 0, r * 0.8, 0, 60);
                SkillShapes.area(c, px, py, pz, r, dmg, hit().fire((int) st[5]).push(st[4]));
                schedule(5, () -> {
                    fx(c, Fx.RING, c.el, px, py, pz, 0, 0, 0, r * 1.3, 0, 6);
                    SkillShapes.area(c, px, py, pz, r * 1.3, dmg * 0.5F, hit().fire(3).noFx());
                    snd(c, "minecraft:entity.blaze.shoot", 0.8F, 0.7F);
                });
                snd(c, "minecraft:entity.blaze.shoot", 1.0F, 0.8F);
                snd(c, "minecraft:item.firecharge.use", 1.0F, 0.8F);
                break;
            case "wand_ice":
                at(c, Fx.NOVA, px, py + 1.0, pz, r * 0.5);
                fx(c, Fx.SPIKES, c.el, px, py, pz, 0, 0, 0, r * 0.6, 0, 1);
                fx(c, Fx.RING, c.el, px, py, pz, 0, 0, 0, r, 0, 6);
                SkillShapes.area(c, px, py, pz, r, dmg, hit());
                for (EntityLivingBase t : SkillShapes.around(c.w, px, py, pz, r)) {
                    SkillStatus.addStatus(c, t, dmg);
                    t.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 80, 3, false, false));
                }
                // second beat: the frost cracks for half damage on everything still inside
                schedule(10, () -> {
                    fx(c, Fx.SPIKES, c.el, px, py, pz, 0, 0, 0, r * 0.8, 0, 1);
                    SkillShapes.area(c, px, py, pz, r, dmg * 0.6F, hit().noFx());
                    snd(c, "minecraft:block.glass.break", 0.8F, 1.3F);
                });
                snd(c, "minecraft:block.glass.break", 1.0F, 0.8F);
                break;
            case "wand_lightning": {
                int n = 0;
                final List<EntityLivingBase> struck = new ArrayList<>();
                for (EntityLivingBase t : SkillShapes.around(c.w, px, py, pz, r)) {
                    if (n++ >= 10) {
                        break;
                    }
                    fx(c, Fx.LIGHTNING, c.el, px, py + 1.4, pz, t.posX, t.posY + t.height * 0.6, t.posZ, 1.0, 0, 4);
                    SkillShapes.damage(c, t, dmg, hit().push(st[4]));
                    struck.add(t);
                }
                // aftershock: the same targets are struck again from the sky for half damage
                schedule(8, () -> {
                    for (EntityLivingBase t : struck) {
                        if (!t.isDead) {
                            fx(c, Fx.M_BOLT, c.el, t.posX, t.posY + 10, t.posZ, t.posX, t.posY, t.posZ, 0.2, 0, 5);
                            SkillShapes.damage(c, t, dmg * 0.5F, hit().noFx());
                        }
                    }
                    snd(c, "minecraft:entity.lightning.impact", 0.6F, 1.5F);
                });
                fx(c, Fx.PILLAR, c.el, px, py, pz, 0, 0, 0, 3.0, 1.0, 8);
                fx(c, Fx.RING, c.el, px, py, pz, 0, 0, 0, r, 0, 5);
                shake(c, 10, 0.5F, 4);
                snd(c, "minecraft:entity.lightning.impact", 1.0F, 1.2F);
                snd(c, "minecraft:entity.lightning.thunder", 0.4F, 1.4F);
                break;
            }
            case "wand_necromancy": {
                int hits = 0;
                for (EntityLivingBase t : SkillShapes.around(c.w, px, py, pz, r)) {
                    if (SkillShapes.damage(c, t, dmg, hit())) {
                        hits++;
                        fx(c, Fx.LIGHTNING, c.el, t.posX, t.posY + t.height * 0.6, t.posZ, px, py + 1.1, pz, 0.5, 0, 4);
                    }
                }
                p.heal(Math.min(12.0F, hits * dmg * 0.25F));
                fx(c, Fx.SPIRAL, c.el, px, py, pz, 0, 0, 0, 1.4, 2.4, 12);
                // second drain pulse
                schedule(10, () -> {
                    int h2 = 0;
                    for (EntityLivingBase t : SkillShapes.around(c.w, px, py, pz, r)) {
                        if (SkillShapes.damage(c, t, dmg * 0.6F, hit().noFx())) {
                            h2++;
                        }
                    }
                    p.heal(Math.min(6.0F, h2 * dmg * 0.15F));
                    fx(c, Fx.RING, c.el, px, py, pz, 0, 0, 0, r, 0, 5);
                });
                snd(c, "minecraft:entity.wither.ambient", 0.4F, 1.5F);
                break;
            }
            case "wand_earth":
                fx(c, Fx.SPIKES, c.el, px, py, pz, 0, 0, 0, r * 0.7, 0, 1);
                fx(c, Fx.RING, c.el, px, py, pz, 0, 0, 0, r, 0, 6);
                SkillShapes.area(c, px, py, pz, r, dmg, hit().lift(st[3]).stagger());
                // aftershock: a wider ring of spikes
                schedule(6, () -> {
                    fx(c, Fx.SPIKES, c.el, px, py, pz, 0, 0, 0, r * 0.9, 0, 1);
                    fx(c, Fx.RING, c.el, px, py, pz, 0, 0, 0, r * 1.3, 0, 6);
                    SkillShapes.area(c, px, py, pz, r * 1.3, dmg * 0.5F, hit().stagger().noFx());
                    snd(c, "minecraft:entity.generic.explode", 0.6F, 0.7F);
                });
                shake(c, 12, 1.0F, 6);
                snd(c, "minecraft:entity.generic.explode", 0.8F, 0.5F);
                break;
            case "wand_sorcery": {
                double go = SkillShapes.blink(p, c.lx, c.lz, 8 * c.size);
                fx(c, Fx.DASH, c.el, px, py, pz, px + c.lx * go, py, pz + c.lz * go, 0, 0, 3);
                double x = px + c.lx * go, z = pz + c.lz * go;
                Fx.at(c.w, Fx.EXPLOSION, c.el, x, py, z, 2.5);
                SkillShapes.area(c, x, py, z, r, dmg, hit().push(st[4]).from(new Vec3d(x, py, z)));
                // the rift left behind implodes for half damage
                schedule(8, () -> {
                    Fx.at(c.w, Fx.EXPLOSION, c.el, px, py, pz, 2.0);
                    for (EntityLivingBase t : SkillShapes.around(c.w, px, py, pz, r)) {
                        SkillShapes.pull(t, px, pz, 0.5);
                    }
                    SkillShapes.area(c, px, py, pz, r, dmg * 0.5F, hit().noFx());
                    Fx.sound(c.w, px, py, pz, "minecraft:entity.endermen.teleport", 0.8F, 0.6F);
                });
                snd(c, "minecraft:entity.endermen.teleport", 1.0F, 1.0F);
                break;
            }
            case "wand_healing": {
                int amp = ("advanced".equals(c.in.mat) || "master".equals(c.in.mat) || "legend".equals(c.in.mat)
                        || "arcane".equals(c.in.mat) || "soulforged".equals(c.in.mat)) ? 1 : 0;
                for (EntityPlayer o : allies(c, 8 * c.size)) {
                    effect(o, MobEffects.INSTANT_HEALTH, 1, amp);
                    effect(o, MobEffects.REGENERATION, 120, 1);
                    effect(o, MobEffects.RESISTANCE, 120, 0);
                    Fx.on(o, Fx.HEAL, Fx.HOLY, 1.5, 0, 10);
                }
                fx(c, Fx.RUNES, Fx.HOLY, px, py + 0.05, pz, 0, 0, 0, 8 * c.size, 0, 20);
                fx(c, Fx.PILLAR, Fx.HOLY, px, py, pz, 0, 0, 0, 4.0, 1.2, 8);
                snd(c, "minecraft:entity.player.levelup", 0.8F, 1.5F);
                break;
            }
            default:
                break;
        }
    }

    /** Legendary weapons add a second effect to their Heavy skill. */
    private static void legend(final SkillCtx c) {
        final EntityPlayerMP p = c.p;
        final float dmg = c.dmg(SkillCatalog.heavyStats(c.in.type)[0], false);
        final double px = p.posX, py = p.posY, pz = p.posZ;
        switch (c.legend) {
            case "stormcaller":
                repeat(12, 6, 2, i -> {
                    SkillShapes.blink(p, c.lx, c.lz, 3);
                    fx(c, Fx.SLASH, c.el, p.posX, p.posY + 1.0, p.posZ, c.lx, 0, c.lz, 2.8, 3.0, 3);
                    SkillShapes.area(c, p.posX, p.posY, p.posZ, 3.5, dmg * 0.7F, hit().push(0.2));
                    snd(c, "minecraft:entity.lightning.impact", 0.6F, 1.8F);
                });
                break;
            case "earthbreaker":
            case "gaia":
                if ("gaia".equals(c.legend)) {
                    effect(p, MobEffects.RESISTANCE, 100, 1);
                }
                repeat(5, 6, 2, i -> {
                    double r = i == 0 ? 4.5 : 7.0;
                    fx(c, Fx.RING, Fx.EARTH, px, py, pz, 0, 0, 0, r, 0, 5);
                    fx(c, Fx.SPIKES, Fx.EARTH, px, py, pz, 0, 0, 0, r * 0.8, 0, 1);
                    SkillShapes.area(c, px, py, pz, r, dmg, hit().lift(0.5));
                    Fx.shake(c.w, px, py, pz, 14, 0.9F, 6);
                });
                break;
            case "bloodthirst":
                p.heal(8.0F);
                effect(p, MobEffects.STRENGTH, 160, 2);
                effect(p, MobEffects.REGENERATION, 160, 1);
                at(c, Fx.NOVA, px, py + 1.0, pz, 2.0);
                break;
            case "windpiercer": {
                Vec3d aim = aimGround(c, 24);
                rain(c, aim.x, aim.y, aim.z, 3.0, 16, dmg, true);
                fx(c, Fx.RING, Fx.FIRE, aim.x, aim.y, aim.z, 0, 0, 0, 3.5, 0, 5);
                break;
            }
            case "frostfang":
            case "glacier":
                debuffArea(c, px, py, pz, "glacier".equals(c.legend) ? 7 : 5, MobEffects.SLOWNESS, 100, 3);
                if ("glacier".equals(c.legend)) {
                    debuffArea(c, px, py, pz, 7, MobEffects.MINING_FATIGUE, 120, 1);
                    fx(c, Fx.SPIKES, Fx.ICE, px, py, pz, 0, 0, 0, 6, 0, 1);
                }
                at(c, Fx.NOVA, px, py + 1.0, pz, 3.0);
                snd(c, "minecraft:block.glass.break", 1.0F, 1.2F);
                break;
            case "phoenix":
                SkillShapes.area(c, px, py, pz, 7.0, dmg * 0.8F, hit().lift(0.3).push(0.3).fire(8));
                fx(c, Fx.RING, Fx.FIRE, px, py, pz, 0, 0, 0, 7.0, 0, 7);
                Fx.at(c.w, Fx.EXPLOSION, Fx.FIRE, px, py, pz, 3.0);
                snd(c, "minecraft:entity.enderdragon.growl", 0.5F, 1.6F);
                break;
            case "aegis_edge":
                for (EntityPlayer o : allies(c, 6)) {
                    effect(o, MobEffects.ABSORPTION, 200, 1);
                    effect(o, MobEffects.RESISTANCE, 120, 0);
                }
                SkillShapes.area(c, px, py, pz, 5.0, dmg * 0.5F, hit().lift(0.2).push(1.2));
                fx(c, Fx.RUNES, Fx.HOLY, px, py + 0.05, pz, 0, 0, 0, 3.0, 0, 20);
                snd(c, "minecraft:item.shield.block", 1.0F, 0.8F);
                break;
            case "dawnbreaker":
                schedule(4, () -> {
                    fx(c, Fx.SLASH, Fx.HOLY, p.posX, p.posY + 1.2, p.posZ, c.lx, 0, c.lz, 4.5, 3.2, 3);
                    SkillShapes.area(c, p.posX, p.posY, p.posZ, 6.5, dmg * 0.8F, hit().lift(0.3).push(0.6).fire(6));
                    fx(c, Fx.RING, Fx.FIRE, p.posX, p.posY, p.posZ, 0, 0, 0, 6.0, 0, 6);
                    snd(c, "minecraft:entity.blaze.shoot", 1.0F, 0.7F);
                });
                break;
            case "gravewarden":
            case "lich":
                debuffArea(c, px, py, pz, 6.5, MobEffects.WITHER, 120, 1);
                debuffArea(c, px, py, pz, 6.5, MobEffects.SLOWNESS, 80, 1);
                if ("lich".equals(c.legend)) {
                    p.heal(Math.min(10, SkillShapes.around(c.w, px, py, pz, 7).size() * 2));
                }
                fx(c, Fx.FIELD, Fx.NECRO, px, py, pz, 0, 0, 0, 5.5, 0, 40);
                snd(c, "minecraft:entity.wither.ambient", 0.6F, 1.4F);
                break;
            case "shadowfang":
                effect(p, MobEffects.INVISIBILITY, 80, 0);
                effect(p, MobEffects.SPEED, 80, 1);
                schedule(8, () -> {
                    SkillShapes.blink(p, c.lx, c.lz, 4);
                    SkillShapes.area(c, p.posX, p.posY, p.posZ, 4.0, dmg * 0.6F, hit());
                    at(c, Fx.NOVA, p.posX, p.posY + 1.0, p.posZ, 2.0);
                });
                break;
            case "starfall": {
                final Vec3d aim = aimGround(c, 28);
                fx(c, Fx.RUNES, Fx.ARCANE, aim.x, aim.y + 0.05, aim.z, 0, 0, 0, 4.0, 0, 16);
                repeat(4, 3, 3, i -> rain(c, aim.x, aim.y, aim.z, 4.0, 4, dmg, false));
                snd(c, "minecraft:entity.firework.twinkle", 1.0F, 1.0F);
                break;
            }
            case "thunderstring":
            case "stormlord": {
                boolean lord = "stormlord".equals(c.legend);
                double r = lord ? 9 : 7;
                boolean was = c.magic;
                c.magic = true;
                for (EntityLivingBase t : SkillShapes.around(c.w, px, py, pz, r)) {
                    fx(c, Fx.LIGHTNING, Fx.LIGHTNING_E, t.posX, t.posY + 8, t.posZ, t.posX, t.posY, t.posZ, 1.2, 0, 4);
                    SkillShapes.Hit h = lord ? hit().lift(0.4) : hit().stagger();
                    SkillShapes.damage(c, t, dmg * (lord ? 0.6F : 1.5F), h.noFx());
                }
                c.magic = was;
                fx(c, Fx.RING, Fx.LIGHTNING_E, px, py, pz, 0, 0, 0, r, 0, 6);
                snd(c, "minecraft:entity.lightning.thunder", 0.6F, 1.3F);
                break;
            }
            case "hydra": {
                Vec3d look = c.look;
                for (int i = 0; i < 7; i++) {
                    double a = (i - 3) * 0.18;
                    double ca = Math.cos(a), sa = Math.sin(a);
                    arrow(c, px + look.x, c.eyeY() - 0.1, pz + look.z, (look.x * ca - look.z * sa) * 2.6, look.y * 2.6, (look.z * ca + look.x * sa) * 2.6, dmg, false);
                }
                snd(c, "minecraft:entity.arrow.shoot", 1.0F, 0.7F);
                break;
            }
            case "venomstring":
                debuffArea(c, px, py, pz, 8, MobEffects.POISON, 160, 1);
                debuffArea(c, px, py, pz, 8, MobEffects.WEAKNESS, 120, 0);
                fx(c, Fx.FIELD, Fx.POISON, px, py, pz, 0, 0, 0, 7, 0, 60);
                snd(c, "minecraft:entity.slime.squish", 1.0F, 0.6F);
                break;
            case "archon":
                schedule(2, () -> {
                    Fx.at(c.w, Fx.EXPLOSION, Fx.SORCERY, p.posX, p.posY, p.posZ, 3.0);
                    SkillShapes.area(c, p.posX, p.posY, p.posZ, 5.5, dmg * 0.8F, hit().push(0.8));
                });
                break;
            case "seraph":
                for (EntityPlayer o : allies(c, 10)) {
                    effect(o, MobEffects.INSTANT_HEALTH, 1, 1);
                    effect(o, MobEffects.REGENERATION, 160, 1);
                }
                for (EntityLivingBase t : SkillShapes.around(c.w, px, py, pz, 6)) {
                    fx(c, Fx.PILLAR, Fx.HOLY, t.posX, t.posY, t.posZ, 0, 0, 0, 4, 0.8, 8);
                    SkillShapes.damage(c, t, dmg * 0.6F, hit().noFx());
                }
                snd(c, "minecraft:block.note.chime", 1.0F, 1.4F);
                break;
            default:
                break;
        }
    }

    // ================================================================== ART
    /** Wind-up shared by every Art: title, vignette, rune circle and gathering light. */
    static void artIntro(SkillCtx c) {
        EntityPlayerMP p = c.p;
        String col = c.legend.isEmpty() ? "light_purple" : "gold";
        String nm = c.name;
        SkillServer.cmd("title " + p.getName() + " times 2 26 8");
        SkillServer.cmd("title " + p.getName() + " subtitle {\"text\":\"" + nm + "\",\"color\":\"" + col + "\",\"bold\":true}");
        SkillServer.cmd("title " + p.getName() + " title {\"text\":\"\"}");
        Fx.send(c.w, Fx.VIGNETTE, c.el, p.posX, p.posY, p.posZ, 0, 0, 0, 0.6, 0, 26, -1);
        Fx.send(c.w, Fx.RUNES, c.el, p.posX, p.posY + 0.05, p.posZ, 0, 0, 0, 2.6, 0, 22, -1);
        Fx.send(c.w, Fx.GATHER, c.el, p.posX, p.posY, p.posZ, 0, 0, 0, 2.2, 3, 4, p.getEntityId());
        snd(c, "minecraft:block.enchantment_table.use", 1.0F, 0.6F);
        snd(c, "minecraft:entity.illusion_illager.prepare_mirror", 0.8F, 1.0F);
    }

    /** Release punctuation: screen flash for everyone near, big shake, layered boom. */
    static void artBoom(SkillCtx c, Vec3d at, float power) {
        Fx.send(c.w, Fx.SCREEN_FLASH, c.el, at.x, at.y, at.z, 0, 0, 0, 0.35 * power, 0, 6, -1);
        Fx.shake(c.w, at.x, at.y, at.z, 24, 1.6F * power, 12);
        Fx.sound(c.w, at.x, at.y, at.z, "minecraft:entity.generic.explode", 1.2F, 0.6F);
        Fx.sound(c.w, at.x, at.y, at.z, "minecraft:entity.lightning.thunder", 0.4F, 1.6F);
    }

    /** Legendary weapons awaken their Art: an element finisher at the Art's centre (+50% damage burst, status, unique look). */
    private static void awaken(final SkillCtx c, final Vec3d at, final double r, final float dmg) {
        Fx.send(c.w, Fx.SCREEN_FLASH, Fx.HOLY, at.x, at.y, at.z, 0, 0, 0, 0.3, 0, 6, -1);
        Fx.send(c.w, Fx.M_DOME, c.el, at.x, at.y, at.z, 0, 0, 0, r * 0.7, 0, 14, -1);
        Fx.send(c.w, Fx.PILLAR, c.el, at.x, at.y, at.z, 0, 0, 0, 9, 1.6, 16, -1);
        switch (c.element) {
            case "lightning":
                for (EntityLivingBase t : SkillShapes.around(c.w, at.x, at.y, at.z, r)) {
                    Fx.send(c.w, Fx.M_BOLT, c.el, t.posX, t.posY + 14, t.posZ, t.posX, t.posY, t.posZ, 0.25, 0, 8, -1);
                }
                break;
            case "fire":
                Fx.send(c.w, Fx.M_ORB, c.el, at.x - 4, at.y + 16, at.z - 2, at.x, at.y, at.z, 1.6, 0, 8, -1);
                Fx.send(c.w, Fx.FIELD, c.el, at.x, at.y, at.z, 0, 0, 0, r * 0.7, 0, 60, -1);
                break;
            case "ice":
                Fx.send(c.w, Fx.SPIKES, c.el, at.x, at.y, at.z, 0, 0, 0, r * 0.6, 0, 1, -1);
                break;
            case "earth":
                for (int k = 0; k < 6; k++) {
                    double a = Math.PI * 2 * k / 6;
                    Fx.send(c.w, Fx.M_CRACK, c.el, at.x, at.y, at.z, Math.cos(a), 0, Math.sin(a), r, 0, 40, -1);
                }
                break;
            case "necromancy":
            case "sorcery":
            case "magic":
            case "shadow":
                Fx.send(c.w, Fx.M_VORTEX, c.el, at.x, at.y, at.z, 0, 0, 0, r * 0.5, 5, 30, -1);
                break;
            default:
                Fx.send(c.w, Fx.RING, c.el, at.x, at.y, at.z, 0, 0, 0, r * 1.2, 0, 8, -1);
                break;
        }
        schedule(6, () -> {
            Fx.at(c.w, Fx.EXPLOSION, c.el, at.x, at.y + 0.3, at.z, r * 0.6);
            SkillShapes.area(c, at.x, at.y, at.z, r, dmg * 0.5F, hit().lift(0.4).from(at).noFx());
            artBoom(c, at, 1.0F);
        });
    }

    private static void art(final SkillCtx c) {
        double[] st = SkillCatalog.artStats(c.in);
        final EntityPlayerMP p = c.p;
        final float per = c.dmg(st[0], SkillCatalog.artPerHit(c.in));
        final double R = st[1] * c.size;
        final double px = p.posX, py = p.posY, pz = p.posZ;
        final Vec3d me = new Vec3d(px, py, pz);
        artIntro(c);
        if (!c.legend.isEmpty() && LegendArts.cast(c)) {
            return;
        }
        if (c.in.isWand()) {
            wandArt(c, st, per, R);
            if (!c.legend.isEmpty()) {
                final Vec3d at = aimGround(c, 24);
                schedule(24, () -> awaken(c, at, 6, per * 2));
            }
            return;
        }
        Vec3d center = me;
        int finish = 20;
        switch (c.in.type) {
            case "dagger": {
                List<EntityLivingBase> list = SkillShapes.around(c.w, px, py, pz, 10);
                list.sort((a, b) -> Double.compare(a.getDistanceSq(p), b.getDistanceSq(p)));
                effect(p, MobEffects.INVISIBILITY, 40, 0);
                effect(p, MobEffects.RESISTANCE, 40, 3);
                final int n = Math.min(5, list.size());
                for (int i = 0; i < n; i++) {
                    final EntityLivingBase t = list.get(i);
                    schedule(4 + i * 4, () -> {
                        if (t.isDead) {
                            return;
                        }
                        Vec3d tl = t.getLookVec();
                        double bx = t.posX - tl.x * 1.3, bz = t.posZ - tl.z * 1.3;
                        float yaw = (float) (Math.atan2(t.posZ - bz, t.posX - bx) * 180 / Math.PI) - 90F;
                        fx(c, Fx.DASH, Fx.SHADOW, p.posX, p.posY, p.posZ, bx, t.posY, bz, 0, 0, 3);
                        p.connection.setPlayerLocation(bx, t.posY, bz, yaw, 10F);
                        boolean low = t.getHealth() < t.getMaxHealth() * 0.3F;
                        fx(c, Fx.XSLASH, c.el, t.posX, t.posY + t.height * 0.6, t.posZ, -tl.x, 0, -tl.z, 1.4, 0, 4);
                        SkillShapes.damage(c, t, low ? per * 3 : per, hit().noFx());
                        Fx.sound(t, "minecraft:entity.player.attack.crit", 1.0F, low ? 0.6F : 1.3F);
                        if (low) {
                            Fx.send(c.w, Fx.PILLAR, Fx.BLOOD, t.posX, t.posY, t.posZ, 0, 0, 0, 3, 0.8, 8, -1);
                        }
                    });
                }
                finish = 6 + n * 4;
                break;
            }
            case "parrying_dagger":
                SkillEvents.startCounter(c, per, 60);
                Fx.send(c.w, Fx.M_DOME, c.el, px, py, pz, 0, 0, 0, 1.6, 1, 60, p.getEntityId());
                effect(p, MobEffects.SLOWNESS, 60, 1);
                finish = 60;
                break;
            case "longsword":
            case "vanilla_sword": {
                final Vec3d at = aimGround(c, 22);
                center = at;
                fx(c, Fx.M_SWORD, c.el == Fx.STEEL ? Fx.HOLY : c.el, at.x, at.y, at.z, c.lx, 0, c.lz, 7 * c.size, 10, 50);
                fx(c, Fx.RUNES, Fx.HOLY, at.x, at.y + 0.05, at.z, 0, 0, 0, R, 0, 12);
                schedule(10, () -> {
                    Fx.at(c.w, Fx.EXPLOSION, Fx.HOLY, at.x, at.y, at.z, R * 0.7);
                    SkillShapes.area(c, at.x, at.y, at.z, R, per, hit().lift(0.6).push(0.6).from(at));
                    for (int k = 0; k < 4; k++) {
                        double a = Math.PI / 2 * k + Math.atan2(c.lz, c.lx);
                        final double dx = Math.cos(a), dz = Math.sin(a);
                        Fx.send(c.w, Fx.CRESCENT, Fx.HOLY, at.x, at.y + 0.6, at.z, dx, 0, dz, 3.0, 1.0, 10, -1);
                        final Set<Integer> once = SkillShapes.set();
                        repeat(1, 2, 5, i -> SkillShapes.line(c, new Vec3d(at.x + dx * (1 + 2 * i), at.y + 0.8, at.z + dz * (1 + 2 * i)),
                                new Vec3d(dx, 0, dz), 2.2, 1.6, per * 0.5F, hit().push(0.6), once));
                    }
                    artBoom(c, at, 1.3F);
                });
                break;
            }
            case "katana": {
                final Vec3d at = new Vec3d(c.fx(4), py, c.fz(4));
                center = at;
                effect(p, MobEffects.INVISIBILITY, 24, 0);
                effect(p, MobEffects.RESISTANCE, 24, 2);
                for (EntityLivingBase t : SkillShapes.around(c.w, at.x, at.y, at.z, R)) {
                    t.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 24, 9, false, false));
                }
                fx(c, Fx.RUNES, c.el, at.x, at.y + 0.05, at.z, 0, 0, 0, R, 0, 22);
                repeat(2, 2, 8, i -> {
                    List<EntityLivingBase> list = SkillShapes.around(c.w, at.x, at.y, at.z, R);
                    if (!list.isEmpty()) {
                        EntityLivingBase t = list.get(RNG.nextInt(list.size()));
                        Fx.at(c.w, Fx.BARRAGE, c.el, t.posX, t.posY + t.height * 0.6, t.posZ, 1.4);
                        SkillShapes.damage(c, t, per, hit().noFx());
                    } else {
                        Fx.at(c.w, Fx.BARRAGE, c.el, at.x + (RNG.nextDouble() - 0.5) * R, at.y + 1, at.z + (RNG.nextDouble() - 0.5) * R, 1.4);
                    }
                    Fx.sound(c.w, at.x, at.y, at.z, "minecraft:entity.player.attack.sweep", 0.8F, 1.2F + i * 0.1F);
                });
                schedule(20, () -> {
                    Fx.send(c.w, Fx.XSLASH, c.el, at.x, at.y + 1.2, at.z, c.lx, 0, c.lz, 3.0, 0, 6, -1);
                    SkillShapes.area(c, at.x, at.y, at.z, R, per * 3, hit().push(0.5).from(at));
                    artBoom(c, at, 1.1F);
                });
                break;
            }
            case "saber": {
                if (c.el == Fx.STEEL) {
                    c.el = Fx.BLOOD;
                }
                final double[] angs = {-0.4, 0, 0.4};
                for (int k = 0; k < 3; k++) {
                    final double a = angs[k];
                    final double dx = c.lx * Math.cos(a) - c.lz * Math.sin(a), dz = c.lx * Math.sin(a) + c.lz * Math.cos(a);
                    final Set<Integer> once = SkillShapes.set();
                    schedule(2 + k * 3, () -> {
                        Fx.send(c.w, Fx.CRESCENT, c.el, p.posX + dx, p.posY + 0.9, p.posZ + dz, dx, 0, dz, 3.5, 1.0, 9, -1);
                        Fx.sound(p, "minecraft:entity.player.attack.sweep", 1.0F, 0.7F);
                    });
                    final double sx = px, sz = pz;
                    repeat(3 + k * 3, 1, 8, i -> {
                        int n = SkillShapes.line(c, new Vec3d(sx + dx * (1 + i), py + 1.0, sz + dz * (1 + i)), new Vec3d(dx, 0, dz), 1.2, 1.8, per, hit().push(0.3), once);
                        if (n > 0) {
                            p.heal(per * n * 0.25F);
                        }
                    });
                }
                center = new Vec3d(c.fx(5), py, c.fz(5));
                finish = 14;
                break;
            }
            case "rapier":
                repeat(1, 2, 12, i -> {
                    double a = (RNG.nextDouble() - 0.5) * 0.7;
                    double dx = c.lx * Math.cos(a) - c.lz * Math.sin(a), dz = c.lx * Math.sin(a) + c.lz * Math.cos(a);
                    Vec3d from = chest(p);
                    Vec3d to = from.addVector(dx * R, (RNG.nextDouble() - 0.5) * 0.8, dz * R);
                    fx(c, Fx.BEAM, c.el, from.x, from.y, from.z, to.x, to.y, to.z, 0.2, 0, 2);
                    SkillShapes.cone(c, R, 0.45, per, hit().noFx());
                    snd(c, "minecraft:entity.player.attack.crit", 0.5F, 1.6F + RNG.nextFloat() * 0.3F);
                });
                schedule(26, () -> {
                    Vec3d from = chest(p);
                    Vec3d dir = p.getLookVec().normalize();
                    Vec3d end = from.add(dir.scale(10));
                    fx(c, Fx.BEAM, c.el, from.x, from.y, from.z, end.x, end.y, end.z, 0.8, 0, 6);
                    SkillShapes.line(c, from, dir, 10, 1.2, per * 4, hit().push(0.8), null);
                    artBoom(c, end, 0.9F);
                });
                center = new Vec3d(c.fx(4), py, c.fz(4));
                finish = 26;
                break;
            case "greatsword":
            case "vanilla_axe": {
                fx(c, Fx.SLASH, c.el, px, py + 1.4, pz, c.lx, 0, c.lz, 4.5, 3.2, 4);
                fx(c, Fx.CRESCENT, c.el, px + c.lx, py + 1.0, pz + c.lz, c.lx, 0, c.lz, 6.0, 1.25, 12);
                fx(c, Fx.M_CRACK, Fx.EARTH, px + c.lx, py, pz + c.lz, c.lx, 0, c.lz, R, 0, 50);
                final Set<Integer> once = SkillShapes.set();
                final double lx = c.lx, lz = c.lz;
                repeat(1, 1, 12, i -> {
                    double a = 1 + 1.25 * i;
                    SkillShapes.line(c, new Vec3d(px + lx * a, py + 1.0, pz + lz * a), new Vec3d(lx, 0, lz), 1.4, 3.0, per, hit().lift(0.5).push(0.4), once);
                    if (i % 3 == 0) {
                        Fx.send(c.w, Fx.DEBRIS, Fx.EARTH, px + lx * a, py, pz + lz * a, 0, 0, 0, 1.5, 0, 1, -1);
                    }
                });
                artBoom(c, me, 1.0F);
                center = new Vec3d(c.fx(R), py, c.fz(R));
                finish = 14;
                break;
            }
            case "hammer":
                SkillShapes.launch(p, c.lx * 0.6, 1.35, c.lz * 0.6);
                Fx.on(p, Fx.AURA, c.el, 0.8, 0, 30);
                snd(c, "minecraft:entity.enderdragon.flap", 1.0F, 0.6F);
                onLanding(p, 6, 40, () -> {
                    double x = p.posX, y = p.posY, z = p.posZ;
                    Fx.at(c.w, Fx.EXPLOSION, c.el, x, y, z, R * 0.7);
                    Fx.send(c.w, Fx.SPIKES, Fx.EARTH, x, y, z, 0, 0, 0, R * 0.7, 0, 1, -1);
                    Fx.send(c.w, Fx.RING, c.el, x, y, z, 0, 0, 0, R, 0, 7, -1);
                    for (int k = 0; k < 5; k++) {
                        double a = Math.PI * 2 * k / 5;
                        Fx.send(c.w, Fx.M_CRACK, Fx.EARTH, x, y, z, Math.cos(a), 0, Math.sin(a), R, 0, 40, -1);
                    }
                    SkillShapes.area(c, x, y, z, R, per, hit().lift(0.8).push(0.6));
                    artBoom(c, new Vec3d(x, y, z), 1.5F);
                    if (!c.legend.isEmpty()) {
                        awaken(c, new Vec3d(x, y, z), R, per);
                    }
                });
                return;
            case "warhammer": {
                fx(c, Fx.M_CRACK, Fx.EARTH, px + c.lx, py, pz + c.lz, c.lx, 0, c.lz, R, 0, 60);
                final double lx = c.lx, lz = c.lz;
                final Set<Integer> once = SkillShapes.set();
                repeat(3, 2, 8, i -> {
                    double a = 2 + R * i / 8.0;
                    double x = px + lx * a, z = pz + lz * a;
                    Fx.send(c.w, Fx.SPIKES, Fx.EARTH, x, py, z, 0, 0, 0, 1.4, 0, 1, -1);
                    Fx.send(c.w, Fx.PILLAR, Fx.EARTH, x, py, z, 0, 0, 0, 2.5, 0.9, 8, -1);
                    SkillShapes.areaOnce(c, x, py, z, 2.2, per, hit().lift(0.9).stagger(), once);
                    Fx.shake(c.w, x, py, z, 14, 0.9F, 5);
                    Fx.sound(c.w, x, py, z, "minecraft:entity.generic.explode", 0.7F, 0.5F + i * 0.05F);
                });
                center = new Vec3d(c.fx(R), py, c.fz(R));
                finish = 20;
                break;
            }
            case "mace":
                Fx.send(c.w, Fx.M_DOME, c.el == Fx.STEEL ? Fx.HOLY : c.el, px, py + 2.2, pz, 0, 0, 0, 2.2, 0, 34, -1);
                repeat(2, 10, 3, i -> {
                    Fx.send(c.w, Fx.RING, Fx.HOLY, px, py, pz, 0, 0, 0, R, 0, 6, -1);
                    for (EntityLivingBase t : SkillShapes.around(c.w, px, py, pz, R)) {
                        t.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 20, 9, false, false));
                        SkillShapes.damage(c, t, per, hit().stagger().noFx());
                    }
                    Fx.sound(c.w, px, py, pz, "minecraft:block.note.bell", 1.5F, 0.5F + i * 0.1F);
                    Fx.shake(c.w, px, py, pz, 14, 0.7F, 5);
                });
                schedule(32, () -> {
                    Fx.at(c.w, Fx.EXPLOSION, Fx.HOLY, px, py, pz, R * 0.6);
                    SkillShapes.area(c, px, py, pz, R, per * 2.5F, hit().push(1.0).lift(0.4));
                    Fx.sound(c.w, px, py, pz, "minecraft:block.anvil.land", 1.0F, 0.5F);
                    artBoom(c, me, 1.2F);
                });
                finish = 32;
                break;
            case "battleaxe":
                if (c.el == Fx.STEEL) {
                    c.el = Fx.BLOOD;
                }
                effect(p, MobEffects.RESISTANCE, 64, 0);
                Fx.send(c.w, Fx.SPIN, c.el, px, py, pz, 0, 0, 0, 3.0, 0, 60, p.getEntityId());
                Fx.send(c.w, Fx.M_VORTEX, c.el, px, py, pz, 0, 0, 0, 2.6, 3.0, 60, p.getEntityId());
                repeat(0, 5, 12, i -> {
                    int n = 0;
                    for (EntityLivingBase t : SkillShapes.around(c.w, p.posX, p.posY, p.posZ, R)) {
                        SkillShapes.pull(t, p.posX, p.posZ, 0.4);
                        if (SkillShapes.damage(c, t, per * (1 + 0.15F * i), hit().noFx())) {
                            n++;
                        }
                    }
                    p.heal(n * per * 0.2F);
                    snd(c, "minecraft:entity.player.attack.sweep", 0.7F, 0.6F + i * 0.05F);
                });
                finish = 60;
                break;
            case "spear": {
                final Vec3d from = chest(p);
                final Vec3d dir = c.look.normalize();
                Vec3d end = from.add(dir.scale(R));
                RayTraceResult rt = c.w.rayTraceBlocks(from, end, false, true, false);
                final double len = rt != null ? rt.hitVec.distanceTo(from) : R;
                fx(c, Fx.M_SPEAR, c.el == Fx.STEEL ? Fx.WIND : c.el, from.x, from.y, from.z, dir.x, dir.y, dir.z, 3.2, 2.0, (int) (len / 2.0) + 4);
                final Set<Integer> once = SkillShapes.set();
                final int steps = (int) Math.ceil(len / 2.0);
                repeat(1, 1, steps, i -> SkillShapes.line(c, from.add(dir.scale(2.0 * i)), dir, 2.2, 1.3, per * 0.6F, hit().push(0.4), once));
                final Vec3d hitAt = from.add(dir.scale(len));
                schedule(steps + 1, () -> {
                    Fx.at(c.w, Fx.EXPLOSION, c.el == Fx.STEEL ? Fx.WIND : c.el, hitAt.x, hitAt.y, hitAt.z, 3.0);
                    SkillShapes.area(c, hitAt.x, hitAt.y, hitAt.z, 3.5, per, hit().push(0.8).lift(0.4).from(hitAt));
                    artBoom(c, hitAt, 1.1F);
                });
                snd(c, "minecraft:entity.enderdragon.growl", 0.6F, 1.4F);
                snd(c, "minecraft:entity.arrow.shoot", 1.0F, 0.4F);
                center = hitAt;
                finish = steps + 2;
                break;
            }
            case "pike": {
                final Vec3d at = new Vec3d(c.fx(2), py, c.fz(2));
                final double lx = c.lx, lz = c.lz;
                fx(c, Fx.M_WALL, c.el, at.x, at.y, at.z, lx, 0, lz, R, 0, 100);
                repeat(2, 5, 20, i -> {
                    for (EntityLivingBase t : SkillShapes.lineTargets(c.w, new Vec3d(at.x - lz * R / 2, py + 1, at.z + lx * R / 2), new Vec3d(-lz, 0, lx).scale(-1), R, 1.8)) {
                        t.motionX = lx * 0.9;
                        t.motionZ = lz * 0.9;
                        t.velocityChanged = true;
                        t.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 10, 2, false, false));
                        SkillShapes.damage(c, t, per, hit().noFx());
                    }
                });
                snd(c, "minecraft:item.shield.block", 1.0F, 0.6F);
                center = at;
                finish = 100;
                break;
            }
            case "lance": {
                effect(p, MobEffects.SPEED, 24, 4);
                effect(p, MobEffects.RESISTANCE, 30, 1);
                final Set<Integer> once = SkillShapes.set();
                final double lx = c.lx, lz = c.lz;
                final double[] last = {px, py, pz};
                repeat(0, 1, 20, i -> {
                    SkillShapes.launch(p, lx * 1.3, p.onGround ? 0.05 : p.motionY, lz * 1.3);
                    SkillShapes.areaOnce(c, p.posX, p.posY, p.posZ, 2.6, per, hit().lift(0.7).push(1.3), once);
                    Fx.send(c.w, Fx.DASH, Fx.WIND, last[0], last[1], last[2], p.posX, p.posY, p.posZ, 0, 0, 3, -1);
                    last[0] = p.posX;
                    last[1] = p.posY;
                    last[2] = p.posZ;
                    if (i % 4 == 0) {
                        snd(c, "minecraft:entity.horse.gallop", 1.0F, 0.8F);
                    }
                });
                schedule(21, () -> {
                    Vec3d at = new Vec3d(p.posX, p.posY, p.posZ);
                    Fx.at(c.w, Fx.EXPLOSION, Fx.WIND, at.x, at.y, at.z, 3.5);
                    SkillShapes.area(c, at.x, at.y, at.z, 4.5, per * 1.5F, hit().push(1.2).lift(0.5).from(at));
                    artBoom(c, at, 1.0F);
                });
                finish = 22;
                break;
            }
            case "halberd": {
                fx(c, Fx.SLASH, c.el, px, py + 1.0, pz, c.lx, 0, c.lz, 3.0, Math.PI * 2, 6);
                final Set<Integer> once = SkillShapes.set();
                SkillShapes.areaOnce(c, px, py, pz, 3.2, per, hit().push(0.4), once);
                schedule(7, () -> {
                    fx(c, Fx.SLASH, c.el == Fx.STEEL ? Fx.ARCANE : c.el, p.posX, p.posY + 1.2, p.posZ, -c.lx, 0, -c.lz, R, Math.PI * 2, 8);
                    Fx.send(c.w, Fx.RING, c.el, p.posX, p.posY, p.posZ, 0, 0, 0, R + 1, 0, 6, -1);
                    SkillShapes.area(c, p.posX, p.posY, p.posZ, R, per * 1.3F, hit().push(0.9).lift(0.3));
                    artBoom(c, new Vec3d(p.posX, p.posY, p.posZ), 1.0F);
                });
                finish = 8;
                break;
            }
            case "glaive": {
                if (c.status.isEmpty()) {
                    c.status = "fire";
                }
                fx(c, Fx.SLASH, Fx.FIRE, px, py + 1.0, pz, c.lx, 0, c.lz, R * 0.85, 4.7, 4);
                fx(c, Fx.SLASH, Fx.FIRE, px, py + 0.6, pz, c.lx, 0, c.lz, R * 0.6, 4.7, 5);
                final List<EntityLivingBase> hitList = new ArrayList<>();
                for (EntityLivingBase t : SkillShapes.around(c.w, px, py, pz, R)) {
                    double dx = t.posX - px, dz = t.posZ - pz, d = Math.sqrt(dx * dx + dz * dz);
                    if (d < 1.2 || (dx * c.lx + dz * c.lz) / d >= Math.cos(2.35)) {
                        hitList.add(t);
                        SkillShapes.damage(c, t, per, hit().fire(6).push(0.5));
                    }
                }
                schedule(3, () -> {
                    int kills = 0;
                    for (EntityLivingBase t : hitList) {
                        if (t.isDead || t.getHealth() <= 0) {
                            kills++;
                        }
                    }
                    if (kills > 0) {
                        SkillServer.reduceCooldown(p, 2, Math.min(600, kills * 240));
                        SkillServer.msg(p, "\u00a76Reaper's Harvest: \u00a7f" + kills + " souls \u00a77(cooldown -" + Math.min(30, kills * 12) + "s)");
                    }
                });
                snd(c, "minecraft:entity.blaze.shoot", 1.0F, 0.6F);
                artBoom(c, me, 0.9F);
                finish = 4;
                break;
            }
            case "staff":
                SkillShapes.launch(p, c.lx * 0.4, 1.2, c.lz * 0.4);
                effect(p, Potion.REGISTRY.getObject(new ResourceLocation("potioncore", "slow_fall")), 80, 0);
                onLanding(p, 6, 50, () -> {
                    final double x = p.posX, y = p.posY, z = p.posZ;
                    Fx.send(c.w, Fx.M_VORTEX, Fx.WIND, x, y, z, 0, 0, 0, 3.0, 6, 40, -1);
                    repeat(0, 5, 8, i -> {
                        for (EntityLivingBase t : SkillShapes.around(c.w, x, y, z, R)) {
                            SkillShapes.pull(t, x, z, 0.3);
                            t.motionY = 0.45;
                            t.velocityChanged = true;
                            SkillShapes.damage(c, t, per, hit().noFx());
                        }
                        Fx.sound(c.w, x, y, z, "minecraft:entity.enderdragon.flap", 0.7F, 1.4F);
                    });
                    schedule(42, () -> {
                        for (EntityLivingBase t : SkillShapes.around(c.w, x, y, z, R)) {
                            t.motionY = -1.5;
                            t.velocityChanged = true;
                        }
                        Fx.at(c.w, Fx.EXPLOSION, Fx.WIND, x, y, z, R * 0.6);
                        SkillShapes.area(c, x, y, z, R, per * 2.5F, hit().push(0.8));
                        artBoom(c, new Vec3d(x, y, z), 1.2F);
                        if (!c.legend.isEmpty()) {
                            awaken(c, new Vec3d(x, y, z), R, per * 2);
                        }
                    });
                });
                return;
            case "longbow": {
                final Vec3d at = aimGround(c, 30);
                center = at;
                fx(c, Fx.RUNES, Fx.ARCANE, at.x, at.y + 0.05, at.z, 0, 0, 0, R, 0, 34);
                repeat(6, 2, 12, i -> {
                    for (int k = 0; k < 10; k++) {
                        double ox = (RNG.nextDouble() * 2 - 1) * R, oz = (RNG.nextDouble() * 2 - 1) * R;
                        EntitySkillBolt b = arrow(c, at.x + ox, at.y + 14 + RNG.nextDouble() * 4, at.z + oz, (RNG.nextDouble() - 0.5) * 0.3, -2.4,
                                (RNG.nextDouble() - 0.5) * 0.3, per, c.flame, null, 0.18);
                        b.maxLife = 30;
                    }
                    if (i % 2 == 0) {
                        Fx.sound(c.w, at.x, at.y + 10, at.z, "minecraft:entity.firework.twinkle", 1.0F, 1.0F + i * 0.08F);
                    }
                });
                schedule(32, () -> {
                    Fx.at(c.w, Fx.EXPLOSION, Fx.ARCANE, at.x, at.y, at.z, 4.0);
                    SkillShapes.area(c, at.x, at.y, at.z, R, per * 3, hit().lift(0.5).from(at));
                    artBoom(c, at, 1.2F);
                });
                finish = 32;
                break;
            }
            case "crossbow":
                effect(p, MobEffects.SLOWNESS, 20, 3);
                schedule(20, () -> {
                    Vec3d from = chest(p);
                    Vec3d dir = p.getLookVec().normalize();
                    Vec3d end = from.add(dir.scale(R));
                    RayTraceResult rt = c.w.rayTraceBlocks(from, end, false, true, false);
                    double len = rt != null ? rt.hitVec.distanceTo(from) : R;
                    end = from.add(dir.scale(len));
                    fx(c, Fx.BEAM, c.el == Fx.STEEL ? Fx.LIGHTNING_E : c.el, from.x, from.y, from.z, end.x, end.y, end.z, 1.0, 0, 8);
                    fx(c, Fx.M_SPEAR, c.el, from.x, from.y, from.z, dir.x, dir.y, dir.z, 2.5, 6.0, 8);
                    SkillShapes.line(c, from, dir, len, 1.4, per, hit().push(1.5).lift(0.3), null);
                    Fx.at(c.w, Fx.EXPLOSION, c.el, end.x, end.y, end.z, 3.0);
                    SkillShapes.area(c, end.x, end.y, end.z, 3.0, per * 0.5F, hit().from(end).noFx());
                    SkillShapes.launch(p, -dir.x * 1.0, 0.2, -dir.z * 1.0);
                    artBoom(c, end, 1.3F);
                });
                center = new Vec3d(c.fx(10), py, c.fz(10));
                finish = 20;
                break;
            case "bow": {
                // Thousand Arrows: leap up, hover and pour homing arrows on every enemy around, then one giant arrow
                SkillShapes.launch(p, 0, 1.05, 0);
                onLanding(p, 40, 140, () -> at(c, Fx.RING, p.posX, p.posY, p.posZ, 2.0));
                effect(p, MobEffects.RESISTANCE, 50, 2);
                final int waves = 16, perWave = 5;
                repeat(8, 1, 32, i -> {
                    p.motionY = 0.01;
                    p.motionX *= 0.5;
                    p.motionZ *= 0.5;
                    p.fallDistance = 0;
                    p.velocityChanged = true;
                });
                fx(c, Fx.RUNES, Fx.ARCANE, px, py + 0.05, pz, 0, 0, 0, R * 0.5, 0, 40);
                repeat(9, 2, waves, i -> {
                    List<EntityLivingBase> pool = SkillShapes.around(c.w, p.posX, py, p.posZ, R, 8, 10);
                    for (int k = 0; k < perWave; k++) {
                        int n = i * perWave + k;
                        double a = n * 2.39996 + RNG.nextDouble() * 0.3;
                        double vx = Math.cos(a) * 1.3, vz = Math.sin(a) * 1.3;
                        EntityLivingBase t = pool.isEmpty() ? null : pool.get(n % pool.size());
                        EntitySkillBolt b = arrow(c, p.posX + Math.cos(a) * 0.8, p.posY + 1.2, p.posZ + Math.sin(a) * 0.8, vx,
                                t == null ? -0.6 : 0.4, vz, per, c.flame, t, t == null ? 0.05 : 0.3);
                        b.maxLife = 45;
                    }
                    fx(c, Fx.SPIN, c.el, p.posX, p.posY, p.posZ, 0, 0, 0, 1.6, 0, 3);
                    snd(c, "minecraft:entity.arrow.shoot", 1.0F, 0.9F + (i % 4) * 0.15F);
                    if (i % 3 == 0) {
                        snd(c, "minecraft:entity.firework.twinkle", 0.6F, 1.4F);
                    }
                });
                schedule(42, () -> {
                    EntityLivingBase big = null;
                    double bh = -1;
                    for (EntityLivingBase t : SkillShapes.around(c.w, p.posX, py, p.posZ, R, 8, 10)) {
                        if (t.getMaxHealth() > bh) {
                            bh = t.getMaxHealth();
                            big = t;
                        }
                    }
                    Vec3d at = big != null ? new Vec3d(big.posX, big.posY, big.posZ) : aimGround(c, 20);
                    fx(c, Fx.M_SPEAR, c.el, at.x, at.y + 12, at.z, 0, -1, 0, 6.0, 4.0, 8);
                    fx(c, Fx.BEAM, c.el, at.x, at.y + 14, at.z, at.x, at.y, at.z, 1.1, 0, 6);
                    schedule(3, () -> {
                        Fx.at(c.w, Fx.EXPLOSION, c.el, at.x, at.y, at.z, 4.5);
                        fx(c, Fx.M_CRACK, c.el, at.x, at.y + 0.05, at.z, 1, 0, 0, 6.0, 0, 30);
                        SkillShapes.area(c, at.x, at.y, at.z, 6.0 * c.size, per * 8, hit().lift(0.6).from(at));
                        artBoom(c, at, 1.3F);
                    });
                });
                center = aimGround(c, 20);
                finish = 46;
                break;
            }
            case "switch":
                repeat(0, 10, 3, i -> {
                    double ang = i == 0 ? Math.PI / 2 : (i == 1 ? -Math.PI / 2 : Math.PI);
                    double dx = c.lx * Math.cos(ang) - c.lz * Math.sin(ang), dz = c.lx * Math.sin(ang) + c.lz * Math.cos(ang);
                    double v = SkillShapes.dashSpeed(p, 3.5);
                    SkillShapes.launch(p, dx * v, 0.3, dz * v);
                    Vec3d look = p.getLookVec();
                    List<EntityLivingBase> tg = aimTargets(c, 26, 0.3);
                    for (int k = 0; k < 14; k++) {
                        double a = (k - 6.5) * 0.11;
                        double ca = Math.cos(a), sa = Math.sin(a);
                        arrow(c, p.posX + look.x, c.eyeY() - 0.1, p.posZ + look.z, (look.x * ca - look.z * sa) * 2.6, look.y * 2.6 + (RNG.nextDouble() - 0.5) * 0.2,
                                (look.x * sa + look.z * ca) * 2.6, per, false, pick(tg, k + i), tg.isEmpty() ? 0.1 : 0.25);
                    }
                    fx(c, Fx.SPIN, Fx.WIND, p.posX, p.posY, p.posZ, 0, 0, 0, 1.4, 0, 6);
                    snd(c, "minecraft:entity.arrow.shoot", 1.0F, 1.2F);
                });
                finish = 22;
                break;
            case "pistol": {
                List<EntityLivingBase> list = new ArrayList<>();
                for (EntityLivingBase t : SkillShapes.around(c.w, px, py, pz, 30)) {
                    double dx = t.posX - px, dz = t.posZ - pz, d = Math.sqrt(dx * dx + dz * dz);
                    if (d > 0.5 && (dx * c.lx + dz * c.lz) / d > 0.5 && p.canEntityBeSeen(t)) {
                        list.add(t);
                    }
                }
                list.sort((a, b) -> Double.compare(a.getDistanceSq(p), b.getDistanceSq(p)));
                final List<EntityLivingBase> marks = list.subList(0, Math.min((int) st[1], list.size()));
                effect(p, MobEffects.SLOWNESS, 18, 3);
                for (EntityLivingBase t : marks) {
                    Fx.send(c.w, Fx.RUNES, Fx.BLOOD, t.posX, t.posY + t.height + 0.3, t.posZ, 0, 0, 0, 0.6, 0, 16, -1);
                    Fx.send(c.w, Fx.PILLAR, Fx.BLOOD, t.posX, t.posY, t.posZ, 0, 0, 0, t.height + 0.6, 0.5, 16, -1);
                }
                snd(c, "minecraft:block.note.hat", 1.0F, 0.5F);
                for (int i = 0; i < marks.size(); i++) {
                    final EntityLivingBase t = marks.get(i);
                    schedule(16 + i, () -> {
                        if (t.isDead) {
                            return;
                        }
                        Vec3d mz = muzzle(c);
                        Vec3d to = new Vec3d(t.posX, t.posY + t.getEyeHeight() - 0.1, t.posZ);
                        fx(c, Fx.BEAM, c.el == Fx.STEEL ? Fx.FIRE : c.el, mz.x, mz.y, mz.z, to.x, to.y, to.z, 0.15, 0, 4);
                        Fx.at(c.w, Fx.FLASH, Fx.FIRE, mz.x, mz.y, mz.z, 0.6);
                        SkillShapes.damage(c, t, per, hit());
                        snd(c, "minecraft:entity.firework.blast", 1.0F, 1.4F);
                    });
                }
                finish = 18 + marks.size();
                center = marks.isEmpty() ? me : new Vec3d(marks.get(0).posX, marks.get(0).posY, marks.get(0).posZ);
                break;
            }
            case "shotgun": {
                Vec3d mz = muzzle(c);
                Fx.at(c.w, Fx.EXPLOSION, c.el == Fx.STEEL ? Fx.FIRE : c.el, mz.x + c.lx * 1.5, mz.y - 0.6, mz.z + c.lz * 1.5, 2.2);
                fx(c, Fx.CRESCENT, Fx.FIRE, mz.x, mz.y - 0.6, mz.z, c.lx, 0, c.lz, 5.0, 1.5, 5);
                SkillShapes.cone(c, R, 0.9, per, hit().push(2.0).lift(0.5));
                GunLogic.recoil(p, 3.0F, -1.2);
                snd(c, "minecraft:entity.generic.explode", 1.2F, 0.9F);
                artBoom(c, mz, 1.2F);
                center = new Vec3d(c.fx(3), py, c.fz(3));
                finish = 2;
                break;
            }
            case "rifle": {
                effect(p, MobEffects.SLOWNESS, 32, 4);
                final Vec3d aimPt = SkillShapes.aim(p, R);
                fx(c, Fx.RUNES, Fx.BLOOD, aimPt.x, aimPt.y + 0.1, aimPt.z, 0, 0, 0, 1.6, 0, 30);
                snd(c, "minecraft:block.note.hat", 1.0F, 0.4F);
                schedule(30, () -> {
                    Vec3d from = muzzle(c);
                    Vec3d dir = p.getLookVec().normalize();
                    Vec3d end = from.add(dir.scale(R));
                    RayTraceResult rt = c.w.rayTraceBlocks(from, end, false, true, false);
                    double len = rt != null ? rt.hitVec.distanceTo(from) : R;
                    end = from.add(dir.scale(len));
                    fx(c, Fx.BEAM, c.el, from.x, from.y, from.z, end.x, end.y, end.z, 0.6, 0, 8);
                    SkillShapes.line(c, from, dir, len, 1.0, per, hit().push(0.8), null);
                    Fx.at(c.w, Fx.EXPLOSION, c.el, end.x, end.y, end.z, 3.0);
                    SkillShapes.area(c, end.x, end.y, end.z, 3.0, per * 0.5F, hit().from(end).noFx());
                    GunLogic.recoil(p, 3.0F, -0.8);
                    snd(c, "minecraft:entity.firework.large_blast", 1.4F, 0.6F);
                    artBoom(c, end, 1.4F);
                });
                center = aimPt;
                finish = 30;
                break;
            }
            case "cannon": {
                final Vec3d at = aimGround(c, 40);
                center = at;
                fx(c, Fx.RUNES, Fx.FIRE, at.x, at.y + 0.05, at.z, 0, 0, 0, R + 1, 0, 36);
                repeat(4, 6, 5, i -> {
                    final double x = at.x + (RNG.nextDouble() * 2 - 1) * R, z = at.z + (RNG.nextDouble() * 2 - 1) * R;
                    Fx.send(c.w, Fx.M_ORB, Fx.FIRE, x - 3, at.y + 18, z - 2, x, at.y, z, 0.7, 0, 8, -1);
                    schedule(8, () -> {
                        Fx.at(c.w, Fx.EXPLOSION, c.el == Fx.STEEL ? Fx.FIRE : c.el, x, at.y, z, 3.0);
                        SkillShapes.area(c, x, at.y, z, 3.2, per, hit().lift(0.5).push(0.6).from(new Vec3d(x, at.y, z)));
                        Fx.shake(c.w, x, at.y, z, 20, 1.2F, 6);
                        Fx.sound(c.w, x, at.y, z, "minecraft:entity.generic.explode", 1.2F, 0.8F);
                    });
                });
                snd(c, "minecraft:entity.firework.launch", 1.0F, 0.5F);
                finish = 40;
                break;
            }
            default:
                break;
        }
        if (!c.legend.isEmpty()) {
            final Vec3d at = center;
            schedule(finish + 2, () -> awaken(c, at, 6, per * 2));
        }
    }

    private static void wandArt(final SkillCtx c, double[] st, final float per, final double R) {
        final EntityPlayerMP p = c.p;
        switch (c.in.wandElement()) {
            case "fire": {
                final Vec3d aim = aimGround(c, 32);
                fx(c, Fx.METEOR, c.el, aim.x, aim.y, aim.z, 0, 0, 0, R, 0, 20);
                snd(c, "minecraft:entity.blaze.shoot", 1.0F, 0.5F);
                schedule(20, () -> {
                    SkillShapes.area(c, aim.x, aim.y, aim.z, R, per, hit().fire(6).lift(0.5).push(0.6).from(aim));
                    Fx.shake(c.w, aim.x, aim.y, aim.z, 24, 2.0F, 12);
                    Fx.sound(c.w, aim.x, aim.y, aim.z, "minecraft:entity.generic.explode", 1.4F, 0.6F);
                    Fx.send(c.w, Fx.FIELD, c.el, aim.x, aim.y, aim.z, 0, 0, 0, R * 0.7, 0, 80, -1);
                });
                final float burn = c.dmg(2, true);
                repeat(30, 10, 8, i -> SkillShapes.area(c, aim.x, aim.y, aim.z, R * 0.7, burn, hit().fire(3).noFx().noStatus()));
                break;
            }
            case "ice": {
                final Vec3d aim = aimGround(c, 24);
                final double r = st[1] * c.size;
                fx(c, Fx.FIELD, c.el, aim.x, aim.y, aim.z, 0, 0, 0, r, 0, 60);
                fx(c, Fx.RUNES, c.el, aim.x, aim.y + 0.05, aim.z, 0, 0, 0, r, 0, 60);
                long until = c.w.getTotalWorldTime() + 64; // still frozen when the blast lands at +60
                for (EntityLivingBase t : SkillShapes.around(c.w, aim.x, aim.y, aim.z, r)) {
                    t.getEntityData().setLong("za_st_frozen", until);
                    t.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 60, 9, false, false));
                    t.addPotionEffect(new PotionEffect(MobEffects.WEAKNESS, 60, 1, false, false));
                    fx(c, Fx.PILLAR, c.el, t.posX, t.posY, t.posZ, 0, 0, 0, t.height + 0.5, t.width + 0.5, 10);
                }
                snd(c, "minecraft:block.glass.place", 1.0F, 0.5F);
                final float chillTick = c.dmg(2, true);
                repeat(15, 15, 3, i -> SkillShapes.area(c, aim.x, aim.y, aim.z, r, chillTick, hit().noFx().noStatus()));
                schedule(60, () -> {
                    Fx.at(c.w, Fx.NOVA, c.el, aim.x, aim.y + 1.0, aim.z, r * 0.5);
                    Fx.send(c.w, Fx.SPIKES, c.el, aim.x, aim.y, aim.z, 0, 0, 0, r * 0.6, 0, 1, -1);
                    // the frozen shatter: x1.5 on enemies still frozen
                    for (EntityLivingBase t : SkillShapes.around(c.w, aim.x, aim.y, aim.z, r)) {
                        boolean frozen = t.getEntityData().getLong("za_st_frozen") > c.w.getTotalWorldTime();
                        t.getEntityData().setLong("za_st_frozen", 0);
                        SkillShapes.damage(c, t, frozen ? per * 1.5F : per, hit().from(aim));
                    }
                    Fx.sound(c.w, aim.x, aim.y, aim.z, "minecraft:block.glass.break", 1.4F, 0.6F);
                    Fx.shake(c.w, aim.x, aim.y, aim.z, 16, 1.2F, 8);
                });
                break;
            }
            case "lightning":
            case "magic": {
                final boolean storm = "lightning".equals(c.in.wandElement());
                final int pulses = 10;
                final int every = storm ? 3 : 4;
                final double len = st[1] * c.size;
                effect(p, MobEffects.SLOWNESS, pulses * every + 4, 1);
                snd(c, storm ? "minecraft:entity.lightning.thunder" : "minecraft:entity.guardian.attack", 0.7F, 1.5F);
                repeat(0, every, pulses, i -> {
                    Vec3d from = new Vec3d(p.posX, p.posY + p.getEyeHeight() - 0.2, p.posZ);
                    Vec3d dir = p.getLookVec().normalize();
                    Vec3d end = from.add(dir.scale(len));
                    RayTraceResult rt = c.w.rayTraceBlocks(from, end, false, true, false);
                    double l = rt != null ? rt.hitVec.distanceTo(from) : len;
                    end = from.add(dir.scale(l));
                    fx(c, Fx.BEAM, c.el, from.x + dir.x * 0.6, from.y + dir.y * 0.6, from.z + dir.z * 0.6, end.x, end.y, end.z, storm ? 0.8 : 0.7, 0, every + 1);
                    SkillShapes.line(c, from, dir, l, storm ? 1.2 : 1.0, per, hit().noFx(), null);
                    if (i % 3 == 0) {
                        snd(c, storm ? "minecraft:entity.lightning.impact" : "minecraft:block.note.hat", 0.3F, 1.6F);
                    }
                });
                break;
            }
            case "necromancy": {
                Fx.on(p, Fx.AURA, c.el, 2.0, 0, 80);
                final double r = st[1] * c.size;
                snd(c, "minecraft:entity.wither.ambient", 0.7F, 1.0F);
                repeat(0, 10, 8, i -> {
                    int hits = 0;
                    for (EntityLivingBase t : SkillShapes.around(c.w, p.posX, p.posY, p.posZ, r)) {
                        if (SkillShapes.damage(c, t, per, hit().noFx())) {
                            hits++;
                            if (i % 2 == 0) {
                                fx(c, Fx.LIGHTNING, c.el, t.posX, t.posY + t.height * 0.6, t.posZ, p.posX, p.posY + 1.1, p.posZ, 0.5, 0, 3);
                            }
                        }
                    }
                    p.heal(hits * per * 0.4F);
                    fx(c, Fx.RING, c.el, p.posX, p.posY, p.posZ, 0, 0, 0, r, 0, 5);
                });
                break;
            }
            case "earth": {
                final double x = p.posX, y = p.posY, z = p.posZ;
                final Set<Integer> once = SkillShapes.set();
                repeat(0, 6, 3, i -> {
                    double r = R * (i + 1) / 3.0;
                    Fx.send(c.w, Fx.SPIKES, c.el, x, y, z, 0, 0, 0, r, 0, 1, -1);
                    Fx.send(c.w, Fx.RING, c.el, x, y, z, 0, 0, 0, r, 0, 4, -1);
                    SkillShapes.area(c, x, y, z, r, per, hit().lift(0.8).stagger());
                    Fx.shake(c.w, x, y, z, 18, 1.4F, 6);
                    Fx.sound(c.w, x, y, z, "minecraft:entity.generic.explode", 0.9F, 0.5F + i * 0.1F);
                });
                break;
            }
            case "sorcery": {
                final Vec3d aim = aimGround(c, 24);
                fx(c, Fx.FIELD, c.el, aim.x, aim.y, aim.z, 0, 0, 0, R, 0, 40);
                fx(c, Fx.SPIRAL, c.el, aim.x, aim.y, aim.z, 0, 0, 0, 1.5, 3.0, 40);
                final float tick = c.dmg(2, true);
                snd(c, "minecraft:block.portal.trigger", 0.5F, 1.4F);
                repeat(0, 4, 10, i -> {
                    for (EntityLivingBase t : SkillShapes.around(c.w, aim.x, aim.y, aim.z, R)) {
                        SkillShapes.pull(t, aim.x, aim.z, 0.35);
                        SkillShapes.damage(c, t, tick, hit().noFx().noStatus());
                    }
                });
                schedule(40, () -> {
                    Fx.at(c.w, Fx.EXPLOSION, c.el, aim.x, aim.y + 0.5, aim.z, R * 0.6);
                    SkillShapes.area(c, aim.x, aim.y, aim.z, R * 0.7, per, hit().push(1.0).lift(0.4).from(aim));
                    Fx.shake(c.w, aim.x, aim.y, aim.z, 18, 1.6F, 10);
                    Fx.sound(c.w, aim.x, aim.y, aim.z, "minecraft:entity.endermen.teleport", 1.2F, 0.5F);
                    Fx.sound(c.w, aim.x, aim.y, aim.z, "minecraft:entity.generic.explode", 1.0F, 0.8F);
                });
                break;
            }
            case "healing": {
                Fx.on(p, Fx.AURA, Fx.HOLY, 1.6, 0, 120);
                final double r = st[1] * c.size;
                snd(c, "minecraft:entity.player.levelup", 0.8F, 1.2F);
                repeat(0, 20, 6, i -> {
                    for (EntityPlayer o : allies(c, r)) {
                        o.heal(2.0F);
                        effect(o, MobEffects.REGENERATION, 40, 0);
                        Fx.on(o, Fx.HEAL, Fx.HOLY, 1.2, 0, 10);
                    }
                    for (EntityLivingBase t : SkillShapes.around(c.w, p.posX, p.posY, p.posZ, r)) {
                        fx(c, Fx.PILLAR, Fx.HOLY, t.posX, t.posY, t.posZ, 0, 0, 0, 4, 0.8, 8);
                        SkillShapes.damage(c, t, per, hit().noFx());
                    }
                    fx(c, Fx.RUNES, Fx.HOLY, p.posX, p.posY + 0.05, p.posZ, 0, 0, 0, r, 0, 20);
                    snd(c, "minecraft:block.note.chime", 0.8F, 1.2F + i * 0.1F);
                });
                break;
            }
            default:
                break;
        }
    }
}
