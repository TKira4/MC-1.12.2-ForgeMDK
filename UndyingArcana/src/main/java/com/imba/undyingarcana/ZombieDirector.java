package com.imba.undyingarcana;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.state.IBlockState;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.projectile.EntityThrowable;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.world.ExplosionEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * The zombie director: spawns Undying Arcana zombies around players (overworld only), runs the Blood Moon,
 * spreads noise, and applies zombie-side damage rules (magic resist, riot shields, Corroded).
 *
 * Budgets: per player 3-10 by day (dark places only), 12-40 at night, 40-60 in a Blood Moon; 250 per world.
 * Game rules: uaDirector, uaBloodMoon, uaBloodMoonEvery (days), uaZombieGrief. While the old Bloodmoon mod is
 * installed the director and Blood Moon default to off, so the current world keeps its old mobs.
 */
@Mod.EventBusSubscriber(modid = UndyingArcana.MODID)
public final class ZombieDirector {
    public static final String RULE_ON = "uaDirector", RULE_BM = "uaBloodMoon", RULE_BM_EVERY = "uaBloodMoonEvery", RULE_GRIEF = "uaZombieGrief";
    public static final int CAP_PLAYER = 60, CAP_WORLD = 250;
    private static boolean bloodMoonActive;
    private static long forcedBloodMoonDay = -1;

    private ZombieDirector() {
    }

    // ------------------------------------------------------------------ rules
    @SubscribeEvent
    public static void onWorldLoad(WorldEvent.Load e) {
        World w = e.getWorld();
        if (w.isRemote || w.provider.getDimension() != 0) {
            return;
        }
        GameRules r = w.getGameRules();
        String def = Loader.isModLoaded("bloodmoon") ? "false" : "true";
        if (!r.hasRule(RULE_ON)) {
            r.addGameRule(RULE_ON, def, GameRules.ValueType.BOOLEAN_VALUE);
        }
        if (!r.hasRule(RULE_BM)) {
            r.addGameRule(RULE_BM, def, GameRules.ValueType.BOOLEAN_VALUE);
        }
        if (!r.hasRule(RULE_BM_EVERY)) {
            r.addGameRule(RULE_BM_EVERY, "8", GameRules.ValueType.NUMERICAL_VALUE);
        }
        if (!r.hasRule(RULE_GRIEF)) {
            r.addGameRule(RULE_GRIEF, "true", GameRules.ValueType.BOOLEAN_VALUE);
        }
    }

    public static boolean isNight(World w) {
        long t = w.getWorldTime() % 24000L;
        return t >= 13000L && t < 23000L;
    }

    public static boolean isBloodMoon(World w) {
        if (!isNight(w)) {
            return false;
        }
        long day = w.getWorldTime() / 24000L;
        if (day == forcedBloodMoonDay) {
            return true;
        }
        int every = Math.max(2, w.getGameRules().getInt(RULE_BM_EVERY));
        return w.getGameRules().getBoolean(RULE_BM) && day % every == every - 1;
    }

    // ------------------------------------------------------------------ tick
    @SubscribeEvent
    public static void onWorldTick(TickEvent.WorldTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.world.isRemote || e.world.provider.getDimension() != 0
                || e.world.getTotalWorldTime() % 20 != 0) {
            return;
        }
        WorldServer w = (WorldServer) e.world;
        boolean bm = isBloodMoon(w);
        if (bm != bloodMoonActive) {
            bloodMoonActive = bm;
            announce(w, bm);
        }
        if (!w.getGameRules().getBoolean(RULE_ON) && forcedBloodMoonDay < 0) {
            return;
        }
        // Blood Moon midnight: a Zombie Warlord comes for a random survivor (replaces the old threats script)
        long day = w.getWorldTime() / 24000L, tod = w.getWorldTime() % 24000L;
        if (bm && tod >= 18000L && tod < 18400L && warlordDay != day) {
            warlordDay = day;
            spawnWarlord(w, day);
        }
        List<EntityUAZombie> all = w.getEntities(EntityUAZombie.class, z -> z != null && z.isEntityAlive());
        if (all.size() >= CAP_WORLD) {
            return;
        }
        boolean night = isNight(w);
        long week = EntityUAZombie.week(w);
        int budget = CAP_WORLD - all.size();
        for (EntityPlayer p : w.playerEntities) {
            if (p.isSpectator() || !p.isEntityAlive() || budget <= 0) {
                continue;
            }
            int near = 0;
            for (EntityUAZombie z : all) {
                double dx = z.posX - p.posX, dz = z.posZ - p.posZ;
                if (dx * dx + dz * dz < 64 * 64) {
                    near++;
                }
            }
            int target = bm ? (int) Math.min(60, 40 + 2 * week) : night ? (int) Math.min(40, 12 + 3 * week) : (int) Math.min(10, 3 + week);
            target = Math.min(target, CAP_PLAYER);
            if (near >= target || w.rand.nextFloat() > (bm ? 1.0F : 0.5F)) {
                continue;
            }
            budget -= spawnGroup(w, p, Math.min(Math.min(target - near, budget), bm ? 6 : 4), night, bm, week);
        }
    }

    private static void announce(WorldServer w, boolean rising) {
        String msg = rising ? "\u00a74\u00a7lThe Blood Moon rises. \u00a7cThe dead are coming." : "\u00a77The Blood Moon sets.";
        for (EntityPlayer p : w.playerEntities) {
            p.sendMessage(new TextComponentString(msg));
            if (rising) {
                Fx.sound(w, p.posX, p.posY, p.posZ, "minecraft:entity.wither.spawn", 0.6F, 0.5F);
            }
            if (p instanceof EntityPlayerMP) {
                SkillNet.NET.sendTo(new SkillNet.MsgMoon(rising), (EntityPlayerMP) p);
            }
        }
        if (!rising) {
            forcedBloodMoonDay = -1;
            Stats.bloodMoonSurvived(w);
            for (EntityPlayer p : w.playerEntities) {
                if (p.isEntityAlive()) {
                    Quests.bloodMoonSurvived(p);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.player instanceof EntityPlayerMP && !e.player.world.isRemote) {
            SkillNet.NET.sendTo(new SkillNet.MsgMoon(bloodMoonActive), (EntityPlayerMP) e.player);
        }
    }

    // ------------------------------------------------------------------ spawning
    /** Spawns up to n zombies near one point 24-44 blocks from the player. Returns how many spawned. */
    private static long warlordDay = -1;

    /** Tier 1 for Blood Moons 1-3, 2 for 4-6, 3 for 7-9, 4 after that. */
    static int warlordTier(World w, long day) {
        int every = Math.max(2, w.getGameRules().getInt(RULE_BM_EVERY));
        return (int) Math.min(4, 1 + (day / every) / 3);
    }

    static void spawnWarlord(WorldServer w, long day) {
        List<EntityPlayer> alive = new ArrayList<>();
        for (EntityPlayer p : w.playerEntities) {
            if (p.isEntityAlive() && !p.isSpectator()) {
                alive.add(p);
            }
        }
        if (alive.isEmpty()) {
            return;
        }
        EntityPlayer p = alive.get(w.rand.nextInt(alive.size()));
        int tier = warlordTier(w, day);
        for (int tries = 0; tries < 10; tries++) {
            double a = w.rand.nextDouble() * Math.PI * 2, d = 20 + w.rand.nextDouble() * 10;
            int x = MathHelper.floor(p.posX + Math.cos(a) * d), z = MathHelper.floor(p.posZ + Math.sin(a) * d);
            if (!w.isBlockLoaded(new BlockPos(x, 64, z))) {
                continue;
            }
            BlockPos pos = w.getTopSolidOrLiquidBlock(new BlockPos(x, 0, z));
            if (!open(w, pos, 3, true, true)) {
                continue;
            }
            EntityZombieWarlord wl = new EntityZombieWarlord(w);
            wl.setLocationAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, w.rand.nextFloat() * 360, 0);
            wl.setup(tier);
            wl.setAttackTarget(p);
            w.spawnEntity(wl);
            String name = EntityZombieWarlord.tierName(tier);
            for (EntityPlayer o : w.playerEntities) {
                o.sendMessage(new TextComponentString("\u00a74\u00a7l" + name + " \u00a7chas come for " + p.getName() + "!"));
            }
            Fx.at(w, Fx.PILLAR, Fx.BLOOD, wl.posX, wl.posY, wl.posZ, 2.5);
            Fx.sound(w, wl.posX, wl.posY, wl.posZ, "minecraft:entity.wither.spawn", 1.0F, 0.6F);
            return;
        }
    }

    static int spawnGroup(WorldServer w, EntityPlayer p, int n, boolean night, boolean bm, long week) {
        boolean surface = w.canSeeSky(new BlockPos(p.posX, p.posY + 1, p.posZ)) || p.posY >= w.getHeight((int) p.posX, (int) p.posZ) - 6;
        for (int tries = 0; tries < 6; tries++) {
            double a = w.rand.nextDouble() * Math.PI * 2, d = 24 + w.rand.nextDouble() * 20;
            int x = MathHelper.floor(p.posX + Math.cos(a) * d), z = MathHelper.floor(p.posZ + Math.sin(a) * d);
            if (!w.isBlockLoaded(new BlockPos(x, 64, z))) {
                continue;
            }
            BlockPos base = surface ? w.getTopSolidOrLiquidBlock(new BlockPos(x, 0, z)) : findCaveFloor(w, x, (int) p.posY, z);
            if (base == null || !open(w, base, 2, night, bm)) {
                continue;
            }
            int made = 0;
            boolean special = false;
            for (int i = 0; i < n; i++) {
                ZType t = ZType.pick(w.rand, week);
                if (t == ZType.SCREAMER || t == ZType.NECROMANCER) {
                    if (special) {
                        t = ZType.WALKER;
                    }
                    special = true;
                }
                BlockPos pos = i == 0 ? base : base.add(w.rand.nextInt(5) - 2, 0, w.rand.nextInt(5) - 2);
                if (i > 0 && (!open(w, pos, 2, night, bm))) {
                    continue;
                }
                if (t.height > 2.5F && !w.isAirBlock(pos.up(2))) {
                    t = ZType.WALKER;
                }
                if (spawn(w, t, pos, eliteAffixes(w, week), night, bm, p) != null) {
                    made++;
                }
            }
            return made;
        }
        return 0;
    }

    private static BlockPos findCaveFloor(World w, int x, int py, int z) {
        for (int y = py + 6; y >= Math.max(2, py - 8); y--) {
            BlockPos pos = new BlockPos(x, y, z);
            if (w.isAirBlock(pos) && w.isAirBlock(pos.up()) && !w.isAirBlock(pos.down())) {
                return pos;
            }
        }
        return null;
    }

    private static boolean open(World w, BlockPos pos, int h, boolean night, boolean bm) {
        IBlockState below = w.getBlockState(pos.down());
        if (!below.isSideSolid(w, pos.down(), EnumFacing.UP) || below.getMaterial().isLiquid()) {
            return false;
        }
        for (int i = 0; i < h; i++) {
            if (!w.isAirBlock(pos.up(i))) {
                return false;
            }
        }
        for (EntityPlayer p : w.playerEntities) {
            if (p.getDistanceSq(pos) < 20 * 20) {
                return false;
            }
        }
        if (bm) {
            return true;
        }
        if (w.getLightFromNeighbors(pos) > 7) {
            return false;
        }
        return night || !w.canSeeSky(pos);
    }

    static int eliteAffixes(World w, long week) {
        float chance = Math.min(0.12F, 0.03F + 0.01F * week);
        if (w.rand.nextFloat() >= chance) {
            return 0;
        }
        int a = 1 << w.rand.nextInt(EntityUAZombie.AFFIX_NAMES.length);
        if (week >= 4 && w.rand.nextInt(3) == 0) {
            a |= 1 << w.rand.nextInt(EntityUAZombie.AFFIX_NAMES.length);
        }
        return a;
    }

    public static EntityUAZombie spawn(World w, ZType t, BlockPos pos, int affixes, boolean night, boolean bm, EntityLivingBase target) {
        EntityUAZombie z = new EntityUAZombie(w);
        z.setLocationAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, w.rand.nextFloat() * 360F, 0);
        z.setup(t, affixes, night, bm);
        if (bm && target != null) {
            z.setAttackTarget(target);
        }
        return w.spawnEntity(z) ? z : null;
    }

    // ------------------------------------------------------------------ noise
    /** Zombies in range with no target walk to the noise; with aggro, the ones within half range take the source as target. */
    public static void noise(World w, double x, double y, double z, double radius, EntityLivingBase source, boolean aggro) {
        if (w.isRemote) {
            return;
        }
        BlockPos pos = new BlockPos(x, y, z);
        for (EntityUAZombie zb : w.getEntitiesWithinAABB(EntityUAZombie.class,
                new net.minecraft.util.math.AxisAlignedBB(x - radius, y - 16, z - radius, x + radius, y + 16, z + radius))) {
            if (zb.getAttackTarget() != null) {
                continue;
            }
            if (aggro && source != null && zb.getDistanceSq(x, y, z) < radius * radius * 0.25 && !(source instanceof EntityUAZombie)) {
                zb.setAttackTarget(source);
            } else {
                zb.investigate(pos, 300);
            }
        }
    }

    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate e) {
        Vec3d p = e.getExplosion().getPosition();
        noise(e.getWorld(), p.x, p.y, p.z, 48.0D, null, false);
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase == TickEvent.Phase.END && !e.player.world.isRemote && e.player.ticksExisted % 20 == 0 && e.player.isSprinting()) {
            noise(e.player.world, e.player.posX, e.player.posY, e.player.posZ, 12.0D, e.player, false);
        }
    }

    // ------------------------------------------------------------------ damage rules
    @SubscribeEvent
    public static void onHurt(LivingHurtEvent e) {
        EntityLivingBase t = e.getEntityLiving();
        if (t.world.isRemote) {
            return;
        }
        DamageSource s = e.getSource();
        if (t instanceof EntityPlayer) {
            if (t.getEntityData().getLong(EntityAcidSpit.CORRODE) > t.world.getTotalWorldTime()) {
                e.setAmount(e.getAmount() * 1.2F);
            }
            return;
        }
        if (!(t instanceof EntityUAZombie)) {
            return;
        }
        EntityUAZombie z = (EntityUAZombie) t;
        float amount = e.getAmount();
        if (Pierce.isMagic(s) && !s.isDamageAbsolute()) {
            float mr = z.magicResist();
            if (mr > 0) {
                Entity src = s.getTrueSource();
                long now = t.world.getTotalWorldTime();
                float pierce = src instanceof EntityPlayerMP ? Pierce.spellPierce((EntityPlayerMP) src) : 0.0F;
                pierce = Math.min(0.9F, pierce + 0.1F * Pierce.hex(t, now));
                amount *= 1.0F - mr * (1.0F - pierce);
                if (t.world.rand.nextInt(3) == 0) {
                    Fx.at(t.world, Fx.M_DOME, Fx.ARCANE, t.posX, t.posY + t.height * 0.5, t.posZ, t.width * 1.5);
                }
            }
        }
        Entity direct = s.getImmediateSource();
        if (z.type() == ZType.ARMORED && direct != null && direct != s.getTrueSource() && !Signatures.ignoresShields(s.getTrueSource())
                && (s.isProjectile() || direct instanceof EntityThrowable || direct instanceof EntitySkillBolt)) {
            Vec3d look = z.getLookVec();
            Vec3d to = new Vec3d(direct.posX - z.posX, 0, direct.posZ - z.posZ).normalize();
            if (look.x * to.x + look.z * to.z > 0.35) {
                amount *= 0.4F;
                Fx.sound(t.world, t.posX, t.posY, t.posZ, "minecraft:item.shield.block", 1.0F, 0.9F);
            }
        }
        e.setAmount(amount);
    }

    // ------------------------------------------------------------------ command
    public static class Command extends CommandBase {
        @Override
        public String getName() {
            return "uazombie";
        }

        @Override
        public String getUsage(ICommandSender sender) {
            return "/uazombie spawn <type> [count] [affix...] | /uazombie bloodmoon <start|stop> | /uazombie info";
        }

        @Override
        public int getRequiredPermissionLevel() {
            return 2;
        }

        @Override
        public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
            if (args.length == 0) {
                throw new WrongUsageException(getUsage(sender));
            }
            World w = sender.getEntityWorld();
            switch (args[0].toLowerCase()) {
                case "spawn": {
                    if (args.length < 2) {
                        throw new WrongUsageException(getUsage(sender));
                    }
                    ZType t = ZType.byId(args[1]);
                    if (t == null) {
                        throw new CommandException("Unknown type " + args[1] + ". Types: " + types());
                    }
                    int count = args.length > 2 ? parseInt(args[2], 1, 20) : 1;
                    int affix = 0;
                    for (int i = 3; i < args.length; i++) {
                        for (int k = 0; k < EntityUAZombie.AFFIX_NAMES.length; k++) {
                            if (EntityUAZombie.AFFIX_NAMES[k].equalsIgnoreCase(args[i])) {
                                affix |= 1 << k;
                            }
                        }
                    }
                    Vec3d look = sender.getCommandSenderEntity() != null ? sender.getCommandSenderEntity().getLookVec() : new Vec3d(1, 0, 0);
                    BlockPos at = new BlockPos(sender.getPositionVector().addVector(look.x * 4, 0, look.z * 4));
                    for (int i = 0; i < count; i++) {
                        spawn(w, t, at.add(i % 3 - 1, 0, i / 3), affix, false, false, null);
                    }
                    notifyCommandListener(sender, this, "Spawned " + count + " " + t.display());
                    return;
                }
                case "bloodmoon":
                    if (args.length > 1 && "start".equalsIgnoreCase(args[1])) {
                        w.setWorldTime(w.getWorldTime() - w.getWorldTime() % 24000L + 13000L);
                        forcedBloodMoonDay = w.getWorldTime() / 24000L;
                        notifyCommandListener(sender, this, "Blood Moon started");
                    } else {
                        forcedBloodMoonDay = -1;
                        w.setWorldTime(w.getWorldTime() - w.getWorldTime() % 24000L + 24000L);
                        notifyCommandListener(sender, this, "Blood Moon stopped (skipped to morning)");
                    }
                    return;
                case "info": {
                    List<EntityUAZombie> all = w.getEntities(EntityUAZombie.class, z -> z != null && z.isEntityAlive());
                    int[] by = new int[ZType.values().length];
                    int elites = 0;
                    for (EntityUAZombie z : all) {
                        by[z.type().ordinal()]++;
                        elites += z.isElite() ? 1 : 0;
                    }
                    StringBuilder sb = new StringBuilder("\u00a7eZombies: " + all.size() + "/" + CAP_WORLD + " (elites " + elites + ")\u00a77 ");
                    for (ZType t : ZType.values()) {
                        if (by[t.ordinal()] > 0) {
                            sb.append(t.display()).append(' ').append(by[t.ordinal()]).append("  ");
                        }
                    }
                    sender.sendMessage(new TextComponentString(sb.toString()));
                    sender.sendMessage(new TextComponentString("\u00a77Week " + EntityUAZombie.week(w) + ", director "
                            + w.getGameRules().getBoolean(RULE_ON) + ", Blood Moon " + (isBloodMoon(w) ? "now" : "every "
                            + w.getGameRules().getInt(RULE_BM_EVERY) + " days (" + w.getGameRules().getBoolean(RULE_BM) + ")")));
                    return;
                }
                default:
                    throw new WrongUsageException(getUsage(sender));
            }
        }

        private static String types() {
            List<String> l = new ArrayList<>();
            for (ZType t : ZType.values()) {
                l.add(t.id());
            }
            return String.join(", ", l);
        }

        @Override
        public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, BlockPos pos) {
            if (args.length == 1) {
                return getListOfStringsMatchingLastWord(args, "spawn", "bloodmoon", "info");
            }
            if (args.length == 2 && "spawn".equalsIgnoreCase(args[0])) {
                List<String> l = new ArrayList<>();
                for (ZType t : ZType.values()) {
                    l.add(t.id());
                }
                return getListOfStringsMatchingLastWord(args, l);
            }
            if (args.length == 2 && "bloodmoon".equalsIgnoreCase(args[0])) {
                return getListOfStringsMatchingLastWord(args, "start", "stop");
            }
            if (args.length > 3 && "spawn".equalsIgnoreCase(args[0])) {
                return getListOfStringsMatchingLastWord(args, EntityUAZombie.AFFIX_NAMES);
            }
            return new ArrayList<>();
        }
    }
}
