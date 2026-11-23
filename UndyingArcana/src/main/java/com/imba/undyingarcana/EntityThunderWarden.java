package com.imba.undyingarcana;

import java.util.List;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.effect.EntityLightningBolt;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.MobEffects;
import net.minecraft.init.SoundEvents;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.BossInfo;
import net.minecraft.world.World;

/**
 * Thunder Warden (mountains). Chain lightning every 6 s (target 80%, two more players within 8 blocks 50%),
 * Storm Shield every 15 s for 5 s (melee attackers take 50% back), Stomp every 8 s (players within 5: damage + Slowness).
 */
public class EntityThunderWarden extends EntityLSBoss {
    private int reflectUntil = -1;

    public EntityThunderWarden(World world) {
        super(world, BossInfo.Color.BLUE);
        setSize(1.2F, 3.5F);
    }

    @Override
    protected String bossId() {
        return "thunder_warden";
    }

    @Override
    protected double baseHealth() {
        return 420.0D;
    }

    @Override
    protected double baseDamage() {
        return 14.0D;
    }

    @Override
    protected double baseSpeed() {
        return 0.24D;
    }

    @Override
    public boolean attackEntityFrom(DamageSource source, float amount) {
        if (age < reflectUntil && !world.isRemote) {
            Entity src = source.getImmediateSource();
            if (src instanceof EntityLivingBase && src == source.getTrueSource()) {
                src.attackEntityFrom(DamageSource.causeThornsDamage(this), amount * 0.5F);
                particles(EnumParticleTypes.FIREWORKS_SPARK, src.posX, src.posY + 1.0D, src.posZ, 15, 0.3D, 0.1D);
            }
        }
        return super.attackEntityFrom(source, amount);
    }

    private void bolt(EntityLivingBase e, float mult) {
        world.addWeatherEffect(new EntityLightningBolt(world, e.posX, e.posY, e.posZ, true));
        e.attackEntityFrom(DamageSource.causeIndirectMagicDamage(this, this), dmg(mult));
    }

    @Override
    protected void abilities(EntityLivingBase target) {
        if (age % 120 == 60) {
            bolt(target, 0.8F);
            int n = 0;
            List<EntityPlayer> near = world.getEntitiesWithinAABB(EntityPlayer.class, target.getEntityBoundingBox().grow(8.0D));
            for (EntityPlayer p : near) {
                if (p != target && n < 2 && !p.isCreative() && !p.isSpectator()) {
                    bolt(p, 0.5F);
                    n++;
                }
            }
        }
        if (age % 300 == 150) {
            reflectUntil = age + 100;
            ring(EnumParticleTypes.FIREWORKS_SPARK, 1.8D, 1.0D, 20);
            ring(EnumParticleTypes.FIREWORKS_SPARK, 1.8D, 2.5D, 20);
            sound(SoundEvents.ENTITY_LIGHTNING_THUNDER, 2.0F, 1.5F);
            announce("The Thunder Warden crackles with a storm shield! Stop hitting it!", TextFormatting.AQUA);
        }
        if (age < reflectUntil && age % 10 == 0) {
            particles(EnumParticleTypes.FIREWORKS_SPARK, posX, posY + 2.0D, posZ, 6, 1.0D, 0.05D);
        }
        if (age % 160 == 20 && !playersNear(5.0D).isEmpty()) {
            ring(EnumParticleTypes.EXPLOSION_NORMAL, 3.0D, 0.2D, 16);
            sound(SoundEvents.ENTITY_IRONGOLEM_ATTACK, 2.0F, 0.5F);
            for (EntityPlayer p : playersNear(5.5D)) {
                p.attackEntityFrom(DamageSource.causeMobDamage(this), dmg(0.5F));
                p.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 60, 2));
            }
        }
    }
}
