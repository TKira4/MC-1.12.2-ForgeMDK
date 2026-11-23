package com.imba.undyingarcana;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.MobEffects;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import static com.imba.undyingarcana.SpellGraph.*;

/**
 * Runs Spell Weave graphs on the server. A run starts at the graph's events; exec wires are followed node by node,
 * value inputs are pulled on demand. Each cast has a budget (steps, shapes, mana given back, nested spells) so no graph
 * can lag the server or loop forever. Cast Shape hands 'on hit' / 'on end' callbacks to SpellForge.
 */
@Mod.EventBusSubscriber(modid = UndyingArcana.MODID)
public final class SpellVM {
    public static final int MAX_STEPS = 300, MAX_SHAPES = 24, MAX_DEPTH = 2, KILL_WINDOW = 200;

    private SpellVM() {
    }

    /** Callbacks from SpellForge for one Cast Shape node. */
    public interface Hooks {
        void onHit(EntityLivingBase t, Vec3d at);

        void onEnd(Vec3d at);
    }

    /** One cast of a graph (shared by everything it starts). */
    static final class Run {
        EntityPlayerMP p;
        SpellGraph g;
        int slot, depth, steps, shapes, rings;
        float mult = 1F, restored, size = 1F;
        Vec3d origin; // a spell cast by another spell starts here
        Vec3d aim;
        long until;
        final Set<Integer> hurt = new HashSet<>();
        final boolean[] called = new boolean[Magic.SPELLS];
        Run root;

        boolean budget() {
            Run r = root == null ? this : root;
            return ++r.steps <= MAX_STEPS && !p.isDead;
        }
    }

    /** Values bound along one path: node * 16 + output pin -> value. */
    static final class Frame {
        final Map<Integer, Object> v = new HashMap<>();

        Frame with(int key, Object o) {
            Frame f = new Frame();
            f.v.putAll(v);
            f.v.put(key, o);
            return f;
        }
    }

    private static final List<Run> ACTIVE = new ArrayList<>();

    // ------------------------------------------------------------------ entry points
    /** Cast: runs every On Cast node now and schedules Every X s nodes. */
    public static void cast(EntityPlayerMP p, int slot, SpellGraph g, float mult, Vec3d origin, Vec3d aim, Run parent) {
        cast(p, slot, g, mult, origin, aim, parent, 0);
    }

    /** rings: how many charge rings the player built (each +10% size; the damage part is already in mult). */
    public static void cast(EntityPlayerMP p, int slot, SpellGraph g, float mult, Vec3d origin, Vec3d aim, Run parent, int rings) {
        Run r = new Run();
        r.rings = parent != null ? parent.rings : rings;
        r.size = 1F + 0.1F * r.rings;
        r.p = p;
        r.g = g;
        r.slot = slot;
        r.mult = mult;
        r.origin = origin;
        r.aim = aim != null ? aim : SkillShapes.aim(p, 24);
        r.depth = parent == null ? 0 : parent.depth + 1;
        r.root = parent == null ? null : parent.root == null ? parent : parent.root;
        r.until = p.world.getTotalWorldTime() + KILL_WINDOW;
        long now = p.world.getTotalWorldTime();
        ACTIVE.removeIf(o -> now > o.until || o.p.isDead);
        ACTIVE.add(r);
        for (int i = 0; i < g.nodes.size(); i++) {
            Node n = g.nodes.get(i);
            if (n.type == ON_CAST) {
                Frame f = new Frame().with(i * 16 + 1, p).with(i * 16 + 2, r.aim);
                follow(r, f, i, 0);
            } else if (n.type == EVERY) {
                final int idx = i, every = n.p[0] * 2, times = n.p[1];
                SkillServer.repeat(every, every, times, k -> follow(r, new Frame().with(idx * 16 + 1, (double) k), idx, 0));
            }
        }
    }

    /** Ward spells: a graph with When Hurt runs when the player is hurt (cooldown and mana of that spell). */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onHurt(LivingHurtEvent e) {
        if (!(e.getEntityLiving() instanceof EntityPlayerMP) || e.getAmount() <= 0) {
            return;
        }
        EntityPlayerMP p = (EntityPlayerMP) e.getEntityLiving();
        if (!Stats.on(p.world) || !Magic.hasGrimoire(p)) {
            return;
        }
        Entity src = e.getSource().getTrueSource();
        for (int slot = 0; slot < Magic.spellSlots(p); slot++) {
            SpellGraph g = Magic.graph(p, slot);
            int at = -1;
            for (int i = 0; i < g.nodes.size(); i++) {
                if (g.nodes.get(i).type == ON_HURT) {
                    at = i;
                }
            }
            if (at < 0 || !Magic.payFor(p, slot, g, true)) {
                continue;
            }
            Run r = new Run();
            r.p = p;
            r.g = g;
            r.slot = slot;
            r.mult = Magic.masteryMult(p, slot);
            r.aim = src != null ? new Vec3d(src.posX, src.posY + src.height * 0.5, src.posZ) : SkillShapes.aim(p, 16);
            r.until = p.world.getTotalWorldTime() + KILL_WINDOW;
            ACTIVE.add(r);
            Frame f = new Frame();
            if (src instanceof EntityLivingBase && src != p) {
                f = f.with(at * 16 + 1, src);
            }
            follow(r, f, at, 0);
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent e) {
        EntityLivingBase dead = e.getEntityLiving();
        if (dead.world.isRemote || ACTIVE.isEmpty()) {
            return;
        }
        long now = dead.world.getTotalWorldTime();
        List<Run> fire = new ArrayList<>();
        for (Iterator<Run> it = ACTIVE.iterator(); it.hasNext(); ) {
            Run r = it.next();
            if (now > r.until || r.p.isDead) {
                it.remove();
            } else if (r.hurt.contains(dead.getEntityId())) {
                fire.add(r);
            }
        }
        for (Run r : fire) {
            for (int i = 0; i < r.g.nodes.size(); i++) {
                if (r.g.nodes.get(i).type == ON_KILL) {
                    Frame f = new Frame().with(i * 16 + 1, dead).with(i * 16 + 2, center(dead));
                    follow(r, f, i, 0);
                }
            }
        }
    }

    // ------------------------------------------------------------------ execution
    /** Runs everything wired to output pin 'op' of node 'from'. */
    static void follow(Run r, Frame f, int from, int op) {
        for (int j : r.g.next(from, op)) {
            exec(r, f, j);
        }
    }

    private static void exec(final Run r, final Frame f, final int i) {
        if (!r.budget()) {
            return;
        }
        final Node n = r.g.nodes.get(i);
        final EntityPlayerMP p = r.p;
        switch (n.type) {
            case IF: {
                Object c = in(r, f, i, 1);
                follow(r, f, i, Boolean.TRUE.equals(c) ? 0 : 1);
                break;
            }
            case CHANCE:
                follow(r, f, i, p.getRNG().nextInt(100) < n.p[0] ? 0 : 1);
                break;
            case REPEAT: {
                int times = n.p[0], gap = n.p[1];
                if (gap <= 0) {
                    for (int k = 0; k < times; k++) {
                        follow(r, f.with(i * 16 + 2, (double) k), i, 0);
                    }
                    follow(r, f, i, 1);
                } else {
                    SkillServer.repeat(0, gap, times, k -> {
                        follow(r, f.with(i * 16 + 2, (double) k), i, 0);
                        if (k == times - 1) {
                            follow(r, f, i, 1);
                        }
                    });
                }
                break;
            }
            case FOR_EACH: {
                Vec3d c = pos(in(r, f, i, 1), center(p));
                List<EntityLivingBase> l = SkillShapes.around(p.world, c.x, c.y, c.z, n.p[0]);
                l.sort((a, b) -> Double.compare(a.getDistanceSq(c.x, c.y, c.z), b.getDistanceSq(c.x, c.y, c.z)));
                for (int k = 0; k < l.size() && k < n.p[1]; k++) {
                    follow(r, f.with(i * 16 + 2, l.get(k)), i, 0);
                }
                follow(r, f, i, 1);
                break;
            }
            case DELAY:
                SkillServer.schedule(n.p[0] * 2, () -> follow(r, f, i, 0));
                break;
            case SEQUENCE:
                follow(r, f, i, 0);
                follow(r, f, i, 1);
                follow(r, f, i, 2);
                break;
            case CAST_SPELL: {
                int slot = n.p[0] - 1;
                Run root = r.root == null ? r : r.root;
                if (r.depth < MAX_DEPTH && slot >= 0 && slot < Magic.spellSlots(p) && !root.called[slot]) {
                    root.called[slot] = true;
                    SpellGraph g = Magic.graph(p, slot);
                    Vec3d from = pos(in(r, f, i, 1), null);
                    Vec3d to = pos(in(r, f, i, 2), null);
                    cast(p, slot, g, r.mult, from, to != null ? to : from, r);
                }
                follow(r, f, i, 0);
                break;
            }
            case SHAPE:
                shape(r, f, i, n);
                break;
            case ART: {
                Run root = r.root == null ? r : r.root;
                if (++root.shapes <= MAX_SHAPES) {
                    Vec3d at = pos(in(r, f, i, 1), r.aim);
                    Hooks h = new Hooks() {
                        @Override
                        public void onHit(EntityLivingBase t, Vec3d hitAt) {
                            r.hurt.add(t.getEntityId());
                            follow(r, f.with(i * 16 + 2, t), i, 1);
                        }

                        @Override
                        public void onEnd(Vec3d end) {
                            follow(r, f.with(i * 16 + 4, end), i, 3);
                        }
                    };
                    SpellArts.cast(p, n.p[0], r.slot, r.mult, at, h);
                }
                follow(r, f, i, 0);
                break;
            }
            case STATUSA: {
                EntityLivingBase t = ent(in(r, f, i, 1));
                if (t != null && t != p) {
                    SkillCtx c = ctx(r, n.p[0]);
                    if (c != null) {
                        SkillStatus.addStatus(c, t, 3F);
                        Fx.at(p.world, Fx.IMPACT, c.el, t.posX, t.posY + t.height * 0.5, t.posZ, 0.6);
                        r.hurt.add(t.getEntityId());
                    }
                }
                follow(r, f, i, 0);
                break;
            }
            case DAMAGE: {
                EntityLivingBase t = ent(in(r, f, i, 1));
                SkillCtx c = ctx(r, 0);
                if (t != null && t != p && c != null) {
                    c.status = "";
                    SkillShapes.damage(c, t, c.dmg(n.p[0], false), SkillShapes.hit());
                    Fx.at(p.world, Fx.IMPACT, c.el, t.posX, t.posY + t.height * 0.5, t.posZ, 0.5);
                    r.hurt.add(t.getEntityId());
                }
                follow(r, f, i, 0);
                break;
            }
            case HEAL: {
                EntityLivingBase t = ent(in(r, f, i, 1));
                if (t == null) {
                    t = p;
                }
                if (ally(p, t)) {
                    t.heal(n.p[0] * (1F + 0.01F * Stats.stats(p)[Builds.MAG]));
                    Fx.on(t, Fx.HEAL, Fx.HOLY, 1.0, 0, 10);
                }
                follow(r, f, i, 0);
                break;
            }
            case SHIELD: {
                EntityLivingBase t = ent(in(r, f, i, 1));
                if (t == null) {
                    t = p;
                }
                if (ally(p, t)) {
                    t.addPotionEffect(new PotionEffect(MobEffects.ABSORPTION, n.p[1] * 20, n.p[0] - 1, false, true));
                    Fx.on(t, Fx.HEAL, Fx.EARTH, 1.2, 0, 10);
                }
                follow(r, f, i, 0);
                break;
            }
            case BLINK: {
                Vec3d to = pos(in(r, f, i, 1), r.aim);
                blink(p, to, n.p[0]);
                follow(r, f, i, 0);
                break;
            }
            case PUSH: {
                EntityLivingBase t = ent(in(r, f, i, 1));
                if (t != null && t != p) {
                    Vec3d from = pos(in(r, f, i, 2), center(p));
                    Vec3d d = new Vec3d(t.posX - from.x, 0, t.posZ - from.z);
                    if (d.lengthSquared() < 1e-4) {
                        d = new Vec3d(p.getLookVec().x, 0, p.getLookVec().z);
                    }
                    d = d.normalize().scale(n.p[0] * 0.18);
                    t.motionX += d.x;
                    t.motionZ += d.z;
                    t.motionY += Math.abs(n.p[0]) * 0.03;
                    t.velocityChanged = true;
                }
                follow(r, f, i, 0);
                break;
            }
            case ZONE: {
                final Vec3d at = pos(in(r, f, i, 1), r.aim);
                final SkillCtx c = ctx(r, n.p[0]);
                if (c != null) {
                    final double rad = n.p[1];
                    final float d = c.dmg(1.2, true);
                    Fx.send(p.world, Fx.FIELD, c.el, at.x, at.y, at.z, 0, 0, 0, rad * 0.8, 0, n.p[2] * 20, -1);
                    SkillServer.repeat(0, 10, n.p[2] * 2, k -> {
                        for (EntityLivingBase t : SkillShapes.around(p.world, at.x, at.y, at.z, rad)) {
                            if (SkillShapes.damage(c, t, d, SkillShapes.hit())) {
                                r.hurt.add(t.getEntityId());
                            }
                        }
                    });
                }
                follow(r, f, i, 0);
                break;
            }
            case RESTORE: {
                Run root = r.root == null ? r : r.root;
                float give = Math.min(n.p[0], 30F - root.restored);
                if (give > 0) {
                    root.restored += give;
                    Power.setMana(p, Power.mana(p) + give);
                }
                follow(r, f, i, 0);
                break;
            }
            default:
                break;
        }
    }

    private static void shape(final Run r, final Frame f, final int i, final Node n) {
        final EntityPlayerMP p = r.p;
        Run root = r.root == null ? r : r.root;
        if (++root.shapes > MAX_SHAPES) {
            return;
        }
        Magic.Spell sp = SpellGraph.shapeSpell(n);
        SkillCtx c = Magic.prepareEl(p, r.slot, sp, n.p[1], n.p[2]);
        if (c == null) {
            return;
        }
        c.dmgMult *= r.mult;
        c.size *= r.size;
        Vec3d from = pos(in(r, f, i, 1), r.origin);
        EntityLivingBase tgt = ent(in(r, f, i, 3));
        Vec3d to = tgt != null ? center(tgt) : pos(in(r, f, i, 2), null);
        boolean self = sp.shape == 3 || sp.shape == 6 || sp.shape == 10 || sp.shape == 11 || sp.shape == 12 || sp.shape == 13;
        if (self && from == null) {
            to = null; // centred on the caster: a normal cast (Blink teleports, Nova rings around you...)
        }
        if (to == null && from != null) {
            to = r.aim;
        }
        if (from == null && to != null) {
            from = new Vec3d(p.posX, p.posY + p.getEyeHeight() - 0.2, p.posZ);
        }
        int el = Magic.slotOf(p, n.p[1]) >= 0 ? n.p[1] : Magic.element(p, 0);
        int el2 = n.p[2] >= 0 && Magic.slotOf(p, n.p[2]) >= 0 ? n.p[2] : -1;
        String e2 = el2 >= 0 && el2 != el ? Magic.ELEMENT[el2] : "";
        Hooks h = new Hooks() {
            @Override
            public void onHit(EntityLivingBase t, Vec3d at) {
                r.hurt.add(t.getEntityId());
                follow(r, f.with(i * 16 + 2, t).with(i * 16 + 3, at), i, 1);
            }

            @Override
            public void onEnd(Vec3d at) {
                follow(r, f.with(i * 16 + 5, at), i, 4);
            }
        };
        SpellForge.castShape(c, sp, e2, from, to, h);
        follow(r, f, i, 0);
    }

    /** Skill context for an element (id) of this player (null without any element). */
    private static SkillCtx ctx(Run r, int element) {
        Magic.Spell s = new Magic.Spell();
        s.shape = 0;
        SkillCtx c = Magic.prepareEl(r.p, r.slot, s, element, -1);
        if (c != null) {
            c.dmgMult *= r.mult;
        }
        return c;
    }

    // ------------------------------------------------------------------ values
    /** Value of input pin k of node i (null when not wired / not available). */
    static Object in(Run r, Frame f, int i, int k) {
        int l = r.g.nodes.get(i).link[k];
        if (l < 0) {
            return null;
        }
        return value(r, f, l / 16, l % 16, 0);
    }

    private static Object value(Run r, Frame f, int src, int op, int depth) {
        if (depth > 24 || src < 0 || src >= r.g.nodes.size()) {
            return null;
        }
        Node n = r.g.nodes.get(src);
        if (n.def().cat != VALUE) {
            return f.v.get(src * 16 + op);
        }
        EntityPlayerMP p = r.p;
        switch (n.type) {
            case CASTER:
                return p;
            case AIM:
                return SkillShapes.aim(p, n.p[0]);
            case POS_OF: {
                EntityLivingBase t = ent(arg(r, f, src, 0, depth));
                return t == null ? null : center(t);
            }
            case NEAREST: {
                Vec3d c = pos(arg(r, f, src, 0, depth), center(p));
                EntityLivingBase skip = ent(arg(r, f, src, 1, depth));
                EntityLivingBase best = null;
                double bd = Double.MAX_VALUE;
                for (EntityLivingBase t : SkillShapes.around(p.world, c.x, c.y, c.z, n.p[0])) {
                    double q = t.getDistanceSq(c.x, c.y, c.z);
                    if (t != skip && q < bd) {
                        bd = q;
                        best = t;
                    }
                }
                return best;
            }
            case HP: {
                EntityLivingBase t = ent(arg(r, f, src, 0, depth));
                return t == null ? null : (double) (t.getHealth() * 100F / Math.max(1F, t.getMaxHealth()));
            }
            case DIST: {
                Vec3d a = pos(arg(r, f, src, 0, depth), center(p)), b = pos(arg(r, f, src, 1, depth), center(p));
                return a.distanceTo(b);
            }
            case HAS_STATUS: {
                EntityLivingBase t = ent(arg(r, f, src, 0, depth));
                return t != null && SkillStatus.is(t, n.p[0]);
            }
            case COUNT: {
                Vec3d c = pos(arg(r, f, src, 0, depth), center(p));
                return (double) SkillShapes.around(p.world, c.x, c.y, c.z, n.p[0]).size();
            }
            case MANA:
                return (double) (Power.mana(p) * 100F / Math.max(1, Power.maxMana(p)));
            case NUMBER:
                return (double) n.p[0];
            case COMPARE: {
                Double a = num(arg(r, f, src, 0, depth));
                Double b = num(arg(r, f, src, 1, depth));
                if (a == null) {
                    return false;
                }
                double bb = b == null ? n.p[1] : b;
                switch (n.p[0]) {
                    case 0: return a < bb;
                    case 1: return a > bb;
                    case 2: return Math.abs(a - bb) < 0.5;
                    case 3: return a <= bb;
                    default: return a >= bb;
                }
            }
            case MATHS: {
                Double a = num(arg(r, f, src, 0, depth)), b = num(arg(r, f, src, 1, depth));
                double x = a == null ? 0 : a, y = b == null ? 0 : b;
                switch (n.p[0]) {
                    case 0: return x + y;
                    case 1: return x - y;
                    case 2: return x * y;
                    case 3: return y == 0 ? 0 : x / y;
                    case 4: return Math.min(x, y);
                    default: return Math.max(x, y);
                }
            }
            case LOGICS: {
                boolean a = Boolean.TRUE.equals(arg(r, f, src, 0, depth)), b = Boolean.TRUE.equals(arg(r, f, src, 1, depth));
                return n.p[0] == 0 ? a && b : n.p[0] == 1 ? a || b : !a;
            }
            case RANDOM:
                return (double) p.getRNG().nextInt(101);
            case WORLDQ:
                switch (n.p[0]) {
                    case 0: return !p.world.isDaytime();
                    case 1: return p.world.isRaining();
                    case 2: return p.isSneaking();
                    case 3: return p.isInWater();
                    case 4: return p.onGround;
                    default: return p.getHealth() < p.getMaxHealth() * 0.5F;
                }
            case IS_BOSS: {
                EntityLivingBase t = ent(arg(r, f, src, 0, depth));
                return t != null && (!t.isNonBoss() || t instanceof EntityLSBoss);
            }
            case RINGS: {
                Run root = r.root == null ? r : r.root;
                return (double) Math.max(r.rings, root.rings);
            }
            case OFFSET: {
                Vec3d a = pos(arg(r, f, src, 0, depth), center(p));
                Vec3d look = p.getLookVec();
                Vec3d h = new Vec3d(look.x, 0, look.z);
                h = h.lengthSquared() < 1e-4 ? new Vec3d(0, 0, 0) : h.normalize();
                return a.addVector(h.x * n.p[1], n.p[0], h.z * n.p[1]);
            }
            default:
                return null;
        }
    }

    private static Object arg(Run r, Frame f, int node, int k, int depth) {
        int l = r.g.nodes.get(node).link[k];
        return l < 0 ? null : value(r, f, l / 16, l % 16, depth + 1);
    }

    static Vec3d center(Entity e) {
        return new Vec3d(e.posX, e.posY + e.height * 0.5, e.posZ);
    }

    private static EntityLivingBase ent(Object o) {
        return o instanceof EntityLivingBase && !((EntityLivingBase) o).isDead ? (EntityLivingBase) o : null;
    }

    private static Vec3d pos(Object o, Vec3d def) {
        if (o instanceof Vec3d) {
            return (Vec3d) o;
        }
        if (o instanceof EntityLivingBase) {
            return center((EntityLivingBase) o);
        }
        return def;
    }

    private static Double num(Object o) {
        return o instanceof Double ? (Double) o : null;
    }

    private static boolean ally(EntityPlayer p, EntityLivingBase t) {
        return t == p || t instanceof EntityPlayer || !(t instanceof IMob) && t instanceof net.minecraft.entity.passive.EntityTameable
                && ((net.minecraft.entity.passive.EntityTameable) t).isOwner(p);
    }

    /** Teleport toward a point, up to max blocks, onto the last safe spot on the way. */
    private static void blink(EntityPlayerMP p, Vec3d to, int max) {
        Vec3d from = new Vec3d(p.posX, p.posY, p.posZ);
        Vec3d d = new Vec3d(to.x - from.x, 0, to.z - from.z);
        double len = Math.min(max, d.lengthVector());
        if (len < 0.5) {
            return;
        }
        d = d.normalize();
        Vec3d best = null;
        for (double s = 0.5; s <= len; s += 0.5) {
            Vec3d at = from.add(d.scale(s));
            BlockPos b = new BlockPos(at.x, at.y + 0.1, at.z);
            if (!p.world.isAirBlock(b) && p.world.getBlockState(b).getMaterial().blocksMovement()
                    || !p.world.isAirBlock(b.up()) && p.world.getBlockState(b.up()).getMaterial().blocksMovement()) {
                break;
            }
            best = at;
        }
        if (best != null) {
            Fx.send(p.world, Fx.DASH, Fx.SHADOW, from.x, from.y, from.z, best.x, best.y, best.z, 0, 0, 4, -1);
            p.setPositionAndUpdate(best.x, best.y, best.z);
            p.fallDistance = 0;
        }
    }
}
