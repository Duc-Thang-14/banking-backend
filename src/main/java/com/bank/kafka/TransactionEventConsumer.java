package com.bank.kafka;

import com.bank.enums.TransactionType;
import com.bank.event.TransactionEvent;
import com.bank.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionEventConsumer {

    private final NotificationService notificationService;

    @KafkaListener(topics = "transaction-events", groupId = "banking-group")
    public void consumeTransactionEvent(TransactionEvent event) {
        log.info("========== [KAFKA EVENT RECEIVED] ==========");
        log.info("TxRef: {}", event.getTransactionReference());
        log.info("Loại giao dịch: {}", event.getType());
        log.info("Từ ví: {} -> Đến ví: {}", event.getSourceWalletNumber(), event.getDestinationWalletNumber());
        log.info("Số tiền: {} {}", event.getAmount(), event.getCurrency());
        log.info("Trạng thái: {}", event.getStatus());

        sendNotification(event);
        recordAuditLog(event);
        log.info("============================================");
    }

    private void sendNotification(TransactionEvent event) {
        // 1. Gửi thông báo Real-time cho ví gửi (nếu không phải ví hệ thống)
        if (event.getSourceWalletNumber() != null && !event.getSourceWalletNumber().startsWith("SYS_")) {
            String msgSource;
            if (event.getType() == TransactionType.WITHDRAWAL) {
                msgSource = String.format("Rút tiền thành công: -%s %s về tài khoản ngân hàng",
                        event.getAmount(), event.getCurrency());
            } else {
                msgSource = String.format("Biến động số dư: -%s %s (Giao dịch: %s)",
                        event.getAmount(), event.getCurrency(), event.getTransactionReference());
            }
            notificationService.sendBalanceNotification(event.getSourceWalletNumber(), event, msgSource);
        }

        // 2. Gửi thông báo Real-time "Ting ting" cho ví nhận (nếu không phải ví hệ thống)
        if (event.getDestinationWalletNumber() != null && !event.getDestinationWalletNumber().startsWith("SYS_")) {
            String msgDest;
            if (event.getType() == TransactionType.TOP_UP) {
                msgDest = String.format("Ting ting! Bạn vừa nạp thành công +%s %s vào tài khoản",
                        event.getAmount(), event.getCurrency());
            } else {
                msgDest = String.format("Ting ting! Bạn vừa nhận được +%s %s từ ví %s",
                        event.getAmount(), event.getCurrency(), event.getSourceWalletNumber());
            }
            notificationService.sendBalanceNotification(event.getDestinationWalletNumber(), event, msgDest);
        }

        log.info("[NOTIFICATION] Đã gửi thông báo biến động số dư: Ví {} / Ví {}",
                event.getSourceWalletNumber(), event.getDestinationWalletNumber());
    }

    private void recordAuditLog(TransactionEvent event) {
        log.info("[AUDIT LOG] Đã lưu vết kiểm toán hệ thống cho giao dịch: {}", event.getTransactionReference());
    }
}
