package com.example.cargotracker.quotation.interfaces.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Objects;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * 1 回の送信の合計が multipart の上限（55 MB）を超えたときの案内。この例外はコントローラーに届く前に起きるため、
 * 入力を残したエラー要約にはできない。上限を超えない 10 MB 超の書類は、業務の規則（Q-INV-16）がエラー要約で示す。
 *
 * <p>超過は multipart の解析（コントローラーを決める前）で起きるため、対象をコントローラーで絞れない。
 * 顧客 Web のレイアウトの案内を返すのは、要求のパスが荷主の見積依頼のコントローラー（{@link TransportRequestController}）の
 * 割り当ての下のときだけにし、ほかは例外をそのまま投げ直して既定のエラーの扱いに任せる（Bolt 6〜8 レビュー R-35）。
 * パスはコントローラーの {@code @RequestMapping} から読み、ここで決め打ちしない。
 */
@ControllerAdvice
public class UploadLimitAdvice {

    /** 荷主の見積依頼のコントローラーに割り当てたパス（例: /customer/transport-requests）。 */
    private static final List<String> CUSTOMER_PATHS = List.of(Objects.requireNonNull(
                    AnnotationUtils.findAnnotation(TransportRequestController.class, RequestMapping.class),
                    "TransportRequestController の @RequestMapping")
            .value());

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.CONTENT_TOO_LARGE)
    public String uploadTooLarge(MaxUploadSizeExceededException exception, HttpServletRequest request)
            throws MaxUploadSizeExceededException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (CUSTOMER_PATHS.stream().noneMatch(path::startsWith)) {
            throw exception;
        }
        return "quotation/transport-requests/upload-too-large";
    }
}
