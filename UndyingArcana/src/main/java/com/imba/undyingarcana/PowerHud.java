package com.imba.undyingarcana;

import org.lwjgl.input.Keyboard;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.EnumHandSide;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Client HUD for mana (bar left of the hotbar), the Awakening state and the Grimoire's open spells as icons (left of the
 * mana bar, while the spell key is held). Also owns the Awaken key (G).
 */
@SideOnly(Side.CLIENT)
public class PowerHud {
    public static volatile SkillNet.MsgPower data;
    private static KeyBinding awakenKey;
    private static long receivedAt;
    private float shownMana = -1;

    public static void registerKey() {
        awakenKey = new KeyBinding("key.undyingarcana.awaken", KeyConflictContext.IN_GAME, Keyboard.KEY_G, "key.categories.undyingarcana");
        ClientRegistry.registerKeyBinding(awakenKey);
    }

    /** "+N" coin pop-up (amount and time of the last gain). */
    static int coinPop;
    static long coinPopAt;

    public static void receive(SkillNet.MsgPower m) {
        RelicClient.receive(m);
        if (data != null && m.coins > data.coins) {
            coinPop = m.coins - data.coins + (System.currentTimeMillis() - coinPopAt < 1500 ? coinPop : 0);
            coinPopAt = System.currentTimeMillis();
        }
        Coins.clientBalance = m.coins;
        data = m;
        receivedAt = System.currentTimeMillis();
    }

    public static String keyLabel() {
        return awakenKey == null ? "G" : Keyboard.getKeyName(awakenKey.getKeyCode());
    }

    /** Decoded spells from the last sync. */
    public static Magic.Spell[] spells() {
        Magic.Spell[] out = new Magic.Spell[Magic.SPELLS];
        String[] parts = data == null ? new String[0] : data.spells.split(";");
        for (int i = 0; i < Magic.SPELLS; i++) {
            out[i] = i < parts.length ? Magic.Spell.decode(parts[i]) : Magic.Spell.decode("0,0,0,0,0");
        }
        return out;
    }

    /** Learned element of each of the 8 slots (-1 = none), then open element slots [8], rune slots [9], spell slots [10]. */
    public static int[] elements() {
        int[] e = {-1, -1, -1, -1, -1, -1, -1, -1, 1, 1, 1};
        if (data != null) {
            String[] p = data.elements.split(",");
            for (int i = 0; i < e.length && i < p.length; i++) {
                try {
                    e[i] = Integer.parseInt(p[i].trim());
                } catch (NumberFormatException ignored) {
                    // keep default
                }
            }
        }
        return e;
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent e) {
        Minecraft mc = Minecraft.getMinecraft();
        if (e.phase != TickEvent.Phase.END || awakenKey == null || mc.player == null) {
            return;
        }
        while (awakenKey.isPressed()) {
            if (data != null && data.on && mc.currentScreen == null) {
                SkillNet.NET.sendToServer(new SkillNet.MsgAct(1, 0, ""));
            }
        }
    }

    @SubscribeEvent
    public void onOverlay(RenderGameOverlayEvent.Post e) {
        SkillNet.MsgPower d = data;
        Minecraft mc = Minecraft.getMinecraft();
        if (e.getType() != RenderGameOverlayEvent.ElementType.HOTBAR || d == null || !d.on || mc.player == null || mc.player.isSpectator()
                || DebugToggles.noHud
                || mc.gameSettings.hideGUI) {
            return;
        }
        ScaledResolution sr = e.getResolution();
        int w = sr.getScaledWidth(), h = sr.getScaledHeight();
        FontRenderer fr = mc.fontRenderer;
        float secs = (System.currentTimeMillis() - receivedAt) / 1000.0F;
        // ---- "+N" coins, rising beside the crosshair for 1.5 s
        long pop = System.currentTimeMillis() - coinPopAt;
        if (coinPop > 0 && pop < 1500) {
            float t = pop / 1500F;
            int alpha = Math.max(8, Math.round(255 * (1 - t * t)));
            String s = "+" + coinPop;
            int px = w / 2 + 12, py = Math.round(h / 2 - 12 - 10 * t);
            GlStateManager.enableBlend();
            GlStateManager.pushMatrix();
            GlStateManager.translate(px, py, 0);
            GlStateManager.scale(0.5F, 0.5F, 1F);
            net.minecraft.client.renderer.RenderHelper.enableGUIStandardItemLighting();
            mc.getRenderItem().renderItemAndEffectIntoGUI(new net.minecraft.item.ItemStack(ModItems.SOUL_COIN), 0, 0);
            net.minecraft.client.renderer.RenderHelper.disableStandardItemLighting();
            GlStateManager.popMatrix();
            fr.drawStringWithShadow(s, px + 10, py, 0xFFD050 | alpha << 24);
        }
        // ---- left of the hotbar: awakening line and mana bar
        int right = w / 2 - 91 - 6;
        if (mc.player.getPrimaryHand() == EnumHandSide.RIGHT && !mc.player.getHeldItemOffhand().isEmpty()) {
            right -= 29;
        }
        int bw = 82, x = right - bw;
        SkillCatalog.Info held = SkillCatalog.identify(mc.player.getHeldItemMainhand());
        boolean hasGrim = Magic.hasGrimoire(mc.player);
        // spell bar: while the spell key is held, or for 2s after a cast press
        boolean grim = hasGrim && (SpellKeys.held() || SpellKeys.chargeSlot >= 0 || mc.player.ticksExisted - SpellKeys.lastCast < 40);
        boolean caster = hasGrim || held != null && held.isWand();
        boolean showMana = caster || d.mana < d.manaMax;
        if (shownMana < 0) {
            shownMana = d.mana;
        }
        shownMana += (d.mana - shownMana) * 0.25F;
        GlStateManager.enableBlend();
        if (showMana) {
            float f = d.manaMax <= 0 ? 0 : shownMana / d.manaMax;
            UiKit.rect(x - 1, h - 10, x + bw + 1, h - 3, 0xC0000000);
            UiKit.rect(x, h - 9, x + Math.round(bw * Math.max(0, Math.min(1, f))), h - 4, 0xFF3A6AF0);
            UiKit.rect(x, h - 9, x + Math.round(bw * Math.max(0, Math.min(1, f))), h - 8, 0xFF9CC0FF);
            String ms = "\u00a79Mana \u00a7f" + d.mana + "\u00a77/" + d.manaMax;
            fr.drawStringWithShadow(ms, right - fr.getStringWidth(ms), h - 19, 0xFFFFFF);
        }
        if (d.canAwaken) {
            String s;
            int left = Math.max(0, d.awakeLeft - Math.round(secs * 20));
            if (left > 0) {
                float pulse = 0.5F + 0.5F * (float) Math.sin(System.currentTimeMillis() / 120.0);
                s = "\u00a76\u2726 AWAKENED \u00a7f" + (left / 20 + 1) + "s";
                int tw = fr.getStringWidth(s);
                UiKit.rect(right - tw - 3, h - 31, right + 1, h - 21, (Math.round(0x50 + 0x50 * pulse) << 24) | 0x3A2A08);
            } else {
                int cd = Math.max(0, d.awakeCd - Math.round(secs * 20));
                s = cd > 0 ? "\u00a78[" + keyLabel() + "] Awaken \u00a77" + (cd / 1200) + ":" + String.format("%02d", cd / 20 % 60)
                        : "\u00a7e[" + keyLabel() + "] Awaken ready";
            }
            fr.drawStringWithShadow(s, right - fr.getStringWidth(s), showMana ? h - 30 : h - 19, 0xFFFFFF);
        }
        // ---- left of the mana bar, while the spell key is held: spell icons (one row, two rows on narrow screens)
        if (grim) {
            Magic.Spell[] sp = spells();
            int[] el = elements();
            int n = Math.min(Magic.SPELLS, el[10]);
            int per = x - 6 - n * 25 >= 2 ? n : 3;
            int x0 = x - 6 - per * 25 + 3;
            for (int i = 0; i < n; i++) {
                int bx = x0 + (i % per) * 25, by = h - 23 - (i / per) * 34;
                Magic.Spell s = sp[i];
                int e1 = s.which >= 0 && s.which < 8 && el[s.which] >= 0 ? el[s.which] : el[0];
                int e2 = s.which2 >= 0 && s.which2 < 8 ? el[s.which2] : -1;
                int col = e1 >= 0 ? Magic.ELEMENT_RGB[e1] : 0x808080;
                int col2 = e2 >= 0 && e2 != e1 ? Magic.ELEMENT_RGB[e2] : col;
                boolean ok = s.valid() && d.mana >= s.cost();
                UiKit.rect(bx, by, bx + 22, by + 22, 0xC00A0A10);
                if (s.shape >= 0 && s.shape < Magic.SHAPE.length) {
                    icon(mc, Magic.SHAPE[s.shape], bx + 3, by + 3, col, col2, ok ? 1F : 0.4F);
                }
                if (SpellKeys.chargeSlot == i) {
                    int ch = Math.round(20 * SpellKeys.charge());
                    UiKit.rect(bx + 1, by + 21 - ch, bx + 21, by + 21, 0x60FFFFFF);
                }
                // ring pips on the right edge, bottom up: how many rings the spell can charge (lit = built)
                int built = SpellKeys.chargeSlot == i ? SpellKeys.rings() : 0;
                for (int r = 0; r < Math.min(6, s.rings); r++) {
                    int py = by + 18 - r * 3; // up to 6 pips inside the 22 px slot
                    UiKit.rect(bx + 19, py, bx + 21, py + 2, r < built ? 0xFF000000 | col : 0x90FFFFFF & 0x90A0A0A0);
                }
                int cd = Math.max(0, d.spellCd[i] - Math.round(secs * 20));
                if (cd > 0 && s.valid()) {
                    float f = Math.min(1, cd / (float) Math.max(1, s.cooldown()));
                    UiKit.rect(bx + 1, by + 1 + Math.round(20 * (1 - f)), bx + 21, by + 21, 0xA0000000);
                }
                String k = SpellKeys.label(i);
                fr.drawStringWithShadow(k, bx + 11 - fr.getStringWidth(k) / 2, by - 9, 0xC8B890);
                UiKit.rect(bx, by, bx + 22, by + 1, 0xFF000000 | col);
                UiKit.rect(bx, by + 21, bx + 22, by + 22, ok ? 0xFF000000 | col2 : 0xFFD04040);
            }
        }
        GlStateManager.color(1, 1, 1, 1);
    }

    /** Spell shape icon (textures/gui/spell_<shape>.png, 32x64): glow tinted with the element, core with a light mix of the second. */
    private static void icon(Minecraft mc, String shape, int x, int y, int col, int col2, float bright) {
        mc.getTextureManager().bindTexture(new net.minecraft.util.ResourceLocation(UndyingArcana.MODID,
                "textures/gui/spell_" + shape.toLowerCase() + ".png"));
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color((col >> 16 & 255) / 255F * bright, (col >> 8 & 255) / 255F * bright, (col & 255) / 255F * bright, 0.85F);
        net.minecraft.client.gui.Gui.drawModalRectWithCustomSizedTexture(x, y, 0, 0, 16, 16, 16, 32);
        float r = (0.55F + 0.45F * (col2 >> 16 & 255) / 255F) * bright, g = (0.55F + 0.45F * (col2 >> 8 & 255) / 255F) * bright,
                b = (0.55F + 0.45F * (col2 & 255) / 255F) * bright;
        GlStateManager.color(r, g, b, 1F);
        net.minecraft.client.gui.Gui.drawModalRectWithCustomSizedTexture(x, y, 0, 16, 16, 16, 16, 32);
        GlStateManager.color(1F, 1F, 1F, 1F);
    }
}
