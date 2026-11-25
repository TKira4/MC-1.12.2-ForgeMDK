package com.imba.undyingarcana;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.MobEffects;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Mana and Awakenings (new-world systems, game rule uaBuilds).
 *
 * Mana: max 60 + 2 per Magic point, regen 2 + 0.06 per Magic point per second (x1.5 after 5 s without taking damage).
 * Wand skills and Grimoire spells cost mana. Mana Potions restore 60.
 *
 * Focus: max 100 + 0.5 per Strength and Weapons point; weapon skills cost it (Quick 15, Heavy 25-35, Art 40). Regen 5 per
 * second, +6 for every basic melee hit that lands, so fights mix normal attacks with skills. Blade Storm halves the cost.
 *
 * Awakening (key G, level 60+): a 12 s state that depends on the build. A pure build uses its stat's awakening at full
 * strength, a hybrid both of its stats at 70%, a Savant its two highest stats at 50%. Cooldown 3 minutes (kept through
 * death). Effects: Vitality Titan (absorption, regeneration, no knockback), Magic Ascension (spells cost half mana, half cooldowns,
 * +30% spell damage), Strength Rampage (+40% melee, shockwaves), Weapons Blade Storm (cooldowns reset, then halved),
 * Agility Phantom (+40% dodge, speed, mobs lose you), Defense Bastion (-50% damage, taunt, reflect), Luck Fortune
 * (guaranteed crits, double coins).
 */
@Mod.EventBusSubscriber(modid = UndyingArcana.MODID)
public final class Power {
    public static final int AWAKEN_LEVEL = 60, AWAKEN_TICKS = 240, AWAKEN_CD = 3600;
    public static final String[] AWAKENING = {"Titan", "Ascension", "Rampage", "Blade Storm", "Phantom", "Bastion", "Fortune"};
    private static final UUID KB_ID = UUID.fromString("f5a7b9c1-4d6e-4f80-b1a2-3c4d5e6f7081");
    private static final Map<UUID, float[]> AWAKE = new HashMap<>();
    private static final Map<UUID, Integer> SENT = new HashMap<>();
    public static net.minecraft.item.Item MANA_POTION;

    private Power() {
    }

    // ------------------------------------------------------------------ mana
    public static int maxMana(EntityPlayer p) {
        return 60 + 2 * Stats.stats(p)[Builds.MAG] + (Relics.has(p, Relics.SAGE_DIADEM) ? 20 : 0) + Catalyst.extraMana(p);
    }

    public static float mana(EntityPlayer p) {
        NBTTagCompound d = p.getEntityData();
        return d.hasKey("ua_mana") ? d.getFloat("ua_mana") : maxMana(p);
    }

    public static void setMana(EntityPlayer p, float v) {
        p.getEntityData().setFloat("ua_mana", Math.max(0, Math.min(maxMana(p), v)));
    }

    /** Mana cost after Ascension (up to -50%, so charging many rings still costs mana). */
    public static int cost(EntityPlayer p, int base) {
        float s = strength(p, Builds.MAG);
        return Math.round(base * (1.0F - 0.5F * s));
    }

    /** Takes mana; false (with a message) when there is not enough. Creative never pays. */
    public static boolean spend(EntityPlayer p, int base) {
        if (p.capabilities.isCreativeMode) {
            return true;
        }
        int c = cost(p, base);
        float m = mana(p);
        if (m < c) {
            p.sendStatusMessage(new TextComponentString("\u00a79Not enough mana \u00a77(" + (int) m + "/" + c + ")"), true);
            return false;
        }
        setMana(p, m - c);
        return true;
    }

    /** Wand skill cost by slot (Quick, Heavy by tier, Art). */
    public static int wandCost(int slot, int tier) {
        return slot == 0 ? 12 : slot == 2 ? 45 : 16 + 6 * Math.max(1, tier);
    }

    // ------------------------------------------------------------------ focus
    public static int maxFocus(EntityPlayer p) {
        int[] s = Stats.stats(p);
        return 100 + (s[Builds.STR] + s[Builds.WPN]) / 2;
    }

    public static float focus(EntityPlayer p) {
        NBTTagCompound d = p.getEntityData();
        return d.hasKey("ua_focus") ? d.getFloat("ua_focus") : maxFocus(p);
    }

    public static void setFocus(EntityPlayer p, float v) {
        p.getEntityData().setFloat("ua_focus", Math.max(0, Math.min(maxFocus(p), v)));
    }

    /** Focus regeneration multiplier (Duelist set). */
    static float focusRegenMult(EntityPlayer p) {
        return Relics.set(p, Relics.DUELIST) >= 2 ? 1.5F : 1F;
    }

    /** Focus cost after Blade Storm (up to -50%) and Duelist Bracers (-15%). */
    public static int focusCost(EntityPlayer p, int base) {
        return Math.round(base * (1.0F - 0.5F * strength(p, Builds.WPN)) * (Relics.has(p, Relics.DUELIST_BRACERS) ? 0.85F : 1F));
    }

    /** True when the player has the focus (with a message when not). Creative always has it. */
    public static boolean hasFocus(EntityPlayer p, int base) {
        if (p.capabilities.isCreativeMode) {
            return true;
        }
        int c = focusCost(p, base);
        if (focus(p) < c) {
            p.sendStatusMessage(new TextComponentString("\u00a76Not enough focus \u00a77(" + (int) focus(p) + "/" + c
                    + ") - land normal hits to recover it"), true);
            return false;
        }
        return true;
    }

    /** Takes focus; false (with a message) when there is not enough. */
    public static boolean spendFocus(EntityPlayer p, int base) {
        if (!hasFocus(p, base)) {
            return false;
        }
        if (!p.capabilities.isCreativeMode) {
            setFocus(p, focus(p) - focusCost(p, base));
        }
        return true;
    }

    // ------------------------------------------------------------------ awakening
    /** Strength (0..1) of one stat's awakening for this player right now. */
    public static float strength(EntityPlayer p, int stat) {
        float[] a = AWAKE.get(p.getUniqueID());
        if (a == null || p.world.getTotalWorldTime() >= (long) a[Builds.COUNT]) {
            return 0;
        }
        return a[stat];
    }

    public static boolean awake(EntityPlayer p) {
        float[] a = AWAKE.get(p.getUniqueID());
        return a != null && p.world.getTotalWorldTime() < (long) a[Builds.COUNT];
    }

    /** Which stats this build awakens and how strongly. */
    public static float[] plan(EntityPlayer p) {
        float[] out = new float[Builds.COUNT];
        int b = Stats.build(p);
        int[] s = Stats.stats(p);
        if (b >= Builds.WARDEN && b <= Builds.GAMBLER) {
            for (int st = 0; st < Builds.COUNT; st++) {
                if (Builds.PURE[st] == b) {
                    out[st] = 1.0F;
                }
            }
        } else if (b == Builds.SAVANT) {
            int a = -1, c = -1;
            for (int st = 0; st < Builds.COUNT; st++) {
                if (a < 0 || s[st] > s[a]) {
                    c = a;
                    a = st;
                } else if (c < 0 || s[st] > s[c]) {
                    c = st;
                }
            }
            out[a] = 0.5F;
            out[c] = 0.5F;
        } else if (b > Builds.GAMBLER && b < Builds.SAVANT) {
            int[] pr = Builds.pair(b);
            out[pr[0]] = 0.7F;
            out[pr[1]] = 0.7F;
        }
        if (Relics.has(p, Relics.SIGIL_AWAKENING)) {
            for (int st = 0; st < Builds.COUNT; st++) {
                out[st] *= 1.2F; // Sigil of Awakening: every awakening effect +20%
            }
        }
        return out;
    }

    private static NBTTagCompound pdata(EntityPlayer p) {
        return Stats.data(p);
    }

    public static long cooldownLeft(EntityPlayer p) {
        return Math.max(0, pdata(p).getLong("awk") - p.world.getTotalWorldTime());
    }

    public static void awaken(EntityPlayerMP p) {
        if (!Stats.on(p.world)) {
            return;
        }
        if (Stats.level(p) < AWAKEN_LEVEL) {
            p.sendStatusMessage(new TextComponentString("\u00a77Awakening unlocks at level " + AWAKEN_LEVEL + "."), true);
            return;
        }
        float[] plan = plan(p);
        boolean any = false;
        for (float v : plan) {
            any |= v > 0;
        }
        if (!any) {
            p.sendStatusMessage(new TextComponentString("\u00a77You need a build to awaken (K)."), true);
            return;
        }
        long now = p.world.getTotalWorldTime();
        if (awake(p)) {
            return;
        }
        long left = cooldownLeft(p);
        if (left > 0 && !p.capabilities.isCreativeMode) {
            p.sendStatusMessage(new TextComponentString("\u00a77Awakening ready in " + (left / 20 + 1) + "s"), true);
            return;
        }
        float[] a = new float[Builds.COUNT + 1];
        System.arraycopy(plan, 0, a, 0, Builds.COUNT);
        a[Builds.COUNT] = now + AWAKEN_TICKS + (Relics.has(p, Relics.AMULET_AWAKENED) ? 60 : 0);
        AWAKE.put(p.getUniqueID(), a);
        pdata(p).setLong("awk", now + Math.round(AWAKEN_CD * (Relics.has(p, Relics.POCKET_WATCH) ? 0.8F : 1F)));
        StringBuilder name = new StringBuilder();
        for (int st = 0; st < Builds.COUNT; st++) {
            if (plan[st] > 0) {
                name.append(name.length() > 0 ? " + " : "").append(Builds.STAT_COLOR[st]).append(AWAKENING[st]);
            }
        }
        p.world.getMinecraftServer().getPlayerList().sendMessage(
                new TextComponentString("\u00a76\u2726 " + p.getName() + " awakens: " + name + "\u00a76!"));
        Fx.at(p.world, Fx.PILLAR, Fx.HOLY, p.posX, p.posY, p.posZ, 2.0);
        Fx.at(p.world, Fx.NOVA, Fx.ARCANE, p.posX, p.posY + 1, p.posZ, 2.0);
        Fx.shake(p.world, p.posX, p.posY, p.posZ, 16, 0.6F, 10);
        Fx.sound(p, "minecraft:entity.wither.spawn", 0.5F, 1.6F);
        // instant parts
        float vit = plan[Builds.VIT];
        if (vit > 0) {
            p.setAbsorptionAmount(p.getAbsorptionAmount() + 16 * vit);
            p.addPotionEffect(new PotionEffect(MobEffects.REGENERATION, AWAKEN_TICKS, vit >= 1 ? 1 : 0));
        }
        if (plan[Builds.STR] > 0) {
            p.addPotionEffect(new PotionEffect(MobEffects.STRENGTH, AWAKEN_TICKS, 0));
        }
        if (plan[Builds.AGI] > 0) {
            p.addPotionEffect(new PotionEffect(MobEffects.SPEED, AWAKEN_TICKS, plan[Builds.AGI] >= 1 ? 1 : 0));
        }
        if (plan[Builds.WPN] > 0) {
            for (int slot = 0; slot < 3; slot++) {
                SkillServer.reduceCooldown(p, slot, Math.round(6000 * plan[Builds.WPN]));
            }
        }
        if (plan[Builds.MAG] > 0) {
            setMana(p, maxMana(p));
        }
        sync(p, true);
    }

    /** Skill cooldown multiplier from Ascension / Blade Storm. */
    public static double cooldownMult(EntityPlayer p) {
        return 1.0D - 0.5D * Math.max(strength(p, Builds.MAG), strength(p, Builds.WPN));
    }

    public static float dodge(EntityPlayer p) {
        return 0.4F * strength(p, Builds.AGI);
    }

    public static float crit(EntityPlayer p) {
        return strength(p, Builds.LUCK);
    }

    public static float coinMult(EntityPlayer p) {
        return (1.0F + strength(p, Builds.LUCK)) * (Relics.has(p, Relics.LUCKY_COIN) ? 1.1F : 1F);
    }

    /** Spell damage bonus while Ascended. */
    public static void onSkill(SkillCtx c) {
        float s = strength(c.p, Builds.MAG);
        if (s > 0 && c.in != null && c.in.isWand()) {
            c.dmgMult *= 1.0F + 0.3F * s;
        }
        // Duelist set (3 pieces): weapon skills +10%
        if (c.in != null && !c.in.isWand() && !c.magic && Relics.set(c.p, Relics.DUELIST) >= 3) {
            c.dmgMult *= 1.1F;
        }
    }

    // ------------------------------------------------------------------ events
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onHurt(LivingHurtEvent e) {
        EntityLivingBase t = e.getEntityLiving();
        if (t.world.isRemote || !Stats.on(t.world)) {
            return;
        }
        DamageSource s = e.getSource();
        if (t instanceof EntityPlayer) {
            EntityPlayer p = (EntityPlayer) t;
            p.getEntityData().setLong("ua_lasthurt", p.world.getTotalWorldTime());
            float def = strength(p, Builds.DEF);
            if (def > 0) {
                e.setAmount(e.getAmount() * (1.0F - 0.5F * def));
                Entity src = s.getTrueSource();
                if (src instanceof EntityLivingBase && s.getImmediateSource() == src && src != p) {
                    ((EntityLivingBase) src).attackEntityFrom(DamageSource.causeThornsDamage(p), e.getAmount() * 0.3F * def);
                }
            }
        }
        Entity src = s.getTrueSource();
        if (src instanceof EntityPlayer && src != t && s.getImmediateSource() == src && DamageMeter.current == null && "player".equals(s.getDamageType())) {
            // a basic melee hit (not a skill) recovers focus
            setFocus((EntityPlayer) src, focus((EntityPlayer) src) + 6 * focusRegenMult((EntityPlayer) src));
        }
        if (src instanceof EntityPlayer && src != t && s.getImmediateSource() == src) {
            EntityPlayer p = (EntityPlayer) src;
            float str = strength(p, Builds.STR);
            if (str > 0 && !Pierce.isMagic(s)) {
                e.setAmount(e.getAmount() * (1.0F + 0.4F * str));
                if (p.getRNG().nextFloat() < 0.5F) {
                    for (EntityLivingBase o : t.world.getEntitiesWithinAABB(EntityLivingBase.class, t.getEntityBoundingBox().grow(3.0D),
                            x -> x != t && SkillShapes.isTarget(x))) {
                        o.knockBack(p, 0.8F * str, p.posX - o.posX, p.posZ - o.posZ);
                    }
                    Fx.at(t.world, Fx.RING, Fx.EARTH, t.posX, t.posY + 0.2, t.posZ, 1.2);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent e) {
        EntityPlayer p = e.player;
        if (e.phase != TickEvent.Phase.END || p.world.isRemote || !(p instanceof EntityPlayerMP) || !Stats.on(p.world)) {
            return;
        }
        long now = p.world.getTotalWorldTime();
        if (p.ticksExisted % 20 == 7) {
            int mag = Stats.stats(p)[Builds.MAG];
            float regen = (2.0F + 0.06F * mag) * Food.manaMul(p) * (Relics.has(p, Relics.SAGE_DIADEM) ? 1.25F : 1F) * Catalyst.regenMult(p)
                    * (Econ.blessed(p.world, 2) ? 1.2F : 1F);
            if (now - p.getEntityData().getLong("ua_lasthurt") > 100) {
                regen *= 1.5F;
            }
            setMana(p, mana(p) + regen);
            setFocus(p, focus(p) + 5 * focusRegenMult(p) * (Econ.blessed(p.world, 2) ? 1.2F : 1F));
            // awakening upkeep
            boolean on = awake(p);
            Stats.setModifier(p.getEntityAttribute(SharedMonsterAttributes.KNOCKBACK_RESISTANCE), KB_ID, "UA titan",
                    on ? strength(p, Builds.VIT) : 0, 0);
            if (on) {
                Fx.on(p, Fx.AURA, auraElement(p), 1.0, 0, 22);
                float agi = strength(p, Builds.AGI), def = strength(p, Builds.DEF);
                if (agi > 0 || def > 0) {
                    for (EntityLiving m : p.world.getEntitiesWithinAABB(EntityLiving.class, p.getEntityBoundingBox().grow(12.0D),
                            x -> x instanceof IMob && x.isEntityAlive())) {
                        if (def > 0 && p.getRNG().nextFloat() < def) {
                            m.setAttackTarget(p);
                        } else if (agi > 0 && m.getAttackTarget() == p && p.getRNG().nextFloat() < agi * 0.6F) {
                            m.setAttackTarget(null);
                        }
                    }
                }
            }
        }
        if (p.ticksExisted % 5 == 0) {
            sync((EntityPlayerMP) p, false);
        }
    }

    private static int auraElement(EntityPlayer p) {
        int[] el = {Fx.HEALING, Fx.ARCANE, Fx.FIRE, Fx.STEEL, Fx.WIND, Fx.EARTH, Fx.HOLY};
        int best = 0;
        for (int st = 1; st < Builds.COUNT; st++) {
            if (strength(p, st) > strength(p, best)) {
                best = st;
            }
        }
        return el[best];
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent e) {
        if (!e.player.world.isRemote) {
            setMana(e.player, maxMana(e.player));
            setFocus(e.player, maxFocus(e.player));
            AWAKE.remove(e.player.getUniqueID());
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        AWAKE.remove(e.player.getUniqueID());
        SENT.remove(e.player.getUniqueID());
    }

    /** Sends mana / awakening / grimoire state when it changed (or always when forced). */
    public static void sync(EntityPlayerMP p, boolean force) {
        SkillNet.MsgPower m = new SkillNet.MsgPower();
        m.on = Stats.on(p.world);
        m.mana = (int) mana(p);
        m.manaMax = maxMana(p);
        float[] a = AWAKE.get(p.getUniqueID());
        m.awakeLeft = awake(p) ? (int) ((long) a[Builds.COUNT] - p.world.getTotalWorldTime()) : 0;
        m.awakeCd = (int) Math.min(Short.MAX_VALUE, cooldownLeft(p));
        m.canAwaken = Stats.level(p) >= AWAKEN_LEVEL;
        float[] plan = plan(p);
        for (int st = 0; st < Builds.COUNT; st++) {
            m.plan[st] = (byte) Math.round(plan[st] * 100);
        }
        Magic.fill(p, m);
        m.coins = Coins.balance(p);
        m.relicMask = Relics.openMask(p);
        m.ringTicks = Relics.ringTicks(p);
        for (int i = 0; i < 2; i++) {
            m.relicCd[i] = (int) Relics.activeLeft(p, i);
            m.relicTotal[i] = Relics.activeTotal(p, i);
        }
        int hash = java.util.Objects.hash(m.on, m.mana, m.manaMax, m.awakeLeft / 20, m.awakeCd / 20, m.canAwaken, java.util.Arrays.hashCode(m.plan),
                m.spells, m.elements, java.util.Arrays.hashCode(m.spellCd), m.relicMask, m.ringTicks, m.coins, m.relicCd[0] / 20, m.relicCd[1] / 20);
        Integer last = SENT.get(p.getUniqueID());
        if (force || last == null || last != hash) {
            SENT.put(p.getUniqueID(), hash);
            SkillNet.NET.sendTo(m, p);
        }
    }

    /** Mana Potion: drink to restore 60 mana. */
    public static class ItemManaPotion extends net.minecraft.item.Item {
        @Override
        public int getMaxItemUseDuration(net.minecraft.item.ItemStack s) {
            return 24;
        }

        @Override
        public net.minecraft.item.EnumAction getItemUseAction(net.minecraft.item.ItemStack s) {
            return net.minecraft.item.EnumAction.DRINK;
        }

        @Override
        public net.minecraft.util.ActionResult<net.minecraft.item.ItemStack> onItemRightClick(net.minecraft.world.World w, EntityPlayer p,
                net.minecraft.util.EnumHand hand) {
            p.setActiveHand(hand);
            return new net.minecraft.util.ActionResult<>(net.minecraft.util.EnumActionResult.SUCCESS, p.getHeldItem(hand));
        }

        @Override
        public net.minecraft.item.ItemStack onItemUseFinish(net.minecraft.item.ItemStack s, net.minecraft.world.World w, EntityLivingBase e) {
            if (!w.isRemote && e instanceof EntityPlayerMP) {
                EntityPlayerMP p = (EntityPlayerMP) e;
                setMana(p, mana(p) + 60);
                Fx.at(w, Fx.HEAL, Fx.ARCANE, p.posX, p.posY + 1, p.posZ, 0.8);
                sync(p, true);
                if (!p.capabilities.isCreativeMode) {
                    s.shrink(1);
                    net.minecraft.item.ItemStack bottle = new net.minecraft.item.ItemStack(net.minecraft.init.Items.GLASS_BOTTLE);
                    if (!p.inventory.addItemStackToInventory(bottle)) {
                        p.dropItem(bottle, false);
                    }
                }
            }
            return s;
        }

        @Override
        @net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT)
        public void addInformation(net.minecraft.item.ItemStack s, net.minecraft.world.World w, java.util.List<String> tip,
                net.minecraft.client.util.ITooltipFlag flag) {
            tip.add("\u00a79Restores 60 mana.");
        }
    }
}
