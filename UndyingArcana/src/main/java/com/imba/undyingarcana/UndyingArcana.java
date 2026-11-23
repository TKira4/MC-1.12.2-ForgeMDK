package com.imba.undyingarcana;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;

/**
 * Undying Arcana - items, bosses and the weapon skill system for the Zombie Angry modpack.
 * Weapon skills (3 per weapon: Quick / Heavy / Art), their VFX and the skill HUD live in the Skill* / Fx* classes;
 * classes, ranks, paths and builds are still decided by the pack's CraftTweaker scripts (read through player data).
 */
@Mod(modid = UndyingArcana.MODID, name = UndyingArcana.NAME, version = UndyingArcana.VERSION, acceptedMinecraftVersions = "[1.12.2]")
public class UndyingArcana {
    public static final String MODID = "undyingarcana";
    public static final String NAME = "Undying Arcana";
    public static final String VERSION = "0.2.0";

    @SidedProxy(clientSide = "com.imba.undyingarcana.ClientProxy", serverSide = "com.imba.undyingarcana.CommonProxy")
    public static CommonProxy proxy;

    public static final CreativeTabs TAB = new CreativeTabs(MODID) {
        @Override
        public ItemStack getTabIconItem() {
            return new ItemStack(ModItems.TAB_ICON);
        }
    };

    @Mod.EventHandler
    public void serverStarting(net.minecraftforge.fml.common.event.FMLServerStartingEvent event) {
        event.registerServerCommand(new DamageMeter.Command());
        event.registerServerCommand(new ZombieDirector.Command());
        event.registerServerCommand(new Stats.Command());
        event.registerServerCommand(new Quests.Command());
        Quests.load();
        Shop.load();
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        SkillNet.init();
        proxy.init();
    }
}
