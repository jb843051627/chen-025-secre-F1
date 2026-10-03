package com.fc.v2.service;

import com.fc.v2.model.auto.TSecreLevelBill;

/**
 * 密级变更签批单 Service接口（approval-chain 形状：多阶段签批，无增删改查入口）
 *
 * @author fuce
 * @date 2026-09-14
 */
public interface ITSecreLevelBillService {

    /** 按主键回查单据 */
    TSecreLevelBill selectTSecreLevelBillById(Long id);

    /** 签批一票：返回更新后的单据；被拒返回 null */
    TSecreLevelBill approve(Long id, String approver, String comment);

    /** 否决：返回更新后的单据；被拒返回 null */
    TSecreLevelBill reject(Long id, String approver, String comment);

    /** 退回上一环节：返回更新后的单据；被拒返回 null */
    TSecreLevelBill rollback(Long id, String comment);
}
