package com.example.cargotracker.booking.infrastructure.persistence;

import com.example.cargotracker.booking.domain.model.aggregates.BookingRepository;
import com.example.cargotracker.booking.domain.model.aggregates.TrackingNumberIssuer;
import com.example.cargotracker.booking.domain.model.valueobjects.TrackingNumber;
import java.util.random.RandomGenerator;

/**
 * 追跡番号の発行の実装（BR-07。Bolt 23）。暗号論的に安全な乱数で作り、発行の前に使われていないかを確かめ、重なれば引き直す
 * （上限 3 回）。PostgreSQL は一意制約の違反でトランザクションを中断し同じトランザクションでは引き直せないため、違反を待たずに確かめる。
 * 確かめた後に別の確定と重なったときは一意制約が止め、確定の失敗になる。
 */
public class RandomTrackingNumberIssuer implements TrackingNumberIssuer {

    static final int MAX_ATTEMPTS = 3;

    private final BookingRepository repository;
    private final RandomGenerator random;

    public RandomTrackingNumberIssuer(BookingRepository repository, RandomGenerator random) {
        this.repository = repository;
        this.random = random;
    }

    @Override
    public TrackingNumber issue() {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            TrackingNumber candidate = TrackingNumber.generate(random);
            if (!repository.existsByTrackingNumber(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("追跡番号を " + MAX_ATTEMPTS + " 回引いても使われていない番号がなかった");
    }
}
