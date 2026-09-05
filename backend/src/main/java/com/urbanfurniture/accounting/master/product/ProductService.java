package com.urbanfurniture.accounting.master.product;

import com.urbanfurniture.accounting.common.Money;
import com.urbanfurniture.accounting.common.PageResponse;
import com.urbanfurniture.accounting.common.SearchTerms;
import com.urbanfurniture.accounting.common.exception.ApiExceptions;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    public PageResponse<ProductDtos.ProductResponse> search(String search, ProductType type,
                                                            boolean includeArchived, Pageable pageable) {
        String term = SearchTerms.normalize(search);
        return PageResponse.of(productRepository.search(term, type, includeArchived, pageable),
                ProductDtos.ProductResponse::from);
    }

    @Transactional(readOnly = true)
    public List<ProductDtos.ProductOption> options() {
        return productRepository.findByActiveTrueOrderByNameAsc().stream()
                .map(ProductDtos.ProductOption::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<String> categories() {
        return productRepository.findDistinctCategories();
    }

    @Transactional(readOnly = true)
    public ProductDtos.ProductResponse get(Long id) {
        return ProductDtos.ProductResponse.from(requireProduct(id));
    }

    @Transactional(readOnly = true)
    public Product requireProduct(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ApiExceptions.NotFoundException("Product", id));
    }

    @Transactional
    public ProductDtos.ProductResponse create(ProductDtos.ProductRequest request) {
        Product product = new Product();
        apply(product, request);
        product.setActive(true);
        return ProductDtos.ProductResponse.from(productRepository.save(product));
    }

    @Transactional
    public ProductDtos.ProductResponse update(Long id, ProductDtos.ProductRequest request) {
        Product product = requireProduct(id);
        apply(product, request);
        return ProductDtos.ProductResponse.from(productRepository.save(product));
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public ProductDtos.ProductResponse setArchived(Long id, boolean archived) {
        Product product = requireProduct(id);
        product.setActive(!archived);
        return ProductDtos.ProductResponse.from(productRepository.save(product));
    }

    private void apply(Product product, ProductDtos.ProductRequest request) {
        product.setName(request.name().trim());
        product.setType(request.type());
        product.setSalesPrice(Money.of(request.salesPrice()));
        product.setCost(Money.of(request.cost()));
        product.setCategory(blankToNull(request.category()));
        product.setHsnCode(blankToNull(request.hsnCode()));
        product.setTaxRate(request.taxRate() == null ? BigDecimal.ZERO : request.taxRate());
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
