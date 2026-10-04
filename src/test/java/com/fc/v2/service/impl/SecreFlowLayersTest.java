package com.fc.v2.service.impl;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 「一关一记」层叠底稿的走法校核（第五条、第七条）。
 *
 * @author fuce
 * @date 2026-10-03
 */
class SecreFlowLayersTest {

    private static final String T1 = "2026-10-03 09:00:00";
    private static final String T2 = "2026-10-03 10:00:00";
    private static final String T3 = "2026-10-03 11:00:00";

    @Test
    void layerRoundTrip() {
        String line = SecreFlowLayers.layer(2, T1, "推进", "核一遍");
        assertEquals("【档2】" + T1 + "｜推进｜核一遍\n", line);
        assertEquals(2, SecreFlowLayers.stageOf(line));
        assertEquals("推进", SecreFlowLayers.actionOf(line));
        assertEquals("核一遍", SecreFlowLayers.noteOf(line));
        assertFalse(SecreFlowLayers.isSunk(line));
    }

    @Test
    void noteFlattenedToOneLine() {
        String line = SecreFlowLayers.layer(1, T1, "按语", "头一句\n第二句");
        assertEquals(1, SecreFlowLayers.count(line));
        assertEquals("头一句 第二句", SecreFlowLayers.noteOf(line));
    }

    @Test
    void noteMayCarrySeparator() {
        String line = SecreFlowLayers.layer(0, T1, "按语", "甲｜乙");
        assertEquals("甲｜乙", SecreFlowLayers.noteOf(line));
        assertEquals("按语", SecreFlowLayers.actionOf(line));
    }

    @Test
    void appendLiveKeepsSunkAtBottom() {
        String content = SecreFlowLayers.layer(0, T1, "起行", "件码「J1」")
                + SecreFlowLayers.SUNK_MARK + "【档1】" + T2 + "｜推进｜旧层\n";
        String out = SecreFlowLayers.appendLive(content, SecreFlowLayers.layer(1, T3, "按语", "新层"));
        List<String> lines = SecreFlowLayers.split(out);
        assertEquals(3, lines.size());
        assertEquals("按语", SecreFlowLayers.actionOf(lines.get(1)));
        assertTrue(SecreFlowLayers.isSunk(lines.get(2)), "沉层始终压在末位");
    }

    @Test
    void sinkSendsCurrentStageLayersToTheEnd() {
        String content = SecreFlowLayers.layer(0, T1, "起行", "件码「J1」")
                + SecreFlowLayers.layer(1, T1, "推进", "进定级")
                + SecreFlowLayers.layer(1, T2, "按语", "定级这句")
                + SecreFlowLayers.layer(2, T2, "推进", "进清退")
                + SecreFlowLayers.layer(2, T3, "按语", "清退甲")
                + SecreFlowLayers.layer(2, T3, "按语", "清退乙");

        String[] parts = SecreFlowLayers.sink(content, 2);
        // 活层段只剩头一档、定级这一档
        List<String> live = SecreFlowLayers.split(parts[0]);
        assertEquals(3, live.size());
        assertEquals(0, SecreFlowLayers.stageOf(live.get(0)));
        assertEquals(1, SecreFlowLayers.stageOf(live.get(1)));
        assertEquals(1, SecreFlowLayers.stageOf(live.get(2)));
        // 被收那一档的三层冠【沉】沉到末位，先后不乱
        List<String> sunk = SecreFlowLayers.split(parts[1]);
        assertEquals(3, sunk.size());
        for (String s : sunk) {
            assertTrue(SecreFlowLayers.isSunk(s));
            assertEquals(2, SecreFlowLayers.stageOf(s));
        }
        assertEquals("清退甲", SecreFlowLayers.noteOf(sunk.get(1)));
        assertEquals("清退乙", SecreFlowLayers.noteOf(sunk.get(2)));

        // 回退层叠在活层段尾、沉层段前；段数一笔不少
        String rolled = parts[0] + SecreFlowLayers.layer(1, T3, "回退", "收一档") + parts[1];
        assertEquals(7, SecreFlowLayers.count(rolled));
        List<String> all = SecreFlowLayers.split(rolled);
        assertEquals("回退", SecreFlowLayers.actionOf(all.get(3)));
        assertTrue(SecreFlowLayers.isSunk(all.get(4)));
    }

    @Test
    void sinkTwiceKeepsEveryLayer() {
        // 走一遍：进清退 → 收回 → 重走清退 → 再收回；两回的清退层都沉底，段数合得上
        String content = SecreFlowLayers.layer(0, T1, "起行", "件码「J1」")
                + SecreFlowLayers.layer(1, T1, "推进", "进定级")
                + SecreFlowLayers.layer(2, T1, "推进", "头一遍进清退")
                + SecreFlowLayers.layer(2, T2, "按语", "头一遍那句");
        String[] first = SecreFlowLayers.sink(content, 2);
        String rolled = first[0] + SecreFlowLayers.layer(1, T2, "回退", "收") + first[1];
        // 重走这一档，从这一档重新攒起
        String walked = SecreFlowLayers.appendLive(rolled, SecreFlowLayers.layer(2, T3, "推进", "第二遍进清退"));
        walked = SecreFlowLayers.appendLive(walked, SecreFlowLayers.layer(2, T3, "按语", "第二遍那句"));
        String[] second = SecreFlowLayers.sink(walked, 2);
        String rolled2 = second[0] + SecreFlowLayers.layer(1, T3, "回退", "再收") + second[1];

        // 起行、进定级、两回回退是活层；两回的清退层都冠【沉】压在末位
        assertEquals(8, SecreFlowLayers.count(rolled2));
        List<String> lines = SecreFlowLayers.split(rolled2);
        assertEquals(4, lines.stream().filter(s -> !SecreFlowLayers.isSunk(s)).count());
        assertEquals(4, lines.stream().filter(SecreFlowLayers::isSunk).count());
        // 末四层是沉层，头一遍在前、第二遍在后
        List<String> sunk = lines.subList(4, 8);
        assertEquals("头一遍进清退", SecreFlowLayers.noteOf(sunk.get(0)));
        assertEquals("第二遍那句", SecreFlowLayers.noteOf(sunk.get(3)));
    }

    @Test
    void duplicateOnlyWhenSameDeliveryComesAgain() {
        String content = SecreFlowLayers.layer(1, T1, "推进", "进定级")
                + SecreFlowLayers.layer(1, T2, "按语", "这句");
        // 同一档第二遍递上来：同档、同动作、同一句 → 是回头递
        assertTrue(SecreFlowLayers.isDuplicate(content, 1, "按语", "这句"));
        // 句不同，是另一笔
        assertFalse(SecreFlowLayers.isDuplicate(content, 1, "按语", "另一句"));
        // 动作不同
        assertFalse(SecreFlowLayers.isDuplicate(content, 1, "推进", "这句"));
        // 档不同
        assertFalse(SecreFlowLayers.isDuplicate(content, 2, "按语", "这句"));
    }

    @Test
    void duplicateIgnoresSunkAndOlderLiveLayers() {
        // 同一句在沉层里：重走这一档重新攒起，不算回头递
        String content = SecreFlowLayers.layer(1, T1, "推进", "进定级")
                + SecreFlowLayers.SUNK_MARK + "【档2】" + T2 + "｜按语｜旧句\n";
        assertFalse(SecreFlowLayers.isDuplicate(content, 2, "按语", "旧句"));
        // 同一句在更早的活层、但不是末一层：中间隔了别笔，也不算回头递
        String content2 = SecreFlowLayers.layer(1, T1, "按语", "这句")
                + SecreFlowLayers.layer(1, T2, "按语", "别句");
        assertFalse(SecreFlowLayers.isDuplicate(content2, 1, "按语", "这句"));
        assertTrue(SecreFlowLayers.isDuplicate(content2, 1, "按语", "别句"));
    }

    @Test
    void countMatchesBookLineByLine() {
        String content = SecreFlowLayers.layer(0, T1, "起行", "件码「J1」")
                + SecreFlowLayers.layer(1, T1, "推进", "进定级")
                + SecreFlowLayers.layer(1, T2, "按语", "一句")
                + SecreFlowLayers.SUNK_MARK + "【档2】" + T3 + "｜推进｜沉的\n";
        assertEquals(4, SecreFlowLayers.count(content));
        assertEquals(0, SecreFlowLayers.count(null));
        assertEquals(0, SecreFlowLayers.count(""));
    }
}
