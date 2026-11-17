package com.core.orders.api;

import jakarta.validation.constraints.*;

public record OrderRequest(
        @NotBlank @Size(max = 200) String customerName,
        @NotBlank @Size(max = 100) String sku,
        @NotNull @Positive @Max(1_000_000_000) Integer quantity) {
}
