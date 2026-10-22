package com.codex.nomad.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What a death would keep and drop right now: the most valuable items are kept one by one (three,
 * four with Protect Item, none while skulled) and the rest fall.
 */
public final class Kept
{
	public final Map<Integer, Long> kept = new LinkedHashMap<>();
	public final Map<Integer, Long> dropped = new LinkedHashMap<>();
	public long keptWorth;
	public long droppedWorth;

	public static Kept of(Map<Integer, Long> carried, Prices prices, int keep)
	{
		Kept k = new Kept();
		List<Map.Entry<Integer, Long>> list = new ArrayList<>(carried.entrySet());
		list.sort((a, b) -> Long.compare(prices.each(b.getKey()), prices.each(a.getKey())));
		int left = Math.max(0, keep);
		for (Map.Entry<Integer, Long> e : list)
		{
			long n = e.getValue();
			long each = prices.each(e.getKey());
			long keepN = Math.min(left, n);
			left -= keepN;
			if (keepN > 0)
			{
				k.kept.put(e.getKey(), keepN);
				k.keptWorth += keepN * each;
			}
			if (n - keepN > 0)
			{
				k.dropped.put(e.getKey(), n - keepN);
				k.droppedWorth += (n - keepN) * each;
			}
		}
		return k;
	}
}
