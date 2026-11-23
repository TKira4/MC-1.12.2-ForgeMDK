package com.imba.undyingarcana;

import net.minecraft.client.model.ModelBiped;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** Path armor piece: own 64x64 texture and a model with path-specific extra parts (horns, pauldrons, halo...). */
public class PathArmor extends ItemArmor {
    public final String path;
    public final String mat;

    public PathArmor(ArmorMaterial material, EntityEquipmentSlot slot, String path, String mat) {
        super(material, 0, slot);
        this.path = path;
        this.mat = mat;
    }

    @Override
    public String getArmorTexture(ItemStack stack, Entity entity, EntityEquipmentSlot slot, String type) {
        int layer = slot == EntityEquipmentSlot.LEGS ? 2 : 1;
        return UndyingArcana.MODID + ":textures/models/armor/" + path + "_" + mat + "_layer_" + layer + ".png";
    }

    @Override
    @SideOnly(Side.CLIENT)
    public ModelBiped getArmorModel(EntityLivingBase entity, ItemStack stack, EntityEquipmentSlot slot, ModelBiped original) {
        return ModelPathArmor.get(path, slot);
    }
}
