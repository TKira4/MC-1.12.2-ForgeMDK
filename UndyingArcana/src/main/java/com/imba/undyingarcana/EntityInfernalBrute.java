package com.imba.undyingarcana;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.monster.EntityBlaze;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntitySmallFireball;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.BossInfo;
import net.minecraft.world.World;

/**
 * Infernal Brute (Nether). Fire Nova every 8 s (players within 6 blocks: 70% damage, burning 5 s), fireball volleys at
 * targets further than 8 blocks, two blazes every 20 s (max 4 near), Enrage below 50% health (+35% speed, +30% damage).
 */
public class EntityInfernalBrute extends EntityLSBoss {
    private boolean enraged;

    public EntityInfernalBrute(World world) {
        super(world, BossInfo.Color.RED);
        isImmuneToFire = true;
        setSize(1.0F, 3.3F);
    }

    @Override
    protected String bossId() {
        return "infernal_brute";
    }

    @Override
    protected double baseHealth() {
        return 400.0D;
    }

    @Override
    protected double baseDamage() {
        return 14.0D;
    }

    @Override
    protected double baseSpeed() {
        return 0.27D;
    }

    @Override
    public boolean attackEntityAsMob(net.minecraft.entity.Entity e) {
        boolean hit = super.attackEntityAsMob(e);
        if (hit) {
            e.setFire(4);
        }
        return hit;
    }

    @Override
    protected void abilities(EntityLivingBase target) {
        if (age % 160 == 0) {
            ring(EnumParticleTypes.FLAME, 2.0D, 0.3D, 16);
            ring(EnumParticleTypes.FLAME, 4.0D, 0.3D, 24);
            ring(EnumParticleTypes.LAVA, 6.0D, 0.3D, 20);
            sound(SoundEvents.ENTITY_BLAZE_SHOOT, 1.5F, 0.5F);
            for (EntityPlayer p : playersNear(6.0D)) {
                p.attackEntityFrom(DamageSource.causeMobDamage(this).setFireDamage(), dmg(0.7F));
                p.setFire(5);
            }
        }
        if (age % 100 == 50 && getDistanceSq(target) > 64.0D) {
            sound(SoundEvents.ENTITY_GHAST_SHOOT, 1.0F, 0.6F);
            for (int i = 0; i < 3; i++) {
                double dx = target.posX - posX + rand.nextGaussian() * 1.5D;
                double dy = target.posY + target.height * 0.5D - (posY + 2.5D);
                double dz = target.posZ - posZ + rand.nextGaussian() * 1.5D;
                EntitySmallFireball fb = new EntitySmallFireball(world, this, dx, dy, dz);
                fb.setPosition(fb.posX, posY + 2.5D, fb.posZ);
                world.spawnEntity(fb);
            }
        }
        if (age % 400 == 200 && countNear(EntityBlaze.class, 24.0D) < 4) {
            for (int i = 0; i < 2; i++) {
                EntityBlaze b = new EntityBlaze(world);
                b.setLocationAndAngles(posX + rand.nextGaussian() * 3.0D, posY + 1.0D, posZ + rand.nextGaussian() * 3.0D, rand.nextFloat() * 360F, 0F);
                b.setAttackTarget(target);
                world.spawnEntity(b);
                particles(EnumParticleTypes.FLAME, b.posX, b.posY + 1.0D, b.posZ, 20, 0.4D, 0.05D);
            }
            sound(SoundEvents.ENTITY_BLAZE_AMBIENT, 1.5F, 0.6F);
        }
        if (!enraged && getHealth() < getMaxHealth() * 0.5F) {
            enraged = true;
            getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(baseSpeed() * 1.35D);
            getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).setBaseValue(
                    getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).getBaseValue() * 1.3D);
            sound(SoundEvents.ENTITY_ENDERDRAGON_GROWL, 2.0F, 0.6F);
            announce("The Infernal Brute is enraged!", TextFormatting.RED);
        }
        if (enraged && age % 10 == 0) {
            particles(EnumParticleTypes.FLAME, posX, posY + 2.0D, posZ, 6, 0.6D, 0.02D);
        }
    }

    @Override
    public void writeEntityToNBT(net.minecraft.nbt.NBTTagCompound tag) {
        super.writeEntityToNBT(tag);
        tag.setBoolean("LsEnraged", enraged);
    }

    @Override
    public void readEntityFromNBT(net.minecraft.nbt.NBTTagCompound tag) {
        super.readEntityFromNBT(tag);
        enraged = tag.getBoolean("LsEnraged");
    }
}
