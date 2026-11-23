package com.imba.undyingarcana;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderLiving;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.entity.layers.LayerHeldItem;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class RenderLSBoss<T extends net.minecraft.entity.EntityLiving> extends RenderLiving<T> {
    private final ResourceLocation texture;
    private final float sx, sy, sz;

    public RenderLSBoss(RenderManager manager, String boss, float sx, float sy, float sz) {
        super(manager, new ModelLSBoss(boss), 0.9F * sx);
        this.texture = new ResourceLocation(UndyingArcana.MODID, "textures/entity/boss/" + boss + ".png");
        this.sx = sx;
        this.sy = sy;
        this.sz = sz;
        addLayer(new LayerHeldItem(this));
    }

    @Override
    protected void preRenderCallback(T entity, float partialTicks) {
        GlStateManager.scale(sx, sy, sz);
    }

    @Override
    protected ResourceLocation getEntityTexture(T entity) {
        return texture;
    }
}
