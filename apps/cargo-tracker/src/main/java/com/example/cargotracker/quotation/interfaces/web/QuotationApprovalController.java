package com.example.cargotracker.quotation.interfaces.web;

import com.example.cargotracker.quotation.application.internal.commandservices.QuotationResponseService;
import com.example.cargotracker.quotation.application.internal.queryservices.QuotationQueryService;
import java.time.Clock;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * C-17 見積りと経路の承認（US-24 AC4・AC5。Bolt 20）。
 */
@Controller
@RequestMapping("/customer/transport-requests/{number}/quotations/{quotationNo}/approval")
public class QuotationApprovalController {

    private final QuotationResponseService responseService;
    private final QuotationQueryService quotationQueryService;
    private final Clock clock;

    public QuotationApprovalController(
            QuotationResponseService responseService, QuotationQueryService quotationQueryService, Clock clock) {
        this.responseService = responseService;
        this.quotationQueryService = quotationQueryService;
        this.clock = clock;
    }
}
