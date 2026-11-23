package com.imba.undyingarcana;

import java.util.List;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAIHurtByTarget;
import net.minecraft.entity.ai.EntityAILookIdle;
import net.minecraft.entity.ai.EntityAINearestAttackableTarget;
import net.minecraft.entity.ai.EntityAISwimming;
import net.minecraft.entity.ai.EntityAIWanderAvoidWater;
import net.minecraft.entity.ai.EntityAIWatchClosest;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.BossInfo;
import net.minecraft.world.BossInfoServer;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

/**
 * Base class for the Undying Arcana bosses: boss bar, scaling with the world's week and nearby players,
 * shared helpers for area attacks and particles, drops (Mythic Core, Relic Shards, blueprint, tome).
 * Each boss adds its abilities in {@link #abilities(EntityLivingBase)}, called every tick on the server while it has a target.
 */
public abstract class EntityLSBoss extends EntityMob {
    protected final BossInfoServer bossInfo;
    protected int age;
    protected boolean scaled;

    public EntityLSBoss(World world, BossInfo.Color color) {
        super(world);
        bossInfo = new BossInfoServer(getDisplayName(), color, BossInfo.Overlay.PROGRESS);
        experienceValue = 250;
    }

    protected abstract double baseHealth();

    protected abstract double baseDamage();

    protected abstract double baseSpeed();

    protected abstract void abilities(EntityLivingBase target);

    /** Registry path, e.g. "infernal_brute" (scoreboard za_bk_<id> for quests and the leaderboard). */
    protected abstract String bossId();

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(baseHealth());
        getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).setBaseValue(baseDamage());
        getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(baseSpeed());
        getEntityAttribute(SharedMonsterAttributes.FOLLOW_RANGE).setBaseValue(40.0D);
        getEntityAttribute(SharedMonsterAttributes.KNOCKBACK_RESISTANCE).setBaseValue(0.85D);
        getEntityAttribute(SharedMonsterAttributes.ARMOR).setBaseValue(8.0D);
    }

    @Override
    protected void initEntityAI() {
        tasks.addTask(0, new EntityAISwimming(this));
        addAttackTask();
        tasks.addTask(7, new EntityAIWanderAvoidWater(this, 0.8D));
        tasks.addTask(8, new EntityAIWatchClosest(this, EntityPlayer.class, 16.0F));
        tasks.addTask(8, new EntityAILookIdle(this));
        targetTasks.addTask(1, new EntityAIHurtByTarget(this, false));
        targetTasks.addTask(2, new EntityAINearestAttackableTarget<>(this, EntityPlayer.class, true));
    }

    protected void addAttackTask() {
        tasks.addTask(2, new net.minecraft.entity.ai.EntityAIAttackMelee(this, 1.0D, true));
    }

    /** Health x(1 + 10% per in-game week, max +100%) x(1 + 50% per extra player within 48 blocks). Done once. */
    private void scaleOnce() {
        if (scaled) {
            return;
        }
        scaled = true;
        long weeks = Math.min(10L, world.getWorldTime() / 168000L);
        int players = world.getEntitiesWithinAABB(EntityPlayer.class, getEntityBoundingBox().grow(48.0D)).size();
        double mult = (1.0D + 0.1D * weeks) * (1.0D + 0.5D * Math.max(0, players - 1));
        getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(baseHealth() * mult);
        getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).setBaseValue(baseDamage() * (1.0D + 0.05D * weeks));
        setHealth(getMaxHealth());
    }

    @Override
    protected void updateAITasks() {
        super.updateAITasks();
        scaleOnce();
        age++;
        EntityLivingBase target = getAttackTarget();
        if (target != null && target.isEntityAlive()) {
            abilities(target);
        }
        bossInfo.setPercent(getHealth() / getMaxHealth());
    }

    @Override
    public void addTrackingPlayer(EntityPlayerMP player) {
        super.addTrackingPlayer(player);
        bossInfo.addPlayer(player);
    }

    @Override
    public void removeTrackingPlayer(EntityPlayerMP player) {
        super.removeTrackingPlayer(player);
        bossInfo.removePlayer(player);
    }

    @Override
    public void setCustomNameTag(String name) {
        super.setCustomNameTag(name);
        bossInfo.setName(getDisplayName());
    }

    @Override
    public boolean isNonBoss() {
        return false;
    }

    @Override
    protected boolean canDespawn() {
        return false;
    }

    @Override
    public void fall(float distance, float damageMultiplier) {
    }

    /** Natural spawns are rare: 1 in 40 of the normal spawn attempts, and never near another boss. */
    @Override
    public boolean getCanSpawnHere() {
        return super.getCanSpawnHere() && rand.nextInt(40) == 0
                && world.getEntitiesWithinAABB(EntityLSBoss.class, getEntityBoundingBox().grow(128.0D)).isEmpty();
    }

    // ---------- helpers ----------
    protected List<EntityPlayer> playersNear(double r) {
        AxisAlignedBB box = getEntityBoundingBox().grow(r);
        return world.getEntitiesWithinAABB(EntityPlayer.class, box, p -> p != null && p.isEntityAlive() && !p.isCreative()
                && !p.isSpectator() && p.getDistanceSq(this) <= r * r);
    }

    protected <T extends EntityLivingBase> int countNear(Class<T> cls, double r) {
        return world.getEntitiesWithinAABB(cls, getEntityBoundingBox().grow(r)).size();
    }

    protected void particles(EnumParticleTypes t, double x, double y, double z, int count, double spread, double speed) {
        if (world instanceof WorldServer) {
            ((WorldServer) world).spawnParticle(t, x, y, z, count, spread, spread, spread, speed);
        }
    }

    protected void ring(EnumParticleTypes t, double r, double y, int n) {
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2.0D * i / n;
            particles(t, posX + Math.cos(a) * r, posY + y, posZ + Math.sin(a) * r, 1, 0.05D, 0.01D);
        }
    }

    protected void sound(SoundEvent s, float vol, float pitch) {
        world.playSound(null, posX, posY, posZ, s, SoundCategory.HOSTILE, vol, pitch);
    }

    protected float dmg(float mult) {
        return (float) (getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).getAttributeValue() * mult);
    }

    protected void announce(String text, TextFormatting color) {
        for (EntityPlayer p : playersNear(64.0D)) {
            p.sendMessage(new TextComponentString(color + text));
        }
    }

    // ---------- drops ----------
    @Override
    protected void dropFewItems(boolean wasRecentlyHit, int looting) {
        entityDropItem(new ItemStack(ModItems.MYTHIC_CORE), 1.0F);
        entityDropItem(new ItemStack(ModItems.RELIC_SHARD, 8 + rand.nextInt(5 + looting)), 1.0F);
        if (rand.nextInt(2) == 0 && !ModItems.BLUEPRINTS.isEmpty()) {
            entityDropItem(new ItemStack(ModItems.BLUEPRINTS.get(rand.nextInt(ModItems.BLUEPRINTS.size()))), 1.0F);
        }
        if (rand.nextInt(10) < 3 && !ModItems.TOMES.isEmpty()) {
            entityDropItem(new ItemStack(ModItems.TOMES.get(rand.nextInt(ModItems.TOMES.size()))), 1.0F);
        }
        entityDropItem(new ItemStack(net.minecraft.init.Items.DIAMOND, 3 + rand.nextInt(3)), 1.0F);
    }

    @Override
    public void onDeath(DamageSource cause) {
        super.onDeath(cause);
        if (!world.isRemote) {
            String who = cause.getTrueSource() instanceof EntityPlayer ? cause.getTrueSource().getName() : "The survivors";
            for (EntityPlayer p : world.playerEntities) {
                p.sendMessage(new TextComponentString(TextFormatting.GOLD + who + " has slain the " + getName() + "!"));
            }
            // Everyone who fought nearby gets the kill (objectives are created by the scripts on login).
            net.minecraft.server.MinecraftServer server = world.getMinecraftServer();
            if (server != null) {
                for (EntityPlayer p : playersNear(48.0D)) {
                    server.getCommandManager().executeCommand(server, "scoreboard players add " + p.getName() + " za_bk_" + bossId() + " 1");
                    server.getCommandManager().executeCommand(server, "scoreboard players add " + p.getName() + " za_lb_boss 1");
                }
            }
        }
    }

    @Override
    public void writeEntityToNBT(NBTTagCompound tag) {
        super.writeEntityToNBT(tag);
        tag.setBoolean("LsScaled", scaled);
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound tag) {
        super.readEntityFromNBT(tag);
        scaled = tag.getBoolean("LsScaled");
        if (hasCustomName()) {
            bossInfo.setName(getDisplayName());
        }
    }

    protected static ItemStack stackOf(String id) {
        Item item = Item.getByNameOrId(id);
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }
}
