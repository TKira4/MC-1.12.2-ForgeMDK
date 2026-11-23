package com.imba.undyingarcana;

import java.util.List;
import java.util.Random;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.init.Items;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.util.EnumHelper;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.oredict.ShapedOreRecipe;
import net.minecraftforge.oredict.ShapelessOreRecipe;

/**
 * The armory: 39 weapon types in 4 tiers (Iron, Steel, Arcane, Soulforged), item id arm_<type>_<tier>.
 * Iron is crafted; each higher tier is an upgrade recipe that keeps the weapon's jewels, scroll and enchantments.
 * Until every type has its own move set, a type uses the moves of the closest existing weapon (MOVE).
 */
@Mod.EventBusSubscriber(modid = UndyingArcana.MODID)
public final class Armory {
    public static final String[] TIER = {"iron", "steel", "arcane", "soulforged"};
    public static final String[] TIER_NAME = {"Iron", "Steel", "Arcane", "Soulforged"};

    public static final String[] TYPE = {"longsword", "claymore", "katana", "rapier", "scimitar", "dagger", "twinblades", "cleaver",
            "spear", "glaive", "halberd", "scythe", "lance", "trident",
            "battleaxe", "greataxe", "handaxe", "warhammer", "mace", "flail",
            "whip", "sickles", "gauntlets", "claws", "nunchaku", "bostaff",
            "shortbow", "longbow", "crossbow", "repeater", "knives", "chakram", "boomerang", "javelin",
            "wand", "staff", "orb", "scepter", "runefocus"};
    public static final String[] NAME = {"Longsword", "Claymore", "Katana", "Rapier", "Scimitar", "Dagger", "Twin Blades", "Cleaver",
            "Spear", "Glaive", "Halberd", "Scythe", "Lance", "Trident",
            "Battleaxe", "Greataxe", "Hand Axe", "Warhammer", "Mace", "Flail",
            "Whip", "Twin Sickles", "Gauntlets", "Claws", "Nunchaku", "Bo Staff",
            "Shortbow", "Longbow", "Crossbow", "Repeater", "Throwing Knives", "Chakram", "Boomerang", "Javelin",
            "Wand", "Staff", "Orb", "Scepter", "Rune Focus"};
    /** Skill move set used by each type (SkillCatalog types). */
    public static final String[] MOVE = {"longsword", "greatsword", "katana", "rapier", "saber", "dagger", "twinblades", "cleaver",
            "spear", "glaive", "halberd", "scythe", "lance", "trident",
            "battleaxe", "greataxe", "handaxe", "warhammer", "mace", "flail",
            "whip", "sickles", "gauntlets", "claws", "nunchaku", "staff",
            "bow", "longbow", "crossbow", "repeater", "knives", "chakram", "boomerang", "javelin",
            "m_wand", "m_staff", "m_orb", "m_scepter", "m_rune"};
    /** Iron-tier attack damage (hearts x2) and attack speed modifier (vanilla sword: 6, -2.4). Bows ignore these. */
    private static final float[] DMG = {6, 8, 5.5F, 4.5F, 5.5F, 4, 4.5F, 7,
            5.5F, 6.5F, 7, 7, 6.5F, 6,
            8, 9.5F, 6, 9, 7, 7.5F,
            4.5F, 4.5F, 5, 4, 4.5F, 5,
            0, 0, 0, 0, 3.5F, 5, 5, 6,
            3, 4, 3, 3.5F, 3};
    private static final float[] SPD = {-2.4F, -3.0F, -2.1F, -1.8F, -2.2F, -1.5F, -1.6F, -2.8F,
            -2.6F, -2.8F, -3.0F, -2.9F, -2.9F, -2.7F,
            -3.0F, -3.3F, -2.6F, -3.2F, -2.7F, -2.9F,
            -2.2F, -1.8F, -1.6F, -1.4F, -1.7F, -2.2F,
            0, 0, 0, 0, -1.4F, -2.2F, -2.4F, -2.6F,
            -2.0F, -2.6F, -2.2F, -2.2F, -2.2F};
    /** Arrow damage multiplier of the bow types (shortbow, longbow, crossbow, repeater). */
    private static final float[] BOW_DMG = {1.0F, 1.25F, 1.4F, 1.05F};
    private static final float[] TIER_DMG = {0, 1, 2.5F, 4};
    private static final float[] TIER_ARROW = {1.0F, 1.15F, 1.3F, 1.45F};
    private static final int[] TIER_DUR = {300, 700, 1300, 2200};
    private static final int FIRST_BOW = 26;
    /** One weapon per hand: the off hand shows a copy while it is empty (PairRender). */
    public static final boolean[] PAIRED = new boolean[TYPE.length];

    static {
        for (String t : new String[]{"twinblades", "sickles", "gauntlets", "claws"}) {
            PAIRED[typeIndex(t)] = true;
        }
    }

    /** Iron-tier crafting patterns. Keys: see KEYS. */
    private static final String[][] RECIPE = {
            {" I ", " I ", " L "}, {"II", "II", "L "}, {"  I", " I ", "L  "}, {"  N", " N ", "IL "},
            {" II", " I ", "L  "}, {"I", "L"}, {"I I", "L L"}, {"III", "IL "},
            {"  I", " S ", "S  "}, {" II", " S ", "S  "}, {"FI ", " SI", "S  "}, {"III", " S ", "S  "},
            {" I ", "III", " S "}, {"I I", "ISI", " S "},
            {"III", "ISI", " S "}, {"BI ", "BS ", " S "}, {"II", "IS"}, {"BBB", " S ", " S "}, {" N ", "NIN", " S "},
            {"SN ", "  N", "  I"},
            {"  L", " L ", "S  "}, {"II ", "I I", "S S"}, {"I I", "ILI"}, {"NNN", "ILI"}, {"SNS"}, {"  N", " S ", "S  "},
            {" ST", "L T", " ST"}, {"IST", "S T", "IST"}, {"ITI", " S ", " S "}, {"ICI", "ITI", " S "},
            {"III", "LLL"}, {"NIN", "I I", "NIN"}, {"SSI", "S  ", "I  "}, {"  I", " S ", "E  "},
            {" P", "S "}, {"  P", " S ", "S  "}, {" P ", "IPI", " S "}, {"GPG", " S ", " S "}, {"XPX", "XLX"}};

    public static final Item[][] ITEMS = new Item[TYPE.length][TIER.length];
    private static final Item.ToolMaterial[] MATS = new Item.ToolMaterial[TIER.length];

    private Armory() {
    }

    public static boolean isBow(int type) {
        return type >= FIRST_BOW && type < FIRST_BOW + 4;
    }

    public static int typeIndex(String t) {
        for (int i = 0; i < TYPE.length; i++) {
            if (TYPE[i].equals(t)) {
                return i;
            }
        }
        return -1;
    }

    /** [type, tier] of an armory item id ("undyingarcana:arm_<type>_<tier>"), or null. */
    public static int[] parse(String id) {
        String pre = UndyingArcana.MODID + ":arm_";
        if (!id.startsWith(pre)) {
            return null;
        }
        String rest = id.substring(pre.length());
        int u = rest.lastIndexOf('_');
        if (u < 0) {
            return null;
        }
        int t = typeIndex(rest.substring(0, u));
        int r = -1;
        for (int i = 0; i < TIER.length; i++) {
            if (TIER[i].equals(rest.substring(u + 1))) {
                r = i;
            }
        }
        return t < 0 || r < 0 ? null : new int[]{t, r};
    }

    private static EnumRarity rarity(int tier) {
        return tier == 0 ? EnumRarity.COMMON : tier == 1 ? EnumRarity.UNCOMMON : tier == 2 ? EnumRarity.RARE : EnumRarity.EPIC;
    }

    /** Melee, thrown and magic armory weapons. */
    public static class Weapon extends ItemSword {
        private final int type, tier;

        public Weapon(int type, int tier) {
            super(MATS[tier]);
            this.type = type;
            this.tier = tier;
            String n = TYPE[type];
            if (n.equals("nunchaku") || n.equals("flail") || n.equals("whip")) {
                // swing frames: the model swaps to the swing_0..2 variants while the holder's arm swings
                addPropertyOverride(new net.minecraft.util.ResourceLocation(UndyingArcana.MODID, "swing"), new net.minecraft.item.IItemPropertyGetter() {
                    @Override
                    public float apply(ItemStack stack, net.minecraft.world.World world, net.minecraft.entity.EntityLivingBase e) {
                        if (e == null || !e.isSwingInProgress) return 0.0F;
                        if (e.swingingHand == null || e.getHeldItem(e.swingingHand) != stack) return 0.0F;
                        return e.swingProgress + 0.01F;
                    }
                });
            }
        }

        @Override
        public Multimap<String, AttributeModifier> getItemAttributeModifiers(EntityEquipmentSlot slot) {
            Multimap<String, AttributeModifier> map = HashMultimap.create();
            if (slot == EntityEquipmentSlot.MAINHAND) {
                map.put(SharedMonsterAttributes.ATTACK_DAMAGE.getName(),
                        new AttributeModifier(ATTACK_DAMAGE_MODIFIER, "Weapon modifier", DMG[type] + TIER_DMG[tier] - 1.0D, 0));
                map.put(SharedMonsterAttributes.ATTACK_SPEED.getName(),
                        new AttributeModifier(ATTACK_SPEED_MODIFIER, "Weapon modifier", SPD[type], 0));
            }
            return map;
        }

        @Override
        public EnumRarity getRarity(ItemStack stack) {
            return rarity(tier);
        }
    }

    /** Shortbow, longbow, crossbow and repeater: vanilla bow use, arrow damage scaled by type and tier. */
    public static class Bow extends ItemBow {
        private final int type, tier;

        public Bow(int type, int tier) {
            this.type = type;
            this.tier = tier;
            setMaxDamage(TIER_DUR[tier]);
            // vanilla "pull" only reads Items.BOW, so armory bows / crossbows report their own draw for the pull models
            addPropertyOverride(new net.minecraft.util.ResourceLocation("pull"), new net.minecraft.item.IItemPropertyGetter() {
                @Override
                public float apply(ItemStack stack, net.minecraft.world.World world, net.minecraft.entity.EntityLivingBase e) {
                    if (e == null || e.getActiveItemStack() != stack) return 0.0F;
                    return (stack.getMaxItemUseDuration() - e.getItemInUseCount()) / 20.0F;
                }
            });
        }

        @Override
        public EntityArrow customizeArrow(EntityArrow arrow) {
            arrow.setDamage(arrow.getDamage() * BOW_DMG[type - FIRST_BOW] * TIER_ARROW[tier]);
            return arrow;
        }

        @Override
        public EnumRarity getRarity(ItemStack stack) {
            return rarity(tier);
        }

        @Override
        public int getItemEnchantability() {
            return 10 + 4 * tier;
        }

        @Override
        public boolean getIsRepairable(ItemStack toRepair, ItemStack repair) {
            return repair.getItem() == (tier < 2 ? Items.IRON_INGOT : ModItems.RELIC_SHARD);
        }
    }

    /** Registers all armory items into the list (called from ModItems.registerItems). */
    public static void register(List<Item> all) {
        int[] ench = {14, 10, 18, 22};
        for (int r = 0; r < TIER.length; r++) {
            MATS[r] = EnumHelper.addToolMaterial("UA_ARMORY_" + TIER[r].toUpperCase(), 2, TIER_DUR[r], 6.0F, 2.0F, ench[r]);
            // repair item (anvil, Quality Tools reforging station): iron for Iron / Steel, Relic Shard for Arcane / Soulforged
            MATS[r].setRepairItem(r < 2 ? new ItemStack(Items.IRON_INGOT) : new ItemStack(ModItems.RELIC_SHARD));
        }
        for (int t = 0; t < TYPE.length; t++) {
            for (int r = 0; r < TIER.length; r++) {
                Item it = isBow(t) ? new Bow(t, r) : new Weapon(t, r);
                String name = "arm_" + TYPE[t] + "_" + TIER[r];
                it.setRegistryName(UndyingArcana.MODID, name);
                it.setUnlocalizedName(UndyingArcana.MODID + "." + name);
                it.setCreativeTab(UndyingArcana.TAB);
                ITEMS[t][r] = it;
                all.add(it);
            }
        }
    }

    // ------------------------------------------------------------------ recipes
    private static Object key(char c) {
        switch (c) {
            case 'I': return "ingotIron";
            case 'N': return "nuggetIron";
            case 'S': return "stickWood";
            case 'L': return "leather";
            case 'T': return "string";
            case 'F': return Items.FLINT;
            case 'B': return "blockIron";
            case 'E': return "feather";
            case 'P': return "gemLapis";
            case 'G': return "ingotGold";
            case 'X': return "stone";
            case 'C': return "chestWood";
            default: return null;
        }
    }

    /** Lowest gear quality after upgrading to each tier: Steel Uncommon, Arcane Rare, Soulforged Exotic. */
    public static final int[] QUALITY_FLOOR = {0, 1, 2, 3};

    /**
     * Upgrade to the next tier: shapeless, the result keeps the input weapon's NBT (jewels, scroll, enchantments).
     * Its quality is raised to the tier's floor (QUALITY_FLOOR); a higher quality is kept.
     */
    public static class Upgrade extends ShapelessOreRecipe {
        public Upgrade(ResourceLocation group, ItemStack out, Object... in) {
            super(group, out, in);
        }

        @Override
        public ItemStack getCraftingResult(InventoryCrafting inv) {
            ItemStack out = output.copy();
            for (int i = 0; i < inv.getSizeInventory(); i++) {
                ItemStack s = inv.getStackInSlot(i);
                if (!s.isEmpty() && s.getItem().getRegistryName() != null && parse(s.getItem().getRegistryName().toString()) != null
                        && s.hasTagCompound()) {
                    out.setTagCompound(s.getTagCompound().copy());
                }
            }
            int[] at = out.getItem().getRegistryName() == null ? null : parse(out.getItem().getRegistryName().toString());
            if (at != null) {
                Gear.roll(out, new Random(), 0);
                if (Gear.quality(out) < QUALITY_FLOOR[at[1]]) {
                    Gear.setQuality(out, QUALITY_FLOOR[at[1]]);
                }
            }
            return out;
        }
    }

    @SubscribeEvent
    public static void registerRecipes(RegistryEvent.Register<IRecipe> event) {
        ResourceLocation group = new ResourceLocation(UndyingArcana.MODID, "armory");
        for (int t = 0; t < TYPE.length; t++) {
            String[] rows = RECIPE[t];
            StringBuilder keys = new StringBuilder();
            java.util.List<Object> args = new java.util.ArrayList<>();
            java.util.Collections.addAll(args, (Object[]) rows);
            for (String row : rows) {
                for (char c : row.toCharArray()) {
                    if (c != ' ' && keys.indexOf(String.valueOf(c)) < 0) {
                        keys.append(c);
                        args.add(c);
                        args.add(key(c));
                    }
                }
            }
            ShapedOreRecipe craft = new ShapedOreRecipe(group, new ItemStack(ITEMS[t][0]), args.toArray());
            craft.setRegistryName(UndyingArcana.MODID, "arm_" + TYPE[t] + "_iron");
            event.getRegistry().register(craft);
            Object[][] extra = {
                    {"blockIron", Items.COAL, Items.COAL},
                    {ModItems.RELIC_SHARD, ModItems.RELIC_SHARD, "blockLapis"},
                    {ModItems.MYTHIC_CORE, ModItems.RELIC_SHARD, ModItems.RELIC_SHARD}};
            for (int r = 1; r < TIER.length; r++) {
                Object[] in = new Object[1 + extra[r - 1].length];
                in[0] = ITEMS[t][r - 1];
                System.arraycopy(extra[r - 1], 0, in, 1, extra[r - 1].length);
                Upgrade up = new Upgrade(group, new ItemStack(ITEMS[t][r]), in);
                up.setRegistryName(UndyingArcana.MODID, "arm_" + TYPE[t] + "_" + TIER[r]);
                event.getRegistry().register(up);
            }
        }
    }

    // ------------------------------------------------------------------ loot
    /** A random armory weapon of this tier. */
    public static ItemStack random(Random r, int tier) {
        return new ItemStack(ITEMS[r.nextInt(TYPE.length)][Math.max(0, Math.min(TIER.length - 1, tier))]);
    }

    /** Shop specs (Iron and Steel of every type) with prices. */
    public static void shopOffers(List<String> specs, List<Integer> prices) {
        for (int t = 0; t < TYPE.length; t++) {
            specs.add(UndyingArcana.MODID + ":arm_" + TYPE[t] + "_iron");
            prices.add(30);
            specs.add(UndyingArcana.MODID + ":arm_" + TYPE[t] + "_steel");
            prices.add(90);
        }
    }
}
