package com.example.cargotracker.booking.application.internal.outboundservices.acl;

import com.example.cargotracker.quotation.interfaces.api.BookableQuotationQuery;
import com.example.cargotracker.quotation.interfaces.api.BookableQuotationRequest;
import com.example.cargotracker.quotation.interfaces.api.BookableQuotationResult;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**
 * 予約のテストで使う見積りの公開 API（予約確定に使える見積りの照会）の偽物。どちらの操作で照会したかを記録する（本予約の確定は確定用の
 * 照会、S-09 の GET は今の照会を使う。Bolt 37e2）。
 */
public final class RecordingBookableQuotationQuery implements BookableQuotationQuery {

    private final Function<BookableQuotationRequest, BookableQuotationResult> answer;
    // 並行の統合テストが 2 つのスレッドから呼ぶ
    private final List<BookableQuotationRequest> finds = new CopyOnWriteArrayList<>();
    private final List<BookableQuotationRequest> confirmations = new CopyOnWriteArrayList<>();

    public RecordingBookableQuotationQuery(Function<BookableQuotationRequest, BookableQuotationResult> answer) {
        this.answer = answer;
    }

    @Override
    public BookableQuotationResult find(BookableQuotationRequest request) {
        finds.add(request);
        return answer.apply(request);
    }

    @Override
    public BookableQuotationResult findForConfirmation(BookableQuotationRequest request) {
        confirmations.add(request);
        return answer.apply(request);
    }

    /** 今の照会（ロックしない）で照会した依頼。 */
    public List<BookableQuotationRequest> finds() {
        return List.copyOf(finds);
    }

    /** 確定用の照会（見積りの行を押さえる）で照会した依頼。 */
    public List<BookableQuotationRequest> confirmations() {
        return List.copyOf(confirmations);
    }

    /** どちらの操作かを問わず照会した依頼（照会の順）。 */
    public List<BookableQuotationRequest> all() {
        List<BookableQuotationRequest> all = new ArrayList<>(finds);
        all.addAll(confirmations);
        return List.copyOf(all);
    }
}
