package com.imba.undyingarcana;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.MobEffects;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.ActionResult;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.NonNullList;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Martial arts (Strength): six fighting styles used with bare hands or a fist weapon (gauntlets, claws).
 *
 * A style is learned from its Martial Manual (Brawler is known from the start) and worn one at a time: right-click a
 * manual of a learned style to switch. With a style, Z / X / C use the style's moves (ArmoryMoves "fist_*"), fists hit
 * for +3 and martial hits scale with Strength (bare hands +2% per point, fist weapons +1.2%). Normal hits build a combo;
 * the 4th hit within 1.5 s of the last is a finisher with the style's effect. Each style has a passive, and a mastery
 * (I-V at 200 / 600 / 1500 / 3000 hits, +5% martial damage per level).
 */
@Mod.EventBusSubscriber(modid = UndyingArcana.MODID)
public final class Martial {
    public static final String[] ID = {"brawler", "ironleg", "tiger", "stone", "palm", "arcane"};
    public static final String[] NAME = {"Brawler", "Iron Leg", "Tiger Claw", "Stone Fist", "Flowing Palm", "Arcane Fist"};
    public static final int[] RGB = {0xD8A060, 0x7FD8C8, 0xC83A3A, 0x9A7A52, 0xA8D8FF, 0xB070F0};
    public static final String[] PASSIVE = {"Fast fists: +0.8 attack speed", "Kicks push enemies further",
            "Hits may bleed (20%; every hit during Tiger Frenzy)", "Heavy blows: +20% damage, -0.4 attack speed",
            "Every 4th hit heals you; Redirect guards against melee", "Hits burst with your element for 2 mana"};
    public static final String[] FINISHER = {"Uppercut: x1.6 and launches", "Sweeping kick: hits everyone in front",
            "Rend: x1.4 and heavy bleed", "Shatter: x1.8 and a short stun", "Palm blast: blows the target away, heals 2",
            "Elemental burst around the target"};
    private static final int[] MASTERY = {0, 200, 600, 1500, 3000};
    public static final int BRAWLER = 0, IRONLEG = 1, TIGER = 2, STONE = 3, PALM = 4, ARCANE = 5;
    /** Body poses sent to clients (MartialAnim). COMBAT only refreshes the guard stance and the worn style. */
    public static final int POSE_COMBAT = 0, POSE_UPPERCUT = 1, POSE_HOOK = 2, POSE_JAB_L = 3, POSE_JAB_R = 4, POSE_KICK_R = 5,
            POSE_SPIN = 6, POSE_SWEEP = 7, POSE_SKYDROP = 8, POSE_KICK_L = 9;
    private static final UUID SPEED_ID = UUID.fromString("2d4f6a80-91b3-4c5d-8e7f-a0b1c2d3e4f5");
    public static Item MANUAL;

    private Martial() {
    }

    // ------------------------------------------------------------------ weapons
    /** Fist weapons that count as martial (scale with Strength): gauntlets, claws, hand wraps, iron greaves. */
    public static boolean isMartial(ItemStack s) {
        SkillCatalog.Info in = SkillCatalog.identify(s);
        return in != null && isMartialType(in.type);
    }

    public static boolean isMartialType(String type) {
        return "gauntlets".equals(type) || "claws".equals(type) || "wraps".equals(type) || "greaves".equals(type);
    }

    /** Bare hands or a fist weapon in the main hand. */
    public static boolean martialHands(EntityPlayer p) {
        ItemStack h = p.getHeldItemMainhand();
        return h.isEmpty() || isMartial(h);
    }

    // ------------------------------------------------------------------ styles
    public static int learned(EntityPlayer p) {
        NBTTagCompound d = Stats.data(p);
        return d.hasKey("ma_learn") ? d.getInteger("ma_learn") | 1 : 1;
    }

    public static boolean knows(EntityPlayer p, int st) {
        return (learned(p) & 1 << st) != 0;
    }

    /** Worn style (Brawler by default). */
    public static int style(EntityPlayer p) {
        NBTTagCompound d = Stats.data(p);
        int s = d.hasKey("ma_style") ? d.getInteger("ma_style") : BRAWLER;
        return s >= 0 && s < ID.length && knows(p, s) ? s : BRAWLER;
    }

    public static int hits(EntityPlayer p, int st) {
        return Stats.data(p).getInteger("ma_mx" + st);
    }

    public static int mastery(EntityPlayer p, int st) {
        int n = hits(p, st), lv = 0;
        for (int i = 1; i < MASTERY.length; i++) {
            if (n >= MASTERY[i]) {
                lv = i;
            }
        }
        return lv;
    }

    /** The skill identity of the player's hands: the worn style when bare-handed or holding a fist weapon. */
    public static SkillCatalog.Info heldInfo(EntityPlayer p) {
        ItemStack h = p.getHeldItemMainhand();
        SkillCatalog.Info in = SkillCatalog.identify(h);
        if (!Stats.on(p.world)) {
            return in;
        }
        String type = "fist_" + ID[style(p)];
        if (h.isEmpty()) {
            return new SkillCatalog.Info("martial:" + ID[style(p)], type, "iron", "");
        }
        if (in != null && isMartialType(in.type) && in.legend.isEmpty()) {
            return new SkillCatalog.Info(in.id, type, in.mat, "");
        }
        return in;
    }

    /** Show a body pose on this player for everyone around (and the player). */
    public static void pose(EntityPlayer p, int pose, int dur) {
        if (p.world.isRemote) {
            return;
        }
        SkillNet.MsgPose m = new SkillNet.MsgPose(p.getEntityId(), pose, Math.min(120, dur), style(p));
        SkillNet.NET.sendToAllTracking(m, p);
        if (p instanceof EntityPlayerMP) {
            SkillNet.NET.sendTo(m, (EntityPlayerMP) p);
        }
    }

    // ------------------------------------------------------------------ combo, passives, finishers
    private static final class Combo {
        int step;
        long last;
        int count;
    }

    private static final Map<UUID, Combo> COMBO = new HashMap<>();

    /** Combo step (0..3) for the HUD, from MsgHud.header; kept on the server. */
    public static int comboStep(EntityPlayer p) {
        Combo c = COMBO.get(p.getUniqueID());
        return c == null || p.world.getTotalWorldTime() - c.last > 30 ? 0 : c.step;
    }

    private static boolean basicHit(DamageSource s, EntityPlayer p) {
        return s.getImmediateSource() == p && DamageMeter.current == null && "player".equals(s.getDamageType());
    }

    /** True while a finisher or counter deals its own extra hits (they must not count as combo hits). */
    private static boolean inner;

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onHurt(LivingHurtEvent e) {
        if (inner) {
            return;
        }
        EntityLivingBase t = e.getEntityLiving();
        Entity src = e.getSource().getTrueSource();
        if (t.world.isRemote || !(src instanceof EntityPlayer) || src == t || !Stats.on(t.world)) {
            return;
        }
        EntityPlayer p = (EntityPlayer) src;
        if (!martialHands(p) || Pierce.isMagic(e.getSource()) || e.getSource().getImmediateSource() != p) {
            return;
        }
        int st = style(p);
        // mastery: +5% per level on every martial hit (skills included)
        e.setAmount(e.getAmount() * (1F + 0.05F * mastery(p, st)));
        if (!basicHit(e.getSource(), p)) {
            return;
        }
        long now = p.world.getTotalWorldTime();
        NBTTagCompound pd = p.getEntityData();
        if (p.getHeldItemMainhand().isEmpty()) {
            e.setAmount(e.getAmount() + 3F); // trained fists
        }
        Combo c = COMBO.computeIfAbsent(p.getUniqueID(), k -> new Combo());
        c.step = now - c.last <= 30 ? c.step + 1 : 1;
        c.last = now;
        NBTTagCompound d = Stats.data(p);
        d.setInteger("ma_mx" + st, d.getInteger("ma_mx" + st) + 1);
        boolean finisher = c.step >= 4;
        if (finisher) {
            c.step = 0;
            pose(p, st == BRAWLER ? POSE_UPPERCUT : st == IRONLEG ? POSE_SWEEP : POSE_HOOK, st == IRONLEG ? 14 : 10);
        } else {
            pose(p, POSE_COMBAT, 0); // guard stance + style for the basic punch / kick animation
        }
        // passives
        switch (st) {
            case IRONLEG:
                t.knockBack(p, 0.5F, p.posX - t.posX, p.posZ - t.posZ);
                break;
            case TIGER:
                if (now < pd.getLong("ua_tiger_until") || p.getRNG().nextFloat() < 0.2F) {
                    t.addPotionEffect(new PotionEffect(MobEffects.WITHER, 40, 0));
                }
                break;
            case STONE:
                e.setAmount(e.getAmount() * 1.2F);
                if (now < pd.getLong("ua_stone_until")) {
                    shock(p, t, e.getAmount() * 0.4F, 2.5);
                }
                break;
            case PALM:
                if (++c.count % 4 == 0) {
                    p.heal(1F);
                }
                break;
            case ARCANE:
                if (Power.mana(p) >= 2) {
                    Power.setMana(p, Power.mana(p) - 2);
                    e.setAmount(e.getAmount() * 1.25F);
                    int el = Magic.element(p, 0);
                    Fx.at(p.world, Fx.IMPACT, el >= 0 ? Magic.ELEMENT_FX[el] : Fx.ARCANE, t.posX, t.posY + t.height * 0.6, t.posZ, 0.6);
                    if (now < pd.getLong("ua_avatar_until")) {
                        shock(p, t, e.getAmount() * 0.5F, 2.0);
                    }
                }
                break;
            default:
                break;
        }
        if (!finisher) {
            return;
        }
        Fx.sound(p, "minecraft:entity.player.attack.crit", 1F, 0.9F);
        switch (st) {
            case BRAWLER:
                e.setAmount(e.getAmount() * 1.6F);
                t.motionY += 0.55;
                t.velocityChanged = true;
                break;
            case IRONLEG:
                inner = true;
                try {
                    sweep(p, t, e.getAmount() * 0.6F);
                } finally {
                    inner = false;
                }
                t.knockBack(p, 1.2F, p.posX - t.posX, p.posZ - t.posZ);
                Fx.at(p.world, Fx.RING, Fx.WIND, p.posX, p.posY + 0.5, p.posZ, 1.2);
                break;
            case TIGER:
                e.setAmount(e.getAmount() * 1.4F);
                t.addPotionEffect(new PotionEffect(MobEffects.WITHER, 100, 1));
                break;
            case STONE:
                e.setAmount(e.getAmount() * 1.8F);
                t.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 30, 4));
                Fx.at(p.world, Fx.RING, Fx.EARTH, t.posX, t.posY + 0.1, t.posZ, 1.4);
                break;
            case PALM:
                t.knockBack(p, 2.2F, p.posX - t.posX, p.posZ - t.posZ);
                p.heal(2F);
                Fx.at(p.world, Fx.RING, Fx.WIND, t.posX, t.posY + 1, t.posZ, 1.0);
                break;
            case ARCANE: {
                int el = Magic.element(p, 0);
                Fx.at(p.world, Fx.EXPLOSION, el >= 0 ? Magic.ELEMENT_FX[el] : Fx.ARCANE, t.posX, t.posY + 1, t.posZ, 1.2);
                shock(p, t, e.getAmount() * 0.8F, 3.0);
                break;
            }
            default:
                break;
        }
    }

    /** Iron Leg finisher: everyone in a cone in front of the player takes part of the hit. */
    private static void sweep(EntityPlayer p, EntityLivingBase t, float dmg) {
        Vec look = new Vec(p);
        for (EntityLivingBase o : SkillShapes.around(p.world, p.posX, p.posY, p.posZ, 3.5)) {
            double dx = o.posX - p.posX, dz = o.posZ - p.posZ, l = Math.max(0.1, Math.sqrt(dx * dx + dz * dz));
            if (o != t && (dx * look.x + dz * look.z) / l > 0.3) {
                o.hurtResistantTime = 0;
                o.attackEntityFrom(DamageSource.causePlayerDamage(p), dmg);
                o.knockBack(p, 1.0F, p.posX - o.posX, p.posZ - o.posZ);
            }
        }
    }

    private static final class Vec {
        final double x, z;

        Vec(EntityPlayer p) {
            double yaw = Math.toRadians(p.rotationYaw);
            x = -Math.sin(yaw);
            z = Math.cos(yaw);
        }
    }

    private static boolean shocking;

    /** Damage everyone else around a hit target (no chain: these hits do not count as basic hits). */
    private static void shock(EntityPlayer p, EntityLivingBase t, float dmg, double r) {
        if (shocking) {
            return;
        }
        shocking = true;
        try {
            for (EntityLivingBase o : SkillShapes.around(p.world, t.posX, t.posY, t.posZ, r)) {
                if (o != t) {
                    o.hurtResistantTime = 0;
                    o.attackEntityFrom(DamageSource.causeIndirectMagicDamage(p, p), dmg);
                }
            }
        } finally {
            shocking = false;
        }
    }

    /** Flowing Palm's Redirect: a melee hit during the guard is sent back. */
    @SubscribeEvent
    public static void onAttacked(LivingAttackEvent e) {
        if (!(e.getEntityLiving() instanceof EntityPlayer) || e.getEntityLiving().world.isRemote) {
            return;
        }
        EntityPlayer p = (EntityPlayer) e.getEntityLiving();
        NBTTagCompound pd = p.getEntityData();
        Entity a = e.getSource().getTrueSource();
        if (p.world.getTotalWorldTime() >= pd.getLong("ua_parry_until") || !(a instanceof EntityLivingBase) || e.getSource().getImmediateSource() != a) {
            return;
        }
        e.setCanceled(true);
        pd.setLong("ua_parry_until", 0);
        EntityLivingBase at = (EntityLivingBase) a;
        inner = true;
        try {
            at.attackEntityFrom(DamageSource.causePlayerDamage(p), Math.max(e.getAmount(), pd.getFloat("ua_parry_dmg")) * 1.5F);
        } finally {
            inner = false;
        }
        at.knockBack(p, 1.6F, p.posX - at.posX, p.posZ - at.posZ);
        p.heal(p.getMaxHealth() * 0.1F);
        Fx.at(p.world, Fx.IMPACT, Fx.WIND, at.posX, at.posY + 1, at.posZ, 1.2);
        Fx.sound(p, "minecraft:item.shield.block", 1F, 1.8F);
        p.sendStatusMessage(new TextComponentString("\u00a7bRedirected!"), true);
    }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent e) {
        EntityPlayer p = e.player;
        if (e.phase != TickEvent.Phase.END || p.world.isRemote || p.ticksExisted % 10 != 7 || !(p instanceof EntityPlayerMP)) {
            return;
        }
        double v = 0;
        if (Stats.on(p.world) && martialHands(p)) {
            int st = style(p);
            v = st == BRAWLER ? 0.8 : st == STONE ? -0.4 : 0;
        }
        Stats.setModifier(p.getEntityAttribute(SharedMonsterAttributes.ATTACK_SPEED), SPEED_ID, "UA martial style", v, 0);
    }

    // ------------------------------------------------------------------ manuals
    public static void register(List<Item> all) {
        MANUAL = new ItemManual();
        MANUAL.setRegistryName(UndyingArcana.MODID, "martial_manual");
        MANUAL.setUnlocalizedName(UndyingArcana.MODID + ".martial_manual");
        MANUAL.setCreativeTab(UndyingArcana.TAB);
        MANUAL.setMaxStackSize(1);
        all.add(MANUAL);
    }

    public static ItemStack manual(int st) {
        ItemStack s = new ItemStack(MANUAL);
        NBTTagCompound t = new NBTTagCompound();
        t.setInteger("style", st);
        s.setTagCompound(t);
        return s;
    }

    /** A random manual other than Brawler (drops, gifts). */
    public static ItemStack randomManual(java.util.Random r) {
        return manual(1 + r.nextInt(ID.length - 1));
    }

    public static int manualStyle(ItemStack s) {
        return s.hasTagCompound() ? Math.max(0, Math.min(ID.length - 1, s.getTagCompound().getInteger("style"))) : 0;
    }

    public static class ItemManual extends Item {
        @Override
        public String getItemStackDisplayName(ItemStack s) {
            return "\u00a76Martial Manual: " + NAME[manualStyle(s)];
        }

        @Override
        public void getSubItems(CreativeTabs tab, NonNullList<ItemStack> items) {
            if (isInCreativeTab(tab)) {
                for (int i = 0; i < ID.length; i++) {
                    items.add(manual(i));
                }
            }
        }

        @Override
        public ActionResult<ItemStack> onItemRightClick(World w, EntityPlayer p, EnumHand hand) {
            ItemStack s = p.getHeldItem(hand);
            if (!w.isRemote && Stats.on(w)) {
                int st = manualStyle(s);
                NBTTagCompound d = Stats.data(p);
                if (!knows(p, st)) {
                    d.setInteger("ma_learn", learned(p) | 1 << st);
                    if (!p.capabilities.isCreativeMode) {
                        s.shrink(1);
                    }
                    p.sendMessage(new TextComponentString("\u00a76You learned " + NAME[st] + "! \u00a77Hold nothing or a fist weapon and use Z / X / C."));
                    Fx.sound(p, "minecraft:entity.player.levelup", 0.7F, 1.2F);
                } else {
                    p.sendStatusMessage(new TextComponentString("\u00a77Style: \u00a76" + NAME[st]), true);
                }
                d.setInteger("ma_style", st);
            }
            return new ActionResult<>(EnumActionResult.SUCCESS, s);
        }

        @SideOnly(Side.CLIENT)
        @Override
        public void addInformation(ItemStack s, World w, List<String> tip, ITooltipFlag flag) {
            int st = manualStyle(s);
            tip.add("\u00a77Passive: " + PASSIVE[st]);
            tip.add("\u00a77Finisher (4th hit): " + FINISHER[st]);
            tip.add("\u00a78Z / X / C: " + ArmoryMoves.quickName("fist_" + ID[st]) + ", " + ArmoryMoves.heavyName("fist_" + ID[st]) + ", "
                    + ArmoryMoves.artName("fist_" + ID[st]));
            tip.add("\u00a78Right-click to learn it, or to switch to it if you know it.");
        }
    }
}
