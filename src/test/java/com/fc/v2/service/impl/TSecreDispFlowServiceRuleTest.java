package com.fc.v2.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import com.fc.v2.common.exception.secre.SecreFlowException;
import com.fc.v2.mapper.auto.TSecreDispFlowMapper;
import com.fc.v2.mapper.auto.TSecreHandRowMapper;
import com.fc.v2.mapper.auto.TSecreLevelBillMapper;
import com.fc.v2.mapper.auto.TSecreOrgBookMapper;
import com.fc.v2.mapper.custom.SecreDispFlowDao;
import com.fc.v2.model.auto.TSecreDispFlow;
import com.fc.v2.model.auto.TSecreHandRow;
import com.fc.v2.model.auto.TSecreLevelBill;
import com.fc.v2.model.auto.TSecreOrgBook;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 处置进度七条规矩的库内联测：H2（MySQL 模式）＋真 mapper 语句，逐条过。
 *
 * @author fuce
 * @date 2026-10-03
 */
class TSecreDispFlowServiceRuleTest {

    private SqlSessionFactory factory;
    private SqlSession session;
    private TSecreDispFlowServiceImpl service;
    private TSecreDispFlowMapper flowMapper;
    private TSecreLevelBillMapper billMapper;
    private TSecreHandRowMapper handMapper;
    private TSecreOrgBookMapper bookMapper;

    @BeforeEach
    void setUp() throws Exception {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:secreflow" + System.nanoTime() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            st.execute("CREATE TABLE t_secre_disp_flow ("
                    + "id bigint NOT NULL PRIMARY KEY, biz_no varchar(64), carrier_no varchar(64),"
                    + " book_no varchar(64), qty int, plan_level varchar(32), stage int, status int,"
                    + " witness_one varchar(64), witness_two varchar(64), pulp_no varchar(64),"
                    + " content varchar(2000), last_action varchar(64), del_flag int DEFAULT 0,"
                    + " create_by varchar(64), create_time datetime, update_by varchar(64),"
                    + " update_time datetime, remark varchar(500))");
            st.execute("CREATE TABLE t_secre_level_bill ("
                    + "id bigint NOT NULL PRIMARY KEY, bill_no varchar(64), node_no int, sign_mode int,"
                    + " need_count int, sign_count int, status int, del_flag int DEFAULT 0,"
                    + " create_by varchar(64), create_time datetime, update_by varchar(64),"
                    + " update_time datetime, remark varchar(500))");
            st.execute("CREATE TABLE t_secre_hand_row ("
                    + "id bigint NOT NULL PRIMARY KEY, batch_no varchar(64), row_no int, item_code varchar(64),"
                    + " qty decimal(12,2), quali_num decimal(12,2), hand_kind varchar(32), status int,"
                    + " del_flag int DEFAULT 0, create_by varchar(64), create_time datetime,"
                    + " update_by varchar(64), update_time datetime, remark varchar(500))");
            st.execute("CREATE TABLE t_secre_org_book ("
                    + "id bigint NOT NULL PRIMARY KEY, book_no varchar(64), book_name varchar(128),"
                    + " book_kind varchar(32), road_name varchar(128), sync_kind varchar(32), status int,"
                    + " del_flag int DEFAULT 0, create_by varchar(64), create_time datetime,"
                    + " update_by varchar(64), update_time datetime, remark varchar(500))");
            // 种子名录：ML00 在册、ML01 已并走撤出（与 doc/schema/secre.sql 一致）
            st.execute("INSERT INTO t_secre_org_book (id, book_no, book_name, status, del_flag)"
                    + " VALUES (0, 'ML00', '在册单位', 0, 0), (1, 'ML01', '已并走单位', 1, 0)");
        }

        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setEnvironment(new Environment("test", new JdbcTransactionFactory(), ds));
        configuration.addMapper(TSecreDispFlowMapper.class);
        configuration.addMapper(TSecreLevelBillMapper.class);
        configuration.addMapper(TSecreHandRowMapper.class);
        configuration.addMapper(TSecreOrgBookMapper.class);
        try (InputStream in = Resources.getResourceAsStream("mybatis/custom/SecreDispFlowMapper.xml")) {
            new XMLMapperBuilder(in, configuration, "mybatis/custom/SecreDispFlowMapper.xml",
                    configuration.getSqlFragments()).parse();
        }
        configuration.addMapper(SecreDispFlowDao.class);
        GlobalConfigUtils.setGlobalConfig(configuration, new GlobalConfig());
        this.factory = new MybatisSqlSessionFactoryBuilder().build(configuration);

        this.session = factory.openSession(true);
        this.service = newService(session);
        this.flowMapper = session.getMapper(TSecreDispFlowMapper.class);
        this.billMapper = session.getMapper(TSecreLevelBillMapper.class);
        this.handMapper = session.getMapper(TSecreHandRowMapper.class);
        this.bookMapper = session.getMapper(TSecreOrgBookMapper.class);
    }

    /** 每个入口各搭各的会话，两入口互不挤一个 SqlSession */
    private TSecreDispFlowServiceImpl newService(SqlSession sqlSession) {
        TSecreDispFlowServiceImpl s = new TSecreDispFlowServiceImpl();
        ReflectionTestUtils.setField(s, "secreDispFlowMapper", sqlSession.getMapper(TSecreDispFlowMapper.class));
        ReflectionTestUtils.setField(s, "secreLevelBillMapper", sqlSession.getMapper(TSecreLevelBillMapper.class));
        ReflectionTestUtils.setField(s, "secreHandRowMapper", sqlSession.getMapper(TSecreHandRowMapper.class));
        ReflectionTestUtils.setField(s, "secreOrgBookMapper", sqlSession.getMapper(TSecreOrgBookMapper.class));
        ReflectionTestUtils.setField(s, "secreDispFlowDao", sqlSession.getMapper(SecreDispFlowDao.class));
        return s;
    }

    private TSecreDispFlow newRecord(String carrierNo, String bookNo) {
        TSecreDispFlow r = new TSecreDispFlow();
        r.setCarrierNo(carrierNo);
        r.setBookNo(bookNo);
        r.setQty(3);
        r.setPlanLevel("机密");
        return r;
    }

    private TSecreDispFlow walk(TSecreDispFlowServiceImpl s, Long id, int toStage) {
        // 未起→核件→…→目标档；闸门数据由用例先备好
        TSecreDispFlow r = s.selectTSecreDispFlowById(id);
        while (r.getStatus() == 0 || r.getStage() < toStage) {
            r = s.advance(id, "走", null, null, null);
        }
        return r;
    }

    // ---------------- 第一条 ----------------

    @Test
    void rule1_missingFieldNamedAndRefused() {
        TSecreDispFlow r = newRecord("J001", "ML00");
        r.setQty(null);
        r.setPlanLevel("  ");
        SecreFlowException e = assertThrows(SecreFlowException.class, () -> service.add(r));
        assertTrue(e.getMessage().contains("件数"), "缺件数要点名件数：" + e.getMessage());
        assertTrue(e.getMessage().contains("拟密级"), "缺拟密级要点名拟密级：" + e.getMessage());
        assertTrue(e.getMessage().contains("起不来"));

        SecreFlowException e2 = assertThrows(SecreFlowException.class,
                () -> service.add(new TSecreDispFlow()));
        assertTrue(e2.getMessage().contains("载体件码"));
        assertTrue(e2.getMessage().contains("所属名录代号"));
    }

    @Test
    void rule1_addLandsUnstartedAndCounts() {
        TSecreDispFlow r = service.add(newRecord("J002", "ML00"));
        assertNotNull(r.getId());
        assertEquals(0, r.getStatus().intValue(), "起行落未起");
        assertEquals(0, r.getStage().intValue());
        assertEquals(1, SecreFlowLayers.count(r.getContent()), "起行落一层");
        assertEquals(1, service.countUnfinished(), "随起行加一");
        // 已并走的名录挡在册外
        assertThrows(SecreFlowException.class, () -> service.add(newRecord("J003", "ML01")));
        // 不在册的名录也起不来
        assertThrows(SecreFlowException.class, () -> service.add(newRecord("J004", "ML99")));
    }

    // ---------------- 第二条 ----------------

    @Test
    void rule2_oneStepAtATime() {
        TSecreDispFlow r = service.add(newRecord("J010", "ML00"));
        r = service.advance(r.getId(), "起核件", null, null, null);
        assertEquals(1, r.getStatus().intValue());
        assertEquals(0, r.getStage().intValue(), "头一档核件起来");
        r = service.advance(r.getId(), "核完", null, null, null);
        assertEquals(1, r.getStage().intValue(), "只挪紧邻一档到定级");
        // 报送里捎来的档位号不作数：服务根本没有收档位号的口子，下一笔仍按系统数出来的走
        insertBill(r.getBizNo(), 1);
        r = service.advance(r.getId(), "定级齐", null, null, null);
        assertEquals(2, r.getStage().intValue(), "到清退");
    }

    @Test
    void rule2_twoEntrancesOneStepOnly() throws Exception {
        TSecreDispFlow r = service.add(newRecord("J011", "ML00"));
        r = service.advance(r.getId(), "起", null, null, null);
        final Long id = r.getId();

        // 两个入口同一瞬间各递一笔推进：只许先落的那笔走一档，另一笔原样打回
        TSecreDispFlowServiceImpl s1 = newService(factory.openSession(true));
        TSecreDispFlowServiceImpl s2 = newService(factory.openSession(true));
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        Runnable waitGo = () -> {
            ready.countDown();
            try {
                go.await(3, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };
        Future<Object> f1 = pool.submit(() -> {
            waitGo.run();
            try {
                return s1.advance(id, "甲口", null, null, null);
            } catch (SecreFlowException e) {
                return e;
            }
        });
        Future<Object> f2 = pool.submit(() -> {
            waitGo.run();
            try {
                return s2.advance(id, "乙口", null, null, null);
            } catch (SecreFlowException e) {
                return e;
            }
        });
        ready.await(3, TimeUnit.SECONDS);
        go.countDown();
        Object o1 = f1.get(10, TimeUnit.SECONDS);
        Object o2 = f2.get(10, TimeUnit.SECONDS);
        pool.shutdownNow();

        int wins = (o1 instanceof TSecreDispFlow ? 1 : 0) + (o2 instanceof TSecreDispFlow ? 1 : 0);
        assertEquals(1, wins, "两笔同递只许一笔落成，另一笔打回");
        session.clearCache();
        TSecreDispFlow after = service.selectTSecreDispFlowById(id);
        assertEquals(1, after.getStage().intValue(), "只走紧邻一档，决不许一笔推着多走两档");
    }

    // ---------------- 第三条 ----------------

    @Test
    void rule3_gatesAndDestroySealing() {
        TSecreDispFlow r = service.add(newRecord("J020", "ML00"));
        r = walk(service, r.getId(), 1);
        final Long id = r.getId();
        final String bizNo = r.getBizNo();
        final String carrierNo = r.getCarrierNo();
        // 定级→清退：密级变更没走满，不许挪
        SecreFlowException e1 = assertThrows(SecreFlowException.class,
                () -> service.advance(id, "挪", null, null, null));
        assertTrue(e1.getMessage().contains("密级变更"));
        insertBill(bizNo, 0);
        assertThrows(SecreFlowException.class, () -> service.advance(id, "挪", null, null, null),
                "签批单还在签，不算走满");
        insertBill(bizNo, 1);
        r = service.advance(id, "走满", null, null, null);
        assertEquals(2, r.getStage().intValue());

        // 清退→监销：核收没落「可」，不许开口
        SecreFlowException e2 = assertThrows(SecreFlowException.class,
                () -> service.advance(id, "挪", null, null, null));
        assertTrue(e2.getMessage().contains("核收"));
        insertHandRow(carrierNo, 2);
        assertThrows(SecreFlowException.class, () -> service.advance(id, "挪", null, null, null),
                "退回的不算核收落可");
        insertHandRow(carrierNo, 1);
        r = service.advance(id, "核收可", null, null, null);
        assertEquals(3, r.getStage().intValue());

        // 监销走满：两名见证岗＋化浆凭证码，缺一样走不满
        assertThrows(SecreFlowException.class, () -> service.advance(id, "", "岗甲", null, "PZ-1"));
        assertThrows(SecreFlowException.class, () -> service.advance(id, "", "岗甲", "岗甲", "PZ-1"), "同一岗不算两名");
        assertThrows(SecreFlowException.class, () -> service.advance(id, "", "岗甲", "岗乙", "  "));
        r = service.advance(id, "当场", "岗甲", "岗乙", "PZ-1");
        assertEquals(2, r.getStatus().intValue(), "监销走满即封住");
        assertEquals("岗甲", r.getWitnessOne());
        assertEquals("岗乙", r.getWitnessTwo());
        assertEquals("PZ-1", r.getPulpNo(), "化浆凭证的码挂回这一行");
        assertEquals(0, service.countUnfinished(), "随封行减一");
    }

    @Test
    void rule3_withdrawAndMergeAlsoCloseTheTail() {
        // 中途撤了申请 → 收尾封住
        TSecreDispFlow r1 = walk(service, service.add(newRecord("J021", "ML00")).getId(), 1);
        r1 = service.seal(r1.getId(), "撤申请", "申请方撤回");
        assertEquals(2, r1.getStatus().intValue());

        // 所属单位还在册：按并走收尾，不收
        TSecreDispFlow r2 = walk(service, service.add(newRecord("J022", "ML00")).getId(), 0);
        assertThrows(SecreFlowException.class, () -> service.seal(r2.getId(), "单位并走", "并走"));

        // 未起的单子不走收尾，走撤销
        TSecreDispFlow r4 = service.add(newRecord("J024", "ML00"));
        assertThrows(SecreFlowException.class, () -> service.seal(r4.getId(), "撤申请", ""));

        // 所属单位真从名录上并走了（名册上已撤出）：收尾成立
        TSecreOrgBook ml00 = bookMapper.selectOne(new QueryWrapper<TSecreOrgBook>().eq("book_no", "ML00"));
        ml00.setStatus(1);
        bookMapper.updateById(ml00);
        TSecreDispFlow merged = service.seal(r2.getId(), "单位并走", "单位并入他处");
        assertEquals(2, merged.getStatus().intValue());
    }

    // ---------------- 第四条 ----------------

    @Test
    void rule4_sealedIsReadOnly() {
        TSecreDispFlow r = service.add(newRecord("J030", "ML00"));
        r = walk(service, r.getId(), 1);
        insertBill(r.getBizNo(), 1);
        r = walk(service, r.getId(), 2);
        insertHandRow(r.getCarrierNo(), 1);
        r = walk(service, r.getId(), 3);
        final Long id = r.getId();
        r = service.advance(id, "当场", "岗甲", "岗乙", "PZ-9");
        assertEquals(2, r.getStatus().intValue());

        assertThrows(SecreFlowException.class, () -> service.advance(id, "", null, null, null), "封住挪不动");
        assertThrows(SecreFlowException.class, () -> service.rollback(id, ""), "封住收不回");
        assertThrows(SecreFlowException.class, () -> service.updateContent(id, "改"), "封住改不进");
        assertThrows(SecreFlowException.class, () -> service.remove(id), "封住抽不走");
        assertThrows(SecreFlowException.class, () -> service.halt(id, ""), "封住叫不停");
        assertThrows(SecreFlowException.class, () -> service.annul(id, ""), "封住废不了");
        assertThrows(SecreFlowException.class, () -> service.seal(id, "撤申请", ""), "封住不再收");

        // 封住的照旧列得出，只是没有把手
        List<TSecreDispFlow> listed = service.selectTSecreDispFlowList(new QueryWrapper<>());
        assertTrue(listed.stream().anyMatch(x -> x.getId().equals(id)));
        assertEquals(0, service.countUnfinished());
    }

    // ---------------- 第五条 ----------------

    @Test
    void rule5_rollbackSinksAndRewalkAccumulates() {
        TSecreDispFlow r = service.add(newRecord("J040", "ML00"));
        r = walk(service, r.getId(), 1);
        insertBill(r.getBizNo(), 1);
        r = walk(service, r.getId(), 2);
        final Long id = r.getId();
        service.updateContent(id, "清退头一句");
        int before = SecreFlowLayers.count(service.selectTSecreDispFlowById(id).getContent());

        // 收一档：清退落下的按语沉到末位
        r = service.rollback(id, "核收有出入");
        assertEquals(1, r.getStage().intValue());
        List<String> lines = SecreFlowLayers.split(r.getContent());
        assertEquals(before + 1, lines.size(), "层数只多不少，一笔不吞");
        assertTrue(SecreFlowLayers.isSunk(lines.get(lines.size() - 1)), "被收档的按语沉到末位");
        assertEquals("清退头一句", SecreFlowLayers.noteOf(lines.get(lines.size() - 1)));

        // 重走这一档，从这一档重新攒起
        r = service.advance(id, "重走", null, null, null);
        assertEquals(2, r.getStage().intValue());
        assertTrue(service.updateContent(id, "清退第二遍这句"));
        // 同一档第二遍递上来，只留头一遍那句，不另叠一层
        int layersNow = SecreFlowLayers.count(service.selectTSecreDispFlowById(id).getContent());
        assertFalse(service.updateContent(id, "清退第二遍这句"));
        assertEquals(layersNow, SecreFlowLayers.count(service.selectTSecreDispFlowById(id).getContent()));

        // 一路收回头一档，再收就不动了
        service.rollback(id, "再收");
        service.rollback(id, "收到头");
        TSecreDispFlow atHead = service.selectTSecreDispFlowById(id);
        assertEquals(0, atHead.getStage().intValue());
        SecreFlowException e = assertThrows(SecreFlowException.class, () -> service.rollback(id, "还收"));
        assertTrue(e.getMessage().contains("头一档"));
    }

    // ---------------- 第六条 ----------------

    @Test
    void rule6_oneUnfinishedPerCarrierAndTwoExits() {
        TSecreDispFlow a = service.add(newRecord("J050", "ML00"));
        // 前一条卡在半途，后一件起不来
        SecreFlowException e1 = assertThrows(SecreFlowException.class,
                () -> service.add(newRecord("J050", "ML00")));
        assertTrue(e1.getMessage().contains("没走到尾"));

        // 撤销只在头一档起来之前；撤掉的不留册面、不占格
        assertTrue(service.revoke(a.getId()));
        assertEquals(0, service.countUnfinished());
        assertTrue(service.selectTSecreDispFlowList(new QueryWrapper<>()).isEmpty(), "撤掉的不留进册面");
        TSecreDispFlow b = service.add(newRecord("J050", "ML00"));
        b = service.advance(b.getId(), "起", null, null, null);
        final Long bId = b.getId();
        assertThrows(SecreFlowException.class, () -> service.revoke(bId), "起来了就撤不了");

        // 中途叫停的也占着格
        service.halt(bId, "先停停");
        assertEquals(1, service.countUnfinished());
        assertThrows(SecreFlowException.class, () -> service.add(newRecord("J050", "ML00")));
        assertThrows(SecreFlowException.class, () -> service.advance(bId, "", null, null, null), "叫停的挪不动");

        // 作废只从末一档走
        assertThrows(SecreFlowException.class, () -> service.annul(bId, ""));
        TSecreDispFlow c = walk(service, service.add(newRecord("J051", "ML00")).getId(), 1);
        insertBill(c.getBizNo(), 1);
        c = walk(service, c.getId(), 2);
        insertHandRow(c.getCarrierNo(), 1);
        c = walk(service, c.getId(), 3);
        c = service.annul(c.getId(), "监销环节发现不符");
        assertEquals(2, c.getStatus().intValue(), "作废即封住");
        // 作废的尾收了，这件载体可以再起
        assertNotNull(service.add(newRecord("J051", "ML00")));
    }

    // ---------------- 第七条 ----------------

    @Test
    void rule7_twoEntrancesEachKeepTheirOwn() throws Exception {
        TSecreDispFlow r = walk(service, service.add(newRecord("J060", "ML00")).getId(), 1);
        final Long id = r.getId();
        int base = SecreFlowLayers.count(service.selectTSecreDispFlowById(id).getContent());

        // 两个入口同一瞬间各递一笔按语：各自记各自的先后，不并作一条、不吞掉一笔
        TSecreDispFlowServiceImpl s1 = newService(factory.openSession(true));
        TSecreDispFlowServiceImpl s2 = newService(factory.openSession(true));
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        Future<Boolean> f1 = pool.submit(() -> {
            ready.countDown();
            go.await(3, TimeUnit.SECONDS);
            return s1.updateContent(id, "甲口这一句");
        });
        Future<Boolean> f2 = pool.submit(() -> {
            ready.countDown();
            go.await(3, TimeUnit.SECONDS);
            return s2.updateContent(id, "乙口这一句");
        });
        ready.await(3, TimeUnit.SECONDS);
        go.countDown();
        assertTrue(f1.get(10, TimeUnit.SECONDS));
        assertTrue(f2.get(10, TimeUnit.SECONDS));
        pool.shutdownNow();

        session.clearCache();
        TSecreDispFlow after = service.selectTSecreDispFlowById(id);
        String content = after.getContent();
        assertTrue(content.contains("甲口这一句"), "甲口那笔得在");
        assertTrue(content.contains("乙口这一句"), "乙口那笔得在");
        assertEquals(base + 2, SecreFlowLayers.count(content), "两笔各占一层");
    }

    @Test
    void rule7_layerCountMatchesTheWalk() {
        // 从核件起到监销止走一遍，中途收一档再重走：段数与当日受理簿一笔一笔合得拢
        TSecreDispFlow r = service.add(newRecord("J061", "ML00"));           // 起行 1 层
        r = service.advance(r.getId(), "一", null, null, null);              // 推进 1 层
        r = service.advance(r.getId(), "二", null, null, null);              // 推进 1 层
        insertBill(r.getBizNo(), 1);
        service.updateContent(r.getId(), "定级按语");                        // 按语 1 层
        r = service.advance(r.getId(), "三", null, null, null);              // 推进 1 层
        r = service.rollback(r.getId(), "收");                               // 回退 1 层（清退推进层沉底）
        r = service.advance(r.getId(), "三重走", null, null, null);          // 推进 1 层
        insertHandRow(r.getCarrierNo(), 1);
        r = service.advance(r.getId(), "四", null, null, null);              // 推进 1 层
        r = service.advance(r.getId(), "当场", "岗甲", "岗乙", "PZ-7");      // 监销 1 层
        assertEquals(2, r.getStatus().intValue());
        // 受理簿：起行1＋推进5＋按语1＋回退1＋监销1＝9 笔；沉底的也算段
        assertEquals(9, SecreFlowLayers.count(r.getContent()));
        // 单拎一档出来也站得住：每层都带档次、时刻、动作
        for (String line : SecreFlowLayers.split(r.getContent())) {
            assertTrue(SecreFlowLayers.stageOf(line) >= 0, line);
            assertFalse(SecreFlowLayers.actionOf(line).isEmpty(), line);
        }
    }

    // ---------------- 闸门数据 ----------------

    private void insertBill(String billNo, int status) {
        TSecreLevelBill b = new TSecreLevelBill();
        b.setBillNo(billNo);
        b.setStatus(status);
        b.setDelFlag(0);
        billMapper.insert(b);
    }

    private void insertHandRow(String itemCode, int status) {
        TSecreHandRow row = new TSecreHandRow();
        row.setItemCode(itemCode);
        row.setStatus(status);
        row.setDelFlag(0);
        handMapper.insert(row);
    }
}
