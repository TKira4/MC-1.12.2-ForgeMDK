package com.imba.undyingarcana;

import java.util.Set;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

/**
 * Phase 5 magic: the Grimoire. A player picks a magic element (a second one at level 50) and designs three spells:
 * a shape (Bolt, Blast, Beam, Nova, Barrage), which element, and up to 15 points split over Power / Size / Speed
 * (max 10 each). Stronger spells cost more mana. Spells use the element status system, so two elements on one enemy
 * react (Steam, Shatter, Overload, Firestorm, Miasma, Toxic Burst, Eclipse - see SkillStatus).
 * Cast with the spell key held (V) + 1-6 while a Grimoire is anywhere in the inventory; right-click the Grimoire to
 * open its screen. Damage scales with Magic points and gear Intensity.
 */
public final class Magic {
    public static final String[] ELEMENT = {"fire", "ice", "lightning", "wind", "earth", "shadow", "holy", "poison"};
    public static final String[] ELEMENT_NAME = {"Fire", "Ice", "Lightning", "Wind", "Earth", "Shadow", "Light", "Poison"};
    public static final int[] ELEMENT_FX = {Fx.FIRE, Fx.ICE, Fx.LIGHTNING_E, Fx.WIND, Fx.EARTH, Fx.SHADOW, Fx.HOLY, Fx.POISON};
    public static final int[] ELEMENT_RGB = {0xFF6A2A, 0x8FE0FF, 0xFFE15A, 0xBFF5D0, 0xB08A50, 0x9A6AD8, 0xFFF4B0, 0x9AD84A};
    public static final String[] ELEMENT_DESC = {"Burns. Fire + Ice = Steam, + Wind = Firestorm, + Poison = Toxic Burst.",
            "Chills, 3 stacks freeze. Ice + Fire = Steam; Lightning or Earth on Frozen = Shatter.",
            "Shocks: the next hit arcs. Lightning on burning = Overload, on Frozen = Shatter.",
            "Pushes enemies. Wind on burning = Firestorm, on poisoned = Miasma.",
            "Staggers (+15% damage taken). Earth on Frozen = Shatter.",
            "Weakens and exposes. Shadow + Light = Eclipse.",
            "Burns undead, marks Radiant. Light + Shadow = Eclipse.",
            "Poison II. Poison on burning = Toxic Burst; Wind spreads it (Miasma)."};
    public static final String[] SHAPE = {"Bolt", "Blast", "Beam", "Nova", "Barrage", "Wall", "Orbit", "Trap", "Rain", "Chain",
            "Blink", "Shield", "Summon", "Shards"};
    public static final String[] SHAPE_DESC = {"A fast projectile.", "An explosion where you aim (8 blocks).", "A piercing ray that pulses 4 times.",
            "A ring around you that pushes enemies away.", "Five bolts in a spread.",
            "A wall where you aim for 3s: enemies touching it are hurt and pushed back.", "Three orbs circle you for 4s.",
            "A rune on the ground that explodes when an enemy steps on it.", "Bolts rain on the area you aim at.",
            "A jolt that jumps between up to 4 enemies.", "Teleport forward and blast where you land.",
            "Absorption for you and allies near you.", "A spirit follows you for 8s and shoots enemies.",
            "Pieces fly out in every direction from a point (6-12 pieces, more with Size)."};
    private static final int[] BASE_COST = {10, 18, 22, 26, 20, 24, 22, 16, 30, 18, 20, 25, 35, 16};
    private static final int[] BASE_CD = {30, 80, 100, 160, 120, 160, 160, 100, 200, 70, 120, 300, 400, 70};
    private static final float[] BASE_DMG = {7, 9, 3.5F, 8, 3.5F, 3, 4, 10, 3, 6, 7, 0, 4, 3.5F};
    /** Runes (up to 3 per spell, 1 + Magic / 40 slots). */
    public static final String[] RUNE = {"Pierce", "Homing", "Split", "Bounce", "Echo", "Linger", "Lifesteal", "Pull", "Knockback",
            "Delay", "Cluster"};
    public static final String[] RUNE_DESC = {"Bolts pass through 3 more enemies.", "Bolts curve towards enemies.",
            "Bolts split into 3 when they land.", "Bolts jump to 2 more enemies.", "The spell casts again 1s later at half damage.",
            "Impacts leave a burning field for 3s.", "Heal 15% of the damage dealt.", "Hits pull enemies to the impact.",
            "Hits throw enemies back.", "Impacts go off 1s later, 30% stronger.", "Impacts burst into 4 small explosions."};
    public static final int SPELLS = 6, MAX_RUNES = 3, MAGIC_PER_RUNE = 40;
    /** Magic points needed to open each spell slot of the Grimoire. */
    public static final int[] SPELL_MAGIC = {0, 10, 25, 45, 70, 100};
    /** One more element slot per MAGIC_PER_ELEMENT Magic points (1 at 0, all 8 at 140). */
    public static final int POINTS = 15, MAX_POINT = 10, MAGIC_PER_ELEMENT = 20, MAX_ELEMENTS = 8, CHANGE_COST = 200;
    public static Item GRIMOIRE, PAGE;
    private static final SkillCatalog.Info INFO = new SkillCatalog.Info(UndyingArcana.MODID + ":grimoire", "wand_grimoire", "legend", "");

    private Magic() {
    }

    /** One spell design: shape, element slot(s), Power / Size / Speed, up to 3 runes, and a spell to cast on hit. */
    public static final class Spell {
        public int shape, which, pw, sz, sp;
        public int which2 = -1, trig = -1;
        public int[] rune = {-1, -1, -1};
        /** HUD summary of a Spell Weave graph: its real cost and cooldown (0 = use the formula). */
        public int costOv, cdOv;
        /** Most magic rings the spell can charge (0 = instant cast on key press). */
        public int rings;

        public boolean valid() {
            if (shape < 0 || shape >= SHAPE.length || which < 0 || which >= MAX_ELEMENTS || which2 < -1 || which2 >= MAX_ELEMENTS
                    || which2 == which || trig < -1 || trig >= SPELLS) {
                return false;
            }
            for (int i = 0; i < MAX_RUNES; i++) {
                if (rune[i] < -1 || rune[i] >= RUNE.length) {
                    return false;
                }
                for (int j = 0; j < i; j++) {
                    if (rune[i] >= 0 && rune[i] == rune[j]) {
                        return false;
                    }
                }
            }
            return pw >= 0 && sz >= 0 && sp >= 0 && pw <= MAX_POINT && sz <= MAX_POINT && sp <= MAX_POINT
                    && (costOv > 0 || pw + sz + sp <= POINTS);
        }

        public int runes() {
            int n = 0;
            for (int r : rune) {
                if (r >= 0) {
                    n++;
                }
            }
            return n;
        }

        public boolean has(int r) {
            for (int x : rune) {
                if (x == r) {
                    return true;
                }
            }
            return false;
        }

        public int cost() {
            if (costOv > 0) {
                return costOv;
            }
            float c = BASE_COST[shape] * (1.0F + 0.06F * (pw + sz + sp));
            c *= which2 >= 0 ? 1.4F : 1.0F;
            c *= 1.0F + 0.15F * runes();
            c *= trig >= 0 ? 1.2F : 1.0F;
            return Math.round(c);
        }

        public int cooldown() {
            if (cdOv > 0) {
                return cdOv;
            }
            return Math.max(10, Math.round(BASE_CD[shape] * (1.0F - 0.04F * sp) * (1.0F + 0.02F * pw)));
        }

        public float damage() {
            return BASE_DMG[shape] * (1.0F + 0.1F * pw);
        }

        public float size() {
            return 1.0F + 0.12F * sz;
        }

        public float speed() {
            return 1.0F + 0.1F * sp;
        }

        public String encode() {
            return shape + "," + which + "," + pw + "," + sz + "," + sp + "," + which2 + "," + rune[0] + "," + rune[1] + "," + rune[2] + "," + trig
                    + "," + costOv + "," + cdOv + "," + rings;
        }

        public Spell copy() {
            return decode(encode());
        }

        public static Spell decode(String s) {
            Spell o = new Spell();
            try {
                String[] p = s.split(",");
                o.shape = Integer.parseInt(p[0].trim());
                o.which = Integer.parseInt(p[1].trim());
                o.pw = Integer.parseInt(p[2].trim());
                o.sz = Integer.parseInt(p[3].trim());
                o.sp = Integer.parseInt(p[4].trim());
                if (p.length >= 10) {
                    o.which2 = Integer.parseInt(p[5].trim());
                    for (int i = 0; i < MAX_RUNES; i++) {
                        o.rune[i] = Integer.parseInt(p[6 + i].trim());
                    }
                    o.trig = Integer.parseInt(p[9].trim());
                }
                if (p.length >= 12) {
                    o.costOv = Integer.parseInt(p[10].trim());
                    o.cdOv = Integer.parseInt(p[11].trim());
                }
                if (p.length >= 13) {
                    o.rings = Integer.parseInt(p[12].trim());
                }
            } catch (RuntimeException e) {
                o.shape = -1;
            }
            return o;
        }
    }

    // ------------------------------------------------------------------ player data
    static NBTTagCompound magicData(EntityPlayer p) {
        return mg(p);
    }

    private static NBTTagCompound mg(EntityPlayer p) {
        NBTTagCompound d = Stats.data(p);
        if (!d.hasKey("mg")) {
            NBTTagCompound m = new NBTTagCompound();
            m.setInteger("e1", -1);
            m.setInteger("e2", -1);
            m.setString("s0", "0,0,5,0,5");
            m.setString("s1", "1,0,6,6,0");
            m.setString("s2", "2,0,8,4,3");
            d.setTag("mg", m);
        }
        NBTTagCompound m = d.getCompoundTag("mg");
        if (!m.hasKey("s3")) {
            m.setString("s3", "6,0,4,4,2,-1,-1,-1,-1,-1");
            m.setString("s4", "7,0,6,4,0,-1,-1,-1,-1,-1");
            m.setString("s5", "11,0,5,0,5,-1,-1,-1,-1,-1");
        }
        return m;
    }

    /** Element in slot 'which' (0..7), or -1. */
    public static int element(EntityPlayer p, int which) {
        NBTTagCompound m = mg(p);
        String k = "e" + (which + 1);
        return which < 0 || which >= MAX_ELEMENTS || !m.hasKey(k) ? -1 : m.getInteger(k);
    }

    /** Element slots this player has: 1 + Magic / 20, up to 8. */
    public static int slots(EntityPlayer p) {
        return Math.max(1, Math.min(MAX_ELEMENTS, 1 + Stats.stats(p)[Builds.MAG] / MAGIC_PER_ELEMENT));
    }

    /** Open spell slots (SPELL_MAGIC thresholds). */
    public static int spellSlots(EntityPlayer p) {
        int mag = Stats.stats(p)[Builds.MAG], n = 0;
        for (int need : SPELL_MAGIC) {
            if (mag >= need) {
                n++;
            }
        }
        return Math.max(1, n);
    }

    /** Rune slots per spell: 1 + Magic / 40, up to 3. */
    public static int runeSlots(EntityPlayer p) {
        return Math.max(1, Math.min(MAX_RUNES, 1 + Stats.stats(p)[Builds.MAG] / MAGIC_PER_RUNE));
    }

    public static Spell spell(EntityPlayer p, int slot) {
        return Spell.decode(mg(p).getString("s" + slot));
    }

    public static boolean isGrimoire(ItemStack s) {
        return !s.isEmpty() && s.getItem() == GRIMOIRE;
    }

    /** A Grimoire anywhere in the inventory (hotbar, bag, off hand) lets the player cast. */
    public static boolean hasGrimoire(EntityPlayer p) {
        for (ItemStack s : p.inventory.mainInventory) {
            if (isGrimoire(s)) {
                return true;
            }
        }
        return isGrimoire(p.getHeldItemOffhand());
    }

    public static int baseCost(int shape) {
        return BASE_COST[Math.max(0, Math.min(BASE_COST.length - 1, shape))];
    }

    // ------------------------------------------------------------------ Spell Weave graphs
    /** The spell graph in a slot (an old design is converted on the fly). */
    public static SpellGraph graph(EntityPlayer p, int slot) {
        NBTTagCompound m = mg(p);
        if (m.hasKey("w" + slot)) {
            return SpellGraph.fromNBT(m.getCompoundTag("w" + slot));
        }
        int[] ids = new int[MAX_ELEMENTS];
        for (int i = 0; i < MAX_ELEMENTS; i++) {
            ids[i] = element(p, i);
        }
        return SpellGraph.fromLegacy(spell(p, slot), ids);
    }

    /** Casts with this spell: I at 0, II at 15, III at 50, IV at 120, V at 250. */
    public static final int[] MASTERY = {0, 15, 50, 120, 250};

    public static int mastery(EntityPlayer p, int slot) {
        int n = mg(p).getInteger("mx" + slot), lv = 0;
        for (int i = 0; i < MASTERY.length; i++) {
            if (n >= MASTERY[i]) {
                lv = i + 1;
            }
        }
        return lv;
    }

    /** +5% spell damage per mastery level above I. */
    public static float masteryMult(EntityPlayer p, int slot) {
        return 1F + 0.05F * (mastery(p, slot) - 1);
    }

    /** -5% mana per mastery level above I. */
    public static int graphCost(EntityPlayer p, int slot, SpellGraph g) {
        return Math.max(1, Math.round(g.cost() * (1F - 0.05F * (mastery(p, slot) - 1))));
    }

    /** HUD summary of a slot: icon of the first shape, element slots, real cost and cooldown. */
    static Spell summary(EntityPlayer p, int slot) {
        SpellGraph g = graph(p, slot);
        SpellGraph.Node main = g.mainShape();
        Spell s = main != null ? SpellGraph.shapeSpell(main) : new Spell();
        if (main == null) {
            s.shape = 11;
        }
        // graph nodes hold element ids; the HUD wants element slots
        s.which = Math.max(0, slotOf(p, s.which));
        s.which2 = s.which2 >= 0 ? slotOf(p, s.which2) : -1;
        s.costOv = graphCost(p, slot, g);
        s.cdOv = g.cooldown();
        s.rings = g.maxRings();
        return s;
    }

    /** Pays for a cast: open slot, cooldown, mana. A ward (When Hurt) stays quiet when it cannot fire. */
    static boolean payFor(EntityPlayerMP p, int slot, SpellGraph g, boolean ward) {
        return payFor(p, slot, g, ward, 1F);
    }

    static boolean payFor(EntityPlayerMP p, int slot, SpellGraph g, boolean ward, float costMul) {
        if (slot >= spellSlots(p)) {
            if (!ward) {
                p.sendStatusMessage(new TextComponentString("\u00a77Spell " + (slot + 1) + " opens at " + SPELL_MAGIC[slot] + " Magic (K)."), true);
            }
            return false;
        }
        if (element(p, 0) < 0) {
            if (!ward) {
                p.sendStatusMessage(new TextComponentString("\u00a77Pick an element first (right-click the Grimoire)."), true);
            }
            return false;
        }
        NBTTagCompound m = mg(p);
        long now = p.world.getTotalWorldTime();
        long left = m.getLong("cd" + slot) - now;
        if (left > 0) {
            if (!ward) {
                p.sendStatusMessage(new TextComponentString("\u00a77" + g.name + " ready in \u00a7f" + (left / 20 + 1) + "s"), true);
            }
            return false;
        }
        int cost = Math.round(graphCost(p, slot, g) * costMul);
        if (ward ? Power.mana(p) < Power.cost(p, cost) && !p.capabilities.isCreativeMode : !Power.spend(p, cost)) {
            return false;
        }
        if (ward) {
            Power.spend(p, cost);
        }
        boolean free = Power.strength(p, Builds.MAG) > 0;
        int cd = free ? 10 : (int) Math.round(g.cooldown() * Gear.cooldownMult(p) * Power.cooldownMult(p));
        m.setLong("cd" + slot, now + cd);
        m.setInteger("mx" + slot, m.getInteger("mx" + slot) + 1);
        Power.sync(p, true);
        return true;
    }

    /** Saves a graph into a slot after checking it. */
    public static void saveGraph(EntityPlayerMP p, int slot, NBTTagCompound tag) {
        if (!Stats.on(p.world) || slot < 0 || slot >= SPELLS) {
            return;
        }
        if (slot >= spellSlots(p)) {
            p.sendMessage(new TextComponentString("\u00a77Spell " + (slot + 1) + " opens at " + SPELL_MAGIC[slot] + " Magic (K)."));
            return;
        }
        SpellGraph g = SpellGraph.fromNBT(tag);
        String bad = g.check(Stats.stats(p)[Builds.MAG], runeSlots(p));
        for (SpellGraph.Node n : g.nodes) {
            if (bad == null && n.type == SpellGraph.ART && SpellArts.rank(p, n.p[0]) <= 0) {
                bad = "You have not learned " + SpellArts.ART[n.p[0]] + " (Arcane Scroll).";
            }
            int[] els = n.type == SpellGraph.SHAPE ? new int[]{n.p[1], n.p[2]}
                    : n.type == SpellGraph.STATUSA || n.type == SpellGraph.ZONE ? new int[]{n.p[0]} : new int[0];
            for (int e : els) {
                if (bad == null && e >= 0 && slotOf(p, e) < 0) {
                    bad = "Learn " + ELEMENT_NAME[Math.min(ELEMENT_NAME.length - 1, e)] + " first (Grimoire, left side).";
                }
            }
        }
        if (bad != null) {
            p.sendMessage(new TextComponentString("\u00a7c" + bad));
            return;
        }
        mg(p).setTag("w" + slot, g.toNBT());
        p.sendStatusMessage(new TextComponentString("\u00a7dSpell " + (slot + 1) + " saved: " + g.name + " \u00a77(" + graphCost(p, slot, g)
                + " mana)"), true);
        sendWeave(p);
        Power.sync(p, true);
    }

    /** All graphs and limits for the editor. */
    public static void sendWeave(EntityPlayerMP p) {
        NBTTagCompound t = new NBTTagCompound();
        for (int i = 0; i < SPELLS; i++) {
            t.setTag("g" + i, graph(p, i).toNBT());
            t.setInteger("mx" + i, mg(p).getInteger("mx" + i));
        }
        int mag = Stats.stats(p)[Builds.MAG];
        t.setInteger("magic", mag);
        t.setInteger("runes", runeSlots(p));
        t.setInteger("open", spellSlots(p));
        t.setIntArray("arts", SpellArts.ranks(p));
        SkillNet.NET.sendTo(new SkillNet.MsgWeave(0, 0, t), p);
    }

    /** Writes a spell onto a Spell Page (takes 1 paper). */
    static void writePage(EntityPlayerMP p, int slot) {
        if (slot < 0 || slot >= SPELLS) {
            return;
        }
        if (!p.capabilities.isCreativeMode && !Bench.take(p, new ItemStack(net.minecraft.init.Items.PAPER), 1)) {
            p.sendMessage(new TextComponentString("\u00a77Printing a Spell Blueprint needs 1 paper."));
            return;
        }
        SpellGraph g = graph(p, slot);
        ItemStack page = new ItemStack(PAGE);
        NBTTagCompound t = new NBTTagCompound();
        t.setTag("g", g.toNBT());
        t.setString("author", p.getName());
        page.setTagCompound(t);
        if (!p.inventory.addItemStackToInventory(page)) {
            p.dropItem(page, false);
        }
        p.sendStatusMessage(new TextComponentString("\u00a7dPrinted a blueprint of \u00a7f" + g.name + "\u00a7d."), true);
    }

    static void fill(EntityPlayer p, SkillNet.MsgPower m) {
        StringBuilder es = new StringBuilder();
        for (int i = 0; i < MAX_ELEMENTS; i++) {
            es.append(element(p, i)).append(',');
        }
        m.elements = es.append(slots(p)).append(',').append(runeSlots(p)).append(',').append(spellSlots(p)).toString();
        es.setLength(0);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < SPELLS; i++) {
            sb.append(i > 0 ? ";" : "").append(summary(p, i).encode());
            m.spellCd[i] = (int) Math.max(0, mg(p).getLong("cd" + i) - p.world.getTotalWorldTime());
        }
        m.spells = sb.toString();
        m.grimoire = hasGrimoire(p);
    }

    /** op 2: learn element s into slot a (replacing costs coins); op 3: save spell (a = slot, s = design). */
    public static void act(EntityPlayerMP p, int op, int a, String s) {
        if (!Stats.on(p.world)) {
            return;
        }
        NBTTagCompound m = mg(p);
        if (op == 2) {
            int el;
            try {
                el = Integer.parseInt(s.trim());
            } catch (NumberFormatException e) {
                return;
            }
            if (el < 0 || el >= ELEMENT.length || a < 0 || a >= MAX_ELEMENTS) {
                return;
            }
            if (a >= slots(p)) {
                p.sendMessage(new TextComponentString("\u00a77Element slot " + (a + 1) + " unlocks at " + a * MAGIC_PER_ELEMENT + " Magic (K)."));
                return;
            }
            for (int i = 0; i < MAX_ELEMENTS; i++) {
                if (i != a && element(p, i) == el) {
                    p.sendMessage(new TextComponentString("\u00a77You already have " + ELEMENT_NAME[el] + "."));
                    return;
                }
            }
            String key = "e" + (a + 1);
            if (!m.hasKey(key)) {
                m.setInteger(key, -1);
            }
            if (m.getInteger(key) >= 0 && m.getInteger(key) != el && !p.capabilities.isCreativeMode) {
                if (!Bench.take(p, new ItemStack(ModItems.SOUL_COIN), CHANGE_COST)) {
                    p.sendMessage(new TextComponentString("\u00a7cChanging an element costs " + CHANGE_COST + " Soul Coins."));
                    return;
                }
            }
            m.setInteger(key, el);
            p.sendMessage(new TextComponentString("\u00a7dYou learned " + ELEMENT_NAME[el] + " (element " + (a + 1) + ")."));
            Fx.at(p.world, Fx.TIERUP, ELEMENT_FX[el], p.posX, p.posY + 1, p.posZ, 1.2);
            Fx.sound(p, "minecraft:block.enchantment_table.use", 1.0F, 1.2F);
        } else if (op == 3) {
            Spell sp = Spell.decode(s);
            if (a < 0 || a >= SPELLS || !sp.valid() || sp.trig == a) {
                return;
            }
            if (a >= spellSlots(p)) {
                p.sendMessage(new TextComponentString("\u00a77Spell " + (a + 1) + " opens at " + SPELL_MAGIC[a] + " Magic (K)."));
                return;
            }
            if (sp.runes() > runeSlots(p)) {
                p.sendMessage(new TextComponentString("\u00a77You have " + runeSlots(p) + " rune slot(s); one more every " + MAGIC_PER_RUNE + " Magic."));
                return;
            }
            if (sp.which2 >= 0 && (sp.which2 >= slots(p) || element(p, sp.which2) < 0)) {
                p.sendMessage(new TextComponentString("\u00a77Learn a second element first."));
                return;
            }
            m.setString("s" + a, sp.encode());
            p.sendStatusMessage(new TextComponentString("\u00a7dSpell " + (a + 1) + " saved: " + SHAPE[sp.shape]), true);
        } else if (op == 4) {
            if (hasGrimoire(p)) {
                int charge = 0;
                try {
                    charge = s.isEmpty() ? 0 : Integer.parseInt(s.trim());
                } catch (NumberFormatException e) {
                    charge = 0;
                }
                cast(p, a, charge);
            }
            return;
        } else if (op == 6) {
            writePage(p, a);
            return;
        }
        p.inventoryContainer.detectAndSendChanges();
        Power.sync(p, true);
    }

    // ------------------------------------------------------------------ casting
    public static void cast(EntityPlayerMP p, int slot) {
        cast(p, slot, 0);
    }

    /** Ticks of holding the key per magic ring. */
    public static final int RING_TICKS = 12;

    /** Casts a slot; charge = ticks the key was held. Every full ring (RING_TICKS) up to the spell's max rings
     *  adds +25% power, +10% size and +15% mana. */
    public static void cast(EntityPlayerMP p, int slot, int charge) {
        if (!Stats.on(p.world) || slot < 0 || slot >= SPELLS) {
            return;
        }
        SpellGraph g = graph(p, slot);
        int rings = Math.max(0, Math.min(g.maxRings(), charge / RING_TICKS));
        float f = rings / 5F;
        if (g.check(Stats.stats(p)[Builds.MAG], runeSlots(p)) != null || !payFor(p, slot, g, false, 1F + 0.15F * rings)) {
            return;
        }
        SpellGraph.Node main = g.mainShape();
        int el = main != null && slotOf(p, main.p[1]) >= 0 ? main.p[1] : element(p, 0);
        // magic circle at the feet, element sound, element cast effect
        for (int k = 0; k <= rings; k++) {
            Fx.send(p.world, Fx.RUNES, el >= 0 ? ELEMENT_FX[el] : Fx.ARCANE, p.posX, p.posY + 0.05 + 0.02 * k, p.posZ, 0, 0, 0, 1.1 + 0.45 * k, 0,
                    14 + 2 * k, -1);
        }
        if (el >= 0) {
            Fx.sound(p, SpellArts.CAST_SOUND[el], 0.7F, 1.2F - 0.2F * f);
            SpellArts.onCast(p, el);
        }
        SpellVM.cast(p, slot, g, masteryMult(p, slot) * (1F + 0.25F * rings), null, null, null, rings);
        SkillServer.scoreSet(p, "za_t_wand", 1);
    }

    /** Skill context of a spell (element, Magic scaling, size / speed); null without an element. */
    static SkillCtx prepare(EntityPlayerMP p, int slot, Spell sp) {
        int el = element(p, sp.which);
        if (el < 0) {
            el = element(p, 0);
        }
        return prepareEl(p, slot, sp, el, sp.which2 >= 0 ? element(p, sp.which2) : -1);
    }

    /** Element slot holding element el, or -1 when it is not learned. */
    public static int slotOf(EntityPlayer p, int el) {
        for (int i = 0; i < MAX_ELEMENTS && i < slots(p); i++) {
            if (el >= 0 && element(p, i) == el) {
                return i;
            }
        }
        return -1;
    }

    /** Skill context with element ids (Spell Weave): an element that is not learned falls back to the first one. */
    static SkillCtx prepareEl(EntityPlayerMP p, int slot, Spell sp, int el, int el2) {
        if (el < 0 || slotOf(p, el) < 0) {
            el = element(p, 0);
        }
        if (el < 0) {
            return null;
        }
        if (el2 >= 0 && slotOf(p, el2) < 0) {
            el2 = -1;
        }
        SkillCtx c = new SkillCtx(p, INFO, Math.min(2, slot));
        c.name = ELEMENT_NAME[el] + " " + SHAPE[sp.shape];
        c.element = ELEMENT[el];
        c.status = ELEMENT[el];
        c.el = ELEMENT_FX[el];
        if (el2 >= 0 && el2 != el) {
            // two-element spell: mixed colours and a combined name (a fusion has its own name and effect)
            c.el = Fx.combo(el, el2);
            c.fusion = SpellArts.fusion(el, el2);
            c.name = (c.fusion >= 0 ? SpellArts.FUSION[c.fusion] : ELEMENT_NAME[el] + "-" + ELEMENT_NAME[el2]) + " " + SHAPE[sp.shape];
        }
        c.magic = true;
        c.tierDmg = 1.0F;
        c.level = Builds.skillLevel(Stats.stats(p)[Builds.MAG]);
        c.lvlK = 0.25F;
        c.dmgMult *= 1.0F + 0.012F * Stats.stats(p)[Builds.MAG];
        Stats.skillBonus(c);
        c.size *= sp.size();
        c.speed *= sp.speed();
        return c;
    }

    // ------------------------------------------------------------------ item
    public static class ItemGrimoire extends Item {
        public ItemGrimoire() {
            setMaxStackSize(1);
        }

        @Override
        public boolean hasEffect(ItemStack s) {
            return true;
        }

        @Override
        public ActionResult<ItemStack> onItemRightClick(World w, EntityPlayer p, EnumHand hand) {
            ItemStack s = p.getHeldItem(hand);
            if (hand != EnumHand.MAIN_HAND) {
                return new ActionResult<>(EnumActionResult.PASS, s);
            }
            if (!w.isRemote && p instanceof EntityPlayerMP) {
                if (!Stats.on(w)) {
                    p.sendMessage(new TextComponentString("\u00a77The Grimoire is part of the new-world systems (/gamerule uaBuilds true)."));
                } else {
                    Power.sync((EntityPlayerMP) p, true);
                    sendWeave((EntityPlayerMP) p);
                    SkillNet.NET.sendTo(new SkillNet.MsgOpen(2, ""), (EntityPlayerMP) p);
                }
            }
            return new ActionResult<>(EnumActionResult.SUCCESS, s);
        }

        @Override
        @net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT)
        public void addInformation(ItemStack s, World w, java.util.List<String> tip, net.minecraft.client.util.ITooltipFlag flag) {
            tip.add("\u00a7dYour own spells: learn elements, design up to 6 spells.");
            tip.add("\u00a77Keep it in your inventory: hold V + 1-6 to cast");
            tip.add("\u00a77Right-click: open the Grimoire");
            tip.add("\u00a78Spells cost mana; Magic points raise damage.");
        }
    }

    /** Spell Blueprint: a printed spell. Right-click to open it in the Spell Weave editor and keep it in one of your slots. */
    public static class ItemSpellPage extends Item {
        public ItemSpellPage() {
            setMaxStackSize(1);
        }

        @Override
        public net.minecraft.util.ActionResult<ItemStack> onItemRightClick(net.minecraft.world.World w, EntityPlayer p,
                net.minecraft.util.EnumHand hand) {
            ItemStack s = p.getHeldItem(hand);
            if (w.isRemote && s.hasTagCompound()) {
                UndyingArcana.proxy.openPage(s.getTagCompound());
            } else if (!w.isRemote && p instanceof EntityPlayerMP) {
                sendWeave((EntityPlayerMP) p); // slot limits and saved spells for the editor
            }
            return new net.minecraft.util.ActionResult<>(net.minecraft.util.EnumActionResult.SUCCESS, s);
        }

        @Override
        public String getItemStackDisplayName(ItemStack s) {
            if (s.hasTagCompound()) {
                String n = s.getTagCompound().getCompoundTag("g").getString("name");
                if (!n.isEmpty()) {
                    return "Spell Blueprint: " + n;
                }
            }
            return super.getItemStackDisplayName(s);
        }

        @Override
        @net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT)
        public void addInformation(ItemStack s, net.minecraft.world.World w, java.util.List<String> tip,
                net.minecraft.client.util.ITooltipFlag flag) {
            if (!s.hasTagCompound()) {
                tip.add("\u00a77Blank. Print one from the Grimoire.");
                return;
            }
            SpellGraph g = SpellGraph.fromNBT(s.getTagCompound().getCompoundTag("g"));
            tip.add("\u00a77By " + s.getTagCompound().getString("author") + " \u00a78- " + g.nodes.size() + " nodes, " + g.cost() + " mana, "
                    + String.format("%.1f", g.cooldown() / 20F) + "s");
            StringBuilder sb = new StringBuilder();
            int shown = 0;
            for (SpellGraph.Node n : g.nodes) {
                if (n.def().cat == SpellGraph.ACTION && shown < 4) {
                    String part = n.type == SpellGraph.SHAPE ? SHAPE[Math.max(0, Math.min(SHAPE.length - 1, n.p[0]))]
                            : n.type == SpellGraph.ART ? SpellArts.ART[Math.max(0, Math.min(SpellArts.ART.length - 1, n.p[0]))] : n.def().name;
                    sb.append(shown++ > 0 ? ", " : "").append(part);
                }
            }
            if (sb.length() > 0) {
                tip.add("\u00a7d" + sb);
            }
            tip.add("\u00a78Right-click to open it, pick a slot and Save to learn it.");
        }
    }
}
