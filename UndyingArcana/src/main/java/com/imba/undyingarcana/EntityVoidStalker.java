package com.imba.undyingarcana;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.MobEffects;
import net.minecraft.init.SoundEvents;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.BossInfo;
import net.minecraft.world.World;

/**
 * Void Stalker (the End, or deep underground). Blink Strike every 6 s (teleports behind the target and hits),
 * Levitation pulse every 15 s (players within 8 blocks), hits cause Blindness, two shadow clones at 66% and 33% health
 * (20 health, no drops, gone after 15 s).
 */
public class EntityVoidStalker extends EntityLSBoss {
    private boolean clone;
    private int clonesMade;

    public EntityVoidStalker(World world) {
        super(world, BossInfo.Color.PURPLE);
        setSize(0.8F, 3.0F);
    }

    public void makeClone() {
        clone = true;
        scaled = true;
        bossInfo.setVisible(false);
        getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(20.0D);
        setHealth(20.0F);
        experienceValue = 0;
    }

    @Override
    protected String bossId() {
        return "void_stalker";
    }

    @Override
    protected double baseHealth() {
        return 360.0D;
    }

    @Override
    protected double baseDamage() {
        return 12.0D;
    }

    @Override
    protected double baseSpeed() {
        return 0.3D;
    }

    @Override
    public boolean attackEntityAsMob(Entity e) {
        boolean hit = super.attackEntityAsMob(e);
        if (hit && e instanceof EntityLivingBase) {
            ((EntityLivingBase) e).addPotionEffect(new PotionEffect(MobEffects.BLINDNESS, 60, 0));
        }
        return hit;
    }

    @Override
    protected void abilities(EntityLivingBase target) {
        if (clone) {
            if (age > 300) {
                particles(EnumParticleTypes.PORTAL, posX, posY + 1.5D, posZ, 40, 0.5D, 0.5D);
                setDead();
            }
            return;
        }
        if (age % 120 == 60) {
            double lx = -Math.sin(Math.toRadians(target.rotationYaw));
            double lz = Math.cos(Math.toRadians(target.rotationYaw));
            double x = target.posX - lx * 2.0D, z = target.posZ - lz * 2.0D;
            particles(EnumParticleTypes.PORTAL, posX, posY + 1.5D, posZ, 50, 0.5D, 0.5D);
            if (world.isAirBlock(new BlockPos(x, target.posY, z)) && world.isAirBlock(new BlockPos(x, target.posY + 1.0D, z))) {
                setPositionAndUpdate(x, target.posY, z);
            }
            particles(EnumParticleTypes.PORTAL, posX, posY + 1.5D, posZ, 50, 0.5D, 0.5D);
            sound(SoundEvents.ENTITY_ENDERMEN_TELEPORT, 1.5F, 0.6F);
            if (getDistanceSq(target) < 16.0D) {
                attackEntityAsMob(target);
            }
        }
        if (age % 300 == 150) {
            ring(EnumParticleTypes.DRAGON_BREATH, 4.0D, 0.3D, 24);
            ring(EnumParticleTypes.DRAGON_BREATH, 8.0D, 0.3D, 36);
            sound(SoundEvents.ENTITY_SHULKER_SHOOT, 1.5F, 0.5F);
            for (EntityPlayer p : playersNear(8.0D)) {
                p.addPotionEffect(new PotionEffect(MobEffects.LEVITATION, 40, 1));
            }
        }
        float hp = getHealth() / getMaxHealth();
        if ((clonesMade == 0 && hp < 0.66F) || (clonesMade == 1 && hp < 0.33F)) {
            clonesMade++;
            for (int i = 0; i < 2; i++) {
                EntityVoidStalker c = new EntityVoidStalker(world);
                c.makeClone();
                c.setLocationAndAngles(posX + rand.nextGaussian() * 3.0D, posY, posZ + rand.nextGaussian() * 3.0D, rotationYaw, 0F);
                c.setAttackTarget(target);
                c.setCustomNameTag("Void Shade");
                world.spawnEntity(c);
                particles(EnumParticleTypes.PORTAL, c.posX, c.posY + 1.5D, c.posZ, 60, 0.5D, 0.5D);
            }
            announce("The Void Stalker splits into shadows!", TextFormatting.DARK_PURPLE);
        }
    }

    @Override
    protected void dropFewItems(boolean wasRecentlyHit, int looting) {
        if (!clone) {
            super.dropFewItems(wasRecentlyHit, looting);
        }
    }

    @Override
    public void onDeath(DamageSource cause) {
        if (clone) {
            setDead();
            return;
        }
        super.onDeath(cause);
    }

    @Override
    public boolean getCanSpawnHere() {
        return !clone && (world.provider.getDimension() == 1 || posY < 20.0D) && super.getCanSpawnHere();
    }

    @Override
    public void writeEntityToNBT(NBTTagCompound tag) {
        super.writeEntityToNBT(tag);
        tag.setBoolean("LsClone", clone);
        tag.setInteger("LsClones", clonesMade);
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound tag) {
        super.readEntityFromNBT(tag);
        clone = tag.getBoolean("LsClone");
        clonesMade = tag.getInteger("LsClones");
        if (clone) {
            bossInfo.setVisible(false);
        }
    }
}
