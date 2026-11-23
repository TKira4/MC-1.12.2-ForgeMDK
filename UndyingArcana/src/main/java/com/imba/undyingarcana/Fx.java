package com.imba.undyingarcana;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.NetworkRegistry;

/** Server-side VFX / SFX emitter. The client draws every effect itself (FxClient) from one small packet. */
public final class Fx {
    // ---- effect kinds
    public static final int SLASH = 0, XSLASH = 1, RING = 2, NOVA = 3, BEAM = 4, EXPLOSION = 5, PILLAR = 6, SPIRAL = 7,
            GATHER = 8, DASH = 9, IMPACT = 10, METEOR = 11, RUNES = 12, FIELD = 13, CRESCENT = 14, LIGHTNING = 15,
            SPIKES = 16, DEBRIS = 17, HEAL = 18, AURA = 19, TIERUP = 20, SPIN = 21, BARRAGE = 22, FLASH = 23;
    // glow-geometry only kinds (FxMesh)
    public static final int M_SWORD = 30, M_SPEAR = 31, M_CRACK = 32, M_VORTEX = 33, M_DOME = 34, M_WALL = 35, M_ORB = 36, M_BOLT = 37;
    // screen effects for nearby players
    public static final int SCREEN_FLASH = 50, VIGNETTE = 51;

    // ---- elements (colour palettes)
    public static final int STEEL = 0, FIRE = 1, ICE = 2, LIGHTNING_E = 3, NECRO = 4, EARTH = 5, SORCERY = 6, HEALING = 7,
            ARCANE = 8, BLOOD = 9, SHADOW = 10, WIND = 11, HOLY = 12, POISON = 13;

    private Fx() {
    }

    /** Two-element spell palette id: COMBO + a * 8 + b (a, b = Magic element indices); particles mix both palettes. */
    public static final int COMBO = 32;

    public static int combo(int a, int b) {
        return COMBO + a * 8 + b;
    }

    public static int primary(int el) {
        return el >= COMBO && el < COMBO + 64 ? Magic.ELEMENT_FX[(el - COMBO) / 8] : el;
    }

    public static int secondary(int el) {
        return el >= COMBO && el < COMBO + 64 ? Magic.ELEMENT_FX[(el - COMBO) % 8] : el;
    }

    /** True when the palette is this element or a combo containing it. */
    public static boolean is(int el, int x) {
        return el == x || primary(el) == x || secondary(el) == x;
    }

    public static int elementOf(String name) {
        switch (name) {
            case "fire": return FIRE;
            case "ice": return ICE;
            case "lightning": return LIGHTNING_E;
            case "necromancy": return NECRO;
            case "earth": return EARTH;
            case "sorcery": return SORCERY;
            case "healing": return HEALING;
            case "magic": return ARCANE;
            case "blood": return BLOOD;
            case "shadow": return SHADOW;
            case "wind": return WIND;
            case "holy": return HOLY;
            case "poison": return POISON;
            default: return STEEL;
        }
    }

    public static void send(World w, int kind, int el, double x, double y, double z, double ax, double ay, double az,
                            double size, double extra, int dur, int entity) {
        if (w.isRemote) {
            return;
        }
        SkillNet.MsgFx m = new SkillNet.MsgFx();
        m.kind = kind;
        m.element = el;
        m.x = (float) x;
        m.y = (float) y;
        m.z = (float) z;
        m.ax = (float) ax;
        m.ay = (float) ay;
        m.az = (float) az;
        m.size = (float) size;
        m.extra = (float) extra;
        m.seed = w.rand.nextInt();
        m.dur = dur;
        m.entity = entity;
        SkillNet.NET.sendToAllAround(m, new NetworkRegistry.TargetPoint(w.provider.getDimension(), x, y, z, 80));
    }

    /** Effect at a point. */
    public static void at(World w, int kind, int el, double x, double y, double z, double size) {
        send(w, kind, el, x, y, z, 0, 0, 0, size, 0, 20, -1);
    }

    /** Effect that follows an entity. */
    public static void on(Entity e, int kind, int el, double size, double extra, int dur) {
        send(e.world, kind, el, e.posX, e.posY, e.posZ, 0, 0, 0, size, extra, dur, e.getEntityId());
    }

    /** Camera shake for players within radius, weaker with distance. */
    public static void shake(World w, double x, double y, double z, double radius, float power, int ticks) {
        if (w.isRemote) {
            return;
        }
        for (EntityPlayer pl : w.playerEntities) {
            double d = pl.getDistance(x, y, z);
            if (d < radius && pl instanceof EntityPlayerMP) {
                float pw = power * (float) (1.0 - d / radius * 0.7);
                SkillNet.NET.sendTo(new SkillNet.MsgShake(pw, ticks), (EntityPlayerMP) pl);
            }
        }
    }

    public static void sound(World w, double x, double y, double z, String id, float vol, float pitch) {
        SoundEvent s = SoundEvent.REGISTRY.getObject(new ResourceLocation(id));
        if (s != null) {
            w.playSound(null, x, y, z, s, SoundCategory.PLAYERS, vol, pitch);
        }
    }

    public static void sound(Entity e, String id, float vol, float pitch) {
        sound(e.world, e.posX, e.posY, e.posZ, id, vol, pitch);
    }
}
