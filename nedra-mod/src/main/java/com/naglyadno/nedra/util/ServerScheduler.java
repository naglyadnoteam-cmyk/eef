package com.naglyadno.nedra.util;

import java.util.ArrayList;
import java.util.List;

/** Простейший планировщик отложенных действий на серверном потоке (тик за тиком). */
public final class ServerScheduler {

	private record Task(long dueTick, Runnable action) {
	}

	private static final List<Task> TASKS = new ArrayList<>();
	private static long currentTick;

	private ServerScheduler() {
	}

	public static void schedule(int delayTicks, Runnable action) {
		TASKS.add(new Task(currentTick + Math.max(1, delayTicks), action));
	}

	public static void tick() {
		currentTick++;
		if (TASKS.isEmpty()) {
			return;
		}
		List<Task> due = new ArrayList<>();
		TASKS.removeIf(task -> {
			if (task.dueTick() <= currentTick) {
				due.add(task);
				return true;
			}
			return false;
		});
		for (Task task : due) {
			task.action().run();
		}
	}

	public static void clear() {
		TASKS.clear();
	}
}
