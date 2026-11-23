package com.imba.undyingarcana;

import java.util.List;


import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** Ranger gun (pistol, shotgun, rifle, hand cannon). Right-click fires, sneak + right-click reloads. Logic: GunLogic. */
public class ItemGun extends Item {
    public final String type;
    public final String legend;

    public ItemGun(String type, String legend) {
        this.type = type;
        this.legend = legend;
        setMaxStackSize(1);
        setMaxDamage(legend.isEmpty() ? 900 : 3000);
        setFull3D();
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World w, EntityPlayer p, EnumHand hand) {
        ItemStack st = p.getHeldItem(hand);
        if (hand != EnumHand.MAIN_HAND) {
            return new ActionResult<>(EnumActionResult.PASS, st);
        }
        if (!w.isRemote && p instanceof EntityPlayerMP) {
            GunLogic.trigger((EntityPlayerMP) p, st);
        }
        return new ActionResult<>(EnumActionResult.SUCCESS, st);
    }

    @Override
    public void onUpdate(ItemStack st, World w, Entity e, int slot, boolean selected) {
        if (!w.isRemote && e instanceof EntityPlayerMP) {
            GunLogic.tick((EntityPlayerMP) e, st, selected);
        }
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || oldStack.getItem() != newStack.getItem();
    }

    @Override
    public boolean getIsRepairable(ItemStack toRepair, ItemStack repair) {
        return repair.getItem() == Items.IRON_INGOT || super.getIsRepairable(toRepair, repair);
    }

    @Override
    public EnumRarity getRarity(ItemStack stack) {
        return legend.isEmpty() ? EnumRarity.UNCOMMON : EnumRarity.EPIC;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public boolean hasEffect(ItemStack stack) {
        return !legend.isEmpty();
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack st, World w, List<String> tip, ITooltipFlag flag) {
        tip.add("\u00a77Ammo: \u00a7f" + GunLogic.mag(st) + "/" + GunLogic.magSize(type) + " \u00a78(" + GunLogic.ammoName(type) + ")");
        tip.add("\u00a77Needs Archery " + GunLogic.needLevel(type) + ". Right-click: fire, sneak + right-click: reload.");
    }
}
