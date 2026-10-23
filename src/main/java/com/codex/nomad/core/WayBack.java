package com.codex.nomad.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The quickest way back to a deathpile. An ultimate ironman respawns with nothing, so no runes, no
 * jewellery and no tablets: what is left is running, the home teleport and the minigame teleport,
 * both free on a cooldown. Each is costed in tiles: run 2 a tick, about 40 to cast, and 200 for each
 * minute still to wait. Distances are as the crow flies.
 */
public final class WayBack
{
	/** Tiles a minute of waiting is worth: two a tick, a hundred ticks a minute. */
	static final int TILES_PER_MINUTE = 200;
	/** A teleport's cast, in tiles of running. */
	static final int CAST = 40;

	/** A place a free teleport lands. */
	public static final class Spot
	{
		public final String name;
		public final int x;
		public final int y;

		public Spot(String name, int x, int y)
		{
			this.name = name;
			this.x = x;
			this.y = y;
		}
	}

	/** One way back. */
	public static final class Way
	{
		public final String how;
		/** Tiles still to run once there. */
		public final int then;
		/** Minutes until it can be cast; 0 when ready. */
		public final int wait;
		final int cost;

		Way(String how, int then, int wait, int cost)
		{
			this.how = how;
			this.then = then;
			this.wait = wait;
			this.cost = cost;
		}

		/** "Home teleport to Lumbridge, then 40 tiles (ready in 12 min)". */
		public String say()
		{
			return how + (then > 0 ? ", then " + then + (then == 1 ? " tile" : " tiles") : "") + (wait > 0 ? " (ready in " + wait + " min)" : "");
		}
	}

	/** Home teleport landings by spellbook varbit value: standard, Ancient, Lunar, Arceuus. */
	public static final Spot[] HOMES = {
		new Spot("Lumbridge", 3222, 3218),
		new Spot("Edgeville", 3087, 3496),
		new Spot("Lunar Isle", 2100, 3914),
		new Spot("the Dark Altar", 1712, 3883),
	};

	public static final Spot CASTLE_WARS = new Spot("Castle Wars", 2440, 3090);
	public static final Spot FEROX = new Spot("the Ferox Enclave (Clan Wars)", 3151, 3636);
	public static final Spot SOUL_WARS = new Spot("Soul Wars", 2210, 2857);
	public static final Spot BURTHORPE = new Spot("Burthorpe Games Room", 2898, 3563);
	public static final Spot TRAWLER = new Spot("Fishing Trawler", 2658, 3158);
	public static final Spot PEST_CONTROL = new Spot("Pest Control", 2659, 2676);
	public static final Spot TITHE = new Spot("Tithe Farm", 1796, 3501);
	public static final Spot MORTTON = new Spot("Shades of Mort'ton", 3505, 3315);
	public static final Spot BREWING = new Spot("Trouble Brewing", 3812, 3021);

	private WayBack()
	{
	}

	static int tiles(int x1, int y1, int x2, int y2)
	{
		return Math.max(Math.abs(x1 - x2), Math.abs(surface(y1) - surface(y2)));
	}

	/** Most dungeons lie 6,400 rows north of the land above them on the map. */
	static int surface(int y)
	{
		return y >= 8700 && y < 11000 ? y - 6400 : y;
	}

	/**
	 * @param here where the player stands, {x, y}
	 * @param pile where the pile lies, {x, y}
	 * @param home the home teleport's landing, or null
	 * @param homeWait minutes until the home teleport is ready
	 * @param minigames the minigame teleports this account has
	 * @param minigameWait minutes until the minigame teleport is ready
	 * @return the ways back that beat or match running, best first, running among them
	 */
	public static List<Way> plan(int[] here, int[] pile, Spot home, int homeWait, List<Spot> minigames, int minigameWait)
	{
		List<Way> ways = new ArrayList<>();
		int run = tiles(here[0], here[1], pile[0], pile[1]);
		ways.add(new Way("Run there", run, 0, run));
		if (home != null)
		{
			ways.add(way("Home teleport to " + home.name, home, pile, homeWait));
		}
		Way best = null;
		for (Spot s : minigames)
		{
			Way w = way("Minigame teleport to " + s.name, s, pile, minigameWait);
			if (best == null || w.cost < best.cost)
			{
				best = w;
			}
		}
		if (best != null)
		{
			ways.add(best);
		}
		ways.sort(Comparator.comparingInt(w -> w.cost));
		List<Way> out = new ArrayList<>();
		for (Way w : ways)
		{
			if (w.cost <= run)
			{
				out.add(w);
			}
		}
		return out;
	}

	private static Way way(String how, Spot s, int[] pile, int wait)
	{
		int then = tiles(s.x, s.y, pile[0], pile[1]);
		return new Way(how, then, wait, CAST + then + Math.max(0, wait) * TILES_PER_MINUTE);
	}

	/** Minutes left on a free teleport's cooldown, from its varp (minutes since 1970 it was last cast). */
	public static int wait(int lastCastMinute, int cooldownMinutes, long nowMs)
	{
		long since = nowMs / 60_000 - lastCastMinute;
		return (int) Math.max(0, cooldownMinutes - since);
	}
}
