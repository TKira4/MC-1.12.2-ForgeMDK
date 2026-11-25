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
 * Soul Exchange: three tabs (Shop, Black Market, Services) and the coin balance. The shop grid shows icon + price only;
 * names, stats and hints live in tooltips. Shop offers come from config/undyingarcana/shop.json (MsgOpen), the Black
 * Market and services from Econ.state (MsgEcon, refreshed after every action).
 */
@SideOnly(Side.CLIENT)
public class GuiShop extends GuiScreen {
    private static final String[] TABS = {"Shop", "Black Market", "Services"};
    private static final String[] FILTER = {"All", "Gems", "Scrolls", "Weapons", "Relics", "Other"};
    private static final int W = 300, H = 212, COLS = 7, CW = 39, CELL = 38, GX = 14, GY = 74, ROWS = 3, ROW_H = 42;
    private static final int[] RARITY_RGB = {0x6EA0FF, 0xC878FF, 0xFFAA3C};
    private static final String[] SERVICE = {"Repair held item", "Repair all gear", "Soul Ward", "Recall Scroll", "Cleanse curse"};
    private static final String[][] SERVICE_TIP = {{"Restores the held item's durability."}, {"Restores the held item and all worn armor."},
            {"Your next death keeps your hotbar, relics and coins.", "\u00a7cWithout it, death costs 10% of your coins."},
            {"Read it for 2 s to return to your bed (Overworld)."}, {"Removes a cursed enchant from the held item."}};
    private final UiKit.Anim a = new UiKit.Anim();
    private final List<Shop.Offer> offers = new ArrayList<>();
    private final List<ItemStack> stacks = new ArrayList<>();
    private final List<Integer> view = new ArrayList<>();
    private final ItemStack[] filterIcon = new ItemStack[FILTER.length];
    private Econ.State econ;
    private final List<ItemStack> market = new ArrayList<>();
    private long econAt;
    private int x0, y0, scroll, flash = -1, tab, filter;
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
        filterIcon[0] = new ItemStack(ModItems.SOUL_COIN);
        filterIcon[1] = new ItemStack(Jewelry.GEMS[0]);
        filterIcon[2] = new ItemStack(Jewelry.SCROLL);
        filterIcon[3] = new ItemStack(Items.IRON_SWORD);
        filterIcon[4] = new ItemStack(Relics.ITEMS[Relics.RING_FIRST]);
        filterIcon[5] = new ItemStack(Power.MANA_POTION);
        setFilter(0);
    }

    /** New Black Market / services state from the server. */
    public void econ(String json) {
        try {
            econ = new Gson().fromJson(json, Econ.State.class);
            econAt = System.currentTimeMillis();
            market.clear();
            if (econ != null && econ.market != null) {
                for (Econ.MarketOffer o : econ.market) {
                    ItemStack s;
                    try {
                        s = Shop.stack(o.item);
                    } catch (RuntimeException e) {
                        s = ItemStack.EMPTY;
                    }
                    market.add(s);
                }
            }
        } catch (RuntimeException ignored) {
            econ = null;
        }
    }

    private static int category(String spec) {
        if (spec.startsWith("scroll:") || spec.startsWith("arcane:") || spec.startsWith("manual:") || spec.contains(":scroll_")) {
            return 2;
        }
        if (spec.contains(":arm_")) {
            return 3;
        }
        if (spec.startsWith("relic:") || isRelicId(spec)) {
            return 4;
        }
        return spec.contains(":gem_") ? 1 : 5;
    }

    private static boolean isRelicId(String spec) {
        for (Relics.Def d : Relics.DEFS) {
            if (spec.equals(UndyingArcana.MODID + ":" + d.id)) {
                return true;
            }
        }
        return false;
    }

    private void setFilter(int f) {
        filter = f;
        scroll = 0;
        view.clear();
        for (int i = 0; i < offers.size(); i++) {
            if (f == 0 || category(offers.get(i).item) == f) {
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
        int x = x0 + 12;
        for (int i = 0; i < t; i++) {
            x += fontRenderer.getStringWidth(TABS[i]) + 16;
        }
        return x;
    }

    private int cellAt(int mx, int my) {
        for (int v = 0; v < view.size(); v++) {
            int row = v / COLS - scroll;
            if (row < 0 || row >= ROWS) {
                continue;
            }
            int cx = x0 + GX + (v % COLS) * CW, cy = y0 + GY + row * ROW_H;
            if (UiKit.in(mx, my, cx, cy, CW - 3, CELL)) {
                return v;
            }
        }
        return -1;
    }

    private int cardX(int i) {
        return x0 + 12 + i * 70;
    }

    private int rowY(int i) {
        return y0 + 50 + i * 28;
    }

    // ------------------------------------------------------------------ input
    @Override
    protected void mouseClicked(int mx, int my, int button) throws IOException {
        super.mouseClicked(mx, my, button);
        if (button != 0 || a.openT(0.28F) < 1) {
            return;
        }
        for (int t = 0; t < TABS.length; t++) {
            if (UiKit.in(mx, my, tabX(t) - 2, y0 + 27, fontRenderer.getStringWidth(TABS[t]) + 4, 15) && t != tab) {
                tab = t;
                UiKit.click();
                return;
            }
        }
        boolean creative = mc.player.capabilities.isCreativeMode;
        int coins = Coins.count(mc.player);
        if (tab == 0) {
            for (int f = 0; f < FILTER.length; f++) {
                if (UiKit.in(mx, my, x0 + 12 + f * 21, y0 + 48, 18, 18) && f != filter) {
                    setFilter(f);
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
            if (coins < offers.get(i).price * times && !creative) {
                flash = i;
                flashAt = System.currentTimeMillis();
                return;
            }
            SkillNet.NET.sendToServer(new SkillNet.MsgBuy(i, times));
            UiKit.click();
        } else if (tab == 1 && econ != null) {
            for (int i = 0; i < econ.market.size(); i++) {
                Econ.MarketOffer o = econ.market.get(i);
                if (UiKit.in(mx, my, cardX(i) + 6, y0 + 162, 54, 15) && !o.owned && (coins >= o.price || creative)) {
                    SkillNet.NET.sendToServer(new SkillNet.MsgEconAct(1, i, 0));
                    UiKit.click();
                }
            }
        } else if (tab == 2 && econ != null) {
            for (int i = 0; i < SERVICE.length; i++) {
                int c = econ.svc[i];
                if (UiKit.in(mx, my, x0 + W - 70, rowY(i) + 5, 53, 14) && c > 0 && (coins >= c || creative)) {
                    SkillNet.NET.sendToServer(new SkillNet.MsgEconAct(2, i, 0));
                    UiKit.click();
                }
            }
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int d = org.lwjgl.input.Mouse.getEventDWheel();
        if (d != 0 && tab == 0) {
            scroll = Math.max(0, Math.min(maxScroll(), scroll + (d > 0 ? -1 : 1)));
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    // ------------------------------------------------------------------ drawing
    private void bigItem(ItemStack s, int x, int y) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0);
        GlStateManager.scale(2F, 2F, 1F);
        UiKit.item(itemRender, s, 0, 0);
        GlStateManager.popMatrix();
    }

    private void frame(int x, int y, int w, int h, int argb) {
        UiKit.rect(x - 1, y - 1, x + w + 1, y, argb);
        UiKit.rect(x - 1, y + h, x + w + 1, y + h + 1, argb);
        UiKit.rect(x - 1, y - 1, x, y + h + 1, argb);
        UiKit.rect(x + w, y - 1, x + w + 1, y + h + 1, argb);
    }

    private void coinButton(int x, int y, int w, int h, String label, boolean enabled, boolean hover) {
        UiKit.button(fontRenderer, x, y, w, h, "  " + label, enabled, hover ? 1F : 0F, 0xE0B040);
        int tw = fontRenderer.getStringWidth("  " + label);
        GlStateManager.pushMatrix();
        GlStateManager.translate(x + (w - tw) / 2 + 1, y + (h - 8) / 2 - (hover ? 1 : 0), 0);
        GlStateManager.scale(0.5F, 0.5F, 1F);
        UiKit.item(itemRender, new ItemStack(ModItems.SOUL_COIN), 0, 0);
        GlStateManager.popMatrix();
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
        String cs = String.format("%,d", coins);
        int cw = fontRenderer.getStringWidth(cs);
        UiKit.item(itemRender, new ItemStack(ModItems.SOUL_COIN), x0 + W - 30 - cw, y0 + 4);
        fontRenderer.drawStringWithShadow(cs, x0 + W - 12 - cw, y0 + 9, UiKit.GOLD);
        GlStateManager.enableBlend();
        // tabs: text with a gold underline under the open one
        for (int t = 0; t < TABS.length; t++) {
            int tx = tabX(t);
            boolean h = UiKit.in(mx, my, tx - 2, y0 + 27, fontRenderer.getStringWidth(TABS[t]) + 4, 15);
            fontRenderer.drawStringWithShadow(TABS[t], tx, y0 + 30, t == tab ? UiKit.GOLD_TEXT : h ? 0xD8C8A0 : UiKit.MUTED);
            if (t == tab) {
                UiKit.rect(tx - 1, y0 + 40, tx + fontRenderer.getStringWidth(TABS[t]), y0 + 42, 0xFFE0B040);
            }
        }
        List<String> tip = null;
        if (tab == 0) {
            tip = drawShop(mx, my, coins);
        } else if (econ == null) {
            fontRenderer.drawString("Loading...", x0 + 14, y0 + 54, UiKit.FAINT);
        } else if (tab == 1) {
            tip = drawMarket(mx, my, coins);
        } else {
            tip = drawServices(mx, my, coins);
        }
        UiKit.endOpen();
        if (tip != null) {
            drawHoveringText(tip, mx, my);
        }
        super.drawScreen(mx, my, pt);
    }

    private List<String> drawShop(int mx, int my, int coins) {
        List<String> tip = null;
        for (int f = 0; f < FILTER.length; f++) {
            int fx = x0 + 12 + f * 21, fy = y0 + 48;
            boolean h = UiKit.in(mx, my, fx, fy, 18, 18);
            UiKit.inset(fx, fy, 18, 18, f == filter ? 0xFF1E180E : 0xE60C0A08);
            if (f == filter || h) {
                frame(fx, fy, 18, 18, f == filter ? 0xFFE0B040 : 0xFF6B5A36);
            }
            UiKit.item(itemRender, filterIcon[f], fx + 1, fy + 1);
            if (h) {
                tip = java.util.Collections.singletonList(FILTER[f]);
            }
        }
        UiKit.inset(x0 + 10, y0 + 70, W - 20, ROWS * ROW_H + 6, 0xD80E0C0A);
        int hover = cellAt(mx, my);
        boolean creative = mc.player.capabilities.isCreativeMode;
        for (int v = 0; v < view.size(); v++) {
            int row = v / COLS - scroll;
            if (row < 0 || row >= ROWS) {
                continue;
            }
            int i = view.get(v);
            int cx = x0 + GX + (v % COLS) * CW, cy = y0 + GY + row * ROW_H;
            float h = a.to("h" + i, v == hover ? 1 : 0, 20);
            int bg = UiKit.blend(0xFF1A150E, 0xFF3A2E18, h);
            if (i == flash && System.currentTimeMillis() - flashAt < 400) {
                bg = 0xFF5A1A14;
            }
            UiKit.rect(cx, cy, cx + CW - 3, cy + CELL, bg);
            UiKit.rect(cx, cy, cx + CW - 3, cy + 1, h > 0.5F ? 0xFFE0B040 : 0xFF3A3020);
            ItemStack s = stacks.get(i);
            if (!s.isEmpty()) {
                UiKit.item(itemRender, s, cx + (CW - 3) / 2 - 8, cy + 4 - Math.round(h * 2));
                if (s.getCount() > 1) {
                    String n = "x" + s.getCount();
                    fontRenderer.drawStringWithShadow(n, cx + CW - 4 - fontRenderer.getStringWidth(n), cy + 14, 0xFFFFFF);
                }
            }
            String p = String.valueOf(offers.get(i).price);
            boolean afford = coins >= offers.get(i).price || creative;
            fontRenderer.drawStringWithShadow(p, cx + (CW - 3) / 2 - fontRenderer.getStringWidth(p) / 2, cy + 26, afford ? UiKit.GOLD : 0x8A4A3A);
            if (v == hover && !s.isEmpty()) {
                tip = new ArrayList<>(getItemToolTip(s));
                int pr = offers.get(i).price;
                tip.add((afford ? "\u00a76" : "\u00a7c") + pr + " Soul Coins \u00a78- shift-click: buy 5 (" + pr * 5 + ")");
            }
        }
        int ms = maxScroll();
        if (ms > 0) {
            int th = ROWS * ROW_H, bh = Math.max(12, th * ROWS / (ROWS + ms));
            int by = y0 + GY + Math.round((float) (th - bh) * scroll / ms);
            UiKit.rect(x0 + W - 14, y0 + GY, x0 + W - 12, y0 + GY + th, 0x40000000);
            UiKit.rect(x0 + W - 14, by, x0 + W - 12, by + bh, 0xFFB08A3A);
        }
        return tip;
    }

    private List<String> drawMarket(int mx, int my, int coins) {
        List<String> tip = null;
        long left = Math.max(0, econ.restock - (System.currentTimeMillis() - econAt) / 50) / 20;
        String t = String.format("%d:%02d", left / 60, left % 60);
        fontRenderer.drawStringWithShadow(t, x0 + W - 12 - fontRenderer.getStringWidth(t), y0 + 30, 0xC878FF);
        if (UiKit.in(mx, my, x0 + W - 12 - fontRenderer.getStringWidth(t), y0 + 28, fontRenderer.getStringWidth(t), 12)) {
            tip = java.util.Arrays.asList("New stock at the next dawn.", "\u00a78One of each per player.");
        }
        boolean creative = mc.player.capabilities.isCreativeMode;
        for (int i = 0; i < econ.market.size() && i < 4; i++) {
            Econ.MarketOffer o = econ.market.get(i);
            int x = cardX(i), y = y0 + 52;
            boolean h = UiKit.in(mx, my, x, y, 66, 130);
            UiKit.inset(x, y, 66, 130, 0xEB14100B);
            UiKit.rect(x + 1, y + 1, x + 65, y + 3, 0xFF000000 | RARITY_RGB[Math.max(0, Math.min(2, o.rarity))]);
            if (h) {
                frame(x, y, 66, 130, 0xFFE0B040);
            }
            ItemStack s = i < market.size() ? market.get(i) : ItemStack.EMPTY;
            if (!s.isEmpty()) {
                bigItem(s, x + 17, y + 20);
                List<String> lines = fontRenderer.listFormattedStringToWidth(s.getDisplayName().replaceAll("\u00a7.", ""), 60);
                for (int k = 0; k < Math.min(2, lines.size()); k++) {
                    String ln = lines.get(k);
                    fontRenderer.drawStringWithShadow(ln, x + 33 - fontRenderer.getStringWidth(ln) / 2, y + 64 + k * 10, 0xFFFFFF);
                }
                if (h && !UiKit.in(mx, my, x + 6, y + 110, 54, 15)) {
                    tip = getItemToolTip(s);
                }
            }
            boolean can = !o.owned && (coins >= o.price || creative);
            boolean bh = UiKit.in(mx, my, x + 6, y + 110, 54, 15);
            if (o.owned) {
                UiKit.button(fontRenderer, x + 6, y + 110, 54, 15, "Owned", false, 0F, 0xE0B040);
            } else {
                coinButton(x + 6, y + 110, 54, 15, String.valueOf(o.price), can, bh && can);
            }
        }
        return tip;
    }

    private List<String> drawServices(int mx, int my, int coins) {
        List<String> tip = null;
        boolean creative = mc.player.capabilities.isCreativeMode;
        ItemStack[] icons = {mc.player.getHeldItemMainhand().isEmpty() ? new ItemStack(Items.IRON_SWORD) : mc.player.getHeldItemMainhand(),
                new ItemStack(Items.IRON_CHESTPLATE), new ItemStack(Relics.ITEMS[Relics.SANCTUARY]), new ItemStack(Econ.RECALL_SCROLL),
                new ItemStack(Jewelry.SCROLL)};
        for (int i = 0; i < SERVICE.length; i++) {
            int y = rowY(i), c = econ.svc[i];
            boolean usable = c > 0 || i == Econ.WARD && c == 0;
            UiKit.inset(x0 + 12, y, W - 24, 24, 0xEB14100B);
            UiKit.item(itemRender, icons[i], x0 + 17, y + 4);
            fontRenderer.drawStringWithShadow(SERVICE[i], x0 + 40, y + 8, usable ? 0xFFFFFF : UiKit.FAINT);
            boolean bh = UiKit.in(mx, my, x0 + W - 70, y + 5, 53, 14);
            if (i == Econ.WARD && c == 0) {
                UiKit.rect(x0 + W - 70, y + 5, x0 + W - 17, y + 19, 0xFF1E461E);
                String s = "Active";
                fontRenderer.drawStringWithShadow(s, x0 + W - 43 - fontRenderer.getStringWidth(s) / 2, y + 8, 0x8CFF8C);
            } else {
                boolean can = c > 0 && (coins >= c || creative);
                coinButton(x0 + W - 70, y + 5, 53, 14, c > 0 ? String.valueOf(c) : "-", can, bh && can);
            }
            if (UiKit.in(mx, my, x0 + 12, y, W - 24, 24)) {
                tip = new ArrayList<>(java.util.Arrays.asList(SERVICE_TIP[i]));
                if (c < 0 || c == 0 && i != Econ.WARD) {
                    tip.add("\u00a78" + (i == Econ.CLEANSE ? "Nothing cursed in your hand." : "Nothing to repair."));
                }
            }
        }
        return tip;
    }
}
