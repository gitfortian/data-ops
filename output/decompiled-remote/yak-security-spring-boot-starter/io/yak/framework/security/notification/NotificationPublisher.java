/*
 * Decompiled with CFR 0.152.
 */
package io.yak.framework.security.notification;

import io.yak.framework.security.common.dto.message.MessageDTO;
import java.util.List;

public interface NotificationPublisher {
    public void publish(MessageDTO var1);

    public void publishAll(List<MessageDTO> var1);
}

