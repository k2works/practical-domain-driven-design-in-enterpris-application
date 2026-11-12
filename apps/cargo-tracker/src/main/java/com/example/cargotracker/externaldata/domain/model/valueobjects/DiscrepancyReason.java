package com.example.cargotracker.externaldata.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;

/**
 * 差異の理由。取込で差異に分類した原本が、なぜ自動で採用されなかったか（US-14 AC5、ED-INV-03。Bolt 39h の開始準備の決定 (4)、
 * 【業務の判断】(az)・(ba)）。両方に当たるときは版の逆転を理由にする（理由は 1 つ）。区分は受信記録の列に持たず、注記の文と DE-31・監査に残す。
 */
@ValueObject
public enum DiscrepancyReason {
    /** 版の逆転。今の外部版の取得時刻が、この外部 ID の最後に取得した採用値の取得時刻より厳密に前（同じ時刻は逆転でない）。 */
    VERSION_REVERSED,
    /** 既存の採用値との矛盾。主要実績の追跡番号（対象のキー）が、この外部 ID の採用値（公開した時刻の順で最も新しいもの）と違う。 */
    TARGET_KEY_DIFFERS
}
