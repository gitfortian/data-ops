/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.baomidou.mybatisplus.core.conditions.Wrapper
 *  com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper
 *  com.baomidou.mybatisplus.core.toolkit.Wrappers
 *  com.baomidou.mybatisplus.core.toolkit.support.SFunction
 *  lombok.Generated
 *  org.springframework.stereotype.Repository
 */
package io.yak.framework.security.dao.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import io.yak.framework.security.common.dto.resource.ControlLevelQueryDTO;
import io.yak.framework.security.common.dto.resource.UserResourceQueryDTO;
import io.yak.framework.security.common.entity.UserResource;
import io.yak.framework.security.common.enums.resource.ControlLevelCode;
import io.yak.framework.security.common.po.UserResourcePO;
import io.yak.framework.security.dao.UserResourceDao;
import io.yak.framework.security.dao.mapper.UserResourceMapper;
import io.yak.framework.security.util.CopyBeanUtil;
import io.yak.framework.security.util.DatabaseNumberUtils;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Generated;
import org.springframework.stereotype.Repository;

@Repository
public class UserResourceDaoImpl
implements UserResourceDao {
    private final UserResourceMapper userResourceMapper;

    private static int toInt(Long value) {
        return Math.toIntExact(value == null ? 0L : value);
    }

    private static boolean isEmpty(List<?> values) {
        return values == null || values.isEmpty();
    }

    @Override
    public int selectCountByUserId(Long userId, UserResourceQueryDTO queryDTO) {
        return UserResourceDaoImpl.toInt(this.userResourceMapper.selectCount((Wrapper)this.queryCriteria(userId, queryDTO)));
    }

    @Override
    public void deleteByUserId(Long userId, UserResourceQueryDTO queryDTO) {
        if (userId == null) {
            return;
        }
        this.userResourceMapper.delete((Wrapper)this.queryCriteria(userId, queryDTO));
    }

    @Override
    public void deleteByControlLevel(ControlLevelCode controlLevel) {
        if (controlLevel == null) {
            return;
        }
        this.userResourceMapper.delete((Wrapper)Wrappers.lambdaQuery().eq(UserResourcePO::getControlLevel, (Object)controlLevel.getType()));
    }

    @Override
    public void insert(UserResource userResource) {
        UserResourcePO userResourcePO = CopyBeanUtil.copy(userResource, UserResourcePO.class);
        this.userResourceMapper.insert(userResourcePO);
    }

    @Override
    public void insertBatch(List<UserResource> userResourceList) {
        if (UserResourceDaoImpl.isEmpty(userResourceList)) {
            return;
        }
        CopyBeanUtil.copyList(userResourceList, UserResourcePO.class).forEach(arg_0 -> ((UserResourceMapper)this.userResourceMapper).insert(arg_0));
    }

    @Override
    public void deleteByUserIdList(List<Long> userIdList, UserResourceQueryDTO queryDTO) {
        if (UserResourceDaoImpl.isEmpty(userIdList)) {
            return;
        }
        this.userResourceMapper.delete((Wrapper)this.queryCriteria(null, queryDTO).in(UserResourcePO::getUserId, userIdList));
    }

    @Override
    public void deleteByProjectIdList(List<Long> projectIdList, UserResourceQueryDTO queryDTO) {
        if (UserResourceDaoImpl.isEmpty(projectIdList)) {
            return;
        }
        this.userResourceMapper.delete((Wrapper)this.queryCriteria(null, queryDTO).in(UserResourcePO::getProjectId, projectIdList));
    }

    @Override
    public void deleteByResourceTypeIdList(List<Long> resourceTypeIdList, UserResourceQueryDTO queryDTO) {
        if (UserResourceDaoImpl.isEmpty(resourceTypeIdList)) {
            return;
        }
        this.userResourceMapper.delete((Wrapper)this.queryCriteria(null, queryDTO).in(UserResourcePO::getResourceTypeId, resourceTypeIdList));
    }

    @Override
    public void deleteByResourceIdList(List<Long> resourceIdList, UserResourceQueryDTO queryDTO) {
        if (UserResourceDaoImpl.isEmpty(resourceIdList)) {
            return;
        }
        this.userResourceMapper.delete((Wrapper)this.queryCriteria(null, queryDTO).in(UserResourcePO::getResourceId, resourceIdList));
    }

    @Override
    public int selectCountByUserIdAndControlLevel(Long userId, ControlLevelCode controlLevel) {
        if (controlLevel == null) {
            return 0;
        }
        return UserResourceDaoImpl.toInt(this.userResourceMapper.selectCount((Wrapper)((LambdaQueryWrapper)Wrappers.lambdaQuery().eq(userId != null, UserResourcePO::getUserId, (Object)userId)).eq(UserResourcePO::getControlLevel, (Object)controlLevel.getType())));
    }

    @Override
    public int selectCount(UserResourceQueryDTO queryDTO) {
        return UserResourceDaoImpl.toInt(this.userResourceMapper.selectCount((Wrapper)this.queryCriteria(null, queryDTO)));
    }

    @Override
    public List<Long> selectResourceIdListByUserId(Long userId, UserResourceQueryDTO queryDTO) {
        if (userId == null) {
            return Collections.emptyList();
        }
        return this.userResourceMapper.selectObjs((Wrapper)this.queryCriteria(userId, queryDTO).select(new SFunction[]{UserResourcePO::getResourceId})).stream().map(DatabaseNumberUtils::toLong).distinct().collect(Collectors.toList());
    }

    @Override
    public void deleteWithoutUserIdList(UserResourceQueryDTO queryDTO, List<Long> excludeUserIdList) {
        LambdaQueryWrapper<UserResourcePO> wrapper = this.queryCriteria(null, queryDTO);
        if (!UserResourceDaoImpl.isEmpty(excludeUserIdList)) {
            wrapper.notIn(UserResourcePO::getUserId, excludeUserIdList);
        }
        this.userResourceMapper.delete((Wrapper)wrapper);
    }

    @Override
    public void deleteByUserIdWithoutProjectIdList(Long userId, UserResourceQueryDTO queryDTO, List<Long> excludeIdList) {
        if (userId == null) {
            return;
        }
        LambdaQueryWrapper<UserResourcePO> wrapper = this.queryCriteria(userId, queryDTO);
        if (!UserResourceDaoImpl.isEmpty(excludeIdList)) {
            wrapper.notIn(UserResourcePO::getProjectId, excludeIdList);
        }
        this.userResourceMapper.delete((Wrapper)wrapper);
    }

    @Override
    public void deleteByUserIdWithoutResourceTypeIdList(Long userId, UserResourceQueryDTO queryDTO, List<Long> excludeIdList) {
        if (userId == null) {
            return;
        }
        LambdaQueryWrapper<UserResourcePO> wrapper = this.queryCriteria(userId, queryDTO);
        if (!UserResourceDaoImpl.isEmpty(excludeIdList)) {
            wrapper.notIn(UserResourcePO::getResourceTypeId, excludeIdList);
        }
        this.userResourceMapper.delete((Wrapper)wrapper);
    }

    @Override
    public int selectCountGroupByUserId(UserResourceQueryDTO queryDTO) {
        return this.selectUserIdListGroupByUserId(queryDTO).size();
    }

    @Override
    public List<Long> selectUserIdListGroupByUserId(UserResourceQueryDTO queryDTO) {
        return this.userResourceMapper.selectObjs((Wrapper)this.queryCriteria(null, queryDTO).select(new SFunction[]{UserResourcePO::getUserId}).groupBy(UserResourcePO::getUserId)).stream().map(DatabaseNumberUtils::toLong).collect(Collectors.toList());
    }

    @Override
    public Integer selectControlLevel(ControlLevelQueryDTO queryDTO) {
        if (queryDTO == null) {
            return null;
        }
        UserResourcePO result = (UserResourcePO)this.userResourceMapper.selectOne((Wrapper)((LambdaQueryWrapper)((LambdaQueryWrapper)((LambdaQueryWrapper)((LambdaQueryWrapper)((LambdaQueryWrapper)Wrappers.lambdaQuery().select(new SFunction[]{UserResourcePO::getControlLevel}).eq(queryDTO.getUserId() != null, UserResourcePO::getUserId, (Object)queryDTO.getUserId())).eq(queryDTO.getProjectId() != null, UserResourcePO::getProjectId, (Object)queryDTO.getProjectId())).eq(queryDTO.getResourceTypeId() != null, UserResourcePO::getResourceTypeId, (Object)queryDTO.getResourceTypeId())).eq(queryDTO.getResourceId() != null, UserResourcePO::getResourceId, (Object)queryDTO.getResourceId())).orderByDesc(UserResourcePO::getControlLevel)).last("LIMIT 1"));
        return result == null ? null : result.getControlLevel();
    }

    private LambdaQueryWrapper<UserResourcePO> queryCriteria(Long userId, UserResourceQueryDTO queryDTO) {
        LambdaQueryWrapper wrapper = (LambdaQueryWrapper)Wrappers.lambdaQuery().eq(userId != null, UserResourcePO::getUserId, (Object)userId);
        if (queryDTO == null) {
            return wrapper;
        }
        return (LambdaQueryWrapper)((LambdaQueryWrapper)((LambdaQueryWrapper)((LambdaQueryWrapper)wrapper.eq(queryDTO.getControlLevel() != null, UserResourcePO::getControlLevel, (Object)queryDTO.getControlLevel())).eq(queryDTO.getProjectId() != null, UserResourcePO::getProjectId, (Object)queryDTO.getProjectId())).eq(queryDTO.getResourceTypeId() != null, UserResourcePO::getResourceTypeId, (Object)queryDTO.getResourceTypeId())).eq(queryDTO.getResourceId() != null, UserResourcePO::getResourceId, (Object)queryDTO.getResourceId());
    }

    @Generated
    public UserResourceDaoImpl(UserResourceMapper userResourceMapper) {
        this.userResourceMapper = userResourceMapper;
    }
}

