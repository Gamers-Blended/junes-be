package com.gamersblended.junes.service;

import com.gamersblended.junes.dto.CartProductDTO;
import com.gamersblended.junes.model.Cart;
import com.gamersblended.junes.model.Product;
import com.gamersblended.junes.repository.jpa.CartRepository;
import com.gamersblended.junes.repository.mongodb.ProductRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class CartService {

    private CartRepository cartRepository;
    private ProductRepository productRepository;

    public CartService(CartRepository cartRepository, ProductRepository productRepository) {
        this.cartRepository = cartRepository;
        this.productRepository = productRepository;
    }

    /**
     * For get shopping cart API for both logged user and guest
     *
     * @param userID                  Provided if user is logged in
     * @param guestCartProductDTOList Provided if user is not logged in
     * @param pageable                Sets page size and sorting order
     * @return A paginated list of products in cart for both logged user and guest cases
     */
    public Page<CartProductDTO> getCartProducts(Integer userID, List<CartProductDTO> guestCartProductDTOList, Pageable pageable) {
        if (null != userID) {
            // Logged user -> get from database
            return getLoggedUserCart(userID, pageable);
        } else {
            // Not logged -> data will come from frontend cache
            return getGuestCart(guestCartProductDTOList, pageable);
        }
    }

    /**
     * Get products inside cart for logged user
     *
     * @param userID   The logged-in user's ID
     * @param pageable Sets page size and sorting order
     * @return A paginated list of products in cart
     */
    public Page<CartProductDTO> getLoggedUserCart(Integer userID, Pageable pageable) {
        // Get cart items from database
        Page<Cart> userCart = cartRepository.getUserCart(userID, pageable);
        log.info("userID {} has {} item(s) in cart.", userID, userCart.getTotalElements());

        if (userCart.isEmpty()) {
            return Page.empty(pageable);
        }
        // Extract product_IDs to fetch metadata
        List<String> productIDFromCartList = userCart.getContent().stream()
                .map(Cart::getProductID)
                .collect(Collectors.toList());

        // Fetch product metadata from MongoDB
        List<Product> metadataList = productRepository.findByIdIn(productIDFromCartList);
        Map<String, Product> productMap = metadataList.stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        // Create DTO using cart items and metadata data
        List<CartProductDTO> productsInCartList = userCart.getContent().stream()
                .map(currentProductInCart -> {
                    Product metadata = productMap.get(currentProductInCart.getProductID());
                    if (metadata != null) {
                        return new CartProductDTO(
                                currentProductInCart.getProductID(),
                                metadata.getName(),
                                metadata.getPrice(),
                                metadata.getPlatform(),
                                metadata.getRegion(),
                                metadata.getEdition(),
                                metadata.getProductImageUrl(),
                                currentProductInCart.getQuantity(),
                                currentProductInCart.getUserID(),
                                currentProductInCart.getCreatedOn()
                        );
                    } else {
                        // Case when productID not found in MongoDB
                        return new CartProductDTO(
                                currentProductInCart.getProductID(),
                                "Unknown Product",
                                0.00,
                                "Unknown Product",
                                "Unknown Product",
                                "Unknown Product",
                                "",
                                currentProductInCart.getQuantity(),
                                currentProductInCart.getUserID(),
                                currentProductInCart.getCreatedOn()
                        );
                    }
                })
                .collect(Collectors.toList());

        return new PageImpl<>(productsInCartList, pageable, userCart.getTotalElements());
    }

    /**
     * Get products inside cart for guest
     *
     * @param guestCartProductDTOList A list of products (product_id + quantity) that guest has in cart from frontend cache
     * @param pageable                Sets page size and sorting order
     * @return A paginated list of products in cart
     */
    private Page<CartProductDTO> getGuestCart(List<CartProductDTO> guestCartProductDTOList, Pageable pageable) {
        if (null == guestCartProductDTOList || guestCartProductDTOList.isEmpty()) {
            return Page.empty(pageable);
        }

        // Extract product_IDs from guest cart
        List<String> productIDFromCartList = guestCartProductDTOList.stream()
                .map(CartProductDTO::getProductID)
                .collect(Collectors.toList());

        // Fetch metadata from MongoDB
        Map<String, Product> productMap = getProductMap(productIDFromCartList);

        // Combine cart items with metadata
        List<CartProductDTO> productsInCartList = guestCartProductDTOList.stream()
                .map(currentProductInCart -> {
                    Product metadata = productMap.get(currentProductInCart.getProductID());
                    if (metadata != null) {
                        currentProductInCart.setName(metadata.getName());
                        currentProductInCart.setPrice(metadata.getPrice());
                        currentProductInCart.setPlatform(metadata.getPlatform());
                        currentProductInCart.setRegion(metadata.getRegion());
                        currentProductInCart.setEdition(metadata.getEdition());
                        currentProductInCart.setProductImageUrl(metadata.getProductImageUrl());
                        return currentProductInCart;
                    } else {
                        // Case when productID not found in MongoDB
                        return new CartProductDTO(
                                currentProductInCart.getProductID(),
                                "Unknown Product",
                                0.00,
                                "Unknown Product",
                                "Unknown Product",
                                "Unknown Product",
                                "",
                                currentProductInCart.getQuantity(),
                                currentProductInCart.getUserID(),
                                currentProductInCart.getCreatedOn()
                        );
                    }
                })
                .collect(Collectors.toList());

        // Only sort by created_on, descending
        productsInCartList.sort(Comparator.comparing(CartProductDTO::getCreatedOn).reversed());

        // Apply pagination
        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), productsInCartList.size());

        if (start >= productsInCartList.size()) {
            return Page.empty(pageable);
        }

        List<CartProductDTO> pageContent = productsInCartList.subList(start, end);
        return new PageImpl<>(pageContent, pageable, productsInCartList.size());
    }

    /**
     * Query MongoDB on a list of product_IDs to get Product metadata
     *
     * @param productIDList A lsit of product_IDs to query MongoDB
     * @return A map with the product_ID as key, corresponding Product as value
     */
    public Map<String, Product> getProductMap(List<String> productIDList) {
        List<Product> metadataList = productRepository.findByIdIn(productIDList);
        return metadataList.stream().collect(Collectors.toMap(Product::getId, Function.identity()));
    }
}
