package com.example.cargotracker.externaldata.domain.model.valueobjects;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 航海の値を拒否した（Bolt 39b2b）。理由（{@link VoyageValuesRejectionReason}）と、理由にかかわる寄港の番号（1 から。なければ空）を持つ。
 * 組み立ては {@link #detail()} をそのまま隔離の注記の詳細に写す。値の型の拒否として {@link IllegalArgumentException} の一種にする。
 */
public final class VoyageValuesRejected extends IllegalArgumentException {

    private static final long serialVersionUID = 1L;

    private final VoyageValuesRejectionReason reason;
    private final transient List<Integer> portCallNumbers;

    public VoyageValuesRejected(VoyageValuesRejectionReason reason) {
        this(reason, List.of());
    }

    public VoyageValuesRejected(VoyageValuesRejectionReason reason, List<Integer> portCallNumbers) {
        super(detailOf(Objects.requireNonNull(reason, "reason"), List.copyOf(portCallNumbers)));
        this.reason = reason;
        this.portCallNumbers = List.copyOf(portCallNumbers);
    }

    public VoyageValuesRejectionReason reason() {
        return reason;
    }

    /** 理由にかかわる寄港の番号（1 から）。 */
    public List<Integer> portCallNumbers() {
        return portCallNumbers;
    }

    /** 同じ理由を、その番号の寄港の拒否にする（寄港の値は自分の番号を知らないので、組み立てが番号を足す）。 */
    public VoyageValuesRejected at(int portCallNumber) {
        return new VoyageValuesRejected(reason, List.of(portCallNumber));
    }

    /** 注記の詳細（例「3 つ目の寄港: 到着か出発が要ります」「2 つ目と 3 つ目の寄港が同じです」）。 */
    public String detail() {
        return getMessage();
    }

    private static String detailOf(VoyageValuesRejectionReason reason, List<Integer> numbers) {
        if (numbers.isEmpty()) {
            return reason.wording();
        }
        String which = numbers.stream().map(n -> n + " つ目").collect(Collectors.joining("と ")) + "の";
        return reason == VoyageValuesRejectionReason.DUPLICATE_PORT_CALLS
                ? which + reason.wording()
                : which + "寄港: " + reason.wording();
    }
}
