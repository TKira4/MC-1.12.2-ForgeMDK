package com.imba.undyingarcana;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.MobEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.play.server.SPacketSoundEffect;
import net.minecraft.potion.PotionEffect;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Server side of the weapon skill system: key input, Heavy charging (held key), cooldowns, cast
 * bookkeeping (ranks, build, preset, quests), the scheduler used by multi-stage skills, and HUD sync.
 *
 * Player data it reads (written by the pack's CraftTweaker scripts): za_class, za_rrank / za_wrank / za_mrank,
 * za_lv_atk / za_lv_arc / za_lv_mag / za_lv_def, za_build, za_tune_&lt;family&gt;, tags za_path_*, za_tr_quick,
 * za_tr_volley, za_ac_&lt;n&gt;. Cooldowns are per move set, so every weapon of one type shares them (compound za_cds,
 * key "t:&lt;type&gt;#&lt;slot&gt;"; legend weapons keep their own "&lt;item id&gt;#&lt;slot&gt;");
 * scripts shorten all of them by adding ticks to za_cd_cut.
 */
@Mod.EventBusSubscriber(modid = UndyingArcana.MODID)
public final class SkillServer {

    private static long tick;
    private static final List<Task> TASKS = new ArrayList<>();
    private static final List<Task> PENDING = new ArrayList<>();
    private static final Map<UUID, State> STATES = new HashMap<>();

    private SkillServer() {
    }

    private static final class Task {
        final long at;
        final Runnable r;

        Task(long at, Runnable r) {
            this.at = at;
            this.r = r;
        }
    }

    private static final class State {
        int src;          // Heavy charge source: 0 none, 1 held right-click, 2 key
        long start;
        long lastSignal;
        String item = "";
        int need;
        int tierShown;
        int hudHash;
        long hudSent;
        final boolean[] cooling = new boolean[3];
        String coolItem = "";
        // drawing a weapon: skills of a newly held weapon type wait drawTotal ticks
        String drawKey = "";
        long drawUntil;
        int drawTotal = 1;
    }

    /** True while the HUD builds a preview context: one-shot bonuses (Weapon Master, Overcharge) must not trigger. */
    static boolean preview;

    /** Cooldown key of a weapon: its move set (all katanas share), or the item itself for legend weapons. */
    static String cdKey(SkillCatalog.Info in) {
        return in.legend.isEmpty() ? "t:" + in.type : in.id;
    }

    private static final java.util.Set<String> LIGHT = new java.util.HashSet<>(java.util.Arrays.asList("dagger", "knives", "rapier",
            "sickles", "claws", "gauntlets", "chakram", "twinblades", "katana", "boomerang"));

    /** Ticks to draw a weapon before its skills can be used: heavy 1.5 s, light 0.6 s, others 1 s. */
    static int drawTicks(SkillCatalog.Info in) {
        return "heavy".equals(in.family) ? 30 : LIGHT.contains(in.type) || "fist".equals(in.family) ? 12 : 20;
    }

    /** Focus cost of a weapon skill (Quick 15, Heavy 20 + 5 per charge tier, Art 40). Wands pay mana instead. */
    public static int focusCost(int slot, int tier) {
        return slot == 0 ? 15 : slot == 2 ? 40 : 20 + 5 * Math.max(1, tier);
    }

    /** Ticks left before skills of the held weapon can be used (weapon being drawn). */
    public static long drawLeft(EntityPlayer p) {
        State s = STATES.get(p.getUniqueID());
        return s == null ? 0 : Math.max(0, s.drawUntil - p.world.getTotalWorldTime());
    }

    private static State state(EntityPlayer p) {
        return STATES.computeIfAbsent(p.getUniqueID(), k -> new State());
    }

    // ------------------------------------------------------------------ scheduler
    public static void schedule(int delay, Runnable r) {
        PENDING.add(new Task(tick + Math.max(1, delay), r));
    }

    /** Runs r(i) for i = 0..times-1, every 'every' ticks, starting after 'delay'. */
    public static void repeat(int delay, int every, int times, java.util.function.IntConsumer r) {
        for (int i = 0; i < times; i++) {
            final int k = i;
            schedule(delay + i * every, () -> r.accept(k));
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) {
            return;
        }
        tick++;
        if (!PENDING.isEmpty()) {
            TASKS.addAll(PENDING);
            PENDING.clear();
        }
        if (TASKS.isEmpty()) {
            return;
        }
        List<Task> due = new ArrayList<>();
        TASKS.removeIf(t -> {
            if (t.at <= tick) {
                due.add(t);
                return true;
            }
            return false;
        });
        for (Task t : due) {
            try {
                t.r.run();
            } catch (RuntimeException ex) {
                // a skill stage failed (entity gone, chunk unloaded...): skip it
            }
        }
    }

    // ------------------------------------------------------------------ helpers
    public static void cmd(String c) {
        MinecraftServer s = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (s != null) {
            s.getCommandManager().executeCommand(s, c);
        }
    }

    public static void score(EntityPlayer p, String objective, int add) {
        cmd("scoreboard players add " + p.getName() + " " + objective + " " + add);
    }

    public static void scoreSet(EntityPlayer p, String objective, int value) {
        cmd("scoreboard players set " + p.getName() + " " + objective + " " + value);
    }

    public static void msg(EntityPlayer p, String s) {
        p.sendStatusMessage(new TextComponentString(s), true);
    }

    /** Sound only this player hears. */
    public static void privateSound(EntityPlayerMP p, String id, float vol, float pitch) {
        SoundEvent s = SoundEvent.REGISTRY.getObject(new ResourceLocation(id));
        if (s != null) {
            p.connection.sendPacket(new SPacketSoundEffect(s, SoundCategory.PLAYERS, p.posX, p.posY, p.posZ, vol, pitch));
        }
    }

    public static String tagValue(EntityPlayer p, String prefix) {
        for (String t : p.getTags()) {
            if (t.startsWith(prefix)) {
                return t.substring(prefix.length());
            }
        }
        return "";
    }

    public static boolean hasTag(EntityPlayer p, String tag) {
        return p.getTags().contains(tag);
    }

    private static int skillLevel(EntityPlayer p, SkillCatalog.Info in) {
        return Stats.skillLevel(p, in);
    }

    private static String skillWord(SkillCatalog.Info in) {
        return in.isRanged() ? "Archery" : (in.isWand() ? "Magic" : "Attack");
    }

    public static int needLevel(int slot) {
        return slot == 0 ? SkillCatalog.QUICK_LEVEL : (slot == 2 ? SkillCatalog.ART_LEVEL : 1);
    }

    private static boolean unlocked(EntityPlayer p, SkillCatalog.Info in, int slot) {
        return !in.legend.isEmpty() || skillLevel(p, in) >= needLevel(slot);
    }

    /** Skill preset of a weapon family (0 Balanced, 1 Power, 2 Size, 3 Speed); kept through death in PlayerPersisted. */
    public static int tune(EntityPlayer p, String family) {
        return Math.max(0, Math.min(3, p.getEntityData().getCompoundTag("PlayerPersisted").getInteger("za_tune_" + family)));
    }

    // ---- per-weapon cooldowns: player data compound "za_cds", key "<item id>#<slot>" = expiry, "<item id>#<slot>t" = length
    private static NBTTagCompound cds(EntityPlayer p) {
        NBTTagCompound d = p.getEntityData();
        if (!d.hasKey("za_cds")) {
            d.setTag("za_cds", new NBTTagCompound());
        }
        return d.getCompoundTag("za_cds");
    }

    public static long cdEnd(EntityPlayer p, SkillCatalog.Info in, int slot) {
        return in == null ? 0 : cds(p).getLong(cdKey(in) + "#" + slot);
    }

    public static int cdTotal(EntityPlayer p, SkillCatalog.Info in, int slot) {
        return in == null ? 1 : Math.max(1, cds(p).getInteger(cdKey(in) + "#" + slot + "t"));
    }

    public static long cdLeft(EntityPlayer p, SkillCatalog.Info in, int slot) {
        return Math.max(0, cdEnd(p, in, slot) - p.world.getTotalWorldTime());
    }

    private static void setCooldown(EntityPlayer p, SkillCatalog.Info in, int slot, long end, int total) {
        NBTTagCompound c = cds(p);
        long now = p.world.getTotalWorldTime();
        // drop expired entries so the compound stays small
        for (String k : new java.util.ArrayList<>(c.getKeySet())) {
            if (!k.endsWith("t") && c.getLong(k) < now) {
                c.removeTag(k);
                c.removeTag(k + "t");
            }
        }
        c.setLong(cdKey(in) + "#" + slot, end);
        c.setInteger(cdKey(in) + "#" + slot + "t", total);
    }

    /** Shortens one skill slot of every weapon (slot -1 = all slots). */
    public static void reduceCooldown(EntityPlayer p, int slot, int ticks) {
        NBTTagCompound c = cds(p);
        for (String k : new java.util.ArrayList<>(c.getKeySet())) {
            if (k.endsWith("t")) {
                continue;
            }
            if (slot < 0 || k.endsWith("#" + slot)) {
                c.setLong(k, c.getLong(k) - ticks);
            }
        }
    }

    /** Element of a wand the player carries for Spellblade / Arcane Archer imbues (off hand first, then hotbar). */
    private static String imbueElement(EntityPlayer p) {
        SkillCatalog.Info off = SkillCatalog.identify(p.getHeldItemOffhand());
        if (off != null && off.isWand()) {
            return off.wandElement();
        }
        for (int i = 0; i < 9; i++) {
            SkillCatalog.Info h = SkillCatalog.identify(p.inventory.getStackInSlot(i));
            if (h != null && h.isWand()) {
                return h.wandElement();
            }
        }
        return "";
    }

    private static String capital(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    // ------------------------------------------------------------------ context
    public static SkillCtx prepare(EntityPlayerMP p, SkillCatalog.Info in, int slot) {
        SkillCtx c = new SkillCtx(p, in, slot);
        NBTTagCompound d = p.getEntityData();
        boolean newStats = Stats.on(p.world);
        if (in.isRanged() && Stats.isRanger(p)) {
            c.rr = Stats.rank(p, 'r');
        }
        if (in.isMelee() && Stats.isWarrior(p)) {
            c.er = Stats.rank(p, 'w');
        }
        if (in.isWand() && Stats.isMage(p)) {
            c.er = Stats.rank(p, 'm');
        }
        c.path = tagValue(p, "za_path_");
        c.build = d.getString("za_build");
        c.level = skillLevel(p, in);
        c.lvlK = in.isRanged() ? 0.25F : (in.isWand() ? 0.25F : 0.2F);
        c.tierDmg = SkillCatalog.tierDamage(in.mat);
        c.magic = in.isWand();
        c.projectile = in.isRanged();
        if (in.isBow()) {
            ItemStack bowStack = p.getHeldItemMainhand();
            int power = net.minecraft.enchantment.EnchantmentHelper.getEnchantmentLevel(net.minecraft.init.Enchantments.POWER, bowStack);
            c.dmgMult *= 1.0F + 0.2F * power;
            c.punch = net.minecraft.enchantment.EnchantmentHelper.getEnchantmentLevel(net.minecraft.init.Enchantments.PUNCH, bowStack);
            c.flame = net.minecraft.enchantment.EnchantmentHelper.getEnchantmentLevel(net.minecraft.init.Enchantments.FLAME, bowStack) > 0;
        }
        c.element = SkillCatalog.elementOf(in);
        ArmoryMoves.element(c);
        if (in.isWand() || (!in.legend.isEmpty() && !"steel".equals(c.element))
                || (ArmoryMoves.has(in.type) && !"steel".equals(c.element) && !"blood".equals(c.element))) {
            c.status = c.element; // armory weapons with an element apply it too, so weapon + spell can react
        }
        if (newStats ? Stats.imbues(p) && (in.isMelee() || in.isRanged())
                : ("spellblade".equals(c.build) && in.isMelee()) || ("arcane_archer".equals(c.build) && in.isRanged())) {
            String ie = imbueElement(p);
            if (!ie.isEmpty()) {
                c.element = ie;
                c.status = ie;
                c.imbued = true;
                c.dmgMult *= 1.1F;
            }
        }
        c.el = Fx.elementOf(c.element);
        // build bonuses
        if (newStats) {
            Stats.skillBonus(c);
        } else if (("warrior".equals(c.build) && in.isMelee()) || ("marksman".equals(c.build) && in.isRanged()) || ("mage".equals(c.build) && in.isWand())) {
            c.dmgMult *= 1.15F;
        }
        if ("mage".equals(c.build) && in.isWand()) {
            c.size *= 1.15F;
        }
        if (("juggernaut".equals(c.build) && in.isMelee()) || ("sentinel".equals(c.build) && in.isRanged())) {
            c.dmgMult *= 1.1F;
        }
        // preset
        switch (tune(p, in.family)) {
            case 1: c.dmgMult *= 1.3F; c.size *= 0.85F; break;
            case 2: c.dmgMult *= 0.85F; c.size *= 1.4F; break;
            case 3: c.dmgMult *= 0.85F; c.speed *= 1.4F; break;
            default: break;
        }
        if (!in.legend.isEmpty()) {
            c.dmgMult *= 1.25F;
        }
        // names and rank scaling
        if (slot == 0) {
            c.name = SkillCatalog.quickName(in);
        } else if (slot == 2) {
            c.name = SkillCatalog.artName(in);
        } else {
            c.name = SkillCatalog.heavyName(in.type);
            String[] evo = SkillCatalog.evoNames(in.type);
            if (in.isRanged()) {
                if (c.rr >= 4 && !"switch".equals(in.type)) {
                    c.name = evo[1];
                } else if (c.rr >= 2 && !"switch".equals(in.type)) {
                    c.name = evo[0];
                }
                if ("crossbow".equals(in.type)) {
                    c.dmgMult *= c.rr >= 4 ? 1.4F * 1.3F : (c.rr >= 2 ? 1.4F : 1.0F);
                } else if (in.isGun()) {
                    // guns have no extra projectiles per rank, so their Heavy scales like melee / wand Heavies
                    c.dmgMult *= c.rr >= 4 ? 1.6F : (c.rr >= 2 ? 1.3F : 1.0F);
                }
            } else if (c.er >= 4) {
                c.name = evo[1];
                c.dmgMult *= 1.6F;
            } else if (c.er >= 2) {
                c.name = evo[0];
                c.dmgMult *= 1.3F;
            }
        }
        int rank = Math.max(c.er, c.rr);
        if (slot != 1) {
            c.dmgMult *= rank >= 4 ? 1.3F : (rank >= 2 ? 1.15F : 1.0F);
        }
        return c;
    }

    private static int cooldownTicks(SkillCtx c, double base) {
        double m = SkillCatalog.tierCooldown(c.in.mat);
        if ((Stats.isWarrior(c.p) && c.in.isMelee()) || (Stats.isMage(c.p) && c.in.isWand())) {
            m *= 0.8;
        } else if (Stats.isRanger(c.p) && c.in.isRanged()) {
            m *= 0.7;
            if (c.rr >= 3) {
                m *= 0.85;
            }
        }
        if (c.er >= 3) {
            m *= 0.85;
        }
        if (c.slot == 0 && Relics.has(c.p, Relics.SWIFT_GLOVES)) {
            m *= 0.8;
        }
        if ("assassin".equals(c.path) && "dagger".equals(c.in.type) && c.slot == 1) {
            m *= 0.75;
        }
        if (hasTag(c.p, "za_tr_quick")) {
            m *= 0.9;
        }
        if (c.in.isRanged() && hasTag(c.p, "za_tr_volley")) {
            m *= 0.8;
        }
        if (tune(c.p, c.in.family) == 3) {
            m *= 0.75;
        }
        if ("marksman".equals(c.build) && c.in.isRanged() && c.slot == 0) {
            m *= 0.7;
        }
        if ("warden".equals(c.build) && c.slot == 2) {
            m *= 0.8;
        }
        if ("adept".equals(c.build)) {
            m *= 0.9;
        }
        if (Stats.on(c.w)) {
            m *= Gear.cooldownMult(c.p) * Power.cooldownMult(c.p);
        }
        return Math.max(10, (int) (base * m));
    }

    private static double baseCooldown(SkillCatalog.Info in, int slot) {
        if (slot == 0) {
            return SkillCatalog.quickStats(in)[2];
        }
        if (slot == 2) {
            return SkillCatalog.artStats(in)[2];
        }
        return SkillCatalog.heavyStats(in.type)[2];
    }

    private static int chargeNeed(EntityPlayer p, SkillCatalog.Info in) {
        double n = SkillCatalog.chargeTicks(in.type);
        String b = p.getEntityData().getString("za_build");
        if ("warrior".equals(b) && in.isMelee()) {
            n *= 0.75;
        }
        if (tune(p, in.family) == 3) {
            n *= 0.75;
        }
        return Math.max(6, (int) n);
    }

    // ------------------------------------------------------------------ input
    public static void onKey(EntityPlayerMP p, int slot, boolean down) {
        if (slot < 0 || slot > 2 || p.isDead || p.isSpectator()) {
            return;
        }
        if (slot == 1) {
            if (down) {
                startCharge(p, 2);
            } else {
                State s = state(p);
                if (s.src == 2) {
                    release(p);
                }
            }
            return;
        }
        if (down) {
            tryCast(p, slot, 1);
        }
    }

    private static boolean checkReady(EntityPlayerMP p, SkillCatalog.Info in, int slot) {
        if (!in.legend.isEmpty()) {
            String need = SkillCatalog.legendClass(in.legend);
            if (!Stats.isClass(p, need)) {
                msg(p, "\u00a7cOnly a " + need + " can awaken this legend's skills.");
                return false;
            }
        }
        if (!unlocked(p, in, slot)) {
            if (Stats.on(p.world)) {
                int pts = (int) Math.ceil((needLevel(slot) - 1) * 100 / 31.0);
                msg(p, "\u00a77" + SkillCatalog.KIND[slot] + " skill unlocks at " + pts + " " + (in.isWand() ? "Magic" : "fist".equals(in.family) ? "Strength" : "Weapons") + " points (K).");
            } else {
                msg(p, "\u00a77" + SkillCatalog.KIND[slot] + " skill unlocks at " + skillWord(in) + " level " + needLevel(slot) + ".");
            }
            return false;
        }
        long draw = drawLeft(p);
        if (draw > 0) {
            msg(p, "\u00a77Drawing weapon... \u00a7f" + String.format("%.1f", draw / 20.0) + "s");
            return false;
        }
        long left = cdLeft(p, in, slot);
        if (left > 0) {
            String n = slot == 0 ? SkillCatalog.quickName(in) : (slot == 2 ? SkillCatalog.artName(in) : SkillCatalog.heavyName(in.type));
            msg(p, "\u00a77" + n + " ready in \u00a7f" + (left / 20 + 1) + "s");
            return false;
        }
        return true;
    }

    private static void startCharge(EntityPlayerMP p, int src) {
        SkillCatalog.Info in = Martial.heldInfo(p);
        if (in == null) {
            if (src == 2) {
                msg(p, "\u00a77Hold a weapon, bow or wand to use a skill.");
            }
            return;
        }
        State s = state(p);
        if (s.src != 0) {
            return;
        }
        if (!checkReady(p, in, 1)) {
            s.lastSignal = p.world.getTotalWorldTime();
            return;
        }
        if (!in.isWand() && Stats.on(p.world) && !Power.hasFocus(p, focusCost(1, 1))) {
            s.lastSignal = p.world.getTotalWorldTime();
            return;
        }
        s.src = src;
        s.start = p.world.getTotalWorldTime();
        s.lastSignal = s.start;
        s.item = in.id;
        s.need = chargeNeed(p, in);
        s.tierShown = 0;
        Fx.sound(p, "minecraft:block.note.hat", 0.6F, 0.6F);
        s.hudHash = 0;
    }

    private static void release(EntityPlayerMP p) {
        State s = state(p);
        if (s.src == 0) {
            return;
        }
        long el = p.world.getTotalWorldTime() - s.start;
        int need = s.need;
        s.src = 0;
        s.hudHash = 0;
        s.lastSignal = p.world.getTotalWorldTime();
        if (el < need) {
            if (el > 4) {
                msg(p, "\u00a77Charge cancelled");
            }
            return;
        }
        int tier = el >= need * 2 ? 3 : (el >= need * 3 / 2 ? 2 : 1);
        tryCast(p, 1, tier);
    }

    // ------------------------------------------------------------------ cast
    private static void tryCast(EntityPlayerMP p, int slot, int tier) {
        SkillCatalog.Info in = Martial.heldInfo(p);
        if (in == null) {
            msg(p, "\u00a77Hold a weapon, bow or wand to use a skill.");
            return;
        }
        if (!checkReady(p, in, slot)) {
            return;
        }
        // Mana (new-world systems): wand skills cost mana
        if (in.isWand() && Stats.on(p.world) && !Power.spend(p, Power.wandCost(slot, tier))) {
            return;
        }
        // Focus (new-world systems): every other weapon skill costs focus
        if (!in.isWand() && Stats.on(p.world) && !Power.spendFocus(p, focusCost(slot, tier))) {
            return;
        }
        SkillCtx c = prepare(p, in, slot);
        if (slot == 1) {
            c.tier = tier;
            c.dmgMult *= tier >= 3 ? 1.33F : (tier == 2 ? 1.15F : 1.0F);
            c.size *= tier >= 3 ? 1.2F : (tier == 2 ? 1.1F : 1.0F);
        }
        // Mage Spell Weave: Arcane Charges add 10% each to wand Heavy skills and are used up.
        if (slot == 1 && in.isWand() && c.er >= 1) {
            String ac = tagValue(p, "za_ac_");
            if (!ac.isEmpty()) {
                try {
                    int n = Integer.parseInt(ac);
                    c.dmgMult *= 1.0F + 0.1F * n;
                    p.removeTag("za_ac_" + ac);
                } catch (NumberFormatException ignored) {
                    // malformed tag
                }
            }
        }
        int cd = c.freeCast ? 10 : cooldownTicks(c, baseCooldown(in, slot));
        setCooldown(p, in, slot, c.now + cd, cd);
        state(p).hudHash = 0;

        SkillMoves.cast(c);

        // build effects on cast
        if ("warden".equals(c.build)) {
            p.addPotionEffect(new PotionEffect(MobEffects.ABSORPTION, 160, 0, false, true));
        }
        if ("juggernaut".equals(c.build)) {
            p.addPotionEffect(new PotionEffect(MobEffects.RESISTANCE, 50, 1, false, true));
        }
        if ("sentinel".equals(c.build)) {
            p.addPotionEffect(new PotionEffect(MobEffects.RESISTANCE, 60, 0, false, true));
        }
        if ("paladin".equals(c.build)) {
            for (EntityPlayer o : p.world.getEntitiesWithinAABB(EntityPlayer.class, p.getEntityBoundingBox().grow(6))) {
                o.addPotionEffect(new PotionEffect(MobEffects.ABSORPTION, 120, 0, false, true));
                o.heal(2.0F);
            }
            Fx.on(p, Fx.HEAL, Fx.HOLY, 3.0, 0, 10);
        }
        // quests / tutorial counters
        scoreSet(p, "za_t_skill", 1);
        if (in.isRanged()) {
            score(p, "za_p_bowsk", 1);
            scoreSet(p, "za_t_bow", 1);
        }
        if (in.isWand()) {
            scoreSet(p, "za_t_wand", 1);
        }
        if (slot == 0) {
            scoreSet(p, "za_wa_quick", 1);
        } else if (slot == 2) {
            scoreSet(p, "za_wa_art", 1);
        } else if (tier >= 3) {
            scoreSet(p, "za_wa_t3", 1);
        }
        if (c.imbued) {
            scoreSet(p, "za_wa_imbue", 1);
        }
        // report after the instant part of the skill
        final SkillCtx fc = c;
        schedule(2, () -> {
            String col = SkillCatalog.tierColor(fc.in.mat);
            if (Math.max(fc.er, fc.rr) >= 2 || !fc.legend.isEmpty()) {
                col = "\u00a76";
            }
            String t = fc.slot == 1 && fc.tier > 1 ? " \u00a7e" + (fc.tier == 3 ? "III" : "II") : "";
            msg(fc.p, col + fc.name + "!" + t + (fc.hits > 0 ? " \u00a77" + fc.hits + " hit" : ""));
            // Vanguard: hits shorten your other skills' cooldowns (0.5s each, up to 3s).
            if ("vanguard".equals(fc.build) && fc.hits > 0) {
                int red = Math.min(60, fc.hits * 10);
                for (int i = 0; i < 3; i++) {
                    if (i != fc.slot) {
                        reduceCooldown(fc.p, i, red);
                    }
                }
            }
        });
    }

    // ------------------------------------------------------------------ per-player tick: charging and HUD
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.player.world.isRemote || !(e.player instanceof EntityPlayerMP)) {
            return;
        }
        EntityPlayerMP p = (EntityPlayerMP) e.player;
        State s = state(p);
        long now = p.world.getTotalWorldTime();
        if (s.src != 0) {
            SkillCatalog.Info in = Martial.heldInfo(p);
            if (in == null || !in.id.equals(s.item) || p.isDead) {
                s.src = 0;
                s.hudHash = 0;
                msg(p, "\u00a77Charge cancelled");
            } else if (s.src == 1 && now - s.lastSignal > 6) {
                release(p);
            } else {
                long el = now - s.start;
                int tier = el >= s.need * 2L ? 3 : (el >= s.need * 3L / 2 ? 2 : (el >= s.need ? 1 : 0));
                int elId = Fx.elementOf(SkillCatalog.elementOf(in));
                if (tier > s.tierShown) {
                    s.tierShown = tier;
                    Fx.send(p.world, Fx.TIERUP, elId, p.posX, p.posY, p.posZ, 0, 0, 0, 1, tier, 6, p.getEntityId());
                    Fx.sound(p, "minecraft:block.note.pling", 0.7F, 0.9F + 0.3F * tier);
                }
                if (el % 3 == 0) {
                    Fx.send(p.world, Fx.GATHER, elId, p.posX, p.posY, p.posZ, 0, 0, 0, 1.8, tier, 4, p.getEntityId());
                }
                if (el > s.need * 2L + 60) {
                    release(p);
                }
            }
        }
        // Cooldown cuts from the pack scripts (Apex Hunter, Elemental Mastery...): "za_cd_cut" ticks off every skill.
        int cut = p.getEntityData().getInteger("za_cd_cut");
        if (cut > 0) {
            p.getEntityData().setInteger("za_cd_cut", 0);
            reduceCooldown(p, -1, cut);
        }
        // "ready" ping for Heavy and Art of the weapon in hand
        SkillCatalog.Info held = Martial.heldInfo(p);
        if (held != null) {
            // a different weapon type in hand: its skills wait while it is drawn (empty hands keep the last one)
            String key = cdKey(held);
            if (!key.equals(s.drawKey)) {
                if (!s.drawKey.isEmpty() && Stats.on(p.world)) {
                    s.drawTotal = drawTicks(held);
                    s.drawUntil = now + s.drawTotal;
                }
                s.drawKey = key;
            }
        }
        String heldId = held == null ? "" : held.id;
        if (!heldId.equals(s.coolItem)) {
            s.coolItem = heldId;
            s.cooling[1] = false;
            s.cooling[2] = false;
        }
        for (int i = 1; i < 3; i++) {
            boolean cooling = cdLeft(p, held, i) > 0;
            if (s.cooling[i] && !cooling) {
                privateSound(p, "minecraft:block.note.pling", 0.4F, i == 2 ? 2.0F : 1.6F);
            }
            s.cooling[i] = cooling;
        }
        if (now % 2 == 0) {
            syncHud(p, s, now);
        }
    }

    private static void syncHud(EntityPlayerMP p, State s, long now) {
        SkillNet.MsgHud m = new SkillNet.MsgHud();
        SkillCatalog.Info in = Martial.heldInfo(p);
        NBTTagCompound d = p.getEntityData();
        int hash;
        if (in == null) {
            m.active = false;
            hash = 1;
        } else {
            m.active = true;
            m.type = in.id;
            SkillCtx c;
            preview = true;
            try {
                c = prepare(p, in, 1);
            } finally {
                preview = false;
            }
            String head = SkillCatalog.buildName(c.build);
            if (c.imbued) {
                head += " [" + capital(c.element) + "]";
            }
            int tune = tune(p, in.family);
            if (tune > 0) {
                head += (head.isEmpty() ? "" : " - ") + SkillCatalog.presetNames()[Math.min(3, tune)];
            }
            if ("fist".equals(in.family)) {
                int st = Martial.style(p), lv = Martial.mastery(p, st);
                head = "\u00a76" + Martial.NAME[st] + (lv > 0 ? " " + new String[]{"I", "II", "III", "IV", "V"}[lv - 1] : "")
                        + (head.isEmpty() ? "" : "\u00a7r - " + head);
                m.combo = Martial.comboStep(p);
            }
            m.header = head;
            m.name[0] = SkillCatalog.quickName(in);
            m.name[1] = c.name;
            m.name[2] = SkillCatalog.artName(in);
            long draw = Math.max(0, s.drawUntil - now);
            for (int i = 0; i < 3; i++) {
                m.icon[i] = SkillCatalog.icon(in, i);
                m.cdLeft[i] = (int) cdLeft(p, in, i);
                m.cdTotal[i] = cdTotal(p, in, i);
                if (draw > m.cdLeft[i]) {
                    m.cdLeft[i] = (int) draw;
                    m.cdTotal[i] = s.drawTotal;
                }
                m.cost[i] = in.isWand() || !Stats.on(p.world) ? 0 : focusCost(i, 1);
                m.unlocked[i] = unlocked(p, in, i);
                m.need[i] = needLevel(i);
            }
            if (s.src != 0) {
                m.chargeEl = (int) (now - s.start);
                m.chargeNeed = s.need;
            }
            if (in.isGun()) {
                ItemStack gs = p.getHeldItemMainhand();
                m.gun = true;
                m.mag = GunLogic.mag(gs);
                m.magMax = GunLogic.magSize(in.type);
                m.reserve = p.capabilities.isCreativeMode ? 999 : GunLogic.reserve(p, in.type);
                m.reloadLeft = (int) GunLogic.reloadLeft(p, gs);
                m.reloadTotal = gs.hasTagCompound() ? Math.max(1, gs.getTagCompound().getInteger("ReloadT")) : 1;
            }
            m.focus = (int) Power.focus(p);
            int comboHash = m.combo;
            m.focusMax = Power.maxFocus(p);
            hash = Objects.hash(in.id, m.header, m.name[1], cdEnd(p, in, 0), cdEnd(p, in, 1), cdEnd(p, in, 2), m.focus, m.focusMax, s.drawUntil, comboHash,
                    m.unlocked[0], m.unlocked[1], m.unlocked[2], s.src, s.start, m.mag, m.reserve, m.reloadLeft > 0);
        }
        if (hash != s.hudHash || now - s.hudSent >= 40) {
            s.hudHash = hash;
            s.hudSent = now;
            SkillNet.NET.sendTo(m, p);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        STATES.remove(e.player.getUniqueID());
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent e) {
        State s = STATES.get(e.player.getUniqueID());
        if (s != null) {
            s.src = 0;
            s.hudHash = 0;
        }
    }
}
