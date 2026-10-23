package com.codex.nomad.core;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * When the inventory will be full at the pace it is filling: skilling, looting or picking things up.
 * Watches slots used over the last few minutes of play; any fall (dropping, noting, eating) starts
 * the watch again, so the pace is only ever of filling.
 */
public final class Forecast
{
	static final long WINDOW = 5 * 60_000L;
	static final long LEAST = 60_000L;

	private final Deque<long[]> seen = new ArrayDeque<>();

	/** Notes the slots used at this much play time, ms. */
	public void note(long played, int used)
	{
		long[] last = seen.peekLast();
		if (last != null && used < last[1])
		{
			seen.clear();
		}
		if (last == null || used != last[1] || seen.size() == 1)
		{
			seen.addLast(new long[]{played, used});
		}
		while (seen.size() > 2 && played - seen.peekFirst()[0] > WINDOW)
		{
			seen.removeFirst();
		}
	}

	/** Ms of play until all 28 slots are used, or -1 when not filling or not yet known. */
	public long untilFull(long played, int used)
	{
		long[] first = seen.peekFirst();
		if (first == null || used >= 28)
		{
			return -1;
		}
		long span = played - first[0];
		long gained = used - first[1];
		if (span < LEAST || gained <= 0)
		{
			return -1;
		}
		return (28 - used) * span / gained;
	}
}
