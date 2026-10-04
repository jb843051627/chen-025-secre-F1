package com.fc.v2.mapper.custom;

import org.apache.ibatis.annotations.Param;

import java.util.Date;

/**
 * 载体处置流转单 落库直写（带哨兵的条件更新）
 *
 * 规矩的底子：写库读库都按系统数出来的那一个档位，报送里捎来的号不作数。
 * 每条语句的 WHERE 都钉死「id + 未删 + 当时那档那情形 + 当时那段底稿的长度」，
 * 两笔同时递到，库里只容先落的那笔；后到的回去重算——档位情形没动过才许重递，
 * 动过了就原样打回，不许并作一条、也不许吞掉一笔。
 *
 * @author fuce
 * @date 2026-10-03
 */
public interface SecreDispFlowDao {

    /**
     * 挪档与叠层（推进/回退/记按语/叫停/作废/收尾）：整段底稿重排后落回，
     * 同时挪档换情形。哨兵钉死当时那档那情形与底稿长度，防两笔相吞。
     * 见证两岗与化浆凭证码只在监销走满那一笔带值，其余时候为 null 不落列。
     *
     * @return 落成的行数；0 = 哨兵没对上（另有一笔先落了）
     */
    int rewriteAndTransit(@Param("id") Long id,
                          @Param("expectStatus") int expectStatus,
                          @Param("expectStage") int expectStage,
                          @Param("expectContentLen") int expectContentLen,
                          @Param("content") String content,
                          @Param("stage") int stage,
                          @Param("status") int status,
                          @Param("lastAction") String lastAction,
                          @Param("witnessOne") String witnessOne,
                          @Param("witnessTwo") String witnessTwo,
                          @Param("pulpNo") String pulpNo,
                          @Param("updateBy") String updateBy,
                          @Param("updateTime") Date updateTime);

    /**
     * 撤销：只在头一档起来之前那一小段（未起）落得下；撤掉的不留进册面。
     *
     * @return 落成的行数；0 = 哨兵没对上
     */
    int markRevoked(@Param("id") Long id,
                    @Param("updateBy") String updateBy,
                    @Param("updateTime") Date updateTime);
}
