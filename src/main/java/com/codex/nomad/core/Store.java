package com.codex.nomad.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** One place things are kept outside the player's pockets, as last seen. */
public final class Store
{
	public String key;
	public Kind kind = Kind.OTHER;
	public String name;
	/** Items by id, as the game holds them. */
	public Map<Integer, Long> items = new LinkedHashMap<>();
	/** When the contents were last seen, epoch ms. */
	public long seen;
	/** False while the contents are a guess, such as a deathbank worked out from a death. */
	public boolean exact = true;
	/** Where it is, or plane -1 for nowhere in particular. */
	public int x;
	public int y;
	public int plane = -1;
	/** When it began, epoch ms: the death that made a pile or a deathbank. */
	public long since;
	/** Game ticks of logged-in time left before a deathpile vanishes, or -1 for no timer. */
	public long ticksLeft = -1;
	/** When dropped items vanish, epoch ms, or 0 for no deadline. */
	public long until;
	/** The world dropped items lie on, or 0. */
	public int world;
	/** When each dropped item vanishes, epoch ms. */
	public Map<Integer, Long> expires = new LinkedHashMap<>();

	public Store()
	{
	}

	public Store(String key, Kind kind, String name)
	{
		this.key = key;
		this.kind = kind;
		this.name = name;
	}

	public Store at(int x, int y, int plane)
	{
		this.x = x;
		this.y = y;
		this.plane = plane;
		return this;
	}

	public boolean located()
	{
		return plane >= 0;
	}

	public long count(int item)
	{
		return items.getOrDefault(item, 0L);
	}

	public boolean isEmpty()
	{
		return items.isEmpty();
	}

	public Map<Integer, Long> view()
	{
		return Collections.unmodifiableMap(items);
	}

	/** Replaces the contents with what was just seen. */
	public void fill(Map<Integer, Long> seenItems, long now)
	{
		items = Items.clean(seenItems);
		seen = now;
		exact = true;
	}

	/** Takes up to n of an item out; returns how many came out. */
	public long take(int item, long n)
	{
		long have = count(item);
		long out = Math.min(have, Math.max(0, n));
		if (have - out <= 0)
		{
			items.remove(item);
			if (expires != null)
			{
				expires.remove(item);
			}
		}
		else
		{
			items.put(item, have - out);
		}
		return out;
	}
}
