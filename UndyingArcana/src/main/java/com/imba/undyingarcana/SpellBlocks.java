package com.imba.undyingarcana;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import static com.imba.undyingarcana.SpellGraph.*;

/**
 * Block view of a spell (Scratch style): scripts start with a "when" block and hold statements; control blocks have
 * sections (then / else, loop body, a cast's on hit / on end). Values are chips (dropdowns): who ("me", "the target")
 * and where ("aim point", "hit point"). A tree compiles into a SpellGraph, which is what the server runs; the tree is
 * kept next to the graph (tag "blk") so the spell can be edited as blocks again.
 */
public final class SpellBlocks {
    // chip kinds
    public static final int ENUM = 0, NUM = 1, ENT = 2, PT = 3, ELEM = 4, ELEM2 = 5, PREDK = 6;
    // hats
    public static final int H_CAST = 0, H_KILL = 1, H_EVERY = 2, H_HURT = 3;
    // statements
    public static final int S_SHAPE = 10, S_ART = 11, S_IF = 12, S_REPEAT = 13, S_FOREACH = 14, S_WAIT = 15, S_STATUS = 16,
            S_DAMAGE = 17, S_HEAL = 18, S_SHIELD = 19, S_BLINK = 20, S_PUSH = 21, S_ZONE = 22, S_MANA = 23, S_SPELL = 24;

    public static final String[] ENT_NAME = {"me", "the target", "each enemy", "the victim", "the attacker", "nearest enemy",
            "enemy nearest the target"};
    public static final String[] PT_NAME = {"aim point", "my position", "the target", "hit point", "end point", "each enemy",
            "the victim", "the attacker", "above the target", "above aim point", "in front of me"};
    public static final String[] PRED = {"is Burning", "is Chilled", "is Frozen", "is Shocked", "is Wet", "is Drained", "is Staggered",
            "is Exposed", "is Radiant", "is Poisoned", "HP below %", "HP above %", "is a boss", "has N+ enemies near", "is closer than",
            "it is night", "it is raining", "I'm sneaking", "I'm in water", "I'm on the ground", "my HP is below half", "my mana above %",
            "chance %", "rings at least"};
    /** Predicates that use a number, and those that ignore the subject (world / self / chance). */
    static boolean predNum(int p) {
        return p == 10 || p == 11 || p == 13 || p == 14 || p == 21 || p == 22 || p == 23;
    }

    static boolean predWorld(int p) {
        return p >= 15;
    }

    /** A chip of a block: kind, label for its value list, range. */
    public static final class Chip {
        public final int kind, min, max, def;
        public final String[] labels;

        Chip(int kind, int min, int max, int def, String[] labels) {
            this.kind = kind;
            this.min = min;
            this.max = max;
            this.def = def;
            this.labels = labels;
        }
    }

    /** A block type: text with {n} chip markers, chips, section names. */
    public static final class BDef {
        public final int id, cat;
        public final String text;
        public final Chip[] chips;
        public final String[] sections;
        public final String help;

        BDef(int id, int cat, String text, String help, String[] sections, Chip... chips) {
            this.id = id;
            this.cat = cat;
            this.text = text;
            this.help = help;
            this.sections = sections;
            this.chips = chips;
        }
    }

    public static final BDef[] BDEFS = new BDef[32];
    public static final List<BDef> BLIST = new ArrayList<>();

    private static Chip num(int min, int max, int def) {
        return new Chip(NUM, min, max, def, null);
    }

    private static Chip en(int def, String... labels) {
        return new Chip(ENUM, 0, labels.length - 1, def, labels);
    }

    private static void bdef(BDef d) {
        BDEFS[d.id] = d;
        BLIST.add(d);
    }

    private static final String[] NONE = {};

    static {
        String[] runes = new String[Magic.RUNE.length + 1];
        runes[0] = "no rune";
        System.arraycopy(Magic.RUNE, 0, runes, 1, Magic.RUNE.length);
        Chip ent = new Chip(ENT, 0, ENT_NAME.length - 1, 1, ENT_NAME), pt = new Chip(PT, 0, PT_NAME.length - 1, 0, PT_NAME);
        Chip el = new Chip(ELEM, 0, Magic.ELEMENT.length - 1, 0, Magic.ELEMENT_NAME);
        bdef(new BDef(H_CAST, 0, "when I cast {0}", "Runs when you cast the spell. Instant fires at once; with rings you hold the key"
                + " and a ring forms every 0.6 s (+25% damage, +10% size, +15% mana each).", NONE,
                en(0, "(instant)", "(charge 1 ring)", "(charge up to 2 rings)", "(charge up to 3 rings)", "(charge up to 4 rings)",
                        "(charge up to 5 rings)")));
        bdef(new BDef(H_KILL, 0, "when it kills", "Runs when an enemy this spell hurt dies.", NONE));
        bdef(new BDef(H_EVERY, 0, "every {0} s, {1} times", "Runs again and again after the cast.", NONE,
                en(1, "0.5", "1", "1.5", "2", "3", "5"), num(1, 10, 3)));
        bdef(new BDef(H_HURT, 0, "when I get hurt", "A ward: runs by itself when you are hit (cooldown and mana of this spell).", NONE));
        bdef(new BDef(S_SHAPE, 2, "cast {0} of {1} {2} power {3} size {4} speed {5} {6} {7} {9} {8}",
                "Casts a shape. Put blocks in 'on hit' (runs for each enemy hit) or 'on end' (where it lands). 'from' sets where it starts.",
                new String[]{"on hit", "on end"}, en(0, Magic.SHAPE), el, new Chip(ELEM2, -1, Magic.ELEMENT.length - 1, -1, null),
                num(0, 10, 5), num(0, 10, 3), num(0, 10, 3), new Chip(ENUM, -1, Magic.RUNE.length - 1, -1, runes),
                new Chip(ENUM, -1, Magic.RUNE.length - 1, -1, runes), pt, new Chip(PT, -1, PT_NAME.length - 1, -1, PT_NAME)));
        bdef(new BDef(S_ART, 2, "arcane art {0} at {1}", "One of your learned Arcane Arts.", new String[]{"on hit", "on end"},
                en(0, SpellArts.ART), pt));
        bdef(new BDef(S_IF, 1, "if {0} {1} {2}", "Runs 'then' when it is true, 'else' when not.", new String[]{"then", "else"},
                ent, new Chip(PREDK, 0, PRED.length - 1, 4, PRED), num(1, 100, 30)));
        bdef(new BDef(S_REPEAT, 1, "repeat {0} times, every {1} ticks", "Runs the inside several times.", new String[]{"do"},
                num(1, 8, 3), num(0, 20, 4)));
        bdef(new BDef(S_FOREACH, 1, "for each enemy near {0} in {1} blocks, max {2}", "Runs the inside once per enemy ('each enemy').",
                new String[]{"do"}, pt, num(1, 12, 6), num(1, 10, 3)));
        bdef(new BDef(S_WAIT, 1, "wait {0} s", "Waits, then the blocks below continue.", NONE, en(2, "0.25", "0.5", "1", "1.5", "2", "3", "5")));
        bdef(new BDef(S_STATUS, 2, "apply {0} status to {1}", "Puts an element status on someone.", NONE, el, ent));
        bdef(new BDef(S_DAMAGE, 2, "damage {0} by {1}", "Magic damage (grows with Magic).", NONE, ent, num(1, 20, 4)));
        bdef(new BDef(S_HEAL, 2, "heal {0} by {1}", "Heals (never enemies).", NONE, new Chip(ENT, 0, ENT_NAME.length - 1, 0, ENT_NAME), num(1, 20, 4)));
        bdef(new BDef(S_SHIELD, 2, "shield {0}, strength {1}, {2} s", "Absorption hearts.", NONE,
                new Chip(ENT, 0, ENT_NAME.length - 1, 0, ENT_NAME), num(1, 4, 1), num(2, 20, 8)));
        bdef(new BDef(S_BLINK, 2, "blink to {0}, max {1} blocks", "Teleports you.", NONE, pt, num(2, 16, 8)));
        bdef(new BDef(S_PUSH, 2, "push {0} away from {1}, strength {2}", "Negative strength pulls.", NONE, ent,
                new Chip(PT, 0, PT_NAME.length - 1, 1, PT_NAME), num(-10, 10, 5)));
        bdef(new BDef(S_ZONE, 2, "{0} zone at {1}, {2} blocks, {3} s", "A field that hurts enemies every 0.5 s.", NONE, el, pt,
                num(1, 6, 3), num(1, 10, 4)));
        bdef(new BDef(S_MANA, 2, "restore {0} mana", "At most 30 per cast.", NONE, num(1, 20, 5)));
        bdef(new BDef(S_SPELL, 2, "cast my spell {0} from {1}", "Casts another of your spells there (free, once per cast).", NONE,
                num(1, 6, 1), new Chip(PT, 0, PT_NAME.length - 1, 3, PT_NAME)));
    }

    // ------------------------------------------------------------------ tree
    public static final class Blk {
        public int type;
        public int[] v;
        public final List<List<Blk>> sec = new ArrayList<>();
        public int x, y; // hats only: script position

        public Blk(int type) {
            this.type = type;
            BDef d = BDEFS[type];
            v = new int[d.chips.length];
            for (int i = 0; i < v.length; i++) {
                v[i] = d.chips[i].def;
            }
            for (int i = 0; i < d.sections.length; i++) {
                sec.add(new ArrayList<>());
            }
        }

        public BDef def() {
            return BDEFS[type];
        }

        /** Hats keep their statements in section 0 (the stack under them). */
        public List<Blk> body() {
            if (sec.isEmpty()) {
                sec.add(new ArrayList<>());
            }
            return sec.get(0);
        }

        public Blk copy() {
            return read(write(this));
        }
    }

    /** A spell as blocks: its scripts (each a hat with a stack). */
    public static final class Tree {
        public final List<Blk> scripts = new ArrayList<>();

        public int count() {
            int n = 0;
            for (Blk h : scripts) {
                n += 1 + count(h.body());
            }
            return n;
        }

        private static int count(List<Blk> l) {
            int n = 0;
            for (Blk b : l) {
                n++;
                if (b.def().cat != 0) {
                    for (List<Blk> s : b.sec) {
                        n += count(s);
                    }
                }
            }
            return n;
        }

        public NBTTagCompound toNBT() {
            NBTTagCompound t = new NBTTagCompound();
            NBTTagList l = new NBTTagList();
            for (Blk b : scripts) {
                l.appendTag(write(b));
            }
            t.setTag("s", l);
            return t;
        }

        public static Tree fromNBT(NBTTagCompound t) {
            Tree tr = new Tree();
            NBTTagList l = t.getTagList("s", 10);
            for (int i = 0; i < l.tagCount() && i < 12; i++) {
                Blk b = read(l.getCompoundTagAt(i));
                if (b != null && b.def().cat == 0) {
                    tr.scripts.add(b);
                }
            }
            return tr;
        }
    }

    static NBTTagCompound write(Blk b) {
        NBTTagCompound c = new NBTTagCompound();
        c.setByte("t", (byte) b.type);
        c.setIntArray("v", b.v);
        c.setShort("x", (short) b.x);
        c.setShort("y", (short) b.y);
        NBTTagList secs = new NBTTagList();
        for (List<Blk> s : b.sec) {
            NBTTagList one = new NBTTagList();
            for (Blk k : s) {
                one.appendTag(write(k));
            }
            NBTTagCompound w = new NBTTagCompound();
            w.setTag("b", one);
            secs.appendTag(w);
        }
        c.setTag("sec", secs);
        return c;
    }

    static Blk read(NBTTagCompound c) {
        return read(c, 0);
    }

    private static Blk read(NBTTagCompound c, int depth) {
        int t = c.getByte("t");
        if (t < 0 || t >= BDEFS.length || BDEFS[t] == null || depth > 12) {
            return null;
        }
        Blk b = new Blk(t);
        int[] v = c.getIntArray("v");
        BDef d = b.def();
        for (int i = 0; i < b.v.length && i < v.length; i++) {
            b.v[i] = Math.max(d.chips[i].min, Math.min(d.chips[i].max, v[i]));
        }
        b.x = c.getShort("x");
        b.y = c.getShort("y");
        NBTTagList secs = c.getTagList("sec", 10);
        int want = d.cat == 0 ? 1 : d.sections.length;
        b.sec.clear();
        for (int i = 0; i < want; i++) {
            List<Blk> l = new ArrayList<>();
            if (i < secs.tagCount()) {
                NBTTagList one = secs.getCompoundTagAt(i).getTagList("b", 10);
                for (int k = 0; k < one.tagCount() && k < 40; k++) {
                    Blk x = read(one.getCompoundTagAt(k), depth + 1);
                    if (x != null && x.def().cat != 0) {
                        l.add(x);
                    }
                }
            }
            b.sec.add(l);
        }
        return b;
    }

    // ------------------------------------------------------------------ compile
    /** Where values come from in the current place of the tree. */
    private static final class Ctx {
        final Map<Integer, Integer> ent = new HashMap<>(); // ENT index -> node * 16 + pin
        final Map<Integer, Integer> pt = new HashMap<>();  // PT index -> node * 16 + pin

        Ctx copy() {
            Ctx c = new Ctx();
            c.ent.putAll(ent);
            c.pt.putAll(pt);
            return c;
        }
    }

    private static final class Out {
        final SpellGraph g = new SpellGraph();
        int row;
        int caster = -1, aim = -1;

        int node(int type, int depth) {
                g.add(type, 20 + depth * 150, 10 + row * 46);
            row++;
            return g.nodes.size() - 1;
        }

        int value(int type) {
            g.add(type, -140, 10 + row * 30);
            return g.nodes.size() - 1;
        }
    }

    public static SpellGraph compile(Tree t, String name) {
        Out o = new Out();
        o.g.name = name;
        for (Blk h : t.scripts) {
            Ctx c = new Ctx();
            int ev;
            switch (h.type) {
                case H_KILL:
                    ev = o.node(ON_KILL, 0);
                    c.ent.put(3, ev * 16 + 1);
                    c.pt.put(6, ev * 16 + 2);
                    break;
                case H_EVERY: {
                    ev = o.node(EVERY, 0);
                    int[] tenths = {5, 10, 15, 20, 30, 50};
                    o.g.nodes.get(ev).p[0] = tenths[Math.max(0, Math.min(5, h.v[0]))];
                    o.g.nodes.get(ev).p[1] = h.v[1];
                    break;
                }
                case H_HURT:
                    ev = o.node(ON_HURT, 0);
                    c.ent.put(4, ev * 16 + 1);
                    break;
                default:
                    ev = o.node(ON_CAST, 0);
                    o.g.nodes.get(ev).p[0] = Math.max(0, Math.min(5, h.v.length > 0 ? h.v[0] : 0));
                    break;
            }
            list(o, h.body(), ev * 16, c, 1);
        }
        o.g.blocks = t.toNBT();
        return o.g;
    }

    /** Compiles a statement list; 'prev' = node * 16 + exec output to continue from. */
    private static void list(Out o, List<Blk> l, int prev, Ctx c, int depth) {
        for (Blk b : l) {
            prev = stmt(o, b, prev, c, depth);
            if (prev < 0) {
                return;
            }
        }
    }

    /** Wires the exec 'prev' into node n's 'do' (input 0). */
    private static void exec(Out o, int prev, int n) {
        o.g.link(prev / 16, prev % 16, n, 0);
    }

    /** Returns the exec output to continue from. */
    private static int stmt(Out o, Blk b, int prev, Ctx c, int depth) {
        Node n;
        int i;
        switch (b.type) {
            case S_SHAPE: {
                i = o.node(SHAPE, depth);
                n = o.g.nodes.get(i);
                for (int k = 0; k < 8; k++) {
                    n.p[k] = b.v[k];
                }
                exec(o, prev, i);
                wirePt(o, b.v[8], i, 2, c);
                if (b.v[9] >= 0) {
                    wirePt(o, b.v[9], i, 1, c); // from: where the shape starts (-1 = from me)
                }
                Ctx hit = c.copy();
                hit.ent.put(1, i * 16 + 2);
                hit.pt.put(2, i * 16 + 3);
                hit.pt.put(3, i * 16 + 3);
                list(o, b.sec.get(0), i * 16 + 1, hit, depth + 1);
                Ctx end = c.copy();
                end.pt.put(4, i * 16 + 5);
                list(o, b.sec.get(1), i * 16 + 4, end, depth + 1);
                return i * 16;
            }
            case S_ART: {
                i = o.node(ART, depth);
                o.g.nodes.get(i).p[0] = b.v[0];
                exec(o, prev, i);
                wirePt(o, b.v[1], i, 1, c);
                Ctx hit = c.copy();
                hit.ent.put(1, i * 16 + 2);
                hit.pt.put(2, -2); // computed from the target entity
                list(o, b.sec.get(0), i * 16 + 1, hit, depth + 1);
                Ctx end = c.copy();
                end.pt.put(4, i * 16 + 4);
                list(o, b.sec.get(1), i * 16 + 3, end, depth + 1);
                return i * 16;
            }
            case S_IF: {
                // If has no 'after' pin: a Sequence runs the if first, then what follows
                int seq = o.node(SEQUENCE, depth);
                exec(o, prev, seq);
                int pred = b.v[1];
                if (pred == 22) {
                    i = o.node(CHANCE, depth);
                    o.g.nodes.get(i).p[0] = b.v[2];
                    exec(o, seq * 16, i);
                } else {
                    i = o.node(IF, depth);
                    exec(o, seq * 16, i);
                    int cond = condition(o, b, c);
                    if (cond >= 0) {
                        o.g.link(cond / 16, cond % 16, i, 1);
                    }
                }
                list(o, b.sec.get(0), i * 16, c, depth + 1);
                list(o, b.sec.get(1), i * 16 + 1, c, depth + 1);
                return seq * 16 + 1;
            }
            case S_REPEAT: {
                i = o.node(REPEAT, depth);
                o.g.nodes.get(i).p[0] = b.v[0];
                o.g.nodes.get(i).p[1] = b.v[1];
                exec(o, prev, i);
                list(o, b.sec.get(0), i * 16, c, depth + 1);
                return i * 16 + 1;
            }
            case S_FOREACH: {
                i = o.node(FOR_EACH, depth);
                o.g.nodes.get(i).p[0] = b.v[1];
                o.g.nodes.get(i).p[1] = b.v[2];
                exec(o, prev, i);
                wirePt(o, b.v[0], i, 1, c);
                Ctx in = c.copy();
                in.ent.put(2, i * 16 + 2);
                in.pt.put(5, i * 16 + 2);
                list(o, b.sec.get(0), i * 16, in, depth + 1);
                return i * 16 + 1;
            }
            case S_WAIT: {
                i = o.node(DELAY, depth);
                int[] tenths = {3, 5, 10, 15, 20, 30, 50};
                o.g.nodes.get(i).p[0] = tenths[Math.max(0, Math.min(6, b.v[0]))];
                exec(o, prev, i);
                return i * 16;
            }
            case S_STATUS:
                i = o.node(STATUSA, depth);
                o.g.nodes.get(i).p[0] = b.v[0];
                exec(o, prev, i);
                wireEnt(o, b.v[1], i, 1, c);
                return i * 16;
            case S_DAMAGE:
                i = o.node(DAMAGE, depth);
                o.g.nodes.get(i).p[0] = b.v[1];
                exec(o, prev, i);
                wireEnt(o, b.v[0], i, 1, c);
                return i * 16;
            case S_HEAL:
                i = o.node(HEAL, depth);
                o.g.nodes.get(i).p[0] = b.v[1];
                exec(o, prev, i);
                wireEnt(o, b.v[0], i, 1, c);
                return i * 16;
            case S_SHIELD:
                i = o.node(SHIELD, depth);
                o.g.nodes.get(i).p[0] = b.v[1];
                o.g.nodes.get(i).p[1] = b.v[2];
                exec(o, prev, i);
                wireEnt(o, b.v[0], i, 1, c);
                return i * 16;
            case S_BLINK:
                i = o.node(BLINK, depth);
                o.g.nodes.get(i).p[0] = b.v[1];
                exec(o, prev, i);
                wirePt(o, b.v[0], i, 1, c);
                return i * 16;
            case S_PUSH:
                i = o.node(PUSH, depth);
                o.g.nodes.get(i).p[0] = b.v[2];
                exec(o, prev, i);
                wireEnt(o, b.v[0], i, 1, c);
                wirePt(o, b.v[1], i, 2, c);
                return i * 16;
            case S_ZONE:
                i = o.node(ZONE, depth);
                o.g.nodes.get(i).p[0] = b.v[0];
                o.g.nodes.get(i).p[1] = b.v[2];
                o.g.nodes.get(i).p[2] = b.v[3];
                exec(o, prev, i);
                wirePt(o, b.v[1], i, 1, c);
                return i * 16;
            case S_MANA:
                i = o.node(RESTORE, depth);
                o.g.nodes.get(i).p[0] = b.v[0];
                exec(o, prev, i);
                return i * 16;
            case S_SPELL:
                i = o.node(CAST_SPELL, depth);
                o.g.nodes.get(i).p[0] = b.v[0];
                exec(o, prev, i);
                wirePt(o, b.v[1], i, 1, c);
                return i * 16;
            default:
                return prev;
        }
    }

    /** Value output (node * 16 + pin) of an entity chip, or -1 (not available here). */
    private static int entSrc(Out o, int e, Ctx c) {
        if (c.ent.containsKey(e) && c.ent.get(e) >= 0) {
            return c.ent.get(e);
        }
        if (e == 0) {
            int n = o.value(CASTER);
            return n * 16;
        }
        if (e == 5 || e == 6) {
            int n = o.value(NEAREST);
            if (e == 6) {
                int t = entSrc(o, 1, c);
                if (t >= 0) {
                    int pos = o.value(POS_OF);
                    o.g.link(t / 16, t % 16, pos, 0);
                    o.g.link(pos, 0, n, 0);
                    o.g.link(t / 16, t % 16, n, 1);
                }
            }
            return n * 16;
        }
        return -1;
    }

    private static int ptSrc(Out o, int p, Ctx c) {
        if (c.pt.containsKey(p) && c.pt.get(p) >= 0) {
            return c.pt.get(p);
        }
        switch (p) {
            case 0: {
                int n = o.value(AIM);
                return n * 16;
            }
            case 1: {
                int me = entSrc(o, 0, c), pos = o.value(POS_OF);
                o.g.link(me / 16, me % 16, pos, 0);
                return pos * 16;
            }
            case 2:
            case 5:
            case 6:
            case 7: {
                int e = entSrc(o, p == 2 ? 1 : p == 5 ? 2 : p == 6 ? 3 : 4, c);
                if (e < 0) {
                    return -1;
                }
                int pos = o.value(POS_OF);
                o.g.link(e / 16, e % 16, pos, 0);
                return pos * 16;
            }
            case 8:
            case 9:
            case 10: {
                int base = ptSrc(o, p == 8 ? 2 : p == 9 ? 0 : 1, c);
                if (base < 0) {
                    return -1;
                }
                int off = o.value(OFFSET);
                o.g.nodes.get(off).p[0] = p == 10 ? 0 : p == 8 ? 6 : 10;
                o.g.nodes.get(off).p[1] = p == 10 ? 6 : 0;
                o.g.link(base / 16, base % 16, off, 0);
                return off * 16;
            }
            default:
                return -1;
        }
    }

    private static void wireEnt(Out o, int e, int node, int input, Ctx c) {
        int s = entSrc(o, e, c);
        if (s >= 0) {
            o.g.link(s / 16, s % 16, node, input);
        }
    }

    private static void wirePt(Out o, int p, int node, int input, Ctx c) {
        int s = ptSrc(o, p, c);
        if (s >= 0) {
            o.g.link(s / 16, s % 16, node, input);
        }
    }

    /** Builds the yes/no value of an If block; returns node * 16 + pin or -1. */
    private static int condition(Out o, Blk b, Ctx c) {
        int pred = b.v[1], numv = b.v[2];
        if (pred <= 9) {
            int e = entSrc(o, b.v[0], c);
            int n = o.value(HAS_STATUS);
            o.g.nodes.get(n).p[0] = pred;
            if (e >= 0) {
                o.g.link(e / 16, e % 16, n, 0);
            }
            return n * 16;
        }
        if (pred == 10 || pred == 11) {
            int e = entSrc(o, b.v[0], c);
            int hp = o.value(HP);
            if (e >= 0) {
                o.g.link(e / 16, e % 16, hp, 0);
            }
            return compare(o, hp * 16, pred == 10 ? 0 : 1, numv);
        }
        if (pred == 12) {
            int e = entSrc(o, b.v[0], c);
            int n = o.value(IS_BOSS);
            if (e >= 0) {
                o.g.link(e / 16, e % 16, n, 0);
            }
            return n * 16;
        }
        if (pred == 13) {
            int e = entSrc(o, b.v[0], c);
            int cnt = o.value(COUNT);
            o.g.nodes.get(cnt).p[0] = 6;
            if (e >= 0) {
                int pos = o.value(POS_OF);
                o.g.link(e / 16, e % 16, pos, 0);
                o.g.link(pos, 0, cnt, 0);
            }
            return compare(o, cnt * 16, 4, numv);
        }
        if (pred == 14) {
            int e = entSrc(o, b.v[0], c);
            int dist = o.value(DIST);
            if (e >= 0) {
                int pos = o.value(POS_OF);
                o.g.link(e / 16, e % 16, pos, 0);
                o.g.link(pos, 0, dist, 0);
            }
            return compare(o, dist * 16, 0, numv);
        }
        if (pred == 21) {
            int m = o.value(MANA);
            return compare(o, m * 16, 1, numv);
        }
        if (pred == 23) {
            int rg = o.value(RINGS);
            return compare(o, rg * 16, 4, numv);
        }
        int w = o.value(WORLDQ);
        o.g.nodes.get(w).p[0] = Math.max(0, Math.min(WORLD.length - 1, pred - 15));
        return w * 16;
    }

    private static int compare(Out o, int a, int op, int b) {
        int cmp = o.value(COMPARE);
        o.g.nodes.get(cmp).p[0] = op;
        o.g.nodes.get(cmp).p[1] = b;
        o.g.link(a / 16, a % 16, cmp, 0);
        return cmp * 16;
    }

    // ------------------------------------------------------------------ text
    /** "Reads like" text of a tree. */
    public static String describe(Tree t, int[] elements) {
        StringBuilder sb = new StringBuilder();
        for (Blk h : t.scripts) {
            sb.append(chipText(h, -1, elements)).append(": ");
            say(sb, h.body(), elements);
            sb.append(" ");
        }
        return sb.toString().trim();
    }

    private static void say(StringBuilder sb, List<Blk> l, int[] el) {
        if (l.isEmpty()) {
            sb.append("nothing.");
            return;
        }
        for (int i = 0; i < l.size(); i++) {
            Blk b = l.get(i);
            sb.append(i > 0 ? " Then " : "").append(chipText(b, -1, el));
            BDef d = b.def();
            for (int s = 0; s < d.sections.length; s++) {
                if (!b.sec.get(s).isEmpty()) {
                    sb.append(" (").append(d.sections[s]).append(": ");
                    say(sb, b.sec.get(s), el);
                    sb.append(")");
                }
            }
            sb.append(".");
        }
    }

    /** Block text with chips filled in (no formatting). */
    public static String chipText(Blk b, int skip, int[] el) {
        String text = b.def().text;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '{') {
                int k = text.charAt(i + 1) - '0';
                sb.append(value(b, k, el));
                i += 2;
            } else {
                sb.append(ch);
            }
        }
        return sb.toString().replaceAll(" +", " ").trim();
    }

    /** Text of chip k of a block. Empty for hidden chips (no rune, no 2nd element, unused number). */
    public static String value(Blk b, int k, int[] el) {
        Chip c = b.def().chips[k];
        int v = b.v[k];
        switch (c.kind) {
            case NUM:
                if (b.type == S_SHAPE && k == 4 && b.v[0] == 13) {
                    return v + " (" + Math.max(4, Math.min(16, Math.round(6 * (1 + 0.12F * v)))) + " pieces)";
                }
                if (b.type == S_IF && k == 2 && !predNum(b.v[1])) {
                    return "";
                }
                return String.valueOf(v) + (b.type == S_IF && (b.v[1] == 10 || b.v[1] == 11 || b.v[1] == 21 || b.v[1] == 22) ? "%" : "");
            case ELEM:
            case ELEM2: {
                if (v < 0 || v >= Magic.ELEMENT_NAME.length) {
                    return c.kind == ELEM2 ? "" : "?";
                }
                if (c.kind == ELEM2) {
                    int fu = b.type == S_SHAPE ? SpellArts.fusion(b.v[1], v) : -1;
                    return "+ " + Magic.ELEMENT_NAME[v] + (fu >= 0 ? " = " + SpellArts.FUSION[fu] : "");
                }
                return Magic.ELEMENT_NAME[v];
            }
            case PREDK:
                return PRED[Math.max(0, Math.min(PRED.length - 1, v))].replace(" %", "").replace("N+ ", "");
            case ENT:
                if (b.type == S_IF && predWorld(b.v[1])) {
                    return "";
                }
                return ENT_NAME[Math.max(0, Math.min(ENT_NAME.length - 1, v))];
            case PT:
                if (b.type == S_SHAPE && k == 9) {
                    return v < 0 ? "" : "from " + PT_NAME[Math.min(PT_NAME.length - 1, v)];
                }
                if (b.type == S_SHAPE && k == 8) {
                    int sh = b.v[0];
                    boolean self = sh == 3 || sh == 6 || sh == 10 || sh == 11 || sh == 12 || sh == 13;
                    return self ? "" : "toward " + PT_NAME[Math.max(0, Math.min(PT_NAME.length - 1, v))];
                }
                return PT_NAME[Math.max(0, Math.min(PT_NAME.length - 1, v))];
            default:
                if (c.labels != null && v - c.min >= 0 && v - c.min < c.labels.length) {
                    String s = c.labels[v - c.min];
                    return "no rune".equals(s) ? "" : s;
                }
                return String.valueOf(v);
        }
    }

    // ------------------------------------------------------------------ templates and conversion
    public static final String[] TEMPLATE = {"Fire Bolt", "Storm Lance", "Frost Trap Field", "Healing Nova", "Shadow Step",
            "Meteor Rain", "Thorn Ward", "Plasma Burst", "Shatterflame"};

    private static Blk shape(int shape, int el, int pw, int sz, int sp) {
        Blk b = new Blk(S_SHAPE);
        b.v[0] = shape;
        b.v[1] = el;
        b.v[3] = pw;
        b.v[4] = sz;
        b.v[5] = sp;
        return b;
    }

    private static Blk hat(Tree t, int type, int y) {
        Blk h = new Blk(type);
        h.x = 10;
        h.y = y;
        t.scripts.add(h);
        return h;
    }

    public static Tree template(int i) {
        Tree t = new Tree();
        switch (i) {
            case 0: {
                Blk b = shape(0, 0, 6, 3, 4);
                b.v[6] = 1;
                hat(t, H_CAST, 10).body().add(b);
                break;
            }
            case 1: {
                Blk bolt = shape(0, 2, 6, 3, 4);
                bolt.v[6] = 0;
                Blk iff = new Blk(S_IF);
                iff.v[0] = 1;
                iff.v[1] = 4;
                Blk fe = new Blk(S_FOREACH);
                fe.v[0] = 2;
                Blk ch = shape(9, 2, 3, 3, 3);
                ch.v[8] = 5;
                fe.sec.get(0).add(ch);
                iff.sec.get(0).add(fe);
                Blk low = new Blk(S_IF);
                low.v[0] = 1;
                low.v[1] = 10;
                low.v[2] = 30;
                Blk bl = shape(1, 2, 5, 3, 3);
                bl.v[8] = 2;
                low.sec.get(0).add(bl);
                iff.sec.get(1).add(low);
                bolt.sec.get(0).add(iff);
                hat(t, H_CAST, 10).body().add(bolt);
                Blk m = new Blk(S_MANA);
                m.v[0] = 10;
                hat(t, H_KILL, 200).body().add(m);
                break;
            }
            case 2: {
                Blk trap = shape(7, 1, 5, 4, 0);
                Blk z = new Blk(S_ZONE);
                z.v[0] = 1;
                z.v[1] = 4;
                trap.sec.get(1).add(z);
                hat(t, H_CAST, 10).body().add(trap);
                break;
            }
            case 3: {
                Blk nova = shape(3, 6, 3, 5, 3);
                Blk h = new Blk(S_HEAL);
                h.v[0] = 0;
                h.v[1] = 2;
                nova.sec.get(0).add(h);
                hat(t, H_CAST, 10).body().add(nova);
                break;
            }
            case 4: {
                Blk bl = new Blk(S_BLINK);
                bl.v[0] = 0;
                bl.v[1] = 10;
                Blk h = hat(t, H_CAST, 10);
                h.body().add(bl);
                h.body().add(shape(3, 5, 5, 3, 3));
                break;
            }
            case 5: {
                Blk r = new Blk(S_REPEAT);
                r.v[0] = 4;
                r.v[1] = 6;
                Blk b = shape(0, 0, 6, 4, 3);
                b.v[8] = 0;
                r.sec.get(0).add(b);
                Blk h = hat(t, H_CAST, 10);
                h.v[0] = 3; // charge up to 3 rings for a heavier rain
                h.body().add(r);
                break;
            }
            case 7: {
                // fusion + rings: a Fire + Lightning (Plasma) blast; fully charged it adds a plasma nova
                Blk h = hat(t, H_CAST, 10);
                h.v[0] = 2;
                Blk bl = shape(1, 0, 5, 3, 3);
                bl.v[2] = 2;
                h.body().add(bl);
                Blk iff = new Blk(S_IF);
                iff.v[0] = 0;
                iff.v[1] = 23;
                iff.v[2] = 2;
                Blk nova = shape(3, 0, 4, 3, 3);
                nova.v[2] = 2;
                iff.sec.get(0).add(nova);
                h.body().add(iff);
                break;
            }
            case 8: {
                // a fireball that bursts into ice shards where it hits (shards start at the hit point)
                Blk h = hat(t, H_CAST, 10);
                h.v[0] = 2;
                Blk fb = shape(0, 0, 6, 3, 4);
                Blk sh = shape(13, 1, 4, 3, 3);
                sh.v[9] = 3;
                fb.sec.get(0).add(sh);
                h.body().add(fb);
                break;
            }
            case 6:
            default: {
                Blk h = hat(t, H_HURT, 10);
                Blk s = new Blk(S_SHIELD);
                s.v[0] = 0;
                h.body().add(s);
                Blk p = new Blk(S_PUSH);
                p.v[0] = 4;
                p.v[1] = 1;
                p.v[2] = 6;
                h.body().add(p);
                break;
            }
        }
        return t;
    }

    /** Element chips set to elements the player has not learned are moved to their first element. */
    public static void fitElements(Tree t, int[] slotElements) {
        int first = slotElements != null && slotElements.length > 0 ? slotElements[0] : -1;
        if (first < 0) {
            return;
        }
        for (Blk h : t.scripts) {
            fit(h.body(), slotElements, first);
        }
    }

    private static void fit(List<Blk> l, int[] el, int first) {
        for (Blk b : l) {
            for (int k = 0; k < b.v.length; k++) {
                int kind = b.def().chips[k].kind;
                if ((kind == ELEM || kind == ELEM2) && b.v[k] >= 0) {
                    boolean has = false;
                    for (int e : el) {
                        has |= e == b.v[k];
                    }
                    if (!has) {
                        b.v[k] = kind == ELEM ? first : -1;
                    }
                }
            }
            for (List<Blk> s : b.sec) {
                fit(s, el, first);
            }
        }
    }

    /** Blocks for an old design (or a fresh slot). */
    public static Tree fromLegacy(Magic.Spell s, int[] slotElements) {
        Tree t = new Tree();
        int first = slotElements != null && slotElements.length > 0 && slotElements[0] >= 0 ? slotElements[0] : 0;
        Blk b = shape(0, first, 5, 3, 3);
        if (s.valid()) {
            b.v[0] = s.shape;
            int e1 = slotElements != null && s.which < slotElements.length ? slotElements[s.which] : -1;
            int e2 = slotElements != null && s.which2 >= 0 && s.which2 < slotElements.length ? slotElements[s.which2] : -1;
            b.v[1] = e1 >= 0 ? e1 : first;
            b.v[2] = e2 >= 0 && e2 != b.v[1] ? e2 : -1;
            b.v[3] = s.pw;
            b.v[4] = s.sz;
            b.v[5] = s.sp;
            b.v[6] = s.rune[0];
            b.v[7] = s.rune[1];
            if (s.trig >= 0) {
                Blk cs = new Blk(S_SPELL);
                cs.v[0] = s.trig + 1;
                cs.v[1] = 3;
                b.sec.get(0).add(cs);
            }
        }
        hat(t, H_CAST, 10).body().add(b);
        return t;
    }
}
