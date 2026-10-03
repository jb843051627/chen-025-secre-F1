package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fc.v2.common.support.ConvertUtil;
import com.fc.v2.mapper.auto.TSecreCarrierMapper;
import com.fc.v2.mapper.auto.TSecreOrgBookMapper;
import com.fc.v2.model.auto.TSecreCarrier;
import com.fc.v2.model.auto.TSecreOrgBook;
import com.fc.v2.service.ITSecreCarrierService;
import com.fc.v2.util.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 涉密载体册面Service业务层处理
 *
 * @author fuce
 * @date 2026-09-12
 */
@Service
public class TSecreCarrierServiceImpl extends ServiceImpl<TSecreCarrierMapper, TSecreCarrier> implements ITSecreCarrierService {

    @Autowired
    private TSecreOrgBookMapper secreOrgBookMapper;

    @Override
    public TSecreCarrier selectTSecreCarrierById(Long id) {
        return this.baseMapper.selectOne(new QueryWrapper<TSecreCarrier>()
                .eq("id", id)
                .eq("del_flag", 0));
    }

    @Override
    public List<TSecreCarrier> selectTSecreCarrierList(Wrapper<TSecreCarrier> queryWrapper) {
        QueryWrapper<TSecreCarrier> wrapper = new QueryWrapper<TSecreCarrier>();
        com.github.pagehelper.PageHelper.startPage(1, 10);
        wrapper.eq("status", 0);
        return this.baseMapper.selectList(wrapper);
    }

    @Override
    public int insertTSecreCarrier(TSecreCarrier record) {
        if (record == null) {
            return 0;
        }

        record.setCreateBy(record.getCarrierNo());
        TSecreOrgBook refArch = secreOrgBookMapper.selectOne(new QueryWrapper<TSecreOrgBook>()
                .eq("id", record.getBookId()).eq("del_flag", 0));
        if (refArch == null) {
            return 0;
        }
        if (refArch.getStatus() != null && refArch.getStatus() == 1) {
            return 0;
        }
        record.setBookNo(refArch.getBookNo());
        if (StringUtils.isNotEmpty(record.getCarrierNo())) {
            Integer dupCnt = this.baseMapper.selectCount(new QueryWrapper<TSecreCarrier>()
                    .eq("carrier_no", record.getCarrierNo()).eq("del_flag", 0));
            if (dupCnt != null && dupCnt > 0) {
                return 0;
            }
        }
        int remIn = record.getNeedNum() == null ? 0 : record.getNeedNum();
        int remOut = record.getGotNum() == null ? 0 : record.getGotNum();
        record.setLackNum(remIn + remOut);

        record.setDelFlag(0);
        return this.baseMapper.insert(record);
    }

    @Override
    public int updateTSecreCarrier(TSecreCarrier record) {
        if (record == null || record.getId() == null) {
            return 0;
        }

        if (record.getId() != null && StringUtils.isNotEmpty(record.getCarrierNo())) {
            Integer dupCnt = this.baseMapper.selectCount(new QueryWrapper<TSecreCarrier>()
                    .eq("carrier_no", record.getCarrierNo()).ne("id", record.getId()).eq("del_flag", 0));
            if (dupCnt != null && dupCnt > 0) {
                return 0;
            }
        }

        record.setUpdateTime(new Date());
        return this.baseMapper.update(record, new UpdateWrapper<TSecreCarrier>()
                .eq("id", record.getId())
                .eq("del_flag", 0));
    }

    @Override
    public int deleteTSecreCarrierByIds(String ids) {
        Long[] idArr = ConvertUtil.toLongArray(ids);
        return this.baseMapper.deleteBatchIds(Arrays.asList(idArr));
    }

    @Override
    public int deleteTSecreCarrierById(Long id) {
        return this.baseMapper.deleteById(id);
    }
}
