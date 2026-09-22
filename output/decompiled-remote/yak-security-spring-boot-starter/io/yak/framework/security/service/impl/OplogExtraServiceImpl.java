/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.springframework.stereotype.Service
 *  org.springframework.transaction.annotation.Transactional
 *  org.springframework.util.CollectionUtils
 *  org.springframework.util.StringUtils
 */
package io.yak.framework.security.service.impl;

import io.yak.framework.security.common.entity.OplogExtra;
import io.yak.framework.security.common.enums.oplog.OplogCode;
import io.yak.framework.security.dao.OplogExtraDao;
import io.yak.framework.security.service.OplogExtraService;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

@Service(value="yakSecurityOplogExtraServiceImpl")
public class OplogExtraServiceImpl
implements OplogExtraService {
    private final OplogExtraDao oplogExtraDao;

    public OplogExtraServiceImpl(OplogExtraDao oplogExtraDao) {
        this.oplogExtraDao = oplogExtraDao;
    }

    @Override
    @Transactional(transactionManager="yakSecurityTransactionManager", rollbackFor={Exception.class})
    public void saveOplogExtraList(List<String> nameList, OplogCode oplogCode) {
        if (oplogCode == null) {
            return;
        }
        List<String> validNameList = this.normalizeNames(nameList);
        if (validNameList.isEmpty()) {
            return;
        }
        List<OplogExtra> oplogExtraList = this.buildOplogExtraList(validNameList, oplogCode);
        if (oplogExtraList.isEmpty()) {
            return;
        }
        this.oplogExtraDao.insertBatch(oplogExtraList);
    }

    @Override
    public List<String> getOplogExtraNameListByType(Integer type) {
        if (type == null) {
            return new ArrayList<String>();
        }
        List<OplogExtra> oplogExtraList = this.oplogExtraDao.selectListByType(type);
        if (CollectionUtils.isEmpty(oplogExtraList)) {
            return new ArrayList<String>();
        }
        return oplogExtraList.stream().filter(Objects::nonNull).map(OplogExtra::getInfo).filter(StringUtils::hasText).map(String::trim).distinct().collect(Collectors.toList());
    }

    private List<OplogExtra> buildOplogExtraList(List<String> nameList, OplogCode oplogCode) {
        ArrayList<OplogExtra> oplogExtraList = new ArrayList<OplogExtra>(nameList.size());
        for (String name : nameList) {
            OplogExtra oplogExtra = new OplogExtra();
            oplogExtra.setInfo(name);
            oplogExtra.setType(oplogCode.getType());
            oplogExtraList.add(oplogExtra);
        }
        return oplogExtraList;
    }

    private List<String> normalizeNames(List<String> nameList) {
        if (CollectionUtils.isEmpty(nameList)) {
            return new ArrayList<String>();
        }
        return nameList.stream().filter(StringUtils::hasText).map(String::trim).distinct().collect(Collectors.toList());
    }
}

