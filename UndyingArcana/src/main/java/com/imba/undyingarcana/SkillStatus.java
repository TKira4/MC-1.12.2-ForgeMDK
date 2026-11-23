package com.imba.undyingarcana;

import java.util.List;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.MobEffects;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;

/**
 * Element statuses put on enemies by elemental skills (wands, imbued weapons, elemental legends), and the combos
 * that trigger when two elements meet on the same enemy - from any player:
 *   Fire  -> Charred: +15% skill damage taken, burning.          Ice -> Chill (+5% per stack); 3 Chill = Frozen (+15%, 1.5s).
 *   Lightning -> Shock: +10%, the next skill hit arcs.           Necromancy -> Drain: +10%, skill hits on it heal 20%.
 *   Earth -> Stagger: +15% (and the team combo mark).  Poison -> Poison II.  Sorcery / Shadow / Magic -> Weakness, +10%.
 *   Combos: Fire + Ice (either order) = Steam burst (blinds), Lightning or Earth on Frozen = Shatter (x1.5),
 *           Lightning on Charred = Overload explosion. A plain Heavy hit on Frozen also Shatters.
 *           Wind on Charred = Firestorm (fire spreads), Wind on poisoned = Miasma, Poison on Charred = Toxic Burst,
 *           Light on Exposed / Shadow on Radiant = Eclipse (armor-ignoring burst). Light marks Radiant (+10%).
 * Statuses live in the mob's ForgeData as expiry ticks.
 */
public final class SkillStatus {
    private static boolean chaining;

    private SkillStatus() {
    }

    /** How long an element status stays on an enemy (ticks). */
    public static final int DUR = 200;

    /** Burning counts as Charred for reactions (lava, fire aspect, burning ground...). */
    private static boolean charred(NBTTagCompound nd, EntityLivingBase t, long now) {
        return nd.getLong("za_st_char") > now || t.isBurning();
    }

    /** In water or rain. */
    private static boolean wet(EntityLivingBase t) {
        return t.isWet();
    }

    private static NBTTagCompound d(EntityLivingBase t) {
        return t.getEntityData();
    }

    private static boolean has(EntityLivingBase t, String key, long now) {
        return d(t).getLong(key) > now;
    }

    /** Spell Weave "Has Status": SpellGraph.STATUS order. */
    public static boolean is(EntityLivingBase t, int which) {
        long now = t.world.getTotalWorldTime();
        NBTTagCompound nd = d(t);
        switch (which) {
            case 0: return charred(nd, t, now);
            case 1: return nd.getLong("za_st_chillT") > now && nd.getInteger("za_st_chill") > 0;
            case 2: return nd.getLong("za_st_frozen") > now;
            case 3: return nd.getLong("za_st_shock") > now;
            case 4: return wet(t);
            case 5: return nd.getLong("za_st_drain") > now;
            case 6: return nd.getLong("za_st_stagger") > now;
            case 7: return nd.getLong("za_st_expose") > now;
            case 8: return nd.getLong("za_st_radiant") > now;
            default: return t.isPotionActive(net.minecraft.init.MobEffects.POISON);
        }
    }

    public static float damageMult(SkillCtx c, EntityLivingBase t) {
        float m = 1.0F;
        long now = c.w.getTotalWorldTime();
        if (has(t, "za_st_char", now)) {
            m *= 1.15F;
        }
        // Every element's status amplifies skill damage, so no element is strictly better than fire's Charred.
        if (has(t, "za_st_frozen", now)) {
            m *= 1.15F;
        } else if (has(t, "za_st_chillT", now)) {
            m *= 1.0F + 0.05F * Math.min(2, d(t).getInteger("za_st_chill"));
        }
        if (has(t, "za_st_shock", now)) {
            m *= 1.10F;
        }
        if (has(t, "za_st_drain", now)) {
            m *= 1.10F;
        }
        if (has(t, "za_st_stagger", now)) {
            m *= 1.15F;
        }
        if (has(t, "za_st_expose", now)) {
            m *= 1.10F;
        }
        if (has(t, "za_st_radiant", now)) {
            m *= 1.10F;
        }
        // Shatter: lightning or a heavy blow on a frozen enemy
        if (has(t, "za_st_frozen", now) && ("lightning".equals(c.status) || "earth".equals(c.status) || (c.slot == 1 && c.status.isEmpty()))) {
            m *= 1.5F;
            d(t).setLong("za_st_frozen", 0);
            t.removePotionEffect(MobEffects.SLOWNESS);
            Fx.at(c.w, Fx.NOVA, Fx.ICE, t.posX, t.posY + t.height * 0.5, t.posZ, 2.5);
            Fx.sound(t, "minecraft:block.glass.break", 1.2F, 0.7F);
            banner(c, "\u00a7bShatter!");
        }
        return m;
    }

    /** Status, combos, drain and shock chaining after a successful skill hit. */
    public static void afterHit(SkillCtx c, EntityLivingBase t, float amount, boolean noStatus) {
        long now = c.w.getTotalWorldTime();
        NBTTagCompound nd = d(t);
        // Sunder (physical) / Hex (magic): stacking armor and magic-resist shred, see Pierce.
        if (c.magic) {
            Pierce.addHex(t, now, 1);
        } else {
            Pierce.addSunder(t, now, "steel".equals(c.element) ? 2 : 1);
        }
        // Drain: hits on a drained enemy heal the attacker.
        if (nd.getLong("za_st_drain") > now) {
            c.p.heal(amount * 0.2F);
        }
        // Shock: the next skill hit arcs to 2 more enemies for 50%.
        if (nd.getLong("za_st_shock") > now && !chaining) {
            nd.setLong("za_st_shock", 0);
            chaining = true;
            try {
                int arcs = 0;
                for (EntityLivingBase o : SkillShapes.around(c.w, t.posX, t.posY, t.posZ, 6)) {
                    if (o == t || arcs >= 2) {
                        continue;
                    }
                    arcs++;
                    Fx.send(c.w, Fx.LIGHTNING, Fx.LIGHTNING_E, t.posX, t.posY + t.height * 0.6, t.posZ, o.posX, o.posY + o.height * 0.6, o.posZ, 0.8, 0, 4, -1);
                    SkillShapes.damage(c, o, amount * 0.5F, SkillShapes.hit().noStatus().noFx());
                }
                if (arcs > 0) {
                    Fx.sound(t, "minecraft:entity.lightning.impact", 0.5F, 1.8F);
                }
            } finally {
                chaining = false;
            }
        }
        if (noStatus) {
            return;
        }
        addStatus(c, t, amount);
    }

    /** Puts the caster's element status on the target (and triggers element combos). */
    public static void addStatus(SkillCtx c, EntityLivingBase t, float amount) {
        if (c.status.isEmpty() || t.isDead) {
            return;
        }
        long now = c.w.getTotalWorldTime();
        NBTTagCompound nd = d(t);
        switch (c.status) {
            case "fire":
                if (nd.getLong("za_st_frozen") > now || nd.getInteger("za_st_chill") > 0 && nd.getLong("za_st_chillT") > now || wet(t)) {
                    steam(c, t, amount);
                } else {
                    nd.setLong("za_st_char", now + DUR);
                    t.setFire(3);
                }
                break;
            case "ice":
                if (charred(nd, t, now)) {
                    steam(c, t, amount);
                    break;
                }
                int stacks = nd.getLong("za_st_chillT") > now ? nd.getInteger("za_st_chill") : 0;
                stacks += wet(t) ? 3 : 1; // a wet enemy freezes at once
                if (stacks >= 3) {
                    nd.setInteger("za_st_chill", 0);
                    nd.setLong("za_st_frozen", now + 30);
                    t.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 30, 6, false, false));
                    t.addPotionEffect(new PotionEffect(MobEffects.WEAKNESS, 30, 1, false, false));
                    Fx.send(c.w, Fx.PILLAR, Fx.ICE, t.posX, t.posY, t.posZ, 0, 0, 0, t.height + 0.4, t.width + 0.4, 10, -1);
                    Fx.sound(t, "minecraft:block.glass.place", 1.0F, 0.6F);
                } else {
                    nd.setInteger("za_st_chill", stacks);
                    nd.setLong("za_st_chillT", now + DUR);
                    t.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 60, stacks - 1, false, false));
                }
                break;
            case "lightning":
                if (charred(nd, t, now) || wet(t)) {
                    overload(c, t, amount);
                } else {
                    nd.setLong("za_st_shock", now + DUR);
                }
                break;
            case "necromancy":
                nd.setLong("za_st_drain", now + DUR);
                t.addPotionEffect(new PotionEffect(MobEffects.WITHER, 60, 0, false, false));
                break;
            case "earth":
                t.addTag("za_stg_" + ((int) now + DUR));
                nd.setLong("za_st_stagger", now + DUR);
                break;
            case "poison":
                if (charred(nd, t, now)) {
                    toxicBurst(c, t, amount);
                } else {
                    t.addPotionEffect(new PotionEffect(MobEffects.POISON, 80, 1, false, false));
                }
                break;
            case "wind":
                if (charred(nd, t, now)) {
                    firestorm(c, t, amount);
                } else if (t.isPotionActive(MobEffects.POISON)) {
                    miasma(c, t);
                } else {
                    t.motionY = Math.max(t.motionY, 0.35);
                    t.velocityChanged = true;
                }
                break;
            case "shadow":
                if (nd.getLong("za_st_radiant") > now) {
                    eclipse(c, t, amount);
                    break;
                }
                t.addPotionEffect(new PotionEffect(MobEffects.WEAKNESS, 60, 0, false, false));
                nd.setLong("za_st_expose", now + DUR);
                break;
            case "sorcery":
            case "magic":
                t.addPotionEffect(new PotionEffect(MobEffects.WEAKNESS, 60, 0, false, false));
                nd.setLong("za_st_expose", now + DUR);
                break;
            case "holy":
                if (nd.getLong("za_st_expose") > now) {
                    eclipse(c, t, amount);
                    break;
                }
                nd.setLong("za_st_radiant", now + DUR);
                if (t.isEntityUndead()) {
                    t.setFire(2);
                }
                break;
            case "healing":
                if (t.isEntityUndead()) {
                    t.setFire(2);
                }
                break;
            default:
                break;
        }
    }

    private static void steam(SkillCtx c, EntityLivingBase t, float amount) {
        t.extinguish();
        NBTTagCompound nd = d(t);
        nd.setLong("za_st_char", 0);
        nd.setLong("za_st_frozen", 0);
        nd.setInteger("za_st_chill", 0);
        Fx.at(c.w, Fx.EXPLOSION, Fx.WIND, t.posX, t.posY + 0.5, t.posZ, 3.0);
        Fx.sound(t, "minecraft:block.fire.extinguish", 1.2F, 0.7F);
        Fx.sound(t, "minecraft:entity.generic.explode", 0.6F, 1.4F);
        float boom = amount * 0.5F + 4.0F;
        chaining = true;
        try {
            List<EntityLivingBase> list = SkillShapes.around(c.w, t.posX, t.posY, t.posZ, 3.0);
            for (EntityLivingBase o : list) {
                o.hurtResistantTime = 0;
                o.attackEntityFrom(DamageSource.causeIndirectMagicDamage(c.p, c.p), boom);
                o.addPotionEffect(new PotionEffect(MobEffects.BLINDNESS, 60, 0, false, false));
                o.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 60, 1, false, false));
            }
        } finally {
            chaining = false;
        }
        banner(c, "\u00a7fSteam Burst!");
    }

    private static void overload(SkillCtx c, EntityLivingBase t, float amount) {
        d(t).setLong("za_st_char", 0);
        Fx.at(c.w, Fx.EXPLOSION, Fx.LIGHTNING_E, t.posX, t.posY + 0.5, t.posZ, 3.0);
        Fx.sound(t, "minecraft:entity.lightning.impact", 1.0F, 1.0F);
        Fx.sound(t, "minecraft:entity.generic.explode", 0.8F, 1.1F);
        float boom = amount * 0.6F + 4.0F;
        for (EntityLivingBase o : SkillShapes.around(c.w, t.posX, t.posY, t.posZ, 3.0)) {
            o.hurtResistantTime = 0;
            o.attackEntityFrom(DamageSource.causeIndirectMagicDamage(c.p, c.p), boom);
            double dx = o.posX - t.posX, dz = o.posZ - t.posZ, dd = Math.max(0.3, Math.sqrt(dx * dx + dz * dz));
            o.motionX = dx / dd * 0.9;
            o.motionZ = dz / dd * 0.9;
            o.motionY = 0.35;
            o.velocityChanged = true;
        }
        banner(c, "\u00a7eOverload!");
    }

    /** Wind on a burning enemy: the fire spreads to everything around it. */
    private static void firestorm(SkillCtx c, EntityLivingBase t, float amount) {
        d(t).setLong("za_st_char", 0);
        Fx.at(c.w, Fx.SPIRAL, Fx.FIRE, t.posX, t.posY, t.posZ, 3.5);
        Fx.sound(t, "minecraft:item.firecharge.use", 1.0F, 0.6F);
        burst(c, t, 4.0, amount * 0.5F + 3.0F, o -> {
            o.setFire(5);
            d(o).setLong("za_st_char", c.w.getTotalWorldTime() + 100);
        });
        banner(c, "\u00a76Firestorm!");
    }

    /** Wind on a poisoned enemy: the poison spreads. */
    private static void miasma(SkillCtx c, EntityLivingBase t) {
        Fx.at(c.w, Fx.FIELD, Fx.POISON, t.posX, t.posY, t.posZ, 3.5);
        burst(c, t, 4.5, 2.0F, o -> o.addPotionEffect(new PotionEffect(MobEffects.POISON, 120, 1, false, false)));
        banner(c, "\u00a72Miasma!");
    }

    /** Poison on a burning enemy: a toxic explosion. */
    private static void toxicBurst(SkillCtx c, EntityLivingBase t, float amount) {
        d(t).setLong("za_st_char", 0);
        Fx.at(c.w, Fx.EXPLOSION, Fx.POISON, t.posX, t.posY + 0.5, t.posZ, 3.0);
        Fx.sound(t, "minecraft:entity.generic.explode", 0.7F, 1.6F);
        burst(c, t, 3.5, amount * 0.6F + 4.0F, o -> o.addPotionEffect(new PotionEffect(MobEffects.POISON, 100, 2, false, false)));
        banner(c, "\u00a7aToxic Burst!");
    }

    /** Light meets Shadow: a big burst of damage that ignores armor. */
    private static void eclipse(SkillCtx c, EntityLivingBase t, float amount) {
        d(t).setLong("za_st_expose", 0);
        d(t).setLong("za_st_radiant", 0);
        Fx.at(c.w, Fx.M_ORB, Fx.SHADOW, t.posX, t.posY + t.height * 0.5, t.posZ, 2.0);
        Fx.at(c.w, Fx.FLASH, Fx.HOLY, t.posX, t.posY + t.height * 0.5, t.posZ, 2.5);
        Fx.sound(t, "minecraft:block.end_portal.spawn", 0.5F, 1.8F);
        chaining = true;
        try {
            t.hurtResistantTime = 0;
            t.attackEntityFrom(DamageSource.causeIndirectMagicDamage(c.p, c.p).setDamageBypassesArmor(), amount + 6.0F);
        } finally {
            chaining = false;
        }
        banner(c, "\u00a75Eclipse!");
    }

    private static void burst(SkillCtx c, EntityLivingBase t, double r, float dmg, java.util.function.Consumer<EntityLivingBase> extra) {
        chaining = true;
        try {
            for (EntityLivingBase o : SkillShapes.around(c.w, t.posX, t.posY, t.posZ, r)) {
                o.hurtResistantTime = 0;
                o.attackEntityFrom(DamageSource.causeIndirectMagicDamage(c.p, c.p), dmg);
                extra.accept(o);
            }
        } finally {
            chaining = false;
        }
    }

    private static void banner(SkillCtx c, String s) {
        c.p.sendStatusMessage(new net.minecraft.util.text.TextComponentString(s), true);
        SkillServer.score(c.p, "za_wa_combo", 1);
    }
}
