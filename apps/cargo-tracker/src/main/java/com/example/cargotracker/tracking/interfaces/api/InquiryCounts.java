package com.example.cargotracker.tracking.interfaces.api;

/**
 * 期間の照会の件数（追跡の公開 API の戻り値。W9 の Bolt 44-1）。
 *
 * @param total 照会総数（経路を問わず、引継ぎが不要なものも含む）
 * @param selfServed 顧客 Web で完結した照会数
 */
public record InquiryCounts(long total, long selfServed) {}
