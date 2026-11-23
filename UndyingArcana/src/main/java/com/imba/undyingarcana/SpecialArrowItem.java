package com.imba.undyingarcana;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.item.ItemArrow;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

/** Frost / Storm / Blast arrow. The effect only works for a Ranger of a high enough Ranger Rank (scripts store it). */
public class SpecialArrowItem extends ItemArrow {
    public final int kind;

    public SpecialArrowItem(int kind) {
        this.kind = kind;
    }

    @Override
    public EntityArrow createArrow(World world, ItemStack stack, EntityLivingBase shooter) {
        EntitySpecialArrow arrow = new EntitySpecialArrow(world, shooter, kind);
        arrow.setPower(powerOf(shooter, kind));
        return arrow;
    }

    /** Rank needed: Frost I, Storm II, Blast III. Data is written by the CraftTweaker scripts (za_class, za_rrank). */
    static boolean powerOf(EntityLivingBase shooter, int kind) {
        if (!(shooter instanceof EntityPlayer)) {
            return false;
        }
        EntityPlayer p = (EntityPlayer) shooter;
        return Stats.isRanger(p) && Stats.rank(p, 'r') >= kind + 1;
    }
}
