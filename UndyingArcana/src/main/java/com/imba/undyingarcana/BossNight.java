package com.imba.undyingarcana;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Boss Night: every 10th in-game day (not on a Blood Moon) a random Overworld boss (Frost Lich, Plague Mother, Bone
 * Colossus or Thunder Warden) rises 40 blocks from a random player at dusk, announced at noon and when it rises.
 * Game rule uaBossNight (default true). New-world systems only.
 */
@Mod.EventBusSubscriber(modid = UndyingArcana.MODID)
public final class BossNight {
    public static final String RULE = "uaBossNight";
    public static final int EVERY_DAYS = 10;
    private static final String[] BOSSES = {"frost_lich", "plague_mother", "bone_colossus", "thunder_warden"};
    private static long warnedDay = -1, risenDay = -1;

    private BossNight() {
    }

    private static boolean on(World w) {
        GameRules r = w.getGameRules();
        if (!r.hasRule(RULE)) {
            r.addGameRule(RULE, "true", GameRules.ValueType.BOOLEAN_VALUE);
        }
        return r.getBoolean(RULE) && Stats.on(w);
    }

    @SubscribeEvent
    public static void onWorldTick(TickEvent.WorldTickEvent e) {
        World w = e.world;
        if (e.phase != TickEvent.Phase.END || w.isRemote || w.provider.getDimension() != 0 || w.getTotalWorldTime() % 20 != 0) {
            return;
        }
        long time = w.getWorldTime(), day = time / 24000, tod = time % 24000;
        if (day == 0 || day % EVERY_DAYS != 0 || !on(w) || ZombieDirector.isBloodMoon(w)) {
            return;
        }
        if (tod >= 6000 && tod < 6100 && warnedDay != day) {
            warnedDay = day;
            for (EntityPlayer p : w.playerEntities) {
                p.sendMessage(new TextComponentString(TextFormatting.DARK_PURPLE + "The ground trembles... something old will rise at dusk. (Boss Night)"));
            }
        }
        if (tod >= 12600 && tod < 12700 && risenDay != day) {
            risenDay = day;
            List<EntityPlayer> ps = new ArrayList<>();
            for (EntityPlayer p : w.playerEntities) {
                if (!p.isCreative() && !p.isSpectator() && p.isEntityAlive()) {
                    ps.add(p);
                }
            }
            if (ps.isEmpty()) {
                return;
            }
            EntityPlayer p = ps.get(w.rand.nextInt(ps.size()));
            String id = BOSSES[w.rand.nextInt(BOSSES.length)];
            double a = w.rand.nextDouble() * Math.PI * 2;
            BlockPos at = w.getTopSolidOrLiquidBlock(new BlockPos(p.posX + Math.cos(a) * 40, 0, p.posZ + Math.sin(a) * 40));
            EntityLSBoss b = BossSigil.create(w, id);
            b.setLocationAndAngles(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, w.rand.nextFloat() * 360F, 0F);
            b.onInitialSpawn(w.getDifficultyForLocation(at), null);
            w.spawnEntity(b);
            w.addWeatherEffect(new net.minecraft.entity.effect.EntityLightningBolt(w, b.posX, b.posY, b.posZ, true));
            w.playSound(null, b.posX, b.posY, b.posZ, SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.HOSTILE, 1.5F, 0.8F);
            int dist = (int) Math.round(Math.sqrt(p.getDistanceSq(b)));
            for (EntityPlayer o : w.playerEntities) {
                o.sendMessage(new TextComponentString(TextFormatting.DARK_RED + "Boss Night: the " + b.getName() + " has risen near "
                        + p.getName() + " (" + dist + " blocks, at " + at.getX() + ", " + at.getZ() + ")!"));
            }
        }
    }
}
