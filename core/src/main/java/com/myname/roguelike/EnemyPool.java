package com.myname.roguelike;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 敌人预制池（0.2.5）。
 * <p>
 * 废除原先「在 {@link RunState#createEnemies()} 里直接按层数写 if」的做法，
 * 把「哪个节点、哪一层该出什么怪」集中到这里，按节点类型和层数抽取：
 * <ul>
 *   <li>普通战斗：1~3 层史莱姆；4~6 层 50% 强化史莱姆 / 50% 骸骨战士；7 层骸骨猎手 + 小史莱姆</li>
 *   <li>精英战斗：1~4 层史莱姆王；5~7 层 50% 骸骨猎手（强化）/ 50% 史莱姆王</li>
 *   <li>Boss：第 8 层固定腐化史莱姆王</li>
 * </ul>
 * 纯 Java，不 import libGDX，控制台自检能直接调。
 */
public class EnemyPool {

    private EnemyPool() {
        // 工具类，不实例化
    }

    /**
     * 按节点类型和层数生成这一场的敌人。
     *
     * @param type  节点类型（BATTLE / ELITE / BOSS）
     * @param floor 层数（1 起）
     * @param rng   随机源（同一局复用 RunState 的 rng，保证可复现）
     */
    public static List<Enemy> create(MapNode.Type type, int floor, Random rng) {
        List<Enemy> list = new ArrayList<>();
        switch (type) {
            case BATTLE -> fillBattle(list, floor, rng);
            case ELITE -> fillElite(list, floor, rng);
            case BOSS -> list.add(new SlimeKing(true));
            default -> {
                // 非战斗节点不该走到这里
            }
        }
        return list;
    }

    private static void fillBattle(List<Enemy> list, int floor, Random rng) {
        if (floor <= 3) {
            list.add(new Slime());
        } else if (floor <= 6) {
            // 0.2.8：4~6 层三选一：强化史莱姆 / 骸骨战士 / 凋亡骸骨战士
            int roll = rng.nextInt(3);
            if (roll == 0) {
                list.add(new Slime("强化史莱姆", 34, 1, 1));
            } else if (roll == 1) {
                list.add(new BoneWarrior());
            } else {
                list.add(new WitherBoneWarrior());
            }
        } else {
            // 第 7 层：骸骨猎手 + 小史莱姆
            list.add(new BoneHunter(false));
            list.add(new Slime("小史莱姆", 18, 0, 0));
        }
    }

    private static void fillElite(List<Enemy> list, int floor, Random rng) {
        if (floor <= 4) {
            list.add(new SlimeKing(false));
        } else if (rng.nextBoolean()) {
            list.add(new BoneHunter(true));
        } else {
            list.add(new SlimeKing(false));
        }
    }
}
