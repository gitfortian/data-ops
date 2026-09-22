/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.baomidou.mybatisplus.core.conditions.Wrapper
 *  com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper
 *  com.baomidou.mybatisplus.core.metadata.IPage
 *  com.baomidou.mybatisplus.core.toolkit.Wrappers
 *  com.baomidou.mybatisplus.extension.plugins.pagination.Page
 *  lombok.Generated
 *  org.springframework.stereotype.Repository
 *  org.springframework.util.CollectionUtils
 *  org.springframework.util.StringUtils
 */
package io.yak.framework.security.dao.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.yak.framework.security.common.entity.Message;
import io.yak.framework.security.common.po.BasePO;
import io.yak.framework.security.common.po.MessagePO;
import io.yak.framework.security.dao.MessageDao;
import io.yak.framework.security.dao.mapper.MessageMapper;
import io.yak.framework.security.util.CopyBeanUtil;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import lombok.Generated;
import org.springframework.stereotype.Repository;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

@Repository
public class MessageDaoImpl
implements MessageDao {
    private final MessageMapper messageMapper;

    @Override
    public void insert(Message message) {
        MessagePO messagePO = CopyBeanUtil.copy(message, MessagePO.class);
        this.messageMapper.insert(messagePO);
        message.setId(messagePO.getId());
    }

    @Override
    public void update(Message message) {
        this.messageMapper.updateById(CopyBeanUtil.copy(message, MessagePO.class));
    }

    @Override
    public void insertBatch(List<Message> messageList) {
        if (messageList == null || messageList.isEmpty()) {
            return;
        }
        CopyBeanUtil.copyList(messageList, MessagePO.class).forEach(arg_0 -> ((MessageMapper)this.messageMapper).insert(arg_0));
    }

    @Override
    public List<Message> selectListByUserIdAndReadTag(Long userId, Boolean readTag, List<Long> visibleProjectIds, boolean restrictProjects) {
        LambdaQueryWrapper wrapper = (LambdaQueryWrapper)((LambdaQueryWrapper)Wrappers.lambdaQuery().eq(userId != null, MessagePO::getUserId, (Object)userId)).eq(readTag != null, MessagePO::getReadTag, (Object)readTag);
        this.applyProjectVisibility((LambdaQueryWrapper<MessagePO>)wrapper, visibleProjectIds, restrictProjects);
        ((LambdaQueryWrapper)wrapper.orderByDesc(BasePO::getCreateTime)).orderByDesc(BasePO::getId);
        return CopyBeanUtil.copyList(this.messageMapper.selectList((Wrapper)wrapper), Message.class);
    }

    @Override
    public List<Message> selectListByMessageIdList(List<Long> messageIdList) {
        if (messageIdList == null || messageIdList.isEmpty()) {
            return Collections.emptyList();
        }
        List messagePOList = this.messageMapper.selectList((Wrapper)Wrappers.lambdaQuery().in(BasePO::getId, messageIdList));
        return CopyBeanUtil.copyList(messagePOList, Message.class);
    }

    @Override
    public List<Message> selectListByMessageIdListAndUserId(List<Long> messageIdList, Long userId) {
        if (messageIdList == null || messageIdList.isEmpty() || userId == null) {
            return Collections.emptyList();
        }
        List messagePOList = this.messageMapper.selectList((Wrapper)((LambdaQueryWrapper)Wrappers.lambdaQuery().eq(MessagePO::getUserId, (Object)userId)).in(BasePO::getId, messageIdList));
        return CopyBeanUtil.copyList(messagePOList, Message.class);
    }

    @Override
    public Message selectByMessageIdAndUserId(Long messageId, Long userId) {
        if (messageId == null || userId == null) {
            return null;
        }
        MessagePO messagePO = (MessagePO)this.messageMapper.selectOne((Wrapper)((LambdaQueryWrapper)Wrappers.lambdaQuery().eq(BasePO::getId, (Object)messageId)).eq(MessagePO::getUserId, (Object)userId));
        return CopyBeanUtil.copy(messagePO, Message.class);
    }

    @Override
    public IPage<Message> selectPageByUserId(Long userId, Boolean readTag, String type, List<Long> visibleProjectIds, boolean restrictProjects, Date startTime, Date endTime, int pageNum, int pageSize) {
        Page page = Page.of((long)pageNum, (long)pageSize);
        LambdaQueryWrapper wrapper = (LambdaQueryWrapper)((LambdaQueryWrapper)((LambdaQueryWrapper)((LambdaQueryWrapper)((LambdaQueryWrapper)Wrappers.lambdaQuery().eq(MessagePO::getUserId, (Object)userId)).eq(readTag != null, MessagePO::getReadTag, (Object)readTag)).eq(StringUtils.hasText((String)type), MessagePO::getType, (Object)type)).ge(startTime != null, BasePO::getCreateTime, (Object)startTime)).le(endTime != null, BasePO::getCreateTime, (Object)endTime);
        this.applyProjectVisibility((LambdaQueryWrapper<MessagePO>)wrapper, visibleProjectIds, restrictProjects);
        ((LambdaQueryWrapper)wrapper.orderByDesc(BasePO::getCreateTime)).orderByDesc(BasePO::getId);
        IPage result = this.messageMapper.selectPage((IPage)page, (Wrapper)wrapper);
        return CopyBeanUtil.copyPage(result, Message.class);
    }

    @Override
    public long countUnreadByUserId(Long userId, List<Long> visibleProjectIds, boolean restrictProjects) {
        if (userId == null) {
            return 0L;
        }
        LambdaQueryWrapper wrapper = (LambdaQueryWrapper)((LambdaQueryWrapper)Wrappers.lambdaQuery().eq(MessagePO::getUserId, (Object)userId)).eq(MessagePO::getReadTag, (Object)false);
        this.applyProjectVisibility((LambdaQueryWrapper<MessagePO>)wrapper, visibleProjectIds, restrictProjects);
        return this.messageMapper.selectCount((Wrapper)wrapper);
    }

    private void applyProjectVisibility(LambdaQueryWrapper<MessagePO> wrapper, List<Long> visibleProjectIds, boolean restrictProjects) {
        if (!restrictProjects) {
            return;
        }
        wrapper.and(nested -> {
            nested.isNull(MessagePO::getProjectId);
            if (!CollectionUtils.isEmpty((Collection)visibleProjectIds)) {
                ((LambdaQueryWrapper)nested.or()).in(MessagePO::getProjectId, (Collection)visibleProjectIds);
            }
        });
    }

    @Generated
    public MessageDaoImpl(MessageMapper messageMapper) {
        this.messageMapper = messageMapper;
    }
}

