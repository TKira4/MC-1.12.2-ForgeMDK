package com.imba.undyingarcana;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.text.TextComponentString;

/**
 * Jeweler's Bench actions (server side): cut a jewel from 1 uncut gem + 5 reagents, put a jewel into a socket of the
 * item in the main hand, take one out (costs Soul Coins).
 * Reagents add +1 (Perfect +2) to a secondary stat each; Relic Shards raise the grade (1-2 Fine, 3+ Perfect).
 */
public final class Bench {
    public static final int UNSOCKET_COST = 20, REAGENTS = 5;
    /** Reagent i raises gear stat i (0..7); index 8 is the Relic Shard. */
    public static ItemStack reagent(int i) {
        switch (i) {
            case 0: return new ItemStack(Items.BLAZE_POWDER);
            case 1: return new ItemStack(Items.IRON_NUGGET);
            case 2: return new ItemStack(Items.SUGAR);
            case 3: return new ItemStack(Items.PRISMARINE_SHARD);
            case 4: return new ItemStack(Items.REDSTONE);
            case 5: return new ItemStack(Items.GLOWSTONE_DUST);
            case 6: return new ItemStack(Items.FLINT);
            case 7: return new ItemStack(Items.DYE, 1, 4);
            default: return new ItemStack(ModItems.RELIC_SHARD);
        }
    }

    public static int grade(int shards) {
        return shards >= 3 ? 2 : shards >= 1 ? 1 : 0;
    }

    private Bench() {
    }

    public static int count(EntityPlayer p, ItemStack like) {
        int n = 0;
        for (int i = 0; i < p.inventory.getSizeInventory(); i++) {
            ItemStack s = p.inventory.getStackInSlot(i);
            if (same(s, like)) {
                n += s.getCount();
            }
        }
        return n;
    }

    private static boolean same(ItemStack s, ItemStack like) {
        return !s.isEmpty() && s.getItem() == like.getItem() && (!like.getHasSubtypes() || s.getMetadata() == like.getMetadata());
    }

    public static boolean take(EntityPlayer p, ItemStack like, int n) {
        if (p.capabilities.isCreativeMode) {
            return true;
        }
        if (count(p, like) < n) {
            return false;
        }
        for (int i = 0; i < p.inventory.getSizeInventory() && n > 0; i++) {
            ItemStack s = p.inventory.getStackInSlot(i);
            if (same(s, like)) {
                int k = Math.min(n, s.getCount());
                s.shrink(k);
                n -= k;
            }
        }
        return true;
    }

    private static void msg(EntityPlayer p, String s) {
        p.sendMessage(new TextComponentString(s));
    }

    /** op 0 cut (a = gem, r = 9 reagent counts), 1 socket (a = inventory slot of the jewel), 2 unsocket (a = socket index). */
    public static void act(EntityPlayerMP p, int op, int a, int[] r) {
        if (!Stats.on(p.world)) {
            return;
        }
        switch (op) {
            case 0: cut(p, a, r); break;
            case 1: socket(p, a); break;
            case 2: unsocket(p, a); break;
            default: break;
        }
        p.inventoryContainer.detectAndSendChanges();
    }

    private static void cut(EntityPlayerMP p, int gem, int[] r) {
        if (gem < 0 || gem >= Gear.GEM.length || r == null || r.length != 9) {
            return;
        }
        int total = 0;
        for (int v : r) {
            if (v < 0) {
                return;
            }
            total += v;
        }
        if (total != REAGENTS) {
            msg(p, "\u00a7cA jewel needs exactly " + REAGENTS + " reagents.");
            return;
        }
        if (gem >= Gear.FIRST_LEGEND_GEM && r[8] < 1) {
            msg(p, "\u00a7cA legendary gem needs at least 1 Relic Shard.");
            return;
        }
        int gr = grade(r[8]);
        int cost = Gear.GRADE_COST[gr];
        ItemStack gemStack = new ItemStack(Jewelry.GEMS[gem]);
        if (count(p, gemStack) < 1 && !p.capabilities.isCreativeMode) {
            msg(p, "\u00a7cYou have no uncut " + Gear.GEM_NAME[gem] + ".");
            return;
        }
        for (int i = 0; i < 9; i++) {
            if (r[i] > 0 && count(p, reagent(i)) < r[i] && !p.capabilities.isCreativeMode) {
                msg(p, "\u00a7cNot enough " + reagent(i).getDisplayName() + ".");
                return;
            }
        }
        if (Coins.count(p) < cost && !p.capabilities.isCreativeMode) {
            msg(p, "\u00a7cCutting a " + Gear.GRADE[gr] + " jewel costs " + cost + " Soul Coins.");
            return;
        }
        take(p, gemStack, 1);
        for (int i = 0; i < 9; i++) {
            if (r[i] > 0) {
                take(p, reagent(i), r[i]);
            }
        }
        take(p, new ItemStack(ModItems.SOUL_COIN), cost);
        NBTTagCompound j = new NBTTagCompound();
        j.setInteger("g", gem);
        j.setInteger("gr", gr);
        int[] sec = new int[Gear.STATS];
        for (int i = 0; i < 8; i++) {
            sec[i] = r[i] * (gr == 2 ? 2 : 1);
        }
        j.setIntArray("sec", sec);
        if (gr == 2) {
            int dr;
            do {
                dr = p.getRNG().nextInt(Gear.STATS);
            } while (dr == Gear.GEM_STAT[gem] || dr >= 8);
            j.setInteger("dr", dr);
        }
        ItemStack out = Jewelry.jewel(j);
        if (!p.inventory.addItemStackToInventory(out)) {
            p.dropItem(out, false);
        }
        Fx.at(p.world, Fx.FLASH, Fx.ARCANE, p.posX, p.posY + 1, p.posZ, 0.8);
        Fx.sound(p.world, p.posX, p.posY, p.posZ, "minecraft:block.anvil.use", 0.6F, 1.6F);
        msg(p, "\u00a7dCut: " + Jewelry.jewelLine(j));
    }

    private static void socket(EntityPlayerMP p, int slot) {
        ItemStack gear = p.getHeldItemMainhand();
        if (!Gear.isGear(gear) || !Gear.rolled(gear)) {
            msg(p, "\u00a7cHold the weapon or armor piece in your main hand.");
            return;
        }
        if (slot < 0 || slot >= p.inventory.getSizeInventory()) {
            return;
        }
        ItemStack js = p.inventory.getStackInSlot(slot);
        NBTTagCompound j = Jewelry.jewelData(js);
        if (j == null) {
            return;
        }
        NBTTagList list = Gear.tag(gear).getTagList("j", 10);
        if (list.tagCount() >= Gear.sockets(gear)) {
            msg(p, "\u00a7cNo free socket on this item.");
            return;
        }
        if (j.getInteger("g") >= Gear.FIRST_LEGEND_GEM) {
            for (int i = 0; i < list.tagCount(); i++) {
                if (list.getCompoundTagAt(i).getInteger("g") >= Gear.FIRST_LEGEND_GEM) {
                    msg(p, "\u00a7cOnly one legendary jewel per item.");
                    return;
                }
            }
        }
        list.appendTag(j);
        Gear.tag(gear).setTag("j", list);
        js.shrink(1);
        Fx.sound(p.world, p.posX, p.posY, p.posZ, "minecraft:block.end_portal_frame.fill", 1.0F, 1.2F);
    }

    private static void unsocket(EntityPlayerMP p, int idx) {
        ItemStack gear = p.getHeldItemMainhand();
        NBTTagList list = Gear.jewels(gear);
        if (idx < 0 || idx >= list.tagCount()) {
            return;
        }
        if (!take(p, new ItemStack(ModItems.SOUL_COIN), UNSOCKET_COST)) {
            msg(p, "\u00a7cTaking a jewel out costs " + UNSOCKET_COST + " Soul Coins.");
            return;
        }
        NBTTagCompound j = list.getCompoundTagAt(idx);
        list.removeTag(idx);
        Gear.tag(gear).setTag("j", list);
        ItemStack out = Jewelry.jewel(j);
        if (!p.inventory.addItemStackToInventory(out)) {
            p.dropItem(out, false);
        }
        Fx.sound(p.world, p.posX, p.posY, p.posZ, "minecraft:entity.item.pickup", 1.0F, 0.8F);
    }

}
