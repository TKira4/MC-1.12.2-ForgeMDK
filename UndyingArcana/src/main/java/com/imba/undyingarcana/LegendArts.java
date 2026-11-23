package com.imba.undyingarcana;

import static com.imba.undyingarcana.SkillMoves.RNG;
import static com.imba.undyingarcana.SkillMoves.aimGround;
import static com.imba.undyingarcana.SkillMoves.aimTargets;
import static com.imba.undyingarcana.SkillMoves.artBoom;
import static com.imba.undyingarcana.SkillMoves.arrow;
import static com.imba.undyingarcana.SkillMoves.chest;
import static com.imba.undyingarcana.SkillMoves.effect;
import static com.imba.undyingarcana.SkillMoves.fx;
import static com.imba.undyingarcana.SkillMoves.muzzle;
import static com.imba.undyingarcana.SkillMoves.onLanding;
import static com.imba.undyingarcana.SkillMoves.pick;
import static com.imba.undyingarcana.SkillMoves.snd;
import static com.imba.undyingarcana.SkillServer.repeat;
import static com.imba.undyingarcana.SkillServer.schedule;
import static com.imba.undyingarcana.SkillShapes.hit;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.MobEffects;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;

/**
 * Signature Arts of the 24 legendary weapons (Alt+R). Each replaces the weapon type's Art.
 * Damage: c.dmg(base, perHit) - legend tier, legend bonus, ranks, build and preset already apply.
 */
final class LegendArts {
    private LegendArts() {
    }

    // ------------------------------------------------------------------ catalogue
    static String name(String k) {
        switch (k) {
            case "stormcaller": return "Raijin's Thousand Cuts";
            case "earthbreaker": return "Tectonic Rupture";
            case "bloodthirst": return "Crimson Feast";
            case "frostfang": return "Absolute Zero";
            case "aegis_edge": return "Sanctuary";
            case "dawnbreaker": return "Solar Flare";
            case "gravewarden": return "Reaper's Toll";
            case "shadowfang": return "Night Assassin";
            case "windpiercer": return "Hurricane Arrow";
            case "starfall": return "Constellation";
            case "thunderstring": return "Thunder God's Volley";
            case "hydra": return "Nine-Headed Hydra";
            case "venomstring": return "Plague Cloud";
            case "peacemaker": return "Judgement Day";
            case "dragons_roar": return "Dragon's Breath";
            case "widowmaker": return "Final Verdict";
            case "thunderclap": return "Storm Artillery";
            case "phoenix": return "Rebirth Flame";
            case "glacier": return "Eternal Winter";
            case "stormlord": return "Tempest Crown";
            case "lich": return "Wraith Legion";
            case "gaia": return "Nature's Wrath";
            case "archon": return "Singularity";
            case "seraph": return "Seraph's Wings";
            default: return "";
        }
    }

    static String desc(String k) {
        switch (k) {
            case "stormcaller": return "Flash between up to 8 enemies with lightning cuts, then the sky strikes every one of them.";
            case "earthbreaker": return "Leap and slam: a ring of stone spires launches enemies, then the ground caves in and drags them down.";
            case "bloodthirst": return "Hurl a spinning blood axe out and back; every hit drains life to you.";
            case "frostfang": return "Freeze everything within 10 blocks, shatter them with 12 thrusts, then a final frost nova.";
            case "aegis_edge": return "A holy dome for 6s: allies inside get Resistance and Regeneration, enemies are smitten and pushed out.";
            case "dawnbreaker": return "Raise a small sun overhead, then hurl it forward as a wave of solar fire.";
            case "gravewarden": return "Four spectral halberds orbit you for 4s; enemies they kill burst into souls.";
            case "shadowfang": return "Blind every enemy and vanish; six blinks behind the weakest enemies for x3 backstabs.";
            case "windpiercer": return "A giant arrow pierces everything in a line and leaves a fire tornado that drags enemies in.";
            case "starfall": return "Mark up to 7 enemies as a constellation, then a falling star hits each of them.";
            case "thunderstring": return "40 lightning arrows; every hit chains to two more enemies.";
            case "hydra": return "Nine snaking streams of hydra arrows; every hit splits in two.";
            case "venomstring": return "An arrow bursts into a plague cloud for 8s; enemies that die inside spread it.";
            case "peacemaker": return "Mark every enemy in sight, then a holy shot for each; kills heal you.";
            case "dragons_roar": return "Breathe dragon fire in a cone for 3s.";
            case "widowmaker": return "Aim for 1.5s, then a rift round pierces everything; a rift implodes at each enemy hit.";
            case "thunderclap": return "Fire a storm shell into the sky; the storm drops 10 lightning shells on enemies.";
            case "phoenix": return "Dash as a phoenix through enemies, burst into flame, and cheat death once in the next 30s.";
            case "glacier": return "A 6s blizzard chills and spikes enemies, then everything inside freezes solid.";
            case "stormlord": return "A crown of storms for 6s: lightning strikes a nearby enemy every half second.";
            case "lich": return "Six wraiths hunt enemies, pass through them and drain life back to you.";
            case "gaia": return "Vines root every enemy within 12 blocks with thorns, then stone spires burst under them.";
            case "archon": return "Open a black hole that drags in everything within 15 blocks, then it collapses.";
            case "seraph": return "Spread wings of light: allies are healed to full over 3s while holy light smites enemies (x2 on undead).";
            default: return "";
        }
    }

    /** Returns true when the legend has its own Art (all 24 do). */
    static boolean cast(SkillCtx c) {
        switch (c.legend) {
            case "stormcaller": stormcaller(c); return true;
            case "earthbreaker": earthbreaker(c); return true;
            case "bloodthirst": bloodthirst(c); return true;
            case "frostfang": frostfang(c); return true;
            case "aegis_edge": aegis(c); return true;
            case "dawnbreaker": dawnbreaker(c); return true;
            case "gravewarden": gravewarden(c); return true;
            case "shadowfang": shadowfang(c); return true;
            case "windpiercer": windpiercer(c); return true;
            case "starfall": starfall(c); return true;
            case "thunderstring": thunderstring(c); return true;
            case "hydra": hydra(c); return true;
            case "venomstring": venomstring(c); return true;
            case "peacemaker": peacemaker(c); return true;
            case "dragons_roar": dragonsRoar(c); return true;
            case "widowmaker": widowmaker(c); return true;
            case "thunderclap": thunderclap(c); return true;
            case "phoenix": phoenix(c); return true;
            case "glacier": glacier(c); return true;
            case "stormlord": stormlord(c); return true;
            case "lich": lich(c); return true;
            case "gaia": gaia(c); return true;
            case "archon": archon(c); return true;
            case "seraph": seraph(c); return true;
            default: return false;
        }
    }

    // ------------------------------------------------------------------ helpers
    private static boolean gone(EntityPlayerMP p) {
        return p.isDead || p.getHealth() <= 0;
    }

    private static Vec3d mid(EntityLivingBase t) {
        return new Vec3d(t.posX, t.posY + t.height * 0.6, t.posZ);
    }

    private static List<EntityLivingBase> near(SkillCtx c, double x, double y, double z, double r) {
        return SkillShapes.around(c.w, x, y, z, r, 6, 8);
    }

    private static List<EntityLivingBase> nearest(SkillCtx c, double r, int max) {
        List<EntityLivingBase> l = near(c, c.p.posX, c.p.posY, c.p.posZ, r);
        l.sort((a, b) -> Double.compare(a.getDistanceSq(c.p), b.getDistanceSq(c.p)));
        return l.size() > max ? new ArrayList<>(l.subList(0, max)) : l;
    }

    private static void sound(SkillCtx c, Vec3d at, String id, float v, float pitch) {
        Fx.sound(c.w, at.x, at.y, at.z, id, v, pitch);
    }

    /** Teleport the player next to t, on the side away from the player's current spot, facing t. */
    private static void blinkBehind(EntityPlayerMP p, EntityLivingBase t) {
        double dx = t.posX - p.posX, dz = t.posZ - p.posZ;
        double d = Math.max(0.01, Math.sqrt(dx * dx + dz * dz));
        double bx = t.posX + dx / d * (t.width * 0.5 + 0.9), bz = t.posZ + dz / d * (t.width * 0.5 + 0.9);
        float yaw = (float) (Math.atan2(-dx, dz) * 180.0 / Math.PI) + 180.0F;
        p.connection.setPlayerLocation(bx, t.posY, bz, yaw, p.rotationPitch);
        p.fallDistance = 0;
    }

    private static void killHeal(SkillCtx c, EntityLivingBase t, float hp) {
        if (t.getHealth() <= 0 || t.isDead) {
            c.p.heal(hp);
            fx(c, Fx.HEAL, Fx.HEALING, c.p.posX, c.p.posY, c.p.posZ, 0, 0, 0, 1.0, 0, 8);
        }
    }

    // ================================================================== WARRIOR
    private static void stormcaller(final SkillCtx c) {
        final EntityPlayerMP p = c.p;
        final float per = c.dmg(9, true);
        final List<EntityLivingBase> list = nearest(c, 14, 8);
        effect(p, MobEffects.RESISTANCE, 40, 4);
        if (list.isEmpty()) {
            Vec3d a = aimGround(c, 10);
            fx(c, Fx.DASH, c.el, p.posX, p.posY, p.posZ, a.x, a.y, a.z, 0, 0, 4);
            p.connection.setPlayerLocation(a.x, a.y, a.z, p.rotationYaw, p.rotationPitch);
            fx(c, Fx.M_BOLT, c.el, a.x, a.y + 16, a.z, a.x, a.y, a.z, 0.3, 0, 8);
            SkillShapes.area(c, a.x, a.y, a.z, 4, per * 2, hit().from(a));
            artBoom(c, a, 1.0F);
            return;
        }
        final int n = list.size();
        for (int i = 0; i < n; i++) {
            final EntityLivingBase t = list.get(i);
            final int k = i;
            schedule(2 + i * 3, () -> {
                if (gone(p) || t.isDead) {
                    return;
                }
                Vec3d from = new Vec3d(p.posX, p.posY + 1, p.posZ);
                blinkBehind(p, t);
                fx(c, Fx.LIGHTNING, c.el, from.x, from.y, from.z, p.posX, p.posY + 1, p.posZ, 0.8, 0, 4);
                Fx.send(c.w, Fx.XSLASH, c.el, t.posX, t.posY + t.height * 0.55, t.posZ, p.posX - t.posX, 0, p.posZ - t.posZ, 1.6, 0, 6, -1);
                SkillShapes.damage(c, t, per, hit().stagger());
                snd(c, "minecraft:entity.player.attack.sweep", 1.0F, 1.4F + k * 0.05F);
                snd(c, "minecraft:entity.lightning.impact", 0.4F, 1.8F);
            });
        }
        final int end = 2 + n * 3 + 6;
        schedule(end, () -> {
            Vec3d at = new Vec3d(p.posX, p.posY, p.posZ);
            for (EntityLivingBase t : list) {
                if (t.isDead) {
                    continue;
                }
                fx(c, Fx.M_BOLT, c.el, t.posX, t.posY + 16, t.posZ, t.posX, t.posY, t.posZ, 0.35, 0, 8);
                Fx.at(c.w, Fx.IMPACT, c.el, t.posX, t.posY + 0.5, t.posZ, 1.6);
                SkillShapes.damage(c, t, per * 2.0F, hit().stagger().lift(0.3));
            }
            sound(c, at, "minecraft:entity.lightning.thunder", 1.2F, 1.0F);
            artBoom(c, at, 1.1F);
        });
    }

    private static void earthbreaker(final SkillCtx c) {
        final EntityPlayerMP p = c.p;
        final float per = c.dmg(14, false);
        final double R = 8 * c.size;
        SkillShapes.launch(p, 0, 0.95, 0);
        snd(c, "minecraft:entity.enderdragon.flap", 1.0F, 0.6F);
        onLanding(p, 6, 60, () -> {
            final Vec3d at = new Vec3d(p.posX, p.posY, p.posZ);
            for (int k = 0; k < 8; k++) {
                double a = Math.PI * 2 * k / 8;
                Fx.send(c.w, Fx.M_CRACK, c.el, at.x, at.y + 0.05, at.z, Math.cos(a), 0, Math.sin(a), R, 0, 40, -1);
            }
            for (int k = 0; k < 12; k++) {
                double a = Math.PI * 2 * k / 12, rr = 2.5 + (k % 3) * (R - 2.5) / 2;
                Fx.send(c.w, Fx.PILLAR, c.el, at.x + Math.cos(a) * rr, at.y, at.z + Math.sin(a) * rr, 0, 0, 0, 3.5, 1.4, 14, -1);
            }
            Fx.send(c.w, Fx.SPIKES, c.el, at.x, at.y, at.z, 0, 0, 0, R * 0.7, 0, 1, -1);
            SkillShapes.area(c, at.x, at.y, at.z, R, per, hit().lift(1.0).from(at));
            artBoom(c, at, 1.3F);
            snd(c, "minecraft:entity.generic.explode", 1.2F, 0.5F);
            schedule(24, () -> {
                Fx.send(c.w, Fx.M_VORTEX, c.el, at.x, at.y + 0.2, at.z, 0, 0, 0, R * 0.55, 3, 22, -1);
                repeat(0, 2, 8, i -> {
                    for (EntityLivingBase t : near(c, at.x, at.y, at.z, R + 4)) {
                        SkillShapes.pull(t, at.x, at.z, 0.6);
                    }
                });
                schedule(18, () -> {
                    Fx.at(c.w, Fx.EXPLOSION, c.el, at.x, at.y, at.z, R * 0.6);
                    Fx.send(c.w, Fx.DEBRIS, c.el, at.x, at.y, at.z, 0, 0, 0, R * 0.6, 0, 20, -1);
                    SkillShapes.area(c, at.x, at.y, at.z, R * 0.7, per * 1.5F, hit().from(at).stagger());
                    for (EntityLivingBase t : near(c, at.x, at.y, at.z, R * 0.7)) {
                        effect(t, MobEffects.SLOWNESS, 80, 3);
                    }
                    artBoom(c, at, 1.0F);
                });
            });
        });
    }

    private static void bloodthirst(final SkillCtx c) {
        final EntityPlayerMP p = c.p;
        final float per = c.dmg(7, true);
        final Vec3d start = chest(p);
        final Vec3d dir = c.look.normalize();
        final double out = 14 * c.size;
        final int half = 12;
        snd(c, "minecraft:entity.player.attack.sweep", 1.0F, 0.6F);
        repeat(0, 1, half * 2, i -> {
            if (gone(p)) {
                return;
            }
            Vec3d pt;
            if (i < half) {
                pt = start.add(dir.scale(out * Math.sin((i + 1) / (double) half * Math.PI / 2)));
            } else {
                double t = (i - half + 1) / (double) half;
                Vec3d far = start.add(dir.scale(out));
                Vec3d me = chest(p);
                pt = far.add(me.subtract(far).scale(t * t));
            }
            Fx.send(c.w, Fx.SPIN, c.el, pt.x, pt.y - 1.0, pt.z, 0, 0, 0, 1.8, 0, 3, -1);
            if (i % 2 == 0) {
                Fx.send(c.w, Fx.CRESCENT, c.el, pt.x, pt.y, pt.z, dir.x, 0, dir.z, 1.6, 0.8, 3, -1);
                snd(c, "minecraft:entity.player.attack.sweep", 0.5F, 0.8F + i * 0.03F);
            }
            if (i % 3 == 0) {
                int hits = SkillShapes.area(c, pt.x, pt.y - 1, pt.z, 2.8 * c.size, per, hit().noFx());
                if (hits > 0) {
                    p.heal(hits * per * 0.2F);
                    Fx.send(c.w, Fx.LIGHTNING, Fx.BLOOD, pt.x, pt.y, pt.z, p.posX, p.posY + 1.1, p.posZ, 0.5, 0, 4, -1);
                }
            }
        });
        schedule(half * 2 + 1, () -> {
            fx(c, Fx.HEAL, Fx.BLOOD, p.posX, p.posY, p.posZ, 0, 0, 0, 1.5, 0, 10);
            Fx.at(c.w, Fx.NOVA, c.el, p.posX, p.posY + 1, p.posZ, 2.0);
            artBoom(c, new Vec3d(p.posX, p.posY, p.posZ), 0.7F);
        });
    }

    private static void frostfang(final SkillCtx c) {
        final EntityPlayerMP p = c.p;
        final float per = c.dmg(5, true);
        final double R = 10 * c.size;
        final Vec3d at = new Vec3d(p.posX, p.posY, p.posZ);
        final List<EntityLivingBase> list = near(c, at.x, at.y, at.z, R);
        long until = c.w.getTotalWorldTime() + 60;
        Fx.send(c.w, Fx.M_DOME, c.el, at.x, at.y, at.z, 0, 0, 0, R, 0, 16, -1);
        Fx.send(c.w, Fx.SPIKES, c.el, at.x, at.y, at.z, 0, 0, 0, R * 0.7, 0, 1, -1);
        for (EntityLivingBase t : list) {
            t.getEntityData().setLong("za_st_frozen", until);
            t.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 60, 9, false, false));
            fx(c, Fx.PILLAR, c.el, t.posX, t.posY, t.posZ, 0, 0, 0, t.height + 0.6, t.width + 0.6, 12);
        }
        snd(c, "minecraft:block.glass.place", 1.2F, 0.5F);
        repeat(8, 2, 12, i -> {
            if (gone(p) || list.isEmpty()) {
                return;
            }
            EntityLivingBase t = list.get(i % list.size());
            if (t.isDead) {
                return;
            }
            Vec3d from = chest(p), to = mid(t);
            fx(c, Fx.BEAM, c.el, from.x, from.y, from.z, to.x, to.y, to.z, 0.35, 0, 2);
            Fx.at(c.w, Fx.IMPACT, c.el, to.x, to.y, to.z, 1.0);
            SkillShapes.damage(c, t, per, hit().noFx());
            snd(c, "minecraft:entity.player.attack.crit", 0.8F, 1.5F + i * 0.04F);
        });
        schedule(34, () -> {
            Fx.at(c.w, Fx.NOVA, c.el, at.x, at.y + 1, at.z, R * 0.5);
            Fx.send(c.w, Fx.SPIKES, c.el, at.x, at.y, at.z, 0, 0, 0, R * 0.8, 0, 1, -1);
            SkillShapes.area(c, at.x, at.y, at.z, R, per * 3, hit().from(at).push(0.6));
            sound(c, at, "minecraft:block.glass.break", 1.5F, 0.5F);
            artBoom(c, at, 1.1F);
        });
    }

    private static void aegis(final SkillCtx c) {
        final EntityPlayerMP p = c.p;
        final float per = c.dmg(10, false);
        final double R = 7 * c.size;
        final Vec3d at = new Vec3d(p.posX, p.posY, p.posZ);
        Fx.send(c.w, Fx.M_DOME, c.el, at.x, at.y, at.z, 0, 0, 0, R, 1, 120, -1);
        Fx.send(c.w, Fx.RUNES, c.el, at.x, at.y + 0.05, at.z, 0, 0, 0, R, 0, 120, -1);
        sound(c, at, "minecraft:block.beacon.activate", 1.2F, 1.2F);
        repeat(0, 10, 12, i -> {
            for (EntityPlayer a : c.w.getEntitiesWithinAABB(EntityPlayer.class, new net.minecraft.util.math.AxisAlignedBB(at.x - R, at.y - 3, at.z - R, at.x + R, at.y + 6, at.z + R))) {
                if (a.getDistance(at.x, a.posY, at.z) <= R) {
                    a.addPotionEffect(new PotionEffect(MobEffects.RESISTANCE, 25, 1, false, false));
                    a.addPotionEffect(new PotionEffect(MobEffects.REGENERATION, 30, 1, false, false));
                }
            }
            for (EntityLivingBase t : near(c, at.x, at.y, at.z, R)) {
                fx(c, Fx.PILLAR, c.el, t.posX, t.posY, t.posZ, 0, 0, 0, 3.0, 0.6, 6);
                SkillShapes.damage(c, t, per * 0.4F, hit().push(0.8).from(at).noFx());
            }
        });
        schedule(120, () -> {
            Fx.send(c.w, Fx.M_SWORD, c.el, at.x, at.y, at.z, c.lx, 0, c.lz, 5.0, 4, 20, -1);
            schedule(6, () -> {
                Fx.at(c.w, Fx.EXPLOSION, c.el, at.x, at.y, at.z, R * 0.6);
                SkillShapes.area(c, at.x, at.y, at.z, R, per * 2.5F, hit().from(at).lift(0.5));
                artBoom(c, at, 1.2F);
            });
        });
    }

    private static void dawnbreaker(final SkillCtx c) {
        final EntityPlayerMP p = c.p;
        final float per = c.dmg(12, false);
        effect(p, MobEffects.SLOWNESS, 28, 3);
        effect(p, MobEffects.RESISTANCE, 28, 2);
        final Vec3d sun = new Vec3d(p.posX, p.posY + 5, p.posZ);
        Fx.send(c.w, Fx.M_ORB, c.el, sun.x, sun.y, sun.z, sun.x, sun.y, sun.z, 2.2, 0, 28, -1);
        Fx.send(c.w, Fx.GATHER, c.el, sun.x, sun.y, sun.z, 0, 0, 0, 3.0, 3, 24, -1);
        Fx.send(c.w, Fx.PILLAR, c.el, p.posX, p.posY, p.posZ, 0, 0, 0, 5.0, 1.2, 26, -1);
        sound(c, sun, "minecraft:entity.blaze.ambient", 1.2F, 0.5F);
        schedule(26, () -> {
            if (gone(p)) {
                return;
            }
            Vec3d dir = p.getLookVec();
            Vec3d h = new Vec3d(dir.x, 0, dir.z).normalize();
            final double len = 18 * c.size;
            final Vec3d s0 = new Vec3d(p.posX, p.posY + 5, p.posZ);
            final Vec3d end = aimGround(c, len);
            Fx.send(c.w, Fx.M_ORB, c.el, s0.x, s0.y, s0.z, end.x, end.y + 1, end.z, 2.0, 0, 8, -1);
            final Set<Integer> once = SkillShapes.set();
            final int steps = (int) (len / 2);
            repeat(1, 1, steps, i -> {
                double x = p.posX + h.x * 2 * (i + 1), z = p.posZ + h.z * 2 * (i + 1);
                double y = SkillShapes.ground(c.w, x, p.posY + 2, z);
                Fx.at(c.w, Fx.EXPLOSION, c.el, x, y, z, 1.6);
                SkillShapes.areaOnce(c, x, y, z, 3.0 * c.size, per, hit().fire(5).noFx().push(0.5), once);
                if (i % 2 == 0) {
                    Fx.sound(c.w, x, y, z, "minecraft:entity.blaze.shoot", 0.8F, 0.7F);
                }
            });
            schedule(8, () -> {
                Fx.at(c.w, Fx.EXPLOSION, c.el, end.x, end.y, end.z, 4.0);
                Fx.send(c.w, Fx.FIELD, c.el, end.x, end.y, end.z, 0, 0, 0, 4.5, 0, 60, -1);
                SkillShapes.area(c, end.x, end.y, end.z, 5 * c.size, per * 2, hit().fire(6).lift(0.6).from(end));
                artBoom(c, end, 1.3F);
            });
        });
    }

    private static void gravewarden(final SkillCtx c) {
        final EntityPlayerMP p = c.p;
        final float per = c.dmg(6, true);
        Fx.on(p, Fx.AURA, c.el, 1.4, 0, 80);
        snd(c, "minecraft:entity.wither.ambient", 0.8F, 1.4F);
        repeat(0, 2, 40, i -> {
            if (gone(p)) {
                return;
            }
            for (int k = 0; k < 4; k++) {
                double a = i * 0.35 + Math.PI / 2 * k;
                double x = p.posX + Math.cos(a) * 3.2 * c.size, z = p.posZ + Math.sin(a) * 3.2 * c.size;
                Fx.send(c.w, Fx.CRESCENT, c.el, x, p.posY + 1.0, z, -Math.sin(a), 0, Math.cos(a), 1.4, 0.8, 3, -1);
                if (i % 2 == 0) {
                    for (EntityLivingBase t : near(c, x, p.posY, z, 1.9)) {
                        if (SkillShapes.damage(c, t, per, hit().noFx()) && (t.isDead || t.getHealth() <= 0)) {
                            Fx.at(c.w, Fx.NOVA, Fx.NECRO, t.posX, t.posY + 1, t.posZ, 1.6);
                            SkillShapes.area(c, t.posX, t.posY, t.posZ, 3.5, per * 1.5F, hit().noFx());
                            p.heal(2.0F);
                            Fx.sound(t, "minecraft:entity.vex.death", 0.8F, 0.8F);
                        }
                    }
                }
            }
            if (i % 5 == 0) {
                snd(c, "minecraft:entity.player.attack.sweep", 0.6F, 0.6F);
            }
        });
        schedule(81, () -> artBoom(c, new Vec3d(p.posX, p.posY, p.posZ), 0.6F));
    }

    private static void shadowfang(final SkillCtx c) {
        final EntityPlayerMP p = c.p;
        final float per = c.dmg(8, true);
        for (EntityLivingBase t : near(c, p.posX, p.posY, p.posZ, 16)) {
            t.addPotionEffect(new PotionEffect(MobEffects.BLINDNESS, 100, 0, false, false));
            t.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 60, 1, false, false));
        }
        effect(p, MobEffects.INVISIBILITY, 100, 0);
        effect(p, MobEffects.SPEED, 100, 1);
        Fx.send(c.w, Fx.M_VORTEX, c.el, p.posX, p.posY, p.posZ, 0, 0, 0, 4.0, 4, 20, -1);
        snd(c, "minecraft:entity.endermen.stare", 0.6F, 1.6F);
        final Set<Integer> done = SkillShapes.set();
        repeat(6, 5, 6, i -> {
            if (gone(p)) {
                return;
            }
            EntityLivingBase best = null;
            double bk = Double.MAX_VALUE;
            for (EntityLivingBase t : near(c, p.posX, p.posY, p.posZ, 14)) {
                double key = (done.contains(t.getEntityId()) ? 10000 : 0) + t.getHealth();
                if (key < bk) {
                    bk = key;
                    best = t;
                }
            }
            if (best == null) {
                return;
            }
            done.add(best.getEntityId());
            Vec3d from = new Vec3d(p.posX, p.posY + 1, p.posZ);
            blinkBehind(p, best);
            fx(c, Fx.DASH, c.el, from.x, from.y - 1, from.z, p.posX, p.posY, p.posZ, 0, 0, 4);
            Fx.send(c.w, Fx.XSLASH, c.el, best.posX, best.posY + best.height * 0.55, best.posZ, best.posX - p.posX, 0, best.posZ - p.posZ, 1.4, 0, 6, -1);
            SkillShapes.damage(c, best, per * 3, hit().stagger());
            snd(c, "minecraft:entity.player.attack.crit", 1.0F, 0.7F);
            snd(c, "minecraft:entity.endermen.teleport", 0.5F, 1.6F);
        });
        schedule(38, () -> artBoom(c, new Vec3d(p.posX, p.posY, p.posZ), 0.7F));
    }

    // ================================================================== RANGER
    private static void windpiercer(final SkillCtx c) {
        final EntityPlayerMP p = c.p;
        final float per = c.dmg(14, false);
        Vec3d dir = c.look.normalize();
        Vec3d from = new Vec3d(p.posX + dir.x, c.eyeY() - 0.1, p.posZ + dir.z);
        EntitySkillBolt b = new EntitySkillBolt(c, "arrow", 3, 1.2F, from.x, from.y, from.z, dir.scale(2.2));
        b.damage = per;
        b.pierce = 99;
        b.maxLife = 14;
        b.onEnd = (bb, at) -> tornado(c, at, per);
        c.w.spawnEntity(b);
        fx(c, Fx.M_SPEAR, c.el, from.x, from.y, from.z, dir.x, dir.y, dir.z, 4.0, 2.2, 14);
        fx(c, Fx.DASH, Fx.WIND, p.posX, p.posY, p.posZ, p.posX + dir.x * 6, p.posY, p.posZ + dir.z * 6, 0, 0, 4);
        SkillShapes.launch(p, -dir.x * 0.6, 0.2, -dir.z * 0.6);
        snd(c, "minecraft:entity.arrow.shoot", 1.2F, 0.4F);
        snd(c, "minecraft:entity.enderdragon.flap", 0.8F, 1.4F);
    }

    private static void tornado(final SkillCtx c, Vec3d hitAt, final float per) {
        final double gy = SkillShapes.ground(c.w, hitAt.x, hitAt.y + 1, hitAt.z);
        final Vec3d at = new Vec3d(hitAt.x, gy, hitAt.z);
        Fx.send(c.w, Fx.M_VORTEX, c.el, at.x, at.y, at.z, 0, 0, 0, 2.6, 6, 80, -1);
        Fx.send(c.w, Fx.SPIRAL, Fx.WIND, at.x, at.y, at.z, 0, 0, 0, 3.0, 0, 80, -1);
        Fx.at(c.w, Fx.EXPLOSION, c.el, at.x, at.y, at.z, 2.5);
        sound(c, at, "minecraft:entity.generic.explode", 1.0F, 0.8F);
        repeat(4, 4, 19, i -> {
            for (EntityLivingBase t : near(c, at.x, at.y, at.z, 8)) {
                SkillShapes.pull(t, at.x, at.z, 0.45);
                t.motionY = Math.max(t.motionY, 0.25);
                SkillShapes.damage(c, t, per * 0.25F, hit().fire(3).noFx());
            }
            if (i % 3 == 0) {
                sound(c, at, "minecraft:entity.enderdragon.flap", 0.6F, 0.6F);
            }
        });
    }

    private static void starfall(final SkillCtx c) {
        final EntityPlayerMP p = c.p;
        final float per = c.dmg(16, false);
        final List<EntityLivingBase> marks = aimTargets(c, 28, 0.1);
        while (marks.size() > 7) {
            marks.remove(marks.size() - 1);
        }
        for (int i = 0; i < marks.size(); i++) {
            EntityLivingBase t = marks.get(i);
            Fx.send(c.w, Fx.RUNES, c.el, t.posX, t.posY + 0.05, t.posZ, 0, 0, 0, 1.4, 0, 30, -1);
            t.addPotionEffect(new PotionEffect(MobEffects.GLOWING, 40, 0, false, false));
            if (i > 0) {
                Vec3d a = mid(marks.get(i - 1)), b = mid(t);
                Fx.send(c.w, Fx.BEAM, c.el, a.x, a.y + 1, a.z, b.x, b.y + 1, b.z, 0.12, 0, 28, -1);
            }
        }
        snd(c, "minecraft:entity.firework.twinkle", 1.0F, 0.8F);
        final List<Vec3d> extra = new ArrayList<>();
        for (int i = marks.size(); i < 3; i++) {
            Vec3d a = aimGround(c, 24);
            extra.add(a.addVector((RNG.nextDouble() - 0.5) * 6, 0, (RNG.nextDouble() - 0.5) * 6));
        }
        repeat(28, 3, marks.size() + extra.size(), i -> {
            Vec3d at;
            if (i < marks.size()) {
                EntityLivingBase t = marks.get(i);
                at = new Vec3d(t.posX, t.posY, t.posZ);
            } else {
                at = extra.get(i - marks.size());
            }
            final Vec3d to = at;
            fx(c, Fx.M_SPEAR, c.el, to.x + 3, to.y + 14, to.z, -0.2, -1, 0, 3.0, 5.0, 6);
            schedule(3, () -> {
                Fx.at(c.w, Fx.EXPLOSION, c.el, to.x, to.y, to.z, 2.4);
                Fx.send(c.w, Fx.TIERUP, c.el, to.x, to.y, to.z, 0, 0, 0, 1, 2, 8, -1);
                SkillShapes.area(c, to.x, to.y, to.z, 3.2 * c.size, per, hit().from(to).lift(0.4));
                Fx.sound(c.w, to.x, to.y, to.z, "minecraft:entity.firework.large_blast", 1.0F, 0.8F);
            });
        });
        schedule(32 + 3 * (marks.size() + extra.size()), () -> artBoom(c, new Vec3d(p.posX, p.posY, p.posZ), 0.8F));
    }

    private static void thunderstring(final SkillCtx c) {
        final EntityPlayerMP p = c.p;
        final float per = c.dmg(4, true);
        repeat(0, 2, 8, w -> {
            if (gone(p)) {
                return;
            }
            List<EntityLivingBase> tg = aimTargets(c, 30, 0.2);
            Vec3d look = p.getLookVec();
            for (int k = 0; k < 5; k++) {
                double ang = (RNG.nextDouble() - 0.5) * 0.9, up = RNG.nextDouble() * 0.25;
                double ca = Math.cos(ang), sa = Math.sin(ang);
                EntitySkillBolt b = arrow(c, p.posX + look.x, c.eyeY() - 0.1, p.posZ + look.z, (look.x * ca - look.z * sa) * 2.6, (look.y + up) * 2.6,
                        (look.x * sa + look.z * ca) * 2.6, per, false, pick(tg, w * 5 + k), tg.isEmpty() ? 0.1 : 0.3);
                b.onHit = (bb, t) -> {
                    c.direct = bb;
                    boolean ok = SkillShapes.damage(c, t, bb.damage, hit().noFx());
                    c.direct = null;
                    if (!ok) {
                        return;
                    }
                    int chains = 2;
                    for (EntityLivingBase o : SkillShapes.around(c.w, t.posX, t.posY, t.posZ, 6)) {
                        if (o == t || chains <= 0) {
                            continue;
                        }
                        chains--;
                        Fx.send(c.w, Fx.LIGHTNING, c.el, t.posX, t.posY + t.height * 0.6, t.posZ, o.posX, o.posY + o.height * 0.6, o.posZ, 0.8, 0, 4, -1);
                        SkillShapes.damage(c, o, bb.damage * 0.5F, hit().noFx());
                    }
                };
            }
            snd(c, "minecraft:entity.arrow.shoot", 1.0F, 1.0F + w * 0.08F);
            snd(c, "minecraft:entity.lightning.impact", 0.3F, 2.0F);
        });
        schedule(18, () -> artBoom(c, new Vec3d(p.posX, p.posY, p.posZ), 0.6F));
    }

    private static void hydra(final SkillCtx c) {
        final EntityPlayerMP p = c.p;
        final float per = c.dmg(3.5, true);
        final List<EntityLivingBase> tg = aimTargets(c, 28, 0.1);
        for (int h = 0; h < 9; h++) {
            final int head = h;
            final double side = (head - 4) * 0.32;
            repeat(head % 3, 2, 5, k -> {
                if (gone(p)) {
                    return;
                }
                Vec3d look = p.getLookVec();
                double ang = side + Math.sin(k * 1.4 + head) * 0.25;
                double ca = Math.cos(ang), sa = Math.sin(ang);
                double ox = -look.z * side * 1.2, oz = look.x * side * 1.2;
                EntitySkillBolt b = arrow(c, p.posX + ox, c.eyeY() + 0.3 + Math.abs(side) * 0.5, p.posZ + oz, (look.x * ca - look.z * sa) * 2.0,
                        look.y * 2.0 + 0.3, (look.x * sa + look.z * ca) * 2.0, per, false, pick(tg, head), tg.isEmpty() ? 0.08 : 0.28);
                b.move = "hydra";
                if (k == 0) {
                    snd(c, "minecraft:entity.arrow.shoot", 0.7F, 0.7F + head * 0.08F);
                }
            });
        }
        snd(c, "minecraft:entity.enderdragon.growl", 0.5F, 1.6F);
        schedule(16, () -> artBoom(c, new Vec3d(p.posX, p.posY, p.posZ), 0.6F));
    }

    private static void venomstring(final SkillCtx c) {
        final EntityPlayerMP p = c.p;
        final float per = c.dmg(8, false);
        Vec3d dir = c.look.normalize();
        EntitySkillBolt b = arrow(c, p.posX + dir.x, c.eyeY() - 0.1, p.posZ + dir.z, dir.x * 2.4, dir.y * 2.4, dir.z * 2.4, per, false, null, 0.15);
        b.maxLife = 16;
        final int[] spreads = {0};
        b.onEnd = (bb, at) -> plague(c, new Vec3d(at.x, SkillShapes.ground(c.w, at.x, at.y + 1, at.z), at.z), 5 * c.size, 160, per, spreads);
        snd(c, "minecraft:entity.arrow.shoot", 1.0F, 0.6F);
        snd(c, "minecraft:entity.witch.throw", 1.0F, 0.8F);
    }

    private static void plague(final SkillCtx c, final Vec3d at, final double r, final int ticks, final float per, final int[] spreads) {
        Fx.send(c.w, Fx.FIELD, c.el, at.x, at.y, at.z, 0, 0, 0, r, 0, ticks, -1);
        Fx.send(c.w, Fx.M_DOME, c.el, at.x, at.y, at.z, 0, 0, 0, r, 0, Math.min(40, ticks), -1);
        Fx.at(c.w, Fx.EXPLOSION, c.el, at.x, at.y, at.z, r * 0.4);
        sound(c, at, "minecraft:entity.splash_potion.break", 1.2F, 0.6F);
        repeat(5, 10, ticks / 10, i -> {
            for (EntityLivingBase t : near(c, at.x, at.y, at.z, r)) {
                t.addPotionEffect(new PotionEffect(MobEffects.POISON, 60, 1, false, false));
                t.addPotionEffect(new PotionEffect(MobEffects.WEAKNESS, 60, 1, false, false));
                if (SkillShapes.damage(c, t, per * 0.35F, hit().noFx()) && (t.isDead || t.getHealth() <= 0) && spreads[0] < 4) {
                    spreads[0]++;
                    plague(c, new Vec3d(t.posX, t.posY, t.posZ), 3 * c.size, 60, per, spreads);
                }
            }
        });
    }

    private static void peacemaker(final SkillCtx c) {
        final EntityPlayerMP p = c.p;
        final float per = c.dmg(14, false);
        final List<EntityLivingBase> marks = aimTargets(c, 32, 0.2);
        while (marks.size() > 12) {
            marks.remove(marks.size() - 1);
        }
        effect(p, MobEffects.SLOWNESS, 16 + marks.size() * 2, 2);
        for (EntityLivingBase t : marks) {
            Fx.send(c.w, Fx.TIERUP, c.el, t.posX, t.posY + t.height, t.posZ, 0, 0, 0, 1, 1, 16, -1);
            t.addPotionEffect(new PotionEffect(MobEffects.GLOWING, 30, 0, false, false));
        }
        snd(c, "minecraft:block.note.bell", 1.0F, 1.2F);
        final int shots = Math.max(6, marks.size());
        repeat(16, 2, shots, i -> {
            if (gone(p)) {
                return;
            }
            Vec3d mz = muzzle(c);
            Vec3d dir;
            final EntityLivingBase t = i < marks.size() ? marks.get(i) : null;
            if (t != null && !t.isDead) {
                dir = new Vec3d(t.posX, t.posY + t.getEyeHeight(), t.posZ).subtract(mz);
            } else {
                dir = SkillShapes.aim(p, 30).subtract(mz);
            }
            EntitySkillBolt b = SkillMoves.bullet(c, "bullet", mz, dir, 6.0, per, 0, 10);
            b.onHit = (bb, tt) -> {
                c.direct = bb;
                SkillShapes.damage(c, tt, bb.damage, hit().noFx().push(0.3));
                c.direct = null;
                Fx.at(c.w, Fx.IMPACT, c.el, bb.posX, bb.posY, bb.posZ, 1.2);
                killHeal(c, tt, 4.0F);
            };
            Fx.at(c.w, Fx.FLASH, c.el, mz.x, mz.y, mz.z, 0.7);
            snd(c, "minecraft:entity.firework.blast", 1.0F, 1.4F + (i % 3) * 0.1F);
            SkillNet.NET.sendTo(new SkillNet.MsgShake(0.6F, 3), p);
        });
        schedule(18 + shots * 2, () -> artBoom(c, new Vec3d(p.posX, p.posY, p.posZ), 0.6F));
    }

    private static void dragonsRoar(final SkillCtx c) {
        final EntityPlayerMP p = c.p;
        final float per = c.dmg(6, true);
        effect(p, MobEffects.SLOWNESS, 60, 0);
        effect(p, MobEffects.FIRE_RESISTANCE, 80, 0);
        snd(c, "minecraft:entity.enderdragon.growl", 1.2F, 1.2F);
        repeat(4, 2, 28, i -> {
            if (gone(p)) {
                return;
            }
            Vec3d look = p.getLookVec();
            Vec3d h = new Vec3d(look.x, 0, look.z).normalize();
            c.lx = h.x;
            c.lz = h.z;
            c.look = look;
            Vec3d mz = muzzle(c);
            for (int k = 0; k < 3; k++) {
                double d = 2 + RNG.nextDouble() * 7 * c.size;
                double sp = (RNG.nextDouble() - 0.5) * d * 0.7;
                Fx.at(c.w, Fx.FLASH, c.el, mz.x + look.x * d - h.z * sp, mz.y + look.y * d + (RNG.nextDouble() - 0.4), mz.z + look.z * d + h.x * sp, 0.9 + d * 0.15);
            }
            if (i % 3 == 0) {
                Fx.send(c.w, Fx.BEAM, c.el, mz.x, mz.y, mz.z, mz.x + look.x * 8, mz.y + look.y * 8, mz.z + look.z * 8, 1.2, 0, 4, -1);
            }
            SkillShapes.cone(c, 9 * c.size, 0.5, per * 0.3F, hit().fire(4).push(0.15).noFx());
            if (i % 4 == 0) {
                snd(c, "minecraft:item.firecharge.use", 1.0F, 0.6F);
                SkillNet.NET.sendTo(new SkillNet.MsgShake(0.3F, 3), p);
            }
        });
        schedule(62, () -> artBoom(c, new Vec3d(p.posX, p.posY, p.posZ), 0.5F));
    }

    private static void widowmaker(final SkillCtx c) {
        final EntityPlayerMP p = c.p;
        final float per = c.dmg(40, false);
        effect(p, MobEffects.SLOWNESS, 30, 5);
        Fx.on(p, Fx.GATHER, c.el, 2.0, 3, 28);
        snd(c, "minecraft:block.portal.trigger", 0.4F, 2.0F);
        repeat(0, 6, 5, i -> {
            Vec3d from = chest(p);
            Vec3d to = from.add(p.getLookVec().scale(40));
            Fx.send(c.w, Fx.BEAM, c.el, from.x, from.y, from.z, to.x, to.y, to.z, 0.06, 0, 6, -1);
        });
        schedule(30, () -> {
            if (gone(p)) {
                return;
            }
            Vec3d from = chest(p);
            Vec3d dir = p.getLookVec().normalize();
            Vec3d far = from.add(dir.scale(64));
            RayTraceResult rt = c.w.rayTraceBlocks(from, far, false, true, false);
            double len = rt != null ? rt.hitVec.distanceTo(from) : 64;
            Vec3d end = from.add(dir.scale(len));
            fx(c, Fx.BEAM, c.el, from.x, from.y, from.z, end.x, end.y, end.z, 1.0, 0, 8);
            fx(c, Fx.M_SPEAR, c.el, from.x, from.y, from.z, dir.x, dir.y, dir.z, 3.0, 8.0, 8);
            for (final EntityLivingBase t : SkillShapes.lineTargets(c.w, from, dir, len, 0.8)) {
                SkillShapes.damage(c, t, per, hit().push(0.6).stagger());
                final Vec3d at = mid(t);
                schedule(6, () -> {
                    Fx.send(c.w, Fx.M_VORTEX, c.el, at.x, at.y - 0.8, at.z, 0, 0, 0, 1.8, 4, 14, -1);
                    for (EntityLivingBase o : near(c, at.x, at.y, at.z, 5)) {
                        SkillShapes.pull(o, at.x, at.z, 0.5);
                    }
                    schedule(10, () -> {
                        Fx.at(c.w, Fx.EXPLOSION, c.el, at.x, at.y, at.z, 2.2);
                        SkillShapes.area(c, at.x, at.y, at.z, 4, per * 0.5F, hit().from(at).noFx());
                        Fx.sound(c.w, at.x, at.y, at.z, "minecraft:entity.endermen.teleport", 1.0F, 0.5F);
                    });
                });
            }
            SkillShapes.launch(p, -dir.x * 1.2, 0.3, -dir.z * 1.2);
            snd(c, "minecraft:entity.generic.explode", 1.0F, 1.6F);
            snd(c, "minecraft:entity.lightning.thunder", 0.5F, 2.0F);
            SkillNet.NET.sendTo(new SkillNet.MsgShake(1.6F, 8), p);
            artBoom(c, end, 1.0F);
        });
    }

    private static void thunderclap(final SkillCtx c) {
        final EntityPlayerMP p = c.p;
        final float per = c.dmg(14, false);
        final Vec3d center = aimGround(c, 26);
        final double R = 8 * c.size;
        Vec3d mz = muzzle(c);
        Fx.send(c.w, Fx.M_ORB, c.el, mz.x, mz.y, mz.z, center.x, center.y + 18, center.z, 0.8, 0, 10, -1);
        snd(c, "minecraft:entity.generic.explode", 1.0F, 1.2F);
        SkillNet.NET.sendTo(new SkillNet.MsgShake(1.0F, 6), p);
        schedule(10, () -> {
            Fx.send(c.w, Fx.RUNES, c.el, center.x, center.y + 16, center.z, 0, 0, 0, R, 0, 50, -1);
            Fx.send(c.w, Fx.RUNES, c.el, center.x, center.y + 0.05, center.z, 0, 0, 0, R, 0, 50, -1);
            sound(c, center, "minecraft:entity.lightning.thunder", 1.2F, 0.7F);
        });
        repeat(14, 4, 10, i -> {
            List<EntityLivingBase> l = near(c, center.x, center.y, center.z, R + 2);
            final Vec3d to;
            if (!l.isEmpty()) {
                EntityLivingBase t = l.get(RNG.nextInt(l.size()));
                to = new Vec3d(t.posX, t.posY, t.posZ);
            } else {
                to = center.addVector((RNG.nextDouble() - 0.5) * R * 2, 0, (RNG.nextDouble() - 0.5) * R * 2);
            }
            Fx.send(c.w, Fx.M_ORB, c.el, to.x, to.y + 16, to.z, to.x, to.y, to.z, 0.7, 0, 4, -1);
            schedule(4, () -> {
                Fx.at(c.w, Fx.EXPLOSION, c.el, to.x, to.y, to.z, 2.4);
                Fx.send(c.w, Fx.M_BOLT, c.el, to.x, to.y + 16, to.z, to.x, to.y, to.z, 0.25, 0, 6, -1);
                SkillShapes.area(c, to.x, to.y, to.z, 3.5 * c.size, per, hit().from(to).lift(0.3));
                Fx.shake(c.w, to.x, to.y, to.z, 14, 0.6F, 5);
                Fx.sound(c.w, to.x, to.y, to.z, "minecraft:entity.generic.explode", 0.9F, 1.1F);
                Fx.sound(c.w, to.x, to.y, to.z, "minecraft:entity.lightning.impact", 0.8F, 1.0F);
            });
        });
        schedule(58, () -> artBoom(c, center, 1.0F));
    }

    // ================================================================== MAGE
    private static void phoenix(final SkillCtx c) {
        final EntityPlayerMP p = c.p;
        final float per = c.dmg(10, false);
        final List<Vec3d> trail = new ArrayList<>();
        effect(p, MobEffects.RESISTANCE, 20, 3);
        effect(p, MobEffects.FIRE_RESISTANCE, 200, 0);
        double go = SkillShapes.freeDistance(p, c.lx, c.lz, 12 * c.size);
        double v = SkillShapes.dashSpeed(p, go);
        SkillShapes.launch(p, c.lx * v, 0.25, c.lz * v);
        Fx.on(p, Fx.AURA, c.el, 1.6, 0, 10);
        snd(c, "minecraft:entity.blaze.shoot", 1.2F, 0.6F);
        snd(c, "minecraft:entity.enderdragon.flap", 1.0F, 1.2F);
        repeat(1, 1, 8, i -> {
            Vec3d at = new Vec3d(p.posX, p.posY, p.posZ);
            trail.add(at);
            Fx.send(c.w, Fx.CRESCENT, c.el, at.x - c.lz * 0.8, at.y + 1.3, at.z + c.lx * 0.8, -c.lz, 0.4, c.lx, 2.0, 0.8, 4, -1);
            Fx.send(c.w, Fx.CRESCENT, c.el, at.x + c.lz * 0.8, at.y + 1.3, at.z - c.lx * 0.8, c.lz, 0.4, -c.lx, 2.0, 0.8, 4, -1);
            Fx.at(c.w, Fx.FLASH, c.el, at.x, at.y + 1, at.z, 1.2);
            SkillShapes.area(c, at.x, at.y, at.z, 2.5, per * 0.4F, hit().fire(5).noFx());
        });
        schedule(10, () -> {
            Vec3d at = new Vec3d(p.posX, p.posY, p.posZ);
            Fx.at(c.w, Fx.EXPLOSION, c.el, at.x, at.y, at.z, 4.5);
            SkillShapes.area(c, at.x, at.y, at.z, 5 * c.size, per * 2.5F, hit().fire(6).lift(0.6).from(at));
            artBoom(c, at, 1.2F);
            for (Vec3d t : trail) {
                Fx.send(c.w, Fx.FIELD, c.el, t.x, t.y, t.z, 0, 0, 0, 1.6, 0, 60, -1);
            }
        });
        repeat(16, 10, 6, i -> {
            for (Vec3d t : trail) {
                SkillShapes.area(c, t.x, t.y, t.z, 1.8, per * 0.2F, hit().fire(3).noFx().noStatus());
            }
        });
        p.getEntityData().setLong("za_phoenix", c.w.getTotalWorldTime() + 600);
        SkillServer.msg(p, "\u00a76Phoenix blessing: you will rise once if you fall in the next 30s.");
    }

    private static void glacier(final SkillCtx c) {
        final EntityPlayerMP p = c.p;
        final float per = c.dmg(10, false);
        final Vec3d at = aimGround(c, 24);
        final double R = 9 * c.size;
        Fx.send(c.w, Fx.FIELD, c.el, at.x, at.y, at.z, 0, 0, 0, R, 0, 120, -1);
        Fx.send(c.w, Fx.RUNES, c.el, at.x, at.y + 0.05, at.z, 0, 0, 0, R, 0, 120, -1);
        Fx.send(c.w, Fx.SPIRAL, c.el, at.x, at.y, at.z, 0, 0, 0, R * 0.8, 0, 120, -1);
        sound(c, at, "minecraft:item.elytra.flying", 0.6F, 0.6F);
        repeat(5, 10, 11, i -> {
            for (EntityLivingBase t : near(c, at.x, at.y, at.z, R)) {
                t.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 20, 3, false, false));
                SkillShapes.damage(c, t, per * 0.25F, hit().noFx());
            }
            for (int k = 0; k < 2; k++) {
                double a = RNG.nextDouble() * Math.PI * 2, r = RNG.nextDouble() * R;
                double x = at.x + Math.cos(a) * r, z = at.z + Math.sin(a) * r;
                Fx.send(c.w, Fx.SPIKES, c.el, x, at.y, z, 0, 0, 0, 1.2, 0, 1, -1);
                SkillShapes.area(c, x, at.y, z, 1.6, per * 0.6F, hit().lift(0.4).noFx());
            }
            sound(c, at, "minecraft:block.glass.break", 0.5F, 1.4F);
        });
        schedule(120, () -> {
            long until = c.w.getTotalWorldTime() + 60;
            for (EntityLivingBase t : near(c, at.x, at.y, at.z, R)) {
                t.getEntityData().setLong("za_st_frozen", until);
                t.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 60, 9, false, false));
                fx(c, Fx.PILLAR, c.el, t.posX, t.posY, t.posZ, 0, 0, 0, t.height + 0.6, t.width + 0.6, 12);
            }
            Fx.at(c.w, Fx.NOVA, c.el, at.x, at.y + 1, at.z, R * 0.5);
            Fx.send(c.w, Fx.SPIKES, c.el, at.x, at.y, at.z, 0, 0, 0, R * 0.8, 0, 1, -1);
            SkillShapes.area(c, at.x, at.y, at.z, R, per * 2, hit().from(at));
            artBoom(c, at, 1.1F);
        });
    }

    private static void stormlord(final SkillCtx c) {
        final EntityPlayerMP p = c.p;
        final float per = c.dmg(9, false);
        Fx.on(p, Fx.AURA, c.el, 1.2, 0, 120);
        Fx.send(c.w, Fx.TIERUP, c.el, p.posX, p.posY + 1.4, p.posZ, 0, 0, 0, 1, 1, 120, p.getEntityId());
        effect(p, MobEffects.SPEED, 120, 0);
        snd(c, "minecraft:entity.lightning.thunder", 1.0F, 1.4F);
        repeat(4, 10, 12, i -> {
            if (gone(p)) {
                return;
            }
            List<EntityLivingBase> l = near(c, p.posX, p.posY, p.posZ, 14);
            if (l.isEmpty()) {
                return;
            }
            EntityLivingBase t = l.get(RNG.nextInt(l.size()));
            fx(c, Fx.M_BOLT, c.el, t.posX, t.posY + 16, t.posZ, t.posX, t.posY, t.posZ, 0.3, 0, 6);
            Fx.send(c.w, Fx.LIGHTNING, c.el, p.posX, p.posY + 2.2, p.posZ, t.posX, t.posY + 1, t.posZ, 0.6, 0, 4, -1);
            SkillShapes.damage(c, t, per, hit().stagger());
            for (EntityLivingBase o : SkillShapes.around(c.w, t.posX, t.posY, t.posZ, 5)) {
                if (o != t) {
                    Fx.send(c.w, Fx.LIGHTNING, c.el, t.posX, t.posY + 1, t.posZ, o.posX, o.posY + 1, o.posZ, 0.6, 0, 4, -1);
                    SkillShapes.damage(c, o, per * 0.5F, hit().noFx());
                    break;
                }
            }
            Fx.sound(t, "minecraft:entity.lightning.impact", 0.8F, 1.0F + RNG.nextFloat() * 0.3F);
        });
    }

    private static void lich(final SkillCtx c) {
        final EntityPlayerMP p = c.p;
        final float per = c.dmg(9, false);
        final List<EntityLivingBase> tg = nearest(c, 20, 12);
        Fx.on(p, Fx.AURA, c.el, 1.4, 0, 30);
        snd(c, "minecraft:entity.wither.spawn", 0.5F, 1.6F);
        for (int k = 0; k < 6; k++) {
            final int i = k;
            schedule(4 + k * 2, () -> {
                double a = Math.PI * 2 * i / 6;
                Vec3d from = new Vec3d(p.posX + Math.cos(a) * 1.5, p.posY + 2.2, p.posZ + Math.sin(a) * 1.5);
                Vec3d vel = new Vec3d(Math.cos(a) * 0.5, 0.4, Math.sin(a) * 0.5);
                EntitySkillBolt b = new EntitySkillBolt(c, "soul", 0, 0.6F, from.x, from.y, from.z, vel.scale(1.6));
                b.damage = per;
                b.pierce = 3;
                b.homing = 0.22;
                b.maxLife = 120;
                b.target = tg.isEmpty() ? null : tg.get(i % tg.size());
                c.w.spawnEntity(b);
                Fx.at(c.w, Fx.FLASH, c.el, from.x, from.y, from.z, 0.8);
                Fx.sound(c.w, from.x, from.y, from.z, "minecraft:entity.vex.charge", 0.8F, 0.8F);
            });
        }
    }

    private static void gaia(final SkillCtx c) {
        final EntityPlayerMP p = c.p;
        final float per = c.dmg(10, false);
        final List<EntityLivingBase> list = near(c, p.posX, p.posY, p.posZ, 12 * c.size);
        Fx.send(c.w, Fx.RUNES, Fx.POISON, p.posX, p.posY + 0.05, p.posZ, 0, 0, 0, 12 * c.size, 0, 80, -1);
        snd(c, "minecraft:block.grass.break", 1.2F, 0.5F);
        for (EntityLivingBase t : list) {
            t.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 80, 9, false, false));
            t.addPotionEffect(new PotionEffect(MobEffects.WEAKNESS, 80, 1, false, false));
            Fx.send(c.w, Fx.SPIRAL, Fx.POISON, t.posX, t.posY, t.posZ, 0, 0, 0, t.width + 0.4, 0, 80, -1);
            Fx.send(c.w, Fx.PILLAR, Fx.POISON, t.posX, t.posY, t.posZ, 0, 0, 0, t.height * 0.6, t.width + 0.4, 80, -1);
        }
        repeat(10, 10, 7, i -> {
            for (EntityLivingBase t : list) {
                if (!t.isDead) {
                    t.motionX = 0;
                    t.motionZ = 0;
                    t.velocityChanged = true;
                    SkillShapes.damage(c, t, per * 0.25F, hit().noFx());
                }
            }
        });
        schedule(80, () -> {
            for (EntityLivingBase t : list) {
                if (t.isDead) {
                    continue;
                }
                Fx.send(c.w, Fx.SPIKES, Fx.EARTH, t.posX, t.posY, t.posZ, 0, 0, 0, 1.6, 0, 1, -1);
                Fx.send(c.w, Fx.PILLAR, Fx.EARTH, t.posX, t.posY, t.posZ, 0, 0, 0, 3.0, 1.0, 10, -1);
                SkillShapes.damage(c, t, per * 2, hit().lift(1.0).stagger());
            }
            snd(c, "minecraft:entity.generic.explode", 1.0F, 0.6F);
            artBoom(c, new Vec3d(p.posX, p.posY, p.posZ), 1.0F);
        });
    }

    private static void archon(final SkillCtx c) {
        final EntityPlayerMP p = c.p;
        final float per = c.dmg(10, false);
        final Vec3d g = aimGround(c, 20);
        final Vec3d at = new Vec3d(g.x, g.y + 1.8, g.z);
        final double R = 15 * c.size;
        Fx.send(c.w, Fx.M_VORTEX, c.el, g.x, g.y, g.z, 0, 0, 0, 3.0, 5, 66, -1);
        Fx.send(c.w, Fx.M_ORB, c.el, at.x, at.y, at.z, at.x, at.y, at.z, 1.4, 0, 64, -1);
        Fx.send(c.w, Fx.RUNES, c.el, g.x, g.y + 0.05, g.z, 0, 0, 0, R * 0.6, 0, 64, -1);
        sound(c, at, "minecraft:block.portal.travel", 0.4F, 0.6F);
        repeat(2, 2, 30, i -> {
            for (EntityLivingBase t : near(c, at.x, at.y, at.z, R)) {
                double dy = at.y - t.posY;
                SkillShapes.pull(t, at.x, at.z, 0.38);
                t.motionY = Math.max(-0.2, Math.min(0.3, dy * 0.1));
                if (i % 5 == 0) {
                    SkillShapes.damage(c, t, per * 0.3F, hit().noFx());
                }
            }
            if (i % 6 == 0) {
                Fx.send(c.w, Fx.GATHER, c.el, at.x, at.y, at.z, 0, 0, 0, 4.0, 3, 6, -1);
            }
        });
        schedule(64, () -> {
            Fx.at(c.w, Fx.EXPLOSION, c.el, at.x, at.y, at.z, 6.0);
            Fx.send(c.w, Fx.M_DOME, c.el, g.x, g.y, g.z, 0, 0, 0, 7, 0, 10, -1);
            SkillShapes.area(c, at.x, at.y, at.z, 7 * c.size, per * 3, hit().lift(0.8).push(1.2).from(at));
            sound(c, at, "minecraft:entity.generic.explode", 1.4F, 0.5F);
            artBoom(c, at, 1.4F);
        });
    }

    private static void seraph(final SkillCtx c) {
        final EntityPlayerMP p = c.p;
        final float per = c.dmg(6, false);
        Fx.on(p, Fx.AURA, c.el, 1.6, 0, 60);
        Fx.send(c.w, Fx.PILLAR, c.el, p.posX, p.posY, p.posZ, 0, 0, 0, 8, 1.6, 30, -1);
        snd(c, "minecraft:block.beacon.power_select", 1.2F, 1.4F);
        repeat(0, 5, 12, i -> {
            if (gone(p)) {
                return;
            }
            double ry = Math.toRadians(p.renderYawOffset);
            double sx = Math.cos(ry), sz = Math.sin(ry), bx = Math.sin(ry) * 0.4, bz = -Math.cos(ry) * 0.4;
            Fx.send(c.w, Fx.CRESCENT, c.el, p.posX + bx + sx * 0.9, p.posY + 1.5, p.posZ + bz + sz * 0.9, sx, 0.5, sz, 2.4, 1.0, 6, -1);
            Fx.send(c.w, Fx.CRESCENT, c.el, p.posX + bx - sx * 0.9, p.posY + 1.5, p.posZ + bz - sz * 0.9, -sx, 0.5, -sz, 2.4, 1.0, 6, -1);
            for (EntityPlayer a : c.w.getEntitiesWithinAABB(EntityPlayer.class, p.getEntityBoundingBox().grow(16, 6, 16))) {
                a.heal(a.getMaxHealth() / 12F);
                if (i % 3 == 0) {
                    fx(c, Fx.HEAL, c.el, a.posX, a.posY, a.posZ, 0, 0, 0, 1.0, 0, 8);
                }
            }
            for (int k = 0; k < 3; k++) {
                double a = RNG.nextDouble() * Math.PI * 2, r = RNG.nextDouble() * 12;
                double x = p.posX + Math.cos(a) * r, z = p.posZ + Math.sin(a) * r;
                double y = SkillShapes.ground(c.w, x, p.posY + 3, z);
                Fx.send(c.w, Fx.PILLAR, Fx.HOLY, x, y, z, 0, 0, 0, 6, 0.5, 6, -1);
                for (EntityLivingBase t : near(c, x, y, z, 2.2)) {
                    SkillShapes.damage(c, t, t.isEntityUndead() ? per * 2 : per, hit().noFx());
                }
            }
        });
        schedule(62, () -> artBoom(c, new Vec3d(p.posX, p.posY, p.posZ), 0.6F));
    }
}
