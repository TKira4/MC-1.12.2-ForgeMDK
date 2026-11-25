package com.imba.undyingarcana;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Relic Forge (Relic Altar, empty hand). Combine tab: pick three relics of the same quality (the first is kept and goes up
 * one quality; jewels of the other two come back). Reroll tab: pick one relic to turn into another relic of its kind.
 */
@SideOnly(Side.CLIENT)
public class GuiForge extends GuiScreen {
    private static final int W = 300, H = 212, PER_ROW = 13, ROWS = 2;
    private static final int[] QRGB = {0xFFFFFF, 0x55FF55, 0x5555FF, 0xAA00AA, 0xFFAA00};
    private static final String[] TABS = {"Combine", "Reroll"};
    private final UiKit.Anim a = new UiKit.Anim();
    private final List<Integer> sel = new ArrayList<>();
    private int x0, y0, tab;

    @Override
    public void initGui() {
        x0 = (width - W) / 2;
        y0 = (height - H) / 2;
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    /** Inventory slots holding a rolled relic (shown in the grid, max 26). */
    private List<Integer> relics() {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < mc.player.inventory.mainInventory.size() && out.size() < PER_ROW * ROWS; i++) {
            ItemStack s = mc.player.inventory.getStackInSlot(i);
            if (Relics.isRelic(s) && Gear.rolled(s)) {
                out.add(i);
            }
        }
        return out;
    }

    private ItemStack at(int slot) {
        return mc.player.inventory.getStackInSlot(slot);
    }

    private int tabX(int t) {
        int x = x0 + 12;
        for (int i = 0; i < t; i++) {
            x += fontRenderer.getStringWidth(TABS[i]) + 16;
        }
        return x;
    }

    private int cellX(int i) {
        return x0 + 12 + i % PER_ROW * 21;
    }

    private int cellY(int i) {
        return y0 + 60 + i / PER_ROW * 22;
    }

    private int innerY() {
        return y0 + 108;
    }

    /** Selection still valid (inventory can change under the screen). */
    private void clean() {
        sel.removeIf(s -> !Relics.isRelic(at(s)) || !Gear.rolled(at(s)));
        if (!sel.isEmpty()) {
            int q = Gear.quality(at(sel.get(0)));
            sel.removeIf(s -> Gear.quality(at(s)) != q);
        }
    }

    private int cost() {
        if (sel.isEmpty()) {
            return 0;
        }
        int q = Gear.quality(at(sel.get(0)));
        return tab == 0 ? RelicAltar.COMBINE_COST[Math.min(4, q + 1)] : RelicAltar.rerollCost(q);
    }

    private boolean canAct() {
        if (sel.isEmpty()) {
            return false;
        }
        boolean rich = Coins.count(mc.player) >= cost() || mc.player.capabilities.isCreativeMode;
        if (tab == 0) {
            return sel.size() == 3 && Gear.quality(at(sel.get(0))) < 4 && rich;
        }
        return !Relics.sameKind(Relics.defOf(at(sel.get(0)))).isEmpty() && rich;
    }

    @Override
    protected void mouseClicked(int mx, int my, int button) throws IOException {
        super.mouseClicked(mx, my, button);
        if (button != 0 || a.openT(0.28F) < 1) {
            return;
        }
        clean();
        for (int t = 0; t < TABS.length; t++) {
            if (t != tab && UiKit.in(mx, my, tabX(t) - 2, y0 + 28, fontRenderer.getStringWidth(TABS[t]) + 4, 14)) {
                tab = t;
                sel.clear();
                UiKit.click();
                return;
            }
        }
        List<Integer> rs = relics();
        for (int i = 0; i < rs.size(); i++) {
            if (!UiKit.in(mx, my, cellX(i), cellY(i), 18, 18)) {
                continue;
            }
            int slot = rs.get(i);
            if (sel.contains(slot)) {
                sel.remove((Integer) slot);
            } else if (tab == 1) {
                sel.clear();
                sel.add(slot);
            } else if (sel.isEmpty() || Gear.quality(at(slot)) != Gear.quality(at(sel.get(0)))) {
                sel.clear(); // a different quality starts a new pick
                sel.add(slot);
            } else if (sel.size() < 3) {
                sel.add(slot);
            }
            UiKit.click();
            return;
        }
        int by = innerY() + 74;
        if (UiKit.in(mx, my, x0 + 16, by, 130, 16) && canAct()) {
            if (tab == 0) {
                SkillNet.NET.sendToServer(new SkillNet.MsgEconAct(5, sel.get(0), sel.get(1) | sel.get(2) << 8));
            } else {
                SkillNet.NET.sendToServer(new SkillNet.MsgEconAct(6, sel.get(0), 0));
            }
            sel.clear();
            UiKit.click();
        }
    }

    private void slot(int x, int y, ItemStack s, int q) {
        UiKit.inset(x, y, 24, 24, 0xE60C0A08);
        frameRect(x, y, 24, 24, 0xFF000000 | QRGB[q]);
        if (!s.isEmpty()) {
            UiKit.item(itemRender, s, x + 4, y + 4);
        }
    }

    @Override
    public void drawScreen(int mx, int my, float pt) {
        a.frame();
        clean();
        float open = UiKit.easeOutCubic(a.openT(0.22F));
        int dim = Math.round(0x90 * open);
        drawGradientRect(0, 0, width, height, dim << 24, (dim + 0x10) << 24);
        UiKit.beginOpen(a, x0 + W / 2, y0 + H / 2);
        UiKit.panel(x0, y0, W, H);
        UiKit.header(fontRenderer, x0, y0, W, "RELIC FORGE");
        String cs = String.format("%,d", Coins.count(mc.player));
        int cw = fontRenderer.getStringWidth(cs);
        UiKit.item(itemRender, new ItemStack(ModItems.SOUL_COIN), x0 + W - 30 - cw, y0 + 4);
        fontRenderer.drawStringWithShadow(cs, x0 + W - 12 - cw, y0 + 9, UiKit.GOLD);
        for (int t = 0; t < TABS.length; t++) {
            int tx = tabX(t);
            boolean h = UiKit.in(mx, my, tx - 2, y0 + 28, fontRenderer.getStringWidth(TABS[t]) + 4, 14);
            fontRenderer.drawStringWithShadow(TABS[t], tx, y0 + 30, t == tab ? UiKit.GOLD_TEXT : h ? UiKit.SOFT : UiKit.MUTED);
            if (t == tab) {
                UiKit.rect(tx - 1, y0 + 40, tx + fontRenderer.getStringWidth(TABS[t]), y0 + 42, 0xFFE0B040);
            }
        }
        fontRenderer.drawString("Your relics", x0 + 12, y0 + 48, UiKit.MUTED);
        List<Integer> rs = relics();
        List<String> tip = null;
        for (int i = 0; i < rs.size(); i++) {
            int x = cellX(i), y = cellY(i);
            ItemStack s = at(rs.get(i));
            int q = Gear.quality(s);
            UiKit.rect(x, y, x + 18, y + 18, 0xFF1A150E);
            frameRect(x, y, 18, 18, 0xFF000000 | QRGB[q]);
            UiKit.item(itemRender, s, x + 1, y + 1);
            if (sel.contains(rs.get(i))) {
                UiKit.rect(x, y + 19, x + 18, y + 21, 0xFFE0B040);
            }
            if (UiKit.in(mx, my, x, y, 18, 18)) {
                tip = getItemToolTip(s);
            }
        }
        if (rs.isEmpty()) {
            fontRenderer.drawString("No relics in your inventory.", x0 + 12, y0 + 64, UiKit.FAINT);
        }
        int iy = innerY();
        UiKit.inset(x0 + 10, iy, W - 20, 94, 0xD80E0C0A);
        if (tab == 0) {
            drawCombine(iy);
        } else {
            drawReroll(iy);
        }
        boolean bh = UiKit.in(mx, my, x0 + 16, iy + 74, 130, 16);
        String label = (tab == 0 ? "Combine" : "Reroll") + (sel.isEmpty() ? "" : " (" + cost() + ")");
        UiKit.button(fontRenderer, x0 + 16, iy + 74, 130, 16, label, canAct(), a.to("act", bh && canAct() ? 1 : 0, 20), 0xE0B040);
        UiKit.endOpen();
        super.drawScreen(mx, my, pt);
        if (tip != null && a.openT(0.28F) >= 1) {
            drawHoveringText(tip, mx, my);
        }
    }

    private void drawCombine(int iy) {
        int q = sel.isEmpty() ? 0 : Gear.quality(at(sel.get(0)));
        for (int k = 0; k < 3; k++) {
            int x = x0 + 20 + k * 40;
            slot(x, iy + 10, k < sel.size() ? at(sel.get(k)) : ItemStack.EMPTY, q);
            if (k < 2) {
                fontRenderer.drawString("+", x + 30, iy + 18, UiKit.MUTED);
            }
        }
        fontRenderer.drawString("keep", x0 + 32 - fontRenderer.getStringWidth("keep") / 2, iy + 36, UiKit.FAINT);
        fontRenderer.drawStringWithShadow(">", x0 + 132, iy + 18, UiKit.GOLD_TEXT);
        int rq = Math.min(4, q + 1);
        slot(x0 + 144, iy + 10, sel.isEmpty() ? ItemStack.EMPTY : at(sel.get(0)), sel.isEmpty() ? 0 : rq);
        int tw = x0 + W - 14 - (x0 + 174);
        if (!sel.isEmpty()) {
            ItemStack k = at(sel.get(0));
            String name = Relics.DEFS[Relics.defOf(k)].name;
            fontRenderer.drawStringWithShadow(UiKit.fit(fontRenderer, "", name, tw), x0 + 174, iy + 12, QRGB[rq]);
            String step = q >= 4 ? "Already Legendary" : Gear.QUALITY[q] + " > " + Gear.QUALITY[rq];
            fontRenderer.drawString(UiKit.fit(fontRenderer, "", step, tw), x0 + 174, iy + 23, q >= 4 ? 0xE05050 : UiKit.FAINT);
        } else {
            fontRenderer.drawString(UiKit.fit(fontRenderer, "", "Pick 3 relics", tw), x0 + 174, iy + 18, UiKit.FAINT);
        }
        fontRenderer.drawString("Same quality. The first pick is kept;", x0 + 16, iy + 50, UiKit.MUTED);
        fontRenderer.drawString("jewels of the other two come back.", x0 + 16, iy + 60, UiKit.MUTED);
    }

    private void drawReroll(int iy) {
        if (sel.isEmpty()) {
            slot(x0 + 20, iy + 10, ItemStack.EMPTY, 0);
            fontRenderer.drawString("Pick a relic above", x0 + 52, iy + 18, UiKit.FAINT);
            fontRenderer.drawString("It becomes a random other relic of its kind.", x0 + 16, iy + 50, UiKit.MUTED);
            fontRenderer.drawString("Quality, jewels and enchant stay.", x0 + 16, iy + 60, UiKit.MUTED);
            return;
        }
        ItemStack s = at(sel.get(0));
        int q = Gear.quality(s), def = Relics.defOf(s);
        slot(x0 + 20, iy + 10, s, q);
        int tw = x0 + W - 14 - (x0 + 52);
        fontRenderer.drawStringWithShadow(UiKit.fit(fontRenderer, "", Relics.DEFS[def].name, tw), x0 + 52, iy + 12, QRGB[q]);
        int js = Gear.jewels(s).tagCount();
        String kind = Relics.kindName(def) + ", " + Gear.QUALITY[q] + ", " + js + " jewel" + (js == 1 ? "" : "s");
        fontRenderer.drawString(UiKit.fit(fontRenderer, "", kind, tw), x0 + 52, iy + 23, UiKit.FAINT);
        boolean none = Relics.sameKind(def).isEmpty();
        fontRenderer.drawString(UiKit.fit(fontRenderer, "", none ? "There is no other relic of this kind."
                : "Becomes a random other " + Relics.kindName(def) + ".", W - 32), x0 + 16, iy + 50, none ? 0xE05050 : UiKit.MUTED);
        fontRenderer.drawString("Quality, jewels and enchant stay.", x0 + 16, iy + 60, UiKit.MUTED);
    }

    private static void frameRect(int x, int y, int w, int h, int argb) {
        UiKit.rect(x - 1, y - 1, x + w + 1, y, argb);
        UiKit.rect(x - 1, y + h, x + w + 1, y + h + 1, argb);
        UiKit.rect(x - 1, y - 1, x, y + h + 1, argb);
        UiKit.rect(x + w, y - 1, x + w + 1, y + h + 1, argb);
    }
}
