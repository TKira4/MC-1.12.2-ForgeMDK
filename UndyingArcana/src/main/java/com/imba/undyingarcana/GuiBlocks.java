package com.imba.undyingarcana;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import static com.imba.undyingarcana.SpellBlocks.*;

/**
 * Spell Weave, block view (Scratch style): drag blocks from the list into a script, drop them between other blocks or
 * into a block's sections (then / else, do, on hit / on end). Click a chip to pick its value. Right-click deletes;
 * dragging a block back onto the list deletes it too. The right panel reads the spell back in plain words. The
 * graph the server runs is compiled from the blocks; "Node mode" opens the same spell as wired nodes.
 */
@SideOnly(Side.CLIENT)
public class GuiBlocks extends GuiScreen {
    private static final int TOP = 34, BOTTOM = 6, LEFT = 112, RIGHT = 132, HEAD = 12, SEC_INDENT = 10, MAXW = 210;
    private static final int[] CAT_RGB = {0x4682C8, 0xC89632, 0xBE4646};
    private static final String[] CAT = {"WHEN", "CONTROL", "DO"};

    private final Tree[] trees = new Tree[Magic.SPELLS];
    private final String[] names = new String[Magic.SPELLS];
    private final boolean[] dirty = new boolean[Magic.SPELLS];
    private final List<NBTTagCompound> undo = new ArrayList<>();
    private int slot;
    private boolean pageMode, escArmed;
    private boolean nodesOnly; // the saved spell was edited as nodes: no block view
    private float camX = -8, camY = -8;
    private GuiTextField name;
    private String msg = "";
    private long msgUntil;
    private int paletteScroll;

    // drag state
    private Blk drag;
    private List<Blk> dragFrom;
    private int dragIndex = -1, pressX, pressY, grabDX, grabDY;
    private boolean pressing, dragging, panning, fromPalette;
    private Blk pressed;
    private List<Blk> pressedList;
    private int pressedIndex;
    private int lastMX, lastMY;
    // chip popup
    private Blk popBlk;
    private int popChip = -1, popX, popY;

    // layout results (screen coordinates), rebuilt every frame
    private final List<BRect> rects = new ArrayList<>();
    private final List<CRect> chips = new ArrayList<>();
    private final List<Slot> slots = new ArrayList<>();

    private static final class BRect {
        Blk b;
        List<Blk> list;
        int index, x, y, w, h;
        int flags;
    }

    private static final class CRect {
        Blk b;
        int k, x, y, w, h, flags;
    }

    private static final class Slot {
        List<Blk> list;
        int index, x, y, w, flags;
    }

    // context flags for chip choices
    private static final int IN_HIT = 1, IN_END = 2, IN_EACH = 4, IN_KILL = 8, IN_HURT = 16;

    public GuiBlocks(int slot) {
        this.slot = slot;
        for (int i = 0; i < Magic.SPELLS; i++) {
            loadSlot(i);
        }
    }

    /** A Spell Blueprint with blocks: pick a slot and save to learn it. */
    public static GuiScreen forPage(NBTTagCompound page) {
        SpellGraph g = SpellGraph.fromNBT(page.getCompoundTag("g"));
        if (g.blocks == null) {
            return GuiWeave.forPage(page);
        }
        GuiBlocks b = new GuiBlocks(0);
        b.pageMode = true;
        b.trees[0] = Tree.fromNBT(g.blocks);
        b.names[0] = g.name;
        b.dirty[0] = true;
        b.nodesOnly = false;
        b.say("\u00a7dBlueprint by " + page.getString("author") + ": pick a slot (1-6) and Save to learn it.");
        return b;
    }

    /** Opened from Node mode with a graph that has blocks (or a fresh tree). */
    public static GuiBlocks from(int slot, SpellGraph g, boolean dirty) {
        GuiBlocks b = new GuiBlocks(slot);
        if (g != null && g.blocks != null) {
            b.trees[slot] = Tree.fromNBT(g.blocks);
            b.names[slot] = g.name;
            b.dirty[slot] = dirty;
            b.nodesOnly = false;
        }
        return b;
    }

    private void loadSlot(int i) {
        SpellGraph g = GuiWeave.savedGraph(i);
        if (g == null) {
            g = SpellGraph.template(0);
        }
        names[i] = g.name;
        if (g.blocks != null) {
            trees[i] = Tree.fromNBT(g.blocks);
        } else {
            trees[i] = null;
        }
        if (i == slot) {
            nodesOnly = trees[i] == null;
        }
    }

    private Tree tree() {
        if (trees[slot] == null) {
            trees[slot] = new Tree();
        }
        return trees[slot];
    }

    private SpellGraph compiled() {
        return SpellBlocks.compile(tree(), names[slot]);
    }

    private int open() {
        return GuiWeave.data.hasKey("open") ? GuiWeave.data.getInteger("open") : 1;
    }

    private int magic() {
        return GuiWeave.data.getInteger("magic");
    }

    private void say(String s) {
        msg = s;
        msgUntil = Minecraft.getSystemTime() + 5000;
    }

    private void snapshot() {
        undo.add(tree().toNBT());
        if (undo.size() > 40) {
            undo.remove(0);
        }
    }

    private void changed() {
        dirty[slot] = true;
    }

    // ------------------------------------------------------------------ layout
    private int cl() {
        return LEFT + 10;
    }

    private int ct() {
        return TOP + 4;
    }

    private int cw() {
        return width - LEFT - RIGHT - 20;
    }

    private int ch() {
        return height - TOP - BOTTOM - 8;
    }

    private boolean inCanvas(int mx, int my) {
        return mx >= cl() && mx < cl() + cw() && my >= ct() && my < ct() + ch();
    }

    private int[] elements() {
        return PowerHud.elements();
    }

    /** Token list of a block header: strings and chip indexes (as -1 - k). */
    private List<Object> tokens(Blk b) {
        List<Object> out = new ArrayList<>();
        String t = b.def().text;
        StringBuilder w = new StringBuilder();
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c == '{') {
                if (w.length() > 0) {
                    out.add(w.toString());
                    w.setLength(0);
                }
                int k = t.charAt(i + 1) - '0';
                if (!hiddenChip(b, k)) {
                    out.add(-1 - k);
                }
                i += 2;
            } else if (c == ' ') {
                if (w.length() > 0) {
                    out.add(w.toString());
                    w.setLength(0);
                }
            } else {
                w.append(c);
            }
        }
        if (w.length() > 0) {
            out.add(w.toString());
        }
        return out;
    }

    /** Chips that only show when used: the If's number and subject, a cast's empty 2nd element / runes show as "+". */
    private boolean hiddenChip(Blk b, int k) {
        if (b.type == S_IF) {
            return k == 2 && !SpellBlocks.predNum(b.v[1]) || k == 0 && SpellBlocks.predWorld(b.v[1]);
        }
        if (b.type == S_SHAPE && k == 8) {
            return SpellBlocks.value(b, k, null).isEmpty(); // shapes centred on you have no direction
        }
        return false;
    }

    private String chipLabel(Blk b, int k) {
        String v = SpellBlocks.value(b, k, elements());
        if (v.isEmpty()) {
            Chip c = b.def().chips[k];
            return c.kind == ELEM2 ? "+element" : c.kind == PT ? "+from" : "+rune";
        }
        return v;
    }

    private int chipW(Blk b, int k) {
        return fontRenderer.getStringWidth(chipLabel(b, k)) + 8;
    }

    /** Measures (and lays out when draw = true) a block at x, y. Returns its total height. */
    private int layout(Blk b, List<Blk> list, int index, int x, int y, int flags, boolean record) {
        BDef d = b.def();
        // header: flow tokens into lines of at most MAXW
        List<Object> tk = tokens(b);
        int lineW = 0, lines = 1, maxLine = 0;
        List<int[]> pos = new ArrayList<>(); // per token: x offset, line
        for (Object o : tk) {
            int w = o instanceof String ? fontRenderer.getStringWidth((String) o) : chipW(b, -1 - (Integer) o);
            if (lineW > 0 && lineW + w > MAXW) {
                maxLine = Math.max(maxLine, lineW);
                lineW = 0;
                lines++;
            }
            pos.add(new int[]{lineW, lines - 1});
            lineW += w + 4;
        }
        maxLine = Math.max(maxLine, lineW);
        int w = Math.max(40, maxLine + 8);
        int h = lines * HEAD + 2;
        if (record) {
            BRect r = new BRect();
            r.b = b;
            r.list = list;
            r.index = index;
            r.x = x;
            r.y = y;
            r.w = w;
            r.h = h;
            r.flags = flags;
            rects.add(r);
            for (int i = 0; i < tk.size(); i++) {
                Object o = tk.get(i);
                if (o instanceof Integer) {
                    int k = -1 - (Integer) o;
                    CRect c = new CRect();
                    c.b = b;
                    c.k = k;
                    c.x = x + 4 + pos.get(i)[0];
                    c.y = y + 1 + pos.get(i)[1] * HEAD;
                    c.w = chipW(b, k);
                    c.h = HEAD - 1;
                    c.flags = flags;
                    chips.add(c);
                }
            }
        }
        int total = h;
        if (d.cat == 0) {
            return total;
        }
        for (int s = 0; s < d.sections.length; s++) {
            List<Blk> sec = b.sec.get(s);
            boolean first = s == 0 && d.cat == 1;
            boolean show = !sec.isEmpty() || first || dragging;
            if (!show) {
                continue;
            }
            boolean label = !(first && (b.type == S_IF || b.type == S_REPEAT || b.type == S_FOREACH));
            if (label) {
                total += 10;
            }
            int f = flags;
            if (b.type == S_SHAPE || b.type == S_ART) {
                f |= s == 0 ? IN_HIT : IN_END;
            } else if (b.type == S_FOREACH) {
                f |= IN_EACH;
            }
            total += stack(sec, x + SEC_INDENT, y + total, f, record);
            total += 3;
        }
        return total + 2;
    }

    /** Lays out a statement list; returns its height (at least an empty slot). */
    private int stack(List<Blk> l, int x, int y, int flags, boolean record) {
        int h = 0;
        for (int i = 0; i < l.size(); i++) {
            if (record) {
                addSlot(l, i, x, y + h, flags);
            }
            h += layout(l.get(i), l, i, x, y + h, flags, record) + 1;
        }
        if (record) {
            addSlot(l, l.size(), x, y + h, flags);
        }
        return Math.max(h, 8);
    }

    private void addSlot(List<Blk> l, int index, int x, int y, int flags) {
        Slot s = new Slot();
        s.flags = flags;
        s.list = l;
        s.index = index;
        s.x = x;
        s.y = y;
        s.w = 120;
        slots.add(s);
    }

    private void relayout() {
        rects.clear();
        chips.clear();
        slots.clear();
        if (nodesOnly) {
            return;
        }
        for (Blk h : tree().scripts) {
            int x = Math.round(cl() + h.x - camX), y = Math.round(ct() + h.y - camY);
            int flags = h.type == H_KILL ? IN_KILL : h.type == H_HURT ? IN_HURT : 0;
            int hh = layout(h, tree().scripts, -1, x, y, flags, true);
            stack(h.body(), x, y + hh, flags, true);
        }
    }

    // ------------------------------------------------------------------ init / keys
    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        name = new GuiTextField(0, fontRenderer, 8 + Magic.SPELLS * 30 + 8, 6, 110, 12);
        name.setMaxStringLength(24);
        name.setText(names[slot]);
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
            if (!name.getText().equals(names[slot])) {
                names[slot] = name.getText();
                changed();
            }
            return;
        }
        if (key == Keyboard.KEY_ESCAPE) {
            if (popChip >= 0) {
                popChip = -1;
                return;
            }
            boolean any = false;
            for (boolean b : dirty) {
                any |= b;
            }
            if (any && !escArmed) {
                escArmed = true;
                say("\u00a7eUnsaved changes: press Esc again to close without saving.");
                return;
            }
            mc.displayGuiScreen(null);
            return;
        }
        escArmed = false;
        if (key == Keyboard.KEY_Z && isCtrlKeyDown() && !undo.isEmpty()) {
            trees[slot] = Tree.fromNBT(undo.remove(undo.size() - 1));
            changed();
        }
    }

    // ------------------------------------------------------------------ mouse
    @Override
    protected void mouseClicked(int mx, int my, int button) throws IOException {
        lastMX = mx;
        lastMY = my;
        name.mouseClicked(mx, my, button);
        relayout();
        if (popChip >= 0) {
            popupClick(mx, my, button);
            return;
        }
        if (topClick(mx, my) || rightPanelClick(mx, my)) {
            return;
        }
        if (nodesOnly) {
            return;
        }
        if (mx < LEFT + 6 && button == 0) {
            // palette
            int y = ct() + 2 - paletteScroll;
            for (int c = 0; c < 3; c++) {
                y += 10;
                for (BDef d : BLIST) {
                    if (d.cat != c) {
                        continue;
                    }
                    if (UiKit.in(mx, my, 6, y, LEFT - 6, 11) && my > ct()) {
                        drag = new Blk(d.id);
                        int first = elements()[0];
                        for (int k = 0; k < drag.v.length; k++) {
                            if (drag.def().chips[k].kind == ELEM && first >= 0) {
                                drag.v[k] = first; // your first element
                            }
                        }
                        fromPalette = true;
                        dragging = true;
                        grabDX = 4;
                        grabDY = 4;
                        UiKit.click();
                        return;
                    }
                    y += 13;
                }
                y += 4;
            }
            return;
        }
        if (!inCanvas(mx, my)) {
            return;
        }
        // chips first
        for (int i = chips.size() - 1; i >= 0; i--) {
            CRect c = chips.get(i);
            if (UiKit.in(mx, my, c.x, c.y, c.w, c.h) && button == 0) {
                popBlk = c.b;
                popChip = c.k;
                popX = c.x;
                popY = c.y + c.h + 1;
                UiKit.click();
                return;
            }
        }
        for (int i = rects.size() - 1; i >= 0; i--) {
            BRect r = rects.get(i);
            if (!UiKit.in(mx, my, r.x, r.y, r.w, r.h)) {
                continue;
            }
            if (button == 1) {
                snapshot();
                r.list.remove(r.index >= 0 ? r.index : r.list.indexOf(r.b));
                changed();
                return;
            }
            pressed = r.b;
            pressedList = r.list;
            pressedIndex = r.index;
            pressing = true;
            pressX = mx;
            pressY = my;
            grabDX = mx - r.x;
            grabDY = my - r.y;
            return;
        }
        if (button == 0) {
            panning = true;
        }
    }

    @Override
    protected void mouseClickMove(int mx, int my, int button, long time) {
        if (pressing && !dragging && (Math.abs(mx - pressX) > 3 || Math.abs(my - pressY) > 3)) {
            snapshot();
            dragging = true;
            if (pressed.def().cat == 0) {
                drag = pressed; // a hat moves its whole script
                dragFrom = null;
            } else {
                drag = pressed;
                dragFrom = pressedList;
                dragIndex = pressedIndex;
                pressedList.remove(pressedIndex);
            }
        } else if (panning) {
            camX -= mx - lastMX;
            camY -= my - lastMY;
        }
        if (dragging && drag != null && drag.def().cat == 0 && !fromPalette) {
            drag.x = Math.round(mx - grabDX - cl() + camX);
            drag.y = Math.round(my - grabDY - ct() + camY);
            changed();
        }
        lastMX = mx;
        lastMY = my;
    }

    @Override
    protected void mouseReleased(int mx, int my, int state) {
        if (dragging && drag != null) {
            relayout();
            boolean hat = drag.def().cat == 0;
            if (mx < LEFT + 6) {
                // dropped on the list: delete
                if (hat && !fromPalette) {
                    tree().scripts.remove(drag);
                }
                if (!fromPalette) {
                    changed();
                }
            } else if (hat) {
                if (fromPalette && inCanvas(mx, my)) {
                    snapshot();
                    drag.x = Math.round(mx - grabDX - cl() + camX);
                    drag.y = Math.round(my - grabDY - ct() + camY);
                    tree().scripts.add(drag);
                    changed();
                }
            } else {
                Slot best = null;
                int bd = 14;
                int dx = mx - grabDX, dy = my - grabDY;
                for (Slot s : slots) {
                    int d = Math.abs(dy - s.y);
                    if (d < bd && dx >= s.x - 30 && dx <= s.x + s.w) {
                        bd = d;
                        best = s;
                    }
                }
                if (best != null && inCanvas(mx, my)) {
                    if (fromPalette) {
                        snapshot();
                    }
                    if (tree().count() >= SpellGraph.MAX_BLOCKS) {
                        say("\u00a7cAt most " + SpellGraph.MAX_BLOCKS + " blocks.");
                    } else {
                        best.list.add(Math.min(best.index, best.list.size()), drag);
                        if (drag.type == S_SHAPE && drag.v[9] < 0 && (best.flags & (IN_HIT | IN_END)) != 0) {
                            // a cast dropped into 'on hit' / 'on end' starts where the parent hit / landed
                            drag.v[9] = (best.flags & IN_HIT) != 0 ? 3 : 4;
                        }
                        changed();
                    }
                } else if (!fromPalette && dragFrom != null) {
                    dragFrom.add(Math.min(dragIndex, dragFrom.size()), drag); // back where it was
                } else if (fromPalette) {
                    say("\u00a77Drop it under a 'when' block (between blocks or inside one).");
                }
            }
        }
        drag = null;
        dragFrom = null;
        dragging = false;
        pressing = false;
        panning = false;
        fromPalette = false;
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) {
            return;
        }
        int mx = Mouse.getEventX() * width / mc.displayWidth;
        if (mx < LEFT + 6) {
            paletteScroll = Math.max(0, paletteScroll + (wheel > 0 ? -12 : 12));
        } else {
            camY -= wheel > 0 ? 20 : -20;
        }
    }

    // ------------------------------------------------------------------ chip popup
    private List<int[]> popupOptions() {
        // {value, enabled}
        List<int[]> out = new ArrayList<>();
        Chip c = popBlk.def().chips[popChip];
        int flags = 0;
        for (CRect r : chips) {
            if (r.b == popBlk && r.k == popChip) {
                flags = r.flags;
            }
        }
        for (int v = c.min; v <= c.max; v++) {
            boolean ok = true;
            if (c.kind == ENT) {
                ok = entOk(v, flags);
            } else if (c.kind == PT) {
                ok = ptOk(v, flags);
            } else if (c.kind == ELEM || c.kind == ELEM2) {
                ok = v < 0 || learned(v);
            }
            out.add(new int[]{v, ok ? 1 : 0});
        }
        return out;
    }

    /** Is element id e in one of the player's element slots? */
    private boolean learned(int e) {
        int[] el = elements();
        for (int i = 0; i < Magic.MAX_ELEMENTS && i < el.length; i++) {
            if (el[i] == e) {
                return true;
            }
        }
        return false;
    }

    private static boolean entOk(int v, int f) {
        switch (v) {
            case 1:
            case 6: return (f & IN_HIT) != 0;
            case 2: return (f & IN_EACH) != 0;
            case 3: return (f & IN_KILL) != 0;
            case 4: return (f & IN_HURT) != 0;
            default: return true;
        }
    }

    private static boolean ptOk(int v, int f) {
        switch (v) {
            case 2:
            case 3:
            case 8: return (f & IN_HIT) != 0;
            case 4: return (f & IN_END) != 0;
            case 5: return (f & IN_EACH) != 0;
            case 6: return (f & IN_KILL) != 0;
            case 7: return (f & IN_HURT) != 0;
            default: return true;
        }
    }

    private String optionLabel(Chip c, int v) {
        switch (c.kind) {
            case ELEM:
            case ELEM2:
                if (v < 0) {
                    return "none";
                }
                String fu = "";
                if (c.kind == ELEM2 && popBlk.type == S_SHAPE) {
                    int f = SpellArts.fusion(popBlk.v[1], v);
                    fu = f >= 0 ? " \u00a76= " + SpellArts.FUSION[f] + "\u00a7r" : "";
                }
                return Magic.ELEMENT_NAME[v] + fu + (learned(v) ? "" : " (not learned)");
            case ENT:
                return ENT_NAME[v];
            case PT:
                return v < 0 ? "me (default)" : PT_NAME[v];
            case PREDK:
                return PRED[v];
            default:
                if (c.labels != null) {
                    String s = c.labels[v - c.min];
                    if (popBlk.type == S_ART) {
                        int[] ranks = GuiWeave.data.getIntArray("arts");
                        int rk = v < ranks.length ? ranks[v] : 0;
                        return s + (rk > 0 ? " " + new String[]{"I", "II", "III"}[Math.min(2, rk - 1)] : " (not learned)");
                    }
                    return s;
                }
                return String.valueOf(v);
        }
    }

    private int[] popupGeom(int n) {
        int rows = Math.max(4, Math.min(n, (height - popY - 8) / 11));
        int cols = (n + rows - 1) / rows;
        int cw = 0;
        Chip c = popBlk.def().chips[popChip];
        for (int v = c.min; v <= c.max; v++) {
            cw = Math.max(cw, fontRenderer.getStringWidth(optionLabel(c, v)) + 10);
        }
        int x = Math.min(popX, width - cols * cw - 4);
        int y = popY;
        if (y + rows * 11 + 4 > height) {
            y = Math.max(2, height - rows * 11 - 6);
        }
        return new int[]{x, y, rows, cols, cw};
    }

    private void popupClick(int mx, int my, int button) {
        Chip c = popBlk.def().chips[popChip];
        if (c.kind == NUM) {
            int[] g = stepperGeom();
            int[] steps = {-5, -1, 1, 5};
            for (int i = 0; i < 4; i++) {
                int bx = g[0] + (i < 2 ? i * 18 : 54 + (i - 2) * 18);
                if (UiKit.in(mx, my, bx, g[1], 16, 12)) {
                    snapshot();
                    popBlk.v[popChip] = Math.max(c.min, Math.min(c.max, popBlk.v[popChip] + steps[i]));
                    changed();
                    UiKit.click();
                    return;
                }
            }
            popChip = -1;
            return;
        }
        List<int[]> opts = popupOptions();
        int[] g = popupGeom(opts.size());
        for (int i = 0; i < opts.size(); i++) {
            int ox = g[0] + (i / g[2]) * g[4], oy = g[1] + 2 + (i % g[2]) * 11;
            if (UiKit.in(mx, my, ox, oy, g[4], 11)) {
                if (opts.get(i)[1] == 1) {
                    snapshot();
                    popBlk.v[popChip] = opts.get(i)[0];
                    changed();
                    UiKit.click();
                    popChip = -1;
                }
                return;
            }
        }
        popChip = -1;
    }

    private int[] stepperGeom() {
        return new int[]{Math.min(popX, width - 96), Math.min(popY, height - 16)};
    }

    // ------------------------------------------------------------------ top bar / right panel
    private boolean topClick(int mx, int my) {
        for (int t = 0; t < Magic.SPELLS; t++) {
            if (UiKit.in(mx, my, 8 + t * 30, 5, 28, 14)) {
                if (t >= open()) {
                    say("\u00a77Spell " + (t + 1) + " opens at " + Magic.SPELL_MAGIC[t] + " Magic.");
                } else if (pageMode) {
                    trees[t] = trees[slot];
                    names[t] = names[slot];
                    dirty[t] = true;
                    slot = t;
                } else {
                    slot = t;
                    nodesOnly = trees[t] == null;
                    name.setText(names[t]);
                    undo.clear();
                }
                UiKit.click();
                return true;
            }
        }
        return false;
    }

    private static final String[] BTN = {"Templates", "Print blueprint", "Node mode", "Save", "Revert", "Close"};
    private boolean templates;

    private int btnY(int i) {
        return height - BOTTOM - 8 - (BTN.length - i) * 16;
    }

    private boolean rightPanelClick(int mx, int my) {
        int px = width - RIGHT - 4;
        if (templates) {
            int tx = width / 2 - 70, ty = height / 2 - SpellBlocks.TEMPLATE.length * 7 - 8;
            for (int i = 0; i < SpellBlocks.TEMPLATE.length; i++) {
                if (UiKit.in(mx, my, tx + 4, ty + 18 + i * 14, 132, 13)) {
                    snapshot();
                    trees[slot] = SpellBlocks.template(i);
                    SpellBlocks.fitElements(trees[slot], elements());
                    names[slot] = SpellBlocks.TEMPLATE[i];
                    name.setText(names[slot]);
                    nodesOnly = false;
                    camX = -8;
                    camY = -8;
                    changed();
                    UiKit.click();
                }
            }
            templates = false;
            return true;
        }
        if (mx < px) {
            return false;
        }
        for (int i = 0; i < BTN.length; i++) {
            if (!UiKit.in(mx, my, px, btnY(i), RIGHT, 14)) {
                continue;
            }
            UiKit.click();
            switch (i) {
                case 0:
                    templates = true;
                    break;
                case 1:
                    if (dirty[slot]) {
                        say("\u00a7eSave first, then print the blueprint (1 paper).");
                    } else {
                        SkillNet.NET.sendToServer(new SkillNet.MsgAct(6, slot, ""));
                    }
                    break;
                case 2: {
                    SpellGraph g = nodesOnly ? GuiWeave.savedGraph(slot) : compiled();
                    mc.displayGuiScreen(GuiWeave.editing(slot, g, dirty[slot] && !nodesOnly));
                    break;
                }
                case 3:
                    save();
                    break;
                case 4:
                    loadSlot(slot);
                    dirty[slot] = false;
                    name.setText(names[slot]);
                    break;
                default:
                    mc.displayGuiScreen(null);
                    break;
            }
            return true;
        }
        if (nodesOnly && UiKit.in(mx, my, px, ct() + 60, RIGHT, 14)) {
            // start this slot over as blocks
            trees[slot] = SpellBlocks.template(0);
            nodesOnly = false;
            changed();
            UiKit.click();
            return true;
        }
        return true;
    }

    private void save() {
        if (nodesOnly) {
            say("\u00a77This spell is edited in Node mode.");
            return;
        }
        if (slot >= open()) {
            say("\u00a77Spell " + (slot + 1) + " is locked.");
            return;
        }
        SpellGraph g = compiled();
        String bad = g.check(magic(), Math.max(1, GuiWeave.data.getInteger("runes")));
        if (bad != null) {
            say("\u00a7c" + bad);
            return;
        }
        SkillNet.NET.sendToServer(new SkillNet.MsgWeaveSave(slot, g.toNBT()));
        dirty[slot] = false;
        pageMode = false;
    }

    // ------------------------------------------------------------------ drawing
    @Override
    public void drawScreen(int mx, int my, float pt) {
        drawRect(0, 0, width, height, 0xE0100D0A);
        relayout();
        // top bar
        for (int t = 0; t < Magic.SPELLS; t++) {
            boolean locked = t >= open(), cur = t == slot;
            UiKit.rect(8 + t * 30, 5, 36 + t * 30, 19, locked ? 0xFF120F0B : cur ? 0xFF3A2E18 : 0xFF1E1912);
            if (cur) {
                UiKit.rect(8 + t * 30, 18, 36 + t * 30, 19, 0xFFE0B040);
            }
            String lab = locked ? "\u00a78" + Magic.SPELL_MAGIC[t] + "M" : (t + 1) + (dirty[t] ? "*" : "");
            fontRenderer.drawStringWithShadow(lab, 22 + t * 30 - fontRenderer.getStringWidth(lab) / 2, 8, cur ? UiKit.GOLD_TEXT : UiKit.MUTED);
        }
        name.drawTextBox();
        String title = pageMode ? "\u00a7dSPELL BLUEPRINT" : "\u00a7lSPELL WEAVE";
        int tw = fontRenderer.getStringWidth(title);
        if (width - 8 - tw > 8 + Magic.SPELLS * 30 + 8 + 116) {
            fontRenderer.drawStringWithShadow(title, width - 8 - tw, 8, UiKit.GOLD_TEXT);
        }
        SpellGraph g = nodesOnly ? GuiWeave.savedGraph(slot) : compiled();
        int lv = GuiWeave.masteryOf(slot);
        String[] roman = {"I", "II", "III", "IV", "V"};
        if (g != null) {
            int max = SpellGraph.maxNodes(magic());
            int cost = Math.max(1, Math.round(g.cost() * (1F - 0.05F * (lv - 1))));
            String info = "Blocks " + (g.used() > max ? "\u00a7c" : "\u00a7f") + g.used() + "\u00a77/" + max + "   Mana \u00a7b" + cost
                    + "\u00a77   Cooldown \u00a7f" + String.format("%.1f", g.cooldown() / 20F) + "s\u00a77   Mastery \u00a76" + roman[lv - 1];
            fontRenderer.drawStringWithShadow(UiKit.fit(fontRenderer, "", info, width - 16), 8, 22, 0xB0A890);
        }
        drawPalette(mx, my);
        // canvas
        UiKit.inset(cl() - 1, ct() - 1, cw() + 2, ch() + 2, 0xFF0C0B0A);
        scissor(cl(), ct(), cw(), ch());
        if (nodesOnly) {
            fontRenderer.drawSplitString("\u00a77This spell was edited in Node mode. Open it with Node mode, or start it over as blocks"
                    + " (button on the right).", cl() + 8, ct() + 8, cw() - 16, 0xFFFFFF);
        } else {
            if (dragging) {
                for (Slot s : slots) {
                    UiKit.rect(s.x, s.y - 1, s.x + 40, s.y, 0x40FFFFFF);
                }
            }
            for (BRect r : rects) {
                drawBlock(r);
            }
            for (CRect c : chips) {
                drawChip(c, mx, my);
            }
            if (tree().scripts.isEmpty()) {
                String h = "Drag \"when I cast\" here, then blocks under it.";
                fontRenderer.drawString(h, cl() + cw() / 2 - fontRenderer.getStringWidth(h) / 2, ct() + ch() / 2, UiKit.FAINT);
            }
            // insertion marker
            if (dragging && drag != null && drag.def().cat != 0) {
                Slot best = null;
                int bd = 14;
                for (Slot s : slots) {
                    int d = Math.abs(my - grabDY - s.y);
                    if (d < bd && mx - grabDX >= s.x - 30 && mx - grabDX <= s.x + s.w) {
                        bd = d;
                        best = s;
                    }
                }
                if (best != null) {
                    UiKit.rect(best.x, best.y - 2, best.x + 90, best.y, 0xFFFFE070);
                }
            }
        }
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        drawRight(mx, my, g);
        // dragged block follows the mouse (drawn on top, without its sections)
        if (dragging && drag != null) {
            BRect r = new BRect();
            r.b = drag;
            r.x = mx - grabDX;
            r.y = my - grabDY;
            List<Object> tk = tokens(drag);
            r.w = Math.min(MAXW + 8, fontRenderer.getStringWidth(SpellBlocks.chipText(drag, -1, elements())) + 12);
            r.h = HEAD + 2;
            GlStateManager.pushMatrix();
            GlStateManager.translate(0, 0, 300);
            int col = 0xFF000000 | CAT_RGB[drag.def().cat];
            drawRect(r.x, r.y, r.x + r.w, r.y + r.h, 0xE0000000 | darker(CAT_RGB[drag.def().cat]));
            drawRect(r.x, r.y, r.x + r.w, r.y + 1, col);
            fontRenderer.drawStringWithShadow(UiKit.fit(fontRenderer, "", SpellBlocks.chipText(drag, -1, elements()), r.w - 6), r.x + 3, r.y + 3, 0xFFFFFF);
            GlStateManager.popMatrix();
            if (mx < LEFT + 6 && !fromPalette) {
                drawHoveringText(java.util.Arrays.asList("\u00a7cDrop to delete"), mx, my);
            }
        }
        if (popChip >= 0) {
            drawPopup(mx, my);
        }
        if (templates) {
            int tx2 = width / 2 - 70, ty = height / 2 - SpellBlocks.TEMPLATE.length * 7 - 8;
            UiKit.panel(tx2, ty, 140, 22 + SpellBlocks.TEMPLATE.length * 14);
            fontRenderer.drawStringWithShadow("Templates", tx2 + 8, ty + 6, UiKit.GOLD_TEXT);
            for (int i = 0; i < SpellBlocks.TEMPLATE.length; i++) {
                boolean h = UiKit.in(mx, my, tx2 + 4, ty + 18 + i * 14, 132, 13);
                UiKit.button(fontRenderer, tx2 + 4, ty + 18 + i * 14, 132, 13, SpellBlocks.TEMPLATE[i], true, h ? 1 : 0, 0xB070FF);
            }
        }
        super.drawScreen(mx, my, pt);
        if (!dragging && popChip < 0 && !templates) {
            tooltips(mx, my);
        }
    }

    private void tooltips(int mx, int my) {
        if (mx < LEFT + 6) {
            int y = ct() + 2 - paletteScroll;
            for (int c = 0; c < 3; c++) {
                y += 10;
                for (BDef d : BLIST) {
                    if (d.cat != c) {
                        continue;
                    }
                    if (UiKit.in(mx, my, 6, y, LEFT - 6, 11) && my > ct()) {
                        List<String> t = new ArrayList<>();
                        t.addAll(fontRenderer.listFormattedStringToWidth("\u00a77" + d.help, 180));
                        t.add("\u00a78Drag it into a script.");
                        drawHoveringText(t, mx, my);
                    }
                    y += 13;
                }
                y += 4;
            }
        } else if (inCanvas(mx, my)) {
            for (CRect c : chips) {
                if (UiKit.in(mx, my, c.x, c.y, c.w, c.h)) {
                    return;
                }
            }
            for (int i = rects.size() - 1; i >= 0; i--) {
                BRect r = rects.get(i);
                if (UiKit.in(mx, my, r.x, r.y, r.w, r.h)) {
                    List<String> t = new ArrayList<>(fontRenderer.listFormattedStringToWidth("\u00a77" + r.b.def().help, 180));
                    t.add("\u00a78Drag to move, right-click to delete.");
                    drawHoveringText(t, mx, my);
                    return;
                }
            }
        }
    }

    private void drawPalette(int mx, int my) {
        UiKit.inset(4, ct() - 1, LEFT + 2, ch() + 2, 0xFF0C0B0A);
        scissor(5, ct(), LEFT, ch());
        int y = ct() + 2 - paletteScroll;
        for (int c = 0; c < 3; c++) {
            fontRenderer.drawString(CAT[c], 8, y + 1, CAT_RGB[c]);
            y += 10;
            for (BDef d : BLIST) {
                if (d.cat != c) {
                    continue;
                }
                boolean h = UiKit.in(mx, my, 6, y, LEFT - 6, 11) && my > ct();
                UiKit.rect(6, y, LEFT, y + 11, h ? 0xFF000000 | CAT_RGB[c] : 0xFF000000 | darker(CAT_RGB[c]));
                fontRenderer.drawString(UiKit.fit(fontRenderer, "", paletteName(d), LEFT - 12), 9, y + 2, 0xFFFFFF);
                y += 13;
            }
            y += 4;
        }
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    private static String paletteName(BDef d) {
        switch (d.id) {
            case H_EVERY: return "every X s";
            case S_SHAPE: return "cast a shape";
            case S_ART: return "arcane art";
            case S_IF: return "if ... else";
            case S_REPEAT: return "repeat";
            case S_FOREACH: return "for each enemy";
            case S_WAIT: return "wait";
            case S_STATUS: return "apply status";
            case S_DAMAGE: return "damage";
            case S_HEAL: return "heal";
            case S_SHIELD: return "shield";
            case S_BLINK: return "blink to";
            case S_PUSH: return "push / pull";
            case S_ZONE: return "zone";
            case S_MANA: return "restore mana";
            case S_SPELL: return "cast my spell";
            default: return d.text;
        }
    }

    private void drawBlock(BRect r) {
        Blk b = r.b;
        BDef d = b.def();
        int base = CAT_RGB[d.cat];
        int col = 0xFF000000 | base, dark = 0xFF000000 | darker(base);
        // sections: a rail on the left down to the end of the block
        int total = layoutHeight(b);
        if (d.cat != 0 && total > r.h + 1) {
            drawRect(r.x, r.y, r.x + SEC_INDENT - 2, r.y + total - 2, dark);
            drawRect(r.x, r.y, r.x + 1, r.y + total - 2, col);
            drawRect(r.x, r.y + total - 5, r.x + 40, r.y + total - 2, dark);
            drawRect(r.x, r.y + total - 3, r.x + 40, r.y + total - 2, col);
            // section labels
            int yy = r.y + r.h;
            for (int s = 0; s < d.sections.length; s++) {
                List<Blk> sec = b.sec.get(s);
                boolean first = s == 0 && d.cat == 1;
                if (sec.isEmpty() && !first && !dragging) {
                    continue;
                }
                boolean label = !(first && (b.type == S_IF || b.type == S_REPEAT || b.type == S_FOREACH));
                if (label) {
                    drawRect(r.x, yy, r.x + fontRenderer.getStringWidth(d.sections[s]) + 8, yy + 10, dark);
                    fontRenderer.drawString(d.sections[s], r.x + 4, yy + 1, 0xFFE0D8C8);
                    yy += 10;
                }
                yy += stackHeight(sec, b, s) + 3;
            }
        }
        drawRect(r.x, r.y, r.x + r.w, r.y + r.h, dark);
        drawRect(r.x, r.y, r.x + r.w, r.y + 1, col);
        drawRect(r.x, r.y + r.h - 1, r.x + r.w, r.y + r.h, 0xFF000000);
        // words
        List<Object> tk = tokens(b);
        int lineW = 0, line = 0;
        for (Object o : tk) {
            int w = o instanceof String ? fontRenderer.getStringWidth((String) o) : chipW(b, -1 - (Integer) o);
            if (lineW > 0 && lineW + w > MAXW) {
                lineW = 0;
                line++;
            }
            if (o instanceof String) {
                fontRenderer.drawStringWithShadow((String) o, r.x + 4 + lineW, r.y + 3 + line * HEAD, 0xFFFFFF);
            }
            lineW += w + 4;
        }
    }

    private int layoutHeight(Blk b) {
        return layout(b, null, 0, 0, 0, 0, false);
    }

    private int stackHeight(List<Blk> l, Blk parent, int s) {
        return stack(l, 0, 0, 0, false);
    }

    private void drawChip(CRect c, int mx, int my) {
        Chip ch = c.b.def().chips[c.k];
        boolean h = UiKit.in(mx, my, c.x, c.y, c.w, c.h);
        String lab = chipLabel(c.b, c.k);
        boolean empty = SpellBlocks.value(c.b, c.k, elements()).isEmpty();
        boolean bad = ch.kind == ENT && !entOk(c.b.v[c.k], c.flags) || ch.kind == PT && !ptOk(c.b.v[c.k], c.flags)
                || (ch.kind == ELEM || ch.kind == ELEM2) && c.b.v[c.k] >= 0 && !learned(c.b.v[c.k]);
        int edge = bad ? 0xFFE04040 : ch.kind == ENT || ch.kind == PT ? 0xFF6CCB5A : ch.kind == ELEM || ch.kind == ELEM2 ? 0xFFF0D060
                : ch.kind == NUM ? 0xFF9A9080 : 0xFFC0A0FF;
        drawRect(c.x, c.y, c.x + c.w, c.y + c.h, edge);
        drawRect(c.x + 1, c.y + 1, c.x + c.w - 1, c.y + c.h - 1, h ? 0xFF3A342C : 0xFF181512);
        fontRenderer.drawString(lab, c.x + 4, c.y + 2, empty ? 0xFF8A8070 : 0xFFFFFFFF);
    }

    private void drawPopup(int mx, int my) {
        Chip c = popBlk.def().chips[popChip];
        GlStateManager.pushMatrix();
        GlStateManager.translate(0, 0, 300);
        if (c.kind == NUM) {
            int[] g = stepperGeom();
            drawRect(g[0] - 2, g[1] - 2, g[0] + 92, g[1] + 14, 0xF0201C18);
            String[] lab = {"-5", "-1", "+1", "+5"};
            for (int i = 0; i < 4; i++) {
                int bx = g[0] + (i < 2 ? i * 18 : 54 + (i - 2) * 18);
                boolean h = UiKit.in(mx, my, bx, g[1], 16, 12);
                UiKit.button(fontRenderer, bx, g[1], 16, 12, lab[i], true, h ? 1 : 0, 0xE0B040);
            }
            String v = String.valueOf(popBlk.v[popChip]);
            fontRenderer.drawStringWithShadow(v, g[0] + 45 - fontRenderer.getStringWidth(v) / 2, g[1] + 2, 0xFFFFFF);
        } else {
            List<int[]> opts = popupOptions();
            int[] g = popupGeom(opts.size());
            drawRect(g[0] - 1, g[1] - 1, g[0] + g[3] * g[4] + 1, g[1] + g[2] * 11 + 5, 0xFF6CCB5A);
            drawRect(g[0], g[1], g[0] + g[3] * g[4], g[1] + g[2] * 11 + 4, 0xF81C1916);
            for (int i = 0; i < opts.size(); i++) {
                int ox = g[0] + (i / g[2]) * g[4], oy = g[1] + 2 + (i % g[2]) * 11;
                boolean cur = popBlk.v[popChip] == opts.get(i)[0], on = opts.get(i)[1] == 1;
                boolean h = UiKit.in(mx, my, ox, oy, g[4], 11);
                if (cur || h && on) {
                    drawRect(ox + 1, oy, ox + g[4] - 1, oy + 11, cur ? 0xFF3A5A3A : 0xFF33302A);
                }
                fontRenderer.drawString(UiKit.fit(fontRenderer, "", optionLabel(c, opts.get(i)[0]), g[4] - 6), ox + 4, oy + 2,
                        on ? 0xFFFFFF : 0x5A5048);
            }
        }
        GlStateManager.popMatrix();
    }

    private void drawRight(int mx, int my, SpellGraph g) {
        int px = width - RIGHT - 4;
        UiKit.inset(px - 2, ct() - 1, RIGHT + 4, ch() + 2, 0xFF0C0B0A);
        fontRenderer.drawStringWithShadow(UiKit.fit(fontRenderer, "", names[slot], RIGHT - 4), px + 2, ct() + 3, UiKit.GOLD_TEXT);
        int top = ct() + 16, bottom = btnY(0) - 6;
        if (nodesOnly) {
            fontRenderer.drawSplitString("\u00a77Made in Node mode.", px + 2, top, RIGHT - 4, 0xFFFFFF);
            boolean h = UiKit.in(mx, my, px, ct() + 60, RIGHT, 14);
            UiKit.button(fontRenderer, px, ct() + 60, RIGHT, 14, "Start over as blocks", true, h ? 1 : 0, 0xE0B040);
        } else {
            fontRenderer.drawString("Reads like:", px + 2, top, 0xC8A050);
            String text = SpellBlocks.describe(tree(), elements());
            int y = top + 11;
            List<String> lines = fontRenderer.listFormattedStringToWidth("\u00a77" + text, RIGHT - 4);
            for (int i = 0; i < lines.size(); i++) {
                if (y + 9 > bottom) {
                    break;
                }
                boolean last = y + 18 > bottom && i < lines.size() - 1;
                fontRenderer.drawString(last ? UiKit.fit(fontRenderer, "", lines.get(i), RIGHT - 16) + "..." : lines.get(i), px + 2, y, 0xFFFFFF);
                y += 9;
                if (last) {
                    break;
                }
            }
        }
        for (int i = 0; i < BTN.length; i++) {
            boolean h = UiKit.in(mx, my, px, btnY(i), RIGHT, 14);
            boolean on = i != 3 || dirty[slot];
            int accent = i == 1 ? 0xB070FF : i == 3 ? 0x7FC83A : i == 5 ? 0xE05050 : 0xE0B040;
            UiKit.button(fontRenderer, px, btnY(i), RIGHT, 14, i == 3 && !dirty[slot] ? "Saved" : BTN[i], on, h ? 1 : 0, accent);
        }
        // message line above the buttons (never over other text)
        if (Minecraft.getSystemTime() < msgUntil || g != null && g.check(magic(), Math.max(1, GuiWeave.data.getInteger("runes"))) != null) {
            String m = Minecraft.getSystemTime() < msgUntil ? msg
                    : "\u00a7c" + g.check(magic(), Math.max(1, GuiWeave.data.getInteger("runes")));
            List<String> ml = fontRenderer.listFormattedStringToWidth(m, cw() - 8);
            int y = ct() + ch() - 4 - ml.size() * 9;
            drawRect(cl(), y - 2, cl() + cw(), ct() + ch(), 0xE0100D0A);
            for (String l : ml) {
                fontRenderer.drawString(l, cl() + 4, y, 0xFFFFFF);
                y += 9;
            }
        }
    }

    private void scissor(int x, int y, int w, int h) {
        int sf = new ScaledResolution(mc).getScaleFactor();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(x * sf, mc.displayHeight - (y + h) * sf, Math.max(0, w * sf), Math.max(0, h * sf));
    }

    private static int darker(int rgb) {
        int r = (rgb >> 16 & 255) * 2 / 5, g = (rgb >> 8 & 255) * 2 / 5, b = (rgb & 255) * 2 / 5;
        return r << 16 | g << 8 | b;
    }
}
