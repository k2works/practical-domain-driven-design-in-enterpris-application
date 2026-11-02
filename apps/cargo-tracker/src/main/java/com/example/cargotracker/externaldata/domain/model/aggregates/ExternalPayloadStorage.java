package com.example.cargotracker.externaldata.domain.model.aggregates;

import com.example.cargotracker.externaldata.domain.model.valueobjects.PayloadObjectKey;

/**
 * 外部原本の保存。原本（ファイル全体と行の payload）を無加工で保存し、読み戻すポート（ADR-004 の改訂。Bolt 39）。実装は W7 は DB（39a2）、
 * W10 で S3（Object Lock、RET-03）に差し替える。外部データは集約を持たない（トランザクションスクリプト。ADR-002）が、外部の技術のポートは
 * {@code domain.model.aggregates} に置く規則（CLAUDE.md、ADR-017）に従ってここに置く。
 *
 * <p>約束:
 *
 * <ul>
 *   <li>キーは呼び手が {@link PayloadObjectKey#newIngestionFile()}・{@link PayloadObjectKey#newRecordPayload()} で作る
 *   <li>同じキーを上書きしない（2 回目の保存は {@link IllegalStateException}。1 回目のバイト列が残る）
 *   <li>保存していないキーを読めば {@link java.util.NoSuchElementException}
 *   <li>呼び手は保存に成功するまで受信記録を書かない（保存 → 受信記録の順。形式の誤りで隔離する行とファイルも先に保存する）
 *   <li>補償は消さない（トランザクションが巻き戻っても原本は残し、受信記録から参照されない原本は照合で数える）
 * </ul>
 *
 * <p>巻き戻りとの関係: 実装は受信記録とは独立したトランザクションで保存し、外側のトランザクションが巻き戻っても原本を残す（ADR-004 の
 * 改訂。Bolt 39a2）。同じトランザクションで書くと、巻き戻りで原本も消え、「補償は消さない」と食い違うため。保存は外側のトランザクションの
 * 中から呼べる。DB の実装（W7〜W9）は REQUIRES_NEW で外側の接続を握ったまま 2 本目の接続を取るので、プールの大きさ以上の同時の取込で
 * 枯渇しうる（取込は同時に走らない想定なので受け入れる）。行ごとの保存の量は 39b の PERF-06 で測り、足りなければ一括の保存を足す。
 * S3 の実装の接続の扱いは W10 で決める。
 */
public interface ExternalPayloadStorage {

    /**
     * 原本を保存する。
     *
     * @throws IllegalStateException そのキーにもう原本があるとき（上書きしない）
     */
    void store(PayloadObjectKey key, byte[] payload);

    /**
     * 保存した原本を読み戻す。
     *
     * @throws java.util.NoSuchElementException そのキーの原本がないとき
     */
    byte[] read(PayloadObjectKey key);
}
