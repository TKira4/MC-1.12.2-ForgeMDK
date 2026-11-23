package com.imba.undyingarcana;

import java.util.List;

import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** Weapon / bow / wand tooltips: the three skills, keys and unlock levels; Shift for numbers at this material. */
@SideOnly(Side.CLIENT)
public class SkillTooltips {
    private static String f1(double v) {
        return String.format("%.1f", v);
    }

    @SubscribeEvent
    public void onTooltip(ItemTooltipEvent e) {
        SkillCatalog.Info in = SkillCatalog.identify(e.getItemStack());
        if (in == null) {
            return;
        }
        List<String> tip = e.getToolTip();
        String word = in.isRanged() ? "Archery" : (in.isWand() ? "Magic" : "Attack");
        float tier = SkillCatalog.tierDamage(in.mat);
        float cdm = SkillCatalog.tierCooldown(in.mat);
        double[] q = SkillCatalog.quickStats(in), h = SkillCatalog.heavyStats(in.type), a = SkillCatalog.artStats(in);
        boolean shift = GuiScreen.isShiftKeyDown();
        tip.add("\u00a7b[" + SkillKeys.label(0) + "] " + SkillCatalog.quickName(in)
                + (in.legend.isEmpty() ? " \u00a78(" + word + " " + SkillCatalog.QUICK_LEVEL + ")" : ""));
        if (shift) {
            tip.add("\u00a77  " + SkillCatalog.quickDesc(in));
            tip.add("\u00a78  Damage " + f1(q[0] * tier) + ", cooldown " + f1(q[2] * cdm / 20) + "s");
        }
        tip.add("\u00a76[" + SkillKeys.label(1) + "] " + SkillCatalog.heavyName(in.type)
                + " \u00a78(hold to charge)");
        if (shift) {
            tip.add("\u00a77  " + SkillCatalog.heavyDesc(in.type));
            tip.add("\u00a78  " + (h[0] > 0 ? "Damage " + f1(h[0] * tier) + ", " : "") + "cooldown " + f1(h[2] * cdm / 20)
                    + "s, charge " + f1(SkillCatalog.chargeTicks(in.type) / 20.0) + "s (II x1.5, III x2: +15% / +33%)");
        }
        tip.add("\u00a7d[" + SkillKeys.label(2) + "] " + SkillCatalog.artName(in)
                + (in.legend.isEmpty() ? " \u00a78(" + word + " " + SkillCatalog.ART_LEVEL + ")" : ""));
        if (shift) {
            tip.add("\u00a77  " + SkillCatalog.artDesc(in));
            tip.add("\u00a78  Damage " + f1(a[0] * tier) + (a[0] < 6 ? " per hit" : "") + ", cooldown " + f1(a[2] * cdm / 20) + "s");
            tip.add("\u00a78Material: " + SkillCatalog.tierColor(in.mat) + in.mat + " \u00a78(x" + tier + " damage)");
        } else {
            tip.add("\u00a78Hold \u00a7eShift\u00a78 for skill details");
        }
    }
}
