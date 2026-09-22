/*
 * Decompiled with CFR 0.152.
 */
package io.yak.framework.security.service;

import io.yak.framework.security.common.dto.message.MessageDTO;
import io.yak.framework.security.common.dto.message.MessagePageQueryDTO;
import io.yak.framework.security.common.vo.message.MessagePageVO;
import io.yak.framework.security.common.vo.message.MessageVO;
import java.util.List;

public interface MessageService {
    public void saveMessage(MessageDTO var1);

    public List<MessageVO> getMessageListByUsernameAndReadTag(String var1, Boolean var2);

    public void changeMessageStatus(List<Long> var1);

    public void changeMessageStatus(String var1, List<Long> var2);

    public void saveMessages(List<MessageDTO> var1);

    public MessagePageVO getMessagePage(String var1, MessagePageQueryDTO var2);

    public MessageVO getMessageDetail(String var1, Long var2);

    public void markMessageRead(String var1, Long var2);

    public void markMessagesRead(String var1, List<Long> var2);

    public int getUnreadMessageCount(String var1);
}

