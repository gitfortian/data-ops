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
import io.yak.framework.security.common.dto.user.UserProjectDTO;
import io.yak.framework.security.common.entity.UserProject;
import io.yak.framework.security.common.po.BasePO;
import io.yak.framework.security.common.po.UserProjectPO;
import io.yak.framework.security.dao.UserProjectDao;
import io.yak.framework.security.dao.mapper.UserProjectMapper;
import io.yak.framework.security.util.CopyBeanUtil;
import io.yak.framework.security.util.DatabaseNumberUtils;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Generated;
import org.springframework.stereotype.Repository;

@Repository
public class UserProjectDaoImpl
implements UserProjectDao {
    private final UserProjectMapper userProjectMapper;

    private static boolean isEmpty(List<?> values) {
        return values == null || values.isEmpty();
    }

    @Override
    public List<Long> selectUserIdListByProjectId(Long projectId, int userType) {
        if (projectId == null) {
            return Collections.emptyList();
        }
        return this.userProjectMapper.selectObjs((Wrapper)((LambdaQueryWrapper)Wrappers.lambdaQuery().select(new SFunction[]{UserProjectPO::getUserId}).eq(UserProjectPO::getProjectId, (Object)projectId)).eq(UserProjectPO::getUserType, (Object)userType)).stream().map(DatabaseNumberUtils::toLong).collect(Collectors.toList());
    }

    @Override
    public List<UserProject> selectByProjectIds(List<Long> projectIds) {
        if (UserProjectDaoImpl.isEmpty(projectIds)) {
            return Collections.emptyList();
        }
        List records = this.userProjectMapper.selectList((Wrapper)this.briefQuery().in(UserProjectPO::getProjectId, projectIds));
        return CopyBeanUtil.copyList(records, UserProject.class);
    }

    @Override
    public List<Long> selectProjectIdListByUserIdList(List<Long> userIdList) {
        if (UserProjectDaoImpl.isEmpty(userIdList)) {
            return Collections.emptyList();
        }
        return this.userProjectMapper.selectObjs((Wrapper)Wrappers.lambdaQuery().select(new SFunction[]{UserProjectPO::getProjectId}).in(UserProjectPO::getUserId, userIdList)).stream().map(DatabaseNumberUtils::toLong).distinct().collect(Collectors.toList());
    }

    @Override
    public List<UserProjectPO> selectProjectListByUserIdList(List<Long> userIdList) {
        if (UserProjectDaoImpl.isEmpty(userIdList)) {
            return Collections.emptyList();
        }
        return this.userProjectMapper.selectList((Wrapper)Wrappers.lambdaQuery().in(UserProjectPO::getUserId, userIdList));
    }

    @Override
    public void insertBatch(List<UserProject> userProjectList) {
        if (UserProjectDaoImpl.isEmpty(userProjectList)) {
            return;
        }
        for (UserProject userProject : userProjectList) {
            if (userProject == null) continue;
            UserProjectPO existing = this.selectExisting(userProject);
            if (existing == null) {
                this.insertUserProject(userProject);
                continue;
            }
            this.updateUserProject(existing.getId(), userProject);
        }
    }

    @Override
    public int deleteUserProject(List<UserProject> userProjectList) {
        if (UserProjectDaoImpl.isEmpty(userProjectList)) {
            return 0;
        }
        int deletedCount = 0;
        for (UserProject userProject : userProjectList) {
            if (userProject == null) continue;
            deletedCount += this.userProjectMapper.delete((Wrapper)((LambdaQueryWrapper)((LambdaQueryWrapper)Wrappers.lambdaQuery().eq(UserProjectPO::getProjectId, (Object)userProject.getProjectId())).eq(UserProjectPO::getUserId, (Object)userProject.getUserId())).eq(userProject.getUserType() != null, UserProjectPO::getUserType, (Object)userProject.getUserType()));
        }
        return deletedCount;
    }

    @Override
    public void deleteByProjectId(Long projectId) {
        if (projectId == null) {
            return;
        }
        this.userProjectMapper.delete((Wrapper)Wrappers.lambdaQuery().eq(UserProjectPO::getProjectId, (Object)projectId));
    }

    @Override
    public void deleteByUserId(Long userId) {
        if (userId == null) {
            return;
        }
        this.userProjectMapper.delete((Wrapper)Wrappers.lambdaQuery().eq(UserProjectPO::getUserId, (Object)userId));
    }

    @Override
    public void deleteByProjectIdAndUserType(Long projectId, int userType) {
        if (projectId == null) {
            return;
        }
        this.userProjectMapper.delete((Wrapper)((LambdaQueryWrapper)Wrappers.lambdaQuery().eq(UserProjectPO::getProjectId, (Object)projectId)).eq(UserProjectPO::getUserType, (Object)userType));
    }

    @Override
    public List<UserProject> select(UserProjectDTO queryDTO) {
        LambdaQueryWrapper wrapper = Wrappers.lambdaQuery().select(new SFunction[]{BasePO::getId, UserProjectPO::getProjectId, UserProjectPO::getUserId, UserProjectPO::getUserType});
        if (queryDTO != null) {
            ((LambdaQueryWrapper)((LambdaQueryWrapper)((LambdaQueryWrapper)((LambdaQueryWrapper)wrapper.eq(queryDTO.getId() != null, BasePO::getId, (Object)queryDTO.getId())).eq(queryDTO.getProjectId() != null, UserProjectPO::getProjectId, (Object)queryDTO.getProjectId())).eq(queryDTO.getUserId() != null, UserProjectPO::getUserId, (Object)queryDTO.getUserId())).eq(queryDTO.getUserType() != null, UserProjectPO::getUserType, (Object)queryDTO.getUserType())).eq(queryDTO.getIsDelete() != null, BasePO::getIsDelete, (Object)queryDTO.getIsDelete());
        }
        return CopyBeanUtil.copyList(this.userProjectMapper.selectList((Wrapper)wrapper), UserProject.class);
    }

    private UserProjectPO selectExisting(UserProject userProject) {
        return (UserProjectPO)this.userProjectMapper.selectOne((Wrapper)((LambdaQueryWrapper)((LambdaQueryWrapper)Wrappers.lambdaQuery().eq(UserProjectPO::getProjectId, (Object)userProject.getProjectId())).eq(UserProjectPO::getUserId, (Object)userProject.getUserId())).eq(userProject.getUserType() != null, UserProjectPO::getUserType, (Object)userProject.getUserType()));
    }

    private int insertUserProject(UserProject userProject) {
        return this.userProjectMapper.insert(CopyBeanUtil.copy(userProject, UserProjectPO.class));
    }

    private int updateUserProject(Long id, UserProject userProject) {
        UserProjectPO userProjectPO = CopyBeanUtil.copy(userProject, UserProjectPO.class);
        userProjectPO.setId(id);
        return this.userProjectMapper.updateById(userProjectPO);
    }

    private LambdaQueryWrapper<UserProjectPO> briefQuery() {
        return Wrappers.lambdaQuery().select(new SFunction[]{UserProjectPO::getUserId, UserProjectPO::getProjectId, UserProjectPO::getUserType});
    }

    @Generated
    public UserProjectDaoImpl(UserProjectMapper userProjectMapper) {
        this.userProjectMapper = userProjectMapper;
    }
}

