package com.example.cargotracker.identity.interfaces.web;

import com.example.cargotracker.shared.domain.AuthenticatedActor;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * 共通レイアウトのヘッダー（企業名・利用者名・役割名とログアウト）のため、すべての画面のモデルにログインした利用者を置く
 * （UI 設計「ナビゲーションとレイアウト」）。未認証の画面では null。
 */
@ControllerAdvice
public class CurrentActorAdvice {

    @ModelAttribute("currentActor")
    public AuthenticatedActor currentActor(AuthenticatedActor actor) {
        return actor;
    }
}
