package com.imba.undyingarcana;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Soul Coins, the pack's own currency (new-world systems only, game rule uaBuilds). Dropped by kills (more from elites
 * and bosses, Luck adds 1% per point), Gambler jackpots, Blood Moons survived and quests. Spent at the Jeweler, on
 * enchants and in the shop (Phase 4).
 */
@Mod.EventBusSubscriber(modid = UndyingArcana.MODID)
public final class Coins {
    private Coins() {
    }

    @SubscribeEvent
    public static void onDrops(LivingDropsEvent e) {
        EntityLivingBase dead = e.getEntityLiving();
        Entity src = e.getSource().getTrueSource();
        if (dead.world.isRemote || !(src instanceof EntityPlayer) || dead instanceof EntityPlayer || !Stats.on(dead.world)) {
            return;
        }
        java.util.Random r = dead.world.rand;
        int n = 0;
        if (dead instanceof EntityLSBoss) {
            n = 25 + r.nextInt(16);
        } else if (dead instanceof EntityUAZombie) {
            EntityUAZombie z = (EntityUAZombie) dead;
            if (z.owner != null) {
                return;
            }
            n = z.isElite() ? 3 + r.nextInt(4) : r.nextFloat() < 0.35F ? 1 : 0;
        } else if (dead instanceof IMob) {
            n = r.nextFloat() < 0.15F ? 1 : 0;
        }
        EntityPlayer killer = (EntityPlayer) src;
        float fortune = Gear.fortune(killer);
        // uncut gems: elites 12%, bosses 2-3; bosses also drop a tier III or cursed scroll and maybe a legendary gem
        int gems = dead instanceof EntityLSBoss ? 2 + r.nextInt(2)
                : dead instanceof EntityUAZombie && ((EntityUAZombie) dead).isElite() && r.nextFloat() < 0.12F * fortune ? 1 : 0;
        for (int i = 0; i < gems; i++) {
            e.getDrops().add(new EntityItem(dead.world, dead.posX, dead.posY + 0.5, dead.posZ, randomGem(r)));
        }
        if (dead.getTags().contains("ua_warlord")) {
            int tier = 1;
            for (String tg : dead.getTags()) {
                if (tg.startsWith("ua_wl_t")) {
                    tier = Integer.parseInt(tg.substring(7));
                }
            }
            n = Math.max(n, 15 + 10 * tier);
            java.util.List<ItemStack> loot = new java.util.ArrayList<>();
            loot.add(new ItemStack(ModItems.RELIC_SHARD, 1 + tier + r.nextInt(2)));
            if (r.nextFloat() < 0.5F + 0.1F * tier && !ModItems.BLUEPRINTS.isEmpty()) {
                loot.add(new ItemStack(ModItems.BLUEPRINTS.get(r.nextInt(ModItems.BLUEPRINTS.size()))));
            }
            loot.add(rareScroll(r));
            if (r.nextFloat() < 0.25F + 0.05F * tier) {
                loot.add(SpellArts.randomScroll(r));
            }
            loot.add(randomGem(r));
            if (r.nextFloat() < 0.15F * tier) {
                loot.add(legendGem(r));
            }
            if (r.nextFloat() < 0.4F + 0.1F * tier) {
                loot.add(Armory.random(r, tier >= 3 && r.nextFloat() < 0.35F ? 3 : 2));
            }
            for (ItemStack s : loot) {
                e.getDrops().add(new EntityItem(dead.world, dead.posX, dead.posY + 0.5, dead.posZ, s));
            }
        }
        if (dead instanceof EntityLSBoss) {
            e.getDrops().add(new EntityItem(dead.world, dead.posX, dead.posY + 0.5, dead.posZ, rareScroll(r)));
            if (r.nextFloat() < 0.35F) {
                e.getDrops().add(new EntityItem(dead.world, dead.posX, dead.posY + 0.5, dead.posZ, SpellArts.randomScroll(r)));
            }
            if (r.nextFloat() < 0.25F) {
                e.getDrops().add(new EntityItem(dead.world, dead.posX, dead.posY + 0.5, dead.posZ, legendGem(r)));
            }
            float w = r.nextFloat();
            if (w < 0.5F) {
                e.getDrops().add(new EntityItem(dead.world, dead.posX, dead.posY + 0.5, dead.posZ, Armory.random(r, w < 0.12F ? 3 : 2)));
            }
        }
        if (n <= 0) {
            return;
        }
        float luck = (1.0F + 0.01F * Stats.stats(killer)[Builds.LUCK] + (fortune - 1.0F)) * Power.coinMult(killer);
        n = (int) (n * luck) + (r.nextFloat() < n * luck % 1 ? 1 : 0);
        e.getDrops().add(new EntityItem(dead.world, dead.posX, dead.posY + 0.5, dead.posZ, new ItemStack(ModItems.SOUL_COIN, n)));
    }

    /** Gives coins straight to the inventory (dropped at the feet when it is full). */
    public static void give(EntityPlayer p, int n) {
        ItemStack s = new ItemStack(ModItems.SOUL_COIN, n);
        if (!p.inventory.addItemStackToInventory(s) && !s.isEmpty()) {
            p.dropItem(s, false);
        }
    }

    public static int count(EntityPlayer p) {
        int n = 0;
        for (int i = 0; i < p.inventory.getSizeInventory(); i++) {
            ItemStack s = p.inventory.getStackInSlot(i);
            if (s.getItem() == ModItems.SOUL_COIN) {
                n += s.getCount();
            }
        }
        return n;
    }

    /** Deep mining: stone and ores below y 32 have a small chance (Luck adds) to drop an uncut gem; diamond / emerald ore 15%. */
    @SubscribeEvent
    public static void onHarvest(net.minecraftforge.event.world.BlockEvent.HarvestDropsEvent e) {
        EntityPlayer p = e.getHarvester();
        if (p == null || e.getWorld().isRemote || e.isSilkTouching() || !Stats.on(e.getWorld())) {
            return;
        }
        net.minecraft.block.Block b = e.getState().getBlock();
        float chance;
        if (b == net.minecraft.init.Blocks.DIAMOND_ORE || b == net.minecraft.init.Blocks.EMERALD_ORE) {
            chance = 0.15F;
        } else if (e.getPos().getY() < 32 && (b == net.minecraft.init.Blocks.STONE || b instanceof net.minecraft.block.BlockOre
                || b instanceof net.minecraft.block.BlockRedstoneOre)) {
            chance = 0.004F;
        } else {
            return;
        }
        chance *= (1.0F + 0.02F * Stats.stats(p)[Builds.LUCK]) * Gear.fortune(p);
        if (e.getWorld().rand.nextFloat() < chance) {
            e.getDrops().add(randomGem(e.getWorld().rand));
        }
    }

    /** A random non-legendary uncut gem. */
    public static ItemStack randomGem(java.util.Random r) {
        return new ItemStack(Jewelry.GEMS[r.nextInt(Gear.NORMAL_GEMS)]);
    }

    /** Soulstone or Void Crystal (bosses, Blood Moons). */
    public static ItemStack legendGem(java.util.Random r) {
        return new ItemStack(Jewelry.GEMS[Gear.FIRST_LEGEND_GEM + r.nextInt(Gear.GEM.length - Gear.FIRST_LEGEND_GEM)]);
    }

    /** A tier III scroll (75%) or a cursed scroll (25%). */
    public static ItemStack rareScroll(java.util.Random r) {
        if (r.nextFloat() < 0.25F) {
            return Jewelry.ItemEnchantScroll.of(Gear.CURSED + r.nextInt(Gear.ENCHANT.length - Gear.CURSED), 1);
        }
        return Jewelry.ItemEnchantScroll.of(r.nextInt(Gear.CURSED), 3);
    }
}
