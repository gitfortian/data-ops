/*
 * Decompiled with CFR 0.152.
 */
package io.yak.framework.security.notification;

import io.yak.framework.security.common.dto.message.MessageDTO;
import io.yak.framework.security.notification.NotificationPublisher;
import io.yak.framework.security.service.MessageService;
import java.util.List;

public class DefaultNotificationPublisher
implements NotificationPublisher {
    private final MessageService messageService;

    public DefaultNotificationPublisher(MessageService messageService) {
        this.messageService = messageService;
    }

    @Override
    public void publish(MessageDTO notification) {
        this.messageService.saveMessage(notification);
    }

    @Override
    public void publishAll(List<MessageDTO> notifications) {
        this.messageService.saveMessages(notifications);
    }
}

