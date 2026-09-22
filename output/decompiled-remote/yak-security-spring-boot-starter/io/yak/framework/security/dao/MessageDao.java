/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.baomidou.mybatisplus.core.metadata.IPage
 */
package io.yak.framework.security.dao;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.yak.framework.security.common.entity.Message;
import java.util.Date;
import java.util.List;

public interface MessageDao {
    public void insert(Message var1);

    public void update(Message var1);

    public void insertBatch(List<Message> var1);

    public List<Message> selectListByUserIdAndReadTag(Long var1, Boolean var2, List<Long> var3, boolean var4);

    public List<Message> selectListByMessageIdList(List<Long> var1);

    public List<Message> selectListByMessageIdListAndUserId(List<Long> var1, Long var2);

    public Message selectByMessageIdAndUserId(Long var1, Long var2);

    public IPage<Message> selectPageByUserId(Long var1, Boolean var2, String var3, List<Long> var4, boolean var5, Date var6, Date var7, int var8, int var9);

    public long countUnreadByUserId(Long var1, List<Long> var2, boolean var3);
}

