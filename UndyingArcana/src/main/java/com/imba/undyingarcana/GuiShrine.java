package com.imba.undyingarcana;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Soul Shrine: three blessing cards (Wisdom, Fortune, Vigor) with their donation bar, or the time left while active.
 * Click a card to pick it, then a donate button. Top donors and the goal are in the card's tooltip.
 */
@SideOnly(Side.CLIENT)
public class GuiShrine extends GuiScreen {
    private static final int W = 300, H = 196;
    private static final int[] COL = {0x8CFF8C, 0xFFD050, 0x6EA0FF};
    private static final int[] AMOUNTS = {10, 50, 100, 500};
    private final UiKit.Anim a = new UiKit.Anim();
    private Econ.State st;
    private long at;
    private int x0, y0, sel = -1;

    public GuiShrine(String json) {
        econ(json);
    }

    public void econ(String json) {
        try {
            st = new Gson().fromJson(json, Econ.State.class);
            at = System.currentTimeMillis();
        } catch (RuntimeException e) {
            st = null;
        }
        if (st != null && (sel < 0 || st.left[sel] > 0)) {
            // pick the fullest blessing that is not active
            sel = -1;
            for (int b = 0; b < 3; b++) {
                if (st.left[b] <= 0 && (sel < 0 || st.prog[b] * 1F / st.goal[b] > st.prog[sel] * 1F / st.goal[sel])) {
                    sel = b;
                }
            }
        }
    }

    @Override
    public void initGui() {
        x0 = (width - W) / 2;
        y0 = (height - H) / 2;
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private int cardX(int b) {
        return x0 + 12 + b * 94;
    }

    @Override
    protected void mouseClicked(int mx, int my, int button) throws IOException {
        super.mouseClicked(mx, my, button);
        if (button != 0 || st == null || a.openT(0.28F) < 1) {
            return;
        }
        for (int b = 0; b < 3; b++) {
            if (UiKit.in(mx, my, cardX(b), y0 + 32, 88, 112) && st.left[b] <= 0 && b != sel) {
                sel = b;
                UiKit.click();
                return;
            }
        }
        for (int i = 0; i < AMOUNTS.length; i++) {
            if (UiKit.in(mx, my, x0 + 12 + i * 70, y0 + 154, 64, 18) && canGive(i)) {
                SkillNet.NET.sendToServer(new SkillNet.MsgEconAct(3, sel, AMOUNTS[i]));
                UiKit.click();
            }
        }
    }

    private boolean canGive(int i) {
        return sel >= 0 && st.left[sel] <= 0 && (Coins.count(mc.player) >= Math.min(AMOUNTS[i], st.goal[sel] - st.prog[sel])
                || mc.player.capabilities.isCreativeMode);
    }

    @Override
    public void drawScreen(int mx, int my, float pt) {
        a.frame();
        float open = UiKit.easeOutCubic(a.openT(0.22F));
        int dim = Math.round(0x90 * open);
        drawGradientRect(0, 0, width, height, dim << 24, (dim + 0x10) << 24);
        UiKit.beginOpen(a, x0 + W / 2, y0 + H / 2);
        UiKit.panel(x0, y0, W, H);
        UiKit.header(fontRenderer, x0, y0, W, "SOUL SHRINE");
        String cs = String.format("%,d", Coins.count(mc.player));
        int cw = fontRenderer.getStringWidth(cs);
        UiKit.item(itemRender, new ItemStack(ModItems.SOUL_COIN), x0 + W - 30 - cw, y0 + 4);
        fontRenderer.drawStringWithShadow(cs, x0 + W - 12 - cw, y0 + 9, UiKit.GOLD);
        List<String> tip = null;
        if (st != null) {
            ItemStack[] icons = {new ItemStack(Items.EXPERIENCE_BOTTLE), new ItemStack(ModItems.SOUL_COIN), new ItemStack(Power.MANA_POTION)};
            long gone = (System.currentTimeMillis() - at) / 50;
            for (int b = 0; b < 3; b++) {
                int x = cardX(b), y = y0 + 32;
                boolean active = st.left[b] - gone > 0, h = UiKit.in(mx, my, x, y, 88, 112);
                UiKit.inset(x, y, 88, 112, 0xEB14100B);
                if (b == sel) {
                    frameRect(x, y, 88, 112, 0xFFE0B040);
                } else if (h && !active) {
                    frameRect(x, y, 88, 112, 0xFF6B5A36);
                }
                GlStateManager.pushMatrix();
                GlStateManager.translate(x + 28, y + 10, 0);
                GlStateManager.scale(2F, 2F, 1F);
                UiKit.item(itemRender, icons[b], 0, 0);
                GlStateManager.popMatrix();
                String n = Econ.BLESSING[b], d = Econ.BLESSING_DESC[b];
                fontRenderer.drawStringWithShadow(n, x + 44 - fontRenderer.getStringWidth(n) / 2, y + 50, COL[b]);
                fontRenderer.drawStringWithShadow(d, x + 44 - fontRenderer.getStringWidth(d) / 2, y + 62, UiKit.SOFT);
                if (active) {
                    long s = (st.left[b] - gone) / 20;
                    String t = String.format("%d:%02d left", s / 60, s % 60);
                    fontRenderer.drawStringWithShadow(t, x + 44 - fontRenderer.getStringWidth(t) / 2, y + 90, 0x8CFF8C);
                } else {
                    int c = COL[b];
                    UiKit.bar(x + 8, y + 92, 72, 7, st.prog[b] / (float) st.goal[b], 0xFF000000 | c, 0xFF000000 | lighten(c));
                }
                if (h) {
                    tip = new ArrayList<>();
                    tip.add(n + ": " + d + " for everyone, 1 hour");
                    tip.add(active ? "\u00a7aActive" : "\u00a77" + st.prog[b] + " / " + st.goal[b] + " coins");
                    if (st.top[b] != null && !st.top[b].isEmpty()) {
                        tip.add("\u00a78Top: " + st.top[b]);
                    }
                }
            }
            for (int i = 0; i < AMOUNTS.length; i++) {
                boolean bh = UiKit.in(mx, my, x0 + 12 + i * 70, y0 + 154, 64, 18), can = canGive(i);
                String label = "  +" + AMOUNTS[i];
                UiKit.button(fontRenderer, x0 + 12 + i * 70, y0 + 154, 64, 18, label, can, bh && can ? 1F : 0F, 0xE0B040);
                int tw = fontRenderer.getStringWidth(label);
                GlStateManager.pushMatrix();
                GlStateManager.translate(x0 + 12 + i * 70 + (64 - tw) / 2 + 1, y0 + 159 - (bh && can ? 1 : 0), 0);
                GlStateManager.scale(0.5F, 0.5F, 1F);
                UiKit.item(itemRender, new ItemStack(ModItems.SOUL_COIN), 0, 0);
                GlStateManager.popMatrix();
            }
            String foot = sel >= 0 ? "Donating to " + Econ.BLESSING[sel] : "Every blessing is active";
            fontRenderer.drawString(foot, x0 + 12, y0 + 178, UiKit.FAINT);
        }
        UiKit.endOpen();
        if (tip != null) {
            drawHoveringText(tip, mx, my);
        }
        super.drawScreen(mx, my, pt);
    }

    private static int lighten(int c) {
        int r = Math.min(255, (c >> 16 & 255) + 60), g = Math.min(255, (c >> 8 & 255) + 60), b = Math.min(255, (c & 255) + 60);
        return r << 16 | g << 8 | b;
    }

    private static void frameRect(int x, int y, int w, int h, int argb) {
        UiKit.rect(x - 1, y - 1, x + w + 1, y, argb);
        UiKit.rect(x - 1, y + h, x + w + 1, y + h + 1, argb);
        UiKit.rect(x - 1, y - 1, x, y + h + 1, argb);
        UiKit.rect(x + w, y - 1, x + w + 1, y + h + 1, argb);
    }
}
