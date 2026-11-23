package com.imba.undyingarcana;

import net.minecraft.item.EnumRarity;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** Legendary bow: vanilla bow behaviour (pull animation included); its skill and arrow bonus live in the pack scripts. */
public class LegendBow extends ItemBow {
    public LegendBow() {
        setMaxDamage(1200);
    }

    @Override
    public EnumRarity getRarity(ItemStack stack) {
        return EnumRarity.EPIC;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public boolean hasEffect(ItemStack stack) {
        return true;
    }
}
