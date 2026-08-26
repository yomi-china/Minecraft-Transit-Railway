package mtr.item;

import mtr.CreativeModeTabs;
import mtr.block.BlockNode;
import mtr.mappings.Text;
import mtr.screen.RailDataEditorClient;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

public class ItemRailDataEditor extends ItemWithCreativeTabBase {

	public ItemRailDataEditor() {
		super(CreativeModeTabs.CORE, properties -> properties.stacksTo(1));
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		final Level level = context.getLevel();
		final BlockPos pos = context.getClickedPos();
		if (!(level.getBlockState(pos).getBlock() instanceof BlockNode)) {
			return super.useOn(context);
		}
		if (level.isClientSide) {
			RailDataEditorClient.open(pos);
		}
		return InteractionResult.SUCCESS;
	}
}