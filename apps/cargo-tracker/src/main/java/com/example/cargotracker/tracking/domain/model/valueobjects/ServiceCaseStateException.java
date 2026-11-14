package com.example.cargotracker.tracking.domain.model.valueobjects;

/**
 * 有人案件の今の状態に合わない操作（受領できない状態、対応中でないときの回答など。Bolt 41）。入力の誤り（{@link ServiceCaseInputException}）と並べて、
 * アプリケーション層が業務上の拒否として返す。これ以外の {@link IllegalStateException}（バグ）は拒否にせず、そのまま失敗させる。
 */
public final class ServiceCaseStateException extends IllegalStateException {

    private static final long serialVersionUID = 1L;

    /** @param message 状態に合わない理由 */
    public ServiceCaseStateException(String message) {
        super(message);
    }
}
