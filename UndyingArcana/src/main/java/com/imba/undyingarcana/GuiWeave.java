package com.imba.undyingarcana;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import static com.imba.undyingarcana.SpellGraph.*;

/**
 * Spell Weave editor: a node canvas (pan with left-drag on empty space, zoom with the wheel), a node list to add from,
 * and an inspector for the selected node's settings. Drag from an output pin to an input pin to wire; drag a wired
 * input away to move or remove the wire; right-click a node to delete it. Ctrl+Z undoes. Bottom bar: save, revert,
 * templates, print a Spell Blueprint (a scroll item friends can learn from).
 */
@SideOnly(Side.CLIENT)
public class GuiWeave extends GuiScreen {
    private static final int TOP = 34, BOTTOM = 22, RIGHT = 140, ROW = 10, HEAD = 13;
    /** Last data from the server: g0..g5, mx0..mx5, magic, runes, open. */
    public static NBTTagCompound data = new NBTTagCompound();

    private final SpellGraph[] edit = new SpellGraph[Magic.SPELLS];
    private final boolean[] dirty = new boolean[Magic.SPELLS];
    private final List<NBTTagCompound> undo = new ArrayList<>();
    private int slot;
    private boolean pageMode;
    private SpellGraph g;
    private float camX = -20, camY = -20, zoom = 1F;
    private int sel = -1, cat = ACTION, listScroll;
    private int dragNode = -1, wireFrom = -1, wireOut = -1;
    private float dragDX, dragDY;
    private boolean panning, templates, escArmed;
    private int lastMX, lastMY;
    private String msg = "";
    private int msgColor = 0xFFFFFF;
    private long msgUntil;
    private GuiTextField name;

    public GuiWeave(int slot) {
        this.slot = slot;
        load();
        g = edit[slot];
    }

    /** Opening a Spell Page: its graph goes into the editor (unsaved); pick a slot and save to keep it. */
    public static GuiWeave forPage(NBTTagCompound page) {
        GuiWeave w = new GuiWeave(0);
        w.pageMode = true;
        w.g = SpellGraph.fromNBT(page.getCompoundTag("g"));
        w.edit[0] = w.g;
        w.dirty[0] = true;
        w.say("\u00a7dBlueprint by " + page.getString("author") + ": pick a slot (1-6) and Save to learn it.", 0xFFFFFF);
        return w;
    }

    public static void receive(NBTTagCompound t) {
        data = t;
        if (Minecraft.getMinecraft().currentScreen instanceof GuiWeave) {
            ((GuiWeave) Minecraft.getMinecraft().currentScreen).reloadClean();
        }
    }

    private void load() {
        for (int i = 0; i < Magic.SPELLS; i++) {
            edit[i] = data.hasKey("g" + i) ? SpellGraph.fromNBT(data.getCompoundTag("g" + i)) : SpellGraph.template(0);
        }
    }

    private void reloadClean() {
        for (int i = 0; i < Magic.SPELLS; i++) {
            if (!dirty[i] && data.hasKey("g" + i)) {
                edit[i] = SpellGraph.fromNBT(data.getCompoundTag("g" + i));
            }
        }
        g = edit[slot];
        if (name != null && !name.isFocused()) {
            name.setText(g.name);
        }
    }

    /** Mastery level 1..5 of a slot from the last server data. */
    public static int masteryOf(int slot) {
        int n = data.getInteger("mx" + slot), lv = 1;
        for (int i = 0; i < Magic.MASTERY.length; i++) {
            if (n >= Magic.MASTERY[i]) {
                lv = i + 1;
            }
        }
        return lv;
    }

    public static SpellGraph savedGraph(int slot) {
        return data.hasKey("g" + slot) ? SpellGraph.fromNBT(data.getCompoundTag("g" + slot)) : null;
    }

    /** Node mode opened from the block view with a graph (dirty = unsaved changes). */
    public static GuiWeave editing(int slot, SpellGraph g, boolean dirty) {
        GuiWeave w = new GuiWeave(slot);
        if (g != null) {
            w.edit[slot] = g.copy();
            w.g = w.edit[slot];
            w.dirty[slot] = dirty;
        }
        return w;
    }

    private int open() {
        return data.hasKey("open") ? data.getInteger("open") : 1;
    }

    private int magic() {
        return data.getInteger("magic");
    }

    private void say(String s, int color) {
        msg = s;
        msgColor = color;
        msgUntil = Minecraft.getSystemTime() + 5000;
    }

    private void changed() {
        dirty[slot] = true;
        g.blocks = null; // edited as nodes: the block view no longer matches
    }

    private void snapshot() {
        undo.add(g.toNBT());
        if (undo.size() > 40) {
            undo.remove(0);
        }
    }

    // ------------------------------------------------------------------ layout
    private int cl() {
        return 6;
    }

    private int ct() {
        return TOP + 4;
    }

    private int cw() {
        return width - RIGHT - 16;
    }

    private int ch() {
        return height - TOP - BOTTOM - 10;
    }

    private boolean inCanvas(int mx, int my) {
        return mx >= cl() && mx < cl() + cw() && my >= ct() && my < ct() + ch();
    }

    private float wx(int mx) {
        return (mx - cl()) / zoom + camX;
    }

    private float wy(int my) {
        return (my - ct()) / zoom + camY;
    }

    private int nodeW(Node n) {
        Def d = n.def();
        int w = fontRenderer.getStringWidth(d.name) + 18;
        int rows = Math.max(d.in.length, d.out.length);
        for (int r = 0; r < rows; r++) {
            int a = r < d.in.length ? fontRenderer.getStringWidth(d.inName[r]) : 0;
            int b = r < d.out.length ? fontRenderer.getStringWidth(d.outName[r]) : 0;
            w = Math.max(w, a + b + 26);
        }
        for (String l : summary(n)) {
            w = Math.max(w, fontRenderer.getStringWidth(l) + 12);
        }
        return Math.max(70, w);
    }

    private int nodeH(Node n) {
        Def d = n.def();
        return HEAD + Math.max(d.in.length, d.out.length) * ROW + summary(n).size() * ROW + 4;
    }

    private int pinY(Node n, int row) {
        return n.y + HEAD + row * ROW + 5;
    }

    /** Short lines under the pins: the node's settings. */
    private List<String> summary(Node n) {
        List<String> out = new ArrayList<>();
        Def d = n.def();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < d.params.length; i++) {
            String v = show(n, i);
            String part = (d.params[i].labels != null ? v : d.params[i].name + " " + v);
            if (n.type == SHAPE && (i == 2 && n.p[2] < 0 || i >= 6 && n.p[i] < 0)) {
                continue;
            }
            if (sb.length() + part.length() > 22) {
                out.add(sb.toString());
                sb.setLength(0);
            }
            sb.append(sb.length() > 0 ? ", " : "").append(part);
        }
        if (sb.length() > 0) {
            out.add(sb.toString());
        }
        return out.size() > 3 ? out.subList(0, 3) : out;
    }

    /** Value text of a parameter; element slots show the learned element. */
    private String show(Node n, int i) {
        Param pa = n.def().params[i];
        int v = n.p[i];
        if (n.type == ART) {
            int[] ranks = data.getIntArray("arts");
            int rk = v < ranks.length ? ranks[v] : 0;
            return pa.show(v) + (rk > 0 ? " " + new String[]{"I", "II", "III"}[Math.min(2, rk - 1)] : " (not learned)");
        }
        if (pa.labels != null && pa.labels.length > 0 && pa.labels[pa.labels.length - 1].startsWith("Element")) {
            if (v < 0) {
                return "none";
            }
            int[] el = PowerHud.elements();
            int e = v < Magic.MAX_ELEMENTS ? el[v] : -1;
            return e >= 0 ? Magic.ELEMENT_NAME[e] : "Element " + (v + 1) + " (empty)";
        }
        return pa.show(v);
    }

    // ------------------------------------------------------------------ hit tests (world coords)
    private int nodeAt(float x, float y) {
        for (int i = g.nodes.size() - 1; i >= 0; i--) {
            Node n = g.nodes.get(i);
            if (x >= n.x && x <= n.x + nodeW(n) && y >= n.y && y <= n.y + nodeH(n)) {
                return i;
            }
        }
        return -1;
    }

    /** {node, pin} of an output pin near (x, y), or null. */
    private int[] outAt(float x, float y) {
        for (int i = g.nodes.size() - 1; i >= 0; i--) {
            Node n = g.nodes.get(i);
            int w = nodeW(n);
            for (int k = 0; k < n.def().out.length; k++) {
                if (Math.abs(x - (n.x + w)) <= 5 && Math.abs(y - pinY(n, k)) <= 5) {
                    return new int[]{i, k};
                }
            }
        }
        return null;
    }

    private int[] inAt(float x, float y) {
        for (int i = g.nodes.size() - 1; i >= 0; i--) {
            Node n = g.nodes.get(i);
            for (int k = 0; k < n.def().in.length; k++) {
                if (Math.abs(x - n.x) <= 5 && Math.abs(y - pinY(n, k)) <= 5) {
                    return new int[]{i, k};
                }
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ init / input
    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        name = new GuiTextField(0, fontRenderer, 8 + Magic.SPELLS * 30 + 8, 7, 110, 12);
        name.setMaxStringLength(24);
        name.setText(g.name);
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    protected void keyTyped(char c, int key) throws IOException {
        if (name.isFocused()) {
            if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_ESCAPE) {
                name.setFocused(false);
                return;
            }
            name.textboxKeyTyped(c, key);
            if (!name.getText().equals(g.name)) {
                g.name = name.getText();
                dirty[slot] = true;
            }
            return;
        }
        if (key == Keyboard.KEY_ESCAPE) {
            if (templates) {
                templates = false;
                return;
            }
            boolean any = false;
            for (boolean b : dirty) {
                any |= b;
            }
            if (any && !escArmed) {
                escArmed = true;
                say("\u00a7eUnsaved changes: press Esc again to close without saving.", 0xFFFFFF);
                return;
            }
            mc.displayGuiScreen(null);
            return;
        }
        escArmed = false;
        if ((key == Keyboard.KEY_DELETE || key == Keyboard.KEY_BACK) && sel >= 0) {
            snapshot();
            g.remove(sel);
            sel = -1;
            changed();
            return;
        }
        if (key == Keyboard.KEY_Z && isCtrlKeyDown() && !undo.isEmpty()) {
            edit[slot] = SpellGraph.fromNBT(undo.remove(undo.size() - 1));
            g = edit[slot];
            sel = -1;
            changed();
        }
    }

    @Override
    protected void mouseClicked(int mx, int my, int button) throws IOException {
        lastMX = mx;
        lastMY = my;
        name.mouseClicked(mx, my, button);
        if (templates) {
            int tx = width / 2 - 70, ty = height / 2 - TEMPLATE.length * 7 - 8;
            for (int i = 0; i < TEMPLATE.length; i++) {
                if (UiKit.in(mx, my, tx + 4, ty + 18 + i * 14, 132, 13)) {
                    snapshot();
                    edit[slot] = SpellGraph.template(i);
                    g = edit[slot];
                    name.setText(g.name);
                    sel = -1;
                    camX = -20;
                    camY = -20;
                    dirty[slot] = true; // a template keeps its block view
                    UiKit.click();
                }
            }
            templates = false;
            return;
        }
        // slot tabs
        for (int t = 0; t < Magic.SPELLS; t++) {
            if (UiKit.in(mx, my, 8 + t * 30, 6, 28, 14)) {
                if (t >= open()) {
                    say("\u00a77Spell " + (t + 1) + " opens at " + Magic.SPELL_MAGIC[t] + " Magic.", 0xFFFFFF);
                } else if (pageMode) {
                    edit[t] = g;
                    dirty[t] = true;
                    slot = t;
                } else {
                    slot = t;
                    g = edit[t];
                    name.setText(g.name);
                    sel = -1;
                    undo.clear();
                }
                UiKit.click();
                return;
            }
        }
        if (bottomClick(mx, my)) {
            return;
        }
        if (rightClick(mx, my, button)) {
            return;
        }
        if (!inCanvas(mx, my)) {
            return;
        }
        float x = wx(mx), y = wy(my);
        if (button == 1) {
            int[] ip = inAt(x, y);
            if (ip != null && g.nodes.get(ip[0]).link[ip[1]] >= 0) {
                snapshot();
                g.nodes.get(ip[0]).link[ip[1]] = -1;
                changed();
                return;
            }
            int n = nodeAt(x, y);
            if (n >= 0) {
                snapshot();
                g.remove(n);
                sel = -1;
                changed();
            }
            return;
        }
        if (button != 0) {
            return;
        }
        int[] op = outAt(x, y);
        if (op != null) {
            wireFrom = op[0];
            wireOut = op[1];
            return;
        }
        int[] ip = inAt(x, y);
        if (ip != null && g.nodes.get(ip[0]).link[ip[1]] >= 0) {
            // pick the wire up from its input end
            snapshot();
            int l = g.nodes.get(ip[0]).link[ip[1]];
            g.nodes.get(ip[0]).link[ip[1]] = -1;
            wireFrom = l / 16;
            wireOut = l % 16;
            changed();
            return;
        }
        int n = nodeAt(x, y);
        if (n >= 0) {
            sel = n;
            snapshot();
            dragNode = n;
            dragDX = x - g.nodes.get(n).x;
            dragDY = y - g.nodes.get(n).y;
            return;
        }
        sel = -1;
        panning = true;
    }

    @Override
    protected void mouseClickMove(int mx, int my, int button, long time) {
        if (dragNode >= 0 && dragNode < g.nodes.size()) {
            Node n = g.nodes.get(dragNode);
            int nx = Math.round(wx(mx) - dragDX), ny = Math.round(wy(my) - dragDY);
            if (nx != n.x || ny != n.y) {
                n.x = nx;
                n.y = ny;
                dirty[slot] = true; // moving a node does not change the spell
            }
        } else if (panning) {
            camX -= (mx - lastMX) / zoom;
            camY -= (my - lastMY) / zoom;
        }
        lastMX = mx;
        lastMY = my;
    }

    @Override
    protected void mouseReleased(int mx, int my, int state) {
        if (wireFrom >= 0 && inCanvas(mx, my)) {
            int[] ip = inAt(wx(mx), wy(my));
            if (ip != null && ip[0] != wireFrom) {
                Node to = g.nodes.get(ip[0]);
                Def sd = g.nodes.get(wireFrom).def();
                if (sd.out[wireOut] == to.def().in[ip[1]]) {
                    snapshot();
                    int old = to.link[ip[1]];
                    to.link[ip[1]] = wireFrom * 16 + wireOut;
                    if (g.cyclic()) {
                        to.link[ip[1]] = old;
                        say("\u00a7cThat would make a loop: use Repeat instead.", 0xFFFFFF);
                    } else {
                        changed();
                    }
                } else {
                    say("\u00a7cA " + PIN_NAME[sd.out[wireOut]] + " pin cannot go into a " + PIN_NAME[to.def().in[ip[1]]] + " pin.", 0xFFFFFF);
                }
            }
        }
        wireFrom = -1;
        dragNode = -1;
        panning = false;
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) {
            return;
        }
        int mx = Mouse.getEventX() * width / mc.displayWidth;
        int my = height - Mouse.getEventY() * height / mc.displayHeight - 1;
        if (inCanvas(mx, my)) {
            float bx = wx(mx), by = wy(my);
            zoom = Math.max(0.5F, Math.min(1.6F, zoom * (wheel > 0 ? 1.12F : 1 / 1.12F)));
            camX = bx - (mx - cl()) / zoom;
            camY = by - (my - ct()) / zoom;
        } else if (mx >= width - RIGHT - 6) {
            listScroll = Math.max(0, listScroll + (wheel > 0 ? -1 : 1));
        }
    }

    // ------------------------------------------------------------------ right panel (node list + inspector)
    private int px() {
        return width - RIGHT - 6;
    }

    private List<Def> catList() {
        List<Def> l = new ArrayList<>();
        for (Def d : LIST) {
            if (d.cat == cat) {
                l.add(d);
            }
        }
        return l;
    }

    private int listRows() {
        return Math.max(3, (height / 2 - TOP - 24) / 12);
    }

    private boolean rightClick(int mx, int my, int button) {
        int px = px(), py = TOP + 4;
        if (mx < px) {
            return false;
        }
        for (int c = 0; c < CAT.length; c++) {
            if (UiKit.in(mx, my, px + c * 35, py, 34, 13)) {
                cat = c;
                listScroll = 0;
                UiKit.click();
                return true;
            }
        }
        List<Def> l = catList();
        int rows = listRows();
        listScroll = Math.min(listScroll, Math.max(0, l.size() - rows));
        for (int r = 0; r < rows && r + listScroll < l.size(); r++) {
            if (UiKit.in(mx, my, px, py + 16 + r * 12, RIGHT, 11)) {
                if (g.nodes.size() >= SpellGraph.maxNodes(magic())) {
                    say("\u00a7cNode limit " + SpellGraph.maxNodes(magic()) + " (one more every " + MAGIC_PER_NODE + " Magic).", 0xFFFFFF);
                    return true;
                }
                snapshot();
                Def d = l.get(r + listScroll);
                int nx = Math.round(camX + cw() / 2F / zoom - 40 + (g.nodes.size() % 5) * 8);
                int ny = Math.round(camY + ch() / 2F / zoom - 20 + (g.nodes.size() % 5) * 8);
                g.add(d.id, nx, ny);
                sel = g.nodes.size() - 1;
                changed();
                UiKit.click();
                return true;
            }
        }
        // inspector
        if (sel >= 0 && sel < g.nodes.size()) {
            Node n = g.nodes.get(sel);
            Def d = n.def();
            int iy = inspectorY() + 30;
            for (int i = 0; i < d.params.length; i++) {
                int y = iy + i * 13;
                int step = isShiftKeyDown() ? 5 : 1;
                Param pa = d.params[i];
                if (UiKit.in(mx, my, px + RIGHT - 30, y, 12, 11)) {
                    snapshot();
                    n.p[i] = pa.labels != null && n.p[i] <= pa.min ? pa.max : Math.max(pa.min, n.p[i] - step);
                    changed();
                    UiKit.click();
                    return true;
                }
                if (UiKit.in(mx, my, px + RIGHT - 15, y, 12, 11)) {
                    snapshot();
                    n.p[i] = pa.labels != null && n.p[i] >= pa.max ? pa.min : Math.min(pa.max, n.p[i] + step);
                    changed();
                    UiKit.click();
                    return true;
                }
            }
        }
        return true;
    }

    private int inspectorY() {
        return TOP + 4 + 18 + listRows() * 12 + 6;
    }

    // ------------------------------------------------------------------ bottom bar
    private static final String[] BTN = {"Save", "Revert", "Templates", "Print blueprint", "Blocks", "Close"};

    private int btnW() {
        return Math.max(48, Math.min(84, (width - 170) / BTN.length - 4));
    }

    private int btnX(int i) {
        return 6 + i * (btnW() + 4);
    }

    private boolean bottomClick(int mx, int my) {
        int y = height - BOTTOM;
        for (int i = 0; i < BTN.length; i++) {
            if (!UiKit.in(mx, my, btnX(i), y, btnW(), 16)) {
                continue;
            }
            UiKit.click();
            switch (i) {
                case 0: {
                    String bad = g.check(magic(), Math.max(1, data.getInteger("runes")));
                    if (bad != null) {
                        say("\u00a7c" + bad, 0xFFFFFF);
                        break;
                    }
                    if (slot >= open()) {
                        say("\u00a77Spell " + (slot + 1) + " is locked.", 0xFFFFFF);
                        break;
                    }
                    SkillNet.NET.sendToServer(new SkillNet.MsgWeaveSave(slot, g.toNBT()));
                    dirty[slot] = false;
                    if (pageMode) {
                        pageMode = false;
                    }
                    break;
                }
                case 1:
                    if (data.hasKey("g" + slot)) {
                        snapshot();
                        edit[slot] = SpellGraph.fromNBT(data.getCompoundTag("g" + slot));
                        g = edit[slot];
                        name.setText(g.name);
                        dirty[slot] = false;
                        sel = -1;
                    }
                    break;
                case 2:
                    templates = true;
                    break;
                case 4:
                    if (g.blocks == null) {
                        say("\u00a77Edited as nodes: there is no block view (Templates start a new block spell).", 0xFFFFFF);
                    } else {
                        mc.displayGuiScreen(GuiBlocks.from(slot, g, dirty[slot]));
                    }
                    break;
                case 3:
                    if (dirty[slot]) {
                        say("\u00a7eSave the spell first, then print its blueprint (needs 1 paper).", 0xFFFFFF);
                    } else {
                        SkillNet.NET.sendToServer(new SkillNet.MsgAct(6, slot, ""));
                    }
                    break;
                default:
                    mc.displayGuiScreen(null);
                    break;
            }
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ drawing
    @Override
    public void drawScreen(int mx, int my, float pt) {
        drawRect(0, 0, width, height, 0xE0100D0A);
        // top bar
        for (int t = 0; t < Magic.SPELLS; t++) {
            boolean locked = t >= open(), cur = t == slot;
            UiKit.rect(8 + t * 30, 6, 36 + t * 30, 20, locked ? 0xFF120F0B : cur ? 0xFF3A2E18 : 0xFF1E1912);
            if (cur) {
                UiKit.rect(8 + t * 30, 19, 36 + t * 30, 20, 0xFFE0B040);
            }
            String lab = locked ? "\u00a78" + Magic.SPELL_MAGIC[t] + "M" : (t + 1) + (dirty[t] ? "*" : "");
            fontRenderer.drawStringWithShadow(lab, 22 + t * 30 - fontRenderer.getStringWidth(lab) / 2, 9, cur ? UiKit.GOLD_TEXT : UiKit.MUTED);
        }
        name.drawTextBox();
        int max = SpellGraph.maxNodes(magic());
        int mxn = data.getInteger("mx" + slot), lv = 1;
        for (int i = 0; i < Magic.MASTERY.length; i++) {
            if (mxn >= Magic.MASTERY[i]) {
                lv = i + 1;
            }
        }
        int cost = Math.max(1, Math.round(g.cost() * (1F - 0.05F * (lv - 1))));
        String[] roman = {"I", "II", "III", "IV", "V"};
        String info = "Nodes " + (g.nodes.size() > max ? "\u00a7c" : "\u00a7f") + g.nodes.size() + "\u00a77/" + max + "   Mana \u00a7b" + cost
                + "\u00a77   Cooldown \u00a7f" + String.format("%.1f", g.cooldown() / 20F) + "s\u00a77   Mastery \u00a76" + roman[lv - 1]
                + (lv < 5 ? "\u00a78 (" + mxn + "/" + Magic.MASTERY[lv] + ")" : "");
        fontRenderer.drawStringWithShadow(UiKit.fit(fontRenderer, "", info, width - 16), 8, 23, 0xB0A890);
        String title = pageMode ? "\u00a7dSPELL BLUEPRINT" : "\u00a7lSPELL WEAVE";
        fontRenderer.drawStringWithShadow(title, width - 8 - fontRenderer.getStringWidth(title), 9, UiKit.GOLD_TEXT);
        // canvas
        UiKit.inset(cl() - 1, ct() - 1, cw() + 2, ch() + 2, 0xFF0C0B0A);
        scissor(cl(), ct(), cw(), ch());
        GlStateManager.pushMatrix();
        GlStateManager.translate(cl(), ct(), 0);
        GlStateManager.scale(zoom, zoom, 1);
        GlStateManager.translate(-camX, -camY, 0);
        grid();
        for (int i = 0; i < g.nodes.size(); i++) {
            Node n = g.nodes.get(i);
            for (int k = 0; k < n.link.length; k++) {
                int l = n.link[k];
                if (l < 0 || l / 16 >= g.nodes.size()) {
                    continue;
                }
                Node s = g.nodes.get(l / 16);
                int type = n.def().in[k];
                wire(s.x + nodeW(s), pinY(s, l % 16), n.x, pinY(n, k), PIN_RGB[type], type == X ? 2.4F : 1.8F);
            }
        }
        if (wireFrom >= 0 && wireFrom < g.nodes.size()) {
            Node s = g.nodes.get(wireFrom);
            wire(s.x + nodeW(s), pinY(s, wireOut), wx(mx), wy(my), PIN_RGB[s.def().out[wireOut]], 2F);
        }
        int hoverNode = inCanvas(mx, my) ? nodeAt(wx(mx), wy(my)) : -1;
        for (int i = 0; i < g.nodes.size(); i++) {
            drawNode(g.nodes.get(i), i == sel, i == hoverNode);
        }
        GlStateManager.popMatrix();
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        if (g.nodes.isEmpty()) {
            String h = "Add an On Cast event (Events list), then a Cast Shape (Actions), and wire them.";
            fontRenderer.drawString(h, cl() + cw() / 2 - fontRenderer.getStringWidth(h) / 2, ct() + ch() / 2, UiKit.FAINT);
        }
        drawRight(mx, my);
        // bottom bar
        int by = height - BOTTOM;
        for (int i = 0; i < BTN.length; i++) {
            boolean h = UiKit.in(mx, my, btnX(i), by, btnW(), 16);
            boolean on = i != 0 || dirty[slot];
            UiKit.button(fontRenderer, btnX(i), by, btnW(), 16, UiKit.fit(fontRenderer, "", i == 0 && !dirty[slot] ? "Saved" : BTN[i], btnW() - 4), on,
                    h ? 1 : 0,
                    i == 0 ? 0x7FC83A : i == BTN.length - 1 ? 0xE05050 : i == 3 ? 0xB070FF : 0xE0B040);
        }
        int tx = btnX(BTN.length) + 4;
        String bad = g.check(magic(), Math.max(1, data.getInteger("runes")));
        String line = Minecraft.getSystemTime() < msgUntil ? msg : bad != null ? "\u00a7c" + bad
                : "\u00a78Drag pins to wire. Right-click: delete. Wheel: zoom. Ctrl+Z: undo.";
        fontRenderer.drawString(UiKit.fit(fontRenderer, "", line, width - tx - 6), tx, by + 4, msgColor);
        if (templates) {
            int tx2 = width / 2 - 70, ty = height / 2 - TEMPLATE.length * 7 - 8;
            UiKit.panel(tx2, ty, 140, 22 + TEMPLATE.length * 14);
            fontRenderer.drawStringWithShadow("Templates", tx2 + 8, ty + 6, UiKit.GOLD_TEXT);
            for (int i = 0; i < TEMPLATE.length; i++) {
                boolean h = UiKit.in(mx, my, tx2 + 4, ty + 18 + i * 14, 132, 13);
                UiKit.button(fontRenderer, tx2 + 4, ty + 18 + i * 14, 132, 13, TEMPLATE[i], true, h ? 1 : 0, 0xB070FF);
            }
        }
        super.drawScreen(mx, my, pt);
        tooltips(mx, my, hoverNode);
    }

    private void tooltips(int mx, int my, int hoverNode) {
        if (templates) {
            return;
        }
        if (inCanvas(mx, my)) {
            float x = wx(mx), y = wy(my);
            int[] ip = inAt(x, y), op = outAt(x, y);
            if (op != null) {
                Def d = g.nodes.get(op[0]).def();
                drawHoveringText(Arrays.asList("\u00a7f" + d.outName[op[1]], "\u00a77" + PIN_NAME[d.out[op[1]]] + " output: drag to an input"), mx, my);
            } else if (ip != null) {
                Def d = g.nodes.get(ip[0]).def();
                drawHoveringText(Arrays.asList("\u00a7f" + d.inName[ip[1]], "\u00a77" + PIN_NAME[d.in[ip[1]]] + " input"
                        + (d.in[ip[1]] == X ? "" : " (empty = a sensible default)")), mx, my);
            } else if (hoverNode >= 0 && dragNode < 0) {
                Def d = g.nodes.get(hoverNode).def();
                List<String> t = new ArrayList<>();
                t.add("\u00a7f" + d.name);
                t.addAll(fontRenderer.listFormattedStringToWidth("\u00a77" + d.desc, 180));
                drawHoveringText(t, mx, my);
            }
            return;
        }
        if (mx >= px()) {
            List<Def> l = catList();
            int rows = listRows();
            for (int r = 0; r < rows && r + listScroll < l.size(); r++) {
                if (UiKit.in(mx, my, px(), TOP + 4 + 16 + r * 12, RIGHT, 11)) {
                    Def d = l.get(r + listScroll);
                    List<String> t = new ArrayList<>();
                    t.add("\u00a7f" + d.name);
                    t.addAll(fontRenderer.listFormattedStringToWidth("\u00a77" + d.desc, 180));
                    drawHoveringText(t, mx, my);
                }
            }
        }
    }

    private void drawRight(int mx, int my) {
        int px = px(), py = TOP + 4;
        UiKit.inset(px - 2, py - 2, RIGHT + 4, height - TOP - BOTTOM - 8, 0xFF0C0B0A);
        for (int c = 0; c < CAT.length; c++) {
            boolean cur = c == cat;
            UiKit.rect(px + c * 35, py, px + c * 35 + 34, py + 13, cur ? 0xFF000000 | darker(CAT_RGB[c]) : 0xFF1A1612);
            UiKit.rect(px + c * 35, py + 12, px + c * 35 + 34, py + 13, 0xFF000000 | CAT_RGB[c]);
            String s = CAT[c];
            fontRenderer.drawString(UiKit.fit(fontRenderer, "", s, 32), px + c * 35 + 17 - Math.min(32, fontRenderer.getStringWidth(s)) / 2, py + 3,
                    cur ? 0xFFFFFF : UiKit.MUTED);
        }
        List<Def> l = catList();
        int rows = listRows();
        listScroll = Math.min(listScroll, Math.max(0, l.size() - rows));
        for (int r = 0; r < rows && r + listScroll < l.size(); r++) {
            Def d = l.get(r + listScroll);
            int y = py + 16 + r * 12;
            boolean h = UiKit.in(mx, my, px, y, RIGHT, 11);
            UiKit.rect(px, y, px + RIGHT, y + 11, h ? 0xFF2E2618 : 0xFF17130F);
            UiKit.rect(px, y, px + 2, y + 11, 0xFF000000 | CAT_RGB[d.cat]);
            fontRenderer.drawString("+ " + d.name, px + 5, y + 2, h ? 0xFFFFFF : 0xD6CCB4);
        }
        if (l.size() > rows) {
            fontRenderer.drawString("\u00a78wheel: more", px + RIGHT - 52, py + 16 + rows * 12 - 1, 0);
        }
        // inspector
        int iy = inspectorY();
        UiKit.rect(px, iy - 3, px + RIGHT, iy - 2, UiKit.GOLD_EDGE);
        if (sel < 0 || sel >= g.nodes.size()) {
            fontRenderer.drawSplitString("\u00a77Click a node to edit its settings.", px + 2, iy + 2, RIGHT - 4, 0xFFFFFF);
            return;
        }
        Node n = g.nodes.get(sel);
        Def d = n.def();
        fontRenderer.drawStringWithShadow(d.name, px + 2, iy + 2, 0xFF000000 | CAT_RGB[d.cat]);
        List<String> desc = fontRenderer.listFormattedStringToWidth(d.desc, RIGHT - 4);
        for (int i = 0; i < Math.min(1, desc.size()); i++) {
            fontRenderer.drawString(UiKit.fit(fontRenderer, "", desc.get(i), RIGHT - 4), px + 2, iy + 14, UiKit.FAINT);
        }
        int y0 = iy + 30;
        for (int i = 0; i < d.params.length; i++) {
            int y = y0 + i * 13;
            if (y > height - BOTTOM - 16) {
                break;
            }
            fontRenderer.drawString(UiKit.fit(fontRenderer, "", d.params[i].name, 50), px + 2, y + 2, UiKit.MUTED);
            String v = show(n, i);
            fontRenderer.drawStringWithShadow(UiKit.fit(fontRenderer, "", v, RIGHT - 88), px + 54, y + 2, 0xFFFFFF);
            boolean hm = UiKit.in(mx, my, px + RIGHT - 30, y, 12, 11), hp = UiKit.in(mx, my, px + RIGHT - 15, y, 12, 11);
            UiKit.button(fontRenderer, px + RIGHT - 30, y, 12, 11, "<", true, hm ? 1 : 0, 0xE0B040);
            UiKit.button(fontRenderer, px + RIGHT - 15, y, 12, 11, ">", true, hp ? 1 : 0, 0xE0B040);
        }
        if (n.type == SHAPE) {
            int y = y0 + d.params.length * 13 + 2;
            if (y < height - BOTTOM - 20) {
                String sd = Magic.SHAPE_DESC[Math.max(0, Math.min(Magic.SHAPE.length - 1, n.p[0]))];
                fontRenderer.drawSplitString("\u00a78" + sd, px + 2, y, RIGHT - 4, 0xFFFFFF);
            }
        }
    }

    private void drawNode(Node n, boolean selected, boolean hover) {
        Def d = n.def();
        int w = nodeW(n), h = nodeH(n);
        int col = 0xFF000000 | CAT_RGB[d.cat];
        drawRect(n.x - 1, n.y - 1, n.x + w + 1, n.y + h + 1, selected ? 0xFFFFFFFF : hover ? 0xFF8A7A5A : 0xFF000000);
        drawRect(n.x, n.y, n.x + w, n.y + h, 0xF01E1A16);
        drawRect(n.x, n.y, n.x + w, n.y + HEAD - 1, 0xFF000000 | darker(CAT_RGB[d.cat]));
        drawRect(n.x, n.y + HEAD - 2, n.x + w, n.y + HEAD - 1, col);
        fontRenderer.drawStringWithShadow(d.name, n.x + 4, n.y + 2, 0xFFFFFF);
        for (int k = 0; k < d.in.length; k++) {
            int py = pinY(n, k);
            pin(n.x, py, PIN_RGB[d.in[k]], d.in[k] == X, n.link[k] >= 0);
            fontRenderer.drawString(d.inName[k], n.x + 5, py - 4, 0xB0A890);
        }
        for (int k = 0; k < d.out.length; k++) {
            int py = pinY(n, k);
            pin(n.x + w, py, PIN_RGB[d.out[k]], d.out[k] == X, true);
            fontRenderer.drawString(d.outName[k], n.x + w - 5 - fontRenderer.getStringWidth(d.outName[k]), py - 4, 0xB0A890);
        }
        int y = n.y + HEAD + Math.max(d.in.length, d.out.length) * ROW + 1;
        for (String l : summary(n)) {
            fontRenderer.drawString(l, n.x + 4, y, 0xE8D8A8);
            y += ROW;
        }
    }

    private void pin(int x, int y, int rgb, boolean exec, boolean filled) {
        int c = 0xFF000000 | rgb;
        if (exec) {
            drawRect(x - 3, y - 3, x + 3, y + 3, 0xFF000000);
            drawRect(x - 2, y - 2, x + 2, y + 2, filled ? c : 0xFF302A22);
        } else {
            drawRect(x - 3, y - 2, x + 3, y + 2, 0xFF000000);
            drawRect(x - 2, y - 3, x + 2, y + 3, 0xFF000000);
            drawRect(x - 2, y - 1, x + 2, y + 1, filled ? c : 0xFF302A22);
            drawRect(x - 1, y - 2, x + 1, y + 2, filled ? c : 0xFF302A22);
        }
    }

    private void grid() {
        int step = 20;
        float x0 = camX - camX % step - step, y0 = camY - camY % step - step;
        float x1 = camX + cw() / zoom + step, y1 = camY + ch() / zoom + step;
        for (float x = x0; x < x1; x += step) {
            drawRect(Math.round(x), Math.round(y0), Math.round(x) + 1, Math.round(y1), 0x14FFFFFF);
        }
        for (float y = y0; y < y1; y += step) {
            drawRect(Math.round(x0), Math.round(y), Math.round(x1), Math.round(y) + 1, 0x14FFFFFF);
        }
    }

    private static void wire(float x1, float y1, float x2, float y2, int rgb, float width) {
        float k = Math.max(25, Math.abs(x2 - x1) * 0.5F);
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glLineWidth(width * Math.max(1, new ScaledResolution(Minecraft.getMinecraft()).getScaleFactor() / 2F));
        Tessellator t = Tessellator.getInstance();
        BufferBuilder b = t.getBuffer();
        b.begin(GL11.GL_LINE_STRIP, DefaultVertexFormats.POSITION_COLOR);
        float r = (rgb >> 16 & 255) / 255F, g = (rgb >> 8 & 255) / 255F, bl = (rgb & 255) / 255F;
        for (int i = 0; i <= 24; i++) {
            float s = i / 24F, u = 1 - s;
            float x = u * u * u * x1 + 3 * u * u * s * (x1 + k) + 3 * u * s * s * (x2 - k) + s * s * s * x2;
            float y = u * u * u * y1 + 3 * u * u * s * y1 + 3 * u * s * s * y2 + s * s * s * y2;
            b.pos(x, y, 0).color(r, g, bl, 0.9F).endVertex();
        }
        t.draw();
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        GL11.glLineWidth(1F);
        GlStateManager.enableTexture2D();
    }

    private void scissor(int x, int y, int w, int h) {
        int sf = new ScaledResolution(mc).getScaleFactor();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(x * sf, mc.displayHeight - (y + h) * sf, w * sf, h * sf);
    }

    private static int darker(int rgb) {
        int r = (rgb >> 16 & 255) * 2 / 5, g = (rgb >> 8 & 255) * 2 / 5, b = (rgb & 255) * 2 / 5;
        return r << 16 | g << 8 | b;
    }
}
