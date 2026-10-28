package com.example.cargotracker.shared.domain;

/**
 * 担当範囲の外の試行を記録する対象の種類（Bolt 38f、ADR-019）。監査記録の {@code target_type}（{@code VARCHAR(50)}）に名前で書く。
 * 経路設計・追跡の側（38g）で値を足す。予約の側（38f2）は確定の対象が見積りなので {@link #QUOTATION} を使い、値を足さない。
 */
public enum ScopedResourceKind {
    /** 輸送要求（ID は輸送要求番号。例 {@code TR-2026-0001}）。 */
    TRANSPORT_REQUEST,
    /** 見積り（ID は {@code 輸送要求番号/見積り番号}。例 {@code TR-2026-0001/2}）。 */
    QUOTATION
}
