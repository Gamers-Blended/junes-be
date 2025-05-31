package com.gamersblended.junes.service;

import com.gamersblended.junes.dto.CartProductDTO;
import com.gamersblended.junes.model.Cart;
import com.gamersblended.junes.repository.jpa.CartRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class CartService {

    private CartRepository cartRepository;

    public CartService(CartRepository cartRepository) {
        this.cartRepository = cartRepository;
    }


    public Page<CartProductDTO> getCartProducts(Integer userID, List<CartProductDTO> guestCartProductDTOList, Pageable pageable) {
        // Logged user -> get from database
        if (null != userID) {
            Page<Cart> userCart = cartRepository.getUserCart(userID, pageable);
            log.info("userID {} has {} items in cart.", userID, userCart.getTotalElements());
            return userCart.map(product -> new CartProductDTO());
        }

        // Not logged -> data will come from frontend cache
        if (null == guestCartProductDTOList || guestCartProductDTOList.isEmpty()) {
            return Page.empty(pageable);
        }

        // Apply pagination to guest cart items
        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), guestCartProductDTOList.size());

        if (start >= guestCartProductDTOList.size()) {
            return Page.empty(pageable);
        }

        List<CartProductDTO> pageContent = guestCartProductDTOList.subList(start, end);
        return new PageImpl<>(pageContent, pageable, guestCartProductDTOList.size());
    }
}
