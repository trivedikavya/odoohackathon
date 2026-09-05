package com.urbanfurniture.accounting.master.product;

import com.urbanfurniture.accounting.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@Tag(name = "Products (master data)")
public class ProductController {

    private final ProductService productService;

    @GetMapping
    public PageResponse<ProductDtos.ProductResponse> search(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) ProductType type,
            @RequestParam(defaultValue = "false") boolean includeArchived,
            @PageableDefault(size = 20) Pageable pageable) {
        return productService.search(search, type, includeArchived, pageable);
    }

    @GetMapping("/options")
    @Operation(summary = "Active products, for order/invoice line pickers")
    public List<ProductDtos.ProductOption> options() {
        return productService.options();
    }

    @GetMapping("/categories")
    public List<String> categories() {
        return productService.categories();
    }

    @GetMapping("/{id}")
    public ProductDtos.ProductResponse get(@PathVariable Long id) {
        return productService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductDtos.ProductResponse create(@Valid @RequestBody ProductDtos.ProductRequest request) {
        return productService.create(request);
    }

    @PutMapping("/{id}")
    public ProductDtos.ProductResponse update(@PathVariable Long id,
                                              @Valid @RequestBody ProductDtos.ProductRequest request) {
        return productService.update(id, request);
    }

    @PutMapping("/{id}/archive")
    @Operation(summary = "Archive a product (Admin only)")
    public ProductDtos.ProductResponse archive(@PathVariable Long id) {
        return productService.setArchived(id, true);
    }

    @PutMapping("/{id}/restore")
    @Operation(summary = "Restore an archived product (Admin only)")
    public ProductDtos.ProductResponse restore(@PathVariable Long id) {
        return productService.setArchived(id, false);
    }
}
