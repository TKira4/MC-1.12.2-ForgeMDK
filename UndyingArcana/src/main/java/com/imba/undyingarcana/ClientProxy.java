package com.imba.undyingarcana;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraftforge.common.MinecraftForge;

public class ClientProxy extends CommonProxy {
    @Override
    public void init() {
        SkillKeys.register();
        MinecraftForge.EVENT_BUS.register(new SkillKeys());
        MinecraftForge.EVENT_BUS.register(new SkillHud());
        MinecraftForge.EVENT_BUS.register(new FxClient());
        MinecraftForge.EVENT_BUS.register(new FxMesh());
        MinecraftForge.EVENT_BUS.register(new SkillTooltips());
        MinecraftForge.EVENT_BUS.register(new ZombieClient());
        GuiStats.registerKey();
        MinecraftForge.EVENT_BUS.register(new GuiStats.Keys());
        GuiJournal.registerKey();
        MinecraftForge.EVENT_BUS.register(new GuiJournal.Client());
        MinecraftForge.EVENT_BUS.register(new GearTooltips());
        PowerHud.registerKey();
        SpellKeys.register();
        MinecraftForge.EVENT_BUS.register(new SpellKeys());
        MinecraftForge.EVENT_BUS.register(new PowerHud());
        MinecraftForge.EVENT_BUS.register(new SurvivalClient());
        SurvivalClient.init();
        PairRender.init();
        DebugToggles.init();
    }

    @Override
    public void onHud(final SkillNet.MsgHud msg) {
        Minecraft.getMinecraft().addScheduledTask(() -> SkillHud.receive(msg));
    }

    @Override
    public void onFx(final SkillNet.MsgFx msg) {
        Minecraft.getMinecraft().addScheduledTask(() -> FxClient.play(msg));
    }

    @Override
    public void onShake(final SkillNet.MsgShake msg) {
        Minecraft.getMinecraft().addScheduledTask(() -> FxClient.shake(msg.power, msg.ticks));
    }

    @Override
    public void onMoon(final boolean on) {
        Minecraft.getMinecraft().addScheduledTask(() -> ZombieClient.bloodMoon = on);
    }

    @Override
    public void onStats(final SkillNet.MsgStats msg) {
        Minecraft.getMinecraft().addScheduledTask(() -> GuiStats.data = msg);
    }

    @Override
    public void onQuestDefs(final SkillNet.MsgQuestDefs msg) {
        Minecraft.getMinecraft().addScheduledTask(() -> GuiJournal.receiveDefs(msg));
    }

    @Override
    public void onQuestProg(final SkillNet.MsgQuestProg msg) {
        Minecraft.getMinecraft().addScheduledTask(() -> GuiJournal.receiveProgress(msg.json));
    }

    @Override
    public void onOpen(final SkillNet.MsgOpen msg) {
        Minecraft.getMinecraft().addScheduledTask(() -> Minecraft.getMinecraft().displayGuiScreen(
                msg.kind == 2 ? new GuiGrimoire() : msg.kind == UABlock.JEWELER ? new GuiJeweler() : new GuiShop(msg.json)));
    }

    @Override
    public void onWeave(final SkillNet.MsgWeave msg) {
        Minecraft.getMinecraft().addScheduledTask(() -> GuiWeave.receive(msg.tag));
    }

    @Override
    public void openPage(final net.minecraft.nbt.NBTTagCompound page) {
        Minecraft.getMinecraft().displayGuiScreen(GuiBlocks.forPage(page));
    }

    @Override
    public void onSurv(final SkillNet.MsgSurv msg) {
        net.minecraft.client.Minecraft.getMinecraft().addScheduledTask(() -> SurvivalClient.onMsg(msg));
    }

    @Override
    public void onPower(final SkillNet.MsgPower msg) {
        Minecraft.getMinecraft().addScheduledTask(() -> PowerHud.receive(msg));
    }

    @Override
    public void boltTrail(Entity bolt, int element, float size, int kind) {
        FxClient.boltTrail(bolt, element, size, kind);
    }
}
