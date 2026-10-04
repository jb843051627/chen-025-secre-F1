package com.fc.v2.secre;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

import com.fc.v2.mapper.auto.TSecreDispFlowMapper;
import com.fc.v2.model.auto.TSecreDispFlow;
import com.fc.v2.service.impl.TSecreDispFlowServiceImpl;

/**
 * 载体处置流转单状态机单测（对应值班条规第一至七条）。
 * 用 Mockito 假 mapper 顶一个内存册面，selectById 返回副本，模拟真实库往返。
 */
class TSecreDispFlowServiceImplTest {

    private static final int 未起 = 0;
    private static final int 在办 = 1;
    private static final int 已封住 = 2;

    private TSecreDispFlowServiceImpl service;
    private Map<Long, TSecreDispFlow> db;

    @BeforeEach
    void setUp() {
        db = new ConcurrentHashMap<Long, TSecreDispFlow>();
        TSecreDispFlowMapper mapper = mock(TSecreDispFlowMapper.class);
        when(mapper.selectById(any())).thenAnswer(inv -> {
            TSecreDispFlow r = db.get(inv.getArgument(0));
            return r == null ? null : copy(r);
        });
        when(mapper.updateById(any(TSecreDispFlow.class))).thenAnswer(inv -> {
            TSecreDispFlow r = inv.getArgument(0);
            db.put(r.getId(), copy(r));
            return 1;
        });
        when(mapper.deleteById(any())).thenAnswer(inv -> db.remove(inv.getArgument(0)) != null ? 1 : 0);
        when(mapper.selectList(Mockito.isNull())).thenAnswer(inv -> {
            List<TSecreDispFlow> all = new ArrayList<TSecreDispFlow>();
            for (TSecreDispFlow r : db.values()) {
                all.add(copy(r));
            }
            return all;
        });
        service = new TSecreDispFlowServiceImpl();
        ReflectionTestUtils.setField(service, "secreDispFlowMapper", mapper);
    }

    private static TSecreDispFlow copy(TSecreDispFlow r) {
        TSecreDispFlow c = new TSecreDispFlow();
        BeanUtils.copyProperties(r, c);
        return c;
    }

    private TSecreDispFlow seed(long id, Integer stage, Integer status, String content) {
        TSecreDispFlow r = new TSecreDispFlow();
        r.setId(id);
        r.setBizNo("LC-" + id);
        r.setStage(stage);
        r.setStatus(status);
        r.setContent(content);
        r.setDelFlag(0);
        db.put(id, r);
        return r;
    }

    private static long layerCount(String content) {
        if (content == null || content.trim().isEmpty()) {
            return 0;
        }
        return content.trim().split("\n").length;
    }

    /* 第一条/第二条：每回只挪紧邻的下一档，档位按系统数出来的写库 */
    @Test
    void advanceMovesExactlyOneStagePerCall() {
        seed(1L, 0, 在办, null);
        TSecreDispFlow r = service.advance(1L, "核件无误");
        assertEquals(1, r.getStage());
        assertEquals(在办, r.getStatus());
        r = service.advance(1L, "定级已签");
        assertEquals(2, r.getStage());
        r = service.advance(1L, "清退核收落可");
        assertEquals(3, r.getStage());
        assertEquals(3, db.get(1L).getStage());
    }

    /* 第三条/第四条：监销走满即收尾封住，化浆凭证码挂回这一行 */
    @Test
    void advanceSealsWhenLastStageCompletes() {
        seed(2L, 3, 在办, "【监销】2026-10-04 09:00:00｜到监销");
        TSecreDispFlow r = service.advance(2L, "两名见证落笔，化浆凭证码HP-2026-001");
        assertNotNull(r);
        assertEquals(已封住, r.getStatus());
        assertEquals(3, r.getStage());
        assertTrue(r.getContent().contains("【封】"));
        assertTrue(r.getContent().contains("HP-2026-001"));
        assertEquals("两名见证落笔，化浆凭证码HP-2026-001", r.getLastAction());
    }

    /* 第四条：封住的那一条只读得、动不得 */
    @Test
    void sealedRowIsReadOnly() {
        seed(3L, 3, 已封住, "【封】2026-10-04 09:00:00｜走满");
        assertNull(service.advance(3L, "再挪"));
        assertNull(service.rollback(3L, "回退"));
        assertFalse(service.updateContent(3L, "改话"));
        assertFalse(service.remove(3L));
        TSecreDispFlow r = db.get(3L);
        assertEquals(3, r.getStage());
        assertEquals(已封住, r.getStatus());
        assertEquals("【封】2026-10-04 09:00:00｜走满", r.getContent());
    }

    /* 第四条：封住的那一类在屏上照旧列得出来 */
    @Test
    void sealedRowsStillListed() {
        seed(4L, 1, 在办, null);
        seed(5L, 3, 已封住, "【封】2026-10-04 09:00:00｜走满");
        List<TSecreDispFlow> list = service.selectTSecreDispFlowList(null);
        assertEquals(2, list.size());
    }

    /* 第五条：往回一回只收一档；被收那一格的按语沉到末位 */
    @Test
    void rollbackTakesBackOneStageAndSinksNotes() {
        String content = "【核件】2026-10-04 09:00:00｜起\n"
                + "【定级】2026-10-04 09:05:00｜定机密\n"
                + "【清退】2026-10-04 09:10:00｜核收落可";
        seed(6L, 2, 在办, content);
        TSecreDispFlow r = service.rollback(6L, "清退单对不上，收回");
        assertNotNull(r);
        assertEquals(1, r.getStage());
        String[] lines = r.getContent().split("\n");
        assertEquals(4, lines.length);
        assertTrue(lines[0].startsWith("【核件】"));
        assertTrue(lines[1].startsWith("【定级】"));
        assertTrue(lines[2].startsWith("【回退·清退→定级】"));
        assertTrue(lines[2].contains("清退单对不上，收回"));
        // 早先落下的按语沉到末位，原话原刻都在
        assertTrue(lines[3].startsWith("【沉·清退】"));
        assertTrue(lines[3].contains("核收落可"));
        assertTrue(lines[3].contains("2026-10-04 09:10:00"));
    }

    /* 第五条：已停在头一档的，不再往回退 */
    @Test
    void rollbackRefusedAtFirstStage() {
        seed(7L, 0, 在办, "【核件】2026-10-04 09:00:00｜起");
        assertNull(service.rollback(7L, "退"));
        assertEquals(0, db.get(7L).getStage());
    }

    /* 第四条/第五条：未起的没有东西可收，已封住的退不动 */
    @Test
    void rollbackRefusedWhenPendingOrSealed() {
        seed(8L, 0, 未起, null);
        assertNull(service.rollback(8L, "退"));
        seed(9L, 2, 已封住, "【封】2026-10-04 09:00:00｜走满");
        assertNull(service.rollback(9L, "退"));
        assertEquals(2, db.get(9L).getStage());
    }

    /* 第五条：重走这一档从这一档重新攒起，沉底的旧按语仍在册 */
    @Test
    void rewalkAccumulatesAnewAfterRollback() {
        seed(10L, 2, 在办, "【核件】2026-10-04 09:00:00｜起\n【清退】2026-10-04 09:10:00｜旧话");
        service.rollback(10L, "收回");
        TSecreDispFlow r = service.advance(10L, "新话");
        assertEquals(2, r.getStage());
        assertTrue(r.getContent().contains("【沉·清退】"));
        assertTrue(r.getContent().contains("旧话"));
        String tail = r.getContent().substring(r.getContent().lastIndexOf('\n') + 1);
        assertTrue(tail.startsWith("【清退】"));
        assertTrue(tail.contains("新话"));
    }

    /* 第五条：同一档同一句话第二遍递上来，只留头一遍那句，不另叠一层 */
    @Test
    void duplicateSubmissionKeepsFirstNoteOnly() {
        seed(11L, 1, 在办, "【核件】2026-10-04 09:00:00｜起\n【清退】2026-10-04 09:10:00｜复查无误");
        String before = db.get(11L).getContent();
        TSecreDispFlow r = service.advance(11L, "复查无误");
        assertNotNull(r);
        assertEquals(2, r.getStage());
        assertEquals(before, r.getContent());
        assertEquals(2, layerCount(r.getContent()));
    }

    /* 第一条/第六条：未起的头一回递进来算「起行」，进头一档核件 */
    @Test
    void pendingRowStartsAtFirstStage() {
        seed(12L, 0, 未起, null);
        TSecreDispFlow r = service.advance(12L, "起行");
        assertNotNull(r);
        assertEquals(在办, r.getStatus());
        assertEquals(0, r.getStage());
        assertTrue(r.getContent().startsWith("【核件】"));
    }

    /* 第六条：撤销只发生在头一档起来之前那一小段，撤掉的不留册面 */
    @Test
    void removeWithdrawsPendingRow() {
        seed(13L, 0, 未起, null);
        assertTrue(service.remove(13L));
        assertNull(db.get(13L));
    }

    /* 第七条：每一档写过的话连同时刻一层层叠在这一行上，一层一行 */
    @Test
    void layersStackWithTimestamps() {
        seed(14L, 0, 在办, null);
        service.advance(14L, "一");
        service.advance(14L, "二");
        TSecreDispFlow r = service.advance(14L, "三");
        String[] lines = r.getContent().split("\n");
        assertEquals(3, lines.length);
        for (String line : lines) {
            assertTrue(line.matches("^【[^】]+】\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}｜.*$"),
                    "层记版式不对: " + line);
        }
        assertTrue(lines[0].contains("一"));
        assertTrue(lines[1].contains("二"));
        assertTrue(lines[2].contains("三"));
    }

    /* 第七条：两个入口同一瞬间各递一笔，各自记各自的先后，不并不吞 */
    @Test
    void concurrentSubmissionsBothRecorded() throws Exception {
        seed(15L, 0, 在办, null);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<TSecreDispFlow>> futures = new ArrayList<Future<TSecreDispFlow>>();
        for (final String note : new String[]{"甲口递", "乙口递"}) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                return service.advance(15L, note);
            }));
        }
        assertTrue(ready.await(5, TimeUnit.SECONDS));
        go.countDown();
        for (Future<TSecreDispFlow> f : futures) {
            assertNotNull(f.get(5, TimeUnit.SECONDS));
        }
        pool.shutdownNow();
        TSecreDispFlow r = db.get(15L);
        assertEquals(2, r.getStage());
        assertEquals(2, layerCount(r.getContent()));
        assertTrue(r.getContent().contains("甲口递"));
        assertTrue(r.getContent().contains("乙口递"));
    }

    /* 第七条+第四条：多口同递，走满即封，封住后再递不进来 */
    @Test
    void concurrentHammerSealsExactlyOnce() throws Exception {
        seed(16L, 0, 在办, null);
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<TSecreDispFlow>> futures = new ArrayList<Future<TSecreDispFlow>>();
        for (int i = 0; i < threads; i++) {
            final String note = "第" + i + "笔";
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                return service.advance(16L, note);
            }));
        }
        assertTrue(ready.await(5, TimeUnit.SECONDS));
        go.countDown();
        int ok = 0;
        for (Future<TSecreDispFlow> f : futures) {
            if (f.get(5, TimeUnit.SECONDS) != null) {
                ok++;
            }
        }
        pool.shutdownNow();
        TSecreDispFlow r = db.get(16L);
        assertEquals(4, ok);                 // 0→1→2→3→封，恰好四笔递得进来
        assertEquals(3, r.getStage());
        assertEquals(已封住, r.getStatus());
        assertEquals(4, layerCount(r.getContent()));
    }

    /* 全程：从核件起到监销止，未走完的随起行加一、随封行减一 */
    @Test
    void fullJourneyFromHeJianToJianXiao() {
        seed(17L, 0, 未起, null);
        TSecreDispFlow r = service.advance(17L, "起");            // 起行进核件
        assertEquals(在办, r.getStatus());
        assertEquals(0, r.getStage());
        r = service.advance(17L, "核件过");
        assertEquals(1, r.getStage());
        r = service.advance(17L, "定级过");
        assertEquals(2, r.getStage());
        r = service.advance(17L, "清退过");
        assertEquals(3, r.getStage());
        assertEquals(在办, r.getStatus());
        r = service.advance(17L, "见证落笔HP-001");               // 监销走满
        assertEquals(已封住, r.getStatus());
        assertEquals(3, r.getStage());
        assertNull(service.advance(17L, "再递"));                 // 封住后递不进来
        assertEquals(5, layerCount(db.get(17L).getContent()));    // 五笔五层，与受理簿点得拢
    }

    /* 改内容：在办的改得进来，已封的改不进来 */
    @Test
    void updateContentRefusedOnlyWhenSealed() {
        seed(18L, 1, 在办, "旧");
        assertTrue(service.updateContent(18L, "新"));
        assertEquals("新", db.get(18L).getContent());
        seed(19L, 3, 已封住, "【封】2026-10-04 09:00:00｜走满");
        assertFalse(service.updateContent(19L, "新"));
        assertEquals("【封】2026-10-04 09:00:00｜走满", db.get(19L).getContent());
    }

    /* 空号：查无此行一律递不动 */
    @Test
    void missingRowRefusedEverywhere() {
        assertNull(service.advance(99L, "x"));
        assertNull(service.rollback(99L, "x"));
        assertFalse(service.updateContent(99L, "x"));
        assertFalse(service.remove(99L));
    }
}
