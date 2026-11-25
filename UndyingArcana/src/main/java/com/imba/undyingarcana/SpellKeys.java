package com.imba.undyingarcana;

import org.lwjgl.input.Keyboard;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Grimoire spells: hold the spell key (V by default, Controls > Undying Arcana) and press hotbar key 1-6 to cast spell
 * 1-6. While the spell key is held those hotbar presses are swallowed, so the selected slot does not change; release it
 * and 1-9 switch slots as usual. Works whenever a Grimoire is anywhere in the inventory.
 */
@SideOnly(Side.CLIENT)
public class SpellKeys {
    public static KeyBinding KEY;
    /** Client tick of the last cast press (the HUD shows the spell bar for a moment after it). */
    public static long lastCast = -1000;

    public static void register() {
        KEY = new KeyBinding("key.undyingarcana.spellcast", KeyConflictContext.IN_GAME, KeyModifier.NONE, Keyboard.KEY_V,
                "key.categories.undyingarcana");
        ClientRegistry.registerKeyBinding(KEY);
    }

    /** Spell key physically held right now (no screen open). */
    public static boolean held() {
        Minecraft mc = Minecraft.getMinecraft();
        if (KEY == null || mc.player == null || mc.currentScreen != null) {
            return false;
        }
        int code = KEY.getKeyCode();
        if (code < 0) {
            int b = code + 100;
            return b >= 0 && b < org.lwjgl.input.Mouse.getButtonCount() && org.lwjgl.input.Mouse.isButtonDown(b);
        }
        return code > 0 && code < Keyboard.KEYBOARD_SIZE && Keyboard.isKeyDown(code);
    }

    /** HUD label of spell slot i: the hotbar key ("1".."6"). */
    public static String label(int i) {
        KeyBinding k = Minecraft.getMinecraft().gameSettings.keyBindsHotbar[i];
        String key = k.getKeyCode() > 0 ? Keyboard.getKeyName(k.getKeyCode()) : "?";
        return key == null ? "?" : key.length() > 3 ? key.substring(0, 3) : key;
    }

    /** Short name of the spell key for hints ("V"). */
    public static String keyName() {
        String n = KEY == null || KEY.getKeyCode() <= 0 ? null : Keyboard.getKeyName(KEY.getKeyCode());
        return n == null ? "?" : n;
    }

    /** Slot being charged (hotbar key held with the spell key), or -1; start tick. */
    public static int chargeSlot = -1;
    public static long chargeStart;

    private static int ringSound;

    /** Ticks per magic ring for this player (Spellweaver Gloves make it shorter). */
    static int ringTicks() {
        SkillNet.MsgPower d = PowerHud.data;
        return d == null || d.ringTicks <= 0 ? Magic.RING_TICKS : d.ringTicks;
    }

    /** Most rings the charging spell can build. */
    public static int maxRings() {
        return chargeSlot < 0 ? 0 : Math.max(0, Math.min(6, PowerHud.spells()[chargeSlot].rings));
    }

    /** Full rings built so far by the charging spell. */
    public static int rings() {
        Minecraft mc = Minecraft.getMinecraft();
        if (chargeSlot < 0 || mc.player == null) {
            return 0;
        }
        return (int) Math.min(maxRings(), (mc.player.ticksExisted - chargeStart) / ringTicks());
    }

    /** Progress 0..1 of the ring being built (1 when all rings are done). */
    public static float ringProgress() {
        Minecraft mc = Minecraft.getMinecraft();
        if (chargeSlot < 0 || mc.player == null || rings() >= maxRings()) {
            return 1F;
        }
        return (mc.player.ticksExisted - chargeStart) % ringTicks() / (float) ringTicks();
    }

    /** Charge 0..1 of the held spell (rings built / max rings). */
    public static float charge() {
        int m = maxRings();
        return m <= 0 ? 0 : Math.min(1F, (rings() + (rings() < m ? ringProgress() : 0)) / m);
    }

    // Fired inside the keyboard loop, after the hotbar binding counted the press and before vanilla reads it.
    // Press a hotbar key while the spell key is held: start charging that spell; release it: cast (held longer = stronger).
    @SubscribeEvent
    public void onKey(InputEvent.KeyInputEvent e) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) {
            return;
        }
        KeyBinding[] hb = mc.gameSettings.keyBindsHotbar;
        int key = Keyboard.getEventKey();
        boolean down = Keyboard.getEventKeyState();
        if (!down && chargeSlot >= 0 && key == hb[chargeSlot].getKeyCode()) {
            int ticks = (int) (mc.player.ticksExisted - chargeStart);
            lastCast = mc.player.ticksExisted;
            SkillNet.NET.sendToServer(new SkillNet.MsgAct(4, chargeSlot, String.valueOf(ticks)));
            chargeSlot = -1;
            return;
        }
        if (!held()) {
            return;
        }
        for (int i = 0; i < hb.length; i++) {
            boolean pressed = false;
            while (hb[i].isPressed()) {
                pressed = true;
            }
            if (pressed && i < Magic.SPELLS && chargeSlot < 0) {
                if (PowerHud.spells()[i].rings <= 0) {
                    // instant spell: casts on the press
                    lastCast = mc.player.ticksExisted;
                    SkillNet.NET.sendToServer(new SkillNet.MsgAct(4, i, "0"));
                    continue;
                }
                chargeSlot = i;
                chargeStart = mc.player.ticksExisted;
                ringSound = 0;
            }
        }
        while (KEY.isPressed()) {
            // drain
        }
    }

    /** Magic rings around the caster's feet while charging, and the ground ring where an area spell will land. */
    @SubscribeEvent
    public void onWorld(net.minecraftforge.client.event.RenderWorldLastEvent e) {
        Minecraft mc = Minecraft.getMinecraft();
        if (chargeSlot < 0 || mc.player == null) {
            return;
        }
        Magic.Spell s = PowerHud.spells()[chargeSlot];
        int built = rings();
        if (built > ringSound) {
            ringSound = built;
            mc.player.playSound(net.minecraft.init.SoundEvents.BLOCK_NOTE_CHIME, 0.6F, 0.8F + 0.15F * built);
        }
        drawRings(mc, s, e.getPartialTicks(), built);
        drawTarget(mc, s, e.getPartialTicks(), built);
    }

    private static int rgbOf(Magic.Spell s) {
        int[] el = PowerHud.elements();
        int e1 = s.which >= 0 && s.which < 8 && el[s.which] >= 0 ? el[s.which] : el[0];
        return e1 >= 0 ? Magic.ELEMENT_RGB[e1] : 0xC0A0FF;
    }

    /** Built rings as glowing circles at the feet (each larger, alternating spin); the ring in progress grows as an arc. */
    private static void drawRings(Minecraft mc, Magic.Spell s, float pt, int built) {
        int rgb = rgbOf(s);
        float cr = (rgb >> 16 & 255) / 255F, cg = (rgb >> 8 & 255) / 255F, cb = (rgb & 255) / 255F;
        double time = mc.player.ticksExisted + pt;
        net.minecraft.client.renderer.GlStateManager.pushMatrix();
        net.minecraft.client.renderer.GlStateManager.disableTexture2D();
        net.minecraft.client.renderer.GlStateManager.disableLighting();
        net.minecraft.client.renderer.GlStateManager.enableBlend();
        net.minecraft.client.renderer.GlStateManager.disableCull();
        net.minecraft.client.renderer.Tessellator t = net.minecraft.client.renderer.Tessellator.getInstance();
        net.minecraft.client.renderer.BufferBuilder b = t.getBuffer();
        double y = 0.08; // render origin is the interpolated feet position
        int max = maxRings();
        float prog = ringProgress();
        for (int k = 0; k < max && k <= built; k++) {
            boolean done = k < built;
            double r = 0.9 + 0.45 * k;
            double spin = time * (k % 2 == 0 ? 0.05 : -0.04);
            double arc = done ? Math.PI * 2 : Math.PI * 2 * prog;
            float a = done ? 0.75F : 0.4F;
            org.lwjgl.opengl.GL11.glLineWidth(done ? 3F : 2F);
            b.begin(org.lwjgl.opengl.GL11.GL_LINE_STRIP, net.minecraft.client.renderer.vertex.DefaultVertexFormats.POSITION_COLOR);
            int seg = 56;
            for (int i = 0; i <= seg; i++) {
                double ang = spin + arc * i / seg;
                b.pos(Math.cos(ang) * r, y + 0.01 * k, Math.sin(ang) * r).color(cr, cg, cb, a).endVertex();
            }
            t.draw();
            if (done) {
                // rune ticks on a finished ring
                org.lwjgl.opengl.GL11.glLineWidth(2F);
                b.begin(org.lwjgl.opengl.GL11.GL_LINES, net.minecraft.client.renderer.vertex.DefaultVertexFormats.POSITION_COLOR);
                int ticks = 6 + 2 * k;
                for (int i = 0; i < ticks; i++) {
                    double ang = spin + Math.PI * 2 * i / ticks;
                    b.pos(Math.cos(ang) * (r - 0.12), y + 0.01 * k, Math.sin(ang) * (r - 0.12)).color(cr, cg, cb, 0.6F).endVertex();
                    b.pos(Math.cos(ang) * (r + 0.12), y + 0.01 * k, Math.sin(ang) * (r + 0.12)).color(cr, cg, cb, 0.6F).endVertex();
                }
                t.draw();
            }
        }
        org.lwjgl.opengl.GL11.glLineWidth(1F);
        net.minecraft.client.renderer.GlStateManager.enableCull();
        net.minecraft.client.renderer.GlStateManager.enableTexture2D();
        net.minecraft.client.renderer.GlStateManager.popMatrix();
    }

    private static void drawTarget(Minecraft mc, Magic.Spell s, float pt, int built) {
        double range, r;
        switch (s.shape) {
            case 1: range = 8; r = 2.5; break;
            case 5: range = 8; r = 3; break;
            case 7: range = 14; r = 2.5; break;
            case 8: range = 20; r = 3; break;
            case 10: range = 8; r = 2.5; break;
            default: return;
        }
        r *= (1 + 0.12 * s.sz) * (1 + 0.1 * built);
        net.minecraft.util.math.Vec3d at = SkillShapes.aim(mc.player, range);
        double gy = SkillShapes.ground(mc.world, at.x, at.y + 0.5, at.z) + 0.06;
        double cx = mc.player.lastTickPosX + (mc.player.posX - mc.player.lastTickPosX) * pt;
        double cy = mc.player.lastTickPosY + (mc.player.posY - mc.player.lastTickPosY) * pt;
        double cz = mc.player.lastTickPosZ + (mc.player.posZ - mc.player.lastTickPosZ) * pt;
        int rgb = rgbOf(s);
        net.minecraft.client.renderer.GlStateManager.pushMatrix();
        net.minecraft.client.renderer.GlStateManager.disableTexture2D();
        net.minecraft.client.renderer.GlStateManager.disableLighting();
        net.minecraft.client.renderer.GlStateManager.enableBlend();
        net.minecraft.client.renderer.GlStateManager.disableCull();
        org.lwjgl.opengl.GL11.glLineWidth(2.5F);
        net.minecraft.client.renderer.Tessellator t = net.minecraft.client.renderer.Tessellator.getInstance();
        net.minecraft.client.renderer.BufferBuilder b = t.getBuffer();
        float cr = (rgb >> 16 & 255) / 255F, cg = (rgb >> 8 & 255) / 255F, cb = (rgb & 255) / 255F;
        float pulse = 0.55F + 0.35F * charge();
        double rr = r * (0.85 + 0.15 * charge());
        b.begin(org.lwjgl.opengl.GL11.GL_LINE_LOOP, net.minecraft.client.renderer.vertex.DefaultVertexFormats.POSITION_COLOR);
        for (int k = 0; k < 48; k++) {
            double a = k * Math.PI * 2 / 48;
            b.pos(at.x + Math.cos(a) * rr - cx, gy - cy, at.z + Math.sin(a) * rr - cz).color(cr, cg, cb, pulse).endVertex();
        }
        t.draw();
        b.begin(org.lwjgl.opengl.GL11.GL_TRIANGLE_FAN, net.minecraft.client.renderer.vertex.DefaultVertexFormats.POSITION_COLOR);
        b.pos(at.x - cx, gy - cy, at.z - cz).color(cr, cg, cb, 0.18F).endVertex();
        for (int k = 0; k <= 48; k++) {
            double a = k * Math.PI * 2 / 48;
            b.pos(at.x + Math.cos(a) * rr - cx, gy - cy, at.z + Math.sin(a) * rr - cz).color(cr, cg, cb, 0.05F).endVertex();
        }
        t.draw();
        org.lwjgl.opengl.GL11.glLineWidth(1F);
        net.minecraft.client.renderer.GlStateManager.enableCull();
        net.minecraft.client.renderer.GlStateManager.enableTexture2D();
        net.minecraft.client.renderer.GlStateManager.popMatrix();
    }
}
