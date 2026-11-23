package com.imba.undyingarcana;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityAreaEffectCloud;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.init.MobEffects;
import net.minecraft.init.SoundEvents;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.world.BossInfo;
import net.minecraft.world.World;

/**
 * Plague Mother (swamps). Three broodlings (baby zombies) every 12 s (max 8 near), a poison cloud on its target every 10 s,
 * hits poison, and it bursts into a large poison cloud when it dies.
 */
public class EntityPlagueMother extends EntityLSBoss {
    public EntityPlagueMother(World world) {
        super(world, BossInfo.Color.GREEN);
        setSize(1.3F, 3.2F);
    }

    @Override
    protected String bossId() {
        return "plague_mother";
    }

    @Override
    protected double baseHealth() {
        return 380.0D;
    }

    @Override
    protected double baseDamage() {
        return 10.0D;
    }

    @Override
    protected double baseSpeed() {
        return 0.22D;
    }

    @Override
    public boolean attackEntityAsMob(Entity e) {
        boolean hit = super.attackEntityAsMob(e);
        if (hit && e instanceof EntityLivingBase) {
            ((EntityLivingBase) e).addPotionEffect(new PotionEffect(MobEffects.POISON, 100, 1));
        }
        return hit;
    }

    private void cloud(double x, double y, double z, float radius, int ticks) {
        EntityAreaEffectCloud c = new EntityAreaEffectCloud(world, x, y, z);
        c.setOwner(this);
        c.setRadius(radius);
        c.setDuration(ticks);
        c.setRadiusPerTick(-radius / ticks / 2.0F);
        c.addEffect(new PotionEffect(MobEffects.POISON, 80, 1));
        world.spawnEntity(c);
    }

    @Override
    protected void abilities(EntityLivingBase target) {
        if (age % 240 == 120 && countNear(EntityZombie.class, 24.0D) < 8) {
            for (int i = 0; i < 3; i++) {
                EntityZombie z = new EntityZombie(world);
                z.setChild(true);
                z.setLocationAndAngles(posX + rand.nextGaussian() * 2.0D, posY, posZ + rand.nextGaussian() * 2.0D, rand.nextFloat() * 360F, 0F);
                z.addTag("za_seen");
                z.setAttackTarget(target);
                world.spawnEntity(z);
                particles(EnumParticleTypes.SLIME, z.posX, z.posY + 0.5D, z.posZ, 15, 0.3D, 0.05D);
            }
            sound(SoundEvents.ENTITY_SLIME_SQUISH, 1.5F, 0.5F);
        }
        if (age % 200 == 60) {
            cloud(target.posX, target.posY, target.posZ, 3.5F, 120);
            sound(SoundEvents.ENTITY_SPLASH_POTION_BREAK, 1.2F, 0.6F);
        }
    }

    @Override
    public void onDeath(DamageSource cause) {
        super.onDeath(cause);
        if (!world.isRemote) {
            cloud(posX, posY, posZ, 6.0F, 200);
        }
    }
}
