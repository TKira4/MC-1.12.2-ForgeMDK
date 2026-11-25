package com.imba.undyingarcana;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.init.MobEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * The signature mechanic of every build (see Builds.DESC). Strength comes from Builds.sig: 1 for the build itself,
 * 0.5 for a Savant's two highest stats. Runtime state is kept per player in memory (cooldowns, windows, stacks).
 */
@Mod.EventBusSubscriber(modid = UndyingArcana.MODID)
public final class Signatures {
    private static final UUID FORTRESS_ID = UUID.fromString("9b3e1f07-4c2d-4a86-b51e-6d0f7a2c8e49");
    private static final Map<UUID, State> STATES = new HashMap<>();
    private static final List<Object[]> DECOYS = new ArrayList<>(); // armor stand, expiry tick
    private static boolean inBurst;

    private static final class State {
        long shadowUntil, riposteUntil, counterUntil, undyingAt, secondWindAt, comboUntil, rallyUntil, switchAt = -1000, blinkAt,
                lastSneakPress, stillSince;
        int combo, rally, overcharge, wmHits;
        long wmReadyUntil, wmCdUntil;
        String family = "";
        boolean sneaking;
        double lx, ly, lz;
    }

    private Signatures() {
    }

    private static State st(EntityPlayer p) {
        return STATES.computeIfAbsent(p.getUniqueID(), k -> new State());
    }

    private static float sig(EntityPlayer p, int build) {
        return Builds.sig(Stats.build(p), Stats.stats(p), build);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        STATES.remove(e.player.getUniqueID());
    }

    // ------------------------------------------------------------------ skills
    /** Weapon Master and Overcharge (called while a skill cast is prepared). */
    static void onSkill(SkillCtx c) {
        if (SkillServer.preview) {
            return; // HUD preview, not a real cast
        }
        State s = st(c.p);
        float wm = sig(c.p, Builds.WARRIOR);
        if (wm > 0 && c.now < s.wmReadyUntil && c.now >= s.wmCdUntil) {
            c.dmgMult *= 1.0F + 0.4F * wm;
            s.wmReadyUntil = 0;
            s.wmCdUntil = c.now + 160; // once per 8 s
            SkillServer.msg(c.p, "\u00a7cWeapon Master!");
        }
        float oc = sig(c.p, Builds.MAGE);
        if (oc > 0 && c.in.isWand() && ++s.overcharge % 4 == 0) {
            c.freeCast = true;
            c.size *= 1.0F + oc;
            SkillServer.msg(c.p, "\u00a79Overcharge!");
            Fx.at(c.w, Fx.TIERUP, Fx.ARCANE, c.p.posX, c.p.posY + 1, c.p.posZ, 1.0);
        }
    }

    /** Vanguard hits ignore the Armored zombie's riot shield. */
    public static boolean ignoresShields(Entity src) {
        return src instanceof EntityPlayer && Stats.on(src.world) && sig((EntityPlayer) src, Builds.VANGUARD) > 0;
    }

    // ------------------------------------------------------------------ damage
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onHurt(LivingHurtEvent e) {
        EntityLivingBase t = e.getEntityLiving();
        if (t.world.isRemote || !Stats.on(t.world) || e.getAmount() <= 0) {
            return;
        }
        if (t instanceof EntityPlayer) {
            defend(e, (EntityPlayer) t);
            if (e.isCanceled()) {
                return;
            }
        }
        Entity src = e.getSource().getTrueSource();
        if (src instanceof EntityPlayer && src != t && !inBurst) {
            weaponMasterHit((EntityPlayer) src, e.getSource());
            attack(e, (EntityPlayer) src, t);
        }
    }

    /** Weapon Master: 2 basic hits within 5 s of switching weapon type prime the next skill (+40%, once per 8 s). */
    private static void weaponMasterHit(EntityPlayer p, net.minecraft.util.DamageSource s) {
        if (s.getImmediateSource() != p || DamageMeter.current != null || !"player".equals(s.getDamageType())
                || sig(p, Builds.WARRIOR) <= 0) {
            return;
        }
        State st = st(p);
        long now = p.world.getTotalWorldTime();
        if (now - st.switchAt <= 100 && ++st.wmHits == 2 && now >= st.wmCdUntil) {
            st.wmReadyUntil = now + 100;
            SkillServer.msg(p, "\u00a7cWeapon Master ready \u00a77- next skill +40%");
        }
    }

    private static void defend(LivingHurtEvent e, EntityPlayer p) {
        DamageSource s = e.getSource();
        if (s.canHarmInCreative() || s.isDamageAbsolute()) {
            return;
        }
        long now = p.world.getTotalWorldTime();
        State st = st(p);
        int[] stats = Stats.stats(p);
        Entity attacker = s.getTrueSource();
        boolean physical = attacker != null && !Pierce.isMagic(s) && !s.isExplosion() && !s.isFireDamage();
        // dodge (Agility), Rogue raises the cap
        float rogue = sig(p, Builds.ROGUE);
        float dodge = Math.min(0.35F + 0.10F * rogue + Power.dodge(p),
                Math.min(0.25F + 0.10F * rogue, 0.0025F * stats[Builds.AGI]) + Gear.dodge(p) + Power.dodge(p));
        if (physical && p.getRNG().nextFloat() < dodge) {
            e.setCanceled(true);
            Fx.sound(p.world, p.posX, p.posY, p.posZ, "minecraft:entity.player.attack.nodamage", 1.0F, 1.4F);
            Fx.at(p.world, Fx.DASH, Fx.WIND, p.posX, p.posY + 1, p.posZ, 0.6);
            SkillServer.msg(p, "\u00a7bDodge!");
            if (rogue > 0) {
                st.shadowUntil = now + 40;
                for (EntityUAZombie z : p.world.getEntitiesWithinAABB(EntityUAZombie.class, p.getEntityBoundingBox().grow(16))) {
                    if (z.getAttackTarget() == p) {
                        z.setAttackTarget(null);
                        z.investigate(new BlockPos(p.posX + p.getRNG().nextGaussian() * 6, p.posY, p.posZ + p.getRNG().nextGaussian() * 6), 60);
                    }
                }
            }
            if (sig(p, Builds.SKIRMISHER) > 0) {
                p.addPotionEffect(new PotionEffect(MobEffects.RESISTANCE, 60, 0, false, false));
            }
            return;
        }
        // Divine Shield (Templar) and Blessed (Pilgrim)
        float templar = sig(p, Builds.TEMPLAR);
        if (templar > 0 && attacker != null && p.getRNG().nextFloat() < 0.15F * templar) {
            e.setCanceled(true);
            if (attacker instanceof EntityLivingBase) {
                attacker.attackEntityFrom(DamageSource.causeThornsDamage(p), e.getAmount());
            }
            Fx.at(p.world, Fx.M_DOME, Fx.HOLY, p.posX, p.posY + 1, p.posZ, 1.2);
            Fx.sound(p.world, p.posX, p.posY, p.posZ, "minecraft:item.shield.block", 1.0F, 1.2F);
            return;
        }
        float pilgrim = sig(p, Builds.PILGRIM);
        if (pilgrim > 0 && p.getRNG().nextFloat() < 0.10F * pilgrim) {
            e.setCanceled(true);
            p.heal(2.0F);
            Fx.at(p.world, Fx.HEAL, Fx.HOLY, p.posX, p.posY + 1, p.posZ, 0.8);
            return;
        }
        float amount = e.getAmount();
        // Bastion (Guardian): braced against the front
        float guardian = sig(p, Builds.GUARDIAN);
        if (guardian > 0 && p.isSneaking() && attacker != null) {
            Vec3d look = p.getLookVec();
            Vec3d to = new Vec3d(attacker.posX - p.posX, 0, attacker.posZ - p.posZ).normalize();
            if (look.x * to.x + look.z * to.z > 0.3) {
                amount *= 1.0F - 0.6F * guardian;
                Fx.sound(p.world, p.posX, p.posY, p.posZ, "minecraft:item.shield.block", 0.8F, 1.0F);
            }
        }
        if (p.isSneaking() && sig(p, Builds.KNIGHT) > 0) {
            st.riposteUntil = now + 20;
        }
        if (sig(p, Builds.DUELIST) > 0) {
            st.counterUntil = now + 10;
        }
        // Second Wind (Survivor)
        float survivor = sig(p, Builds.SURVIVOR);
        if (survivor > 0 && amount >= p.getMaxHealth() * 0.25F && now >= st.secondWindAt) {
            st.secondWindAt = now + Math.round(600 / survivor);
            p.addPotionEffect(new PotionEffect(MobEffects.SPEED, 80, 1));
            p.addPotionEffect(new PotionEffect(MobEffects.REGENERATION, 80, 1));
            SkillServer.msg(p, "\u00a7aSecond Wind!");
        }
        // Undying (Warden)
        float warden = sig(p, Builds.WARDEN);
        if (warden > 0 && amount >= p.getHealth() && now >= st.undyingAt) {
            st.undyingAt = now + Math.round(3600 / warden);
            amount = Math.max(0, p.getHealth() - 1.0F);
            p.addPotionEffect(new PotionEffect(MobEffects.RESISTANCE, 60, 4));
            final EntityPlayer pp = p;
            p.getServer().addScheduledTask(() -> pp.heal(pp.getMaxHealth() * 0.3F));
            Fx.at(p.world, Fx.PILLAR, Fx.HOLY, p.posX, p.posY, p.posZ, 1.5);
            Fx.sound(p.world, p.posX, p.posY, p.posZ, "minecraft:item.totem.use", 0.8F, 1.0F);
            SkillServer.msg(p, "\u00a7aUndying!");
        }
        e.setAmount(amount);
    }

    private static void attack(LivingHurtEvent e, EntityPlayer p, EntityLivingBase t) {
        DamageSource s = e.getSource();
        long now = p.world.getTotalWorldTime();
        State st = st(p);
        int[] stats = Stats.stats(p);
        Entity direct = s.getImmediateSource();
        boolean projectile = direct != null && direct != p;
        boolean magic = Pierce.isMagic(s);
        boolean unarmed = direct == p && p.getHeldItemMainhand().isEmpty() && !magic;
        boolean melee = direct == p && !magic;
        float m = 1.0F;

        // ---- crits (Luck, Rogue, Assassin, Marksman)
        float chance = Builds.crit(stats[Builds.LUCK]) + Gear.critChance(p) + Power.crit(p);
        float marksman = sig(p, Builds.MARKSMAN);
        boolean forced = false;
        if (projectile && marksman > 0) {
            chance += 0.10F * marksman;
            forced = direct.posY >= t.posY + t.getEyeHeight() - 0.35;
        }
        if (st.shadowUntil > now) {
            forced = true;
            st.shadowUntil = 0;
        }
        float assassin = sig(p, Builds.ASSASSIN);
        if (assassin > 0 && t.getHealth() < t.getMaxHealth() * 0.25F) {
            forced = true;
            m *= 1.0F + 0.5F * assassin;
        }
        if (forced || p.getRNG().nextFloat() < chance) {
            float gambler = sig(p, Builds.GAMBLER);
            float mult = 1.5F + 0.5F * gambler + Gear.critDamage(p);
            if (gambler > 0 && p.getRNG().nextFloat() < 0.10F * gambler) {
                mult = 3.0F;
                goldBurst(p, t);
            }
            m *= mult;
            Fx.sound(p.world, t.posX, t.posY, t.posZ, "minecraft:entity.player.attack.crit", 1.0F, 1.0F);
            Fx.at(p.world, Fx.IMPACT, mult >= 3 ? Fx.FIRE : Fx.HOLY, t.posX, t.posY + t.height * 0.6, t.posZ, mult >= 3 ? 1.2 : 0.6);
            float brigand = sig(p, Builds.BRIGAND);
            if (brigand > 0) {
                t.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, Math.round(30 * brigand), 3));
                t.addPotionEffect(new PotionEffect(MobEffects.WEAKNESS, Math.round(30 * brigand), 1));
            }
            if (magic && sig(p, Builds.DIVINER) > 0) {
                SkillServer.reduceCooldown(p, -1, Math.round(30 * sig(p, Builds.DIVINER)));
            }
        }
        // ---- damage signatures
        float berserk = sig(p, Builds.BERSERKER);
        if (berserk > 0) {
            m *= 1.0F + 0.5F * berserk * (1.0F - p.getHealth() / p.getMaxHealth());
        }
        float knight = sig(p, Builds.KNIGHT);
        if (knight > 0 && st.riposteUntil > now) {
            m *= 1.0F + 0.6F * knight;
            st.riposteUntil = 0;
            SkillServer.msg(p, "\u00a73Riposte!");
        }
        float duelist = sig(p, Builds.DUELIST);
        if (duelist > 0 && st.counterUntil > now) {
            m *= 1.0F + duelist;
            st.counterUntil = 0;
            SkillServer.msg(p, "\u00a7cCounter!");
        }
        float brawler = sig(p, Builds.BRAWLER);
        if (brawler > 0 && unarmed) {
            st.combo = st.comboUntil > now ? Math.min(5, st.combo + 1) : 1;
            st.comboUntil = now + 40;
            m *= 1.0F + 0.1F * st.combo * brawler;
            if (p.isSprinting() && p instanceof EntityPlayerMP) {
                Vec3d l = p.getLookVec();
                SkillShapes.launch((EntityPlayerMP) p, l.x * 0.6, 0.1, l.z * 0.6);
            }
        }
        if (st.rallyUntil > now && st.rally > 0) {
            m *= 1.0F + 0.1F * st.rally;
        }
        if (melee && sig(p, Builds.VANGUARD) > 0) {
            Pierce.addSunder(t, now, 2);
        }
        float sentinel = sig(p, Builds.SENTINEL);
        if (sentinel > 0 && projectile && t instanceof EntityLiving) {
            EntityLivingBase tt = ((EntityLiving) t).getAttackTarget();
            if (tt instanceof EntityPlayer && tt != p) {
                m *= 1.0F + 0.3F * sentinel;
            }
        }
        if (sig(p, Builds.SKIRMISHER) > 0) {
            p.addPotionEffect(new PotionEffect(MobEffects.SPEED, 40, 1, false, false));
        }
        float amount = e.getAmount() * m;
        e.setAmount(amount);
        // ---- effects after the hit
        float paladin = sig(p, Builds.PALADIN);
        if (paladin > 0 && magic) {
            float heal = amount * 0.10F * paladin;
            for (EntityPlayer ally : p.world.getEntitiesWithinAABB(EntityPlayer.class, p.getEntityBoundingBox().grow(8))) {
                ally.heal(heal);
            }
            Fx.at(p.world, Fx.HEAL, Fx.HOLY, p.posX, p.posY + 1, p.posZ, 0.6);
        }
        float warlock = sig(p, Builds.WARLOCK);
        if (warlock > 0 && unarmed) {
            inBurst = true;
            try {
                for (EntityLivingBase o : t.world.getEntitiesWithinAABB(EntityLivingBase.class, t.getEntityBoundingBox().grow(2.5))) {
                    if (o != p && !(o instanceof EntityPlayer)) {
                        o.attackEntityFrom(DamageSource.causeIndirectMagicDamage(p, p), amount * 0.5F * warlock);
                    }
                }
            } finally {
                inBurst = false;
            }
            Fx.at(t.world, Fx.NOVA, Fx.SHADOW, t.posX, t.posY + 1, t.posZ, 1.2);
        }
    }

    private static void goldBurst(EntityPlayer p, EntityLivingBase t) {
        World w = p.world;
        int n = 2 + w.rand.nextInt(4);
        Coins.add(p, n);
        Fx.at(w, Fx.EXPLOSION, Fx.HOLY, t.posX, t.posY + 1, t.posZ, 0.8);
        Fx.sound(w, t.posX, t.posY, t.posZ, "minecraft:entity.experience_orb.pickup", 1.0F, 0.6F);
    }

    // ------------------------------------------------------------------ kills and drops
    @SubscribeEvent
    public static void onKill(LivingDeathEvent e) {
        Entity src = e.getSource().getTrueSource();
        if (!(src instanceof EntityPlayer) || e.getEntityLiving().world.isRemote || !Stats.on(src.world)) {
            return;
        }
        EntityPlayer p = (EntityPlayer) src;
        float berserk = sig(p, Builds.BERSERKER);
        if (berserk > 0) {
            p.heal(p.getMaxHealth() * 0.05F * berserk);
        }
        if (sig(p, Builds.WARLORD) > 0) {
            long now = p.world.getTotalWorldTime();
            for (EntityPlayer ally : p.world.getEntitiesWithinAABB(EntityPlayer.class, p.getEntityBoundingBox().grow(16))) {
                State a = st(ally);
                a.rally = a.rallyUntil > now ? Math.min(3, a.rally + 1) : 1;
                a.rallyUntil = now + 120;
            }
        }
    }

    @SubscribeEvent
    public static void onDrops(LivingDropsEvent e) {
        Entity src = e.getSource().getTrueSource();
        if (!(src instanceof EntityPlayer) || e.getEntityLiving().world.isRemote || !Stats.on(src.world)) {
            return;
        }
        EntityPlayer p = (EntityPlayer) src;
        float chance = Math.min(0.5F, 0.005F * Stats.stats(p)[Builds.LUCK]) + 0.15F * sig(p, Builds.BRIGAND);
        EntityLivingBase dead = e.getEntityLiving();
        boolean elite = dead instanceof EntityUAZombie && ((EntityUAZombie) dead).isElite() || dead instanceof EntityLSBoss;
        if (elite && sig(p, Builds.GAMBLER) > 0) {
            chance = 1.0F;
        }
        if (p.getRNG().nextFloat() >= chance) {
            return;
        }
        List<EntityItem> extra = new ArrayList<>();
        for (EntityItem it : e.getDrops()) {
            extra.add(new EntityItem(it.world, it.posX, it.posY, it.posZ, it.getItem().copy()));
        }
        e.getDrops().addAll(extra);
        Fx.at(dead.world, Fx.FLASH, Fx.HOLY, dead.posX, dead.posY + 1, dead.posZ, 0.6);
    }

    // ------------------------------------------------------------------ per tick
    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent e) {
        EntityPlayer p = e.player;
        if (e.phase != TickEvent.Phase.END || p.world.isRemote || !(p instanceof EntityPlayerMP) || !Stats.on(p.world)) {
            return;
        }
        long now = p.world.getTotalWorldTime();
        State st = st(p);
        // Weapon Master: remember when the weapon type changed
        if (now % 5 == 0) {
            SkillCatalog.Info in = SkillCatalog.identify(p.getHeldItemMainhand());
            String fam = in == null ? "" : in.family;
            if (!fam.isEmpty() && !fam.equals(st.family)) {
                if (!st.family.isEmpty()) {
                    st.switchAt = now;
                    st.wmHits = 0;
                }
                st.family = fam;
            }
        }
        // Blink (Trickster): double-tap sneak
        boolean sneak = p.isSneaking();
        if (sneak && !st.sneaking) {
            if (now - st.lastSneakPress <= 8 && now >= st.blinkAt && sig(p, Builds.TRICKSTER) > 0) {
                blink((EntityPlayerMP) p);
                st.blinkAt = now + Math.round(160 / sig(p, Builds.TRICKSTER));
            }
            st.lastSneakPress = now;
        }
        st.sneaking = sneak;
        if (now % 10 != 0) {
            return;
        }
        // Fortress (Bulwark): still for 2 s
        float bulwark = sig(p, Builds.BULWARK);
        double moved = Math.abs(p.posX - st.lx) + Math.abs(p.posY - st.ly) + Math.abs(p.posZ - st.lz);
        st.lx = p.posX;
        st.ly = p.posY;
        st.lz = p.posZ;
        if (moved > 0.05) {
            st.stillSince = now;
        }
        boolean fortress = bulwark > 0 && now - st.stillSince >= 40;
        Stats.setModifier(p.getEntityAttribute(SharedMonsterAttributes.ARMOR), FORTRESS_ID, "UA Fortress", fortress ? 8.0D * bulwark : 0, 0);
        if (fortress && now % 40 == 0) {
            p.addPotionEffect(new PotionEffect(MobEffects.REGENERATION, 50, 0, false, false));
        }
        // Unstoppable (Juggernaut)
        float jugg = sig(p, Builds.JUGGERNAUT);
        if (jugg > 0) {
            p.removePotionEffect(MobEffects.SLOWNESS);
            if (p.isSprinting()) {
                for (EntityUAZombie z : p.world.getEntitiesWithinAABB(EntityUAZombie.class, p.getEntityBoundingBox().grow(1.2, 0, 1.2))) {
                    Vec3d v = new Vec3d(z.posX - p.posX, 0, z.posZ - p.posZ).normalize();
                    z.attackEntityFrom(DamageSource.causePlayerDamage(p), 2.0F * jugg);
                    z.addVelocity(v.x * 1.2, 0.4, v.z * 1.2);
                }
            }
        }
        if (now % 20 != 0) {
            return;
        }
        // Bastion taunt (Guardian)
        if (p.isSneaking() && sig(p, Builds.GUARDIAN) > 0) {
            for (EntityUAZombie z : p.world.getEntitiesWithinAABB(EntityUAZombie.class, p.getEntityBoundingBox().grow(8))) {
                z.setAttackTarget(p);
            }
        }
        // Ward (Runeguard): a magic shield every 10 s
        float rune = sig(p, Builds.RUNEGUARD);
        if (rune > 0 && now % 200 == 0 && p.getAbsorptionAmount() < p.getMaxHealth() * 0.2F * rune) {
            p.setAbsorptionAmount(p.getMaxHealth() * 0.2F * rune);
            Fx.at(p.world, Fx.M_DOME, Fx.ARCANE, p.posX, p.posY + 1, p.posZ, 1.0);
        }
        // Foresight (Diviner): elites and necromancers glow
        if (sig(p, Builds.DIVINER) > 0 && now % 40 == 0) {
            for (EntityUAZombie z : p.world.getEntitiesWithinAABB(EntityUAZombie.class, p.getEntityBoundingBox().grow(32))) {
                if (z.isElite() || z.type() == ZType.NECROMANCER) {
                    z.addPotionEffect(new PotionEffect(MobEffects.GLOWING, 50, 0, false, false));
                }
            }
        }
    }

    /** Teleports 6 blocks along the look direction (stops before walls) and leaves a decoy the zombies chase. */
    private static void blink(EntityPlayerMP p) {
        Vec3d eye = p.getPositionEyes(1.0F);
        Vec3d look = p.getLookVec();
        Vec3d dir = new Vec3d(look.x, 0, look.z).normalize();
        Vec3d end = eye.add(dir.scale(6));
        RayTraceResult r = p.world.rayTraceBlocks(eye, end, false, true, false);
        Vec3d dest = r != null ? r.hitVec.subtract(dir.scale(0.6)) : end;
        EntityArmorStand decoy = new EntityArmorStand(p.world, p.posX, p.posY, p.posZ);
        decoy.setNoGravity(true);
        decoy.setCustomNameTag(p.getName());
        decoy.setAlwaysRenderNameTag(false);
        p.world.spawnEntity(decoy);
        DECOYS.add(new Object[]{decoy, p.world.getTotalWorldTime() + 60});
        for (EntityUAZombie z : p.world.getEntitiesWithinAABB(EntityUAZombie.class, p.getEntityBoundingBox().grow(16))) {
            if (z.getAttackTarget() == p) {
                z.setAttackTarget(decoy);
            }
        }
        Fx.at(p.world, Fx.GATHER, Fx.ARCANE, p.posX, p.posY + 1, p.posZ, 0.8);
        p.setPositionAndUpdate(dest.x, dest.y - p.getEyeHeight(), dest.z);
        p.fallDistance = 0;
        Fx.at(p.world, Fx.FLASH, Fx.ARCANE, p.posX, p.posY + 1, p.posZ, 0.8);
        Fx.sound(p.world, p.posX, p.posY, p.posZ, "minecraft:entity.endermen.teleport", 0.8F, 1.3F);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || DECOYS.isEmpty()) {
            return;
        }
        for (Iterator<Object[]> it = DECOYS.iterator(); it.hasNext(); ) {
            Object[] d = it.next();
            EntityArmorStand a = (EntityArmorStand) d[0];
            if (a.isDead || a.world.getTotalWorldTime() >= (Long) d[1]) {
                a.setDead();
                it.remove();
            }
        }
    }
}
