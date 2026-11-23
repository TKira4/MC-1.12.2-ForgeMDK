package com.imba.undyingarcana;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.entity.projectile.EntityTippedArrow;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.BossInfo;
import net.minecraft.world.World;

/**
 * Bone Colossus (deserts). Bone volley every 5 s (5 arrows in a fan), Ground Slam when players come within 5 blocks
 * (damage + launch), Bone Shield every 20 s: takes 80% less damage for 4 s.
 */
public class EntityBoneColossus extends EntityLSBoss {
    private int shieldUntil = -1;

    public EntityBoneColossus(World world) {
        super(world, BossInfo.Color.WHITE);
        setSize(1.4F, 4.2F);
    }

    @Override
    protected String bossId() {
        return "bone_colossus";
    }

    @Override
    protected double baseHealth() {
        return 450.0D;
    }

    @Override
    protected double baseDamage() {
        return 15.0D;
    }

    @Override
    protected double baseSpeed() {
        return 0.22D;
    }

    @Override
    public boolean attackEntityFrom(DamageSource source, float amount) {
        if (age < shieldUntil) {
            amount *= 0.2F;
            particles(EnumParticleTypes.CRIT, posX, posY + 2.5D, posZ, 10, 0.8D, 0.1D);
        }
        return super.attackEntityFrom(source, amount);
    }

    @Override
    protected void abilities(EntityLivingBase target) {
        if (age % 100 == 40) {
            double dx = target.posX - posX, dz = target.posZ - posZ;
            double dy = target.posY + target.height * 0.5D - (posY + 3.5D);
            double base = Math.atan2(dz, dx);
            double flat = Math.sqrt(dx * dx + dz * dz);
            for (int i = -2; i <= 2; i++) {
                double a = base + Math.toRadians(10.0D * i);
                EntityTippedArrow arrow = new EntityTippedArrow(world, this);
                arrow.setPosition(arrow.posX, posY + 3.5D, arrow.posZ);
                double vx = Math.cos(a) * flat, vy = dy + flat * 0.12D, vz = Math.sin(a) * flat;
                double vl = Math.max(0.01D, Math.sqrt(vx * vx + vy * vy + vz * vz));
                arrow.motionX = vx / vl * 1.8D;
                arrow.motionY = vy / vl * 1.8D;
                arrow.motionZ = vz / vl * 1.8D;
                arrow.setDamage(dmg(0.25F));
                arrow.pickupStatus = EntityArrow.PickupStatus.DISALLOWED;
                world.spawnEntity(arrow);
            }
            sound(SoundEvents.ENTITY_SKELETON_SHOOT, 1.5F, 0.5F);
        }
        if (age % 160 == 80 && !playersNear(5.0D).isEmpty()) {
            ring(EnumParticleTypes.EXPLOSION_NORMAL, 3.0D, 0.2D, 16);
            ring(EnumParticleTypes.CLOUD, 5.0D, 0.2D, 24);
            sound(SoundEvents.ENTITY_GENERIC_EXPLODE, 1.2F, 0.5F);
            for (EntityPlayer p : playersNear(5.5D)) {
                p.attackEntityFrom(DamageSource.causeMobDamage(this), dmg(0.6F));
                p.motionY = 0.9D;
                p.velocityChanged = true;
            }
        }
        if (age % 400 == 200) {
            shieldUntil = age + 80;
            ring(EnumParticleTypes.CRIT, 1.5D, 1.0D, 20);
            ring(EnumParticleTypes.CRIT, 1.5D, 2.5D, 20);
            sound(SoundEvents.ITEM_SHIELD_BLOCK, 2.0F, 0.5F);
            announce("The Bone Colossus raises its bone shield!", TextFormatting.GRAY);
        }
    }
}
