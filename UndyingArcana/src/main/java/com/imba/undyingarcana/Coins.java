package com.imba.undyingarcana;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Soul Coins, the pack's own currency (new-world systems only, game rule uaBuilds): a balance per player (persisted
 * data "coins"), not an item. Kills pay the killer straight away (bosses pay everyone who fought within 48 blocks; Luck
 * adds 1% per point, the Fortune blessing 10%), and so do Gambler jackpots, Blood Moons survived and quests. Spent at the
 * Soul Exchange, the Jeweler, the Soul Shrine and on element changes. Old coin items are paid in when carried.
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
            if (r.nextFloat() < 0.2F + 0.05F * tier) {
                loot.add(Relics.random(r));
            }
            if (r.nextFloat() < 0.15F + 0.03F * tier) {
                loot.add(Martial.randomManual(r));
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
            if (r.nextFloat() < 0.3F) {
                e.getDrops().add(new EntityItem(dead.world, dead.posX, dead.posY + 0.5, dead.posZ, Relics.random(r)));
            }
            if (r.nextFloat() < 0.2F) {
                e.getDrops().add(new EntityItem(dead.world, dead.posX, dead.posY + 0.5, dead.posZ, Martial.randomManual(r)));
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
        java.util.List<EntityPlayer> payees = new java.util.ArrayList<>();
        if (dead instanceof EntityLSBoss) {
            payees.addAll(dead.world.getEntitiesWithinAABB(EntityPlayer.class, dead.getEntityBoundingBox().grow(48),
                    x -> x.isEntityAlive() && !x.isSpectator()));
        }
        if (!payees.contains(killer)) {
            payees.add(killer);
        }
        for (EntityPlayer p : payees) {
            float luck = (1.0F + 0.01F * Stats.stats(p)[Builds.LUCK] + (Gear.fortune(p) - 1.0F)) * Power.coinMult(p)
                    * (Econ.blessed(p.world, 1) ? 1.1F : 1F);
            int got = (int) (n * luck) + (r.nextFloat() < n * luck % 1 ? 1 : 0);
            add(p, got);
        }
    }

    // ------------------------------------------------------------------ balance
    /** Client copy of the local player's balance (from MsgPower). */
    public static int clientBalance;

    public static int balance(EntityPlayer p) {
        return Stats.data(p).getInteger("coins");
    }

    /** Adds coins to the balance (and syncs it). */
    public static void add(EntityPlayer p, int n) {
        if (n <= 0 || p.world.isRemote) {
            return;
        }
        NBTTagCompound d = Stats.data(p);
        d.setInteger("coins", (int) Math.min(Integer.MAX_VALUE, (long) d.getInteger("coins") + n));
        if (p instanceof EntityPlayerMP) {
            Power.sync((EntityPlayerMP) p, true);
        }
    }

    /** Takes coins if the balance has them; creative always pays. */
    public static boolean spend(EntityPlayer p, int n) {
        if (n <= 0 || p.capabilities.isCreativeMode) {
            return true;
        }
        NBTTagCompound d = Stats.data(p);
        if (d.getInteger("coins") < n) {
            return false;
        }
        d.setInteger("coins", d.getInteger("coins") - n);
        if (p instanceof EntityPlayerMP) {
            Power.sync((EntityPlayerMP) p, true);
        }
        return true;
    }

    /** Old name: adds to the balance. */
    public static void give(EntityPlayer p, int n) {
        add(p, n);
    }

    /** Balance (the client side reads the synced copy). */
    public static int count(EntityPlayer p) {
        return p.world.isRemote ? clientBalance : balance(p);
    }

    /** Old Soul Coin items in the inventory are paid into the balance. */
    @SubscribeEvent
    public static void onTick(net.minecraftforge.fml.common.gameevent.TickEvent.PlayerTickEvent e) {
        EntityPlayer p = e.player;
        if (e.phase != net.minecraftforge.fml.common.gameevent.TickEvent.Phase.END || p.world.isRemote || p.ticksExisted % 20 != 11
                || !Stats.on(p.world)) {
            return;
        }
        int n = 0;
        for (int i = 0; i < p.inventory.getSizeInventory(); i++) {
            ItemStack s = p.inventory.getStackInSlot(i);
            if (s.getItem() == ModItems.SOUL_COIN) {
                n += s.getCount();
                p.inventory.setInventorySlotContents(i, ItemStack.EMPTY);
            }
        }
        if (n > 0) {
            add(p, n);
        }
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
