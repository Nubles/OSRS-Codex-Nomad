package com.codex.nomad.core;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Which emote-clue items each STASH unit takes, so a carried item can be told where it belongs.
 * Read once from the plugin's own data.
 */
public final class StashBook
{
	/** One STASH unit's needs, as written in the data. */
	public static final class Unit
	{
		public String place;
		public String chart;
		public List<Need> needs = new ArrayList<>();
	}

	/** One thing a unit needs: any one of these items. */
	public static final class Need
	{
		public String name;
		public List<Integer> ids = new ArrayList<>();
	}

	/** A unit an item belongs in. */
	public static final class Home
	{
		public final String unit;
		public final String place;
		public final String chart;

		Home(String unit, String place, String chart)
		{
			this.unit = unit;
			this.place = place;
			this.chart = chart;
		}
	}

	private final Map<String, Unit> units;
	private final Map<Integer, List<Home>> homes = new HashMap<>();

	StashBook(Map<String, Unit> units)
	{
		this.units = units;
		units.forEach((k, u) ->
		{
			for (Need n : u.needs)
			{
				for (int id : n.ids)
				{
					homes.computeIfAbsent(id, i -> new ArrayList<>()).add(new Home(k, u.place, u.chart));
				}
			}
		});
	}

	public static StashBook load(Gson gson)
	{
		Type type = new TypeToken<LinkedHashMap<String, Unit>>()
		{
		}.getType();
		try (InputStream in = StashBook.class.getResourceAsStream("/com/codex/nomad/stash.json"))
		{
			if (in == null)
			{
				return new StashBook(Collections.emptyMap());
			}
			Map<String, Unit> units = gson.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), type);
			return new StashBook(units == null ? Collections.emptyMap() : units);
		}
		catch (Exception e)
		{
			return new StashBook(Collections.emptyMap());
		}
	}

	public Unit unit(String name)
	{
		return units.get(name);
	}

	public int size()
	{
		return units.size();
	}

	/** Every STASH unit that takes an item, or none. */
	public List<Home> homes(int item)
	{
		List<Home> h = homes.get(item);
		return h == null ? Collections.emptyList() : Collections.unmodifiableList(h);
	}
}
