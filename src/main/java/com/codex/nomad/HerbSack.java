package com.codex.nomad;

import com.codex.nomad.core.Items;
import com.codex.nomad.core.Nomad;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.IntFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.api.gameval.ItemID;

/**
 * Follows the herb sack from what the game says about it: checking it lists every herb, picking a
 * herb up or filling it adds, and emptying it takes out. Adapted from Dude, Where's My Stuff (BSD
 * 2-Clause).
 */
final class HerbSack
{
	static final String NAME = "Herb sack";
	static final int[] HERBS = {
		ItemID.UNIDENTIFIED_GUAM, ItemID.UNIDENTIFIED_MARENTILL, ItemID.UNIDENTIFIED_TARROMIN,
		ItemID.UNIDENTIFIED_HARRALANDER, ItemID.UNIDENTIFIED_RANARR, ItemID.UNIDENTIFIED_TOADFLAX,
		ItemID.UNIDENTIFIED_IRIT, ItemID.UNIDENTIFIED_AVANTOE, ItemID.UNIDENTIFIED_KWUARM, ItemID.UNIDENTIFIED_HUASCA,
		ItemID.UNIDENTIFIED_SNAPDRAGON, ItemID.UNIDENTIFIED_CADANTINE, ItemID.UNIDENTIFIED_LANTADYME,
		ItemID.UNIDENTIFIED_DWARF_WEED, ItemID.UNIDENTIFIED_TORSTOL};
	/** A herb sack holds up to 30 of each herb. */
	static final long MOST = 30;
	private static final Pattern LISTED = Pattern.compile("(\\d+) x (.*)");
	private static final Pattern PICKED = Pattern.compile("You put the (.*) herb into your herb sack");
	private static final Pattern ADDED = Pattern.compile("You add the (.*) to your sack");

	private enum Pending
	{
		NONE, ADD, TAKE
	}

	private final IntFunction<String> names;
	private Map<String, Integer> byName;
	private boolean listing;
	private Pending pending = Pending.NONE;

	HerbSack(IntFunction<String> names)
	{
		this.names = names;
	}

	private int herb(String name)
	{
		if (byName == null)
		{
			byName = new HashMap<>();
			for (int id : HERBS)
			{
				byName.put(names.apply(id).toLowerCase(Locale.ENGLISH), id);
			}
		}
		Integer id = byName.get(name.trim().toLowerCase(Locale.ENGLISH));
		return id == null ? -1 : id;
	}

	/**
	 * Hears a game message.
	 *
	 * @return true if the sack changed now
	 */
	boolean heard(String message, Nomad n, long now)
	{
		if (message.startsWith("The herb sack is empty"))
		{
			listing = false;
			n.carry(NAME, new HashMap<>(), now);
			return true;
		}
		if (message.startsWith("You look in your herb sack"))
		{
			listing = true;
			n.carry(NAME, new HashMap<>(), now);
			return true;
		}
		if (listing)
		{
			Matcher m = LISTED.matcher(message);
			if (m.matches())
			{
				int id = herb(m.group(2));
				if (id > 0)
				{
					Map<Integer, Long> held = new LinkedHashMap<>(n.carried(NAME));
					held.put(id, Long.parseLong(m.group(1)));
					n.carry(NAME, held, now);
					return true;
				}
			}
			return false;
		}
		Matcher picked = PICKED.matcher(message);
		if (picked.find())
		{
			int id = herb(picked.group(1));
			if (id > 0)
			{
				Map<Integer, Long> held = new LinkedHashMap<>(n.carried(NAME));
				held.merge(id, 1L, (a, b) -> Math.min(MOST, a + b));
				n.carry(NAME, held, now);
				return true;
			}
			return false;
		}
		if (ADDED.matcher(message).find() || message.startsWith("You add the herbs to your sack"))
		{
			pending = Pending.ADD;
		}
		else if (message.startsWith("You rummage around to see if you can extract any herbs from your herb sack"))
		{
			pending = Pending.TAKE;
		}
		return false;
	}

	/**
	 * One tick passes: what left the inventory went into the sack, or what arrived came out of it.
	 *
	 * @return true if the sack changed
	 */
	boolean tick(Map<Integer, Long> removed, Map<Integer, Long> gained, Nomad n, long now)
	{
		listing = false;
		Pending p = pending;
		pending = Pending.NONE;
		if (p == Pending.NONE)
		{
			return false;
		}
		Map<Integer, Long> held = new LinkedHashMap<>(n.carried(NAME));
		Map<Integer, Long> moved = p == Pending.ADD ? removed : gained;
		for (int id : HERBS)
		{
			long q = moved.getOrDefault(id, 0L);
			if (q > 0)
			{
				held.merge(id, p == Pending.ADD ? q : -q, Long::sum);
			}
		}
		n.carry(NAME, Items.clean(held), now);
		return true;
	}
}
