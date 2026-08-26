package in.retailflow.api.inventory.dto;

public record InventorySummaryResponse(long totalProducts, long inStock, long lowStock, long outOfStock) {}
