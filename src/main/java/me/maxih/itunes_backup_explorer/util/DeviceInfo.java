package me.maxih.itunes_backup_explorer.util;

public record DeviceInfo(
        String deviceName,
        String modelNumber,
        String productType,
        String productVersion,
        String buildVersion,
        String serialNumber,
        String udid,
        String phoneNumber,
        String wifiMac,
        int batteryLevel,
        String batteryStatus,
        long totalDiskCapacity,
        long totalDataCapacity,
        long totalDataAvailable
) {}
