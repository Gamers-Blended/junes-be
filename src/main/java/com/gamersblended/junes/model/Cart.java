package com.gamersblended.junes.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "carts")
@Getter
@Setter
public class Cart {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "cart_id")
    private UUID cartID;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userID;

    @Column(name = "session_id", nullable = false)
    private UUID sessionID;

    @Column(name = "created_on", nullable = false, updatable = false)
    @CreationTimestamp
    private LocalDateTime createdOn;

    @Column(name = "updated_on")
    @UpdateTimestamp
    private LocalDateTime updatedOn;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version;

    @OneToMany(
            mappedBy = "cart",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    @Builder.Default
    private List<CartItem> itemList = new ArrayList<>();

    // Helper methods for bidirectional relationship
    public void addItem(CartItem item) {
        itemList.add(item);
        item.setCart(this);
    }

    public void removeItem(CartItem item) {
        itemList.remove(item);
        item.setCart(null);
    }

    public BigDecimal getTotalPrice() {
        return itemList.stream()
                .map(CartItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public int getTotalItems() {
        return itemList.stream()
                .mapToInt(CartItem::getQuantity)
                .sum();
    }
}
