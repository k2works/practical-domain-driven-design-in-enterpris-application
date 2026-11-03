package com.example.cargotracker.externaldata.application.internal.queryservices;

import java.util.Optional;

/**
 * パイロットの開始判定の結果（ED-INV-01、BR-18、US-14 AC2。Bolt 39b）。承認済みの情報源が 0 件なら開始できず、その理由を示す。
 *
 * @param approvedCount 承認済みの情報源の件数（停止中は数えない）
 */
public record PilotReadiness(int approvedCount) {

    /** 開始できない理由の文言（ドメインモデルの開始判定の照会）。 */
    static final String NO_APPROVED_SOURCE = "承認済みの情報源がありません。情報源を選定し、承認してください";

    public PilotReadiness {
        if (approvedCount < 0) {
            throw new IllegalArgumentException("承認済みの情報源の件数は 0 以上です: " + approvedCount);
        }
    }

    /** パイロットを開始できるか。承認済みの情報源が 1 件以上あれば開始できる。 */
    public boolean canStart() {
        return approvedCount > 0;
    }

    /** 開始できない理由。開始できるときは空。 */
    public Optional<String> reason() {
        return canStart() ? Optional.empty() : Optional.of(NO_APPROVED_SOURCE);
    }
}
