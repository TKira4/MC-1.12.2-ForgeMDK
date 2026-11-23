package com.imba.undyingarcana;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Skill keys: Quick = Z, Heavy = X (hold to charge, release to fire), Art = C. Rebindable in Controls. Plain keys work
 * while sneaking or sprinting; right-click is left to the game (shields, food, blocks).
 * The held state is read every client tick so the server knows exactly when Heavy is released.
 */
@SideOnly(Side.CLIENT)
public class SkillKeys {
    public static final KeyBinding[] KEYS = new KeyBinding[3];
    private static final boolean[] DOWN = new boolean[3];
    private static boolean latch;

    public static void register() {
        KEYS[0] = new KeyBinding("key.undyingarcana.skill_quick", KeyConflictContext.IN_GAME, KeyModifier.NONE, Keyboard.KEY_Z, "key.categories.undyingarcana");
        KEYS[1] = new KeyBinding("key.undyingarcana.skill_heavy", KeyConflictContext.IN_GAME, KeyModifier.NONE, Keyboard.KEY_X, "key.categories.undyingarcana");
        KEYS[2] = new KeyBinding("key.undyingarcana.skill_art", KeyConflictContext.IN_GAME, KeyModifier.NONE, Keyboard.KEY_C, "key.categories.undyingarcana");
        for (KeyBinding k : KEYS) {
            ClientRegistry.registerKeyBinding(k);
        }
    }

    private static boolean modifierHeld(KeyModifier m) {
        boolean shift = GuiScreen.isShiftKeyDown(), ctrl = GuiScreen.isCtrlKeyDown(), alt = GuiScreen.isAltKeyDown();
        switch (m) {
            case SHIFT: return shift;
            case CONTROL: return ctrl;
            case ALT: return alt;
            default: return true; // a plain key also works while sneaking / sprinting
        }
    }

    private static boolean physicallyDown(int code) {
        if (code == 0) {
            return false;
        }
        if (code < 0) {
            int b = code + 100;
            return b >= 0 && b < Mouse.getButtonCount() && Mouse.isButtonDown(b);
        }
        return code < Keyboard.KEYBOARD_SIZE && Keyboard.isKeyDown(code);
    }

    /** Short label for the HUD: "Z", "X", "S+Z"... */
    public static String label(int slot) {
        KeyBinding k = KEYS[slot];
        if (k == null) {
            return "?";
        }
        int code = k.getKeyCode();
        String key = code < 0 ? "M" + (code + 101) : Keyboard.getKeyName(code);
        if (key == null) {
            key = "?";
        }
        if (key.length() > 3) {
            key = key.substring(0, 3);
        }
        switch (k.getKeyModifier()) {
            case SHIFT: return "S+" + key;
            case CONTROL: return "C+" + key;
            case ALT: return "A+" + key;
            default: return key;
        }
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        boolean focus = mc.player != null && mc.currentScreen == null && mc.inGameHasFocus;
        // A key with a modifier wins over the plain key (Shift+R must not also fire Quick).
        int active = -1;
        if (focus) {
            for (int i = 2; i >= 0; i--) {
                KeyBinding k = KEYS[i];
                if (k != null && physicallyDown(k.getKeyCode()) && modifierHeld(k.getKeyModifier())) {
                    active = i;
                    break;
                }
            }
        }
        // After Shift+R / Alt+R, letting go of the modifier before R must not fire Quick: wait for R to be released.
        boolean anyPhysical = false;
        for (KeyBinding k : KEYS) {
            if (k != null && physicallyDown(k.getKeyCode())) {
                anyPhysical = true;
            }
        }
        if (!anyPhysical) {
            latch = false;
        }
        if ((DOWN[1] && active != 1 || DOWN[2] && active != 2) && anyPhysical) {
            latch = true;
        }
        if (latch) {
            active = -1;
        }
        for (int i = 0; i < 3; i++) {
            boolean now = i == active;
            if (now != DOWN[i]) {
                DOWN[i] = now;
                SkillNet.NET.sendToServer(new SkillNet.MsgKey(i, now));
            }
            // swallow vanilla press counters so nothing else reacts
            if (KEYS[i] != null) {
                while (KEYS[i].isPressed()) {
                    // drain
                }
            }
        }
    }
}
