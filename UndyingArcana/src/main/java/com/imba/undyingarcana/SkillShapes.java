package com.imba.undyingarcana;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.passive.EntityTameable;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/** Hit shapes (area, cone, line), target rules, damage application and movement helpers for skills. */
public final class SkillShapes {
    private SkillShapes() {
    }

    /** Options of one hit. */
    public static final class Hit {
        public double lift;
        public double push;
        public int fire;
        public boolean stagger;
        public boolean noStatus;
        public boolean noFx;
        /** Push away from this point instead of from the caster. */
        public Vec3d from;

        public Hit lift(double v) {
            lift = v;
            return this;
        }

        public Hit push(double v) {
            push = v;
            return this;
        }

        public Hit fire(int s) {
            fire = s;
            return this;
        }

        public Hit stagger() {
            stagger = true;
            return this;
        }

        public Hit noStatus() {
            noStatus = true;
            return this;
        }

        public Hit noFx() {
            noFx = true;
            return this;
        }

        public Hit from(Vec3d v) {
            from = v;
            return this;
        }
    }

    public static Hit hit() {
        return new Hit();
    }

    /**
     * Every living thing except players, armor stands, pets tamed by a player (Beastmaster wolves...) and the
     * Caravan Ambush merchants (tag za_caravan). Animals, villagers and golems are hit too.
     */
    public static boolean isTarget(Entity e) {
        if (!(e instanceof EntityLivingBase) || e.isDead || ((EntityLivingBase) e).getHealth() <= 0) {
            return false;
        }
        if (e instanceof EntityPlayer || e instanceof EntityArmorStand || e.getTags().contains("za_caravan")) {
            return false;
        }
        if (e instanceof net.minecraft.entity.passive.AbstractHorse && ((net.minecraft.entity.passive.AbstractHorse) e).isTame()) {
            return false;
        }
        for (Entity r : e.getPassengers()) {
            if (r instanceof EntityPlayer) {
                return false;
            }
        }
        return !(e instanceof EntityTameable && ((EntityTameable) e).isTamed());
    }

    public static List<EntityLivingBase> around(World w, double x, double y, double z, double r) {
        return around(w, x, y, z, r, 2.5, 4);
    }

    /** Enemies within horizontal radius r and the given vertical band below / above y. */
    public static List<EntityLivingBase> around(World w, double x, double y, double z, double r, double down, double up) {
        List<EntityLivingBase> out = new ArrayList<>();
        AxisAlignedBB box = new AxisAlignedBB(x - r - 1, y - down, z - r - 1, x + r + 1, y + up, z + r + 1);
        for (Entity e : w.getEntitiesWithinAABB(EntityLivingBase.class, box)) {
            if (!isTarget(e)) {
                continue;
            }
            double dx = e.posX - x, dz = e.posZ - z;
            if (Math.sqrt(dx * dx + dz * dz) <= r + e.width * 0.5 + 0.3) {
                out.add((EntityLivingBase) e);
            }
        }
        return out;
    }

    /** Applies skill damage to one target with knock-up / push / fire / combo marks / element status. */
    public static boolean damage(SkillCtx c, EntityLivingBase t, float dmg, Hit h) {
        if (t.isDead || dmg <= 0) {
            return false;
        }
        float amount = dmg * SkillStatus.damageMult(c, t);
        DamageSource src;
        if (c.projectile) {
            src = new net.minecraft.util.EntityDamageSourceIndirect("arrow", c.direct != null ? c.direct : c.p, c.p).setProjectile();
        } else if (c.magic) {
            src = DamageSource.causeIndirectMagicDamage(c.p, c.p);
        } else {
            src = DamageSource.causePlayerDamage(c.p);
        }
        t.hurtResistantTime = 0;
        DamageMeter.current = c.name;
        boolean ok;
        try {
            ok = t.attackEntityFrom(src, amount);
        } finally {
            DamageMeter.current = null;
        }
        if (!ok) {
            return false;
        }
        c.hits++;
        Vec3d o = h.from != null ? h.from : new Vec3d(c.p.posX, c.p.posY, c.p.posZ);
        if (h.lift > 0) {
            t.motionY = h.lift;
            t.velocityChanged = true;
        }
        if (h.push != 0) {
            double dx = t.posX - o.x, dz = t.posZ - o.z;
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d > 0.01) {
                t.motionX = dx / d * h.push;
                t.motionZ = dz / d * h.push;
                t.velocityChanged = true;
            }
        }
        if (h.fire > 0) {
            t.setFire(h.fire);
        }
        int nowTick = (int) c.w.getTotalWorldTime();
        if (h.lift > 0 || h.stagger) {
            mark(t, "za_by_", c.p.getName(), false);
        }
        if (h.lift > 0) {
            mark(t, "za_air_", String.valueOf(nowTick + 60), true);
        }
        if (h.stagger) {
            mark(t, "za_stg_", String.valueOf(nowTick + 100), true);
        }
        if (!h.noFx) {
            Fx.at(c.w, Fx.IMPACT, c.el, t.posX, t.posY + t.height * 0.6, t.posZ, 0.8);
        }
        SkillStatus.afterHit(c, t, amount, h.noStatus);
        return true;
    }

    private static void mark(Entity e, String prefix, String value, boolean unused) {
        List<String> old = new ArrayList<>();
        for (String tg : e.getTags()) {
            if (tg.startsWith(prefix)) {
                old.add(tg);
            }
        }
        for (String tg : old) {
            e.removeTag(tg);
        }
        e.addTag(prefix + value);
    }

    /** Hits every target within a horizontal radius of (x, y, z). Returns the number hit. */
    public static int area(SkillCtx c, double x, double y, double z, double r, float dmg, Hit h) {
        int n = 0;
        for (EntityLivingBase t : around(c.w, x, y, z, r)) {
            if (damage(c, t, dmg, h)) {
                n++;
            }
        }
        return n;
    }

    /** Area hit that skips targets already in the set (and adds the new ones). */
    public static int areaOnce(SkillCtx c, double x, double y, double z, double r, float dmg, Hit h, Set<Integer> once) {
        int n = 0;
        for (EntityLivingBase t : around(c.w, x, y, z, r)) {
            if (once.add(t.getEntityId()) && damage(c, t, dmg, h)) {
                n++;
            }
        }
        return n;
    }

    /** Targets in front of the caster within radius r and +-halfAngle (radians) of the facing. */
    public static int cone(SkillCtx c, double r, double halfAngle, float dmg, Hit h) {
        int n = 0;
        double cos = Math.cos(halfAngle);
        for (EntityLivingBase t : around(c.w, c.p.posX, c.p.posY, c.p.posZ, r)) {
            double dx = t.posX - c.p.posX, dz = t.posZ - c.p.posZ;
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d < 1.2 || (dx * c.lx + dz * c.lz) / d >= cos) {
                if (damage(c, t, dmg, h)) {
                    n++;
                }
            }
        }
        return n;
    }

    /** Targets whose body is within width of the segment from + dir * len (dir normalised). */
    public static List<EntityLivingBase> lineTargets(World w, Vec3d from, Vec3d dir, double len, double width) {
        List<EntityLivingBase> out = new ArrayList<>();
        Vec3d to = from.add(dir.scale(len));
        AxisAlignedBB box = new AxisAlignedBB(from.x, from.y, from.z, to.x, to.y, to.z).grow(width + 1.5);
        for (Entity e : w.getEntitiesWithinAABB(EntityLivingBase.class, box)) {
            if (!isTarget(e)) {
                continue;
            }
            Vec3d c = new Vec3d(e.posX, e.posY + e.height * 0.5, e.posZ);
            Vec3d rel = c.subtract(from);
            double t = Math.max(0, Math.min(len, rel.dotProduct(dir)));
            Vec3d near = from.add(dir.scale(t));
            double d = c.distanceTo(near);
            if (d <= width + e.width * 0.5 + e.height * 0.25) {
                out.add((EntityLivingBase) e);
            }
        }
        return out;
    }

    public static int line(SkillCtx c, Vec3d from, Vec3d dir, double len, double width, float dmg, Hit h, Set<Integer> once) {
        int n = 0;
        for (EntityLivingBase t : lineTargets(c.w, from, dir, len, width)) {
            if ((once == null || once.add(t.getEntityId())) && damage(c, t, dmg, h)) {
                n++;
            }
        }
        return n;
    }

    public static Set<Integer> set() {
        return new HashSet<>();
    }

    /** Where the player is aiming: first block or target hit within range, else the end of the ray. */
    public static Vec3d aim(EntityPlayer p, double range) {
        Vec3d eye = new Vec3d(p.posX, p.posY + p.getEyeHeight(), p.posZ);
        Vec3d dir = p.getLookVec();
        Vec3d end = eye.add(dir.scale(range));
        RayTraceResult r = p.world.rayTraceBlocks(eye, end, false, true, false);
        double best = r != null ? r.hitVec.distanceTo(eye) : range;
        Vec3d hit = r != null ? r.hitVec : end;
        for (EntityLivingBase t : lineTargets(p.world, eye, dir, best, 0.6)) {
            double d = new Vec3d(t.posX, t.posY, t.posZ).distanceTo(eye);
            if (d < best) {
                best = d;
                hit = new Vec3d(t.posX, t.posY, t.posZ);
            }
        }
        return hit;
    }

    /** Ground height at (x, z), searching down (then up) from y. */
    public static double ground(World w, double x, double y, double z) {
        BlockPos.MutableBlockPos bp = new BlockPos.MutableBlockPos((int) Math.floor(x), (int) Math.floor(y + 0.5), (int) Math.floor(z));
        for (int i = 0; i < 12; i++) {
            IBlockState below = w.getBlockState(bp.down());
            if (below.getMaterial().blocksMovement()) {
                return bp.getY();
            }
            bp.setY(bp.getY() - 1);
        }
        return y;
    }

    private static boolean passable(World w, double x, double y, double z) {
        IBlockState s = w.getBlockState(new BlockPos(x, y, z));
        return !s.getMaterial().blocksMovement() || s.getMaterial() == Material.PLANTS;
    }

    /** Distance the player can blink along (dx, dz) before a wall (0.5 steps). */
    public static double freeDistance(EntityPlayer p, double dx, double dz, double dist) {
        double go = 0;
        for (double s = 0.5; s <= dist; s += 0.5) {
            double x = p.posX + dx * s, z = p.posZ + dz * s;
            if (passable(p.world, x, p.posY + 0.1, z) && passable(p.world, x, p.posY + 1.1, z)) {
                go = s;
            } else {
                break;
            }
        }
        return go;
    }

    /** Short teleport along (dx, dz), stopping before walls. Returns the distance moved. */
    public static double blink(EntityPlayerMP p, double dx, double dz, double dist) {
        double go = freeDistance(p, dx, dz, dist);
        if (go >= 0.5) {
            p.connection.setPlayerLocation(p.posX + dx * go, p.posY, p.posZ + dz * go, p.rotationYaw, p.rotationPitch);
        }
        return go;
    }

    /** Pushes the player (real movement, the client sees it as a dash). */
    public static void launch(EntityPlayerMP p, double vx, double vy, double vz) {
        p.motionX = vx;
        p.motionY = vy;
        p.motionZ = vz;
        p.velocityChanged = true;
    }

    /** Dash speed that covers about dist blocks (ground friction vs air drag). */
    public static double dashSpeed(EntityPlayer p, double dist) {
        return p.onGround ? dist / 2.3 : dist / 7.0;
    }

    /** Pulls a target towards a point. */
    public static void pull(Entity t, double x, double z, double strength) {
        double dx = x - t.posX, dz = z - t.posZ;
        double d = Math.sqrt(dx * dx + dz * dz);
        if (d > 0.6) {
            t.motionX = dx / d * strength;
            t.motionZ = dz / d * strength;
            t.motionY = Math.max(t.motionY, 0.1);
            t.velocityChanged = true;
        }
    }
}
