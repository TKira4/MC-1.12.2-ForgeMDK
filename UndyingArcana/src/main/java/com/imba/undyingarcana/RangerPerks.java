package com.imba.undyingarcana;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.entity.projectile.EntityTippedArrow;
import net.minecraft.init.Items;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Ranger Calling passives that need real bow hooks (the CraftTweaker scripts give the % bonuses):
 * - Quick Draw: bows draw about 30% faster.
 * - Headshot: an arrow / skill bolt hitting the head deals +50%.
 * - Volley: every 4th arrow shot fires 2 extra arrows in a fan.
 * - Thrifty: 30% chance a plain arrow is given back (that arrow cannot be picked up).
 */
@Mod.EventBusSubscriber(modid = UndyingArcana.MODID)
public final class RangerPerks {
    private static final Map<UUID, Integer> SHOTS = new HashMap<>();
    private static final String EXTRA = "za_xa";

    private RangerPerks() {
    }

    public static boolean isRanger(Entity e) {
        return e instanceof EntityPlayer && Stats.isRanger((EntityPlayer) e);
    }

    @SubscribeEvent
    public static void onUseTick(LivingEntityUseItemEvent.Tick e) {
        if (e.getEntityLiving().world.isRemote || !isRanger(e.getEntityLiving())) {
            return;
        }
        ItemStack st = e.getItem();
        if (!st.isEmpty() && st.getItem() instanceof ItemBow && e.getEntityLiving().ticksExisted % 3 == 0 && e.getDuration() > 1) {
            e.setDuration(e.getDuration() - 1);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onHurt(LivingHurtEvent e) {
        EntityLivingBase t = e.getEntityLiving();
        if (t.world.isRemote || !e.getSource().isProjectile() || !isRanger(e.getSource().getTrueSource())) {
            return;
        }
        Entity proj = e.getSource().getImmediateSource();
        if (proj == null || proj == e.getSource().getTrueSource() || t.height < 0.9F) {
            return;
        }
        double headY = t.posY + t.getEyeHeight();
        if (proj.posY + proj.height * 0.5 >= headY - 0.35) {
            e.setAmount(e.getAmount() * 1.5F);
            Fx.send(t.world, Fx.IMPACT, Fx.HOLY, t.posX, headY + 0.2, t.posZ, 0, 0, 0, 1.0, 0, 4, -1);
            Entity shooter = e.getSource().getTrueSource();
            if (shooter instanceof EntityPlayerMP) {
                SkillServer.privateSound((EntityPlayerMP) shooter, "minecraft:entity.arrow.hit_player", 0.6F, 1.6F);
                SkillServer.msg((EntityPlayer) shooter, "\u00a7eHeadshot!");
            }
        }
    }

    @SubscribeEvent
    public static void onArrow(EntityJoinWorldEvent e) {
        if (e.getWorld().isRemote || !(e.getEntity() instanceof EntityArrow)) {
            return;
        }
        EntityArrow a = (EntityArrow) e.getEntity();
        if (a.ticksExisted > 0 || a instanceof EntitySkillArrow || a.getTags().contains(EXTRA) || !isRanger(a.shootingEntity)) {
            return;
        }
        EntityPlayer p = (EntityPlayer) a.shootingEntity;
        // Thrifty: give a plain arrow back.
        if (a.pickupStatus == EntityArrow.PickupStatus.ALLOWED && a.getClass() == EntityTippedArrow.class && p.getRNG().nextFloat() < 0.3F) {
            a.pickupStatus = EntityArrow.PickupStatus.CREATIVE_ONLY;
            if (!p.inventory.addItemStackToInventory(new ItemStack(Items.ARROW))) {
                p.dropItem(new ItemStack(Items.ARROW), false);
            }
        }
        // Volley: every 4th shot.
        int n = SHOTS.getOrDefault(p.getUniqueID(), 0) + 1;
        SHOTS.put(p.getUniqueID(), n);
        if (n % 4 != 0) {
            return;
        }
        Vec3d v = new Vec3d(a.motionX, a.motionY, a.motionZ);
        for (int s = -1; s <= 1; s += 2) {
            double ang = 0.14 * s;
            double ca = Math.cos(ang), sa = Math.sin(ang);
            EntityTippedArrow x = new EntityTippedArrow(e.getWorld(), p);
            x.setPosition(a.posX, a.posY, a.posZ);
            x.motionX = v.x * ca - v.z * sa;
            x.motionY = v.y;
            x.motionZ = v.x * sa + v.z * ca;
            x.setDamage(a.getDamage());
            x.setIsCritical(a.getIsCritical());
            x.pickupStatus = EntityArrow.PickupStatus.CREATIVE_ONLY;
            if (a.isBurning()) {
                x.setFire(100);
            }
            x.addTag(EXTRA);
            e.getWorld().spawnEntity(x);
        }
        Fx.send(e.getWorld(), Fx.FLASH, Fx.WIND, a.posX, a.posY, a.posZ, 0, 0, 0, 0.6, 0, 4, -1);
    }
}
