package com.imba.undyingarcana;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.DamageSource;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Character level (1-100), 2 stat points per level into Vitality / Magic / Strength / Weapons, and the build they make
 * (rules in Builds). Data lives in PlayerPersisted (kept through death).
 *
 * Only active when the game rule uaBuilds is true (default: true unless Reskillable is installed). While it is off,
 * every helper here falls back to the old script data (za_class, za_lv_*, za_*rank), so the current world plays as before.
 */
@Mod.EventBusSubscriber(modid = UndyingArcana.MODID)
public final class Stats {
    public static final String RULE = "uaBuilds", RULE_XP = "uaXpRate";
    private static final UUID HEALTH_ID = UUID.fromString("6f1d2c0e-6a8b-4b4e-9d61-5a0c1f3b7e21");
    private static final UUID KB_ID = UUID.fromString("0b7e5a34-1c9d-4f2a-8e63-2d4c9b1a6f58");
    private static final UUID SPEED_ID = UUID.fromString("3c51e0a2-7d4b-4c19-b8e2-91f0d6a4c7b3");
    private static final UUID ARMOR_ID = UUID.fromString("a7e2c4d1-5b38-4f6a-9c0e-2b1d8f4e6a95");
    private static final UUID TOUGH_ID = UUID.fromString("e4b9d0c3-2a71-4e58-8f16-7c3a5d9b0e24");
    private static final UUID LUCK_ID = UUID.fromString("5f0a8c6e-9d23-4b17-a4e5-0c8b2f7d1e36");
    private static final Set<UUID> DIRTY = new HashSet<>();

    private Stats() {
    }

    // ------------------------------------------------------------------ data
    public static boolean on(World w) {
        GameRules r = w.getGameRules();
        return r.hasRule(RULE) && r.getBoolean(RULE);
    }

    static NBTTagCompound data(EntityPlayer p) {
        NBTTagCompound root = p.getEntityData();
        if (!root.hasKey("PlayerPersisted")) {
            root.setTag("PlayerPersisted", new NBTTagCompound());
        }
        NBTTagCompound pp = root.getCompoundTag("PlayerPersisted");
        if (!pp.hasKey("ua_stats")) {
            NBTTagCompound s = new NBTTagCompound();
            s.setInteger("lvl", 1);
            pp.setTag("ua_stats", s);
        }
        return pp.getCompoundTag("ua_stats");
    }

    public static int level(EntityPlayer p) {
        return Math.max(1, data(p).getInteger("lvl"));
    }

    public static int xp(EntityPlayer p) {
        return data(p).getInteger("xp");
    }

    public static int[] stats(EntityPlayer p) {
        NBTTagCompound d = data(p);
        int[] s = new int[Builds.COUNT];
        for (int i = 0; i < s.length; i++) {
            s[i] = d.getInteger("s" + i);
        }
        return s;
    }

    public static int unspent(EntityPlayer p) {
        int used = 0;
        for (int v : stats(p)) {
            used += v;
        }
        return Builds.points(level(p)) - used;
    }

    public static int build(EntityPlayer p) {
        return Builds.build(level(p), stats(p));
    }

    // ------------------------------------------------------------------ helpers used by the skill system
    public static boolean isMage(EntityPlayer p) {
        return on(p.world) ? Builds.uses(build(p), Builds.MAG) : "mage".equals(p.getEntityData().getString("za_class"));
    }

    public static boolean isWarrior(EntityPlayer p) {
        return on(p.world) ? Builds.uses(build(p), Builds.WPN) : "warrior".equals(p.getEntityData().getString("za_class"));
    }

    /** Bows and guns: in the new system any Weapons build is a ranger too. */
    public static boolean isRanger(EntityPlayer p) {
        return on(p.world) ? Builds.uses(build(p), Builds.WPN) || Builds.uses(build(p), Builds.AGI) : "ranger".equals(p.getEntityData().getString("za_class"));
    }

    public static boolean isClass(EntityPlayer p, String cls) {
        switch (cls) {
            case "mage": return isMage(p);
            case "warrior": return isWarrior(p);
            case "ranger": return isRanger(p);
            default: return cls.equals(p.getEntityData().getString("za_class"));
        }
    }

    public static String className(EntityPlayer p) {
        if (on(p.world)) {
            return Builds.NAME[build(p)];
        }
        String c = p.getEntityData().getString("za_class");
        return c.isEmpty() ? "no class" : c;
    }

    /** Rank 0..5 for 'w' warrior (melee), 'r' ranger (ranged), 'm' mage (wands). */
    public static int rank(EntityPlayer p, char kind) {
        if (!on(p.world)) {
            NBTTagCompound d = p.getEntityData();
            return kind == 'r' ? d.getInteger("za_rrank") : kind == 'm' ? d.getInteger("za_mrank") : d.getInteger("za_wrank");
        }
        return Builds.rank(stats(p)[kind == 'm' ? Builds.MAG : Builds.WPN]);
    }

    public static int skillLevel(EntityPlayer p, SkillCatalog.Info in) {
        if (!on(p.world)) {
            NBTTagCompound d = p.getEntityData();
            String k = in.isRanged() ? "za_lv_arc" : (in.isWand() ? "za_lv_mag" : "za_lv_atk");
            return Math.max(1, d.getInteger(k));
        }
        return Builds.skillLevel(stats(p)[in.isWand() ? Builds.MAG : Builds.WPN]);
    }

    /** Build signatures that change a skill cast (Weapon Master, Overcharge). */
    public static void skillBonus(SkillCtx c) {
        Signatures.onSkill(c);
        Gear.onSkill(c);
        Power.onSkill(c);
    }

    /** Conjurer imbues weapon skills with the carried wand's element (old Spellblade / Arcane Archer). */
    public static boolean imbues(EntityPlayer p) {
        int b = build(p);
        return b == Builds.CONJURER;
    }

    // ------------------------------------------------------------------ changes
    public static void addXp(EntityPlayer p, int amount) {
        if (amount <= 0 || !on(p.world)) {
            return;
        }
        NBTTagCompound d = data(p);
        amount = Math.round(amount * (1.0F + 0.01F * stats(p)[Builds.LUCK]));
        int lvl = level(p), xp = d.getInteger("xp") + amount;
        int gained = 0;
        while (lvl < Builds.MAX_LEVEL && xp >= Builds.need(lvl)) {
            xp -= Builds.need(lvl);
            lvl++;
            gained++;
        }
        if (lvl >= Builds.MAX_LEVEL) {
            xp = 0;
        }
        d.setInteger("lvl", lvl);
        d.setInteger("xp", xp);
        if (gained > 0) {
            p.sendMessage(new TextComponentString("\u00a7e\u00a7lLevel " + lvl + "! \u00a77+" + (2 * gained)
                    + " stat points. Press \u00a7fK\u00a77 to spend them."));
            Fx.sound(p.world, p.posX, p.posY, p.posZ, "minecraft:entity.player.levelup", 1.0F, 0.8F);
            Fx.at(p.world, Fx.TIERUP, Fx.HOLY, p.posX, p.posY + 1, p.posZ, 1.2);
            if (lvl == Builds.BUILD_LEVEL) {
                p.sendMessage(new TextComponentString("\u00a76Your build now counts. Changing it from here needs a Scroll of Rebirth."));
            }
        }
        DIRTY.add(p.getUniqueID());
    }

    /** op 0 = add points, 1 = take back (only before level 16), 2 = Scroll of Rebirth (refund all). */
    public static void act(EntityPlayerMP p, int op, int stat, int amount) {
        if (!on(p.world)) {
            return;
        }
        NBTTagCompound d = data(p);
        if (op == 2) {
            ItemStack scroll = findScroll(p);
            if (level(p) >= Builds.BUILD_LEVEL && scroll.isEmpty()) {
                p.sendMessage(new TextComponentString("\u00a7cYou need a Scroll of Rebirth."));
                return;
            }
            if (level(p) >= Builds.BUILD_LEVEL) {
                scroll.shrink(1);
            }
            for (int i = 0; i < Builds.COUNT; i++) {
                d.setInteger("s" + i, 0);
            }
            p.sendMessage(new TextComponentString("\u00a7dYour stat points are free to spend again."));
            Fx.at(p.world, Fx.HEAL, Fx.ARCANE, p.posX, p.posY + 1, p.posZ, 1.0);
        } else if (stat >= 0 && stat < Builds.COUNT) {
            int n = Math.max(1, Math.min(10, amount));
            if (op == 0) {
                n = Math.min(n, unspent(p));
                d.setInteger("s" + stat, d.getInteger("s" + stat) + n);
            } else if (op == 1 && level(p) < Builds.BUILD_LEVEL) {
                d.setInteger("s" + stat, Math.max(0, d.getInteger("s" + stat) - n));
            }
        }
        apply(p);
        sync(p); // answer at once so the Character screen feels instant
    }

    private static ItemStack findScroll(EntityPlayer p) {
        for (int i = 0; i < p.inventory.getSizeInventory(); i++) {
            ItemStack s = p.inventory.getStackInSlot(i);
            if (!s.isEmpty() && s.getItem().getRegistryName() != null
                    && "scroll_rebirth".equals(s.getItem().getRegistryName().getResourcePath())) {
                return s;
            }
        }
        return ItemStack.EMPTY;
    }

    /** Health and knockback modifiers, build tag (team prefix) above the head and in chat / tab list. */
    public static void apply(EntityPlayerMP p) {
        boolean on = on(p.world);
        int[] s = stats(p);
        int b = build(p);
        setModifier(p.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH), HEALTH_ID, "UA Vitality", on ? 0.5D * s[Builds.VIT] : 0);
        setModifier(p.getEntityAttribute(SharedMonsterAttributes.KNOCKBACK_RESISTANCE), KB_ID, "UA build",
                !on ? 0 : b == Builds.JUGGERNAUT ? 1.0D : b == Builds.GUARDIAN ? 0.5D : 0);
        setModifier(p.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED), SPEED_ID, "UA Agility", on ? 0.004D * s[Builds.AGI] : 0, 1);
        setModifier(p.getEntityAttribute(SharedMonsterAttributes.ARMOR), ARMOR_ID, "UA Defense", on ? 0.15D * s[Builds.DEF] : 0);
        setModifier(p.getEntityAttribute(SharedMonsterAttributes.ARMOR_TOUGHNESS), TOUGH_ID, "UA Defense", on ? 0.05D * s[Builds.DEF] : 0);
        setModifier(p.getEntityAttribute(SharedMonsterAttributes.LUCK), LUCK_ID, "UA Luck", on ? 0.05D * s[Builds.LUCK] : 0);
        if (p.getHealth() > p.getMaxHealth()) {
            p.setHealth(p.getMaxHealth());
        }
        if (on) {
            team(p, b);
            // Compatibility for pack scripts that still read the old za_class tag (legend passives, mythic gear):
            // the strongest of Weapons / Agility / Magic among the build's stats picks warrior / ranger / mage.
            String cls = "";
            int best = 0;
            int[][] map = {{Builds.WPN, 0}, {Builds.AGI, 1}, {Builds.MAG, 2}};
            for (int[] m : map) {
                if (Builds.uses(b, m[0]) && s[m[0]] > best) {
                    best = s[m[0]];
                    cls = new String[]{"warrior", "ranger", "mage"}[m[1]];
                }
            }
            if (cls.isEmpty()) {
                p.getEntityData().removeTag("za_class");
            } else {
                p.getEntityData().setString("za_class", cls);
            }
        }
    }

    private static void setModifier(IAttributeInstance a, UUID id, String name, double v) {
        setModifier(a, id, name, v, 0);
    }

    static void setModifier(IAttributeInstance a, UUID id, String name, double v, int op) {
        if (a == null) {
            return;
        }
        AttributeModifier m = a.getModifier(id);
        if (m != null && m.getAmount() == v) {
            return;
        }
        if (m != null) {
            a.removeModifier(m);
        }
        if (v != 0) {
            a.applyModifier(new AttributeModifier(id, name, v, op));
        }
    }

    private static TextFormatting teamColor(int b) {
        char c = Builds.COLOR[b].charAt(1);
        for (TextFormatting f : TextFormatting.values()) {
            if (f.isColor() && f.toString().charAt(1) == c) {
                return f;
            }
        }
        return TextFormatting.WHITE;
    }

    private static void team(EntityPlayerMP p, int b) {
        Scoreboard sb = p.getServerWorld().getScoreboard();
        ScorePlayerTeam cur = sb.getPlayersTeam(p.getName());
        if (b == Builds.ADVENTURER) {
            if (cur != null && cur.getName().startsWith("ua_b")) {
                sb.removePlayerFromTeam(p.getName(), cur);
            }
            return;
        }
        String name = "ua_b" + b;
        if (cur != null && cur.getName().equals(name)) {
            return;
        }
        if (cur != null && !cur.getName().startsWith("ua_b")) {
            return; // the player is on a team of someone else's; leave it alone
        }
        ScorePlayerTeam t = sb.getTeam(name);
        if (t == null) {
            t = sb.createTeam(name);
            t.setPrefix(Builds.COLOR[b] + "[" + Builds.NAME[b] + "] \u00a7r");
            t.setColor(teamColor(b));
            t.setAllowFriendlyFire(false);
        }
        sb.addPlayerToTeam(p.getName(), name);
    }

    public static void sync(EntityPlayerMP p) {
        int[] s = stats(p);
        SkillNet.NET.sendTo(new SkillNet.MsgStats(on(p.world), level(p), xp(p), s, build(p)), p);
    }

    // ------------------------------------------------------------------ events
    @SubscribeEvent
    public static void onWorldLoad(WorldEvent.Load e) {
        World w = e.getWorld();
        if (w.isRemote || w.provider.getDimension() != 0) {
            return;
        }
        GameRules r = w.getGameRules();
        if (!r.hasRule(RULE)) {
            r.addGameRule(RULE, Loader.isModLoaded("reskillable") ? "false" : "true", GameRules.ValueType.BOOLEAN_VALUE);
        }
        if (!r.hasRule(RULE_XP)) {
            r.addGameRule(RULE_XP, "100", GameRules.ValueType.NUMERICAL_VALUE);
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.player instanceof EntityPlayerMP) {
            apply((EntityPlayerMP) e.player);
            sync((EntityPlayerMP) e.player);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent e) {
        if (e.player instanceof EntityPlayerMP) {
            apply((EntityPlayerMP) e.player);
            e.player.setHealth(e.player.getMaxHealth());
            sync((EntityPlayerMP) e.player);
        }
    }

    @SubscribeEvent
    public static void onDimension(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (e.player instanceof EntityPlayerMP) {
            sync((EntityPlayerMP) e.player);
        }
    }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.player.world.isRemote || e.player.ticksExisted % 20 != 0
                || !(e.player instanceof EntityPlayerMP)) {
            return;
        }
        if (DIRTY.remove(e.player.getUniqueID())) {
            apply((EntityPlayerMP) e.player);
            sync((EntityPlayerMP) e.player);
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent e) {
        EntityLivingBase dead = e.getEntityLiving();
        if (dead.world.isRemote || dead instanceof EntityPlayer || !on(dead.world)) {
            return;
        }
        Entity src = e.getSource().getTrueSource();
        if (!(src instanceof EntityPlayerMP)) {
            return;
        }
        int xp;
        if (dead instanceof EntityUAZombie) {
            EntityUAZombie z = (EntityUAZombie) dead;
            if (z.owner != null) {
                return; // raised minions give nothing
            }
            xp = z.type().xp * 2 * (z.isElite() ? 3 : 1);
        } else if (dead instanceof EntityLSBoss) {
            xp = 400;
        } else if (dead instanceof IMob) {
            xp = Math.max(2, (int) (dead.getMaxHealth() / 5));
        } else {
            return;
        }
        xp = xp * Math.max(0, dead.world.getGameRules().getInt(RULE_XP)) / 100;
        EntityPlayerMP killer = (EntityPlayerMP) src;
        addXp(killer, xp);
        for (EntityPlayer other : dead.world.playerEntities) {
            if (other != killer && other.getDistanceSq(dead) < 32 * 32 && other.isEntityAlive()) {
                addXp(other, xp / 2);
            }
        }
    }

    /** Everyone online and alive when a Blood Moon sets. */
    public static void bloodMoonSurvived(World w) {
        if (!on(w)) {
            return;
        }
        int xp = 150 * Math.max(0, w.getGameRules().getInt(RULE_XP)) / 100;
        for (EntityPlayer p : w.playerEntities) {
            if (p.isEntityAlive()) {
                p.sendMessage(new TextComponentString("\u00a76You survived the Blood Moon. \u00a77+" + xp + " XP, +15 Soul Coins"));
                addXp(p, xp);
                Coins.give(p, 15);
                java.util.List<ItemStack> gifts = new java.util.ArrayList<>();
                gifts.add(Coins.randomGem(p.getRNG()));
                if (p.getRNG().nextFloat() < 0.30F) {
                    gifts.add(Coins.rareScroll(p.getRNG()));
                }
                if (p.getRNG().nextFloat() < 0.10F) {
                    gifts.add(SpellArts.randomScroll(p.getRNG()));
                }
                if (p.getRNG().nextFloat() < 0.10F) {
                    gifts.add(Coins.legendGem(p.getRNG()));
                }
                for (ItemStack gift : gifts) {
                    if (!p.inventory.addItemStackToInventory(gift)) {
                        p.dropItem(gift, false);
                    }
                }
            }
        }
    }

    /** Per-point effects only; build signatures live in Signatures. */
    @SubscribeEvent
    public static void onHurt(LivingHurtEvent e) {
        EntityLivingBase t = e.getEntityLiving();
        if (t.world.isRemote || !on(t.world)) {
            return;
        }
        DamageSource s = e.getSource();
        if (t instanceof EntityPlayer && !s.canHarmInCreative()) {
            int[] st = stats((EntityPlayer) t);
            float m = Builds.damageTaken(st[Builds.VIT]);
            if (s == DamageSource.FALL) {
                m *= 1.0F - Math.min(0.5F, 0.01F * st[Builds.AGI]);
            }
            e.setAmount(e.getAmount() * m);
        }
        Entity src = s.getTrueSource();
        if (!(src instanceof EntityPlayer) || src == t || DamageMeter.current != null) {
            return; // skill damage scales through the skill level instead
        }
        EntityPlayer p = (EntityPlayer) src;
        int[] st = stats(p);
        float m;
        if (Pierce.isMagic(s)) {
            m = 1.0F + 0.006F * st[Builds.MAG];
        } else if (s.getImmediateSource() == p && p.getHeldItemMainhand().isEmpty()) {
            m = 1.0F + 0.02F * st[Builds.STR];
        } else {
            m = 1.0F + 0.006F * st[Builds.WPN] + (s.getImmediateSource() == p ? 0.003F * st[Builds.STR] : 0);
        }
        e.setAmount(e.getAmount() * m);
    }

    // ------------------------------------------------------------------ command
    public static class Command extends CommandBase {
        @Override
        public String getName() {
            return "uastats";
        }

        @Override
        public String getUsage(ICommandSender sender) {
            return "/uastats [player] | /uastats xp <amount> [player] | /uastats level <1-100> [player] | /uastats reset [player]";
        }

        @Override
        public int getRequiredPermissionLevel() {
            return 0;
        }

        @Override
        public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
            return true;
        }

        @Override
        public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
            boolean admin = sender.canUseCommand(2, "uastats");
            if (args.length == 0 || args.length == 1 && !isOp(args[0])) {
                EntityPlayerMP p = args.length == 1 ? getPlayer(server, sender, args[0]) : getCommandSenderAsPlayer(sender);
                int[] s = stats(p);
                int b = build(p);
                sender.sendMessage(new TextComponentString(String.format("\u00a76%s: level %d (%d/%d XP), %s%s\u00a77, unspent %d",
                        p.getName(), level(p), xp(p), Builds.need(level(p)), Builds.COLOR[b], Builds.NAME[b], unspent(p))));
                StringBuilder sb = new StringBuilder("\u00a77");
                for (int i = 0; i < Builds.COUNT; i++) {
                    sb.append(Builds.STAT[i]).append(' ').append(s[i]).append(i < Builds.COUNT - 1 ? ", " : "");
                }
                sender.sendMessage(new TextComponentString(sb + (on(p.world) ? "" : " \u00a78(system off: /gamerule uaBuilds true)")));
                return;
            }
            if (!admin) {
                throw new CommandException("commands.generic.permission");
            }
            EntityPlayerMP p = args.length > 2 || "reset".equalsIgnoreCase(args[0]) && args.length > 1
                    ? getPlayer(server, sender, args[args.length - 1]) : getCommandSenderAsPlayer(sender);
            NBTTagCompound d = data(p);
            switch (args[0].toLowerCase()) {
                case "xp":
                    addXp(p, parseInt(args[1], 1, 1000000));
                    break;
                case "level":
                    d.setInteger("lvl", parseInt(args[1], 1, Builds.MAX_LEVEL));
                    d.setInteger("xp", 0);
                    break;
                case "reset":
                    p.getEntityData().getCompoundTag("PlayerPersisted").removeTag("ua_stats");
                    break;
                default:
                    throw new WrongUsageException(getUsage(sender));
            }
            apply(p);
            sync(p);
            notifyCommandListener(sender, this, "Stats of " + p.getName() + " updated");
        }

        private static boolean isOp(String a) {
            return "xp".equalsIgnoreCase(a) || "level".equalsIgnoreCase(a) || "reset".equalsIgnoreCase(a);
        }
    }
}
