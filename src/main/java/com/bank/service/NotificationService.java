package com.bank.service;

import com.bank.event.TransactionEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class NotificationService {

    // Quản lý các kết nối SSE đang mở theo số ví: Map<WalletNumber, SseEmitter>
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(String walletNumber) {
        // Timeout 30 phút, sau đó client tự động reconnect
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);

        emitters.put(walletNumber, emitter);

        emitter.onCompletion(() -> {
            log.info("SSE kết thúc cho ví: {}", walletNumber);
            emitters.remove(walletNumber);
        });

        emitter.onTimeout(() -> {
            log.info("SSE timeout cho ví: {}", walletNumber);
            emitter.complete();
            emitters.remove(walletNumber);
        });

        emitter.onError(e -> {
            log.warn("SSE lỗi cho ví {}: {}", walletNumber, e.getMessage());
            emitters.remove(walletNumber);
        });

        // Gửi tin nhắn chào mừng khởi tạo kết nối
        try {
            emitter.send(SseEmitter.event()
                    .name("CONNECTED")
                    .data("Kết nối kênh thông báo biến động số dư thời gian thực thành công"));
        } catch (IOException e) {
            emitters.remove(walletNumber);
        }

        return emitter;
    }

    public void sendBalanceNotification(String walletNumber, TransactionEvent event, String message) {
        SseEmitter emitter = emitters.get(walletNumber);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event()
                        .name("BALANCE_UPDATE")
                        .data(Map.of(
                                "event", event,
                                "message", message,
                                "timestamp", System.currentTimeMillis()
                        )));
                log.info("🔔 [REALTIME TING TING] Đã đẩy thông báo số dư tới ví {}: {}", walletNumber, message);
            } catch (IOException e) {
                log.warn("Không thể gửi SSE tới ví {}: {}", walletNumber, e.getMessage());
                emitters.remove(walletNumber);
            }
        }
    }
}
