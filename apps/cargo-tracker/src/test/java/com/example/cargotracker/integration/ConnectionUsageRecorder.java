package com.example.cargotracker.integration;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;

/**
 * 1 つのスレッドが握る接続とコミットを数えるテストの部品（Bolt 39b4 の行 3・4。ADR-004 の改訂の「接続」と「量」）。{@code DataSource} の
 * Bean を包み、{@link #start()} を呼んだスレッドが取った接続だけを記録する。記録するのは、同時に握っている接続の数の最大と、
 * {@link Connection#commit()} の回数。監査の listener の非同期のスレッドなど、ほかのスレッドの接続は数えない。
 *
 * <p>{@code TestcontainersConfiguration} が読み込み、PostgreSQL の統合テストの Spring の文脈を分けない（Bolt 39b4 の設計の決定）。
 * 記録していないときは接続を包まずにそのまま返す。包みは {@code DataSource} の実装のクラスの子にし（{@code HikariDataSource} を型で受ける
 * テストを壊さない）、{@code unwrap} などほかの呼び出しは包む前の Bean に渡す。
 */
public class ConnectionUsageRecorder implements BeanPostProcessor {

    private volatile Thread recordingThread;
    private final AtomicInteger openConnections = new AtomicInteger();
    private final AtomicInteger peakConnections = new AtomicInteger();
    private final AtomicInteger commits = new AtomicInteger();

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (!(bean instanceof DataSource)) {
            return bean;
        }
        ProxyFactory factory = new ProxyFactory(bean);
        factory.setProxyTargetClass(true);
        factory.addAdvice(new ConnectionInterceptor());
        return factory.getProxy(bean.getClass().getClassLoader());
    }

    /** 呼んだスレッドの記録を始める（前の記録は消す）。 */
    public void start() {
        openConnections.set(0);
        peakConnections.set(0);
        commits.set(0);
        recordingThread = Thread.currentThread();
    }

    /** 記録を止める。記録した値は次の {@link #start()} まで読める。 */
    public void stop() {
        recordingThread = null;
    }

    /** 記録しているスレッドが同時に握った接続の数の最大。 */
    public int peakConnections() {
        return peakConnections.get();
    }

    /** 記録しているスレッドの接続のコミットの回数。 */
    public int commits() {
        return commits.get();
    }

    private boolean recording() {
        return Thread.currentThread() == recordingThread;
    }

    private Connection record(Connection connection) {
        peakConnections.accumulateAndGet(openConnections.incrementAndGet(), Math::max);
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[] {Connection.class},
                new RecordedConnection(connection));
    }

    /** {@code getConnection} で取った接続を、記録しているスレッドのときだけ包む。 */
    private final class ConnectionInterceptor implements MethodInterceptor {

        @Override
        public Object invoke(MethodInvocation invocation) throws Throwable {
            Object result = invocation.proceed();
            if (result instanceof Connection connection
                    && "getConnection".equals(invocation.getMethod().getName())
                    && recording()) {
                return record(connection);
            }
            return result;
        }
    }

    /** 記録している接続。コミットを数え、閉じたら（1 回だけ）握っている数を減らす。 */
    private final class RecordedConnection implements java.lang.reflect.InvocationHandler {

        private final Connection target;
        private final AtomicBoolean closed = new AtomicBoolean();

        RecordedConnection(Connection target) {
            this.target = target;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            switch (method.getName()) {
                // 包んだ接続どうしを同じものと見るのは同じ包みのときだけ（Spring は保持している接続と渡された接続を比べる）
                case "equals" -> {
                    return proxy == args[0];
                }
                case "hashCode" -> {
                    return System.identityHashCode(proxy);
                }
                case "commit" -> {
                    if (recording()) {
                        commits.incrementAndGet();
                    }
                }
                case "close" -> {
                    if (closed.compareAndSet(false, true)) {
                        openConnections.decrementAndGet();
                    }
                }
                default -> {
                    // ほかの呼び出しは数えない
                }
            }
            try {
                return method.invoke(target, args);
            } catch (InvocationTargetException e) {
                throw e.getTargetException();
            }
        }
    }
}
