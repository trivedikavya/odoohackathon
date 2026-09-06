package com.urbanfurniture.accounting.stock;

import com.urbanfurniture.accounting.master.Product;
import com.urbanfurniture.accounting.master.ProductComponent;
import com.urbanfurniture.accounting.master.ProductType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * How a sold line turns into stock to move.
 * <p>
 * This is what stops a bundle becoming a way to sell stock you do not
 * have: the availability check runs against the expanded components, not
 * against the wrapper.
 */
@ExtendWith(MockitoExtension.class)
class StockServiceExpansionTest {

    @Mock
    private StockMovementRepository movements;

    @InjectMocks
    private StockService stockService;

    private Product desk;
    private Product chair;
    private Product fitting;
    private Product studySet;

    @BeforeEach
    void setUp() {
        desk = goods(1L, "Desk");
        chair = goods(2L, "Chair");
        fitting = Product.builder().id(3L).name("Fitting service")
                .type(ProductType.SERVICE).trackInventory(false).build();

        studySet = Product.builder().id(4L).name("Study Set")
                .type(ProductType.COMBO).trackInventory(false).build();
        studySet.getComponents().add(component(studySet, desk, "1"));
        studySet.getComponents().add(component(studySet, chair, "2"));
    }

    private Product goods(Long id, String name) {
        return Product.builder().id(id).name(name)
                .type(ProductType.GOODS).trackInventory(true).build();
    }

    private ProductComponent component(Product combo, Product part, String qty) {
        return ProductComponent.builder()
                .combo(combo).component(part).quantity(new BigDecimal(qty)).build();
    }

    @Test
    @DisplayName("a plain item consumes itself")
    void plainItemConsumesItself() {
        List<StockService.Consumption> result = stockService.expand(desk, new BigDecimal("5"));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).product()).isEqualTo(desk);
        assertThat(result.get(0).quantity()).isEqualByComparingTo("5");
    }

    @Test
    @DisplayName("a service consumes nothing")
    void serviceConsumesNothing() {
        assertThat(stockService.expand(fitting, new BigDecimal("3"))).isEmpty();
    }

    @Test
    @DisplayName("a combo consumes its components, scaled by how many were sold")
    void comboConsumesComponents() {
        // Two Study Sets = 2 desks and 4 chairs.
        List<StockService.Consumption> result = stockService.expand(studySet, new BigDecimal("2"));

        assertThat(result).hasSize(2);
        assertThat(result).extracting(c -> c.product().getName())
                .containsExactly("Desk", "Chair");
        assertThat(result).extracting(StockService.Consumption::quantity)
                .containsExactly(new BigDecimal("2"), new BigDecimal("4"));
    }

    @Test
    @DisplayName("a combo never consumes itself, so nothing can recurse")
    void comboDoesNotConsumeItself() {
        List<StockService.Consumption> result = stockService.expand(studySet, BigDecimal.ONE);

        assertThat(result).noneMatch(c -> c.product().getId().equals(studySet.getId()));
    }

    @Test
    @DisplayName("a zero or negative quantity moves nothing")
    void nonPositiveQuantityMovesNothing() {
        assertThat(stockService.expand(desk, BigDecimal.ZERO)).isEmpty();
        assertThat(stockService.expand(desk, new BigDecimal("-1"))).isEmpty();
        assertThat(stockService.expand(null, BigDecimal.ONE)).isEmpty();
    }

    @Test
    @DisplayName("the same item on two lines is totalled, not checked twice")
    void aggregatesRepeatedItems() {
        // A document selling a desk outright and a Study Set needs three
        // desks in total. Checking line by line would let a shelf with two
        // desks pass both checks and then go negative.
        List<StockService.Consumption> consumptions = List.of(
                new StockService.Consumption(desk, new BigDecimal("2")),
                new StockService.Consumption(desk, new BigDecimal("1")),
                new StockService.Consumption(chair, new BigDecimal("4")));

        Map<Product, BigDecimal> totals = stockService.aggregate(consumptions);

        assertThat(totals).hasSize(2);
        assertThat(totals.get(desk)).isEqualByComparingTo("3");
        assertThat(totals.get(chair)).isEqualByComparingTo("4");
    }

    @Test
    @DisplayName("two combos sharing a component total that component")
    void aggregatesAcrossCombos() {
        List<StockService.Consumption> consumptions = List.of(
                new StockService.Consumption(chair, new BigDecimal("2")),
                new StockService.Consumption(chair, new BigDecimal("3")));

        assertThat(stockService.aggregate(consumptions).get(chair)).isEqualByComparingTo("5");
    }
}
