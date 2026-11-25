package com.imba.undyingarcana;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Server side of the quest Journal: loads quest files, counts progress from game events (no scoreboard tricks),
 * claims rewards (overflow goes to a reward pouch), keeps up to 3 tracked quests, syncs to the client only when
 * something changed. Active when the game rule uaJournal is true (default: true unless BetterQuesting is installed).
 */
@Mod.EventBusSubscriber(modid = UndyingArcana.MODID)
public final class Quests {
    private static final Logger LOG = LogManager.getLogger("UndyingArcana-Quests");
    public static final String RULE = "uaJournal";
    private static final java.util.Set<java.util.UUID> DIRTY = new java.util.HashSet<>();
    private static String defsJson = "{\"quests\":[]}";

    private Quests() {
    }

    // ------------------------------------------------------------------ loading
    public static void load() {
        File dir = new File(Loader.instance().getConfigDir(), "undyingarcana/quests");
        try {
            if (!dir.isDirectory() || dir.listFiles((d, n) -> n.endsWith(".json")).length == 0) {
                dir.mkdirs();
                try (InputStream in = Quests.class.getResourceAsStream("/assets/undyingarcana/quests/default.json")) {
                    if (in != null) {
                        Files.copy(in, new File(dir, "default.json").toPath());
                    }
                }
            }
            // mod-side fixes to default quests (loaded last, so they replace the same ids); rewritten every start
            try (InputStream in = Quests.class.getResourceAsStream("/assets/undyingarcana/quests/overrides.json")) {
                if (in != null) {
                    Files.copy(in, new File(dir, "zz_ua_overrides.json").toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            }
            Map<String, QuestDefs.Quest> all = new LinkedHashMap<>();
            File[] files = dir.listFiles((d, n) -> n.endsWith(".json"));
            java.util.Arrays.sort(files);
            for (File f : files) {
                try (Reader r = new InputStreamReader(Files.newInputStream(f.toPath()), StandardCharsets.UTF_8)) {
                    QuestDefs.Pack pack = QuestDefs.GSON.fromJson(r, QuestDefs.Pack.class);
                    for (QuestDefs.Quest q : pack.quests) {
                        all.put(q.id, q);
                    }
                } catch (Exception e) {
                    LOG.error("Bad quest file " + f.getName() + ": " + e.getMessage());
                }
            }
            QuestDefs.ALL = all;
            QuestDefs.Pack out = new QuestDefs.Pack();
            out.quests.addAll(all.values());
            defsJson = new com.google.gson.Gson().toJson(out);
            LOG.info("Loaded " + all.size() + " quests from " + dir);
        } catch (Exception e) {
            LOG.error("Could not load quests", e);
        }
    }

    public static boolean on(World w) {
        GameRules r = w.getGameRules();
        return r.hasRule(RULE) && r.getBoolean(RULE);
    }

    @SubscribeEvent
    public static void onWorldLoad(WorldEvent.Load e) {
        World w = e.getWorld();
        if (!w.isRemote && w.provider.getDimension() == 0 && !w.getGameRules().hasRule(RULE)) {
            w.getGameRules().addGameRule(RULE, Loader.isModLoaded("betterquesting") ? "false" : "true", GameRules.ValueType.BOOLEAN_VALUE);
        }
    }

    // ------------------------------------------------------------------ player data
    private static NBTTagCompound pp(EntityPlayer p) {
        NBTTagCompound root = p.getEntityData();
        if (!root.hasKey("PlayerPersisted")) {
            root.setTag("PlayerPersisted", new NBTTagCompound());
        }
        return root.getCompoundTag("PlayerPersisted");
    }

    private static long today(EntityPlayer p) {
        return p.world.getWorldTime() / 24000L;
    }

    /** Progress entry of a quest; a daily quest claimed on an earlier day starts over. */
    private static NBTTagCompound entry(EntityPlayer p, QuestDefs.Quest q) {
        NBTTagCompound all = pp(p).getCompoundTag("ua_q");
        pp(p).setTag("ua_q", all);
        NBTTagCompound e = all.getCompoundTag(q.id);
        if ("daily".equals(q.repeat) && e.getBoolean("c") && e.getLong("day") < today(p)) {
            e = new NBTTagCompound();
        }
        int[] prog = e.getIntArray("p");
        if (prog.length != q.goals.size()) {
            e.setIntArray("p", java.util.Arrays.copyOf(prog, q.goals.size()));
        }
        all.setTag(q.id, e);
        return e;
    }

    public static boolean claimed(EntityPlayer p, QuestDefs.Quest q) {
        return entry(p, q).getBoolean("c");
    }

    public static boolean complete(EntityPlayer p, QuestDefs.Quest q) {
        int[] prog = entry(p, q).getIntArray("p");
        for (int i = 0; i < q.goals.size(); i++) {
            if (prog[i] < q.goals.get(i).count) {
                return false;
            }
        }
        return true;
    }

    public static boolean unlocked(EntityPlayer p, QuestDefs.Quest q) {
        for (String r : q.requires) {
            QuestDefs.Quest req = QuestDefs.ALL.get(r);
            if (req != null && !claimed(p, req) && !complete(p, req)) {
                return false;
            }
        }
        return true;
    }

    private static boolean active(EntityPlayer p, QuestDefs.Quest q) {
        return unlocked(p, q) && !claimed(p, q);
    }

    private static void bump(EntityPlayer p, QuestDefs.Quest q, int goal, int amount, boolean set) {
        NBTTagCompound e = entry(p, q);
        int[] prog = e.getIntArray("p");
        int cap = q.goals.get(goal).count;
        int v = set ? Math.min(cap, amount) : Math.min(cap, prog[goal] + amount);
        if (v == prog[goal]) {
            return;
        }
        boolean was = complete(p, q);
        prog[goal] = v;
        e.setIntArray("p", prog);
        DIRTY.add(p.getUniqueID());
        if (!was && complete(p, q)) {
            p.sendMessage(new TextComponentString("\u00a7a\u2714 Quest complete: \u00a7f" + q.title + "\u00a77 - claim it in the Journal (U)."));
            Fx.sound(p.world, p.posX, p.posY, p.posZ, "minecraft:entity.player.levelup", 0.7F, 1.6F);
        }
    }

    // ------------------------------------------------------------------ progress from events
    @SubscribeEvent
    public static void onKill(LivingDeathEvent e) {
        EntityLivingBase dead = e.getEntityLiving();
        Entity src = e.getSource().getTrueSource();
        if (dead.world.isRemote || !(src instanceof EntityPlayerMP) || dead instanceof EntityPlayer || !on(dead.world)) {
            return;
        }
        EntityPlayerMP p = (EntityPlayerMP) src;
        String with = weaponKind(p);
        for (QuestDefs.Quest q : QuestDefs.ALL.values()) {
            for (int i = 0; i < q.goals.size(); i++) {
                QuestDefs.Goal g = q.goals.get(i);
                if ("kill".equals(g.type) && (g.with.isEmpty() || g.with.equals(with)) && matches(g.target, dead) && active(p, q)) {
                    bump(p, q, i, 1, false);
                }
            }
        }
    }

    private static String weaponKind(EntityPlayer p) {
        ItemStack held = p.getHeldItemMainhand();
        if (held.isEmpty()) {
            return "unarmed";
        }
        SkillCatalog.Info in = SkillCatalog.identify(held);
        if (in == null) {
            return "melee";
        }
        return in.isGun() ? "gun" : in.isRanged() ? "bow" : in.isWand() ? "wand" : "melee";
    }

    private static boolean matches(String target, EntityLivingBase e) {
        if (e instanceof EntityUAZombie) {
            EntityUAZombie z = (EntityUAZombie) e;
            if (z.owner != null) {
                return false;
            }
            if ("zombie".equals(target)) {
                return true;
            }
            if ("elite".equals(target)) {
                return z.isElite();
            }
            if (target.startsWith("zombie:")) {
                return target.substring(7).equals(z.type().id());
            }
        }
        ResourceLocation key = EntityList.getKey(e);
        // "miniboss": a boss, a Zombie Warlord, or any enemy tagged ua_miniboss (faction captains)
        if ("miniboss".equals(target) && (e instanceof EntityLSBoss || e.getTags().contains("ua_warlord") || e.getTags().contains("ua_miniboss"))) {
            return true;
        }
        if (e instanceof EntityLSBoss) {
            if ("boss".equals(target)) {
                return true;
            }
            if (target.startsWith("boss:")) {
                return key != null && target.substring(5).equals(key.getResourcePath());
            }
        }
        return key != null && QuestDefs.glob(target, key.toString());
    }

    @SubscribeEvent
    public static void onCraft(PlayerEvent.ItemCraftedEvent e) {
        if (e.player.world.isRemote || e.crafting.isEmpty() || !on(e.player.world)) {
            return;
        }
        String id = String.valueOf(e.crafting.getItem().getRegistryName());
        for (QuestDefs.Quest q : QuestDefs.ALL.values()) {
            for (int i = 0; i < q.goals.size(); i++) {
                QuestDefs.Goal g = q.goals.get(i);
                if ("craft".equals(g.type) && QuestDefs.glob(g.target, id) && active(e.player, q)) {
                    bump(e.player, q, i, e.crafting.getCount(), false);
                }
            }
        }
    }

    public static void bloodMoonSurvived(EntityPlayer p) {
        if (!on(p.world)) {
            return;
        }
        for (QuestDefs.Quest q : QuestDefs.ALL.values()) {
            for (int i = 0; i < q.goals.size(); i++) {
                if ("bloodmoon".equals(q.goals.get(i).type) && active(p, q)) {
                    bump(p, q, i, 1, false);
                }
            }
        }
    }

    /** Every 2 s: state goals (items held, level, build, biome), then sync if anything changed. */
    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent e) {
        EntityPlayer p = e.player;
        if (e.phase != TickEvent.Phase.END || p.world.isRemote || p.ticksExisted % 40 != 7 || !(p instanceof EntityPlayerMP) || !on(p.world)) {
            return;
        }
        String biome = String.valueOf(p.world.getBiome(p.getPosition()).getRegistryName()).toLowerCase();
        for (QuestDefs.Quest q : QuestDefs.ALL.values()) {
            boolean checked = false;
            for (int i = 0; i < q.goals.size(); i++) {
                QuestDefs.Goal g = q.goals.get(i);
                int v = -1;
                switch (g.type) {
                    case "item": v = countItems(p, g.target); break;
                    case "level": v = Stats.level(p); break;
                    case "build": {
                        int b = Stats.build(p);
                        boolean ok = "any".equals(g.target) || g.target.isEmpty()
                                ? b != Builds.ADVENTURER && b != Builds.UNBOUND : Builds.NAME[b].equalsIgnoreCase(g.target);
                        v = ok ? 1 : 0;
                        break;
                    }
                    case "biome": v = biome.contains(g.target.toLowerCase()) ? 1 : -1; break;
                    default: break;
                }
                if (v < 0) {
                    continue;
                }
                if (!checked) {
                    if (!active(p, q)) {
                        break;
                    }
                    checked = true;
                }
                if ("level".equals(g.type) && v < g.count) {
                    v = 0; // a level goal is done or not, no partial bar
                }
                bump(p, q, i, v, true);
            }
        }
        if (DIRTY.remove(p.getUniqueID())) {
            syncProgress((EntityPlayerMP) p);
        }
    }

    private static int countItems(EntityPlayer p, String pattern) {
        int n = 0;
        for (int i = 0; i < p.inventory.getSizeInventory(); i++) {
            ItemStack s = p.inventory.getStackInSlot(i);
            if (!s.isEmpty() && QuestDefs.glob(pattern, String.valueOf(s.getItem().getRegistryName()))) {
                n += s.getCount();
            }
        }
        return n;
    }

    // ------------------------------------------------------------------ actions
    /** op 0 claim, 1 track, 2 untrack, 3 take items from the reward pouch. */
    public static void act(EntityPlayerMP p, int op, String id) {
        if (!on(p.world)) {
            return;
        }
        QuestDefs.Quest q = QuestDefs.ALL.get(id);
        NBTTagCompound pp = pp(p);
        List<String> tracked = tracked(p);
        switch (op) {
            case 0:
                if (q == null || !unlocked(p, q) || claimed(p, q) || !complete(p, q)) {
                    return;
                }
                claim(p, q);
                tracked.remove(q.id);
                for (QuestDefs.Quest next : QuestDefs.ALL.values()) {
                    if (tracked.size() < 3 && next.requires.contains(q.id) && active(p, next) && !tracked.contains(next.id)) {
                        tracked.add(next.id);
                    }
                }
                break;
            case 1:
                if (q != null && !tracked.contains(id)) {
                    if (tracked.size() >= 3) {
                        tracked.remove(0);
                    }
                    tracked.add(id);
                }
                break;
            case 2:
                tracked.remove(id);
                break;
            case 3: {
                NBTTagList pouch = pp.getTagList("ua_pouch", 10);
                NBTTagList keep = new NBTTagList();
                for (int i = 0; i < pouch.tagCount(); i++) {
                    ItemStack s = new ItemStack(pouch.getCompoundTagAt(i));
                    if (!p.inventory.addItemStackToInventory(s) && !s.isEmpty()) {
                        keep.appendTag(s.writeToNBT(new NBTTagCompound()));
                    }
                }
                pp.setTag("ua_pouch", keep);
                break;
            }
            default:
                return;
        }
        setTracked(p, tracked);
        syncProgress(p);
    }

    private static void claim(EntityPlayerMP p, QuestDefs.Quest q) {
        NBTTagCompound e = entry(p, q);
        e.setBoolean("c", true);
        e.setLong("day", today(p));
        if (q.xp > 0) {
            if (Stats.on(p.world)) {
                Stats.addXp(p, q.xp);
            } else {
                p.addExperience(q.xp);
            }
        }
        NBTTagList pouch = pp(p).getTagList("ua_pouch", 10);
        for (String spec : q.items) {
            ItemStack s = parseStack(spec);
            if (s.isEmpty()) {
                continue;
            }
            if (s.getItem() == ModItems.SOUL_COIN && Stats.on(p.world)) {
                Coins.add(p, s.getCount()); // coins are a balance now
                continue;
            }
            if (!p.inventory.addItemStackToInventory(s) && !s.isEmpty()) {
                pouch.appendTag(s.writeToNBT(new NBTTagCompound()));
            }
        }
        pp(p).setTag("ua_pouch", pouch);
        p.sendMessage(new TextComponentString("\u00a76Claimed: \u00a7f" + q.title + (q.xp > 0 ? "\u00a77 (+" + q.xp + " XP)" : "")
                + (pouch.tagCount() > 0 ? "\u00a7e - some rewards went to your reward pouch" : "")));
        Fx.sound(p.world, p.posX, p.posY, p.posZ, "minecraft:entity.player.levelup", 0.8F, 1.2F);
    }

    /** "modid:item*count" or "modid:item@meta*count". */
    static ItemStack parseStack(String spec) {
        String s = spec.trim();
        int count = 1, meta = 0;
        int star = s.lastIndexOf('*');
        if (star > 0) {
            count = Integer.parseInt(s.substring(star + 1));
            s = s.substring(0, star);
        }
        int at = s.indexOf('@');
        if (at > 0) {
            meta = Integer.parseInt(s.substring(at + 1));
            s = s.substring(0, at);
        }
        Item item = Item.getByNameOrId(s);
        return item == null ? ItemStack.EMPTY : new ItemStack(item, count, meta);
    }

    private static List<String> tracked(EntityPlayer p) {
        List<String> l = new ArrayList<>();
        NBTTagList t = pp(p).getTagList("ua_qt", 8);
        for (int i = 0; i < t.tagCount(); i++) {
            l.add(t.getStringTagAt(i));
        }
        return l;
    }

    private static void setTracked(EntityPlayer p, List<String> l) {
        NBTTagList t = new NBTTagList();
        for (String s : l) {
            t.appendTag(new NBTTagString(s));
        }
        pp(p).setTag("ua_qt", t);
    }

    // ------------------------------------------------------------------ sync
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.player instanceof EntityPlayerMP)) {
            return;
        }
        EntityPlayerMP p = (EntityPlayerMP) e.player;
        boolean on = on(p.world);
        int chunk = 10000, total = Math.max(1, (defsJson.length() + chunk - 1) / chunk);
        for (int i = 0; i < total; i++) {
            String part = on ? defsJson.substring(i * chunk, Math.min(defsJson.length(), (i + 1) * chunk)) : "";
            SkillNet.NET.sendTo(new SkillNet.MsgQuestDefs(on, i, on ? total : 1, part), p);
            if (!on) {
                break;
            }
        }
        if (on) {
            if (tracked(p).isEmpty()) {
                List<String> t = new ArrayList<>();
                for (QuestDefs.Quest q : QuestDefs.ALL.values()) {
                    if (t.size() < 1 && "story".equals(q.tab) && active(p, q)) {
                        t.add(q.id);
                    }
                }
                setTracked(p, t);
            }
            syncProgress(p);
        }
    }

    static void syncProgress(EntityPlayerMP p) {
        JsonObject root = new JsonObject();
        JsonObject qs = new JsonObject();
        for (QuestDefs.Quest q : QuestDefs.ALL.values()) {
            NBTTagCompound e = entry(p, q);
            JsonObject o = new JsonObject();
            JsonArray a = new JsonArray();
            for (int v : e.getIntArray("p")) {
                a.add(new com.google.gson.JsonPrimitive(v));
            }
            o.add("p", a);
            o.addProperty("c", e.getBoolean("c"));
            qs.add(q.id, o);
        }
        root.add("q", qs);
        JsonArray t = new JsonArray();
        for (String s : tracked(p)) {
            t.add(new com.google.gson.JsonPrimitive(s));
        }
        root.add("t", t);
        root.addProperty("pouch", pp(p).getTagList("ua_pouch", 10).tagCount());
        SkillNet.NET.sendTo(new SkillNet.MsgQuestProg(root.toString()), p);
    }

    // ------------------------------------------------------------------ command
    public static class Command extends CommandBase {
        @Override
        public String getName() {
            return "uaquest";
        }

        @Override
        public String getUsage(ICommandSender sender) {
            return "/uaquest reload | /uaquest complete <id> [player] | /uaquest reset [player]";
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
            switch (args[0].toLowerCase()) {
                case "reload":
                    load();
                    for (EntityPlayerMP p : server.getPlayerList().getPlayers()) {
                        onLogin(new PlayerEvent.PlayerLoggedInEvent(p));
                    }
                    notifyCommandListener(sender, this, "Reloaded " + QuestDefs.ALL.size() + " quests");
                    return;
                case "complete": {
                    if (args.length < 2) {
                        throw new WrongUsageException(getUsage(sender));
                    }
                    EntityPlayerMP p = args.length > 2 ? getPlayer(server, sender, args[2]) : getCommandSenderAsPlayer(sender);
                    QuestDefs.Quest q = QuestDefs.ALL.get(args[1]);
                    if (q == null) {
                        throw new CommandException("No quest " + args[1]);
                    }
                    for (int i = 0; i < q.goals.size(); i++) {
                        bump(p, q, i, q.goals.get(i).count, true);
                    }
                    syncProgress(p);
                    return;
                }
                case "reset": {
                    EntityPlayerMP p = args.length > 1 ? getPlayer(server, sender, args[1]) : getCommandSenderAsPlayer(sender);
                    pp(p).removeTag("ua_q");
                    pp(p).removeTag("ua_qt");
                    syncProgress(p);
                    notifyCommandListener(sender, this, "Quests of " + p.getName() + " reset");
                    return;
                }
                default:
                    throw new WrongUsageException(getUsage(sender));
            }
        }

        @Override
        public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, net.minecraft.util.math.BlockPos pos) {
            if (args.length == 1) {
                return getListOfStringsMatchingLastWord(args, "reload", "complete", "reset");
            }
            if (args.length == 2 && "complete".equalsIgnoreCase(args[0])) {
                return getListOfStringsMatchingLastWord(args, QuestDefs.ALL.keySet());
            }
            return new ArrayList<>();
        }
    }

}
