package com.myname.roguelike;

/**
 * 属性点分配的「临时池」状态机（0.2.1 可反悔 + 置灰确认）。
 * <p>
 * 纯 Java，不 import libGDX —— 这样 BattleTest 能在控制台里直接验证
 * 「加 / 减 / 反悔 / 置灰 / 确认」这套逻辑，而不用起窗口。
 * <p>
 * 它管的事只有一件：把一批待分配点数，通过「加一点 / 减一点」暂存在
 * 内存里，等玩家确认后一次性写进 {@link Player}。在确认之前，
 * {@link Player} 的真实面板**一个字都不动**。
 * <p>
 * 四种属性（下标 0~3）：0 力量 / 1 精准 / 2 防御 / 3 生命（1 点 = 上限 +5）。
 */
public class AttributeAllocator {

    /** 属性种类数 */
    public static final int ATTR_COUNT = 4;

    /** 每种属性每 1 点对应的面板增量（生命是 +5 上限，其余 +1） */
    private static final int[] PER_POINT = {1, 1, 1, 5};

    private final Player player;

    /** 本次待分配的总点数（进入分配界面时从 RunState 抄一份） */
    private final int total;

    /** 临时分配：pending[i] = 第 i 项暂存了几点（确认前不落盘） */
    private final int[] pending = new int[ATTR_COUNT];

    public AttributeAllocator(Player player, int totalPoints) {
        this.player = player;
        this.total = totalPoints;
    }

    /** 还剩几点没分配 = 总数 - 已暂存的点数 */
    public int remaining() {
        int used = 0;
        for (int p : pending) {
            used += p;
        }
        return Math.max(0, total - used);
    }

    /** 第 i 项暂存了几点（UI 显示「+N」用） */
    public int pendingAt(int i) {
        return pending[i];
    }

    /** 加 1 点到第 i 项。没有剩余点数时不生效 */
    public void add(int i) {
        if (i < 0 || i >= ATTR_COUNT || remaining() <= 0) {
            return;
        }
        pending[i]++;
    }

    /** 从第 i 项退 1 点（可反悔，下限 0） */
    public void sub(int i) {
        if (i < 0 || i >= ATTR_COUNT || pending[i] <= 0) {
            return;
        }
        pending[i]--;
    }

    /**
     * 能不能点「确认并继续」。
     * <p>
     * 0.2.1 置灰逻辑：剩余点数归 0 才允许确认。UI 据此把按钮画成暗灰色，
     * 并且剩余 &gt; 0 时忽略空格键。
     */
    public boolean canContinue() {
        return remaining() == 0;
    }

    /**
     * 把临时分配一次性写进 {@link Player} 真实面板。
     * <p>
     * 只有 {@link #canContinue()} 为 true 时才该被调用；这里仍然做防御性判断，
     * 万一调用方漏判，剩余点数不为 0 时直接拒绝，避免「点没花完却把面板改了」。
     *
     * @return 是否真的写入了（剩余点数归 0 才写，否则返回 false）
     */
    public boolean commit() {
        if (!canContinue()) {
            return false;
        }
        for (int i = 0; i < ATTR_COUNT; i++) {
            for (int j = 0; j < pending[i]; j++) {
                player.addAttributePoint(i);
            }
        }
        return true;
    }

    /** 每 1 点对应的面板增量，UI 显示「+N」预览用 */
    public static int perPoint(int i) {
        return PER_POINT[i];
    }
}
