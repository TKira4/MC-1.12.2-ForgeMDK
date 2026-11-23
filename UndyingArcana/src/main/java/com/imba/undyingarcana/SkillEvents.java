package com.imba.undyingarcana;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** Event hooks of skills that react to incoming damage (Perfect Counter, Phoenix rebirth). */
@Mod.EventBusSubscriber(modid = UndyingArcana.MODID)
public final class SkillEvents {
    private static final class Counter {
        SkillCtx c;
        float dmg;
        long until;
        long last;
    }

    private static final Map<UUID, Counter> COUNTERS = new HashMap<>();

    private SkillEvents() {
    }

    public static void startCounter(SkillCtx c, float dmg, int ticks) {
        Counter k = new Counter();
        k.c = c;
        k.dmg = dmg;
        k.until = c.w.getTotalWorldTime() + ticks;
        COUNTERS.put(c.p.getUniqueID(), k);
    }

    @SubscribeEvent
    public static void onAttack(LivingAttackEvent e) {
        if (!(e.getEntityLiving() instanceof EntityPlayerMP) || e.getEntityLiving().world.isRemote) {
            return;
        }
        EntityPlayerMP p = (EntityPlayerMP) e.getEntityLiving();
        Counter k = COUNTERS.get(p.getUniqueID());
        if (k == null) {
            return;
        }
        long now = p.world.getTotalWorldTime();
        if (now > k.until || p.isDead) {
            COUNTERS.remove(p.getUniqueID());
            return;
        }
        if (e.getSource().isUnblockable()) {
            return;
        }
        e.setCanceled(true);
        if (now - k.last < 4) {
            return;
        }
        k.last = now;
        Fx.send(p.world, Fx.NOVA, Fx.STEEL, p.posX, p.posY + 1.0, p.posZ, 0, 0, 0, 1.2, 0, 4, -1);
        Fx.sound(p, "minecraft:item.shield.block", 1.0F, 1.4F);
        Entity src = e.getSource().getTrueSource();
        if (src instanceof EntityLivingBase && SkillShapes.isTarget(src) && src.getDistance(p) < 8) {
            EntityLivingBase t = (EntityLivingBase) src;
            Fx.send(p.world, Fx.XSLASH, k.c.el, t.posX, t.posY + t.height * 0.6, t.posZ, t.posX - p.posX, 0, t.posZ - p.posZ, 1.5, 0, 4, -1);
            SkillShapes.damage(k.c, t, k.dmg * 2, SkillShapes.hit().push(0.8).noFx());
            Fx.sound(p, "minecraft:entity.player.attack.crit", 1.0F, 0.8F);
            SkillServer.msg(p, "\u00a7fCounter!");
        }
    }

    /** Phoenix legend Art: the first death within 30s is cancelled; the player rises at half health in a burst of flame. */
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent e) {
        if (!(e.getEntityLiving() instanceof EntityPlayerMP) || e.getEntityLiving().world.isRemote) {
            return;
        }
        EntityPlayerMP p = (EntityPlayerMP) e.getEntityLiving();
        long until = p.getEntityData().getLong("za_phoenix");
        if (until <= p.world.getTotalWorldTime()) {
            return;
        }
        p.getEntityData().removeTag("za_phoenix");
        e.setCanceled(true);
        p.setHealth(p.getMaxHealth() * 0.5F);
        p.extinguish();
        p.addPotionEffect(new net.minecraft.potion.PotionEffect(net.minecraft.init.MobEffects.RESISTANCE, 60, 4, false, true));
        p.addPotionEffect(new net.minecraft.potion.PotionEffect(net.minecraft.init.MobEffects.FIRE_RESISTANCE, 200, 0, false, true));
        Fx.send(p.world, Fx.SCREEN_FLASH, Fx.FIRE, p.posX, p.posY, p.posZ, 0, 0, 0, 0.5, 0, 8, -1);
        Fx.send(p.world, Fx.PILLAR, Fx.FIRE, p.posX, p.posY, p.posZ, 0, 0, 0, 10, 1.8, 20, -1);
        Fx.at(p.world, Fx.EXPLOSION, Fx.FIRE, p.posX, p.posY, p.posZ, 4.0);
        for (EntityLivingBase t : SkillShapes.around(p.world, p.posX, p.posY, p.posZ, 5)) {
            t.setFire(6);
            t.motionY = 0.6;
            t.velocityChanged = true;
        }
        Fx.sound(p, "minecraft:entity.blaze.death", 1.2F, 0.6F);
        Fx.sound(p, "minecraft:item.totem.use", 1.0F, 1.2F);
        SkillServer.msg(p, "\u00a76The Phoenix rises again!");
    }
}
