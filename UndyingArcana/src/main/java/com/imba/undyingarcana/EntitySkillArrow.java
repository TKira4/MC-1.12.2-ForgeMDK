package com.imba.undyingarcana;

import net.minecraft.entity.projectile.EntityTippedArrow;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

/** Arrow fired by bow skills: cannot be picked up, vanishes 1 second after it lands, never saved. */
public class EntitySkillArrow extends EntityTippedArrow {
    private int groundTicks;

    public EntitySkillArrow(World w) {
        super(w);
        this.pickupStatus = PickupStatus.DISALLOWED;
    }

    public EntitySkillArrow(World w, double x, double y, double z) {
        super(w, x, y, z);
        this.pickupStatus = PickupStatus.DISALLOWED;
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        if (!world.isRemote && (inGround && ++groundTicks > 20 || ticksExisted > 200)) {
            setDead();
        }
    }

    @Override
    public boolean writeToNBTOptional(NBTTagCompound c) {
        return false;
    }
}
