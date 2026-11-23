package com.imba.undyingarcana;

import java.util.List;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.IEntityOwnable;
import net.minecraft.entity.effect.EntityLightningBolt;
import net.minecraft.entity.monster.EntityGolem;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.init.MobEffects;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EntityDamageSourceIndirect;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

/**
 * Arrow entity for the Frost (0), Storm (1) and Blast (2) arrows.
 * Frost: Slowness III for 3 s. Storm: lightning arcs to the 2 nearest other enemies within 5 blocks for 50%.
 * Blast: bursts on impact (entity or block) for 75% to enemies within 2.5 blocks; never breaks blocks or hurts players.
 * Bonus damage is a projectile damage source of the shooter, so kills count as arrow kills in the scripts.
 */
public class EntitySpecialArrow extends EntityArrow {
    private static final DataParameter<Integer> KIND = EntityDataManager.createKey(EntitySpecialArrow.class, DataSerializers.VARINT);
    private boolean power;
    private boolean done;
    private float lastDamage = 2.0F;

    public EntitySpecialArrow(World world) {
        super(world);
    }

    public EntitySpecialArrow(World world, EntityLivingBase shooter, int kind) {
        super(world, shooter);
        dataManager.set(KIND, kind);
    }

    @Override
    protected void entityInit() {
        super.entityInit();
        dataManager.register(KIND, 0);
    }

    public int getKind() {
        return dataManager.get(KIND);
    }

    public void setPower(boolean p) {
        power = p;
    }

    @Override
    protected ItemStack getArrowStack() {
        int k = getKind();
        return new ItemStack(k == 0 ? ModItems.FROST_ARROW : k == 1 ? ModItems.STORM_ARROW : ModItems.BLAST_ARROW);
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        if (world.isRemote && !inGround) {
            int k = getKind();
            EnumParticleTypes t = k == 0 ? EnumParticleTypes.SNOW_SHOVEL : k == 1 ? EnumParticleTypes.FIREWORKS_SPARK : EnumParticleTypes.SMOKE_NORMAL;
            world.spawnParticle(t, posX, posY, posZ, 0.0D, 0.0D, 0.0D);
        }
    }

    @Override
    protected void onHit(RayTraceResult hit) {
        float speed = MathHelper.sqrt(motionX * motionX + motionY * motionY + motionZ * motionZ);
        lastDamage = Math.max(2.0F, MathHelper.ceil(speed * getDamage()));
        super.onHit(hit);
        if (!world.isRemote && power && !done && getKind() == 2) {
            done = true;
            blast();
            setDead();
        }
    }

    @Override
    protected void arrowHit(EntityLivingBase living) {
        super.arrowHit(living);
        if (world.isRemote || !power) {
            return;
        }
        int k = getKind();
        if (k == 0) {
            living.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 60, 2));
            particles(EnumParticleTypes.SNOW_SHOVEL, living.posX, living.posY + 1.0D, living.posZ, 30, 0.4D);
            world.playSound(null, living.posX, living.posY, living.posZ, SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.PLAYERS, 0.7F, 1.4F);
        } else if (k == 1) {
            List<EntityLivingBase> list = enemies(living.posX, living.posY, living.posZ, 5.0D, living);
            list.sort((a, b) -> Double.compare(a.getDistanceSq(living), b.getDistanceSq(living)));
            int n = 0;
            for (EntityLivingBase e : list) {
                if (n >= 2) {
                    break;
                }
                e.attackEntityFrom(source(), lastDamage * 0.5F);
                particles(EnumParticleTypes.FIREWORKS_SPARK, e.posX, e.posY + 1.0D, e.posZ, 20, 0.3D);
                n++;
            }
            if (n > 0) {
                world.addWeatherEffect(new EntityLightningBolt(world, living.posX, living.posY, living.posZ, true));
            }
        }
    }

    private void blast() {
        for (EntityLivingBase e : enemies(posX, posY, posZ, 2.5D, null)) {
            e.attackEntityFrom(source(), Math.max(4.0F, lastDamage * 0.75F));
        }
        particles(EnumParticleTypes.EXPLOSION_LARGE, posX, posY, posZ, 3, 0.6D);
        particles(EnumParticleTypes.SMOKE_LARGE, posX, posY, posZ, 20, 0.8D);
        world.playSound(null, posX, posY, posZ, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 0.8F, 1.3F);
    }

    private DamageSource source() {
        Entity owner = shootingEntity == null ? this : shootingEntity;
        return new EntityDamageSourceIndirect("arrow", this, owner).setProjectile();
    }

    /** Living enemies near a point: no players, villagers, golems or tamed pets. */
    private List<EntityLivingBase> enemies(double x, double y, double z, double r, Entity except) {
        AxisAlignedBB box = new AxisAlignedBB(x - r, y - r, z - r, x + r, y + r, z + r);
        return world.getEntitiesWithinAABB(EntityLivingBase.class, box, e -> e != null && e != except && e.isEntityAlive()
                && !(e instanceof EntityPlayer) && !(e instanceof EntityVillager) && !(e instanceof EntityGolem)
                && !(e instanceof IEntityOwnable && ((IEntityOwnable) e).getOwnerId() != null)
                && e.getDistanceSq(x, y, z) <= r * r);
    }

    private void particles(EnumParticleTypes t, double x, double y, double z, int count, double spread) {
        if (world instanceof WorldServer) {
            ((WorldServer) world).spawnParticle(t, x, y, z, count, spread, spread, spread, 0.05D);
        }
    }

    @Override
    public void writeEntityToNBT(NBTTagCompound tag) {
        super.writeEntityToNBT(tag);
        tag.setInteger("LsKind", getKind());
        tag.setBoolean("LsPower", power);
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound tag) {
        super.readEntityFromNBT(tag);
        dataManager.set(KIND, tag.getInteger("LsKind"));
        power = tag.getBoolean("LsPower");
    }
}
