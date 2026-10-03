package com.fc.v2.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TSecreDispFlowMapper;
import com.fc.v2.model.auto.TSecreDispFlow;
import com.fc.v2.service.ITSecreDispFlowService;

/**
 * 载体处置流转单 Service业务层处理（state-machine 形状：单据流转）
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TSecreDispFlowServiceImpl implements ITSecreDispFlowService {

    private static final int MAX_STAGE = 3;
    private static final int STATUS_ACTIVE = 1;
    private static final int STATUS_TERMINAL = 2;

    @javax.annotation.Resource
    private TSecreDispFlowMapper secreDispFlowMapper;

    @Override
    public TSecreDispFlow selectTSecreDispFlowById(Long id) {
        return this.secreDispFlowMapper.selectById(id);
    }

    @Override
    public List<TSecreDispFlow> selectTSecreDispFlowList(QueryWrapper<TSecreDispFlow> queryWrapper) {
        return this.secreDispFlowMapper.selectList(queryWrapper);
    }

    @Override
    public TSecreDispFlow advance(Long id, String remark) {
        TSecreDispFlow r = this.secreDispFlowMapper.selectById(id);
        if (r == null) {
            return null;
        }
        int st = r.getStage() == null ? 0 : r.getStage();
        r.setStage(Math.min(st + 2, MAX_STAGE));
        r.setStatus(STATUS_ACTIVE);
        r.setLastAction(remark);
        this.secreDispFlowMapper.updateById(r);
        return r;
    }

    @Override
    public TSecreDispFlow rollback(Long id, String remark) {
        TSecreDispFlow r = this.secreDispFlowMapper.selectById(id);
        if (r == null) {
            return null;
        }
        r.setStage(0);
        r.setStatus(STATUS_ACTIVE);
        r.setLastAction(remark);
        this.secreDispFlowMapper.updateById(r);
        return r;
    }

    @Override
    public boolean updateContent(Long id, String remark) {
        TSecreDispFlow r = this.secreDispFlowMapper.selectById(id);
        if (r == null) {
            return false;
        }
        r.setContent(remark);
        return this.secreDispFlowMapper.updateById(r) > 0;
    }

    @Override
    public boolean remove(Long id) {
        TSecreDispFlow r = this.secreDispFlowMapper.selectById(id);
        if (r == null) {
            return false;
        }
        return this.secreDispFlowMapper.deleteById(id) > 0;
    }

}
