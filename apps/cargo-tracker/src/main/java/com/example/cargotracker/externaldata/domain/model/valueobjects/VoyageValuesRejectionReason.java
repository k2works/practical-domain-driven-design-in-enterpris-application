package com.example.cargotracker.externaldata.domain.model.valueobjects;

/**
 * 航海の値を拒否した理由（Bolt 39b2b）。航海の値・寄港の値が拒む理由と、組み立てが寄港の列を区切れない理由。組み立ては理由の言葉を
 * そのまま隔離の注記の詳細に写し、型の外で理由を推し量らない。
 */
public enum VoyageValuesRejectionReason {
    /** 航海番号が空か空白だけ。 */
    VOYAGE_NUMBER_BLANK("航海番号が空です"),
    /** 航海番号が上限の文字数（{@link VoyageValues#VOYAGE_NUMBER_MAX_LENGTH}）より長い。 */
    VOYAGE_NUMBER_TOO_LONG("航海番号が " + VoyageValues.VOYAGE_NUMBER_MAX_LENGTH + " 文字より長いです"),
    /** 寄港が下限の数（{@link VoyageValues#MIN_PORT_CALLS}）より少ない。 */
    TOO_FEW_PORT_CALLS("寄港が足りません（" + VoyageValues.MIN_PORT_CALLS + " つ以上）"),
    /** 寄港が上限の数（{@link VoyageValues#MAX_PORT_CALLS}）より多い（Bolt 39b3）。 */
    TOO_MANY_PORT_CALLS("寄港が多すぎます（" + VoyageValues.MAX_PORT_CALLS + " まで）"),
    /** 同じ港・同じ到着・同じ出発の寄港が重なる（重なる寄港の番号を持つ）。 */
    DUPLICATE_PORT_CALLS("寄港が同じです"),
    /** 寄港を港・到着・出発の 3 つに区切れない（組み立てが見つける）。 */
    UNSPLITTABLE_PORT_CALL("港;到着;出発 の 3 つに区切れません"),
    /** 寄港に到着も出発もない。 */
    NO_ARRIVAL_OR_DEPARTURE("到着か出発が要ります"),
    /** 寄港の出発が到着より前。 */
    DEPARTURE_BEFORE_ARRIVAL("到着より前に出発しています");

    private final String wording;

    VoyageValuesRejectionReason(String wording) {
        this.wording = wording;
    }

    /** 理由の業務の言葉（寄港の番号を除く）。 */
    public String wording() {
        return wording;
    }
}
