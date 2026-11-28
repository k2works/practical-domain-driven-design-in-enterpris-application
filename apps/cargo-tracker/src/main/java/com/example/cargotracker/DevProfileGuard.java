package com.example.cargotracker;

import java.util.Set;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * ステージング・本番で dev プロファイルを起動時に拒否する（#48 K-03。W10 の Bolt 52-1）。dev は H2 のインメモリで動き、
 * データは再起動で失われ、開発用の利用者が入る。環境の名前は Terraform がタスクの環境変数
 * {@code CARGOTRACKER_DEPLOYMENT_ENVIRONMENT}（{@code cargotracker.deployment.environment}）で渡す。
 *
 * <p>規則: dev が有効で、環境の名前があり、{@link #ENVIRONMENTS_ALLOWING_DEV} にない値なら起動を拒否する。
 * 名前がないとき（ローカルの {@code bootRun}、テスト、Heroku のデモ）は通す。打ち間違い（{@code prod} など）は拒否の側に倒す。
 */
@Component
public class DevProfileGuard implements InitializingBean {

    /** 環境の名前のプロパティ。 */
    static final String DEPLOYMENT_PROPERTY = "cargotracker.deployment.environment";

    /** dev を許す環境の名前。ステージング・本番だけでなく、知らない名前も拒否する。 */
    static final Set<String> ENVIRONMENTS_ALLOWING_DEV = Set.of("local", "demo");

    private final Environment environment;

    public DevProfileGuard(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void afterPropertiesSet() {
        String deployment = environment.getProperty(DEPLOYMENT_PROPERTY);
        if (deployment == null || deployment.isBlank()) {
            return;
        }
        if (environment.acceptsProfiles(Profiles.of("dev")) && !ENVIRONMENTS_ALLOWING_DEV.contains(deployment)) {
            throw new IllegalStateException("環境 " + deployment + " では dev プロファイルを使えない"
                    + "（H2 のインメモリで動き、データが失われる）。SPRING_PROFILES_ACTIVE を確かめる");
        }
    }
}
