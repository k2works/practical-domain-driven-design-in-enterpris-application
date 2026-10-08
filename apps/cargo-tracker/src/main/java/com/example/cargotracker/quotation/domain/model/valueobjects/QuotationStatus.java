package com.example.cargotracker.quotation.domain.model.valueobjects;

/**
 * 見積りの状態。値の名前はデータモデルの状態の値と同じにする。Bolt 10 は提示までの 3 つ、Bolt 11 で失効と置換済みを、Bolt 12 で詳細設計依頼済みを、Bolt 20 で荷主承認待ちと承認済みを足した。
 * 失効は、有効期限を過ぎても状態を書き換えず判定時刻で決める（{@code Quotation#isExpiredAt}）。状態の失効は、再見積りのときに
 * 有効期限を過ぎていた旧版にだけ記録する（2026-10-05 の決定）。
 */
public enum QuotationStatus {
    /** 作成中（算出の前）。 */
    DRAFT,
    /** 承認待ち（算出した後、社内承認の前）。 */
    PENDING_APPROVAL,
    /** 提示済み（社内承認して荷主へ提示した）。 */
    PRESENTED,
    /** 詳細設計依頼済み（荷主が詳細経路設計へ進むと回答した。US-24 AC1。Bolt 12）。 */
    ROUTING_REQUESTED,
    /** 荷主承認待ち（経路設計が確定した経路版を割り当てた。US-24 AC4、R-INV-11。Bolt 20）。 */
    AWAITING_SHIPPER_APPROVAL,
    /** 承認済み（荷主が見積りと経路を承認した。US-24 AC4、Q-INV-10。Bolt 20）。 */
    APPROVED,
    /** 失効（再見積りのときに有効期限を過ぎていた。終わりの状態）。 */
    EXPIRED,
    /** 置換済み（再見積りで新しい見積りに置き換えた。終わりの状態）。 */
    REPLACED;

    /**
     * 判定時刻で失効し得るか（承認待ち・提示済み・詳細設計依頼済み・荷主承認待ち・承認済み。Q-INV-06・07）。状態を足すときはここだけを直す
     * （Bolt 12 レビュー R-05）。
     */
    public boolean expiresByTime() {
        return this == PENDING_APPROVAL || this == PRESENTED || isRoutingStarted();
    }

    /** 荷主が詳細経路設計を依頼した後か（詳細設計依頼済み・荷主承認待ち・承認済み）。経路設計の途中から後は再見積りできない（Bolt 12・20）。 */
    public boolean isRoutingStarted() {
        return this == ROUTING_REQUESTED || this == AWAITING_SHIPPER_APPROVAL || this == APPROVED;
    }

    /** 1 つの輸送要求に 1 つだけの見積りに数えるか（作成中と、判定時刻で失効し得る状態。Q-INV-18）。DB の部分一意インデックスと合わせる。 */
    public boolean countsAsActive() {
        return this == DRAFT || expiresByTime();
    }
}
