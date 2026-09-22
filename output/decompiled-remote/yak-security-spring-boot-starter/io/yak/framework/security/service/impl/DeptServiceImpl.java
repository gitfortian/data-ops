/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 *  org.springframework.stereotype.Service
 *  org.springframework.transaction.annotation.Transactional
 *  org.springframework.util.CollectionUtils
 *  org.springframework.util.StringUtils
 */
package io.yak.framework.security.service.impl;

import io.yak.framework.security.common.dto.dept.DeptDTO;
import io.yak.framework.security.common.dto.dept.DeptSaveDTO;
import io.yak.framework.security.common.entity.dept.Dept;
import io.yak.framework.security.common.entity.dept.DeptBrief;
import io.yak.framework.security.common.entity.user.UserBrief;
import io.yak.framework.security.common.enums.ResultCode;
import io.yak.framework.security.common.vo.dept.DeptBriefVO;
import io.yak.framework.security.common.vo.dept.DeptDeleteCheckVO;
import io.yak.framework.security.common.vo.dept.DeptTreeVO;
import io.yak.framework.security.common.vo.dept.DeptVO;
import io.yak.framework.security.dao.DeptDao;
import io.yak.framework.security.dao.UserDao;
import io.yak.framework.security.exception.YakSecurityException;
import io.yak.framework.security.service.DeptService;
import io.yak.framework.security.util.CopyBeanUtil;
import io.yak.framework.security.util.MathUtil;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

@Service(value="yakSecurityDeptServiceImpl")
public class DeptServiceImpl
implements DeptService {
    private static final Logger LOGGER = LoggerFactory.getLogger(DeptServiceImpl.class);
    private static final Long ROOT_DEPT_ID = 0L;
    private static final int DEPT_ID_RANDOM_LENGTH = 5;
    private static final long DEPT_ID_FACTOR = 100000L;
    private static final int MAX_ID_RETRY_COUNT = 100;
    private static final int MAX_DEPT_NAME_LENGTH = 64;
    private static final int MAX_DESCRIPTION_LENGTH = 500;
    private final DeptDao deptDao;
    private final UserDao userDao;

    public DeptServiceImpl(DeptDao deptDao, UserDao userDao) {
        this.deptDao = deptDao;
        this.userDao = userDao;
    }

    @Override
    public DeptTreeVO buildDeptTree() {
        List<Dept> deptList = this.deptDao.selectAllAndAscOrderByLevel();
        DeptTreeVO root = DeptTreeVO.builder().id(ROOT_DEPT_ID).leaf(Boolean.FALSE).childList(new ArrayList<DeptTreeVO>()).build();
        HashMap<Long, DeptTreeVO> deptTreeMap = new HashMap<Long, DeptTreeVO>();
        deptTreeMap.put(ROOT_DEPT_ID, root);
        if (CollectionUtils.isEmpty(deptList)) {
            return root;
        }
        for (Dept dept : deptList) {
            Long parentId;
            DeptTreeVO parent;
            if (dept == null || dept.getId() == null) {
                throw new YakSecurityException(ResultCode.DEPT_DATA_ERROR);
            }
            DeptTreeVO deptTreeVO = CopyBeanUtil.copy(dept, DeptTreeVO.class);
            if (deptTreeVO == null) {
                throw new IllegalStateException("\u90e8\u95e8\u6811\u5bf9\u8c61\u8f6c\u6362\u5931\u8d25");
            }
            if (!Boolean.TRUE.equals(deptTreeVO.getLeaf())) {
                deptTreeVO.setChildList(new ArrayList<DeptTreeVO>());
            }
            if ((parent = (DeptTreeVO)deptTreeMap.get(parentId = dept.getParentId() == null ? ROOT_DEPT_ID : dept.getParentId())) == null) {
                LOGGER.error("\u6784\u5efa\u90e8\u95e8\u6811\u5931\u8d25\uff0c\u672a\u627e\u5230\u7236\u90e8\u95e8\uff0c\u90e8\u95e8ID={}\uff0c\u7236\u90e8\u95e8ID={}", (Object)dept.getId(), (Object)parentId);
                throw new YakSecurityException(ResultCode.DEPT_DATA_ERROR);
            }
            if (parent.getChildList() == null) {
                parent.setChildList(new ArrayList<DeptTreeVO>());
            }
            parent.setLeaf(Boolean.FALSE);
            parent.getChildList().add(deptTreeVO);
            DeptTreeVO previous = deptTreeMap.put(deptTreeVO.getId(), deptTreeVO);
            if (previous == null) continue;
            LOGGER.error("\u6784\u5efa\u90e8\u95e8\u6811\u5931\u8d25\uff0c\u5b58\u5728\u91cd\u590d\u90e8\u95e8ID\uff0c\u90e8\u95e8ID={}", (Object)deptTreeVO.getId());
            throw new YakSecurityException(ResultCode.DEPT_DATA_ERROR);
        }
        return root;
    }

    @Override
    public DeptVO getDeptDetail(Long deptId) {
        Dept dept = this.requireDept(deptId);
        List<Dept> childDeptList = this.getDirectChildDeptList(deptId);
        List<UserBrief> userList = this.getDirectUserList(deptId);
        DeptVO deptVO = CopyBeanUtil.copy(dept, DeptVO.class);
        if (deptVO == null) {
            throw new IllegalStateException("\u90e8\u95e8\u8be6\u60c5\u5bf9\u8c61\u8f6c\u6362\u5931\u8d25");
        }
        deptVO.setLeaf(childDeptList.isEmpty());
        deptVO.setChildDeptCount(childDeptList.size());
        deptVO.setUserCount(userList.size());
        return deptVO;
    }

    @Override
    @Transactional(transactionManager="yakSecurityTransactionManager", rollbackFor={Exception.class})
    public void createDept(DeptSaveDTO deptSaveDTO) {
        String deptName = this.normalizeDeptName(deptSaveDTO);
        String description = this.normalizeDescription(deptSaveDTO.getDescription());
        Long parentId = this.normalizeParentId(deptSaveDTO.getParentId());
        int level = this.resolveDeptLevel(parentId);
        this.validateSiblingDeptName(deptName, parentId, null);
        Dept dept = new Dept();
        dept.setDeptName(deptName);
        dept.setDescription(description);
        dept.setParentId(parentId);
        dept.setLevel(level);
        dept.setLeaf(Boolean.TRUE);
        this.deptDao.insert(dept);
        if (!this.isRootDept(parentId)) {
            this.deptDao.updateLeaf(parentId, false);
        }
        LOGGER.info("\u65b0\u589e\u90e8\u95e8\u6210\u529f\uff0c\u90e8\u95e8ID={}\uff0c\u90e8\u95e8\u540d\u79f0={}\uff0c\u4e0a\u7ea7\u90e8\u95e8ID={}", new Object[]{dept.getId(), deptName, parentId});
    }

    @Override
    @Transactional(transactionManager="yakSecurityTransactionManager", rollbackFor={Exception.class})
    public void updateDept(DeptSaveDTO deptSaveDTO) {
        if (deptSaveDTO == null || deptSaveDTO.getId() == null) {
            throw new YakSecurityException("\u90e8\u95e8 ID \u4e0d\u80fd\u4e3a\u7a7a");
        }
        Long deptId = deptSaveDTO.getId();
        Dept currentDept = this.requireDept(deptId);
        String deptName = this.normalizeDeptName(deptSaveDTO);
        String description = this.normalizeDescription(deptSaveDTO.getDescription());
        Long newParentId = this.normalizeParentId(deptSaveDTO.getParentId());
        List<Long> subtreeDeptIdList = this.getDeptIdListByParentId(deptId);
        if (subtreeDeptIdList.contains(newParentId)) {
            throw new YakSecurityException("\u4e0a\u7ea7\u90e8\u95e8\u4e0d\u80fd\u9009\u62e9\u5f53\u524d\u90e8\u95e8\u6216\u5176\u4e0b\u7ea7\u90e8\u95e8");
        }
        int newLevel = this.resolveDeptLevel(newParentId);
        this.validateSiblingDeptName(deptName, newParentId, deptId);
        if (currentDept.getLevel() == null) {
            throw new YakSecurityException(ResultCode.DEPT_DATA_ERROR);
        }
        int levelOffset = newLevel - currentDept.getLevel();
        Long oldParentId = this.normalizeParentId(currentDept.getParentId());
        List<Dept> allDeptList = this.deptDao.selectAllAndAscOrderByLevel();
        boolean hasChildren = this.deptDao.countByParentId(deptId) > 0;
        Dept updateDept = new Dept();
        updateDept.setId(deptId);
        updateDept.setDeptName(deptName);
        updateDept.setDescription(description);
        updateDept.setParentId(newParentId);
        updateDept.setLevel(newLevel);
        updateDept.setLeaf(!hasChildren);
        this.deptDao.update(updateDept);
        if (levelOffset != 0 && !CollectionUtils.isEmpty(allDeptList)) {
            HashSet<Long> subtreeDeptIdSet = new HashSet<Long>(subtreeDeptIdList);
            for (Dept childDept : allDeptList) {
                if (childDept == null || childDept.getId() == null || Objects.equals(deptId, childDept.getId()) || !subtreeDeptIdSet.contains(childDept.getId())) continue;
                if (childDept.getLevel() == null) {
                    throw new YakSecurityException(ResultCode.DEPT_DATA_ERROR);
                }
                this.deptDao.updateLevel(childDept.getId(), childDept.getLevel() + levelOffset);
            }
        }
        if (!Objects.equals(oldParentId, newParentId)) {
            if (!this.isRootDept(newParentId)) {
                this.deptDao.updateLeaf(newParentId, false);
            }
            this.refreshParentLeaf(oldParentId);
        }
        LOGGER.info("\u7f16\u8f91\u90e8\u95e8\u6210\u529f\uff0c\u90e8\u95e8ID={}\uff0c\u539f\u4e0a\u7ea7\u90e8\u95e8ID={}\uff0c\u65b0\u4e0a\u7ea7\u90e8\u95e8ID={}", new Object[]{deptId, oldParentId, newParentId});
    }

    @Override
    public DeptDeleteCheckVO checkBeforeDelete(Long deptId) {
        this.requireDept(deptId);
        List<Dept> childDeptList = this.getDirectChildDeptList(deptId);
        List<UserBrief> userList = this.getDirectUserList(deptId);
        ArrayList<String> childDeptNameList = new ArrayList<String>();
        for (Dept childDept : childDeptList) {
            childDeptNameList.add(this.getDeptDisplayName(childDept));
        }
        List<String> userNameList = this.getUserDisplayNameList(userList);
        DeptDeleteCheckVO result = new DeptDeleteCheckVO();
        result.setDeptId(deptId);
        result.setChildDeptNameList(childDeptNameList);
        result.setUserNameList(userNameList);
        result.setDeletable(childDeptNameList.isEmpty() && userNameList.isEmpty());
        return result;
    }

    @Override
    @Transactional(transactionManager="yakSecurityTransactionManager", rollbackFor={Exception.class})
    public void deleteDept(Long deptId) {
        Dept dept = this.requireDept(deptId);
        DeptDeleteCheckVO checkResult = this.checkBeforeDelete(deptId);
        if (!checkResult.getChildDeptNameList().isEmpty()) {
            throw new YakSecurityException("\u5f53\u524d\u90e8\u95e8\u5b58\u5728\u5b50\u90e8\u95e8\uff0c\u4e0d\u80fd\u5220\u9664");
        }
        if (!checkResult.getUserNameList().isEmpty()) {
            throw new YakSecurityException("\u5f53\u524d\u90e8\u95e8\u5b58\u5728\u5173\u8054\u7528\u6237\uff0c\u4e0d\u80fd\u5220\u9664");
        }
        if (!this.deptDao.deleteByDeptId(deptId)) {
            throw new YakSecurityException("\u90e8\u95e8\u5220\u9664\u5931\u8d25");
        }
        this.refreshParentLeaf(this.normalizeParentId(dept.getParentId()));
        LOGGER.info("\u5220\u9664\u90e8\u95e8\u6210\u529f\uff0c\u90e8\u95e8ID={}\uff0c\u90e8\u95e8\u540d\u79f0={}", (Object)deptId, (Object)dept.getDeptName());
    }

    @Override
    public List<DeptBriefVO> getDeptBriefListByChildId(Long deptId) {
        Map<Long, Dept> deptMap = this.getAllDeptMap();
        return this.getDeptBriefListFromDeptMapByChildId(deptMap, deptId);
    }

    @Override
    public List<Long> getDeptIdListByParentId(Long deptId) {
        if (deptId == null) {
            ArrayList deptIdList = this.deptDao.selectAllDeptIdList();
            return deptIdList == null ? new ArrayList() : deptIdList;
        }
        List<Dept> deptList = this.deptDao.selectAllAndAscOrderByLevel();
        LinkedHashSet<Long> deptIdSet = new LinkedHashSet<Long>();
        deptIdSet.add(deptId);
        if (!CollectionUtils.isEmpty(deptList)) {
            for (Dept dept : deptList) {
                if (dept == null || dept.getId() == null || dept.getParentId() == null || !deptIdSet.contains(dept.getParentId())) continue;
                deptIdSet.add(dept.getId());
            }
        }
        return new ArrayList<Long>(deptIdSet);
    }

    @Override
    public List<Long> getDeptIdListByParentIdAndDeptName(Long deptId, String deptName) {
        List<Long> scopeDeptIdList = this.getDeptIdListByParentId(deptId);
        if (CollectionUtils.isEmpty(scopeDeptIdList)) {
            return new ArrayList<Long>();
        }
        if (!StringUtils.hasText((String)deptName)) {
            return scopeDeptIdList;
        }
        List<Long> matchedDeptIdList = this.deptDao.selectIdListByLikeDeptName(deptName.trim());
        if (CollectionUtils.isEmpty(matchedDeptIdList)) {
            return new ArrayList<Long>();
        }
        HashSet<Long> scopeDeptIdSet = new HashSet<Long>(scopeDeptIdList);
        ArrayList<Long> result = new ArrayList<Long>();
        for (Long matchedDeptId : matchedDeptIdList) {
            if (!scopeDeptIdSet.contains(matchedDeptId)) continue;
            result.add(matchedDeptId);
        }
        return result;
    }

    @Override
    public Map<Long, Dept> getAllDeptMap() {
        List<Dept> deptList = this.deptDao.selectAllAndAscOrderByLevel();
        LinkedHashMap<Long, Dept> deptMap = new LinkedHashMap<Long, Dept>();
        if (CollectionUtils.isEmpty(deptList)) {
            return deptMap;
        }
        for (Dept dept : deptList) {
            if (dept == null || dept.getId() == null) continue;
            deptMap.put(dept.getId(), dept);
        }
        return deptMap;
    }

    @Override
    public List<DeptBriefVO> getDeptBriefListFromDeptMapByChildId(Map<Long, Dept> deptMap, Long deptId) {
        if (this.isRootDept(deptId) || CollectionUtils.isEmpty(deptMap)) {
            return new ArrayList<DeptBriefVO>();
        }
        ArrayDeque<DeptBriefVO> deptPath = new ArrayDeque<DeptBriefVO>();
        HashSet<Long> visitedDeptIds = new HashSet<Long>();
        Long currentDeptId = deptId;
        while (!this.isRootDept(currentDeptId)) {
            if (!visitedDeptIds.add(currentDeptId)) {
                LOGGER.error("\u90e8\u95e8\u5c42\u7ea7\u5b58\u5728\u5faa\u73af\u5f15\u7528\uff0c\u90e8\u95e8ID={}", (Object)currentDeptId);
                throw new YakSecurityException(ResultCode.DEPT_DATA_ERROR);
            }
            Dept dept = deptMap.get(currentDeptId);
            if (dept == null) {
                LOGGER.error("\u90e8\u95e8\u5c42\u7ea7\u6570\u636e\u4e0d\u5b8c\u6574\uff0c\u672a\u627e\u5230\u90e8\u95e8\uff0c\u90e8\u95e8ID={}", (Object)currentDeptId);
                throw new YakSecurityException(ResultCode.DEPT_DATA_ERROR);
            }
            DeptBriefVO deptBriefVO = CopyBeanUtil.copy(dept, DeptBriefVO.class);
            if (deptBriefVO == null) {
                throw new IllegalStateException("\u90e8\u95e8\u7b80\u8981\u5bf9\u8c61\u8f6c\u6362\u5931\u8d25");
            }
            deptPath.addFirst(deptBriefVO);
            currentDeptId = dept.getParentId();
        }
        return new ArrayList<DeptBriefVO>(deptPath);
    }

    @Override
    @Transactional(transactionManager="yakSecurityTransactionManager", rollbackFor={Exception.class})
    public void saveDept(List<DeptDTO> deptDTOList) {
        if (CollectionUtils.isEmpty(deptDTOList)) {
            return;
        }
        List<Dept> deptList = this.buildDeptList(deptDTOList);
        if (deptList.isEmpty()) {
            return;
        }
        this.deptDao.insertBatch(deptList);
        LOGGER.info("\u6279\u91cf\u5bfc\u5165\u90e8\u95e8\u6210\u529f\uff0c\u90e8\u95e8\u6570\u91cf={}", (Object)deptList.size());
    }

    @Override
    public List<DeptBrief> listAllDeptBrief() {
        ArrayList deptBriefList = this.deptDao.selectAllDeptBriefList();
        return deptBriefList == null ? new ArrayList() : deptBriefList;
    }

    private Dept requireDept(Long deptId) {
        if (deptId == null || this.isRootDept(deptId)) {
            throw new YakSecurityException("\u90e8\u95e8 ID \u4e0d\u6b63\u786e");
        }
        Dept dept = this.deptDao.selectByDeptId(deptId);
        if (dept == null) {
            throw new YakSecurityException("\u90e8\u95e8\u4e0d\u5b58\u5728");
        }
        return dept;
    }

    private String normalizeDeptName(DeptSaveDTO deptSaveDTO) {
        if (deptSaveDTO == null || !StringUtils.hasText((String)deptSaveDTO.getDeptName())) {
            throw new YakSecurityException("\u90e8\u95e8\u540d\u79f0\u4e0d\u80fd\u4e3a\u7a7a");
        }
        String deptName = deptSaveDTO.getDeptName().trim();
        if (deptName.length() > 64) {
            throw new YakSecurityException("\u90e8\u95e8\u540d\u79f0\u957f\u5ea6\u4e0d\u80fd\u8d85\u8fc7 64 \u4e2a\u5b57\u7b26");
        }
        return deptName;
    }

    private String normalizeDescription(String description) {
        if (!StringUtils.hasText((String)description)) {
            return "";
        }
        String normalizedDescription = description.trim();
        if (normalizedDescription.length() > 500) {
            throw new YakSecurityException("\u90e8\u95e8\u63cf\u8ff0\u957f\u5ea6\u4e0d\u80fd\u8d85\u8fc7 500 \u4e2a\u5b57\u7b26");
        }
        return normalizedDescription;
    }

    private Long normalizeParentId(Long parentId) {
        return parentId == null ? ROOT_DEPT_ID : parentId;
    }

    private int resolveDeptLevel(Long parentId) {
        if (this.isRootDept(parentId)) {
            return 1;
        }
        Dept parentDept = this.requireDept(parentId);
        if (parentDept.getLevel() == null) {
            throw new YakSecurityException(ResultCode.DEPT_DATA_ERROR);
        }
        return parentDept.getLevel() + 1;
    }

    private void validateSiblingDeptName(String deptName, Long parentId, Long excludedDeptId) {
        int count = this.deptDao.countByNameAndParentId(deptName, parentId, excludedDeptId);
        if (count > 0) {
            throw new YakSecurityException("\u540c\u4e00\u4e0a\u7ea7\u90e8\u95e8\u4e0b\u5df2\u5b58\u5728\u540c\u540d\u90e8\u95e8");
        }
    }

    private List<Dept> getDirectChildDeptList(Long deptId) {
        List<Dept> childDeptList = this.deptDao.selectListByParentId(deptId);
        return childDeptList == null ? Collections.emptyList() : childDeptList;
    }

    private List<UserBrief> getDirectUserList(Long deptId) {
        List<UserBrief> userList = this.userDao.selectBriefListByDeptIdList(Collections.singletonList(deptId));
        return userList == null ? Collections.emptyList() : userList;
    }

    private void refreshParentLeaf(Long parentId) {
        if (this.isRootDept(parentId)) {
            return;
        }
        Dept parentDept = this.deptDao.selectByDeptId(parentId);
        if (parentDept == null) {
            throw new YakSecurityException(ResultCode.DEPT_DATA_ERROR);
        }
        boolean leaf = this.deptDao.countByParentId(parentId) == 0;
        this.deptDao.updateLeaf(parentId, leaf);
    }

    private String getDeptDisplayName(Dept dept) {
        if (dept == null) {
            return "\u672a\u77e5\u90e8\u95e8";
        }
        if (StringUtils.hasText((String)dept.getDeptName())) {
            return dept.getDeptName();
        }
        return dept.getId() == null ? "\u672a\u77e5\u90e8\u95e8" : String.valueOf(dept.getId());
    }

    private List<String> getUserDisplayNameList(List<UserBrief> userList) {
        if (CollectionUtils.isEmpty(userList)) {
            return new ArrayList<String>();
        }
        LinkedHashSet<String> userNameSet = new LinkedHashSet<String>();
        for (UserBrief user : userList) {
            if (user == null) continue;
            if (StringUtils.hasText((String)user.getRealName())) {
                userNameSet.add(user.getRealName());
                continue;
            }
            if (StringUtils.hasText((String)user.getUserName())) {
                userNameSet.add(user.getUserName());
                continue;
            }
            if (user.getId() == null) continue;
            userNameSet.add(String.valueOf(user.getId()));
        }
        return new ArrayList<String>(userNameSet);
    }

    private List<Dept> buildDeptList(List<DeptDTO> deptDTOList) {
        ArrayList<Dept> deptList = new ArrayList<Dept>();
        ArrayDeque<DeptQueueNode> queue = new ArrayDeque<DeptQueueNode>();
        Set visitedDeptDTOs = Collections.newSetFromMap(new IdentityHashMap());
        HashSet<Long> generatedDeptIds = new HashSet<Long>();
        for (DeptDTO deptDTO : deptDTOList) {
            if (deptDTO == null) {
                throw new YakSecurityException(ResultCode.DEPT_DATA_ERROR);
            }
            queue.offer(new DeptQueueNode(deptDTO, ROOT_DEPT_ID, 1));
        }
        while (!queue.isEmpty()) {
            DeptDTO deptDTO;
            DeptQueueNode queueNode = (DeptQueueNode)queue.poll();
            deptDTO = queueNode.getDeptDTO();
            if (!visitedDeptDTOs.add(deptDTO)) {
                LOGGER.error("\u5bfc\u5165\u90e8\u95e8\u6570\u636e\u5b58\u5728\u5faa\u73af\u5f15\u7528");
                throw new YakSecurityException(ResultCode.DEPT_DATA_ERROR);
            }
            Dept dept = CopyBeanUtil.copy(deptDTO, Dept.class);
            if (dept == null) {
                throw new IllegalStateException("\u90e8\u95e8\u5bf9\u8c61\u8f6c\u6362\u5931\u8d25");
            }
            long deptId = this.generateUniqueDeptId(generatedDeptIds);
            List<DeptDTO> childDeptList = deptDTO.getChildDeptDTOList();
            dept.setId(deptId);
            dept.setParentId(queueNode.getParentId());
            dept.setLevel(queueNode.getLevel());
            dept.setLeaf(CollectionUtils.isEmpty(childDeptList));
            deptList.add(dept);
            if (CollectionUtils.isEmpty(childDeptList)) continue;
            for (DeptDTO childDeptDTO : childDeptList) {
                if (childDeptDTO == null) {
                    throw new YakSecurityException(ResultCode.DEPT_DATA_ERROR);
                }
                queue.offer(new DeptQueueNode(childDeptDTO, deptId, queueNode.getLevel() + 1));
            }
        }
        return deptList;
    }

    private long generateUniqueDeptId(Set<Long> generatedDeptIds) {
        for (int index = 0; index < 100; ++index) {
            long deptId = this.getDeptId();
            if (Objects.equals(ROOT_DEPT_ID, deptId) || !generatedDeptIds.add(deptId)) continue;
            return deptId;
        }
        throw new IllegalStateException("\u751f\u6210\u90e8\u95e8 ID \u5931\u8d25");
    }

    private long getDeptId() {
        return System.currentTimeMillis() % 1000L * 100000L + MathUtil.getRandomNumber(5);
    }

    private boolean isRootDept(Long deptId) {
        return deptId == null || Objects.equals(ROOT_DEPT_ID, deptId);
    }

    private static final class DeptQueueNode {
        private final DeptDTO deptDTO;
        private final Long parentId;
        private final int level;

        private DeptQueueNode(DeptDTO deptDTO, Long parentId, int level) {
            this.deptDTO = deptDTO;
            this.parentId = parentId;
            this.level = level;
        }

        private DeptDTO getDeptDTO() {
            return this.deptDTO;
        }

        private Long getParentId() {
            return this.parentId;
        }

        private int getLevel() {
            return this.level;
        }
    }
}

