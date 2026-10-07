package com.example.cargotracker.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.CargoTrackerApplication;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * AT-03: コンテキスト間の依存は公開 API とドメインイベントだけに限る（ADR-001）。
 */
class ModularityTest {

    private final ApplicationModules modules = ApplicationModules.of(CargoTrackerApplication.class);

    @Test
    void 見積りと経路設計とアクセス監査と共有カーネルがモジュールとして認識される() {
        assertThat(modules.stream().map(module -> module.getIdentifier().toString()))
                .contains("quotation", "routing", "identity", "shared");
    }

    @Test
    void モジュール間の依存が境界を守っている() {
        modules.verify();
    }
}
