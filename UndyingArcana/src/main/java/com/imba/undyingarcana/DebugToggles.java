package com.imba.undyingarcana;

import java.util.Arrays;
import java.util.List;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Client-only switches to find rendering problems: /uadebug <fx|mesh|shake|hud|pair|sound|all|off>.
 * Each word toggles one part of the mod's client rendering off or back on; nothing is saved.
 */
@SideOnly(Side.CLIENT)
public final class DebugToggles {
    public static volatile boolean noFx, noMesh, noShake, noHud, noPair, noSound;
    /** Finer switches inside fx: GLOW particles, particles near the camera, vanilla particles, mesh creation, timed jobs. */
    public static volatile boolean noGlow, noNear, noVanilla, noMeshFx, noJobs;
    private static long lastReport;

    /** True when this particle should be skipped by the finer switches. */
    public static boolean skipParticle(boolean glowSheet, double x, double y, double z) {
        if (noGlow && glowSheet) {
            return true;
        }
        if (noNear) {
            net.minecraft.entity.Entity v = net.minecraft.client.Minecraft.getMinecraft().getRenderViewEntity();
            return v != null && v.getDistanceSq(x, y - v.getEyeHeight(), z) < 3.0 * 3.0;
        }
        return false;
    }

    /** Chat + log message for a suspicious value (at most once a second). */
    public static void report(String msg) {
        org.apache.logging.log4j.LogManager.getLogger("UndyingArcana-Debug").warn(msg);
        long now = System.currentTimeMillis();
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
        if (now - lastReport > 1000 && mc.player != null) {
            lastReport = now;
            mc.player.sendMessage(new TextComponentString("\u00a7c[UA debug] " + msg));
        }
    }

    private DebugToggles() {
    }

    public static void init() {
        ClientCommandHandler.instance.registerCommand(new Cmd());
        MinecraftForge.EVENT_BUS.register(new Events());
    }

    private static String state() {
        return "\u00a7efx " + off(noFx) + " mesh " + off(noMesh) + " shake " + off(noShake) + " hud " + off(noHud)
                + " pair " + off(noPair) + " sound " + off(noSound) + " | glow " + off(noGlow) + " near " + off(noNear)
                + " vanilla " + off(noVanilla) + " meshfx " + off(noMeshFx) + " jobs " + off(noJobs);
    }

    private static String off(boolean b) {
        return b ? "\u00a7cOFF\u00a7e" : "\u00a7aon\u00a7e";
    }

    public static final class Events {
        @SubscribeEvent
        public void onSound(PlaySoundEvent e) {
            if (noSound) {
                e.setResultSound(null);
            }
        }
    }

    private static final class Cmd extends CommandBase {
        @Override
        public String getName() {
            return "uadebug";
        }

        @Override
        public String getUsage(ICommandSender sender) {
            return "/uadebug <fx|mesh|shake|hud|pair|sound|glow|near|vanilla|meshfx|jobs|all|off>";
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
        public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, net.minecraft.util.math.BlockPos pos) {
            return args.length == 1 ? getListOfStringsMatchingLastWord(args, Arrays.asList("fx", "mesh", "shake", "hud", "pair", "sound",
                    "glow", "near", "vanilla", "meshfx", "jobs", "all", "off"))
                    : java.util.Collections.<String>emptyList();
        }

        @Override
        public void execute(MinecraftServer server, ICommandSender sender, String[] args) {
            String a = args.length > 0 ? args[0] : "";
            switch (a) {
                case "fx": noFx = !noFx; break;
                case "mesh": noMesh = !noMesh; break;
                case "shake": noShake = !noShake; break;
                case "hud": noHud = !noHud; break;
                case "pair": noPair = !noPair; break;
                case "sound": noSound = !noSound; break;
                case "glow": noGlow = !noGlow; break;
                case "near": noNear = !noNear; break;
                case "vanilla": noVanilla = !noVanilla; break;
                case "meshfx": noMeshFx = !noMeshFx; break;
                case "jobs": noJobs = !noJobs; break;
                case "all": noFx = noMesh = noShake = noHud = noPair = noSound = true; break;
                case "off": noFx = noMesh = noShake = noHud = noPair = noSound = false;
                    noGlow = noNear = noVanilla = noMeshFx = noJobs = false;
                    break;
                default: sender.sendMessage(new TextComponentString(getUsage(sender))); break;
            }
            sender.sendMessage(new TextComponentString(state()));
        }
    }
}
