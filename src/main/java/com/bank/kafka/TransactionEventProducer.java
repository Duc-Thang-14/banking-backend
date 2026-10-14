package com.bank.kafka;

import com.bank.event.TransactionEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionEventProducer {

    private static final String TOPIC_TRANSACTIONS = "transaction-events";
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishTransactionEvent(TransactionEvent event) {
        log.info("Publishing transaction event to Kafka [Topic: {}, TxRef: {}]", TOPIC_TRANSACTIONS, event.getTransactionReference());
        kafkaTemplate.send(TOPIC_TRANSACTIONS, event.getTransactionReference(), event);
    }
}
