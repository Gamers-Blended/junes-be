package com.gamersblended.junes.service;

import com.gamersblended.junes.dto.CartItemDTO;
import com.gamersblended.junes.exception.EmptyCartException;
import com.gamersblended.junes.exception.InvalidQuantityException;
import com.gamersblended.junes.exception.ProductNotFoundException;
import com.gamersblended.junes.model.CartItem;
import com.gamersblended.junes.model.Product;
import com.gamersblended.junes.repository.jpa.CartRepository;
import com.gamersblended.junes.repository.mongodb.ProductRepository;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
     * @param guestCartItemDTOList Provided if user is not logged in
     * @param pageable                Sets page size and sorting order
     * @return A paginated list of products in cart for both logged user and guest cases
     */
    public Page<CartItemDTO> getCartProducts(Long userID, List<CartItemDTO> guestCartItemDTOList, Pageable pageable) {
        if (null != userID) {
            // Logged user -> get from database
            return getLoggedUserCart(userID, pageable);
        } else {
            // Not logged -> data will come from frontend cache
            return getGuestCart(guestCartItemDTOList, pageable);
        }
    }

    /**
     * Get products inside cart for logged user
     *
     * @param userID   The logged-in user's ID
     * @param pageable Sets page size and sorting order
     * @return A paginated list of products in cart
     */
    public Page<CartItemDTO> getLoggedUserCart(Long userID, Pageable pageable) {
        // Get cart items from database
        Page<CartItem> userCart = cartRepository.getUserCart(userID, pageable);
        log.info("userID {} has {} item(s) in cart.", userID, userCart.getTotalElements());

        if (userCart.isEmpty()) {
            return Page.empty(pageable);
        }
        // Extract product_IDs to fetch metadata
        List<String> productIDFromCartList = userCart.getContent().stream()
                .map(CartItem::getProductID)
                .collect(Collectors.toList());

        // Fetch product metadata from MongoDB
        List<Product> metadataList = productRepository.findByIdIn(productIDFromCartList);
        Map<String, Product> productMap = metadataList.stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        // Create DTO using cart items and metadata data
        List<CartItemDTO> productsInCartList = userCart.getContent().stream()
                .map(currentProductInCartItem -> {
                    Product metadata = productMap.get(currentProductInCartItem.getProductID());
                    if (metadata != null) {
                        return new CartItemDTO(
                                currentProductInCartItem.getProductID(),
                                metadata.getName(),
                                metadata.getPrice(),
                                metadata.getPlatform(),
                                metadata.getRegion(),
                                metadata.getEdition(),
                                metadata.getProductImageUrl(),
                                currentProductInCartItem.getQuantity(),
                                currentProductInCartItem.getUserID(),
                                currentProductInCartItem.getCreatedOn()
                        );
                    } else {
                        // Case when productID not found in MongoDB
                        return new CartItemDTO(
                                currentProductInCartItem.getProductID(),
                                "Unknown Product",
                                new BigDecimal("0.00"),
                                "Unknown Product",
                                "Unknown Product",
                                "Unknown Product",
                                "",
                                currentProductInCartItem.getQuantity(),
                                currentProductInCartItem.getUserID(),
                                currentProductInCartItem.getCreatedOn()
                        );
                    }
                })
                .collect(Collectors.toList());

        return new PageImpl<>(productsInCartList, pageable, userCart.getTotalElements());
    }

    /**
     * Get products inside cart for guest
     *
     * @param guestCartItemDTOList A list of products (product_id + quantity) that guest has in cart from frontend cache
     * @param pageable                Sets page size and sorting order
     * @return A paginated list of products in cart
     */
    private Page<CartItemDTO> getGuestCart(List<CartItemDTO> guestCartItemDTOList, Pageable pageable) {
        if (null == guestCartItemDTOList || guestCartItemDTOList.isEmpty()) {
            return Page.empty(pageable);
        }

        // Extract product_IDs from guest cart
        List<String> productIDFromCartList = guestCartItemDTOList.stream()
                .map(CartItemDTO::getProductID)
                .collect(Collectors.toList());

        // Fetch metadata from MongoDB
        Map<String, Product> productMap = getProductMap(productIDFromCartList);

        // Combine cart items with metadata
        List<CartItemDTO> productsInCartList = guestCartItemDTOList.stream()
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
                        return new CartItemDTO(
                                currentProductInCart.getProductID(),
                                "Unknown Product",
                                new BigDecimal("0.00"),
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
        productsInCartList.sort(Comparator.comparing(CartItemDTO::getCreatedOn).reversed());

        // Apply pagination
        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), productsInCartList.size());

        if (start >= productsInCartList.size()) {
            return Page.empty(pageable);
        }

        List<CartItemDTO> pageContent = productsInCartList.subList(start, end);
        return new PageImpl<>(pageContent, pageable, productsInCartList.size());
    }

    /**
     * Query MongoDB on a list of product_IDs to get Product metadata
     *
     * @param productIDList A list of product_IDs to query MongoDB
     * @return A map with the product_ID as key, corresponding Product as value
     */
    public Map<String, Product> getProductMap(List<String> productIDList) {
        List<Product> metadataList = productRepository.findByIdIn(productIDList);
        return metadataList.stream().collect(Collectors.toMap(Product::getId, Function.identity()));
    }

    /**
     * Add given product to user's cart
     *
     * @param userID       The ID of the user whose cart is modified
     * @param productToAdd The Product to add to user's cart
     * @return Output message
     */
    @Transactional
    public String addToCart(Long userID, CartItemDTO productToAdd) {
        try {
            // Validate quantity
            if (Boolean.FALSE.equals(validateQuantity(productToAdd.getQuantity()))) {
                throw new InvalidQuantityException("Error in adding to userID's (" + userID + ") cart due to invalid quantity value: " + productToAdd.getQuantity());
            }

            // Get user's cart from database
            List<CartItem> userCartItemProductList = cartRepository.getUserCart(userID);

            // If user does not have cart data in database
            // Create new record with productToAdd and save to database
            if (userCartItemProductList.isEmpty()) {
                log.info("UserID {} has an empty cart, creating a new record...", userID);
                addCartItemToDatabase(userID, productToAdd);
                return "Product added to cart";
            }

            // Check if user already has product in cart
            Optional<CartItem> queriedCartProduct = userCartItemProductList.stream()
                    .filter(cartItemDTO -> cartItemDTO.getProductID().equals(productToAdd.getProductID()))
                    .findFirst();
            if (queriedCartProduct.isPresent()) {
                // Update quantity if record exists in database
                CartItem existingCartItemProduct = queriedCartProduct.get();
                Integer newQuantity = existingCartItemProduct.getQuantity() + productToAdd.getQuantity();
                log.info("UserID {} already has productID {} in their cart, updating quantity from {} to {}...", userID, productToAdd.getProductID(),
                        existingCartItemProduct.getQuantity(), newQuantity);
                existingCartItemProduct.setQuantity(newQuantity);
                existingCartItemProduct.setUpdatedOn(LocalDateTime.now());
                cartRepository.save(existingCartItemProduct);
            } else {
                // Create new record inside cart database
                log.info("UserID {} doesn't have productID {} in their cart, creating a new record...", userID, productToAdd.getProductID());
                addCartItemToDatabase(userID, productToAdd);
            }

            return "Product added to cart";
        } catch (Exception ex) {
            log.error("Exception in addToCart: ", ex);
            return "Error in adding to userID's (" + userID + ") cart.";
        }
    }

    /**
     * Add given product as a new record to carts database
     *
     * @param userID       The ID of the user whose cart is modified
     * @param productToAdd The Product to add to user's cart
     */
    private void addCartItemToDatabase(Long userID, CartItemDTO productToAdd) {
        try {
            CartItem newCartItem = new CartItem();
            newCartItem.setUserID(userID);
            newCartItem.setProductID(productToAdd.getProductID());
            newCartItem.setQuantity(productToAdd.getQuantity());
            newCartItem.setCreatedOn(productToAdd.getCreatedOn());
            cartRepository.save(newCartItem);
        } catch (Exception ex) {
            log.error("Exception in addCartItemToDatabase: ", ex);
        }
    }

    /**
     * Remove given product from user's cart
     *
     * @param userID          The ID of the user whose cart is modified
     * @param productToRemove The Product to remove from user's cart (must already exist in cart)
     * @return Output message
     */
    @Transactional
    public String removeFromCart(Long userID, CartItemDTO productToRemove) {
        try {
            // Validate quantity
            if (Boolean.FALSE.equals(validateQuantity(productToRemove.getQuantity()))) {
                throw new InvalidQuantityException("Error in removing productID " + productToRemove.getProductID() + " from userID's (" + userID + ") cart due to invalid quantity value: " + productToRemove.getQuantity());
            }

            // Get user's cart from database
            List<CartItem> userCartItemProductList = cartRepository.getUserCart(userID);

            // User supposed to have cart data in database, else throw error
            if (userCartItemProductList.isEmpty()) {
                log.error("UserID {} has an empty cart! Nothing to remove!", userID);
                throw new EmptyCartException("UserID " + userID + " has an empty cart! Nothing to remove!");
            }

            // Check if user already has product in cart
            Optional<CartItem> queriedCartProduct = userCartItemProductList.stream()
                    .filter(cartItemDTO -> cartItemDTO.getProductID().equals(productToRemove.getProductID()))
                    .findFirst();
            if (queriedCartProduct.isPresent()) {
                // Update quantity
                CartItem existingCartItemProduct = queriedCartProduct.get();
                Integer newQuantity = existingCartItemProduct.getQuantity() - productToRemove.getQuantity();

                // if newQuantity is <= 0, remove record from carts database
                if (newQuantity <= 0) {
                    log.info("productID {} will be removed from user's ({}) cart.", existingCartItemProduct.getProductID(), userID);
                    cartRepository.delete(existingCartItemProduct);
                } else {
                    log.info("Updating productID {} quantity from {} to {} for UserID's ({}) cart, ...", productToRemove.getProductID(),
                            existingCartItemProduct.getQuantity(), newQuantity, userID);
                    existingCartItemProduct.setQuantity(newQuantity);
                    existingCartItemProduct.setUpdatedOn(LocalDateTime.now());
                    cartRepository.save(existingCartItemProduct);
                }
            } else {
                // User supposed to have to-be-removed product inside cart, else throw error
                log.error("UserID {} doesn't have productID {} in their cart, nothing to remove!", userID, productToRemove.getProductID());
                throw new ProductNotFoundException("UserID + " + userID + " doesn't have productID " + productToRemove.getProductID() + " in their cart, nothing to remove!");
            }

            return productToRemove.getQuantity() + " of ProductID " + productToRemove.getProductID() + " removed from cart.";

        } catch (Exception ex) {
            log.error("Exception in removeFromCart: ", ex);
            return "Error in removing " + productToRemove.getQuantity() + " of productID " + productToRemove.getProductID() + " from userID's (" + userID + ") cart.";
        }
    }

    /**
     * Check that quantity is at least 1
     *
     * @param quantity Integer value to check
     * @return True if value >= 0, else false
     */
    public Boolean validateQuantity(Integer quantity) {
        if (quantity <= 0) {
            log.error("Invalid quantity value {} given.", quantity);
            return false;
        }
        return true;
    }
}
