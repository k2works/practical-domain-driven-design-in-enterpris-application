package com.example.cargotracker.tracking.domain.model.valueobjects;

/**
 * 有人案件の操作の入力の誤り（内容が空、理由がない、新しい期限が今の期限より後でない、など。Bolt 41）。アプリケーション層が業務上の拒否として返す。
 * これ以外の {@link IllegalArgumentException}（呼び出しの誤り）は拒否にせず、そのまま失敗させる。
 */
public final class ServiceCaseInputException extends IllegalArgumentException {

    private static final long serialVersionUID = 1L;

    /** @param message 入力の誤りの理由 */
    public ServiceCaseInputException(String message) {
        super(message);
    }
}
