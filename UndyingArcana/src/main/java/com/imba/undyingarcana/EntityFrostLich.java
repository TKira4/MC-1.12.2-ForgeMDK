package com.imba.undyingarcana;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.IRangedAttackMob;
import net.minecraft.entity.ai.EntityAIAttackRanged;
import net.minecraft.entity.monster.EntityStray;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.MobEffects;
import net.minecraft.init.SoundEvents;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.world.BossInfo;
import net.minecraft.world.World;

/**
 * Frost Lich (cold biomes). Casts ice bolts from range (80% damage + Slowness II), Ice Prison every 12 s (target rooted 2 s),
 * Frost Nova when players come within 4 blocks (knockback + Slowness III), three strays every 20 s (max 6 near).
 */
public class EntityFrostLich extends EntityLSBoss implements IRangedAttackMob {
    public EntityFrostLich(World world) {
        super(world, BossInfo.Color.BLUE);
        setSize(0.9F, 2.9F);
        setItemStackToSlot(EntityEquipmentSlot.MAINHAND, stackOf("ebwizardry:master_ice_wand"));
        setDropChance(EntityEquipmentSlot.MAINHAND, 0.0F);
    }

    @Override
    protected String bossId() {
        return "frost_lich";
    }

    @Override
    protected double baseHealth() {
        return 320.0D;
    }

    @Override
    protected double baseDamage() {
        return 11.0D;
    }

    @Override
    protected double baseSpeed() {
        return 0.25D;
    }

    @Override
    protected void addAttackTask() {
        tasks.addTask(2, new EntityAIAttackRanged(this, 1.0D, 30, 16.0F));
    }

    @Override
    public void attackEntityWithRangedAttack(EntityLivingBase target, float distanceFactor) {
        double sx = posX, sy = posY + 2.4D, sz = posZ;
        double tx = target.posX, ty = target.posY + target.height * 0.5D, tz = target.posZ;
        for (int i = 1; i <= 12; i++) {
            double f = i / 12.0D;
            particles(EnumParticleTypes.SNOW_SHOVEL, sx + (tx - sx) * f, sy + (ty - sy) * f, sz + (tz - sz) * f, 3, 0.05D, 0.01D);
        }
        sound(SoundEvents.ENTITY_SNOWBALL_THROW, 1.0F, 1.5F);
        target.attackEntityFrom(DamageSource.causeIndirectMagicDamage(this, this), dmg(0.8F));
        target.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 60, 1));
    }

    @Override
    public void setSwingingArms(boolean swinging) {
    }

    @Override
    protected void abilities(EntityLivingBase target) {
        if (age % 240 == 120) {
            target.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 40, 6));
            target.addPotionEffect(new PotionEffect(MobEffects.MINING_FATIGUE, 40, 2));
            particles(EnumParticleTypes.SNOW_SHOVEL, target.posX, target.posY + 1.0D, target.posZ, 60, 0.6D, 0.02D);
            particles(EnumParticleTypes.CLOUD, target.posX, target.posY + 0.2D, target.posZ, 20, 0.6D, 0.01D);
            world.playSound(null, target.posX, target.posY, target.posZ, SoundEvents.BLOCK_GLASS_BREAK, net.minecraft.util.SoundCategory.HOSTILE, 1.0F, 0.6F);
        }
        if (age % 100 == 0 && !playersNear(4.0D).isEmpty()) {
            ring(EnumParticleTypes.SNOW_SHOVEL, 2.5D, 0.5D, 18);
            ring(EnumParticleTypes.SNOW_SHOVEL, 4.5D, 0.3D, 26);
            sound(SoundEvents.BLOCK_GLASS_BREAK, 1.5F, 0.5F);
            for (EntityPlayer p : playersNear(5.0D)) {
                p.attackEntityFrom(DamageSource.causeIndirectMagicDamage(this, this), dmg(0.5F));
                p.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 80, 2));
                double dx = p.posX - posX, dz = p.posZ - posZ;
                double len = Math.max(0.1D, Math.sqrt(dx * dx + dz * dz));
                p.knockBack(this, 1.4F, -dx / len, -dz / len);
            }
        }
        if (age % 400 == 300 && countNear(EntityStray.class, 24.0D) < 6) {
            for (int i = 0; i < 3; i++) {
                EntityStray s = new EntityStray(world);
                s.setLocationAndAngles(posX + rand.nextGaussian() * 3.0D, posY, posZ + rand.nextGaussian() * 3.0D, rand.nextFloat() * 360F, 0F);
                s.onInitialSpawn(world.getDifficultyForLocation(s.getPosition()), null);
                s.setAttackTarget(target);
                world.spawnEntity(s);
                particles(EnumParticleTypes.SNOW_SHOVEL, s.posX, s.posY + 1.0D, s.posZ, 20, 0.4D, 0.05D);
            }
        }
    }
}
