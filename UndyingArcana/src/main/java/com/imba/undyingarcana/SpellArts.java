package com.imba.undyingarcana;

import java.util.List;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.MobEffects;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

import static com.imba.undyingarcana.SkillServer.repeat;
import static com.imba.undyingarcana.SkillServer.schedule;

/**
 * Element mechanics of Grimoire spells and the 16 Arcane Arts.
 *
 * Elements (every spell hit / impact / cast of that element): Fire sets enemies on fire and may spread it; Ice slows;
 * Lightning bolts fly faster and may arc to a second enemy; Wind throws enemies back and gives the caster speed;
 * Earth gives the caster a stone skin (absorption) on impact; Shadow steals life; Light heals the caster and weakens
 * enemies; Poison poisons (stronger when already poisoned).
 *
 * Arcane Arts: unique spells learned from Arcane Scrolls (bosses, the Warlord, Blood Moons, the shop), two per
 * element, ranks I-III (a duplicate scroll raises the rank). Used in Spell Weave with the "Arcane Art" node.
 */
public final class SpellArts {
    public static final String[] ART = {"Meteor", "Phoenix Dash", "Blizzard", "Glacial Prison", "Thunderstorm", "Chain Storm",
            "Tornado", "Gale Burst", "Earthquake", "Stone Fortress", "Black Hole", "Shadow Clones", "Judgment", "Sanctuary",
            "Plague Cloud", "Venom Nova"};
    public static final String[] ART_DESC = {"A burning meteor falls where you aim.", "Dash through enemies in flames, healing per hit.",
            "A freezing storm on an area for 6s.", "Freezes every enemy in an area solid.", "Lightning strikes enemies around the aim point.",
            "Lightning jumps between up to 8 enemies.", "A moving tornado lifts and pulls enemies.", "A wind blast that throws everything away.",
            "Three shockwaves ripple out from you.", "Stone walls ring you; you and allies gain stone skin.",
            "Pulls everything in, then collapses.", "Three shadow spirits fight with you for 10s.", "A pillar of light smites the target.",
            "A holy zone heals allies and burns enemies for 6s.", "A drifting poison cloud.", "A ring of venom: poison and weakness."};
    /** Element index (Magic.ELEMENT) of each art. */
    public static final int[] ART_EL = {0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 7, 7};
    public static final int[] ART_COST = {60, 45, 55, 50, 60, 45, 55, 40, 55, 50, 70, 65, 55, 60, 50, 45};
    public static final int[] ART_CD = {400, 240, 400, 360, 400, 260, 360, 200, 360, 500, 480, 600, 360, 600, 360, 300};
    public static Item SCROLL;

    private SpellArts() {
    }

    // ------------------------------------------------------------------ element mechanics
    private static int elIndex(SkillCtx c) {
        for (int i = 0; i < Magic.ELEMENT.length; i++) {
            if (Magic.ELEMENT[i].equals(c.status)) {
                return i;
            }
        }
        return -1;
    }

    /** Projectile speed multiplier of an element (Lightning fast, Wind quick, Earth slow). */
    public static double speed(SkillCtx c) {
        switch (elIndex(c)) {
            case 2: return 1.5;
            case 3: return 1.25;
            case 4: return 0.85;
            default: return 1.0;
        }
    }

    /** After a spell hit landed. */
    public static void onHit(SkillCtx c, EntityLivingBase t, float dmg) {
        if (!c.magic) {
            return;
        }
        EntityPlayerMP p = c.p;
        switch (elIndex(c)) {
            case 0:
                t.setFire(3);
                if (p.getRNG().nextFloat() < 0.25F) {
                    for (EntityLivingBase o : SkillShapes.around(c.w, t.posX, t.posY, t.posZ, 3)) {
                        if (o != t && !o.isBurning()) {
                            o.setFire(2);
                            Fx.at(c.w, Fx.IMPACT, Fx.FIRE, o.posX, o.posY + 1, o.posZ, 0.5);
                            break;
                        }
                    }
                }
                break;
            case 1:
                t.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 40, 1));
                break;
            case 2:
                if (p.getRNG().nextFloat() < 0.3F) {
                    for (EntityLivingBase o : SkillShapes.around(c.w, t.posX, t.posY, t.posZ, 5)) {
                        if (o != t) {
                            Fx.send(c.w, Fx.LIGHTNING, Fx.LIGHTNING_E, t.posX, t.posY + 1, t.posZ, o.posX, o.posY + 1, o.posZ, 0.5, 0, 4, -1);
                            SkillShapes.damage(c, o, dmg * 0.4F, SkillShapes.hit());
                            break;
                        }
                    }
                }
                break;
            case 3: {
                double dx = t.posX - p.posX, dz = t.posZ - p.posZ, l = Math.max(0.1, Math.sqrt(dx * dx + dz * dz));
                t.motionX += dx / l * 0.35;
                t.motionZ += dz / l * 0.35;
                t.velocityChanged = true;
                break;
            }
            case 5:
                p.heal(dmg * 0.1F);
                break;
            case 6:
                p.heal(dmg * 0.05F);
                t.addPotionEffect(new PotionEffect(MobEffects.WEAKNESS, 60, 0));
                break;
            case 7: {
                PotionEffect cur = t.getActivePotionEffect(MobEffects.POISON);
                t.addPotionEffect(new PotionEffect(MobEffects.POISON, 80, cur != null ? 1 : 0));
                break;
            }
            default:
                break;
        }
    }

    /** After an impact (explosion / landing). */
    public static void onImpact(SkillCtx c, Vec3d at, double r) {
        if (!c.magic) {
            return;
        }
        if (elIndex(c) == 4) {
            long now = c.w.getTotalWorldTime();
            if (now - c.p.getEntityData().getLong("ua_stoneskin") > 200) {
                c.p.getEntityData().setLong("ua_stoneskin", now);
                c.p.addPotionEffect(new PotionEffect(MobEffects.ABSORPTION, 200, 0, false, true));
            }
        } else if (elIndex(c) == 1) {
            // a short frost patch that keeps slowing
            final Vec3d a = at;
            repeat(10, 10, 4, k -> {
                for (EntityLivingBase t : SkillShapes.around(c.w, a.x, a.y, a.z, r)) {
                    t.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 25, 1));
                }
            });
        }
    }

    /** When a spell of this element is cast. */
    public static void onCast(EntityPlayerMP p, int element) {
        if (element == 3) {
            p.addPotionEffect(new PotionEffect(MobEffects.SPEED, 60, 0, false, false));
        }
    }

    // ------------------------------------------------------------------ fusions
    /** Fused elements: a cast with these two elements becomes the fusion (name, colour mix and its own effect). */
    public static final String[] FUSION = {"Plasma", "Steam", "Magma", "Frostgale", "Glacial Shock", "Tempest", "Mire", "Blight",
            "Twilight"};
    public static final String[] FUSION_DESC = {"Fire + Lightning: every hit bursts in a small chain explosion.",
            "Fire + Ice: impacts leave a scalding cloud that blinds and burns.", "Fire + Earth: impacts leave molten ground for 3 s.",
            "Ice + Wind: hits slow and push everything around the target.", "Ice + Lightning: hits may freeze solid; frozen enemies take +50%.",
            "Wind + Lightning: bolts fly much faster and pierce 2 more; hits throw back.", "Earth + Poison: impacts leave a mire (slow, poison).",
            "Shadow + Poison: hits wither (Wither II).", "Shadow + Light: lifesteal, and allies near you heal 10% of the damage."};
    private static final int[][] FUSION_PAIR = {{0, 2}, {0, 1}, {0, 4}, {1, 3}, {1, 2}, {3, 2}, {4, 7}, {5, 7}, {5, 6}};

    /** Fusion of two elements (ids, any order), or -1. */
    public static int fusion(int a, int b) {
        for (int i = 0; i < FUSION_PAIR.length; i++) {
            if (FUSION_PAIR[i][0] == a && FUSION_PAIR[i][1] == b || FUSION_PAIR[i][0] == b && FUSION_PAIR[i][1] == a) {
                return i;
            }
        }
        return -1;
    }

    public static void fusionHit(SkillCtx c, EntityLivingBase t, float dmg) {
        if (c.fusion < 0) {
            return;
        }
        EntityPlayerMP p = c.p;
        switch (c.fusion) {
            case 0: // Plasma
                Fx.at(c.w, Fx.IMPACT, c.el, t.posX, t.posY + 1, t.posZ, 1.4);
                for (EntityLivingBase o : SkillShapes.around(c.w, t.posX, t.posY, t.posZ, 2.5)) {
                    if (o != t) {
                        SkillShapes.damage(c, o, dmg * 0.35F, SkillShapes.hit());
                    }
                }
                break;
            case 3: // Frostgale
                for (EntityLivingBase o : SkillShapes.around(c.w, t.posX, t.posY, t.posZ, 3)) {
                    o.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 50, 2));
                    SkillShapes.pull(o, t.posX, t.posZ, -0.4);
                }
                break;
            case 4: { // Glacial Shock
                long now = c.w.getTotalWorldTime();
                if (t.getEntityData().getLong("za_st_frozen") > now) {
                    SkillShapes.damage(c, t, dmg * 0.5F, SkillShapes.hit());
                    Fx.at(c.w, Fx.IMPACT, Fx.ICE, t.posX, t.posY + 1, t.posZ, 1.0);
                } else if (p.getRNG().nextFloat() < 0.25F) {
                    t.getEntityData().setLong("za_st_frozen", now + 40);
                    Fx.on(t, Fx.AURA, Fx.ICE, 1.0, 0, 40);
                }
                break;
            }
            case 5: { // Tempest
                double dx = t.posX - p.posX, dz = t.posZ - p.posZ, l = Math.max(0.1, Math.sqrt(dx * dx + dz * dz));
                t.motionX += dx / l * 0.5;
                t.motionZ += dz / l * 0.5;
                t.velocityChanged = true;
                break;
            }
            case 7: // Blight
                t.addPotionEffect(new PotionEffect(MobEffects.WITHER, 80, 1));
                break;
            case 8: // Twilight
                p.heal(dmg * 0.1F);
                for (EntityPlayer o : c.w.getEntitiesWithinAABB(EntityPlayer.class, p.getEntityBoundingBox().grow(8, 3, 8))) {
                    if (o != p) {
                        o.heal(dmg * 0.1F);
                    }
                }
                break;
            default:
                break;
        }
    }

    public static void fusionImpact(final SkillCtx c, final Vec3d at, final double r) {
        if (c.fusion != 1 && c.fusion != 2 && c.fusion != 6) {
            return;
        }
        final float d = c.dmg(1.2, true);
        final int kind = c.fusion;
        Fx.send(c.w, Fx.FIELD, c.el, at.x, at.y, at.z, 0, 0, 0, r, 0, 60, -1);
        repeat(10, 10, 6, k -> {
            for (EntityLivingBase t : SkillShapes.around(c.w, at.x, at.y, at.z, r)) {
                if (kind == 1) {
                    t.addPotionEffect(new PotionEffect(MobEffects.BLINDNESS, 30, 0));
                    SkillShapes.damage(c, t, d, SkillShapes.hit());
                } else if (kind == 2) {
                    t.setFire(2);
                    SkillShapes.damage(c, t, d * 1.2F, SkillShapes.hit());
                } else {
                    t.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 30, 2));
                    t.addPotionEffect(new PotionEffect(MobEffects.POISON, 40, 0));
                }
            }
        });
    }

    public static final String[] CAST_SOUND = {"minecraft:item.firecharge.use", "minecraft:block.glass.break", "minecraft:entity.lightning.impact",
            "minecraft:entity.enderdragon.flap", "minecraft:block.gravel.break", "minecraft:entity.endermen.teleport", "minecraft:block.note.chime",
            "minecraft:entity.witch.drink"};

    // ------------------------------------------------------------------ arcane arts
    /** Rank of an art for this player (0 = not learned). */
    public static int rank(EntityPlayer p, int art) {
        return art < 0 || art >= ART.length ? 0 : Magic.magicData(p).getInteger("art" + art);
    }

    public static int[] ranks(EntityPlayer p) {
        int[] r = new int[ART.length];
        for (int i = 0; i < r.length; i++) {
            r[i] = rank(p, i);
        }
        return r;
    }

    static void learn(EntityPlayerMP p, int art) {
        NBTTagCompound mg = Magic.magicData(p);
        int r = mg.getInteger("art" + art);
        if (r >= 3) {
            p.sendMessage(new TextComponentString("\u00a77" + ART[art] + " is already rank III."));
            return;
        }
        mg.setInteger("art" + art, r + 1);
        String[] rn = {"I", "II", "III"};
        p.sendMessage(new TextComponentString("\u00a7dArcane Art: \u00a7f" + ART[art] + " " + rn[r] + "\u00a7d. Use it in the Spell Weave (Arcane Art node)."));
        Fx.at(p.world, Fx.TIERUP, Magic.ELEMENT_FX[ART_EL[art]], p.posX, p.posY + 1, p.posZ, 1.4);
        Fx.sound(p, "minecraft:ui.toast.challenge_complete", 0.8F, 1.2F);
        Magic.sendWeave(p);
    }

    /** Runs an art from 'from' (null = the player) toward 'aim'. Returns false when not learned. */
    public static boolean cast(final EntityPlayerMP p, int art, int slot, float mult, Vec3d aim, final SpellVM.Hooks hooks) {
        int rank = rank(p, art);
        if (rank <= 0) {
            p.sendStatusMessage(new TextComponentString("\u00a77You have not learned " + ART[Math.max(0, art)] + " (Arcane Scroll)."), true);
            return false;
        }
        Magic.Spell s = new Magic.Spell();
        s.shape = 0;
        s.which = 0;
        final SkillCtx c = Magic.prepare(p, slot, s);
        if (c == null) {
            return false;
        }
        int el = ART_EL[art];
        c.element = Magic.ELEMENT[el];
        c.status = Magic.ELEMENT[el];
        c.el = Magic.ELEMENT_FX[el];
        c.name = ART[art];
        c.dmgMult *= mult * (1F + 0.3F * (rank - 1));
        final float R = 1F + 0.15F * (rank - 1);
        final Vec3d at = aim != null ? aim : SkillShapes.aim(p, 24);
        final Vec3d ground = new Vec3d(at.x, SkillShapes.ground(c.w, at.x, at.y + 0.5, at.z), at.z);
        final Vec3d me = new Vec3d(p.posX, p.posY, p.posZ);
        switch (art) {
            case 0: { // Meteor
                Fx.send(c.w, Fx.METEOR, c.el, ground.x, ground.y, ground.z, 0, 0, 0, 3 * R, 0, 20, -1);
                schedule(18, () -> {
                    boom(c, ground, 4 * R, c.dmg(16, false), 1.2, hooks);
                    Fx.shake(c.w, ground.x, ground.y, ground.z, 16, 0.6F, 10);
                    zone(c, ground, 3 * R, 6, 0.8F, hooks);
                });
                break;
            }
            case 1: { // Phoenix Dash
                Vec3d look = p.getLookVec();
                SkillShapes.blink(p, look.x, look.z, 10 * R);
                Fx.send(c.w, Fx.DASH, c.el, me.x, me.y, me.z, p.posX, p.posY, p.posZ, 0, 0, 6, -1);
                Vec3d dir = new Vec3d(p.posX - me.x, 0, p.posZ - me.z);
                double len = Math.max(1, dir.lengthVector());
                int hit = 0;
                for (EntityLivingBase t : SkillShapes.lineTargets(c.w, me.addVector(0, 1, 0), dir.normalize(), len, 1.6)) {
                    if (hitOne(c, t, c.dmg(9, false), me, hooks)) {
                        hit++;
                    }
                }
                p.heal(4 * hit * R);
                break;
            }
            case 2: { // Blizzard
                Fx.send(c.w, Fx.FIELD, c.el, ground.x, ground.y, ground.z, 0, 0, 0, 5 * R, 0, 120, -1);
                final float d = c.dmg(2.2, true);
                repeat(0, 10, 12, k -> {
                    Fx.at(c.w, Fx.BARRAGE, c.el, ground.x, ground.y + 6, ground.z, 4 * R);
                    for (EntityLivingBase t : SkillShapes.around(c.w, ground.x, ground.y, ground.z, 5 * R)) {
                        t.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 30, 2));
                        hitOne(c, t, d, ground, hooks);
                    }
                });
                break;
            }
            case 3: { // Glacial Prison
                Fx.at(c.w, Fx.NOVA, c.el, ground.x, ground.y + 0.5, ground.z, 4 * R);
                for (EntityLivingBase t : SkillShapes.around(c.w, ground.x, ground.y, ground.z, 4 * R)) {
                    t.getEntityData().setLong("za_st_frozen", c.w.getTotalWorldTime() + (long) (60 * R));
                    t.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, (int) (60 * R), 6));
                    Fx.on(t, Fx.AURA, c.el, 1.2, 0, (int) (60 * R));
                    hitOne(c, t, c.dmg(5, false), ground, hooks);
                }
                break;
            }
            case 4: { // Thunderstorm
                final float d = c.dmg(9, false);
                repeat(0, 8, (int) (6 * R), k -> {
                    List<EntityLivingBase> l = SkillShapes.around(c.w, ground.x, ground.y, ground.z, 7 * R);
                    Vec3d strike = l.isEmpty() ? ground.addVector((p.getRNG().nextDouble() - 0.5) * 8, 0, (p.getRNG().nextDouble() - 0.5) * 8)
                            : SpellVM.center(l.get(p.getRNG().nextInt(l.size())));
                    Fx.send(c.w, Fx.LIGHTNING, c.el, strike.x, strike.y + 12, strike.z, strike.x, strike.y, strike.z, 1.0, 0, 5, -1);
                    Fx.sound(c.w, strike.x, strike.y, strike.z, "minecraft:entity.lightning.thunder", 0.6F, 1.4F);
                    boom(c, strike, 2.2, d, 0.3, hooks);
                });
                break;
            }
            case 5: { // Chain Storm
                java.util.Set<Integer> done = SkillShapes.set();
                List<EntityLivingBase> first = SkillMoves.aimTargets(c, 20, 0.8);
                EntityLivingBase cur = first.isEmpty() ? null : first.get(0);
                Vec3d from = new Vec3d(p.posX, p.posY + 1.5, p.posZ);
                float d = c.dmg(8, false);
                for (int j = 0; j < 8 * R && cur != null; j++) {
                    done.add(cur.getEntityId());
                    Vec3d to = SpellVM.center(cur);
                    Fx.send(c.w, Fx.LIGHTNING, c.el, from.x, from.y, from.z, to.x, to.y, to.z, 0.8, 0, 5, -1);
                    hitOne(c, cur, d, from, hooks);
                    from = to;
                    d *= 0.9F;
                    EntityLivingBase next = null;
                    double nd = 81;
                    for (EntityLivingBase o : SkillShapes.around(c.w, cur.posX, cur.posY, cur.posZ, 9)) {
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
            case 6: { // Tornado
                Vec3d look = p.getLookVec();
                final Vec3d dir = new Vec3d(look.x, 0, look.z).normalize();
                final float d = c.dmg(2.5, true);
                repeat(0, 5, (int) (24 * R), k -> {
                    Vec3d pos = me.add(dir.scale(2 + k * 0.5)).addVector(0, 0.2, 0);
                    if (k % 4 == 0) {
                        Fx.send(c.w, Fx.M_VORTEX, c.el, pos.x, pos.y, pos.z, 0, 0, 0, 1.5 * R, 5, 24, -1);
                    }
                    for (EntityLivingBase t : SkillShapes.around(c.w, pos.x, pos.y, pos.z, 3 * R)) {
                        SkillShapes.pull(t, pos.x, pos.z, 0.25);
                        t.motionY = Math.max(t.motionY, 0.25);
                        t.velocityChanged = true;
                        hitOne(c, t, d, pos, hooks);
                    }
                });
                break;
            }
            case 7: { // Gale Burst
                Fx.at(c.w, Fx.NOVA, c.el, me.x, me.y + 1, me.z, 7 * R);
                for (EntityLivingBase t : SkillShapes.around(c.w, me.x, me.y, me.z, 7 * R)) {
                    double dx = t.posX - me.x, dz = t.posZ - me.z, l = Math.max(0.1, Math.sqrt(dx * dx + dz * dz));
                    t.motionX = dx / l * 1.6;
                    t.motionZ = dz / l * 1.6;
                    t.motionY = 0.5;
                    t.velocityChanged = true;
                    hitOne(c, t, c.dmg(6, false), me, hooks);
                }
                break;
            }
            case 8: { // Earthquake
                final float d = c.dmg(7, false);
                repeat(0, 12, 3, k -> {
                    double r = 4 + k * 3 * R;
                    Fx.at(c.w, Fx.RING, Fx.EARTH, p.posX, p.posY + 0.1, p.posZ, r);
                    Fx.shake(c.w, p.posX, p.posY, p.posZ, 14, 0.4F, 6);
                    for (EntityLivingBase t : SkillShapes.around(c.w, p.posX, p.posY, p.posZ, r)) {
                        if (t.getDistance(p) > r - 3.5) {
                            t.motionY = 0.55;
                            t.velocityChanged = true;
                            hitOne(c, t, d, new Vec3d(p.posX, p.posY, p.posZ), hooks);
                        }
                    }
                });
                break;
            }
            case 9: { // Stone Fortress
                for (int k = 0; k < 4; k++) {
                    double a = k * Math.PI / 2;
                    Fx.send(c.w, Fx.M_WALL, Fx.EARTH, me.x + Math.cos(a) * 4, me.y, me.z + Math.sin(a) * 4, -Math.sin(a), 0, Math.cos(a),
                            6, 0, 160, -1);
                }
                for (EntityPlayer o : c.w.getEntitiesWithinAABB(EntityPlayer.class, p.getEntityBoundingBox().grow(6, 3, 6))) {
                    o.addPotionEffect(new PotionEffect(MobEffects.ABSORPTION, 300, (int) (1 + R), false, true));
                    o.addPotionEffect(new PotionEffect(MobEffects.RESISTANCE, 160, 0, false, true));
                }
                final float d = c.dmg(2, true);
                repeat(0, 10, 16, k -> {
                    for (EntityLivingBase t : SkillShapes.around(c.w, me.x, me.y, me.z, 5)) {
                        if (t.getDistance(me.x, t.posY, me.z) > 3) {
                            SkillShapes.pull(t, me.x, me.z, -0.6);
                            hitOne(c, t, d, me, hooks);
                        }
                    }
                });
                break;
            }
            case 10: { // Black Hole
                Fx.send(c.w, Fx.M_VORTEX, c.el, ground.x, ground.y + 1.2, ground.z, 0, 0, 0, 2.5 * R, 8, 60, -1);
                repeat(0, 5, 12, k -> {
                    for (EntityLivingBase t : SkillShapes.around(c.w, ground.x, ground.y, ground.z, 9 * R)) {
                        SkillShapes.pull(t, ground.x, ground.z, 0.45);
                    }
                });
                schedule(62, () -> {
                    boom(c, ground.addVector(0, 1, 0), 5 * R, c.dmg(14, false), 0.2, hooks);
                    Fx.shake(c.w, ground.x, ground.y, ground.z, 14, 0.5F, 8);
                });
                break;
            }
            case 11: { // Shadow Clones: three summons
                Magic.Spell sm = new Magic.Spell();
                sm.shape = 12;
                sm.which = 0;
                sm.pw = (int) (3 * R);
                sm.sz = (int) (4 * R);
                for (int k = 0; k < 3; k++) {
                    SpellForge.castShape(c, sm, "", null, null, hooks);
                }
                break;
            }
            case 12: { // Judgment
                List<EntityLivingBase> l = SkillMoves.aimTargets(c, 24, 1.0);
                Vec3d spot = l.isEmpty() ? ground : SpellVM.center(l.get(0));
                Fx.send(c.w, Fx.PILLAR, Fx.HOLY, spot.x, spot.y - 1, spot.z, 0, 0, 0, 2.2 * R, 1.0, 14, -1);
                final Vec3d sp2 = spot;
                schedule(8, () -> {
                    boom(c, sp2, 2.5 * R, c.dmg(18, false), 0.3, hooks);
                    for (EntityPlayer o : c.w.getEntitiesWithinAABB(EntityPlayer.class, p.getEntityBoundingBox().grow(8, 4, 8))) {
                        o.heal(4 * R);
                        Fx.on(o, Fx.HEAL, Fx.HOLY, 1.0, 0, 10);
                    }
                });
                break;
            }
            case 13: { // Sanctuary
                Fx.send(c.w, Fx.FIELD, Fx.HOLY, me.x, me.y, me.z, 0, 0, 0, 5 * R, 0, 120, -1);
                final float d = c.dmg(2, true);
                repeat(0, 20, 6, k -> {
                    for (EntityPlayer o : c.w.getEntitiesWithinAABB(EntityPlayer.class, new net.minecraft.util.math.AxisAlignedBB(me.x - 5 * R,
                            me.y - 2, me.z - 5 * R, me.x + 5 * R, me.y + 3, me.z + 5 * R))) {
                        o.heal(1.5F * R);
                    }
                    for (EntityLivingBase t : SkillShapes.around(c.w, me.x, me.y, me.z, 5 * R)) {
                        hitOne(c, t, t.isEntityUndead() ? d * 2 : d, me, hooks);
                    }
                });
                break;
            }
            case 14: { // Plague Cloud
                Vec3d look = p.getLookVec();
                final Vec3d dir = new Vec3d(look.x, 0, look.z).normalize();
                repeat(0, 10, (int) (10 * R), k -> {
                    Vec3d pos = ground.add(dir.scale(k * 0.4));
                    Fx.at(c.w, Fx.FIELD, Fx.POISON, pos.x, pos.y, pos.z, 3 * R);
                    for (EntityLivingBase t : SkillShapes.around(c.w, pos.x, pos.y, pos.z, 3.5 * R)) {
                        t.addPotionEffect(new PotionEffect(MobEffects.POISON, 60, 1));
                        hitOne(c, t, c.dmg(1.2, true), pos, hooks);
                    }
                });
                break;
            }
            default: { // Venom Nova
                Fx.at(c.w, Fx.NOVA, Fx.POISON, me.x, me.y + 0.5, me.z, 6 * R);
                for (EntityLivingBase t : SkillShapes.around(c.w, me.x, me.y, me.z, 6 * R)) {
                    t.addPotionEffect(new PotionEffect(MobEffects.POISON, (int) (120 * R), 1));
                    t.addPotionEffect(new PotionEffect(MobEffects.WEAKNESS, (int) (120 * R), 0));
                    hitOne(c, t, c.dmg(5, false), me, hooks);
                }
                break;
            }
        }
        Fx.sound(p, CAST_SOUND[el], 0.9F, 0.9F);
        return true;
    }

    private static boolean hitOne(SkillCtx c, EntityLivingBase t, float dmg, Vec3d from, SpellVM.Hooks hooks) {
        SkillShapes.Hit h = SkillShapes.hit();
        h.from = from;
        if (!SkillShapes.damage(c, t, dmg, h)) {
            return false;
        }
        onHit(c, t, dmg);
        if (hooks != null) {
            hooks.onHit(t, SpellVM.center(t));
        }
        return true;
    }

    private static void boom(SkillCtx c, Vec3d at, double r, float dmg, double push, SpellVM.Hooks hooks) {
        Fx.at(c.w, Fx.EXPLOSION, c.el, at.x, at.y, at.z, r);
        for (EntityLivingBase t : SkillShapes.around(c.w, at.x, at.y, at.z, r)) {
            SkillShapes.Hit h = SkillShapes.hit();
            h.from = at;
            h.push = push;
            if (SkillShapes.damage(c, t, dmg, h)) {
                onHit(c, t, dmg);
                if (hooks != null) {
                    hooks.onHit(t, SpellVM.center(t));
                }
            }
        }
        if (hooks != null) {
            hooks.onEnd(at);
        }
    }

    private static void zone(SkillCtx c, Vec3d at, double r, int halfSeconds, float dmgBase, SpellVM.Hooks hooks) {
        Fx.send(c.w, Fx.FIELD, c.el, at.x, at.y, at.z, 0, 0, 0, r, 0, halfSeconds * 10, -1);
        final float d = c.dmg(dmgBase, true);
        repeat(10, 10, halfSeconds, k -> {
            for (EntityLivingBase t : SkillShapes.around(c.w, at.x, at.y, at.z, r)) {
                hitOne(c, t, d, at, null);
            }
        });
    }

    // ------------------------------------------------------------------ scroll item
    public static ItemStack scroll(int art) {
        ItemStack s = new ItemStack(SCROLL);
        NBTTagCompound t = new NBTTagCompound();
        t.setInteger("art", Math.max(0, Math.min(ART.length - 1, art)));
        s.setTagCompound(t);
        return s;
    }

    public static ItemStack randomScroll(java.util.Random r) {
        return scroll(r.nextInt(ART.length));
    }

    public static class ItemArcaneScroll extends Item {
        public ItemArcaneScroll() {
            setMaxStackSize(16);
        }

        static int art(ItemStack s) {
            return s.hasTagCompound() ? Math.max(0, Math.min(ART.length - 1, s.getTagCompound().getInteger("art"))) : 0;
        }

        @Override
        public ActionResult<ItemStack> onItemRightClick(World w, EntityPlayer p, EnumHand hand) {
            ItemStack s = p.getHeldItem(hand);
            if (!w.isRemote && p instanceof EntityPlayerMP) {
                if (!Stats.on(w)) {
                    return new ActionResult<>(EnumActionResult.PASS, s);
                }
                int a = art(s);
                if (rank(p, a) >= 3) {
                    p.sendMessage(new TextComponentString("\u00a77" + ART[a] + " is already rank III."));
                    return new ActionResult<>(EnumActionResult.FAIL, s);
                }
                learn((EntityPlayerMP) p, a);
                if (!p.capabilities.isCreativeMode) {
                    s.shrink(1);
                }
            }
            return new ActionResult<>(EnumActionResult.SUCCESS, s);
        }

        @Override
        public String getItemStackDisplayName(ItemStack s) {
            return "Arcane Scroll: " + ART[art(s)];
        }

        @Override
        public boolean hasEffect(ItemStack s) {
            return true;
        }

        @Override
        @net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT)
        public void addInformation(ItemStack s, World w, List<String> tip, net.minecraft.client.util.ITooltipFlag flag) {
            int a = art(s);
            tip.add("\u00a7" + "d" + Magic.ELEMENT_NAME[ART_EL[a]] + " Arcane Art");
            tip.add("\u00a77" + ART_DESC[a]);
            tip.add("\u00a78" + ART_COST[a] + " mana, " + ART_CD[a] / 20 + "s cooldown. Right-click to learn (a copy raises the rank).");
        }

        @Override
        public void getSubItems(net.minecraft.creativetab.CreativeTabs tab, net.minecraft.util.NonNullList<ItemStack> items) {
            if (isInCreativeTab(tab)) {
                for (int i = 0; i < ART.length; i++) {
                    items.add(scroll(i));
                }
            }
        }
    }
}
