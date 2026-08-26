package mtr.screen;

import io.netty.buffer.Unpooled;
import mtr.RegistryClient;
import mtr.client.ClientData;
import mtr.data.Rail;
import mtr.data.RailType;
import mtr.mappings.Text;
import mtr.packet.IPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RailDataEditorClient {

	private RailDataEditorClient() {
	}

	public static void sendUpdateC2S(Map<String, String> railData, BlockPos posStart, BlockPos posEnd) {
		final FriendlyByteBuf packet = new FriendlyByteBuf(Unpooled.buffer());
		packet.writeResourceLocation(Minecraft.getInstance().level.dimension().location());
		packet.writeBlockPos(posStart);
		packet.writeBlockPos(posEnd);
		writeStringMap(packet, railData);
		RegistryClient.sendToServer(IPacket.PACKET_UPDATE_RAIL_DATA, packet);
	}

	private static void writeStringMap(FriendlyByteBuf packet, Map<String, String> map) {
		packet.writeInt(map.size());
		for (Map.Entry<String, String> entry : map.entrySet()) {
			packet.writeUtf(entry.getKey());
			packet.writeUtf(entry.getValue());
		}
	}

	public static void open(BlockPos pos) {
		Minecraft.getInstance().execute(() -> {
			final List<RailInfo> rails = collectRailsAtNode(pos);
			if (rails.isEmpty()) {
				final Player player = Minecraft.getInstance().player;
				if (player != null) {
					player.displayClientMessage(Text.translatable("gui.mtr.rail_data_editor.no_rail"), true);
				}
			} else {
				Minecraft.getInstance().setScreen(new RailDataEditorScreen(rails));
			}
		});
	}

	public static List<RailInfo> collectRailsAtNode(BlockPos pos) {
		final Map<Long, RailInfo> result = new LinkedHashMap<>();
		final Map<BlockPos, Rail> fromPos = ClientData.RAILS.get(pos);
		if (fromPos != null) {
			for (Map.Entry<BlockPos, Rail> entry : fromPos.entrySet()) {
				final Rail rail = entry.getValue();
				if (rail.railType == RailType.NONE) {
					continue;
				}
				result.put(railKey(pos, entry.getKey()), new RailInfo(rail, pos, entry.getKey()));
			}
		}
		for (Map.Entry<BlockPos, Map<BlockPos, Rail>> outer : ClientData.RAILS.entrySet()) {
			final Rail rail = outer.getValue().get(pos);
			if (rail != null && rail.railType != RailType.NONE) {
				result.put(railKey(outer.getKey(), pos), new RailInfo(rail, outer.getKey(), pos));
			}
		}
		return new ArrayList<>(result.values());
	}

	private static long railKey(BlockPos posStart, BlockPos posEnd) {
		final long start = posStart.asLong();
		final long end = posEnd.asLong();
		return start < end ? start * 31L + end : end * 31L + start;
	}

	public static class RailInfo {
		public final Rail rail;
		public final BlockPos posStart;
		public final BlockPos posEnd;

		public RailInfo(Rail rail, BlockPos posStart, BlockPos posEnd) {
			this.rail = rail;
			this.posStart = posStart;
			this.posEnd = posEnd;
		}
	}
}
