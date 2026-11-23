package com.imba.undyingarcana;

import java.util.List;
import java.util.UUID;

import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityAreaEffectCloud;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.EnumCreatureAttribute;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAIAttackMelee;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.ai.EntityAIHurtByTarget;
import net.minecraft.entity.ai.EntityAILookIdle;
import net.minecraft.entity.ai.EntityAINearestAttackableTarget;
import net.minecraft.entity.ai.EntityAISwimming;
import net.minecraft.entity.ai.EntityAIWanderAvoidWater;
import net.minecraft.entity.ai.EntityAIWatchClosest;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.MobEffects;
import net.minecraft.init.SoundEvents;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.pathfinding.PathNavigateGround;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.storage.loot.LootTableList;

/**
 * One entity for every zombie type (ZType in a synced byte), so one renderer, one AI set and one spawner cover them all.
 *
 * Performance: full AI within 32 blocks of a player, every 2nd tick from 32 to 64, frozen beyond 64 (unless hurt
 * recently); director zombies despawn past 96 blocks and burn away at dawn.
 */
public class EntityUAZombie extends EntityMob {
    private static final DataParameter<Byte> TYPE = EntityDataManager.createKey(EntityUAZombie.class, DataSerializers.BYTE);
    private static final DataParameter<Byte> AFFIX = EntityDataManager.createKey(EntityUAZombie.class, DataSerializers.BYTE);

    public static final int SHIELDED = 1, SWIFT = 2, VAMPIRIC = 4, SPLITTER = 8, BOMBER = 16, WARDED = 32;
    public static final String[] AFFIX_NAMES = {"Shielded", "Swift", "Vampiric", "Splitter", "Bomber", "Warded"};

    /** Spawned by the director at night (burns away at dawn) / during a Blood Moon. */
    public boolean night, bloodMoon;
    public UUID owner; // necromancer that raised this one
    private boolean scaled;
    private int week;
    private BlockPos investigate;
    private long investigateUntil;
    private double nearSq = 0;
    private long lastHurt = -1000;
    private long nextSkill, fuseEnd = -1;
    private boolean shieldRefilled;
    // stuck detection for block breaking
    private double lastX, lastZ;
    int stuck;

    public EntityUAZombie(World w) {
        super(w);
        setSize(0.6F, 1.95F);
        ((PathNavigateGround) getNavigator()).setBreakDoors(true);
    }

    // ------------------------------------------------------------------ type, affixes, setup
    @Override
    protected void entityInit() {
        super.entityInit();
        dataManager.register(TYPE, (byte) 0);
        dataManager.register(AFFIX, (byte) 0);
    }

    public ZType type() {
        return ZType.of(dataManager.get(TYPE));
    }

    public int affixes() {
        return dataManager.get(AFFIX);
    }

    public boolean has(int affix) {
        return (affixes() & affix) != 0;
    }

    public boolean isElite() {
        return affixes() != 0;
    }

    @Override
    public void notifyDataManagerChange(DataParameter<?> key) {
        super.notifyDataManagerChange(key);
        if (TYPE.equals(key)) {
            ZType t = type();
            setSize(t.width, t.height);
        }
    }

    /** Sets type, affixes and director flags, then scales stats to the world's week (once). */
    public EntityUAZombie setup(ZType t, int affixes, boolean night, boolean bloodMoon) {
        dataManager.set(TYPE, (byte) t.ordinal());
        dataManager.set(AFFIX, (byte) affixes);
        setSize(t.width, t.height);
        this.night = night;
        this.bloodMoon = bloodMoon;
        scaled = false;
        scale();
        return this;
    }

    public static long week(World w) {
        return Math.min(12L, w.getWorldTime() / 168000L);
    }

    private void scale() {
        if (scaled || world.isRemote) {
            return;
        }
        scaled = true;
        ZType t = type();
        week = (int) week(world);
        double hp = t.health * (1.0D + 0.12D * week) * (isElite() ? 2.0D : 1.0D);
        getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(hp);
        getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).setBaseValue(t.damage * (1.0D + 0.06D * week) * (isElite() ? 1.3D : 1.0D));
        getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(t.speed * (has(SWIFT) ? 1.4D : 1.0D));
        getEntityAttribute(SharedMonsterAttributes.ARMOR).setBaseValue(Math.min(30.0D, t.armor + Math.min(6.0D, 0.5D * week)));
        getEntityAttribute(SharedMonsterAttributes.ARMOR_TOUGHNESS).setBaseValue(t.toughness);
        getEntityAttribute(SharedMonsterAttributes.KNOCKBACK_RESISTANCE).setBaseValue(t.kbResist);
        getEntityAttribute(SharedMonsterAttributes.FOLLOW_RANGE).setBaseValue(bloodMoon ? 64.0D : 35.0D);
        setHealth(getMaxHealth());
        if (has(SHIELDED)) {
            setAbsorptionAmount((float) hp * 0.4F);
        }
        experienceValue = t.xp * (isElite() ? 3 : 1);
        if (isElite()) {
            StringBuilder sb = new StringBuilder("\u00a76");
            for (int i = 0; i < AFFIX_NAMES.length; i++) {
                if ((affixes() & (1 << i)) != 0) {
                    sb.append(AFFIX_NAMES[i]).append(' ');
                }
            }
            setCustomNameTag(sb.append(t.display()).toString());
        }
    }

    /** Magic damage taken is cut by this share (before Spell Pierce / Hex). */
    public float magicResist() {
        return Math.min(0.8F, type().magicResist + Math.min(0.25F, 0.02F * week) * (type().magicResist > 0 ? 1 : 0)
                + (has(WARDED) ? 0.4F : 0.0F));
    }

    // ------------------------------------------------------------------ attributes and AI
    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        getEntityAttribute(SharedMonsterAttributes.FOLLOW_RANGE).setBaseValue(35.0D);
        getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(0.22D);
        getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).setBaseValue(4.0D);
        getEntityAttribute(SharedMonsterAttributes.ARMOR).setBaseValue(2.0D);
    }

    @Override
    protected void initEntityAI() {
        tasks.addTask(0, new EntityAISwimming(this));
        tasks.addTask(1, new AIBreakThrough(this));
        tasks.addTask(2, new EntityAIAttackMelee(this, 1.0D, false));
        tasks.addTask(4, new AIInvestigate(this));
        tasks.addTask(7, new EntityAIWanderAvoidWater(this, 0.8D));
        tasks.addTask(8, new EntityAIWatchClosest(this, EntityPlayer.class, 8.0F));
        tasks.addTask(8, new EntityAILookIdle(this));
        targetTasks.addTask(1, new EntityAIHurtByTarget(this, true));
        targetTasks.addTask(2, new EntityAINearestAttackableTarget<>(this, EntityPlayer.class, true));
        targetTasks.addTask(3, new EntityAINearestAttackableTarget<>(this, EntityVillager.class, false));
    }

    /** Distance level of detail: blocks the whole AI step on skipped ticks. */
    @Override
    protected boolean isMovementBlocked() {
        if (super.isMovementBlocked()) {
            return true;
        }
        if (world.isRemote || world.getTotalWorldTime() - lastHurt < 100) {
            return false;
        }
        if (nearSq > 64 * 64) {
            return true;
        }
        return nearSq > 32 * 32 && (ticksExisted & 1) == 0;
    }

    @Override
    public void onLivingUpdate() {
        if (!world.isRemote && ticksExisted % 10 == 0) {
            EntityPlayer p = world.getClosestPlayerToEntity(this, -1.0D);
            nearSq = p == null ? Double.MAX_VALUE : getDistanceSq(p);
            if (ticksExisted % 20 == 0) {
                directorRules();
            }
        }
        super.onLivingUpdate();
    }

    private void directorRules() {
        if (!night && !bloodMoon || isNoDespawnRequired()) {
            return;
        }
        if (nearSq > 96 * 96) {
            setDead();
            return;
        }
        long t = world.getWorldTime() % 24000L;
        if (t < 12000L && world.canSeeSky(new BlockPos(posX, posY + getEyeHeight(), posZ))) {
            setFire(8); // dawn: night zombies burn away in the open
            if (nearSq > 24 * 24 && rand.nextInt(10) == 0) {
                setDead();
            }
        }
    }

    @Override
    protected boolean canDespawn() {
        return !isElite() || night || bloodMoon;
    }

    public void investigate(BlockPos pos, int ticks) {
        investigate = pos;
        investigateUntil = world.getTotalWorldTime() + ticks;
    }

    // ------------------------------------------------------------------ abilities
    @Override
    protected void updateAITasks() {
        super.updateAITasks();
        scale();
        long now = world.getTotalWorldTime();
        if (ticksExisted % 20 == 0) {
            double moved = (posX - lastX) * (posX - lastX) + (posZ - lastZ) * (posZ - lastZ);
            stuck = getAttackTarget() != null && moved < 0.04D ? stuck + 1 : 0;
            lastX = posX;
            lastZ = posZ;
        }
        if (has(SHIELDED) && !shieldRefilled && getHealth() < getMaxHealth() * 0.5F) {
            shieldRefilled = true;
            setAbsorptionAmount(getMaxHealth() * 0.3F);
            Fx.at(world, Fx.M_DOME, Fx.HOLY, posX, posY + height * 0.5, posZ, width * 1.6);
        }
        EntityLivingBase target = getAttackTarget();
        if (target == null || !target.isEntityAlive()) {
            return;
        }
        double d2 = getDistanceSq(target);
        switch (type()) {
            case RUNNER:
                if (now >= nextSkill && onGround && d2 > 9 && d2 < 64 && canEntityBeSeen(target)) {
                    Vec3d v = new Vec3d(target.posX - posX, 0, target.posZ - posZ).normalize();
                    motionX = v.x * 0.75;
                    motionZ = v.z * 0.75;
                    motionY = 0.42;
                    nextSkill = now + 60;
                }
                break;
            case BRUTE:
                if (now >= nextSkill && d2 < 16 && onGround) {
                    slam();
                    nextSkill = now + 160;
                }
                break;
            case SCREAMER:
                if (now >= nextSkill && d2 < 24 * 24 && canEntityBeSeen(target)) {
                    Fx.sound(world, posX, posY, posZ, "minecraft:entity.ghast.scream", 2.5F, 0.55F);
                    Fx.at(world, Fx.RING, Fx.SHADOW, posX, posY + height * 0.85, posZ, 3.0);
                    swingArm(net.minecraft.util.EnumHand.MAIN_HAND);
                    ZombieDirector.noise(world, posX, posY, posZ, 32.0D, target, true);
                    nextSkill = now + 300;
                }
                break;
            case SPITTER:
                if (now >= nextSkill && d2 > 9 && d2 < 16 * 16 && canEntityBeSeen(target)) {
                    EntityAcidSpit s = new EntityAcidSpit(world, this, (float) getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).getAttributeValue() * 1.4F);
                    double dx = target.posX - posX, dz = target.posZ - posZ;
                    double dy = target.posY + target.height * 0.5 - s.posY;
                    s.shoot(dx, dy + Math.sqrt(dx * dx + dz * dz) * 0.12D, dz, 1.1F, 4.0F);
                    world.spawnEntity(s);
                    Fx.sound(world, posX, posY, posZ, "minecraft:entity.llama.spit", 1.2F, 0.7F);
                    swingArm(net.minecraft.util.EnumHand.MAIN_HAND);
                    nextSkill = now + 50;
                }
                break;
            case BLOATER:
                if (fuseEnd < 0 && d2 < 2.4 * 2.4) {
                    fuseEnd = now + 20;
                    Fx.sound(world, posX, posY, posZ, "minecraft:entity.creeper.primed", 1.2F, 0.6F);
                    Fx.at(world, Fx.GATHER, Fx.POISON, posX, posY + height * 0.5, posZ, 1.5);
                } else if (fuseEnd > 0 && now >= fuseEnd) {
                    burst(true);
                    setDead();
                }
                break;
            case NECROMANCER:
                if (now >= nextSkill && d2 < 20 * 20) {
                    raiseDead();
                    nextSkill = now + 300;
                }
                break;
            default:
                break;
        }
    }

    private void slam() {
        float dmg = (float) getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).getAttributeValue() * 0.8F;
        swingArm(net.minecraft.util.EnumHand.MAIN_HAND);
        Fx.at(world, Fx.RING, Fx.EARTH, posX, posY + 0.1, posZ, 4.0);
        Fx.at(world, Fx.DEBRIS, Fx.EARTH, posX, posY + 0.1, posZ, 2.0);
        Fx.sound(world, posX, posY, posZ, "minecraft:entity.generic.explode", 1.0F, 0.6F);
        Fx.shake(world, posX, posY, posZ, 12.0, 0.8F, 8);
        for (EntityLivingBase e : world.getEntitiesWithinAABB(EntityLivingBase.class, getEntityBoundingBox().grow(4.0D, 1.5D, 4.0D))) {
            if (e == this || e instanceof EntityUAZombie) {
                continue;
            }
            e.attackEntityFrom(DamageSource.causeMobDamage(this), dmg);
            Vec3d v = new Vec3d(e.posX - posX, 0, e.posZ - posZ).normalize();
            if (e instanceof EntityPlayerMP) {
                SkillShapes.launch((EntityPlayerMP) e, v.x * 0.9, 0.6, v.z * 0.9);
            } else {
                e.addVelocity(v.x * 0.9, 0.6, v.z * 0.9);
            }
        }
    }

    /** Poison cloud (and a blast when it was a fuse, not a death). */
    private void burst(boolean blast) {
        if (blast) {
            float dmg = 4.0F + 0.5F * week;
            for (EntityLivingBase e : world.getEntitiesWithinAABB(EntityLivingBase.class, getEntityBoundingBox().grow(3.0D))) {
                if (e != this && !(e instanceof EntityUAZombie)) {
                    e.attackEntityFrom(DamageSource.causeMobDamage(this), dmg);
                }
            }
        }
        EntityAreaEffectCloud c = new EntityAreaEffectCloud(world, posX, posY, posZ);
        c.setRadius(3.5F);
        c.setRadiusOnUse(-0.3F);
        c.setWaitTime(5);
        c.setDuration(160);
        c.setRadiusPerTick(-c.getRadius() / c.getDuration());
        c.setColor(0x7FBF3F);
        c.addEffect(new PotionEffect(MobEffects.POISON, 100, 1));
        world.spawnEntity(c);
        Fx.at(world, Fx.EXPLOSION, Fx.POISON, posX, posY + 0.8, posZ, 1.6);
        Fx.sound(world, posX, posY, posZ, "minecraft:entity.slime.squish", 2.0F, 0.5F);
    }

    private void raiseDead() {
        UUID me = getUniqueID();
        List<EntityUAZombie> mine = world.getEntitiesWithinAABB(EntityUAZombie.class, getEntityBoundingBox().grow(16.0D), z -> me.equals(z.owner));
        if (mine.size() >= 4) {
            return;
        }
        Fx.at(world, Fx.RUNES, Fx.NECRO, posX, posY + 0.1, posZ, 2.5);
        Fx.sound(world, posX, posY, posZ, "minecraft:entity.evocation_illager.prepare_summon", 1.2F, 0.7F);
        swingArm(net.minecraft.util.EnumHand.OFF_HAND);
        for (int i = 0; i < 2; i++) {
            double a = rand.nextDouble() * Math.PI * 2;
            BlockPos pos = new BlockPos(posX + Math.cos(a) * 3, posY, posZ + Math.sin(a) * 3);
            if (!world.isAirBlock(pos) || !world.isAirBlock(pos.up()) || world.isAirBlock(pos.down())) {
                continue;
            }
            EntityUAZombie z = new EntityUAZombie(world);
            z.setLocationAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, rand.nextFloat() * 360F, 0);
            z.setup(ZType.WALKER, 0, true, bloodMoon);
            z.owner = me;
            z.setAttackTarget(getAttackTarget());
            world.spawnEntity(z);
            Fx.at(world, Fx.PILLAR, Fx.NECRO, z.posX, z.posY, z.posZ, 1.0);
        }
    }

    @Override
    public boolean attackEntityAsMob(Entity target) {
        boolean hit = super.attackEntityAsMob(target);
        if (!hit || !(target instanceof EntityLivingBase)) {
            return hit;
        }
        if (has(VAMPIRIC)) {
            heal((float) getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).getAttributeValue() * 0.4F);
            Fx.at(world, Fx.HEAL, Fx.BLOOD, posX, posY + height * 0.6, posZ, 0.6);
        }
        if (type() == ZType.BRUTE) {
            Vec3d v = new Vec3d(target.posX - posX, 0, target.posZ - posZ).normalize();
            if (target instanceof EntityPlayerMP) {
                SkillShapes.launch((EntityPlayerMP) target, target.motionX + v.x * 1.3, 0.45, target.motionZ + v.z * 1.3);
            } else {
                target.addVelocity(v.x * 1.3, 0.45, v.z * 1.3);
            }
        }
        return true;
    }

    @Override
    public boolean attackEntityFrom(DamageSource source, float amount) {
        if (!world.isRemote && source.getTrueSource() != null) {
            lastHurt = world.getTotalWorldTime();
        }
        return super.attackEntityFrom(source, amount);
    }

    @Override
    public void onDeath(DamageSource cause) {
        super.onDeath(cause);
        if (world.isRemote) {
            return;
        }
        if (type() == ZType.BLOATER && fuseEnd < 0) {
            burst(false);
        }
        if (has(BOMBER)) {
            world.newExplosion(this, posX, posY + 0.5, posZ, 2.5F, false, false);
        }
        if (has(SPLITTER)) {
            for (int i = 0; i < 2; i++) {
                EntityUAZombie z = new EntityUAZombie(world);
                z.setLocationAndAngles(posX + rand.nextGaussian() * 0.5, posY, posZ + rand.nextGaussian() * 0.5, rand.nextFloat() * 360F, 0);
                z.setup(ZType.RUNNER, 0, night, bloodMoon);
                z.setAttackTarget(getAttackTarget());
                world.spawnEntity(z);
            }
        }
    }

    @Override
    protected void dropLoot(boolean wasRecentlyHit, int looting, DamageSource source) {
        super.dropLoot(wasRecentlyHit, looting, source);
        if (isElite()) {
            super.dropLoot(wasRecentlyHit, looting, source);
        }
    }

    @Override
    protected ResourceLocation getLootTable() {
        return owner != null ? null : LootTableList.ENTITIES_ZOMBIE;
    }

    /** Runners and crawlers climb walls toward their target, like spiders. */
    @Override
    public boolean isOnLadder() {
        ZType t = type();
        return (t == ZType.RUNNER || t == ZType.CRAWLER) && collidedHorizontally && getAttackTarget() != null
                && getAttackTarget().posY > posY + 0.5 || super.isOnLadder();
    }

    @Override
    public String getName() {
        return hasCustomName() ? getCustomNameTag() : type().display();
    }

    @Override
    public EnumCreatureAttribute getCreatureAttribute() {
        return EnumCreatureAttribute.UNDEAD;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return type() == ZType.CRAWLER ? null : SoundEvents.ENTITY_ZOMBIE_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ENTITY_ZOMBIE_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ENTITY_ZOMBIE_DEATH;
    }

    @Override
    protected float getSoundPitch() {
        return super.getSoundPitch() * (type() == ZType.BRUTE ? 0.6F : type() == ZType.SCREAMER ? 1.4F : 1.0F);
    }

    @Override
    public float getEyeHeight() {
        return type() == ZType.CRAWLER ? 0.5F : height * 0.88F;
    }

    // ------------------------------------------------------------------ save
    @Override
    public void writeEntityToNBT(NBTTagCompound tag) {
        super.writeEntityToNBT(tag);
        tag.setString("UAType", type().id());
        tag.setByte("UAAffix", (byte) affixes());
        tag.setBoolean("UANight", night);
        tag.setBoolean("UABloodMoon", bloodMoon);
        tag.setBoolean("UAScaled", scaled);
        tag.setInteger("UAWeek", week);
        if (owner != null) {
            tag.setUniqueId("UAOwner", owner);
        }
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound tag) {
        super.readEntityFromNBT(tag);
        ZType t = ZType.byId(tag.getString("UAType"));
        dataManager.set(TYPE, (byte) (t == null ? 0 : t.ordinal()));
        dataManager.set(AFFIX, tag.getByte("UAAffix"));
        night = tag.getBoolean("UANight");
        bloodMoon = tag.getBoolean("UABloodMoon");
        scaled = tag.getBoolean("UAScaled") || tag.hasKey("UAType") && !tag.hasKey("UAScaled");
        week = tag.getInteger("UAWeek");
        owner = tag.hasUniqueId("UAOwner") ? tag.getUniqueId("UAOwner") : null;
        ZType tt = type();
        setSize(tt.width, tt.height);
    }

    // ------------------------------------------------------------------ AI tasks
    /** Walks to the last noise heard (gunshot, explosion, sprinting, a scream) while it has no target. */
    static class AIInvestigate extends EntityAIBase {
        private final EntityUAZombie z;

        AIInvestigate(EntityUAZombie z) {
            this.z = z;
            setMutexBits(1);
        }

        @Override
        public boolean shouldExecute() {
            return z.getAttackTarget() == null && z.investigate != null && z.investigateUntil > z.world.getTotalWorldTime();
        }

        @Override
        public void startExecuting() {
            z.getNavigator().tryMoveToXYZ(z.investigate.getX() + 0.5, z.investigate.getY(), z.investigate.getZ() + 0.5, 1.0D);
        }

        @Override
        public boolean shouldContinueExecuting() {
            return z.getAttackTarget() == null && !z.getNavigator().noPath() && z.investigateUntil > z.world.getTotalWorldTime();
        }

        @Override
        public void resetTask() {
            z.investigate = null;
        }
    }

    /**
     * When stuck on the way to its target, breaks the soft block in the way (wood, glass, dirt, leaves, wool;
     * Brutes also stone up to cobblestone hardness). Respects the mobGriefing and uaZombieGrief game rules.
     */
    static class AIBreakThrough extends EntityAIBase {
        private final EntityUAZombie z;
        private BlockPos pos;
        private float progress;
        private int stage = -1;

        AIBreakThrough(EntityUAZombie z) {
            this.z = z;
            setMutexBits(0);
        }

        @Override
        public boolean shouldExecute() {
            EntityLivingBase t = z.getAttackTarget();
            if (z.stuck < 2 || t == null || z.getDistanceSq(t) > 20 * 20 || !z.world.getGameRules().getBoolean("mobGriefing")
                    || !z.world.getGameRules().getBoolean(ZombieDirector.RULE_GRIEF)) {
                return false;
            }
            Vec3d dir = new Vec3d(t.posX - z.posX, 0, t.posZ - z.posZ).normalize();
            BlockPos front = new BlockPos(z.posX + dir.x * (z.width * 0.5 + 0.6), z.posY, z.posZ + dir.z * (z.width * 0.5 + 0.6));
            int h = MathHelper.ceil(z.height);
            for (int dy = 0; dy < Math.min(3, h); dy++) {
                if (breakable(front.up(dy))) {
                    pos = front.up(dy);
                    return true;
                }
            }
            if (t.posY > z.posY + 1.5 && breakable(new BlockPos(z.posX, z.posY + z.height + 0.5, z.posZ))) {
                pos = new BlockPos(z.posX, z.posY + z.height + 0.5, z.posZ);
                return true;
            }
            return false;
        }

        private boolean breakable(BlockPos p) {
            IBlockState s = z.world.getBlockState(p);
            if (s.getBlock().isAir(s, z.world, p) || s.getBlock() instanceof BlockContainer || s.getMaterial().isLiquid()) {
                return false;
            }
            float hard = s.getBlockHardness(z.world, p);
            if (hard < 0) {
                return false;
            }
            Material m = s.getMaterial();
            boolean soft = m == Material.WOOD || m == Material.GLASS || m == Material.LEAVES || m == Material.CLOTH
                    || m == Material.GROUND || m == Material.GRASS || m == Material.SAND || m == Material.PLANTS
                    || m == Material.GOURD || m == Material.ICE || m == Material.PACKED_ICE || m == Material.SNOW || m == Material.CRAFTED_SNOW;
            if (soft && hard <= 3.0F) {
                return true;
            }
            return z.type() == ZType.BRUTE && m == Material.ROCK && hard <= 2.0F;
        }

        @Override
        public void startExecuting() {
            progress = 0;
            stage = -1;
        }

        @Override
        public boolean shouldContinueExecuting() {
            return pos != null && progress < 1.0F && z.getAttackTarget() != null && breakable(pos)
                    && z.getDistanceSq(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) < 9.0D;
        }

        @Override
        public void updateTask() {
            z.getLookHelper().setLookPosition(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 30F, 30F);
            float hard = Math.max(0.3F, z.world.getBlockState(pos).getBlockHardness(z.world, pos));
            progress += (z.type() == ZType.BRUTE ? 2.0F : 1.0F) / (hard * 30.0F);
            int s = (int) (progress * 10.0F);
            if (s != stage) {
                stage = s;
                z.world.sendBlockBreakProgress(z.getEntityId(), pos, s);
                z.swingArm(net.minecraft.util.EnumHand.MAIN_HAND);
                z.world.playEvent(1019, pos, 0); // zombie attacks wooden door sound
            }
            if (progress >= 1.0F) {
                z.world.sendBlockBreakProgress(z.getEntityId(), pos, -1);
                z.world.destroyBlock(pos, true);
                z.stuck = 0;
            }
        }

        @Override
        public void resetTask() {
            if (pos != null) {
                z.world.sendBlockBreakProgress(z.getEntityId(), pos, -1);
            }
            pos = null;
        }
    }
}
