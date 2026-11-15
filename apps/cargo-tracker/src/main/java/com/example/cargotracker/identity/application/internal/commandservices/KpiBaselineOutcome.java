package com.example.cargotracker.identity.application.internal.commandservices;

import com.example.cargotracker.identity.domain.model.aggregates.KpiBaseline;
import java.util.Objects;

/**
 * KPI 基準値の登録の結果（US-21 AC5。W9 の Bolt 44-2）。
 */
public sealed interface KpiBaselineOutcome {

    /**
     * 登録した。
     *
     * @param baseline 登録した基準値
     */
    record Registered(KpiBaseline baseline) implements KpiBaselineOutcome {

        public Registered {
            Objects.requireNonNull(baseline, "baseline");
        }
    }

    /**
     * 入力が規則に合わない（期間・値・算出方法）。何も保存しない。
     *
     * @param message 画面に示す文
     */
    record InvalidInput(String message) implements KpiBaselineOutcome {

        public InvalidInput {
            Objects.requireNonNull(message, "message");
        }
    }

    /** 監査担当者でない（登録できない）。 */
    record NotPermitted() implements KpiBaselineOutcome {}
}
