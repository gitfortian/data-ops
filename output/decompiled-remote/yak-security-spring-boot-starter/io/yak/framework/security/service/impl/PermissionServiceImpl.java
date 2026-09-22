/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 *  org.springframework.stereotype.Service
 *  org.springframework.transaction.annotation.Transactional
 *  org.springframework.util.CollectionUtils
 */
package io.yak.framework.security.service.impl;

import io.yak.framework.security.common.dto.permission.PermissionDTO;
import io.yak.framework.security.common.entity.Permission;
import io.yak.framework.security.common.enums.ResultCode;
import io.yak.framework.security.common.vo.permission.PermissionTreeVO;
import io.yak.framework.security.dao.PermissionDao;
import io.yak.framework.security.exception.YakSecurityException;
import io.yak.framework.security.service.PermissionCache;
import io.yak.framework.security.service.PermissionService;
import io.yak.framework.security.service.RolePermissionService;
import io.yak.framework.security.util.CopyBeanUtil;
import io.yak.framework.security.util.MathUtil;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

@Service(value="yakSecurityPermissionServiceImpl")
public class PermissionServiceImpl
implements PermissionService {
    private static final Logger LOGGER = LoggerFactory.getLogger(PermissionServiceImpl.class);
    private static final Long ROOT_PERMISSION_ID = 0L;
    private static final int PERMISSION_ID_RANDOM_LENGTH = 5;
    private static final long PERMISSION_ID_FACTOR = 100000L;
    private static final int MAX_ID_RETRY_COUNT = 100;
    private final PermissionDao permissionDao;
    private final RolePermissionService rolePermissionService;
    private final PermissionCache permissionCache;

    public PermissionServiceImpl(PermissionDao permissionDao, RolePermissionService rolePermissionService, PermissionCache permissionCache) {
        this.permissionDao = permissionDao;
        this.rolePermissionService = rolePermissionService;
        this.permissionCache = permissionCache;
    }

    @Override
    public PermissionTreeVO buildPermissionTreeWithHas(List<Long> permissionIdList) {
        Set<Long> selectedPermissionIds = this.normalizeIds(permissionIdList).stream().collect(Collectors.toSet());
        return this.buildPermissionTree(selectedPermissionIds);
    }

    @Override
    public PermissionTreeVO buildPermissionTree() {
        return this.buildPermissionTree(Collections.emptySet());
    }

    @Override
    public PermissionTreeVO buildPermissionTreeByRoleId(Long roleId) {
        if (roleId == null) {
            return this.buildPermissionTree();
        }
        List<Long> permissionIdList = this.rolePermissionService.getPermissionIdListByRoleId(roleId);
        return this.buildPermissionTreeWithHas(permissionIdList);
    }

    @Override
    @Transactional(transactionManager="yakSecurityTransactionManager", rollbackFor={Exception.class})
    public void savePermission(List<PermissionDTO> permissionDTOList) {
        if (CollectionUtils.isEmpty(permissionDTOList)) {
            return;
        }
        List<Permission> permissionList = this.buildPermissionList(permissionDTOList);
        if (permissionList.isEmpty()) {
            return;
        }
        this.permissionDao.insertBatch(permissionList);
        this.permissionCache.invalidateAll();
        LOGGER.info("\u6279\u91cf\u5bfc\u5165\u6743\u9650\u6210\u529f\uff0c\u6743\u9650\u6570\u91cf={}", (Object)permissionList.size());
    }

    @Override
    @Transactional(transactionManager="yakSecurityTransactionManager", rollbackFor={Exception.class})
    public void deletePermissionById(Long permissionId) {
        if (permissionId == null) {
            throw new IllegalArgumentException("\u6743\u9650 ID \u4e0d\u80fd\u4e3a\u7a7a");
        }
        this.rolePermissionService.deleteRolePermissionByPermissionId(permissionId);
        this.permissionDao.deleteById(permissionId);
        this.permissionCache.invalidateAll();
    }

    private PermissionTreeVO buildPermissionTree(Set<Long> selectedPermissionIds) {
        List<Permission> permissionList = this.permissionDao.selectAllAndAscOrderByLevel();
        PermissionTreeVO root = PermissionTreeVO.builder().id(ROOT_PERMISSION_ID).leaf(Boolean.FALSE).has(Boolean.TRUE).childList(new ArrayList<PermissionTreeVO>()).build();
        if (CollectionUtils.isEmpty(permissionList)) {
            return root;
        }
        HashMap<Long, PermissionTreeVO> permissionTreeMap = new HashMap<Long, PermissionTreeVO>(permissionList.size() + 1);
        permissionTreeMap.put(ROOT_PERMISSION_ID, root);
        for (Permission permission : permissionList) {
            Long parentId;
            PermissionTreeVO parent;
            if (permission == null || permission.getId() == null) {
                LOGGER.error("\u6743\u9650\u6570\u636e\u5f02\u5e38\uff0c\u6743\u9650\u6216\u6743\u9650 ID \u4e3a\u7a7a");
                throw new YakSecurityException(ResultCode.PERMISSION_DATA_ERROR);
            }
            PermissionTreeVO permissionTreeVO = CopyBeanUtil.copy(permission, PermissionTreeVO.class);
            if (permissionTreeVO == null) {
                throw new IllegalStateException("\u6743\u9650\u6811\u5bf9\u8c61\u8f6c\u6362\u5931\u8d25");
            }
            if (!Boolean.TRUE.equals(permissionTreeVO.getLeaf())) {
                permissionTreeVO.setChildList(new ArrayList<PermissionTreeVO>());
            }
            if ((parent = (PermissionTreeVO)permissionTreeMap.get(parentId = permission.getParentId() == null ? ROOT_PERMISSION_ID : permission.getParentId())) == null) {
                LOGGER.error("\u6784\u5efa\u6743\u9650\u6811\u5931\u8d25\uff0c\u672a\u627e\u5230\u7236\u6743\u9650\uff0c\u6743\u9650ID={}\uff0c\u7236\u6743\u9650ID={}", (Object)permission.getId(), (Object)parentId);
                throw new YakSecurityException(ResultCode.PERMISSION_DATA_ERROR);
            }
            boolean selected = Boolean.TRUE.equals(parent.getHas()) && selectedPermissionIds.contains(permission.getId());
            permissionTreeVO.setHas(selected);
            if (parent.getChildList() == null) {
                parent.setChildList(new ArrayList<PermissionTreeVO>());
            }
            parent.setLeaf(Boolean.FALSE);
            parent.getChildList().add(permissionTreeVO);
            PermissionTreeVO previous = permissionTreeMap.put(permissionTreeVO.getId(), permissionTreeVO);
            if (previous == null) continue;
            LOGGER.error("\u6784\u5efa\u6743\u9650\u6811\u5931\u8d25\uff0c\u5b58\u5728\u91cd\u590d\u6743\u9650ID\uff0c\u6743\u9650ID={}", (Object)permissionTreeVO.getId());
            throw new YakSecurityException(ResultCode.PERMISSION_DATA_ERROR);
        }
        return root;
    }

    private List<Permission> buildPermissionList(List<PermissionDTO> permissionDTOList) {
        ArrayList<Permission> permissionList = new ArrayList<Permission>();
        ArrayDeque<PermissionQueueNode> queue = new ArrayDeque<PermissionQueueNode>();
        Set visitedDTOs = Collections.newSetFromMap(new IdentityHashMap());
        HashSet<Long> generatedPermissionIds = new HashSet<Long>();
        for (PermissionDTO permissionDTO : permissionDTOList) {
            if (permissionDTO == null) {
                throw new YakSecurityException(ResultCode.PERMISSION_DATA_ERROR);
            }
            queue.offer(new PermissionQueueNode(permissionDTO, ROOT_PERMISSION_ID, 1));
        }
        while (!queue.isEmpty()) {
            PermissionDTO permissionDTO;
            PermissionQueueNode queueNode = (PermissionQueueNode)queue.poll();
            permissionDTO = queueNode.getPermissionDTO();
            if (!visitedDTOs.add(permissionDTO)) {
                LOGGER.error("\u5bfc\u5165\u6743\u9650\u6570\u636e\u5b58\u5728\u5faa\u73af\u5f15\u7528");
                throw new YakSecurityException(ResultCode.PERMISSION_DATA_ERROR);
            }
            Permission permission = CopyBeanUtil.copy(permissionDTO, Permission.class);
            if (permission == null) {
                throw new IllegalStateException("\u6743\u9650\u5bf9\u8c61\u8f6c\u6362\u5931\u8d25");
            }
            long permissionId = this.generateUniquePermissionId(generatedPermissionIds);
            List<PermissionDTO> childPermissionList = permissionDTO.getChildPermissionDTOList();
            permission.setId(permissionId);
            permission.setParentId(queueNode.getParentId());
            permission.setLevel(queueNode.getLevel());
            permission.setLeaf(CollectionUtils.isEmpty(childPermissionList));
            permissionList.add(permission);
            if (CollectionUtils.isEmpty(childPermissionList)) continue;
            for (PermissionDTO childPermission : childPermissionList) {
                if (childPermission == null) {
                    throw new YakSecurityException(ResultCode.PERMISSION_DATA_ERROR);
                }
                queue.offer(new PermissionQueueNode(childPermission, permissionId, queueNode.getLevel() + 1));
            }
        }
        return permissionList;
    }

    private long generateUniquePermissionId(Set<Long> generatedPermissionIds) {
        for (int index = 0; index < 100; ++index) {
            long permissionId = this.getPermissionId();
            if (Objects.equals(ROOT_PERMISSION_ID, permissionId) || !generatedPermissionIds.add(permissionId)) continue;
            return permissionId;
        }
        throw new IllegalStateException("\u751f\u6210\u6743\u9650 ID \u5931\u8d25");
    }

    private long getPermissionId() {
        return System.currentTimeMillis() % 1000L * 100000L + MathUtil.getRandomNumber(5);
    }

    private List<Long> normalizeIds(List<Long> idList) {
        if (CollectionUtils.isEmpty(idList)) {
            return new ArrayList<Long>();
        }
        return idList.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
    }

    private static final class PermissionQueueNode {
        private final PermissionDTO permissionDTO;
        private final Long parentId;
        private final int level;

        private PermissionQueueNode(PermissionDTO permissionDTO, Long parentId, int level) {
            this.permissionDTO = permissionDTO;
            this.parentId = parentId;
            this.level = level;
        }

        private PermissionDTO getPermissionDTO() {
            return this.permissionDTO;
        }

        private Long getParentId() {
            return this.parentId;
        }

        private int getLevel() {
            return this.level;
        }
    }
}

