package com.example.cargotracker.identity.infrastructure.security;

import com.example.cargotracker.identity.domain.model.aggregates.RecoveryCode;
import com.example.cargotracker.identity.domain.model.aggregates.RecoveryCodeGenerator;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 回復コードの生成（Bolt 31b、SEC-09、UI 設計「A-07」）。{@value RecoveryCode#CODES_PER_USER} 個を乱数で作る。形は 8 文字の
 * 16 進を 4 文字ずつハイフンで区切る（{@code 1a2b-3c4d}。32 ビット）。総当たりはロック（5 回）と bcrypt のハッシュで抑える。
 * 一組の中に同じコードは入れない。
 */
public final class RandomRecoveryCodeGenerator implements RecoveryCodeGenerator {

    private static final int CODE_BYTES = 4;

    private final SecureRandom random = new SecureRandom();

    @Override
    public List<String> generate() {
        Set<String> codes = new LinkedHashSet<>();
        byte[] bytes = new byte[CODE_BYTES];
        while (codes.size() < RecoveryCode.CODES_PER_USER) {
            random.nextBytes(bytes);
            String hex = HexFormat.of().formatHex(bytes);
            codes.add(hex.substring(0, 4) + "-" + hex.substring(4));
        }
        return new ArrayList<>(codes);
    }
}
