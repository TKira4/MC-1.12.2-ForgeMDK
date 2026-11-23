package com.imba.undyingarcana;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

/**
 * Spell Weave: a spell is a small node graph, wired like a blueprint. White (exec) wires say what happens next, coloured
 * wires carry values (an entity, a point, a number, yes / no). Events start a run (On Cast, On Kill, Every X s, When
 * Hurt); flow nodes branch and loop; value nodes read the world; action nodes do things (Cast Shape with the 13 shapes,
 * statuses, damage, heal, shield, blink, push, zones, mana, casting another spell).
 *
 * Saved as NBT {name, n: [{t, x, y, p: int[], l: int[] (per input: from node * 16 + from pin, or -1)}]} on the player and
 * in Spell Pages; shared as text "UA2:" + base64 of the compressed NBT.
 */
public final class SpellGraph {
    // pin types
    public static final int X = 0, E = 1, P = 2, N = 3, B = 4;
    public static final String[] PIN_NAME = {"exec", "entity", "point", "number", "yes/no"};
    public static final int[] PIN_RGB = {0xF0F0F0, 0xE07AC8, 0x6FB8FF, 0x7FD46A, 0xF0C850};
    // categories
    public static final int EVENT = 0, FLOW = 1, VALUE = 2, ACTION = 3;
    public static final String[] CAT = {"Events", "Flow", "Values", "Actions"};
    public static final int[] CAT_RGB = {0x4682C8, 0xC89632, 0x5AAA5A, 0xBE4646};

    public static final int MAX_NODES = 64, MAX_BLOCKS = 40, BASE_NODES = 6, MAGIC_PER_NODE = 8;

    public static final String[] STATUS = {"Burning", "Chilled", "Frozen", "Shocked", "Wet", "Drained", "Staggered", "Exposed",
            "Radiant", "Poisoned"};
    public static final String[] CMP = {"<", ">", "=", "<=", ">="};
    public static final String[] MATH = {"+", "-", "x", "/", "min", "max"};
    public static final String[] LOGIC = {"and", "or", "not a"};
    public static final String[] WORLD = {"Night", "Raining", "I'm sneaking", "I'm in water", "I'm on the ground", "I'm below half HP"};

    /** A parameter of a node type: label, range, default, optional value labels. */
    public static final class Param {
        public final String name;
        public final int min, max, def;
        public final String[] labels;
        public final int labelOffset;

        Param(String name, int min, int max, int def) {
            this(name, min, max, def, null, 0);
        }

        Param(String name, int min, int max, int def, String[] labels, int labelOffset) {
            this.name = name;
            this.min = min;
            this.max = max;
            this.def = def;
            this.labels = labels;
            this.labelOffset = labelOffset;
        }

        public String show(int v) {
            if (labels != null) {
                int i = v - labelOffset;
                return i >= 0 && i < labels.length ? labels[i] : "-";
            }
            return String.valueOf(v);
        }
    }

    /** A node type. */
    public static final class Def {
        public final int id, cat;
        public final String name, desc;
        public final String[] inName, outName;
        public final int[] in, out;
        public final Param[] params;

        Def(int id, int cat, String name, String desc, Object[] ins, Object[] outs, Param... params) {
            this.id = id;
            this.cat = cat;
            this.name = name;
            this.desc = desc;
            inName = new String[ins.length / 2];
            in = new int[ins.length / 2];
            for (int i = 0; i < in.length; i++) {
                inName[i] = (String) ins[2 * i];
                in[i] = (Integer) ins[2 * i + 1];
            }
            outName = new String[outs.length / 2];
            out = new int[outs.length / 2];
            for (int i = 0; i < out.length; i++) {
                outName[i] = (String) outs[2 * i];
                out[i] = (Integer) outs[2 * i + 1];
            }
            this.params = params;
        }
    }

    public static final Def[] DEFS = new Def[64];
    public static final List<Def> LIST = new ArrayList<>();
    // ids (saved in data: never renumber)
    public static final int ON_CAST = 0, ON_KILL = 1, EVERY = 2, ON_HURT = 3;
    public static final int IF = 10, CHANCE = 11, REPEAT = 12, FOR_EACH = 13, DELAY = 14, SEQUENCE = 15, CAST_SPELL = 16;
    public static final int CASTER = 20, AIM = 21, POS_OF = 22, NEAREST = 23, HP = 24, DIST = 25, HAS_STATUS = 26, COUNT = 27,
            MANA = 28, NUMBER = 29, COMPARE = 30, MATHS = 31, LOGICS = 32, RANDOM = 33, WORLDQ = 34, IS_BOSS = 35, OFFSET = 36,
            RINGS = 37;
    public static final int SHAPE = 40, STATUSA = 41, DAMAGE = 42, HEAL = 43, SHIELD = 44, BLINK = 45, PUSH = 46, ZONE = 47,
            RESTORE = 49, ART = 48;

    private static Object[] a(Object... o) {
        return o;
    }

    private static void def(Def d) {
        DEFS[d.id] = d;
        LIST.add(d);
    }

    private static String[] elems() {
        return Magic.ELEMENT_NAME;
    }

    static {
        String[] el = elems();
        String[] el2 = new String[el.length + 1];
        el2[0] = "none";
        System.arraycopy(el, 0, el2, 1, el.length);
        String[] runes = new String[Magic.RUNE.length + 1];
        runes[0] = "none";
        System.arraycopy(Magic.RUNE, 0, runes, 1, Magic.RUNE.length);
        String[] slotEl = new String[Magic.MAX_ELEMENTS];
        for (int i = 0; i < slotEl.length; i++) {
            slotEl[i] = "Element " + (i + 1);
        }
        // events
        def(new Def(ON_CAST, EVENT, "On Cast", "Runs when you cast this spell. Max rings 0 = instant; more = hold the key to charge"
                + " rings (0.6 s each, +25% damage and +10% size per ring).", a(), a("then", X, "caster", E, "aim", P),
                new Param("max rings", 0, 5, 0)));
        def(new Def(ON_KILL, EVENT, "On Kill", "Runs when something this spell hurt dies (within 10 s).", a(),
                a("then", X, "victim", E, "at", P)));
        def(new Def(EVERY, EVENT, "Every X s", "After the cast, runs again and again.", a(), a("then", X, "index", N),
                new Param("tenths of s", 5, 50, 10), new Param("times", 1, 10, 3)));
        def(new Def(ON_HURT, EVENT, "When Hurt", "Ward: runs by itself when you get hurt (uses this spell's cooldown and mana).",
                a(), a("then", X, "attacker", E)));
        // flow
        def(new Def(IF, FLOW, "If", "Goes one way or the other.", a("do", X, "cond", B), a("true", X, "false", X)));
        def(new Def(CHANCE, FLOW, "Chance", "Random split.", a("do", X), a("yes", X, "no", X), new Param("percent", 1, 100, 50)));
        def(new Def(REPEAT, FLOW, "Repeat", "Runs 'loop' N times, then 'done'.", a("do", X), a("loop", X, "done", X, "index", N),
                new Param("times", 1, 8, 3), new Param("gap ticks", 0, 20, 4)));
        def(new Def(FOR_EACH, FLOW, "For Each Enemy", "Runs 'loop' once per enemy near a point.", a("do", X, "center", P),
                a("loop", X, "done", X, "target", E), new Param("radius", 1, 12, 6), new Param("max", 1, 10, 3)));
        def(new Def(DELAY, FLOW, "Delay", "Waits, then continues.", a("do", X), a("then", X), new Param("tenths of s", 1, 50, 10)));
        def(new Def(SEQUENCE, FLOW, "Sequence", "Runs 1, then 2, then 3.", a("do", X), a("1", X, "2", X, "3", X)));
        def(new Def(CAST_SPELL, FLOW, "Cast Spell", "Casts another of your spells from a point (free, once per cast).",
                a("do", X, "from", P, "toward", P), a("then", X), new Param("spell", 1, 6, 1)));
        // values
        def(new Def(CASTER, VALUE, "Caster", "You.", a(), a("you", E)));
        def(new Def(AIM, VALUE, "Aim Point", "Where you are looking (block or enemy).", a(), a("point", P), new Param("range", 4, 32, 16)));
        def(new Def(POS_OF, VALUE, "Position Of", "The middle of an entity.", a("entity", E), a("point", P)));
        def(new Def(NEAREST, VALUE, "Nearest Enemy", "The closest enemy to a point (not the given one).", a("near", P, "skip", E),
                a("enemy", E), new Param("radius", 2, 16, 8)));
        def(new Def(HP, VALUE, "HP %", "Health of an entity, 0-100.", a("of", E), a("%", N)));
        def(new Def(DIST, VALUE, "Distance", "Blocks between two points.", a("a", P, "b", P), a("blocks", N)));
        def(new Def(HAS_STATUS, VALUE, "Has Status", "Is the entity burning, frozen, wet...?", a("entity", E), a("yes", B),
                new Param("status", 0, STATUS.length - 1, 4, STATUS, 0)));
        def(new Def(COUNT, VALUE, "Enemies Near", "How many enemies around a point.", a("center", P), a("count", N),
                new Param("radius", 1, 16, 6)));
        def(new Def(MANA, VALUE, "My Mana %", "Your mana, 0-100.", a(), a("%", N)));
        def(new Def(NUMBER, VALUE, "Number", "A fixed number.", a(), a("n", N), new Param("value", -100, 100, 30)));
        def(new Def(COMPARE, VALUE, "Compare", "Compares two numbers.", a("a", N, "b", N), a("yes", B),
                new Param("op", 0, CMP.length - 1, 0, CMP, 0), new Param("b if empty", -100, 100, 30)));
        def(new Def(MATHS, VALUE, "Math", "Adds, subtracts...", a("a", N, "b", N), a("n", N), new Param("op", 0, MATH.length - 1, 0, MATH, 0)));
        def(new Def(LOGICS, VALUE, "Logic", "and / or / not.", a("a", B, "b", B), a("yes", B), new Param("op", 0, LOGIC.length - 1, 0, LOGIC, 0)));
        def(new Def(RANDOM, VALUE, "Random", "A random number 0-100.", a(), a("n", N)));
        def(new Def(WORLDQ, VALUE, "Check", "Night, rain, sneaking, in water...", a(), a("yes", B),
                new Param("what", 0, WORLD.length - 1, 0, WORLD, 0)));
        def(new Def(IS_BOSS, VALUE, "Is Boss", "Is the entity a boss?", a("entity", E), a("yes", B)));
        def(new Def(RINGS, VALUE, "Rings", "How many rings this cast was charged with (0 = instant).", a(), a("rings", N)));
        def(new Def(OFFSET, VALUE, "Offset", "Moves a point up and forward (your facing).", a("point", P), a("point", P),
                new Param("up", -8, 16, 6), new Param("forward", -8, 8, 0)));
        // actions
        def(new Def(SHAPE, ACTION, "Cast Shape", "Casts one of the 13 shapes. 'on hit' runs for every enemy it hits.",
                a("do", X, "from", P, "toward", P, "target", E),
                a("then", X, "on hit", X, "hit", E, "hit at", P, "on end", X, "end at", P),
                new Param("shape", 0, Magic.SHAPE.length - 1, 0, Magic.SHAPE, 0), new Param("element", 0, Magic.ELEMENT.length - 1, 0, el, 0),
                new Param("2nd element", -1, Magic.ELEMENT.length - 1, -1, el2, -1), new Param("power", 0, 10, 5),
                new Param("size", 0, 10, 3), new Param("speed", 0, 10, 3), new Param("rune", -1, Magic.RUNE.length - 1, -1, runes, -1),
                new Param("rune 2", -1, Magic.RUNE.length - 1, -1, runes, -1)));
        def(new Def(STATUSA, ACTION, "Apply Status", "Puts an element status on an entity.", a("do", X, "target", E), a("then", X),
                new Param("element", 0, Magic.ELEMENT.length - 1, 0, el, 0)));
        def(new Def(DAMAGE, ACTION, "Damage", "Magic damage (grows with Magic).", a("do", X, "target", E), a("then", X),
                new Param("amount", 1, 20, 4)));
        def(new Def(HEAL, ACTION, "Heal", "Heals (you if no target). Enemies are never healed.", a("do", X, "target", E), a("then", X),
                new Param("amount", 1, 20, 4)));
        def(new Def(SHIELD, ACTION, "Shield", "Absorption hearts (you if no target).", a("do", X, "target", E), a("then", X),
                new Param("strength", 1, 4, 1), new Param("seconds", 2, 20, 8)));
        def(new Def(BLINK, ACTION, "Blink To", "Teleports you toward a point.", a("do", X, "to", P), a("then", X),
                new Param("max blocks", 2, 16, 8)));
        def(new Def(PUSH, ACTION, "Push / Pull", "Pushes away from a point (negative pulls).", a("do", X, "target", E, "from", P),
                a("then", X), new Param("strength", -10, 10, 5)));
        def(new Def(ZONE, ACTION, "Zone", "A lingering field that hurts enemies every 0.5 s.", a("do", X, "at", P), a("then", X),
                new Param("element", 0, Magic.ELEMENT.length - 1, 0, el, 0), new Param("radius", 1, 6, 3), new Param("seconds", 1, 10, 4)));
        def(new Def(ART, ACTION, "Arcane Art", "One of your learned Arcane Arts (from Arcane Scrolls).", a("do", X, "at", P),
                a("then", X, "on hit", X, "hit", E, "on end", X, "end at", P),
                new Param("art", 0, SpellArts.ART.length - 1, 0, SpellArts.ART, 0)));
        def(new Def(RESTORE, ACTION, "Restore Mana", "Gives mana back (at most 30 per cast).", a("do", X), a("then", X),
                new Param("amount", 1, 20, 5)));
    }

    private static String[] slotEl2() {
        String[] s = new String[Magic.MAX_ELEMENTS + 1];
        s[0] = "none";
        for (int i = 0; i < Magic.MAX_ELEMENTS; i++) {
            s[i + 1] = "Element " + (i + 1);
        }
        return s;
    }

    // ------------------------------------------------------------------ graph
    public static final class Node {
        public int type, x, y;
        public int[] p;
        /** per input: source node index * 16 + source output pin, or -1 */
        public int[] link;

        public Node(int type, int x, int y) {
            this.type = type;
            this.x = x;
            this.y = y;
            Def d = DEFS[type];
            p = new int[d.params.length];
            for (int i = 0; i < p.length; i++) {
                p[i] = d.params[i].def;
            }
            link = new int[d.in.length];
            java.util.Arrays.fill(link, -1);
        }

        public Def def() {
            return DEFS[type];
        }
    }

    public String name = "New Spell";
    public final List<Node> nodes = new ArrayList<>();
    /** The block view this graph was compiled from (SpellBlocks.Tree), or null when it was edited as nodes. */
    public NBTTagCompound blocks;

    public Node add(int type, int x, int y) {
        Node n = new Node(type, x, y);
        nodes.add(n);
        return n;
    }

    /** Connects output pin 'op' of node 'from' to input pin 'ip' of node 'to'. */
    public void link(int from, int op, int to, int ip) {
        nodes.get(to).link[ip] = from * 16 + op;
    }

    public void remove(int idx) {
        nodes.remove(idx);
        for (Node n : nodes) {
            for (int i = 0; i < n.link.length; i++) {
                int l = n.link[i];
                if (l < 0) {
                    continue;
                }
                int src = l / 16;
                if (src == idx) {
                    n.link[i] = -1;
                } else if (src > idx) {
                    n.link[i] = l - 16;
                }
            }
        }
    }

    public SpellGraph copy() {
        return fromNBT(toNBT());
    }

    public NBTTagCompound toNBT() {
        NBTTagCompound t = new NBTTagCompound();
        t.setString("name", name);
        NBTTagList l = new NBTTagList();
        for (Node n : nodes) {
            NBTTagCompound c = new NBTTagCompound();
            c.setByte("t", (byte) n.type);
            c.setShort("x", (short) n.x);
            c.setShort("y", (short) n.y);
            c.setIntArray("p", n.p);
            c.setIntArray("l", n.link);
            l.appendTag(c);
        }
        t.setTag("n", l);
        if (blocks != null) {
            t.setTag("blk", blocks);
        }
        return t;
    }

    /** Reads a graph; unknown node types and bad links are dropped / cleared. */
    public static SpellGraph fromNBT(NBTTagCompound t) {
        SpellGraph g = new SpellGraph();
        g.name = t.getString("name");
        if (g.name.length() > 24) {
            g.name = g.name.substring(0, 24);
        }
        NBTTagList l = t.getTagList("n", 10);
        for (int i = 0; i < l.tagCount() && i < MAX_NODES; i++) {
            NBTTagCompound c = l.getCompoundTagAt(i);
            int type = c.getByte("t");
            if (type < 0 || type >= DEFS.length || DEFS[type] == null) {
                type = NUMBER; // keeps indexes stable
            }
            Node n = new Node(type, c.getShort("x"), c.getShort("y"));
            int[] p = c.getIntArray("p");
            Def d = DEFS[type];
            for (int k = 0; k < n.p.length && k < p.length; k++) {
                n.p[k] = Math.max(d.params[k].min, Math.min(d.params[k].max, p[k]));
            }
            int[] ln = c.getIntArray("l");
            for (int k = 0; k < n.link.length && k < ln.length; k++) {
                n.link[k] = ln[k];
            }
            g.nodes.add(n);
        }
        if (t.hasKey("blk")) {
            g.blocks = t.getCompoundTag("blk");
        }
        // drop links that do not match pin types
        for (Node n : g.nodes) {
            for (int k = 0; k < n.link.length; k++) {
                if (!g.linkOk(n, k)) {
                    n.link[k] = -1;
                }
            }
        }
        return g;
    }

    boolean linkOk(Node n, int k) {
        int lk = n.link[k];
        if (lk < 0) {
            return true;
        }
        int src = lk / 16, op = lk % 16;
        if (src < 0 || src >= nodes.size()) {
            return false;
        }
        Def sd = nodes.get(src).def();
        return op < sd.out.length && sd.out[op] == n.def().in[k];
    }

    // ------------------------------------------------------------------ share codes
    public String toCode() {
        try {
            ByteArrayOutputStream o = new ByteArrayOutputStream();
            CompressedStreamTools.writeCompressed(toNBT(), o);
            return "UA2:" + Base64.getEncoder().encodeToString(o.toByteArray());
        } catch (Exception e) {
            return "";
        }
    }

    public static SpellGraph fromCode(String code) {
        try {
            String s = code.trim();
            if (!s.startsWith("UA2:") || s.length() > 12000) {
                return null;
            }
            byte[] b = Base64.getDecoder().decode(s.substring(4));
            return fromNBT(CompressedStreamTools.readCompressed(new ByteArrayInputStream(b)));
        } catch (Exception e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ rules
    /** How many blocks (events, flow and actions; value nodes are free) a spell may use. */
    public static int maxNodes(int magic) {
        return Math.min(MAX_BLOCKS, BASE_NODES + magic / MAGIC_PER_NODE);
    }

    /** Blocks used: every node but values and the hidden Sequence nodes blocks compile to. */
    public int used() {
        int n = 0;
        for (Node x : nodes) {
            if (x.def().cat != VALUE && x.type != SEQUENCE) {
                n++;
            }
        }
        return n;
    }

    /** Problems that stop saving, or null when the graph is fine. */
    public String check(int magic, int runeSlots) {
        if (used() > maxNodes(magic)) {
            return "Too many blocks: " + used() + "/" + maxNodes(magic) + " (one more every " + MAGIC_PER_NODE + " Magic).";
        }
        if (nodes.size() > MAX_NODES) {
            return "Too many nodes (" + MAX_NODES + " at most, values included).";
        }
        boolean event = false;
        for (Node n : nodes) {
            if (n.def().cat == EVENT) {
                event = true;
            }
            if (n.type == SHAPE) {
                int runes = (n.p[6] >= 0 ? 1 : 0) + (n.p[7] >= 0 ? 1 : 0);
                if (runes > runeSlots) {
                    return "Rune slots: " + runeSlots + " (one more every " + Magic.MAGIC_PER_RUNE + " Magic).";
                }
                if (n.p[6] >= 0 && n.p[6] == n.p[7]) {
                    return "The same rune twice on one shape.";
                }
            }
        }
        if (!event) {
            return "Add an event node (On Cast...).";
        }
        if (cyclic()) {
            return "The white wires make a loop: use Repeat instead.";
        }
        return null;
    }

    /** True when exec wires form a cycle (a spell must always end). */
    public boolean cyclic() {
        int n = nodes.size();
        int[] state = new int[n];
        for (int i = 0; i < n; i++) {
            if (state[i] == 0 && dfs(i, state)) {
                return true;
            }
        }
        return false;
    }

    private boolean dfs(int i, int[] state) {
        state[i] = 1;
        for (int j = 0; j < nodes.size(); j++) {
            Node m = nodes.get(j);
            for (int k = 0; k < m.link.length; k++) {
                if (m.def().in[k] == X && m.link[k] >= 0 && m.link[k] / 16 == i) {
                    if (state[j] == 1 || state[j] == 0 && dfs(j, state)) {
                        return true;
                    }
                }
            }
        }
        state[i] = 2;
        return false;
    }

    /** Nodes whose exec input 'do' is wired to output pin 'op' of node 'from'. */
    public List<Integer> next(int from, int op) {
        List<Integer> r = new ArrayList<>();
        int key = from * 16 + op;
        for (int j = 0; j < nodes.size(); j++) {
            Node m = nodes.get(j);
            for (int k = 0; k < m.link.length; k++) {
                if (m.def().in[k] == X && m.link[k] == key) {
                    r.add(j);
                }
            }
        }
        return r;
    }

    // ------------------------------------------------------------------ cost
    /** A Magic.Spell view of a Cast Shape node (for cost, damage and the shape code). */
    public static Magic.Spell shapeSpell(Node n) {
        Magic.Spell s = new Magic.Spell();
        s.shape = n.p[0];
        s.which = n.p[1];
        s.which2 = n.p[2];
        s.pw = n.p[3];
        s.sz = n.p[4];
        s.sp = n.p[5];
        s.rune[0] = n.p[6];
        s.rune[1] = n.p[7];
        return s;
    }

    private static int actionCost(Node n) {
        switch (n.type) {
            case SHAPE: {
                Magic.Spell s = shapeSpell(n);
                // free points: the graph's node budget is the limit now, not the old 15 points
                float c = Magic.baseCost(s.shape) * (1.0F + 0.05F * (s.pw + s.sz + s.sp));
                c *= s.which2 >= 0 ? 1.3F : 1.0F;
                c *= 1.0F + 0.12F * s.runes();
                return Math.round(c);
            }
            case STATUSA:
                return 4;
            case DAMAGE:
            case HEAL:
                return 2 * n.p[0];
            case SHIELD:
                return 4 * n.p[0] + n.p[1] / 2;
            case BLINK:
                return 4 + n.p[0];
            case PUSH:
                return 2 + Math.abs(n.p[0]) / 2;
            case ZONE:
                return 3 * n.p[1] + 2 * n.p[2];
            case CAST_SPELL:
                return 4;
            case ART:
                return SpellArts.ART_COST[Math.max(0, Math.min(SpellArts.ART.length - 1, n.p[0]))];
            default:
                return 0;
        }
    }

    /** Mana cost: every action reachable from an event, multiplied by loops and timers on the way. */
    public int cost() {
        float total = 0;
        for (int i = 0; i < nodes.size(); i++) {
            Node n = nodes.get(i);
            if (n.def().cat == EVENT) {
                float mul = n.type == EVERY ? 0.4F + 0.6F * n.p[1] : 1;
                if (n.type == ON_KILL || n.type == ON_HURT) {
                    mul = 0.5F;
                }
                total += walk(i, mul, 0, new java.util.HashSet<>());
            }
        }
        return Math.max(1, Math.round(total));
    }

    private float walk(int i, float mul, int depth, java.util.Set<Integer> seen) {
        if (depth > 40 || !seen.add(i * 1000 + depth)) {
            return 0;
        }
        Node n = nodes.get(i);
        float c = actionCost(n) * mul;
        Def d = n.def();
        for (int op = 0; op < d.out.length; op++) {
            if (d.out[op] != X) {
                continue;
            }
            float m = mul;
            if (n.type == REPEAT && op == 0) {
                m *= n.p[0];
            } else if (n.type == FOR_EACH && op == 0) {
                m *= 1F + 0.5F * (n.p[1] - 1);
            } else if ((n.type == SHAPE || n.type == ART) && (op == 1 || op == 4)) {
                m *= 0.8F; // runs only when it hits / lands
            } else if (n.type == IF || n.type == CHANCE) {
                m *= 0.7F; // only one branch runs
            }
            for (int j : next(i, op)) {
                c += walk(j, m, depth + 1, seen);
            }
        }
        return c;
    }

    /** Cooldown in ticks: the slowest shape, or the mana cost when that is higher. */
    public int cooldown() {
        int cd = 10;
        for (Node n : nodes) {
            if (n.type == SHAPE) {
                cd = Math.max(cd, shapeSpell(n).cooldown());
            } else if (n.type == ART) {
                cd = Math.max(cd, SpellArts.ART_CD[Math.max(0, Math.min(SpellArts.ART.length - 1, n.p[0]))]);
            }
        }
        return Math.max(cd, 10 + Math.round(cost() * 1.2F));
    }

    /** Max charge rings of the spell (its On Cast node), 0 = instant. */
    public int maxRings() {
        for (Node n : nodes) {
            if (n.type == ON_CAST && n.p.length > 0) {
                return n.p[0];
            }
        }
        return 0;
    }

    /** The first Cast Shape node (its shape and elements are shown on the HUD), or null. */
    public Node mainShape() {
        for (Node n : nodes) {
            if (n.type == SHAPE) {
                return n;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ templates and migration
    /** An old design (shape, points, runes, trigger) as a spell: On Cast -> Cast Shape (-> on hit -> Cast Spell). */
    public static SpellGraph fromLegacy(Magic.Spell s, int[] slotElements) {
        return SpellBlocks.compile(SpellBlocks.fromLegacy(s, slotElements), s.valid() ? Magic.SHAPE[s.shape] : "Bolt");
    }

    public static final String[] TEMPLATE = SpellBlocks.TEMPLATE;

    /** Ready-made spells to learn from. */
    public static SpellGraph template(int i) {
        return SpellBlocks.compile(SpellBlocks.template(i), TEMPLATE[i]);
    }
}
