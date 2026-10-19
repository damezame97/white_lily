package com.damezame97.white_lily.debugging;

import com.damezame97.white_lily.gamerule.happy_mode;
import com.damezame97.white_lily.white_lilycurio.improvement_loadout;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * 本模组的「世界级调试文件」：{@code <世界文件夹>/Debugging_whitelilymod.json}。
 *
 * <h2>它是什么</h2>
 * <p>每个世界一份，放在该世界的根目录（也就是 {@code level.dat} 旁边）。世界第一次启动时由本模组
 * <b>生成</b>，之后<b>只读</b>（除非文件坏了或被人删了，才会用当时的难度重新生成）。</p>
 *
 * <h2>文件内容</h2>
 * <pre>{@code
 * {
 *   "difficulty_level": 3,
 *   "difficulty_name": "normal",
 *   "slots": 3,
 *   "items": [
 *     "white_lily:invalid_placeholder",
 *     "white_lily:fate_compass",
 *     "white_lily:improve_body"
 *   ],
 *   "happy_mode": false
 * }
 * }</pre>
 *
 * <h2>哪个字段算数</h2>
 * <p><b>{@value #LEVEL_KEY} 是唯一权威参数</b>：1=和平、2=简单、3=普通、4=困难；
 * 「改良」饰品栏的格数就等于它，装备清单也按同一张表推导（见 {@link improvement_loadout}）。
 * 其余字段（{@code difficulty_name} / {@code slots} / {@code items} / {@value #HAPPY_MODE_KEY}）
 * 只是把这次实际的结果写下来给人看，<b>改它们不生效</b>，要换档就改 {@value #LEVEL_KEY}。</p>
 * <p>例外：{@value #HAPPY_MODE_KEY} 虽然不由这里决定，但游戏内 {@code /gamerule Happy_mode}
 * 的改动会被这里的值拉回去（见 {@link happy_mode}），所以它实际上是「锁」的另一半。</p>
 *
 * <h2>什么时候读写</h2>
 * <ul>
 *     <li>{@code ServerStartedEvent}：世界加载完就 {@link #loadOrCreateLevel}，所以「建世界」那一刻
 *         文件就已经躺在那儿了；</li>
 *     <li>玩家首次进世界要发饰品时，再 {@link #loadOrCreateLevel} 读一次——也就是说，
 *         建完图先把 {@value #LEVEL_KEY} 改成想要的档位，再进游戏，就会按改过的值发。</li>
 * </ul>
 * <p>「坏文件当作不存在」：文件缺失、不是合法 JSON、缺 {@value #LEVEL_KEY}、或者值不在 1~4，
 * 都会当成没有，用当时的难度重新生成一份（并打一条 WARN/INFO 日志）。</p>
 */
public final class debug_file
{
    /** 文件名。 */
    public static final String FILE_NAME = "Debugging_whitelilymod.json";

    /** 唯一权威参数：1=和平、2=简单、3=普通、4=困难。 */
    public static final String LEVEL_KEY = "difficulty_level";

    /** 难度名（{@code peaceful}/{@code easy}/{@code normal}/{@code hard}），只写不读。 */
    public static final String NAME_KEY = "difficulty_name";

    /** 该档位下的栏位数量，只写不读。 */
    public static final String SLOTS_KEY = "slots";

    /** 该档位下实际发放的饰品（物品 id 字符串数组），只写不读。 */
    public static final String ITEMS_KEY = "items";

    /**
     * 游戏规则 {@code Happy_mode} 的快照（{@code true}/{@code false}）。
     *
     * <p>写：世界第一次生成时按当时的游戏规则值写进去；老文件缺这一项会自动补上。
     * 读：游戏内 {@code /gamerule Happy_mode} 被改时，拿这里记的值把它拉回来
     * （见 {@link com.damezame97.white_lily.gamerule.happy_mode}）。</p>
     */
    public static final String HAPPY_MODE_KEY = "happy_mode";

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    private debug_file()
    {
    }

    /** 文件路径：世界根目录下的 {@value #FILE_NAME}。 */
    public static Path path(MinecraftServer server)
    {
        return server.getWorldPath(LevelResource.ROOT).resolve(FILE_NAME);
    }

    /**
     * 只读：把文件里的 {@value #LEVEL_KEY} 读出来。
     *
     * @return 文件不在 / 不是合法 JSON / 缺字段 / 值不在 1~4 时返回空
     */
    public static OptionalInt readLevel(MinecraftServer server)
    {
        Path path = path(server);
        if (!Files.isRegularFile(path))
        {
            return OptionalInt.empty();
        }

        try
        {
            JsonObject json = JsonParser
                    .parseString(Files.readString(path, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            if (!json.has(LEVEL_KEY))
            {
                LOGGER.warn("White Lily: {} 里没有 {}，当作没有这份文件", FILE_NAME, LEVEL_KEY);
                return OptionalInt.empty();
            }

            int level = json.get(LEVEL_KEY).getAsInt();
            if (!improvement_loadout.isValidLevel(level))
            {
                LOGGER.warn("White Lily: {} 的 {} = {} 不在 {}-{} 之内，当作没有这份文件",
                        FILE_NAME, LEVEL_KEY, level,
                        improvement_loadout.MIN_LEVEL, improvement_loadout.MAX_LEVEL);
                return OptionalInt.empty();
            }

            return OptionalInt.of(level);
        }
        catch (IOException | RuntimeException e)
        {
            LOGGER.warn("White Lily: 读不了 {}（{}），当作没有这份文件", FILE_NAME, e.toString());
            return OptionalInt.empty();
        }
    }

    /**
     * 读档位；文件不在或坏了就用<b>当时的世界难度</b>写一份新的，并返回它。
     *
     * <p>这个方法就是「以 json 内的参数为准」的落点：只要文件在且合法，返回值只由文件决定，
     * 跟当前难度无关。</p>
     */
    public static int loadOrCreateLevel(MinecraftServer server)
    {
        OptionalInt level = readLevel(server);
        if (level.isPresent())
        {
            // 老世界（这轮之前建的）那份 json 里可能还没有 happy_mode，补上
            ensureHappyMode(server);
            return level.getAsInt();
        }

        Difficulty difficulty = server.getWorldData().getDifficulty();
        int created = improvement_loadout.levelForDifficulty(difficulty);
        write(server, created);
        LOGGER.info("White Lily: 已按当前难度 {} 生成 {}（{} = {}）",
                difficulty, FILE_NAME, LEVEL_KEY, created);
        return created;
    }

    /** 读 {@value #HAPPY_MODE_KEY}；文件不在 / 坏 / 没这一项时返回空。 */
    public static Optional<Boolean> readHappyMode(MinecraftServer server)
    {
        Path path = path(server);
        if (!Files.isRegularFile(path))
        {
            return Optional.empty();
        }

        try
        {
            JsonObject json = JsonParser
                    .parseString(Files.readString(path, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            return json.has(HAPPY_MODE_KEY)
                    ? Optional.of(json.get(HAPPY_MODE_KEY).getAsBoolean())
                    : Optional.empty();
        }
        catch (IOException | RuntimeException e)
        {
            return Optional.empty();
        }
    }

    /**
     * 老文件里没有 {@value #HAPPY_MODE_KEY} 就用当前游戏规则值补上。
     *
     * <p>只在缺项时改文件，而且是「读出原 json → 只加/改这一项 → 写回」，
     * 手改过的其它字段不会被碰。</p>
     */
    private static void ensureHappyMode(MinecraftServer server)
    {
        Path path = path(server);
        if (!Files.isRegularFile(path) || readHappyMode(server).isPresent())
        {
            return;
        }

        try
        {
            JsonObject json = JsonParser
                    .parseString(Files.readString(path, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            json.addProperty(HAPPY_MODE_KEY, happy_mode.isEnabled(server));
            Files.writeString(path, GSON.toJson(json), StandardCharsets.UTF_8);
            LOGGER.info("White Lily: {} 里没有 {}，已按当前游戏规则补上（{} = {}）",
                    FILE_NAME, HAPPY_MODE_KEY, HAPPY_MODE_KEY, happy_mode.isEnabled(server));
        }
        catch (IOException | RuntimeException e)
        {
            LOGGER.warn("White Lily: 给 {} 补 {} 失败（{}）", FILE_NAME, HAPPY_MODE_KEY, e.toString());
        }
    }

    /** 按给定档位重新写一份文件（会覆盖；档位会先夹到 1~4，所以写出来的文件一定合法）。 */
    public static void write(MinecraftServer server, int level)
    {
        int clamped = improvement_loadout.clampLevel(level);

        JsonObject json = new JsonObject();
        json.addProperty(LEVEL_KEY, clamped);
        json.addProperty(NAME_KEY, improvement_loadout.difficultyForLevel(clamped).getKey());
        json.addProperty(SLOTS_KEY, improvement_loadout.slotsForLevel(clamped));

        JsonArray items = new JsonArray();
        for (Item item : improvement_loadout.loadoutForLevel(clamped))
        {
            items.add(String.valueOf(ForgeRegistries.ITEMS.getKey(item)));
        }
        json.add(ITEMS_KEY, items);

        // 游戏规则的当前值也记一份（游戏内改它会被拉回这里记的值）
        json.addProperty(HAPPY_MODE_KEY, happy_mode.isEnabled(server));

        try
        {
            Files.writeString(path(server), GSON.toJson(json), StandardCharsets.UTF_8);
        }
        catch (IOException e)
        {
            LOGGER.error("White Lily: 写不了 {}", path(server), e);
        }
    }
}
