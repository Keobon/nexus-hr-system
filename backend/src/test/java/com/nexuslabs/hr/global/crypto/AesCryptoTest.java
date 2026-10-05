package com.nexuslabs.hr.global.crypto;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AesCryptoTest {

    private static String key(char c) {
        return Base64.getEncoder().encodeToString(String.valueOf(c).repeat(32).getBytes());
    }

    @Test
    void 암호화한_값을_복호화하면_원문() {
        AesCrypto crypto = new AesCrypto(key('a'));
        String enc = crypto.encrypt("110-123-456789");
        assertThat(enc).doesNotContain("456789");
        assertThat(crypto.decrypt(enc)).isEqualTo("110-123-456789");
    }

    @Test
    void 같은_값도_매번_다른_암호문() {
        AesCrypto crypto = new AesCrypto(key('a'));
        assertThat(crypto.encrypt("same")).isNotEqualTo(crypto.encrypt("same"));
    }

    @Test
    void 다른_키로는_복호화할_수_없다() {
        String enc = new AesCrypto(key('a')).encrypt("secret");
        assertThatThrownBy(() -> new AesCrypto(key('b')).decrypt(enc)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 키는_32바이트여야_한다() {
        assertThatThrownBy(() -> new AesCrypto(Base64.getEncoder().encodeToString(new byte[16])))
                .isInstanceOf(IllegalStateException.class);
    }
}
