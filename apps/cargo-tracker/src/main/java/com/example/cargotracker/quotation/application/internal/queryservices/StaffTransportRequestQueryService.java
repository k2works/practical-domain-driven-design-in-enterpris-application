package com.example.cargotracker.quotation.application.internal.queryservices;

import com.example.cargotracker.quotation.domain.model.aggregates.RequiredDocumentStorage;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequestRepository;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestNumber;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestSummary;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 社内の営業担当者が輸送要求を照会する入力ポート（S-02・S-03）。営業担当者が扱えるのは担当企業の輸送要求だけで（Bolt 38f。US-16 AC4）、
 * 判定は {@link StaffTransportRequestScope} の 1 か所。荷主の画面から誤って使わないよう、荷主用の {@link TransportRequestQueryService} と
 * 分ける（Bolt 5 レビュー R-10）。
 *
 * <p>{@code @Service} は JIG がユースケースとして読むための印で、部品探索の対象にはしない（CargoTrackerApplication）。
 * 組み立ては {@code QuotationConfiguration} が担う。
 */
@Service
public class StaffTransportRequestQueryService {

    private final TransportRequestRepository repository;
    private final RequiredDocumentStorage documentStorage;
    private final StaffTransportRequestScope scope;

    public StaffTransportRequestQueryService(
            TransportRequestRepository repository,
            RequiredDocumentStorage documentStorage,
            StaffTransportRequestScope scope) {
        this.repository = repository;
        this.documentStorage = documentStorage;
        this.scope = scope;
    }

    /** 業務番号で輸送要求を、操作者の担当範囲で引き当てる。 */
    @Transactional(readOnly = true)
    public StaffTransportRequestLookup findByNumber(TransportRequestNumber number, AuthenticatedActor actor) {
        return scope.find(number, actor);
    }

    /** 担当企業の審査中の輸送要求を、最初の提出時刻の古い順に一覧する（受付一覧）。 */
    @Transactional(readOnly = true)
    public List<TransportRequestSummary> findUnderReview(AuthenticatedActor actor) {
        return scope.listWithin(actor, repository::findUnderReviewSummaries);
    }

    /** 担当企業の見積り作成中の輸送要求を、最初の提出時刻の古い順に一覧する（受付一覧の見積り作成中。Bolt 10）。 */
    @Transactional(readOnly = true)
    public List<TransportRequestSummary> findQuoting(AuthenticatedActor actor) {
        return scope.listWithin(actor, repository::findQuotingSummaries);
    }

    /** 担当企業の輸送要求の必要書類を取得する（D-20: 営業担当者は開ける）。担当範囲の外なら空。 */
    @Transactional(readOnly = true)
    public Optional<DocumentFile> findDocument(
            TransportRequestNumber number, int versionNo, int documentNo, AuthenticatedActor actor) {
        if (!(scope.find(number, actor) instanceof StaffTransportRequestLookup.Found(var request))) {
            return Optional.empty();
        }
        return request.document(versionNo, documentNo)
                .map(document -> new DocumentFile(document, documentStorage.read(document.objectKey())));
    }
}
