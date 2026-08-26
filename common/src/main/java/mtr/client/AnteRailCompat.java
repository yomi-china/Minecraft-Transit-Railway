package mtr.client;

import mtr.data.Rail;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public class AnteRailCompat {

	private static final Logger LOGGER = LogManager.getLogger("mtr");

	private static final String CLASS_SUPPLIER = "cn.zbx1425.mtrsteamloco.data.RailExtraSupplier";

	private static boolean loaded = false;
	private static boolean checked = false;
	private static Class<?> supplierClass;
	private static Method getCustomConfigsMethod;
	private static Method setCustomConfigsMethod;

	private AnteRailCompat() {
	}

	public static boolean isAnteLoaded() {
		if (!checked) {
			checked = true;
			try {
				supplierClass = Class.forName(CLASS_SUPPLIER);
				loaded = true;
				try {
					getCustomConfigsMethod = supplierClass.getMethod("getCustomConfigs");
					setCustomConfigsMethod = supplierClass.getMethod("setCustomConfigs", Map.class);
				} catch (NoSuchMethodException e) {
					LOGGER.warn("ANTE RailExtraSupplier 方法签名与预期不符，轨道数据将无法镜像到 ANTE", e);
				}
			} catch (ClassNotFoundException e) {
				loaded = false;
			}
		}
		return loaded;
	}
	public static Map<String, String> getRailCustomConfigs(Rail rail) {
		final Map<String, String> result = new HashMap<>();
		if (!isAnteLoaded() || rail == null || getCustomConfigsMethod == null) {
			return result;
		}
		try {
			final Object map = getCustomConfigsMethod.invoke(supplierClass.cast(rail));
			if (map instanceof Map) {
				((Map<?, ?>) map).forEach((key, value) -> result.put(String.valueOf(key), value == null ? "" : String.valueOf(value)));
			}
		} catch (Exception e) {
			LOGGER.warn("读取 ANTE customConfigs 失败", e);
		}
		return result;
	}

	public static void setRailCustomConfigs(Rail rail, Map<String, String> customConfigs) {
		if (!isAnteLoaded() || rail == null || customConfigs == null || setCustomConfigsMethod == null) {
			return;
		}
		try {
			setCustomConfigsMethod.invoke(supplierClass.cast(rail), customConfigs);
		} catch (Exception e) {
			LOGGER.warn("写入 ANTE customConfigs 失败", e);
		}
	}

	public static void setRailCustomConfigsMirror(Rail rail, Map<String, String> railData) {
		setRailCustomConfigs(rail, railData);
	}
}
