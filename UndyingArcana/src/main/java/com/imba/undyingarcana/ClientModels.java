package com.imba.undyingarcana;

import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

@Mod.EventBusSubscriber(modid = UndyingArcana.MODID, value = Side.CLIENT)
public final class ClientModels {
    private ClientModels() {
    }

    @SubscribeEvent
    public static void registerModels(ModelRegistryEvent event) {
        net.minecraftforge.fml.client.registry.RenderingRegistry.registerEntityRenderingHandler(EntitySpecialArrow.class, RenderSpecialArrow::new);
        net.minecraftforge.fml.client.registry.RenderingRegistry.registerEntityRenderingHandler(EntityInfernalBrute.class,
                m -> new RenderLSBoss<EntityInfernalBrute>(m, "infernal_brute", 1.7F, 1.7F, 1.7F));
        net.minecraftforge.fml.client.registry.RenderingRegistry.registerEntityRenderingHandler(EntityFrostLich.class,
                m -> new RenderLSBoss<EntityFrostLich>(m, "frost_lich", 1.5F, 1.5F, 1.5F));
        net.minecraftforge.fml.client.registry.RenderingRegistry.registerEntityRenderingHandler(EntityVoidStalker.class,
                m -> new RenderLSBoss<EntityVoidStalker>(m, "void_stalker", 1.15F, 1.5F, 1.15F));
        net.minecraftforge.fml.client.registry.RenderingRegistry.registerEntityRenderingHandler(EntityPlagueMother.class,
                m -> new RenderLSBoss<EntityPlagueMother>(m, "plague_mother", 1.9F, 1.7F, 1.9F));
        net.minecraftforge.fml.client.registry.RenderingRegistry.registerEntityRenderingHandler(EntityBoneColossus.class,
                m -> new RenderLSBoss<EntityBoneColossus>(m, "bone_colossus", 2.2F, 2.2F, 2.2F));
        net.minecraftforge.fml.client.registry.RenderingRegistry.registerEntityRenderingHandler(EntityThunderWarden.class,
                m -> new RenderLSBoss<EntityThunderWarden>(m, "thunder_warden", 1.8F, 1.8F, 1.8F));
        net.minecraftforge.fml.client.registry.RenderingRegistry.registerEntityRenderingHandler(EntityUndyingKing.class,
                m -> new RenderLSBoss<EntityUndyingKing>(m, "undying_king", 1.9F, 1.9F, 1.9F));
        net.minecraftforge.fml.client.registry.RenderingRegistry.registerEntityRenderingHandler(EntityZombieWarlord.class,
                m -> new RenderLSBoss<EntityZombieWarlord>(m, "zombie_warlord", 1.35F, 1.35F, 1.35F));
        net.minecraftforge.fml.client.registry.RenderingRegistry.registerEntityRenderingHandler(EntitySkillBolt.class, RenderSkillBolt::new);
        net.minecraftforge.fml.client.registry.RenderingRegistry.registerEntityRenderingHandler(EntityUAZombie.class, RenderUAZombie::new);
        net.minecraftforge.fml.client.registry.RenderingRegistry.registerEntityRenderingHandler(EntityAcidSpit.class,
                m -> new net.minecraft.client.renderer.entity.RenderSnowball<EntityAcidSpit>(m, net.minecraft.init.Items.SLIME_BALL,
                        net.minecraft.client.Minecraft.getMinecraft().getRenderItem()));
        net.minecraftforge.fml.client.registry.RenderingRegistry.registerEntityRenderingHandler(EntitySkillArrow.class,
                net.minecraft.client.renderer.entity.RenderTippedArrow::new);
        for (Item item : ModItems.ALL) {
            ModelLoader.setCustomModelResourceLocation(item, 0, new ModelResourceLocation(item.getRegistryName(), "inventory"));
        }
    }
}
