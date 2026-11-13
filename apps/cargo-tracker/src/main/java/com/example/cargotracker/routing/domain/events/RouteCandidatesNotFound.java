package com.example.cargotracker.routing.domain.events;

import com.example.cargotracker.shared.annotation.ddd.DomainEvent;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.UUID;

/**
 * DE-32 適合する経路候補が見つからなかった（UC-06 3a、US-20。Bolt 40-14）。候補の算出で適合する候補がなく、経路設計者が理由を確かめて専門判断へ
 * 引き継ぐときに出す。追跡の listener が有人案件を起票する（経路設計は追跡に依存しない。ADR-014 の方向）。業務キーは（経路設計案件 ID・理由）で、
 * 再算出のたびに出るが、有人案件の重複の判定が未終結の同じ内容の二重の起票を防ぐ。イベントはドメインの型を持たない。
 *
 * @param routingCaseId 経路設計案件 ID
 * @param caseNumber 案件番号（表示用の文字列）
 * @param quotationId 依頼元の見積り ID
 * @param transportRequestId 輸送要求 ID
 * @param transportRequestVersionNo 輸送要求の版番号
 * @param shipperCompanyId 荷主企業（有人案件の対象。範囲の判定に使う）
 * @param reason 理由の名前（{@code CandidatesNotFoundReason} の名前: {@code EXTERNAL_DATA_MISSING}、{@code NO_CONFORMING_CANDIDATE}）
 * @param candidatesFound 見つけた候補の数（上限で切る前）
 * @param notFoundAt 算出して見つからなかった時刻
 * @param operatorUserId 算出した経路設計者
 */
@DomainEvent
@SuppressWarnings("java:S107") // イベントは運ぶ値をそのまま持つ
public record RouteCandidatesNotFound(
        UUID routingCaseId,
        String caseNumber,
        UUID quotationId,
        UUID transportRequestId,
        int transportRequestVersionNo,
        CompanyId shipperCompanyId,
        String reason,
        int candidatesFound,
        UtcInstant notFoundAt,
        UserId operatorUserId) {}
