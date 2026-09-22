/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.baomidou.mybatisplus.core.metadata.IPage
 *  io.yak.framework.common.PageData
 *  io.yak.framework.common.PagingData
 *  org.springframework.stereotype.Service
 *  org.springframework.transaction.annotation.Transactional
 *  org.springframework.util.CollectionUtils
 *  org.springframework.util.StringUtils
 */
package io.yak.framework.security.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.yak.framework.common.PageData;
import io.yak.framework.common.PagingData;
import io.yak.framework.security.common.dto.oplog.OplogDTO;
import io.yak.framework.security.common.dto.oplog.OplogQueryDTO;
import io.yak.framework.security.common.entity.Oplog;
import io.yak.framework.security.common.enums.ResultCode;
import io.yak.framework.security.common.vo.oplog.OplogOptionsVO;
import io.yak.framework.security.common.vo.oplog.OplogVO;
import io.yak.framework.security.dao.OplogDao;
import io.yak.framework.security.exception.YakSecurityException;
import io.yak.framework.security.service.OplogService;
import io.yak.framework.security.util.CopyBeanUtil;
import io.yak.framework.security.util.NetworkUtil;
import io.yak.framework.security.util.SensitiveDataSanitizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

@Service(value="yakSecurityOplogServiceImpl")
public class OplogServiceImpl
implements OplogService {
    private static final String SYSTEM_OPERATOR_IP = "0.0.0.0";
    private static final String DEFAULT_OPERATE_PAGE = "SYSTEM";
    private static final String DEFAULT_OPERATION_METHOD = "SERVICE";
    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 200;
    private final OplogDao oplogDao;

    public OplogServiceImpl(OplogDao oplogDao) {
        this.oplogDao = oplogDao;
    }

    @Override
    public PagingData<OplogVO> getOplogPage(OplogQueryDTO queryDTO) {
        if (queryDTO == null) {
            queryDTO = new OplogQueryDTO();
        }
        this.normalizeQuery(queryDTO);
        IPage<Oplog> oplogPage = this.oplogDao.selectPageWithoutDetail(queryDTO);
        if (oplogPage == null) {
            throw new IllegalStateException("\u67e5\u8be2\u64cd\u4f5c\u65e5\u5fd7\u5206\u9875\u6570\u636e\u5931\u8d25");
        }
        List oplogList = oplogPage.getRecords();
        if (CollectionUtils.isEmpty((Collection)oplogList)) {
            return OplogServiceImpl.toPagingData(new ArrayList(), oplogPage);
        }
        ArrayList<OplogVO> oplogVOList = new ArrayList<OplogVO>(oplogList.size());
        for (Oplog oplog : oplogList) {
            OplogVO oplogVO = this.convertToVO(oplog);
            if (oplogVO == null) continue;
            oplogVOList.add(oplogVO);
        }
        return OplogServiceImpl.toPagingData(oplogVOList, oplogPage);
    }

    @Override
    public OplogVO getOplogDetailByOplogId(Long oplogId) {
        if (oplogId == null) {
            throw new YakSecurityException(ResultCode.OPLOG_NOT_EXIST);
        }
        Oplog oplog = this.oplogDao.selectByOplogId(oplogId);
        if (oplog == null) {
            throw new YakSecurityException(ResultCode.OPLOG_NOT_EXIST);
        }
        return this.convertToVO(oplog);
    }

    @Override
    public OplogOptionsVO getOptions() {
        OplogOptionsVO options = new OplogOptionsVO();
        options.setOperateTypes(this.cleanOptions(this.oplogDao.listOperateType()));
        options.setOperatePages(this.cleanOptions(this.oplogDao.listOperatePage()));
        options.setOperationMethods(this.cleanOptions(this.oplogDao.listOperationMethods()));
        options.setTargetTypes(this.cleanOptions(this.oplogDao.listTargetType()));
        return options;
    }

    @Override
    public List<String> listTargetType() {
        return this.cleanOptions(this.oplogDao.listTargetType());
    }

    @Override
    @Transactional(transactionManager="yakSecurityTransactionManager", rollbackFor={Exception.class})
    public Long saveOplog(OplogDTO oplogDTO) {
        if (oplogDTO == null) {
            throw new IllegalArgumentException("\u64cd\u4f5c\u65e5\u5fd7\u4fe1\u606f\u4e0d\u80fd\u4e3a\u7a7a");
        }
        Oplog oplog = CopyBeanUtil.copy(oplogDTO, Oplog.class);
        if (oplog == null) {
            throw new IllegalStateException("\u64cd\u4f5c\u65e5\u5fd7\u5bf9\u8c61\u8f6c\u6362\u5931\u8d25");
        }
        oplog.setOperator(this.sanitize(oplogDTO.getOperator()));
        oplog.setOperatePage(this.sanitize(OplogServiceImpl.defaultIfBlank(oplogDTO.getOperatePage(), DEFAULT_OPERATE_PAGE)));
        oplog.setOperateType(this.sanitize(oplogDTO.getOperateType()));
        oplog.setTarget(this.sanitize(oplogDTO.getTarget()));
        oplog.setTargetType(this.sanitize(oplogDTO.getTargetType()));
        oplog.setDetail(this.sanitize(oplogDTO.getDetail()));
        oplog.setOperationMethods(this.sanitize(OplogServiceImpl.defaultIfBlank(oplogDTO.getOperationMethods(), DEFAULT_OPERATION_METHOD)));
        oplog.setOperatorIp(this.sanitize(NetworkUtil.getRealIpAddressOrDefault(SYSTEM_OPERATOR_IP)));
        this.oplogDao.insert(oplog);
        if (oplog.getId() == null) {
            throw new IllegalStateException("\u4fdd\u5b58\u64cd\u4f5c\u65e5\u5fd7\u540e\u672a\u751f\u6210\u65e5\u5fd7 ID");
        }
        return oplog.getId();
    }

    private void normalizeQuery(OplogQueryDTO queryDTO) {
        if (queryDTO.getPage() < 1) {
            queryDTO.setPage(1);
        }
        if (queryDTO.getSize() < 1) {
            queryDTO.setSize(10);
        } else if (queryDTO.getSize() > 200) {
            queryDTO.setSize(200);
        }
        Long startTime = queryDTO.getStartTime();
        Long endTime = queryDTO.getEndTime();
        if (startTime != null && endTime != null && startTime > endTime) {
            throw new YakSecurityException(ResultCode.PARAM_ERROR);
        }
    }

    private OplogVO convertToVO(Oplog oplog) {
        if (oplog == null) {
            return null;
        }
        OplogVO oplogVO = CopyBeanUtil.copy(oplog, OplogVO.class);
        if (oplogVO == null) {
            throw new IllegalStateException("\u64cd\u4f5c\u65e5\u5fd7\u89c6\u56fe\u5bf9\u8c61\u8f6c\u6362\u5931\u8d25");
        }
        oplogVO.setCreateTime(oplog.getCreateTime());
        oplogVO.setUpdateTime(oplog.getUpdateTime());
        return oplogVO;
    }

    private List<String> cleanOptions(List<String> values) {
        if (CollectionUtils.isEmpty(values)) {
            return new ArrayList<String>();
        }
        return values.stream().filter(StringUtils::hasText).map(String::trim).distinct().sorted().collect(Collectors.toList());
    }

    private static <T> PagingData<T> toPagingData(List<T> records, IPage<?> page) {
        return PagingData.from((PageData)new PageData(records, page.getTotal(), page.getPages(), page.getCurrent(), page.getSize()));
    }

    private static String defaultIfBlank(String value, String defaultValue) {
        return StringUtils.hasText((String)value) ? value : defaultValue;
    }

    private String sanitize(String value) {
        if (value == null) {
            return null;
        }
        return SensitiveDataSanitizer.sanitize(value);
    }
}

