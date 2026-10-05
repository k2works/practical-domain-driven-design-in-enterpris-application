package com.example.cargotracker.quotation.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;
import java.math.BigDecimal;

/**
 * 料金明細の入力。営業担当者が入れようとする 1 行。項目が欠けていてよく、検証して違反がなければ料金明細になる。
 *
 * @param description 内容（なければ null または空）
 * @param amount 金額（なければ null）
 * @param contractReference 参照した契約条件（任意。なければ null または空）
 */
@ValueObject
public record PricingLineInput(String description, BigDecimal amount, String contractReference) {}
