package com.codex.nomad.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.IntPredicate;

/**
 * Dead weight: carried items that have sat untouched for hours of play, each with the slots it
 * takes, so the player can see what to leave in a STASH, the costume room or a deathpile.
 */
public final class Idle
{
	public final int item;
	public final long qty;
	/** Play time since it last changed, ms. */
	public final long idleMs;
	public final int slots;

	Idle(int item, long qty, long idleMs, int slots)
	{
		this.item = item;
		this.qty = qty;
		this.idleMs = idleMs;
		this.slots = slots;
	}

	/**
	 * @param carried the inventory, by item
	 * @param keep items never called dead weight, such as the looting bag and rune pouch
	 * @param after play time untouched before an item counts, ms
	 */
	public static List<Idle> find(Nomad n, Map<Integer, Long> carried, IntPredicate stackable, IntPredicate keep, long after)
	{
		List<Idle> out = new ArrayList<>();
		for (Map.Entry<Integer, Long> e : carried.entrySet())
		{
			int item = e.getKey();
			if (keep.test(item))
			{
				continue;
			}
			long idle = n.played - n.touched(item);
			if (idle >= after)
			{
				int slots = stackable.test(item) ? 1 : (int) Math.min(28, e.getValue());
				out.add(new Idle(item, e.getValue(), idle, slots));
			}
		}
		out.sort((a, b) -> b.slots != a.slots ? Integer.compare(b.slots, a.slots) : Long.compare(b.idleMs, a.idleMs));
		return out;
	}

	public static int slots(List<Idle> list)
	{
		int n = 0;
		for (Idle i : list)
		{
			n += i.slots;
		}
		return n;
	}
}
