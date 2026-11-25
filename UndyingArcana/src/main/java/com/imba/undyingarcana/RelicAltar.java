package com.imba.undyingarcana;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

/**
 * Relic Altar: right-click it with a Relic Blueprint to forge the legendary weapon (handled by the pack scripts), or with
 * 8 Relic Shards to forge a random relic (accessory). With an empty hand it opens the Relic Forge: three relics of the same
 * quality combine into the one you keep at the next quality (the jewels of the other two come back), and a relic can be
 * rerolled into another relic of the same kind (quality, jewels and enchant stay).
 */
public class RelicAltar extends Block {
    public static final int SHARDS_PER_RELIC = 8;
    /** Soul Coins to combine three relics, by the quality made (index 1 Uncommon .. 4 Legendary). */
    public static final int[] COMBINE_COST = {0, 50, 120, 300, 700};

    public static int rerollCost(int quality) {
        return 60 + 40 * quality;
    }

    @Override
    public boolean onBlockActivated(World w, BlockPos pos, IBlockState state, EntityPlayer p, EnumHand hand, EnumFacing side, float hx,
            float hy, float hz) {
        ItemStack held = p.getHeldItem(hand);
        if (held.isEmpty() && hand == EnumHand.MAIN_HAND && Stats.on(w)) {
            if (!w.isRemote) {
                SkillNet.NET.sendTo(new SkillNet.MsgOpen(UABlock.FORGE, ""), (net.minecraft.entity.player.EntityPlayerMP) p);
            }
            return true;
        }
        if (held.getItem() != ModItems.RELIC_SHARD) {
            return false; // blueprints and everything else: the pack scripts
        }
        if (!w.isRemote) {
            if (held.getCount() < SHARDS_PER_RELIC && !p.capabilities.isCreativeMode) {
                p.sendStatusMessage(new TextComponentString("\u00a77" + SHARDS_PER_RELIC + " Relic Shards forge a relic (you hold "
                        + held.getCount() + ")."), true);
                return true;
            }
            if (!p.capabilities.isCreativeMode) {
                held.shrink(SHARDS_PER_RELIC);
            }
            ItemStack r = Relics.random(p.getRNG());
            Gear.roll(r, p.getRNG(), Stats.stats(p)[Builds.LUCK]);
            if (!p.inventory.addItemStackToInventory(r)) {
                p.dropItem(r, false);
            }
            Fx.at(w, Fx.PILLAR, Fx.ARCANE, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 1.0);
            Fx.sound(p, "minecraft:block.enchantment_table.use", 1F, 0.8F);
            p.sendStatusMessage(new TextComponentString("\u00a7dThe altar forges " + r.getDisplayName() + "\u00a7d!"), true);
        }
        return true;
    }

    public RelicAltar() {
        super(Material.ROCK);
        setHardness(5.0F);
        setResistance(1200.0F);
        setLightLevel(0.6F);
        setHarvestLevel("pickaxe", 2);
    }

    // ------------------------------------------------------------------ Relic Forge (server side, MsgEconAct 5 / 6)
    private static ItemStack at(EntityPlayer p, int slot) {
        return slot < 0 || slot >= p.inventory.mainInventory.size() ? ItemStack.EMPTY : p.inventory.getStackInSlot(slot);
    }

    /** Combine: keep = slot of the relic kept, a / b = slots of the two consumed (same quality, below Legendary). */
    public static void combine(net.minecraft.entity.player.EntityPlayerMP p, int keep, int a, int b) {
        ItemStack k = at(p, keep), x = at(p, a), y = at(p, b);
        if (keep == a || keep == b || a == b || !Relics.isRelic(k) || !Relics.isRelic(x) || !Relics.isRelic(y)
                || !Gear.rolled(k) || !Gear.rolled(x) || !Gear.rolled(y)) {
            return;
        }
        int q = Gear.quality(k);
        if (q >= 4 || Gear.quality(x) != q || Gear.quality(y) != q) {
            p.sendMessage(new TextComponentString("\u00a7cThe three relics must have the same quality, below Legendary."));
            return;
        }
        int cost = COMBINE_COST[q + 1];
        if (!p.capabilities.isCreativeMode && !Coins.spend(p, cost)) {
            p.sendMessage(new TextComponentString("\u00a7cCombining costs " + cost + " Soul Coins."));
            return;
        }
        for (ItemStack used : new ItemStack[]{x, y}) {
            net.minecraft.nbt.NBTTagList js = Gear.jewels(used);
            for (int i = 0; i < js.tagCount(); i++) {
                ItemStack jw = Jewelry.jewel(js.getCompoundTagAt(i));
                if (!p.inventory.addItemStackToInventory(jw)) {
                    p.dropItem(jw, false);
                }
            }
            used.shrink(1);
        }
        Gear.setQuality(k, q + 1);
        Fx.at(p.world, Fx.PILLAR, Fx.ARCANE, p.posX, p.posY + 1, p.posZ, 1.0);
        Fx.sound(p, "minecraft:block.anvil.use", 0.8F, 1.2F);
        p.sendMessage(new TextComponentString("\u00a7dThe forge raises " + k.getDisplayName() + "\u00a7d to " + Gear.QUALITY[q + 1] + "!"));
    }

    /** Reroll: the relic in slot becomes another relic of the same kind; quality, jewels and enchant stay. */
    public static void reroll(net.minecraft.entity.player.EntityPlayerMP p, int slot) {
        ItemStack s = at(p, slot);
        int def = Relics.defOf(s);
        if (def < 0 || !Gear.rolled(s)) {
            return;
        }
        java.util.List<Integer> same = Relics.sameKind(def);
        if (same.isEmpty()) {
            p.sendMessage(new TextComponentString("\u00a7cThere is no other relic of this kind."));
            return;
        }
        int cost = rerollCost(Gear.quality(s));
        if (!p.capabilities.isCreativeMode && !Coins.spend(p, cost)) {
            p.sendMessage(new TextComponentString("\u00a7cRerolling costs " + cost + " Soul Coins."));
            return;
        }
        ItemStack out = new ItemStack(Relics.ITEMS[same.get(p.getRNG().nextInt(same.size()))]);
        out.setTagCompound(s.getTagCompound() == null ? null : s.getTagCompound().copy());
        p.inventory.setInventorySlotContents(slot, out);
        Fx.sound(p, "minecraft:block.enchantment_table.use", 0.8F, 1.0F);
        p.sendMessage(new TextComponentString("\u00a7dThe forge reshapes it into " + out.getDisplayName() + "\u00a7d."));
    }
}
