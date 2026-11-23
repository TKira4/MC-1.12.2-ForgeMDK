package com.imba.undyingarcana;

import net.minecraft.entity.Entity;

/** Server-side (and shared) proxy: every client-only hook is a no-op here. */
public class CommonProxy {
    public void init() {
    }

    public void onHud(SkillNet.MsgHud msg) {
    }

    public void onFx(SkillNet.MsgFx msg) {
    }

    public void onShake(SkillNet.MsgShake msg) {
    }

    public void onMoon(boolean on) {
    }

    public void onStats(SkillNet.MsgStats msg) {
    }

    public void onQuestDefs(SkillNet.MsgQuestDefs msg) {
    }

    public void onQuestProg(SkillNet.MsgQuestProg msg) {
    }

    public void onOpen(SkillNet.MsgOpen msg) {
    }

    public void onPower(SkillNet.MsgPower msg) {
    }

    public void onSurv(SkillNet.MsgSurv msg) {
    }

    public void onWeave(SkillNet.MsgWeave msg) {
    }

    /** Right-clicking a Spell Page opens it in the editor (client). */
    public void openPage(net.minecraft.nbt.NBTTagCompound page) {
    }

    /** Client-side trail of a skill bolt (called from EntitySkillBolt.onUpdate on the client). */
    public void boltTrail(Entity bolt, int element, float size, int kind) {
    }
}
