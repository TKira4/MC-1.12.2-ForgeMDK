package com.imba.undyingarcana;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Jeweler's Bench screen (style A). Left: cut a jewel (pick an uncut gem, five reagents; Relic Shards raise the grade).
 * Right: the sockets of the item in your main hand (take a jewel out for coins) and your jewels to put in (a fee by grade).
 * The left panel has a Fuse tab: three jewels of the same gem and grade fuse into one of the next grade.
 */
@SideOnly(Side.CLIENT)
public class GuiJeweler extends GuiScreen {
    private static final int W = 384, H = 266, ROWH = 12, GCOLS = 10, GCW = 17, GCH = 19;
    private static final String[] REAGENT_TEXT = {"+Power", "+Defense", "+Agility", "+Attack Size", "+Attack Speed", "+Intensity",
            "+Armor Pierce", "+Spell Pierce", "Raises grade"};
    private final UiKit.Anim a = new UiKit.Anim();
    private int x0, y0, gem, mode; // mode 0 = Cut, 1 = Fuse
    private static final String[] MODES = {"Cut", "Fuse"};
    private final int[] r = new int[9];

    @Override
    public void initGui() {
        x0 = (width - W) / 2;
        y0 = (height - H) / 2;
    }

    private int have(ItemStack like) {
        int n = 0;
        for (ItemStack s : mc.player.inventory.mainInventory) {
            if (!s.isEmpty() && s.getItem() == like.getItem() && (!like.getHasSubtypes() || s.getMetadata() == like.getMetadata())) {
                n += s.getCount();
            }
        }
        return n;
    }

    private int used() {
        int t = 0;
        for (int v : r) {
            t += v;
        }
        return t;
    }

    private int reagentY(int i) {
        return y0 + 94 + i * ROWH;
    }

    private int gemX(int g) {
        return x0 + 12 + (g % GCOLS) * GCW;
    }

    private int gemY(int g) {
        return y0 + 42 + (g / GCOLS) * GCH;
    }

    private int rightX() {
        return x0 + 198;
    }

    private List<Integer> jewelSlots() {
        List<Integer> l = new ArrayList<>();
        for (int i = 0; i < mc.player.inventory.getSizeInventory(); i++) {
            if (mc.player.inventory.getStackInSlot(i).getItem() instanceof Jewelry.ItemJewel) {
                l.add(i);
            }
        }
        return l;
    }

    private int modeX(int m) {
        return m == 0 ? x0 + 16 : x0 + 16 + fontRenderer.getStringWidth(MODES[0]) + 16;
    }

    /** Fusable groups: {first slot, gem, grade, count} for jewels below Perfect, by gem then grade. */
    private List<int[]> fuseRows() {
        List<int[]> rows = new ArrayList<>();
        for (int slot : jewelSlots()) {
            NBTTagCompound j = Jewelry.jewelData(mc.player.inventory.getStackInSlot(slot));
            int g = j.getInteger("g"), gr = j.getInteger("gr");
            if (gr >= 2) {
                continue;
            }
            int[] row = null;
            for (int[] o : rows) {
                if (o[1] == g && o[2] == gr) {
                    row = o;
                }
            }
            if (row == null) {
                rows.add(new int[]{slot, g, gr, 1});
            } else {
                row[3]++;
            }
        }
        rows.sort((x, y) -> x[1] != y[1] ? x[1] - y[1] : x[2] - y[2]);
        return rows;
    }

    private int fuseY(int i) {
        return y0 + 50 + i * 26;
    }

    private static final int FUSE_ROWS = 5;

    private boolean canFuse(int[] row) {
        return row[3] >= 3 && (Coins.count(mc.player) >= Bench.FUSE_COST[row[2] + 1] || mc.player.capabilities.isCreativeMode);
    }

    // ------------------------------------------------------------------ input
    @Override
    protected void mouseClicked(int mx, int my, int button) throws IOException {
        super.mouseClicked(mx, my, button);
        if (button != 0 || a.openT(0.28F) < 1) {
            return;
        }
        for (int m = 0; m < 2; m++) {
            if (m != mode && UiKit.in(mx, my, modeX(m) - 2, y0 + 28, fontRenderer.getStringWidth(MODES[m]) + 4, 14)) {
                mode = m;
                UiKit.click();
                return;
            }
        }
        if (mode == 1) {
            List<int[]> rows = fuseRows();
            for (int i = 0; i < rows.size() && i < FUSE_ROWS; i++) {
                if (UiKit.in(mx, my, x0 + 132, fuseY(i) + 4, 52, 14) && canFuse(rows.get(i))) {
                    SkillNet.NET.sendToServer(new SkillNet.MsgBench(3, rows.get(i)[0], new int[9]));
                    UiKit.click();
                    return;
                }
            }
        }
        for (int g = 0; g < Gear.GEM.length && mode == 0; g++) {
            if (UiKit.in(mx, my, gemX(g), gemY(g), GCW - 1, GCH - 1)) {
                gem = g;
                UiKit.click();
                return;
            }
        }
        for (int i = 0; i < 9 && mode == 0; i++) {
            int y = reagentY(i);
            if (UiKit.in(mx, my, x0 + 150, y, 12, 11) && r[i] > 0) {
                r[i]--;
                UiKit.click();
                return;
            }
            if (UiKit.in(mx, my, x0 + 174, y, 12, 11) && used() < Bench.REAGENTS && r[i] < have(Bench.reagent(i))) {
                r[i]++;
                UiKit.click();
                return;
            }
        }
        if (mode == 0 && UiKit.in(mx, my, x0 + 12, y0 + H - 24, 176, 16) && canCut()) {
            SkillNet.NET.sendToServer(new SkillNet.MsgBench(0, gem, r.clone()));
            java.util.Arrays.fill(r, 0);
            UiKit.click();
            return;
        }
        ItemStack held = mc.player.getHeldItemMainhand();
        if (Gear.isGear(held) && Gear.rolled(held)) {
            NBTTagList js = Gear.jewels(held);
            for (int i = 0; i < js.tagCount(); i++) {
                if (UiKit.in(mx, my, x0 + W - 58, y0 + 56 + i * 16, 48, 14)) {
                    SkillNet.NET.sendToServer(new SkillNet.MsgBench(2, i, new int[9]));
                    UiKit.click();
                    return;
                }
            }
            if (js.tagCount() < Gear.sockets(held)) {
                List<Integer> slots = jewelSlots();
                for (int i = 0; i < slots.size() && i < 5; i++) {
                    if (UiKit.in(mx, my, x0 + W - 58, y0 + 132 + i * 18, 48, 14) && canInsert(slots.get(i))) {
                        SkillNet.NET.sendToServer(new SkillNet.MsgBench(1, slots.get(i), new int[9]));
                        UiKit.click();
                        return;
                    }
                }
            }
        }
    }

    private int insertFee(int slot) {
        NBTTagCompound j = Jewelry.jewelData(mc.player.inventory.getStackInSlot(slot));
        return Bench.SOCKET_COST[j == null ? 0 : Math.max(0, Math.min(2, j.getInteger("gr")))];
    }

    private boolean canInsert(int slot) {
        return Coins.count(mc.player) >= insertFee(slot) || mc.player.capabilities.isCreativeMode;
    }

    private boolean canCut() {
        return used() == Bench.REAGENTS && (gem < Gear.FIRST_LEGEND_GEM || r[8] >= 1) && (have(new ItemStack(Jewelry.GEMS[gem])) > 0 || mc.player.capabilities.isCreativeMode)
                && (Coins.count(mc.player) >= Gear.GRADE_COST[Bench.grade(r[8])] || mc.player.capabilities.isCreativeMode);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    // ------------------------------------------------------------------ drawing
    @Override
    public void drawScreen(int mx, int my, float pt) {
        a.frame();
        float open = UiKit.easeOutCubic(a.openT(0.22F));
        int dim = Math.round(0x90 * open);
        drawGradientRect(0, 0, width, height, dim << 24, (dim + 0x10) << 24);
        UiKit.beginOpen(a, x0 + W / 2, y0 + H / 2);
        UiKit.panel(x0, y0, W, H);
        UiKit.header(fontRenderer, x0, y0, W, "JEWELER'S BENCH");
        String coins = String.valueOf(Coins.count(mc.player));
        int cw = fontRenderer.getStringWidth(coins);
        UiKit.item(itemRender, new ItemStack(ModItems.SOUL_COIN), x0 + W - 30 - cw, y0 + 4);
        fontRenderer.drawStringWithShadow(coins, x0 + W - 12 - cw, y0 + 9, UiKit.GOLD);
        GlStateManager.enableBlend();

        // ---- left: cut a jewel
        UiKit.inset(x0 + 10, y0 + 26, 180, H - 34, 0xD80E0C0A);
        for (int m = 0; m < 2; m++) {
            int mxp = modeX(m);
            boolean h = UiKit.in(mx, my, mxp - 2, y0 + 28, fontRenderer.getStringWidth(MODES[m]) + 4, 14);
            fontRenderer.drawStringWithShadow(MODES[m], mxp, y0 + 30, m == mode ? UiKit.GOLD_TEXT : h ? UiKit.SOFT : UiKit.MUTED);
            if (m == mode) {
                UiKit.rect(mxp - 1, y0 + 40, mxp + fontRenderer.getStringWidth(MODES[m]), y0 + 42, 0xFFE0B040);
            }
        }
        if (mode == 1) {
            drawFuse(mx, my);
        } else {
        float selX = a.to("gemsel", gemX(gem), 18), selY = a.to("gemselY", gemY(gem), 18);
        UiKit.rect(Math.round(selX) - 1, Math.round(selY) - 1, Math.round(selX) + GCW, Math.round(selY) + GCH, 0xFFE0B040);
        for (int g = 0; g < Gear.GEM.length; g++) {
            int gx = gemX(g), gy = gemY(g);
            UiKit.rect(gx, gy, gx + GCW - 1, gy + GCH - 1, g >= Gear.FIRST_LEGEND_GEM ? 0xFF2A1E08 : 0xFF1A150E);
            UiKit.item(itemRender, new ItemStack(Jewelry.GEMS[g]), gx, gy + 1);
            int n = have(new ItemStack(Jewelry.GEMS[g]));
            if (n > 0) {
                String ns = String.valueOf(n);
                GlStateManager.disableDepth();
                fontRenderer.drawStringWithShadow(ns, gx + GCW - fontRenderer.getStringWidth(ns), gy + GCH - 8, 0xFFFFFF);
                GlStateManager.enableDepth();
            }
        }
        String gl = Gear.GEM_NAME[gem] + ": " + Gear.gemMain(gem);
        fontRenderer.drawStringWithShadow(UiKit.fit(fontRenderer, "", gl, 174), x0 + 13, y0 + 82, 0xFF000000 | Gear.GEM_RGB[gem]);
        for (int i = 0; i < 9; i++) {
            int y = reagentY(i);
            UiKit.rect(x0 + 12, y, x0 + 188, y + 11, i % 2 == 0 ? 0x40000000 : 0x20000000);
            GlStateManager.pushMatrix();
            GlStateManager.translate(x0 + 13, y - 1, 0);
            GlStateManager.scale(0.75F, 0.75F, 1);
            UiKit.item(itemRender, Bench.reagent(i), 0, 0);
            GlStateManager.popMatrix();
            fontRenderer.drawString(UiKit.fit(fontRenderer, "", REAGENT_TEXT[i], 90), x0 + 28, y + 2, i == 8 ? UiKit.GOLD : 0xD6CCB4);
            String own = "x" + have(Bench.reagent(i));
            fontRenderer.drawString(own, x0 + 146 - fontRenderer.getStringWidth(own), y + 2, UiKit.FAINT);
            UiKit.button(fontRenderer, x0 + 150, y, 12, 11, "-", r[i] > 0, a.to("rm" + i, UiKit.in(mx, my, x0 + 150, y, 12, 11) ? 1 : 0, 20), 0xE0B040);
            String v = String.valueOf(r[i]);
            fontRenderer.drawStringWithShadow(v, x0 + 168 - fontRenderer.getStringWidth(v) / 2, y + 2, r[i] > 0 ? 0xFFFFFF : 0x6A6050);
            boolean canAdd = used() < Bench.REAGENTS && r[i] < have(Bench.reagent(i));
            UiKit.button(fontRenderer, x0 + 174, y, 12, 11, "+", canAdd, a.to("rp" + i, UiKit.in(mx, my, x0 + 174, y, 12, 11) ? 1 : 0, 20), 0xE0B040);
        }
        // preview
        int gr = Bench.grade(r[8]);
        NBTTagCompound pv = new NBTTagCompound();
        pv.setInteger("g", gem);
        pv.setInteger("gr", gr);
        int[] sec = new int[Gear.STATS];
        for (int i = 0; i < 8; i++) {
            sec[i] = r[i] * (gr == 2 ? 2 : 1);
        }
        pv.setIntArray("sec", sec);
        String pl = used() + "/" + Bench.REAGENTS + " reagents  -> " + Gear.GRADE[gr] + (gr == 2 ? " (one random -3)" : "");
        if (gem >= Gear.FIRST_LEGEND_GEM && r[8] < 1) {
            pl = "Legendary: add a Relic Shard";
        }
        fontRenderer.drawString(UiKit.fit(fontRenderer, "", pl, 174), x0 + 13, y0 + 206, 0xC8B890);
        fontRenderer.drawString(UiKit.fit(fontRenderer, "", Jewelry.jewelLine(pv).replaceFirst("^[^:]*: ", ""), 174), x0 + 13, y0 + 217,
                UiKit.GREEN);
        boolean ch = UiKit.in(mx, my, x0 + 12, y0 + H - 24, 176, 16);
        UiKit.button(fontRenderer, x0 + 12, y0 + H - 24, 176, 16, "Cut jewel (" + Gear.GRADE_COST[gr] + " coins)", canCut(), a.to("cut", ch ? 1 : 0, 20), 0xE0B040);
        }

        // ---- right: sockets of the held item
        int rx = rightX(), rw = x0 + W - 10 - rx;
        UiKit.inset(rx, y0 + 26, rw, H - 34, 0xD80E0C0A);
        ItemStack held = mc.player.getHeldItemMainhand();
        if (!Gear.isGear(held) || !Gear.rolled(held)) {
            int ly = y0 + 32;
            for (String l : fontRenderer.listFormattedStringToWidth("Hold a weapon or armor piece in your main hand to see its sockets.", rw - 12)) {
                fontRenderer.drawString(l, rx + 6, ly, UiKit.MUTED);
                ly += 10;
            }
        } else {
            UiKit.item(itemRender, held, rx + 4, y0 + 30);
            int q = Gear.quality(held);
            fontRenderer.drawStringWithShadow(UiKit.fit(fontRenderer, Gear.QUALITY_COLOR[q], held.getDisplayName(), rw - 28), rx + 24, y0 + 30, 0xFFFFFF);
            fontRenderer.drawString(Gear.QUALITY[q] + ", " + Gear.sockets(held) + " socket" + (Gear.sockets(held) == 1 ? "" : "s"), rx + 24, y0 + 40,
                    UiKit.FAINT);
            NBTTagList js = Gear.jewels(held);
            for (int i = 0; i < Gear.sockets(held); i++) {
                int y = y0 + 56 + i * 16;
                if (i < js.tagCount()) {
                    NBTTagCompound j = js.getCompoundTagAt(i);
                    fontRenderer.drawString(UiKit.fit(fontRenderer, "\u00a7d\u25c6 ", Jewelry.jewelLine(j), rw - 70), rx + 6, y + 3, 0xFFFFFF);
                    boolean rh = UiKit.in(mx, my, x0 + W - 58, y, 48, 14);
                    UiKit.button(fontRenderer, x0 + W - 58, y, 48, 14, "Out " + Bench.UNSOCKET_COST, Coins.count(mc.player) >= Bench.UNSOCKET_COST,
                            a.to("out" + i, rh ? 1 : 0, 20), 0xE05050);
                } else {
                    fontRenderer.drawString("\u00a78\u25c7 Empty socket", rx + 6, y + 3, 0xFFFFFF);
                }
            }
            if (Gear.sockets(held) == 0) {
                fontRenderer.drawString("Common items have no sockets.", rx + 6, y0 + 59, UiKit.FAINT);
            }
            int[] en = Gear.enchant(held);
            String el = en == null ? "No enchant (use a scroll)" : Gear.enchantName(en[0], en[1]).substring(2);
            fontRenderer.drawString(UiKit.fit(fontRenderer, "\u00a7b\u2726 ", el, rw - 12), rx + 6, y0 + 108, 0xFFFFFF);
            // jewels in the inventory
            fontRenderer.drawStringWithShadow("\u00a7lYOUR JEWELS", rx + 6, y0 + 120, UiKit.GOLD_TEXT);
            List<Integer> slots = jewelSlots();
            boolean free = js.tagCount() < Gear.sockets(held);
            for (int i = 0; i < slots.size() && i < 5; i++) {
                int y = y0 + 132 + i * 18;
                ItemStack s = mc.player.inventory.getStackInSlot(slots.get(i));
                UiKit.item(itemRender, s, rx + 4, y - 1);
                fontRenderer.drawString(UiKit.fit(fontRenderer, "", Jewelry.jewelLine(Jewelry.jewelData(s)), rw - 82), rx + 22, y + 3, 0xD6CCB4);
                boolean ih = UiKit.in(mx, my, x0 + W - 58, y, 48, 14);
                UiKit.button(fontRenderer, x0 + W - 58, y, 48, 14, "In " + insertFee(slots.get(i)), free && canInsert(slots.get(i)),
                        a.to("in" + i, ih ? 1 : 0, 20), 0x7FC83A);
            }
            if (slots.isEmpty()) {
                fontRenderer.drawString("Cut one on the left.", rx + 6, y0 + 134, UiKit.FAINT);
            }
        }
        UiKit.endOpen();
        super.drawScreen(mx, my, pt);
        if (a.openT(0.28F) >= 1) {
            List<String> tip = tooltip(mx, my);
            if (tip != null && !tip.isEmpty()) {
                drawHoveringText(tip, mx, my);
            }
        }
    }

    private void drawFuse(int mx, int my) {
        List<int[]> rows = fuseRows();
        for (int i = 0; i < rows.size() && i < FUSE_ROWS; i++) {
            int[] row = rows.get(i);
            int y = fuseY(i);
            boolean ok = row[3] >= 3;
            UiKit.rect(x0 + 12, y, x0 + 188, y + 23, i % 2 == 0 ? 0x40000000 : 0x20000000);
            UiKit.item(itemRender, mc.player.inventory.getStackInSlot(row[0]), x0 + 14, y + 3);
            String l1 = Gear.GEM_NAME[row[1]] + " " + Gear.GRADE[row[2]] + " x" + row[3];
            fontRenderer.drawString(UiKit.fit(fontRenderer, "", l1, 96), x0 + 33, y + 3, ok ? 0xD6CCB4 : UiKit.FAINT);
            fontRenderer.drawString("3 > " + Gear.GRADE[row[2] + 1], x0 + 33, y + 13, ok ? UiKit.GREEN : UiKit.FAINT);
            boolean h = UiKit.in(mx, my, x0 + 132, y + 4, 52, 14);
            UiKit.button(fontRenderer, x0 + 132, y + 4, 52, 14, "Fuse " + Bench.FUSE_COST[row[2] + 1], canFuse(row),
                    a.to("fu" + i, h && canFuse(row) ? 1 : 0, 20), 0xE0B040);
        }
        int hy = fuseY(Math.min(Math.max(rows.size(), 1), FUSE_ROWS)) + 4;
        if (rows.isEmpty()) {
            fontRenderer.drawString("No Rough or Fine jewels yet.", x0 + 14, fuseY(0) + 4, UiKit.FAINT);
            hy = fuseY(0) + 22;
        }
        fontRenderer.drawString("Needs 3 of one gem and grade.", x0 + 14, hy, UiKit.FAINT);
        fontRenderer.drawString("Best secondary stats are kept.", x0 + 14, hy + 10, UiKit.FAINT);
        if (rows.size() > FUSE_ROWS) {
            fontRenderer.drawString("+" + (rows.size() - FUSE_ROWS) + " more groups", x0 + 14, hy + 20, UiKit.FAINT);
        }
    }

    /** Tooltip for whatever is under the mouse, or null. */
    private List<String> tooltip(int mx, int my) {
        List<String> tip = new ArrayList<>();
        if (mode == 1) {
            List<int[]> rows = fuseRows();
            for (int i = 0; i < rows.size() && i < FUSE_ROWS; i++) {
                if (UiKit.in(mx, my, x0 + 12, fuseY(i), 118, 23)) {
                    java.util.List<Integer> set = Bench.fuseSet(mc.player, rows.get(i)[0]);
                    if (set == null) {
                        tip.add("\u00a77Collect " + (3 - rows.get(i)[3]) + " more to fuse.");
                        return tip;
                    }
                    tip.add("\u00a77Fuses into (Perfect's random -3 not shown):");
                    tip.addAll(getItemToolTip(Jewelry.jewel(Bench.fused(mc.player, set, null))));
                    return tip;
                }
            }
        }
        for (int g = 0; g < Gear.GEM.length && mode == 0; g++) {
            if (UiKit.in(mx, my, gemX(g), gemY(g), GCW - 1, GCH - 1)) {
                tip.addAll(getItemToolTip(new ItemStack(Jewelry.GEMS[g])));
                tip.add("\u00a77You have " + have(new ItemStack(Jewelry.GEMS[g])));
                return tip;
            }
        }
        for (int i = 0; i < 9 && mode == 0; i++) {
            if (UiKit.in(mx, my, x0 + 12, reagentY(i), 136, 11)) {
                ItemStack rs = Bench.reagent(i);
                tip.add(rs.getDisplayName());
                if (i < 8) {
                    tip.add("\u00a7a+1 " + Gear.STAT[i] + " \u00a77per reagent (+2 in a Perfect jewel)");
                    tip.add("\u00a78" + Gear.STAT[i] + ": " + Gear.STAT_HELP[i] + " per point");
                } else {
                    tip.add("\u00a76Raises the jewel grade:");
                    tip.add("\u00a770 = Rough, 1-2 = Fine, 3+ = Perfect");
                    tip.add("\u00a78Perfect doubles reagent stats but one random stat gets -3.");
                    tip.add("\u00a78Legendary gems need at least one.");
                }
                tip.add("\u00a77You have " + have(rs));
                return tip;
            }
        }
        if (mode == 0 && UiKit.in(mx, my, x0 + 12, y0 + 204, 176, 22)) {
            int gr = Bench.grade(r[8]);
            NBTTagCompound pv = new NBTTagCompound();
            pv.setInteger("g", gem);
            pv.setInteger("gr", gr);
            int[] sec = new int[Gear.STATS];
            for (int i = 0; i < 8; i++) {
                sec[i] = r[i] * (gr == 2 ? 2 : 1);
            }
            pv.setIntArray("sec", sec);
            tip.add("\u00a77Preview (the random -3 of a Perfect jewel is not shown):");
            tip.addAll(getItemToolTip(Jewelry.jewel(pv)));
            return tip;
        }
        ItemStack held = mc.player.getHeldItemMainhand();
        if (!Gear.isGear(held) || !Gear.rolled(held)) {
            return null;
        }
        int rx = rightX(), rw = x0 + W - 10 - rx;
        if (UiKit.in(mx, my, rx, y0 + 28, rw, 22)) {
            return getItemToolTip(held);
        }
        NBTTagList js = Gear.jewels(held);
        for (int i = 0; i < js.tagCount(); i++) {
            if (UiKit.in(mx, my, rx + 4, y0 + 56 + i * 16, x0 + W - 62 - rx - 4, 14)) {
                tip.addAll(getItemToolTip(Jewelry.jewel(js.getCompoundTagAt(i))));
                tip.add("\u00a78Out: returns the jewel for " + Bench.UNSOCKET_COST + " Soul Coins.");
                tip.add("\u00a78Putting a jewel in costs 5 / 15 / 40 (Rough / Fine / Perfect).");
                return tip;
            }
        }
        int[] en = Gear.enchant(held);
        if (en != null && UiKit.in(mx, my, rx + 4, y0 + 106, rw - 8, 11)) {
            tip.add(Gear.enchantName(en[0], en[1]));
            tip.add((Gear.cursed(en[0]) ? "\u00a7c" : "\u00a77") + Gear.enchantDesc(en[0], en[1]));
            tip.add("\u00a78A Scroll of Cleansing removes it; a new scroll replaces it.");
            return tip;
        }
        List<Integer> slots = jewelSlots();
        for (int i = 0; i < slots.size() && i < 5; i++) {
            if (UiKit.in(mx, my, rx + 4, y0 + 131 + i * 18, x0 + W - 62 - rx - 4, 16)) {
                return getItemToolTip(mc.player.inventory.getStackInSlot(slots.get(i)));
            }
        }
        return null;
    }
}
