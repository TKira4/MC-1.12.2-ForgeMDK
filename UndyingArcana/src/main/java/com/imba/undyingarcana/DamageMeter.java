package com.imba.undyingarcana;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.DamageSource;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Damage meter for balancing: "/dps" starts a session, "/dps" again (or 120 s) prints the damage dealt by source -
 * each weapon skill by name, melee, arrows / bullets, spells - to the player and to the server log ("[DPS]" lines).
 * Final damage after armor and all bonuses (LivingDamageEvent, lowest priority).
 */
@Mod.EventBusSubscriber(modid = UndyingArcana.MODID)
public final class DamageMeter {
    private static final Logger LOG = LogManager.getLogger("undyingarcana-dps");
    private static final int MAX_TICKS = 2400;

    /** Name of the weapon skill whose damage is being applied right now (set by SkillShapes.damage). */
    static String current;

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private static final class Session {
        final long start;
        final Map<String, float[]> parts = new LinkedHashMap<>();

        Session(long start) {
            this.start = start;
        }
    }

    private DamageMeter() {
    }

    private static String category(DamageSource s) {
        if (current != null) {
            return "Skill: " + current;
        }
        String type = s.getDamageType();
        if (type.contains("wizardry") || s.isMagicDamage()) {
            return "Spells";
        }
        if (s.isProjectile()) {
            return "Arrows / bullets";
        }
        if ("player".equals(type)) {
            return "Melee";
        }
        return "Other (" + type + ")";
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamage(LivingDamageEvent e) {
        if (SESSIONS.isEmpty() || e.getEntityLiving().world.isRemote) {
            return;
        }
        Entity src = e.getSource().getTrueSource();
        if (!(src instanceof EntityPlayerMP) || e.getEntityLiving() == src) {
            return;
        }
        Session s = SESSIONS.get(src.getUniqueID());
        if (s == null) {
            return;
        }
        float amount = Math.min(e.getAmount(), e.getEntityLiving().getHealth() + e.getEntityLiving().getAbsorptionAmount());
        float[] v = s.parts.computeIfAbsent(category(e.getSource()), k -> new float[2]);
        v[0] += Math.max(0, amount);
        v[1] += 1;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.player.world.isRemote || SESSIONS.isEmpty()) {
            return;
        }
        Session s = SESSIONS.get(e.player.getUniqueID());
        if (s != null && e.player.world.getTotalWorldTime() - s.start >= MAX_TICKS) {
            report((EntityPlayerMP) e.player);
        }
    }

    private static void report(EntityPlayerMP p) {
        Session s = SESSIONS.remove(p.getUniqueID());
        if (s == null) {
            return;
        }
        float secs = Math.max(1, (p.world.getTotalWorldTime() - s.start) / 20F);
        float total = 0;
        for (float[] v : s.parts.values()) {
            total += v[0];
        }
        List<Map.Entry<String, float[]>> rows = new ArrayList<>(s.parts.entrySet());
        rows.sort((a, b) -> Float.compare(b.getValue()[0], a.getValue()[0]));
        String cls = Stats.className(p);
        String head = String.format("[DPS] %s (%s) %.0fs: total %.0f, %.1f/s", p.getName(), cls, secs, total, total / secs);
        p.sendMessage(new TextComponentString("\u00a76" + head));
        LOG.info(head);
        for (Map.Entry<String, float[]> r : rows) {
            float d = r.getValue()[0];
            String line = String.format("  %s: %.0f (%.1f/s, %d hits, %.0f%%)", r.getKey(), d, d / secs, (int) r.getValue()[1], total > 0 ? d * 100 / total : 0);
            p.sendMessage(new TextComponentString("\u00a77" + line));
            LOG.info("[DPS] " + p.getName() + line);
        }
    }

    /** "/dps inspect": what makes the mob under the crosshair tanky (armor, toughness, effects, mod tags, our statuses). */
    private static void inspect(EntityPlayerMP p) {
        net.minecraft.util.math.Vec3d eye = p.getPositionEyes(1.0F), look = p.getLookVec();
        net.minecraft.util.math.Vec3d end = eye.add(look.scale(32));
        net.minecraft.entity.EntityLivingBase best = null;
        double bd = 33;
        for (Entity e : p.world.getEntitiesWithinAABBExcludingEntity(p, p.getEntityBoundingBox().expand(look.x * 32, look.y * 32, look.z * 32).grow(1))) {
            if (!(e instanceof net.minecraft.entity.EntityLivingBase)) {
                continue;
            }
            net.minecraft.util.math.RayTraceResult r = e.getEntityBoundingBox().grow(0.3).calculateIntercept(eye, end);
            if (r != null && eye.distanceTo(r.hitVec) < bd) {
                bd = eye.distanceTo(r.hitVec);
                best = (net.minecraft.entity.EntityLivingBase) e;
            }
        }
        if (best == null) {
            p.sendMessage(new TextComponentString("\u00a77[Inspect] Look at a mob within 32 blocks."));
            return;
        }
        long now = p.world.getTotalWorldTime();
        net.minecraft.entity.EntityLivingBase t = best;
        StringBuilder fx = new StringBuilder();
        for (net.minecraft.potion.PotionEffect pe : t.getActivePotionEffects()) {
            fx.append(String.valueOf(pe.getPotion().getRegistryName()))
              .append(' ').append(pe.getAmplifier() + 1).append(", ");
        }
        net.minecraft.nbt.NBTTagCompound nbt = new net.minecraft.nbt.NBTTagCompound();
        t.writeToNBT(nbt);
        StringBuilder mods = new StringBuilder();
        for (String k : nbt.getKeySet()) {
            String lk = k.toLowerCase();
            if (lk.contains("champion") || lk.contains("infernal") || lk.contains("rough") || lk.contains("forgecaps")) {
                mods.append(k).append(' ');
            }
        }
        String[] lines = {
            String.format("\u00a76[Inspect] %s \u00a77(%s)", t.getName(), net.minecraft.entity.EntityList.getKey(t)),
            String.format("  Health %.0f / %.0f, Armor %d, Toughness %.1f", t.getHealth(), t.getMaxHealth(), t.getTotalArmorValue(),
                t.getEntityAttribute(net.minecraft.entity.SharedMonsterAttributes.ARMOR_TOUGHNESS).getAttributeValue()),
            "  Effects: " + (fx.length() == 0 ? "none" : fx.toString()),
            "  Mod data: " + (mods.length() == 0 ? "none" : mods.toString()) + " | Tags: " + t.getTags(),
            String.format("  Sunder %d, Hex %d | your Armor Pierce %.0f%%, Spell Pierce %.0f%%", Pierce.sunder(t, now), Pierce.hex(t, now),
                Pierce.armorPierce(p) * 100, Pierce.spellPierce(p) * 100)
        };
        if (t instanceof EntityUAZombie) {
            EntityUAZombie z = (EntityUAZombie) t;
            lines = java.util.Arrays.copyOf(lines, lines.length + 1);
            lines[lines.length - 1] = String.format("  Zombie %s, week %d, magic resist %.0f%%%s", z.type().display(),
                EntityUAZombie.week(p.world), z.magicResist() * 100, z.isElite() ? ", elite" : "");
        }
        for (String l : lines) {
            p.sendMessage(new TextComponentString(l));
            LOG.info(l.replaceAll("\u00a7.", ""));
        }
    }

    /** "/dps": start or stop (and print) a damage meter session. Usable by everyone. */
    public static final class Command extends CommandBase {
        @Override
        public String getName() {
            return "dps";
        }

        @Override
        public String getUsage(ICommandSender sender) {
            return "/dps - start, then /dps again to stop and show your damage per second; /dps inspect - what the mob you look at resists";
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
        public void execute(MinecraftServer server, ICommandSender sender, String[] args) {
            if (!(sender instanceof EntityPlayerMP)) {
                return;
            }
            EntityPlayerMP p = (EntityPlayerMP) sender;
            if (args.length > 0 && "inspect".equalsIgnoreCase(args[0])) {
                inspect(p);
                return;
            }
            if (SESSIONS.containsKey(p.getUniqueID())) {
                report(p);
                return;
            }
            SESSIONS.put(p.getUniqueID(), new Session(p.world.getTotalWorldTime()));
            p.sendMessage(new TextComponentString("\u00a76[DPS] \u00a77Meter started. Fight for a minute, then type /dps again (stops by itself after 2 min)."));
        }
    }
}
