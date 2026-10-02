package com.example.cargotracker.quotation.domain.model.aggregates;

import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestId;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestNumber;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestSummary;
import com.example.cargotracker.shared.domain.CompanyId;
import java.util.List;
import java.util.Optional;

/**
 * 輸送要求のリポジトリ（送信ポート）。実装は infrastructure に置く。
 */
public interface TransportRequestRepository {

    /** 提出した新しい輸送要求を保存する。 */
    void save(TransportRequest transportRequest);

    /**
     * 読み込んだ輸送要求の変更（状態、再提出の版、審査記録）を保存する。読み込んだときの集約の版で照合し（楽観ロック）、
     * ほかの更新が先に保存されていたら {@link ConcurrentTransportRequestUpdateException} を投げる。
     */
    void update(TransportRequest transportRequest);

    Optional<TransportRequest> findById(TransportRequestId id);

    /**
     * 荷主企業の輸送要求を業務番号で探す。業務番号は一意なので、見つかるのは高々 1 件。
     * 業務番号は連番で推測しやすいため、必ず荷主企業で絞る（他社の輸送要求は見つからない。Q-INV-08、Bolt 4 レビュー R-02）。
     */
    Optional<TransportRequest> findByNumber(TransportRequestNumber number, CompanyId shipperCompanyId);

    /**
     * 社内用: 業務番号で輸送要求を探す。営業担当者はすべての荷主の輸送要求を扱うため、荷主企業で絞らない。
     * 荷主の画面では使わない（荷主の画面は {@link #findByNumber(TransportRequestNumber, CompanyId)} で必ず絞る）。
     */
    Optional<TransportRequest> findByNumberForStaff(TransportRequestNumber number);

    /**
     * 社内用: 審査中の輸送要求を、最初の提出時刻（版 1）の古い順に一覧する（受付一覧。待たせている順）。
     * 差し戻して出し直された版も、最初の提出時刻で並べる（Bolt 5 レビュー R-05）。集約を組み立てない読み取りモデルを返す。
     */
    List<TransportRequestSummary> findUnderReviewSummaries();
}
