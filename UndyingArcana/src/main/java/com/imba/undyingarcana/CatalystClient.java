package com.imba.undyingarcana;

import org.lwjgl.input.Mouse;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** Client side of catalysts: left-click fires the free bolt; tooltip lines for the catalyst bonus and attunement. */
public class CatalystClient {
    @SubscribeEvent
    public void onMouse(MouseEvent e) {
        Minecraft mc = Minecraft.getMinecraft();
        if (e.getButton() != 0 || !e.isButtonstate() || mc.player == null || mc.currentScreen != null || !RelicClient.on()) {
            return;
        }
        if (Catalyst.kind(mc.player.getHeldItemMainhand()) != Catalyst.NONE && !Mouse.isButtonDown(1)) {
            SkillNet.NET.sendToServer(new SkillNet.MsgAct(7, 0, ""));
        }
    }

    @SubscribeEvent
    public void onTooltip(ItemTooltipEvent e) {
        ItemStack s = e.getItemStack();
        int k = Catalyst.kind(s);
        if (k == Catalyst.NONE) {
            return;
        }
        int el = Catalyst.element(s);
        e.getToolTip().add(1, "\u00a7dCatalyst (" + Catalyst.KIND_NAME[k] + "): \u00a77" + Catalyst.KIND_BONUS[k]);
        e.getToolTip().add(2, "\u00a77Attuned: " + (el < 0 ? "\u00a78none" : "\u00a7b" + Magic.ELEMENT_NAME[el] + " \u00a77(+15%)")
                + " \u00a78- sneak + right-click to change");
        e.getToolTip().add(3, "\u00a78Hold it for full spell power (70% without). Left-click: a free bolt.");
    }
}
