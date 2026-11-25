package com.imba.undyingarcana;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.google.gson.Gson;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.EnumAction;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;

/**
 * Economy around the Soul Coin balance (see Coins): Black Market, services, Soul Ward, Recall Scroll, the Soul Shrine
 * and /pay.
 *
 * Black Market: 4 rare offers per in-game day (same for everyone, seeded by world seed and day), one of each per player.
 * Services: repair held item / all gear (cost by missing durability), Soul Ward (next death keeps hotbar, relics and
 * coins), Recall Scroll, cleanse a cursed enchant. Death costs 10% of the balance unless a Soul Ward is active.
 * Soul Shrine: three blessings (Wisdom +25% XP, Fortune +10% coins, Vigor +20% mana / focus regen); everyone donates
 * coins, a full bar turns the blessing on for an hour for the whole server.
 */
@Mod.EventBusSubscriber(modid = UndyingArcana.MODID)
public final class Econ {
    private static final Gson GSON = new Gson();

    private Econ() {
    }

    // ------------------------------------------------------------------ black market
    public static final class MarketOffer {
        public String item = "";
        public int price, rarity; // rarity 0 Rare, 1 Exotic, 2 Legendary
        public boolean owned;
    }

    /** Today's 4 offers (owned flags for this player). */
    public static List<MarketOffer> market(EntityPlayer p) {
        long day = p.world.getWorldTime() / 24000;
        Random r = new Random(p.world.getSeed() ^ day * 0x5DEECE66DL);
        List<MarketOffer> out = new ArrayList<>();
        NBTTagCompound d = Stats.data(p);
        int mask = d.getLong("bm_day") == day ? d.getInteger("bm_bought") : 0;
        for (int i = 0; i < 4; i++) {
            MarketOffer o = new MarketOffer();
            switch ((i + r.nextInt(6)) % 6) {
                case 0: {
                    int q = 2 + r.nextInt(3); // Rare..Legendary relic
                    o.item = "relic:" + Relics.DEFS[r.nextInt(Relics.DEFS.length)].id + ":" + q;
                    o.rarity = q - 2;
                    o.price = new int[]{650, 1000, 1600}[q - 2];
                    break;
                }
                case 1:
                    o.item = "arcane:" + r.nextInt(SpellArts.ART.length);
                    o.rarity = 1;
                    o.price = 900;
                    break;
                case 2:
                    o.item = UndyingArcana.MODID + ":gem_" + Gear.GEM[Gear.FIRST_LEGEND_GEM + r.nextInt(Gear.GEM.length - Gear.FIRST_LEGEND_GEM)];
                    o.rarity = 2;
                    o.price = 800;
                    break;
                case 3:
                    o.item = UndyingArcana.MODID + ":sigil_" + ModItems.BOSSES[r.nextInt(ModItems.BOSSES.length - 1)];
                    o.rarity = 0;
                    o.price = 400;
                    break;
                case 4:
                    o.item = "manual:" + (1 + r.nextInt(Martial.ID.length - 1));
                    o.rarity = 1;
                    o.price = 700;
                    break;
                default:
                    o.item = UndyingArcana.MODID + ":arm_" + Armory.TYPE[r.nextInt(Armory.TYPE.length)] + "_arcane";
                    o.rarity = 1;
                    o.price = 750;
                    break;
            }
            o.owned = (mask & 1 << i) != 0;
            out.add(o);
        }
        return out;
    }

    /** Ticks until the market restocks (next in-game day). */
    public static int restock(World w) {
        return (int) (24000 - w.getWorldTime() % 24000);
    }

    static void buyMarket(EntityPlayerMP p, int i) {
        List<MarketOffer> m = market(p);
        if (i < 0 || i >= m.size() || m.get(i).owned) {
            return;
        }
        MarketOffer o = m.get(i);
        if (!Coins.spend(p, o.price)) {
            p.sendStatusMessage(new TextComponentString("\u00a7cYou need " + o.price + " Soul Coins."), true);
            return;
        }
        NBTTagCompound d = Stats.data(p);
        long day = p.world.getWorldTime() / 24000;
        if (d.getLong("bm_day") != day) {
            d.setLong("bm_day", day);
            d.setInteger("bm_bought", 0);
        }
        d.setInteger("bm_bought", d.getInteger("bm_bought") | 1 << i);
        give(p, Shop.stack(o.item));
        Fx.sound(p.world, p.posX, p.posY, p.posZ, "minecraft:entity.villager.yes", 0.8F, 0.9F);
    }

    static void give(EntityPlayer p, ItemStack s) {
        if (!s.isEmpty() && !p.inventory.addItemStackToInventory(s)) {
            p.dropItem(s, false);
        }
        p.inventoryContainer.detectAndSendChanges();
    }

    // ------------------------------------------------------------------ services
    public static final int REPAIR_HELD = 0, REPAIR_ALL = 1, WARD = 2, RECALL = 3, CLEANSE = 4;
    public static final int WARD_COST = 150, RECALL_COST = 25, CLEANSE_COST = 90;

    private static int repairCost(ItemStack s) {
        if (s.isEmpty() || !s.isItemStackDamageable() || s.getItemDamage() <= 0) {
            return 0;
        }
        return Math.max(1, Math.round(s.getItemDamage() * 0.14F * (1F + 0.15F * Gear.quality(s))));
    }

    /** Service prices for the screen: repair held, repair all, ward (0 = active), recall, cleanse (-1 = nothing). */
    public static int[] services(EntityPlayer p) {
        int all = repairCost(p.getHeldItemMainhand());
        for (ItemStack a : p.getArmorInventoryList()) {
            all += repairCost(a);
        }
        int[] e = Gear.enchant(p.getHeldItemMainhand());
        return new int[]{repairCost(p.getHeldItemMainhand()), all, Stats.data(p).getBoolean("ward") ? 0 : WARD_COST, RECALL_COST,
                e != null && Gear.cursed(e[0]) ? CLEANSE_COST : -1};
    }

    static void service(EntityPlayerMP p, int s) {
        int[] c = services(p);
        if (s < 0 || s > 4 || c[s] <= 0) {
            return;
        }
        if (!Coins.spend(p, c[s])) {
            p.sendStatusMessage(new TextComponentString("\u00a7cYou need " + c[s] + " Soul Coins."), true);
            return;
        }
        switch (s) {
            case REPAIR_HELD:
                p.getHeldItemMainhand().setItemDamage(0);
                break;
            case REPAIR_ALL:
                p.getHeldItemMainhand().setItemDamage(0);
                for (ItemStack a : p.getArmorInventoryList()) {
                    if (a.isItemStackDamageable()) {
                        a.setItemDamage(0);
                    }
                }
                break;
            case WARD:
                Stats.data(p).setBoolean("ward", true);
                p.sendMessage(new TextComponentString("\u00a7bSoul Ward: your next death keeps your hotbar, relics and coins."));
                break;
            case RECALL:
                give(p, new ItemStack(RECALL_SCROLL));
                break;
            default:
                Gear.tag(p.getHeldItemMainhand()).removeTag("e");
                break;
        }
        p.inventoryContainer.detectAndSendChanges();
        Fx.sound(p.world, p.posX, p.posY, p.posZ, s <= REPAIR_ALL ? "minecraft:block.anvil.use" : "minecraft:entity.villager.yes", 0.7F, 1.1F);
    }

    // ------------------------------------------------------------------ death: 10% coins, or the Soul Ward
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onDeath(LivingDeathEvent e) {
        EntityLivingBase t = e.getEntityLiving();
        if (!(t instanceof EntityPlayerMP) || t.world.isRemote || !Stats.on(t.world)) {
            return;
        }
        EntityPlayerMP p = (EntityPlayerMP) t;
        NBTTagCompound d = Stats.data(p);
        if (d.getBoolean("ward")) {
            d.setBoolean("ward", false);
            d.setBoolean("ward_now", true); // relics stay (Relics.onDrops)
            NBTTagList hot = new NBTTagList();
            for (int i = 0; i < 9; i++) {
                ItemStack s = p.inventory.mainInventory.get(i);
                if (!s.isEmpty()) {
                    NBTTagCompound c = s.writeToNBT(new NBTTagCompound());
                    c.setInteger("Slot", i);
                    hot.appendTag(c);
                    p.inventory.mainInventory.set(i, ItemStack.EMPTY);
                }
            }
            d.setTag("ward_hotbar", hot);
            p.sendMessage(new TextComponentString("\u00a7bYour Soul Ward breaks: hotbar, relics and coins are kept."));
            return;
        }
        int lose = Coins.balance(p) / 10;
        if (lose > 0) {
            Coins.spend(p, lose);
            p.sendMessage(new TextComponentString("\u00a7cYou lost " + lose + " Soul Coins (10%)."));
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent e) {
        EntityPlayer p = e.player;
        if (p.world.isRemote) {
            return;
        }
        NBTTagCompound d = Stats.data(p);
        d.removeTag("ward_now");
        NBTTagList hot = d.getTagList("ward_hotbar", 10);
        for (int i = 0; i < hot.tagCount(); i++) {
            NBTTagCompound c = hot.getCompoundTagAt(i);
            ItemStack s = new ItemStack(c);
            int slot = c.getInteger("Slot");
            if (slot >= 0 && slot < 9 && p.inventory.mainInventory.get(slot).isEmpty()) {
                p.inventory.mainInventory.set(slot, s);
            } else {
                give(p, s);
            }
        }
        d.removeTag("ward_hotbar");
    }

    // ------------------------------------------------------------------ Recall Scroll
    public static Item RECALL_SCROLL;

    public static void register(List<Item> all) {
        RECALL_SCROLL = new ItemRecall();
        RECALL_SCROLL.setRegistryName(UndyingArcana.MODID, "recall_scroll");
        RECALL_SCROLL.setUnlocalizedName(UndyingArcana.MODID + ".recall_scroll");
        RECALL_SCROLL.setCreativeTab(UndyingArcana.TAB);
        RECALL_SCROLL.setMaxStackSize(16);
        all.add(RECALL_SCROLL);
    }

    /** Read for 2 s, then return to your bed (or the world spawn). Overworld only. */
    public static class ItemRecall extends Item {
        @Override
        public int getMaxItemUseDuration(ItemStack s) {
            return 40;
        }

        @Override
        public EnumAction getItemUseAction(ItemStack s) {
            return EnumAction.BOW;
        }

        @Override
        public ActionResult<ItemStack> onItemRightClick(World w, EntityPlayer p, EnumHand hand) {
            if (p.dimension != 0) {
                if (!w.isRemote) {
                    p.sendStatusMessage(new TextComponentString("\u00a77The scroll only works in the Overworld."), true);
                }
                return new ActionResult<>(EnumActionResult.FAIL, p.getHeldItem(hand));
            }
            p.setActiveHand(hand);
            return new ActionResult<>(EnumActionResult.SUCCESS, p.getHeldItem(hand));
        }

        @Override
        public ItemStack onItemUseFinish(ItemStack s, World w, EntityLivingBase e) {
            if (!w.isRemote && e instanceof EntityPlayerMP) {
                EntityPlayerMP p = (EntityPlayerMP) e;
                BlockPos bed = p.getBedLocation(0);
                BlockPos to = bed != null ? EntityPlayer.getBedSpawnLocation(w, bed, p.isSpawnForced(0)) : null;
                if (to == null) {
                    to = w.getTopSolidOrLiquidBlock(w.getSpawnPoint());
                }
                Fx.at(w, Fx.PILLAR, Fx.ARCANE, p.posX, p.posY, p.posZ, 1.0);
                p.setPositionAndUpdate(to.getX() + 0.5, to.getY() + 0.1, to.getZ() + 0.5);
                Fx.sound(p, "minecraft:entity.endermen.teleport", 0.8F, 1.2F);
                if (!p.capabilities.isCreativeMode) {
                    s.shrink(1);
                }
            }
            return s;
        }
    }

    // ------------------------------------------------------------------ Soul Shrine
    public static final String[] BLESSING = {"Wisdom", "Fortune", "Vigor"};
    public static final String[] BLESSING_DESC = {"+25% XP", "+10% coins", "+20% regen"};
    public static final int[] GOAL = {1000, 1500, 1000};
    public static final int BLESS_TICKS = 72000;

    public static class ShrineData extends WorldSavedData {
        public int[] progress = new int[3];
        public long[] until = new long[3];
        public NBTTagCompound donors = new NBTTagCompound();

        public ShrineData(String name) {
            super(name);
        }

        @Override
        public void readFromNBT(NBTTagCompound n) {
            int[] pr = n.getIntArray("p");
            for (int i = 0; i < 3 && i < pr.length; i++) {
                progress[i] = pr[i];
                until[i] = n.getLong("u" + i);
            }
            donors = n.getCompoundTag("d");
        }

        @Override
        public NBTTagCompound writeToNBT(NBTTagCompound n) {
            n.setIntArray("p", progress);
            for (int i = 0; i < 3; i++) {
                n.setLong("u" + i, until[i]);
            }
            n.setTag("d", donors);
            return n;
        }
    }

    public static ShrineData shrine(World w) {
        World ow = w.getMinecraftServer() != null ? w.getMinecraftServer().getWorld(0) : w;
        ShrineData d = (ShrineData) ow.getMapStorage().getOrLoadData(ShrineData.class, "ua_shrine");
        if (d == null) {
            d = new ShrineData("ua_shrine");
            ow.getMapStorage().setData("ua_shrine", d);
        }
        return d;
    }

    /** Is blessing b on right now (server side)? */
    public static boolean blessed(World w, int b) {
        if (w.isRemote || w.getMinecraftServer() == null) {
            return false;
        }
        return w.getMinecraftServer().getWorld(0).getTotalWorldTime() < shrine(w).until[b];
    }

    static void donate(EntityPlayerMP p, int b, int amount) {
        if (b < 0 || b > 2 || amount <= 0 || amount > 100000) {
            return;
        }
        ShrineData d = shrine(p.world);
        long now = p.getServer().getWorld(0).getTotalWorldTime();
        if (now < d.until[b]) {
            p.sendStatusMessage(new TextComponentString("\u00a77" + BLESSING[b] + " is already active."), true);
            return;
        }
        amount = Math.min(amount, GOAL[b] - d.progress[b]);
        if (!Coins.spend(p, amount)) {
            p.sendStatusMessage(new TextComponentString("\u00a7cNot enough Soul Coins."), true);
            return;
        }
        d.progress[b] += amount;
        NBTTagCompound don = d.donors.getCompoundTag("b" + b);
        don.setInteger(p.getName(), don.getInteger(p.getName()) + amount);
        d.donors.setTag("b" + b, don);
        if (d.progress[b] >= GOAL[b]) {
            d.progress[b] = 0;
            d.until[b] = now + BLESS_TICKS;
            d.donors.removeTag("b" + b);
            for (EntityPlayer o : p.getServer().getPlayerList().getPlayers()) {
                o.sendMessage(new TextComponentString("\u00a76The Soul Shrine grants " + BLESSING[b] + " (" + BLESSING_DESC[b] + ") to everyone for 1 hour!"));
            }
        }
        d.markDirty();
        Fx.sound(p.world, p.posX, p.posY, p.posZ, "minecraft:block.note.chime", 0.8F, 1.4F);
    }

    // ------------------------------------------------------------------ screens state (JSON to the client)
    public static final class State {
        public List<MarketOffer> market;
        public int restock;
        public int[] svc;
        public int[] prog = new int[3], goal = GOAL, left = new int[3];
        public String[] top = new String[3];
    }

    public static String state(EntityPlayer p) {
        State s = new State();
        s.market = market(p);
        s.restock = restock(p.world);
        s.svc = services(p);
        ShrineData d = shrine(p.world);
        long now = p.getServer() != null ? p.getServer().getWorld(0).getTotalWorldTime() : 0;
        for (int b = 0; b < 3; b++) {
            s.prog[b] = d.progress[b];
            s.left[b] = (int) Math.max(0, d.until[b] - now);
            NBTTagCompound don = d.donors.getCompoundTag("b" + b);
            List<String> names = new ArrayList<>(don.getKeySet());
            names.sort((x, y) -> don.getInteger(y) - don.getInteger(x));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < Math.min(3, names.size()); i++) {
                sb.append(i > 0 ? ", " : "").append(names.get(i)).append(' ').append(don.getInteger(names.get(i)));
            }
            s.top[b] = sb.toString();
        }
        return GSON.toJson(s);
    }

    public static void sendState(EntityPlayerMP p) {
        SkillNet.NET.sendTo(new SkillNet.MsgEcon(state(p)), p);
    }

    /**
     * Client actions (MsgEconAct): 1 market buy, 2 service, 3 shrine donate, 4 refresh, 5 Relic Forge combine
     * (a = kept slot, b = the two other slots packed as low byte | high byte << 8), 6 Relic Forge reroll (a = slot).
     */
    static void act(EntityPlayerMP p, int op, int a, int b) {
        if (!Stats.on(p.world)) {
            return;
        }
        if (op == 1) {
            buyMarket(p, a);
        } else if (op == 2) {
            service(p, a);
        } else if (op == 3) {
            donate(p, a, b);
        } else if (op == 5) {
            RelicAltar.combine(p, a, b & 0xFF, b >> 8 & 0xFF);
            p.inventoryContainer.detectAndSendChanges();
        } else if (op == 6) {
            RelicAltar.reroll(p, a);
            p.inventoryContainer.detectAndSendChanges();
        }
        sendState(p);
    }

    // ------------------------------------------------------------------ /pay
    public static class PayCommand extends CommandBase {
        @Override
        public String getName() {
            return "pay";
        }

        @Override
        public String getUsage(ICommandSender s) {
            return "/pay <player> <amount>";
        }

        @Override
        public int getRequiredPermissionLevel() {
            return 0;
        }

        @Override
        public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
            return true;
        }

        @Override
        public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
            if (args.length != 2) {
                throw new WrongUsageException(getUsage(sender));
            }
            EntityPlayerMP from = getCommandSenderAsPlayer(sender);
            EntityPlayerMP to = getPlayer(server, sender, args[0]);
            int n = parseInt(args[1], 1);
            if (to == from) {
                return;
            }
            if (!Coins.spend(from, n)) {
                from.sendMessage(new TextComponentString("\u00a7cYou have only " + Coins.balance(from) + " Soul Coins."));
                return;
            }
            Coins.add(to, n);
            from.sendMessage(new TextComponentString("\u00a76Sent " + n + " Soul Coins to " + to.getName() + "."));
            to.sendMessage(new TextComponentString("\u00a76" + from.getName() + " sent you " + n + " Soul Coins."));
        }

        @Override
        public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, BlockPos pos) {
            return args.length == 1 ? getListOfStringsMatchingLastWord(args, server.getOnlinePlayerNames()) : java.util.Collections.emptyList();
        }
    }
}
