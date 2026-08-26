package mtr.render;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.math.Matrix4f;
import com.mojang.math.Vector3f;
import mtr.data.IGui;
import mtr.data.RailwayData;
import mtr.data.Train;
import mtr.data.TrainClient;
import mtr.mappings.UtilitiesClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;

public class RenderDrivingOverlay implements IGui {

	private static TrainClient trainClient;
	private static int coolDown;

	private static final int EDGE_PADDING = 16;
	private static final int TOOL_SIZE = 96;
	private static final int RADIUS = TOOL_SIZE / 2;
	private static final int SPEEDOMETER_CIRCLE_INTERVAL = 3;
	private static final double SPEEDOMETER_CIRCLE_EDGE_LENGTH = Math.tan(Math.toRadians(SPEEDOMETER_CIRCLE_INTERVAL) / 2) * TOOL_SIZE + 0.5;
	private static final int SPEEDOMETER_START_ANGLE = -60;
	private static final int SPEEDOMETER_SPAN = 300;
	private static final int SPEEDOMETER_TICK_INTERVAL = 5;

	private static final int PLATFORM_BAR_WIDTH = 6;
	private static final int PLATFORM_BAR_HEIGHT = 120;
	private static final int PLATFORM_BAR_OFFSET_Y = 20;
	private static final int TEXT_PADDING = 4;

	private static final int BLUE_COLOR = 0xFFAACCFF;
	private static final int ORANGE_COLOR = 0xFFFF9900;
	private static final int GREEN_COLOR = 0xFF00FF00;
	private static final int RED_COLOR = 0xFFFF0000;
	private static final int DARK_GRAY = 0xFF333333;
	private static final int LIGHT_GRAY = 0xFFAAAAAA;

	public static void render(PoseStack poseStack) {
		final Minecraft client = Minecraft.getInstance();
		final LocalPlayer player = client.player;
		if (player == null || trainClient == null || coolDown <= 0) {
			return;
		}
		coolDown--;

		if (!Train.isHoldingKey(player) || !trainClient.isPlayerRiding(player)) {
			return;
		}

		final Window window = client.getWindow();
		if (window == null) return;

		final int screenWidth = window.getGuiScaledWidth();
		final int screenHeight = window.getGuiScaledHeight();

		renderPlatformBar(poseStack, client, screenWidth, screenHeight);
		renderSpeedometer(poseStack, client, screenWidth, screenHeight);
		renderStationInfo(poseStack, client, screenWidth, screenHeight);
	}

	private static void renderPlatformBar(PoseStack poseStack, Minecraft client, int screenWidth, int screenHeight) {
		final double distanceToStop = trainClient.getDistanceToNextStop();
		if (distanceToStop == -1 || distanceToStop < -5) return;

		final int barX = EDGE_PADDING;
		final int barY = screenHeight / 2 - PLATFORM_BAR_HEIGHT / 2;
		final int zeroLineOffset = 12;

		fillRect(poseStack, barX, barY, barX + PLATFORM_BAR_WIDTH, barY + PLATFORM_BAR_HEIGHT, 0x80000000);

		final int zeroLineY = barY + zeroLineOffset;
		fillRect(poseStack, barX, zeroLineY, barX + PLATFORM_BAR_WIDTH, zeroLineY + 1, 0x40FFFFFF);

		final double maxDisplayDistance = 500.0;
		final double clampedDistance = Mth.clamp(distanceToStop, -5.0, maxDisplayDistance);

		final int indicatorY;
		if (clampedDistance >= 0) {
			indicatorY = barY + zeroLineOffset + (int) ((clampedDistance / maxDisplayDistance) * (PLATFORM_BAR_HEIGHT - zeroLineOffset));
		} else {
			indicatorY = barY + zeroLineOffset + (int) ((clampedDistance / 5.0) * zeroLineOffset);
		}

		final int visibleIndicatorY = Mth.clamp(indicatorY, barY, barY + PLATFORM_BAR_HEIGHT);

		fillRect(poseStack, barX - 1, visibleIndicatorY, barX + PLATFORM_BAR_WIDTH + 1, visibleIndicatorY + 1, RED_COLOR);

		final String distanceText = RailwayData.round(distanceToStop, 1) + " m";
		final int textX = barX + PLATFORM_BAR_WIDTH + TEXT_PADDING;
		final int textY = visibleIndicatorY - client.font.lineHeight / 2;
		client.font.drawShadow(poseStack, distanceText, textX, textY, ARGB_WHITE);
	}

	private static void renderSpeedometer(PoseStack poseStack, Minecraft client, int screenWidth, int screenHeight) {
		final int centerX = screenWidth - RADIUS - EDGE_PADDING;
		final int centerY = screenHeight - RADIUS - EDGE_PADDING;

		poseStack.pushPose();
		poseStack.translate(centerX, centerY, 0);

		final float speed = trainClient.getSpeed();
		final double speedKmh = speed * 20 * 3.6;
		final float doorValue = trainClient.getDoorValue();
		final int manualNotch = trainClient.getManualNotch();
		final boolean isManual = trainClient.isCurrentlyManual();

		final int rawMaxSpeedKmh = trainClient.getMaxManualSpeedKmh();
		final int maxSpeedKmh = rawMaxSpeedKmh > 0 ? rawMaxSpeedKmh : 120;

		RenderSystem.enableBlend();
		final Tesselator tesselator = Tesselator.getInstance();
		final BufferBuilder buffer = tesselator.getBuilder();
		final float halfEdge = (float) SPEEDOMETER_CIRCLE_EDGE_LENGTH / 2;

		poseStack.pushPose();
		for (int i = 0; i < 180; i += SPEEDOMETER_CIRCLE_INTERVAL) {
			final Matrix4f pose = poseStack.last().pose();
			UtilitiesClient.beginDrawingRectangle(buffer);
			drawRectangle(buffer, pose, -RADIUS, -halfEdge, RADIUS, halfEdge, 0xFFAAAAAA);
			drawRectangle(buffer, pose, -RADIUS + 1, -halfEdge, RADIUS - 1, halfEdge, 0xFF222222);
			tesselator.end();
			UtilitiesClient.finishDrawingRectangle();
			poseStack.mulPose(Vector3f.ZP.rotationDegrees(SPEEDOMETER_CIRCLE_INTERVAL));
		}
		poseStack.popPose();

		poseStack.pushPose();
		poseStack.mulPose(Vector3f.ZP.rotationDegrees(SPEEDOMETER_START_ANGLE));
		for (int i = 0; i <= maxSpeedKmh; i += SPEEDOMETER_TICK_INTERVAL) {
			final boolean isMajor = (i % 20 == 0);
			final int tickLength = isMajor ? 8 : 4;
			fillRect(poseStack, -RADIUS + 2, -1, -RADIUS + 2 + tickLength, 1, LIGHT_GRAY);
			poseStack.mulPose(Vector3f.ZP.rotationDegrees((float) SPEEDOMETER_TICK_INTERVAL * SPEEDOMETER_SPAN / maxSpeedKmh));
		}
		poseStack.popPose();

		poseStack.pushPose();
		poseStack.mulPose(Vector3f.ZP.rotationDegrees(SPEEDOMETER_START_ANGLE));
		for (int i = 0; i <= maxSpeedKmh; i += 20) {
			poseStack.pushPose();
			poseStack.translate(-RADIUS + 12, 0, 0);
			poseStack.mulPose(Vector3f.ZP.rotationDegrees(-SPEEDOMETER_START_ANGLE - (float) i * SPEEDOMETER_SPAN / maxSpeedKmh));
			poseStack.scale(0.5F, 0.5F, 1);
			final String label = String.valueOf(i);
			final int width = client.font.width(label);
			client.font.drawShadow(poseStack, label, -width / 2f, -4, ARGB_WHITE);
			poseStack.popPose();
			poseStack.mulPose(Vector3f.ZP.rotationDegrees(20F * SPEEDOMETER_SPAN / maxSpeedKmh));
		}
		poseStack.popPose();

		poseStack.pushPose();
		final float needleAngle = SPEEDOMETER_START_ANGLE + (float) speedKmh * SPEEDOMETER_SPAN / maxSpeedKmh;
		poseStack.mulPose(Vector3f.ZP.rotationDegrees(needleAngle));
		final Matrix4f needlePose = poseStack.last().pose();
		UtilitiesClient.beginDrawingRectangle(buffer);
		drawRectangle(buffer, needlePose, -RADIUS + 4, -1, 0, 1, RED_COLOR);
		tesselator.end();
		UtilitiesClient.finishDrawingRectangle();
		poseStack.popPose();

		fillRect(poseStack, -2, -2, 2, 2, 0xFFFFFFFF);

		poseStack.pushPose();
		poseStack.translate(-RADIUS * 0.3, -TOOL_SIZE * 0.1, 0);
		String notchText;
		int notchColor;
		if (manualNotch <= Train.EB) {
			notchText = "EB";
			notchColor = RED_COLOR;
		} else if (manualNotch < 0) {
			notchText = "B" + (-manualNotch);
			notchColor = ORANGE_COLOR;
		} else if (manualNotch > 0) {
			notchText = "P" + manualNotch;
			notchColor = BLUE_COLOR;
		} else {
			notchText = "N";
			notchColor = ARGB_WHITE;
		}
		drawCenteredText(poseStack, client, notchText, notchColor);
		if (manualNotch != 0 && manualNotch > Train.EB) {
			poseStack.translate(0, 8, 0);
			poseStack.scale(0.5F, 0.5F, 1);
			final int powerPercent = Math.round(Math.abs(Train.getManualNotchAccelerationMultiplier(manualNotch)) * 100);
			drawCenteredText(poseStack, client, "(" + powerPercent + "%)", notchColor);
		}
		poseStack.popPose();

		poseStack.pushPose();
		poseStack.translate(0, -TOOL_SIZE * 0.25, 0);
		drawCenteredText(poseStack, client, "MANUAL", isManual ? GREEN_COLOR : DARK_GRAY);
		poseStack.popPose();

		poseStack.pushPose();
		poseStack.translate(RADIUS * 0.3, -TOOL_SIZE * 0.1, 0);
		final String doorState = doorValue > 0 ? "DO" : "DC";
		drawCenteredText(poseStack, client, doorState, ARGB_WHITE);
		poseStack.translate(0, 8, 0);
		poseStack.scale(0.5F, 0.5F, 1);
		drawCenteredText(poseStack, client, "(" + (int) (doorValue * 100) + "%)", ARGB_WHITE);
		poseStack.popPose();

		poseStack.pushPose();
		poseStack.translate(0, TOOL_SIZE * 0.1, 0);
		drawCenteredText(poseStack, client, RailwayData.round(speedKmh, 1) + "", ARGB_WHITE);
		poseStack.translate(0, 8, 0);
		poseStack.scale(0.5F, 0.5F, 1);
		drawCenteredText(poseStack, client, "km/h", ARGB_WHITE);
		poseStack.popPose();

		poseStack.popPose();
		RenderSystem.disableBlend();
	}

	private static void renderStationInfo(PoseStack poseStack, Minecraft client, int screenWidth, int screenHeight) {
		final String thisStation = trainClient.getThisStation() != null ? IGui.formatStationName(trainClient.getThisStation().name) : null;
		final String nextStation = trainClient.getNextStation() != null ? IGui.formatStationName(trainClient.getNextStation().name) : null;
		final String thisRoute = trainClient.getThisRoute() != null ? IGui.formatStationName(trainClient.getThisRoute().name) : null;
		final String lastStation = trainClient.getLastStation() != null ? IGui.formatStationName(trainClient.getLastStation().name) : null;

		final int barY = screenHeight / 2 - PLATFORM_BAR_HEIGHT / 2;
		final int barBottomY = barY + PLATFORM_BAR_HEIGHT;

		final int textX = EDGE_PADDING;
		int textY = barBottomY + TEXT_PADDING * 2;

		if (thisStation != null) {
			client.font.drawShadow(poseStack, thisStation, textX, textY, ARGB_WHITE);
			textY += client.font.lineHeight + 2;
		}
		if (nextStation != null) {
			client.font.drawShadow(poseStack, "> " + nextStation, textX, textY, ARGB_WHITE);
			textY += client.font.lineHeight + 2;
		}
		if (thisRoute != null) {
			client.font.drawShadow(poseStack, thisRoute, textX, textY, ARGB_WHITE);
			textY += client.font.lineHeight + 2;
		}
		if (lastStation != null) {
			client.font.drawShadow(poseStack, "> " + lastStation, textX, textY, ARGB_WHITE);
		}
	}

	public static void setData(int accelerationSign, TrainClient trainClient) {
		RenderDrivingOverlay.trainClient = trainClient;
		coolDown = 2;
	}

	private static void drawCenteredText(PoseStack poseStack, Minecraft client, String text, int color) {
		final int width = client.font.width(text);
		client.font.drawShadow(poseStack, text, -width / 2f, -client.font.lineHeight / 2f, color);
	}

	private static void drawRectangle(BufferBuilder buffer, Matrix4f pose, float x1, float y1, float x2, float y2, int color) {
		final int a = (color >> 24) & 0xFF;
		final int r = (color >> 16) & 0xFF;
		final int g = (color >> 8) & 0xFF;
		final int b = color & 0xFF;
		buffer.vertex(pose, x1, y1, 0.0f).color(r, g, b, a).endVertex();
		buffer.vertex(pose, x1, y2, 0.0f).color(r, g, b, a).endVertex();
		buffer.vertex(pose, x2, y2, 0.0f).color(r, g, b, a).endVertex();
		buffer.vertex(pose, x2, y1, 0.0f).color(r, g, b, a).endVertex();
	}

	private static void fillRect(PoseStack poseStack, int x1, int y1, int x2, int y2, int color) {
		Matrix4f matrix = poseStack.last().pose();
		Tesselator tesselator = Tesselator.getInstance();
		BufferBuilder buffer = tesselator.getBuilder();
		UtilitiesClient.beginDrawingRectangle(buffer);
		drawRectangle(buffer, matrix, x1, y1, x2, y2, color);
		tesselator.end();
		UtilitiesClient.finishDrawingRectangle();
	}
}