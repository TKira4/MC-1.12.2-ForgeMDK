package com.imba.undyingarcana;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.init.SoundEvents;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Small UI kit shared by the Character screen and the Journal: one palette, easing curves, springs that ease any
 * number toward a target every frame (frame-rate independent), colour blending and the open animation.
 */
@SideOnly(Side.CLIENT)
public final class UiKit {
    public static final int CARD = 0xFF0E0C0A, CARD_HI = 0xFF2A2214, MUTED = 0x8C8370, DIM = 0x7A6A50, GOLD = 0xFFD050,
            GREEN = 0x8CFF8C;

    private UiKit() {
    }

    // ------------------------------------------------------------------ easing
    public static float clamp01(float t) {
        return t < 0 ? 0 : t > 1 ? 1 : t;
    }

    public static float easeOutCubic(float t) {
        t = clamp01(t);
        float u = 1 - t;
        return 1 - u * u * u;
    }

    /** Slight overshoot, for things that pop in. */
    public static float easeOutBack(float t) {
        t = clamp01(t);
        float c1 = 1.70158F, c3 = c1 + 1;
        float u = t - 1;
        return 1 + c3 * u * u * u + c1 * u * u;
    }

    public static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    public static int blend(int a, int b, float t) {
        t = clamp01(t);
        int aa = a >>> 24, ar = a >> 16 & 255, ag = a >> 8 & 255, ab = a & 255;
        int ba = b >>> 24, br = b >> 16 & 255, bg = b >> 8 & 255, bb = b & 255;
        return Math.round(lerp(aa, ba, t)) << 24 | Math.round(lerp(ar, br, t)) << 16 | Math.round(lerp(ag, bg, t)) << 8 | Math.round(lerp(ab, bb, t));
    }

    /** Eases toward its target with exponential smoothing; speed ~ 1 / time constant in seconds. */
    public static final class Spring {
        public float v;
        private boolean init;

        public float to(float target, float dt, float speed) {
            if (!init) {
                init = true;
                v = target;
                return v;
            }
            v += (target - v) * (1 - (float) Math.exp(-dt * speed));
            if (Math.abs(target - v) < 0.001F) {
                v = target;
            }
            return v;
        }

        public void snap(float value) {
            init = true;
            v = value;
        }
    }

    /** Springs by name, plus the frame clock and the open animation of one screen. */
    public static final class Anim {
        private final Map<String, Spring> springs = new HashMap<>();
        private final long opened = System.nanoTime();
        private long last = opened;
        public float dt;

        public void frame() {
            long now = System.nanoTime();
            dt = Math.min(0.1F, (now - last) / 1e9F);
            last = now;
        }

        public float to(String key, float target, float speed) {
            return springs.computeIfAbsent(key, k -> new Spring()).to(target, dt, speed);
        }

        /** Same, but starts from a given value instead of jumping to the first target. */
        public float from(String key, float start, float target, float speed) {
            Spring s = springs.get(key);
            if (s == null) {
                s = new Spring();
                s.snap(start);
                springs.put(key, s);
            }
            return s.to(target, dt, speed);
        }

        public float openT(float seconds) {
            return clamp01((System.nanoTime() - opened) / 1e9F / seconds);
        }

        public float time() {
            return (System.nanoTime() - opened) / 1e9F;
        }
    }

    // ------------------------------------------------------------------ drawing (style A: dark stone, gold trim)
    private static final net.minecraft.util.ResourceLocation STONE = new net.minecraft.util.ResourceLocation("minecraft", "textures/blocks/stonebrick.png");
    public static final int GOLD_EDGE = 0xFF6B5A36, GOLD_DARK = 0xFF2A2316, GOLD_TEXT = 0xFFE38A, SOFT = 0xA89878, FAINT = 0x7A6A50;

    public static void rect(int x, int y, int x2, int y2, int argb) {
        Gui.drawRect(x, y, x2, y2, argb);
    }

    /** Panel pop-in: scale 0.94 -> 1 with a small overshoot and a 10 px rise, around the panel centre. */
    public static void beginOpen(Anim a, int cx, int cy) {
        float t = a.openT(0.28F);
        float s = lerp(0.94F, 1.0F, easeOutBack(t));
        float rise = (1 - easeOutCubic(t)) * 10;
        GlStateManager.pushMatrix();
        GlStateManager.translate(cx, cy + rise, 0);
        GlStateManager.scale(s, s, 1);
        GlStateManager.translate(-cx, -cy, 0);
    }

    public static void endOpen() {
        GlStateManager.popMatrix();
    }

    /** Stone brick panel, darkened, with a gold bevel and corner studs. */
    public static void panel(int x, int y, int w, int h) {
        rect(x - 2, y - 2, x + w + 2, y + h + 2, 0x60000000);
        rect(x, y, x + w, y + h, 0xFF050505);
        Minecraft.getMinecraft().getTextureManager().bindTexture(STONE);
        GlStateManager.color(0.22F, 0.22F, 0.22F, 1.0F);
        Gui.drawModalRectWithCustomSizedTexture(x + 2, y + 2, 0, 0, w - 4, h - 4, 16, 16);
        GlStateManager.color(1, 1, 1, 1);
        rect(x + 1, y + 1, x + w - 1, y + 2, GOLD_EDGE);
        rect(x + 1, y + 1, x + 2, y + h - 1, GOLD_EDGE);
        rect(x + 1, y + h - 2, x + w - 1, y + h - 1, GOLD_DARK);
        rect(x + w - 2, y + 1, x + w - 1, y + h - 1, GOLD_DARK);
        int[][] corners = {{x, y}, {x + w - 5, y}, {x, y + h - 5}, {x + w - 5, y + h - 5}};
        for (int[] c : corners) {
            rect(c[0] + 1, c[1] + 1, c[0] + 4, c[1] + 4, 0xFFC9A14A);
            rect(c[0] + 2, c[1] + 2, c[0] + 3, c[1] + 3, 0xFFFFE38A);
        }
    }

    /** Header title with a gold rule under it. */
    public static void header(FontRenderer f, int x, int y, int w, String title) {
        f.drawStringWithShadow("\u00a7l" + title, x + 12, y + 9, GOLD_TEXT);
        rect(x + 10, y + 21, x + w - 10, y + 22, GOLD_EDGE);
    }

    /** Recessed box: dark fill, black top-left edge, warm bottom-right edge. */
    public static void inset(int x, int y, int w, int h, int fill) {
        rect(x, y, x + w, y + h, fill);
        rect(x, y, x + w, y + 1, 0xFF000000);
        rect(x, y, x + 1, y + h, 0xFF000000);
        rect(x, y + h - 1, x + w, y + h, 0xFF3C3224);
        rect(x + w - 1, y, x + w, y + h, 0xFF3C3224);
    }

    /** Gold-trimmed button; hover is an eased 0..1 value and lifts the label by a pixel. */
    public static void button(FontRenderer f, int x, int y, int w, int h, String label, boolean enabled, float hover, int accent) {
        rect(x, y, x + w, y + h, 0xFF000000);
        rect(x + 1, y + 1, x + w - 1, y + h - 1, enabled ? blend(0xFF4A3C22, 0xFF5E4C2A, hover) : 0xFF1E1B17);
        rect(x + 1, y + 1, x + w - 1, y + 2, enabled ? blend(0xFF7A6436, 0xFF000000 | accent, hover) : 0xFF2A2620);
        rect(x + 1, y + h - 2, x + w - 1, y + h - 1, 0xFF241C10);
        int tw = f.getStringWidth(label);
        f.drawStringWithShadow(label, x + (w - tw) / 2 + 1, y + (h - 8) / 2 - Math.round(hover), enabled ? 0xFFE9B0 : 0x6A6050);
    }

    /** A filled progress bar inside an inset, with a light top line. */
    public static void bar(int x, int y, int w, int h, float fill, int color, int light) {
        inset(x, y, w, h, 0xFF0B0907);
        int fw = Math.round((w - 2) * clamp01(fill));
        if (fw > 0) {
            rect(x + 1, y + 1, x + 1 + fw, y + h - 1, color);
            rect(x + 1, y + 1, x + 1 + fw, y + 2, light);
        }
    }

    public static void item(net.minecraft.client.renderer.RenderItem r, net.minecraft.item.ItemStack s, int x, int y) {
        net.minecraft.client.renderer.RenderHelper.enableGUIStandardItemLighting();
        r.renderItemAndEffectIntoGUI(s, x, y);
        net.minecraft.client.renderer.RenderHelper.disableStandardItemLighting();
        GlStateManager.enableBlend();
    }

    /** Thin line made of 1 px steps (for the build map). */
    public static void line(float x1, float y1, float x2, float y2, int argb) {
        int n = Math.max(1, Math.round(Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1))));
        for (int i = 0; i <= n; i++) {
            int px = Math.round(lerp(x1, x2, i / (float) n)), py = Math.round(lerp(y1, y2, i / (float) n));
            rect(px, py, px + 1, py + 1, argb);
        }
    }

    /** Filled convex polygon (triangle fan) in one colour. */
    public static void poly(float[] xs, float[] ys, int argb) {
        net.minecraft.client.renderer.Tessellator t = net.minecraft.client.renderer.Tessellator.getInstance();
        net.minecraft.client.renderer.BufferBuilder b = t.getBuffer();
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        b.begin(org.lwjgl.opengl.GL11.GL_TRIANGLE_FAN, net.minecraft.client.renderer.vertex.DefaultVertexFormats.POSITION_COLOR);
        int a = argb >>> 24, r = argb >> 16 & 255, g = argb >> 8 & 255, bl = argb & 255;
        for (int i = 0; i < xs.length; i++) {
            b.pos(xs[i], ys[i], 0).color(r, g, bl, a).endVertex();
        }
        t.draw();
        GlStateManager.enableTexture2D();
    }

    /** Closed outline made of pixel lines. */
    public static void outline(float[] xs, float[] ys, int argb) {
        for (int i = 0; i < xs.length; i++) {
            int j = (i + 1) % xs.length;
            line(xs[i], ys[i], xs[j], ys[j], argb);
        }
    }

    /** Cuts text to fit a width and adds ".." (the prefix, e.g. a bold code, counts toward the width). */
    public static String fit(FontRenderer f, String prefix, String s, int px) {
        if (f.getStringWidth(prefix + s) <= px) {
            return prefix + s;
        }
        while (!s.isEmpty() && f.getStringWidth(prefix + s + "..") > px) {
            s = s.substring(0, s.length() - 1);
        }
        return prefix + s.trim() + "..";
    }

    public static void click() {
        Minecraft.getMinecraft().getSoundHandler().playSound(PositionedSoundRecord.getMasterRecord(SoundEvents.UI_BUTTON_CLICK, 1.4F));
    }

    public static boolean in(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
