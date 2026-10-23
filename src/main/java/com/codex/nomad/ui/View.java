package com.codex.nomad.ui;

import com.codex.nomad.core.Kind;
import java.util.Collections;
import java.util.List;

/**
 * Everything the panel shows, worked out on the client thread and handed over whole, so the panel
 * never reads the plugin's live state while the game changes it.
 */
public final class View
{
	public boolean loggedIn;
	/** The ironman varbit: 2 is ultimate; -1 unknown. */
	public int account = -1;
	public long now;

	// ---- pack ----
	public int invUsed;
	public boolean bagKnown;
	public int bagUsed;
	public long bagSeen;
	public List<ItemLine> inventory = Collections.emptyList();
	public List<ItemLine> worn = Collections.emptyList();
	public List<ItemLine> bag = Collections.emptyList();
	public List<ItemLine> pouch = Collections.emptyList();
	public long carriedWorth;
	/** What a death now would drop, and keep. */
	public long dropNow;
	public long keepNow;
	public int keepCount = 3;
	public List<Peril> perils = Collections.emptyList();
	public List<IdleLine> idle = Collections.emptyList();
	public int idleSlots;
	public long idleAfter;
	public List<NoteLine> notes = Collections.emptyList();

	// ---- stores ----
	public List<StoreView> stores = Collections.emptyList();
	public long keptWorth;

	// ---- trips ----
	public List<String> trips = Collections.emptyList();
	public String active;
	public FetchView fetch;

	// ---- route ----
	public RouteView route;

	/** One stack. */
	public static final class ItemLine
	{
		public int id;
		public String name;
		public long qty;
		public long worth;
		public boolean noted;

		public ItemLine()
		{
		}

		public ItemLine(int id, String name, long qty, long worth)
		{
			this.id = id;
			this.name = name;
			this.qty = qty;
			this.worth = worth;
		}
	}

	/** Something that can be lost: a deathpile, the deathbank, the looting bag. */
	public static final class Peril
	{
		public Kind kind;
		public String key;
		public String title;
		public String note;
		public long worth;
		/** Game ticks left, or -1 for no timer. */
		public long ticksLeft = -1;
		public boolean exact = true;
		public int count;
	}

	/** A carried stack that has sat untouched. */
	public static final class IdleLine
	{
		public ItemLine item;
		public long idleMs;
		public int slots;
		/** The STASH unit it belongs in, or null. */
		public String home;
	}

	/** A stack that can be noted or unnoted. */
	public static final class NoteLine
	{
		public ItemLine item;
		public boolean unnote;
		public String who;
		public String where;
		public int fee;
		public boolean wilderness;
		public int frees;
		/** Tiles away, or -1. */
		public int distance = -1;
	}

	/** A place things are kept. */
	public static final class StoreView
	{
		public String key;
		public Kind kind;
		public String name;
		public List<ItemLine> items = Collections.emptyList();
		public long worth;
		public long seen;
		public boolean exact = true;
		public int distance = -1;
		public long ticksLeft = -1;
	}

	/** The active packing list, checked. */
	public static final class FetchView
	{
		public String name;
		public List<FetchLine> lines = Collections.emptyList();
		public List<StopView> stops = Collections.emptyList();
		public List<ItemLine> extra = Collections.emptyList();
		public int ready;
		public int total;
		public int missing;
		public int slots;
	}

	/** One line of a packing list. */
	public static final class FetchLine
	{
		public ItemLine want;
		public long have;
		public long missing;
		public long bagged;
		public boolean worn;
		/** Where the shortfall waits, in words, or null. */
		public String from;
	}

	/** A place to call at. */
	public static final class StopView
	{
		public String name;
		public Kind kind;
		public int distance = -1;
		public List<ItemLine> take = Collections.emptyList();
	}

	/** The route to Prifddinas. */
	public static final class RouteView
	{
		public boolean following;
		public int done;
		public int total;
		/** Index of the step in hand, or -1 when finished. */
		public int current = -1;
		public List<String> sections = Collections.emptyList();
		public List<int[]> progress = Collections.emptyList();
		public List<StepView> steps = Collections.emptyList();
	}

	/** One step of the route. */
	public static final class StepView
	{
		public int index;
		public String key;
		public String section;
		public String text;
		public List<String> tips = Collections.emptyList();
		public boolean done;
		/** Done because the game says so, so it cannot be unticked. */
		public boolean met;
		/** What the game checks: "Quest", "Agility 50", "Holding 2 items". */
		public String check;
		/** Done because a later step the game confirms is done. */
		public boolean passed;
		public boolean optional;
		/** What to click, for the step in hand: "Talk to Bob · Buy Spade". */
		public String clicks;
	}
}
