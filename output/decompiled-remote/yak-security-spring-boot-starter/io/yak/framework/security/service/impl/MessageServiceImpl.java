/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.baomidou.mybatisplus.core.metadata.IPage
 *  org.springframework.stereotype.Service
 *  org.springframework.transaction.annotation.Transactional
 *  org.springframework.util.CollectionUtils
 *  org.springframework.util.StringUtils
 */
package io.yak.framework.security.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.yak.framework.security.common.dto.message.MessageDTO;
import io.yak.framework.security.common.dto.message.MessagePageQueryDTO;
import io.yak.framework.security.common.entity.Message;
import io.yak.framework.security.common.entity.user.User;
import io.yak.framework.security.common.enums.ResultCode;
import io.yak.framework.security.common.vo.message.MessagePageVO;
import io.yak.framework.security.common.vo.message.MessageVO;
import io.yak.framework.security.context.AuthorizationSnapshot;
import io.yak.framework.security.dao.MessageDao;
import io.yak.framework.security.dao.UserDao;
import io.yak.framework.security.exception.YakSecurityException;
import io.yak.framework.security.service.MessageService;
import io.yak.framework.security.service.impl.AuthorizationSnapshotService;
import io.yak.framework.security.util.CopyBeanUtil;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

@Service(value="yakSecurityMessageServiceImpl")
public class MessageServiceImpl
implements MessageService {
    private static final String STATUS_READ = "READ";
    private static final String STATUS_UNREAD = "UNREAD";
    private static final String SCOPE_SYSTEM = "SYSTEM";
    private static final String SCOPE_PROJECT = "PROJECT";
    private static final String LEVEL_INFO = "INFO";
    private static final Set<String> MESSAGE_LEVELS = Set.of("INFO", "SUCCESS", "WARNING", "ERROR");
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 100;
    private final MessageDao messageDao;
    private final UserDao userDao;
    private final AuthorizationSnapshotService authorizationSnapshotService;

    public MessageServiceImpl(MessageDao messageDao, UserDao userDao, AuthorizationSnapshotService authorizationSnapshotService) {
        this.messageDao = messageDao;
        this.userDao = userDao;
        this.authorizationSnapshotService = authorizationSnapshotService;
    }

    @Override
    @Transactional(transactionManager="yakSecurityTransactionManager", rollbackFor={Exception.class})
    public void saveMessage(MessageDTO messageDTO) {
        if (messageDTO == null) {
            return;
        }
        Message message = this.convertToEntity(messageDTO);
        this.normalizeMessage(message);
        this.messageDao.insert(message);
    }

    @Override
    public List<MessageVO> getMessageListByUsernameAndReadTag(String username, Boolean readTag) {
        User user = this.requireUser(username);
        AuthorizationSnapshot authorization = this.authorization(user);
        ProjectVisibility visibility = this.resolveVisibility(authorization, null);
        List<Message> messages = this.messageDao.selectListByUserIdAndReadTag(user.getId(), readTag, visibility.projectIds(), visibility.restrictProjects());
        return this.convertToVOList(messages);
    }

    @Override
    @Transactional(transactionManager="yakSecurityTransactionManager", rollbackFor={Exception.class})
    public void changeMessageStatus(List<Long> messageIdList) {
        this.toggleMessages(this.messageDao.selectListByMessageIdList(this.normalizeIds(messageIdList)));
    }

    @Override
    @Transactional(transactionManager="yakSecurityTransactionManager", rollbackFor={Exception.class})
    public void changeMessageStatus(String username, List<Long> messageIdList) {
        User user = this.requireUser(username);
        AuthorizationSnapshot authorization = this.authorization(user);
        List<Message> messages = this.messageDao.selectListByMessageIdListAndUserId(this.normalizeIds(messageIdList), user.getId());
        this.toggleMessages(this.filterVisibleMessages(messages, authorization));
    }

    @Override
    @Transactional(transactionManager="yakSecurityTransactionManager", rollbackFor={Exception.class})
    public void saveMessages(List<MessageDTO> messageDTOList) {
        if (CollectionUtils.isEmpty(messageDTOList)) {
            return;
        }
        List<Message> messages = messageDTOList.stream().filter(Objects::nonNull).map(this::convertToEntity).peek(this::normalizeMessage).collect(Collectors.toList());
        if (!messages.isEmpty()) {
            this.messageDao.insertBatch(messages);
        }
    }

    @Override
    public MessagePageVO getMessagePage(String username, MessagePageQueryDTO queryDTO) {
        User user = this.requireUser(username);
        AuthorizationSnapshot authorization = this.authorization(user);
        MessagePageQueryDTO query = queryDTO == null ? new MessagePageQueryDTO() : queryDTO;
        this.validateTimeRange(query.getStartTime(), query.getEndTime());
        int pageNum = query.getPageNum() == null ? 1 : Math.max(1, query.getPageNum());
        int pageSize = query.getPageSize() == null ? 10 : Math.max(1, Math.min(100, query.getPageSize()));
        ProjectVisibility visibility = this.resolveVisibility(authorization, query.getProjectId());
        IPage<Message> page = this.messageDao.selectPageByUserId(user.getId(), this.parseReadTag(query.getStatus()), this.normalizeText(query.getType()), visibility.projectIds(), visibility.restrictProjects(), this.toDate(query.getStartTime()), this.toDate(query.getEndTime()), pageNum, pageSize);
        return new MessagePageVO(this.convertToVOList(page.getRecords()), page.getTotal());
    }

    @Override
    public MessageVO getMessageDetail(String username, Long messageId) {
        User user = this.requireUser(username);
        Message message = this.messageDao.selectByMessageIdAndUserId(messageId, user.getId());
        if (!this.canAccessMessage(this.authorization(user), message)) {
            return null;
        }
        return this.convertToVO(message);
    }

    @Override
    @Transactional(transactionManager="yakSecurityTransactionManager", rollbackFor={Exception.class})
    public void markMessageRead(String username, Long messageId) {
        if (messageId == null) {
            return;
        }
        User user = this.requireUser(username);
        Message message = this.messageDao.selectByMessageIdAndUserId(messageId, user.getId());
        if (this.canAccessMessage(this.authorization(user), message)) {
            this.markRead(message);
        }
    }

    @Override
    @Transactional(transactionManager="yakSecurityTransactionManager", rollbackFor={Exception.class})
    public void markMessagesRead(String username, List<Long> messageIds) {
        List<Long> ids = this.normalizeIds(messageIds);
        if (ids.isEmpty()) {
            return;
        }
        User user = this.requireUser(username);
        AuthorizationSnapshot authorization = this.authorization(user);
        this.filterVisibleMessages(this.messageDao.selectListByMessageIdListAndUserId(ids, user.getId()), authorization).forEach(this::markRead);
    }

    @Override
    public int getUnreadMessageCount(String username) {
        User user = this.requireUser(username);
        ProjectVisibility visibility = this.resolveVisibility(this.authorization(user), null);
        return Math.toIntExact(this.messageDao.countUnreadByUserId(user.getId(), visibility.projectIds(), visibility.restrictProjects()));
    }

    private void toggleMessages(List<Message> messages) {
        if (CollectionUtils.isEmpty(messages)) {
            return;
        }
        for (Message message : messages) {
            if (message == null) continue;
            boolean nextRead = !Boolean.TRUE.equals(message.getReadTag());
            message.setReadTag(nextRead);
            message.setReadTime(nextRead ? new Date() : null);
            this.messageDao.update(message);
        }
    }

    private void markRead(Message message) {
        if (message == null || Boolean.TRUE.equals(message.getReadTag())) {
            return;
        }
        message.setReadTag(true);
        message.setReadTime(new Date());
        this.messageDao.update(message);
    }

    private User requireUser(String username) {
        if (!StringUtils.hasText((String)username)) {
            throw new YakSecurityException(ResultCode.USER_NOT_EXISTS);
        }
        User user = this.userDao.selectByUsername(username.trim());
        if (user == null) {
            throw new YakSecurityException(ResultCode.USER_NOT_EXISTS);
        }
        return user;
    }

    private AuthorizationSnapshot authorization(User user) {
        return this.authorizationSnapshotService.get(user == null ? null : user.getId());
    }

    private ProjectVisibility resolveVisibility(AuthorizationSnapshot authorization, Long requestedProjectId) {
        if (requestedProjectId != null) {
            if (requestedProjectId <= 0L) {
                throw new YakSecurityException(ResultCode.PARAM_ERROR);
            }
            if (!authorization.canAccessProject(requestedProjectId)) {
                throw new YakSecurityException(ResultCode.NO_PERMISSION);
            }
            return new ProjectVisibility(List.of(requestedProjectId), true);
        }
        if (authorization.hasPermission("security:root")) {
            return new ProjectVisibility(List.of(), false);
        }
        return new ProjectVisibility(new ArrayList<Long>(authorization.getProjectIds()), true);
    }

    private boolean canAccessMessage(AuthorizationSnapshot authorization, Message message) {
        return message != null && (message.getProjectId() == null || authorization.canAccessProject(message.getProjectId()));
    }

    private List<Message> filterVisibleMessages(List<Message> messages, AuthorizationSnapshot authorization) {
        if (CollectionUtils.isEmpty(messages)) {
            return new ArrayList<Message>();
        }
        return messages.stream().filter(message -> this.canAccessMessage(authorization, (Message)message)).collect(Collectors.toList());
    }

    private Boolean parseReadTag(String status) {
        if (!StringUtils.hasText((String)status)) {
            return null;
        }
        if (STATUS_READ.equalsIgnoreCase(status)) {
            return true;
        }
        if (STATUS_UNREAD.equalsIgnoreCase(status)) {
            return false;
        }
        throw new YakSecurityException(ResultCode.PARAM_ERROR);
    }

    private String normalizeText(String value) {
        return StringUtils.hasText((String)value) ? value.trim().toUpperCase(Locale.ROOT) : null;
    }

    private Date toDate(Long epochMillis) {
        return epochMillis == null ? null : new Date(epochMillis);
    }

    private void validateTimeRange(Long startTime, Long endTime) {
        if (startTime != null && startTime < 0L || endTime != null && endTime < 0L || startTime != null && endTime != null && startTime > endTime) {
            throw new YakSecurityException(ResultCode.PARAM_ERROR);
        }
    }

    private Message convertToEntity(MessageDTO messageDTO) {
        Message message = CopyBeanUtil.copy(messageDTO, Message.class);
        if (message == null) {
            throw new IllegalStateException("\u6d88\u606f\u5bf9\u8c61\u8f6c\u6362\u5931\u8d25");
        }
        return message;
    }

    private void normalizeMessage(Message message) {
        String level;
        if (message.getProjectId() != null && message.getProjectId() <= 0L) {
            throw new YakSecurityException(ResultCode.PARAM_ERROR);
        }
        if (!StringUtils.hasText((String)message.getType())) {
            message.setType(message.getOplogId() == null ? SCOPE_SYSTEM : "SECURITY");
        } else {
            message.setType(message.getType().trim().toUpperCase(Locale.ROOT));
        }
        String string = level = StringUtils.hasText((String)message.getLevel()) ? message.getLevel().trim().toUpperCase(Locale.ROOT) : LEVEL_INFO;
        if (!MESSAGE_LEVELS.contains(level)) {
            throw new YakSecurityException(ResultCode.PARAM_ERROR);
        }
        message.setLevel(level);
        message.setScope(message.getProjectId() == null ? SCOPE_SYSTEM : SCOPE_PROJECT);
        if (message.getReadTag() == null) {
            message.setReadTag(false);
        }
        if (!StringUtils.hasText((String)message.getSummary()) && StringUtils.hasText((String)message.getContent())) {
            message.setSummary(this.compactSummary(message.getContent()));
        }
    }

    private String compactSummary(String content) {
        String normalized = content.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 160 ? normalized : normalized.substring(0, 157) + "...";
    }

    private List<MessageVO> convertToVOList(List<Message> messages) {
        if (CollectionUtils.isEmpty(messages)) {
            return new ArrayList<MessageVO>();
        }
        return messages.stream().filter(Objects::nonNull).map(this::convertToVO).filter(Objects::nonNull).collect(Collectors.toList());
    }

    private MessageVO convertToVO(Message message) {
        if (message == null) {
            return null;
        }
        MessageVO messageVO = CopyBeanUtil.copy(message, MessageVO.class);
        if (messageVO == null) {
            throw new IllegalStateException("\u6d88\u606f\u89c6\u56fe\u5bf9\u8c61\u8f6c\u6362\u5931\u8d25");
        }
        messageVO.setStatus(Boolean.TRUE.equals(message.getReadTag()) ? STATUS_READ : STATUS_UNREAD);
        messageVO.setOperationLogId(message.getOplogId());
        if (message.getCreateTime() != null) {
            messageVO.setCreateTime(message.getCreateTime().getTime());
        }
        if (message.getReadTime() != null) {
            messageVO.setReadTime(message.getReadTime().getTime());
        }
        if (!StringUtils.hasText((String)messageVO.getSummary()) && StringUtils.hasText((String)message.getContent())) {
            messageVO.setSummary(this.compactSummary(message.getContent()));
        }
        return messageVO;
    }

    private List<Long> normalizeIds(List<Long> idList) {
        if (CollectionUtils.isEmpty(idList)) {
            return new ArrayList<Long>();
        }
        return idList.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
    }

    private record ProjectVisibility(List<Long> projectIds, boolean restrictProjects) {
    }
}

