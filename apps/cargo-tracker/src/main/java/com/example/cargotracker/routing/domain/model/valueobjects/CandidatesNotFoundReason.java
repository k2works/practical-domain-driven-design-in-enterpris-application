package com.example.cargotracker.routing.domain.model.valueobjects;

/**
 * 適合する候補が見つからなかった理由（UC-06 3a。Bolt 40-14）。専門判断（有人案件）へ引き継ぐ理由で、イベントには名前の文字列で載せる。
 */
public enum CandidatesNotFoundReason {
    /** 経路に使える航海がない（外部データがまだ取り込まれていない、または形式の誤りで隔離のまま。G-06b）。候補は 0 件。 */
    EXTERNAL_DATA_MISSING,
    /** 候補はあるが、基本制約（到着期限・必要最小接続時間・情報鮮度）を満たす候補がない。 */
    NO_CONFORMING_CANDIDATE
}
