package com.imba.undyingarcana;

import java.util.List;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.effect.EntityLightningBolt;
import net.minecraft.entity.monster.EntitySkeleton;
import net.minecraft.entity.monster.EntityWitherSkeleton;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.entity.projectile.EntityTippedArrow;
import net.minecraft.init.Items;
import net.minecraft.init.MobEffects;
import net.minecraft.init.SoundEvents;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.BossInfo;
import net.minecraft.world.World;

/**
 * Undying King: the final boss, summoned with the Crown of the Undying (3 Mythic Cores). Three phases:
 *   I  (100-66%): Fire Nova every 10 s, raises 3 skeletons every 20 s.
 *   II (66-33%):  + Blink Strike every 8 s, Ice Prison every 12 s, Levitation every 18 s.
 *   III (<33%):   + chain lightning every 6 s, bone volley every 6 s, enrage, raises wither skeletons.
 * Each phase change: 2 s of invulnerability and a shockwave.
 */
public class EntityUndyingKing extends EntityLSBoss {
    private int phase = 1;
    private int invulnUntil = -1;

    public EntityUndyingKing(World world) {
        super(world, BossInfo.Color.YELLOW);
        setSize(1.2F, 3.6F);
        isImmuneToFire = true;
        setItemStackToSlot(EntityEquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_SWORD));
        setDropChance(EntityEquipmentSlot.MAINHAND, 0.0F);
    }

    @Override
    protected String bossId() {
        return "undying_king";
    }

    @Override
    protected double baseHealth() {
        return 900.0D;
    }

    @Override
    protected double baseDamage() {
        return 16.0D;
    }

    @Override
    protected double baseSpeed() {
        return 0.26D;
    }

    @Override
    public boolean getCanSpawnHere() {
        return false;
    }

    @Override
    public boolean attackEntityFrom(DamageSource source, float amount) {
        if (age < invulnUntil && source != DamageSource.OUT_OF_WORLD) {
            return false;
        }
        return super.attackEntityFrom(source, amount);
    }

    private void shockwave() {
        ring(EnumParticleTypes.EXPLOSION_LARGE, 2.0D, 0.5D, 8);
        ring(EnumParticleTypes.TOTEM, 4.0D, 0.5D, 30);
        ring(EnumParticleTypes.DRAGON_BREATH, 7.0D, 0.3D, 40);
        sound(SoundEvents.ENTITY_WITHER_SPAWN, 2.0F, 0.8F);
        for (EntityPlayer p : playersNear(7.0D)) {
            double dx = p.posX - posX, dz = p.posZ - posZ;
            double len = Math.max(0.1D, Math.sqrt(dx * dx + dz * dz));
            p.knockBack(this, 1.6F, -dx / len, -dz / len);
        }
    }

    private void raise(boolean wither, int n, EntityLivingBase target) {
        if (countNear(net.minecraft.entity.monster.AbstractSkeleton.class, 24.0D) >= 6) {
            return;
        }
        for (int i = 0; i < n; i++) {
            net.minecraft.entity.monster.EntityMob m = wither ? new EntityWitherSkeleton(world) : new EntitySkeleton(world);
            m.setLocationAndAngles(posX + rand.nextGaussian() * 3.0D, posY, posZ + rand.nextGaussian() * 3.0D, rand.nextFloat() * 360F, 0F);
            m.onInitialSpawn(world.getDifficultyForLocation(m.getPosition()), null);
            m.setAttackTarget(target);
            world.spawnEntity(m);
            particles(EnumParticleTypes.SMOKE_LARGE, m.posX, m.posY + 1.0D, m.posZ, 20, 0.3D, 0.02D);
        }
        sound(SoundEvents.ENTITY_WITHER_AMBIENT, 1.5F, 0.6F);
    }

    @Override
    protected void abilities(EntityLivingBase target) {
        float hp = getHealth() / getMaxHealth();
        int want = hp < 0.33F ? 3 : hp < 0.66F ? 2 : 1;
        if (want > phase) {
            phase = want;
            invulnUntil = age + 40;
            shockwave();
            announce(phase == 2 ? "The Undying King draws on frost and shadow!" : "The Undying King unleashes the storm! Phase III!",
                    TextFormatting.GOLD);
            if (phase == 3) {
                getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(baseSpeed() * 1.3D);
                getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).setBaseValue(
                        getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).getBaseValue() * 1.25D);
            }
        }
        // Phase I+: Fire Nova, skeletons.
        if (age % 200 == 100) {
            ring(EnumParticleTypes.FLAME, 2.5D, 0.3D, 18);
            ring(EnumParticleTypes.FLAME, 5.0D, 0.3D, 28);
            sound(SoundEvents.ENTITY_BLAZE_SHOOT, 1.5F, 0.5F);
            for (EntityPlayer p : playersNear(6.0D)) {
                p.attackEntityFrom(DamageSource.causeMobDamage(this).setFireDamage(), dmg(0.6F));
                p.setFire(4);
            }
        }
        if (age % 400 == 200) {
            raise(phase >= 3, 3, target);
        }
        if (phase >= 2) {
            if (age % 160 == 80) {
                double lx = -Math.sin(Math.toRadians(target.rotationYaw));
                double lz = Math.cos(Math.toRadians(target.rotationYaw));
                double x = target.posX - lx * 2.0D, z = target.posZ - lz * 2.0D;
                particles(EnumParticleTypes.PORTAL, posX, posY + 1.5D, posZ, 50, 0.5D, 0.5D);
                if (world.isAirBlock(new BlockPos(x, target.posY, z)) && world.isAirBlock(new BlockPos(x, target.posY + 1.0D, z))) {
                    setPositionAndUpdate(x, target.posY, z);
                }
                sound(SoundEvents.ENTITY_ENDERMEN_TELEPORT, 1.5F, 0.6F);
                if (getDistanceSq(target) < 16.0D) {
                    attackEntityAsMob(target);
                }
            }
            if (age % 240 == 20) {
                target.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 40, 6));
                particles(EnumParticleTypes.SNOW_SHOVEL, target.posX, target.posY + 1.0D, target.posZ, 60, 0.6D, 0.02D);
            }
            if (age % 360 == 180) {
                ring(EnumParticleTypes.DRAGON_BREATH, 6.0D, 0.3D, 30);
                for (EntityPlayer p : playersNear(8.0D)) {
                    p.addPotionEffect(new PotionEffect(MobEffects.LEVITATION, 40, 1));
                }
            }
        }
        if (phase >= 3) {
            if (age % 120 == 0) {
                world.addWeatherEffect(new EntityLightningBolt(world, target.posX, target.posY, target.posZ, true));
                target.attackEntityFrom(DamageSource.causeIndirectMagicDamage(this, this), dmg(0.7F));
                List<EntityPlayer> near = world.getEntitiesWithinAABB(EntityPlayer.class, target.getEntityBoundingBox().grow(8.0D));
                int n = 0;
                for (EntityPlayer p : near) {
                    if (p != target && n < 2 && !p.isCreative() && !p.isSpectator()) {
                        world.addWeatherEffect(new EntityLightningBolt(world, p.posX, p.posY, p.posZ, true));
                        p.attackEntityFrom(DamageSource.causeIndirectMagicDamage(this, this), dmg(0.45F));
                        n++;
                    }
                }
            }
            if (age % 120 == 60) {
                double dx = target.posX - posX, dz = target.posZ - posZ;
                double dy = target.posY + target.height * 0.5D - (posY + 3.0D);
                double base = Math.atan2(dz, dx);
                double flat = Math.sqrt(dx * dx + dz * dz);
                for (int i = -2; i <= 2; i++) {
                    double a = base + Math.toRadians(10.0D * i);
                    EntityTippedArrow arrow = new EntityTippedArrow(world, this);
                    arrow.setPosition(arrow.posX, posY + 3.0D, arrow.posZ);
                    double vx = Math.cos(a) * flat, vy = dy + flat * 0.12D, vz = Math.sin(a) * flat;
                double vl = Math.max(0.01D, Math.sqrt(vx * vx + vy * vy + vz * vz));
                arrow.motionX = vx / vl * 1.8D;
                arrow.motionY = vy / vl * 1.8D;
                arrow.motionZ = vz / vl * 1.8D;
                    arrow.setDamage(dmg(0.2F));
                    arrow.pickupStatus = EntityArrow.PickupStatus.DISALLOWED;
                    world.spawnEntity(arrow);
                }
                sound(SoundEvents.ENTITY_SKELETON_SHOOT, 1.5F, 0.5F);
            }
            if (age % 10 == 0) {
                particles(EnumParticleTypes.TOTEM, posX, posY + 2.5D, posZ, 4, 0.8D, 0.05D);
            }
        }
    }

    @Override
    protected void dropFewItems(boolean wasRecentlyHit, int looting) {
        for (int i = 0; i < 3; i++) {
            entityDropItem(new ItemStack(ModItems.MYTHIC_CORE), 1.0F);
        }
        entityDropItem(new ItemStack(ModItems.RELIC_SHARD, 20 + rand.nextInt(6)), 1.0F);
        for (int i = 0; i < 2 && !ModItems.BLUEPRINTS.isEmpty(); i++) {
            entityDropItem(new ItemStack(ModItems.BLUEPRINTS.get(rand.nextInt(ModItems.BLUEPRINTS.size()))), 1.0F);
        }
        if (!ModItems.TOMES.isEmpty()) {
            entityDropItem(new ItemStack(ModItems.TOMES.get(rand.nextInt(ModItems.TOMES.size()))), 1.0F);
        }
        entityDropItem(new ItemStack(Items.NETHER_STAR), 1.0F);
        entityDropItem(new ItemStack(Items.DIAMOND, 8), 1.0F);
    }

    @Override
    public void writeEntityToNBT(NBTTagCompound tag) {
        super.writeEntityToNBT(tag);
        tag.setInteger("LsPhase", phase);
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound tag) {
        super.readEntityFromNBT(tag);
        phase = Math.max(1, tag.getInteger("LsPhase"));
    }
}
