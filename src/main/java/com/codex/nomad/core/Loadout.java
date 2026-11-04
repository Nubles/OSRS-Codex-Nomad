package com.codex.nomad.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntPredicate;
import java.util.function.IntUnaryOperator;
import net.runelite.api.gameval.ItemID;

/**
 * Solves what an ultimate ironman carries for a packing list: what is worn, which runes go in the
 * rune pouch, how the 28 inventory slots fill, and, for everything carried that the list does not
 * need, where it can wait. A UIM has no bank, so each thing left behind is given a real home: its
 * STASH unit, the costume room, the tool leprechaun, the looting bag (which takes no slot but only
 * opens on death), death storage at Zulrah, or the floor. Nothing untradeable is ever sent to the
 * floor: it goes to death storage, since it cannot be bought back.
 *
 * <p>Rules, from the OSRS Wiki: a rune pouch holds three kinds of rune (a divine rune pouch four),
 * up to 16,000 of each; the looting bag holds 28 stacks of tradeable items, filled only in the
 * Wilderness, and an ultimate ironman gets them back only by dying or destroying it there; the
 * tool leprechaun holds up to 100 rakes, seed dibbers, spades, secateurs and trowels, one
 * watering can and one bottomless compost bucket, and 1,000 plant cures, buckets and buckets of
 * each compost; an ultimate ironman cannot keep duplicates in the costume room and reclaims from
 * Priestess Zul-Gwenwynig for free.
 */
public final class Loadout
{
	public static final int SLOTS = 28;
	public static final int POUCH_TYPES = 3;
	public static final int DIVINE_POUCH_TYPES = 4;
	public static final int POUCH_EACH = 16_000;
	public static final int BAG_STACKS = 28;
	/** The landing of the Zul-Andra teleport, beside Priestess Zul-Gwenwynig (Shortest Path's travel data). */
	public static final int[] ZUL_ANDRA = {2196, 3056, 0};

	private static final Map<Integer, Integer> LEPRECHAUN = new HashMap<>();

	static
	{
		for (int id : new int[]{ItemID.RAKE, ItemID.DIBBER, ItemID.SPADE, ItemID.SECATEURS, ItemID.FAIRY_ENCHANTED_SECATEURS,
			ItemID.GARDENING_TROWEL})
		{
			LEPRECHAUN.put(id, 100);
		}
		for (int id : new int[]{ItemID.WATERING_CAN_0, ItemID.WATERING_CAN_1, ItemID.WATERING_CAN_2, ItemID.WATERING_CAN_3,
			ItemID.WATERING_CAN_4, ItemID.WATERING_CAN_5, ItemID.WATERING_CAN_6, ItemID.WATERING_CAN_7, ItemID.WATERING_CAN_8,
			ItemID.ZEAH_WATERINGCAN, ItemID.BOTTOMLESS_COMPOST_BUCKET, ItemID.BOTTOMLESS_COMPOST_BUCKET_FILLED})
		{
			LEPRECHAUN.put(id, 1);
		}
		for (int id : new int[]{ItemID.PLANT_CURE, ItemID.BUCKET_EMPTY, ItemID.BUCKET_COMPOST, ItemID.BUCKET_SUPERCOMPOST,
			ItemID.BUCKET_ULTRACOMPOST})
		{
			LEPRECHAUN.put(id, 1000);
		}
	}

	/** How many of an item the tool leprechaun takes, or 0 for none. */
	public static int leprechaun(int item)
	{
		return LEPRECHAUN.getOrDefault(item, 0);
	}

	/** Why a stack sits where it does. */
	public enum Role
	{
		/** On the packing list. */
		LIST,
		/** A bag or pouch that holds other things, always carried. */
		CONTAINER,
		/** Not needed, but there is room and nowhere safer for it. */
		SPARE
	}

	/** Where something not needed can wait, safest first. */
	public enum Home
	{
		STASH("STASH unit", "STASH", true),
		COSTUME("Costume room", "Costume", true),
		LEPRECHAUN("Tool leprechaun", "Leprechaun", true),
		LOOTING_BAG("Looting bag", "Bag", false),
		DEATH_STORAGE("Death storage", "Zulrah", false),
		DROP("Drop it", "Drop", false);

		private final String label;
		private final String brief;
		private final boolean safe;

		Home(String label, String brief, boolean safe)
		{
			this.label = label;
			this.brief = brief;
			this.safe = safe;
		}

		/** One short word for a row's figure. */
		public String getBrief()
		{
			return brief;
		}

		public String getLabel()
		{
			return label;
		}

		/** Kept for good, whatever happens on the trip. */
		public boolean isSafe()
		{
			return safe;
		}
	}

	/** One stack carried, worn or pouched. */
	public static final class Packed
	{
		public final int item;
		public final long qty;
		public final int slots;
		public final Role role;

		Packed(int item, long qty, int slots, Role role)
		{
			this.item = item;
			this.qty = qty;
			this.slots = slots;
			this.role = role;
		}
	}

	/** One stack to leave behind before the trip. */
	public static final class Leave
	{
		public final int item;
		public final long qty;
		/** Inventory slots it frees. */
		public final int slots;
		public final Home home;
		/** The STASH unit or furniture, in words, or null. */
		public final String where;
		/** The trip does not fit unless this goes. */
		public final boolean must;
		public final long worth;

		Leave(int item, long qty, int slots, Home home, String where, boolean must, long worth)
		{
			this.item = item;
			this.qty = qty;
			this.slots = slots;
			this.home = home;
			this.where = where;
			this.must = must;
			this.worth = worth;
		}
	}

	/** A packing list line to carry fewer of, so the list itself fits. */
	public static final class Trim
	{
		public final int item;
		public final long cut;

		Trim(int item, long cut)
		{
			this.item = item;
			this.cut = cut;
		}
	}

	/** What the solver knows about items. */
	public interface Facts
	{
		boolean stackable(int item);

		/** A rune the rune pouch takes. */
		boolean rune(int item);

		boolean tradeable(int item);

		/** Worth of one. */
		long price(int item);

		/** A bag or pouch that holds other things. */
		boolean container(int item);

		/** The STASH unit this item belongs in, in words, or null. */
		String stash(int item);

		/** Whether the costume room takes it. */
		boolean costume(int item);
	}

	public final List<Packed> pack = new ArrayList<>();
	public final List<Packed> worn = new ArrayList<>();
	public final List<Packed> pouch = new ArrayList<>();
	public final List<Leave> leave = new ArrayList<>();
	public final List<Trim> trim = new ArrayList<>();
	/** Inventory slots the list and the containers take. */
	public int needed;
	/** Inventory slots taken once packed, spares included. */
	public int used;
	public int pouchTypes;
	public boolean hasPouch;
	public boolean hasBag;
	/** Looting bag stacks still free. */
	public int bagFree;
	/** The deathbank a death for storage would wipe, or null. */
	public String wipes;

	private Loadout()
	{
	}

	/** True when the list fits in 28 slots. */
	public boolean fits()
	{
		return needed <= SLOTS;
	}

	public int over()
	{
		return Math.max(0, needed - SLOTS);
	}

	public int free()
	{
		return Math.max(0, SLOTS - used);
	}

	public long leaveWorth(boolean safeOnly)
	{
		long n = 0;
		for (Leave l : leave)
		{
			if (!safeOnly || l.home.isSafe())
			{
				n += l.worth;
			}
		}
		return n;
	}

	private static int slots(Facts f, int item, long qty)
	{
		return f.stackable(item) ? 1 : (int) Math.min(SLOTS * 4L, Math.max(0, qty));
	}

	/**
	 * @param trip the packing list
	 * @param inventory what the inventory holds, by common item id
	 * @param wornNow what is worn
	 * @param pouchNow what the rune pouch holds
	 * @param bag what the looting bag holds
	 * @param canon turns an item into its common form
	 * @param facts what is known about items
	 * @param deathbank the name of a deathbank waiting now, or null
	 * @param storeOver worth a stack must reach to be worth a trip to death storage rather than dropped
	 */
	public static Loadout solve(Trip trip, Map<Integer, Long> inventory, Map<Integer, Long> wornNow, Map<Integer, Long> pouchNow,
		Map<Integer, Long> bag, IntUnaryOperator canon, Facts facts, String deathbank, long storeOver)
	{
		Loadout l = new Loadout();
		Map<Integer, Long> inv = Items.canon(inventory, canon);
		Map<Integer, Long> wornC = Items.canon(wornNow, canon);
		Map<Integer, Long> pouchC = Items.canon(pouchNow, canon);
		boolean divine = inv.containsKey(ItemID.DIVINE_RUNE_POUCH) || inv.containsKey(ItemID.DIVINE_RUNE_POUCH_TROUVER);
		l.hasPouch = divine || inv.containsKey(ItemID.BH_RUNE_POUCH) || inv.containsKey(ItemID.BH_RUNE_POUCH_TROUVER);
		l.pouchTypes = divine ? DIVINE_POUCH_TYPES : l.hasPouch ? POUCH_TYPES : 0;
		l.hasBag = inv.containsKey(ItemID.LOOTING_BAG) || inv.containsKey(ItemID.LOOTING_BAG_OPEN);
		l.bagFree = l.hasBag ? Math.max(0, BAG_STACKS - Items.canon(bag, canon).size()) : 0;

		Map<Integer, Long> listed = new LinkedHashMap<>();
		Map<Integer, Long> listedWorn = new LinkedHashMap<>();
		for (Trip.Want w : trip.wants)
		{
			(w.worn ? listedWorn : listed).merge(w.item, w.qty, Long::sum);
		}

		// Worn first: no slots.
		listedWorn.forEach((item, qty) -> l.worn.add(new Packed(item, qty, 0, Role.LIST)));

		// Runes on the list fill the pouch, the biggest stacks first; the rest ride in the pack.
		List<Map.Entry<Integer, Long>> runes = new ArrayList<>();
		for (Map.Entry<Integer, Long> e : listed.entrySet())
		{
			if (l.hasPouch && facts.rune(e.getKey()))
			{
				runes.add(e);
			}
		}
		runes.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
		Map<Integer, Long> toPack = new LinkedHashMap<>(listed);
		for (Map.Entry<Integer, Long> e : runes)
		{
			if (l.pouch.size() >= l.pouchTypes)
			{
				break;
			}
			long in = Math.min(POUCH_EACH, e.getValue());
			l.pouch.add(new Packed(e.getKey(), in, 0, Role.LIST));
			long rest = e.getValue() - in;
			if (rest > 0)
			{
				toPack.put(e.getKey(), rest);
			}
			else
			{
				toPack.remove(e.getKey());
			}
		}
		// Runes already pouched that the list does not need: kept while there is room for their kind.
		List<Integer> pouchOut = new ArrayList<>();
		for (Map.Entry<Integer, Long> e : pouchC.entrySet())
		{
			if (listed.containsKey(e.getKey()))
			{
				continue;
			}
			if (l.pouch.size() < l.pouchTypes)
			{
				l.pouch.add(new Packed(e.getKey(), e.getValue(), 0, Role.SPARE));
			}
			else
			{
				pouchOut.add(e.getKey());
			}
		}

		// Containers carried stay; each takes a slot.
		for (Map.Entry<Integer, Long> e : inv.entrySet())
		{
			if (facts.container(e.getKey()) && !toPack.containsKey(e.getKey()) && e.getKey() != ItemID.COINS)
			{
				l.pack.add(new Packed(e.getKey(), e.getValue(), 1, Role.CONTAINER));
				l.needed++;
			}
		}
		for (Map.Entry<Integer, Long> e : toPack.entrySet())
		{
			int s = slots(facts, e.getKey(), e.getValue());
			l.pack.add(new Packed(e.getKey(), e.getValue(), s, Role.LIST));
			l.needed += s;
		}

		// The list alone too big: carry fewer of the biggest non-stacking lines.
		int over = l.over();
		if (over > 0)
		{
			List<Packed> cuttable = new ArrayList<>();
			for (Packed p : l.pack)
			{
				if (p.role == Role.LIST && !facts.stackable(p.item) && p.qty > 1)
				{
					cuttable.add(p);
				}
			}
			Map<Integer, Long> cut = new LinkedHashMap<>();
			Map<Integer, Long> left = new HashMap<>();
			for (Packed p : cuttable)
			{
				left.put(p.item, p.qty);
			}
			while (over > 0 && !cuttable.isEmpty())
			{
				Packed biggest = null;
				for (Packed p : cuttable)
				{
					if (left.get(p.item) > 1 && (biggest == null || left.get(p.item) > left.get(biggest.item)))
					{
						biggest = p;
					}
				}
				if (biggest == null)
				{
					break;
				}
				left.merge(biggest.item, -1L, Long::sum);
				cut.merge(biggest.item, 1L, Long::sum);
				over--;
			}
			cut.forEach((item, n) -> l.trim.add(new Trim(item, n)));
		}

		// What is carried but not needed: a safe home first, then room in the pack, then the bag,
		// then death storage or the floor.
		List<int[]> extras = new ArrayList<>();
		for (Map.Entry<Integer, Long> e : inv.entrySet())
		{
			int item = e.getKey();
			if (listed.containsKey(item) || listedWorn.containsKey(item) || facts.container(item) || item == ItemID.COINS)
			{
				continue;
			}
			extras.add(new int[]{item});
		}
		for (int item : pouchOut)
		{
			extras.add(new int[]{item, 1});
		}
		Map<Integer, Long> extraQty = new HashMap<>();
		for (int[] x : extras)
		{
			extraQty.put(x[0], x.length > 1 ? pouchC.get(x[0]) : inv.get(x[0]));
		}
		extras.sort((a, b) ->
		{
			double da = perSlot(facts, a[0], extraQty.get(a[0]));
			double db = perSlot(facts, b[0], extraQty.get(b[0]));
			return Double.compare(db, da);
		});
		int room = Math.max(0, SLOTS - l.needed);
		l.used = l.needed;
		boolean storageUsed = false;
		for (int[] x : extras)
		{
			int item = x[0];
			long qty = extraQty.get(item);
			long rest = qty;
			// Safe homes take what they can.
			String unit = facts.stash(item);
			if (unit != null && rest > 0)
			{
				l.leave.add(new Leave(item, 1, freed(facts, item, 1, rest - 1), Home.STASH, unit, false, facts.price(item)));
				rest -= 1;
			}
			if (rest > 0 && facts.costume(item))
			{
				// No duplicates for an ultimate ironman.
				l.leave.add(new Leave(item, 1, freed(facts, item, 1, rest - 1), Home.COSTUME, null, false, facts.price(item)));
				rest -= 1;
			}
			int lep = leprechaun(item);
			if (rest > 0 && lep > 0)
			{
				long n = Math.min(rest, lep);
				l.leave.add(new Leave(item, n, freed(facts, item, n, rest - n), Home.LEPRECHAUN, null, false, facts.price(item) * n));
				rest -= n;
			}
			if (rest <= 0)
			{
				continue;
			}
			int restSlots = slots(facts, item, rest);
			if (restSlots <= room)
			{
				l.pack.add(new Packed(item, rest, restSlots, Role.SPARE));
				room -= restSlots;
				l.used += restSlots;
				continue;
			}
			long restWorth = facts.price(item) * rest;
			if (l.bagFree > 0 && facts.tradeable(item))
			{
				l.leave.add(new Leave(item, rest, restSlots, Home.LOOTING_BAG, null, true, restWorth));
				l.bagFree--;
			}
			else if (restWorth >= storeOver || !facts.tradeable(item))
			{
				l.leave.add(new Leave(item, rest, restSlots, Home.DEATH_STORAGE, "Zulrah", true, restWorth));
				storageUsed = true;
			}
			else
			{
				l.leave.add(new Leave(item, rest, restSlots, Home.DROP, null, true, restWorth));
			}
		}
		l.leave.sort((a, b) -> a.must != b.must ? Boolean.compare(b.must, a.must) : Integer.compare(a.home.ordinal(), b.home.ordinal()));
		// A deathbank is wiped by any unsafe death, so storing by dying loses the one waiting.
		if (storageUsed && deathbank != null)
		{
			l.wipes = deathbank;
		}
		return l;
	}

	/** Slots freed by taking some of a stack away. */
	private static int freed(Facts f, int item, long taken, long remaining)
	{
		return f.stackable(item) ? (remaining <= 0 ? 1 : 0) : (int) taken;
	}

	private static double perSlot(Facts f, int item, long qty)
	{
		return f.price(item) * (double) qty / Math.max(1, slots(f, item, qty));
	}

	/** The pack as 28 slots, in order, for drawing: each entry is the stack's index in {@link #pack}, or -1 when empty. */
	public int[] grid()
	{
		int[] g = new int[SLOTS];
		java.util.Arrays.fill(g, -1);
		int at = 0;
		for (int i = 0; i < pack.size() && at < SLOTS; i++)
		{
			for (int k = 0; k < pack.get(i).slots && at < SLOTS; k++)
			{
				g[at++] = i;
			}
		}
		return g;
	}

	public List<Packed> packed()
	{
		return Collections.unmodifiableList(pack);
	}
}
