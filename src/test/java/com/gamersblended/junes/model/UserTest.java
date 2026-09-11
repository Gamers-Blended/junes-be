package com.gamersblended.junes.model;

import com.gamersblended.junes.dto.AddressDTO;
import com.gamersblended.junes.dto.PaymentMethodDTO;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserTest {

    @Test
    void getHistoryList_returnsUnmodifiableList() {
        User user = new User();
        user.setHistoryList(List.of("product-1"));

        List<String> historyList = user.getHistoryList();

        assertThatThrownBy(() -> historyList.add("product-2"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void setHistoryList_defensivelyCopiesInput() {
        User user = new User();
        List<String> historyList = new ArrayList<>(List.of("product-1"));

        user.setHistoryList(historyList);
        historyList.add("product-2");

        assertThat(user.getHistoryList()).containsExactly("product-1");
    }

    @Test
    void setHistoryList_replacesNullWithEmptyList() {
        User user = new User();

        user.setHistoryList(null);

        assertThat(user.getHistoryList()).isEmpty();
    }

    @Test
    void getAddressList_returnsUnmodifiableList() {
        User user = new User();
        user.setAddressList(List.of(new AddressDTO()));

        List<AddressDTO> addressList = user.getAddressList();
        AddressDTO newAddress = new AddressDTO();

        assertThatThrownBy(() -> addressList.add(newAddress))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void setAddressList_defensivelyCopiesInput() {
        User user = new User();
        AddressDTO address = new AddressDTO();
        List<AddressDTO> addressList = new ArrayList<>(List.of(address));

        user.setAddressList(addressList);
        addressList.add(new AddressDTO());

        assertThat(user.getAddressList()).containsExactly(address);
    }

    @Test
    void setAddressList_replacesNullWithEmptyList() {
        User user = new User();

        user.setAddressList(null);

        assertThat(user.getAddressList()).isEmpty();
    }

    @Test
    void getPaymentInfoList_returnsUnmodifiableList() {
        User user = new User();
        user.setPaymentInfoList(List.of(new PaymentMethodDTO()));

        List<PaymentMethodDTO> paymentInfoList = user.getPaymentInfoList();
        PaymentMethodDTO newPaymentMethod = new PaymentMethodDTO();

        assertThatThrownBy(() -> paymentInfoList.add(newPaymentMethod))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void setPaymentInfoList_defensivelyCopiesInput() {
        User user = new User();
        PaymentMethodDTO paymentMethod = new PaymentMethodDTO();
        List<PaymentMethodDTO> paymentInfoList = new ArrayList<>(List.of(paymentMethod));

        user.setPaymentInfoList(paymentInfoList);
        paymentInfoList.add(new PaymentMethodDTO());

        assertThat(user.getPaymentInfoList()).containsExactly(paymentMethod);
    }

    @Test
    void setPaymentInfoList_replacesNullWithEmptyList() {
        User user = new User();

        user.setPaymentInfoList(null);

        assertThat(user.getPaymentInfoList()).isEmpty();
    }
}
