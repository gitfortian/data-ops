/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.baomidou.mybatisplus.core.conditions.Wrapper
 *  com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper
 *  com.baomidou.mybatisplus.core.metadata.IPage
 *  com.baomidou.mybatisplus.core.toolkit.Wrappers
 *  com.baomidou.mybatisplus.core.toolkit.support.SFunction
 *  com.baomidou.mybatisplus.extension.plugins.pagination.Page
 *  lombok.Generated
 *  org.springframework.stereotype.Repository
 *  org.springframework.util.StringUtils
 */
package io.yak.framework.security.dao.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.yak.framework.security.common.dto.user.UserBriefQueryDTO;
import io.yak.framework.security.common.dto.user.UserQueryDTO;
import io.yak.framework.security.common.entity.user.User;
import io.yak.framework.security.common.entity.user.UserBrief;
import io.yak.framework.security.common.po.BasePO;
import io.yak.framework.security.common.po.UserPO;
import io.yak.framework.security.dao.UserDao;
import io.yak.framework.security.dao.mapper.UserMapper;
import io.yak.framework.security.util.CopyBeanUtil;
import io.yak.framework.security.util.DatabaseNumberUtils;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Generated;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
public class UserDaoImpl
implements UserDao {
    private final UserMapper userMapper;

    private static boolean isEmptyScope(List<?> values) {
        return values != null && values.isEmpty();
    }

    @Override
    public int addUser(UserPO userPO) {
        return this.userMapper.insert(userPO);
    }

    @Override
    public int editUser(UserPO userPO) {
        return this.userMapper.updateById(userPO);
    }

    @Override
    public IPage<User> selectPageByUserIdList(UserQueryDTO queryDTO, List<Long> userIdList) {
        Page page = Page.of((long)queryDTO.getPage(), (long)queryDTO.getSize());
        if (UserDaoImpl.isEmptyScope(userIdList)) {
            return CopyBeanUtil.copyPage(page, User.class);
        }
        LambdaQueryWrapper wrapper = (LambdaQueryWrapper)((LambdaQueryWrapper)((LambdaQueryWrapper)((LambdaQueryWrapper)((LambdaQueryWrapper)Wrappers.lambdaQuery().eq(queryDTO.getId() != null, BasePO::getId, (Object)queryDTO.getId())).like(StringUtils.hasText((String)queryDTO.getUserName()), UserPO::getUserName, (Object)queryDTO.getUserName())).like(StringUtils.hasText((String)queryDTO.getRealName()), UserPO::getRealName, (Object)queryDTO.getRealName())).in(userIdList != null, BasePO::getId, userIdList)).orderByDesc(BasePO::getCreateTime);
        IPage result = this.userMapper.selectPage((IPage)page, (Wrapper)wrapper);
        return CopyBeanUtil.copyPage(result, User.class);
    }

    @Override
    public IPage<UserBrief> selectBriefPageByDeptIdList(UserBriefQueryDTO queryDTO, List<Long> deptIdList) {
        Page page = Page.of((long)queryDTO.getPage(), (long)queryDTO.getSize());
        if (UserDaoImpl.isEmptyScope(deptIdList)) {
            return CopyBeanUtil.copyPage(page, UserBrief.class);
        }
        LambdaQueryWrapper wrapper = (LambdaQueryWrapper)((LambdaQueryWrapper)((LambdaQueryWrapper)((LambdaQueryWrapper)this.briefQuery().like(StringUtils.hasText((String)queryDTO.getUserName()), UserPO::getUserName, (Object)queryDTO.getUserName())).like(StringUtils.hasText((String)queryDTO.getRealName()), UserPO::getRealName, (Object)queryDTO.getRealName())).in(deptIdList != null, UserPO::getDeptId, deptIdList)).orderByDesc(BasePO::getCreateTime);
        IPage result = this.userMapper.selectPage((IPage)page, (Wrapper)wrapper);
        return CopyBeanUtil.copyPage(result, UserBrief.class);
    }

    @Override
    public User selectByUserId(Long userId) {
        if (userId == null) {
            return null;
        }
        return CopyBeanUtil.copy(this.userMapper.selectById(userId), User.class);
    }

    @Override
    public User selectByUserMail(String email) {
        if (!StringUtils.hasText((String)email)) {
            return null;
        }
        UserPO userPO = (UserPO)this.userMapper.selectOne((Wrapper)Wrappers.lambdaQuery().eq(UserPO::getEmail, (Object)email));
        return CopyBeanUtil.copy(userPO, User.class);
    }

    @Override
    public User selectByUserPhone(String phone) {
        if (!StringUtils.hasText((String)phone)) {
            return null;
        }
        UserPO userPO = (UserPO)this.userMapper.selectOne((Wrapper)Wrappers.lambdaQuery().eq(UserPO::getPhone, (Object)phone));
        return CopyBeanUtil.copy(userPO, User.class);
    }

    @Override
    public boolean deleteByUserId(Long userId) {
        return userId != null && this.userMapper.deleteById(userId) > 0;
    }

    @Override
    public List<UserBrief> selectBriefListByUserIdList(List<Long> userIdList) {
        if (userIdList == null || userIdList.isEmpty()) {
            return Collections.emptyList();
        }
        List users = this.userMapper.selectList((Wrapper)this.briefQuery().in(BasePO::getId, userIdList));
        return CopyBeanUtil.copyList(users, UserBrief.class);
    }

    @Override
    public List<UserBrief> selectBriefListByNameAndDescOrderByCreateTime(String name) {
        LambdaQueryWrapper wrapper = (LambdaQueryWrapper)((LambdaQueryWrapper)this.briefQuery().and(StringUtils.hasText((String)name), nested -> ((LambdaQueryWrapper)((LambdaQueryWrapper)nested.like(UserPO::getUserName, (Object)name)).or()).like(UserPO::getRealName, (Object)name))).orderByDesc(BasePO::getCreateTime);
        return CopyBeanUtil.copyList(this.userMapper.selectList((Wrapper)wrapper), UserBrief.class);
    }

    @Override
    public List<UserBrief> selectBriefListByDeptIdList(List<Long> deptIdList) {
        if (UserDaoImpl.isEmptyScope(deptIdList)) {
            return Collections.emptyList();
        }
        List users = this.userMapper.selectList((Wrapper)this.briefQuery().in(deptIdList != null, UserPO::getDeptId, deptIdList));
        return CopyBeanUtil.copyList(users, UserBrief.class);
    }

    @Override
    public List<UserBrief> selectBriefListOrderByCreateTime(boolean ascending) {
        List users = this.userMapper.selectList((Wrapper)this.briefQuery().orderBy(true, ascending, BasePO::getCreateTime));
        return CopyBeanUtil.copyList(users, UserBrief.class);
    }

    @Override
    public List<UserBrief> selectAllBriefList() {
        List users = this.userMapper.selectList((Wrapper)this.briefQuery().orderByAsc(BasePO::getId));
        return CopyBeanUtil.copyList(users, UserBrief.class);
    }

    @Override
    public List<Long> selectUserIdListByUsernameOrRealName(String name) {
        if (!StringUtils.hasText((String)name)) {
            return Collections.emptyList();
        }
        return this.userMapper.selectObjs((Wrapper)Wrappers.lambdaQuery().select(new SFunction[]{BasePO::getId}).and(nested -> ((LambdaQueryWrapper)((LambdaQueryWrapper)nested.like(UserPO::getUserName, (Object)name)).or()).like(UserPO::getRealName, (Object)name))).stream().map(DatabaseNumberUtils::toLong).collect(Collectors.toList());
    }

    @Override
    public User selectByUsername(String username) {
        if (!StringUtils.hasText((String)username)) {
            return null;
        }
        UserPO userPO = (UserPO)this.userMapper.selectOne((Wrapper)Wrappers.lambdaQuery().eq(UserPO::getUserName, (Object)username));
        return CopyBeanUtil.copy(userPO, User.class);
    }

    private LambdaQueryWrapper<UserPO> briefQuery() {
        return Wrappers.lambdaQuery().select(new SFunction[]{BasePO::getId, UserPO::getUserName, UserPO::getRealName, UserPO::getDeptId});
    }

    @Generated
    public UserDaoImpl(UserMapper userMapper) {
        this.userMapper = userMapper;
    }
}

