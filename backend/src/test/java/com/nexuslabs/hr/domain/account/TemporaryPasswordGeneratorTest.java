package com.nexuslabs.hr.domain.account;

import com.nexuslabs.hr.domain.account.service.PasswordRule;
import com.nexuslabs.hr.domain.account.service.TemporaryPasswordGenerator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TemporaryPasswordGeneratorTest {

    @Test
    void 항상_10자이고_비밀번호_규칙을_만족하며_헷갈리는_글자가_없다() {
        TemporaryPasswordGenerator generator = new TemporaryPasswordGenerator();
        for (int i = 0; i < 1000; i++) {
            String p = generator.generate();
            assertThat(p).hasSize(10).matches(PasswordRule.REGEX).doesNotContainAnyWhitespaces();
            assertThat(p).doesNotContain("0", "O", "o", "1", "l", "I");
        }
    }
}
