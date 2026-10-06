package com.example.cargotracker.quotation.interfaces.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/** C-05 見積りへの回答（US-24 AC1。Bolt 12）。 */
@Controller
@RequestMapping("/customer/transport-requests/{number}/quotations/{quotationNo}/response")
public class QuotationResponseController {}
