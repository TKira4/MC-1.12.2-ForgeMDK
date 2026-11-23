package com.imba.undyingarcana;

import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** Renderer for invisible entities (skill bolts are drawn with particles). */
@SideOnly(Side.CLIENT)
public class RenderNothing<T extends Entity> extends Render<T> {
    public RenderNothing(RenderManager m) {
        super(m);
    }

    @Override
    public void doRender(T entity, double x, double y, double z, float yaw, float pt) {
    }

    @Override
    protected ResourceLocation getEntityTexture(T entity) {
        return null;
    }
}
