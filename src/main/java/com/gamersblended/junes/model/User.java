package com.gamersblended.junes.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "users", schema = "junes_rel")
@Getter
@Setter
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long userID;

    @Column(nullable = false)
    private String username;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private String email;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @Column(name = "history_list", nullable = false)
    private List<String> historyList;

    @Column(name = "address_list", nullable = false)
    private List<String> addressList;

    @Column(name = "payment_info_list", nullable = false)
    private List<String> paymentInfoList;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public List<String> getHistoryList() {
        return Collections.unmodifiableList(historyList);
    }

    public List<String> getAddressList() {
        return Collections.unmodifiableList(addressList);
    }

    public List<String> getPaymentInfoList() {
        return Collections.unmodifiableList(paymentInfoList);
    }

    public void setHistoryList(List<String> historyList) {
        this.historyList = historyList != null ? new ArrayList<>(historyList) : new ArrayList<>();
    }

    public void setAddressList(List<String> addressList) {
        this.addressList = addressList != null ? new ArrayList<>(addressList) : new ArrayList<>();
    }

    public void setPaymentInfoList(List<String> paymentInfoList) {
        this.paymentInfoList = paymentInfoList != null ? new ArrayList<>(paymentInfoList) : new ArrayList<>();
    }
}
