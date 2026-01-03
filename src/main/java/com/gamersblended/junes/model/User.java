package com.gamersblended.junes.model;

import com.gamersblended.junes.dto.AddressDTO;
import com.gamersblended.junes.dto.PaymentMethodDTO;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "users", schema = "junes_rel")
@Getter
@Setter
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "user_id")
    private UUID userID;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private String email;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @Column(name = "is_email_verified", nullable = false)
    private Boolean isEmailVerified = false;

    @Column(name = "verification_token_hash")
    private String verificationTokenHash;

    @Column(name = "verification_token_issued_at")
    private Long verificationTokenIssuedAt;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Column(name = "history_list", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> historyList = new ArrayList<>();

    @Column(name = "address_list", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private List<AddressDTO> addressList = new ArrayList<>();

    @Column(name = "payment_info_list", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private List<PaymentMethodDTO> paymentInfoList = new ArrayList<>();

    @Column(name = "created_on", nullable = false)
    @CreationTimestamp // Hibernate will automatically set this on insert
    private LocalDateTime createdOn;

    @Column(name = "updated_on")
    @UpdateTimestamp // Hibernate will automatically update this on modify
    private LocalDateTime updatedOn;

    public List<String> getHistoryList() {
        return Collections.unmodifiableList(historyList);
    }

    public List<AddressDTO> getAddressList() {
        return Collections.unmodifiableList(addressList);
    }

    public List<PaymentMethodDTO> getPaymentInfoList() {
        return Collections.unmodifiableList(paymentInfoList);
    }

    public void setHistoryList(List<String> historyList) {
        this.historyList = historyList != null ? new ArrayList<>(historyList) : new ArrayList<>();
    }

    public void setAddressList(List<AddressDTO> addressList) {
        this.addressList = addressList != null ? new ArrayList<>(addressList) : new ArrayList<>();
    }

    public void setPaymentInfoList(List<PaymentMethodDTO> paymentInfoList) {
        this.paymentInfoList = paymentInfoList != null ? new ArrayList<>(paymentInfoList) : new ArrayList<>();
    }
}
