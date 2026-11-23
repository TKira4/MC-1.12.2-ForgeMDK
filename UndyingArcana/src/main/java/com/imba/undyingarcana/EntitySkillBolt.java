package com.imba.undyingarcana;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Flying skill projectile (wand bolts, skill arrows, bullets). Look 3 is drawn as a glowing arrow (RenderSkillBolt);
 * the other looks are invisible and drawn with trail particles only.
 * What happens on hit is decided by SkillMoves (kind string + numbers carried here). Never saved with the chunk.
 */
public class EntitySkillBolt extends Entity {
    private static final DataParameter<Byte> ELEMENT = EntityDataManager.createKey(EntitySkillBolt.class, DataSerializers.BYTE);
    private static final DataParameter<Byte> LOOK = EntityDataManager.createKey(EntitySkillBolt.class, DataSerializers.BYTE);
    private static final DataParameter<Float> SIZE = EntityDataManager.createKey(EntitySkillBolt.class, DataSerializers.FLOAT);
    /** Item drawn by looks 4 (spinning end over end) and 5 (spinning flat): thrown armory weapons. */
    private static final DataParameter<net.minecraft.item.ItemStack> ITEM = EntityDataManager.createKey(EntitySkillBolt.class, DataSerializers.ITEM_STACK);

    public SkillCtx ctx;      // server only
    public String move = "";  // what to do on hit (SkillMoves.boltHit / boltEnd)
    public float damage;
    public int pierce;
    public int maxLife = 40;
    public double homing;
    public double radius;
    public int fire;
    public EntityLivingBase target;
    public java.util.function.BiConsumer<EntitySkillBolt, EntityLivingBase> onHit; // custom hit (legend Arts), replaces the move's
    public java.util.function.BiConsumer<EntitySkillBolt, Vec3d> onEnd;            // custom end // preferred homing target (falls back to the nearest enemy ahead)
    /** Called every server tick before moving (returning / orbiting weapons steer here). */
    public java.util.function.Consumer<EntitySkillBolt> onTick;
    /** Flies through blocks (returning and orbiting weapons). */
    public boolean ghost;
    private final Set<Integer> hit = new HashSet<>();

    public EntitySkillBolt(World w) {
        super(w);
        setSize(0.4F, 0.4F);
        this.noClip = true;
    }

    public EntitySkillBolt(SkillCtx c, String move, int look, float size, double x, double y, double z, Vec3d vel) {
        this(c.w);
        this.ctx = c;
        this.move = move;
        setPosition(x, y, z);
        this.motionX = vel.x;
        this.motionY = vel.y;
        this.motionZ = vel.z;
        dataManager.set(ELEMENT, (byte) c.el);
        dataManager.set(LOOK, (byte) look);
        dataManager.set(SIZE, size);
    }

    @Override
    protected void entityInit() {
        dataManager.register(ELEMENT, (byte) 0);
        dataManager.register(LOOK, (byte) 0);
        dataManager.register(SIZE, 0.5F);
        dataManager.register(ITEM, net.minecraft.item.ItemStack.EMPTY);
    }

    @Override
    public void onUpdate() {
        this.lastTickPosX = this.posX;
        this.lastTickPosY = this.posY;
        this.lastTickPosZ = this.posZ;
        this.prevPosX = this.posX;
        this.prevPosY = this.posY;
        this.prevPosZ = this.posZ;
        super.onUpdate();
        if (world.isRemote) {
            UndyingArcana.proxy.boltTrail(this, dataManager.get(ELEMENT), dataManager.get(SIZE), dataManager.get(LOOK));
            setPosition(posX + motionX, posY + motionY, posZ + motionZ);
            return;
        }
        if (spent) {
            if (ticksExisted >= 3) {
                setDead();
            }
            return;
        }
        if (ctx == null || ctx.p.isDead || ticksExisted > maxLife) {
            if (ctx != null) {
                SkillMoves.boltEnd(this, new Vec3d(posX, posY, posZ));
            }
            retire();
            return;
        }
        if (onTick != null) {
            onTick.accept(this);
            if (isDead || spent) {
                return;
            }
        }
        if (homing > 0 && ticksExisted > 2) {
            steer();
        }
        Vec3d from = new Vec3d(posX, posY, posZ);
        Vec3d to = from.addVector(motionX, motionY, motionZ);
        RayTraceResult block = ghost ? null : world.rayTraceBlocks(from, to, false, true, false);
        if (block != null) {
            to = block.hitVec;
        }
        float s = dataManager.get(SIZE);
        AxisAlignedBB box = getEntityBoundingBox().expand(motionX, motionY, motionZ).grow(s + 0.5);
        for (Entity e : world.getEntitiesWithinAABBExcludingEntity(this, box)) {
            if (!SkillShapes.isTarget(e) || hit.contains(e.getEntityId())) {
                continue;
            }
            AxisAlignedBB eb = e.getEntityBoundingBox().grow(s * 0.5 + 0.2);
            if (eb.contains(from) || eb.calculateIntercept(from, to) != null) {
                hit.add(e.getEntityId());
                SkillMoves.boltHit(this, (EntityLivingBase) e);
                if (pierce-- <= 0) {
                    SkillMoves.boltEnd(this, new Vec3d(e.posX, e.posY + e.height * 0.5, e.posZ));
                    retire();
                    return;
                }
            }
        }
        if (block != null) {
            SkillMoves.boltEnd(this, block.hitVec);
            retire();
            return;
        }
        setPosition(posX + motionX, posY + motionY, posZ + motionZ);
    }

    private boolean spent;

    /**
     * Ends the bolt. A bolt that ends in its first ticks (point-blank hit) is kept as an invisible, inert entity until
     * tick 3, so the entity tracker never builds a spawn packet for an already-removed entity (log spam, wasted work).
     */
    private void retire() {
        if (ticksExisted >= 3) {
            setDead();
            return;
        }
        spent = true;
        motionX = motionY = motionZ = 0;
        velocityChanged = true;
        setInvisible(true);
    }

    /** Item shown by looks 4 / 5. */
    public void setItem(net.minecraft.item.ItemStack s) {
        net.minecraft.item.ItemStack c = s.copy();
        c.setCount(1);
        c.setTagCompound(null);
        dataManager.set(ITEM, c);
    }

    public net.minecraft.item.ItemStack item() {
        return dataManager.get(ITEM);
    }

    /** The bolt passes through this entity (shards leaving the enemy they burst from). */
    public void skip(net.minecraft.entity.Entity e) {
        hit.add(e.getEntityId());
    }

    /** Lets the bolt hit the same enemies again (returning weapons, orbits). */
    public void clearHits() {
        hit.clear();
    }

    /** Ends the bolt now (no end effect). */
    public void finish() {
        retire();
    }

    public int look() {
        return dataManager.get(LOOK);
    }

    public int element() {
        return dataManager.get(ELEMENT);
    }

    public float size() {
        return dataManager.get(SIZE);
    }

    private EntityLivingBase seek;  // fallback target, re-scanned every 3 ticks
    private Vec3d sentDir;          // last direction sent to clients

    private void steer() {
        EntityLivingBase best = null;
        if (target != null && !target.isDead && target.getHealth() > 0 && !hit.contains(target.getEntityId())) {
            best = target;
        } else if (seek != null && !seek.isDead && seek.getHealth() > 0 && !hit.contains(seek.getEntityId()) && ticksExisted % 3 != 0) {
            best = seek;
        }
        double bd = 18;
        for (EntityLivingBase t : best != null ? java.util.Collections.<EntityLivingBase>emptyList() : SkillShapes.around(world, posX, posY, posZ, 9, 16, 6)) {
            if (hit.contains(t.getEntityId())) {
                continue;
            }
            Vec3d to = new Vec3d(t.posX - posX, t.posY + t.height * 0.5 - posY, t.posZ - posZ);
            Vec3d v = new Vec3d(motionX, motionY, motionZ);
            double d = to.lengthVector();
            if (d < 18 && d < bd && v.normalize().dotProduct(to.normalize()) > 0.3) {
                bd = d;
                best = t;
            }
        }
        seek = best == target ? seek : best;
        if (best == null) {
            return;
        }
        Vec3d v = new Vec3d(motionX, motionY, motionZ);
        double speed = v.lengthVector();
        Vec3d want = new Vec3d(best.posX - posX, best.posY + best.height * 0.5 - posY, best.posZ - posZ).normalize().scale(speed);
        Vec3d nv = v.add(want.subtract(v).scale(homing)).normalize().scale(speed);
        motionX = nv.x;
        motionY = nv.y;
        motionZ = nv.z;
        // only resync clients when the heading really changed (saves a velocity packet per arrow per tick)
        Vec3d dir = nv.normalize();
        if (sentDir == null || sentDir.dotProduct(dir) < 0.9986 || ticksExisted % 4 == 0) {
            sentDir = dir;
            velocityChanged = true;
        }
    }

    @Override
    public boolean writeToNBTOptional(NBTTagCompound c) {
        return false;
    }

    @Override
    protected void readEntityFromNBT(NBTTagCompound c) {
    }

    @Override
    protected void writeEntityToNBT(NBTTagCompound c) {
    }

    @Override
    public boolean isInRangeToRenderDist(double d) {
        return d < 128 * 128;
    }
}
