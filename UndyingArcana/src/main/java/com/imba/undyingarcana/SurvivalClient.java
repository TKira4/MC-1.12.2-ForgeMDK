package com.imba.undyingarcana;

import org.lwjgl.opengl.GL11;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.color.IItemColor;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.RayTraceResult;
import net.minecraftforge.client.GuiIngameForge;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Client side of the survival needs: thirst droplets above the food bar, a temperature gauge above the armor bar,
 * cold / heat screen edges, sneak + right-click drinking (water in view or rain overhead), no sprinting while thirsty,
 * drink tints and lining tooltips.
 */
@SideOnly(Side.CLIENT)
public class SurvivalClient {
    private static final ResourceLocation ICONS = new ResourceLocation(UndyingArcana.MODID, "textures/gui/survival.png");
    public static boolean on;
    public static float thirst = Survival.MAX, hydration, temp = Survival.NEUTRAL, target = Survival.NEUTRAL;
    public static int flags;
    public static byte[] nutrition = {50, 50, 50, 50, 50};
    private static float shown = Survival.NEUTRAL;

    public static void init() {
        Minecraft.getMinecraft().getItemColors().registerItemColorHandler(new IItemColor() {
            @Override
            public int colorMultiplier(ItemStack s, int tint) {
                if (tint != 0) {
                    return -1;
                }
                for (int i = 0; i < SurvivalItems.DRINKS.length; i++) {
                    if (s.getItem() == SurvivalItems.DRINKS[i]) {
                        return SurvivalItems.DRINK_COLOR[i];
                    }
                }
                return -1;
            }
        }, SurvivalItems.DRINKS);
        Minecraft.getMinecraft().getItemColors().registerItemColorHandler((s, tint) -> tint == 0 ? 0xD6E2EE : -1, Food.SALT_ITEM);
        Minecraft.getMinecraft().getBlockColors().registerBlockColorHandler((state, world, pos, tint) -> tint == 0 ? 0x3F76E4 : -1,
                FoodBlocks.POT);
    }

    public static void onMsg(SkillNet.MsgSurv m) {
        on = m.on;
        thirst = m.thirst;
        hydration = m.hydration;
        temp = m.temp;
        target = m.target;
        flags = m.flags;
        nutrition = m.nutrition;
    }

    private static boolean active(Minecraft mc) {
        return on && mc.player != null && !mc.player.capabilities.isCreativeMode && !mc.player.isSpectator();
    }

    // ------------------------------------------------------------------ input
    @SubscribeEvent
    public void onMouse(MouseEvent e) {
        Minecraft mc = Minecraft.getMinecraft();
        if (e.getButton() != 1 || !e.isButtonstate() || mc.currentScreen != null || mc.player == null || !on || !mc.player.isSneaking()) {
            return;
        }
        ItemStack held = mc.player.getHeldItemMainhand();
        if (held.getItem() == Items.GLASS_BOTTLE || held.getItem() == Items.BUCKET || held.getItem() instanceof SurvivalItems.ItemFlask) {
            return;
        }
        if (mc.objectMouseOver != null && mc.objectMouseOver.typeOfHit == RayTraceResult.Type.ENTITY) {
            return;
        }
        double reach = mc.playerController.getBlockReachDistance();
        if (Survival.waterAt(mc.player, reach) >= 0) {
            // a solid block in front of the water wins (placing, using a chest...)
            if (mc.objectMouseOver != null && mc.objectMouseOver.typeOfHit == RayTraceResult.Type.BLOCK) {
                double block = mc.objectMouseOver.hitVec.distanceTo(mc.player.getPositionEyes(1F));
                net.minecraft.util.math.Vec3d eye = mc.player.getPositionEyes(1F);
                RayTraceResult wr = mc.world.rayTraceBlocks(eye, eye.add(mc.player.getLook(1F).scale(reach)), true, false, false);
                if (wr != null && wr.hitVec.distanceTo(eye) > block - 0.01) {
                    return;
                }
            }
            e.setCanceled(true);
            SkillNet.NET.sendToServer(new SkillNet.MsgDrink(0));
        } else if (mc.player.rotationPitch < -50 && mc.world.isRainingAt(new net.minecraft.util.math.BlockPos(mc.player).up(2))
                && (mc.objectMouseOver == null || mc.objectMouseOver.typeOfHit == RayTraceResult.Type.MISS)) {
            e.setCanceled(true);
            SkillNet.NET.sendToServer(new SkillNet.MsgDrink(1));
        }
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent e) {
        Minecraft mc = Minecraft.getMinecraft();
        if (e.phase != TickEvent.Phase.END || mc.player == null) {
            return;
        }
        shown += (temp - shown) * 0.15F;
        if (active(mc) && thirst <= 6F && mc.player.isSprinting()) {
            mc.player.setSprinting(false);
        }
    }

    // ------------------------------------------------------------------ HUD
    @SubscribeEvent
    public void onOverlay(RenderGameOverlayEvent.Post e) {
        Minecraft mc = Minecraft.getMinecraft();
        if (!active(mc) || !mc.playerController.shouldDrawHUD()) {
            return;
        }
        ScaledResolution r = e.getResolution();
        int w = r.getScaledWidth(), h = r.getScaledHeight();
        if (e.getType() == RenderGameOverlayEvent.ElementType.FOOD) {
            drawThirst(mc, w, h);
        } else if (e.getType() == RenderGameOverlayEvent.ElementType.ARMOR) {
            drawTemp(mc, w, h);
        }
    }

    @SubscribeEvent
    public void onPre(RenderGameOverlayEvent.Pre e) {
        Minecraft mc = Minecraft.getMinecraft();
        if (e.getType() != RenderGameOverlayEvent.ElementType.HOTBAR || !active(mc)) {
            return;
        }
        float cold = Math.max(0, (6.5F - shown) / 6.5F), hot = Math.max(0, (shown - 19F) / 6F);
        if (cold > 0) {
            edges(e.getResolution(), 0x9FD8FF, Math.min(0.55F, cold * 0.55F));
        } else if (hot > 0) {
            edges(e.getResolution(), 0xFF7A20, Math.min(0.45F, hot * 0.45F));
        }
    }

    private void drawThirst(Minecraft mc, int w, int h) {
        int top = h - GuiIngameForge.right_height;
        GuiIngameForge.right_height += 10;
        mc.getTextureManager().bindTexture(ICONS);
        GlStateManager.enableBlend();
        GlStateManager.color(1F, 1F, 1F, 1F);
        int row = (flags & 1) != 0 ? 9 : 0; // sick: green drops
        int left = w / 2 + 91;
        int shake = thirst <= 4F && mc.player.ticksExisted % (int) (thirst * 3 + 1) == 0 ? 1 : 0;
        for (int i = 0; i < 10; i++) {
            int x = left - i * 8 - 9;
            int y = top + (shake != 0 ? mc.player.getRNG().nextInt(2) : 0);
            Gui.drawModalRectWithCustomSizedTexture(x, y, 0, row, 9, 9, 64, 32);
            int v = Math.round(thirst) - i * 2;
            if (v >= 2) {
                Gui.drawModalRectWithCustomSizedTexture(x, y, 9, row, 9, 9, 64, 32);
            } else if (v == 1) {
                Gui.drawModalRectWithCustomSizedTexture(x, y, 18, row, 9, 9, 64, 32);
            }
        }
        GlStateManager.disableBlend();
    }

    private static final int[] GRAD = {0xFF2E5BFF, 0xFF57B8FF, 0xFF9AF0D8, 0xFFFFE070, 0xFFFF8A2A, 0xFFE8302A};

    private void drawTemp(Minecraft mc, int w, int h) {
        int top = h - GuiIngameForge.left_height;
        GuiIngameForge.left_height += 8;
        int x0 = w / 2 - 91, bw = 81, y0 = top + 3;
        Gui.drawRect(x0 - 1, y0 - 1, x0 + bw + 1, y0 + 5, 0xC0101018);
        for (int i = 0; i < bw; i++) {
            float f = i / (float) (bw - 1) * (GRAD.length - 1);
            int a = (int) Math.floor(f), b = Math.min(GRAD.length - 1, a + 1);
            Gui.drawRect(x0 + i, y0, x0 + i + 1, y0 + 4, UiKit.blend(GRAD[a], GRAD[b], f - a));
        }
        // comfortable zone ticks
        int c1 = x0 + Math.round(9F / Survival.TMAX * (bw - 1)), c2 = x0 + Math.round(16F / Survival.TMAX * (bw - 1));
        Gui.drawRect(c1, y0 + 4, c1 + 1, y0 + 5, 0xC0FFFFFF);
        Gui.drawRect(c2, y0 + 4, c2 + 1, y0 + 5, 0xC0FFFFFF);
        int mx = x0 + Math.round(Math.max(0, Math.min(Survival.TMAX, shown)) / Survival.TMAX * (bw - 1));
        boolean danger = shown <= 5F || shown >= 20F;
        int mark = danger && mc.player.ticksExisted % 20 < 10 ? 0xFFFF4040 : 0xFFFFFFFF;
        Gui.drawRect(mx - 2, y0 - 2, mx + 3, y0 + 6, 0xFF101018);
        Gui.drawRect(mx - 1, y0 - 1, mx + 2, y0 + 5, mark);
        // trend arrow toward the place temperature
        float d = target - temp;
        if (Math.abs(d) > 0.6F) {
            int ax = d > 0 ? mx + 4 : mx - 5;
            int col = d > 0 ? 0xFFFFB060 : 0xFF8FD0FF;
            int dir = d > 0 ? 1 : -1;
            Gui.drawRect(ax, y0, ax + 1, y0 + 4, col);
            Gui.drawRect(ax + dir, y0 + 1, ax + dir + 1, y0 + 3, col);
        }
    }

    /** Soft colored edges around the screen. */
    private static void edges(ScaledResolution r, int rgb, float alpha) {
        int w = r.getScaledWidth(), h = r.getScaledHeight();
        int band = Math.max(24, h / 5);
        float cr = (rgb >> 16 & 255) / 255F, cg = (rgb >> 8 & 255) / 255F, cb = (rgb & 255) / 255F;
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.shadeModel(GL11.GL_SMOOTH);
        Tessellator t = Tessellator.getInstance();
        BufferBuilder b = t.getBuffer();
        b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        quad(b, 0, 0, w, band, cr, cg, cb, alpha, alpha, 0, 0); // top
        quad(b, 0, h - band, w, h, cr, cg, cb, 0, 0, alpha, alpha); // bottom
        quad(b, 0, 0, band, h, cr, cg, cb, alpha, 0, 0, alpha); // left
        quad(b, w - band, 0, w, h, cr, cg, cb, 0, alpha, alpha, 0); // right
        t.draw();
        GlStateManager.shadeModel(GL11.GL_FLAT);
        GlStateManager.enableTexture2D();
        GlStateManager.color(1F, 1F, 1F, 1F);
    }

    /** Quad with alpha per corner: top-left, top-right, bottom-right, bottom-left. */
    private static void quad(BufferBuilder b, int x1, int y1, int x2, int y2, float r, float g, float bl, float tl, float tr, float br,
            float blA) {
        b.pos(x1, y2, 0).color(r, g, bl, blA).endVertex();
        b.pos(x2, y2, 0).color(r, g, bl, br).endVertex();
        b.pos(x2, y1, 0).color(r, g, bl, tr).endVertex();
        b.pos(x1, y1, 0).color(r, g, bl, tl).endVertex();
    }

    // ------------------------------------------------------------------ nutrition panel (above the inventory)
    @SubscribeEvent
    public void onScreen(net.minecraftforge.client.event.GuiScreenEvent.DrawScreenEvent.Post e) {
        Minecraft mc = Minecraft.getMinecraft();
        if (!(e.getGui() instanceof net.minecraft.client.gui.inventory.GuiInventory) || !on || mc.player == null) {
            return;
        }
        net.minecraft.client.gui.inventory.GuiContainer g = (net.minecraft.client.gui.inventory.GuiContainer) e.getGui();
        int x0 = g.getGuiLeft(), y0 = g.getGuiTop() - 15, cw = 35;
        Gui.drawRect(x0, y0, x0 + 176, y0 + 13, 0xD0100E0C);
        String hover = null;
        for (int i = 0; i < Food.GROUP.length; i++) {
            int x = x0 + 3 + i * cw, v = nutrition[i];
            mc.fontRenderer.drawStringWithShadow(Food.GROUP[i].substring(0, 1), x, y0 + 3, Food.GROUP_RGB[i]);
            int bx = x + 7, bw = cw - 11;
            Gui.drawRect(bx, y0 + 4, bx + bw, y0 + 9, 0xFF2A2620);
            Gui.drawRect(bx, y0 + 4, bx + Math.round(bw * v / 100F), y0 + 9, 0xFF000000 | Food.GROUP_RGB[i]);
            Gui.drawRect(bx + Math.round(bw * 0.6F), y0 + 3, bx + Math.round(bw * 0.6F) + 1, y0 + 10, 0x90FFFFFF);
            if (e.getMouseX() >= x && e.getMouseX() < x + cw - 2 && e.getMouseY() >= y0 && e.getMouseY() < y0 + 13) {
                hover = Food.GROUP_COLOR[i] + Food.GROUP[i] + " " + v + "/100\n\u00a77At 60+: " + BONUS[i]
                        + "\n\u00a78All five at 40+: +4 max health. At 0: -2 max health.";
            }
        }
        if (hover != null) {
            g.drawHoveringText(java.util.Arrays.asList(hover.split("\n")), e.getMouseX(), e.getMouseY());
        }
    }

    private static final String[] BONUS = {"+8% damage", "+12% mining speed", "-15% thirst", "+5% speed", "+20% mana regen"};

    // ------------------------------------------------------------------ tooltips
    @SubscribeEvent
    public void onTooltip(ItemTooltipEvent e) {
        ItemStack s = e.getItemStack();
        if (s.getItem() instanceof net.minecraft.item.ItemFood && s.getItem() != Food.SPOILED) {
            int g = Food.groups(s);
            if (g != 0) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < Food.GROUP.length; i++) {
                    if ((g & 1 << i) != 0) {
                        sb.append(sb.length() > 0 ? "\u00a78, " : "").append(Food.GROUP_COLOR[i]).append(Food.GROUP[i]);
                    }
                }
                e.getToolTip().add(1, sb.toString());
            }
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.world != null) {
                float f = Food.freshness(s, mc.world.getTotalWorldTime());
                boolean salted = s.hasTagCompound() && s.getTagCompound().getBoolean(Food.SALT);
                if (f >= 0) {
                    long left = s.getTagCompound().getLong(Food.BB) - mc.world.getTotalWorldTime();
                    String time = left >= Food.DAY ? String.format("%.1f days", left / (double) Food.DAY) : Math.max(1, left / 1200) + " min";
                    e.getToolTip().add(1, (f > 0.5F ? "\u00a7aFresh" : f > 0.15F ? "\u00a7eStale" : "\u00a7cSpoils soon") + " \u00a78(" + time
                            + ")" + (salted ? " \u00a77salted" : ""));
                } else if (Food.life(s) > 0) {
                    e.getToolTip().add(1, "\u00a78Keeps " + Food.life(s) / Food.DAY + " days" + (salted ? " (x3 salted)" : ""));
                }
            }
        }
        if (s.hasTagCompound() && s.getTagCompound().hasKey(SurvivalItems.LINING)) {
            int l = s.getTagCompound().getInteger(SurvivalItems.LINING);
            if (l == 1) {
                e.getToolTip().add(1, "\u00a76Warm lining \u00a78(+1.5 warmth)");
            } else if (l == 2) {
                e.getToolTip().add(1, "\u00a7bCool lining \u00a78(-1.5 heat)");
            }
        }
    }
}
