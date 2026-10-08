package com.example.cargotracker.quotation.domain.model.valueobjects;

/**
 * 輸送要求の状態。値の名前はデータモデルの状態の値と同じにする。
 */
public enum TransportRequestStatus {
    /** 下書き（差戻しの後。下書きの保存は R1.0）。 */
    DRAFT,
    /** 審査中。 */
    UNDER_REVIEW,
    /** 見積り作成中（審査を確定した後）。 */
    QUOTING,
    /** 見積提示済み（見積りと経路方針を提示した後。DE-03 を受けて変える。Bolt 10）。 */
    QUOTED,
    /** 経路設計中（荷主が詳細経路設計を依頼した後。DE-16 を受けて変える。Bolt 12）。 */
    ROUTING,
    /** 荷主承認待ち（経路版を見積りに割り当てた後。DE-21 を受けて変える。Bolt 20）。 */
    AWAITING_APPROVAL,
    /** 予約待ち（荷主が見積りと経路を承認した後。DE-04 を受けて変える。本予約の確定は US-04。Bolt 20）。 */
    READY_TO_BOOK,
    /** 予約確定済み（本予約を確定した後。予約の DE-07 の listener が見積りの公開 API で変える。ADR-014。Bolt 23）。 */
    BOOKED
}
