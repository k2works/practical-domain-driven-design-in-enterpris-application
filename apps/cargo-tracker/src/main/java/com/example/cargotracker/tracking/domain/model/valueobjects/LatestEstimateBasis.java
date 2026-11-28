package com.example.cargotracker.tracking.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;
import java.util.Objects;
import java.util.OptionalInt;

/**
 * 見込みの根拠。最新の到着見込みがどこから来たか（当初の到着予定・採用の実績・最後の区間の航海の到着予定）を示す（Bolt 39g4c。【業務の判断】(aw)、台帳
 * D-139）。最新の到着見込みの規則が見込みと同じ 1 回の評価で決め、追跡記録が見込みと一緒に保存する。実績のときだけ実績番号を持つ（出発・積替・到着の別は
 * その実績の種類が持つ）。航海の到着予定の航海番号と取得時刻は予定の最後の区間と追跡記録の航海の到着予定から読む。
 *
 * @param kind 種類
 * @param milestoneNo 根拠の実績の実績番号（種類が実績のときだけ。1 以上）
 */
@ValueObject
public record LatestEstimateBasis(Kind kind, OptionalInt milestoneNo) {

    /** 見込みの根拠の種類（表の {@code latest_eta_basis} の値）。 */
    public enum Kind {
        /** 当初の到着予定（見込みを動かす採用の実績も遅い航海の到着予定もない）。 */
        ORIGINAL_ETA,
        /** 採用の実績（出発・積替の実績の遅れで送った見込み、または到着の実績）。 */
        MILESTONE,
        /** 最後の区間の航海の到着予定（実績の遅れの見込みより厳密に遅い）。 */
        VOYAGE_ARRIVAL
    }

    public LatestEstimateBasis {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(milestoneNo, "milestoneNo");
        boolean milestoneKind = kind == Kind.MILESTONE;
        if (milestoneKind != milestoneNo.isPresent()) {
            throw new IllegalArgumentException("見込みの根拠の実績番号は種類が実績のときだけ持ちます: " + kind);
        }
        if (milestoneNo.isPresent() && milestoneNo.getAsInt() < 1) {
            throw new IllegalArgumentException("見込みの根拠の実績番号は 1 から: " + milestoneNo.getAsInt());
        }
    }

    /** 当初の到着予定。 */
    public static LatestEstimateBasis originalEta() {
        return new LatestEstimateBasis(Kind.ORIGINAL_ETA, OptionalInt.empty());
    }

    /** 採用の実績（実績番号）。 */
    public static LatestEstimateBasis milestone(int milestoneNo) {
        return new LatestEstimateBasis(Kind.MILESTONE, OptionalInt.of(milestoneNo));
    }

    /** 最後の区間の航海の到着予定。 */
    public static LatestEstimateBasis voyageArrival() {
        return new LatestEstimateBasis(Kind.VOYAGE_ARRIVAL, OptionalInt.empty());
    }
}
