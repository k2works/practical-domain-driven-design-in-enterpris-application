package com.example.cargotracker.identity.interfaces.web;

/**
 * 再設定の session の属性。リンクを開いたときに確かめたトークンの ID を A-06 へ渡す session の属性の名前（Bolt 33a、SEC-08）。トークンの値（平文）
 * は置かない。読み書きするのは画面の層（{@link PasswordResetController}）だけ。
 */
final class PasswordResetSession {

    /** トークンの ID（{@code UUID}）を置く属性の名前。 */
    static final String TOKEN_ID = PasswordResetSession.class.getName() + ".TOKEN_ID";

    private PasswordResetSession() {}
}
