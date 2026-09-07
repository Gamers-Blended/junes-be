package com.gamersblended.junes.util;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenUtilsTest {

    @Test
    void hashToken_returnsKnownSha256Base64Hash_forGivenInput() throws Exception {
        String hash = TokenUtils.hashToken("password123");

        assertThat(hash).isEqualTo("75K3eLr+dx6JJFuJ7LwIpEpOFmwGZZkRiB84PURz6U8=");
    }

    @Test
    void hashToken_returnsKnownSha256Base64Hash_forEmptyString() throws Exception {
        String hash = TokenUtils.hashToken("");

        assertThat(hash).isEqualTo("47DEQpj8HBSa+/TImW+5JCeuQeRkm5NMpJWZG3hSuFU=");
    }

    @Test
    void hashToken_isDeterministic_forSameInput() throws Exception {
        String first = TokenUtils.hashToken("some-token-value");
        String second = TokenUtils.hashToken("some-token-value");

        assertThat(first).isEqualTo(second);
    }

    @Test
    void hashToken_returnsDifferentHashes_forDifferentInputs() throws Exception {
        String first = TokenUtils.hashToken("token-a");
        String second = TokenUtils.hashToken("token-b");

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void hashToken_returnsValidBase64EncodedThirtyTwoByteDigest() throws Exception {
        String hash = TokenUtils.hashToken("arbitrary-token");

        byte[] decoded = Base64.getDecoder().decode(hash);

        assertThat(decoded).hasSize(32); // SHA-256 digest length
    }

    @Test
    @SuppressWarnings("DataFlowIssue")
    void hashToken_throwsNullPointerException_whenTokenIsNull() {
        assertThatThrownBy(() -> TokenUtils.hashToken(null))
                .isInstanceOf(NullPointerException.class);
    }
}
