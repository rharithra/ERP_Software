package in.retailflow.api.tenant.domain;

import java.util.Arrays;

public enum BusinessType {
    GROCERY_SUPERMARKET,
    ELECTRONICS_COMPUTER,
    MOBILE_ACCESSORIES,
    APPLIANCES_WATER_PURIFIER,
    FURNITURE,
    HARDWARE_BUILDING_MATERIALS,
    OTHER;

    public static BusinessType fromValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Arrays.stream(values())
                .filter(item -> item.name().equalsIgnoreCase(value.trim()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported business type"));
    }
}
