package com.example.cargotracker.identity.interfaces.web;

import com.example.cargotracker.shared.domain.Role;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * 開発環境のログインの入力済みと、開発用の利用者の選択（守りの 1 層目）。dev プロファイルでだけ部品になり、ほかの環境ではこの部品自体がない。
 * 設定の値が誤ってほかの環境に入っても、部品がなければ画面は入力済みにならない。
 *
 * <p>開発用の利用者の値を受け取る。部品として作るときは、設定（{@code cargotracker.dev-login.accounts}）から値を結び付けて渡す。
 * 守りの 2 層目（{@code identity.infrastructure.devlogin}）の設定の型を参照しない（インターフェース層は infrastructure に依存しない。
 * 2026-10-09 の人の指示）。
 */
@Component
@Profile("dev")
public final class DevLoginPrefill {

    private static final String ACCOUNTS = "cargotracker.dev-login.accounts";

    private final List<Account> accounts;

    /**
     * 開発用の利用者の値を受け取る。
     *
     * @param accounts 開発用の利用者（最初の利用者でログインの画面を入力済みにする）
     */
    public DevLoginPrefill(List<Account> accounts) {
        this.accounts = List.copyOf(Objects.requireNonNull(accounts, "accounts"));
    }

    /** 部品として作るときに、設定から開発用の利用者の値を結び付けて渡す。 */
    @Autowired
    public DevLoginPrefill(Environment environment) {
        this(boundAccounts(environment));
    }

    private static List<Account> boundAccounts(Environment environment) {
        List<Account> bound = Binder.get(environment)
                .bind(ACCOUNTS, Bindable.listOf(Account.class))
                .orElseGet(List::of);
        return Objects.requireNonNullElse(bound, List.of());
    }

    public String email() {
        return accounts.getFirst().email();
    }

    public String password() {
        return accounts.getFirst().password();
    }

    /** 選んでそのままログインできる開発用の利用者。 */
    public List<Account> accounts() {
        return accounts;
    }

    /**
     * 開発用の利用者 1 人（db/dev-data の利用者と同じ値）。
     *
     * @param role 役割（ボタンの名前に使う）
     * @param email メールアドレス
     * @param password password（開発用の固定の値。本物の秘密を使わない）
     */
    public record Account(Role role, String email, String password) {}
}
