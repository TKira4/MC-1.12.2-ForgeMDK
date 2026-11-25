package com.imba.undyingarcana;

import java.util.List;
import java.util.Set;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.effect.EntityLightningBolt;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.MobEffects;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import static com.imba.undyingarcana.SkillMoves.aimGround;
import static com.imba.undyingarcana.SkillMoves.aimTargets;
import static com.imba.undyingarcana.SkillMoves.allies;
import static com.imba.undyingarcana.SkillMoves.effect;
import static com.imba.undyingarcana.SkillMoves.fx;
import static com.imba.undyingarcana.SkillMoves.shake;
import static com.imba.undyingarcana.SkillMoves.snd;
import static com.imba.undyingarcana.SkillServer.repeat;
import static com.imba.undyingarcana.SkillServer.schedule;
import static com.imba.undyingarcana.SkillShapes.hit;

/**
 * Own move sets (Quick / Heavy / Art) of the armory types that have no vanilla-pack counterpart: twin blades, sickles,
 * claws, nunchaku, cleaver, greataxe, hand axe, flail, gauntlets, scythe, trident, whip, repeater, throwing knives,
 * chakram, boomerang, javelin and the five magic weapons (m_wand, m_staff, m_orb, m_scepter, m_rune).
 * SkillCatalog asks this class first for names, numbers, families and icons; SkillMoves.cast lets it run the moves.
 * Thrown weapons fly as EntitySkillBolt look 4 / 5 (the held item, spinning).
 */
@Mod.EventBusSubscriber(modid = UndyingArcana.MODID)
public final class ArmoryMoves {
    private ArmoryMoves() {
    }

    // ------------------------------------------------------------------ data
    /** type, family, element, quick name, heavy name, art name. */
    private static final String[][] T = {
            {"twinblades", "blade", "steel", "Twin Cut", "Blade Tornado", "Dance of Eight"},
            {"sickles", "blade", "blood", "Hook Slash", "Reaping Hooks", "Pounce"},
            {"claws", "blade", "blood", "Rake", "Shred Dash", "Frenzy"},
            {"nunchaku", "blade", "wind", "Flurry Combo", "Whirling Guard", "Hundred Strikes"},
            {"cleaver", "heavy", "blood", "Butcher Chop", "Grinder Slam", "Execution"},
            {"greataxe", "heavy", "earth", "Overhead Chop", "Double Whirl", "Earth Rend"},
            {"handaxe", "heavy", "steel", "Returning Axe", "Axe Fan", "Axe Orbit"},
            {"flail", "heavy", "steel", "Flail Lash", "Spinning Crush", "Chain Hook"},
            {"gauntlets", "heavy", "earth", "Triple Jab", "Haymaker", "Earthshaker Fist"},
            {"scythe", "polearm", "necromancy", "Wide Reap", "Soul Harvest", "Death Wheel"},
            {"trident", "polearm", "lightning", "Tide Thrust", "Storm Call", "Tidal Surge"},
            {"whip", "polearm", "steel", "Lash", "Snare Pull", "Five-Way Lash"},
            {"repeater", "bow", "steel", "Burst Fire", "Bolt Spray", "Bolt Storm"},
            {"knives", "bow", "shadow", "Knife Toss", "Blade Ring", "Mark of Death"},
            {"chakram", "bow", "wind", "Ricochet", "Spinning Ring", "Triple Orbit"},
            {"boomerang", "bow", "wind", "Boomerang", "Wide Arc", "Giant Boomerang"},
            {"javelin", "bow", "steel", "Piercing Javelin", "Explosive Javelin", "Skyfall Javelin"},
            {"m_wand", "wand", "", "Quick Bolt", "Chain Bolt", "Bolt Storm"},
            {"m_staff", "wand", "", "Grand Orb", "Pillar", "Elemental Meteor"},
            {"m_orb", "wand", "", "Seeker", "Orbiting Wisps", "Singularity"},
            {"m_scepter", "wand", "", "Royal Bolt", "Sanctum", "Blessing"},
            {"m_rune", "wand", "", "Rune Mine", "Triple Trap", "Grand Circle"},
            // fighting styles (Martial): bare hands or a fist weapon with a style; scale with Strength
            {"fist_brawler", "fist", "steel", "One-Two", "Rising Uppercut", "Haymaker Rush"},
            {"fist_ironleg", "fist", "wind", "Snap Kick", "Whirlwind Kick", "Sky Drop"},
            {"fist_tiger", "fist", "blood", "Tiger Rake", "Pounce", "Tiger Frenzy"},
            {"fist_stone", "fist", "earth", "Stone Jab", "Earthbreaker", "Mountain Stance"},
            {"fist_palm", "fist", "wind", "Palm Push", "Redirect", "Hundred Palms"},
            {"fist_arcane", "fist", "", "Spark Jab", "Arcane Burst", "Elemental Avatar"}};

    private static final String[][] DESC = {
            {"Dash a short way and cut twice.", "A tornado of blades spins around you, hitting 6 times.", "Blink to the target and cut it 8 times."},
            {"Two hooking cuts in front of you; enemies bleed.", "Hook enemies around you in, then a spinning cut.", "Leap onto the target and cut it 6 times."},
            {"Three fast rakes in front of you.", "Dash through enemies, raking everything on the way.", "Frenzy for 5s: much faster attacks, Strength and Speed."},
            {"A 4-hit combo in front of you.", "Spin the sticks for 3s: half damage taken and part of it struck back.", "Ten strikes on one enemy, then it is stunned."},
            {"A heavy chop in front of you; enemies bleed.", "Slam the ground: everyone around you bleeds.", "Execute one enemy: more damage the less health it has."},
            {"A slow overhead chop that cracks the ground ahead.", "Two full spins with the axe.", "Split the ground 14 blocks ahead, throwing enemies up."},
            {"Throw the axe; it flies back to your hand.", "Throw three axes in a fan.", "Three axes circle you for 4s."},
            {"Lash 4 blocks ahead with the ball.", "Spin the ball and crush: Weakness on everyone hit.", "Throw the ball: the first enemy hit is dragged to you."},
            {"Three quick jabs.", "A charged punch that launches enemies away.", "Punch the ground: three shockwaves roll outwards."},
            {"A wide reaping sweep in front of you.", "A full 360 reap; you heal for every enemy hit.", "Throw the scythe: it spins out and back, hitting twice."},
            {"A piercing thrust that hits a line.", "Throw the trident: lightning strikes where it lands.", "Three tidal waves push enemies back."},
            {"A long lash that reaches 6 blocks.", "Lash and drag enemies to you; they are slowed.", "Lash in five directions; everything hit is slowed."},
            {"Fire 5 bolts in quick succession.", "Fire 12 bolts in a wide fan.", "Bolts rain on the area you aim at in three waves."},
            {"Throw three knives in a narrow fan.", "Eight knives fly out in a ring.", "Mark up to 5 enemies; the marks explode after 3s."},
            {"Throw the chakram; it bounces to 3 enemies.", "The chakram spins in place for 3s, cutting around it.", "Three chakrams circle you, then fly at enemies."},
            {"Throw the boomerang; it comes back, hitting twice.", "The boomerang flies a wide circle around you.", "A giant boomerang pierces everything, out and back."},
            {"Throw a javelin that pierces 5 enemies.", "Throw a javelin that explodes where it lands.", "A giant javelin falls from the sky at your aim."},
            {"A fast, cheap bolt of your element.", "A bolt that chains to 2 more enemies.", "Bolts pour from the wand for 2s at enemies ahead."},
            {"A big, slow orb that explodes.", "A pillar of your element erupts at your aim.", "Call down a meteor of your element."},
            {"A seeking orb that follows its target.", "Three wisps circle you for 4s and burn what they touch.", "A black hole at your aim pulls enemies in, then bursts."},
            {"A bolt; allies near you get Speed and a little health.", "Heal allies around you, with Absorption and Resistance.", "Allies within 10 blocks deal 20% more damage for 8s."},
            {"Place a rune at your aim that explodes when an enemy steps on it.", "Three runes: slowing, exploding and rooting.", "A great circle at your aim burns enemies for 5s."},
            {"Two fast jabs; the second knocks back.", "A charged uppercut that launches enemies.", "Rush the target: six punches, then a huge knockback."},
            {"A long, fast kick that pushes enemies away.", "Two spinning kicks hit everything around you.", "Leap high and drop: a shockwave where you land."},
            {"Three rakes in front of you; enemies bleed.", "Pounce forward through enemies; they bleed heavily.", "Frenzy for 6s: much faster hits, every hit bleeds."},
            {"One heavy jab that weakens the target.", "Smash the ground: enemies around you are stunned.", "Stance for 6s: Resistance, no knockback, hits send shockwaves."},
            {"A palm strike that blows enemies far away.", "Guard for 1.5s: a melee hit on you is redirected back.", "Twelve palm strikes on the target; each heals you a little."},
            {"A jab that bursts with your element.", "Your fist explodes with your element around you.", "For 8s your hits burst with your element (2 mana each)."}};

    /** Quick [damage, size, cooldown], Heavy [damage, radius, cooldown, lift, push, fire], Art [damage, size, cooldown]. */
    private static final double[][] QS = {{4.5, 3, 80}, {4.5, 3.5, 80}, {2.5, 3, 70}, {2.2, 3, 70},
            {6, 3.5, 100}, {8, 4, 110}, {6, 1, 80}, {6, 4, 90}, {2.5, 3, 70},
            {6, 4.5, 90}, {6.5, 6, 80}, {5, 6, 70},
            {4, 0, 70}, {4, 0, 60}, {5, 0, 70}, {5, 0, 70}, {8, 0, 80},
            {5, 0, 40}, {9, 2.5, 80}, {6, 0, 60}, {5, 0, 70}, {10, 2.5, 80},
            {3, 3, 50}, {4, 4, 60}, {2.4, 3, 60}, {7, 3, 80}, {2, 4, 60}, {4, 3, 50}};
    private static final double[][] HS = {{3.5, 3.5, 160, 0, 0.2, 0}, {6, 5, 180, 0, 0.3, 0}, {7, 6, 160, 0, 0.2, 0},
            {3, 3, 220, 0, 0.4, 0}, {8, 4.5, 200, 0.2, 0.4, 0}, {9, 4.5, 220, 0, 0.8, 0}, {7, 0, 160, 0, 0, 0},
            {9, 4, 220, 0, 0.6, 0}, {14, 3.5, 200, 0.35, 1.6, 0}, {7, 5, 200, 0, 0.4, 0}, {10, 3, 220, 0, 0.3, 0},
            {6, 7, 180, 0, 0, 0}, {3.5, 0, 180, 0, 0, 0}, {3.5, 0, 180, 0, 0, 0}, {3, 2.5, 200, 0, 0, 0},
            {6, 5, 180, 0, 0, 0}, {10, 3, 200, 0.3, 0.5, 0}, {6, 0, 160, 0, 0, 0}, {10, 2.5, 200, 0.6, 0, 0},
            {4, 2.2, 240, 0, 0, 0}, {0, 8, 400, 0, 0, 0}, {9, 2.5, 240, 0, 0, 0},
            {11, 3, 160, 0.7, 0.3, 0}, {6, 3.5, 180, 0.2, 0.6, 0}, {9, 7, 180, 0, 0.2, 0}, {10, 4, 200, 0.4, 0.2, 0},
            {6, 0, 200, 0, 0, 0}, {9, 3, 180, 0, 0.4, 0}};
    private static final double[][] AS = {{5, 10, 1200}, {6, 8, 1200}, {6, 3, 1200}, {3, 8, 1200},
            {22, 6, 1200}, {14, 14, 1200}, {4, 2.2, 1200}, {14, 18, 1200}, {14, 7, 1200},
            {10, 10, 1200}, {12, 10, 1200}, {10, 7, 1200},
            {5, 4, 1200}, {22, 12, 1200}, {6, 2.2, 1200}, {20, 20, 1200}, {28, 6, 1200},
            {5, 16, 1200}, {26, 6, 1200}, {4, 5, 1200}, {0, 10, 1200}, {4, 6, 1200},
            {4, 10, 1000}, {16, 5, 1000}, {6, 4, 1000}, {12, 4, 1000}, {2.5, 8, 1000}, {6, 4, 1000}};
    private static final int[] CHARGE = {12, 12, 12, 14, 20, 24, 14, 20, 22, 18, 18, 14, 15, 13, 14, 14, 16, 16, 18, 18, 18, 18,
            14, 14, 12, 20, 10, 14};

    private static int idx(String t) {
        for (int i = 0; i < T.length; i++) {
            if (T[i][0].equals(t)) {
                return i;
            }
        }
        return -1;
    }

    public static boolean has(String t) {
        return idx(t) >= 0;
    }

    /** Family of an armory move type, or null for other types. */
    public static String family(String t) {
        int i = idx(t);
        return i < 0 ? null : T[i][1];
    }

    public static String elementOf(String t) {
        int i = idx(t);
        return i < 0 ? "" : T[i][2];
    }

    public static String quickName(String t) {
        return T[idx(t)][3];
    }

    public static String heavyName(String t) {
        return T[idx(t)][4];
    }

    public static String artName(String t) {
        return T[idx(t)][5];
    }

    public static String quickDesc(String t) {
        return DESC[idx(t)][0];
    }

    public static String heavyDesc(String t) {
        return DESC[idx(t)][1];
    }

    public static String artDesc(String t) {
        return DESC[idx(t)][2];
    }

    public static double[] quickStats(String t) {
        return QS[idx(t)].clone();
    }

    public static double[] heavyStats(String t) {
        return HS[idx(t)].clone();
    }

    public static double[] artStats(String t) {
        return AS[idx(t)].clone();
    }

    public static int chargeTicks(String t) {
        return CHARGE[idx(t)];
    }

    public static String[] evoNames(String t) {
        return new String[]{"Greater " + heavyName(t), "Ultimate " + heavyName(t)};
    }

    /** Icon file (textures/skills/<name>.png): q_ / h_ / a_ + type. */
    public static String icon(String t, int slot) {
        return (slot == 0 ? "q_" : slot == 1 ? "h_" : "a_") + t;
    }

    /** Magic weapons use the element picked in the Grimoire (Arcane until one is picked). */
    public static void element(SkillCtx c) {
        if (!has(c.in.type) || !"wand".equals(T[idx(c.in.type)][1]) && !"fist_arcane".equals(c.in.type)) {
            return;
        }
        int e = Magic.element(c.p, 0);
        c.element = e >= 0 && e < Magic.ELEMENT.length ? Magic.ELEMENT[e] : "magic";
    }

    // ------------------------------------------------------------------ helpers
    private static Vec3d dir(SkillCtx c, double deg) {
        double a = Math.toRadians(deg);
        double x = c.lx * Math.cos(a) - c.lz * Math.sin(a), z = c.lx * Math.sin(a) + c.lz * Math.cos(a);
        return new Vec3d(x, 0, z);
    }

    private static Vec3d eye(SkillCtx c) {
        return new Vec3d(c.p.posX, c.eyeY() - 0.2, c.p.posZ);
    }

    /** First enemy in front of the player (crosshair first), or the nearest within range. */
    private static EntityLivingBase target(SkillCtx c, double range) {
        List<EntityLivingBase> l = aimTargets(c, range, 0.5);
        if (!l.isEmpty()) {
            return l.get(0);
        }
        EntityLivingBase best = null;
        double bd = range * range;
        for (EntityLivingBase t : SkillShapes.around(c.w, c.p.posX, c.p.posY, c.p.posZ, range)) {
            double d = t.getDistanceSq(c.p);
            if (d < bd) {
                bd = d;
                best = t;
            }
        }
        return best;
    }

    private static void bleed(EntityLivingBase t, int ticks) {
        effect(t, MobEffects.WITHER, ticks, 0);
    }

    /** The held weapon thrown as a spinning projectile (look 4 end over end, 5 flat). */
    private static EntitySkillBolt toss(SkillCtx c, Vec3d d, double speed, float dmg, int pierce, int life, boolean flat, float size) {
        Vec3d n = d.normalize();
        Vec3d from = eye(c).add(n.scale(0.7));
        EntitySkillBolt b = new EntitySkillBolt(c, "armory", flat ? 5 : 4, size, from.x, from.y, from.z, n.scale(speed));
        b.setItem(c.p.getHeldItemMainhand());
        b.damage = dmg;
        b.pierce = pierce;
        b.maxLife = life;
        b.onHit = (bb, t) -> SkillShapes.damage(bb.ctx, t, bb.damage, hit());
        b.onEnd = (bb, at) -> { };
        c.w.spawnEntity(b);
        return b;
    }

    /** Makes a thrown weapon turn back after 'out' ticks and fly to the player, hitting again on the way. */
    private static void returning(EntitySkillBolt b, int out, double speed) {
        b.ghost = true;
        b.maxLife = out + 60;
        b.pierce = 999;
        b.onTick = bb -> {
            if (bb.ticksExisted == out) {
                bb.clearHits();
            }
            if (bb.ticksExisted >= out) {
                EntityPlayer p = bb.ctx.p;
                Vec3d to = new Vec3d(p.posX - bb.posX, p.posY + 1.2 - bb.posY, p.posZ - bb.posZ);
                double d = to.lengthVector();
                if (d < 1.3) {
                    bb.finish();
                    return;
                }
                Vec3d v = to.scale(speed / Math.max(0.01, d));
                bb.motionX = v.x;
                bb.motionY = v.y;
                bb.motionZ = v.z;
                bb.velocityChanged = true;
            }
        };
    }

    /** Keeps a bolt on a circle of radius r around the player for 'life' ticks, re-hitting every 10 ticks. */
    private static void orbit(EntitySkillBolt b, double r, double phase, int life, double turn) {
        b.ghost = true;
        b.maxLife = life;
        b.pierce = 999;
        b.onTick = bb -> {
            EntityPlayer p = bb.ctx.p;
            double a = phase + bb.ticksExisted * turn;
            double nx = p.posX + Math.cos(a) * r, nz = p.posZ + Math.sin(a) * r, ny = p.posY + 1.0;
            bb.motionX = nx - bb.posX;
            bb.motionY = ny - bb.posY;
            bb.motionZ = nz - bb.posZ;
            bb.velocityChanged = true;
            if (bb.ticksExisted % 10 == 0) {
                bb.clearHits();
            }
        };
    }

    /** Element bolt for the magic weapons (orb look); hits apply the element status. */
    private static EntitySkillBolt mbolt(SkillCtx c, Vec3d d, float size, double speed, float dmg, int look) {
        EntitySkillBolt b = SkillMoves.bolt(c, "armory", look, size, speed, d, dmg);
        b.onHit = (bb, t) -> SkillShapes.damage(bb.ctx, t, bb.damage, hit());
        b.onEnd = (bb, at) -> { };
        return b;
    }

    private static void boom(SkillCtx c, Vec3d at, double r, float dmg, double lift) {
        Fx.at(c.w, Fx.EXPLOSION, c.el, at.x, at.y, at.z, r);
        SkillShapes.area(c, at.x, at.y, at.z, r, dmg, hit().push(0.4).lift(lift).from(at).noFx());
        Fx.shake(c.w, at.x, at.y, at.z, 10, 0.6F, 5);
        Fx.sound(c.w, at.x, at.y, at.z, "minecraft:entity.generic.explode", 0.8F, 1.3F);
    }

    private static void slashFx(SkillCtx c, double x, double y, double z, Vec3d d, double size) {
        fx(c, Fx.SLASH, c.el, x, y, z, d.x, 0, d.z, size, size, 3);
    }

    // ------------------------------------------------------------------ entry points
    /** Runs the move if this is an armory move type; false otherwise. */
    public static boolean cast(final SkillCtx c) {
        final int i = idx(c.in.type);
        if (i < 0) {
            return false;
        }
        if (c.slot == 0) {
            quick(c, i);
        } else if (c.slot == 2) {
            SkillMoves.artIntro(c);
            art(c, i);
        } else {
            heavy(c, i);
        }
        return true;
    }

    // ================================================================== QUICK
    private static void quick(final SkillCtx c, int i) {
        final EntityPlayerMP p = c.p;
        final double[] st = QS[i];
        final float dmg = c.dmg(st[0], false);
        final double r = st[1] * c.size;
        final double px = p.posX, py = p.posY, pz = p.posZ;
        final Vec3d f = new Vec3d(c.lx, 0, c.lz);
        switch (c.in.type) {
            case "twinblades": {
                double go = Math.max(1.0, SkillShapes.freeDistance(p, c.lx, c.lz, 3 * c.size));
                double v = SkillShapes.dashSpeed(p, go);
                SkillShapes.launch(p, c.lx * v, p.onGround ? 0.1 : p.motionY, c.lz * v);
                final Set<Integer> once = SkillShapes.set();
                SkillShapes.line(c, new Vec3d(px, py + 1, pz), f, go + 1, 1.3, dmg, hit(), once);
                fx(c, Fx.DASH, c.el, px, py, pz, px + c.lx * go, py, pz + c.lz * go, 0, 0, 4);
                repeat(2, 3, 2, k -> {
                    slashFx(c, p.posX, p.posY + 1.1, p.posZ, dir(c, k == 0 ? 25 : -25), 2.2);
                    SkillShapes.area(c, p.posX + c.lx, p.posY, p.posZ + c.lz, 2.2, dmg * 0.7F, hit().noFx());
                    snd(c, "minecraft:entity.player.attack.sweep", 0.8F, 1.5F + k * 0.2F);
                });
                break;
            }
            case "sickles":
                repeat(0, 3, 2, k -> {
                    slashFx(c, p.posX, p.posY + 1.0, p.posZ, dir(c, k == 0 ? 30 : -30), 2.4);
                    for (EntityLivingBase t : SkillShapes.around(c.w, p.posX, p.posY, p.posZ, r)) {
                        double dx = t.posX - p.posX, dz = t.posZ - p.posZ, d = Math.sqrt(dx * dx + dz * dz);
                        if (d < 1.2 || (dx * c.lx + dz * c.lz) / d > 0.4) {
                            if (SkillShapes.damage(c, t, dmg, hit().noFx())) {
                                bleed(t, 60);
                            }
                        }
                    }
                    snd(c, "minecraft:entity.player.attack.sweep", 0.8F, 1.6F);
                });
                break;
            case "claws":
                repeat(0, 2, 3, k -> {
                    slashFx(c, p.posX, p.posY + 1.0, p.posZ, dir(c, (k - 1) * 20), 1.8);
                    SkillShapes.cone(c, r, Math.toRadians(45), dmg, hit().noFx());
                    snd(c, "minecraft:entity.player.attack.weak", 0.9F, 1.6F + k * 0.1F);
                });
                break;
            case "nunchaku":
                repeat(0, 2, 4, k -> {
                    SkillShapes.cone(c, r, Math.toRadians(50), dmg, hit().push(k == 3 ? 0.6 : 0.05));
                    Fx.at(c.w, Fx.IMPACT, c.el, p.posX + c.lx * 1.6, p.posY + 1.1, p.posZ + c.lz * 1.6, 0.6);
                    snd(c, "minecraft:entity.player.attack.knockback", 0.7F, 1.4F + k * 0.15F);
                });
                break;
            case "cleaver": {
                slashFx(c, px, py + 1.2, pz, f, 2.8);
                for (EntityLivingBase t : SkillShapes.around(c.w, px, py, pz, r)) {
                    double dx = t.posX - px, dz = t.posZ - pz, d = Math.sqrt(dx * dx + dz * dz);
                    if (d < 1.2 || (dx * c.lx + dz * c.lz) / d > 0.6) {
                        if (SkillShapes.damage(c, t, dmg, hit().push(0.2))) {
                            bleed(t, 80);
                        }
                    }
                }
                snd(c, "minecraft:entity.player.attack.strong", 1.0F, 0.8F);
                break;
            }
            case "greataxe": {
                Vec3d from = new Vec3d(px, py + 0.5, pz);
                SkillShapes.line(c, from, f, r, 1.3, dmg, hit().lift(0.2), null);
                Fx.send(c.w, Fx.M_CRACK, c.el, px + c.lx, py, pz + c.lz, c.lx, 0, c.lz, r, 0, 30, -1);
                fx(c, Fx.DEBRIS, c.el, px + c.lx * 2, py, pz + c.lz * 2, 0, 0, 0, 1.2, 0, 6);
                shake(c, 8, 0.6F, 5);
                snd(c, "minecraft:entity.generic.explode", 0.6F, 1.4F);
                break;
            }
            case "handaxe": {
                EntitySkillBolt b = toss(c, c.p.getLookVec(), 1.3 * c.speed, dmg, 3, 40, false, 0.35F);
                returning(b, 9, 1.4);
                snd(c, "minecraft:entity.snowball.throw", 1.0F, 0.6F);
                break;
            }
            case "flail": {
                Vec3d from = new Vec3d(px, py + 1.0, pz);
                SkillShapes.line(c, from, f, r, 1.2, dmg, hit().push(0.5), null);
                Fx.at(c.w, Fx.IMPACT, c.el, px + c.lx * r, py + 1, pz + c.lz * r, 1.0);
                snd(c, "minecraft:entity.irongolem.attack", 0.9F, 1.1F);
                break;
            }
            case "gauntlets":
                repeat(0, 3, 3, k -> {
                    SkillShapes.cone(c, r, Math.toRadians(40), dmg, hit().push(k == 2 ? 0.8 : 0.1));
                    Fx.at(c.w, Fx.IMPACT, c.el, p.posX + c.lx * 1.5, p.posY + 1.2, p.posZ + c.lz * 1.5, 0.7);
                    snd(c, "minecraft:entity.player.attack.knockback", 0.9F, 1.2F + k * 0.15F);
                });
                break;
            case "fist_brawler":
                repeat(0, 3, 2, k -> {
                    Martial.pose(p, k == 0 ? Martial.POSE_JAB_L : Martial.POSE_JAB_R, 6);
                    SkillShapes.cone(c, r, Math.toRadians(40), dmg, hit().push(k == 1 ? 0.9 : 0.05));
                    Fx.at(c.w, Fx.IMPACT, c.el, p.posX + c.lx * 1.5, p.posY + 1.3, p.posZ + c.lz * 1.5, 0.7);
                    snd(c, "minecraft:entity.player.attack.strong", 0.8F, 1.3F + k * 0.2F);
                });
                break;
            case "fist_ironleg":
                Martial.pose(p, Martial.POSE_KICK_R, 8);
                SkillShapes.cone(c, r, Math.toRadians(30), dmg, hit().push(1.1));
                fx(c, Fx.CRESCENT, c.el, px + c.lx, py + 0.6, pz + c.lz, c.lx, 0, c.lz, r * 0.7, 0.6, 6);
                snd(c, "minecraft:entity.player.attack.knockback", 1.0F, 1.1F);
                break;
            case "fist_tiger":
                repeat(0, 2, 3, k -> {
                    slashFx(c, p.posX, p.posY + 1.0, p.posZ, dir(c, (k - 1) * 25), 1.6);
                    for (EntityLivingBase t : SkillShapes.coneTargets(c, r, Math.toRadians(45))) {
                        if (SkillShapes.damage(c, t, dmg, hit().noFx())) {
                            bleed(t, 40);
                        }
                    }
                    snd(c, "minecraft:entity.player.attack.weak", 0.9F, 1.5F + k * 0.1F);
                });
                break;
            case "fist_stone":
                for (EntityLivingBase t : SkillShapes.coneTargets(c, r, Math.toRadians(35))) {
                    if (SkillShapes.damage(c, t, dmg, hit().push(0.5))) {
                        effect(t, MobEffects.WEAKNESS, 80, 1);
                    }
                }
                Fx.at(c.w, Fx.IMPACT, c.el, px + c.lx * 1.6, py + 1.2, pz + c.lz * 1.6, 1.0);
                snd(c, "minecraft:entity.irongolem.attack", 0.8F, 1.3F);
                break;
            case "fist_palm":
                SkillShapes.cone(c, r, Math.toRadians(40), dmg, hit().push(2.0));
                fx(c, Fx.RING, c.el, px + c.lx * 1.5, py + 1.1, pz + c.lz * 1.5, c.lx, 0, c.lz, 1.2, 0, 6);
                snd(c, "minecraft:entity.enderdragon.flap", 0.7F, 1.6F);
                break;
            case "fist_arcane":
                SkillShapes.cone(c, r, Math.toRadians(40), dmg, hit().push(0.3));
                Fx.at(c.w, Fx.EXPLOSION, c.el, px + c.lx * 1.6, py + 1.2, pz + c.lz * 1.6, 0.6);
                snd(c, "minecraft:entity.firework.blast", 0.6F, 1.6F);
                break;
            case "scythe":
                fx(c, Fx.CRESCENT, c.el, px + c.lx, py + 0.9, pz + c.lz, c.lx, 0, c.lz, r * 0.9, 0.9, 8);
                SkillShapes.cone(c, r, Math.toRadians(80), dmg, hit().push(0.3));
                snd(c, "minecraft:entity.player.attack.sweep", 1.0F, 0.7F);
                break;
            case "trident": {
                Vec3d from = new Vec3d(px, py + 1.1, pz);
                SkillShapes.line(c, from, f, r, 0.9, dmg, hit().push(0.3), null);
                fx(c, Fx.BEAM, Fx.ICE, from.x, from.y, from.z, from.x + c.lx * r, from.y, from.z + c.lz * r, 0.35, 0, 3);
                snd(c, "minecraft:entity.player.attack.strong", 0.9F, 1.3F);
                break;
            }
            case "whip": {
                Vec3d from = new Vec3d(px, py + 1.1, pz);
                SkillShapes.line(c, from, f, r, 0.8, dmg, hit().push(0.2), null);
                fx(c, Fx.BEAM, c.el, from.x, from.y, from.z, from.x + c.lx * r, from.y, from.z + c.lz * r, 0.15, 0, 3);
                snd(c, "minecraft:entity.firework.blast", 0.7F, 1.8F);
                break;
            }
            case "repeater":
                repeat(0, 2, 5, k -> {
                    Vec3d l = c.p.getLookVec();
                    Vec3d m = SkillMoves.muzzle(c);
                    SkillMoves.arrow(c, m.x, m.y, m.z, l.x * 3 * c.speed, l.y * 3 * c.speed, l.z * 3 * c.speed, dmg, false);
                    snd(c, "minecraft:entity.arrow.shoot", 0.7F, 1.4F + k * 0.05F);
                });
                break;
            case "knives":
                for (int a = -1; a <= 1; a++) {
                    Vec3d l = c.p.getLookVec();
                    Vec3d d = new Vec3d(l.x * Math.cos(a * 0.12) - l.z * Math.sin(a * 0.12), l.y, l.x * Math.sin(a * 0.12) + l.z * Math.cos(a * 0.12));
                    toss(c, d, 1.8 * c.speed, dmg, 0, 25, false, 0.25F);
                }
                snd(c, "minecraft:entity.snowball.throw", 1.0F, 1.4F);
                break;
            case "chakram": {
                EntitySkillBolt b = toss(c, c.p.getLookVec(), 1.4 * c.speed, dmg, 3, 50, true, 0.4F);
                b.homing = 0.5;
                snd(c, "minecraft:entity.snowball.throw", 1.0F, 1.0F);
                break;
            }
            case "boomerang": {
                EntitySkillBolt b = toss(c, c.p.getLookVec(), 1.2 * c.speed, dmg, 99, 40, true, 0.4F);
                returning(b, 11, 1.3);
                snd(c, "minecraft:entity.snowball.throw", 1.0F, 0.8F);
                break;
            }
            case "javelin":
                toss(c, c.p.getLookVec(), 2.2 * c.speed, dmg, 5, 30, false, 0.4F);
                snd(c, "minecraft:entity.arrow.shoot", 1.0F, 0.6F);
                break;
            case "m_wand":
                mbolt(c, c.p.getLookVec(), 0.25F, 1.6 * c.speed, dmg, 1);
                snd(c, "minecraft:entity.blaze.shoot", 0.6F, 1.6F);
                break;
            case "m_staff": {
                EntitySkillBolt b = mbolt(c, c.p.getLookVec(), 0.6F, 0.7 * c.speed, dmg, 2);
                final double rr = r;
                b.onEnd = (bb, at) -> boom(bb.ctx, at, rr, bb.damage * 0.6F, 0.2);
                snd(c, "minecraft:entity.blaze.shoot", 0.8F, 0.8F);
                break;
            }
            case "m_orb": {
                EntitySkillBolt b = mbolt(c, c.p.getLookVec(), 0.4F, 0.9 * c.speed, dmg, 0);
                b.homing = 0.35;
                b.target = target(c, 20);
                b.maxLife = 60;
                snd(c, "minecraft:entity.illusion_illager.cast_spell", 0.7F, 1.4F);
                break;
            }
            case "m_scepter":
                mbolt(c, c.p.getLookVec(), 0.35F, 1.3 * c.speed, dmg, 0);
                for (EntityPlayer o : allies(c, 6)) {
                    effect(o, MobEffects.SPEED, 80, 0);
                    o.heal(2.0F);
                }
                Fx.on(p, Fx.HEAL, Fx.HOLY, 1.2, 0, 8);
                snd(c, "minecraft:block.note.chime", 0.8F, 1.4F);
                break;
            case "m_rune": {
                Vec3d at = aimGround(c, 16);
                rune(c, at, 0, dmg, r);
                snd(c, "minecraft:block.enchantment_table.use", 0.8F, 1.2F);
                break;
            }
            default:
                break;
        }
    }

    // ================================================================== HEAVY
    private static void heavy(final SkillCtx c, int i) {
        final EntityPlayerMP p = c.p;
        final double[] st = HS[i];
        final float dmg = c.dmg(st[0], false);
        final double r = Math.max(2.0, st[1]) * c.size;
        final double px = p.posX, py = p.posY, pz = p.posZ;
        final Vec3d f = new Vec3d(c.lx, 0, c.lz);
        switch (c.in.type) {
            case "twinblades":
                Fx.send(c.w, Fx.SPIN, c.el, px, py, pz, 0, 0, 0, r * 0.6, 0, 18, p.getEntityId());
                repeat(0, 3, 6, k -> {
                    SkillShapes.area(c, p.posX, p.posY, p.posZ, r, dmg, hit().push(0.15).noFx());
                    snd(c, "minecraft:entity.player.attack.sweep", 0.7F, 1.2F + k * 0.08F);
                });
                break;
            case "sickles": {
                for (EntityLivingBase t : SkillShapes.around(c.w, px, py, pz, r)) {
                    SkillShapes.pull(t, px, pz, 0.7);
                }
                fx(c, Fx.GATHER, c.el, px, py, pz, 0, 0, 0, r * 0.5, 2, 6);
                schedule(6, () -> {
                    Fx.send(c.w, Fx.SPIN, c.el, p.posX, p.posY, p.posZ, 0, 0, 0, 2.2, 0, 6, p.getEntityId());
                    for (EntityLivingBase t : SkillShapes.around(c.w, p.posX, p.posY, p.posZ, 3.0)) {
                        if (SkillShapes.damage(c, t, dmg, hit().noFx())) {
                            bleed(t, 80);
                        }
                    }
                    snd(c, "minecraft:entity.player.attack.sweep", 1.0F, 0.9F);
                });
                break;
            }
            case "claws": {
                double go = Math.max(1.0, SkillShapes.freeDistance(p, c.lx, c.lz, r));
                double v = SkillShapes.dashSpeed(p, go);
                SkillShapes.launch(p, c.lx * v, p.onGround ? 0.1 : p.motionY, c.lz * v);
                fx(c, Fx.DASH, c.el, px, py, pz, px + c.lx * go, py, pz + c.lz * go, 0, 0, 4);
                Set<Integer> once = SkillShapes.set();
                for (EntityLivingBase t : SkillShapes.lineTargets(c.w, new Vec3d(px, py + 1, pz), f, go + 1, 1.4)) {
                    if (once.add(t.getEntityId()) && SkillShapes.damage(c, t, dmg, hit())) {
                        bleed(t, 60);
                    }
                }
                snd(c, "minecraft:entity.player.attack.sweep", 1.0F, 1.8F);
                break;
            }
            case "nunchaku":
                p.getEntityData().setLong("ua_guard_until", c.w.getTotalWorldTime() + 60);
                Fx.send(c.w, Fx.SPIN, c.el, px, py, pz, 0, 0, 0, 1.6, 0, 60, p.getEntityId());
                repeat(0, 15, 4, k -> SkillShapes.area(c, p.posX, p.posY, p.posZ, r, dmg, hit().push(0.4).noFx()));
                snd(c, "minecraft:item.shield.block", 1.0F, 1.2F);
                break;
            case "cleaver":
                fx(c, Fx.DEBRIS, c.el, px, py, pz, 0, 0, 0, r * 0.4, 0, 8);
                fx(c, Fx.RING, c.el, px, py + 0.1, pz, 0, 0, 0, r, 0, 6);
                for (EntityLivingBase t : SkillShapes.around(c.w, px, py, pz, r)) {
                    if (SkillShapes.damage(c, t, dmg, hit().lift(st[3]).push(st[4]))) {
                        bleed(t, 100);
                    }
                }
                shake(c, 10, 0.8F, 6);
                snd(c, "minecraft:entity.generic.explode", 0.7F, 1.2F);
                break;
            case "greataxe":
                for (int k = 0; k < 2; k++) {
                    final int kk = k;
                    schedule(1 + k * 7, () -> {
                        Fx.send(c.w, Fx.SPIN, c.el, p.posX, p.posY, p.posZ, 0, 0, 0, r * 0.6, 0, 6, p.getEntityId());
                        SkillShapes.area(c, p.posX, p.posY, p.posZ, r, dmg * (kk == 0 ? 0.8F : 1.0F), hit().push(0.8).noFx());
                        snd(c, "minecraft:entity.player.attack.sweep", 1.0F, 0.6F);
                    });
                }
                break;
            case "handaxe":
                for (int a = -1; a <= 1; a++) {
                    toss(c, dir(c, a * 20).addVector(0, c.p.getLookVec().y, 0), 1.4 * c.speed, dmg, 2, 30, false, 0.35F);
                }
                snd(c, "minecraft:entity.snowball.throw", 1.0F, 0.7F);
                break;
            case "flail":
                Fx.send(c.w, Fx.SPIN, c.el, px, py, pz, 0, 0, 0, r * 0.6, 0, 10, p.getEntityId());
                schedule(10, () -> {
                    Fx.at(c.w, Fx.EXPLOSION, c.el, p.posX + c.lx * 2, p.posY, p.posZ + c.lz * 2, r * 0.6);
                    for (EntityLivingBase t : SkillShapes.around(c.w, p.posX, p.posY, p.posZ, r)) {
                        if (SkillShapes.damage(c, t, dmg, hit().push(0.6).noFx())) {
                            effect(t, MobEffects.WEAKNESS, 100, 1);
                        }
                    }
                    shake(c, 10, 0.8F, 6);
                    snd(c, "minecraft:entity.irongolem.attack", 1.0F, 0.7F);
                });
                break;
            case "fist_brawler":
                Martial.pose(p, Martial.POSE_UPPERCUT, 10);
                SkillShapes.cone(c, r, Math.toRadians(40), dmg, hit().lift(st[3]).push(st[4]).stagger());
                Fx.at(c.w, Fx.EXPLOSION, c.el, px + c.lx * 1.5, py + 1.6, pz + c.lz * 1.5, 1.0);
                shake(c, 8, 0.6F, 4);
                snd(c, "minecraft:entity.player.attack.crit", 1.0F, 0.8F);
                break;
            case "fist_ironleg":
                Martial.pose(p, Martial.POSE_SPIN, 14);
                Fx.send(c.w, Fx.SPIN, c.el, px, py, pz, 0, 0, 0, r * 0.6, 0, 12, p.getEntityId());
                repeat(0, 6, 2, k -> {
                    SkillShapes.area(c, p.posX, p.posY, p.posZ, r, dmg, hit().lift(st[3]).push(st[4]).noFx());
                    snd(c, "minecraft:entity.player.attack.sweep", 0.9F, 1.0F + k * 0.2F);
                });
                break;
            case "fist_tiger": {
                double go = Math.max(1.0, SkillShapes.freeDistance(p, c.lx, c.lz, r));
                double v = SkillShapes.dashSpeed(p, go);
                SkillShapes.launch(p, c.lx * v, 0.25, c.lz * v);
                fx(c, Fx.DASH, c.el, px, py, pz, px + c.lx * go, py, pz + c.lz * go, 0, 0, 4);
                Set<Integer> once = SkillShapes.set();
                for (EntityLivingBase t : SkillShapes.lineTargets(c.w, new Vec3d(px, py + 1, pz), f, go + 1, 1.5)) {
                    if (once.add(t.getEntityId()) && SkillShapes.damage(c, t, dmg, hit())) {
                        bleed(t, 100);
                    }
                }
                snd(c, "minecraft:entity.wolf.growl", 0.8F, 1.4F);
                break;
            }
            case "fist_stone":
                fx(c, Fx.RING, c.el, px, py + 0.1, pz, 0, 0, 0, r, 0, 6);
                fx(c, Fx.DEBRIS, c.el, px, py, pz, 0, 0, 0, r * 0.4, 0, 8);
                for (EntityLivingBase t : SkillShapes.around(c.w, px, py, pz, r)) {
                    if (SkillShapes.damage(c, t, dmg, hit().lift(st[3]).push(st[4]))) {
                        effect(t, MobEffects.SLOWNESS, 40, 4); // stunned
                    }
                }
                shake(c, 10, 1.0F, 6);
                snd(c, "minecraft:entity.generic.explode", 0.8F, 0.7F);
                break;
            case "fist_palm":
                p.getEntityData().setLong("ua_parry_until", c.w.getTotalWorldTime() + 30);
                p.getEntityData().setFloat("ua_parry_dmg", dmg);
                Fx.on(p, Fx.AURA, c.el, 0.9, 0, 30);
                snd(c, "minecraft:item.shield.block", 0.9F, 1.5F);
                break;
            case "fist_arcane":
                Fx.at(c.w, Fx.EXPLOSION, c.el, px + c.lx, py + 1, pz + c.lz, 1.4);
                SkillShapes.area(c, px + c.lx, py, pz + c.lz, r, dmg, hit().push(st[4]));
                snd(c, "minecraft:entity.generic.explode", 0.6F, 1.4F);
                break;
            case "gauntlets":
                SkillShapes.cone(c, r, Math.toRadians(45), dmg, hit().lift(st[3]).push(st[4]));
                Fx.at(c.w, Fx.EXPLOSION, c.el, px + c.lx * 1.8, py + 1.1, pz + c.lz * 1.8, 1.2);
                shake(c, 8, 0.8F, 5);
                snd(c, "minecraft:entity.irongolem.attack", 1.0F, 1.0F);
                break;
            case "scythe": {
                fx(c, Fx.CRESCENT, c.el, px, py + 0.9, pz, c.lx, 0, c.lz, r, 2.0, 10);
                int n = SkillShapes.area(c, px, py, pz, r, dmg, hit().push(0.4));
                if (n > 0) {
                    p.heal(Math.min(8, n * 1.0F));
                    Fx.on(p, Fx.HEAL, c.el, 1.0, 0, 8);
                }
                snd(c, "minecraft:entity.player.attack.sweep", 1.0F, 0.5F);
                break;
            }
            case "trident": {
                EntitySkillBolt b = toss(c, c.p.getLookVec(), 1.8 * c.speed, dmg * 0.5F, 0, 30, false, 0.45F);
                final float big = dmg;
                final double rr = r;
                b.onEnd = (bb, at) -> {
                    bb.world.addWeatherEffect(new EntityLightningBolt(bb.world, at.x, at.y, at.z, true));
                    Fx.at(bb.world, Fx.EXPLOSION, Fx.LIGHTNING_E, at.x, at.y, at.z, rr);
                    SkillShapes.area(bb.ctx, at.x, at.y, at.z, rr, big, hit().lift(0.3).from(at).noFx());
                };
                snd(c, "minecraft:entity.arrow.shoot", 1.0F, 0.5F);
                break;
            }
            case "whip": {
                Vec3d from = new Vec3d(px, py + 1.1, pz);
                fx(c, Fx.BEAM, c.el, from.x, from.y, from.z, from.x + c.lx * r, from.y, from.z + c.lz * r, 0.18, 0, 4);
                for (EntityLivingBase t : SkillShapes.lineTargets(c.w, from, f, r, 1.0)) {
                    if (SkillShapes.damage(c, t, dmg, hit())) {
                        SkillShapes.pull(t, px, pz, 1.2);
                        effect(t, MobEffects.SLOWNESS, 60, 2);
                    }
                }
                snd(c, "minecraft:entity.firework.blast", 0.9F, 1.4F);
                break;
            }
            case "repeater":
                for (int k = 0; k < 12; k++) {
                    Vec3d d = dir(c, -45 + k * (90.0 / 11));
                    Vec3d m = SkillMoves.muzzle(c);
                    SkillMoves.arrow(c, m.x, m.y, m.z, d.x * 2.6 * c.speed, c.p.getLookVec().y * 2.6, d.z * 2.6 * c.speed, dmg, false);
                }
                snd(c, "minecraft:entity.arrow.shoot", 1.0F, 1.0F);
                break;
            case "knives":
                for (int k = 0; k < 8; k++) {
                    toss(c, dir(c, k * 45), 1.5 * c.speed, dmg, 1, 20, false, 0.25F);
                }
                snd(c, "minecraft:entity.snowball.throw", 1.0F, 1.6F);
                break;
            case "chakram": {
                EntitySkillBolt b = toss(c, c.p.getLookVec(), 1.2 * c.speed, dmg, 999, 70, true, 0.5F);
                b.ghost = true;
                final double rr = r;
                b.onTick = bb -> {
                    if (bb.ticksExisted == 6) {
                        bb.motionX = bb.motionY = bb.motionZ = 0;
                        bb.velocityChanged = true;
                    }
                    if (bb.ticksExisted > 6 && bb.ticksExisted % 5 == 0) {
                        SkillShapes.area(bb.ctx, bb.posX, bb.posY - 1, bb.posZ, rr, bb.damage, hit().noFx());
                        Fx.at(bb.world, Fx.SLASH, bb.ctx.el, bb.posX, bb.posY, bb.posZ, rr * 0.6);
                    }
                };
                snd(c, "minecraft:entity.snowball.throw", 1.0F, 1.2F);
                break;
            }
            case "boomerang":
                for (int k = 0; k < 1; k++) {
                    EntitySkillBolt b = toss(c, f, 0.5, dmg, 999, 50, true, 0.45F);
                    orbit(b, r, Math.atan2(c.lz, c.lx), 50, 0.16);
                }
                snd(c, "minecraft:entity.snowball.throw", 1.0F, 0.9F);
                break;
            case "javelin": {
                EntitySkillBolt b = toss(c, c.p.getLookVec(), 2.0 * c.speed, dmg * 0.5F, 0, 30, false, 0.45F);
                final float big = dmg;
                final double rr = r;
                b.onEnd = (bb, at) -> boom(bb.ctx, at, rr, big, 0.3);
                snd(c, "minecraft:entity.arrow.shoot", 1.0F, 0.5F);
                break;
            }
            case "m_wand": {
                EntitySkillBolt b = mbolt(c, c.p.getLookVec(), 0.3F, 1.5 * c.speed, dmg, 1);
                b.onHit = (bb, t) -> {
                    SkillShapes.damage(bb.ctx, t, bb.damage, hit());
                    int chains = 2;
                    for (EntityLivingBase o : SkillShapes.around(bb.world, t.posX, t.posY, t.posZ, 6)) {
                        if (o == t || chains-- <= 0) {
                            continue;
                        }
                        Fx.send(bb.world, Fx.LIGHTNING, bb.ctx.el, t.posX, t.posY + t.height * 0.6, t.posZ, o.posX, o.posY + o.height * 0.6, o.posZ, 0.6, 0, 4, -1);
                        SkillShapes.damage(bb.ctx, o, bb.damage * 0.7F, hit().noFx());
                    }
                };
                snd(c, "minecraft:entity.blaze.shoot", 0.7F, 1.4F);
                break;
            }
            case "m_staff": {
                final Vec3d at = aimGround(c, 18);
                fx(c, Fx.PILLAR, c.el, at.x, at.y, at.z, 0, 0, 0, r, 1.0, 10);
                schedule(4, () -> {
                    SkillShapes.area(c, at.x, at.y, at.z, r, dmg, hit().lift(st[3]).from(at));
                    shake(c, 10, 0.6F, 5);
                });
                snd(c, "minecraft:entity.evocation_illager.cast_spell", 0.9F, 1.0F);
                break;
            }
            case "m_orb":
                for (int k = 0; k < 3; k++) {
                    EntitySkillBolt b = mbolt(c, f, 0.35F, 0.5, dmg, 0);
                    orbit(b, r, k * Math.PI * 2 / 3, 80, 0.18);
                }
                snd(c, "minecraft:entity.illusion_illager.cast_spell", 0.8F, 1.2F);
                break;
            case "m_scepter":
                fx(c, Fx.RING, Fx.HOLY, px, py + 0.1, pz, 0, 0, 0, r, 0, 10);
                for (EntityPlayer o : allies(c, r)) {
                    o.heal(6.0F);
                    effect(o, MobEffects.ABSORPTION, 160, 0);
                    effect(o, MobEffects.RESISTANCE, 120, 0);
                    Fx.on(o, Fx.HEAL, Fx.HOLY, 1.5, 0, 10);
                }
                snd(c, "minecraft:block.note.chime", 1.0F, 1.0F);
                break;
            case "m_rune": {
                Vec3d at = aimGround(c, 14);
                Vec3d s = new Vec3d(-c.lz, 0, c.lx);
                for (int k = -1; k <= 1; k++) {
                    rune(c, at.add(s.scale(k * 2.5)), k + 2, dmg, r);
                }
                snd(c, "minecraft:block.enchantment_table.use", 1.0F, 0.9F);
                break;
            }
            default:
                break;
        }
    }

    /** A ground rune that waits up to 10s and goes off when an enemy steps within 1.6 blocks. kind 0/2 blast, 1 slow, 3 root. */
    private static void rune(final SkillCtx c, final Vec3d at, final int kind, final float dmg, final double r) {
        Fx.send(c.w, Fx.RUNES, c.el, at.x, at.y + 0.05, at.z, 0, 0, 0, 1.4, 0, 200, -1);
        final boolean[] done = {false};
        repeat(10, 5, 40, k -> {
            if (done[0]) {
                return;
            }
            if (SkillShapes.around(c.w, at.x, at.y, at.z, 1.6).isEmpty()) {
                return;
            }
            done[0] = true;
            if (kind == 1) {
                Fx.at(c.w, Fx.NOVA, c.el, at.x, at.y + 0.5, at.z, r);
                for (EntityLivingBase t : SkillShapes.around(c.w, at.x, at.y, at.z, r)) {
                    SkillShapes.damage(c, t, dmg * 0.4F, hit().noFx());
                    effect(t, MobEffects.SLOWNESS, 80, 2);
                }
            } else if (kind == 3) {
                Fx.at(c.w, Fx.PILLAR, c.el, at.x, at.y, at.z, r * 0.6);
                for (EntityLivingBase t : SkillShapes.around(c.w, at.x, at.y, at.z, r)) {
                    SkillShapes.damage(c, t, dmg * 0.4F, hit().noFx());
                    effect(t, MobEffects.SLOWNESS, 50, 6);
                }
            } else {
                boom(c, at.addVector(0, 0.5, 0), r, dmg, 0.3);
            }
        });
    }

    // ================================================================== ART
    private static void art(final SkillCtx c, int i) {
        final EntityPlayerMP p = c.p;
        final double[] st = AS[i];
        final float per = c.dmg(st[0], st[0] < 10);
        final double R = st[1] * c.size;
        final double px = p.posX, py = p.posY, pz = p.posZ;
        switch (c.in.type) {
            case "twinblades": {
                final EntityLivingBase t = target(c, R);
                if (t == null) {
                    break;
                }
                double dx = p.posX - t.posX, dz = p.posZ - t.posZ, d = Math.max(0.1, Math.sqrt(dx * dx + dz * dz));
                p.setPositionAndUpdate(t.posX + dx / d * 1.4, t.posY, t.posZ + dz / d * 1.4);
                effect(p, MobEffects.RESISTANCE, 40, 2);
                repeat(2, 2, 8, k -> {
                    if (!t.isDead) {
                        SkillShapes.damage(c, t, per, hit().noFx());
                        Fx.at(c.w, Fx.XSLASH, c.el, t.posX, t.posY + t.height * 0.6, t.posZ, 1.0);
                        snd(c, "minecraft:entity.player.attack.sweep", 0.8F, 1.3F + k * 0.08F);
                    }
                });
                break;
            }
            case "sickles": {
                final EntityLivingBase t = target(c, R);
                if (t == null) {
                    break;
                }
                Vec3d to = new Vec3d(t.posX - px, 0, t.posZ - pz);
                double d = Math.max(0.1, to.lengthVector());
                SkillShapes.launch(p, to.x / d * Math.min(2.2, d / 4), 0.55, to.z / d * Math.min(2.2, d / 4));
                SkillMoves.onLanding(p, 4, 30, () -> repeat(0, 2, 6, k -> {
                    if (!t.isDead) {
                        SkillShapes.damage(c, t, per, hit().noFx());
                        bleed(t, 100);
                        Fx.at(c.w, Fx.SLASH, c.el, t.posX, t.posY + t.height * 0.5, t.posZ, 1.4);
                        snd(c, "minecraft:entity.player.attack.sweep", 0.8F, 1.5F);
                    }
                }));
                break;
            }
            case "claws":
                effect(p, MobEffects.HASTE, 100, 3);
                effect(p, MobEffects.STRENGTH, 100, 0);
                effect(p, MobEffects.SPEED, 100, 1);
                Fx.on(p, Fx.AURA, c.el, 0.9, 0, 100);
                SkillShapes.area(c, px, py, pz, R, per, hit().push(0.3));
                snd(c, "minecraft:entity.wolf.growl", 1.0F, 0.8F);
                break;
            case "nunchaku": {
                final EntityLivingBase t = target(c, R);
                if (t == null) {
                    break;
                }
                repeat(0, 2, 10, k -> {
                    if (!t.isDead) {
                        SkillShapes.damage(c, t, per, hit().noFx());
                        Fx.at(c.w, Fx.IMPACT, c.el, t.posX, t.posY + t.height * 0.6, t.posZ, 0.6);
                        snd(c, "minecraft:entity.player.attack.knockback", 0.7F, 1.0F + k * 0.08F);
                        if (k == 9) {
                            effect(t, MobEffects.SLOWNESS, 40, 6);
                            effect(t, MobEffects.WEAKNESS, 40, 2);
                        }
                    }
                });
                break;
            }
            case "cleaver": {
                final EntityLivingBase t = target(c, R);
                if (t == null) {
                    break;
                }
                float miss = 1.0F - t.getHealth() / Math.max(1.0F, t.getMaxHealth());
                SkillShapes.damage(c, t, per * (1.0F + 2.0F * miss), hit().push(0.5));
                Fx.at(c.w, Fx.EXPLOSION, Fx.BLOOD, t.posX, t.posY + t.height * 0.5, t.posZ, 1.5);
                fx(c, Fx.M_SWORD, Fx.BLOOD, t.posX, t.posY, t.posZ, c.lx, 0, c.lz, 4, 6, 20);
                shake(c, 10, 1.0F, 6);
                snd(c, "minecraft:entity.generic.explode", 0.8F, 0.8F);
                break;
            }
            case "greataxe": {
                final Vec3d from = new Vec3d(px, py, pz);
                final Vec3d f = new Vec3d(c.lx, 0, c.lz);
                Fx.send(c.w, Fx.M_CRACK, c.el, px, py, pz, c.lx, 0, c.lz, R, 0, 40, -1);
                fx(c, Fx.SPIKES, Fx.EARTH, px + c.lx, py, pz + c.lz, c.lx, 0, c.lz, 0, R, 8);
                final Set<Integer> once = SkillShapes.set();
                repeat(0, 2, 7, k -> {
                    double a = k * R / 7, b = (k + 1) * R / 7;
                    SkillShapes.line(c, from.add(f.scale(a)), f, b - a, 1.6, per, hit().lift(0.6), once);
                });
                shake(c, 14, 1.2F, 8);
                snd(c, "minecraft:entity.generic.explode", 1.0F, 0.6F);
                break;
            }
            case "handaxe":
                for (int k = 0; k < 3; k++) {
                    EntitySkillBolt b = toss(c, new Vec3d(c.lx, 0, c.lz), 0.5, per, 999, 80, false, 0.35F);
                    orbit(b, R, k * Math.PI * 2 / 3, 80, 0.25);
                }
                snd(c, "minecraft:entity.snowball.throw", 1.0F, 0.8F);
                break;
            case "flail": {
                EntitySkillBolt b = toss(c, c.p.getLookVec(), 1.6 * c.speed, per, 0, (int) (R / 1.6) + 4, false, 0.5F);
                b.onHit = (bb, t) -> {
                    SkillShapes.damage(bb.ctx, t, bb.damage, hit());
                    SkillShapes.pull(t, bb.ctx.p.posX, bb.ctx.p.posZ, 1.6);
                    effect(t, MobEffects.SLOWNESS, 40, 3);
                    Fx.send(bb.world, Fx.LIGHTNING, Fx.STEEL, bb.ctx.p.posX, bb.ctx.p.posY + 1.2, bb.ctx.p.posZ, t.posX, t.posY + 1, t.posZ, 0.4, 0, 6, -1);
                };
                snd(c, "minecraft:entity.irongolem.attack", 1.0F, 0.6F);
                break;
            }
            case "fist_brawler": {
                final EntityLivingBase t = target(c, R);
                if (t == null) {
                    break;
                }
                double dx = p.posX - t.posX, dz = p.posZ - t.posZ, d = Math.max(0.1, Math.sqrt(dx * dx + dz * dz));
                p.setPositionAndUpdate(t.posX + dx / d * 1.3, t.posY, t.posZ + dz / d * 1.3);
                repeat(2, 3, 6, k -> {
                    Martial.pose(p, k == 5 ? Martial.POSE_HOOK : k % 2 == 0 ? Martial.POSE_JAB_L : Martial.POSE_JAB_R, k == 5 ? 10 : 4);
                    if (!t.isDead) {
                        SkillShapes.damage(c, t, k == 5 ? per * 2 : per, k == 5 ? hit().push(2.2).lift(0.4) : hit().noFx());
                        Fx.at(c.w, Fx.IMPACT, c.el, t.posX, t.posY + t.height * 0.6, t.posZ, k == 5 ? 1.4 : 0.6);
                        snd(c, "minecraft:entity.player.attack.strong", 0.8F, 1.0F + k * 0.1F);
                    }
                });
                break;
            }
            case "fist_ironleg":
                Martial.pose(p, Martial.POSE_SKYDROP, 26);
                SkillShapes.launch(p, p.motionX, 1.15, p.motionZ);
                snd(c, "minecraft:entity.enderdragon.flap", 0.8F, 1.2F);
                schedule(16, () -> {
                    SkillShapes.launch(p, 0, -1.6, 0);
                    p.fallDistance = 0;
                });
                schedule(22, () -> {
                    p.fallDistance = 0;
                    fx(c, Fx.RING, c.el, p.posX, p.posY + 0.1, p.posZ, 0, 0, 0, R, 0, 8);
                    fx(c, Fx.DEBRIS, c.el, p.posX, p.posY, p.posZ, 0, 0, 0, R * 0.4, 0, 8);
                    SkillShapes.area(c, p.posX, p.posY, p.posZ, R, per, hit().lift(0.5).push(0.6));
                    shake(c, 14, 1.2F, 6);
                    snd(c, "minecraft:entity.generic.explode", 1.0F, 0.8F);
                });
                break;
            case "fist_tiger":
                effect(p, MobEffects.HASTE, 120, 3);
                effect(p, MobEffects.SPEED, 120, 1);
                p.getEntityData().setLong("ua_tiger_until", c.w.getTotalWorldTime() + 120);
                Fx.on(p, Fx.AURA, c.el, 0.9, 0, 120);
                for (EntityLivingBase t : SkillShapes.around(c.w, px, py, pz, R)) {
                    if (SkillShapes.damage(c, t, per, hit().push(0.3))) {
                        bleed(t, 80);
                    }
                }
                snd(c, "minecraft:entity.wolf.growl", 1.0F, 0.7F);
                break;
            case "fist_stone":
                effect(p, MobEffects.RESISTANCE, 120, 1);
                p.getEntityData().setLong("ua_stone_until", c.w.getTotalWorldTime() + 120);
                Fx.on(p, Fx.AURA, c.el, 1.0, 0, 120);
                fx(c, Fx.RING, c.el, px, py + 0.1, pz, 0, 0, 0, R, 0, 6);
                SkillShapes.area(c, px, py, pz, R, per, hit().lift(0.3).push(0.5));
                shake(c, 10, 0.8F, 5);
                snd(c, "minecraft:block.anvil.land", 0.7F, 0.6F);
                break;
            case "fist_palm": {
                final EntityLivingBase t = target(c, R);
                if (t == null) {
                    break;
                }
                repeat(0, 3, 12, k -> {
                    if (!t.isDead) {
                        SkillShapes.damage(c, t, per, k == 11 ? hit().push(2.5) : hit().noFx());
                        p.heal(0.5F);
                        Fx.at(c.w, Fx.IMPACT, c.el, t.posX, t.posY + t.height * 0.6, t.posZ, 0.5);
                        snd(c, "minecraft:entity.player.attack.weak", 0.7F, 1.2F + k * 0.04F);
                    }
                });
                break;
            }
            case "fist_arcane":
                p.getEntityData().setLong("ua_avatar_until", c.w.getTotalWorldTime() + 160);
                Fx.on(p, Fx.AURA, c.el, 1.0, 0, 160);
                Fx.at(c.w, Fx.NOVA, c.el, px, py + 1, pz, 1.6);
                SkillShapes.area(c, px, py, pz, R, per, hit().push(0.5));
                snd(c, "minecraft:entity.evocation_illager.cast_spell", 0.9F, 1.2F);
                break;
            case "gauntlets":
                repeat(0, 6, 3, k -> {
                    double rr = 3 + k * 2;
                    fx(c, Fx.RING, c.el, p.posX, p.posY + 0.1, p.posZ, 0, 0, 0, rr * c.size, 0, 6);
                    fx(c, Fx.DEBRIS, c.el, p.posX, p.posY, p.posZ, 0, 0, 0, rr * 0.4, 0, 6);
                    SkillShapes.area(c, p.posX, p.posY, p.posZ, rr * c.size, per, hit().lift(0.5).push(0.4).noFx());
                    shake(c, 12, 1.0F, 5);
                    snd(c, "minecraft:entity.generic.explode", 0.8F, 1.0F - k * 0.1F);
                });
                break;
            case "scythe": {
                EntitySkillBolt b = toss(c, c.p.getLookVec(), 0.9, per, 999, 60, true, 0.9F);
                returning(b, 14, 1.0);
                final double rr = 2.5 * c.size;
                java.util.function.Consumer<EntitySkillBolt> back = b.onTick;
                b.onTick = bb -> {
                    back.accept(bb);
                    if (bb.ticksExisted % 4 == 0) {
                        SkillShapes.area(bb.ctx, bb.posX, bb.posY - 1, bb.posZ, rr, bb.damage * 0.5F, hit().noFx());
                    }
                };
                snd(c, "minecraft:entity.player.attack.sweep", 1.0F, 0.4F);
                break;
            }
            case "trident":
                for (int k = -1; k <= 1; k++) {
                    final Vec3d d = dir(c, k * 40);
                    final Vec3d from = new Vec3d(px, py + 0.6, pz);
                    final int kk = k;
                    schedule(2 + (kk + 1) * 4, () -> {
                        fx(c, Fx.CRESCENT, Fx.ICE, from.x + d.x, from.y, from.z + d.z, d.x, 0, d.z, 3.0, 1.0, 10);
                        SkillShapes.line(c, from, d, R, 1.8, per, hit().push(1.2).lift(0.2), null);
                        snd(c, "minecraft:entity.generic.splash", 1.0F, 0.8F);
                    });
                }
                break;
            case "whip":
                for (int k = -2; k <= 2; k++) {
                    Vec3d d = dir(c, k * 30);
                    Vec3d from = new Vec3d(px, py + 1.1, pz);
                    fx(c, Fx.BEAM, c.el, from.x, from.y, from.z, from.x + d.x * R, from.y, from.z + d.z * R, 0.15, 0, 4);
                    for (EntityLivingBase t : SkillShapes.lineTargets(c.w, from, d, R, 0.9)) {
                        if (SkillShapes.damage(c, t, per, hit().noFx())) {
                            effect(t, MobEffects.SLOWNESS, 80, 2);
                        }
                    }
                }
                snd(c, "minecraft:entity.firework.blast", 1.0F, 1.2F);
                break;
            case "repeater": {
                final Vec3d at = aimGround(c, 28);
                fx(c, Fx.RUNES, c.el, at.x, at.y + 0.05, at.z, 0, 0, 0, R, 0, 40);
                repeat(6, 10, 3, k -> SkillMoves.rain(c, at.x, at.y, at.z, R, 20, per, false));
                break;
            }
            case "knives": {
                List<EntityLivingBase> list = aimTargets(c, R, 0.3);
                int n = 0;
                for (final EntityLivingBase t : list) {
                    if (n++ >= 5) {
                        break;
                    }
                    Fx.send(c.w, Fx.RUNES, Fx.SHADOW, t.posX, t.posY + 0.05, t.posZ, 0, 0, 0, 1.0, 0, 60, t.getEntityId());
                    schedule(60, () -> {
                        if (!t.isDead) {
                            Fx.at(c.w, Fx.EXPLOSION, Fx.SHADOW, t.posX, t.posY + t.height * 0.5, t.posZ, 1.6);
                            SkillShapes.damage(c, t, per, hit().push(0.3).noFx());
                            Fx.sound(c.w, t.posX, t.posY, t.posZ, "minecraft:entity.generic.explode", 0.6F, 1.6F);
                        }
                    });
                }
                snd(c, "minecraft:entity.evocation_illager.prepare_attack", 1.0F, 1.4F);
                break;
            }
            case "chakram":
                for (int k = 0; k < 3; k++) {
                    final EntitySkillBolt b = toss(c, new Vec3d(c.lx, 0, c.lz), 0.5, per, 999, 120, true, 0.4F);
                    orbit(b, R, k * Math.PI * 2 / 3, 120, 0.3);
                    final java.util.function.Consumer<EntitySkillBolt> spin = b.onTick;
                    b.onTick = bb -> {
                        if (bb.ticksExisted < 40) {
                            spin.accept(bb);
                            return;
                        }
                        if (bb.ticksExisted == 40) {
                            EntityLivingBase t = target(bb.ctx, 16);
                            Vec3d to = t == null ? bb.ctx.p.getLookVec()
                                    : new Vec3d(t.posX - bb.posX, t.posY + t.height * 0.5 - bb.posY, t.posZ - bb.posZ).normalize();
                            bb.motionX = to.x * 1.5;
                            bb.motionY = to.y * 1.5;
                            bb.motionZ = to.z * 1.5;
                            bb.homing = 0.5;
                            bb.target = t;
                            bb.clearHits();
                            bb.velocityChanged = true;
                        }
                    };
                }
                snd(c, "minecraft:entity.snowball.throw", 1.0F, 1.1F);
                break;
            case "boomerang": {
                EntitySkillBolt b = toss(c, c.p.getLookVec(), 1.4, per, 999, 80, true, 1.4F);
                returning(b, (int) (R / 1.4), 1.4);
                snd(c, "minecraft:entity.enderdragon.flap", 1.0F, 1.2F);
                break;
            }
            case "javelin": {
                final Vec3d at = aimGround(c, 26);
                fx(c, Fx.M_SPEAR, c.el, at.x, at.y + 14, at.z, 0, -1, 0, 4.0, 2.0, 10);
                fx(c, Fx.RUNES, c.el, at.x, at.y + 0.05, at.z, 0, 0, 0, R, 0, 12);
                schedule(10, () -> {
                    boom(c, at, R, per, 0.6);
                    SkillMoves.artBoom(c, at, 1.0F);
                });
                break;
            }
            case "m_wand":
                repeat(0, 3, 13, k -> {
                    EntityLivingBase t = target(c, R);
                    EntitySkillBolt b = mbolt(c, c.p.getLookVec(), 0.25F, 1.6, per, 1);
                    b.target = t;
                    b.homing = 0.3;
                    if (k % 3 == 0) {
                        snd(c, "minecraft:entity.blaze.shoot", 0.6F, 1.6F);
                    }
                });
                break;
            case "m_staff": {
                final Vec3d at = aimGround(c, 24);
                fx(c, Fx.METEOR, c.el, at.x, at.y, at.z, 0, 0, 0, R, 0, 20);
                schedule(20, () -> {
                    boom(c, at, R, per, 0.5);
                    SkillMoves.artBoom(c, at, 1.0F);
                });
                break;
            }
            case "m_orb": {
                final Vec3d at = aimGround(c, 20).addVector(0, 1.2, 0);
                Fx.send(c.w, Fx.M_VORTEX, c.el, at.x, at.y, at.z, 0, 0, 0, R * 0.5, 5, 60, -1);
                repeat(0, 5, 12, k -> {
                    for (EntityLivingBase t : SkillShapes.around(c.w, at.x, at.y, at.z, R)) {
                        SkillShapes.pull(t, at.x, at.z, 0.35);
                        SkillShapes.damage(c, t, per, hit().noFx());
                    }
                });
                schedule(62, () -> boom(c, at, R, per * 3, 0.5));
                snd(c, "minecraft:entity.endermen.teleport", 1.0F, 0.5F);
                break;
            }
            case "m_scepter":
                Fx.send(c.w, Fx.M_DOME, Fx.HOLY, px, py, pz, 0, 0, 0, R * 0.7, 0, 20, -1);
                for (EntityPlayer o : allies(c, R)) {
                    o.getEntityData().setLong("ua_bless_until", c.w.getTotalWorldTime() + 160);
                    effect(o, MobEffects.STRENGTH, 160, 0);
                    Fx.on(o, Fx.AURA, Fx.HOLY, 0.9, 0, 160);
                }
                snd(c, "minecraft:entity.player.levelup", 1.0F, 1.2F);
                break;
            case "m_rune": {
                final Vec3d at = aimGround(c, 18);
                fx(c, Fx.FIELD, c.el, at.x, at.y, at.z, 0, 0, 0, R * 0.8, 0, 100);
                fx(c, Fx.RUNES, c.el, at.x, at.y + 0.05, at.z, 0, 0, 0, R, 0, 100);
                repeat(0, 10, 10, k -> SkillShapes.area(c, at.x, at.y, at.z, R, per, hit().noFx()));
                break;
            }
            default:
                break;
        }
    }

    // ------------------------------------------------------------------ events
    /** Nunchaku guard (half damage, part struck back) and Scepter Blessing (+20% damage). */
    @SubscribeEvent
    public static void onHurt(LivingHurtEvent e) {
        EntityLivingBase t = e.getEntityLiving();
        if (t.world.isRemote) {
            return;
        }
        long now = t.world.getTotalWorldTime();
        if (t instanceof EntityPlayer && t.getEntityData().getLong("ua_guard_until") > now) {
            float a = e.getAmount();
            e.setAmount(a * 0.5F);
            Entity src = e.getSource().getTrueSource();
            if (src instanceof EntityLivingBase && src != t && e.getSource().getImmediateSource() == src) {
                src.attackEntityFrom(net.minecraft.util.DamageSource.causeThornsDamage(t), a * 0.4F);
            }
        }
        Entity src = e.getSource().getTrueSource();
        if (src instanceof EntityPlayer && src.getEntityData().getLong("ua_bless_until") > now) {
            e.setAmount(e.getAmount() * 1.2F);
        }
    }

}
