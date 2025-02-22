package io.github.Alathra.AlathranCommands.runnable;

import io.github.Alathra.AlathranCommands.AlathranCommands;
import io.github.Alathra.AlathranCommands.data.model.TPARequest;
import io.github.Alathra.AlathranCommands.events.TeleportGraceEvent;
import com.github.milkdrinkers.colorparser.ColorParser;
import io.github.Alathra.AlathranCommands.config.TeleportConfigHandler;
import io.github.Alathra.AlathranCommands.utils.TPCfg;
import io.github.Alathra.AlathranCommands.utils.TeleportMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

/**
 * Start a runnable that executes teleport request at the end of teleport grace period
 */
public class TeleportGraceTask implements Runnable {
    private final static boolean canMove = TPCfg.get().getBoolean("Settings.TPA.Grace.Allow-movement");
    private final TPARequest request;
    private final Location teleporterLocation; // Original location of teleporter, used to determine wether they moved
    private final CompletableFuture<Boolean> future;
    private final long timeStarted = System.currentTimeMillis();
    private final long timeGrace;
    private int taskId;

    public TeleportGraceTask(final TPARequest request, final long timeGrace, final CompletableFuture<Boolean> future) {
        this.request = request;
        this.future = future;
        this.teleporterLocation = request.getOrigin().getLocation();
        this.timeGrace = timeGrace;

        final TeleportGraceEvent e = new TeleportGraceEvent();
        Bukkit.getServer().getPluginManager().callEvent(e);
        if (e.isCancelled()) {
            future.complete(false);
        }

        TeleportMessage.requestTeleporting(this.timeGrace, this.request);

        this.taskId = Bukkit.getServer().getScheduler().runTaskTimerAsynchronously(AlathranCommands.getInstance(), this, 0, 10).getTaskId();
    }

    @Override
    public void run() {
        // Check is origin online and has location
        if (!request.getOrigin().isOnline()) {
            switch (request.getType()) {
                case TPA, TPAHERE -> request.getTarget().sendMessage(ColorParser.of(TPCfg.get().getString("Messages.error-target-cancel-originnotonline")).build());
            }
            cancelTask();
            return;
        }

        // Is target online and has location
        if (!request.getTarget().isOnline()) {
            switch (request.getType()) {
                case TPA, TPAHERE -> request.getOrigin().sendMessage(ColorParser.of(TPCfg.get().getString("Messages.error-origin-cancel-targetnotonline")).build());
            }
            cancelTask();
            return;
        }

        // Cancel if target moves
        final @Nullable Location teleporter = switch (request.getType()) {
            case TPA -> request.getOrigin().getLocation();
            case TPAHERE -> request.getTarget().getLocation();
            default -> null;
        };

        if (!canMove && teleporter != null && teleporterLocation.distance(teleporter) > 0.3) {
            TeleportMessage.requestTeleportCancel(this.request);
            cancelTask();
            return;
        }

        // Check if we should teleport
        final long timeCurrent = System.currentTimeMillis();
        if (timeCurrent > timeStarted + timeGrace) {
            cancelTask();

            Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(AlathranCommands.getInstance(), new TeleportTask(request, future));
        }
    }

    void cancelTask() {
        if (this.taskId == -1) return;

        try {
            Bukkit.getServer().getScheduler().cancelTask(this.taskId);
            future.complete(false);
        } finally {
            this.taskId = -1;
        }
    }
}
