package com.imba.undyingarcana;

import org.lwjgl.opengl.GL11;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.EnumHandSide;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Weapon skill HUD: three slots right of the hotbar (Quick / Heavy / Art) with key labels, cooldown sweep and seconds,
 * Heavy charge tiers and locked state; a header line (build, imbue, preset) and a short toast naming the three moves
 * whenever the held weapon changes. State comes from the server (SkillNet.MsgHud); timers count down locally.
 */
@SideOnly(Side.CLIENT)
public class SkillHud {
    private static final int[] BORDER = {0xFF3CC8E6, 0xFFF08C28, 0xFFBE5AFF};
    private static SkillNet.MsgHud cur;
    private static long ticks;
    private static long recv;
    private static int toast;
    private static final int[] flash = new int[3];
    private static final boolean[] wasCooling = new boolean[3];

    public static void receive(SkillNet.MsgHud msg) {
        if (msg.active && (cur == null || !cur.active || !msg.type.equals(cur.type))) {
            toast = 70;
        }
        cur = msg;
        recv = ticks;
    }

    private static int cdLeft(int i) {
        if (cur == null) {
            return 0;
        }
        return (int) Math.max(0, cur.cdLeft[i] - (ticks - recv));
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END || Minecraft.getMinecraft().isGamePaused()) {
            return;
        }
        ticks++;
        if (toast > 0) {
            toast--;
        }
        for (int i = 0; i < 3; i++) {
            if (flash[i] > 0) {
                flash[i]--;
            }
            boolean cooling = cur != null && cur.active && cdLeft(i) > 0;
            if (wasCooling[i] && !cooling && cur != null && cur.active) {
                flash[i] = 8;
            }
            wasCooling[i] = cooling;
        }
        if (Minecraft.getMinecraft().world == null) {
            cur = null;
        }
    }

    @SubscribeEvent
    public void onOverlay(RenderGameOverlayEvent.Post e) {
        if (e.getType() != RenderGameOverlayEvent.ElementType.HOTBAR || cur == null || !cur.active || DebugToggles.noHud) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.player.isSpectator() || mc.gameSettings.hideGUI) {
            return;
        }
        ScaledResolution sr = e.getResolution();
        int w = sr.getScaledWidth(), h = sr.getScaledHeight();
        int x0 = w / 2 + 91 + 6;
        if (mc.player.getPrimaryHand() == EnumHandSide.LEFT && !mc.player.getHeldItemOffhand().isEmpty()) {
            x0 += 29;
        }
        if (mc.gameSettings.attackIndicator == 2) {
            x0 += 24; // past the hotbar attack indicator, leaving room for the focus bar
        }
        int y = h - 22;
        FontRenderer fr = mc.fontRenderer;
        float pt = e.getPartialTicks();

        // header
        if (!cur.header.isEmpty()) {
            int hw = fr.getStringWidth(cur.header);
            int hx = Math.min(x0, w - 2 - hw);
            fr.drawStringWithShadow(cur.header, hx, y - 21, 0xC8C8FF);
        }
        for (int i = 0; i < 3; i++) {
            drawSlot(mc, fr, i, x0 + i * 25, y, pt);
        }
        // martial combo: four pips under the crosshair (lit = hits so far; the 4th hit is the finisher)
        if (cur.combo > 0 && !mc.gameSettings.hideGUI) {
            for (int i = 0; i < 4; i++) {
                int px = w / 2 - 10 + i * 5, py = h / 2 + 20;
                Gui.drawRect(px, py, px + 3, py + 3, i < cur.combo ? 0xFFFFB43C : i == 3 ? 0x90FF5050 : 0x90000000);
            }
        }
        // focus: a thin vertical bar in the gap between the hotbar and the skill slots
        if (cur.focusMax > 0) {
            float f = Math.max(0, Math.min(1F, cur.focus / (float) cur.focusMax));
            int fh = Math.round(20 * f);
            Gui.drawRect(x0 - 5, y, x0 - 2, y + 22, 0xB00A0A10);
            Gui.drawRect(x0 - 4, y + 21 - fh, x0 - 3, y + 21, 0xFFFFB43C);
        }
        if (cur.gun) {
            drawAmmo(fr, x0 + 76, y, pt);
        }
        // toast with the three move names when the weapon changes
        if (toast > 0) {
            int alpha = (int) (255 * Math.min(1.0F, toast / 15.0F));
            if (alpha > 8) {
                String s = "\u00a7b" + cur.name[0] + " \u00a77| \u00a76" + cur.name[1] + " \u00a77| \u00a7d" + cur.name[2];
                int sw = fr.getStringWidth(s);
                GlStateManager.enableBlend();
                fr.drawStringWithShadow(s, (w - sw) / 2.0F, h - 72, 0xFFFFFF | (alpha << 24));
                GlStateManager.disableBlend();
            }
        }
        GlStateManager.color(1F, 1F, 1F, 1F);
    }

    private static void drawSlot(Minecraft mc, FontRenderer fr, int i, int x, int y, float pt) {
        boolean unlocked = cur.unlocked[i];
        int left = cdLeft(i);
        int total = Math.max(1, cur.cdTotal[i]);
        // background + border
        Gui.drawRect(x, y, x + 22, y + 22, 0xB00A0A10);
        int border = BORDER[i];
        if (flash[i] > 0) {
            border = 0xFFFFFFFF;
        }
        int tier = -1;
        if (i == 1 && cur.chargeEl >= 0) {
            double el = cur.chargeEl + (ticks - recv) + pt;
            int need = Math.max(1, cur.chargeNeed);
            tier = el >= need * 2 ? 3 : (el >= need * 1.5 ? 2 : (el >= need ? 1 : 0));
            float pulse = (float) (0.6 + 0.4 * Math.sin((ticks + pt) * 0.6));
            int a = (int) (pulse * 255);
            border = (a << 24) | 0xFFC83C;
        }
        Gui.drawRect(x, y, x + 22, y + 1, border);
        Gui.drawRect(x, y + 21, x + 22, y + 22, border);
        Gui.drawRect(x, y, x + 1, y + 22, border);
        Gui.drawRect(x + 21, y, x + 22, y + 22, border);
        // icon
        if (!cur.icon[i].isEmpty()) {
            mc.getTextureManager().bindTexture(new ResourceLocation(UndyingArcana.MODID, "textures/skills/" + cur.icon[i] + ".png"));
            GlStateManager.enableBlend();
            if (unlocked) {
                GlStateManager.color(1F, 1F, 1F, 1F);
            } else {
                GlStateManager.color(0.35F, 0.35F, 0.35F, 1F);
            }
            Gui.drawModalRectWithCustomSizedTexture(x + 3, y + 3, 0, 0, 16, 16, 16, 16);
            GlStateManager.color(1F, 1F, 1F, 1F);
        }
        if (!unlocked) {
            // padlock + required level
            Gui.drawRect(x + 15, y + 3, x + 20, y + 7, 0xFFC8C8C8);
            Gui.drawRect(x + 16, y + 1, x + 17, y + 3, 0xFFC8C8C8);
            Gui.drawRect(x + 18, y + 1, x + 19, y + 3, 0xFFC8C8C8);
            Gui.drawRect(x + 16, y + 1, x + 19, y + 2, 0xFFC8C8C8);
            String s = String.valueOf(cur.need[i]);
            fr.drawStringWithShadow(s, x + 11 - fr.getStringWidth(s) / 2.0F + 1, y + 7, 0xD2D2D2);
        } else if (left <= 0 && cur.cost[i] > cur.focus) {
            // not enough focus: dim the icon and show the cost
            Gui.drawRect(x + 1, y + 1, x + 21, y + 21, 0x90200C00);
            String s = String.valueOf(cur.cost[i]);
            fr.drawStringWithShadow(s, x + 11 - fr.getStringWidth(s) / 2.0F + 1, y + 7, 0xFFB43C);
        } else if (left > 0) {
            pie(x + 3, y + 3, 16, (float) left / total);
            String s = String.valueOf(left / 20 + 1);
            fr.drawStringWithShadow(s, x + 11 - fr.getStringWidth(s) / 2.0F + 1, y + 7, 0xFFFFFF);
        }
        if (tier >= 0) {
            int need = Math.max(1, cur.chargeNeed);
            double el = cur.chargeEl + (ticks - recv) + pt;
            double[] edges = {0, need, need * 1.5, need * 2.0};
            for (int k = 0; k < 3; k++) {
                int bx = x + 2 + k * 6;
                Gui.drawRect(bx, y + 18, bx + 5, y + 20, 0xFF3C3228);
                double f = (el - edges[k]) / (edges[k + 1] - edges[k]);
                if (k == 0) {
                    f = el / need;
                }
                f = Math.max(0, Math.min(1, f));
                if (f > 0) {
                    Gui.drawRect(bx, y + 18, bx + (int) Math.round(5 * f), y + 20, k < tier ? 0xFFFFC83C : 0xFFB08A30);
                }
            }
        }
        // key label
        String key = SkillKeys.label(i);
        fr.drawStringWithShadow(key, x + 11 - fr.getStringWidth(key) / 2.0F + 1, y - 10, 0xFFFF55);
    }

    /** Magazine / reserve counter and reload bar for guns, right of the skill slots. */
    private static void drawAmmo(FontRenderer fr, int x, int y, float pt) {
        Gui.drawRect(x, y, x + 34, y + 22, 0xB00A0A10);
        int left = (int) Math.max(0, cur.reloadLeft - (ticks - recv));
        if (left > 0) {
            float f = 1F - (left - pt) / Math.max(1, cur.reloadTotal);
            Gui.drawRect(x + 2, y + 16, x + 32, y + 19, 0xFF3C3228);
            Gui.drawRect(x + 2, y + 16, x + 2 + (int) (30 * Math.max(0, Math.min(1, f))), y + 19, 0xFFFFC83C);
            fr.drawStringWithShadow("RLD", x + 17 - fr.getStringWidth("RLD") / 2.0F, y + 4, 0xFFC83C);
        } else {
            String m = String.valueOf(cur.mag);
            int col = cur.mag == 0 ? 0xFF5555 : (cur.mag * 4 <= cur.magMax ? 0xFFAA33 : 0xFFFFFF);
            fr.drawStringWithShadow(m, x + 17 - fr.getStringWidth(m) / 2.0F, y + 3, col);
            String r = "/" + (cur.reserve >= 999 ? "inf" : String.valueOf(cur.reserve));
            GlStateManager.pushMatrix();
            GlStateManager.translate(x + 17 - fr.getStringWidth(r) * 0.35F, y + 13, 0);
            GlStateManager.scale(0.7F, 0.7F, 1F);
            fr.drawStringWithShadow(r, 0, 0, 0xAAAAAA);
            GlStateManager.popMatrix();
        }
    }

    /** Dark clockwise sweep over a square icon: frac of the square stays covered. */
    static void pie(int x, int y, int size, float frac) {
        float cx = x + size / 2.0F, cy = y + size / 2.0F, half = size / 2.0F;
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        Tessellator tes = Tessellator.getInstance();
        BufferBuilder b = tes.getBuffer();
        b.begin(GL11.GL_TRIANGLE_FAN, DefaultVertexFormats.POSITION_COLOR);
        b.pos(cx, cy, 0).color(0, 0, 0, 0.72F).endVertex();
        int steps = 48;
        double start = -Math.PI / 2;
        // counter-clockwise from 12 o'clock so the cleared part grows clockwise
        for (int k = 0; k <= steps; k++) {
            double a = start - (Math.PI * 2 * frac) * k / steps;
            double dx = Math.cos(a), dy = Math.sin(a);
            double s = half / Math.max(Math.abs(dx), Math.abs(dy));
            b.pos(cx + dx * s, cy + dy * s, 0).color(0, 0, 0, 0.72F).endVertex();
        }
        tes.draw();
        // light overall dim
        Gui.drawRect(x, y, x + size, y + size, 0x30000000);
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
    }
}
