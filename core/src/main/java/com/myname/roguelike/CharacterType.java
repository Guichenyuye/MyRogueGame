package com.myname.roguelike;

/**
 * 角色类型枚举（0.2.4 预留）。
 * <p>
 * 目前只有「归尘」一个角色。将来加新角色时：在枚举里加一项，再在
 * {@link CharacterData} 里补一条数据，并让 {@link Player} 按角色读取不同的
 * 初始面板 / 初始天赋 / 专属技巧池即可，UI（{@code CharacterSelectScreen}）不用改。
 * <p>
 * 纯 Java，不 import libGDX。
 */
public enum CharacterType {
    /** 归尘：以血为引、以伤换伤的狂战士 */
    GUICHEN,
    /** 空梦：镜像与倒影的舞者（0.2.5 测试角色） */
    KONGMENG,
    /** 降临者：自定义捏脸角色（0.2.8），初始属性由玩家分配 */
    DESCENDER,
    /** 墓渊：驱使亡灵的召唤师（0.3.0） */
    MUYUAN
}
