package com.imba.undyingarcana;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.init.Items;
import net.minecraft.item.ItemBlock;
import net.minecraft.init.SoundEvents;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.util.SoundEvent;
import net.minecraftforge.common.util.EnumHelper;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@Mod.EventBusSubscriber(modid = UndyingArcana.MODID)
public final class ModItems {
    public static final String[] PATHS = {"berserker", "guardian", "assassin", "sharpshooter", "beastmaster", "skirmisher",
            "elementalist", "summoner", "cleric"};
    public static final String[] CLASSES = {"warrior", "ranger", "mage"};
    public static final String[] SKILLS = {"attack", "defense", "archery", "magic", "agility", "mining", "gathering", "farming", "building"};

    /** Every registered item, for model registration. */
    public static final List<Item> ALL = new ArrayList<>();
    public static Item TAB_ICON;

    private ModItems() {
    }

    private static Item simple(String name, boolean glint, int stack) {
        Item item = glint ? new GlintItem() : new Item();
        item.setRegistryName(UndyingArcana.MODID, name);
        item.setUnlocalizedName(UndyingArcana.MODID + "." + name);
        item.setCreativeTab(UndyingArcana.TAB);
        item.setMaxStackSize(stack);
        ALL.add(item);
        return item;
    }

    /** Vanilla-like stats with a bit more durability (the set takes extra materials). */
    private static ItemArmor.ArmorMaterial material(String path, String mat) {
        String enumName = "LASTSTAND_" + path.toUpperCase() + "_" + mat.toUpperCase();
        String texture = UndyingArcana.MODID + ":" + path + "_" + mat; // textures/models/armor/<path>_<mat>_layer_1.png
        ItemArmor.ArmorMaterial m;
        if ("gold".equals(mat)) {
            m = EnumHelper.addArmorMaterial(enumName, texture, 10, new int[]{1, 3, 5, 2}, 25, SoundEvents.ITEM_ARMOR_EQUIP_GOLD, 0.0F);
            m.setRepairItem(new ItemStack(Items.GOLD_INGOT));
        } else if ("diamond".equals(mat)) {
            m = EnumHelper.addArmorMaterial(enumName, texture, 40, new int[]{3, 6, 8, 3}, 10, SoundEvents.ITEM_ARMOR_EQUIP_DIAMOND, 2.0F);
            m.setRepairItem(new ItemStack(Items.DIAMOND));
        } else {
            m = EnumHelper.addArmorMaterial(enumName, texture, 20, new int[]{2, 5, 6, 2}, 9, SoundEvents.ITEM_ARMOR_EQUIP_IRON, 0.0F);
            m.setRepairItem(new ItemStack(Items.IRON_INGOT));
        }
        return m;
    }


    public static Block RELIC_ALTAR, JEWELER, EXCHANGE;
    public static Item FROST_ARROW, STORM_ARROW, BLAST_ARROW, MYTHIC_CORE, RELIC_SHARD, SOUL_COIN;
    public static Item AMMO_BULLET, AMMO_SHELL, AMMO_BALL;
    private static final List<ItemArmor.ArmorMaterial> MYTHIC_MATS = new ArrayList<>();
    public static final List<Item> BLUEPRINTS = new ArrayList<>();
    public static final List<Item> TOMES = new ArrayList<>();
    public static final String[] BOSSES = {"infernal_brute", "frost_lich", "void_stalker", "plague_mother", "bone_colossus",
            "thunder_warden", "undying_king"};
    private static Item.ToolMaterial LEGEND_TOOL;

    private static void legend(String key, Item item) {
        item.setRegistryName(UndyingArcana.MODID, "legend_" + key);
        item.setUnlocalizedName(UndyingArcana.MODID + ".legend_" + key);
        item.setCreativeTab(UndyingArcana.TAB);
        ALL.add(item);
        BLUEPRINTS.add(simple("blueprint_" + key, true, 1));
    }

    @SubscribeEvent
    public static void registerBlocks(RegistryEvent.Register<Block> event) {
        RELIC_ALTAR = new RelicAltar();
        RELIC_ALTAR.setRegistryName(UndyingArcana.MODID, "relic_altar");
        RELIC_ALTAR.setUnlocalizedName(UndyingArcana.MODID + ".relic_altar");
        RELIC_ALTAR.setCreativeTab(UndyingArcana.TAB);
        event.getRegistry().register(RELIC_ALTAR);
        JEWELER = new UABlock(UABlock.JEWELER);
        JEWELER.setRegistryName(UndyingArcana.MODID, "jeweler_bench");
        JEWELER.setUnlocalizedName(UndyingArcana.MODID + ".jeweler_bench");
        JEWELER.setCreativeTab(UndyingArcana.TAB);
        event.getRegistry().register(JEWELER);
        EXCHANGE = new UABlock(UABlock.EXCHANGE);
        EXCHANGE.setRegistryName(UndyingArcana.MODID, "soul_exchange");
        EXCHANGE.setUnlocalizedName(UndyingArcana.MODID + ".soul_exchange");
        EXCHANGE.setCreativeTab(UndyingArcana.TAB);
        event.getRegistry().register(EXCHANGE);
        FoodBlocks.registerBlocks(event);
    }

    /** Legendary weapons (with their blueprints), the relic shard and the altar's item. */
    private static void registerLegends() {
        LEGEND_TOOL = EnumHelper.addToolMaterial("LASTSTAND_LEGEND", 3, 3000, 8.0F, 4.0F, 18);
        legend("stormcaller", new LegendSword(LEGEND_TOOL, 9D, -2.2D));
        legend("earthbreaker", new LegendSword(LEGEND_TOOL, 12D, -3.2D));
        legend("bloodthirst", new LegendSword(LEGEND_TOOL, 11D, -3.0D));
        legend("frostfang", new LegendSword(LEGEND_TOOL, 8D, -1.8D));
        legend("aegis_edge", new LegendSword(LEGEND_TOOL, 9D, -2.6D));
        legend("dawnbreaker", new LegendSword(LEGEND_TOOL, 12D, -3.0D));
        legend("gravewarden", new LegendSword(LEGEND_TOOL, 11D, -3.0D));
        legend("shadowfang", new LegendSword(LEGEND_TOOL, 7D, -1.5D));
        legend("windpiercer", new LegendBow());
        legend("starfall", new LegendBow());
        legend("thunderstring", new LegendBow());
        legend("hydra", new LegendBow());
        legend("venomstring", new LegendBow());
        legend("phoenix", new LegendSword(LEGEND_TOOL, 6D, -2.6D));
        legend("glacier", new LegendSword(LEGEND_TOOL, 6D, -2.6D));
        legend("stormlord", new LegendSword(LEGEND_TOOL, 6D, -2.6D));
        legend("lich", new LegendSword(LEGEND_TOOL, 6D, -2.6D));
        legend("gaia", new LegendSword(LEGEND_TOOL, 6D, -2.6D));
        legend("archon", new LegendSword(LEGEND_TOOL, 6D, -2.6D));
        legend("seraph", new LegendSword(LEGEND_TOOL, 6D, -2.6D));
        legend("peacemaker", new ItemGun("pistol", "peacemaker"));
        legend("dragons_roar", new ItemGun("shotgun", "dragons_roar"));
        legend("widowmaker", new ItemGun("rifle", "widowmaker"));
        legend("thunderclap", new ItemGun("cannon", "thunderclap"));
        RELIC_SHARD = simple("relic_shard", false, 64);
        SOUL_COIN = simple("soul_coin", false, 64);
        Item altar = new ItemBlock(RELIC_ALTAR);
        altar.setRegistryName(RELIC_ALTAR.getRegistryName());
        ALL.add(altar);
        for (Block b : new Block[]{JEWELER, EXCHANGE}) {
            Item ib = new ItemBlock(b);
            ib.setRegistryName(b.getRegistryName());
            ALL.add(ib);
        }
        Jewelry.register(ALL);
    }

    private static Item arrow(String name, int kind) {
        Item item = new SpecialArrowItem(kind);
        item.setRegistryName(UndyingArcana.MODID, name);
        item.setUnlocalizedName(UndyingArcana.MODID + "." + name);
        item.setCreativeTab(UndyingArcana.TAB);
        ALL.add(item);
        return item;
    }

    /** Ranger guns and their ammo. */
    private static void registerGuns() {
        for (String g : SkillCatalog.GUNS) {
            Item gun = new ItemGun(g, "");
            gun.setRegistryName(UndyingArcana.MODID, "gun_" + g);
            gun.setUnlocalizedName(UndyingArcana.MODID + ".gun_" + g);
            gun.setCreativeTab(UndyingArcana.TAB);
            ALL.add(gun);
        }
        AMMO_BULLET = simple("ammo_bullet", false, 64);
        AMMO_SHELL = simple("ammo_shell", false, 64);
        AMMO_BALL = simple("ammo_cannonball", false, 16);
    }

    /** Mythic armor (one set per Calling, upgraded from diamond path armor), Mythic Core, special arrows. */
    private static void registerBuild1() {
        MYTHIC_CORE = simple("mythic_core", true, 16);
        FROST_ARROW = arrow("frost_arrow", 0);
        STORM_ARROW = arrow("storm_arrow", 1);
        BLAST_ARROW = arrow("blast_arrow", 2);
        for (String b : BOSSES) {
            Item sigil = new BossSigil(b);
            sigil.setRegistryName(UndyingArcana.MODID, "sigil_" + b);
            sigil.setUnlocalizedName(UndyingArcana.MODID + ".sigil_" + b);
            sigil.setCreativeTab(UndyingArcana.TAB);
            ALL.add(sigil);
        }
        EntityEquipmentSlot[] slots = {EntityEquipmentSlot.HEAD, EntityEquipmentSlot.CHEST, EntityEquipmentSlot.LEGS, EntityEquipmentSlot.FEET};
        String[] slotNames = {"helmet", "chestplate", "leggings", "boots"};
        for (String c : CLASSES) {
            String path = "mythic_" + c;
            ItemArmor.ArmorMaterial m = EnumHelper.addArmorMaterial("LASTSTAND_MYTHIC_" + c.toUpperCase(), UndyingArcana.MODID + ":" + path,
                    60, new int[]{3, 7, 9, 3}, 15, SoundEvents.ITEM_ARMOR_EQUIP_DIAMOND, 3.0F);
            m.setRepairItem(new ItemStack(RELIC_SHARD));
            MYTHIC_MATS.add(m);
            for (int i = 0; i < 4; i++) {
                String name = path + "_" + slotNames[i];
                ItemArmor armor = new PathArmor(m, slots[i], path, "mythic");
                armor.setRegistryName(UndyingArcana.MODID, name);
                armor.setUnlocalizedName(UndyingArcana.MODID + "." + name);
                armor.setCreativeTab(UndyingArcana.TAB);
                ALL.add(armor);
            }
        }
    }

    @SubscribeEvent
    public static void registerEntities(RegistryEvent.Register<net.minecraftforge.fml.common.registry.EntityEntry> event) {
        event.getRegistry().register(net.minecraftforge.fml.common.registry.EntityEntryBuilder.create()
                .entity(EntitySpecialArrow.class)
                .id(new net.minecraft.util.ResourceLocation(UndyingArcana.MODID, "special_arrow"), 0)
                .name(UndyingArcana.MODID + ".special_arrow")
                .tracker(64, 20, true)
                .build());
        net.minecraft.world.biome.Biome[] cold = {net.minecraft.init.Biomes.ICE_PLAINS, net.minecraft.init.Biomes.ICE_MOUNTAINS,
                net.minecraft.init.Biomes.COLD_TAIGA, net.minecraft.init.Biomes.COLD_TAIGA_HILLS, net.minecraft.init.Biomes.MUTATED_ICE_FLATS,
                net.minecraft.init.Biomes.MUTATED_TAIGA_COLD};
        event.getRegistry().register(net.minecraftforge.fml.common.registry.EntityEntryBuilder.create()
                .entity(EntityInfernalBrute.class)
                .id(new net.minecraft.util.ResourceLocation(UndyingArcana.MODID, "infernal_brute"), 1)
                .name(UndyingArcana.MODID + ".infernal_brute")
                .tracker(80, 3, true)
                .egg(0x3A1C18, 0xFF8220)
                .spawn(net.minecraft.entity.EnumCreatureType.MONSTER, 1, 1, 1, net.minecraft.init.Biomes.HELL)
                .build());
        event.getRegistry().register(net.minecraftforge.fml.common.registry.EntityEntryBuilder.create()
                .entity(EntityFrostLich.class)
                .id(new net.minecraft.util.ResourceLocation(UndyingArcana.MODID, "frost_lich"), 2)
                .name(UndyingArcana.MODID + ".frost_lich")
                .tracker(80, 3, true)
                .egg(0x263469, 0xAFE6FF)
                .spawn(net.minecraft.entity.EnumCreatureType.MONSTER, 1, 1, 1, cold)
                .build());
        event.getRegistry().register(net.minecraftforge.fml.common.registry.EntityEntryBuilder.create()
                .entity(EntityVoidStalker.class)
                .id(new net.minecraft.util.ResourceLocation(UndyingArcana.MODID, "void_stalker"), 3)
                .name(UndyingArcana.MODID + ".void_stalker")
                .tracker(80, 3, true)
                .egg(0x181022, 0x9646E6)
                .spawn(net.minecraft.entity.EnumCreatureType.MONSTER, 1, 1, 1, net.minecraft.init.Biomes.SKY)
                .build());
        event.getRegistry().register(net.minecraftforge.fml.common.registry.EntityEntryBuilder.create()
                .entity(EntityPlagueMother.class)
                .id(new net.minecraft.util.ResourceLocation(UndyingArcana.MODID, "plague_mother"), 4)
                .name(UndyingArcana.MODID + ".plague_mother")
                .tracker(80, 3, true)
                .egg(0x5A8C46, 0xB9CD46)
                .spawn(net.minecraft.entity.EnumCreatureType.MONSTER, 1, 1, 1, net.minecraft.init.Biomes.SWAMPLAND,
                        net.minecraft.init.Biomes.MUTATED_SWAMPLAND)
                .build());
        event.getRegistry().register(net.minecraftforge.fml.common.registry.EntityEntryBuilder.create()
                .entity(EntityBoneColossus.class)
                .id(new net.minecraft.util.ResourceLocation(UndyingArcana.MODID, "bone_colossus"), 5)
                .name(UndyingArcana.MODID + ".bone_colossus")
                .tracker(80, 3, true)
                .egg(0xE6DCC3, 0x3C3732)
                .spawn(net.minecraft.entity.EnumCreatureType.MONSTER, 1, 1, 1, net.minecraft.init.Biomes.DESERT,
                        net.minecraft.init.Biomes.DESERT_HILLS, net.minecraft.init.Biomes.MUTATED_DESERT)
                .build());
        event.getRegistry().register(net.minecraftforge.fml.common.registry.EntityEntryBuilder.create()
                .entity(EntityThunderWarden.class)
                .id(new net.minecraft.util.ResourceLocation(UndyingArcana.MODID, "thunder_warden"), 6)
                .name(UndyingArcana.MODID + ".thunder_warden")
                .tracker(80, 3, true)
                .egg(0x6E6E76, 0x5AE6FF)
                .spawn(net.minecraft.entity.EnumCreatureType.MONSTER, 1, 1, 1, net.minecraft.init.Biomes.EXTREME_HILLS,
                        net.minecraft.init.Biomes.EXTREME_HILLS_WITH_TREES, net.minecraft.init.Biomes.MUTATED_EXTREME_HILLS)
                .build());
        event.getRegistry().register(net.minecraftforge.fml.common.registry.EntityEntryBuilder.create()
                .entity(EntityUndyingKing.class)
                .id(new net.minecraft.util.ResourceLocation(UndyingArcana.MODID, "undying_king"), 7)
                .name(UndyingArcana.MODID + ".undying_king")
                .tracker(80, 3, true)
                .egg(0x372D46, 0xF5C846)
                .build());
        event.getRegistry().register(net.minecraftforge.fml.common.registry.EntityEntryBuilder.create()
                .entity(EntityZombieWarlord.class)
                .id(new net.minecraft.util.ResourceLocation(UndyingArcana.MODID, "zombie_warlord"), 8)
                .name(UndyingArcana.MODID + ".zombie_warlord")
                .tracker(80, 3, true)
                .egg(0x5A8C46, 0xAFB4BE)
                .build());
        event.getRegistry().register(net.minecraftforge.fml.common.registry.EntityEntryBuilder.create()
                .entity(EntitySkillBolt.class)
                .id(new net.minecraft.util.ResourceLocation(UndyingArcana.MODID, "skill_bolt"), 9)
                .name(UndyingArcana.MODID + ".skill_bolt")
                .tracker(80, 2, true)
                .build());
        event.getRegistry().register(net.minecraftforge.fml.common.registry.EntityEntryBuilder.create()
                .entity(EntitySkillArrow.class)
                .id(new net.minecraft.util.ResourceLocation(UndyingArcana.MODID, "skill_arrow"), 10)
                .name(UndyingArcana.MODID + ".skill_arrow")
                .tracker(64, 20, true)
                .build());
        event.getRegistry().register(net.minecraftforge.fml.common.registry.EntityEntryBuilder.create()
                .entity(EntityUAZombie.class)
                .id(new net.minecraft.util.ResourceLocation(UndyingArcana.MODID, "zombie"), 11)
                .name(UndyingArcana.MODID + ".zombie")
                .tracker(80, 3, true)
                .egg(0x5C804A, 0x6E1212)
                .build());
        event.getRegistry().register(net.minecraftforge.fml.common.registry.EntityEntryBuilder.create()
                .entity(EntityAcidSpit.class)
                .id(new net.minecraft.util.ResourceLocation(UndyingArcana.MODID, "acid_spit"), 12)
                .name(UndyingArcana.MODID + ".acid_spit")
                .tracker(64, 10, true)
                .build());
    }

    @SubscribeEvent
    public static void registerItems(RegistryEvent.Register<Item> event) {
        // Scrolls and tomes.
        for (String c : CLASSES) {
            simple("calling_" + c, false, 16);
        }
        for (String p : PATHS) {
            simple("path_" + p, true, 16);
        }
        TAB_ICON = simple("scroll_rebirth", true, 16);
        for (String s : SKILLS) {
            TOMES.add(simple("tome_" + s, true, 16));
        }
        // Path armor sets: <path>_<material>_<slot>.
        String[] mats = {"iron", "gold", "diamond"};
        EntityEquipmentSlot[] slots = {EntityEquipmentSlot.HEAD, EntityEquipmentSlot.CHEST, EntityEquipmentSlot.LEGS, EntityEquipmentSlot.FEET};
        String[] slotNames = {"helmet", "chestplate", "leggings", "boots"};
        for (String p : PATHS) {
            for (String mat : mats) {
                ItemArmor.ArmorMaterial m = material(p, mat);
                for (int i = 0; i < 4; i++) {
                    String name = p + "_" + mat + "_" + slotNames[i];
                    ItemArmor armor = new PathArmor(m, slots[i], p, mat);
                    armor.setRegistryName(UndyingArcana.MODID, name);
                    armor.setUnlocalizedName(UndyingArcana.MODID + "." + name);
                    armor.setCreativeTab(UndyingArcana.TAB);
                    ALL.add(armor);
                }
            }
        }
        registerLegends();
        SurvivalItems.register(ALL);
        Food.register(ALL);
        FoodBlocks.registerItems(ALL);
        registerBuild1();
        registerGuns();
        Armory.register(ALL);
        for (Item item : ALL) {
            event.getRegistry().register(item);
        }
    }
}
