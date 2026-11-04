package com.myname.roguelike;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 一章地图的生成与查询（文档《节点地图设计》第 1、3 节）。
 * <p>
 * 固定 8 层：第 1 层教学战斗，第 2~7 层分支，第 8 层首领。
 * 生成规则全部集中在 {@link #generate(Random)}，改平衡只用动这一个方法。
 */
public class GameMap {

    public static final int FLOORS = 8;

    /** 第 2~7 层出 3 个分支的概率，其余出 2 个 */
    private static final double THREE_WAY_CHANCE = 0.55;

    /**
     * 第 2~7 层的类型权重（五项之和 = 100）。
     * <p>
     * 0.2.2 重新分配：普通战斗 38 / 精英 20 / 事件 15 / 篝火 15 / 宝箱 12。
     * 注意实际占比会比这里的数字**更偏战斗**：同层不允许两个篝火/宝箱/精英/事件，
     * 抽到重复就重抽，而重抽的池子里 BATTLE 最大，等于变相加权。
     * battleTest 里有 1000 局的实测分布，改完记得跑一眼。
     */
    private static final int W_BATTLE = 38;
    private static final int W_REST = 15;
    private static final int W_TREASURE = 12;
    private static final int W_ELITE = 20;
    private static final int W_EVENT = 15;

    /** rows.get(i) = 第 i+1 层的所有节点 */
    private final List<List<MapNode>> rows;

    private GameMap(List<List<MapNode>> rows) {
        this.rows = rows;
    }

    // ==================== 生成 ====================

    public static GameMap generate(Random rng) {
        List<List<MapNode>> rows = new ArrayList<>(FLOORS);

        // 第 1 层：固定一场普通战斗，没有选择
        rows.add(single(1, MapNode.Type.BATTLE));

        // 第 2 ~ FLOORS-1 层：分支
        for (int floor = 2; floor <= FLOORS - 1; floor++) {
            int count = rng.nextDouble() < THREE_WAY_CHANCE ? 3 : 2;
            rows.add(rollFloor(rng, floor, count));
        }

        // 最后一层：首领
        rows.add(single(FLOORS, MapNode.Type.BOSS));

        return new GameMap(rows);
    }

    private static List<MapNode> single(int floor, MapNode.Type type) {
        List<MapNode> list = new ArrayList<>(1);
        list.add(new MapNode(type, floor, 0));
        return list;
    }

    /**
     * 生成一层分支。
     * <p>
     * 约束（设计文档第 3 节）：
     * 同层最多 1 个篝火 / 宝箱 / 精英；精英不出现在 1~3 层；篝火不出现在 1~2 层；
     * 每层至少有 1 个战斗类节点。
     */
    private static List<MapNode> rollFloor(Random rng, int floor, int count) {
        MapNode.Type[] picked = new MapNode.Type[count];
        boolean hasRest = false;
        boolean hasTreasure = false;
        boolean hasElite = false;
        boolean hasEvent = false;

        for (int i = 0; i < count; i++) {
            MapNode.Type chosen = null;

            // 抽到不合法的就重抽；20 次还不行就兜底普战，避免死循环
            for (int attempt = 0; attempt < 20; attempt++) {
                MapNode.Type candidate = rollType(rng, floor);
                if (candidate == MapNode.Type.REST && hasRest) continue;
                if (candidate == MapNode.Type.TREASURE && hasTreasure) continue;
                if (candidate == MapNode.Type.ELITE && hasElite) continue;
                if (candidate == MapNode.Type.EVENT && hasEvent) continue;
                chosen = candidate;
                break;
            }
            if (chosen == null) {
                chosen = MapNode.Type.BATTLE;
            }

            if (chosen == MapNode.Type.REST) hasRest = true;
            if (chosen == MapNode.Type.TREASURE) hasTreasure = true;
            if (chosen == MapNode.Type.ELITE) hasElite = true;
            if (chosen == MapNode.Type.EVENT) hasEvent = true;
            picked[i] = chosen;
        }

        // 每层至少一个战斗类，否则玩家可以一路白嫖，整局节奏会塌
        boolean hasCombat = false;
        for (MapNode.Type t : picked) {
            if (t == MapNode.Type.BATTLE || t == MapNode.Type.ELITE) {
                hasCombat = true;
                break;
            }
        }
        if (!hasCombat) {
            picked[0] = MapNode.Type.BATTLE;
        }

        List<MapNode> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            list.add(new MapNode(picked[i], floor, i));
        }
        return list;
    }

    /** 按权重抽一个类型；不满足楼层条件的权重直接置 0 */
    private static MapNode.Type rollType(Random rng, int floor) {
        int wRest = floor >= 3 ? W_REST : 0;
        int wTreasure = floor >= 2 ? W_TREASURE : 0;
        int wElite = floor >= 4 ? W_ELITE : 0;
        int wEvent = floor >= 2 ? W_EVENT : 0;

        int total = W_BATTLE + wRest + wTreasure + wElite + wEvent;
        int roll = rng.nextInt(total);

        if ((roll -= W_BATTLE) < 0) return MapNode.Type.BATTLE;
        if ((roll -= wRest) < 0) return MapNode.Type.REST;
        if ((roll -= wTreasure) < 0) return MapNode.Type.TREASURE;
        if ((roll -= wElite) < 0) return MapNode.Type.ELITE;
        return MapNode.Type.EVENT;
    }

    // ==================== 查询 ====================

    public int floorCount() {
        return rows.size();
    }

    /** floor 从 1 开始 */
    public List<MapNode> nodesOn(int floor) {
        return rows.get(floor - 1);
    }

    // ==================== 调试 ====================

    /** 控制台用的地图预览，battleTest 里会打印一张看生成结果 */
    public String toAsciiArt() {
        StringBuilder sb = new StringBuilder();
        for (int floor = FLOORS; floor >= 1; floor--) {
            sb.append("L").append(floor).append("  ");
            for (MapNode n : nodesOn(floor)) {
                sb.append("[").append(n.type.label).append("]  ");
            }
            sb.append('\n');
        }
        return sb.toString();
    }
}
