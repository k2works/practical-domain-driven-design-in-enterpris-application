package com.example.cargotracker.externaldata.application.internal.commandservices;

/**
 * 1 つの取込の形式の誤りのまとめての却下の結果（{@link ReceivedRecordDecisionService#rejectKeyless}。Bolt 39d2b）。断るときは判断を追記せず、
 * イベントも出さない（例外にせず種類で返す）。対象はその取込の判断を待つキーのない隔離（外部原本を持たない隔離）。
 */
public sealed interface KeylessRejectionOutcome {

    /**
     * 却下した。
     *
     * @param count 実際に却下した件数（読んだ後にほかの人が 1 件ずつ却下した分は数えない）
     */
    record RejectedAll(int count) implements KeylessRejectionOutcome {

        public RejectedAll {
            if (count < 1) {
                throw new IllegalArgumentException("却下した件数は 1 以上: " + count);
            }
        }
    }

    /** 操作者がデータ責任者でない（URL の認可に加えてサービスでも確かめる）。 */
    record NotPermitted() implements KeylessRejectionOutcome {}

    /** 取込がない。 */
    record NotFound() implements KeylessRejectionOutcome {}

    /** その取込に判断を待つキーのない隔離がない（ほかの人が先に却下した・二重の送信・読んだ分がすべて先に却下された）。 */
    record NothingToReject() implements KeylessRejectionOutcome {}
}
