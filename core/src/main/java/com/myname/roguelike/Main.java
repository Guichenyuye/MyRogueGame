package com.myname.roguelike;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;

import java.util.HashMap;
import java.util.Map;

/**
 * 游戏入口：只负责「准备共享资源」+「决定第一个界面」。
 * <p>
 * 改成继承 {@link Game} 是为了拿到 {@code setScreen()} —— 每个界面实现 {@link com.badlogic.gdx.Screen}，
 * 相互切换时不用再手写状态机。
 * <p>
 * 共享资源（SpriteBatch / 字体）都放在这里，各 Screen 只借用不释放，
 * 这样切界面时不会把字体一起 dispose 掉。
 */
public class Main extends Game {

    public SpriteBatch batch;
    public BitmapFont font;

    /** incremental 模式必须保活：新字形是运行时按需生成的 */
    private FreeTypeFontGenerator generator;

    /**
     * 角色头像纹理，按 {@link CharacterType} 索引。0.2.7 起由 Main 统一加载并管理生命周期，
     * 各 Screen 只借用不释放。8x8 像素图必须 Nearest 过滤放大，否则边缘糊成一团。
     */
    public final Map<CharacterType, Texture> avatars = new HashMap<>();

    /**
     * 角色背景纹理，按 {@link CharacterType} 索引。1920x1080，角色选择界面用 Cover 模式铺满。
     */
    public final Map<CharacterType, Texture> backgrounds = new HashMap<>();

    /** 1x1 白图，共享给各 Screen 画矩形 / 边框（避免每个 Screen 各造一张） */
    private Texture white;

    @Override
    public void create() {
        batch = new SpriteBatch();

        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(Color.WHITE);
        pixmap.fill();
        white = new Texture(pixmap);
        pixmap.dispose();

        generator = new FreeTypeFontGenerator(Gdx.files.internal("Minecraft.ttf"));
        FreeTypeFontGenerator.FreeTypeFontParameter parameter = new FreeTypeFontGenerator.FreeTypeFontParameter();
        // 20px 是界面正文的实际字号：像素字体只在「整数倍/原生尺寸」下才清晰，
        // 用 32px 再缩到 0.55 画出来笔画会发虚（血条上的中文就会糊）。
        parameter.size = 20;
        parameter.color = Color.WHITE;

        // 像素字体防模糊
        parameter.minFilter = Texture.TextureFilter.Nearest;
        parameter.magFilter = Texture.TextureFilter.Nearest;

        // 【关键】默认只会生成 parameter.characters（ASCII）里的字形，
        // 中文会画成空白。incremental = true 改成「用到哪个字就现场生成哪个字」。
        parameter.incremental = true;

        font = generator.generateFont(parameter);

        loadCharacterTextures();

        setScreen(new StartScreen(this));
    }

    /**
     * 加载所有角色的头像和背景纹理。
     * <p>
     * 遍历 {@link CharacterData#ALL}，不硬编码路径，以后加新角色只需在 CharacterData 里补数据。
     * 头像统一 Nearest 过滤（防 8x8 放大模糊）；背景是普通照片式大图，用默认线性过滤即可。
     * <p>
     * 0.2.8 降临者：头像用代码生成的问号图（见 {@link #generateQuestionAvatar()}），
     * 背景复用归尘的（降临者还没专属立绘）。
     */
    private void loadCharacterTextures() {
        for (CharacterData data : CharacterData.ALL) {
            Texture avatar = new Texture(Gdx.files.internal(data.avatarPath));
            avatar.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
            avatars.put(data.type, avatar);

            Texture background = new Texture(Gdx.files.internal(data.backgroundPath));
            backgrounds.put(data.type, background);
        }

        // 降临者：代码生成问号头像 + 复用归尘背景
        Texture questionAvatar = generateQuestionAvatar();
        avatars.put(CharacterType.DESCENDER, questionAvatar);
        backgrounds.put(CharacterType.DESCENDER, backgrounds.get(CharacterType.GUICHEN));
    }

    /**
     * 代码生成一个 8x8 的问号头像（0.2.8 降临者默认头像）。
     * <p>
     * 8x8 太小，问号画不出来，用「外圈实心边框 + 中间一个点」的像素图案代替，
     * 语义上表达「未定型/自定义」。Nearest 放大后就是一块像素风图标。
     */
    private Texture generateQuestionAvatar() {
        Pixmap pixmap = new Pixmap(8, 8, Pixmap.Format.RGBA8888);
        // 全透明底
        pixmap.setColor(0, 0, 0, 0);
        pixmap.fill();
        // 外圈 1px 边框（浅灰）
        pixmap.setColor(0.85f, 0.85f, 0.88f, 1f);
        pixmap.drawRectangle(0, 0, 8, 8);
        // 中间一个 2x2 的实心点
        pixmap.fillRectangle(3, 3, 2, 2);
        Texture texture = new Texture(pixmap);
        texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        pixmap.dispose();
        return texture;
    }

    /** 按角色类型取头像（找不到返回 null，调用方需兜底） */
    public Texture avatar(CharacterType type) {
        return avatars.get(type);
    }

    /**
     * 覆盖某个角色的头像纹理（0.2.8 降临者上传自定义头像用）。
     * <p>
     * 新纹理直接替换 map 里的旧值，后续所有通过 {@link #avatar} 取头像的界面（战斗/地图）
     * 都会用到新头像。旧纹理不在此处 dispose（避免别处仍持有引用时悬空），交给游戏退出时统一释放。
     */
    public void setAvatar(CharacterType type, Texture avatar) {
        if (avatar != null) {
            avatars.put(type, avatar);
        }
    }

    /** 按角色类型取背景（找不到返回 null，调用方需兜底） */
    public Texture background(CharacterType type) {
        return backgrounds.get(type);
    }

    /**
     * 存档管理器（0.3.1 预留骨架）。
     * <p>
     * 第一版暂不做本地文件读写，只把类结构搭好，将来加存档时在这里填实现。
     * 预期做法：把 {@link RunState}（唯一持有整局状态的类）序列化成 JSON / 二进制
     * 存到本地文件，读档时反序列化重建一局。
     */
    public static class SaveManager {
        /** 存档文件路径（将来接本地 IO 时用） */
        public static final String SAVE_FILE = "save.json";

        /** 是否已启用存档（0.3.1 恒 false，接 IO 时置 true） */
        public static boolean enabled() {
            return false;
        }

        /** 保存当前一局（占位，未实现） */
        public static void save(RunState run) {
            // TODO 0.3.x：序列化 run 到本地文件
        }

        /** 读档（占位，未实现，返回 null） */
        public static RunState load() {
            // TODO 0.3.x：从本地文件反序列化
            return null;
        }
    }

    /** 共享 1x1 白图，画矩形 / 边框用（不释放） */
    public Texture whiteTexture() {
        return white;
    }

    @Override
    public void dispose() {
        // Game.dispose() 只会 hide 当前 Screen，不会 dispose，这里手动来一次
        if (getScreen() != null) {
            getScreen().dispose();
        }
        if (font != null) {
            font.dispose();
        }
        if (batch != null) {
            batch.dispose();
        }
        if (generator != null) {
            generator.dispose();
        }
        for (Texture t : avatars.values()) {
            t.dispose();
        }
        for (Texture t : backgrounds.values()) {
            t.dispose();
        }
        if (white != null) {
            white.dispose();
        }
    }
}
