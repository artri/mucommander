package com.mucommander.utils;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ExecutorManager class.
 *
 */
public class ExecutorManager {
	private static final Logger LOGGER = LoggerFactory.getLogger(ExecutorManager.class);
	private static final int CORE_POOL_SIZE = 8;
	private static final long MIN_SLEEP_MILLIS = 5;
	private static final long KEEP_ALIVE_SECONDS = 30;
	
	private static ExecutorManager INSTANCE;
	private final ScheduledThreadPoolExecutor scheduledExecutor;
	
	public static void init() {
		LOGGER.debug("init");
		getInstance();
	}
	
	public static void destroy() {
		LOGGER.debug("destroy");
		shutdownAndAwaitTermination(getInstance().getScheduledExecutor());
		INSTANCE = null;
	}
	
	private static ExecutorManager getInstance() {
		if (null == INSTANCE) {
			synchronized(ExecutorManager.class) {
				if (null == INSTANCE) {
					INSTANCE = new ExecutorManager();
				}
			}
		}
		return INSTANCE;
	}
	
	private ExecutorManager() {
		this.scheduledExecutor = new ScheduledThreadPoolExecutor(CORE_POOL_SIZE);
		this.scheduledExecutor.setKeepAliveTime(KEEP_ALIVE_SECONDS, TimeUnit.SECONDS);
		this.scheduledExecutor.allowCoreThreadTimeOut(true);
		this.scheduledExecutor.setRemoveOnCancelPolicy(true);
		this.scheduledExecutor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
		this.scheduledExecutor.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
		this.scheduledExecutor.prestartCoreThread();
	}	
	
	public ScheduledThreadPoolExecutor getScheduledExecutor() {
		return scheduledExecutor;
	}

	public static void execute(Runnable command) {
		getInstance().getScheduledExecutor().execute(command);
	}
	
	public static Future<?> submit(Runnable command) {
		return getInstance().getScheduledExecutor().submit(command);
	}
	
	public static ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
		return getInstance().getScheduledExecutor().schedule(command, delay, unit);
	}

	public static ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
		return getInstance().getScheduledExecutor().scheduleAtFixedRate(command, initialDelay, period, unit);
	}

	public static ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
		return getInstance().getScheduledExecutor().scheduleWithFixedDelay(command, initialDelay, delay, unit);
	}

	/**
	 * Cause given <b>executor</b> to terminate
	 * @param executor - the Executor to be terminated
	 * @return <b>true</b> if terminated successfully, <b>false</b> otherwise.
	 */
	public static boolean shutdownAndAwaitTermination(ExecutorService executor) {
		boolean terminated = true;
		// make sure no new tasks are submitted
		executor.shutdown(); 
		try {
			// wait a while for existing tasks to terminate
			if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
				// cancel currently executed tasks
				executor.shutdownNow();
				// wait a while for tasks to respond to being cancelled
				if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
					LOGGER.warn("Executor did not terminate");
					terminated = false;
				}
			}
		} catch (final InterruptedException ex) {
			// (re)-cancel if current thread also interrupted
			executor.shutdownNow();
			// preserve interrupt status
			Thread.currentThread().interrupt();
		}
		return terminated;
	}
	
    /**
     * Causes the currently executing thread to sleep (temporarily cease execution) 
     * for the specified number of milliseconds.
     * Might be interrupted 
     * 
     * @param millis the length of time to sleep in milliseconds
     */
	public static void sleepInterrupt(long millis) {
        LOGGER.trace("sleepInterrupt: {}", millis);
        if (millis < MIN_SLEEP_MILLIS) {
            millis = MIN_SLEEP_MILLIS;
        }
        
        boolean interrupted = false;
        long shutdownTimeMillis = millis + System.currentTimeMillis();
        do {
            try {
                Thread.sleep(MIN_SLEEP_MILLIS);
            } catch (InterruptedException e) {
                LOGGER.trace("interrupted");
                interrupted = true;
            }
        } while (shutdownTimeMillis > System.currentTimeMillis() || interrupted);
	}
	
    /**
     * Causes the currently executing thread to sleep (temporarily cease execution) 
     * for the specified number of milliseconds.
     * Interruption is ignored
     * 
     * @param millis the length of time to sleep in milliseconds
     */
    public static void sleepNoInterrupt(long millis) {
        LOGGER.trace("sleepNoInterrupt: {}", millis);
        if (millis < MIN_SLEEP_MILLIS) {
            millis = MIN_SLEEP_MILLIS;
        }

        long shutdownTimeMillis = millis + System.currentTimeMillis();
        do {
            try {
                Thread.sleep(MIN_SLEEP_MILLIS);
            } catch (InterruptedException e) {
                // ignored
            	LOGGER.trace("interrupt is ignored");
            }
        } while (shutdownTimeMillis > System.currentTimeMillis());
    }	
}
