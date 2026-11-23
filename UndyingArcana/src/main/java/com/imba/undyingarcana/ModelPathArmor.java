package com.imba.undyingarcana;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Path armor model: the normal armor boxes plus path-specific extra parts (horns, pauldrons, halo...).
 * GENERATED from armor_design.py (gen_model_java.py) - texture offsets match the bottom half of the 64x64 armor layers.
 */
@SideOnly(Side.CLIENT)
public class ModelPathArmor extends ModelBiped {
    private static final Map<String, ModelPathArmor> CACHE = new HashMap<>();

    public static ModelPathArmor get(String path, EntityEquipmentSlot slot) {
        String key = path + ":" + slot.getName();
        ModelPathArmor m = CACHE.get(key);
        if (m == null) {
            m = new ModelPathArmor(path, slot);
            CACHE.put(key, m);
        }
        return m;
    }

    private ModelPathArmor(String path, EntityEquipmentSlot slot) {
        super(slot == EntityEquipmentSlot.LEGS ? 0.5F : 1.0F, 0.0F, 64, 64);
        if ("berserker".equals(path) && slot == EntityEquipmentSlot.HEAD) {
            ModelRenderer r1 = new ModelRenderer(this, 0, 32);
            r1.setRotationPoint(4.6F, -7.5F, 0.0F);
            r1.addBox(-1.0F, -3.0F, -1.0F, 2, 3, 2, 0.0F);
            r1.rotateAngleX = -0.1745F;
            r1.rotateAngleY = 0.0F;
            r1.rotateAngleZ = 0.7854F;
            bipedHead.addChild(r1);
            ModelRenderer r2 = new ModelRenderer(this, 8, 32);
            r2.setRotationPoint(0.0F, -2.5F, 0.0F);
            r2.addBox(-1.0F, -2.5F, -1.0F, 2, 3, 2, 0.0F);
            r2.rotateAngleX = -0.1745F;
            r2.rotateAngleY = 0.0F;
            r2.rotateAngleZ = 0.4363F;
            r1.addChild(r2);
            ModelRenderer r3 = new ModelRenderer(this, 16, 32);
            r3.setRotationPoint(0.0F, -2.5F, 0.5F);
            r3.addBox(-0.5F, -2.0F, -0.5F, 1, 2, 1, 0.0F);
            r3.rotateAngleX = 0.0F;
            r3.rotateAngleY = 0.0F;
            r3.rotateAngleZ = 0.4363F;
            r2.addChild(r3);
            ModelRenderer r4 = new ModelRenderer(this, 0, 32);
            r4.setRotationPoint(-4.6F, -7.5F, 0.0F);
            r4.addBox(-1.0F, -3.0F, -1.0F, 2, 3, 2, 0.0F);
            r4.rotateAngleX = -0.1745F;
            r4.rotateAngleY = 0.0F;
            r4.rotateAngleZ = -0.7854F;
            bipedHead.addChild(r4);
            ModelRenderer r5 = new ModelRenderer(this, 8, 32);
            r5.setRotationPoint(0.0F, -2.5F, 0.0F);
            r5.addBox(-1.0F, -2.5F, -1.0F, 2, 3, 2, 0.0F);
            r5.rotateAngleX = -0.1745F;
            r5.rotateAngleY = 0.0F;
            r5.rotateAngleZ = -0.4363F;
            r4.addChild(r5);
            ModelRenderer r6 = new ModelRenderer(this, 16, 32);
            r6.setRotationPoint(0.0F, -2.5F, 0.5F);
            r6.addBox(-0.5F, -2.0F, -0.5F, 1, 2, 1, 0.0F);
            r6.rotateAngleX = 0.0F;
            r6.rotateAngleY = 0.0F;
            r6.rotateAngleZ = -0.4363F;
            r5.addChild(r6);
        }
        if ("berserker".equals(path) && slot == EntityEquipmentSlot.CHEST) {
            ModelRenderer r7 = new ModelRenderer(this, 20, 32);
            r7.setRotationPoint(0.0F, 0.0F, 0.0F);
            r7.addBox(-4.5F, -3.2F, -3.0F, 5, 2, 6, 0.0F);
            bipedRightArm.addChild(r7);
            ModelRenderer r8 = new ModelRenderer(this, 42, 32);
            r8.setRotationPoint(-2.5F, -3.2F, 0.0F);
            r8.addBox(-0.5F, -2.0F, -0.5F, 1, 2, 1, 0.0F);
            r8.rotateAngleX = 0.0F;
            r8.rotateAngleY = 0.0F;
            r8.rotateAngleZ = -0.3491F;
            r7.addChild(r8);
            ModelRenderer r9 = new ModelRenderer(this, 42, 32);
            r9.setRotationPoint(-0.5F, -3.2F, 0.0F);
            r9.addBox(-0.5F, -2.0F, -0.5F, 1, 2, 1, 0.0F);
            r9.rotateAngleX = 0.0F;
            r9.rotateAngleY = 0.0F;
            r9.rotateAngleZ = -0.0873F;
            r7.addChild(r9);
            ModelRenderer r10 = new ModelRenderer(this, 20, 32);
            r10.mirror = true;
            r10.setRotationPoint(0.0F, 0.0F, 0.0F);
            r10.addBox(-0.5F, -3.2F, -3.0F, 5, 2, 6, 0.0F);
            bipedLeftArm.addChild(r10);
            ModelRenderer r11 = new ModelRenderer(this, 42, 32);
            r11.setRotationPoint(2.5F, -3.2F, 0.0F);
            r11.addBox(-0.5F, -2.0F, -0.5F, 1, 2, 1, 0.0F);
            r11.rotateAngleX = 0.0F;
            r11.rotateAngleY = 0.0F;
            r11.rotateAngleZ = 0.3491F;
            r10.addChild(r11);
            ModelRenderer r12 = new ModelRenderer(this, 42, 32);
            r12.setRotationPoint(0.5F, -3.2F, 0.0F);
            r12.addBox(-0.5F, -2.0F, -0.5F, 1, 2, 1, 0.0F);
            r12.rotateAngleX = 0.0F;
            r12.rotateAngleY = 0.0F;
            r12.rotateAngleZ = 0.0873F;
            r10.addChild(r12);
        }
        if ("guardian".equals(path) && slot == EntityEquipmentSlot.HEAD) {
            ModelRenderer r13 = new ModelRenderer(this, 0, 32);
            r13.setRotationPoint(0.0F, -9.2F, 0.0F);
            r13.addBox(-0.5F, -1.0F, -4.5F, 1, 1, 9, 0.0F);
            bipedHead.addChild(r13);
            ModelRenderer r14 = new ModelRenderer(this, 20, 32);
            r14.setRotationPoint(0.0F, -1.0F, 0.0F);
            r14.addBox(-0.5F, -3.0F, -4.0F, 1, 3, 8, 0.0F);
            r14.rotateAngleX = -0.1396F;
            r14.rotateAngleY = 0.0F;
            r14.rotateAngleZ = 0.0F;
            r13.addChild(r14);
        }
        if ("guardian".equals(path) && slot == EntityEquipmentSlot.CHEST) {
            ModelRenderer r15 = new ModelRenderer(this, 38, 32);
            r15.setRotationPoint(0.0F, 0.0F, 0.0F);
            r15.addBox(-5.0F, -3.4F, -3.5F, 5, 2, 6, 0.0F);
            bipedRightArm.addChild(r15);
            ModelRenderer r16 = new ModelRenderer(this, 0, 43);
            r16.setRotationPoint(-4.6F, -0.8F, 0.0F);
            r16.addBox(-0.8F, 0.0F, -3.3F, 2, 3, 6, 0.0F);
            r16.rotateAngleX = 0.0F;
            r16.rotateAngleY = 0.0F;
            r16.rotateAngleZ = 0.2618F;
            r15.addChild(r16);
            ModelRenderer r17 = new ModelRenderer(this, 38, 32);
            r17.mirror = true;
            r17.setRotationPoint(0.0F, 0.0F, 0.0F);
            r17.addBox(-1.0F, -3.4F, -3.5F, 5, 2, 6, 0.0F);
            bipedLeftArm.addChild(r17);
            ModelRenderer r18 = new ModelRenderer(this, 0, 43);
            r18.mirror = true;
            r18.setRotationPoint(4.6F, -0.8F, 0.0F);
            r18.addBox(-1.2F, 0.0F, -3.3F, 2, 3, 6, 0.0F);
            r18.rotateAngleX = 0.0F;
            r18.rotateAngleY = 0.0F;
            r18.rotateAngleZ = -0.2618F;
            r17.addChild(r18);
            ModelRenderer r19 = new ModelRenderer(this, 16, 43);
            r19.setRotationPoint(0.0F, 0.0F, 0.0F);
            r19.addBox(-2.0F, 1.5F, -3.6F, 4, 5, 1, 0.0F);
            bipedBody.addChild(r19);
        }
        if ("assassin".equals(path) && slot == EntityEquipmentSlot.HEAD) {
            ModelRenderer r20 = new ModelRenderer(this, 0, 32);
            r20.setRotationPoint(0.0F, 0.0F, 0.0F);
            r20.addBox(-4.6F, -9.3F, -4.6F, 9, 1, 9, 0.0F);
            bipedHead.addChild(r20);
            ModelRenderer r21 = new ModelRenderer(this, 36, 32);
            r21.setRotationPoint(0.0F, 0.0F, 0.0F);
            r21.addBox(-4.6F, -8.3F, 4.1F, 9, 8, 1, 0.0F);
            bipedHead.addChild(r21);
            ModelRenderer r22 = new ModelRenderer(this, 0, 42);
            r22.setRotationPoint(0.0F, 0.0F, 0.0F);
            r22.addBox(-4.2F, -3.6F, -5.2F, 8, 3, 1, 0.0F);
            bipedHead.addChild(r22);
        }
        if ("assassin".equals(path) && slot == EntityEquipmentSlot.CHEST) {
            ModelRenderer r23 = new ModelRenderer(this, 18, 42);
            r23.setRotationPoint(0.0F, 0.0F, 0.0F);
            r23.addBox(-4.6F, 9.2F, -2.6F, 9, 1, 5, 0.0F);
            bipedBody.addChild(r23);
            ModelRenderer r24 = new ModelRenderer(this, 46, 42);
            r24.setRotationPoint(3.2F, 9.0F, -2.4F);
            r24.addBox(-0.5F, -0.5F, -0.5F, 1, 4, 1, 0.0F);
            r24.rotateAngleX = 0.0F;
            r24.rotateAngleY = 0.0F;
            r24.rotateAngleZ = -0.3491F;
            bipedBody.addChild(r24);
        }
        if ("sharpshooter".equals(path) && slot == EntityEquipmentSlot.HEAD) {
            ModelRenderer r25 = new ModelRenderer(this, 0, 32);
            r25.setRotationPoint(3.6F, -8.6F, 0.5F);
            r25.addBox(-0.5F, -6.0F, 0.0F, 1, 6, 2, 0.0F);
            r25.rotateAngleX = -0.4363F;
            r25.rotateAngleY = 0.0F;
            r25.rotateAngleZ = 0.1745F;
            bipedHead.addChild(r25);
        }
        if ("sharpshooter".equals(path) && slot == EntityEquipmentSlot.CHEST) {
            ModelRenderer r26 = new ModelRenderer(this, 6, 32);
            r26.setRotationPoint(1.5F, 1.0F, 3.2F);
            r26.addBox(-1.5F, -2.0F, 0.0F, 3, 10, 2, 0.0F);
            r26.rotateAngleX = 0.0F;
            r26.rotateAngleY = 0.0F;
            r26.rotateAngleZ = -0.4363F;
            bipedBody.addChild(r26);
        }
        if ("beastmaster".equals(path) && slot == EntityEquipmentSlot.HEAD) {
            ModelRenderer r27 = new ModelRenderer(this, 0, 32);
            r27.setRotationPoint(-2.8F, -9.0F, -1.0F);
            r27.addBox(-1.0F, -2.5F, -0.5F, 2, 3, 1, 0.0F);
            r27.rotateAngleX = 0.0F;
            r27.rotateAngleY = 0.0F;
            r27.rotateAngleZ = -0.2094F;
            bipedHead.addChild(r27);
            ModelRenderer r28 = new ModelRenderer(this, 0, 32);
            r28.mirror = true;
            r28.setRotationPoint(2.8F, -9.0F, -1.0F);
            r28.addBox(-1.0F, -2.5F, -0.5F, 2, 3, 1, 0.0F);
            r28.rotateAngleX = 0.0F;
            r28.rotateAngleY = 0.0F;
            r28.rotateAngleZ = 0.2094F;
            bipedHead.addChild(r28);
            ModelRenderer r29 = new ModelRenderer(this, 6, 32);
            r29.setRotationPoint(0.0F, -6.6F, -4.6F);
            r29.addBox(-2.0F, -1.0F, -2.5F, 4, 2, 3, 0.0F);
            bipedHead.addChild(r29);
        }
        if ("beastmaster".equals(path) && slot == EntityEquipmentSlot.CHEST) {
            ModelRenderer r30 = new ModelRenderer(this, 20, 32);
            r30.setRotationPoint(0.0F, 0.0F, 0.0F);
            r30.addBox(-5.2F, -1.3F, -3.2F, 10, 3, 6, 0.0F);
            bipedBody.addChild(r30);
        }
        if ("skirmisher".equals(path) && slot == EntityEquipmentSlot.HEAD) {
            ModelRenderer r31 = new ModelRenderer(this, 0, 32);
            r31.setRotationPoint(0.0F, -6.0F, 4.3F);
            r31.addBox(-1.5F, -0.5F, -0.5F, 3, 1, 1, 0.0F);
            bipedHead.addChild(r31);
            ModelRenderer r32 = new ModelRenderer(this, 8, 32);
            r32.setRotationPoint(0.0F, 0.5F, 0.5F);
            r32.addBox(-1.0F, 0.0F, -0.5F, 1, 5, 1, 0.0F);
            r32.rotateAngleX = 0.3491F;
            r32.rotateAngleY = 0.0F;
            r32.rotateAngleZ = 0.2094F;
            r31.addChild(r32);
            ModelRenderer r33 = new ModelRenderer(this, 8, 32);
            r33.setRotationPoint(0.0F, 0.5F, 0.5F);
            r33.addBox(0.0F, 0.0F, -0.5F, 1, 5, 1, 0.0F);
            r33.rotateAngleX = 0.4363F;
            r33.rotateAngleY = 0.0F;
            r33.rotateAngleZ = -0.2618F;
            r31.addChild(r33);
        }
        if ("skirmisher".equals(path) && slot == EntityEquipmentSlot.CHEST) {
            ModelRenderer r34 = new ModelRenderer(this, 12, 32);
            r34.setRotationPoint(0.0F, 6.0F, -2.8F);
            r34.addBox(-0.5F, -7.5F, -0.5F, 1, 15, 1, 0.0F);
            r34.rotateAngleX = 0.0F;
            r34.rotateAngleY = 0.0F;
            r34.rotateAngleZ = 0.6632F;
            bipedBody.addChild(r34);
            ModelRenderer r35 = new ModelRenderer(this, 12, 32);
            r35.setRotationPoint(0.0F, 6.0F, 2.8F);
            r35.addBox(-0.5F, -7.5F, -0.5F, 1, 15, 1, 0.0F);
            r35.rotateAngleX = 0.0F;
            r35.rotateAngleY = 0.0F;
            r35.rotateAngleZ = -0.6632F;
            bipedBody.addChild(r35);
        }
        if ("elementalist".equals(path) && slot == EntityEquipmentSlot.HEAD) {
            ModelRenderer r36 = new ModelRenderer(this, 0, 32);
            r36.setRotationPoint(0.0F, 0.0F, 0.0F);
            r36.addBox(-4.6F, -9.6F, -4.6F, 9, 1, 9, 0.0F);
            bipedHead.addChild(r36);
            ModelRenderer r37 = new ModelRenderer(this, 36, 32);
            r37.setRotationPoint(0.0F, 0.0F, 0.0F);
            r37.addBox(-4.3F, -11.6F, -4.6F, 1, 2, 1, 0.0F);
            bipedHead.addChild(r37);
            ModelRenderer r38 = new ModelRenderer(this, 40, 32);
            r38.setRotationPoint(0.0F, 0.0F, 0.0F);
            r38.addBox(-0.5F, -12.6F, -4.6F, 1, 3, 1, 0.0F);
            bipedHead.addChild(r38);
            ModelRenderer r39 = new ModelRenderer(this, 36, 32);
            r39.setRotationPoint(0.0F, 0.0F, 0.0F);
            r39.addBox(3.3F, -11.6F, -4.6F, 1, 2, 1, 0.0F);
            bipedHead.addChild(r39);
            ModelRenderer r40 = new ModelRenderer(this, 44, 32);
            r40.setRotationPoint(0.0F, 0.0F, 0.0F);
            r40.addBox(-1.0F, -11.2F, -5.1F, 2, 2, 1, 0.0F);
            bipedHead.addChild(r40);
        }
        if ("elementalist".equals(path) && slot == EntityEquipmentSlot.CHEST) {
            ModelRenderer r41 = new ModelRenderer(this, 50, 32);
            r41.setRotationPoint(-1.0F, -3.6F, 0.0F);
            r41.addBox(-1.0F, -3.0F, -1.0F, 2, 3, 2, 0.0F);
            bipedRightArm.addChild(r41);
            ModelRenderer r42 = new ModelRenderer(this, 58, 32);
            r42.setRotationPoint(0.0F, -3.0F, 0.0F);
            r42.addBox(-0.5F, -2.0F, -0.5F, 1, 2, 1, 0.0F);
            r41.addChild(r42);
            ModelRenderer r43 = new ModelRenderer(this, 50, 32);
            r43.setRotationPoint(1.0F, -3.6F, 0.0F);
            r43.addBox(-1.0F, -3.0F, -1.0F, 2, 3, 2, 0.0F);
            bipedLeftArm.addChild(r43);
            ModelRenderer r44 = new ModelRenderer(this, 58, 32);
            r44.setRotationPoint(0.0F, -3.0F, 0.0F);
            r44.addBox(-0.5F, -2.0F, -0.5F, 1, 2, 1, 0.0F);
            r43.addChild(r44);
        }
        if ("summoner".equals(path) && slot == EntityEquipmentSlot.HEAD) {
            ModelRenderer r45 = new ModelRenderer(this, 0, 32);
            r45.setRotationPoint(0.0F, 0.0F, 0.0F);
            r45.addBox(-3.0F, -8.8F, -5.2F, 6, 5, 1, 0.0F);
            bipedHead.addChild(r45);
            ModelRenderer r46 = new ModelRenderer(this, 14, 32);
            r46.setRotationPoint(0.0F, 0.0F, 0.0F);
            r46.addBox(-4.6F, -9.3F, -4.6F, 9, 1, 9, 0.0F);
            bipedHead.addChild(r46);
            ModelRenderer r47 = new ModelRenderer(this, 0, 42);
            r47.setRotationPoint(0.0F, 0.0F, 0.0F);
            r47.addBox(-4.6F, -8.3F, 4.1F, 9, 8, 1, 0.0F);
            bipedHead.addChild(r47);
        }
        if ("summoner".equals(path) && slot == EntityEquipmentSlot.CHEST) {
            ModelRenderer r48 = new ModelRenderer(this, 20, 42);
            r48.setRotationPoint(-2.5F, -3.3F, 0.0F);
            r48.addBox(-0.5F, -3.0F, -0.5F, 1, 3, 1, 0.0F);
            r48.rotateAngleX = 0.0F;
            r48.rotateAngleY = 0.0F;
            r48.rotateAngleZ = -0.2618F;
            bipedRightArm.addChild(r48);
            ModelRenderer r49 = new ModelRenderer(this, 20, 42);
            r49.setRotationPoint(2.5F, -3.3F, 0.0F);
            r49.addBox(-0.5F, -3.0F, -0.5F, 1, 3, 1, 0.0F);
            r49.rotateAngleX = 0.0F;
            r49.rotateAngleY = 0.0F;
            r49.rotateAngleZ = 0.2618F;
            bipedLeftArm.addChild(r49);
            ModelRenderer r50 = new ModelRenderer(this, 24, 42);
            r50.setRotationPoint(0.0F, 0.0F, 0.0F);
            r50.addBox(-4.6F, -0.8F, -2.8F, 9, 2, 6, 0.0F);
            bipedBody.addChild(r50);
        }
        if ("cleric".equals(path) && slot == EntityEquipmentSlot.HEAD) {
            ModelRenderer r51 = new ModelRenderer(this, 0, 32);
            r51.setRotationPoint(0.0F, -11.5F, 0.0F);
            r51.addBox(-4.0F, -0.5F, -4.0F, 8, 1, 1, 0.0F);
            r51.rotateAngleX = 0.1745F;
            r51.rotateAngleY = 0.0F;
            r51.rotateAngleZ = 0.0F;
            bipedHead.addChild(r51);
            ModelRenderer r52 = new ModelRenderer(this, 0, 32);
            r52.setRotationPoint(0.0F, 0.0F, 7.0F);
            r52.addBox(-4.0F, -0.5F, 0.0F, 8, 1, 1, 0.0F);
            r51.addChild(r52);
            ModelRenderer r53 = new ModelRenderer(this, 18, 32);
            r53.setRotationPoint(0.0F, 0.0F, 1.0F);
            r53.addBox(-4.0F, -0.5F, 0.0F, 1, 1, 6, 0.0F);
            r51.addChild(r53);
            ModelRenderer r54 = new ModelRenderer(this, 18, 32);
            r54.setRotationPoint(7.0F, 0.0F, 1.0F);
            r54.addBox(0.0F, -0.5F, 0.0F, 1, 1, 6, 0.0F);
            r51.addChild(r54);
        }
        if ("cleric".equals(path) && slot == EntityEquipmentSlot.CHEST) {
            ModelRenderer r55 = new ModelRenderer(this, 32, 32);
            r55.setRotationPoint(0.0F, 0.0F, 0.0F);
            r55.addBox(-2.5F, 0.5F, -3.1F, 5, 10, 1, 0.0F);
            bipedBody.addChild(r55);
            ModelRenderer r56 = new ModelRenderer(this, 0, 43);
            r56.setRotationPoint(0.0F, 12.0F, 0.0F);
            r56.addBox(-4.2F, 0.0F, -2.4F, 8, 6, 5, 0.0F);
            r56.rotateAngleX = -0.1047F;
            r56.rotateAngleY = 0.0F;
            r56.rotateAngleZ = 0.0F;
            bipedBody.addChild(r56);
        }
        if ("mythic_warrior".equals(path) && slot == EntityEquipmentSlot.HEAD) {
            ModelRenderer r57 = new ModelRenderer(this, 0, 32);
            r57.setRotationPoint(4.6F, -6.5F, -1.0F);
            r57.addBox(-1.0F, -3.0F, -1.0F, 2, 3, 2, 0.0F);
            r57.rotateAngleX = -0.3491F;
            r57.rotateAngleY = 0.0F;
            r57.rotateAngleZ = 1.0472F;
            bipedHead.addChild(r57);
            ModelRenderer r58 = new ModelRenderer(this, 8, 32);
            r58.setRotationPoint(0.0F, -2.5F, 0.0F);
            r58.addBox(-1.0F, -2.5F, -1.0F, 2, 3, 2, 0.0F);
            r58.rotateAngleX = -0.4363F;
            r58.rotateAngleY = 0.0F;
            r58.rotateAngleZ = 0.3491F;
            r57.addChild(r58);
            ModelRenderer r59 = new ModelRenderer(this, 16, 32);
            r59.setRotationPoint(0.0F, -2.5F, 0.5F);
            r59.addBox(-0.5F, -2.0F, -0.5F, 1, 2, 1, 0.0F);
            r59.rotateAngleX = -0.3491F;
            r59.rotateAngleY = 0.0F;
            r59.rotateAngleZ = 0.3491F;
            r58.addChild(r59);
            ModelRenderer r60 = new ModelRenderer(this, 0, 32);
            r60.setRotationPoint(-4.6F, -6.5F, -1.0F);
            r60.addBox(-1.0F, -3.0F, -1.0F, 2, 3, 2, 0.0F);
            r60.rotateAngleX = -0.3491F;
            r60.rotateAngleY = 0.0F;
            r60.rotateAngleZ = -1.0472F;
            bipedHead.addChild(r60);
            ModelRenderer r61 = new ModelRenderer(this, 8, 32);
            r61.setRotationPoint(0.0F, -2.5F, 0.0F);
            r61.addBox(-1.0F, -2.5F, -1.0F, 2, 3, 2, 0.0F);
            r61.rotateAngleX = -0.4363F;
            r61.rotateAngleY = 0.0F;
            r61.rotateAngleZ = -0.3491F;
            r60.addChild(r61);
            ModelRenderer r62 = new ModelRenderer(this, 16, 32);
            r62.setRotationPoint(0.0F, -2.5F, 0.5F);
            r62.addBox(-0.5F, -2.0F, -0.5F, 1, 2, 1, 0.0F);
            r62.rotateAngleX = -0.3491F;
            r62.rotateAngleY = 0.0F;
            r62.rotateAngleZ = -0.3491F;
            r61.addChild(r62);
            ModelRenderer r63 = new ModelRenderer(this, 20, 32);
            r63.setRotationPoint(0.0F, -9.4F, 0.0F);
            r63.addBox(-0.5F, -2.0F, -4.0F, 1, 2, 8, 0.0F);
            bipedHead.addChild(r63);
        }
        if ("mythic_warrior".equals(path) && slot == EntityEquipmentSlot.CHEST) {
            ModelRenderer r64 = new ModelRenderer(this, 38, 32);
            r64.setRotationPoint(0.0F, 0.0F, 0.0F);
            r64.addBox(-5.5F, -3.8F, -3.5F, 6, 3, 7, 0.0F);
            bipedRightArm.addChild(r64);
            ModelRenderer r65 = new ModelRenderer(this, 0, 42);
            r65.setRotationPoint(-3.5F, -3.8F, -1.5F);
            r65.addBox(-0.5F, -2.0F, -0.5F, 1, 2, 1, 0.0F);
            r65.rotateAngleX = 0.0F;
            r65.rotateAngleY = 0.0F;
            r65.rotateAngleZ = -0.4363F;
            r64.addChild(r65);
            ModelRenderer r66 = new ModelRenderer(this, 0, 42);
            r66.setRotationPoint(-3.5F, -3.8F, 1.5F);
            r66.addBox(-0.5F, -2.0F, -0.5F, 1, 2, 1, 0.0F);
            r66.rotateAngleX = 0.0F;
            r66.rotateAngleY = 0.0F;
            r66.rotateAngleZ = -0.4363F;
            r64.addChild(r66);
            ModelRenderer r67 = new ModelRenderer(this, 0, 42);
            r67.setRotationPoint(-1.2F, -3.8F, 0.0F);
            r67.addBox(-0.5F, -2.0F, -0.5F, 1, 2, 1, 0.0F);
            r67.rotateAngleX = 0.0F;
            r67.rotateAngleY = 0.0F;
            r67.rotateAngleZ = -0.1396F;
            r64.addChild(r67);
            ModelRenderer r68 = new ModelRenderer(this, 38, 32);
            r68.mirror = true;
            r68.setRotationPoint(0.0F, 0.0F, 0.0F);
            r68.addBox(-0.5F, -3.8F, -3.5F, 6, 3, 7, 0.0F);
            bipedLeftArm.addChild(r68);
            ModelRenderer r69 = new ModelRenderer(this, 0, 42);
            r69.setRotationPoint(3.5F, -3.8F, -1.5F);
            r69.addBox(-0.5F, -2.0F, -0.5F, 1, 2, 1, 0.0F);
            r69.rotateAngleX = 0.0F;
            r69.rotateAngleY = 0.0F;
            r69.rotateAngleZ = 0.4363F;
            r68.addChild(r69);
            ModelRenderer r70 = new ModelRenderer(this, 0, 42);
            r70.setRotationPoint(3.5F, -3.8F, 1.5F);
            r70.addBox(-0.5F, -2.0F, -0.5F, 1, 2, 1, 0.0F);
            r70.rotateAngleX = 0.0F;
            r70.rotateAngleY = 0.0F;
            r70.rotateAngleZ = 0.4363F;
            r68.addChild(r70);
            ModelRenderer r71 = new ModelRenderer(this, 0, 42);
            r71.setRotationPoint(1.2F, -3.8F, 0.0F);
            r71.addBox(-0.5F, -2.0F, -0.5F, 1, 2, 1, 0.0F);
            r71.rotateAngleX = 0.0F;
            r71.rotateAngleY = 0.0F;
            r71.rotateAngleZ = 0.1396F;
            r68.addChild(r71);
            ModelRenderer r72 = new ModelRenderer(this, 4, 42);
            r72.setRotationPoint(0.0F, 0.2F, 2.7F);
            r72.addBox(-4.0F, 0.0F, 0.0F, 8, 13, 1, 0.0F);
            r72.rotateAngleX = 0.1047F;
            r72.rotateAngleY = 0.0F;
            r72.rotateAngleZ = 0.0F;
            bipedBody.addChild(r72);
            ModelRenderer r73 = new ModelRenderer(this, 22, 42);
            r73.setRotationPoint(0.0F, 0.0F, 0.0F);
            r73.addBox(-4.6F, 9.2F, -2.6F, 9, 1, 5, 0.0F);
            bipedBody.addChild(r73);
        }
        if ("mythic_ranger".equals(path) && slot == EntityEquipmentSlot.HEAD) {
            ModelRenderer r74 = new ModelRenderer(this, 0, 32);
            r74.setRotationPoint(0.0F, 0.0F, 0.0F);
            r74.addBox(-4.6F, -9.3F, -4.6F, 9, 1, 9, 0.0F);
            bipedHead.addChild(r74);
            ModelRenderer r75 = new ModelRenderer(this, 36, 32);
            r75.setRotationPoint(0.0F, 0.0F, 0.0F);
            r75.addBox(-4.6F, -8.3F, 4.1F, 9, 8, 1, 0.0F);
            bipedHead.addChild(r75);
            ModelRenderer r76 = new ModelRenderer(this, 56, 32);
            r76.setRotationPoint(3.6F, -8.6F, 0.5F);
            r76.addBox(-0.5F, -6.0F, 0.0F, 1, 6, 2, 0.0F);
            r76.rotateAngleX = -0.5236F;
            r76.rotateAngleY = 0.0F;
            r76.rotateAngleZ = 0.2618F;
            bipedHead.addChild(r76);
            ModelRenderer r77 = new ModelRenderer(this, 56, 32);
            r77.mirror = true;
            r77.setRotationPoint(-3.6F, -8.6F, 0.5F);
            r77.addBox(-0.5F, -6.0F, 0.0F, 1, 6, 2, 0.0F);
            r77.rotateAngleX = -0.5236F;
            r77.rotateAngleY = 0.0F;
            r77.rotateAngleZ = -0.2618F;
            bipedHead.addChild(r77);
        }
        if ("mythic_ranger".equals(path) && slot == EntityEquipmentSlot.CHEST) {
            ModelRenderer r78 = new ModelRenderer(this, 0, 42);
            r78.setRotationPoint(-2.5F, -2.6F, 0.5F);
            r78.addBox(-1.0F, -5.0F, -3.0F, 1, 5, 6, 0.0F);
            r78.rotateAngleX = 0.0F;
            r78.rotateAngleY = 0.0F;
            r78.rotateAngleZ = 0.4363F;
            bipedRightArm.addChild(r78);
            ModelRenderer r79 = new ModelRenderer(this, 0, 42);
            r79.mirror = true;
            r79.setRotationPoint(2.5F, -2.6F, 0.5F);
            r79.addBox(0.0F, -5.0F, -3.0F, 1, 5, 6, 0.0F);
            r79.rotateAngleX = 0.0F;
            r79.rotateAngleY = 0.0F;
            r79.rotateAngleZ = -0.4363F;
            bipedLeftArm.addChild(r79);
            ModelRenderer r80 = new ModelRenderer(this, 14, 42);
            r80.setRotationPoint(0.0F, 0.0F, 0.0F);
            r80.addBox(-4.5F, -0.6F, -2.5F, 9, 2, 5, 0.0F);
            bipedBody.addChild(r80);
            ModelRenderer r81 = new ModelRenderer(this, 42, 42);
            r81.setRotationPoint(1.5F, 1.0F, 3.2F);
            r81.addBox(-1.5F, -2.0F, 0.0F, 3, 10, 2, 0.0F);
            r81.rotateAngleX = 0.0F;
            r81.rotateAngleY = 0.0F;
            r81.rotateAngleZ = -0.4363F;
            bipedBody.addChild(r81);
        }
        if ("mythic_mage".equals(path) && slot == EntityEquipmentSlot.HEAD) {
            ModelRenderer r82 = new ModelRenderer(this, 0, 32);
            r82.setRotationPoint(0.0F, 0.0F, 0.0F);
            r82.addBox(-4.6F, -9.6F, -4.6F, 9, 1, 9, 0.0F);
            bipedHead.addChild(r82);
            ModelRenderer r83 = new ModelRenderer(this, 36, 32);
            r83.setRotationPoint(0.0F, 0.0F, 0.0F);
            r83.addBox(-4.3F, -11.6F, -4.6F, 1, 2, 1, 0.0F);
            bipedHead.addChild(r83);
            ModelRenderer r84 = new ModelRenderer(this, 40, 32);
            r84.setRotationPoint(0.0F, 0.0F, 0.0F);
            r84.addBox(-0.5F, -12.6F, -4.6F, 1, 3, 1, 0.0F);
            bipedHead.addChild(r84);
            ModelRenderer r85 = new ModelRenderer(this, 36, 32);
            r85.setRotationPoint(0.0F, 0.0F, 0.0F);
            r85.addBox(3.3F, -11.6F, -4.6F, 1, 2, 1, 0.0F);
            bipedHead.addChild(r85);
            ModelRenderer r86 = new ModelRenderer(this, 44, 32);
            r86.setRotationPoint(0.0F, 0.0F, 0.0F);
            r86.addBox(-1.0F, -11.2F, -5.1F, 2, 2, 1, 0.0F);
            bipedHead.addChild(r86);
            ModelRenderer r87 = new ModelRenderer(this, 0, 42);
            r87.setRotationPoint(0.0F, -13.2F, 0.0F);
            r87.addBox(-4.0F, -0.5F, -4.0F, 8, 1, 1, 0.0F);
            r87.rotateAngleX = 0.1396F;
            r87.rotateAngleY = 0.0F;
            r87.rotateAngleZ = 0.0F;
            bipedHead.addChild(r87);
            ModelRenderer r88 = new ModelRenderer(this, 0, 42);
            r88.setRotationPoint(0.0F, 0.0F, 7.0F);
            r88.addBox(-4.0F, -0.5F, 0.0F, 8, 1, 1, 0.0F);
            r87.addChild(r88);
            ModelRenderer r89 = new ModelRenderer(this, 18, 42);
            r89.setRotationPoint(0.0F, 0.0F, 1.0F);
            r89.addBox(-4.0F, -0.5F, 0.0F, 1, 1, 6, 0.0F);
            r87.addChild(r89);
            ModelRenderer r90 = new ModelRenderer(this, 18, 42);
            r90.setRotationPoint(7.0F, 0.0F, 1.0F);
            r90.addBox(0.0F, -0.5F, 0.0F, 1, 1, 6, 0.0F);
            r87.addChild(r90);
        }
        if ("mythic_mage".equals(path) && slot == EntityEquipmentSlot.CHEST) {
            ModelRenderer r91 = new ModelRenderer(this, 32, 42);
            r91.setRotationPoint(-3.0F, -6.5F, 0.0F);
            r91.addBox(-1.0F, -1.0F, -1.0F, 2, 2, 2, 0.0F);
            bipedRightArm.addChild(r91);
            ModelRenderer r92 = new ModelRenderer(this, 32, 42);
            r92.setRotationPoint(3.0F, -6.5F, 0.0F);
            r92.addBox(-1.0F, -1.0F, -1.0F, 2, 2, 2, 0.0F);
            bipedLeftArm.addChild(r92);
            ModelRenderer r93 = new ModelRenderer(this, 0, 49);
            r93.setRotationPoint(0.0F, 0.0F, 0.0F);
            r93.addBox(-4.5F, -0.8F, -3.0F, 9, 2, 6, 0.0F);
            bipedBody.addChild(r93);
            ModelRenderer r94 = new ModelRenderer(this, 30, 49);
            r94.setRotationPoint(0.0F, 12.0F, 0.0F);
            r94.addBox(-4.2F, 0.0F, -2.4F, 8, 6, 5, 0.0F);
            r94.rotateAngleX = -0.1047F;
            r94.rotateAngleY = 0.0F;
            r94.rotateAngleZ = 0.0F;
            bipedBody.addChild(r94);
        }
    }
}
