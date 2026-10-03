package com.fc.v2.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fc.v2.model.auto.TSecreCarrier;

import java.util.List;

/**
 * 涉密载体册面 Service接口
 *
 * @author fuce
 * @date 2026-09-12
 */
public interface ITSecreCarrierService {

    /** 按主键查询 */
    TSecreCarrier selectTSecreCarrierById(Long id);

    /** 按条件查询列表（分页由调用方统一处理） */
    List<TSecreCarrier> selectTSecreCarrierList(Wrapper<TSecreCarrier> queryWrapper);

    /** 新增 */
    int insertTSecreCarrier(TSecreCarrier record);

    /** 修改 */
    int updateTSecreCarrier(TSecreCarrier record);

    /** 批量删除 */
    int deleteTSecreCarrierByIds(String ids);

    /** 按主键删除 */
    int deleteTSecreCarrierById(Long id);
}
