package com.codex.nomad.core;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Everything Nomad keeps for one account: what is carried, every place things are kept, the
 * deathpiles and deathbank, the packing lists, and how long each carried item has sat untouched.
 * Saved as one blob per RuneScape profile.
 */
public final class Nomad
{
	public static final int VERSION = 1;
	/** A deathpile lasts an hour of logged-in time: 6,000 game ticks. */
	public static final long PILE_TICKS = 6_000;
	public static final String INVENTORY = "Inventory";
	public static final String WORN = "Worn";
	public static final String LOOTING_BAG = "Looting bag";
	public static final String RUNE_POUCH = "Rune pouch";
	public static final String DEATHBANK = "deathbank";
	static final int TRIPS = 30;
	static final int PILES = 6;
	static final int FLOORS = 20;

	int version = VERSION;
	/** The ironman varbit: 2 is ultimate; -1 not yet read. */
	public int account = -1;
	/** Milliseconds played in all, counted while logged in. */
	public long played;
	/** What is carried, by container name. */
	Map<String, Map<Integer, Long>> pack = new LinkedHashMap<>();
	/** When each container was last read, epoch ms; a looting bag is only known once checked. */
	Map<String, Long> packSeen = new HashMap<>();
	/** Every place things are kept, by key. */
	Map<String, Store> stores = new LinkedHashMap<>();
	/** Play time, in ms of {@link #played}, when each carried item last changed. */
	Map<Integer, Long> touched = new HashMap<>();
	List<Trip> trips = new ArrayList<>();
	/** The packing list being fetched, by name, or null. */
	public String active;
	/** Route steps ticked by hand or by holding their items, by step key. */
	Set<String> ticked = new HashSet<>();
	/** True while the player follows the route to Prifddinas. */
	public boolean following;

	// ---- what is carried ----

	public void carry(String container, Map<Integer, Long> items, long now)
	{
		pack.put(container, Items.clean(items));
		packSeen.put(container, now);
	}

	public Map<Integer, Long> carried(String container)
	{
		Map<Integer, Long> m = pack.get(container);
		return m == null ? Collections.emptyMap() : Collections.unmodifiableMap(m);
	}

	/** The names of every container ever read, in the order first read. */
	public List<String> containers()
	{
		return new ArrayList<>(pack.keySet());
	}

	public boolean seen(String container)
	{
		return pack.containsKey(container);
	}

	public long seenAt(String container)
	{
		return packSeen.getOrDefault(container, 0L);
	}

	/** Everything carried, every container together. */
	public Map<Integer, Long> carried()
	{
		Map<Integer, Long> all = new LinkedHashMap<>();
		pack.values().forEach(m -> Items.add(all, m));
		return all;
	}

	/** The inventory and worn items: the only things a death can keep. */
	public Map<Integer, Long> loose()
	{
		Map<Integer, Long> all = new LinkedHashMap<>();
		Items.add(all, carried(INVENTORY));
		Items.add(all, carried(WORN));
		return all;
	}

	/** Everything carried inside something else: the looting bag, pouches, sacks and boxes. */
	public Map<Integer, Long> inside()
	{
		Map<Integer, Long> all = new LinkedHashMap<>();
		pack.forEach((k, m) ->
		{
			if (!k.equals(INVENTORY) && !k.equals(WORN))
			{
				Items.add(all, m);
			}
		});
		return all;
	}

	/**
	 * Notes when carried items change, so items that sit untouched for hours of play can be told
	 * apart from the ones in use. Worn items count as in use.
	 */
	public void touch(Map<Integer, Long> before, Map<Integer, Long> after)
	{
		for (Map.Entry<Integer, Long> e : after.entrySet())
		{
			if (!e.getValue().equals(before.get(e.getKey())))
			{
				touched.put(e.getKey(), played);
			}
		}
		touched.keySet().retainAll(after.keySet());
	}

	/** Play time when an item last changed, or the current play time if it never has. */
	public long touched(int item)
	{
		return touched.getOrDefault(item, played);
	}

	// ---- stores ----

	public Store store(String key)
	{
		return stores.get(key);
	}

	public void put(Store s)
	{
		stores.put(s.key, s);
	}

	public Store remove(String key)
	{
		return stores.remove(key);
	}

	public List<Store> stores()
	{
		return new ArrayList<>(stores.values());
	}

	public List<Store> stores(Kind kind)
	{
		List<Store> out = new ArrayList<>();
		for (Store s : stores.values())
		{
			if (s.kind == kind)
			{
				out.add(s);
			}
		}
		return out;
	}

	public Store deathbank()
	{
		return stores.get(DEATHBANK);
	}

	/** Leaves a deathpile where the player died; the oldest goes if there are already too many. */
	public Store pile(Map<Integer, Long> items, int x, int y, int plane, long now)
	{
		List<Store> piles = stores(Kind.DEATHPILE);
		while (piles.size() >= PILES)
		{
			stores.remove(piles.remove(0).key);
		}
		Store pile = new Store("pile:" + now, Kind.DEATHPILE, "Deathpile").at(x, y, plane);
		pile.items = Items.clean(items);
		pile.seen = now;
		pile.since = now;
		pile.ticksLeft = PILE_TICKS;
		pile.exact = true;
		put(pile);
		return pile;
	}

	/**
	 * One logged-in game tick passes: every deathpile's timer runs down.
	 *
	 * @return the piles that ran out on this tick, now gone
	 */
	public List<Store> tick()
	{
		List<Store> gone = new ArrayList<>();
		for (Store s : stores(Kind.DEATHPILE))
		{
			if (s.ticksLeft > 0)
			{
				s.ticksLeft--;
			}
			if (s.ticksLeft == 0 || s.isEmpty())
			{
				stores.remove(s.key);
				if (!s.isEmpty())
				{
					gone.add(s);
				}
			}
		}
		return gone;
	}

	// ---- dropped items ----

	public static String floorKey(int world, int x, int y, int plane)
	{
		return "floor:" + world + ":" + x + ":" + y + ":" + plane;
	}

	/** Notes items the player dropped, with when they vanish; one store per tile and world. */
	public Store drop(int world, int x, int y, int plane, int item, long qty, long until, long now)
	{
		String key = floorKey(world, x, y, plane);
		Store s = stores.get(key);
		if (s == null)
		{
			List<Store> floors = stores(Kind.FLOOR);
			floors.sort((a, b) -> Long.compare(a.until, b.until));
			while (floors.size() >= FLOORS)
			{
				stores.remove(floors.remove(0).key);
			}
			s = new Store(key, Kind.FLOOR, "Dropped on world " + world).at(x, y, plane);
			s.world = world;
			s.since = now;
			put(s);
		}
		if (s.expires == null)
		{
			s.expires = new LinkedHashMap<>();
		}
		s.items.merge(item, qty, Long::sum);
		s.expires.merge(item, until, Math::max);
		s.until = soonest(s);
		s.seen = now;
		s.exact = true;
		return s;
	}

	/** Takes items off the floor: picked up, or seen to vanish. */
	public void lift(int world, int x, int y, int plane, int item, long qty)
	{
		Store s = stores.get(floorKey(world, x, y, plane));
		if (s == null)
		{
			return;
		}
		s.take(item, qty);
		if (s.isEmpty())
		{
			stores.remove(s.key);
		}
		else
		{
			s.until = soonest(s);
		}
	}

	/**
	 * Lets dropped items whose time has come go.
	 *
	 * @return what vanished, by the store it lay in
	 */
	public Map<Store, Map<Integer, Long>> expire(long now)
	{
		Map<Store, Map<Integer, Long>> gone = new LinkedHashMap<>();
		for (Store s : stores(Kind.FLOOR))
		{
			Map<Integer, Long> lost = new LinkedHashMap<>();
			if (s.expires != null)
			{
				for (Map.Entry<Integer, Long> e : new ArrayList<>(s.expires.entrySet()))
				{
					if (e.getValue() <= now)
					{
						lost.put(e.getKey(), s.take(e.getKey(), Long.MAX_VALUE));
					}
				}
			}
			if (s.isEmpty() || s.expires == null || s.expires.isEmpty())
			{
				stores.remove(s.key);
			}
			else
			{
				s.until = soonest(s);
			}
			lost.values().removeIf(q -> q <= 0);
			if (!lost.isEmpty())
			{
				gone.put(s, lost);
			}
		}
		return gone;
	}

	private static long soonest(Store s)
	{
		long t = Long.MAX_VALUE;
		for (long u : s.expires.values())
		{
			t = Math.min(t, u);
		}
		return t == Long.MAX_VALUE ? 0 : t;
	}

	/** Everything the account holds, carried and kept, for the rest of the suite. */
	public Map<Integer, Long> holdings()
	{
		Map<Integer, Long> all = carried();
		for (Store s : stores.values())
		{
			Items.add(all, s.items);
		}
		return all;
	}

	// ---- packing lists ----

	public List<Trip> trips()
	{
		return Collections.unmodifiableList(trips);
	}

	public Trip trip(String name)
	{
		for (Trip t : trips)
		{
			if (t.name.equalsIgnoreCase(name))
			{
				return t;
			}
		}
		return null;
	}

	/** Saves a packing list, replacing one of the same name. */
	public void save(Trip trip)
	{
		trips.removeIf(t -> t.name.equalsIgnoreCase(trip.name));
		trips.add(0, trip);
		while (trips.size() > TRIPS)
		{
			trips.remove(trips.size() - 1);
		}
	}

	public void forget(String name)
	{
		trips.removeIf(t -> t.name.equalsIgnoreCase(name));
		if (name.equalsIgnoreCase(active))
		{
			active = null;
		}
	}

	public Trip activeTrip()
	{
		return active == null ? null : trip(active);
	}

	// ---- the route ----

	public Set<String> ticked()
	{
		return Collections.unmodifiableSet(ticked);
	}

	public void tick(String step, boolean done)
	{
		if (done)
		{
			ticked.add(step);
		}
		else
		{
			ticked.remove(step);
		}
	}

	// ---- saving ----

	public String write(Gson gson)
	{
		return gson.toJson(this);
	}

	/** What reading a save gave: the account's data, and whether any part of it had to be dropped. */
	public static final class Loaded
	{
		public final Nomad nomad;
		public final boolean damaged;

		Loaded(Nomad nomad, boolean damaged)
		{
			this.nomad = nomad;
			this.damaged = damaged;
		}
	}

	/**
	 * Reads a save part by part, so one damaged part costs only that part: the rest of the
	 * account's places and lists come back as they were.
	 */
	public static Loaded read(Gson gson, String saved)
	{
		Nomad n = new Nomad();
		if (saved == null || saved.trim().isEmpty())
		{
			return new Loaded(n, false);
		}
		JsonObject o;
		try
		{
			JsonElement e = new JsonParser().parse(saved);
			if (!e.isJsonObject())
			{
				return new Loaded(n, true);
			}
			o = e.getAsJsonObject();
		}
		catch (RuntimeException ex)
		{
			return new Loaded(n, true);
		}
		boolean damaged = false;
		try
		{
			n.account = o.has("account") ? o.get("account").getAsInt() : -1;
			n.played = o.has("played") ? Math.max(0, o.get("played").getAsLong()) : 0;
		}
		catch (RuntimeException ex)
		{
			damaged = true;
		}
		try
		{
			n.following = o.has("following") && o.get("following").getAsBoolean();
			if (o.has("ticked") && o.get("ticked").isJsonArray())
			{
				for (JsonElement e : o.getAsJsonArray("ticked"))
				{
					if (e.isJsonPrimitive())
					{
						n.ticked.add(e.getAsString());
					}
				}
			}
		}
		catch (RuntimeException ex)
		{
			damaged = true;
		}
		try
		{
			n.active = o.has("active") && o.get("active").isJsonPrimitive() ? o.get("active").getAsString() : null;
		}
		catch (RuntimeException ex)
		{
			damaged = true;
		}
		Type packType = new TypeToken<Map<String, Map<Integer, Long>>>()
		{
		}.getType();
		Type longs = new TypeToken<Map<String, Long>>()
		{
		}.getType();
		Type touchedType = new TypeToken<Map<Integer, Long>>()
		{
		}.getType();
		try
		{
			Map<String, Map<Integer, Long>> pack = gson.fromJson(o.get("pack"), packType);
			if (pack != null)
			{
				pack.forEach((k, v) ->
				{
					if (k != null)
					{
						n.pack.put(k, Items.clean(v));
					}
				});
			}
			Map<String, Long> seen = gson.fromJson(o.get("packSeen"), longs);
			if (seen != null)
			{
				seen.forEach((k, v) ->
				{
					if (k != null && v != null)
					{
						n.packSeen.put(k, v);
					}
				});
			}
		}
		catch (RuntimeException ex)
		{
			damaged = true;
		}
		if (o.has("stores") && o.get("stores").isJsonObject())
		{
			for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("stores").entrySet())
			{
				try
				{
					Store s = gson.fromJson(e.getValue(), Store.class);
					if (s != null && s.key != null && s.kind != null)
					{
						s.items = Items.clean(s.items);
						if (s.expires == null)
						{
							s.expires = new LinkedHashMap<>();
						}
						n.stores.put(s.key, s);
					}
					else
					{
						damaged = true;
					}
				}
				catch (RuntimeException ex)
				{
					damaged = true;
				}
			}
		}
		try
		{
			Map<Integer, Long> touched = gson.fromJson(o.get("touched"), touchedType);
			if (touched != null)
			{
				touched.forEach((k, v) ->
				{
					if (k != null && v != null)
					{
						n.touched.put(k, v);
					}
				});
			}
		}
		catch (RuntimeException ex)
		{
			damaged = true;
		}
		if (o.has("trips") && o.get("trips").isJsonArray())
		{
			for (JsonElement e : o.getAsJsonArray("trips"))
			{
				try
				{
					Trip t = gson.fromJson(e, Trip.class);
					if (t != null && t.name != null && t.wants != null)
					{
						t.wants.removeIf(w -> w == null || w.item <= 0 || w.qty <= 0);
						n.trips.add(t);
					}
					else
					{
						damaged = true;
					}
				}
				catch (RuntimeException ex)
				{
					damaged = true;
				}
			}
		}
		return new Loaded(n, damaged);
	}
}
