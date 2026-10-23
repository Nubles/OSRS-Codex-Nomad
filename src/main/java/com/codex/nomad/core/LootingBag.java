package com.codex.nomad.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.IntPredicate;

/**
 * The looting bag's rules for an ultimate ironman, as the OSRS Wiki has them: its contents come out
 * only on death, or by destroying it in the Wilderness, which drops them on the floor. Destroying it
 * anywhere else deletes them, and so does destroying it in the Ferox Enclave or within the first two
 * tiles past the Wilderness ditch. Food, potions and vials of water inside are lost either way.
 */
public final class LootingBag
{
	/** The first surface row of the Wilderness, just north of the ditch. */
	private static final int DITCH_Y = 3523;

	private LootingBag()
	{
	}

	/**
	 * Whether destroying the bag here drops its contents rather than deleting them.
	 *
	 * @param wild the game says the player is in the Wilderness
	 */
	public static boolean dropsHere(boolean wild, int x, int y, int plane)
	{
		if (!wild)
		{
			return false;
		}
		if (plane == 0 && x >= 2944 && x <= 3392 && y >= DITCH_Y && y < DITCH_Y + 2)
		{
			return false;
		}
		return !ferox(x, y, plane);
	}

	/** The Ferox Enclave, generously drawn: wilderness levels 13 to 16 around the town's walls. */
	static boolean ferox(int x, int y, int plane)
	{
		return x >= 3121 && x <= 3158 && y >= 3614 && y <= 3648 && plane <= 1;
	}

	/** The things in the bag that destroying it would not drop: food, potions and vials of water. */
	public static List<Integer> perishable(Map<Integer, Long> bag, IntPredicate perishes)
	{
		List<Integer> out = new ArrayList<>();
		for (int item : bag.keySet())
		{
			if (perishes.test(item))
			{
				out.add(item);
			}
		}
		return out;
	}
}
