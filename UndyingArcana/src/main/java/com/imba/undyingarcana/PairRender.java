package com.imba.undyingarcana;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.client.renderer.entity.layers.LayerRenderer;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumHandSide;
import net.minecraftforge.client.event.RenderSpecificHandEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Paired armory weapons (Twin Blades, Twin Sickles, Gauntlets, Claws): the item is one weapon; while the off hand is
 * empty, a copy is drawn in the off hand too, in first person and on every player model.
 */
@SideOnly(Side.CLIENT)
public final class PairRender {
    private PairRender() {
    }

    /** True for the paired armory types. */
    public static boolean paired(ItemStack s) {
        if (s.isEmpty() || s.getItem().getRegistryName() == null) {
            return false;
        }
        int[] a = Armory.parse(s.getItem().getRegistryName().toString());
        return a != null && Armory.PAIRED[a[0]];
    }

    private static ItemStack copyFor(EntityLivingBase e) {
        if (DebugToggles.noPair) {
            return ItemStack.EMPTY;
        }
        ItemStack main = e.getHeldItemMainhand();
        return paired(main) && e.getHeldItemOffhand().isEmpty() ? main : ItemStack.EMPTY;
    }

    /** Registered on the event bus from ClientProxy. */
    public static final class Events {
        @SubscribeEvent
        public void onHand(RenderSpecificHandEvent e) {
            Minecraft mc = Minecraft.getMinecraft();
            if (e.getHand() != EnumHand.OFF_HAND || mc.player == null) {
                return;
            }
            ItemStack copy = copyFor(mc.player);
            if (copy.isEmpty()) {
                return;
            }
            e.setCanceled(true);
            mc.getItemRenderer().renderItemInFirstPerson(mc.player, e.getPartialTicks(), e.getInterpolatedPitch(), EnumHand.OFF_HAND,
                    e.getSwingProgress(), copy, e.getEquipProgress());
        }
    }

    /** Third person: the copy in the off hand of the player model. */
    public static final class Layer implements LayerRenderer<AbstractClientPlayer> {
        private final RenderPlayer render;

        public Layer(RenderPlayer render) {
            this.render = render;
        }

        @Override
        public void doRenderLayer(AbstractClientPlayer p, float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks,
                                  float netHeadYaw, float headPitch, float scale) {
            ItemStack copy = copyFor(p);
            if (copy.isEmpty()) {
                return;
            }
            EnumHandSide side = p.getPrimaryHand().opposite();
            boolean left = side == EnumHandSide.LEFT;
            GlStateManager.pushMatrix();
            if (p.isSneaking()) {
                GlStateManager.translate(0.0F, 0.2F, 0.0F);
            }
            ((ModelBiped) render.getMainModel()).postRenderArm(0.0625F, side);
            GlStateManager.rotate(-90.0F, 1.0F, 0.0F, 0.0F);
            GlStateManager.rotate(180.0F, 0.0F, 1.0F, 0.0F);
            GlStateManager.translate((left ? -1 : 1) / 16.0F, 0.125F, -0.625F);
            Minecraft.getMinecraft().getItemRenderer().renderItemSide(p, copy,
                    left ? ItemCameraTransforms.TransformType.THIRD_PERSON_LEFT_HAND : ItemCameraTransforms.TransformType.THIRD_PERSON_RIGHT_HAND,
                    left);
            GlStateManager.popMatrix();
        }

        @Override
        public boolean shouldCombineTextures() {
            return false;
        }
    }

    /** Adds the layer to both player skins (classic and slim) and registers the first-person handler. */
    public static void init() {
        for (RenderPlayer r : Minecraft.getMinecraft().getRenderManager().getSkinMap().values()) {
            r.addLayer(new Layer(r));
        }
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new Events());
    }
}
