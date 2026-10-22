package com.codex.nomad.core;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.IntUnaryOperator;

/** Small sums over item maps. */
public final class Items
{
	private Items()
	{
	}

	/** Positive counts only, no empty slots, held to what one stack can hold. */
	public static Map<Integer, Long> clean(Map<Integer, Long> in)
	{
		Map<Integer, Long> out = new LinkedHashMap<>();
		if (in == null)
		{
			return out;
		}
		in.forEach((k, v) ->
		{
			if (k != null && k > 0 && v != null && v > 0)
			{
				out.merge(k, Math.min(Integer.MAX_VALUE, v), (a, b) -> Math.min(Integer.MAX_VALUE, a + b));
			}
		});
		return out;
	}

	/** Adds every count in b into a. */
	public static void add(Map<Integer, Long> into, Map<Integer, Long> b)
	{
		if (b != null)
		{
			b.forEach((k, v) -> into.merge(k, v, Long::sum));
		}
	}

	/** What a holds beyond b: the items lost or spent when a became b. */
	public static Map<Integer, Long> minus(Map<Integer, Long> a, Map<Integer, Long> b)
	{
		Map<Integer, Long> out = new LinkedHashMap<>();
		a.forEach((k, v) ->
		{
			long d = v - b.getOrDefault(k, 0L);
			if (d > 0)
			{
				out.put(k, d);
			}
		});
		return out;
	}

	/** The same items keyed by a common form, such as noted and unnoted counted together. */
	public static Map<Integer, Long> canon(Map<Integer, Long> in, IntUnaryOperator canon)
	{
		Map<Integer, Long> out = new HashMap<>();
		in.forEach((k, v) -> out.merge(canon.applyAsInt(k), v, Long::sum));
		return out;
	}

	public static long worth(Map<Integer, Long> items, Prices prices)
	{
		long sum = 0;
		for (Map.Entry<Integer, Long> e : items.entrySet())
		{
			sum += prices.each(e.getKey()) * e.getValue();
		}
		return sum;
	}
}
