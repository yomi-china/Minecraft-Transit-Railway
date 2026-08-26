package mtr.packet;

import io.netty.buffer.Unpooled;

import mtr.client.AnteRailCompat;
import mtr.data.Rail;
import mtr.data.RailwayData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;

public class PacketUpdateRailData {

	public static void openRailDataEditorS2C(ServerPlayer player, BlockPos pos) {
		final FriendlyByteBuf packet = new FriendlyByteBuf(Unpooled.buffer());
		packet.writeBlockPos(pos);
		mtr.Registry.sendToPlayer(player, IPacket.PACKET_OPEN_RAIL_DATA_EDITOR, packet);
	}

	public static void receiveUpdateC2S(MinecraftServer server, ServerPlayer player, FriendlyByteBuf packet) {
		if (RailwayData.hasNoPermission(player)) {
			return;
		}
		final ResourceKey<Level> levelKey = packet.readResourceKey(Registry.DIMENSION_REGISTRY);
		final BlockPos posStart = packet.readBlockPos();
		final BlockPos posEnd = packet.readBlockPos();
		final Map<String, String> railData = readStringMap(packet);
		server.execute(() -> {
			final ServerLevel level = server.getLevel(levelKey);
			if (level == null) {
				return;
			}
			final RailwayData railwayData = RailwayData.getInstance(level);
			if (railwayData == null) {
				return;
			}
			final Map<BlockPos, Map<BlockPos, Rail>> rails = railwayData.getRailsMap();
			final Map<BlockPos, Rail> railsFromStart = rails.get(posStart);
			final Map<BlockPos, Rail> railsFromEnd = rails.get(posEnd);
			if (railsFromStart == null || railsFromEnd == null) {
				return;
			}
			final Rail railForward = railsFromStart.get(posEnd);
			final Rail railBackward = railsFromEnd.get(posStart);
			if (railForward == null || railBackward == null) {
				return;
			}
			railForward.setRailData(railData);
			railBackward.setRailData(railData);
			AnteRailCompat.setRailCustomConfigsMirror(railForward, railData);
			AnteRailCompat.setRailCustomConfigsMirror(railBackward, railData);
			PacketTrainDataGuiServer.createRailS2C(level, railForward.transportMode, posStart, posEnd, railForward, railBackward, 0);
			railwayData.setDirty();
		});
	}

	private static Map<String, String> readStringMap(FriendlyByteBuf packet) {
		final Map<String, String> map = new HashMap<>();
		final int size = packet.readInt();
		for (int i = 0; i < size; i++) {
			map.put(packet.readUtf(), packet.readUtf());
		}
		return map;
	}
}
