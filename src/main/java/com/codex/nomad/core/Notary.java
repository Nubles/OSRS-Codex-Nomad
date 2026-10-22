package com.codex.nomad.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The noting atlas: everyone outside a bank who turns items into banknotes or back, and which
 * items they take. For each stack an ultimate ironman carries it finds the nearest way to note or
 * unnote it.
 */
public final class Notary
{
	/** Which way a merchant turns items. */
	public enum Way
	{
		NOTE, UNNOTE
	}

	/** One merchant. */
	public static final class Desk
	{
		public final String who;
		public final String where;
		public final Way way;
		/** Coins per item, -1 when the fee is small but not known, 0 when free. */
		public final int fee;
		public final boolean wilderness;
		/** Map tile, or plane -1 for many places (any farming patch, your house). */
		public final int x;
		public final int y;
		public final int plane;
		final Predicate<String> takes;
		public final String takesText;

		Desk(String who, String where, Way way, int fee, boolean wilderness, int x, int y, int plane, String takesText, Predicate<String> takes)
		{
			this.who = who;
			this.where = where;
			this.way = way;
			this.fee = fee;
			this.wilderness = wilderness;
			this.x = x;
			this.y = y;
			this.plane = plane;
			this.takesText = takesText;
			this.takes = takes;
		}

		public boolean takes(String item)
		{
			return item != null && takes.test(item.toLowerCase(Locale.ENGLISH));
		}

		public int distance(int[] here)
		{
			if (here == null || plane < 0 || here[2] != plane)
			{
				return -1;
			}
			return Math.max(Math.abs(x - here[0]), Math.abs(y - here[1]));
		}
	}

	/** Advice for one carried stack. */
	public static final class Advice
	{
		public final int item;
		public final String name;
		public final long qty;
		public final Way way;
		/** Merchants that take it, nearest first. */
		public final List<Desk> desks;
		/** Inventory slots noting it would free, or 0 for unnoting. */
		public final int frees;

		Advice(int item, String name, long qty, Way way, List<Desk> desks, int frees)
		{
			this.item = item;
			this.name = name;
			this.qty = qty;
			this.way = way;
			this.desks = desks;
			this.frees = frees;
		}

		public Desk best()
		{
			return desks.isEmpty() ? null : desks.get(0);
		}
	}

	private static final Set<String> RESOURCE_AREA = set("coal", "iron ore", "gold ore", "mithril ore", "adamantite ore",
		"runite ore", "yew logs", "magic logs", "raw dark crab", "bronze bar", "iron bar", "steel bar", "gold bar",
		"mithril bar", "adamantite bar", "runite bar", "silver bar");
	private static final Set<String> PRODUCE = set("potato", "onion", "cabbage", "tomato", "sweetcorn", "strawberry",
		"watermelon", "snape grass", "marigolds", "rosemary", "nasturtiums", "woad leaf", "limpwurt root", "white lily",
		"barley", "hammerstone hops", "asgarnian hops", "jute fibre", "yanillian hops", "krandorian hops", "wildblood hops",
		"redberries", "cadava berries", "dwellberries", "jangerberries", "whiteberries", "poison ivy berries",
		"cooking apple", "banana", "orange", "curry leaf", "pineapple", "papaya fruit", "coconut", "dragonfruit",
		"giant seaweed", "mushroom", "cactus spine", "potato cactus", "calquat fruit", "grapes", "white tree fruit");

	public static final List<Desk> DESKS = Collections.unmodifiableList(Arrays.asList(
		new Desk("Tool leprechaun", "any farming patch", Way.NOTE, 0, false, 0, 0, -1,
			"Crops, herbs and fruit you grew", n -> PRODUCE.contains(n) || n.startsWith("grimy ")),
		new Desk("Piles", "Wilderness Resource Area", Way.NOTE, 50, true, 3184, 3934, 0,
			"Ores, coal, bars, yew and magic logs, dark crabs", RESOURCE_AREA::contains),
		new Desk("Phials", "Rimmington", Way.UNNOTE, 5, false, 2949, 3213, 0, "Any noted item", n -> true),
		new Desk("Banknote exchange merchant", "Pollnivneach", Way.UNNOTE, -1, false, 3359, 2972, 0, "Any noted item", n -> true),
		new Desk("Tiles", "the Bandit Camp", Way.UNNOTE, -1, false, 3172, 2978, 0, "Any noted item", n -> true),
		new Desk("Elder Chaos druid", "the Chaos Temple, Wilderness", Way.UNNOTE, 50, true, 3239, 3619, 0, "Bones only",
			n -> n.endsWith("bones")),
		new Desk("Your servant", "your house", Way.UNNOTE, -1, false, 0, 0, -1, "Anything but bones",
			n -> !n.endsWith("bones"))
	));

	private static Set<String> set(String... names)
	{
		return new HashSet<>(Arrays.asList(names));
	}

	private Notary()
	{
	}

	/** Merchants that turn an item the given way, nearest first; ones with no fixed place last. */
	public static List<Desk> desks(String name, Way way, int[] here)
	{
		List<Desk> out = new ArrayList<>();
		for (Desk d : DESKS)
		{
			if (d.way == way && d.takes(name))
			{
				out.add(d);
			}
		}
		out.sort((a, b) ->
		{
			int da = a.distance(here);
			int db = b.distance(here);
			if (da < 0 || db < 0)
			{
				return Boolean.compare(da < 0, db < 0);
			}
			return Integer.compare(da, db);
		});
		return out;
	}

	/**
	 * @param name the item's name, unnoted
	 * @param qty how many are carried
	 * @param noted true when the stack is banknotes
	 * @param here the player's tile, or null
	 * @return what to do with the stack, or null when nothing applies or it is not worth a trip
	 */
	public static Advice advise(int item, String name, long qty, boolean noted, int[] here)
	{
		if (noted)
		{
			List<Desk> d = desks(name, Way.UNNOTE, here);
			return d.isEmpty() ? null : new Advice(item, name, qty, Way.UNNOTE, d, 0);
		}
		if (qty < 2)
		{
			return null;
		}
		List<Desk> d = desks(name, Way.NOTE, here);
		return d.isEmpty() ? null : new Advice(item, name, qty, Way.NOTE, d, (int) Math.min(27, qty - 1));
	}
}
