package com.gamersblended.junes.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WishlistTest {

    @Test
    void addItem_addsItemToItemList() {
        Wishlist wishlist = new Wishlist();
        WishlistItem item = new WishlistItem();

        wishlist.addItem(item);

        assertThat(wishlist.getItemList()).containsExactly(item);
    }

    @Test
    void addItem_setsBackReferenceOnItem() {
        Wishlist wishlist = new Wishlist();
        WishlistItem item = new WishlistItem();

        wishlist.addItem(item);

        assertThat(item.getWishlist()).isSameAs(wishlist);
    }

    @Test
    void addItem_appendsMultipleItemsInOrder() {
        Wishlist wishlist = new Wishlist();
        WishlistItem firstItem = new WishlistItem();
        WishlistItem secondItem = new WishlistItem();

        wishlist.addItem(firstItem);
        wishlist.addItem(secondItem);

        assertThat(wishlist.getItemList()).containsExactly(firstItem, secondItem);
    }
}
