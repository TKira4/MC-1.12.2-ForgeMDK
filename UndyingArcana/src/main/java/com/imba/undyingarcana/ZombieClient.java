package com.imba.undyingarcana;

import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** Client side of the zombie system: the Blood Moon tints the fog red (no screen overlay). */
@SideOnly(Side.CLIENT)
public class ZombieClient {
    public static volatile boolean bloodMoon;
    private float mix;

    @SubscribeEvent
    public void onFog(EntityViewRenderEvent.FogColors e) {
        Minecraft mc = Minecraft.getMinecraft();
        boolean on = bloodMoon && mc.world != null && mc.world.provider.getDimension() == 0;
        mix += ((on ? 1.0F : 0.0F) - mix) * 0.02F;
        if (mix < 0.01F) {
            return;
        }
        e.setRed(e.getRed() + (0.45F - e.getRed()) * mix);
        e.setGreen(e.getGreen() * (1.0F - 0.8F * mix));
        e.setBlue(e.getBlue() * (1.0F - 0.8F * mix));
    }
}
