package com.myname.roguelike;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 敌人预制池（0.2.5，0.3.1 起按章节区分）。
 * <p>
 * 废除原先「在 {@link RunState#createEnemies()} 里直接按层数写 if」的做法，
 * 把「哪个节点、哪一层该出什么怪」集中到这里，按章节、节点类型和层数抽取：
 * <ul>
 *   <li><b>沼之森</b>（史莱姆为主、骸骨为辅）：沿用 0.3.0 的敌人池。</li>
 *   <li><b>南边领地</b>（骑士为主、骸骨为辅）：骑士系敌人池（0.3.1 新增）。</li>
 * </ul>
 * <p>
 * <b>防混合规则（0.3.1）</b>：单场战斗生成时，抽到骑士系则本场不再出骸骨类（反之亦然），
 * 保持阵营纯粹。
 * <p>
 * 纯 Java，不 import libGDX，控制台自检能直接调。
 */
public class EnemyPool {

    private EnemyPool() {
        // 工具类，不实例化
    }

    /**
     * 按章节、节点类型和层数生成这一场的敌人。
     *
     * @param type    节点类型（BATTLE / ELITE / BOSS）
     * @param floor   层数（1 起）
     * @param rng     随机源（同一局复用 RunState 的 rng，保证可复现）
     * @param chapter 章节（决定敌人池）
     */
    public static List<Enemy> create(MapNode.Type type, int floor, Random rng, Chapter chapter) {
        List<Enemy> list = new ArrayList<>();
        switch (type) {
            case BATTLE -> fillBattle(list, floor, rng, chapter);
            case ELITE -> fillElite(list, floor, rng, chapter);
            case BOSS -> list.add(bossFor(chapter));
            default -> {
                // 非战斗节点不该走到这里
            }
        }
        return list;
    }

    /** 保留旧签名（默认沼之森），避免既有调用/测试直接传三参报错 */
    public static List<Enemy> create(MapNode.Type type, int floor, Random rng) {
        return create(type, floor, rng, Chapter.SWAMP);
    }

    /** 按章节返回首领：沼之森 -> 腐化史莱姆王，南边领地 -> 年轻的骑士长 */
    private static Enemy bossFor(Chapter chapter) {
        return chapter == Chapter.SOUTHERN_LAND
                ? new YoungKnightCaptain()
                : new SlimeKing(true);
    }

    private static void fillBattle(List<Enemy> list, int floor, Random rng, Chapter chapter) {
        if (chapter == Chapter.SOUTHERN_LAND) {
            fillSouthernBattle(list, floor, rng);
            return;
        }
        fillSwampBattle(list, floor, rng);
    }

    private static void fillElite(List<Enemy> list, int floor, Random rng, Chapter chapter) {
        if (chapter == Chapter.SOUTHERN_LAND) {
            fillSouthernElite(list, floor, rng);
            return;
        }
        fillSwampElite(list, floor, rng);
    }

    // ==================== 沼之森 ====================

    private static void fillSwampBattle(List<Enemy> list, int floor, Random rng) {
        if (floor <= 3) {
            list.add(new Slime());
        } else if (floor <= 6) {
            // 0.3.0：4~6 层五选一：强化史莱姆 / 骸骨战士 / 凋亡骸骨战士 / 侦查骑士 / 新手及时
            int roll = rng.nextInt(5);
            if (roll == 0) {
                list.add(new Slime("强化史莱姆", 34, 1, 1));
            } else if (roll == 1) {
                list.add(new BoneWarrior());
            } else if (roll == 2) {
                list.add(new WitherBoneWarrior());
            } else if (roll == 3) {
                list.add(new ScoutKnight());
            } else {
                list.add(new Novice());
            }
        } else {
            // 第 7 层：骸骨猎手 + 小史莱姆
            list.add(new BoneHunter(false));
            list.add(new Slime("小史莱姆", 18, 0, 0));
        }
    }

    private static void fillSwampElite(List<Enemy> list, int floor, Random rng) {
        if (floor <= 4) {
            list.add(new SlimeKing(false));
        } else {
            // 5~7 层三选一：骸骨猎手（强化）/ 史莱姆王 / 老骑士（0.3.0）
            int roll = rng.nextInt(3);
            if (roll == 0) {
                list.add(new BoneHunter(true));
            } else if (roll == 1) {
                list.add(new SlimeKing(false));
            } else {
                list.add(new OldKnight());
            }
        }
    }

    // ==================== 南边领地（0.3.1）====================

    private static void fillSouthernBattle(List<Enemy> list, int floor, Random rng) {
        if (floor <= 3) {
            list.add(new SquireKnight());
        } else if (floor <= 6) {
            // 4~6 层二选一（纯骑士，不出史莱姆/骸骨）：
            // 见习骑士 / 冲锋骑士
            int roll = rng.nextInt(2);
            if (roll == 0) {
                list.add(new SquireKnight());
            } else {
                list.add(new ChargingKnight());
            }
        } else {
            // 第 7 层：冲锋骑士 + 见习骑士（纯骑士）
            list.add(new ChargingKnight());
            list.add(new SquireKnight());
        }
    }

    private static void fillSouthernElite(List<Enemy> list, int floor, Random rng) {
        // 南边领地精英只出皇家守卫（纯骑士，不出骸骨）
        list.add(new RoyalGuard());
    }
}
