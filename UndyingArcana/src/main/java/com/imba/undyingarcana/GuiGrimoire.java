package com.imba.undyingarcana;

import java.io.IOException;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Grimoire screen (style A). Left: the 8 element tiles; you learn one per open slot (1 + Magic / 20). Right: the six
 * spells with mana, cooldown and mastery; Edit opens the Spell Weave node editor, Print makes a Spell Blueprint.
 */
@SideOnly(Side.CLIENT)
public class GuiGrimoire extends GuiScreen {
    private static final int W = 360, H = 296;
    private final UiKit.Anim a = new UiKit.Anim();
    private int x0, y0, elSlot, hoverEl = -1;

    @Override
    public void initGui() {
        x0 = (width - W) / 2;
        y0 = (height - H) / 2;
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private int rx() {
        return x0 + 146;
    }

    private int tileX(int e) {
        return x0 + 14 + (e % 2) * 60;
    }

    private int tileY(int e) {
        return y0 + 92 + (e / 2) * 18;
    }

    private int rowY(int t) {
        return y0 + 44 + t * 37;
    }

    /** Next learned element slot after 'from' (wrapping), skipping 'skip'; -1 = none (only when allowNone). */
    private static int nextSlot(int[] el, int from, int skip, boolean allowNone) {
        int n = Magic.MAX_ELEMENTS + 1;
        for (int k = 1; k <= n; k++) {
            int w = from + k;
            if (allowNone && (w == Magic.MAX_ELEMENTS || from < 0 && k == n)) {
                return -1;
            }
            w = ((w % (Magic.MAX_ELEMENTS + 1)) + Magic.MAX_ELEMENTS + 1) % (Magic.MAX_ELEMENTS + 1);
            if (w < Magic.MAX_ELEMENTS && w < el[8] && el[w] >= 0 && w != skip) {
                return w;
            }
        }
        return allowNone ? -1 : from;
    }

    /** Slot holding element e, or -1. */
    private static int slotOf(int[] el, int e) {
        for (int i = 0; i < Magic.MAX_ELEMENTS && i < el[8]; i++) {
            if (el[i] == e) {
                return i;
            }
        }
        return -1;
    }

    // ------------------------------------------------------------------ input
    @Override
    protected void mouseClicked(int mx, int my, int button) throws IOException {
        super.mouseClicked(mx, my, button);
        if (button > 1 || a.openT(0.28F) < 1) {
            return;
        }
        int[] el = PowerHud.elements();
        int open = el[8];
        for (int e = 0; e < Magic.ELEMENT.length; e++) {
            if (button == 0 && UiKit.in(mx, my, tileX(e), tileY(e), 58, 16)) {
                int mine = slotOf(el, e);
                if (mine >= 0) {
                    elSlot = mine; // select it (the one a new element replaces when all slots are full)
                } else {
                    int free = -1;
                    for (int i = 0; i < open && free < 0; i++) {
                        if (el[i] < 0) {
                            free = i;
                        }
                    }
                    int target = free >= 0 ? free : (elSlot < open && el[elSlot] >= 0 ? elSlot : -1);
                    if (target >= 0) {
                        SkillNet.NET.sendToServer(new SkillNet.MsgAct(2, target, String.valueOf(e)));
                        elSlot = target;
                    }
                }
                UiKit.click();
                return;
            }
        }
        int rx = rx(), rw = x0 + W - 10 - rx;
        for (int t = 0; t < Magic.SPELLS; t++) {
            int ry = rowY(t);
            if (t < el[10] && UiKit.in(mx, my, rx + rw - 76, ry + 9, 34, 14)) {
                UiKit.click();
                mc.displayGuiScreen(new GuiBlocks(t));
                return;
            }
            if (t < el[10] && UiKit.in(mx, my, rx + rw - 40, ry + 9, 36, 14)) {
                UiKit.click();
                SkillNet.NET.sendToServer(new SkillNet.MsgAct(6, t, ""));
                return;
            }
        }
    }

    // ------------------------------------------------------------------ drawing
    @Override
    public void drawScreen(int mx, int my, float pt) {
        a.frame();
        SkillNet.MsgPower d = PowerHud.data;
        float open = UiKit.easeOutCubic(a.openT(0.22F));
        int dim = Math.round(0x90 * open);
        drawGradientRect(0, 0, width, height, dim << 24, (dim + 0x10) << 24);
        UiKit.beginOpen(a, x0 + W / 2, y0 + H / 2);
        UiKit.panel(x0, y0, W, H);
        UiKit.header(fontRenderer, x0, y0, W, "GRIMOIRE");
        if (d != null) {
            String ms = "\u00a79Mana \u00a7f" + d.mana + "\u00a77/" + d.manaMax;
            fontRenderer.drawStringWithShadow(ms, x0 + W - 12 - fontRenderer.getStringWidth(ms), y0 + 9, 0xFFFFFF);
        }
        GlStateManager.enableBlend();
        int[] el = PowerHud.elements();

        // ---- left: elements
        UiKit.inset(x0 + 10, y0 + 26, 128, H - 34, 0xD80E0C0A);
        fontRenderer.drawStringWithShadow("\u00a7lELEMENTS", x0 + 14, y0 + 30, UiKit.GOLD_TEXT);
        int slotsOpen = el[8], learned = 0;
        for (int i = 0; i < slotsOpen; i++) {
            if (el[i] >= 0) {
                learned++;
            }
        }
        fontRenderer.drawStringWithShadow("Learned " + learned + " / " + slotsOpen, x0 + 14, y0 + 44, learned < slotsOpen ? UiKit.GREEN : 0xD6CCB4);
        String next = slotsOpen >= Magic.MAX_ELEMENTS ? "All 8 slots open" : "Next slot at " + slotsOpen * Magic.MAGIC_PER_ELEMENT + " Magic";
        fontRenderer.drawString(UiKit.fit(fontRenderer, "", next, 118), x0 + 14, y0 + 56, UiKit.MUTED);
        // max 2 hint lines above the tiles (no overlapping text)
        String hint = learned < slotsOpen ? "Click an element to learn it." : "Full: click one of yours, then a new one to swap.";
        int hy = y0 + 68;
        for (String l : fontRenderer.listFormattedStringToWidth(hint, 118)) {
            if (hy > y0 + 78) {
                break;
            }
            fontRenderer.drawString(l, x0 + 14, hy, UiKit.FAINT);
            hy += 10;
        }
        hoverEl = -1;
        for (int e = 0; e < Magic.ELEMENT.length; e++) {
            int tx = tileX(e), ty = tileY(e);
            boolean h = UiKit.in(mx, my, tx, ty, 58, 16);
            int slot = slotOf(el, e);
            boolean mine = slot >= 0;
            if (h) {
                hoverEl = e;
            }
            float hv = a.to("el" + e, h ? 1 : 0, 20);
            UiKit.rect(tx, ty, tx + 58, ty + 16, UiKit.blend(0xFF1A150E, 0xFF000000 | darker(Magic.ELEMENT_RGB[e]), 0.35F + 0.4F * hv));
            if (mine) {
                int bc = slot == elSlot && learned >= slotsOpen ? 0xFFE0B040 : 0xFF000000 | Magic.ELEMENT_RGB[e];
                UiKit.rect(tx, ty, tx + 58, ty + 1, bc);
                UiKit.rect(tx, ty + 15, tx + 58, ty + 16, bc);
                UiKit.rect(tx, ty, tx + 1, ty + 16, bc);
                UiKit.rect(tx + 57, ty, tx + 58, ty + 16, bc);
            }
            String n = Magic.ELEMENT_NAME[e];
            fontRenderer.drawStringWithShadow(n, tx + 29 - fontRenderer.getStringWidth(n) / 2, ty + 4 - Math.round(hv), Magic.ELEMENT_RGB[e]);
        }
        int show = hoverEl >= 0 ? hoverEl : el[Math.max(0, Math.min(Magic.MAX_ELEMENTS - 1, elSlot))];
        if (show >= 0) {
            int ly = y0 + 168;
            for (String l : fontRenderer.listFormattedStringToWidth(Magic.ELEMENT_DESC[show], 118)) {
                if (ly > y0 + H - 26) {
                    break;
                }
                fontRenderer.drawString(l, x0 + 14, ly, UiKit.SOFT);
                ly += 10;
            }
        } else {
            fontRenderer.drawString("Learn your first element.", x0 + 14, y0 + 168, UiKit.SOFT);
        }
        fontRenderer.drawString(UiKit.fit(fontRenderer, "", "Swapping costs " + Magic.CHANGE_COST + " coins", 118), x0 + 14, y0 + H - 18, UiKit.FAINT);

        // ---- right: spell list (each spell is edited in the Spell Weave)
        int rx = rx(), rw = x0 + W - 10 - rx;
        UiKit.inset(rx, y0 + 26, rw, H - 34, 0xD80E0C0A);
        fontRenderer.drawStringWithShadow("\u00a7lSPELLS", rx + 4, y0 + 30, UiKit.GOLD_TEXT);
        String hold = "Hold " + SpellKeys.keyName() + " + 1-6 to cast";
        fontRenderer.drawString(hold, rx + rw - 4 - fontRenderer.getStringWidth(hold), y0 + 30, UiKit.FAINT);
        String[] roman = {"I", "II", "III", "IV", "V"};
        for (int t = 0; t < Magic.SPELLS; t++) {
            int ry = rowY(t);
            boolean locked = t >= el[10];
            UiKit.rect(rx + 2, ry, rx + rw - 2, ry + 32, locked ? 0xFF100D0A : 0xFF1A150E);
            UiKit.rect(rx + 2, ry, rx + 4, ry + 32, locked ? 0xFF2A2620 : 0xFFE0B040);
            if (locked) {
                fontRenderer.drawString("\u00a78Spell " + (t + 1) + " - opens at " + Magic.SPELL_MAGIC[t] + " Magic", rx + 8, ry + 12, 0);
                continue;
            }
            SpellGraph g = GuiWeave.savedGraph(t);
            if (g == null) {
                continue;
            }
            int lv = GuiWeave.masteryOf(t);
            int cost = Math.max(1, Math.round(g.cost() * (1F - 0.05F * (lv - 1))));
            SpellGraph.Node main = g.mainShape();
            int e = main != null ? main.p[1] : el[0];
            if (e < 0) {
                e = el[0];
            }
            int col = e >= 0 ? Magic.ELEMENT_RGB[e] : 0xA0A0A0;
            fontRenderer.drawStringWithShadow(UiKit.fit(fontRenderer, "", (t + 1) + ". " + g.name, rw - 90), rx + 8, ry + 4, col);
            String l2 = "\u00a7b" + cost + " mana \u00a77" + String.format("%.1f", g.cooldown() / 20F) + "s  \u00a76" + roman[lv - 1]
                    + " \u00a78" + g.nodes.size() + " nodes";
            fontRenderer.drawString(UiKit.fit(fontRenderer, "", l2, rw - 90), rx + 8, ry + 18, 0xFFFFFF);
            boolean he = UiKit.in(mx, my, rx + rw - 76, ry + 9, 34, 14), hp = UiKit.in(mx, my, rx + rw - 40, ry + 9, 36, 14);
            UiKit.button(fontRenderer, rx + rw - 76, ry + 9, 34, 14, "Edit", true, a.to("ed" + t, he ? 1 : 0, 20), 0xE0B040);
            UiKit.button(fontRenderer, rx + rw - 40, ry + 9, 36, 14, "Print", true, a.to("pg" + t, hp ? 1 : 0, 20), 0xB070FF);
        }
        UiKit.endOpen();
        super.drawScreen(mx, my, pt);
        if (a.openT(0.28F) >= 1) {
            for (int t = 0; t < Magic.SPELLS && t < el[10]; t++) {
                int ry = rowY(t);
                if (UiKit.in(mx, my, rx + rw - 40, ry + 9, 36, 14)) {
                    drawHoveringText(java.util.Arrays.asList("\u00a7dPrint a Spell Blueprint", "\u00a77Takes 1 paper. Give the blueprint to a friend:",
                            "\u00a77they right-click it, pick a slot and Save."), mx, my);
                } else if (UiKit.in(mx, my, rx + rw - 76, ry + 9, 34, 14)) {
                    drawHoveringText(java.util.Arrays.asList("\u00a7fOpen the Spell Weave editor"), mx, my);
                }
            }
        }
    }

    private static int darker(int rgb) {
        int r = (rgb >> 16 & 255) / 3, g = (rgb >> 8 & 255) / 3, b = (rgb & 255) / 3;
        return r << 16 | g << 8 | b;
    }

    @Override
    public void onGuiClosed() {
        // unsaved designs are dropped on purpose (Revert / Save are explicit)
    }
}
