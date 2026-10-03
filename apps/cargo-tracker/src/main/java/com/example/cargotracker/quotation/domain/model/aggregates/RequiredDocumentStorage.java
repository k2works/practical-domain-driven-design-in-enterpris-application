package com.example.cargotracker.quotation.domain.model.aggregates;

import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestId;

/**
 * 書類の保存（送信ポート）。必要書類の中身を保存してオブジェクトキーを返し、キーで中身を読む。
 * 本番は S3（ADR-008）、開発はローカルのファイルシステム（ADR-007）。実装は infrastructure に置く。
 * オブジェクトキーにファイル名を使わない（{@code quotation/{輸送要求 ID}/{UUID}}）。
 */
public interface RequiredDocumentStorage {

    /** 中身を保存し、オブジェクトキーを返す。 */
    String store(TransportRequestId transportRequestId, byte[] content);

    /** オブジェクトキーで中身を読む。 */
    byte[] read(String objectKey);
}
