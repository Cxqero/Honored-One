package com.gojolimitless.net;

import com.gojolimitless.GojoLimitless;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** All custom packets. Registered on both sides from the common initializer. */
public final class Payloads {
    private Payloads() {}

    static <T extends CustomPayload> CustomPayload.Id<T> id(String path) {
        return new CustomPayload.Id<>(Identifier.of(GojoLimitless.MOD_ID, path));
    }

    /** Client → server: a technique key went down or up. */
    public record Input(int move, boolean pressed) implements CustomPayload {
        public static final Id<Input> ID = id("input");
        public static final PacketCodec<ByteBuf, Input> CODEC = CustomPayload.codecOf(
                (v, buf) -> { buf.writeByte(v.move); buf.writeBoolean(v.pressed); },
                buf -> new Input(buf.readByte(), buf.readBoolean()));
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    /** Client → server: toggle Infinity. */
    public record InfinityToggle() implements CustomPayload {
        public static final Id<InfinityToggle> ID = id("infinity_toggle");
        public static final PacketCodec<ByteBuf, InfinityToggle> CODEC = PacketCodec.unit(new InfinityToggle());
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    /** Server → client: Infinity state changed. */
    public record InfinityState(boolean on) implements CustomPayload {
        public static final Id<InfinityState> ID = id("infinity_state");
        public static final PacketCodec<ByteBuf, InfinityState> CODEC = CustomPayload.codecOf(
                (v, buf) -> buf.writeBoolean(v.on), buf -> new InfinityState(buf.readBoolean()));
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    /**
     * Server → client: blocks that were torn out, to be shown as flying debris.
     * mode: 0 = pulled into an entity (ownerId), 1 = blasted outward from origin, 2 = erased (brief dissolve).
     */
    public record Debris(int ownerId, int mode, double ox, double oy, double oz, int[] data) implements CustomPayload {
        public static final Id<Debris> ID = id("debris");
        public static final PacketCodec<PacketByteBuf, Debris> CODEC = CustomPayload.codecOf(Debris::write, Debris::read);
        @Override public Id<? extends CustomPayload> getId() { return ID; }

        /** data layout: [x, y, z, rawStateId] per block. */
        private void write(PacketByteBuf buf) {
            buf.writeVarInt(ownerId);
            buf.writeByte(mode);
            buf.writeDouble(ox); buf.writeDouble(oy); buf.writeDouble(oz);
            buf.writeVarInt(data.length / 4);
            for (int i = 0; i < data.length; i += 4) {
                buf.writeVarInt((int) Math.round(data[i] - ox));
                buf.writeVarInt((int) Math.round(data[i + 1] - oy));
                buf.writeVarInt((int) Math.round(data[i + 2] - oz));
                buf.writeVarInt(data[i + 3]);
            }
        }

        private static Debris read(PacketByteBuf buf) {
            int owner = buf.readVarInt();
            int mode = buf.readByte();
            double ox = buf.readDouble(), oy = buf.readDouble(), oz = buf.readDouble();
            int n = buf.readVarInt();
            int[] d = new int[n * 4];
            int bx = (int) Math.round(ox), by = (int) Math.round(oy), bz = (int) Math.round(oz);
            for (int i = 0; i < n; i++) {
                d[i * 4] = buf.readVarInt() + bx;
                d[i * 4 + 1] = buf.readVarInt() + by;
                d[i * 4 + 2] = buf.readVarInt() + bz;
                d[i * 4 + 3] = buf.readVarInt();
            }
            return new Debris(owner, mode, ox, oy, oz, d);
        }
    }

    /** Server → client: a one-shot visual/audio event (see {@link FxType}). */
    public record Fx(int type, double x, double y, double z, float a, float b, int entityId) implements CustomPayload {
        public static final Id<Fx> ID = id("fx");
        public static final PacketCodec<ByteBuf, Fx> CODEC = CustomPayload.codecOf(
                (v, buf) -> {
                    buf.writeByte(v.type);
                    buf.writeDouble(v.x); buf.writeDouble(v.y); buf.writeDouble(v.z);
                    buf.writeFloat(v.a); buf.writeFloat(v.b);
                    buf.writeInt(v.entityId);
                },
                buf -> new Fx(buf.readByte(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
                        buf.readFloat(), buf.readFloat(), buf.readInt()));
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    /** Server → client: a caster's technique stage changed (drives poses, HUD, titles). */
    public record CastState(int casterId, int move, int stage, int durationTicks) implements CustomPayload {
        public static final Id<CastState> ID = id("cast_state");
        public static final PacketCodec<ByteBuf, CastState> CODEC = CustomPayload.codecOf(
                (v, buf) -> { buf.writeInt(v.casterId); buf.writeByte(v.move); buf.writeByte(v.stage); buf.writeInt(v.durationTicks); },
                buf -> new CastState(buf.readInt(), buf.readByte(), buf.readByte(), buf.readInt()));
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    /** Server → client: start a cutscene anchored at the caster (position + yaw at cast time). */
    public record Cutscene(String name, double x, double y, double z, float yaw, int casterId) implements CustomPayload {
        public static final Id<Cutscene> ID = Payloads.id("cutscene");
        public static final PacketCodec<PacketByteBuf, Cutscene> CODEC = CustomPayload.codecOf(
                (v, buf) -> { buf.writeString(v.name); buf.writeDouble(v.x); buf.writeDouble(v.y); buf.writeDouble(v.z); buf.writeFloat(v.yaw); buf.writeInt(v.casterId); },
                buf -> new Cutscene(buf.readString(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat(), buf.readInt()));
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    public static void register() {
        PayloadTypeRegistry.playS2C().register(Cutscene.ID, Cutscene.CODEC);
        PayloadTypeRegistry.playC2S().register(Input.ID, Input.CODEC);
        PayloadTypeRegistry.playC2S().register(InfinityToggle.ID, InfinityToggle.CODEC);
        PayloadTypeRegistry.playS2C().register(InfinityState.ID, InfinityState.CODEC);
        PayloadTypeRegistry.playS2C().register(Debris.ID, Debris.CODEC);
        PayloadTypeRegistry.playS2C().register(Fx.ID, Fx.CODEC);
        PayloadTypeRegistry.playS2C().register(CastState.ID, CastState.CODEC);
    }
}
