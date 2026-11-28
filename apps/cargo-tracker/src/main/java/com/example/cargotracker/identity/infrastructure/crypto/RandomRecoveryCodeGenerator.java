package com.example.cargotracker.identity.infrastructure.crypto;

import com.example.cargotracker.identity.domain.model.aggregates.RecoveryCode;
import com.example.cargotracker.identity.domain.model.aggregates.RecoveryCodeGenerator;
import com.example.cargotracker.identity.domain.model.valueobjects.NormalizedRecoveryCode;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 回復コードの生成（Bolt 31b、SEC-09、UI 設計「A-07」）。{@value RecoveryCode#CODES_PER_USER} 個を乱数で作る。形は
 * {@link NormalizedRecoveryCode} が持つ（Bolt 33d）。総当たりはロック（5 回）と bcrypt のハッシュで抑える。一組の中に同じコードは
 * 入れない。
 */
@Component
public final class RandomRecoveryCodeGenerator implements RecoveryCodeGenerator {

    private final SecureRandom random = new SecureRandom();

    @Override
    public List<NormalizedRecoveryCode> generate() {
        Set<NormalizedRecoveryCode> codes = new LinkedHashSet<>();
        byte[] bytes = new byte[NormalizedRecoveryCode.BYTES];
        while (codes.size() < RecoveryCode.CODES_PER_USER) {
            random.nextBytes(bytes);
            codes.add(NormalizedRecoveryCode.fromBytes(bytes));
        }
        return new ArrayList<>(codes);
    }
}
