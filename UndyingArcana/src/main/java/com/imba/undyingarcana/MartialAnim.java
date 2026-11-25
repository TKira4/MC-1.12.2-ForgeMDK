package com.imba.undyingarcana;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelPlayer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.client.renderer.entity.RenderLivingBase;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.client.renderer.entity.layers.LayerArmorBase;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumHandSide;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.client.event.RenderSpecificHandEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Martial arts body animation (client). Poses are code keyframes on the player model (arms, legs, torso twist and lean,
 * plus a whole-body spin for spinning kicks), eased between keyframes; every pose starts and ends in the guard stance so
 * moves flow back into the guard along the same path they came out.
 *
 * - Guard stance: for 5 s after a martial hit (server COMBAT pose), fists up, lead (left) shoulder forward; legs settle into
 *   a stance only when standing still.
 * - Basic attacks follow the vanilla swing: jabs alternating left / right, or front kicks alternating legs for Iron Leg.
 * - Skills and finishers are explicit poses from the server (Martial.POSE_*).
 *
 * The player renderers' models (and their armor layer models) are swapped once for subclasses that apply the pose after
 * the vanilla angles; held items follow the arms. First person: alternating jabs show the left arm too.
 */
@SideOnly(Side.CLIENT)
public final class MartialAnim {
    // pose vector: body X (lean), body Y (twist), right arm X Y Z, left arm X Y Z, right leg X Y Z, left leg X Y Z
    private static final int N = 14;
    private static final float[] STANCE = {0.05F, -0.25F, -1.10F, -0.20F, 0.15F, -1.35F, 0.55F, -0.10F, 0.15F, 0, 0.06F, -0.18F, 0, -0.06F};

    private static float[] mod(float[] base, float... kv) {
        float[] o = base.clone();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            o[(int) kv[i]] = kv[i + 1];
        }
        return o;
    }

    private static final int BX = 0, BY = 1, RAX = 2, RAY = 3, RAZ = 4, LAX = 5, LAY = 6, LAZ = 7, RLX = 8, RLY = 9, RLZ = 10,
            LLX = 11, LLY = 12, LLZ = 13;

    /** Keyframes: times (0..1) and pose vectors; first and last are the stance. */
    private static final class Clip {
        final float[] t;
        final float[][] k;
        final float spin; // whole-body turns in degrees over the clip (eased)
        boolean legs = true; // false: the legs keep walking / standing (punches)

        Clip(float spin, Object... tk) {
            this.spin = spin;
            t = new float[tk.length / 2];
            k = new float[tk.length / 2][];
            for (int i = 0; i < t.length; i++) {
                t[i] = (Float) tk[i * 2];
                k[i] = (float[]) tk[i * 2 + 1];
            }
        }

        float[] at(float x) {
            x = Math.max(0, Math.min(1, x));
            for (int i = 1; i < t.length; i++) {
                if (x <= t[i]) {
                    float u = (x - t[i - 1]) / Math.max(1e-4F, t[i] - t[i - 1]);
                    u = u * u * (3 - 2 * u); // smoothstep: ease in and out of every key
                    float[] o = new float[N];
                    for (int j = 0; j < N; j++) {
                        o[j] = k[i - 1][j] + (k[i][j] - k[i - 1][j]) * u;
                    }
                    return o;
                }
            }
            return k[k.length - 1].clone();
        }

        float spinAt(float x) {
            if (spin == 0) {
                return 0;
            }
            x = Math.max(0, Math.min(1, (x - 0.1F) / 0.75F));
            return spin * (x * x * (3 - 2 * x));
        }
    }

    private static final Clip JAB_R = new Clip(0, 0F, STANCE,
            0.4F, mod(STANCE, BY, 0.45F, RAX, -1.62F, RAY, -0.05F, RAZ, 0F, LAX, -1.3F, LAY, 0.75F),
            1F, STANCE);
    private static final Clip JAB_L = new Clip(0, 0F, STANCE,
            0.4F, mod(STANCE, BY, -0.6F, LAX, -1.62F, LAY, 0.05F, LAZ, 0F, RAX, -1.15F, RAY, -0.45F),
            1F, STANCE);
    private static final Clip HOOK = new Clip(0, 0F, STANCE,
            0.25F, mod(STANCE, BY, -0.5F, RAX, -1.45F, RAY, 0.7F, RAZ, 0.3F),
            0.55F, mod(STANCE, BY, 0.6F, RAX, -1.5F, RAY, -0.6F, RAZ, 0.1F, LAX, -1.2F),
            0.75F, mod(STANCE, BY, 0.75F, RAX, -1.4F, RAY, -0.9F, RAZ, 0.1F, LAX, -1.1F),
            1F, STANCE);
    private static final Clip UPPERCUT = new Clip(0, 0F, STANCE,
            0.3F, mod(STANCE, BX, 0.25F, BY, -0.3F, RAX, -0.25F, RAY, 0F, RAZ, 0.25F, RLX, 0.25F, LLX, -0.35F),
            0.55F, mod(STANCE, BX, -0.2F, BY, 0.35F, RAX, -2.9F, RAY, -0.2F, RAZ, 0.05F, LAX, -1.2F),
            0.75F, mod(STANCE, BX, -0.15F, BY, 0.3F, RAX, -2.75F, RAY, -0.2F, RAZ, 0.05F, LAX, -1.2F),
            1F, STANCE);
    private static final Clip KICK_R = new Clip(0, 0F, STANCE,
            0.3F, mod(STANCE, BX, -0.12F, RLX, -1.0F, RLZ, 0F, RAX, -0.8F),
            0.5F, mod(STANCE, BX, -0.3F, RLX, -1.65F, RLZ, 0.05F, RAX, -0.4F, RAZ, 0.35F, LAX, -1.2F),
            0.75F, mod(STANCE, BX, -0.15F, RLX, -1.0F, RAX, -0.8F),
            1F, STANCE);
    private static final Clip KICK_L = new Clip(0, 0F, STANCE,
            0.3F, mod(STANCE, BX, -0.12F, LLX, -1.0F, LLZ, 0F, LAX, -0.9F),
            0.5F, mod(STANCE, BX, -0.3F, LLX, -1.65F, LLZ, -0.05F, LAX, -0.45F, LAZ, -0.35F, RAX, -1.2F),
            0.75F, mod(STANCE, BX, -0.15F, LLX, -1.0F, LAX, -0.9F),
            1F, STANCE);
    private static final float[] SPIN_POSE = mod(STANCE, BX, -0.1F, BY, 0F, RAX, -0.2F, RAY, 0F, RAZ, 1.3F, LAX, -0.2F, LAY, 0F,
            LAZ, -1.3F, RLX, -0.3F, RLZ, 1.2F, LLX, 0F, LLZ, -0.05F);
    private static final Clip SPIN = new Clip(-720, 0F, STANCE, 0.15F, SPIN_POSE, 0.85F, SPIN_POSE, 1F, STANCE);
    private static final float[] SWEEP_POSE = mod(STANCE, BX, 0.4F, BY, 0F, RAX, -0.4F, RAY, 0F, RAZ, 0.6F, LAX, -0.4F, LAY, 0F,
            LAZ, -0.6F, RLX, -1.4F, RLZ, 0F, LLX, 0.2F);
    private static final Clip SWEEP = new Clip(-360, 0F, STANCE, 0.15F, SWEEP_POSE, 0.8F, SWEEP_POSE, 1F, STANCE);
    private static final Clip SKYDROP = new Clip(0, 0F, STANCE,
            0.1F, mod(STANCE, BX, 0F, BY, 0F, RAX, -2.8F, RAY, 0F, RAZ, 0.3F, LAX, -2.8F, LAY, 0F, LAZ, -0.3F, RLX, 0.3F, LLX, 0.3F),
            0.55F, mod(STANCE, BX, 0.3F, BY, 0F, RAX, -1.5F, RAY, 0F, RAZ, 0.2F, LAX, -1.5F, LAY, 0F, LAZ, -0.2F, RLX, -1.3F, LLX, -1.3F),
            0.65F, mod(STANCE, BX, 0F, BY, 0F, RAX, -2.6F, RAY, 0F, RAZ, 0.2F, LAX, -2.6F, LAY, 0F, LAZ, -0.2F, RLX, 0F, LLX, 0.3F),
            0.85F, mod(STANCE, BX, 0.45F, BY, 0F, RAX, -0.5F, RAY, 0F, RAZ, 0.9F, LAX, -0.5F, LAY, 0F, LAZ, -0.9F, RLX, -0.4F, LLX, 0.4F),
            1F, STANCE);

    static {
        JAB_R.legs = false;
        JAB_L.legs = false;
        HOOK.legs = false;
    }

    private static Clip clip(int pose) {
        switch (pose) {
            case Martial.POSE_UPPERCUT: return UPPERCUT;
            case Martial.POSE_HOOK: return HOOK;
            case Martial.POSE_JAB_L: return JAB_L;
            case Martial.POSE_JAB_R: return JAB_R;
            case Martial.POSE_KICK_R: return KICK_R;
            case Martial.POSE_KICK_L: return KICK_L;
            case Martial.POSE_SPIN: return SPIN;
            case Martial.POSE_SWEEP: return SWEEP;
            case Martial.POSE_SKYDROP: return SKYDROP;
            default: return null;
        }
    }

    // ------------------------------------------------------------------ per-player state
    private static final class St {
        int style = -1;
        float combatUntil;     // age (ticks) until the guard stance relaxes
        int pose = -1;
        float start, dur;
        float lastSwing;
        boolean left;          // side of the current basic attack
    }

    private static final Map<Integer, St> STATE = new HashMap<>();
    private static final float GUARD_TICKS = 100, FADE = 10;

    private static float age(Entity e, float partial) {
        return e.ticksExisted + partial;
    }

    public static void receive(SkillNet.MsgPose m) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null) {
            return;
        }
        Entity e = mc.world.getEntityByID(m.entity);
        if (!(e instanceof EntityPlayer)) {
            return;
        }
        if (STATE.size() > 64) {
            STATE.clear();
        }
        St s = STATE.computeIfAbsent(m.entity, k -> new St());
        float now = age(e, mc.getRenderPartialTicks());
        s.style = m.style;
        s.combatUntil = now + GUARD_TICKS;
        if (m.pose != Martial.POSE_COMBAT && clip(m.pose) != null) {
            s.pose = m.pose;
            s.start = now;
            s.dur = Math.max(2, m.dur);
        }
    }

    /** Rising edge of a new swing flips the side (basic attacks alternate). */
    private static void trackSwing(St s, float sp) {
        if (sp > 0 && (s.lastSwing <= 0 || sp < s.lastSwing - 0.02F)) {
            s.left = !s.left;
        }
        s.lastSwing = sp;
    }

    private static boolean animates(Entity e) {
        return e instanceof EntityPlayer && !e.isRiding() && !((EntityPlayer) e).isPlayerSleeping() && !((EntityPlayer) e).isElytraFlying()
                && Martial.martialHands((EntityPlayer) e) && !((EntityPlayer) e).isHandActive();
    }

    /** The active clip and its time for an entity, or null. */
    private static Object[] current(St s, Entity e, float age, float swing) {
        if (s.pose >= 0) {
            float t = (age - s.start) / s.dur;
            if (t >= 0 && t < 1) {
                return new Object[]{clip(s.pose), t};
            }
            s.pose = -1;
        }
        if (swing > 0) {
            boolean kick = s.style == Martial.IRONLEG;
            Clip c = kick ? s.left ? KICK_L : KICK_R : s.left ? JAB_L : JAB_R;
            return new Object[]{c, swing};
        }
        return null;
    }

    /** Applies the martial pose on top of the vanilla angles (called from the swapped models). */
    static void apply(ModelBiped m, Entity e, float age, float limbAmount) {
        if (!(e instanceof EntityPlayer)) {
            return;
        }
        St s = STATE.get(e.getEntityId());
        if (s == null || s.style < 0 || !animates(e)) {
            return;
        }
        trackSwing(s, m.swingProgress);
        Object[] cur = current(s, e, age, m.swingProgress);
        float guard = Math.max(0, Math.min(1, (s.combatUntil - age) / FADE));
        if (cur == null && guard <= 0) {
            return;
        }
        float[] v = vanilla(m);
        float[] target;
        if (cur != null) {
            target = ((Clip) cur[0]).at((Float) cur[1]);
        } else {
            target = STANCE;
        }
        float wArms = cur != null ? 1 : guard;
        // legs settle into the stance only when standing still; kicks drive them fully
        float walk = Math.min(1, limbAmount * 3);
        float wLegs = cur != null && ((Clip) cur[0]).legs ? 1 : Math.max(guard, cur != null ? 1 : 0) * (1 - walk);
        float[] o = new float[N];
        for (int i = 0; i < N; i++) {
            float w = i >= RLX ? wLegs : wArms;
            o[i] = v[i] + (target[i] - v[i]) * w;
        }
        write(m, o, m.isSneak);
    }

    private static float[] vanilla(ModelBiped m) {
        return new float[]{m.bipedBody.rotateAngleX, m.bipedBody.rotateAngleY, m.bipedRightArm.rotateAngleX, m.bipedRightArm.rotateAngleY - m.bipedBody.rotateAngleY,
                m.bipedRightArm.rotateAngleZ, m.bipedLeftArm.rotateAngleX, m.bipedLeftArm.rotateAngleY - m.bipedBody.rotateAngleY, m.bipedLeftArm.rotateAngleZ,
                m.bipedRightLeg.rotateAngleX, m.bipedRightLeg.rotateAngleY, m.bipedRightLeg.rotateAngleZ, m.bipedLeftLeg.rotateAngleX,
                m.bipedLeftLeg.rotateAngleY, m.bipedLeftLeg.rotateAngleZ};
    }

    private static void write(ModelBiped m, float[] o, boolean sneak) {
        float b = o[BY];
        m.bipedBody.rotateAngleX = sneak ? Math.max(o[BX], 0.5F) : o[BX];
        m.bipedBody.rotateAngleY = b;
        // the shoulders turn with the torso (same as the vanilla swing)
        m.bipedRightArm.rotationPointZ = (float) Math.sin(b) * 5F;
        m.bipedRightArm.rotationPointX = (float) -Math.cos(b) * 5F;
        m.bipedLeftArm.rotationPointZ = (float) -Math.sin(b) * 5F;
        m.bipedLeftArm.rotationPointX = (float) Math.cos(b) * 5F;
        m.bipedRightArm.rotateAngleX = o[RAX];
        m.bipedRightArm.rotateAngleY = o[RAY] + b;
        m.bipedRightArm.rotateAngleZ = o[RAZ];
        m.bipedLeftArm.rotateAngleX = o[LAX];
        m.bipedLeftArm.rotateAngleY = o[LAY] + b;
        m.bipedLeftArm.rotateAngleZ = o[LAZ];
        m.bipedRightLeg.rotateAngleX = o[RLX];
        m.bipedRightLeg.rotateAngleY = o[RLY];
        m.bipedRightLeg.rotateAngleZ = o[RLZ];
        m.bipedLeftLeg.rotateAngleX = o[LLX];
        m.bipedLeftLeg.rotateAngleY = o[LLY];
        m.bipedLeftLeg.rotateAngleZ = o[LLZ];
        if (m instanceof ModelPlayer) {
            ModelPlayer p = (ModelPlayer) m;
            ModelBase.copyModelAngles(m.bipedLeftLeg, p.bipedLeftLegwear);
            ModelBase.copyModelAngles(m.bipedRightLeg, p.bipedRightLegwear);
            ModelBase.copyModelAngles(m.bipedLeftArm, p.bipedLeftArmwear);
            ModelBase.copyModelAngles(m.bipedRightArm, p.bipedRightArmwear);
            ModelBase.copyModelAngles(m.bipedBody, p.bipedBodyWear);
        }
    }

    // ------------------------------------------------------------------ models
    static final class PlayerModel extends ModelPlayer {
        PlayerModel(float size, boolean slim) {
            super(size, slim);
        }

        @Override
        public void setRotationAngles(float limbSwing, float limbAmount, float age, float yaw, float pitch, float scale, Entity e) {
            super.setRotationAngles(limbSwing, limbAmount, age, yaw, pitch, scale, e);
            apply(this, e, age, limbAmount);
        }
    }

    static final class ArmorModel extends ModelBiped {
        ArmorModel(float size) {
            super(size);
        }

        @Override
        public void setRotationAngles(float limbSwing, float limbAmount, float age, float yaw, float pitch, float scale, Entity e) {
            super.setRotationAngles(limbSwing, limbAmount, age, yaw, pitch, scale, e);
            apply(this, e, age, limbAmount);
        }
    }

    private static final java.util.Set<RenderPlayer> SWAPPED = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());
    private static boolean failed;

    /** Swap the renderer's player model and armor layer models for the posing subclasses (once per renderer). */
    private static void swap(RenderPlayer r) {
        if (failed || SWAPPED.contains(r)) {
            return;
        }
        SWAPPED.add(r);
        try {
            if (r.getMainModel().getClass() != ModelPlayer.class) {
                return; // another mod replaced it: leave it alone
            }
            boolean slim = r == Minecraft.getMinecraft().getRenderManager().getSkinMap().get("slim");
            PlayerModel model = new PlayerModel(0.0F, slim);
            List<?> layers = null;
            for (Field f : RenderLivingBase.class.getDeclaredFields()) {
                f.setAccessible(true);
                if (f.getType() == ModelBase.class) {
                    f.set(r, model);
                } else if (f.getType() == List.class) {
                    layers = (List<?>) f.get(r);
                }
            }
            for (Object layer : layers == null ? java.util.Collections.emptyList() : layers) {
                if (layer instanceof LayerArmorBase) {
                    int n = 0;
                    for (Field lf : LayerArmorBase.class.getDeclaredFields()) {
                        if (lf.getType() == ModelBase.class) {
                            lf.setAccessible(true);
                            Object old = lf.get(layer);
                            if (old != null && old.getClass() == ModelBiped.class) {
                                lf.set(layer, new ArmorModel(n == 0 ? 0.5F : 1.0F)); // leggings, then armor
                            }
                            n++;
                        }
                    }
                } else if (layer instanceof net.minecraft.client.renderer.entity.layers.LayerCustomHead) {
                    // skulls / pumpkins hold the head part of the model they were built with: point them at the new head
                    for (Field lf : net.minecraft.client.renderer.entity.layers.LayerCustomHead.class.getDeclaredFields()) {
                        if (lf.getType() == net.minecraft.client.model.ModelRenderer.class) {
                            lf.setAccessible(true);
                            lf.set(layer, model.bipedHead);
                        }
                    }
                }
            }
        } catch (ReflectiveOperationException | RuntimeException ex) {
            failed = true;
            org.apache.logging.log4j.LogManager.getLogger("undyingarcana").warn("Martial animation disabled: could not swap the player model", ex);
        }
    }

    // ------------------------------------------------------------------ events
    private static final java.util.Set<Integer> PUSHED = new java.util.HashSet<>();

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onRenderPre(RenderPlayerEvent.Pre e) {
        swap(e.getRenderer());
        EntityPlayer p = e.getEntityPlayer();
        St s = STATE.get(p.getEntityId());
        float spin = 0;
        if (s != null && s.pose >= 0 && animates(p)) {
            float t = (age(p, e.getPartialRenderTick()) - s.start) / s.dur;
            Clip c = clip(s.pose);
            if (c != null && t >= 0 && t < 1) {
                spin = c.spinAt(t);
            }
        }
        if (spin != 0) {
            GlStateManager.pushMatrix();
            PUSHED.add(p.getEntityId());
            GlStateManager.translate(e.getX(), e.getY(), e.getZ());
            GlStateManager.rotate(spin, 0, 1, 0);
            GlStateManager.translate(-e.getX(), -e.getY(), -e.getZ());
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onRenderPost(RenderPlayerEvent.Post e) {
        if (PUSHED.remove(e.getEntityPlayer().getEntityId())) {
            GlStateManager.popMatrix();
        }
    }

    private static Method armFirstPerson;
    private static boolean armLookup;

    private static Method armMethod() {
        if (!armLookup) {
            armLookup = true;
            for (Method m : ItemRenderer.class.getDeclaredMethods()) {
                Class<?>[] t = m.getParameterTypes();
                if (m.getReturnType() == void.class && t.length == 3 && t[0] == float.class && t[1] == float.class && t[2] == EnumHandSide.class) {
                    m.setAccessible(true);
                    armFirstPerson = m;
                }
            }
        }
        return armFirstPerson;
    }

    /** First person: alternating jabs and the guard show both fists (vanilla shows the right arm only). */
    @SubscribeEvent
    public void onHand(RenderSpecificHandEvent e) {
        Minecraft mc = Minecraft.getMinecraft();
        AbstractClientPlayer p = mc.player;
        if (e.getHand() != EnumHand.MAIN_HAND || !e.getItemStack().isEmpty() || p == null || !p.getHeldItemOffhand().isEmpty()) {
            return;
        }
        St s = STATE.get(p.getEntityId());
        if (s == null || s.style < 0 || !animates(p) || armMethod() == null) {
            return;
        }
        float age = age(p, e.getPartialTicks());
        float sp = e.getSwingProgress();
        trackSwing(s, sp);
        boolean guard = s.combatUntil > age;
        float right = sp, left = 0;
        if (s.pose >= 0 && (age - s.start) / s.dur < 1) {
            float t = (age - s.start) / s.dur;
            if (s.pose == Martial.POSE_JAB_L) {
                left = t;
                right = 0;
            } else if (s.pose == Martial.POSE_JAB_R || s.pose == Martial.POSE_HOOK || s.pose == Martial.POSE_UPPERCUT) {
                right = t;
            }
        } else if (sp > 0 && s.style != Martial.IRONLEG && s.left) {
            left = sp;
            right = 0;
        }
        if (!guard && left == 0) {
            return; // vanilla draws the right arm as usual
        }
        e.setCanceled(true);
        EnumHandSide main = p.getPrimaryHand();
        try {
            GlStateManager.pushMatrix();
            armFirstPerson.invoke(mc.getItemRenderer(), e.getEquipProgress(), right, main);
            GlStateManager.popMatrix();
            GlStateManager.pushMatrix();
            armFirstPerson.invoke(mc.getItemRenderer(), e.getEquipProgress(), left, main.opposite());
            GlStateManager.popMatrix();
        } catch (ReflectiveOperationException ex) {
            GlStateManager.popMatrix();
            armFirstPerson = null;
        }
    }
}
