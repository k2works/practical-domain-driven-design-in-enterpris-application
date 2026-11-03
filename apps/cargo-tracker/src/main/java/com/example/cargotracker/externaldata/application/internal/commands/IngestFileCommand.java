package com.example.cargotracker.externaldata.application.internal.commands;

import com.example.cargotracker.shared.annotation.ddd.Command;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 取込のコマンド。情報源の 1 ファイルを取り込む入力（Bolt 39b）。受け取った件数はファイルの物理データ行の数（ヘッダーを除く）で、
 * 行の並びとは別に持つ（39b2 で形式の誤りの行が並びに入らなくなっても意味を変えない。Bolt 39b の確認ポイント 1）。
 *
 * @param dataSourceId 情報源 ID
 * @param filePayload ファイル全体の原本のバイト列
 * @param receivedCount 受け取った件数
 * @param rows 取り込む行（物理行の順）
 */
@Command
public record IngestFileCommand(UUID dataSourceId, byte[] filePayload, int receivedCount, List<IngestedRow> rows) {

    public IngestFileCommand {
        Objects.requireNonNull(dataSourceId, "dataSourceId");
        filePayload = Objects.requireNonNull(filePayload, "filePayload").clone();
        rows = List.copyOf(Objects.requireNonNull(rows, "rows"));
        if (receivedCount < 0) {
            throw new IllegalArgumentException("受け取った件数は 0 以上: " + receivedCount);
        }
    }

    @Override
    public byte[] filePayload() {
        return filePayload.clone();
    }

    @Override
    public boolean equals(Object other) {
        return other
                        instanceof
                        IngestFileCommand(
                                UUID otherSource,
                                byte[] otherPayload,
                                int otherCount,
                                List<IngestedRow> otherRows)
                && dataSourceId.equals(otherSource)
                && Arrays.equals(filePayload, otherPayload)
                && receivedCount == otherCount
                && rows.equals(otherRows);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dataSourceId, Arrays.hashCode(filePayload), receivedCount, rows);
    }

    @Override
    public String toString() {
        return "IngestFileCommand[dataSourceId=" + dataSourceId + ", size=" + filePayload.length + ", receivedCount="
                + receivedCount + ", rows=" + rows.size() + "]";
    }

    /**
     * 取り込む行。取込ファイルの形式の正しい 1 行の値と原本。payload はその行の物理行の原文（行末の改行を除く。UTF-8）で、主要実績の
     * 追跡番号は payload の中にだけある（値の列は 39b2）。コマンドの一部なので入れ子にする（単独のコマンドではない）。
     *
     * @param lineNumber 物理行の番号
     * @param externalId 外部 ID
     * @param externalVersion 外部版
     * @param acquiredAt 取得時刻
     * @param payload 行の原本のバイト列
     */
    public record IngestedRow(
            int lineNumber, String externalId, String externalVersion, Instant acquiredAt, byte[] payload) {

        public IngestedRow {
            Objects.requireNonNull(externalId, "externalId");
            Objects.requireNonNull(externalVersion, "externalVersion");
            Objects.requireNonNull(acquiredAt, "acquiredAt");
            payload = Objects.requireNonNull(payload, "payload").clone();
            if (lineNumber < 1) {
                throw new IllegalArgumentException("物理行の番号は 1 以上: " + lineNumber);
            }
        }

        @Override
        public byte[] payload() {
            return payload.clone();
        }

        @Override
        public boolean equals(Object other) {
            return other
                            instanceof
                            IngestedRow(
                                    int otherLine,
                                    String otherId,
                                    String otherVersion,
                                    Instant otherAcquired,
                                    byte[] otherPayload)
                    && lineNumber == otherLine
                    && externalId.equals(otherId)
                    && externalVersion.equals(otherVersion)
                    && acquiredAt.equals(otherAcquired)
                    && Arrays.equals(payload, otherPayload);
        }

        @Override
        public int hashCode() {
            return Objects.hash(lineNumber, externalId, externalVersion, acquiredAt, Arrays.hashCode(payload));
        }

        @Override
        public String toString() {
            return "IngestedRow[lineNumber=" + lineNumber + ", externalId=" + externalId + ", externalVersion="
                    + externalVersion + ", acquiredAt=" + acquiredAt + ", size=" + payload.length + "]";
        }
    }
}
