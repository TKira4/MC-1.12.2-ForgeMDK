package com.imba.undyingarcana;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.EntityEntry;
import net.minecraftforge.registries.IForgeRegistryEntry;

/**
 * Last Stand -> Undying Arcana: every missing "laststand:<name>" item, block and entity in an old save is remapped to
 * "undyingarcana:<name>" (same names), so items in inventories and chests, placed blocks and bosses carry over.
 */
@Mod.EventBusSubscriber(modid = UndyingArcana.MODID)
public final class Migration {
    private static final String OLD = "laststand";

    private Migration() {
    }

    private static <T extends IForgeRegistryEntry<T>> void remap(RegistryEvent.MissingMappings<T> e) {
        for (RegistryEvent.MissingMappings.Mapping<T> m : e.getAllMappings()) {
            if (!OLD.equals(m.key.getResourceDomain())) {
                continue;
            }
            T target = m.registry.getValue(new ResourceLocation(UndyingArcana.MODID, m.key.getResourcePath()));
            if (target != null) {
                m.remap(target);
            } else {
                m.warn();
            }
        }
    }

    @SubscribeEvent
    public static void items(RegistryEvent.MissingMappings<Item> e) {
        remap(e);
    }

    @SubscribeEvent
    public static void blocks(RegistryEvent.MissingMappings<Block> e) {
        remap(e);
    }

    @SubscribeEvent
    public static void entities(RegistryEvent.MissingMappings<EntityEntry> e) {
        remap(e);
    }
}
