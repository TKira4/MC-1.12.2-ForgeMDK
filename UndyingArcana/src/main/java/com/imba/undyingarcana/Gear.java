package com.imba.undyingarcana;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.MobEffects;
import net.minecraft.potion.PotionEffect;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.item.ItemTool;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Gear: quality (Common..Legendary), jewel sockets and one enchant per item, stored in the item's "ua" tag.
 * The eight gear stats are summed over the main hand and the four armor slots every second.
 *
 * Gear stats: Power (+1% damage), Defense (-0.5% damage taken, max 30%), Agility (+0.4% speed, +0.2% dodge),
 * Attack Size (+2% skill size), Attack Speed (-1.5% skill cooldowns, max 45%), Intensity (+1.5% skill damage),
 * Armor Pierce and Spell Pierce (+1% each, added to the player's pierce), Crit Chance (+0.5%), Crit Damage (+2%),
 * Lifesteal (heal 0.3% of damage dealt, max 15%), Max Health (+0.5 health), Thorns (reflect 1% of melee damage taken, max 50%),
 * Fortune (+2% Soul Coins and gem finds).
 *
 * Effect gems (Sunstone, Moonstone, Lapis, Peridot) and legendary gems (Soulstone, Void Crystal) have no main stat: in a
 * weapon they proc on hit, in armor they proc on the attacker when you are hit. Only one legendary gem per item.
 * Cursed enchants (index CURSED and up) are strong but carry a drawback; they have one tier.
 */
@Mod.EventBusSubscriber(modid = UndyingArcana.MODID)
public final class Gear {
    public static final int POWER = 0, DEFENSE = 1, AGILITY = 2, SIZE = 3, SPEED = 4, INTENSITY = 5, APEN = 6, SPEN = 7, CRIT = 8, CRITDMG = 9,
            LEECH = 10, HEALTH = 11, THORNS = 12, FIND = 13, STATS = 14;
    public static final String[] STAT = {"Power", "Defense", "Agility", "Attack Size", "Attack Speed", "Intensity", "Armor Pierce", "Spell Pierce",
            "Crit Chance", "Crit Damage", "Lifesteal", "Max Health", "Thorns", "Fortune"};
    /** What one point of each gear stat does (tooltips). */
    public static final String[] STAT_HELP = {"+1% damage", "-0.5% damage taken (max 30%)", "+0.4% speed, +0.2% dodge", "+2% skill size",
            "-1.5% skill cooldowns (max 45%)", "+1.5% skill damage", "+1% armor pierce", "+1% spell pierce", "+0.5% crit chance (max 40%)",
            "+2% crit damage", "heal 0.3% of damage dealt (max 15%)", "+0.5 max health", "reflect 1% of melee damage (max 50%)",
            "+2% Soul Coins and gem finds"};
    public static final String[] QUALITY = {"Common", "Uncommon", "Rare", "Exotic", "Legendary"};
    public static final String[] QUALITY_COLOR = {"\u00a7f", "\u00a7a", "\u00a79", "\u00a75", "\u00a76"};
    public static final int[] SOCKETS = {0, 1, 2, 3, 3};
    private static final int[] WEAPON_POWER = {0, 3, 6, 10, 15};
    private static final int[] ARMOR_DEFENSE = {0, 1, 2, 3, 5};

    /** Gems: name, main stat. */
    public static final String[] GEM = {"ruby", "topaz", "sapphire", "emerald", "aquamarine", "amethyst", "onyx", "garnet", "citrine", "fire_opal",
            "bloodstone", "jade", "thornstone", "pyrite", "sunstone", "moonstone", "lapis", "peridot", "soulstone", "void_crystal"};
    public static final String[] GEM_NAME = {"Ruby", "Topaz", "Sapphire", "Emerald", "Aquamarine", "Amethyst", "Onyx", "Garnet", "Citrine",
            "Fire Opal", "Bloodstone", "Jade", "Thornstone", "Pyrite", "Sunstone", "Moonstone", "Lapis", "Peridot", "Soulstone", "Void Crystal"};
    public static final int[] GEM_STAT = {POWER, APEN, SPEN, SIZE, AGILITY, SPEED, DEFENSE, INTENSITY, CRIT, CRITDMG, LEECH, HEALTH, THORNS, FIND,
            -1, -1, -1, -1, -1, -1};
    public static final int[] GEM_RGB = {0xE03A3A, 0xF0A030, 0x3A6AF0, 0x3AD070, 0x60E0E0, 0xB060F0, 0x505060, 0x9A1E3A, 0xF5D547, 0xFF6A2A,
            0x7A2424, 0x5FA36A, 0x6E8B3D, 0xC9A64A, 0xFF9F2E, 0xD8E4F0, 0x2747A8, 0xA8D84A, 0x4FE6E0, 0x5A2E8A};
    /** Effect of a gem without a main stat: 0 none, 1 burn, 2 frost, 3 storm, 4 venom, 5 soul (legendary), 6 void (legendary). */
    public static final int[] GEM_EFFECT = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 2, 3, 4, 5, 6};
    public static final int EFFECTS = 7, FIRST_LEGEND_GEM = 18, NORMAL_GEMS = 18;
    public static final String[] EFFECT = {"", "Burn", "Frost", "Storm", "Venom", "Soul Harvest", "Void Pull"};
    /** Proc chance (%) of an effect gem by grade. */
    public static final int[] EFFECT_CHANCE = {10, 15, 20};
    public static final String[] GRADE = {"Rough", "Fine", "Perfect"};
    public static final int[] GRADE_MAIN = {3, 6, 10};
    public static final int[] GRADE_COST = {10, 30, 80};

    /** Enchant scrolls: one per item, tier 1..3 (cursed ones: tier 1 only). */
    public static final String[] ENCHANT = {"Sundering", "Hexing", "Executioner", "Echo", "Storm", "Bulwark", "Swift", "Agile", "Vampiric",
            "Blazing", "Frostbite", "Venom", "Cleaving", "Momentum", "Fortune", "Piercing", "Lucky",
            "Thorns", "Regenerating", "Warding", "Featherweight", "Undying",
            "Bloodpact", "Glass Cannon", "Frenzy", "Leaden", "Gambler's Mark", "Wraith"};
    public static final int CURSED = 22;
    public static final boolean[] ENCHANT_ARMOR = {false, false, false, false, false, true, true, true, false,
            false, false, false, false, false, false, false, false,
            true, true, true, true, true,
            false, false, false, true, true, true};
    public static final String[] ENCHANT_DESC = {"Physical hits add %d Sunder", "Magic hits add %d Hex", "+%d%% damage to enemies under 30%% health",
            "%d%% chance a hit strikes again for half", "%d%% chance to call lightning", "-%d%% damage taken", "+%d%% movement speed",
            "+%d%% dodge chance", "Heal %d%% of damage dealt",
            "%d%% chance to set the target on fire", "%d%% chance to slow the target hard", "%d%% chance to poison the target",
            "Melee hits splash %d%% damage around the target", "Each hit on the same target: +%d%% damage (5 stacks)",
            "+%d Fortune (more Soul Coins and gems)", "+%d Armor Pierce", "+%d Crit Chance",
            "+%d Thorns (reflect melee damage)", "Heal %d health every 5 seconds", "-%d%% magic damage taken", "-%d%% fall damage",
            "Survive a killing blow once every %d seconds",
            "+%d%% damage, but each hit costs 1 health", "+%d%% skill damage, but you take 25%% more damage",
            "-%d%% skill cooldowns, but every skill makes you hungry", "-%d%% damage taken, but -4%% movement speed",
            "+%d Crit Chance and Fortune, but 5%% of hits taken deal double", "+%d%% dodge chance, but -5%% max health"};
    public static final int[][] ENCHANT_VAL = {{1, 2, 3}, {1, 2, 3}, {15, 25, 35}, {8, 12, 16}, {8, 12, 16}, {3, 5, 7}, {5, 8, 12}, {3, 5, 7}, {3, 5, 7},
            {15, 25, 35}, {15, 25, 35}, {15, 25, 35}, {15, 25, 35}, {3, 4, 5}, {10, 20, 30}, {10, 20, 30}, {6, 10, 16},
            {10, 15, 20}, {1, 2, 3}, {8, 12, 16}, {25, 40, 55}, {300, 240, 180},
            {30, 30, 30}, {40, 40, 40}, {30, 30, 30}, {8, 8, 8}, {5, 5, 5}, {4, 4, 4}};

    private static final UUID SPEED_ID = UUID.fromString("c2d4e6f8-1a3b-4c5d-8e7f-9a0b1c2d3e4f");
    private static final UUID HEALTH_ID = UUID.fromString("d3e5f7a9-2b4c-4d6e-9f80-1a2b3c4d5e6f");
    private static final UUID WRAITH_ID = UUID.fromString("e4f6a8b0-3c5d-4e7f-a091-2b3c4d5e6f70");
    private static final Map<UUID, float[]> CACHE = new HashMap<>();
    private static final Map<UUID, int[][]> EFFECT_CACHE = new HashMap<>();
    private static final Map<UUID, int[]> MOMENTUM = new HashMap<>();
    private static boolean echoing;

    private Gear() {
    }

    // ------------------------------------------------------------------ item data
    public static boolean isArmor(ItemStack s) {
        return !s.isEmpty() && s.getItem() instanceof ItemArmor;
    }

    public static boolean isWeapon(ItemStack s) {
        return !s.isEmpty() && (s.getItem() instanceof ItemSword || s.getItem() instanceof ItemTool || s.getItem() instanceof ItemBow
                || SkillCatalog.identify(s) != null);
    }

    public static boolean isGear(ItemStack s) {
        return isArmor(s) || isWeapon(s);
    }

    public static NBTTagCompound tag(ItemStack s) {
        if (!s.hasTagCompound()) {
            s.setTagCompound(new NBTTagCompound());
        }
        NBTTagCompound t = s.getTagCompound();
        if (!t.hasKey("ua")) {
            t.setTag("ua", new NBTTagCompound());
        }
        return t.getCompoundTag("ua");
    }

    public static boolean rolled(ItemStack s) {
        return s.hasTagCompound() && s.getTagCompound().getCompoundTag("ua").hasKey("q");
    }

    public static int quality(ItemStack s) {
        return rolled(s) ? Math.max(0, Math.min(4, s.getTagCompound().getCompoundTag("ua").getInteger("q"))) : 0;
    }

    public static NBTTagList jewels(ItemStack s) {
        return s.hasTagCompound() ? s.getTagCompound().getCompoundTag("ua").getTagList("j", 10) : new NBTTagList();
    }

    public static int sockets(ItemStack s) {
        return SOCKETS[quality(s)];
    }

    /** Enchant {kind, tier} or null. */
    public static int[] enchant(ItemStack s) {
        if (!s.hasTagCompound()) {
            return null;
        }
        NBTTagCompound e = s.getTagCompound().getCompoundTag("ua").getCompoundTag("e");
        return e.hasKey("k") ? new int[]{e.getInteger("k"), Math.max(1, Math.min(3, e.getInteger("t")))} : null;
    }

    public static boolean cursed(int kind) {
        return kind >= CURSED;
    }

    /** "Hexing II" or "Bloodpact (Cursed)", with color. */
    public static String enchantName(int k, int tier) {
        return cursed(k) ? "\u00a74" + ENCHANT[k] + " (Cursed)" : "\u00a7b" + ENCHANT[k] + " " + new String[]{"I", "II", "III"}[Math.max(1, Math.min(3, tier)) - 1];
    }

    public static String enchantDesc(int k, int tier) {
        return String.format(ENCHANT_DESC[k], ENCHANT_VAL[k][Math.max(1, Math.min(3, tier)) - 1]);
    }

    /** "Power" for stat gems, "Burn on hit" for effect gems. */
    public static String gemMain(int g) {
        return GEM_STAT[g] >= 0 ? STAT[GEM_STAT[g]] : EFFECT[GEM_EFFECT[g]] + (g >= FIRST_LEGEND_GEM ? " (legendary)" : " on hit");
    }

    /** Effect proc chances (%) of one item's jewels, by effect index. */
    public static int[] itemEffects(ItemStack s) {
        int[] out = new int[EFFECTS];
        NBTTagList js = jewels(s);
        for (int i = 0; i < js.tagCount(); i++) {
            NBTTagCompound j = js.getCompoundTagAt(i);
            int g = Math.max(0, Math.min(GEM.length - 1, j.getInteger("g")));
            if (GEM_EFFECT[g] > 0) {
                out[GEM_EFFECT[g]] += EFFECT_CHANCE[Math.max(0, Math.min(2, j.getInteger("gr")))];
            }
        }
        return out;
    }

    /** Sets the quality (0 Common .. 4 Legendary); used by armory upgrades. */
    public static void setQuality(ItemStack s, int q) {
        tag(s).setInteger("q", Math.max(0, Math.min(4, q)));
    }

    /** Rolls quality once: Common 50, Uncommon 28, Rare 14, Exotic 6, Legendary 2 (Luck shifts toward rarer); armory tier floor. */
    public static void roll(ItemStack s, java.util.Random r, int luck) {
        if (!isGear(s) || rolled(s)) {
            return;
        }
        SkillCatalog.Info in = SkillCatalog.identify(s);
        int q;
        if (in != null && !in.legend.isEmpty()) {
            q = 4;
        } else {
            float v = r.nextFloat() * 100 - luck * 0.2F;
            q = v < 2 ? 4 : v < 8 ? 3 : v < 22 ? 2 : v < 50 ? 1 : 0;
        }
        // armory tiers have a quality floor (Steel Uncommon, Arcane Rare, Soulforged Exotic), however the item was obtained
        int[] arm = s.getItem().getRegistryName() == null ? null : Armory.parse(s.getItem().getRegistryName().toString());
        if (arm != null) {
            q = Math.max(q, Armory.QUALITY_FLOOR[arm[1]]);
        }
        tag(s).setInteger("q", q);
    }

    /** Gear stats of one item: quality base + jewels (main, reagent secondaries, Perfect drawback). */
    public static void addStats(ItemStack s, float[] out) {
        if (!rolled(s)) {
            return;
        }
        int q = quality(s);
        if (isArmor(s)) {
            out[DEFENSE] += ARMOR_DEFENSE[q];
        } else {
            out[POWER] += WEAPON_POWER[q];
        }
        NBTTagList js = jewels(s);
        for (int i = 0; i < js.tagCount(); i++) {
            jewelStats(js.getCompoundTagAt(i), out);
        }
        int[] en = enchant(s);
        if (en != null) {
            int v = ENCHANT_VAL[en[0]][en[1] - 1];
            switch (en[0]) {
                case 14: out[FIND] += v; break;
                case 15: out[APEN] += v; break;
                case 16: out[CRIT] += v; break;
                case 17: out[THORNS] += v; break;
                case 26: out[CRIT] += v; out[FIND] += v; break;
                default: break;
            }
        }
    }

    public static void jewelStats(NBTTagCompound j, float[] out) {
        int g = Math.max(0, Math.min(GEM.length - 1, j.getInteger("g"))), gr = Math.max(0, Math.min(2, j.getInteger("gr")));
        if (GEM_STAT[g] >= 0) {
            out[GEM_STAT[g]] += GRADE_MAIN[gr];
        }
        int[] sec = j.getIntArray("sec");
        for (int k = 0; k < sec.length && k < STATS; k++) {
            out[k] += sec[k];
        }
        if (j.hasKey("dr") && j.getInteger("dr") < STATS) {
            out[j.getInteger("dr")] -= 3;
        }
    }

    /** Summed gear stats of a player (cached, refreshed every second on the server). */
    public static float[] stats(EntityPlayer p) {
        float[] c = CACHE.get(p.getUniqueID());
        return c != null ? c : recompute(p);
    }

    /** Effect gem chances: [0] = main-hand weapon (on hit), [1] = armor (on the attacker). */
    public static int[][] effects(EntityPlayer p) {
        int[][] c = EFFECT_CACHE.get(p.getUniqueID());
        if (c == null) {
            recompute(p);
            c = EFFECT_CACHE.get(p.getUniqueID());
        }
        return c;
    }

    static float[] recompute(EntityPlayer p) {
        float[] out = new float[STATS];
        addStats(p.getHeldItemMainhand(), out);
        int[] armorFx = new int[EFFECTS];
        for (ItemStack s : p.getArmorInventoryList()) {
            addStats(s, out);
            int[] f = itemEffects(s);
            for (int k = 0; k < EFFECTS; k++) {
                armorFx[k] += f[k];
            }
        }
        CACHE.put(p.getUniqueID(), out);
        EFFECT_CACHE.put(p.getUniqueID(), new int[][]{itemEffects(p.getHeldItemMainhand()), armorFx});
        return out;
    }

    /** Sum of one armor enchant's value over the worn pieces. */
    public static int armorEnchant(EntityPlayer p, int kind) {
        int v = 0;
        for (ItemStack s : p.getArmorInventoryList()) {
            int[] e = enchant(s);
            if (e != null && e[0] == kind) {
                v += ENCHANT_VAL[kind][e[1] - 1];
            }
        }
        return v;
    }

    /** Number of worn armor pieces with this enchant. */
    public static int armorPieces(EntityPlayer p, int kind) {
        int n = 0;
        for (ItemStack s : p.getArmorInventoryList()) {
            int[] e = enchant(s);
            if (e != null && e[0] == kind) {
                n++;
            }
        }
        return n;
    }

    /** Main-hand weapon enchant kind, or -1. */
    public static int weaponEnchant(EntityPlayer p) {
        int[] e = enchant(p.getHeldItemMainhand());
        return e == null ? -1 : e[0];
    }

    // ------------------------------------------------------------------ hooks used by other systems
    /** Extra dodge chance (0..1) from gear Agility, Agile armor and Wraith armor. */
    public static float dodge(EntityPlayer p) {
        return 0.002F * Math.max(0, stats(p)[AGILITY]) + 0.01F * armorEnchant(p, 7) + 0.01F * armorEnchant(p, 27);
    }

    /** Extra crit chance (0..1) and crit damage multiplier bonus from gear. */
    public static float critChance(EntityPlayer p) {
        return Math.min(0.40F, 0.005F * Math.max(0, stats(p)[CRIT]));
    }

    public static float critDamage(EntityPlayer p) {
        return 0.02F * Math.max(0, stats(p)[CRITDMG]);
    }

    /** Fortune multiplier for Soul Coins and gem finds. */
    public static float fortune(EntityPlayer p) {
        return 1.0F + 0.02F * Math.max(0, stats(p)[FIND]);
    }

    /** Attack Size and Intensity on a skill cast; Glass Cannon and Frenzy. */
    public static void onSkill(SkillCtx c) {
        float[] g = stats(c.p);
        c.size *= Math.max(0.5F, 1.0F + 0.02F * g[SIZE]);
        c.dmgMult *= Math.max(0.5F, 1.0F + 0.015F * g[INTENSITY]);
        int we = weaponEnchant(c.p);
        if (we == 23) {
            c.dmgMult *= 1.0F + ENCHANT_VAL[23][0] / 100.0F;
        } else if (we == 24) {
            c.p.getFoodStats().addExhaustion(3.0F);
        }
    }

    /** Skill cooldown multiplier from Attack Speed (and Frenzy). */
    public static double cooldownMult(EntityPlayer p) {
        double m = 1.0D - Math.min(0.45D, Math.max(-0.3D, 0.015D * stats(p)[SPEED]));
        if (weaponEnchant(p) == 24) {
            m *= 1.0D - ENCHANT_VAL[24][0] / 100.0D;
        }
        return m;
    }

    // ------------------------------------------------------------------ events
    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent e) {
        EntityPlayer p = e.player;
        if (e.phase != TickEvent.Phase.END || p.world.isRemote || p.ticksExisted % 20 != 3 || !(p instanceof EntityPlayerMP) || !Stats.on(p.world)) {
            return;
        }
        int luck = Stats.stats(p)[Builds.LUCK];
        for (int i = 0; i < p.inventory.getSizeInventory(); i++) {
            ItemStack s = p.inventory.getStackInSlot(i);
            if (!s.isEmpty() && !rolled(s) && isGear(s)) {
                roll(s, p.getRNG(), luck);
            } else if (!s.isEmpty() && rolled(s) && s.getItem().getRegistryName() != null) {
                // armory items rolled before the quality floor existed are raised to it
                int[] arm = Armory.parse(s.getItem().getRegistryName().toString());
                if (arm != null && quality(s) < Armory.QUALITY_FLOOR[arm[1]]) {
                    setQuality(s, Armory.QUALITY_FLOOR[arm[1]]);
                }
            }
        }
        float[] g = recompute(p);
        // pierce from gear goes where Pierce reads it
        p.getEntityData().setFloat("ua_apen_gear", 0.01F * Math.max(0, g[APEN]));
        p.getEntityData().setFloat("ua_spen_gear", 0.01F * Math.max(0, g[SPEN]));
        double speed = 0.004D * g[AGILITY] + 0.01D * armorEnchant(p, 6) - 0.04D * armorPieces(p, 25);
        Stats.setModifier(p.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED), SPEED_ID, "UA gear speed", speed, 1);
        Stats.setModifier(p.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH), HEALTH_ID, "UA gear health", 0.5D * g[HEALTH], 0);
        Stats.setModifier(p.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH), WRAITH_ID, "UA wraith", -0.05D * armorPieces(p, 27), 1);
        if (p.getHealth() > p.getMaxHealth()) {
            p.setHealth(p.getMaxHealth());
        }
        int regen = armorEnchant(p, 18);
        if (regen > 0 && p.ticksExisted % 100 == 3 && p.getHealth() < p.getMaxHealth()) {
            p.heal(Math.min(6, regen));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onHurt(LivingHurtEvent e) {
        EntityLivingBase t = e.getEntityLiving();
        if (t.world.isRemote || !Stats.on(t.world) || e.getAmount() <= 0) {
            return;
        }
        DamageSource s = e.getSource();
        Entity src = s.getTrueSource();
        if (t instanceof EntityPlayer && !s.canHarmInCreative()) {
            defend(e, (EntityPlayer) t, s, src);
        }
        if (!(src instanceof EntityPlayer) || src == t || echoing) {
            return;
        }
        attack(e, (EntityPlayer) src, t, s);
    }

    private static void defend(LivingHurtEvent e, EntityPlayer p, DamageSource s, Entity src) {
        float[] g = stats(p);
        float dr = Math.min(0.30F, 0.005F * Math.max(0, g[DEFENSE])) + 0.01F * armorEnchant(p, 5) + 0.01F * armorEnchant(p, 25);
        float amount = e.getAmount() * (1.0F - Math.min(0.55F, dr));
        if (Pierce.isMagic(s)) {
            amount *= 1.0F - Math.min(0.40F, armorEnchant(p, 19) / 100.0F);
        }
        if (s == DamageSource.FALL) {
            amount *= 1.0F - Math.min(0.80F, armorEnchant(p, 20) / 100.0F);
        }
        if (weaponEnchant(p) == 23) {
            amount *= 1.25F;
        }
        int mark = armorPieces(p, 26);
        if (mark > 0 && p.getRNG().nextFloat() < 0.05F * mark) {
            amount *= 2.0F;
        }
        // Undying armor: survive a killing blow
        int undying = armorPieces(p, 21);
        if (undying > 0 && amount >= p.getHealth() + p.getAbsorptionAmount()) {
            int best = 999;
            for (ItemStack a : p.getArmorInventoryList()) {
                int[] en = enchant(a);
                if (en != null && en[0] == 21) {
                    best = Math.min(best, ENCHANT_VAL[21][en[1] - 1]);
                }
            }
            long now = p.world.getTotalWorldTime();
            long last = p.getEntityData().getLong("ua_undying");
            if (now - last >= best * 20L || last == 0) {
                p.getEntityData().setLong("ua_undying", now);
                amount = Math.max(0, p.getHealth() - 1.0F);
                p.addPotionEffect(new PotionEffect(MobEffects.REGENERATION, 100, 1));
                p.addPotionEffect(new PotionEffect(MobEffects.RESISTANCE, 60, 2));
                Fx.at(p.world, Fx.FLASH, Fx.HOLY, p.posX, p.posY + 1, p.posZ, 1.2);
                Fx.sound(p.world, p.posX, p.posY, p.posZ, "minecraft:item.totem.use", 0.7F, 1.2F);
            }
        }
        e.setAmount(amount);
        if (!(src instanceof EntityLivingBase) || src == p || echoing) {
            return;
        }
        EntityLivingBase att = (EntityLivingBase) src;
        // Thorns (melee only)
        if (s.getImmediateSource() == src && g[THORNS] > 0) {
            float back = amount * Math.min(0.5F, 0.01F * g[THORNS]);
            if (back > 0.1F) {
                echoing = true;
                try {
                    att.attackEntityFrom(DamageSource.causeThornsDamage(p), back);
                } finally {
                    echoing = false;
                }
            }
        }
        // armor effect gems hit the attacker
        int[] fx = effects(p)[1];
        for (int k = 1; k < EFFECTS; k++) {
            if (fx[k] > 0 && p.getRNG().nextFloat() < Math.min(60, fx[k]) / 100.0F) {
                proc(p, att, k, true);
            }
        }
    }

    private static void attack(LivingHurtEvent e, EntityPlayer p, EntityLivingBase t, DamageSource s) {
        float[] g = stats(p);
        float m = 1.0F + 0.01F * g[POWER];
        int[] en = enchant(p.getHeldItemMainhand());
        long now = t.world.getTotalWorldTime();
        boolean melee = s.getImmediateSource() == p && !Pierce.isMagic(s);
        if (en != null) {
            int v = ENCHANT_VAL[en[0]][en[1] - 1];
            switch (en[0]) {
                case 0:
                    if (!Pierce.isMagic(s)) {
                        Pierce.addSunder(t, now, v);
                    }
                    break;
                case 1:
                    if (Pierce.isMagic(s)) {
                        Pierce.addHex(t, now, v);
                    }
                    break;
                case 2:
                    if (t.getHealth() < t.getMaxHealth() * 0.3F) {
                        m *= 1.0F + v / 100.0F;
                    }
                    break;
                case 4:
                    if (p.getRNG().nextFloat() < v / 100.0F) {
                        Fx.at(t.world, Fx.LIGHTNING, Fx.LIGHTNING_E, t.posX, t.posY, t.posZ, 1.0);
                        Fx.sound(t.world, t.posX, t.posY, t.posZ, "minecraft:entity.lightning.thunder", 0.6F, 1.4F);
                        magicHit(p, t, 2.0F + 2.0F * en[1]);
                    }
                    break;
                case 9:
                    if (p.getRNG().nextFloat() < v / 100.0F) {
                        proc(p, t, 1, false);
                    }
                    break;
                case 10:
                    if (p.getRNG().nextFloat() < v / 100.0F) {
                        proc(p, t, 2, false);
                    }
                    break;
                case 11:
                    if (p.getRNG().nextFloat() < v / 100.0F) {
                        proc(p, t, 4, false);
                    }
                    break;
                case 13: {
                    int[] mo = MOMENTUM.computeIfAbsent(p.getUniqueID(), k -> new int[3]);
                    if (mo[0] == t.getEntityId() && now - mo[2] < 40) {
                        mo[1] = Math.min(5, mo[1] + 1);
                    } else {
                        mo[0] = t.getEntityId();
                        mo[1] = 0;
                    }
                    mo[2] = (int) now;
                    m *= 1.0F + mo[1] * v / 100.0F;
                    break;
                }
                case 22: {
                    m *= 1.0F + v / 100.0F;
                    long last = p.getEntityData().getLong("ua_bloodpact");
                    if (now - last >= 10 && p.getHealth() > 2.0F) {
                        p.getEntityData().setLong("ua_bloodpact", now);
                        p.setHealth(p.getHealth() - 1.0F);
                    }
                    break;
                }
                default:
                    break;
            }
        }
        float amount = e.getAmount() * m;
        e.setAmount(amount);
        float leech = Math.min(0.15F, 0.003F * Math.max(0, g[LEECH])) + (en != null && en[0] == 8 ? ENCHANT_VAL[8][en[1] - 1] / 100.0F : 0);
        if (leech > 0) {
            p.heal(amount * leech);
        }
        // weapon effect gems
        int[] fx = effects(p)[0];
        for (int k = 1; k < EFFECTS; k++) {
            if (k != 5 && fx[k] > 0 && p.getRNG().nextFloat() < Math.min(60, fx[k]) / 100.0F) {
                proc(p, t, k, false);
            }
        }
        if (en != null && en[0] == 12 && melee) {
            float splash = amount * ENCHANT_VAL[12][en[1] - 1] / 100.0F;
            echoing = true;
            try {
                for (EntityLivingBase o : t.world.getEntitiesWithinAABB(EntityLivingBase.class, t.getEntityBoundingBox().grow(2.5D),
                        x -> x != t && SkillShapes.isTarget(x))) {
                    o.hurtResistantTime = 0;
                    o.attackEntityFrom(DamageSource.causePlayerDamage(p), splash);
                }
            } finally {
                echoing = false;
            }
            Fx.at(t.world, Fx.SLASH, Fx.FIRE, t.posX, t.posY + 1, t.posZ, 1.0);
        }
        if (en != null && en[0] == 3 && p.getRNG().nextFloat() < ENCHANT_VAL[3][en[1] - 1] / 100.0F) {
            final EntityLivingBase target = t;
            final float half = amount * 0.5F;
            final EntityPlayer pp = p;
            p.getServer().addScheduledTask(() -> {
                if (target.isEntityAlive()) {
                    echoing = true;
                    try {
                        target.hurtResistantTime = 0;
                        target.attackEntityFrom(DamageSource.causePlayerDamage(pp), half);
                        Fx.at(target.world, Fx.SLASH, Fx.ARCANE, target.posX, target.posY + 1, target.posZ, 0.8);
                    } finally {
                        echoing = false;
                    }
                }
            });
        }
    }

    private static void magicHit(EntityPlayer p, EntityLivingBase t, float dmg) {
        echoing = true;
        try {
            t.hurtResistantTime = 0;
            t.attackEntityFrom(DamageSource.causeIndirectMagicDamage(p, p), dmg);
        } finally {
            echoing = false;
        }
    }

    /** One effect proc on a target (from a weapon hit, or on the attacker when armor procs). */
    private static void proc(EntityPlayer p, EntityLivingBase t, int effect, boolean fromArmor) {
        switch (effect) {
            case 1:
                t.setFire(4);
                Fx.at(t.world, Fx.IMPACT, Fx.FIRE, t.posX, t.posY + t.height * 0.5, t.posZ, 0.6);
                break;
            case 2:
                t.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 60, 2));
                Fx.at(t.world, Fx.IMPACT, Fx.ICE, t.posX, t.posY + t.height * 0.5, t.posZ, 0.6);
                break;
            case 3: {
                int n = 0;
                Fx.at(t.world, Fx.LIGHTNING, Fx.LIGHTNING_E, t.posX, t.posY, t.posZ, 0.6);
                magicHit(p, t, 3.0F);
                for (EntityLivingBase o : t.world.getEntitiesWithinAABB(EntityLivingBase.class, t.getEntityBoundingBox().grow(4.0D),
                        x -> x != t && SkillShapes.isTarget(x))) {
                    if (n++ >= 3) {
                        break;
                    }
                    Fx.at(o.world, Fx.LIGHTNING, Fx.LIGHTNING_E, o.posX, o.posY, o.posZ, 0.5);
                    magicHit(p, o, 3.0F);
                }
                break;
            }
            case 4:
                t.addPotionEffect(new PotionEffect(MobEffects.POISON, 80, 1));
                Fx.at(t.world, Fx.IMPACT, Fx.POISON, t.posX, t.posY + t.height * 0.5, t.posZ, 0.6);
                break;
            case 5:
                if (fromArmor) {
                    p.addPotionEffect(new PotionEffect(MobEffects.ABSORPTION, 100, 0));
                }
                break;
            case 6:
                if (fromArmor) {
                    double dx = t.posX - p.posX, dz = t.posZ - p.posZ, d = Math.max(0.1, Math.sqrt(dx * dx + dz * dz));
                    t.motionX += dx / d * 1.2;
                    t.motionZ += dz / d * 1.2;
                    t.motionY += 0.3;
                    t.velocityChanged = true;
                } else {
                    for (EntityLivingBase o : t.world.getEntitiesWithinAABB(EntityLivingBase.class, t.getEntityBoundingBox().grow(5.0D),
                            x -> x != t && SkillShapes.isTarget(x))) {
                        double dx = t.posX - o.posX, dz = t.posZ - o.posZ, d = Math.max(0.5, Math.sqrt(dx * dx + dz * dz));
                        o.motionX += dx / d * 0.7;
                        o.motionZ += dz / d * 0.7;
                        o.velocityChanged = true;
                    }
                }
                Fx.at(t.world, Fx.FLASH, Fx.ARCANE, t.posX, t.posY + 1, t.posZ, 0.9);
                break;
            default:
                break;
        }
    }

    /** Soulstone in a weapon: kills may heal and drop a Soul Coin. */
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent e) {
        Entity src = e.getSource().getTrueSource();
        if (!(src instanceof EntityPlayer) || e.getEntityLiving().world.isRemote || !Stats.on(src.world) || e.getEntityLiving() instanceof EntityPlayer) {
            return;
        }
        EntityPlayer p = (EntityPlayer) src;
        int c = effects(p)[0][5];
        if (c > 0 && p.getRNG().nextFloat() < Math.min(60, c * 2) / 100.0F) {
            p.heal(3.0F);
            Coins.give(p, 1);
            EntityLivingBase d = e.getEntityLiving();
            Fx.at(d.world, Fx.FLASH, Fx.ICE, d.posX, d.posY + 1, d.posZ, 0.8);
        }
    }
}
