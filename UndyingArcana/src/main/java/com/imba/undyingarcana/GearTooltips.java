package com.imba.undyingarcana;

import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** Gear tooltips: quality, gear stats, sockets and the enchant, right under the item name. */
@SideOnly(Side.CLIENT)
public class GearTooltips {
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onTooltip(ItemTooltipEvent e) {
        ItemStack s = e.getItemStack();
        SkillNet.MsgStats d = GuiStats.data;
        if (d == null || !d.on || !Gear.isGear(s)) {
            return;
        }
        List<String> tip = e.getToolTip();
        int at = Math.min(1, tip.size());
        if (!Gear.rolled(s)) {
            tip.add(at, "\u00a78Quality is rolled when you pick it up");
            return;
        }
        int q = Gear.quality(s);
        tip.add(at++, Gear.QUALITY_COLOR[q] + Gear.QUALITY[q] + (Gear.isArmor(s) ? " Armor" : " Weapon"));
        float[] st = new float[Gear.STATS];
        Gear.addStats(s, st);
        for (int k = 0; k < Gear.STATS; k++) {
            if (st[k] != 0) {
                tip.add(at++, (st[k] > 0 ? "\u00a7a+" : "\u00a7c") + (int) st[k] + " " + Gear.STAT[k]);
            }
        }
        int[] fx = Gear.itemEffects(s);
        for (int k = 1; k < Gear.EFFECTS; k++) {
            if (fx[k] > 0) {
                tip.add(at++, "\u00a76" + Gear.EFFECT[k] + " " + (k == 5 && !Gear.isArmor(s) ? 2 * fx[k] : fx[k]) + "%"
                        + (Gear.isArmor(s) ? " when hit" : k == 5 ? " on kill" : " on hit"));
            }
        }
        NBTTagList js = Gear.jewels(s);
        for (int i = 0; i < Gear.sockets(s); i++) {
            if (i < js.tagCount()) {
                NBTTagCompound j = js.getCompoundTagAt(i);
                tip.add(at++, "\u00a7d\u25c6 " + Gear.GRADE[j.getInteger("gr")] + " " + Gear.GEM_NAME[j.getInteger("g")]);
            } else {
                tip.add(at++, "\u00a78\u25c7 Empty socket");
            }
        }
        int[] en = Gear.enchant(s);
        if (en != null) {
            tip.add(at++, "\u00a7b\u2726 " + Gear.enchantName(en[0], en[1]) + "\u00a77: " + (Gear.cursed(en[0]) ? "\u00a7c" : "")
                    + Gear.enchantDesc(en[0], en[1]));
        }
    }
}
