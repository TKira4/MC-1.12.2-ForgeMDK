package com.imba.undyingarcana;

import net.minecraft.client.particle.Particle;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Tintable particle drawn from the vanilla particle sheet (no new textures):
 * SPARK (firework sparkle), GLOW (end rod), SMOKE, SPELL swirl, FLAME, CRIT, RUNE glyphs, BUBBLE, HEART.
 * Options: full-bright glow, fade-out, shrink / grow, gravity, drag, colour shift towards a second colour.
 */
@SideOnly(Side.CLIENT)
public class FxParticle extends Particle {
    public static final int SPARK = 0, GLOW = 1, SMOKE = 2, SPELL = 3, FLAME = 4, CRIT = 5, RUNE = 6, BUBBLE = 7, HEART = 8, DOT = 9;

    private final int sheet;
    private boolean glow = true;
    private boolean fade = true;
    private float shrink = 1.0F; // scale multiplier reached at end of life
    private float baseScale;
    private float drag = 0.91F;
    private float r2 = -1, g2, b2;
    private float r1, g1, b1;
    private float alpha0 = 1.0F;

    public FxParticle(World w, double x, double y, double z, double vx, double vy, double vz, int sheet) {
        super(w, x, y, z, 0, 0, 0);
        this.motionX = vx;
        this.motionY = vy;
        this.motionZ = vz;
        this.sheet = sheet;
        this.particleGravity = 0.0F;
        this.canCollide = false;
        this.baseScale = 1.0F;
        this.particleScale = 1.0F;
        this.particleMaxAge = 10;
        setFrame(0.0F);
        if (sheet == RUNE) {
            setParticleTextureIndex(208 + rand.nextInt(26));
        }
    }

    public FxParticle color(float[] c) {
        return color(c[0], c[1], c[2]);
    }

    public FxParticle color(float r, float g, float b) {
        this.r1 = r;
        this.g1 = g;
        this.b1 = b;
        setRBGColorF(r, g, b);
        return this;
    }

    /** Colour drifts towards this one over the particle's life. */
    public FxParticle toColor(float[] c) {
        this.r2 = c[0];
        this.g2 = c[1];
        this.b2 = c[2];
        return this;
    }

    public FxParticle scale(float s) {
        this.baseScale = s;
        this.particleScale = s;
        return this;
    }

    public FxParticle life(int ticks) {
        this.particleMaxAge = Math.max(1, ticks);
        return this;
    }

    public FxParticle gravity(float g) {
        this.particleGravity = g;
        return this;
    }

    public FxParticle drag(float d) {
        this.drag = d;
        return this;
    }

    public FxParticle glow(boolean g) {
        this.glow = g;
        return this;
    }

    public FxParticle fade(boolean f) {
        this.fade = f;
        return this;
    }

    public FxParticle shrink(float endScale) {
        this.shrink = endScale;
        return this;
    }

    public FxParticle alpha(float a) {
        this.alpha0 = a;
        setAlphaF(a);
        return this;
    }

    public FxParticle collide(boolean c) {
        this.canCollide = c;
        return this;
    }

    private void setFrame(float t) {
        switch (sheet) {
            case SPARK:
                setParticleTextureIndex(160 + Math.min(7, (int) (t * 8)));
                break;
            case GLOW:
                setParticleTextureIndex(176 + Math.min(7, (int) (t * 8)));
                break;
            case SMOKE:
                setParticleTextureIndex(7 - Math.min(7, (int) (t * 8)));
                break;
            case SPELL:
                setParticleTextureIndex(128 + Math.min(7, (int) (t * 8)));
                break;
            case FLAME:
                setParticleTextureIndex(48);
                break;
            case CRIT:
                setParticleTextureIndex(65);
                break;
            case BUBBLE:
                setParticleTextureIndex(32);
                break;
            case HEART:
                setParticleTextureIndex(80);
                break;
            case DOT:
                setParticleTextureIndex(0);
                break;
            default:
                break;
        }
    }

    @Override
    public void onUpdate() {
        this.prevPosX = this.posX;
        this.prevPosY = this.posY;
        this.prevPosZ = this.posZ;
        if (this.particleAge++ >= this.particleMaxAge) {
            this.setExpired();
            return;
        }
        float t = (float) this.particleAge / (float) this.particleMaxAge;
        setFrame(t);
        this.motionY -= 0.04D * (double) this.particleGravity;
        this.move(this.motionX, this.motionY, this.motionZ);
        this.motionX *= drag;
        this.motionY *= drag;
        this.motionZ *= drag;
        this.particleScale = baseScale * (1.0F + (shrink - 1.0F) * t);
        if (fade) {
            float a = t < 0.6F ? 1.0F : 1.0F - (t - 0.6F) / 0.4F;
            setAlphaF(alpha0 * Math.max(0.0F, a));
        }
        if (r2 >= 0) {
            setRBGColorF(r1 + (r2 - r1) * t, g1 + (g2 - g1) * t, b1 + (b2 - b1) * t);
        }
    }

    @Override
    public int getBrightnessForRender(float partialTicks) {
        return glow ? 0xF000F0 : super.getBrightnessForRender(partialTicks);
    }
}
