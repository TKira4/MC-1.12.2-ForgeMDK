package com.imba.undyingarcana;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.lwjgl.input.Keyboard;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Character screen (K), style A (dark stone, gold trim). Left: your model, level, XP and build. Middle: the seven stats
 * with + / -. Right: the build radar (7 axes, the inner ring is 40% and the outer 60% of your points). Bottom: the
 * build signature, or a preview of the build the next point makes. Clicks apply at once (local copy), the server's
 * answer replaces it. Easing: pop-in, rows slide in, bars / numbers / radar glide, hover fades, floating +1.
 */
@SideOnly(Side.CLIENT)
public class GuiStats extends GuiScreen {
    public static volatile SkillNet.MsgStats data;
    private static KeyBinding key;
    private static final int W = 372, H = 246, ROW = 24;
    private static final String[] SHORT = {"VIT", "MAG", "STR", "WPN", "AGI", "DEF", "LCK"};
    /** 16x16 stat icons, same order as Builds.STAT. */
    private static final net.minecraft.util.ResourceLocation[] ICON = new net.minecraft.util.ResourceLocation[7];

    static {
        String[] n = {"vitality", "magic", "strength", "weapons", "agility", "defense", "luck"};
        for (int i = 0; i < n.length; i++) {
            ICON[i] = new net.minecraft.util.ResourceLocation(UndyingArcana.MODID, "textures/gui/stat_" + n[i] + ".png");
        }
    }
    private int x0, y0;
    private SkillNet.MsgStats view, seen;
    private int pending;
    private final UiKit.Anim a = new UiKit.Anim();
    private final List<float[]> pops = new ArrayList<>(); // x, y, start, stat, amount

    public static void registerKey() {
        key = new KeyBinding("key.undyingarcana.stats", KeyConflictContext.IN_GAME, Keyboard.KEY_K, "key.categories.undyingarcana");
        ClientRegistry.registerKeyBinding(key);
    }

    public static class Keys {
        @SubscribeEvent
        public void onTick(TickEvent.ClientTickEvent e) {
            Minecraft mc = Minecraft.getMinecraft();
            if (e.phase != TickEvent.Phase.END || key == null || mc.player == null) {
                return;
            }
            while (key.isPressed()) {
                if (data != null && data.on && mc.currentScreen == null) {
                    mc.displayGuiScreen(new GuiStats());
                }
            }
        }
    }

    @Override
    public void initGui() {
        x0 = (width - W) / 2;
        y0 = (height - H) / 2;
    }

    private static SkillNet.MsgStats copy(SkillNet.MsgStats d) {
        return new SkillNet.MsgStats(d.on, d.level, d.xp, d.s.clone(), d.build);
    }

    private SkillNet.MsgStats view() {
        SkillNet.MsgStats d = data;
        if (d != seen) {
            seen = d;
            pending = Math.max(0, pending - 1);
            if (pending == 0 || view == null) {
                view = d == null ? null : copy(d);
            }
        }
        return view;
    }

    private static int total(int[] s) {
        int t = 0;
        for (int v : s) {
            t += v;
        }
        return t;
    }

    private static int unspent(SkillNet.MsgStats d) {
        return Builds.points(d.level) - total(d.s);
    }

    // layout
    private int midX() {
        return x0 + 112;
    }

    private int midW() {
        return 150;
    }

    private int rowY(int i) {
        return y0 + 28 + i * ROW;
    }

    private int plusX() {
        return midX() + midW() - 17;
    }

    private int minusX() {
        return plusX() - 16;
    }

    private int rightX() {
        return midX() + midW() + 6;
    }

    private int rightW() {
        return x0 + W - 10 - rightX();
    }

    // ------------------------------------------------------------------ input
    @Override
    protected void mouseClicked(int mx, int my, int button) throws IOException {
        super.mouseClicked(mx, my, button);
        SkillNet.MsgStats d = view();
        if (d == null || button != 0 || a.openT(0.28F) < 1) {
            return;
        }
        int n = isShiftKeyDown() ? 5 : 1;
        for (int i = 0; i < Builds.COUNT; i++) {
            int y = rowY(i) + 4;
            if (UiKit.in(mx, my, plusX(), y, 14, 14) && unspent(d) > 0) {
                int add = Math.min(n, unspent(d));
                d.s[i] += add;
                send(0, i, add);
                pops.add(new float[]{plusX() - 4, y, a.time(), i, add});
                return;
            }
            if (UiKit.in(mx, my, minusX(), y, 14, 14) && d.level < Builds.BUILD_LEVEL && d.s[i] > 0) {
                int take = Math.min(n, d.s[i]);
                d.s[i] -= take;
                send(1, i, take);
                pops.add(new float[]{minusX() - 4, y, a.time(), i, -take});
                return;
            }
        }
        if (d.level >= Builds.BUILD_LEVEL && UiKit.in(mx, my, rightX(), y0 + 178, rightW(), 16)) {
            SkillNet.NET.sendToServer(new SkillNet.MsgStatAct(2, 0, 0));
            UiKit.click();
        }
    }

    private void send(int op, int stat, int amount) {
        SkillNet.NET.sendToServer(new SkillNet.MsgStatAct(op, stat, amount));
        pending++;
        UiKit.click();
    }

    @Override
    protected void keyTyped(char c, int code) throws IOException {
        if (key != null && code == key.getKeyCode()) {
            mc.displayGuiScreen(null);
            return;
        }
        super.keyTyped(c, code);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    // ------------------------------------------------------------------ drawing
    /** Short "what you get now" line (fits beside the buttons); the full rule is in the row tooltip. */
    private static String effect(int stat, int p) {
        switch (stat) {
            case Builds.VIT: return String.format("+%.0f HP, -%.1f%%", p * 0.5F, Math.min(30F, p * 0.15F));
            case Builds.MAG: return "Wand Lv " + Builds.skillLevel(p) + ", +" + Math.round(p * 0.6F) + "%";
            case Builds.STR: return "+" + p * 2 + "% fist, +" + Math.round(p * 0.3F) + "%";
            case Builds.WPN: return "Weapon Lv " + Builds.skillLevel(p) + ", +" + Math.round(p * 0.6F) + "%";
            case Builds.AGI: return String.format("+%.0f%% spd, %.0f%% dodge", p * 0.4F, Builds.dodge(p) * 100);
            case Builds.DEF: return String.format("+%.1f armor", p * 0.15F);
            default: return String.format("%.0f%% crit, +%d%% XP", Builds.crit(p) * 100, p);
        }
    }

    @Override
    public void drawScreen(int mx, int my, float pt) {
        a.frame();
        float open = UiKit.easeOutCubic(a.openT(0.22F));
        int dim = Math.round(0x90 * open);
        drawGradientRect(0, 0, width, height, dim << 24, (dim + 0x10) << 24);
        SkillNet.MsgStats d = view();
        UiKit.beginOpen(a, x0 + W / 2, y0 + H / 2);
        UiKit.panel(x0, y0, W, H);
        UiKit.header(fontRenderer, x0, y0, W, "CHARACTER");
        String coins = mc.player == null ? "0" : String.valueOf(Coins.count(mc.player));
        int cw = fontRenderer.getStringWidth(coins);
        UiKit.item(itemRender, new ItemStack(ModItems.SOUL_COIN), x0 + W - 30 - cw, y0 + 4);
        fontRenderer.drawStringWithShadow(coins, x0 + W - 12 - cw, y0 + 9, UiKit.GOLD);
        String hint = "Shift-click = 5  |  K closes";
        fontRenderer.drawString(hint, x0 + W - 38 - cw - fontRenderer.getStringWidth(hint), y0 + 9, UiKit.FAINT);
        if (d == null) {
            UiKit.endOpen();
            return;
        }
        GlStateManager.enableBlend();
        int left = unspent(d), tot = total(d.s), build = Builds.build(d.level, d.s);

        // ---- left: model, level, XP, build
        int lx = x0 + 10;
        UiKit.inset(lx, y0 + 28, 96, 118, 0xD80E0C0A);
        String lv = "\u00a7lLv " + d.level;
        fontRenderer.drawStringWithShadow(lv, lx + 48 - fontRenderer.getStringWidth(lv) / 2, y0 + 32, UiKit.GOLD);
        if (mc.player != null) {
            GlStateManager.color(1, 1, 1, 1);
            GuiInventory.drawEntityOnScreen(lx + 48, y0 + 138, 42, lx + 48 - mx, y0 + 70 - my, mc.player);
            GlStateManager.enableBlend();
        }
        int need = Builds.need(d.level);
        String xps = d.level >= Builds.MAX_LEVEL ? "MAX LEVEL" : "XP " + d.xp + " / " + need;
        fontRenderer.drawStringWithShadow(xps, lx + 48 - fontRenderer.getStringWidth(xps) / 2, y0 + 150, 0xC8B890);
        float xpT = d.level >= Builds.MAX_LEVEL ? 1 : Math.min(1F, d.xp / (float) need);
        UiKit.bar(lx, y0 + 161, 96, 7, a.from("xp", 0, xpT * open, 9), 0xFF7FC83A, 0xFFB8F070);
        UiKit.inset(lx, y0 + 174, 96, 62, 0xD8180F20);
        String bn = UiKit.fit(fontRenderer, Builds.COLOR[build] + "\u00a7l", Builds.NAME[build], 92);
        fontRenderer.drawStringWithShadow(bn, lx + 48 - fontRenderer.getStringWidth(bn) / 2, y0 + 179, 0xFFFFFF);
        String sub = UiKit.fit(fontRenderer, "", buildSub(build), 92);
        fontRenderer.drawString(sub, lx + 48 - fontRenderer.getStringWidth(sub) / 2, y0 + 190, 0x9E8AB0);
        String desc = Builds.DESC[build];
        if (desc.indexOf(':') > 0 && build != Builds.ADVENTURER && build != Builds.UNBOUND) {
            String sig = UiKit.fit(fontRenderer, "\u00a7l", desc.substring(0, desc.indexOf(':')), 90);
            fontRenderer.drawStringWithShadow(sig, lx + 48 - fontRenderer.getStringWidth(sig) / 2, y0 + 206, UiKit.GOLD_TEXT);
            fontRenderer.drawString("signature", lx + 48 - fontRenderer.getStringWidth("signature") / 2, y0 + 217, UiKit.FAINT);
        }

        // ---- middle: seven stat rows
        int hover = -1, tip = -1;
        boolean free = d.level < Builds.BUILD_LEVEL;
        for (int i = 0; i < Builds.COUNT; i++) {
            int y = rowY(i);
            float slide = UiKit.easeOutCubic(UiKit.clamp01((a.time() - 0.05F - i * 0.035F) / 0.24F));
            int off = Math.round((1 - slide) * 14), al = Math.max(4, Math.round(255 * slide)) << 24;
            boolean over = UiKit.in(mx, my, midX(), y, midW(), ROW - 2);
            float h = a.to("row" + i, over ? 1 : 0, 16);
            int x = midX() + off;
            UiKit.inset(x, y, midW(), ROW - 2, UiKit.blend(0xD80E0C0A, 0xE02A2214, h));
            UiKit.rect(x + 1, y + 1, x + 3 + Math.round(h), y + ROW - 3, al | Builds.STAT_RGB[i]);
            if (slide > 0.6F) {
                mc.getTextureManager().bindTexture(ICON[i]);
                GlStateManager.enableBlend();
                GlStateManager.color(1F, 1F, 1F, 1F);
                drawModalRectWithCustomSizedTexture(x + 5, y + 3, 0, 0, 16, 16, 16, 16);
            }
            fontRenderer.drawStringWithShadow(Builds.STAT[i], x + 24, y + 3, al | Builds.STAT_RGB[i]);
            int shown = Math.round(a.to("num" + i, d.s[i], 14));
            String v = "\u00a7l" + shown;
            fontRenderer.drawStringWithShadow(v, (free ? minusX() : plusX()) - 4 - fontRenderer.getStringWidth(v) + off, y + 3, al | 0xFFFFFF);
            fontRenderer.drawString(UiKit.fit(fontRenderer, "", effect(i, d.s[i]), (free ? minusX() : plusX()) - x - 26), x + 24, y + 12, al | UiKit.MUTED);
            if (over && slide >= 1 && !UiKit.in(mx, my, minusX(), y, 34, ROW)) {
                tip = i;
            }
            if (slide < 1) {
                continue;
            }
            boolean ph = UiKit.in(mx, my, plusX(), y + 4, 14, 14);
            if (free) {
                boolean mh = UiKit.in(mx, my, minusX(), y + 4, 14, 14);
                UiKit.button(fontRenderer, minusX(), y + 4, 14, 14, "-", d.s[i] > 0, a.to("bm" + i, mh ? 1 : 0, 20), Builds.STAT_RGB[i]);
            }
            UiKit.button(fontRenderer, plusX(), y + 4, 14, 14, "+", left > 0, a.to("bp" + i, ph ? 1 : 0, 20), Builds.STAT_RGB[i]);
            if (ph && left > 0) {
                hover = i;
            }
        }

        // ---- right: build radar
        int rx = rightX(), rw = rightW();
        UiKit.inset(rx, y0 + 28, rw, 126, 0xD80E0C0A);
        float cx = rx + rw / 2F, cy = y0 + 90, rr = 30;
        int[] preview = null;
        if (hover >= 0) {
            preview = d.s.clone();
            preview[hover] += isShiftKeyDown() ? Math.min(5, left) : 1;
        }
        radar(cx, cy, rr, d.s, tot, preview, open);
        fontRenderer.drawString("Build map", Math.round(cx) - fontRenderer.getStringWidth("Build map") / 2, y0 + 143, UiKit.FAINT);
        // points pill (pulses while there are points to spend)
        String pts = left > 0 ? left + " point" + (left > 1 ? "s" : "") + " left" : "All points spent";
        float pulse = left > 0 ? 0.5F + 0.5F * (float) Math.sin(a.time() * 4) : 0;
        UiKit.rect(rx, y0 + 159, rx + rw, y0 + 173, left > 0 ? UiKit.blend(0xFF16301A, 0xFF225A2A, pulse * 0.7F) : 0xFF1E1B17);
        fontRenderer.drawString(pts, rx + rw / 2 - fontRenderer.getStringWidth(pts) / 2, y0 + 162, left > 0 ? UiKit.GREEN : UiKit.MUTED);
        if (!free) {
            boolean rh = UiKit.in(mx, my, rx, y0 + 178, rw, 16);
            boolean scroll = Stats.hasRebirthScroll(mc.player);
            int cost = Stats.respecCost(d.level);
            String lbl = scroll ? "Respec (scroll)" : "Respec " + cost;
            UiKit.button(fontRenderer, rx, y0 + 178, rw, 16, UiKit.fit(fontRenderer, "", lbl, rw - 6), scroll || Coins.count(mc.player) >= cost
                    || mc.player.capabilities.isCreativeMode, a.to("rb", rh ? 1 : 0, 20), 0xB070FF);
        } else {
            // max 2 lines between the points pill and the build card (no overlapping text)
            int fy = y0 + 178;
            for (String l : fontRenderer.listFormattedStringToWidth("Free refunds until level " + Builds.BUILD_LEVEL, rw - 4)) {
                if (fy > y0 + 187) {
                    break;
                }
                fontRenderer.drawString(l, rx + rw / 2 - fontRenderer.getStringWidth(l) / 2, fy, UiKit.FAINT);
                fy += 9;
            }
        }

        // ---- bottom: build card / preview
        int bx = midX(), bw = x0 + W - 10 - bx, by = y0 + 200;
        int shownB = build;
        String head = "Build";
        if (preview != null) {
            shownB = Builds.build(d.level, preview);
            head = shownB == build ? "Build stays" : "+1 " + Builds.STAT[hover] + " makes you";
        }
        float change = a.to("bchange", shownB != build ? 1 : 0, 14);
        UiKit.inset(bx, by, bw, 36, UiKit.blend(0xD80E0C0A, 0xE8302408, change));
        UiKit.rect(bx + 1, by + 1, bx + 1 + Math.round((bw - 2) * change), by + 2, 0xFFE0B040);
        fontRenderer.drawStringWithShadow(UiKit.fit(fontRenderer, "", "\u00a77" + head + ": " + Builds.COLOR[shownB] + "\u00a7l" + Builds.NAME[shownB], bw - 12),
                bx + 6, by + 5, 0xFFFFFF);
        int ly = by + 16;
        for (String l : fontRenderer.listFormattedStringToWidth(Builds.DESC[shownB], bw - 12)) {
            if (ly > by + 26) {
                break;
            }
            fontRenderer.drawString(l, bx + 6, ly, UiKit.MUTED);
            ly += 9;
        }

        // ---- floating +1 / -1
        for (Iterator<float[]> it = pops.iterator(); it.hasNext(); ) {
            float[] p = it.next();
            float t = (a.time() - p[2]) / 0.7F;
            if (t >= 1) {
                it.remove();
                continue;
            }
            int al = Math.max(4, Math.round(255 * (1 - t)));
            String s = (p[4] > 0 ? "+" : "") + (int) p[4];
            fontRenderer.drawStringWithShadow(s, p[0] - fontRenderer.getStringWidth(s), p[1] - 12 * UiKit.easeOutCubic(t),
                    al << 24 | Builds.STAT_RGB[(int) p[3]]);
        }
        UiKit.endOpen();
        super.drawScreen(mx, my, pt);
        if (tip >= 0) {
            java.util.List<String> lines = new java.util.ArrayList<>();
            lines.add(Builds.STAT_COLOR[tip] + Builds.STAT[tip]);
            lines.addAll(fontRenderer.listFormattedStringToWidth("\u00a77" + Builds.STAT_DESC[tip], 180));
            drawHoveringText(lines, mx, my);
        } else if (d != null && d.level >= Builds.BUILD_LEVEL && UiKit.in(mx, my, rightX(), y0 + 178, rightW(), 16)) {
            java.util.List<String> lines = new java.util.ArrayList<>();
            lines.add("\u00a7dRespec");
            lines.add("\u00a77Refunds every stat point.");
            lines.add("\u00a77Uses a Scroll of Rebirth if you carry one,");
            lines.add("\u00a77otherwise " + Stats.respecCost(d.level) + " Soul Coins (5 per level).");
            drawHoveringText(lines, mx, my);
        }
    }

    private static String buildSub(int b) {
        if (b == Builds.ADVENTURER) {
            return "Build at Lv " + Builds.BUILD_LEVEL;
        }
        if (b == Builds.SAVANT) {
            return "All seven";
        }
        if (b == Builds.UNBOUND) {
            return "No build";
        }
        int[] p = Builds.pair(b);
        if (p != null) {
            return SHORT[p[0]] + " + " + SHORT[p[1]];
        }
        for (int i = 0; i < Builds.COUNT; i++) {
            if (Builds.PURE[i] == b) {
                return "Pure " + Builds.STAT[i];
            }
        }
        return "";
    }

    /** Seven axes; the radius of a stat is its share of your points, full at 60%. Rings at 40% and 60%. */
    private void radar(float cx, float cy, float r, int[] s, int tot, int[] preview, float open) {
        int n = Builds.COUNT;
        float[] ax = new float[n], ay = new float[n];
        for (int i = 0; i < n; i++) {
            double ang = -Math.PI / 2 + i * 2 * Math.PI / n;
            ax[i] = (float) Math.cos(ang);
            ay[i] = (float) Math.sin(ang);
        }
        float[] ox = new float[n], oy = new float[n], ix = new float[n], iy = new float[n];
        for (int i = 0; i < n; i++) {
            ox[i] = cx + ax[i] * r;
            oy[i] = cy + ay[i] * r;
            ix[i] = cx + ax[i] * r * 40 / 60;
            iy[i] = cy + ay[i] * r * 40 / 60;
        }
        UiKit.poly(ox, oy, 0x60231C12);
        for (int i = 0; i < n; i++) {
            UiKit.line(cx, cy, ox[i], oy[i], 0x503C3224);
        }
        UiKit.outline(ix, iy, 0x706B5A36);
        UiKit.outline(ox, oy, 0xFF8C7B5C);
        // your shape
        float[] px = new float[n], py = new float[n];
        for (int i = 0; i < n; i++) {
            float share = tot > 0 ? s[i] / (float) tot : 0;
            float v = a.from("rad" + i, 0, Math.min(1F, share / 0.6F) * open, 10);
            px[i] = cx + ax[i] * r * Math.max(0.04F, v);
            py[i] = cy + ay[i] * r * Math.max(0.04F, v);
        }
        UiKit.poly(px, py, 0x70E0B040);
        UiKit.outline(px, py, 0xFFF4CE68);
        if (preview != null) {
            int pt = total(preview);
            float[] qx = new float[n], qy = new float[n];
            for (int i = 0; i < n; i++) {
                float v = Math.min(1F, preview[i] / (float) Math.max(1, pt) / 0.6F);
                qx[i] = cx + ax[i] * r * Math.max(0.04F, v);
                qy[i] = cy + ay[i] * r * Math.max(0.04F, v);
            }
            UiKit.outline(qx, qy, 0xC0FFFFFF);
        }
        for (int i = 0; i < n; i++) {
            UiKit.rect(Math.round(ox[i]) - 1, Math.round(oy[i]) - 1, Math.round(ox[i]) + 2, Math.round(oy[i]) + 2, 0xFF000000 | Builds.STAT_RGB[i]);
            int lxp = Math.round(cx + ax[i] * (r + 8)), lyp = Math.round(cy + ay[i] * (r + 8)) - 4;
            fontRenderer.drawStringWithShadow(SHORT[i], lxp - fontRenderer.getStringWidth(SHORT[i]) / 2, lyp, Builds.STAT_RGB[i]);
        }
    }
}
