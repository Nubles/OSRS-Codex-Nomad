package com.codex.nomad.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;
import java.util.function.IntUnaryOperator;

/**
 * Checks a packing list against what is carried and every place things are kept, and works out
 * the stops to make, in order: perishable places first, then the nearest. The first stop is the
 * one the player is pointed at.
 */
public final class Fetch
{
	public static final int SLOTS = 28;

	/** One line of the list, checked. */
	public static final class Line
	{
		public Trip.Want want;
		public long have;
		/** Where the shortfall can come from, by store key. */
		public Map<String, Long> from = new LinkedHashMap<>();
		/** Sitting in the looting bag, which only a death or the Wilderness opens. */
		public long bagged;
		/** Still short once every place has given what it holds. */
		public long missing;

		public long shortfall()
		{
			return Math.max(0, want.qty - have);
		}

		public boolean ready()
		{
			return have >= want.qty;
		}
	}

	/** One place to call at, with what to take. */
	public static final class Stop
	{
		public Store store;
		public Map<Integer, Long> take = new LinkedHashMap<>();
		/** Tiles away as the crow flies, or -1 when unknown or on another plane. */
		public int distance = -1;

		public long count()
		{
			long n = 0;
			for (long v : take.values())
			{
				n += v;
			}
			return n;
		}
	}

	public final Trip trip;
	public final List<Line> lines = new ArrayList<>();
	public final List<Stop> stops = new ArrayList<>();
	/** Carried items that are not on the list, by item: candidates to leave somewhere first. */
	public final Map<Integer, Long> extra = new LinkedHashMap<>();
	public int ready;
	public int missing;
	/** Inventory slots the list takes once packed. */
	public int slots;

	private Fetch(Trip trip)
	{
		this.trip = trip;
	}

	public boolean done()
	{
		return ready == lines.size();
	}

	public boolean fits()
	{
		return slots <= SLOTS;
	}

	public Stop next()
	{
		return stops.isEmpty() ? null : stops.get(0);
	}

	/**
	 * @param pocket what is carried and usable (inventory and worn), by common item id
	 * @param bag what is in the looting bag, by common item id
	 * @param stores every place things are kept
	 * @param canon turns an item into its common form: noted and unnoted alike
	 * @param stackable whether an item stacks in one slot
	 * @param here the player's x, y and plane, or null
	 * @param always items never called extra, such as the rune pouch and coins
	 */
	public static Fetch plan(Trip trip, Map<Integer, Long> pocket, Map<Integer, Long> bag, List<Store> stores,
		IntUnaryOperator canon, IntPredicate stackable, int[] here, IntPredicate always)
	{
		return plan(trip, pocket, bag, stores, canon, stackable, here, always, null);
	}

	/** "Super restore(3)": the potion or jewellery and how many doses or charges it holds. */
	private static final Pattern USES = Pattern.compile("^(.+)\\((\\d)\\)$");

	/** The name without its doses or charges, and how many it holds, or null. */
	static Object[] uses(IntFunction<String> name, int id)
	{
		String n = name == null ? null : name.apply(id);
		Matcher m = n == null ? null : USES.matcher(n);
		return m != null && m.matches() ? new Object[]{m.group(1).trim(), Integer.parseInt(m.group(2))} : null;
	}

	/**
	 * As above; with item names, a potion or charged jewellery wanted at one dose count is met by
	 * the same thing at any other, dose for dose: four super restore(3) stand in for three (4).
	 */
	public static Fetch plan(Trip trip, Map<Integer, Long> pocket, Map<Integer, Long> bag, List<Store> stores,
		IntUnaryOperator canon, IntPredicate stackable, int[] here, IntPredicate always, IntFunction<String> name)
	{
		Fetch f = new Fetch(trip);
		List<Store> order = new ArrayList<>();
		Map<Store, Map<Integer, Long>> left = new LinkedHashMap<>();
		Map<Store, Integer> distance = new LinkedHashMap<>();
		for (Store s : stores)
		{
			if (s.isEmpty())
			{
				continue;
			}
			order.add(s);
			left.put(s, Items.canon(s.items, canon));
			distance.put(s, distance(s, here));
		}
		order.sort((a, b) ->
		{
			int k = Integer.compare(a.kind.ordinal(), b.kind.ordinal());
			if (k != 0)
			{
				return k;
			}
			int da = distance.get(a);
			int db = distance.get(b);
			if (da < 0 || db < 0)
			{
				return Boolean.compare(da < 0, db < 0);
			}
			return Integer.compare(da, db);
		});

		Map<Store, Stop> byStore = new LinkedHashMap<>();
		Map<Integer, Long> wanted = new LinkedHashMap<>();
		for (Trip.Want w : trip.wants)
		{
			Line line = new Line();
			line.want = w;
			long carried = pocket.getOrDefault(w.item, 0L) - wanted.getOrDefault(w.item, 0L);
			line.have = Math.max(0, Math.min(w.qty, carried));
			wanted.merge(w.item, line.have, Long::sum);
			long need = line.shortfall();
			for (Store s : order)
			{
				if (need <= 0)
				{
					break;
				}
				Map<Integer, Long> there = left.get(s);
				long n = Math.min(need, there.getOrDefault(w.item, 0L));
				if (n > 0)
				{
					there.put(w.item, there.get(w.item) - n);
					line.from.put(s.key, n);
					Stop stop = byStore.computeIfAbsent(s, k ->
					{
						Stop st = new Stop();
						st.store = k;
						st.distance = distance.get(k);
						return st;
					});
					stop.take.merge(w.item, n, Long::sum);
					need -= n;
				}
			}
			Object[] wantUses = need > 0 ? uses(name, w.item) : null;
			if (wantUses != null && (int) wantUses[1] > 0)
			{
				int each = (int) wantUses[1];
				long doses = need * each;
				// Carried first: other dose counts in the pack count as had.
				for (Map.Entry<Integer, Long> e : pocket.entrySet())
				{
					Object[] u = e.getKey() == w.item || doses <= 0 ? null : uses(name, e.getKey());
					if (u == null || !u[0].equals(wantUses[0]) || (int) u[1] <= 0)
					{
						continue;
					}
					long free = e.getValue() - wanted.getOrDefault(e.getKey(), 0L);
					long k = Math.min(free, (doses + (int) u[1] - 1) / (int) u[1]);
					if (k > 0)
					{
						wanted.merge(e.getKey(), k, Long::sum);
						doses -= k * (int) u[1];
					}
				}
				long fromPocket = need - Math.max(0, (doses + each - 1) / each);
				line.have = Math.min(w.qty, line.have + fromPocket);
				for (Store st : order)
				{
					if (doses <= 0)
					{
						break;
					}
					Map<Integer, Long> there = left.get(st);
					for (Map.Entry<Integer, Long> e : new ArrayList<>(there.entrySet()))
					{
						Object[] u = e.getKey() == w.item || doses <= 0 ? null : uses(name, e.getKey());
						if (u == null || !u[0].equals(wantUses[0]) || (int) u[1] <= 0 || e.getValue() <= 0)
						{
							continue;
						}
						long k = Math.min(e.getValue(), (doses + (int) u[1] - 1) / (int) u[1]);
						there.put(e.getKey(), e.getValue() - k);
						doses -= k * (int) u[1];
						line.from.merge(st.key, k, Long::sum);
						Stop stop = byStore.computeIfAbsent(st, x ->
						{
							Stop sp = new Stop();
							sp.store = x;
							sp.distance = distance.get(x);
							return sp;
						});
						stop.take.merge(e.getKey(), k, Long::sum);
					}
				}
				need = Math.max(0, (doses + each - 1) / each);
			}
			if (need > 0)
			{
				line.bagged = Math.min(need, bag.getOrDefault(w.item, 0L));
			}
			line.missing = need;
			if (line.ready())
			{
				f.ready++;
			}
			else if (line.missing > 0)
			{
				f.missing++;
			}
			if (!w.worn)
			{
				f.slots += stackable.test(w.item) ? 1 : (int) Math.min(SLOTS + 1, w.qty);
			}
			f.lines.add(line);
		}
		for (Store s : order)
		{
			Stop stop = byStore.get(s);
			if (stop != null)
			{
				f.stops.add(stop);
			}
		}
		for (Map.Entry<Integer, Long> e : pocket.entrySet())
		{
			long beyond = e.getValue() - wanted.getOrDefault(e.getKey(), 0L);
			boolean listed = wanted.getOrDefault(e.getKey(), 0L) > 0;
			for (Trip.Want w : trip.wants)
			{
				listed |= w.item == e.getKey();
			}
			if (beyond > 0 && !listed && !always.test(e.getKey()))
			{
				f.extra.put(e.getKey(), beyond);
			}
		}
		return f;
	}

	static int distance(Store s, int[] here)
	{
		if (here == null || !s.located() || s.plane != here[2])
		{
			return -1;
		}
		return Math.max(Math.abs(s.x - here[0]), Math.abs(s.y - here[1]));
	}

	public List<Line> lines()
	{
		return Collections.unmodifiableList(lines);
	}
}
