package me.maxih.itunes_backup_explorer.util;

import com.dd.plist.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class DeviceService {
    private static final Logger logger = LoggerFactory.getLogger(DeviceService.class);
    private static Boolean libimobiledeviceAvailable;

    public static boolean isLibimobiledeviceAvailable() {
        if (libimobiledeviceAvailable == null) {
            libimobiledeviceAvailable = testCommand("idevice_id", "--help");
            logger.info(libimobiledeviceAvailable
                    ? "libimobiledevice detectado"
                    : "libimobiledevice não encontrado");
        }
        return libimobiledeviceAvailable;
    }

    public static Optional<String> detectDevice() {
        byte[] output = runCommand(5, "idevice_id", "-l");
        if (output == null) return Optional.empty();

        String result = new String(output, StandardCharsets.UTF_8).trim();
        if (result.isEmpty()) return Optional.empty();

        String[] lines = result.split("\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) return Optional.of(trimmed);
        }
        return Optional.empty();
    }

    public static Optional<DeviceInfo> getDeviceInfo(String udid) {
        byte[] mainOutput = runCommand(10, "ideviceinfo", "-u", udid, "-x");
        if (mainOutput == null) return Optional.empty();

        NSDictionary mainDict = parsePlistDict(mainOutput);
        if (mainDict == null) return Optional.empty();

        byte[] batteryOutput = runCommand(10, "ideviceinfo", "-u", udid, "-q", "com.apple.mobile.battery", "-x");
        NSDictionary batteryDict = parsePlistDict(batteryOutput);

        byte[] diskOutput = runCommand(10, "ideviceinfo", "-u", udid, "-q", "com.apple.disk_usage", "-x");
        NSDictionary diskDict = parsePlistDict(diskOutput);

        int batteryLevel = 0;
        String batteryStatus = "";
        if (batteryDict != null) {
            batteryLevel = getInt(batteryDict, "BatteryCurrentCapacity", 0);
            boolean charging = getBool(batteryDict, "BatteryIsCharging", false);
            batteryStatus = charging ? "Carregando" : "Descarregando";
        }

        long totalDiskCapacity = -1;
        long totalDataCapacity = -1;
        long totalDataAvailable = -1;
        if (diskDict != null) {
            totalDiskCapacity = getLong(diskDict, "TotalDiskCapacity", -1);
            totalDataCapacity = getLong(diskDict, "TotalDataCapacity", -1);
            totalDataAvailable = getLong(diskDict, "TotalDataAvailable", -1);
        }

        DeviceInfo info = new DeviceInfo(
                getString(mainDict, "DeviceName", ""),
                getString(mainDict, "ModelNumber", ""),
                getString(mainDict, "ProductType", ""),
                getString(mainDict, "ProductVersion", ""),
                getString(mainDict, "BuildVersion", ""),
                getString(mainDict, "SerialNumber", ""),
                getString(mainDict, "UniqueDeviceID", ""),
                getString(mainDict, "PhoneNumber", ""),
                getString(mainDict, "WiFiAddress", ""),
                batteryLevel,
                batteryStatus,
                totalDiskCapacity,
                totalDataCapacity,
                totalDataAvailable
        );

        return Optional.of(info);
    }

    public static List<DeviceApp> getApps(String udid, boolean system) {
        List<DeviceApp> apps = new ArrayList<>();

        String listOption = system ? "list_system" : "list_user";
        byte[] output = runCommand(15, "ideviceinstaller", "-u", udid, "-l", "-o", listOption, "-o", "xml");
        if (output == null) return apps;

        try {
            NSObject parsed = PropertyListParser.parse(output);
            if (!(parsed instanceof NSArray array)) return apps;

            String appType = system ? "System" : "User";
            for (NSObject item : array.getArray()) {
                if (!(item instanceof NSDictionary dict)) continue;

                String name = getString(dict, "CFBundleDisplayName", "");
                if (name.isEmpty()) name = getString(dict, "CFBundleName", "");

                String bundleId = getString(dict, "CFBundleIdentifier", "");

                String version = getString(dict, "CFBundleShortVersionString", "");
                if (version.isEmpty()) version = getString(dict, "CFBundleVersion", "");

                apps.add(new DeviceApp(name, bundleId, version, appType));
            }
        } catch (Exception e) {
            logger.warn("Falha ao parsear lista de apps: {}", e.getMessage());
        }

        return apps;
    }

    public static boolean uninstallApp(String udid, String bundleId) {
        byte[] result = runCommand(30, "ideviceinstaller", "-u", udid, "-U", bundleId);
        return result != null;
    }

    public static boolean takeScreenshot(String udid, File output) {
        byte[] result = runCommand(15, "idevicescreenshot", "-u", udid, output.getAbsolutePath());
        if (result == null) return false;
        return output.exists();
    }

    public static boolean restartDevice(String udid) {
        return runDiagnostics(udid, "restart");
    }

    public static boolean shutdownDevice(String udid) {
        return runDiagnostics(udid, "shutdown");
    }

    public static boolean sleepDevice(String udid) {
        return runDiagnostics(udid, "sleep");
    }

    private static boolean runDiagnostics(String udid, String command) {
        try {
            ProcessBuilder pb = new ProcessBuilder("idevicediagnostics", "-u", udid, command);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            process.getInputStream().readAllBytes();
            boolean finished = process.waitFor(10, TimeUnit.SECONDS);

            if (!finished) {
                process.destroyForcibly();
                return false;
            }

            return process.exitValue() == 0;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            logger.warn("Falha ao executar diagnóstico '{}': {}", command, e.getMessage());
            return false;
        }
    }

    private static boolean testCommand(String... command) {
        try {
            Process p = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();
            p.getInputStream().readAllBytes();
            boolean finished = p.waitFor(5, TimeUnit.SECONDS);
            return finished && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static byte[] runCommand(int timeoutSeconds, String... command) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            byte[] output = process.getInputStream().readAllBytes();
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);

            if (!finished) {
                process.destroyForcibly();
                return null;
            }

            if (process.exitValue() != 0) return null;

            return output;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            logger.warn("Falha ao executar comando '{}': {}", command[0], e.getMessage());
            return null;
        }
    }

    private static NSDictionary parsePlistDict(byte[] data) {
        if (data == null) return null;
        try {
            NSObject parsed = PropertyListParser.parse(data);
            if (parsed instanceof NSDictionary dict) return dict;
        } catch (Exception e) {
            logger.warn("Falha ao parsear plist: {}", e.getMessage());
        }
        return null;
    }

    private static String getString(NSDictionary dict, String key, String defaultValue) {
        NSObject obj = dict.objectForKey(key);
        if (obj instanceof NSString nsString) return nsString.getContent();
        return defaultValue;
    }

    private static int getInt(NSDictionary dict, String key, int defaultValue) {
        NSObject obj = dict.objectForKey(key);
        if (obj instanceof NSNumber nsNumber) return nsNumber.intValue();
        return defaultValue;
    }

    private static long getLong(NSDictionary dict, String key, long defaultValue) {
        NSObject obj = dict.objectForKey(key);
        if (obj instanceof NSNumber nsNumber) return nsNumber.longValue();
        return defaultValue;
    }

    private static boolean getBool(NSDictionary dict, String key, boolean defaultValue) {
        NSObject obj = dict.objectForKey(key);
        if (obj instanceof NSNumber nsNumber) return nsNumber.boolValue();
        return defaultValue;
    }

    private DeviceService() {}
}
