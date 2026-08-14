package com.uninook.ai;

import com.uninook.exception.BusinessException;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.Executor;

@RestController
@RequestMapping("/api/ai/assistant")
public class AiAssistantStreamController {

    private static final long STREAM_TIMEOUT_MILLIS = 120_000L;

    private final AiAssistantService aiAssistantService;
    private final Executor aiStreamExecutor;

    public AiAssistantStreamController(AiAssistantService aiAssistantService,
                                       @Qualifier("aiStreamExecutor") Executor aiStreamExecutor) {
        this.aiAssistantService = aiAssistantService;
        this.aiStreamExecutor = aiStreamExecutor;
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestHeader(value = "Authorization", required = false) String authorization,
                             @Valid @RequestBody AiAssistantRequest request) {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MILLIS);
        aiStreamExecutor.execute(() -> writeStream(emitter, authorization, request));
        return emitter;
    }

    private void writeStream(SseEmitter emitter, String authorization, AiAssistantRequest request) {
        try {
            AiAssistantResponse response = aiAssistantService.stream(
                    authorization,
                    request,
                    new AiStreamChunkConsumer() {
                        @Override
                        public void accept(String chunk) throws IOException {
                            emitter.send(SseEmitter.event().name("message").data(chunk));
                        }

                        @Override
                        public void acceptMetadata(AiAssistantStreamMetadata metadata) throws IOException {
                            emitter.send(SseEmitter.event().name("metadata").data(metadata));
                        }
                    });
            emitter.send(SseEmitter.event().name("done").data(response));
            emitter.complete();
        } catch (BusinessException exception) {
            completeWithReadableError(emitter, exception.getMessage(), exception);
        } catch (IOException exception) {
            completeWithReadableError(emitter, "智能问答流已中断，请稍后重试。", exception);
        }
    }

    private void completeWithReadableError(SseEmitter emitter, String message, Exception cause) {
        try {
            emitter.send(SseEmitter.event().name("error").data(new StreamError(message)));
        } catch (IOException ignored) {
            // The client may already have disconnected; complete the request below.
        }
        emitter.completeWithError(cause);
    }

    private record StreamError(String message) {
    }
}
