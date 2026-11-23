package com.imba.undyingarcana;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderLiving;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** One renderer for every zombie type: swaps model, texture, scale and shadow by the entity's ZType. */
@SideOnly(Side.CLIENT)
public class RenderUAZombie extends RenderLiving<EntityUAZombie> {
    private static final ZType[] TYPES = ZType.values();
    private final ModelUAZombie[] models = new ModelUAZombie[TYPES.length];
    private final ResourceLocation[] textures = new ResourceLocation[TYPES.length];

    public RenderUAZombie(RenderManager manager) {
        super(manager, new ModelUAZombie(0), 0.5F);
        for (ZType t : TYPES) {
            models[t.ordinal()] = new ModelUAZombie(t.ordinal());
            textures[t.ordinal()] = new ResourceLocation(UndyingArcana.MODID, "textures/entity/zombie/" + t.id() + ".png");
        }
    }

    @Override
    public void doRender(EntityUAZombie e, double x, double y, double z, float yaw, float partialTicks) {
        ZType t = e.type();
        mainModel = models[t.ordinal()];
        shadowSize = 0.5F * t.scale;
        super.doRender(e, x, y, z, yaw, partialTicks);
    }

    @Override
    protected void preRenderCallback(EntityUAZombie e, float partialTicks) {
        float s = e.type().scale;
        GlStateManager.scale(s, s, s);
    }

    @Override
    protected ResourceLocation getEntityTexture(EntityUAZombie e) {
        return textures[e.type().ordinal()];
    }
}
