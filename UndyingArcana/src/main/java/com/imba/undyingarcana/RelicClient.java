package com.imba.undyingarcana;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.InventoryEffectRenderer;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHandSide;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Client side of relics: the relic panel left of the inventory (vanilla style, toggled by a tab button above it), empty
 * slot icons, slot tooltips, shift-click to wear, the active relic keys (R / B) with their HUD icons, the step-up of the
 * Anklet of Stepping, and a one-time move of other mods' keys that clash with Undying Arcana's defaults.
 */
public class RelicClient {
    public static KeyBinding[] keys = new KeyBinding[2];
    /** Relic panel shown in the inventory (tab button toggles it). */
    private static boolean shown = true;
    private static final int TAB_ID = 7701;
    /** Panel geometry relative to the inventory's top-left corner. */
    private static final int PX = -69, PY = 4, PW = 66, PH = 128;
    private static int[] cd = new int[2], total = {1, 1};
    private static long cdAt;
    private static boolean stepped;

    public static void init() {
        keys[0] = new KeyBinding("key.undyingarcana.relic1", KeyConflictContext.IN_GAME, KeyModifier.NONE, Keyboard.KEY_R,
                "key.categories.undyingarcana");
        keys[1] = new KeyBinding("key.undyingarcana.relic2", KeyConflictContext.IN_GAME, KeyModifier.NONE, Keyboard.KEY_B,
                "key.categories.undyingarcana");
        for (KeyBinding k : keys) {
            ClientRegistry.registerKeyBinding(k);
        }
        // Martial Manual: the band takes the style colour
        Minecraft.getMinecraft().getItemColors().registerItemColorHandler(
                (s, tint) -> tint == 1 ? Martial.RGB[Martial.manualStyle(s)] : -1, Martial.MANUAL);
    }

    /** Relic slots are drawn and clickable only in the survival inventory with the panel open. */
    public static boolean slotsShown() {
        return shown && on() && Minecraft.getMinecraft().currentScreen instanceof GuiInventory;
    }

    /** New-world systems on in this world (from the last MsgPower). */
    static boolean on() {
        SkillNet.MsgPower d = PowerHud.data;
        return d == null || d.on;
    }

    public static void receive(SkillNet.MsgPower m) {
        Relics.clientMask = m.relicMask;
        cd = m.relicCd.clone();
        total = m.relicTotal.clone();
        cdAt = System.currentTimeMillis();
    }

    // ------------------------------------------------------------------ slot icons
    @SubscribeEvent
    public void onStitch(TextureStitchEvent.Pre e) {
        if (e.getMap() == Minecraft.getMinecraft().getTextureMapBlocks() || e.getMap().getBasePath().equals("textures")) {
            for (String k : Relics.KIND) {
                e.getMap().registerSprite(new ResourceLocation(UndyingArcana.MODID, "gui/relic_slot/" + k));
            }
        }
    }

    // ------------------------------------------------------------------ inventory panel
    @SubscribeEvent
    public void onInit(GuiScreenEvent.InitGuiEvent.Post e) {
        if (e.getGui() instanceof GuiInventory && on()) {
            GuiContainer g = (GuiContainer) e.getGui();
            e.getButtonList().add(new TabButton(TAB_ID, g.getGuiLeft() + PX, g.getGuiTop() + PY - 23));
        }
    }

    @SubscribeEvent
    public void onAction(GuiScreenEvent.ActionPerformedEvent.Pre e) {
        if (e.getGui() instanceof GuiInventory && e.getButton().id == TAB_ID) {
            shown = !shown;
            e.getGui().initGui(); // re-centre (potion list) and move the tab
            e.setCanceled(true);
        }
    }

    /** With the panel open the inventory stays centred (no potion shift); effects still show on the HUD. */
    @SubscribeEvent
    public void onPotionShift(GuiScreenEvent.PotionShiftEvent e) {
        if (shown && e.getGui() instanceof GuiInventory && on()) {
            e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onDrawPre(GuiScreenEvent.DrawScreenEvent.Pre e) {
        if (shown && e.getGui() instanceof GuiInventory && on()) {
            try {
                ObfuscationReflectionHelper.setPrivateValue(InventoryEffectRenderer.class, (InventoryEffectRenderer) e.getGui(), false,
                        "field_147045_u", "hasActivePotionEffects");
            } catch (RuntimeException ignored) {
                // the list may overlap; nothing else breaks
            }
        }
    }

    @SubscribeEvent
    public void onBackground(GuiScreenEvent.BackgroundDrawnEvent e) {
        if (!slotsShown()) {
            return;
        }
        GuiContainer g = (GuiContainer) e.getGui();
        int x = g.getGuiLeft() + PX, y = g.getGuiTop() + PY;
        panel(x, y, PW, PH);
        Minecraft.getMinecraft().fontRenderer.drawString("Relics", x + 7, y + 5, 0x404040);
        for (int i = 0; i < Relics.SLOTS; i++) {
            int sx = x + 6 + i % 3 * 18, sy = y + 14 + i / 3 * 18;
            boolean open = (Relics.clientMask & 1 << i) != 0;
            slotFrame(sx, sy, open ? 0xFF8B8B8B : 0xFF6E6A64);
        }
        // key letters on the two active relic slots (bottom-right corner, small)
        for (int k = 0; k < 2; k++) {
            int i = k == 0 ? Relics.ACTIVE_1 : Relics.ACTIVE_2;
            int sx = x + 6 + i % 3 * 18, sy = y + 14 + i / 3 * 18;
            String key = keyName(k);
            GlStateManager.pushMatrix();
            GlStateManager.translate(sx + 17 - Minecraft.getMinecraft().fontRenderer.getStringWidth(key) * 0.5F, sy + 12, 300);
            GlStateManager.scale(0.5F, 0.5F, 1F);
            Minecraft.getMinecraft().fontRenderer.drawStringWithShadow(key, 0, 0, 0xFFFF55);
            GlStateManager.popMatrix();
        }
    }

    @SubscribeEvent
    public void onDrawPost(GuiScreenEvent.DrawScreenEvent.Post e) {
        if (!slotsShown()) {
            return;
        }
        GuiContainer g = (GuiContainer) e.getGui();
        Slot s = g.getSlotUnderMouse();
        if (s instanceof Relics.RelicSlot && !s.getHasStack() && Minecraft.getMinecraft().player.inventory.getItemStack().isEmpty()) {
            Relics.RelicSlot r = (Relics.RelicSlot) s;
            int kind = Relics.SLOT_KIND[r.index];
            String name = "\u00a7f" + Relics.KIND_NAME[kind] + (kind == Relics.RELIC ? " \u00a7e[" + keyName(r.index == Relics.ACTIVE_1 ? 0 : 1) + "]" : "");
            String line = r.open() ? "\u00a77Empty - put a " + Relics.KIND_NAME[kind].toLowerCase() + " here"
                    : "\u00a7cLocked \u00a77- opens at level " + Relics.SLOT_LEVEL[r.index];
            g.drawHoveringText(Arrays.asList(name, line), e.getMouseX(), e.getMouseY());
        }
    }

    /** Shift-click a relic in the inventory: wear it. */
    @SubscribeEvent
    public void onMouse(GuiScreenEvent.MouseInputEvent.Pre e) {
        if (!(e.getGui() instanceof GuiInventory) || !Mouse.getEventButtonState() || Mouse.getEventButton() != 0 || !GuiScreen.isShiftKeyDown()) {
            return;
        }
        Slot s = ((GuiContainer) e.getGui()).getSlotUnderMouse();
        if (s != null && !(s instanceof Relics.RelicSlot) && s.slotNumber >= 9 && s.slotNumber < Relics.FIRST && Relics.isRelic(s.getStack())) {
            SkillNet.NET.sendToServer(new SkillNet.MsgRelic(0, s.slotNumber));
            e.setCanceled(true);
        }
    }

    private static void panel(int x, int y, int w, int h) {
        Gui.drawRect(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF000000);
        Gui.drawRect(x, y, x + w, y + h, 0xFFC6C6C6);
        Gui.drawRect(x, y, x + w - 1, y + 1, 0xFFFFFFFF);
        Gui.drawRect(x, y, x + 1, y + h - 1, 0xFFFFFFFF);
        Gui.drawRect(x + 1, y + h - 1, x + w, y + h, 0xFF555555);
        Gui.drawRect(x + w - 1, y + 1, x + w, y + h, 0xFF555555);
    }

    private static void slotFrame(int x, int y, int fill) {
        Gui.drawRect(x, y, x + 18, y + 18, fill);
        Gui.drawRect(x, y, x + 17, y + 1, 0xFF373737);
        Gui.drawRect(x, y, x + 1, y + 17, 0xFF373737);
        Gui.drawRect(x + 1, y + 17, x + 18, y + 18, 0xFFFFFFFF);
        Gui.drawRect(x + 17, y + 1, x + 18, y + 18, 0xFFFFFFFF);
    }

    /** The relic tab above the panel (vanilla button frame with the relic icon). */
    private static class TabButton extends GuiButton {
        TabButton(int id, int x, int y) {
            super(id, x, y, 22, 21, "");
        }

        @Override
        public void drawButton(Minecraft mc, int mx, int my, float pt) {
            if (!visible) {
                return;
            }
            hovered = mx >= x && my >= y && mx < x + width && my < y + height;
            panel(x, y, width, height);
            if (hovered) {
                Gui.drawRect(x + 1, y + 1, x + width - 1, y + height - 1, 0x40FFFFFF);
            }
            mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
            TextureAtlasSprite sp = mc.getTextureMapBlocks().getAtlasSprite(UndyingArcana.MODID + ":gui/relic_slot/relic");
            GlStateManager.enableBlend();
            GlStateManager.color(shown ? 0.45F : 0.35F, shown ? 0.3F : 0.33F, shown ? 0.8F : 0.33F, 1F);
            drawTexturedModalRect(x + 3, y + 3, sp, 16, 16);
            GlStateManager.color(1F, 1F, 1F, 1F);
            if (hovered) {
                GuiScreen s = mc.currentScreen;
                if (s != null) {
                    s.drawHoveringText(Arrays.asList(shown ? "Hide relics" : "Show relics"), mx, my);
                }
            }
        }
    }

    // ------------------------------------------------------------------ keys, HUD, anklet
    static String keyName(int k) {
        return keys[k] == null ? (k == 0 ? "R" : "B") : keys[k].getDisplayName();
    }

    @SubscribeEvent
    public void onKey(InputEvent.KeyInputEvent e) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.currentScreen != null) {
            return;
        }
        for (int k = 0; k < 2; k++) {
            while (keys[k] != null && keys[k].isPressed()) {
                SkillNet.NET.sendToServer(new SkillNet.MsgRelic(1, k));
            }
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent e) {
        Minecraft mc = Minecraft.getMinecraft();
        if (e.phase != TickEvent.Phase.END || mc.player == null) {
            return;
        }
        remapOnce(mc);
        boolean step = false;
        for (ItemStack s : Relics.equipped(mc.player)) {
            step |= Relics.defOf(s) == Relics.STEP_ANKLET;
        }
        if (step) {
            mc.player.stepHeight = Math.max(mc.player.stepHeight, 1.0F);
            stepped = true;
        } else if (stepped) {
            mc.player.stepHeight = 0.6F;
            stepped = false;
        }
    }

    @SubscribeEvent
    public void onOverlay(RenderGameOverlayEvent.Post e) {
        if (e.getType() != RenderGameOverlayEvent.ElementType.HOTBAR || DebugToggles.noHud) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.player.isSpectator() || mc.gameSettings.hideGUI) {
            return;
        }
        ScaledResolution sr = e.getResolution();
        int w = sr.getScaledWidth(), h = sr.getScaledHeight();
        // right of the weapon skill slots (and the gun ammo box)
        int x = w / 2 + 91 + 6;
        if (mc.player.getPrimaryHand() == EnumHandSide.LEFT && !mc.player.getHeldItemOffhand().isEmpty()) {
            x += 29;
        }
        if (mc.gameSettings.attackIndicator == 2) {
            x += 24;
        }
        x += 3 * 25 + 4;
        SkillCatalog.Info held = SkillCatalog.identify(mc.player.getHeldItemMainhand());
        if (held != null && held.isGun()) {
            x += 38;
        }
        int y = h - 22;
        FontRenderer fr = mc.fontRenderer;
        long elapsed = (System.currentTimeMillis() - cdAt) / 50;
        Relics.Inv v = Relics.inv(mc.player);
        for (int k = 0; k < 2; k++) {
            ItemStack s = v.getStackInSlot(k == 0 ? Relics.ACTIVE_1 : Relics.ACTIVE_2);
            if (s.isEmpty()) {
                continue;
            }
            Gui.drawRect(x, y, x + 22, y + 22, 0xB00A0A10);
            int border = 0xFF8A5AD8;
            Gui.drawRect(x, y, x + 22, y + 1, border);
            Gui.drawRect(x, y + 21, x + 22, y + 22, border);
            Gui.drawRect(x, y, x + 1, y + 22, border);
            Gui.drawRect(x + 21, y, x + 22, y + 22, border);
            RenderHelper.enableGUIStandardItemLighting();
            mc.getRenderItem().renderItemAndEffectIntoGUI(s, x + 3, y + 3);
            RenderHelper.disableStandardItemLighting();
            long left = Math.max(0, cd[k] - elapsed);
            if (left > 0) {
                GlStateManager.disableDepth();
                SkillHud.pie(x + 3, y + 3, 16, (float) left / Math.max(1, total[k]));
                String t = String.valueOf(left / 20 + 1);
                fr.drawStringWithShadow(t, x + 11 - fr.getStringWidth(t) / 2.0F + 1, y + 7, 0xFFFFFF);
                GlStateManager.enableDepth();
            }
            String key = keyName(k);
            fr.drawStringWithShadow(key, x + 11 - fr.getStringWidth(key) / 2.0F + 1, y - 10, 0xD8B8FF);
            x += 25;
        }
        GlStateManager.color(1F, 1F, 1F, 1F);
    }

    // ------------------------------------------------------------------ one-time key move
    /** Other mods' keys that sit on Undying Arcana's default keys: id, the clashing default, new key (0 = none). */
    private static final Object[][] MOVE = {
            {"key.journeymap.toggle_waypoints", Keyboard.KEY_Z, 0},
            {"key.toolbelt.open", Keyboard.KEY_R, Keyboard.KEY_Y},
            {"key.toolbelt.slot", Keyboard.KEY_V, 0},
            {"key.mod_lavacow.special", Keyboard.KEY_G, Keyboard.KEY_N}};
    private static boolean remapped;

    /** Moves the clashing keys once per install (marker file in config/); later changes in Controls are left alone. */
    private static void remapOnce(Minecraft mc) {
        if (remapped) {
            return;
        }
        remapped = true;
        File flag = new File(mc.mcDataDir, "config/undyingarcana_keys.txt");
        if (flag.exists()) {
            return;
        }
        StringBuilder log = new StringBuilder("Undying Arcana moved these keys once:\n");
        for (KeyBinding k : mc.gameSettings.keyBindings) {
            for (Object[] m : MOVE) {
                if (k.getKeyDescription().equals(m[0]) && k.getKeyCode() == (Integer) m[1] && k.getKeyModifier() == KeyModifier.NONE) {
                    k.setKeyModifierAndCode(KeyModifier.NONE, (Integer) m[2]);
                    log.append(m[0]).append(" -> ").append((Integer) m[2] == 0 ? "none" : Keyboard.getKeyName((Integer) m[2])).append('\n');
                }
            }
        }
        KeyBinding.resetKeyBindingArrayAndHash();
        mc.gameSettings.saveOptions();
        try {
            flag.getParentFile().mkdirs();
            Files.write(flag.toPath(), log.toString().getBytes(StandardCharsets.UTF_8));
        } catch (java.io.IOException ignored) {
            // the move simply runs again next start
        }
    }
}
