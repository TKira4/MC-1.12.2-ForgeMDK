package com.imba.undyingarcana;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.gui.toasts.GuiToast;
import net.minecraft.client.gui.toasts.IToast;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * The quest Journal (U; J is the JourneyMap map): tabs on the left, the quest list in the middle (locked / in progress / ready / done),
 * details on the right (story line, goals with bars, rewards, Track and Claim). Also the tracker HUD (up to 3 quests)
 * and a toast when a quest is ready to claim.
 */
@SideOnly(Side.CLIENT)
public class GuiJournal extends GuiScreen {
    // ------------------------------------------------------------------ client state
    static boolean on;
    static Map<String, QuestDefs.Quest> defs = new LinkedHashMap<>();
    static Map<String, int[]> prog = new HashMap<>();
    static Set<String> claimed = new HashSet<>();
    static List<String> tracked = new ArrayList<>();
    static int pouch;
    private static String[] parts = new String[0];
    private static KeyBinding key;
    private static int lastTab;
    private static String lastSel = "";

    public static void registerKey() {
        key = new KeyBinding("key.undyingarcana.journal", KeyConflictContext.IN_GAME, Keyboard.KEY_U, "key.categories.undyingarcana");
        ClientRegistry.registerKeyBinding(key);
    }

    static void receiveDefs(SkillNet.MsgQuestDefs m) {
        on = m.on;
        if (!m.on) {
            defs = new LinkedHashMap<>();
            return;
        }
        if (m.index == 0 || parts.length != m.total) {
            parts = new String[m.total];
        }
        parts[m.index] = m.part;
        for (String s : parts) {
            if (s == null) {
                return;
            }
        }
        QuestDefs.Pack pack = QuestDefs.GSON.fromJson(String.join("", parts), QuestDefs.Pack.class);
        Map<String, QuestDefs.Quest> map = new LinkedHashMap<>();
        for (QuestDefs.Quest q : pack.quests) {
            map.put(q.id, q);
        }
        defs = map;
    }

    static void receiveProgress(String json) {
        JsonObject root = new JsonParser().parse(json).getAsJsonObject();
        Map<String, int[]> np = new HashMap<>();
        Set<String> nc = new HashSet<>();
        for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("q").entrySet()) {
            JsonObject o = e.getValue().getAsJsonObject();
            JsonArray a = o.getAsJsonArray("p");
            int[] v = new int[a.size()];
            for (int i = 0; i < v.length; i++) {
                v[i] = a.get(i).getAsInt();
            }
            np.put(e.getKey(), v);
            if (o.get("c").getAsBoolean()) {
                nc.add(e.getKey());
            }
        }
        // toast for quests that just became ready
        for (QuestDefs.Quest q : defs.values()) {
            if (!prog.isEmpty() && !nc.contains(q.id) && done(q, np.get(q.id)) && !done(q, prog.get(q.id))) {
                Minecraft.getMinecraft().getToastGui().add(new Toast(q.title));
            }
        }
        prog = np;
        claimed = nc;
        List<String> t = new ArrayList<>();
        for (JsonElement e : root.getAsJsonArray("t")) {
            t.add(e.getAsString());
        }
        tracked = t;
        pouch = root.get("pouch").getAsInt();
    }

    static boolean done(QuestDefs.Quest q, int[] p) {
        if (p == null) {
            return false;
        }
        for (int i = 0; i < q.goals.size(); i++) {
            if (i >= p.length || p[i] < q.goals.get(i).count) {
                return false;
            }
        }
        return true;
    }

    static boolean complete(QuestDefs.Quest q) {
        return done(q, prog.get(q.id));
    }

    static boolean unlocked(QuestDefs.Quest q) {
        for (String r : q.requires) {
            QuestDefs.Quest req = defs.get(r);
            if (req != null && !claimed.contains(r) && !complete(req)) {
                return false;
            }
        }
        return true;
    }

    /** 0 locked, 1 in progress, 2 ready to claim, 3 claimed. */
    static int state(QuestDefs.Quest q) {
        if (claimed.contains(q.id)) {
            return 3;
        }
        if (!unlocked(q)) {
            return 0;
        }
        return complete(q) ? 2 : 1;
    }

    // ------------------------------------------------------------------ key, HUD
    public static class Client {
        @SubscribeEvent
        public void onTick(TickEvent.ClientTickEvent e) {
            Minecraft mc = Minecraft.getMinecraft();
            if (e.phase != TickEvent.Phase.END || key == null || mc.player == null) {
                return;
            }
            while (key.isPressed()) {
                if (on && mc.currentScreen == null) {
                    mc.displayGuiScreen(new GuiJournal());
                }
            }
        }

        @SubscribeEvent
        public void onOverlay(RenderGameOverlayEvent.Post e) {
            Minecraft mc = Minecraft.getMinecraft();
            if (e.getType() != RenderGameOverlayEvent.ElementType.ALL || !on || tracked.isEmpty() || mc.gameSettings.hideGUI
                    || mc.gameSettings.showDebugInfo || mc.currentScreen != null) {
                return;
            }
            ScaledResolution sr = e.getResolution();
            int w = 150, x = sr.getScaledWidth() - w - 4, y = sr.getScaledHeight() / 2 - 40;
            for (String id : tracked) {
                QuestDefs.Quest q = defs.get(id);
                if (q == null || claimed.contains(id)) {
                    continue;
                }
                int lines = complete(q) ? 1 : q.goals.size();
                drawRect(x - 3, y - 2, x + w, y + 10 + lines * 10, 0x80101520);
                drawRect(x - 3, y - 2, x - 1, y + 10 + lines * 10, complete(q) ? 0xFF4CC864 : 0xFFE0B040);
                mc.fontRenderer.drawStringWithShadow(UiKit.fit(mc.fontRenderer, "", q.title, w - 4), x, y, 0xFFD050);
                y += 10;
                if (complete(q)) {
                    mc.fontRenderer.drawStringWithShadow("Ready to claim (" + key.getDisplayName() + ")", x + 4, y, 0x55FF55);
                    y += 10;
                } else {
                    int[] p = prog.get(id);
                    for (int i = 0; i < q.goals.size(); i++) {
                        QuestDefs.Goal g = q.goals.get(i);
                        int v = p != null && i < p.length ? p[i] : 0;
                        String s = UiKit.fit(mc.fontRenderer, "", g.describe(), w - 40) + " " + v + "/" + g.count;
                        mc.fontRenderer.drawStringWithShadow(s, x + 4, y, v >= g.count ? 0x55FF55 : 0xD0D0D0);
                        y += 10;
                    }
                }
                y += 6;
            }
        }
    }

    static class Toast implements IToast {
        private final String title;
        private long first = -1;

        Toast(String title) {
            this.title = title;
        }

        @Override
        public Visibility draw(GuiToast gui, long delta) {
            if (first < 0) {
                first = delta;
            }
            gui.getMinecraft().getTextureManager().bindTexture(TEXTURE_TOASTS);
            GlStateManager.color(1.0F, 1.0F, 1.0F);
            gui.drawTexturedModalRect(0, 0, 0, 32, 160, 32);
            gui.getMinecraft().fontRenderer.drawString("Quest ready to claim", 30, 7, 0xFFD050);
            gui.getMinecraft().fontRenderer.drawString(UiKit.fit(gui.getMinecraft().fontRenderer, "", title, 125), 30, 18, 0xFFFFFF);
            RenderHelper.enableGUIStandardItemLighting();
            gui.getMinecraft().getRenderItem().renderItemAndEffectIntoGUI(null, new ItemStack(net.minecraft.init.Items.WRITABLE_BOOK), 8, 8);
            return delta - first < 5000L ? Visibility.SHOW : Visibility.HIDE;
        }
    }

    // ------------------------------------------------------------------ screen (style A)
    private static final int W = 400, H = 236, TAB_W = 76, LIST_W = 124, ROW = 16;
    private static final ItemStack[] TAB_ICON = {new ItemStack(net.minecraft.init.Items.BOOK), new ItemStack(net.minecraft.init.Items.ENCHANTED_BOOK),
            new ItemStack(net.minecraft.init.Items.IRON_SWORD), new ItemStack(net.minecraft.init.Items.ROTTEN_FLESH),
            new ItemStack(net.minecraft.init.Items.FILLED_MAP), new ItemStack(net.minecraft.init.Items.CLOCK)};
    private static final String[] TAB_SHORT = {"Story", "Trials", "Mastery", "Hunts", "Explore", "Daily"};
    private final UiKit.Anim a = new UiKit.Anim();
    private int x0, y0, tab = lastTab;
    private String sel = lastSel;
    private float scroll;
    private int scrollTarget;
    private long selChanged = System.nanoTime();
    private List<QuestDefs.Quest> rows = new ArrayList<>();
    private ItemStack hoverItem;
    private int seenHash;
    private static final Map<String, ItemStack> STACKS = new HashMap<>();

    static ItemStack stackOf(String spec) {
        return STACKS.computeIfAbsent(spec, s -> {
            try {
                ItemStack st = Quests.parseStack(s);
                return st.isEmpty() ? new ItemStack(net.minecraft.init.Items.BOOK) : st;
            } catch (Exception e) {
                return new ItemStack(net.minecraft.init.Items.BOOK);
            }
        });
    }

    @Override
    public void initGui() {
        x0 = (width - W) / 2;
        y0 = (height - H) / 2;
        rebuild();
    }

    private int tabY(int i) {
        return y0 + 28 + i * 33;
    }

    private int listX() {
        return x0 + 10 + TAB_W + 6;
    }

    private int detX() {
        return listX() + LIST_W + 6;
    }

    private int detW() {
        return x0 + W - 10 - detX();
    }

    private int visibleRows() {
        return (H - 40) / ROW;
    }

    private int btnY() {
        return y0 + H - 26;
    }

    private int claimX() {
        return detX() + detW() - 66;
    }

    private void rebuild() {
        rows = new ArrayList<>();
        for (QuestDefs.Quest q : defs.values()) {
            if (QuestDefs.tabIndex(q.tab) == tab) {
                rows.add(q);
            }
        }
        rows.sort((p, q) -> Integer.compare(order(state(p)), order(state(q))));
        if (!defs.containsKey(sel) || QuestDefs.tabIndex(defs.get(sel).tab) != tab) {
            select(rows.isEmpty() ? "" : rows.get(0).id);
        }
    }

    private void select(String id) {
        if (!id.equals(sel)) {
            sel = id;
            selChanged = System.nanoTime();
        }
    }

    private static int order(int st) {
        return st == 2 ? 0 : st == 1 ? 1 : st == 0 ? 2 : 3;
    }

    private void rebuildIfChanged() {
        int h = defs.size() * 31 + claimed.hashCode() * 7 + prog.size();
        for (QuestDefs.Quest q : defs.values()) {
            h = h * 31 + state(q);
        }
        if (h != seenHash) {
            seenHash = h;
            rebuild();
        }
    }

    // ------------------------------------------------------------------ input
    @Override
    protected void mouseClicked(int mx, int my, int button) throws IOException {
        super.mouseClicked(mx, my, button);
        if (button != 0 || a.openT(0.28F) < 1) {
            return;
        }
        for (int i = 0; i < QuestDefs.TABS.length; i++) {
            if (UiKit.in(mx, my, x0 + 10, tabY(i), TAB_W, 30)) {
                if (tab != i) {
                    tab = i;
                    scrollTarget = 0;
                    sel = "";
                    rebuild();
                    UiKit.click();
                }
                return;
            }
        }
        int ly = y0 + 31;
        if (UiKit.in(mx, my, listX(), ly, LIST_W, visibleRows() * ROW)) {
            int idx = (my - ly) / ROW + scrollTarget;
            if (idx >= 0 && idx < rows.size()) {
                select(rows.get(idx).id);
                UiKit.click();
            }
            return;
        }
        QuestDefs.Quest q = defs.get(sel);
        if (q != null) {
            int st = state(q);
            if ((st == 1 || st == 2) && UiKit.in(mx, my, detX() + 6, btnY(), 60, 16)) {
                SkillNet.NET.sendToServer(new SkillNet.MsgQuestAct(tracked.contains(q.id) ? 2 : 1, q.id));
                UiKit.click();
                return;
            }
            if (st == 2 && UiKit.in(mx, my, claimX(), btnY(), 60, 16)) {
                SkillNet.NET.sendToServer(new SkillNet.MsgQuestAct(0, q.id));
                UiKit.click();
                return;
            }
        }
        int pw = fontRenderer.getStringWidth("Reward pouch (" + pouch + ")") + 12;
        if (pouch > 0 && UiKit.in(mx, my, x0 + W - 10 - pw, y0 + 6, pw, 14)) {
            SkillNet.NET.sendToServer(new SkillNet.MsgQuestAct(3, ""));
            UiKit.click();
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int d = org.lwjgl.input.Mouse.getEventDWheel();
        if (d != 0) {
            scrollTarget = Math.max(0, Math.min(Math.max(0, rows.size() - visibleRows()), scrollTarget + (d > 0 ? -1 : 1)));
        }
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
    public void onGuiClosed() {
        lastTab = tab;
        lastSel = sel;
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    // ------------------------------------------------------------------ drawing
    private static final String[] GLYPH = {"-", ">", "*", "+"};
    private static final int[] GLYPH_C = {0x5A5040, 0xFFD050, 0x8CFF8C, 0x5C9A5C};
    private static final String[] STATE = {"Locked", "In progress", "Ready to claim", "Claimed"};

    @Override
    public void drawScreen(int mx, int my, float pt) {
        a.frame();
        rebuildIfChanged();
        float open = UiKit.easeOutCubic(a.openT(0.22F));
        int dim = Math.round(0x90 * open);
        drawGradientRect(0, 0, width, height, dim << 24, (dim + 0x10) << 24);
        UiKit.beginOpen(a, x0 + W / 2, y0 + H / 2);
        UiKit.panel(x0, y0, W, H);
        UiKit.header(fontRenderer, x0, y0, W, "JOURNAL");
        GlStateManager.enableBlend();
        if (pouch > 0) {
            String pl = "Reward pouch (" + pouch + ")";
            int pw = fontRenderer.getStringWidth(pl) + 12;
            boolean ph = UiKit.in(mx, my, x0 + W - 10 - pw, y0 + 6, pw, 14);
            UiKit.button(fontRenderer, x0 + W - 10 - pw, y0 + 6, pw, 14, pl, true, a.to("pouch", ph ? 1 : 0, 20), 0xE0B040);
        }

        // tabs with item icons; a gold marker slides to the selected one
        float markY = a.to("tabmark", tabY(tab), 16);
        for (int i = 0; i < QuestDefs.TABS.length; i++) {
            int ty = tabY(i), n = 0, done = 0, ready = 0;
            for (QuestDefs.Quest q : defs.values()) {
                if (QuestDefs.tabIndex(q.tab) == i) {
                    n++;
                    int st = state(q);
                    done += st == 3 ? 1 : 0;
                    ready += st == 2 ? 1 : 0;
                }
            }
            float h = a.to("tab" + i, i == tab ? 1 : UiKit.in(mx, my, x0 + 10, ty, TAB_W, 30) ? 0.45F : 0, 16);
            UiKit.inset(x0 + 10, ty, TAB_W, 30, UiKit.blend(0xD80E0C0A, 0xE82A2214, h));
            UiKit.item(itemRender, TAB_ICON[i], x0 + 15, ty + 7);
            fontRenderer.drawStringWithShadow(TAB_SHORT[i], x0 + 34, ty + 6, UiKit.blend(0xFFA89878, 0xFFFFFFFF, h));
            fontRenderer.drawString(done + "/" + n, x0 + 34, ty + 17, UiKit.FAINT);
            if (ready > 0) {
                String r = ready + "!";
                int rw = fontRenderer.getStringWidth(r) + 4;
                UiKit.rect(x0 + 8 + TAB_W - rw, ty + 16, x0 + 6 + TAB_W, ty + 26, 0xFF2E7A30);
                fontRenderer.drawString(r, x0 + 8 + TAB_W - rw + 2, ty + 17, 0xC8FFC0);
            }
        }
        UiKit.rect(x0 + 10, Math.round(markY), x0 + 12, Math.round(markY) + 30, 0xFFE0B040);

        // quest list
        int lx = listX(), ly = y0 + 31, vis = visibleRows();
        UiKit.inset(lx, y0 + 28, LIST_W, H - 38, 0xD80E0C0A);
        scroll = a.to("scroll", scrollTarget, 18);
        int selIdx = -1;
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).id.equals(sel)) {
                selIdx = i;
            }
        }
        if (selIdx >= 0) {
            float sy = a.to("selbox", ly + (selIdx - scroll) * ROW, 18);
            int top = Math.max(y0 + 29, Math.round(sy) - 1), bot = Math.min(y0 + 28 + H - 39, Math.round(sy) + ROW - 2);
            if (bot > top) {
                UiKit.rect(lx + 2, top, lx + LIST_W - 2, bot, 0xFF3A2E16);
                UiKit.rect(lx + 2, top, lx + 4, bot, 0xFFE0B040);
            }
        }
        int first = (int) Math.floor(scroll);
        for (int i = first; i < Math.min(rows.size(), first + vis + 1); i++) {
            float ry = ly + (i - scroll) * ROW;
            if (ry < ly - 2 || ry > ly + (vis - 1) * ROW + 2) {
                continue;
            }
            QuestDefs.Quest q = rows.get(i);
            int st = state(q);
            int y = Math.round(ry);
            boolean over = UiKit.in(mx, my, lx, y, LIST_W, ROW - 2) && !q.id.equals(sel);
            float h = a.to("row" + q.id, over ? 1 : 0, 18);
            if (h > 0.01F) {
                UiKit.rect(lx + 2, y - 1, lx + LIST_W - 2, y + ROW - 2, UiKit.blend(0x002A2214, 0xC02A2214, h));
            }
            fontRenderer.drawStringWithShadow(GLYPH[st], lx + 8, y + 3, GLYPH_C[st]);
            int tc = st == 0 ? 0x6A6050 : st == 3 ? 0x7A8A6A : 0xF0E6D0;
            fontRenderer.drawStringWithShadow(UiKit.fit(fontRenderer, "", q.title, LIST_W - 22), lx + 16, y + 3, tc);
        }
        if (rows.isEmpty()) {
            fontRenderer.drawString("No quests here yet.", lx + 6, ly + 3, UiKit.FAINT);
        }
        if (rows.size() > vis) {
            int trackH = vis * ROW, barH = Math.max(12, trackH * vis / rows.size());
            int barY = ly + Math.round((trackH - barH) * scroll / Math.max(1, rows.size() - vis));
            UiKit.rect(lx + LIST_W - 3, barY, lx + LIST_W - 1, barY + barH, 0xFF6B5A36);
        }

        // details: slide and fade in when the selection changes
        QuestDefs.Quest q = defs.get(sel);
        int dx0 = detX(), dw = detW();
        UiKit.inset(dx0, y0 + 28, dw, H - 38, 0xD80E0C0A);
        float in = UiKit.easeOutCubic(UiKit.clamp01((System.nanoTime() - selChanged) / 1e9F / 0.22F));
        int dx = dx0 + Math.round((1 - in) * 10), al = Math.max(4, Math.round(255 * in)) << 24;
        int dy = y0 + 33;
        if (q != null) {
            int st = state(q);
            UiKit.rect(dx0 + 4, dy - 1, dx0 + 22, dy + 17, 0xFF000000);
            UiKit.item(itemRender, stackOf(q.icon), dx0 + 5, dy);
            fontRenderer.drawStringWithShadow(UiKit.fit(fontRenderer, "\u00a7l", q.title, dw - 34), dx + 27, dy, al | UiKit.GOLD);
            fontRenderer.drawString(STATE[st] + ("daily".equals(q.repeat) ? " - daily" : ""), dx + 27, dy + 10, al | GLYPH_C[st]);
            dy += 22;
            int fixed = 3 + 11 + q.goals.size() * 18 + 1 + 11 + 18 + 4 + (st == 0 ? 21 : 0);
            int maxStory = Math.max(1, (btnY() - dy - fixed) / 9);
            java.util.List<String> story = fontRenderer.listFormattedStringToWidth(q.story, dw - 12);
            for (int li = 0; li < story.size() && li < maxStory; li++) {
                String line = story.get(li);
                if (li == maxStory - 1 && story.size() > maxStory) {
                    line = UiKit.fit(fontRenderer, "", line + " ...", dw - 12);
                }
                fontRenderer.drawString(line, dx + 6, dy, al | 0xB0A080);
                dy += 9;
            }
            dy += 3;
            if (st == 0) {
                StringBuilder need = new StringBuilder();
                for (String r : q.requires) {
                    QuestDefs.Quest rq = defs.get(r);
                    if (rq != null && !claimed.contains(r) && !complete(rq)) {
                        need.append(need.length() > 0 ? ", " : "").append(rq.title);
                    }
                }
                for (String line : fontRenderer.listFormattedStringToWidth("Finish " + need + " first.", dw - 12)) {
                    fontRenderer.drawString(line, dx + 6, dy, al | 0xD08A5A);
                    dy += 9;
                }
                dy += 3;
            }
            fontRenderer.drawStringWithShadow("\u00a7lGOALS", dx + 6, dy, al | UiKit.GOLD_TEXT);
            dy += 11;
            int[] p = prog.get(q.id);
            for (int i = 0; i < q.goals.size(); i++) {
                QuestDefs.Goal g = q.goals.get(i);
                int v = st == 3 ? g.count : p != null && i < p.length ? p[i] : 0;
                boolean done = v >= g.count;
                String count = v + "/" + g.count;
                fontRenderer.drawStringWithShadow(UiKit.fit(fontRenderer, "", g.describe(), dw - 18 - fontRenderer.getStringWidth(count)), dx + 6, dy,
                        al | (done ? UiKit.GREEN : 0xE0D6C0));
                fontRenderer.drawString(count, dx0 + dw - 6 - fontRenderer.getStringWidth(count), dy, al | UiKit.SOFT);
                float fill = a.from("g" + q.id + i, 0, Math.min(1F, v / (float) Math.max(1, g.count)) * in, 10);
                UiKit.bar(dx0 + 6, dy + 9, dw - 12, 5, fill, done ? 0xFF7FC83A : 0xFFD8A030, done ? 0xFFB8F070 : 0xFFF4CE68);
                dy += 18;
            }
            dy += 1;
            fontRenderer.drawStringWithShadow("\u00a7lREWARDS", dx + 6, dy, al | UiKit.GOLD_TEXT);
            dy += 11;
            int rx = dx0 + 6;
            if (q.xp > 0) {
                String xs = q.xp + " XP";
                int xw = fontRenderer.getStringWidth(xs) + 10;
                UiKit.rect(rx, dy, rx + xw, dy + 16, 0xFF3A2E10);
                fontRenderer.drawStringWithShadow(xs, rx + 5, dy + 4, UiKit.GOLD);
                rx += xw + 4;
            }
            for (String spec : q.items) {
                ItemStack s = stackOf(spec);
                if (!s.isEmpty() && rx < dx0 + dw - 18) {
                    UiKit.rect(rx, dy, rx + 18, dy + 18, 0xFF000000);
                    UiKit.rect(rx + 1, dy + 1, rx + 17, dy + 17, 0xFF2A2418);
                    UiKit.item(itemRender, s, rx + 1, dy + 1);
                    itemRender.renderItemOverlayIntoGUI(fontRenderer, s, rx + 1, dy + 1, null);
                    if (UiKit.in(mx, my, rx, dy, 18, 18)) {
                        hoverItem = s;
                    }
                    rx += 20;
                }
            }
            GlStateManager.enableBlend();
            if (st == 1 || st == 2) {
                boolean th = UiKit.in(mx, my, dx0 + 6, btnY(), 60, 16);
                UiKit.button(fontRenderer, dx0 + 6, btnY(), 60, 16, tracked.contains(q.id) ? "Untrack" : "Track", true, a.to("track", th ? 1 : 0, 20), 0x6688FF);
                boolean ch = UiKit.in(mx, my, claimX(), btnY(), 60, 16);
                float glow = st == 2 ? 0.4F + 0.4F * (float) Math.sin(a.time() * 5) : 0;
                UiKit.button(fontRenderer, claimX(), btnY(), 60, 16, "Claim", st == 2, Math.max(glow, a.to("claim", ch ? 1 : 0, 20)), 0x7FC83A);
            } else if (st == 3) {
                fontRenderer.drawStringWithShadow("Claimed", claimX() + 12, btnY() + 4, 0x5C9A5C);
            }
        } else {
            fontRenderer.drawString("Pick a quest.", dx0 + 6, dy + 2, UiKit.FAINT);
        }
        UiKit.endOpen();
        super.drawScreen(mx, my, pt);
        if (hoverItem != null) {
            renderToolTip(hoverItem, mx, my);
            hoverItem = null;
        }
    }
}
