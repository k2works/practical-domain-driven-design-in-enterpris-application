package com.example.cargotracker.externaldata.domain.model.rules;

import com.example.cargotracker.shared.annotation.ddd.DomainRule;

/**
 * 取込のファイルの上限。1 つの取込で受けるファイルの物理データ行の数とバイト数の上限（Bolt 39b4 の「人に諮ること」1。PERF-06 の規模の前提
 * 「1 ファイル 1 万件以下」）。行の原本はファイルの一部なので、行ごとの上限は持たない。取込のサービスが承認の状態の確かめの後に判定する。
 */
@DomainRule
public final class IngestionFileLimit {

    /** 物理データ行の上限（ヘッダーとレコードの外の空行を除く。受け取った件数と同じ数え方）。 */
    public static final int MAX_DATA_LINES = 10_000;

    /** ファイル全体のバイト数の上限（40 MiB。multipart の上限と同じ 2 進の単位）。 */
    public static final int MAX_BYTES = 40 * 1024 * 1024;

    private IngestionFileLimit() {}

    /**
     * ファイルが上限を超えるか。ちょうどの値は受ける。
     *
     * @param dataLines 物理データ行の数
     * @param bytes ファイル全体のバイト数
     * @return どちらかが上限を超えれば true
     */
    public static boolean exceeds(int dataLines, int bytes) {
        return dataLines > MAX_DATA_LINES || bytes > MAX_BYTES;
    }
}
