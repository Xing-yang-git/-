package com.platform.ai.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AiApiInvoker 外部调用封装单元测试 — 覆盖重试、熔断（打开/快速失败/恢复）、本地缓存（命中/过期/超限清空）。
 *
 * <p>注意：重试退避与熔断阈值依赖真实时间，本测试含真实退避休眠（约 9 秒），用例间使用独立实例隔离熔断状态。</p>
 */
@DisplayName("AiApiInvoker 重试/熔断/缓存单元测试")
class AiApiInvokerTest {

    @Test
    @DisplayName("重试 - 前两次失败第三次成功时返回结果")
    void should_retryAndSucceed_when_transientFailure() {
        AiApiInvoker invoker = new AiApiInvoker();
        AtomicInteger calls = new AtomicInteger();

        String result = invoker.invoke("retry", () -> {
            if (calls.incrementAndGet() < 3) {
                throw new RuntimeException("临时故障");
            }
            return "ok";
        });

        assertThat(result).isEqualTo("ok");
        assertThat(calls.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("重试 - 三次尝试全部失败后抛出最后一次异常")
    void should_throwLastError_when_allRetriesFail() {
        AiApiInvoker invoker = new AiApiInvoker();
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> invoker.invoke("retry-exhaust", () -> {
            calls.incrementAndGet();
            throw new RuntimeException("持续故障");
        }))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("持续故障");
        assertThat(calls.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("熔断 - 连续失败 5 次后打开，后续快速失败不再调用")
    void should_openCircuit_when_fiveConsecutiveFailures() {
        AiApiInvoker invoker = new AiApiInvoker();
        AtomicInteger calls = new AtomicInteger();
        java.util.function.Supplier<String> fail = () -> {
            calls.incrementAndGet();
            throw new RuntimeException("boom");
        };

        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> invoker.invoke("circuit", fail)).hasMessage("boom");
        }
        int callsBeforeOpen = calls.get();
        assertThat(callsBeforeOpen).isEqualTo(15); // 5 次 × 3 次尝试

        // 熔断打开：第 6 次快速失败，action 不被调用
        assertThatThrownBy(() -> invoker.invoke("circuit", fail))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("熔断中");
        assertThat(calls.get()).isEqualTo(callsBeforeOpen);
    }

    @Test
    @DisplayName("熔断 - 打开期间成功一次后计数重置，不误判熔断")
    void should_resetFailureCount_when_successDuringOpen() throws Exception {
        AiApiInvoker invoker = new AiApiInvoker();
        java.util.function.Supplier<String> fail = () -> {
            throw new RuntimeException("boom");
        };
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> invoker.invoke("circuit2", fail)).hasMessage("boom");
        }
        // 手动把熔断截止时间拨回过去，模拟熔断期结束
        expireCircuit(invoker, "circuit2");

        // 熔断恢复：成功调用直接返回
        assertThat(invoker.invoke("circuit2", () -> "recovered")).isEqualTo("recovered");

        // 恢复后单次失败不应再次触发熔断（计数从 0 重新累计）
        assertThatThrownBy(() -> invoker.invoke("circuit2", fail)).hasMessage("boom");
        assertThat(invoker.invoke("circuit2", () -> "again")).isEqualTo("again");
    }

    @Test
    @DisplayName("缓存 - 未过期命中直接返回，loader 只执行一次")
    void should_returnCached_when_notExpired() {
        AiApiInvoker invoker = new AiApiInvoker();
        AtomicInteger loads = new AtomicInteger();

        String first = invoker.cached("emb:hello", () -> "值-" + loads.incrementAndGet(), 0);
        String second = invoker.cached("emb:hello", () -> "值-" + loads.incrementAndGet(), 0);

        assertThat(first).isEqualTo("值-1");
        assertThat(second).isEqualTo("值-1");
        assertThat(loads.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("缓存 - 过期后重新执行 loader")
    void should_reload_when_expired() throws Exception {
        AiApiInvoker invoker = new AiApiInvoker();
        AtomicInteger loads = new AtomicInteger();

        invoker.cached("exp:key", () -> "v-" + loads.incrementAndGet(), 1);
        Thread.sleep(5);
        String reloaded = invoker.cached("exp:key", () -> "v-" + loads.incrementAndGet(), 1);

        assertThat(reloaded).isEqualTo("v-2");
        assertThat(loads.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("缓存 - 超限清空后原键重新加载")
    void should_clearCache_when_overCapacity() {
        AiApiInvoker invoker = new AiApiInvoker();
        AtomicInteger loads = new AtomicInteger();

        invoker.cached("first", () -> "v-" + loads.incrementAndGet(), 0);
        for (int i = 0; i < 5001; i++) {
            invoker.cached("bulk-" + i, () -> "x", 0);
        }
        String reloaded = invoker.cached("first", () -> "v-" + loads.incrementAndGet(), 0);

        assertThat(reloaded).isEqualTo("v-2");
        assertThat(loads.get()).isEqualTo(2);
    }

    /** 反射把熔断状态拨回过去，模拟熔断时长到期 */
    @SuppressWarnings("unchecked")
    private void expireCircuit(AiApiInvoker invoker, String key) throws Exception {
        Field circuitsField = AiApiInvoker.class.getDeclaredField("circuits");
        circuitsField.setAccessible(true);
        Map<String, Object> circuits = (Map<String, Object>) circuitsField.get(invoker);
        Object state = circuits.get(key);
        Field openField = state.getClass().getDeclaredField("openUntilEpochMs");
        openField.setAccessible(true);
        openField.setLong(state, 0L);
    }
}
