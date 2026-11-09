package com.example.cargotracker.routing.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;

/**
 * 古くなった理由の区分。候補の区間 1 つに 1 つで、順は航海がない → 今の航海と合わない（情報版が違うか、同じ情報版で予定が違うか）→
 * 適合の候補だけ取得から許容時間を過ぎた（【業務の判断】(y)・(z)。Bolt 39f2）。
 *
 * <p>{@code INFO_VERSION_CHANGED} は予定の変更を含まない（予定の変更は {@code SCHEDULE_CHANGED}）。確定の拒否の理由の同じ名前の値は
 * 予定の変更を含むので、範囲が違う（W10 の改名に含める）。
 */
@ValueObject
public enum StaleLegReason {
    /** 今の航海の一覧に区間の航海がない。 */
    VOYAGE_MISSING,
    /** 区間の情報版と今の航海の採用情報版が違う。 */
    INFO_VERSION_CHANGED,
    /** 同じ情報版で区間の予定が今の航海と違う。 */
    SCHEDULE_CHANGED,
    /** 今の航海の情報が取得から許容時間を過ぎた（適合の候補だけ）。 */
    INFO_INSUFFICIENT
}
