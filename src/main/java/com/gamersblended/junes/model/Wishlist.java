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
@Table(name = "wishlists", schema = "junes_rel")
@Getter
@Setter
public class Wishlist {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "wishlist_id")
    private UUID wishlistID;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userID;

    // Not persisted to the wishlists table: only relevant to guest (session-keyed) wishlists in Redis,
    // which are never synced to Postgres (see WishlistService#asyncPersistToDatabase)
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
            mappedBy = "wishlist",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    @Builder.Default
    private List<WishlistItem> itemList = new ArrayList<>();

    // Helper methods for bidirectional relationship
    public void addItem(WishlistItem item) {
        itemList.add(item);
        item.setWishlist(this);
    }

    public void removeItem(WishlistItem item) {
        itemList.remove(item);
        item.setWishlist(null);
    }
}
