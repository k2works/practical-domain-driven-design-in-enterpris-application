package com.example.cargotracker.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.CargoTrackerApplication;
import java.util.Arrays;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.RegexPatternTypeFilter;

/**
 * アプリケーションサービスは {@code @Service} を付けるが、部品探索では拾わない（バックエンドアーキテクチャ、2026-10-03 の人の決定）。
 * {@code @Service} は JIG がユースケースとして読むための印で、組み立ては infrastructure.config の {@code @Bean} だけが担う。
 *
 * <p>Spring は同じ名前なら {@code @Bean} が部品探索の定義を上書きするため、二重の登録は起動では見つからない。
 * そこで、アプリの部品探索の除外の設定を当てた候補に application のクラスがないことを、ここで確かめる。
 */
class ComponentScanTest {

    @Test
    void 部品探索はapplicationのクラスを拾わない() {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(true);
        ComponentScan componentScan =
                AnnotatedElementUtils.findMergedAnnotation(CargoTrackerApplication.class, ComponentScan.class);
        assertThat(componentScan).isNotNull();
        Arrays.stream(componentScan.excludeFilters())
                .filter(filter -> filter.type() == FilterType.REGEX)
                .flatMap(filter -> Arrays.stream(filter.pattern()))
                .forEach(pattern -> scanner.addExcludeFilter(new RegexPatternTypeFilter(Pattern.compile(pattern))));

        assertThat(scanner.findCandidateComponents(CargoTrackerApplication.class.getPackageName()))
                .extracting(BeanDefinition::getBeanClassName)
                .noneMatch(name -> name.contains(".application."));
    }
}
