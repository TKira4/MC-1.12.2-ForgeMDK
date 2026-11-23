package com.imba.undyingarcana;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** A plain item that always shows the enchantment glint (path scrolls, Scroll of Rebirth, tomes). */
public class GlintItem extends Item {
    @Override
    @SideOnly(Side.CLIENT)
    public boolean hasEffect(ItemStack stack) {
        return true;
    }
}
