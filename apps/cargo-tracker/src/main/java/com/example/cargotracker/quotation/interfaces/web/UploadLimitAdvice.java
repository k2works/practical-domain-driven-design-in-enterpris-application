package com.example.cargotracker.quotation.interfaces.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * 1 回の送信の合計が multipart の上限（55 MB）を超えたときの案内。この例外はコントローラーに届く前に起きるため、
 * 入力を残したエラー要約にはできない。上限を超えない 10 MB 超の書類は、業務の規則（Q-INV-16）がエラー要約で示す。
 */
@ControllerAdvice
public class UploadLimitAdvice {

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.CONTENT_TOO_LARGE)
    public String uploadTooLarge() {
        return "quotation/transport-requests/upload-too-large";
    }
}
