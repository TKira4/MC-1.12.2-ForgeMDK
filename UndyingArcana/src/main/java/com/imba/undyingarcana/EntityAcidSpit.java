package com.imba.undyingarcana;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityThrowable;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.World;

/** Spitter acid: damage plus Corroded on players (they take 20% more damage for 6 s). */
public class EntityAcidSpit extends EntityThrowable {
    public static final String CORRODE = "ua_corrodeT";
    private float damage = 3.0F;

    public EntityAcidSpit(World w) {
        super(w);
    }

    public EntityAcidSpit(World w, EntityLivingBase thrower, float damage) {
        super(w, thrower);
        this.damage = damage;
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        if (world.isRemote) {
            world.spawnParticle(EnumParticleTypes.SLIME, posX, posY, posZ, 0, 0, 0);
        } else if (ticksExisted > 100) {
            setDead();
        }
    }

    @Override
    protected void onImpact(RayTraceResult r) {
        if (world.isRemote) {
            return;
        }
        if (r.entityHit instanceof EntityLivingBase && r.entityHit != getThrower() && !(r.entityHit instanceof EntityUAZombie)) {
            EntityLivingBase e = (EntityLivingBase) r.entityHit;
            e.attackEntityFrom(DamageSource.causeThrownDamage(this, getThrower()), damage);
            if (e instanceof EntityPlayer) {
                e.getEntityData().setLong(CORRODE, world.getTotalWorldTime() + 120);
            }
        } else if (r.entityHit != null) {
            return;
        }
        Fx.at(world, Fx.IMPACT, Fx.POISON, posX, posY, posZ, 0.7);
        Fx.sound(world, posX, posY, posZ, "minecraft:block.lava.extinguish", 0.6F, 1.6F);
        setDead();
    }

    @Override
    public void writeEntityToNBT(NBTTagCompound tag) {
        super.writeEntityToNBT(tag);
        tag.setFloat("Dmg", damage);
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound tag) {
        super.readEntityFromNBT(tag);
        damage = tag.getFloat("Dmg");
    }
}
