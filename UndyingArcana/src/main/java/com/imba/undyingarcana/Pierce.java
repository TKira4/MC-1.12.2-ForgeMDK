package com.imba.undyingarcana;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.MobEffects;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Armor Pierce and Spell Pierce for player damage, plus the Sunder / Hex statuses that skill hits stack on mobs.
 *
 * Physical damage: vanilla armor is applied after LivingHurtEvent, so the damage is scaled by
 * reduction(armor after pierce and Sunder) / reduction(full armor) - the same as the mob having less armor.
 * Magic damage already ignores armor; Spell Pierce and Hex cut through the Resistance effect instead.
 * Mod-specific damage reductions that run later cannot be undone here; /dps inspect shows what a mob carries.
 */
@Mod.EventBusSubscriber(modid = UndyingArcana.MODID)
public final class Pierce {
    public static final float MAX_PIERCE = 0.6F;
    private static final int MAX_STACKS = 5;

    private Pierce() {
    }

    // ------------------------------------------------------------------ player pierce values
    /** Armor Pierce of a player, 0..0.6. Base from the Calling until stat builds exist (phase 3); gear adds later. */
    public static float armorPierce(EntityPlayerMP p) {
        float v = Stats.isWarrior(p) || Stats.isRanger(p) ? 0.2F : 0.0F;
        v += p.getEntityData().getFloat("ua_apen_gear");
        return Math.min(MAX_PIERCE, v);
    }

    /** Spell Pierce of a player, 0..0.6. */
    public static float spellPierce(EntityPlayerMP p) {
        float v = Stats.isMage(p) ? 0.2F : 0.0F;
        v += p.getEntityData().getFloat("ua_spen_gear");
        return Math.min(MAX_PIERCE, v);
    }

    // ------------------------------------------------------------------ statuses
    public static void addSunder(EntityLivingBase t, long now, int stacks) {
        NBTTagCompound d = t.getEntityData();
        int n = d.getLong("ua_sunderT") > now ? d.getInteger("ua_sunder") : 0;
        d.setInteger("ua_sunder", Math.min(MAX_STACKS, n + stacks));
        d.setLong("ua_sunderT", now + 160);
    }

    public static void addHex(EntityLivingBase t, long now, int stacks) {
        NBTTagCompound d = t.getEntityData();
        int n = d.getLong("ua_hexT") > now ? d.getInteger("ua_hex") : 0;
        d.setInteger("ua_hex", Math.min(MAX_STACKS, n + stacks));
        d.setLong("ua_hexT", now + 160);
    }

    public static int sunder(EntityLivingBase t, long now) {
        NBTTagCompound d = t.getEntityData();
        return d.getLong("ua_sunderT") > now ? d.getInteger("ua_sunder") : 0;
    }

    public static int hex(EntityLivingBase t, long now) {
        NBTTagCompound d = t.getEntityData();
        return d.getLong("ua_hexT") > now ? d.getInteger("ua_hex") : 0;
    }

    // ------------------------------------------------------------------ damage
    /** Vanilla CombatRules.getDamageAfterAbsorb as a multiplier. */
    private static float armorFactor(float damage, float armor, float toughness) {
        float f = 2.0F + toughness / 4.0F;
        float f1 = Math.max(0, Math.min(armor - damage / f, 20.0F));
        f1 = Math.max(armor / 5.0F, f1);
        f1 = Math.min(20.0F, f1);
        return 1.0F - f1 / 25.0F;
    }

    public static boolean isMagic(DamageSource s) {
        return s.isMagicDamage() || s.getDamageType().contains("wizardry");
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onHurt(LivingHurtEvent e) {
        EntityLivingBase t = e.getEntityLiving();
        if (t.world.isRemote || e.getAmount() <= 0) {
            return;
        }
        Entity src = e.getSource().getTrueSource();
        if (!(src instanceof EntityPlayerMP) || src == t) {
            return;
        }
        EntityPlayerMP p = (EntityPlayerMP) src;
        DamageSource s = e.getSource();
        long now = t.world.getTotalWorldTime();
        float amount = e.getAmount();
        if (isMagic(s)) {
            PotionEffect res = t.getActivePotionEffect(MobEffects.RESISTANCE);
            if (res == null || s.isDamageAbsolute()) {
                return;
            }
            float pierce = Math.min(0.9F, spellPierce(p) + 0.1F * hex(t, now));
            float cut = Math.min(1.0F, 0.2F * (res.getAmplifier() + 1));
            float before = 1.0F - cut, after = 1.0F - cut * (1.0F - pierce);
            if (before > 0.05F) {
                e.setAmount(amount * after / before);
            }
            return;
        }
        if (s.isUnblockable()) {
            return;
        }
        float armor = t.getTotalArmorValue();
        if (armor <= 0) {
            return;
        }
        float tough = (float) t.getEntityAttribute(SharedMonsterAttributes.ARMOR_TOUGHNESS).getAttributeValue();
        float pierce = armorPierce(p);
        float pierced = Math.max(0, armor * (1.0F - pierce) - 2.0F * sunder(t, now));
        float full = armorFactor(amount, armor, tough);
        float less = armorFactor(amount, pierced, tough);
        if (full > 0.01F) {
            e.setAmount(amount * less / full);
        }
    }
}
