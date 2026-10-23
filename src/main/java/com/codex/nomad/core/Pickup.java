package com.codex.nomad.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.IntPredicate;
import java.util.function.IntToLongFunction;

/** The order to pick a pile up in: the most worth for each inventory slot first. */
public final class Pickup
{
	private Pickup()
	{
	}

	public static List<Integer> order(Map<Integer, Long> pile, IntToLongFunction price, IntPredicate stackable)
	{
		List<Integer> out = new ArrayList<>(pile.keySet());
		out.sort((a, b) -> Long.compare(perSlot(b, pile.get(b), price, stackable), perSlot(a, pile.get(a), price, stackable)));
		return out;
	}

	static long perSlot(int item, long qty, IntToLongFunction price, IntPredicate stackable)
	{
		return stackable.test(item) ? price.applyAsLong(item) * qty : price.applyAsLong(item);
	}
}
