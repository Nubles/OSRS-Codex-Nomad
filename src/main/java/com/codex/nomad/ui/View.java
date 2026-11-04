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
	/** The other things carried that hold items: the seed box, plank sack, bolt pouch and the rest. */
	public List<Vessel> vessels = Collections.emptyList();
	public long carriedWorth;
	/** What a death now would drop, and keep. */
	public long dropNow;
	public long keepNow;
	public int keepCount = 3;
	/** The items a death now would keep. */
	public List<ItemLine> kept = Collections.emptyList();
	public List<Peril> perils = Collections.emptyList();
	public List<IdleLine> idle = Collections.emptyList();
	public int idleSlots;
	public List<LossLine> losses = Collections.emptyList();
	public long lostWorth;
	public long idleAfter;
	public List<NoteLine> notes = Collections.emptyList();

	// ---- stores ----
	public List<StoreView> stores = Collections.emptyList();
	public long keptWorth;

	// ---- trips ----
	public List<String> trips = Collections.emptyList();
	public String active;
	public FetchView fetch;
	/** What to carry for the active list, solved slot by slot, or null. */
	public LoadoutView loadout;
	/** Codex Wayfinder is on, so places can have a Route button. */
	public boolean wayfinder;

	// ---- route ----
	public RouteView route;

	// ---- clues ----
	public CluesView clues = new CluesView();

	/** The clue companion: what is juggled on the floor, the log by tier, and the STASH unit the step in hand needs. */
	public static final class CluesView
	{
		public List<FloorClue> floor = Collections.emptyList();
		public List<TierLine> tiers = Collections.emptyList();
		/** The STASH unit an emote step needs, or null. */
		public StashStep stash;
		/** Logged-in ticks left at which a piece is shown as about to vanish. */
		public long warnTicks = 500;
		public int sessionCaskets;
		public long sessionMs;
	}

	/** A clue item lying on the floor. */
	public static final class FloorClue
	{
		public int item;
		/** "Hard clue", "Elite reward casket". */
		public String label;
		public long ticksLeft;
		/** True once the game's own despawn time set the timer. */
		public boolean exact;
		/** "6 tiles north-east". */
		public String where;
	}

	/** One tier of the clue log. */
	public static final class TierLine
	{
		public String tier;
		public int casket;
		/** The game's own count, or -1 until it says. */
		public int count = -1;
		/** Trails Nomad timed. */
		public int timed;
		public int session;
		/** Clues and scroll boxes of this tier in the pack. */
		public int held;
		public int boxes;
		public int onFloor;
		public long medianMs = -1;
		public double perHour = -1;
		public double steps = -1;
		public int minSteps;
		public int maxSteps;
		/** Logged-in time the clue in hand has been carried, ms, or 0. */
		public long carriedMs;
	}

	/** The STASH unit an emote step needs, and whether Nomad has seen it filled. */
	public static final class StashStep
	{
		public String tier;
		/** Where the unit stands, as the clue chart names it. */
		public String chart;
		public String place;
		/** Nomad has seen items put in this unit. */
		public boolean filled;
		/** "from the clue you carry", "from the clue you last read". */
		public String source;
		public List<StashNeed> needs = Collections.emptyList();
	}

	/** One thing the STASH unit takes. */
	public static final class StashNeed
	{
		public String name;
		public int icon = -1;
		public boolean stashed;
		public boolean carried;
	}

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
		/** The quickest way back, for a deathpile: "Minigame teleport to Castle Wars, then 10 tiles". */
		public String way;
		public long worth;
		/** Game ticks left, or -1 for no timer. */
		public long ticksLeft = -1;
		/** When dropped items vanish, epoch ms, or 0. */
		public long until;
		public boolean exact = true;
		public int count;
	}

	/** A carried thing that holds items. */
	public static final class Vessel
	{
		public String name;
		public List<ItemLine> items = Collections.emptyList();
		public long seen;
		/** Carried but never checked, so what it holds is unknown. */
		public boolean unchecked;
	}

	/** Something gone for good. */
	public static final class LossLine
	{
		public long at;
		public String cause;
		public long worth;
		/** The most valuable thing in it, for the icon. */
		public int item;
		/** "Abyssal whip, Dragon boots and 12 more". */
		public String what;
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
		/** Read from the game's own counters, so always current. */
		public boolean live;
		public int distance = -1;
		public long ticksLeft = -1;
		/** When dropped items vanish, epoch ms, or 0. */
		public long until;
		public int world;
		/** Slots in use and in all, as the game last showed them, or -1 where it does not say. */
		public int used = -1;
		public int capacity = -1;
		/** The quickest way there with Codex Wayfinder: "~1m 40s · Camelot Teleport", or null. */
		public String travel;
		public Spot spot;
	}

	/** A tile to route to. */
	public static final class Spot
	{
		public final int x;
		public final int y;
		public final int plane;
		public final String name;
		public final String key;

		public Spot(int x, int y, int plane, String name, String key)
		{
			this.x = x;
			this.y = y;
			this.plane = plane;
			this.name = name;
			this.key = key;
		}
	}

	/** The packing list solved: what is worn, pouched, carried, and where the rest waits. */
	public static final class LoadoutView
	{
		/** The 28 slots in order; null where empty. */
		public List<SlotView> grid = Collections.emptyList();
		public List<ItemLine> worn = Collections.emptyList();
		public List<ItemLine> pouch = Collections.emptyList();
		public int pouchTypes;
		public boolean hasPouch;
		public boolean hasBag;
		public int bagFree;
		public int needed;
		public int used;
		public int over;
		public List<ItemLine> trim = Collections.emptyList();
		public List<LeaveView> leave = Collections.emptyList();
		/** The deathbank a death for storage would wipe, or null. */
		public String wipes;
	}

	/** One inventory slot of the solved pack. */
	public static final class SlotView
	{
		public ItemLine item;
		/** "LIST", "CONTAINER" or "SPARE". */
		public String role;
		/** The first slot of a stack spread over several. */
		public boolean first;
	}

	/** One stack to leave behind before setting off. */
	public static final class LeaveView
	{
		public ItemLine item;
		public String home;
		/** One short word for the home: "STASH", "Bag", "Zulrah". */
		public String brief;
		public String where;
		public boolean must;
		public boolean safe;
		public int slots;
		public String travel;
		public Spot spot;
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
		/** The quickest way there with Codex Wayfinder, or null. */
		public String travel;
		public Spot spot;
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
