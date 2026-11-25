package com.imba.undyingarcana;

import java.util.List;
import java.util.Map;

import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.MobEffects;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerDropsEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Relics: Undying Arcana's own accessories (replace Baubles and its add-ons).
 *
 * 18 relic slots are added to the player's inventory container (ContainerPlayer, after its 46 vanilla slots), so the
 * vanilla inventory screen shows them in a panel on its left (RelicClient draws the panel). Contents are kept in the
 * player's persisted data ("relics") and drop on death like armor (unless keepInventory).
 *
 * Slot kinds: Circlet, Earring, Amulet, Cloak, Belt, Bracers, Gloves, Anklet, Sigil, 2 Rings, 2 Trinkets, 3 Charms and
 * 2 active Relics (keys R and B). Ten slots are open from the start; eight unlock with level (SLOT_LEVEL).
 * Relics are gear: they roll a quality (Common..Legendary) with jewel sockets like weapons, and their base stat is a
 * gear stat that grows with quality. Each relic also has its own effect; three sets give bonuses at 2 and 3 pieces.
 */
@Mod.EventBusSubscriber(modid = UndyingArcana.MODID)
public final class Relics {
    // ------------------------------------------------------------------ slots
    public static final int CIRCLET = 0, EARRING = 1, AMULET = 2, CLOAK = 3, BELT = 4, BRACERS = 5, GLOVES = 6, ANKLET = 7, SIGIL = 8,
            RING = 9, TRINKET = 10, CHARM = 11, RELIC = 12, LOCK = 13;
    public static final String[] KIND = {"circlet", "earring", "amulet", "cloak", "belt", "bracers", "gloves", "anklet", "sigil", "ring",
            "trinket", "charm", "relic", "lock"};
    public static final String[] KIND_NAME = {"Circlet", "Earring", "Amulet", "Cloak", "Belt", "Bracers", "Gloves", "Anklet", "Sigil", "Ring",
            "Trinket", "Charm", "Active Relic"};
    public static final int SLOTS = 18, FIRST = 46;
    /** Kind of each relic slot (3 columns x 6 rows in the panel). */
    public static final int[] SLOT_KIND = {CIRCLET, EARRING, AMULET, CLOAK, BELT, BRACERS, GLOVES, ANKLET, SIGIL, RING, RING, TRINKET,
            CHARM, CHARM, CHARM, RELIC, RELIC, TRINKET};
    /** Level that opens each slot (0 = open from the start). */
    public static final int[] SLOT_LEVEL = {0, 15, 0, 0, 0, 30, 0, 0, 40, 0, 20, 0, 0, 25, 50, 0, 60, 35};
    /** Slots of the two active relics (keys R and B). */
    public static final int ACTIVE_1 = 15, ACTIVE_2 = 16;

    // ------------------------------------------------------------------ relic definitions
    public static final int PYRO = 0, WARDEN = 1, DUELIST = 2;
    public static final String[] SET = {"Pyromancer", "Warden", "Duelist"};
    public static final String[][] SET_BONUS = {{"Fire spells +10%", "Immune to fire"}, {"+6 Defense", "+14 more Defense"},
            {"Focus regenerates 50% faster", "Weapon skills +10% damage"}};

    public static final class Def {
        public final String id, name, desc;
        final int kind, stat, stat2, element, set, cooldown;
        final float scale;

        Def(String id, String name, int kind, int stat, int stat2, float scale, int set, int element, int cooldown, String desc) {
            this.id = id;
            this.name = name;
            this.kind = kind;
            this.stat = stat;
            this.stat2 = stat2;
            this.scale = scale;
            this.set = set;
            this.element = element;
            this.cooldown = cooldown;
            this.desc = desc;
        }
    }

    private static Def d(String id, String name, int kind, int stat, float scale, String desc) {
        return new Def(id, name, kind, stat, -1, scale, -1, -1, 0, desc);
    }

    private static Def set(Def x, int set) {
        return new Def(x.id, x.name, x.kind, x.stat, x.stat2, x.scale, set, x.element, x.cooldown, x.desc);
    }

    private static Def two(Def x, int stat2) {
        return new Def(x.id, x.name, x.kind, x.stat, stat2, x.scale, x.set, x.element, x.cooldown, x.desc);
    }

    private static Def active(String id, String name, int cdSeconds, String desc) {
        return new Def(id, name, RELIC, -1, -1, 0, -1, -1, cdSeconds * 20, desc);
    }

    // effect ids = index in DEFS
    public static final int CIRCLET_RINGS = 0, SAGE_DIADEM = 1, CROWN_EMBERS = 2, WHISPER_EARRING = 3, HUNTER_EARRING = 4,
            AMULET_AWAKENED = 5, AMULET_FUSION = 6, PHOENIX = 7, UNDYING = 8, HEARTH_CLOAK = 9, FROST_CLOAK = 10, SHADOW_CLOAK = 11,
            WIND_CLOAK = 12, WATERSKIN_BELT = 13, PROVISIONER_BELT = 14, CHAMPION_BELT = 15, DUELIST_BRACERS = 16, IRON_BRACERS = 17,
            SWIFT_GLOVES = 18, MIGHT_GLOVES = 19, SPELLWEAVER_GLOVES = 20, STEP_ANKLET = 21, LEAPER_ANKLET = 22, SIGIL_FIRST = 23,
            SIGIL_AWAKENING = 31, RING_FIRST = 32, LUCKY_COIN = 38, POCKET_WATCH = 39, MINER_LANTERN = 40, MAGNET_STONE = 41,
            CHARM_FIREPROOF = 42, CHARM_ANTIDOTE = 43, CHARM_ANCHOR = 44, CHARM_CLARITY = 45, CHARM_LIFELINE = 46, ACTIVE_FIRST = 47,
            STORM = 47, SANCTUARY = 48, HOURGLASS = 49, VOID = 50, WINDSTEP = 51, WELLSPRING = 52;

    public static final Def[] DEFS;

    static {
        java.util.List<Def> l = new java.util.ArrayList<>();
        l.add(d("circlet_rings", "Circlet of Rings", CIRCLET, Gear.INTENSITY, 1, "+1 magic ring on spells that charge"));
        l.add(d("sage_diadem", "Sage's Diadem", CIRCLET, Gear.SPEN, 1, "+20 max mana, mana regenerates 25% faster"));
        l.add(set(d("crown_embers", "Crown of Embers", CIRCLET, Gear.INTENSITY, 1, "Fire spells +15% damage"), PYRO));
        l.add(d("whisper_earring", "Whisper Earring", EARRING, Gear.SPEED, 1, "Spell cooldowns -10%"));
        l.add(set(d("hunter_earring", "Hunter's Earring", EARRING, Gear.CRIT, 2, "+20% damage to elites and bosses"), DUELIST));
        l.add(d("amulet_awakened", "Amulet of the Awakened", AMULET, Gear.POWER, 1, "Awakening lasts 3 s longer"));
        l.add(d("amulet_fusion", "Amulet of Fusion", AMULET, Gear.INTENSITY, 1, "Fusion spells +20% damage"));
        l.add(d("phoenix_amulet", "Phoenix Amulet", AMULET, Gear.HEALTH, 2,
                "Once every 5 min a killing blow leaves you at 30% health and bursts into flame"));
        l.add(d("undying_amulet", "Amulet of the Undying", AMULET, Gear.LEECH, 1, "Every kill heals 2 health"));
        l.add(set(d("hearth_cloak", "Hearth Cloak", CLOAK, Gear.DEFENSE, 1, "Keeps you warm in the cold"), PYRO));
        l.add(d("frost_cloak", "Frostweave Cloak", CLOAK, Gear.DEFENSE, 1, "Keeps you cool in the heat"));
        l.add(d("shadow_cloak", "Shadow Cloak", CLOAK, Gear.AGILITY, 2, "While sneaking, zombies lose track of you"));
        l.add(d("wind_cloak", "Wind Cloak", CLOAK, Gear.AGILITY, 2, "-50% fall damage"));
        l.add(d("waterskin_belt", "Waterskin Belt", BELT, Gear.HEALTH, 1, "Thirst drains 30% slower"));
        l.add(d("provisioner_belt", "Provisioner's Belt", BELT, Gear.FIND, 1, "Food gives +2 hunger and +2 saturation"));
        l.add(set(d("champion_belt", "Champion's Belt", BELT, Gear.HEALTH, 2, "+50% knockback resistance"), WARDEN));
        l.add(set(d("duelist_bracers", "Duelist Bracers", BRACERS, Gear.SPEED, 1, "Weapon skills cost 15% less focus"), DUELIST));
        l.add(set(d("iron_bracers", "Iron Bracers", BRACERS, Gear.DEFENSE, 2, "Unarmed and martial hits +12% damage"), WARDEN));
        l.add(set(d("swift_gloves", "Gloves of Swiftness", GLOVES, Gear.SPEED, 1, "Quick skill cooldown -20%"), DUELIST));
        l.add(d("might_gloves", "Gloves of Might", GLOVES, Gear.POWER, 2, "Melee and martial hits +8% damage"));
        l.add(d("spellweaver_gloves", "Spellweaver Gloves", GLOVES, Gear.INTENSITY, 1, "Magic rings form 25% faster"));
        l.add(d("step_anklet", "Anklet of Stepping", ANKLET, Gear.AGILITY, 1, "Walk up full blocks"));
        l.add(d("leaper_anklet", "Leaper's Anklet", ANKLET, Gear.AGILITY, 1, "Jump Boost I, -50% fall damage"));
        for (int e = 0; e < 8; e++) {
            Def x = new Def("sigil_" + Magic.ELEMENT[e], "Sigil of " + Magic.ELEMENT_NAME[e], SIGIL, Gear.INTENSITY, -1, 1,
                    e == 0 ? PYRO : -1, e, 0, Magic.ELEMENT_NAME[e] + " spells +15% damage");
            l.add(x);
        }
        l.add(d("sigil_awakening", "Sigil of Awakening", SIGIL, Gear.POWER, 1, "Awakening effects +20%"));
        l.add(d("ring_power", "Ring of Power", RING, Gear.POWER, 3, ""));
        l.add(two(d("ring_precision", "Ring of Precision", RING, Gear.CRIT, 2, ""), Gear.CRITDMG));
        l.add(d("ring_vigor", "Ring of Vigor", RING, Gear.HEALTH, 3, ""));
        l.add(d("ring_leech", "Ring of the Leech", RING, Gear.LEECH, 2, ""));
        l.add(d("ring_haste", "Ring of Haste", RING, Gear.SPEED, 2, ""));
        l.add(two(d("ring_piercing", "Ring of Piercing", RING, Gear.APEN, 2, ""), Gear.SPEN));
        l.add(d("lucky_coin", "Lucky Coin", TRINKET, Gear.FIND, 3, "+10% Soul Coins"));
        l.add(d("pocket_watch", "Pocket Watch", TRINKET, Gear.SPEED, 1, "Awakening cooldown -20%"));
        l.add(d("miner_lantern", "Miner's Lantern", TRINKET, Gear.DEFENSE, 1, "Night vision"));
        l.add(d("magnet_stone", "Magnet Stone", TRINKET, Gear.FIND, 1, "Pulls dropped items within 6 blocks"));
        l.add(d("charm_fireproof", "Fireproof Charm", CHARM, Gear.DEFENSE, 1, "Immune to fire and lava"));
        l.add(d("charm_antidote", "Antidote Charm", CHARM, Gear.HEALTH, 1, "Immune to poison and wither"));
        l.add(set(d("charm_anchor", "Anchor Charm", CHARM, Gear.DEFENSE, 1, "Immune to knockback"), WARDEN));
        l.add(d("charm_clarity", "Clarity Charm", CHARM, Gear.SPEN, 1, "Immune to blindness, nausea and mining fatigue"));
        l.add(d("charm_lifeline", "Lifeline Charm", CHARM, Gear.HEALTH, 1, "Breathe underwater"));
        l.add(active("relic_storm", "Stormcaller Relic", 30, "Lightning strikes up to 5 enemies within 16 blocks"));
        l.add(active("relic_sanctuary", "Sanctuary Relic", 60, "Heals you and allies within 8 blocks for 30% health"));
        l.add(active("relic_hourglass", "Hourglass Relic", 120, "Resets your weapon skill and spell cooldowns"));
        l.add(active("relic_void", "Void Relic", 40, "Pulls enemies within 10 blocks to you and slows them"));
        l.add(active("relic_windstep", "Windstep Relic", 20, "Dash 8 blocks forward, then Speed II for 5 s"));
        l.add(active("relic_wellspring", "Wellspring Relic", 90, "Restores all your mana and focus"));
        DEFS = l.toArray(new Def[0]);
    }

    public static final Item[] ITEMS = new Item[DEFS.length];
    private static final float[] QMUL = {1F, 1.5F, 2F, 2.75F, 3.5F};

    private Relics() {
    }

    public static void register(List<Item> all) {
        for (int i = 0; i < DEFS.length; i++) {
            ItemRelic it = new ItemRelic(i);
            it.setRegistryName(UndyingArcana.MODID, DEFS[i].id);
            it.setUnlocalizedName(UndyingArcana.MODID + ".relic");
            it.setCreativeTab(UndyingArcana.TAB);
            it.setMaxStackSize(1);
            ITEMS[i] = it;
            all.add(it);
        }
    }

    /** Definition index of a relic stack, or -1. */
    public static int defOf(ItemStack s) {
        return !s.isEmpty() && s.getItem() instanceof ItemRelic ? ((ItemRelic) s.getItem()).def : -1;
    }

    public static boolean isRelic(ItemStack s) {
        return defOf(s) >= 0;
    }

    /** Display name of a relic definition's kind ("Ring", "Cloak", ...). */
    public static String kindName(int def) {
        return KIND_NAME[DEFS[def].kind];
    }

    /** Other relic definitions of the same kind (Relic Forge reroll). */
    public static java.util.List<Integer> sameKind(int def) {
        java.util.List<Integer> out = new java.util.ArrayList<>();
        for (int i = 0; i < DEFS.length; i++) {
            if (i != def && DEFS[i].kind == DEFS[def].kind) {
                out.add(i);
            }
        }
        return out;
    }

    public static ItemStack random(java.util.Random r) {
        return new ItemStack(ITEMS[r.nextInt(ITEMS.length)]);
    }

    /** Base stats of a relic (quality scales them); used by Gear.addStats. */
    public static void baseStats(ItemStack s, float[] out) {
        int i = defOf(s);
        if (i < 0) {
            return;
        }
        Def x = DEFS[i];
        float v = Math.round(x.scale * QMUL[Gear.quality(s)]);
        if (x.stat >= 0) {
            out[x.stat] += v;
        }
        if (x.stat2 >= 0) {
            out[x.stat2] += v;
        }
    }

    // ------------------------------------------------------------------ slot unlocks
    public static boolean open(EntityPlayer p, int slot) {
        return !Stats.on(p.world) || Stats.level(p) >= SLOT_LEVEL[slot];
    }

    /** Bit mask of the open slots (sent to the client for the panel). */
    public static int openMask(EntityPlayer p) {
        int m = 0;
        for (int i = 0; i < SLOTS; i++) {
            if (open(p, i)) {
                m |= 1 << i;
            }
        }
        return m;
    }

    // ------------------------------------------------------------------ inventory
    /** The relic slots of one player entity; saved into its persisted data on every change (server side). */
    public static final class Inv extends InventoryBasic {
        final EntityPlayer owner;
        boolean loading;

        Inv(EntityPlayer owner) {
            super("Relics", false, SLOTS);
            this.owner = owner;
        }

        @Override
        public void markDirty() {
            super.markDirty();
            if (!loading && owner != null && !owner.world.isRemote) {
                save(owner, this);
                DIRTY.add(owner.getUniqueID());
            }
        }

        @Override
        public int getInventoryStackLimit() {
            return 1;
        }
    }

    private static final java.util.Set<java.util.UUID> DIRTY = new java.util.HashSet<>();

    /** The relic inventory of a player: the one behind its injected container slots (each side has its own). */
    public static Inv inv(EntityPlayer p) {
        net.minecraft.inventory.Container c = p.inventoryContainer;
        if (c != null && c.inventorySlots.size() > FIRST && c.inventorySlots.get(FIRST) instanceof RelicSlot) {
            return (Inv) c.inventorySlots.get(FIRST).inventory;
        }
        return fresh(p); // not injected yet: a loaded copy
    }

    private static Inv fresh(EntityPlayer p) {
        Inv v = new Inv(p);
        if (!p.world.isRemote) {
            load(p, v);
        }
        return v;
    }

    private static void load(EntityPlayer p, Inv v) {
        v.loading = true;
        NBTTagList l = Stats.data(p).getTagList("relics", 10);
        for (int i = 0; i < l.tagCount(); i++) {
            NBTTagCompound c = l.getCompoundTagAt(i);
            int s = c.getInteger("s");
            if (s >= 0 && s < SLOTS) {
                v.setInventorySlotContents(s, new ItemStack(c.getCompoundTag("i")));
            }
        }
        v.loading = false;
    }

    private static void save(EntityPlayer p, Inv v) {
        NBTTagList l = new NBTTagList();
        for (int i = 0; i < SLOTS; i++) {
            ItemStack s = v.getStackInSlot(i);
            if (!s.isEmpty()) {
                NBTTagCompound c = new NBTTagCompound();
                c.setInteger("s", i);
                c.setTag("i", s.writeToNBT(new NBTTagCompound()));
                l.appendTag(c);
            }
        }
        Stats.data(p).setTag("relics", l);
    }

    /** Equipped relic stacks (empty ones skipped). */
    public static java.util.List<ItemStack> equipped(EntityPlayer p) {
        java.util.List<ItemStack> out = new java.util.ArrayList<>();
        Inv v = inv(p);
        for (int i = 0; i < SLOTS; i++) {
            ItemStack s = v.getStackInSlot(i);
            if (!s.isEmpty()) {
                out.add(s);
            }
        }
        return out;
    }

    /** A relic slot inside the player's inventory container. */
    public static final class RelicSlot extends Slot {
        public final int index;
        final EntityPlayer player;

        RelicSlot(Inv inv, int index, EntityPlayer player) {
            super(inv, index, -62 + index % 3 * 18, 19 + index / 3 * 18);
            this.index = index;
            this.player = player;
        }

        public boolean open() {
            return player.world.isRemote ? (clientMask & 1 << index) != 0 : Relics.open(player, index);
        }

        @Override
        public boolean isItemValid(ItemStack s) {
            int d = defOf(s);
            return d >= 0 && DEFS[d].kind == SLOT_KIND[index] && open();
        }

        @Override
        public int getSlotStackLimit() {
            return 1;
        }

        @SideOnly(Side.CLIENT)
        @Override
        public String getSlotTexture() {
            return UndyingArcana.MODID + ":gui/relic_slot/" + (open() ? KIND[SLOT_KIND[index]] : "lock");
        }

        @SideOnly(Side.CLIENT)
        @Override
        public boolean isEnabled() {
            return RelicClient.slotsShown();
        }
    }

    /** Client copy of the open-slot mask (from MsgPower). */
    public static int clientMask = (1 << SLOTS) - 1;

    /** Adds the relic slots to a player's inventory container once (both sides, every new player entity). */
    static void inject(EntityPlayer p) {
        net.minecraft.inventory.Container c = p.inventoryContainer;
        if (c == null || c.inventorySlots.size() != FIRST) {
            return; // already done, or another mod changed the container
        }
        Inv v = fresh(p);
        for (int i = 0; i < SLOTS; i++) {
            Slot s = new RelicSlot(v, i, p);
            s.slotNumber = c.inventorySlots.size();
            c.inventorySlots.add(s);
            c.inventoryItemStacks.add(ItemStack.EMPTY);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onJoin(EntityJoinWorldEvent e) {
        if (e.getEntity() instanceof EntityPlayer) {
            inject((EntityPlayer) e.getEntity());
        }
    }

    /** Puts a relic from the player's inventory into its first free matching slot; true when equipped. */
    public static boolean equip(EntityPlayer p, ItemStack s) {
        int d = defOf(s);
        if (d < 0) {
            return false;
        }
        Inv v = inv(p);
        boolean anyKind = false;
        for (int i = 0; i < SLOTS; i++) {
            if (SLOT_KIND[i] != DEFS[d].kind) {
                continue;
            }
            anyKind = true;
            if (open(p, i) && v.getStackInSlot(i).isEmpty()) {
                v.setInventorySlotContents(i, s.splitStack(1));
                return true;
            }
        }
        if (anyKind) {
            p.sendStatusMessage(new TextComponentString("\u00a77No free " + KIND_NAME[DEFS[d].kind] + " slot (E)."), true);
        }
        return false;
    }

    @SubscribeEvent
    public static void onDrops(PlayerDropsEvent e) {
        EntityPlayer p = e.getEntityPlayer();
        if (p.world.isRemote || p.world.getGameRules().getBoolean("keepInventory") || Stats.data(p).getBoolean("ward_now")) {
            return; // keepInventory or a Soul Ward: relics stay worn
        }
        Inv v = inv(p);
        for (int i = 0; i < SLOTS; i++) {
            ItemStack s = v.removeStackFromSlot(i);
            if (!s.isEmpty()) {
                EntityItem it = new EntityItem(p.world, p.posX, p.posY + p.getEyeHeight(), p.posZ, s);
                it.setPickupDelay(40);
                e.getDrops().add(it);
            }
        }
        save(p, v);
    }

    // ------------------------------------------------------------------ worn effects
    private static final Map<java.util.UUID, boolean[]> HAS = new java.util.HashMap<>();
    private static final Map<java.util.UUID, int[]> SETS = new java.util.HashMap<>();

    /** Recomputes which relic effects and set bonuses a player has (called with Gear.recompute). */
    static void recompute(EntityPlayer p, float[] gear) {
        boolean[] h = new boolean[DEFS.length];
        int[] sets = new int[SET.length];
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        for (ItemStack s : equipped(p)) {
            int d = defOf(s);
            if (d < 0) {
                continue;
            }
            h[d] = true;
            if (DEFS[d].set >= 0 && seen.add(d)) {
                sets[DEFS[d].set]++;
            }
        }
        if (sets[WARDEN] >= 2) {
            gear[Gear.DEFENSE] += 6;
        }
        if (sets[WARDEN] >= 3) {
            gear[Gear.DEFENSE] += 14;
        }
        HAS.put(p.getUniqueID(), h);
        SETS.put(p.getUniqueID(), sets);
    }

    public static boolean has(EntityPlayer p, int def) {
        boolean[] h = HAS.get(p.getUniqueID());
        if (h == null) {
            Gear.recompute(p);
            h = HAS.get(p.getUniqueID());
        }
        return h != null && h[def];
    }

    public static int set(EntityPlayer p, int set) {
        int[] s = SETS.get(p.getUniqueID());
        return s == null ? 0 : s[set];
    }

    /** Spell damage multiplier for an element (Crown of Embers, Sigils, Pyromancer set). */
    public static float elementMult(EntityPlayer p, int el) {
        if (el < 0 || el >= 8) {
            return 1F;
        }
        float m = 1F;
        if (has(p, SIGIL_FIRST + el)) {
            m *= 1.15F;
        }
        if (el == 0) {
            if (has(p, CROWN_EMBERS)) {
                m *= 1.15F;
            }
            if (set(p, PYRO) >= 2) {
                m *= 1.10F;
            }
        }
        return m;
    }

    /** Most magic rings a spell can charge with this player's relics and catalyst (at most 6). */
    public static int maxRings(EntityPlayer p, int graphRings) {
        if (graphRings <= 0) {
            return 0;
        }
        return Math.min(6, graphRings + (has(p, CIRCLET_RINGS) ? 1 : 0) + Catalyst.extraRings(p));
    }

    /** Ticks per magic ring for this player (Spellweaver Gloves, Wand). */
    public static int ringTicks(EntityPlayer p) {
        return Math.max(5, Math.round((has(p, SPELLWEAVER_GLOVES) ? 9 : Magic.RING_TICKS) * Catalyst.ringTickMult(p)));
    }

    /** Temperature change from cloaks: warm pulls up toward neutral, cool pulls down. */
    public static float temperature(EntityPlayer p, float t, float neutral) {
        if (t < neutral && has(p, HEARTH_CLOAK)) {
            t = Math.min(neutral, t + 4F);
        }
        if (t > neutral && has(p, FROST_CLOAK)) {
            t = Math.max(neutral, t - 4F);
        }
        return t;
    }

    private static boolean elite(Entity t) {
        return t instanceof EntityLSBoss || t.getTags().contains("ua_warlord") || t instanceof EntityUAZombie && ((EntityUAZombie) t).isElite();
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onHurt(LivingHurtEvent e) {
        EntityLivingBase t = e.getEntityLiving();
        if (t.world.isRemote) {
            return;
        }
        Entity src = e.getSource().getTrueSource();
        if (src instanceof EntityPlayer && src != t) {
            EntityPlayer p = (EntityPlayer) src;
            boolean melee = e.getSource().getImmediateSource() == p && !Pierce.isMagic(e.getSource());
            if (has(p, HUNTER_EARRING) && elite(t)) {
                e.setAmount(e.getAmount() * 1.2F);
            }
            if (melee && has(p, MIGHT_GLOVES)) {
                e.setAmount(e.getAmount() * 1.08F);
            }
            if (melee && has(p, IRON_BRACERS) && (p.getHeldItemMainhand().isEmpty() || Martial.isMartial(p.getHeldItemMainhand()))) {
                e.setAmount(e.getAmount() * 1.12F);
            }
        }
    }

    @SubscribeEvent
    public static void onFall(LivingFallEvent e) {
        if (e.getEntityLiving() instanceof EntityPlayer && !e.getEntityLiving().world.isRemote) {
            EntityPlayer p = (EntityPlayer) e.getEntityLiving();
            if (has(p, WIND_CLOAK)) {
                e.setDamageMultiplier(e.getDamageMultiplier() * 0.5F);
            }
            if (has(p, LEAPER_ANKLET)) {
                e.setDamageMultiplier(e.getDamageMultiplier() * 0.5F);
            }
        }
    }

    @SubscribeEvent
    public static void onEat(LivingEntityUseItemEvent.Finish e) {
        if (e.getEntityLiving() instanceof EntityPlayer && !e.getEntityLiving().world.isRemote && e.getItem().getItem() instanceof ItemFood) {
            EntityPlayer p = (EntityPlayer) e.getEntityLiving();
            if (has(p, PROVISIONER_BELT)) {
                p.getFoodStats().addStats(2, 0.5F);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onDeath(LivingDeathEvent e) {
        EntityLivingBase t = e.getEntityLiving();
        if (t.world.isRemote) {
            return;
        }
        if (t instanceof EntityPlayer && has((EntityPlayer) t, PHOENIX)) {
            EntityPlayer p = (EntityPlayer) t;
            NBTTagCompound d = Stats.data(p);
            long now = p.world.getTotalWorldTime();
            if (now >= d.getLong("phoenix")) {
                e.setCanceled(true);
                d.setLong("phoenix", now + 6000);
                p.setHealth(p.getMaxHealth() * 0.3F);
                p.extinguish();
                for (EntityLivingBase o : SkillShapes.around(p.world, p.posX, p.posY, p.posZ, 5)) {
                    if (o != p && SkillShapes.isTarget(o)) {
                        o.setFire(5);
                        o.knockBack(p, 1.2F, p.posX - o.posX, p.posZ - o.posZ);
                    }
                }
                Fx.at(p.world, Fx.NOVA, Fx.FIRE, p.posX, p.posY + 1, p.posZ, 2.0);
                Fx.sound(p, "minecraft:entity.blaze.shoot", 1F, 0.7F);
                p.sendStatusMessage(new TextComponentString("\u00a76The Phoenix Amulet saves you!"), true);
                return;
            }
        }
        Entity src = e.getSource().getTrueSource();
        if (src instanceof EntityPlayer && src != t && has((EntityPlayer) src, UNDYING)) {
            ((EntityPlayer) src).heal(2F);
        }
    }

    private static final java.util.UUID KB_ID = java.util.UUID.fromString("7c1e2f50-3b4a-4d6e-9f80-a1b2c3d4e5f6");

    private static void keep(EntityPlayer p, Potion potion, int amp) {
        PotionEffect cur = p.getActivePotionEffect(potion);
        if (cur == null || cur.getDuration() < 220) {
            p.addPotionEffect(new PotionEffect(potion, 400, amp, true, false));
        }
    }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent e) {
        EntityPlayer p = e.player;
        if (e.phase != TickEvent.Phase.END || p.world.isRemote || !(p instanceof EntityPlayerMP)) {
            return;
        }
        if (DIRTY.remove(p.getUniqueID())) {
            Gear.recompute(p); // relic changed: stats and effects now
        }
        if (has(p, STEP_ANKLET)) {
            p.stepHeight = Math.max(p.stepHeight, 1.0F);
        } else if (p.stepHeight == 1.0F) {
            p.stepHeight = 0.6F;
        }
        if (has(p, MAGNET_STONE) && p.ticksExisted % 4 == 0 && !p.isSneaking()) {
            for (EntityItem it : p.world.getEntitiesWithinAABB(EntityItem.class, p.getEntityBoundingBox().grow(6))) {
                if (!it.isDead && !it.cannotPickup()) {
                    Vec3d d = new Vec3d(p.posX - it.posX, p.posY + 0.5 - it.posY, p.posZ - it.posZ).normalize().scale(0.35);
                    it.motionX = d.x;
                    it.motionY = d.y;
                    it.motionZ = d.z;
                }
            }
        }
        if (p.ticksExisted % 10 != 5) {
            return;
        }
        double kb = (has(p, CHAMPION_BELT) ? 0.5 : 0) + (has(p, CHARM_ANCHOR) ? 1.0 : 0);
        Stats.setModifier(p.getEntityAttribute(SharedMonsterAttributes.KNOCKBACK_RESISTANCE), KB_ID, "UA relics", Math.min(1.0, kb), 0);
        if (has(p, LEAPER_ANKLET)) {
            keep(p, MobEffects.JUMP_BOOST, 0);
        }
        if (has(p, MINER_LANTERN)) {
            keep(p, MobEffects.NIGHT_VISION, 0);
        }
        if (has(p, CHARM_FIREPROOF) || set(p, PYRO) >= 3) {
            keep(p, MobEffects.FIRE_RESISTANCE, 0);
        }
        if (has(p, CHARM_LIFELINE)) {
            keep(p, MobEffects.WATER_BREATHING, 0);
        }
        if (has(p, CHARM_ANTIDOTE)) {
            p.removePotionEffect(MobEffects.POISON);
            p.removePotionEffect(MobEffects.WITHER);
        }
        if (has(p, CHARM_CLARITY)) {
            p.removePotionEffect(MobEffects.BLINDNESS);
            p.removePotionEffect(MobEffects.NAUSEA);
            p.removePotionEffect(MobEffects.MINING_FATIGUE);
        }
        if (has(p, SHADOW_CLOAK) && p.isSneaking()) {
            for (EntityLiving m : p.world.getEntitiesWithinAABB(EntityLiving.class, p.getEntityBoundingBox().grow(16),
                    x -> x instanceof IMob && x.getAttackTarget() == p)) {
                if (p.getRNG().nextFloat() < 0.35F) {
                    m.setAttackTarget(null);
                }
            }
        }
    }

    // ------------------------------------------------------------------ active relics (keys R / B)
    public static long activeLeft(EntityPlayer p, int which) {
        return Math.max(0, Stats.data(p).getLong("rcd" + which) - p.world.getTotalWorldTime());
    }

    public static int activeTotal(EntityPlayer p, int which) {
        return Math.max(1, Stats.data(p).getInteger("rcdt" + which));
    }

    /** Client requests (MsgRelic). */
    static void onMessage(EntityPlayerMP p, int op, int n) {
        if (op == 1) {
            activate(p, n);
        } else if (op == 0 && n >= 0 && n < FIRST) {
            // shift-click from the inventory: equip that stack
            Slot s = p.inventoryContainer.getSlot(n);
            ItemStack st = s.getStack();
            if (isRelic(st)) {
                if (!Gear.rolled(st)) {
                    Gear.roll(st, p.getRNG(), Stats.stats(p)[Builds.LUCK]);
                }
                if (equip(p, st)) {
                    s.onSlotChanged();
                    p.inventoryContainer.detectAndSendChanges();
                }
            }
        }
    }

    public static void activate(EntityPlayerMP p, int which) {
        if (which < 0 || which > 1) {
            return;
        }
        int slot = which == 0 ? ACTIVE_1 : ACTIVE_2;
        if (!open(p, slot)) {
            p.sendStatusMessage(new TextComponentString("\u00a77The second relic slot opens at level " + SLOT_LEVEL[slot] + "."), true);
            return;
        }
        ItemStack s = inv(p).getStackInSlot(slot);
        int d = defOf(s);
        if (d < 0 || DEFS[d].kind != RELIC) {
            p.sendStatusMessage(new TextComponentString("\u00a77No active relic in that slot (E)."), true);
            return;
        }
        long left = activeLeft(p, which);
        if (left > 0 && !p.capabilities.isCreativeMode) {
            p.sendStatusMessage(new TextComponentString("\u00a77" + DEFS[d].name + " ready in " + (left / 20 + 1) + "s"), true);
            return;
        }
        if (!fire(p, d)) {
            return;
        }
        int cd = (int) Math.round(DEFS[d].cooldown * Gear.cooldownMult(p));
        Stats.data(p).setLong("rcd" + which, p.world.getTotalWorldTime() + cd);
        Stats.data(p).setInteger("rcdt" + which, cd);
        Power.sync(p, true);
    }

    private static boolean fire(EntityPlayerMP p, int d) {
        World w = p.world;
        switch (d) {
            case STORM: {
                java.util.List<EntityLivingBase> l = SkillShapes.around(w, p.posX, p.posY, p.posZ, 16);
                l.removeIf(x -> x == p || !SkillShapes.isTarget(x));
                l.sort((a, b) -> Double.compare(a.getDistanceSq(p), b.getDistanceSq(p)));
                if (l.isEmpty()) {
                    p.sendStatusMessage(new TextComponentString("\u00a77No enemies nearby."), true);
                    return false;
                }
                float dmg = 8F + 0.15F * Stats.stats(p)[Builds.MAG];
                for (int i = 0; i < Math.min(5, l.size()); i++) {
                    EntityLivingBase t = l.get(i);
                    w.addWeatherEffect(new net.minecraft.entity.effect.EntityLightningBolt(w, t.posX, t.posY, t.posZ, true));
                    t.attackEntityFrom(net.minecraft.util.DamageSource.causeIndirectMagicDamage(p, p), dmg);
                    Fx.at(w, Fx.IMPACT, Fx.LIGHTNING_E, t.posX, t.posY + 1, t.posZ, 1.2);
                }
                Fx.sound(p, "minecraft:entity.lightning.thunder", 0.8F, 1.2F);
                return true;
            }
            case SANCTUARY: {
                for (EntityPlayer o : w.getEntitiesWithinAABB(EntityPlayer.class, p.getEntityBoundingBox().grow(8, 4, 8))) {
                    o.heal(o.getMaxHealth() * 0.3F);
                    Fx.at(w, Fx.PILLAR, Fx.HOLY, o.posX, o.posY, o.posZ, 1.0);
                }
                Fx.sound(p, "minecraft:entity.player.levelup", 0.7F, 1.4F);
                return true;
            }
            case HOURGLASS: {
                SkillServer.reduceCooldown(p, -1, 72000);
                Magic.resetCooldowns(p);
                Fx.at(w, Fx.NOVA, Fx.ARCANE, p.posX, p.posY + 1, p.posZ, 1.5);
                Fx.sound(p, "minecraft:block.portal.trigger", 0.4F, 1.8F);
                return true;
            }
            case VOID: {
                int n = 0;
                for (EntityLivingBase t : SkillShapes.around(w, p.posX, p.posY, p.posZ, 10)) {
                    if (t == p || !SkillShapes.isTarget(t)) {
                        continue;
                    }
                    SkillShapes.pull(t, p.posX, p.posZ, 1.4);
                    t.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 80, 1));
                    n++;
                }
                if (n == 0) {
                    p.sendStatusMessage(new TextComponentString("\u00a77No enemies nearby."), true);
                    return false;
                }
                Fx.at(w, Fx.NOVA, Fx.SHADOW, p.posX, p.posY + 1, p.posZ, 2.0);
                Fx.sound(p, "minecraft:entity.endermen.teleport", 0.8F, 0.6F);
                return true;
            }
            case WINDSTEP: {
                Vec3d look = p.getLookVec();
                Vec3d dir = new Vec3d(look.x, 0, look.z).normalize();
                p.motionX = dir.x * 2.2;
                p.motionZ = dir.z * 2.2;
                p.motionY = 0.25;
                p.velocityChanged = true;
                p.fallDistance = 0;
                p.addPotionEffect(new PotionEffect(MobEffects.SPEED, 100, 1));
                Fx.at(w, Fx.RING, Fx.WIND, p.posX, p.posY + 0.2, p.posZ, 1.2);
                Fx.sound(p, "minecraft:entity.enderdragon.flap", 0.6F, 1.5F);
                return true;
            }
            case WELLSPRING: {
                Power.setMana(p, Power.maxMana(p));
                Power.setFocus(p, Power.maxFocus(p));
                Fx.at(w, Fx.PILLAR, Fx.ICE, p.posX, p.posY, p.posZ, 1.2);
                Fx.sound(p, "minecraft:item.bucket.fill", 0.8F, 1.3F);
                return true;
            }
            default:
                return false;
        }
    }

    // ------------------------------------------------------------------ item
    public static class ItemRelic extends Item {
        public final int def;

        ItemRelic(int def) {
            this.def = def;
        }

        @Override
        public String getItemStackDisplayName(ItemStack s) {
            int q = Gear.quality(s);
            return (Gear.rolled(s) ? Gear.QUALITY_COLOR[q] : "\u00a7f") + DEFS[def].name;
        }

        @Override
        public ActionResult<ItemStack> onItemRightClick(World w, EntityPlayer p, EnumHand hand) {
            ItemStack s = p.getHeldItem(hand);
            if (!w.isRemote) {
                if (!Gear.rolled(s)) {
                    Gear.roll(s, p.getRNG(), Stats.stats(p)[Builds.LUCK]);
                }
                if (equip(p, s)) {
                    Fx.sound(p, "minecraft:item.armor.equip_chain", 0.8F, 1.2F);
                    return new ActionResult<>(EnumActionResult.SUCCESS, s);
                }
            }
            return new ActionResult<>(EnumActionResult.PASS, s);
        }

        @SideOnly(Side.CLIENT)
        @Override
        public void addInformation(ItemStack s, World w, List<String> tip, ITooltipFlag flag) {
            Def x = DEFS[def];
            tip.add("\u00a78" + KIND_NAME[x.kind] + (x.kind == RELIC ? " - active, " + x.cooldown / 20 + " s cooldown" : ""));
            if (!x.desc.isEmpty()) {
                tip.add("\u00a7b" + x.desc);
            }
            if (x.stat >= 0) {
                float[] st = new float[Gear.STATS];
                baseStats(s, st);
                tip.add("\u00a77+" + (int) st[x.stat] + " " + Gear.STAT[x.stat] + (x.stat2 >= 0 ? ", +" + (int) st[x.stat2] + " "
                        + Gear.STAT[x.stat2] : ""));
            }
            if (x.set >= 0) {
                tip.add("\u00a76" + SET[x.set] + " set: \u00a772: " + SET_BONUS[x.set][0] + "; 3: " + SET_BONUS[x.set][1]);
            }
            tip.add("\u00a78Right-click or shift-click to wear (E).");
        }
    }
}
