package com.ondemandmonitoring.media.service;

import com.ondemandmonitoring.media.event.CustomerMediaAvailableEvent;
import com.ondemandmonitoring.media.domain.MediaNotificationOutbox;
import com.ondemandmonitoring.media.repository.MediaNotificationOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@Slf4j
@RequiredArgsConstructor
public class CustomerMediaNotificationListener {

    private static final String EVENT_TYPE = "CUSTOMER_MEDIA_AVAILABLE";

    private final MediaNotificationOutboxRepository outboxRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMediaAvailable(CustomerMediaAvailableEvent event) {
        if (!outboxRepository.existsByMediaIdAndEventType(event.mediaId(), EVENT_TYPE)) {
            MediaNotificationOutbox outbox = new MediaNotificationOutbox();
            outbox.setMissionId(event.missionId());
            outbox.setMediaId(event.mediaId());
            outbox.setEventType(EVENT_TYPE);
            outbox.setStatus("PENDING");
            outboxRepository.save(outbox);
        }
        log.info("Customer media notification enqueued. missionId={}, mediaId={}",
                event.missionId(), event.mediaId());
    }
}
