package com.imba.undyingarcana;

import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;

/**
 * Skill bolt renderer. Look 3 (skill arrows) draws a full-bright vanilla arrow plus an additive glowing streak in the
 * element colours; every other look stays invisible (particles only).
 */
@SideOnly(Side.CLIENT)
public class RenderSkillBolt extends Render<EntitySkillBolt> {
    private static final ResourceLocation ARROW = new ResourceLocation("textures/entity/projectiles/arrow.png");

    public RenderSkillBolt(RenderManager m) {
        super(m);
    }

    @Override
    public void doRender(EntitySkillBolt e, double x, double y, double z, float yaw, float pt) {
        if (e.isInvisible()) {
            return;
        }
        if (e.look() == 4 || e.look() == 5) {
            renderItem(e, x, y, z, pt);
            return;
        }
        if (e.look() != 3) {
            return;
        }
        double mx = e.motionX, my = e.motionY, mz = e.motionZ;
        double hor = Math.sqrt(mx * mx + mz * mz);
        double speed = Math.sqrt(hor * hor + my * my);
        if (speed < 1.0E-3) {
            return;
        }
        float ry = (float) (Math.atan2(mx, mz) * 180.0 / Math.PI);
        float rp = (float) (Math.atan2(my, hor) * 180.0 / Math.PI);
        float scale = Math.max(1.0F, e.size() / 0.3F) * 1.25F;
        int el = e.element();

        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);
        GlStateManager.rotate(ry - 90.0F, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(rp, 0.0F, 0.0F, 1.0F);
        GlStateManager.scale(scale, scale, scale);
        GlStateManager.disableLighting();
        float lx = OpenGlHelper.lastBrightnessX, ly = OpenGlHelper.lastBrightnessY;
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240.0F, 240.0F);

        drawArrow();
        drawStreak(el, Math.min(4.0, 1.2 + speed * 0.9) / scale);

        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lx, ly);
        GlStateManager.enableLighting();
        GlStateManager.popMatrix();
    }

    /** Thrown armory weapon: the item model, spinning end over end (look 4) or flat (look 5), full bright. */
    private void renderItem(EntitySkillBolt e, double x, double y, double z, float pt) {
        net.minecraft.item.ItemStack s = e.item();
        if (s.isEmpty()) {
            return;
        }
        double mx = e.motionX, mz = e.motionZ;
        float ry = (float) (Math.atan2(mx, mz) * 180.0 / Math.PI);
        float spin = (e.ticksExisted + pt) * 40.0F;
        float scale = Math.max(1.0F, e.size() / 0.4F) * 0.9F;
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y + 0.1, z);
        GlStateManager.rotate(ry, 0.0F, 1.0F, 0.0F);
        if (e.look() == 5) {
            GlStateManager.rotate(90.0F, 1.0F, 0.0F, 0.0F);
            GlStateManager.rotate(spin, 0.0F, 0.0F, 1.0F);
        } else {
            GlStateManager.rotate(-spin, 1.0F, 0.0F, 0.0F);
        }
        GlStateManager.scale(scale, scale, scale);
        GlStateManager.enableRescaleNormal();
        float lx = OpenGlHelper.lastBrightnessX, ly = OpenGlHelper.lastBrightnessY;
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240.0F, 240.0F);
        bindTexture(net.minecraft.client.renderer.texture.TextureMap.LOCATION_BLOCKS_TEXTURE);
        net.minecraft.client.Minecraft.getMinecraft().getRenderItem().renderItem(s,
                net.minecraft.client.renderer.block.model.ItemCameraTransforms.TransformType.FIXED);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lx, ly);
        GlStateManager.disableRescaleNormal();
        GlStateManager.popMatrix();
    }

    /** Vanilla arrow model (same quads as RenderArrow), pointing along +X. */
    private void drawArrow() {
        bindTexture(ARROW);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.enableRescaleNormal();
        GlStateManager.pushMatrix();
        GlStateManager.rotate(45.0F, 1.0F, 0.0F, 0.0F);
        float k = 0.05625F;
        GlStateManager.scale(k, k, k);
        GlStateManager.translate(-4.0F, 0.0F, 0.0F);
        Tessellator t = Tessellator.getInstance();
        BufferBuilder b = t.getBuffer();
        float u0 = 0.0F, u1 = 0.5F, v0 = 0.0F, v1 = 0.15625F;
        float fu0 = 0.0F, fu1 = 0.15625F, fv0 = 0.15625F, fv1 = 0.3125F;
        b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        b.pos(-7, -2, -2).tex(fu0, fv0).endVertex();
        b.pos(-7, -2, 2).tex(fu1, fv0).endVertex();
        b.pos(-7, 2, 2).tex(fu1, fv1).endVertex();
        b.pos(-7, 2, -2).tex(fu0, fv1).endVertex();
        b.pos(-7, 2, -2).tex(fu0, fv0).endVertex();
        b.pos(-7, 2, 2).tex(fu1, fv0).endVertex();
        b.pos(-7, -2, 2).tex(fu1, fv1).endVertex();
        b.pos(-7, -2, -2).tex(fu0, fv1).endVertex();
        t.draw();
        for (int j = 0; j < 4; j++) {
            GlStateManager.rotate(90.0F, 1.0F, 0.0F, 0.0F);
            b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
            b.pos(-8, -2, 0).tex(u0, v0).endVertex();
            b.pos(8, -2, 0).tex(u1, v0).endVertex();
            b.pos(8, 2, 0).tex(u1, v1).endVertex();
            b.pos(-8, 2, 0).tex(u0, v1).endVertex();
            t.draw();
        }
        GlStateManager.popMatrix();
        GlStateManager.disableRescaleNormal();
    }

    /** Two crossed additive sheets behind the arrow: bright core at the head fading out along the tail. */
    private void drawStreak(int el, double len) {
        float[] core = FxClient.palette(el, 0);
        float[] main = FxClient.palette(el, 1);
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        GlStateManager.depthMask(false);
        GlStateManager.disableCull();
        GlStateManager.disableAlpha();
        Tessellator t = Tessellator.getInstance();
        BufferBuilder b = t.getBuffer();
        double head = 0.2, w = 0.16, wc = 0.06;
        b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        for (int pass = 0; pass < 2; pass++) {
            double ww = pass == 0 ? w : wc;
            float[] c = pass == 0 ? main : core;
            float a = pass == 0 ? 0.55F : 0.9F;
            // horizontal sheet
            b.pos(head, 0, -ww).color(c[0], c[1], c[2], a).endVertex();
            b.pos(head, 0, ww).color(c[0], c[1], c[2], a).endVertex();
            b.pos(-len, 0, ww * 0.2).color(c[0], c[1], c[2], 0.0F).endVertex();
            b.pos(-len, 0, -ww * 0.2).color(c[0], c[1], c[2], 0.0F).endVertex();
            // vertical sheet
            b.pos(head, -ww, 0).color(c[0], c[1], c[2], a).endVertex();
            b.pos(head, ww, 0).color(c[0], c[1], c[2], a).endVertex();
            b.pos(-len, ww * 0.2, 0).color(c[0], c[1], c[2], 0.0F).endVertex();
            b.pos(-len, -ww * 0.2, 0).color(c[0], c[1], c[2], 0.0F).endVertex();
        }
        t.draw();
        GlStateManager.enableAlpha();
        GlStateManager.enableCull();
        GlStateManager.depthMask(true);
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();
    }

    @Override
    protected ResourceLocation getEntityTexture(EntitySkillBolt e) {
        return ARROW;
    }
}
