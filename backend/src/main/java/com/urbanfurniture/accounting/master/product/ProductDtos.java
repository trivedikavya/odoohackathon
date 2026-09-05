package com.urbanfurniture.accounting.master.product;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public final class ProductDtos {

    private ProductDtos() {
    }

    public record ProductRequest(
            @NotBlank @Size(max = 180) String name,
            @NotNull ProductType type,
            @NotNull @DecimalMin("0.00") @Digits(integer = 13, fraction = 2) BigDecimal salesPrice,
            @NotNull @DecimalMin("0.00") @Digits(integer = 13, fraction = 2) BigDecimal cost,
            @Size(max = 100) String category,
            @Size(max = 20) String hsnCode,
            @DecimalMin("0.00") @DecimalMax("100.00") @Digits(integer = 3, fraction = 2) BigDecimal taxRate) {
    }

    public record ProductResponse(
            Long id,
            String name,
            ProductType type,
            BigDecimal salesPrice,
            BigDecimal cost,
            String category,
            String hsnCode,
            BigDecimal taxRate,
            Boolean active) {

        public static ProductResponse from(Product p) {
            return new ProductResponse(p.getId(), p.getName(), p.getType(), p.getSalesPrice(), p.getCost(),
                    p.getCategory(), p.getHsnCode(), p.getTaxRate(), p.getActive());
        }
    }

    public record ProductOption(
            Long id,
            String name,
            ProductType type,
            BigDecimal salesPrice,
            BigDecimal cost,
            BigDecimal taxRate) {

        public static ProductOption from(Product p) {
            return new ProductOption(p.getId(), p.getName(), p.getType(), p.getSalesPrice(), p.getCost(),
                    p.getTaxRate());
        }
    }
}
