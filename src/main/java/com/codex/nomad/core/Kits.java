package com.codex.nomad.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;

/**
 * Turns the kits saved in Codex Quartermaster into packing lists. A kit names supplies, not items:
 * "200 hp of food", "12 doses of prayer restore". Each becomes the items you own that cover it,
 * richest first, and whatever is still short asks for the best item of that kind.
 */
public final class Kits
{
	/** Packing lists made from Quartermaster kits start with this. */
	public static final String PREFIX = "Quartermaster: ";
	public static final String GROUP = "codexquartermaster";
	public static final String KEY = "kitsV1";

	private Kits()
	{
	}

	/**
	 * @param json the kitsV1 value, or null
	 * @param owned how many of an item you own anywhere Nomad knows
	 * @param name an item's name
	 */
	public static List<Trip> read(String json, Map<Integer, Long> owned, IntFunction<String> name)
	{
		List<Trip> out = new ArrayList<>();
		JsonElement root;
		try
		{
			root = json == null ? null : new JsonParser().parse(json);
		}
		catch (RuntimeException e)
		{
			return out;
		}
		if (root == null || !root.isJsonObject() || !root.getAsJsonObject().has("kits")
			|| !root.getAsJsonObject().get("kits").isJsonArray())
		{
			return out;
		}
		String active = string(root.getAsJsonObject(), "active");
		for (JsonElement k : root.getAsJsonObject().getAsJsonArray("kits"))
		{
			if (!k.isJsonObject())
			{
				continue;
			}
			JsonObject kit = k.getAsJsonObject();
			String kitName = string(kit, "name");
			boolean preset = kit.has("preset") && kit.get("preset").isJsonPrimitive() && kit.get("preset").getAsBoolean();
			if (kitName == null || preset && !String.valueOf(string(kit, "id")).equals(active))
			{
				continue;
			}
			Trip trip = new Trip(PREFIX + kitName, 0);
			if (kit.has("needs") && kit.get("needs").isJsonArray())
			{
				for (JsonElement n : kit.getAsJsonArray("needs"))
				{
					if (n.isJsonObject())
					{
						need(trip, n.getAsJsonObject(), owned, name);
					}
				}
			}
			if (!trip.wants.isEmpty())
			{
				out.add(trip);
			}
		}
		return out;
	}

	private static void need(Trip trip, JsonObject need, Map<Integer, Long> owned, IntFunction<String> name)
	{
		if (!need.has("items") || !need.get("items").isJsonObject())
		{
			return;
		}
		List<long[]> items = new ArrayList<>();
		for (Map.Entry<String, JsonElement> e : need.getAsJsonObject("items").entrySet())
		{
			try
			{
				int id = Integer.parseInt(e.getKey());
				long unit = e.getValue().getAsLong();
				if (id > 0 && unit > 0)
				{
					items.add(new long[]{id, unit});
				}
			}
			catch (RuntimeException ignored)
			{
				// Not an item: skipped.
			}
		}
		if (items.isEmpty())
		{
			return;
		}
		long wanted = need.has("perTrip") && need.get("perTrip").isJsonPrimitive() ? need.get("perTrip").getAsLong() : 0;
		// Richest first, as Quartermaster lists them; owned items before ones you would have to find.
		items.sort((a, b) -> Long.compare(b[1], a[1]));
		List<long[]> mine = new ArrayList<>();
		for (long[] i : items)
		{
			if (owned.getOrDefault((int) i[0], 0L) > 0)
			{
				mine.add(i);
			}
		}
		if (wanted <= 0)
		{
			long[] pick = mine.isEmpty() ? items.get(0) : mine.get(0);
			add(trip, (int) pick[0], 1, name);
			return;
		}
		long left = wanted;
		for (long[] i : mine)
		{
			if (left <= 0)
			{
				break;
			}
			long take = Math.min(owned.get((int) i[0]), (left + i[1] - 1) / i[1]);
			add(trip, (int) i[0], take, name);
			left -= take * i[1];
		}
		if (left > 0)
		{
			long[] best = items.get(0);
			add(trip, (int) best[0], (left + best[1] - 1) / best[1], name);
		}
	}

	private static void add(Trip trip, int id, long qty, IntFunction<String> name)
	{
		for (Trip.Want w : trip.wants)
		{
			if (w.item == id)
			{
				w.qty += qty;
				return;
			}
		}
		trip.wants.add(new Trip.Want(id, name.apply(id), qty, false));
	}

	private static String string(JsonObject o, String key)
	{
		JsonElement e = o.get(key);
		return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
	}

	/** Lists made from kits that Quartermaster no longer has. */
	public static List<String> stale(List<Trip> saved, List<Trip> fresh)
	{
		List<String> gone = new ArrayList<>();
		for (Trip t : saved)
		{
			if (t.name.startsWith(PREFIX) && fresh.stream().noneMatch(f -> f.name.equalsIgnoreCase(t.name)))
			{
				gone.add(t.name);
			}
		}
		return gone.isEmpty() ? Collections.emptyList() : gone;
	}
}
