package com.gamersblended.junes.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CartTest {

    @Test
    void addItem_addsItemToItemList() {
        Cart cart = new Cart();
        CartItem item = new CartItem();

        cart.addItem(item);

        assertThat(cart.getItemList()).containsExactly(item);
    }

    @Test
    void addItem_setsBackReferenceOnItem() {
        Cart cart = new Cart();
        CartItem item = new CartItem();

        cart.addItem(item);

        assertThat(item.getCart()).isSameAs(cart);
    }

    @Test
    void addItem_appendsMultipleItemsInOrder() {
        Cart cart = new Cart();
        CartItem firstItem = new CartItem();
        CartItem secondItem = new CartItem();

        cart.addItem(firstItem);
        cart.addItem(secondItem);

        assertThat(cart.getItemList()).containsExactly(firstItem, secondItem);
    }
}
