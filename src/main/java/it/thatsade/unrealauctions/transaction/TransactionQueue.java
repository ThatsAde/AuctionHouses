package it.thatsade.unrealauctions.transaction;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Logger;

public class TransactionQueue {

    private static final Logger log = Logger.getLogger("UnrealAuctions");

    private record QueueEntry(Transaction tx, Consumer<TransactionResult> callback) {}

    /** How long to wait between real connection probes while the DB is down. */
    private static final long PROBE_INTERVAL_MS = 5_000;

    private final LinkedBlockingQueue<QueueEntry> queue;
    private final ExecutorService executor;
    private final Plugin plugin;
    private final int maxDepth;
    private volatile boolean running = true;

    /** Circuit-breaker state (written only by the worker thread). */
    private volatile boolean databaseDown = false;
    private long nextProbeAt = 0;

    public TransactionQueue(Plugin plugin, int maxDepth) {
        this.plugin = plugin;
        this.maxDepth = maxDepth;
        this.queue = new LinkedBlockingQueue<>();
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "UnrealAuctions-TxWorker");
            t.setDaemon(true);
            return t;
        });
        executor.submit(this::workerLoop);
    }

    /**
     * Submit a transaction. Returns false if the queue is full.
     * Callback is always invoked on the main thread.
     */
    public boolean submit(Transaction tx, Consumer<TransactionResult> callback) {
        if (!running) return false;
        if (queue.size() >= maxDepth) {
            log.warning("Transaction queue is full (depth=" + maxDepth + "). Rejecting transaction.");
            return false;
        }
        queue.offer(new QueueEntry(tx, callback));
        return true;
    }

    public int getDepth() {
        return queue.size();
    }

    /** True while the database is detected as unreachable (circuit breaker open). */
    public boolean isDatabaseDown() {
        return databaseDown;
    }

    public void shutdown() {
        running = false;
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                log.warning("Transaction queue did not drain in 10s. " + queue.size() + " transaction(s) lost.");
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private void workerLoop() {
        while (running || !queue.isEmpty()) {
            try {
                QueueEntry entry = queue.poll(500, TimeUnit.MILLISECONDS);
                if (entry == null) continue;

                // While the DB is down, fail queued transactions instantly instead
                // of blocking on a connection attempt — except for one periodic probe
                // that detects recovery.
                if (databaseDown && System.currentTimeMillis() < nextProbeAt) {
                    dispatch(entry, TransactionResult.connectionFailure());
                    continue;
                }

                TransactionResult result = entry.tx().execute();
                updateCircuitBreaker(result);
                dispatch(entry, result);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.severe("Unexpected error in transaction worker: " + e.getMessage());
            }
        }
    }

    /** Opens/closes the circuit breaker based on a transaction's outcome, logging once per transition. */
    private void updateCircuitBreaker(TransactionResult result) {
        if (result.isConnectionFailure()) {
            if (!databaseDown) {
                databaseDown = true;
                log.warning("Database is unavailable — auction operations are paused and will "
                        + "resume automatically once the connection is restored. "
                        + "Player actions will fail until then.");
            }
            nextProbeAt = System.currentTimeMillis() + PROBE_INTERVAL_MS;
        } else if (databaseDown) {
            databaseDown = false;
            log.info("Database connection restored — auction operations resumed.");
        }
    }

    /** Runs the callback on the main thread with the given result. */
    private void dispatch(QueueEntry entry, TransactionResult result) {
        Bukkit.getScheduler().runTask(plugin, () -> entry.callback().accept(result));
    }
}
