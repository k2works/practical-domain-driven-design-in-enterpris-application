package com.example.cargotracker.shared.domain;

/**
 * 担当範囲の外の試行を記録する対象の種類（Bolt 38f、ADR-019）。監査記録の {@code target_type}（{@code VARCHAR(50)}）に名前で書く。
 * 予約の側（38f2）は確定の対象が見積りなので {@link #QUOTATION} を使い、値を足さない。経路設計・追跡の側（38g）の値を足した。
 */
public enum ScopedResourceKind {
    /** 輸送要求（ID は輸送要求番号。例 {@code TR-2026-0001}）。 */
    TRANSPORT_REQUEST,
    /** 見積り（ID は {@code 輸送要求番号/見積り番号}。例 {@code TR-2026-0001/2}）。 */
    QUOTATION,
    /** 経路設計案件（ID は案件番号。例 {@code RC-2026-0001}。Bolt 38g）。 */
    ROUTING_CASE,
    /** 追跡記録（ID は追跡番号。Bolt 38g）。 */
    TRACKING_RECORD
}
