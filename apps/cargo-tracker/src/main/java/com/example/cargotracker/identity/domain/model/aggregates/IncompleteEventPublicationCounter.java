package com.example.cargotracker.identity.domain.model.aggregates;

/**
 * 未完了のイベントの発行の件数を数える送信ポート（NFR の「イベントの発行が欠けていないか」の確かめ。W9 の Bolt 44-2）。実装は infrastructure に置く。
 */
@FunctionalInterface
public interface IncompleteEventPublicationCounter {

    /** 未完了の発行（リスナーが完了していないイベント）の件数。 */
    long count();
}
