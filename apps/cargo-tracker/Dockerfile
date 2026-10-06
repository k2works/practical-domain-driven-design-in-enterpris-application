# 実行用のイメージ。リポジトリのルートの Dockerfile は開発用のツールのイメージで、これとは別。
#   runtime: 本番の成果物（bootJar）だけ。H2 を含まない（AT-06）。W10 の ECR・ECS の出発点
#   demo:    runtime に dev の H2 を足す。Heroku のデモ環境で dev プロファイルを動かす（ADR-013）
# テストは CI（cargo-tracker-ci.yml）で行い、ここでは bootJar だけを作る。

FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace
# 依存の解決を先に行い、ソースだけの変更でキャッシュを使えるようにする
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN ./gradlew --no-daemon dependencies --configuration productionRuntimeClasspath > /dev/null
COPY config config
COPY src src
# bootJar を app.jar と lib/ に展開する（起動を速くし、demo で H2 をクラスパスに足せるようにする）
RUN ./gradlew --no-daemon bootJar copyDemoLibs \
    && cp build/libs/cargo-tracker-*-SNAPSHOT.jar app.jar \
    && java -Djarmode=tools -jar app.jar extract --destination extracted

FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app
RUN useradd --system --uid 10001 --no-create-home app
COPY --from=build /workspace/extracted/ ./
USER app
# Heroku は待ち受けの port を PORT で渡す（既定は 8080）。JVM の設定は JAVA_TOOL_OPTIONS で環境ごとに渡す
ENV EXTRA_CLASSPATH=""
EXPOSE 8080
CMD ["sh", "-c", "exec java -Dserver.port=${PORT:-8080} -cp \"/app/app.jar${EXTRA_CLASSPATH}\" com.example.cargotracker.CargoTrackerApplication"]

FROM runtime AS demo
COPY --from=build /workspace/build/demo-lib/ /app/demo-lib/
# プロファイル（dev）は Heroku の Config Vars で渡す（ADR-013）
ENV EXTRA_CLASSPATH=":/app/demo-lib/*"
