package com.myname.roguelike;

/**
 * 章节（0.3.1 起）。
 * <p>
 * 「多章节」系统的底层枚举：每个章节有独立的敌人池、主题和首领。
 * 敌人池的按章节抽取逻辑在 {@link EnemyPool}，章节状态挂在 {@link RunState#currentChapter}。
 * <p>
 * 纯 Java，不 import libGDX。
 */
public enum Chapter {
    /** 沼之森：史莱姆为主、骸骨为辅（0.3.0 的原有敌人池） */
    SWAMP("沼之森"),
    /** 南边领地：骑士为主（0.3.1 新增） */
    SOUTHERN_LAND("南边领地");

    /** 章节显示名 */
    public final String name;

    Chapter(String name) {
        this.name = name;
    }
}
