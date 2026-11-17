package com.core.inventory.api;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record InventoryRequest(
        @NotBlank @Size(max = 100) String sku,
        @NotBlank @Size(max = 200) String name,
        @Size(max = 2000) String description,
        @NotNull @PositiveOrZero @Max(1_000_000_000) Integer quantity,
        @NotNull @PositiveOrZero @Digits(integer = 12, fraction = 2) BigDecimal price) {
}
