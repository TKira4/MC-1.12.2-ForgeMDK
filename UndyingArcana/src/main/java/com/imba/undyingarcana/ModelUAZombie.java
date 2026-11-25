package com.imba.undyingarcana;

import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Zombie models: biped base + extra parts per type, a static pose per type on top of the walk cycle.
 * GENERATED from zombie_design.py - texture offsets match the 128x64 textures in textures/entity/zombie.
 */
@SideOnly(Side.CLIENT)
public class ModelUAZombie extends ModelBiped {
    /** head xyz, right arm xyz, left arm xyz, right leg xyz, left leg xyz (radians), by ZType ordinal. */
    private static final float[][] POSE = {
            {0.1396F, -0.2094F, 0.1047F, -1.3963F, -0.1047F, 0.0F, -1.6581F, 0.1396F, 0.0F, 0.2443F, 0.0F, 0.0F, -0.1745F, 0.0F, 0.0F},
            {-0.1745F, 0.0F, 0.0F, 0.8727F, 0.0F, 0.1745F, -1.0472F, 0.0F, -0.1745F, -0.7854F, 0.0F, 0.0F, 0.6109F, 0.0F, 0.0F},
            {-1.309F, 0.0F, 0.0F, -1.9199F, 0.0F, 0.2618F, -1.0472F, 0.0F, -0.2618F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F},
            {0.3491F, 0.0F, 0.0F, -0.4363F, 0.0F, 0.2094F, -0.2618F, 0.0F, -0.2094F, 0.1047F, 0.0F, 0.0698F, -0.1047F, 0.0F, -0.0698F},
            {-0.4363F, 0.0F, 0.0F, -1.8326F, 0.0F, 0.9599F, -1.8326F, 0.0F, -0.9599F, 0.0F, 0.0F, 0.0873F, 0.0F, 0.0F, -0.0873F},
            {-0.2618F, 0.0F, 0.0F, -0.6981F, 0.0F, 0.1745F, -0.3491F, 0.0F, -0.2618F, 0.1396F, 0.0F, 0.0F, -0.1396F, 0.0F, 0.0F},
            {0.1745F, 0.0F, 0.0F, -0.5236F, 0.0F, 0.6109F, -0.5236F, 0.0F, -0.6109F, 0.0F, 0.0F, 0.1745F, 0.0F, 0.0F, -0.1745F},
            {0.0873F, 0.0F, 0.0F, -1.4835F, -0.0873F, 0.0F, -1.2217F, 0.5236F, -0.1745F, 0.1745F, 0.0F, 0.0F, -0.1745F, 0.0F, 0.0F},
            {-0.0873F, 0.0F, 0.0F, -1.3963F, 0.0F, 0.0F, -2.4435F, 0.0F, -0.3491F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F}
    };
    private static final float[] TILT = {0.0F, 0.0F, 1.3963F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F};
    private final int type;

    public ModelUAZombie(int type) {
        super(0.0F, 0.0F, 128, 64);
        this.type = type;
        bipedHeadwear.showModel = false;
        if (0 == type) {
            ModelRenderer r1 = new ModelRenderer(this, 84, 0);
            r1.setRotationPoint(0.0F, 0.0F, 0.0F);
            r1.addBox(-4.3F, -8.4F, -1.5F, 9, 2, 6, 0.0F);
            bipedHead.addChild(r1);
            ModelRenderer r2 = new ModelRenderer(this, 92, 9);
            r2.setRotationPoint(0.0F, 0.0F, 0.0F);
            r2.addBox(-0.5F, 3.0F, -2.5F, 4, 5, 1, 0.0F);
            bipedBody.addChild(r2);
            ModelRenderer r3 = new ModelRenderer(this, 64, 9);
            r3.setRotationPoint(0.0F, 0.0F, 0.0F);
            r3.addBox(-4.4F, 10.0F, -2.4F, 9, 3, 5, 0.0F);
            bipedBody.addChild(r3);
            ModelRenderer r4 = new ModelRenderer(this, 64, 0);
            r4.setRotationPoint(0.0F, 0.0F, 0.0F);
            r4.addBox(-3.4F, -2.4F, -2.4F, 5, 4, 5, 0.0F);
            bipedRightArm.addChild(r4);
        }
        if (1 == type) {
            ModelRenderer r1 = new ModelRenderer(this, 82, 0);
            r1.setRotationPoint(0.0F, 0.0F, 0.0F);
            r1.addBox(-4.5F, -8.6F, 3.6F, 9, 9, 1, 0.0F);
            bipedHead.addChild(r1);
            ModelRenderer r2 = new ModelRenderer(this, 64, 15);
            r2.setRotationPoint(0.0F, 0.0F, 0.0F);
            r2.addBox(-4.5F, -8.8F, -4.6F, 9, 1, 9, 0.0F);
            bipedHead.addChild(r2);
            ModelRenderer r3 = new ModelRenderer(this, 64, 0);
            r3.setRotationPoint(0.0F, 0.0F, 0.0F);
            r3.addBox(-4.6F, -8.6F, -4.6F, 1, 7, 8, 0.0F);
            bipedHead.addChild(r3);
            ModelRenderer r4 = new ModelRenderer(this, 64, 0);
            r4.mirror = true;
            r4.setRotationPoint(0.0F, 0.0F, 0.0F);
            r4.addBox(3.6F, -8.6F, -4.6F, 1, 7, 8, 0.0F);
            bipedHead.addChild(r4);
            ModelRenderer r5 = new ModelRenderer(this, 100, 15);
            r5.setRotationPoint(0.0F, 0.0F, 0.0F);
            r5.addBox(-2.5F, 10.0F, -2.2F, 1, 3, 1, 0.0F);
            bipedRightArm.addChild(r5);
            ModelRenderer r6 = new ModelRenderer(this, 100, 15);
            r6.setRotationPoint(0.0F, 0.0F, 0.0F);
            r6.addBox(-0.5F, 10.0F, -2.2F, 1, 3, 1, 0.0F);
            bipedRightArm.addChild(r6);
            ModelRenderer r7 = new ModelRenderer(this, 100, 15);
            r7.setRotationPoint(0.0F, 0.0F, 0.0F);
            r7.addBox(-0.5F, 10.0F, -2.2F, 1, 3, 1, 0.0F);
            bipedLeftArm.addChild(r7);
            ModelRenderer r8 = new ModelRenderer(this, 100, 15);
            r8.setRotationPoint(0.0F, 0.0F, 0.0F);
            r8.addBox(1.5F, 10.0F, -2.2F, 1, 3, 1, 0.0F);
            bipedLeftArm.addChild(r8);
        }
        if (2 == type) {
            ModelRenderer r1 = new ModelRenderer(this, 110, 0);
            r1.setRotationPoint(0.0F, 1.5F, 2.0F);
            r1.addBox(-0.5F, -3.0F, -0.5F, 1, 3, 1, 0.0F);
            r1.rotateAngleX = -0.6109F;
            r1.rotateAngleY = 0.0F;
            r1.rotateAngleZ = 0.0F;
            bipedBody.addChild(r1);
            ModelRenderer r2 = new ModelRenderer(this, 110, 0);
            r2.setRotationPoint(0.0F, 4.5F, 2.0F);
            r2.addBox(-0.5F, -3.0F, -0.5F, 1, 3, 1, 0.0F);
            r2.rotateAngleX = -0.6109F;
            r2.rotateAngleY = 0.0F;
            r2.rotateAngleZ = 0.0F;
            bipedBody.addChild(r2);
            ModelRenderer r3 = new ModelRenderer(this, 110, 0);
            r3.setRotationPoint(0.0F, 7.5F, 2.0F);
            r3.addBox(-0.5F, -3.0F, -0.5F, 1, 3, 1, 0.0F);
            r3.rotateAngleX = -0.6109F;
            r3.rotateAngleY = 0.0F;
            r3.rotateAngleZ = 0.0F;
            bipedBody.addChild(r3);
            ModelRenderer r4 = new ModelRenderer(this, 110, 0);
            r4.setRotationPoint(0.0F, 10.5F, 2.0F);
            r4.addBox(-0.5F, -3.0F, -0.5F, 1, 3, 1, 0.0F);
            r4.rotateAngleX = -0.6109F;
            r4.rotateAngleY = 0.0F;
            r4.rotateAngleZ = 0.0F;
            bipedBody.addChild(r4);
            ModelRenderer r5 = new ModelRenderer(this, 86, 0);
            r5.setRotationPoint(0.0F, 0.0F, 0.0F);
            r5.addBox(-3.5F, 11.5F, -1.5F, 7, 2, 3, 0.0F);
            bipedBody.addChild(r5);
            ModelRenderer r6 = new ModelRenderer(this, 114, 0);
            r6.setRotationPoint(0.0F, 0.0F, 0.0F);
            r6.addBox(-1.5F, 13.0F, -1.0F, 3, 2, 2, 0.0F);
            bipedBody.addChild(r6);
            ModelRenderer r7 = new ModelRenderer(this, 106, 0);
            r7.setRotationPoint(0.0F, 0.0F, 0.0F);
            r7.addBox(-3.0F, 10.0F, -2.4F, 1, 4, 1, 0.0F);
            bipedRightArm.addChild(r7);
            ModelRenderer r8 = new ModelRenderer(this, 106, 0);
            r8.setRotationPoint(0.0F, 0.0F, 0.0F);
            r8.addBox(-1.5F, 10.0F, -2.4F, 1, 4, 1, 0.0F);
            bipedRightArm.addChild(r8);
            ModelRenderer r9 = new ModelRenderer(this, 106, 0);
            r9.setRotationPoint(0.0F, 0.0F, 0.0F);
            r9.addBox(0.0F, 10.0F, -2.4F, 1, 4, 1, 0.0F);
            bipedRightArm.addChild(r9);
            ModelRenderer r10 = new ModelRenderer(this, 106, 0);
            r10.setRotationPoint(0.0F, 0.0F, 0.0F);
            r10.addBox(-1.0F, 10.0F, -2.4F, 1, 4, 1, 0.0F);
            bipedLeftArm.addChild(r10);
            ModelRenderer r11 = new ModelRenderer(this, 106, 0);
            r11.setRotationPoint(0.0F, 0.0F, 0.0F);
            r11.addBox(0.5F, 10.0F, -2.4F, 1, 4, 1, 0.0F);
            bipedLeftArm.addChild(r11);
            ModelRenderer r12 = new ModelRenderer(this, 106, 0);
            r12.setRotationPoint(0.0F, 0.0F, 0.0F);
            r12.addBox(2.0F, 10.0F, -2.4F, 1, 4, 1, 0.0F);
            bipedLeftArm.addChild(r12);
            ModelRenderer r13 = new ModelRenderer(this, 64, 0);
            r13.setRotationPoint(0.0F, -1.0F, -3.5F);
            r13.addBox(-3.0F, 0.0F, -1.0F, 6, 2, 5, 0.0F);
            r13.rotateAngleX = 0.5236F;
            r13.rotateAngleY = 0.0F;
            r13.rotateAngleZ = 0.0F;
            bipedHead.addChild(r13);
            bipedRightLeg.showModel = false;
            bipedLeftLeg.showModel = false;
        }
        if (3 == type) {
            ModelRenderer r1 = new ModelRenderer(this, 88, 0);
            r1.setRotationPoint(0.0F, 0.0F, 0.0F);
            r1.addBox(-5.0F, -1.5F, 0.5F, 10, 7, 4, 0.0F);
            r1.rotateAngleX = 0.1745F;
            r1.rotateAngleY = 0.0F;
            r1.rotateAngleZ = 0.0F;
            bipedBody.addChild(r1);
            ModelRenderer r2 = new ModelRenderer(this, 64, 12);
            r2.setRotationPoint(0.0F, 0.0F, 0.0F);
            r2.addBox(-4.5F, -3.5F, -3.0F, 6, 5, 6, 0.0F);
            bipedRightArm.addChild(r2);
            ModelRenderer r3 = new ModelRenderer(this, 64, 12);
            r3.mirror = true;
            r3.setRotationPoint(0.0F, 0.0F, 0.0F);
            r3.addBox(-1.5F, -3.5F, -3.0F, 6, 5, 6, 0.0F);
            bipedLeftArm.addChild(r3);
            ModelRenderer r4 = new ModelRenderer(this, 64, 0);
            r4.setRotationPoint(0.0F, 0.0F, 0.0F);
            r4.addBox(-4.0F, 7.0F, -3.0F, 6, 6, 6, 0.0F);
            bipedRightArm.addChild(r4);
            ModelRenderer r5 = new ModelRenderer(this, 64, 0);
            r5.mirror = true;
            r5.setRotationPoint(0.0F, 0.0F, 0.0F);
            r5.addBox(-2.0F, 7.0F, -3.0F, 6, 6, 6, 0.0F);
            bipedLeftArm.addChild(r5);
            ModelRenderer r6 = new ModelRenderer(this, 88, 12);
            r6.setRotationPoint(0.0F, 0.0F, 0.0F);
            r6.addBox(-4.5F, 9.5F, -3.5F, 7, 4, 7, 0.0F);
            bipedRightArm.addChild(r6);
            ModelRenderer r7 = new ModelRenderer(this, 116, 12);
            r7.setRotationPoint(0.0F, 0.0F, 0.0F);
            r7.addBox(-4.5F, 1.0F, -2.8F, 5, 6, 1, 0.0F);
            r7.rotateAngleX = 0.0F;
            r7.rotateAngleY = 0.0F;
            r7.rotateAngleZ = -0.1396F;
            bipedBody.addChild(r7);
            ModelRenderer r8 = new ModelRenderer(this, 64, 23);
            r8.setRotationPoint(0.0F, 0.0F, 0.0F);
            r8.addBox(0.5F, 4.0F, -2.8F, 4, 5, 1, 0.0F);
            r8.rotateAngleX = 0.0F;
            r8.rotateAngleY = 0.0F;
            r8.rotateAngleZ = 0.1745F;
            bipedBody.addChild(r8);
            ModelRenderer r9 = new ModelRenderer(this, 74, 23);
            r9.setRotationPoint(0.0F, 0.0F, 0.0F);
            r9.addBox(-3.4F, 3.0F, -2.4F, 5, 1, 5, 0.0F);
            bipedRightArm.addChild(r9);
            ModelRenderer r10 = new ModelRenderer(this, 74, 23);
            r10.setRotationPoint(0.0F, 0.0F, 0.0F);
            r10.addBox(-3.4F, 5.0F, -2.4F, 5, 1, 5, 0.0F);
            bipedRightArm.addChild(r10);
            ModelRenderer r11 = new ModelRenderer(this, 94, 23);
            r11.setRotationPoint(-3.0F, -3.5F, 0.0F);
            r11.addBox(-0.5F, -4.0F, -0.5F, 1, 4, 1, 0.0F);
            r11.rotateAngleX = 0.0F;
            r11.rotateAngleY = 0.0F;
            r11.rotateAngleZ = -0.3491F;
            bipedRightArm.addChild(r11);
            ModelRenderer r12 = new ModelRenderer(this, 94, 23);
            r12.setRotationPoint(-1.5F, -3.5F, 1.5F);
            r12.addBox(-0.5F, -4.0F, -0.5F, 1, 4, 1, 0.0F);
            r12.rotateAngleX = 0.2618F;
            r12.rotateAngleY = 0.0F;
            r12.rotateAngleZ = -0.1745F;
            bipedRightArm.addChild(r12);
        }
        if (4 == type) {
            ModelRenderer r1 = new ModelRenderer(this, 92, 0);
            r1.setRotationPoint(0.0F, 0.0F, -1.0F);
            r1.addBox(-3.0F, -0.5F, -3.2F, 6, 4, 5, 0.0F);
            r1.rotateAngleX = 0.6109F;
            r1.rotateAngleY = 0.0F;
            r1.rotateAngleZ = 0.0F;
            bipedHead.addChild(r1);
            ModelRenderer r2 = new ModelRenderer(this, 64, 0);
            r2.setRotationPoint(0.0F, 0.0F, 0.0F);
            r2.addBox(-3.5F, -11.0F, -2.0F, 7, 3, 7, 0.0F);
            bipedHead.addChild(r2);
            ModelRenderer r3 = new ModelRenderer(this, 64, 10);
            r3.setRotationPoint(0.0F, 0.0F, 0.0F);
            r3.addBox(-2.5F, -13.0F, -1.0F, 5, 2, 5, 0.0F);
            bipedHead.addChild(r3);
            ModelRenderer r4 = new ModelRenderer(this, 88, 10);
            r4.setRotationPoint(0.0F, 0.0F, 0.0F);
            r4.addBox(-2.0F, -1.5F, -2.3F, 1, 3, 1, 0.0F);
            bipedBody.addChild(r4);
            ModelRenderer r5 = new ModelRenderer(this, 88, 10);
            r5.setRotationPoint(0.0F, 0.0F, 0.0F);
            r5.addBox(1.0F, -1.5F, -2.3F, 1, 3, 1, 0.0F);
            bipedBody.addChild(r5);
            ModelRenderer r6 = new ModelRenderer(this, 84, 10);
            r6.setRotationPoint(0.0F, 0.0F, 0.0F);
            r6.addBox(-2.5F, 10.0F, -2.2F, 1, 4, 1, 0.0F);
            bipedRightArm.addChild(r6);
            ModelRenderer r7 = new ModelRenderer(this, 84, 10);
            r7.setRotationPoint(0.0F, 0.0F, 0.0F);
            r7.addBox(-0.5F, 10.0F, -2.2F, 1, 4, 1, 0.0F);
            bipedRightArm.addChild(r7);
            ModelRenderer r8 = new ModelRenderer(this, 84, 10);
            r8.setRotationPoint(0.0F, 0.0F, 0.0F);
            r8.addBox(-0.5F, 10.0F, -2.2F, 1, 4, 1, 0.0F);
            bipedLeftArm.addChild(r8);
            ModelRenderer r9 = new ModelRenderer(this, 84, 10);
            r9.setRotationPoint(0.0F, 0.0F, 0.0F);
            r9.addBox(1.5F, 10.0F, -2.2F, 1, 4, 1, 0.0F);
            bipedLeftArm.addChild(r9);
        }
        if (5 == type) {
            ModelRenderer r1 = new ModelRenderer(this, 64, 0);
            r1.setRotationPoint(0.0F, 0.0F, 0.0F);
            r1.addBox(-3.5F, -2.0F, -5.0F, 7, 4, 5, 0.0F);
            bipedHead.addChild(r1);
            ModelRenderer r2 = new ModelRenderer(this, 88, 0);
            r2.setRotationPoint(0.0F, 0.0F, 0.0F);
            r2.addBox(-5.0F, -4.0F, -3.0F, 2, 3, 3, 0.0F);
            bipedHead.addChild(r2);
            ModelRenderer r3 = new ModelRenderer(this, 88, 0);
            r3.mirror = true;
            r3.setRotationPoint(0.0F, 0.0F, 0.0F);
            r3.addBox(3.0F, -4.0F, -3.0F, 2, 3, 3, 0.0F);
            bipedHead.addChild(r3);
            ModelRenderer r4 = new ModelRenderer(this, 108, 0);
            r4.setRotationPoint(0.0F, 0.0F, 0.0F);
            r4.addBox(-1.5F, 1.5F, -4.8F, 1, 3, 1, 0.0F);
            bipedHead.addChild(r4);
            ModelRenderer r5 = new ModelRenderer(this, 112, 0);
            r5.setRotationPoint(0.0F, 0.0F, 0.0F);
            r5.addBox(1.0F, 1.5F, -4.2F, 1, 2, 1, 0.0F);
            bipedHead.addChild(r5);
            ModelRenderer r6 = new ModelRenderer(this, 98, 0);
            r6.setRotationPoint(0.0F, 0.0F, 0.0F);
            r6.addBox(-3.5F, 1.0F, 1.5F, 3, 3, 2, 0.0F);
            bipedBody.addChild(r6);
            ModelRenderer r7 = new ModelRenderer(this, 98, 0);
            r7.setRotationPoint(0.0F, 0.0F, 0.0F);
            r7.addBox(0.5F, 3.5F, 1.5F, 3, 3, 2, 0.0F);
            bipedBody.addChild(r7);
            ModelRenderer r8 = new ModelRenderer(this, 98, 0);
            r8.setRotationPoint(0.0F, 0.0F, 0.0F);
            r8.addBox(-2.0F, 6.5F, 1.5F, 3, 3, 2, 0.0F);
            bipedBody.addChild(r8);
            ModelRenderer r9 = new ModelRenderer(this, 108, 0);
            r9.setRotationPoint(0.0F, 0.0F, 0.0F);
            r9.addBox(-3.0F, 4.0F, -2.4F, 1, 3, 1, 0.0F);
            bipedBody.addChild(r9);
        }
        if (6 == type) {
            ModelRenderer r1 = new ModelRenderer(this, 64, 0);
            r1.setRotationPoint(0.0F, 0.0F, 0.0F);
            r1.addBox(-5.5F, 2.0F, -6.5F, 11, 10, 9, 0.0F);
            bipedBody.addChild(r1);
            ModelRenderer r2 = new ModelRenderer(this, 100, 19);
            r2.setRotationPoint(0.0F, 0.0F, 0.0F);
            r2.addBox(-4.5F, 3.0F, -7.5F, 9, 8, 1, 0.0F);
            bipedBody.addChild(r2);
            ModelRenderer r3 = new ModelRenderer(this, 74, 32);
            r3.setRotationPoint(0.0F, 0.0F, 0.0F);
            r3.addBox(-4.0F, 3.5F, -8.5F, 3, 3, 2, 0.0F);
            bipedBody.addChild(r3);
            ModelRenderer r4 = new ModelRenderer(this, 74, 32);
            r4.setRotationPoint(0.0F, 0.0F, 0.0F);
            r4.addBox(1.5F, 7.0F, -8.5F, 3, 3, 2, 0.0F);
            bipedBody.addChild(r4);
            ModelRenderer r5 = new ModelRenderer(this, 64, 32);
            r5.setRotationPoint(0.0F, 0.0F, 0.0F);
            r5.addBox(-6.5F, 6.0F, -3.0F, 2, 3, 3, 0.0F);
            bipedBody.addChild(r5);
            ModelRenderer r6 = new ModelRenderer(this, 64, 32);
            r6.setRotationPoint(0.0F, 0.0F, 0.0F);
            r6.addBox(4.5F, 4.0F, -4.0F, 2, 3, 3, 0.0F);
            bipedBody.addChild(r6);
            ModelRenderer r7 = new ModelRenderer(this, 64, 19);
            r7.setRotationPoint(0.0F, 0.0F, 0.0F);
            r7.addBox(-5.0F, -5.0F, -4.5F, 10, 5, 8, 0.0F);
            bipedHead.addChild(r7);
            ModelRenderer r8 = new ModelRenderer(this, 120, 19);
            r8.setRotationPoint(0.0F, 0.0F, 0.0F);
            r8.addBox(-0.5F, 3.0F, -7.8F, 1, 8, 1, 0.0F);
            bipedBody.addChild(r8);
        }
        if (7 == type) {
            ModelRenderer r1 = new ModelRenderer(this, 86, 0);
            r1.setRotationPoint(0.0F, 0.0F, 0.0F);
            r1.addBox(-4.6F, -8.8F, -4.6F, 9, 5, 9, 0.0F);
            bipedHead.addChild(r1);
            ModelRenderer r2 = new ModelRenderer(this, 106, 34);
            r2.setRotationPoint(0.0F, 0.0F, 0.0F);
            r2.addBox(-4.4F, -4.6F, -4.9F, 9, 3, 1, 0.0F);
            bipedHead.addChild(r2);
            ModelRenderer r3 = new ModelRenderer(this, 64, 43);
            r3.setRotationPoint(0.0F, 0.0F, 0.0F);
            r3.addBox(-4.6F, -3.8F, 2.6F, 9, 2, 2, 0.0F);
            bipedHead.addChild(r3);
            ModelRenderer r4 = new ModelRenderer(this, 86, 24);
            r4.setRotationPoint(0.0F, 0.0F, 0.0F);
            r4.addBox(-4.5F, 0.5F, -2.9F, 9, 8, 1, 0.0F);
            bipedBody.addChild(r4);
            ModelRenderer r5 = new ModelRenderer(this, 106, 24);
            r5.setRotationPoint(0.0F, 0.0F, 0.0F);
            r5.addBox(-4.5F, 0.5F, 1.9F, 9, 8, 1, 0.0F);
            bipedBody.addChild(r5);
            ModelRenderer r6 = new ModelRenderer(this, 64, 24);
            r6.setRotationPoint(0.0F, 0.0F, 0.0F);
            r6.addBox(-3.8F, -2.8F, -2.8F, 5, 4, 6, 0.0F);
            bipedRightArm.addChild(r6);
            ModelRenderer r7 = new ModelRenderer(this, 64, 24);
            r7.mirror = true;
            r7.setRotationPoint(0.0F, 0.0F, 0.0F);
            r7.addBox(-1.2F, -2.8F, -2.8F, 5, 4, 6, 0.0F);
            bipedLeftArm.addChild(r7);
            ModelRenderer r8 = new ModelRenderer(this, 64, 0);
            r8.setRotationPoint(0.0F, 0.0F, 0.0F);
            r8.addBox(2.8F, -2.0F, -6.0F, 1, 14, 10, 0.0F);
            bipedLeftArm.addChild(r8);
            ModelRenderer r9 = new ModelRenderer(this, 64, 34);
            r9.setRotationPoint(0.0F, 0.0F, 0.0F);
            r9.addBox(3.6F, 0.0F, -4.0F, 1, 3, 6, 0.0F);
            bipedLeftArm.addChild(r9);
            ModelRenderer r10 = new ModelRenderer(this, 86, 43);
            r10.setRotationPoint(0.0F, 0.0F, 0.0F);
            r10.addBox(-2.4F, 4.0F, -2.8F, 5, 3, 1, 0.0F);
            bipedRightLeg.addChild(r10);
            ModelRenderer r11 = new ModelRenderer(this, 86, 43);
            r11.mirror = true;
            r11.setRotationPoint(0.0F, 0.0F, 0.0F);
            r11.addBox(-2.6F, 4.0F, -2.8F, 5, 3, 1, 0.0F);
            bipedLeftLeg.addChild(r11);
            ModelRenderer r12 = new ModelRenderer(this, 78, 34);
            r12.setRotationPoint(0.0F, 0.0F, 0.0F);
            r12.addBox(-4.4F, 9.0F, -2.4F, 9, 2, 5, 0.0F);
            bipedBody.addChild(r12);
        }
        if (8 == type) {
            ModelRenderer r1 = new ModelRenderer(this, 100, 44);
            r1.setRotationPoint(0.0F, 0.0F, 0.0F);
            r1.addBox(-4.6F, -8.8F, 4.0F, 9, 9, 1, 0.0F);
            bipedHead.addChild(r1);
            ModelRenderer r2 = new ModelRenderer(this, 0, 32);
            r2.setRotationPoint(0.0F, 0.0F, 0.0F);
            r2.addBox(-4.6F, -9.0F, -4.8F, 9, 1, 9, 0.0F);
            bipedHead.addChild(r2);
            ModelRenderer r3 = new ModelRenderer(this, 64, 27);
            r3.setRotationPoint(0.0F, 0.0F, 0.0F);
            r3.addBox(-4.8F, -8.8F, -4.8F, 1, 8, 9, 0.0F);
            bipedHead.addChild(r3);
            ModelRenderer r4 = new ModelRenderer(this, 64, 27);
            r4.mirror = true;
            r4.setRotationPoint(0.0F, 0.0F, 0.0F);
            r4.addBox(3.8F, -8.8F, -4.8F, 1, 8, 9, 0.0F);
            bipedHead.addChild(r4);
            ModelRenderer r5 = new ModelRenderer(this, 0, 51);
            r5.setRotationPoint(0.0F, 0.0F, 0.0F);
            r5.addBox(-1.5F, -11.0F, 2.5F, 3, 2, 3, 0.0F);
            r5.rotateAngleX = -0.5236F;
            r5.rotateAngleY = 0.0F;
            r5.rotateAngleZ = 0.0F;
            bipedHead.addChild(r5);
            ModelRenderer r6 = new ModelRenderer(this, 0, 42);
            r6.setRotationPoint(0.0F, 0.0F, 0.0F);
            r6.addBox(-5.0F, -1.5F, -3.0F, 10, 3, 6, 0.0F);
            bipedBody.addChild(r6);
            ModelRenderer r7 = new ModelRenderer(this, 84, 27);
            r7.setRotationPoint(0.0F, 11.5F, 0.0F);
            r7.addBox(-4.6F, 0.0F, -3.0F, 9, 11, 6, 0.0F);
            r7.rotateAngleX = -0.0698F;
            r7.rotateAngleY = 0.0F;
            r7.rotateAngleZ = 0.0F;
            bipedBody.addChild(r7);
            ModelRenderer r8 = new ModelRenderer(this, 64, 44);
            r8.setRotationPoint(0.0F, 8.0F, 0.0F);
            r8.addBox(-5.4F, 0.0F, -3.6F, 11, 4, 7, 0.0F);
            r7.addChild(r8);
            ModelRenderer r9 = new ModelRenderer(this, 64, 0);
            r9.setRotationPoint(0.0F, 0.0F, 0.0F);
            r9.addBox(-1.5F, 9.5F, -16.0F, 1, 1, 26, 0.0F);
            bipedRightArm.addChild(r9);
            ModelRenderer r10 = new ModelRenderer(this, 32, 42);
            r10.setRotationPoint(0.0F, 0.0F, 0.0F);
            r10.addBox(-2.5F, 8.5F, -19.0F, 3, 3, 3, 0.0F);
            bipedRightArm.addChild(r10);
            ModelRenderer r11 = new ModelRenderer(this, 28, 51);
            r11.setRotationPoint(0.0F, 0.0F, 0.0F);
            r11.addBox(-1.5F, 9.5F, -20.5F, 1, 1, 1, 0.0F);
            bipedRightArm.addChild(r11);
            ModelRenderer r12 = new ModelRenderer(this, 12, 51);
            r12.setRotationPoint(0.0F, 0.0F, 0.0F);
            r12.addBox(-2.8F, 8.2F, -17.5F, 1, 1, 3, 0.0F);
            bipedRightArm.addChild(r12);
            ModelRenderer r13 = new ModelRenderer(this, 12, 51);
            r13.setRotationPoint(0.0F, 0.0F, 0.0F);
            r13.addBox(0.0F, 8.2F, -17.5F, 1, 1, 3, 0.0F);
            bipedRightArm.addChild(r13);
            ModelRenderer r14 = new ModelRenderer(this, 20, 51);
            r14.setRotationPoint(0.0F, 0.0F, 0.0F);
            r14.addBox(-1.5F, 3.0F, -3.2F, 3, 3, 1, 0.0F);
            bipedBody.addChild(r14);
            ModelRenderer r15 = new ModelRenderer(this, 44, 42);
            r15.setRotationPoint(0.0F, 0.0F, 0.0F);
            r15.addBox(-1.5F, 11.0F, -1.5F, 3, 3, 3, 0.0F);
            bipedLeftArm.addChild(r15);
        }
    }

    @Override
    public void render(Entity e, float limbSwing, float limbSwingAmount, float age, float yaw, float pitch, float scale) {
        if (TILT[type] == 0.0F) {
            super.render(e, limbSwing, limbSwingAmount, age, yaw, pitch, scale);
            return;
        }
        GlStateManager.pushMatrix();
        GlStateManager.translate(0.0F, 13.0F * scale, 0.0F);
        GlStateManager.rotate((float) Math.toDegrees(TILT[type]), 1.0F, 0.0F, 0.0F);
        GlStateManager.translate(0.0F, -12.0F * scale, 0.0F);
        super.render(e, limbSwing, limbSwingAmount, age, yaw, pitch, scale);
        GlStateManager.popMatrix();
    }

    @Override
    public void setRotationAngles(float limbSwing, float limbSwingAmount, float age, float yaw, float pitch, float scale, Entity e) {
        super.setRotationAngles(limbSwing, limbSwingAmount, age, yaw, pitch, scale, e);
        float[] p = POSE[type];
        float walk = MathHelper.cos(limbSwing * 0.6662F) * limbSwingAmount;
        float hit = MathHelper.sin(swingProgress * (float) Math.PI);
        float breathe = MathHelper.sin(age * 0.067F) * 0.05F;
        bipedHead.rotateAngleX += p[0];
        bipedHead.rotateAngleY += p[1];
        bipedHead.rotateAngleZ = p[2]; // vanilla never resets Z: += made the head spin
        bipedRightArm.rotateAngleX = p[3] + walk * 0.35F - hit * 1.1F + breathe;
        bipedRightArm.rotateAngleY = p[4];
        bipedRightArm.rotateAngleZ = p[5] + breathe;
        bipedLeftArm.rotateAngleX = p[6] - walk * 0.35F - hit * 0.6F - breathe;
        bipedLeftArm.rotateAngleY = p[7];
        bipedLeftArm.rotateAngleZ = p[8] - breathe;
        bipedRightLeg.rotateAngleZ = p[11];
        bipedLeftLeg.rotateAngleZ = p[14];
        if (TILT[type] != 0.0F) {
            // crawling: the head looks ahead, the arms pull in turn
            bipedHead.rotateAngleX = p[0] + pitch * 0.017453292F * 0.5F;
            bipedRightArm.rotateAngleX = p[3] + MathHelper.cos(limbSwing * 0.9F) * 0.9F * limbSwingAmount - hit;
            bipedLeftArm.rotateAngleX = p[6] - MathHelper.cos(limbSwing * 0.9F) * 0.9F * limbSwingAmount - hit;
        }
    }
}
