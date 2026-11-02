package com.example.cargotracker.architecture;

import com.example.cargotracker.CargoTrackerApplication;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.modulith.core.ApplicationModules;

/**
 * 業務のコンテキストの一覧。モジュールから共有カーネル（{@code shared}）と基盤（{@code platform}）を除いたもの（開始準備の決定 (6)。
 * W6 横断レビュー M-3。Bolt 39）。テストの中に手で写したコンテキストの一覧が実モジュールとずれていないかを確かめるのに使う。
 */
final class BusinessContexts {

    private static final Set<String> NOT_BUSINESS = Set.of("shared", "platform");

    private BusinessContexts() {}

    static Set<String> all() {
        return ApplicationModules.of(CargoTrackerApplication.class).stream()
                .map(module -> module.getIdentifier().toString())
                .filter(name -> !NOT_BUSINESS.contains(name))
                .collect(Collectors.toUnmodifiableSet());
    }
}
