package com.example.cargotracker.identity.domain.model.aggregates;

import com.example.cargotracker.identity.domain.model.valueobjects.AdministrationScope;
import com.example.cargotracker.identity.domain.model.valueobjects.AssignedCompanies;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;

/**
 * 担当範囲のリポジトリ（送信ポート。Bolt 34b）。`user_assignment` の `COMPANY` の行を、システム管理者の管理範囲（Bolt 34b）と業務担当者の
 * 担当企業（Bolt 38e）の 2 つの意味で読む。同じ行の意味は役割で決まるので、管理範囲はシステム管理者のときだけ、担当企業は担当範囲のある役割
 * のときだけ読む（Bolt 38e の決定 (9)。監督の役割と業務の役割を付け替える運用の手順では、先に `COMPANY` の行を消す。W10）。担当企業は S-20
 * で書く（Bolt 38e）。管理範囲は運用の手順だけで書き、アプリは書かない（W10。確認ポイント 12。2026-10-16、human:kakimomokuri）。
 */
public interface UserAssignmentRepository {

    /** システム管理者の管理範囲（割り当てた企業）。割り当てがなければ空の管理範囲。 */
    AdministrationScope scopeOf(UserId admin);

    /** 業務担当者の担当企業（Bolt 38e）。担当範囲のある役割を持たない利用者の行は読まず、空を返す（決定 (9)）。 */
    AssignedCompanies assignedCompaniesOf(UserId user);

    /**
     * 業務担当者の担当企業のうち、範囲の中を選んだ企業に置き換える（Bolt 38e）。最初に利用者の版を条件にして版を上げ（役割の変更と同じ版。
     * 0 件ならほかの更新が先に入ったとして何も書かず false）、その後に範囲の中の差（消す行と足す行）だけを書く。変わらない行の付与の時刻と
     * 付与した人は残し、範囲の外の担当企業と `COMPANY` でない行（担当案件）は触らない。呼ぶ側のトランザクションの中で呼ぶ。
     *
     * @param user 業務担当者
     * @param expectedVersion 画面が持つ利用者の版
     * @param scope 置き換えてよい範囲（操作者が選べる企業）
     * @param selected 範囲の中で選んだ企業（範囲に含まれること）
     * @param grantedBy 操作者
     * @param at 置き換えた時刻
     */
    boolean replaceCompanies(
            UserId user,
            long expectedVersion,
            AdministrationScope scope,
            AssignedCompanies selected,
            UserId grantedBy,
            UtcInstant at);
}
