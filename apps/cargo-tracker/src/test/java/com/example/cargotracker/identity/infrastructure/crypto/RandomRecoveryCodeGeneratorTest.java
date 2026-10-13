package com.example.cargotracker.identity.infrastructure.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.identity.domain.model.aggregates.RecoveryCode;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * 回復コードの生成（Bolt 31b、SEC-09、UI 設計「A-07」）。10 個を乱数で作り、形は 8 文字の 16 進を 4 文字ずつハイフンで区切る。
 */
class RandomRecoveryCodeGeneratorTest {

    private final RandomRecoveryCodeGenerator generator = new RandomRecoveryCodeGenerator();

    @Test
    void 回復コードを10個作り形は4文字ずつ区切った8文字の16進() {
        List<String> codes = generator.generate();

        assertThat(codes)
                .hasSize(RecoveryCode.CODES_PER_USER)
                .allMatch(code -> code.matches("[0-9a-f]{4}-[0-9a-f]{4}"));
    }

    @Test
    void 一組の中に同じコードはなく組ごとに違う() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 50; i++) {
            List<String> codes = generator.generate();
            assertThat(new HashSet<>(codes)).hasSize(codes.size());
            seen.addAll(codes);
        }

        assertThat(seen).as("50 組で重ならない（32 ビットの乱数）").hasSize(50 * RecoveryCode.CODES_PER_USER);
    }
}
