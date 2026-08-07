package com.platform.ai.document.ocr;

import com.platform.ai.common.AiApiInvoker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * VisionOcrClient 单元测试 — 用 MockRestServiceServer 模拟 RestClient，覆盖文本内容/数组内容/空内容抛错。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("VisionOcrClient OCR 客户端单元测试")
class VisionOcrClientTest {

    private static final String BASE_URL = "https://api.example.com";

    @Mock
    private AiApiInvoker aiApiInvoker;

    private MockRestServiceServer server;
    private VisionOcrClient ocrClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();
        // 让 AiApiInvoker 直接执行调用体，单次失败即抛出（重试/缓存属于 AiApiInvoker 自身测试范围）
        when(aiApiInvoker.cached(anyString(), any(), anyLong())).thenAnswer(inv -> ((Supplier<?>) inv.getArgument(1)).get());
        when(aiApiInvoker.invoke(anyString(), any())).thenAnswer(inv -> ((Supplier<?>) inv.getArgument(1)).get());
        ocrClient = new VisionOcrClient(restClient, "test-key", "glm-4v-flash", aiApiInvoker);
    }

    @Test
    @DisplayName("OCR - content 为字符串时提取文字")
    void should_extractText_when_contentIsString() {
        server.expect(requestTo(BASE_URL + "/chat/completions"))
                .andRespond(withSuccess(
                        "{\"choices\":[{\"message\":{\"content\":\"识别出的文字\"}}]}",
                        MediaType.APPLICATION_JSON));

        String text = ocrClient.ocrImage(new byte[]{1, 2, 3}, "image/jpeg");

        assertThat(text).isEqualTo("识别出的文字");
        server.verify();
    }

    @Test
    @DisplayName("OCR - content 为数组时按 type=text 拼接文字")
    void should_concatenateText_when_contentIsArray() {
        server.expect(requestTo(BASE_URL + "/chat/completions"))
                .andRespond(withSuccess(
                        "{\"choices\":[{\"message\":{\"content\":["
                                + "{\"type\":\"text\",\"text\":\"文字A\"},"
                                + "{\"type\":\"image_url\"},"
                                + "{\"type\":\"text\",\"text\":\"文字B\"}]}}]}",
                        MediaType.APPLICATION_JSON));

        String text = ocrClient.ocrImage(new byte[]{1, 2, 3}, "image/jpeg");

        assertThat(text).isEqualTo("文字A文字B");
        server.verify();
    }

    @Test
    @DisplayName("OCR - 内容为空时抛 RuntimeException")
    void should_throw_when_contentEmpty() {
        server.expect(requestTo(BASE_URL + "/chat/completions"))
                .andRespond(withSuccess(
                        "{\"choices\":[{\"message\":{\"content\":\"\"}}]}",
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> ocrClient.ocrImage(new byte[]{1, 2, 3}, "image/jpeg"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("OCR 响应未包含文字内容");
    }
}
