package com.myname.roguelike;

/**
 * 地图上的一个节点（文档《节点地图设计》第 2 节）。
 * <p>
 * 纯数据，不 import libGDX —— 生成逻辑和界面逻辑各管各的。
 */
public class MapNode {

    /**
     * 节点类型。
     * <p>
     * 0.2.2 起 EVENT 参与生成了（事件系统）；SHOP 这一版**仍不参与生成**，
     * 枚举先留着，等金币消费循环（0.3.0）做出来直接打开权重表就行。
     * <p>
     * 只有 {@link #label} 会画在地图框里。之前还有一个 {@code hint} 副标题，
     * 但框内两行字必须缩到 0.85 才放得下，像素字体在非整数缩放下会渗色、
     * 第二行被一道横线穿过，而且「战斗 / 史莱姆」这种副标题本来也不提供信息，索性删了。
     */
    public enum Type {
        BATTLE("战斗", true),
        ELITE("精英", true),
        BOSS("首领", true),
        REST("篝火", false),
        TREASURE("宝箱", false),
        EVENT("事件", false),
        SHOP("商店", false);

        /** 框里那一行字 */
        public final String label;
        /** 要不要切到战斗界面 */
        public final boolean combat;

        Type(String label, boolean combat) {
            this.label = label;
            this.combat = combat;
        }
    }

    public final Type type;
    /** 第几层，1 起 */
    public final int floor;
    /** 本层第几个，0 起，用于布局和数字键 */
    public final int index;

    public boolean visited = false;

    public MapNode(Type type, int floor, int index) {
        this.type = type;
        this.floor = floor;
        this.index = index;
    }

    @Override
    public String toString() {
        return "L" + floor + "-" + index + " " + type.name();
    }
}
