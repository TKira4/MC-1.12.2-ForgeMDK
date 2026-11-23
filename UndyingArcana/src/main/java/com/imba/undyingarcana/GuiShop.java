package com.imba.undyingarcana;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Soul Exchange shop (style A) with four tabs: Gems, Scrolls, Weapons, Other. Click an offer to buy one, shift-click to buy five.
 * The offer list comes from the server (config/undyingarcana/shop.json).
 */
@SideOnly(Side.CLIENT)
public class GuiShop extends GuiScreen {
    private static final String[] SHORT = {"Sund", "Hex", "Exec", "Echo", "Storm", "Bulw", "Swift", "Agile", "Vamp",
            "Blaze", "Frost", "Venom", "Cleave", "Mmntm", "Fortn", "Pierce", "Lucky",
            "Thorn", "Regen", "Ward", "Feath", "Undy",
            "Blood", "Glass", "Frenzy", "Lead", "Mark", "Wraith"};
    private static final String[] TABS = {"Gems", "Scrolls", "Weapons", "Other"};
    private static final int W = 312, H = 238, COLS = 6, CW = 46, CELL = 38, GX = 13, GY = 46, ROWS = 4;
    private final UiKit.Anim a = new UiKit.Anim();
    private final List<Shop.Offer> offers = new ArrayList<>();
    private final List<ItemStack> stacks = new ArrayList<>();
    private final List<Integer> view = new ArrayList<>();
    private int x0, y0, scroll, flash = -1, tab;
    private long flashAt;

    public GuiShop(String json) {
        try {
            Shop.Offers o = new Gson().fromJson(json, Shop.Offers.class);
            if (o != null && o.offers != null) {
                for (Shop.Offer f : o.offers) {
                    ItemStack s;
                    try {
                        s = Shop.stack(f.item);
                    } catch (RuntimeException e) {
                        s = ItemStack.EMPTY;
                    }
                    offers.add(f);
                    stacks.add(s);
                }
            }
        } catch (RuntimeException ignored) {
            // bad JSON from the server: show an empty shop
        }
        setTab(0);
    }

    private static int tabOf(String spec) {
        if (spec.startsWith("scroll:") || spec.startsWith("arcane:") || spec.contains(":scroll_cleansing") || spec.contains(":scroll_reforge")) {
            return 1;
        }
        if (spec.contains(":arm_")) {
            return 2;
        }
        return spec.contains(":gem_") ? 0 : 3;
    }

    private void setTab(int t) {
        tab = t;
        scroll = 0;
        view.clear();
        for (int i = 0; i < offers.size(); i++) {
            if (tabOf(offers.get(i).item) == t) {
                view.add(i);
            }
        }
    }

    @Override
    public void initGui() {
        x0 = (width - W) / 2;
        y0 = (height - H) / 2;
    }

    private int maxScroll() {
        return Math.max(0, (view.size() + COLS - 1) / COLS - ROWS);
    }

    private int tabX(int t) {
        return x0 + 12 + t * 64;
    }

    /** Index into view under the mouse, or -1. */
    private int cellAt(int mx, int my) {
        for (int v = 0; v < view.size(); v++) {
            int row = v / COLS - scroll;
            if (row < 0 || row >= ROWS) {
                continue;
            }
            int cx = x0 + GX + (v % COLS) * CW, cy = y0 + GY + row * (CELL + 2);
            if (UiKit.in(mx, my, cx, cy, CW - 2, CELL)) {
                return v;
            }
        }
        return -1;
    }

    @Override
    protected void mouseClicked(int mx, int my, int button) throws IOException {
        super.mouseClicked(mx, my, button);
        if (button != 0 || a.openT(0.28F) < 1) {
            return;
        }
        for (int t = 0; t < TABS.length; t++) {
            if (UiKit.in(mx, my, tabX(t), y0 + 26, 60, 15) && t != tab) {
                setTab(t);
                UiKit.click();
                return;
            }
        }
        int v = cellAt(mx, my);
        if (v < 0) {
            return;
        }
        int i = view.get(v);
        int times = isShiftKeyDown() ? 5 : 1;
        if (Coins.count(mc.player) < offers.get(i).price * times && !mc.player.capabilities.isCreativeMode) {
            flash = i;
            flashAt = System.currentTimeMillis();
            return;
        }
        SkillNet.NET.sendToServer(new SkillNet.MsgBuy(i, times));
        UiKit.click();
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int d = org.lwjgl.input.Mouse.getEventDWheel();
        if (d != 0) {
            scroll = Math.max(0, Math.min(maxScroll(), scroll + (d > 0 ? -1 : 1)));
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void drawScreen(int mx, int my, float pt) {
        a.frame();
        float open = UiKit.easeOutCubic(a.openT(0.22F));
        int dim = Math.round(0x90 * open);
        drawGradientRect(0, 0, width, height, dim << 24, (dim + 0x10) << 24);
        UiKit.beginOpen(a, x0 + W / 2, y0 + H / 2);
        UiKit.panel(x0, y0, W, H);
        UiKit.header(fontRenderer, x0, y0, W, "SOUL EXCHANGE");
        int coins = Coins.count(mc.player);
        String cs = String.valueOf(coins);
        int cw = fontRenderer.getStringWidth(cs);
        UiKit.item(itemRender, new ItemStack(ModItems.SOUL_COIN), x0 + W - 30 - cw, y0 + 4);
        fontRenderer.drawStringWithShadow(cs, x0 + W - 12 - cw, y0 + 9, UiKit.GOLD);
        GlStateManager.enableBlend();
        // tabs with a sliding gold marker
        float tx = a.to("tab", tabX(tab), 18);
        for (int t = 0; t < TABS.length; t++) {
            boolean h = UiKit.in(mx, my, tabX(t), y0 + 26, 60, 15);
            UiKit.rect(tabX(t), y0 + 26, tabX(t) + 60, y0 + 41, t == tab ? 0xFF3A2E18 : h ? 0xFF2A2214 : 0xFF1A150E);
            fontRenderer.drawStringWithShadow(TABS[t], tabX(t) + 30 - fontRenderer.getStringWidth(TABS[t]) / 2, y0 + 30,
                    t == tab ? UiKit.GOLD_TEXT : UiKit.MUTED);
        }
        UiKit.rect(Math.round(tx), y0 + 40, Math.round(tx) + 60, y0 + 42, 0xFFE0B040);
        UiKit.inset(x0 + 10, y0 + 43, W - 20, ROWS * (CELL + 2) + 5, 0xD80E0C0A);
        float sc = a.to("scroll" + tab, scroll, 16);
        int hover = cellAt(mx, my);
        for (int v = 0; v < view.size(); v++) {
            float row = v / COLS - sc;
            if (row < -0.5F || row > ROWS - 0.5F) {
                continue;
            }
            int i = view.get(v);
            int cx = x0 + GX + (v % COLS) * CW, cy = Math.round(y0 + GY + row * (CELL + 2));
            float h = a.to("h" + i, v == hover ? 1 : 0, 20);
            boolean afford = coins >= offers.get(i).price || mc.player.capabilities.isCreativeMode;
            int bg = UiKit.blend(0xFF1A150E, 0xFF3A2E18, h);
            if (i == flash && System.currentTimeMillis() - flashAt < 400) {
                bg = 0xFF5A1A14;
            }
            UiKit.rect(cx, cy, cx + CW - 2, cy + CELL, bg);
            UiKit.rect(cx, cy, cx + CW - 2, cy + 1, h > 0.5F ? 0xFFE0B040 : 0xFF3A3020);
            ItemStack s = stacks.get(i);
            if (!s.isEmpty()) {
                UiKit.item(itemRender, s, cx + (CW - 2) / 2 - 8, cy + 3 - Math.round(h * 2));
                if (s.getCount() > 1) {
                    String n = "x" + s.getCount();
                    fontRenderer.drawStringWithShadow(n, cx + CW - 3 - fontRenderer.getStringWidth(n), cy + 13, 0xFFFFFF);
                }
            } else {
                fontRenderer.drawString("?", cx + CW / 2 - 3, cy + 8, 0xC04040);
            }
            String spec = offers.get(i).item;
            if (spec.startsWith("scroll:")) {
                try {
                    String[] sp = spec.split(":");
                    int k = Math.max(0, Math.min(SHORT.length - 1, Integer.parseInt(sp[1]))), tier = Math.max(1, Math.min(3, Integer.parseInt(sp[2])));
                    boolean cursed = Gear.cursed(k);
                    String lab = SHORT[k];
                    fontRenderer.drawString(lab, cx + (CW - 2) / 2 - fontRenderer.getStringWidth(lab) / 2, cy + 20, cursed ? 0xFF5555 : 0x55FFFF);
                    if (!cursed) {
                        String rn = new String[]{"I", "II", "III"}[tier - 1];
                        fontRenderer.drawStringWithShadow(rn, cx + CW - 4 - fontRenderer.getStringWidth(rn), cy + 3, 0x55FFFF);
                    }
                } catch (RuntimeException ignored) {
                    // malformed scroll spec in shop.json
                }
            }
            String p = String.valueOf(offers.get(i).price);
            fontRenderer.drawString(p, cx + (CW - 2) / 2 - fontRenderer.getStringWidth(p) / 2, cy + 29, afford ? UiKit.GOLD : 0x8A4A3A);
        }
        if (view.isEmpty()) {
            fontRenderer.drawString("Nothing here.", x0 + 18, y0 + 52, UiKit.FAINT);
        }
        int ms = maxScroll();
        if (ms > 0) {
            int th = ROWS * (CELL + 2), bh = Math.max(12, th * ROWS / (ROWS + ms));
            int by = y0 + GY + Math.round((th - bh) * sc / ms);
            UiKit.rect(x0 + W - 15, y0 + GY, x0 + W - 13, y0 + GY + th, 0x40000000);
            UiKit.rect(x0 + W - 15, by, x0 + W - 13, by + bh, 0xFFB08A3A);
        }
        String hint = "Click: buy 1    Shift-click: buy 5    Wheel: scroll";
        fontRenderer.drawString(UiKit.fit(fontRenderer, "", hint, W - 24), x0 + 12, y0 + H - 14, UiKit.FAINT);
        UiKit.endOpen();
        if (hover >= 0 && !stacks.get(view.get(hover)).isEmpty()) {
            int i = view.get(hover);
            List<String> tip = new ArrayList<>(getItemToolTip(stacks.get(i)));
            int pr = offers.get(i).price;
            tip.add("\u00a76" + pr + " Soul Coins \u00a78(x5: " + pr * 5 + ")");
            drawHoveringText(tip, mx, my);
        }
        super.drawScreen(mx, my, pt);
    }
}
