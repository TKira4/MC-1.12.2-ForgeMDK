package com.imba.undyingarcana;

import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.init.Items;
import net.minecraft.init.MobEffects;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraft.world.World;

/**
 * The Zombie Warlord: a zombie with its own model (horned helm, pauldrons, cape). It does not burn in daylight.
 * Old world: summoned and set up by the CraftTweaker script zzz_wave6_threats.zs. New world (uaDirector): summoned by
 * ZombieDirector at Blood Moon midnight and set up by setup(tier); its drops are in Coins (tag ua_warlord).
 */
public class EntityZombieWarlord extends EntityZombie {
    public EntityZombieWarlord(World world) {
        super(world);
        setSize(0.8F, 2.6F);
    }

    @Override
    protected boolean shouldBurnInDay() {
        return false;
    }

    public static String tierName(int tier) {
        return tier >= 4 ? "Warlord Eternal" : tier == 3 ? "Warlord Tyrant" : tier == 2 ? "Warlord Ascendant" : "Zombie Warlord";
    }

    /** Stats of the director's Warlord (same numbers as the old script). */
    public void setup(int tier) {
        double hp = 300.0 + 150.0 * (tier - 1);
        getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(hp);
        getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).setBaseValue(10.0 + 2.0 * (tier - 1));
        getEntityAttribute(SharedMonsterAttributes.KNOCKBACK_RESISTANCE).setBaseValue(0.8);
        getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(0.25);
        getEntityAttribute(SharedMonsterAttributes.FOLLOW_RANGE).setBaseValue(48.0);
        setHealth((float) hp);
        setCustomNameTag("\u00a74\u00a7l" + tierName(tier));
        setAlwaysRenderNameTag(true);
        setGlowing(true);
        setItemStackToSlot(EntityEquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        setItemStackToSlot(EntityEquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        setItemStackToSlot(EntityEquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
        setItemStackToSlot(EntityEquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
        for (EntityEquipmentSlot s : EntityEquipmentSlot.values()) {
            setDropChance(s, 0.0F);
        }
        addPotionEffect(new PotionEffect(MobEffects.RESISTANCE, 1000000, 0, false, false));
        enablePersistence();
        addTag("ua_warlord");
        addTag("ua_wl_t" + tier);
    }
}
