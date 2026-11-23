package com.imba.undyingarcana;

import net.minecraft.client.renderer.entity.RenderArrow;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class RenderSpecialArrow extends RenderArrow<EntitySpecialArrow> {
    private static final ResourceLocation[] TEX = {
            new ResourceLocation(UndyingArcana.MODID, "textures/entity/frost_arrow.png"),
            new ResourceLocation(UndyingArcana.MODID, "textures/entity/storm_arrow.png"),
            new ResourceLocation(UndyingArcana.MODID, "textures/entity/blast_arrow.png")};

    public RenderSpecialArrow(RenderManager manager) {
        super(manager);
    }

    @Override
    protected ResourceLocation getEntityTexture(EntitySpecialArrow entity) {
        int k = entity.getKind();
        return TEX[k < 0 || k > 2 ? 0 : k];
    }
}
