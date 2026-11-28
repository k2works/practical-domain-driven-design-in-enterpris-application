/**
 * 共有ドメイン。複数のコンテキストが同じ意味で使う値オブジェクト（識別子、UTC 時点、場所）と、例外のポート 1 つ（担当範囲の外の
 * 試行の記録 {@link com.example.cargotracker.shared.domain.OutOfScopeAttemptRecorder}。ADR-019。Bolt 38f。ArchUnit が 1 つに限る）。
 */
package com.example.cargotracker.shared.domain;
