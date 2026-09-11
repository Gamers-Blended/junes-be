package com.gamersblended.junes.model;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "carts", schema = "junes_rel")
@Getter
@Setter
public class Cart {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "cart_id")
    private UUID cartID;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userID;

    // Not persisted to carts table: only relevant to guest (session-keyed) carts in Redis,
    // which are never synced to Postgres (see CartService#asyncPersistToDatabase)
    @Transient
    private UUID sessionID;

    @Column(name = "created_on", nullable = false, updatable = false)
    @CreationTimestamp
    private LocalDateTime createdOn;

    @Column(name = "updated_on")
    @UpdateTimestamp
    private LocalDateTime updatedOn;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version = 0;

    @JsonManagedReference
    @OneToMany(
            mappedBy = "cart",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    @Builder.Default
    private List<CartItem> itemList = new ArrayList<>();

    // Helper method for bidirectional relationship
    public void addItem(CartItem item) {
        itemList.add(item);
        item.setCart(this);
    }
}
