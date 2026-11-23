package com.imba.undyingarcana;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

/** Network channel of the weapon skill system: key presses up, HUD state / VFX / camera shake down. */
public final class SkillNet {
    public static final SimpleNetworkWrapper NET = NetworkRegistry.INSTANCE.newSimpleChannel(UndyingArcana.MODID);

    private SkillNet() {
    }

    public static void init() {
        NET.registerMessage(KeyHandler.class, MsgKey.class, 0, Side.SERVER);
        NET.registerMessage(HudHandler.class, MsgHud.class, 1, Side.CLIENT);
        NET.registerMessage(FxHandler.class, MsgFx.class, 2, Side.CLIENT);
        NET.registerMessage(ShakeHandler.class, MsgShake.class, 3, Side.CLIENT);
        NET.registerMessage(MoonHandler.class, MsgMoon.class, 4, Side.CLIENT);
        NET.registerMessage(StatsHandler.class, MsgStats.class, 5, Side.CLIENT);
        NET.registerMessage(StatActHandler.class, MsgStatAct.class, 6, Side.SERVER);
        NET.registerMessage(QuestDefsHandler.class, MsgQuestDefs.class, 7, Side.CLIENT);
        NET.registerMessage(QuestProgHandler.class, MsgQuestProg.class, 8, Side.CLIENT);
        NET.registerMessage(QuestActHandler.class, MsgQuestAct.class, 9, Side.SERVER);
        NET.registerMessage(OpenHandler.class, MsgOpen.class, 10, Side.CLIENT);
        NET.registerMessage(BenchHandler.class, MsgBench.class, 11, Side.SERVER);
        NET.registerMessage(BuyHandler.class, MsgBuy.class, 12, Side.SERVER);
        NET.registerMessage(PowerHandler.class, MsgPower.class, 13, Side.CLIENT);
        NET.registerMessage(ActHandler.class, MsgAct.class, 14, Side.SERVER);
        NET.registerMessage(SurvHandler.class, MsgSurv.class, 15, Side.CLIENT);
        NET.registerMessage(DrinkHandler.class, MsgDrink.class, 16, Side.SERVER);
        NET.registerMessage(WeaveHandler.class, MsgWeave.class, 17, Side.CLIENT);
        NET.registerMessage(WeaveSaveHandler.class, MsgWeaveSave.class, 18, Side.SERVER);
    }

    // ------------------------------------------------------------------ key (client -> server)
    /** slot 0 = Quick, 1 = Heavy, 2 = Art; down = pressed (true) or released (false). */
    public static class MsgKey implements IMessage {
        public int slot;
        public boolean down;

        public MsgKey() {
        }

        public MsgKey(int slot, boolean down) {
            this.slot = slot;
            this.down = down;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            slot = buf.readByte();
            down = buf.readBoolean();
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeByte(slot);
            buf.writeBoolean(down);
        }
    }

    public static class KeyHandler implements IMessageHandler<MsgKey, IMessage> {
        @Override
        public IMessage onMessage(final MsgKey msg, MessageContext ctx) {
            final EntityPlayerMP p = ctx.getServerHandler().player;
            p.getServerWorld().addScheduledTask(() -> SkillServer.onKey(p, msg.slot, msg.down));
            return null;
        }
    }

    // ------------------------------------------------------------------ HUD (server -> client)
    public static class MsgHud implements IMessage {
        public boolean active;
        public String type = "";
        public String header = "";
        public String[] icon = {"", "", ""};
        public String[] name = {"", "", ""};
        public int[] cdLeft = new int[3];
        public int[] cdTotal = new int[3];
        public boolean[] unlocked = new boolean[3];
        public int[] need = new int[3];
        public int chargeEl = -1;
        public int chargeNeed = 0;
        public boolean gun;
        public int mag, magMax, reserve, reloadLeft, reloadTotal;

        @Override
        public void fromBytes(ByteBuf buf) {
            active = buf.readBoolean();
            if (!active) {
                return;
            }
            type = ByteBufUtils.readUTF8String(buf);
            header = ByteBufUtils.readUTF8String(buf);
            for (int i = 0; i < 3; i++) {
                icon[i] = ByteBufUtils.readUTF8String(buf);
                name[i] = ByteBufUtils.readUTF8String(buf);
                cdLeft[i] = buf.readInt();
                cdTotal[i] = buf.readInt();
                unlocked[i] = buf.readBoolean();
                need[i] = buf.readShort();
            }
            chargeEl = buf.readInt();
            chargeNeed = buf.readInt();
            gun = buf.readBoolean();
            if (gun) {
                mag = buf.readShort();
                magMax = buf.readShort();
                reserve = buf.readShort();
                reloadLeft = buf.readShort();
                reloadTotal = buf.readShort();
            }
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeBoolean(active);
            if (!active) {
                return;
            }
            ByteBufUtils.writeUTF8String(buf, type);
            ByteBufUtils.writeUTF8String(buf, header);
            for (int i = 0; i < 3; i++) {
                ByteBufUtils.writeUTF8String(buf, icon[i]);
                ByteBufUtils.writeUTF8String(buf, name[i]);
                buf.writeInt(cdLeft[i]);
                buf.writeInt(cdTotal[i]);
                buf.writeBoolean(unlocked[i]);
                buf.writeShort(need[i]);
            }
            buf.writeInt(chargeEl);
            buf.writeInt(chargeNeed);
            buf.writeBoolean(gun);
            if (gun) {
                buf.writeShort(mag);
                buf.writeShort(magMax);
                buf.writeShort(Math.min(32000, reserve));
                buf.writeShort(reloadLeft);
                buf.writeShort(reloadTotal);
            }
        }
    }

    public static class HudHandler implements IMessageHandler<MsgHud, IMessage> {
        @Override
        public IMessage onMessage(MsgHud msg, MessageContext ctx) {
            UndyingArcana.proxy.onHud(msg);
            return null;
        }
    }

    // ------------------------------------------------------------------ VFX (server -> client)
    public static class MsgFx implements IMessage {
        public int kind;
        public int element;
        public float x, y, z;
        public float ax, ay, az;
        public float size;
        public float extra;
        public int seed;
        public int dur;
        public int entity = -1;

        public MsgFx() {
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            kind = buf.readByte();
            element = buf.readByte();
            x = buf.readFloat();
            y = buf.readFloat();
            z = buf.readFloat();
            ax = buf.readFloat();
            ay = buf.readFloat();
            az = buf.readFloat();
            size = buf.readFloat();
            extra = buf.readFloat();
            seed = buf.readInt();
            dur = buf.readShort();
            entity = buf.readInt();
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeByte(kind);
            buf.writeByte(element);
            buf.writeFloat(x);
            buf.writeFloat(y);
            buf.writeFloat(z);
            buf.writeFloat(ax);
            buf.writeFloat(ay);
            buf.writeFloat(az);
            buf.writeFloat(size);
            buf.writeFloat(extra);
            buf.writeInt(seed);
            buf.writeShort(dur);
            buf.writeInt(entity);
        }
    }

    public static class FxHandler implements IMessageHandler<MsgFx, IMessage> {
        @Override
        public IMessage onMessage(MsgFx msg, MessageContext ctx) {
            UndyingArcana.proxy.onFx(msg);
            return null;
        }
    }

    // ------------------------------------------------------------------ camera shake (server -> client)
    public static class MsgShake implements IMessage {
        public float power;
        public int ticks;

        public MsgShake() {
        }

        public MsgShake(float power, int ticks) {
            this.power = power;
            this.ticks = ticks;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            power = buf.readFloat();
            ticks = buf.readShort();
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeFloat(power);
            buf.writeShort(ticks);
        }
    }

    public static class ShakeHandler implements IMessageHandler<MsgShake, IMessage> {
        @Override
        public IMessage onMessage(MsgShake msg, MessageContext ctx) {
            UndyingArcana.proxy.onShake(msg);
            return null;
        }
    }

    // ------------------------------------------------------------------ Blood Moon state (server -> client)
    public static class MsgMoon implements IMessage {
        public boolean on;

        public MsgMoon() {
        }

        public MsgMoon(boolean on) {
            this.on = on;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            on = buf.readBoolean();
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeBoolean(on);
        }
    }

    public static class MoonHandler implements IMessageHandler<MsgMoon, IMessage> {
        @Override
        public IMessage onMessage(MsgMoon msg, MessageContext ctx) {
            UndyingArcana.proxy.onMoon(msg.on);
            return null;
        }
    }

    // ------------------------------------------------------------------ character stats (server -> client)
    public static class MsgStats implements IMessage {
        public boolean on;
        public int level, xp, build;
        public int[] s = new int[Builds.COUNT];

        public MsgStats() {
        }

        public MsgStats(boolean on, int level, int xp, int[] s, int build) {
            this.on = on;
            this.level = level;
            this.xp = xp;
            this.s = s;
            this.build = build;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            on = buf.readBoolean();
            level = buf.readShort();
            xp = buf.readInt();
            for (int i = 0; i < Builds.COUNT; i++) {
                s[i] = buf.readShort();
            }
            build = buf.readByte();
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeBoolean(on);
            buf.writeShort(level);
            buf.writeInt(xp);
            for (int i = 0; i < Builds.COUNT; i++) {
                buf.writeShort(s[i]);
            }
            buf.writeByte(build);
        }
    }

    public static class StatsHandler implements IMessageHandler<MsgStats, IMessage> {
        @Override
        public IMessage onMessage(MsgStats msg, MessageContext ctx) {
            UndyingArcana.proxy.onStats(msg);
            return null;
        }
    }

    // ------------------------------------------------------------------ spend / refund / rebirth (client -> server)
    public static class MsgStatAct implements IMessage {
        public int op, stat, amount;

        public MsgStatAct() {
        }

        public MsgStatAct(int op, int stat, int amount) {
            this.op = op;
            this.stat = stat;
            this.amount = amount;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            op = buf.readByte();
            stat = buf.readByte();
            amount = buf.readByte();
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeByte(op);
            buf.writeByte(stat);
            buf.writeByte(amount);
        }
    }

    public static class StatActHandler implements IMessageHandler<MsgStatAct, IMessage> {
        @Override
        public IMessage onMessage(MsgStatAct msg, MessageContext ctx) {
            final EntityPlayerMP p = ctx.getServerHandler().player;
            p.getServerWorld().addScheduledTask(() -> Stats.act(p, msg.op, msg.stat, msg.amount));
            return null;
        }
    }

    // ------------------------------------------------------------------ quest Journal
    public static class MsgQuestDefs implements IMessage {
        public boolean on;
        public int index, total;
        public String part = "";

        public MsgQuestDefs() {
        }

        public MsgQuestDefs(boolean on, int index, int total, String part) {
            this.on = on;
            this.index = index;
            this.total = total;
            this.part = part;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            on = buf.readBoolean();
            index = buf.readShort();
            total = buf.readShort();
            part = ByteBufUtils.readUTF8String(buf);
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeBoolean(on);
            buf.writeShort(index);
            buf.writeShort(total);
            ByteBufUtils.writeUTF8String(buf, part);
        }
    }

    public static class QuestDefsHandler implements IMessageHandler<MsgQuestDefs, IMessage> {
        @Override
        public IMessage onMessage(MsgQuestDefs msg, MessageContext ctx) {
            UndyingArcana.proxy.onQuestDefs(msg);
            return null;
        }
    }

    public static class MsgQuestProg implements IMessage {
        public String json = "";

        public MsgQuestProg() {
        }

        public MsgQuestProg(String json) {
            this.json = json;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            json = ByteBufUtils.readUTF8String(buf);
        }

        @Override
        public void toBytes(ByteBuf buf) {
            ByteBufUtils.writeUTF8String(buf, json);
        }
    }

    public static class QuestProgHandler implements IMessageHandler<MsgQuestProg, IMessage> {
        @Override
        public IMessage onMessage(MsgQuestProg msg, MessageContext ctx) {
            UndyingArcana.proxy.onQuestProg(msg);
            return null;
        }
    }

    public static class MsgQuestAct implements IMessage {
        public int op;
        public String id = "";

        public MsgQuestAct() {
        }

        public MsgQuestAct(int op, String id) {
            this.op = op;
            this.id = id;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            op = buf.readByte();
            id = ByteBufUtils.readUTF8String(buf);
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeByte(op);
            ByteBufUtils.writeUTF8String(buf, id);
        }
    }

    public static class QuestActHandler implements IMessageHandler<MsgQuestAct, IMessage> {
        @Override
        public IMessage onMessage(MsgQuestAct msg, MessageContext ctx) {
            final EntityPlayerMP p = ctx.getServerHandler().player;
            p.getServerWorld().addScheduledTask(() -> Quests.act(p, msg.op, msg.id));
            return null;
        }
    }

    // ------------------------------------------------------------------ work blocks
    public static class MsgOpen implements IMessage {
        public int kind;
        public String json = "";

        public MsgOpen() {
        }

        public MsgOpen(int kind, String json) {
            this.kind = kind;
            this.json = json;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            kind = buf.readByte();
            json = ByteBufUtils.readUTF8String(buf);
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeByte(kind);
            ByteBufUtils.writeUTF8String(buf, json);
        }
    }

    public static class OpenHandler implements IMessageHandler<MsgOpen, IMessage> {
        @Override
        public IMessage onMessage(MsgOpen msg, MessageContext ctx) {
            UndyingArcana.proxy.onOpen(msg);
            return null;
        }
    }

    public static class MsgBench implements IMessage {
        public int op, a;
        public int[] r = new int[9];

        public MsgBench() {
        }

        public MsgBench(int op, int a, int[] r) {
            this.op = op;
            this.a = a;
            this.r = r;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            op = buf.readByte();
            a = buf.readShort();
            for (int i = 0; i < 9; i++) {
                r[i] = buf.readByte();
            }
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeByte(op);
            buf.writeShort(a);
            for (int i = 0; i < 9; i++) {
                buf.writeByte(r[i]);
            }
        }
    }

    public static class BenchHandler implements IMessageHandler<MsgBench, IMessage> {
        @Override
        public IMessage onMessage(MsgBench msg, MessageContext ctx) {
            final EntityPlayerMP p = ctx.getServerHandler().player;
            p.getServerWorld().addScheduledTask(() -> Bench.act(p, msg.op, msg.a, msg.r));
            return null;
        }
    }

    public static class MsgBuy implements IMessage {
        public int index, times;

        public MsgBuy() {
        }

        public MsgBuy(int index, int times) {
            this.index = index;
            this.times = times;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            index = buf.readShort();
            times = buf.readByte();
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeShort(index);
            buf.writeByte(times);
        }
    }

    public static class BuyHandler implements IMessageHandler<MsgBuy, IMessage> {
        @Override
        public IMessage onMessage(MsgBuy msg, MessageContext ctx) {
            final EntityPlayerMP p = ctx.getServerHandler().player;
            p.getServerWorld().addScheduledTask(() -> Shop.buy(p, msg.index, msg.times));
            return null;
        }
    }

    /** Mana, awakening and Grimoire state for the HUD and the Grimoire screen. */
    public static class MsgPower implements IMessage {
        public boolean on, canAwaken, grimoire;
        public int mana, manaMax, awakeLeft, awakeCd;
        public byte[] plan = new byte[Builds.COUNT];
        public String elements = "-1,-1,0", spells = "";
        public int[] spellCd = new int[Magic.SPELLS];

        @Override
        public void fromBytes(ByteBuf buf) {
            on = buf.readBoolean();
            canAwaken = buf.readBoolean();
            grimoire = buf.readBoolean();
            mana = buf.readShort();
            manaMax = buf.readShort();
            awakeLeft = buf.readShort();
            awakeCd = buf.readShort();
            buf.readBytes(plan);
            elements = ByteBufUtils.readUTF8String(buf);
            spells = ByteBufUtils.readUTF8String(buf);
            for (int i = 0; i < Magic.SPELLS; i++) {
                spellCd[i] = buf.readShort();
            }
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeBoolean(on);
            buf.writeBoolean(canAwaken);
            buf.writeBoolean(grimoire);
            buf.writeShort(mana);
            buf.writeShort(manaMax);
            buf.writeShort(awakeLeft);
            buf.writeShort(awakeCd);
            buf.writeBytes(plan);
            ByteBufUtils.writeUTF8String(buf, elements);
            ByteBufUtils.writeUTF8String(buf, spells);
            for (int i = 0; i < Magic.SPELLS; i++) {
                buf.writeShort(Math.min(Short.MAX_VALUE, spellCd[i]));
            }
        }
    }

    public static class PowerHandler implements IMessageHandler<MsgPower, IMessage> {
        @Override
        public IMessage onMessage(MsgPower msg, MessageContext ctx) {
            UndyingArcana.proxy.onPower(msg);
            return null;
        }
    }

    /** Small client actions: 1 awaken, 2 pick element, 3 save spell. */
    public static class MsgAct implements IMessage {
        public int op, a;
        public String s = "";

        public MsgAct() {
        }

        public MsgAct(int op, int a, String s) {
            this.op = op;
            this.a = a;
            this.s = s;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            op = buf.readByte();
            a = buf.readShort();
            s = ByteBufUtils.readUTF8String(buf);
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeByte(op);
            buf.writeShort(a);
            ByteBufUtils.writeUTF8String(buf, s.length() > 64 ? s.substring(0, 64) : s);
        }
    }

    public static class ActHandler implements IMessageHandler<MsgAct, IMessage> {
        @Override
        public IMessage onMessage(MsgAct msg, MessageContext ctx) {
            final EntityPlayerMP p = ctx.getServerHandler().player;
            p.getServerWorld().addScheduledTask(() -> {
                if (msg.op == 1) {
                    Power.awaken(p);
                } else {
                    Magic.act(p, msg.op, msg.a, msg.s);
                }
            });
            return null;
        }
    }

    /** Thirst and temperature for the HUD. flags: 1 sick, 2 warm drink, 4 cool drink, 8 wet. */
    public static class MsgSurv implements IMessage {
        public boolean on;
        public float thirst, hydration, temp, target;
        public int flags;
        public byte[] nutrition = new byte[Food.GROUP.length];

        @Override
        public void fromBytes(ByteBuf buf) {
            on = buf.readBoolean();
            thirst = buf.readFloat();
            hydration = buf.readFloat();
            temp = buf.readFloat();
            target = buf.readFloat();
            flags = buf.readByte();
            buf.readBytes(nutrition);
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeBoolean(on);
            buf.writeFloat(thirst);
            buf.writeFloat(hydration);
            buf.writeFloat(temp);
            buf.writeFloat(target);
            buf.writeByte(flags);
            buf.writeBytes(nutrition);
        }
    }

    public static class SurvHandler implements IMessageHandler<MsgSurv, IMessage> {
        @Override
        public IMessage onMessage(MsgSurv msg, MessageContext ctx) {
            UndyingArcana.proxy.onSurv(msg);
            return null;
        }
    }

    /** Sneak + right-click drink: 0 water in view, 1 rain. */
    public static class MsgDrink implements IMessage {
        public int what;

        public MsgDrink() {
        }

        public MsgDrink(int what) {
            this.what = what;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            what = buf.readByte();
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeByte(what);
        }
    }

    public static class DrinkHandler implements IMessageHandler<MsgDrink, IMessage> {
        @Override
        public IMessage onMessage(MsgDrink msg, MessageContext ctx) {
            final EntityPlayerMP p = ctx.getServerHandler().player;
            p.getServerWorld().addScheduledTask(() -> Survival.drinkRequest(p, msg.what));
            return null;
        }
    }

    /** Spell Weave data for the editor (all graphs, mastery, limits). */
    public static class MsgWeave implements IMessage {
        public int mode, slot;
        public net.minecraft.nbt.NBTTagCompound tag = new net.minecraft.nbt.NBTTagCompound();

        public MsgWeave() {
        }

        public MsgWeave(int mode, int slot, net.minecraft.nbt.NBTTagCompound tag) {
            this.mode = mode;
            this.slot = slot;
            this.tag = tag;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            mode = buf.readByte();
            slot = buf.readByte();
            tag = ByteBufUtils.readTag(buf);
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeByte(mode);
            buf.writeByte(slot);
            ByteBufUtils.writeTag(buf, tag);
        }
    }

    public static class WeaveHandler implements IMessageHandler<MsgWeave, IMessage> {
        @Override
        public IMessage onMessage(MsgWeave msg, MessageContext ctx) {
            UndyingArcana.proxy.onWeave(msg);
            return null;
        }
    }

    /** Save a graph into a slot. */
    public static class MsgWeaveSave extends MsgWeave {
        public MsgWeaveSave() {
        }

        public MsgWeaveSave(int slot, net.minecraft.nbt.NBTTagCompound tag) {
            super(1, slot, tag);
        }
    }

    public static class WeaveSaveHandler implements IMessageHandler<MsgWeaveSave, IMessage> {
        @Override
        public IMessage onMessage(MsgWeaveSave msg, MessageContext ctx) {
            final EntityPlayerMP p = ctx.getServerHandler().player;
            p.getServerWorld().addScheduledTask(() -> Magic.saveGraph(p, msg.slot, msg.tag));
            return null;
        }
    }
}
