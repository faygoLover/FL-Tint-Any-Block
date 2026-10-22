package faygolover.fltint.compat;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.UUID;

/**
 * Что делает WorldEdit с оттенком и защитой, когда ставит блок без них (//set, //replace, вставка неокрашенного):
 * replace (по умолчанию) — снимает; keep — оставляет как было. Блоки, несущие оттенок/защиту (//copy-//paste,
 * //undo, схематики), всегда ставят своё. Хранится в данных игрока, переживает перезаход.
 */
public final class WeMode {
    private static final String KEY = "fltint_we_keep";

    private WeMode() {}

    public static boolean keep(ServerPlayer player) {
        return player.getPersistentData().getBoolean(KEY);
    }

    public static boolean keep(UUID uuid) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        ServerPlayer player = server == null ? null : server.getPlayerList().getPlayer(uuid);
        return player != null && keep(player);
    }

    public static void setKeep(ServerPlayer player, boolean keep) {
        player.getPersistentData().putBoolean(KEY, keep);
    }
}
