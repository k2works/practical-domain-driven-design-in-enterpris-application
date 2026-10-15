package com.example.cargotracker.identity.infrastructure.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.identity.domain.model.aggregates.RecoveryCode;
import com.example.cargotracker.identity.domain.model.valueobjects.NormalizedRecoveryCode;
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
    void 回復コードを10個作り形は正規化しても変わらない() {
        List<String> codes = generator.generate();

        // 形の知識は回復コードの正規化だけに置き、ここに写さない（Bolt 33d）
        assertThat(codes)
                .hasSize(RecoveryCode.CODES_PER_USER)
                .allSatisfy(code -> assertThat(RecoveryCode.normalize(code))
                        .as("生成した値は正規化しても変わらない")
                        .map(NormalizedRecoveryCode::value)
                        .contains(code));
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
