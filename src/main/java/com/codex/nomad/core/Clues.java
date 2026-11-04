package com.codex.nomad.core;

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The clue companion: clue scrolls and caskets juggled on the floor, each with the logged-in time it
 * has left, and the account's own clue log, so the pace shown is this player's, not a guess.
 *
 * <p>Facts, from the OSRS Wiki: dropped clue scrolls, challenge scrolls and Mimics "despawn in exactly
 * 61 minutes"; "Logging out pauses the timer, and any items you are juggling will follow you if you
 * hop worlds" (Guide:Clue Juggling). Other untradeable items, like reward caskets and scroll boxes,
 * "disappear after 180 seconds (three minutes)" (Drop). The game's own despawn tick is used whenever
 * it is given; these are the fallbacks.
 */
public final class Clues
{
	/** Ticks a dropped clue scroll lasts: 61 minutes at 0.6 s a tick. */
	public static final long CLUE_TICKS = 61 * 100;
	/** Ticks any other dropped untradeable lasts: 180 seconds. */
	public static final long UNTRADEABLE_TICKS = 300;
	/** Logged-in ticks a juggled piece may be left with and still be taken as gone, not picked up. */
	public static final long VANISH_SLACK = 5;
	/** Samples kept per tier for the pace. */
	public static final int SAMPLES = 40;
	/** Id of the Mimic, which can be juggled like a clue scroll (ItemID.TRAIL_MIMIC_CASKET). */
	public static final int MIMIC = 23184;
	/** Ids that stay the same through every step, so steps cannot be counted by id (ItemID.TRAIL_CLUE_BEGINNER, TRAIL_CLUE_MASTER). */
	public static final int BEGINNER_CLUE = 23182;
	public static final int MASTER_CLUE = 19835;

	/** "You have completed 12 hard Treasure Trails." (RuneLite's loot tracker reads the same line.) */
	private static final Pattern DONE = Pattern.compile("You have completed ([0-9,]+) ([a-z]+) Treasure Trails?\\.", Pattern.CASE_INSENSITIVE);
	private static final Pattern NAMED = Pattern.compile("^(Clue scroll|Challenge scroll|Scroll box|Reward casket|Casket) \\(([a-z]+)\\)$");

	/** The six tiers, with the steps a trail of each takes (Treasure Trails, OSRS Wiki). */
	public enum Tier
	{
		BEGINNER(1, 3, 23245),
		EASY(2, 4, 20546),
		MEDIUM(3, 5, 20545),
		HARD(4, 6, 20544),
		ELITE(5, 7, 20543),
		MASTER(6, 8, 19836);

		public final int minSteps;
		public final int maxSteps;
		/** The tier's reward casket, for its icon. */
		public final int casket;

		Tier(int minSteps, int maxSteps, int casket)
		{
			this.minSteps = minSteps;
			this.maxSteps = maxSteps;
			this.casket = casket;
		}

		public String label()
		{
			String n = name().toLowerCase(Locale.ENGLISH);
			return Character.toUpperCase(n.charAt(0)) + n.substring(1);
		}

		/** Whether each step is its own item, so steps can be counted as the id changes. */
		public boolean countsSteps()
		{
			return this != BEGINNER && this != MASTER;
		}

		public static Tier of(String word)
		{
			if (word == null)
			{
				return null;
			}
			for (Tier t : values())
			{
				if (t.name().equalsIgnoreCase(word.trim()))
				{
					return t;
				}
			}
			return null;
		}
	}

	/** What a clue item is. */
	public enum Piece
	{
		CLUE("clue"),
		CHALLENGE("challenge scroll"),
		SCROLL_BOX("scroll box"),
		CASKET("casket"),
		REWARD("reward casket"),
		MIMIC("Mimic");

		public final String word;

		Piece(String word)
		{
			this.word = word;
		}

		/** The ground timer when the game gives none. */
		public long fallbackTicks()
		{
			return this == CLUE || this == CHALLENGE || this == MIMIC ? CLUE_TICKS : UNTRADEABLE_TICKS;
		}

		/** A clue in hand: what the one-per-tier rule and the pace follow. */
		public boolean open()
		{
			return this == CLUE || this == CHALLENGE;
		}
	}

	/** A clue item worked out from its id and name. */
	public static final class Kind
	{
		public final Tier tier;
		public final Piece piece;

		Kind(Tier tier, Piece piece)
		{
			this.tier = tier;
			this.piece = piece;
		}

		/** "Hard clue", "Elite reward casket", "Mimic". */
		public String label()
		{
			return tier == null ? piece.word : tier.label() + " " + piece.word;
		}
	}

	/** Which clue item this is, or null for anything else. */
	public static Kind classify(int id, String name)
	{
		if (id == MIMIC)
		{
			return new Kind(null, Piece.MIMIC);
		}
		if (name == null)
		{
			return null;
		}
		Matcher m = NAMED.matcher(name.trim());
		if (!m.matches())
		{
			return null;
		}
		Tier t = Tier.of(m.group(2));
		if (t == null)
		{
			return null;
		}
		switch (m.group(1))
		{
			case "Clue scroll":
				return new Kind(t, Piece.CLUE);
			case "Challenge scroll":
				return new Kind(t, Piece.CHALLENGE);
			case "Scroll box":
				return new Kind(t, Piece.SCROLL_BOX);
			case "Reward casket":
				return new Kind(t, Piece.REWARD);
			default:
				return new Kind(t, Piece.CASKET);
		}
	}

	/** The tier and the game's own count from a completion message, or null. */
	public static Map.Entry<Tier, Integer> completed(String message)
	{
		if (message == null)
		{
			return null;
		}
		Matcher m = DONE.matcher(message);
		if (!m.find())
		{
			return null;
		}
		Tier t = Tier.of(m.group(2));
		if (t == null)
		{
			return null;
		}
		try
		{
			return new java.util.AbstractMap.SimpleImmutableEntry<>(t, Integer.parseInt(m.group(1).replace(",", "")));
		}
		catch (NumberFormatException e)
		{
			return null;
		}
	}

	/** One clue item lying on the floor. */
	public static final class Dropped
	{
		public int item;
		public Tier tier;
		public Piece piece;
		public int x;
		public int y;
		public int plane;
		/** Logged-in game ticks left before it vanishes. */
		public long ticksLeft;
		/** True once the game's own despawn tick set the timer. */
		public boolean exact;
		public long droppedAt;

		public String label()
		{
			return new Kind(tier, piece).label();
		}

		boolean at(int item, int x, int y, int plane)
		{
			return this.item == item && this.x == x && this.y == y && this.plane == plane;
		}
	}

	/** The account's log for one tier. */
	public static final class TierLog
	{
		/** The game's own count of trails done, from its message, or -1 until one is seen. */
		public int count = -1;
		/** Caskets Nomad saw while it watched. */
		public int seen;
		/** Logged-in milliseconds from taking a clue to its casket, newest last. */
		public List<Long> times = new ArrayList<>();
		/** Steps each trail took, newest last; only for tiers whose steps can be counted. */
		public List<Integer> steps = new ArrayList<>();
		/** The trail in hand: logged-in ticks carried, steps so far and the clue last held. */
		public long carried;
		public int stepsSoFar;
		public int lastId;
	}

	/** Everything kept, saved as one entry per account. */
	private List<Dropped> floor = new ArrayList<>();
	private Map<Tier, TierLog> log = new EnumMap<>(Tier.class);
	/** Caskets this session, by tier; not saved. */
	private transient Map<Tier, Integer> session;
	private transient long sessionTicks;

	public List<Dropped> floor()
	{
		return Collections.unmodifiableList(floor);
	}

	public TierLog log(Tier t)
	{
		return log.computeIfAbsent(t, k -> new TierLog());
	}

	public int session(Tier t)
	{
		return session == null ? 0 : session.getOrDefault(t, 0);
	}

	public long sessionTicks()
	{
		return sessionTicks;
	}

	// ---- the floor ----

	/** Notes a clue item the player dropped. A timer from the game wins; otherwise the wiki's. */
	public Dropped drop(int item, Kind kind, int x, int y, int plane, long gameTicks, long now)
	{
		Dropped d = new Dropped();
		d.item = item;
		d.tier = kind.tier;
		d.piece = kind.piece;
		d.x = x;
		d.y = y;
		d.plane = plane;
		d.exact = gameTicks > 0;
		d.ticksLeft = gameTicks > 0 ? gameTicks : kind.piece.fallbackTicks();
		d.droppedAt = now;
		floor.add(d);
		endRun(kind.tier, kind.piece);
		return d;
	}

	/** Dropping the clue in hand ends its run: time on the floor is not the player's pace. */
	private void endRun(Tier tier, Piece piece)
	{
		if (tier != null && piece.open())
		{
			TierLog l = log(tier);
			l.carried = 0;
			l.stepsSoFar = 0;
			l.lastId = 0;
		}
	}

	/** Sets a piece's timer from the game when the floor is seen again, after a login or a hop. */
	public boolean resync(int item, int x, int y, int plane, long gameTicks)
	{
		if (gameTicks <= 0)
		{
			return false;
		}
		for (Dropped d : floor)
		{
			if (d.at(item, x, y, plane))
			{
				d.ticksLeft = gameTicks;
				d.exact = true;
				return true;
			}
		}
		return false;
	}

	/**
	 * Takes a piece off the floor when it goes from sight.
	 *
	 * @return the piece and whether it ran out of time rather than being picked up, or null
	 */
	public Dropped lift(int item, int x, int y, int plane)
	{
		for (Iterator<Dropped> it = floor.iterator(); it.hasNext(); )
		{
			Dropped d = it.next();
			if (d.at(item, x, y, plane))
			{
				it.remove();
				return d;
			}
		}
		return null;
	}

	/** Whether a piece that went from sight with this many ticks left is taken as gone, not picked up. */
	public static boolean vanished(Dropped d)
	{
		return d.ticksLeft <= VANISH_SLACK;
	}

	/** Where a piece lies from the player: "here", "6 tiles north-east", or "upstairs" on another floor. */
	public static String where(int[] here, Dropped d)
	{
		if (here == null)
		{
			return "";
		}
		if (here[2] != d.plane)
		{
			return d.plane > here[2] ? "on the floor above" : "on the floor below";
		}
		int dx = d.x - here[0];
		int dy = d.y - here[1];
		int far = Math.max(Math.abs(dx), Math.abs(dy));
		if (far == 0)
		{
			return "under you";
		}
		String ns = Math.abs(dy) * 2 >= Math.abs(dx) ? dy > 0 ? "north" : "south" : "";
		String ew = Math.abs(dx) * 2 >= Math.abs(dy) ? dx > 0 ? "east" : "west" : "";
		String dir = ns.isEmpty() ? ew : ew.isEmpty() ? ns : ns + "-" + ew;
		return far + (far == 1 ? " tile " : " tiles ") + dir;
	}

	/** The soonest to vanish, or null. */
	public Dropped soonest()
	{
		Dropped s = null;
		for (Dropped d : floor)
		{
			if (s == null || d.ticksLeft < s.ticksLeft)
			{
				s = d;
			}
		}
		return s;
	}

	/** How many lie on the floor of a tier, every kind of piece. */
	public int onFloor(Tier t)
	{
		int n = 0;
		for (Dropped d : floor)
		{
			n += d.tier == t ? 1 : 0;
		}
		return n;
	}

	/**
	 * One logged-in game tick: the floor counts down and the clue in hand is timed.
	 *
	 * @param held the clue (or challenge scroll) carried of each tier, by id
	 * @return what ran out of time
	 */
	public List<Dropped> tick(Map<Tier, Integer> held)
	{
		sessionTicks++;
		List<Dropped> gone = new ArrayList<>();
		for (Iterator<Dropped> it = floor.iterator(); it.hasNext(); )
		{
			Dropped d = it.next();
			d.ticksLeft--;
			if (d.ticksLeft <= 0)
			{
				it.remove();
				gone.add(d);
			}
		}
		for (Map.Entry<Tier, Integer> e : held.entrySet())
		{
			TierLog l = log(e.getKey());
			l.carried++;
			if (e.getValue() != l.lastId)
			{
				l.stepsSoFar++;
				l.lastId = e.getValue();
			}
		}
		return gone;
	}

	/**
	 * A reward casket came into the pack. It closes the trail of its tier if one was being timed and
	 * no clue of that tier is still held: a casket picked up off the floor while a clue is in hand
	 * changes nothing.
	 */
	public boolean casket(Tier t, boolean clueStillHeld)
	{
		TierLog l = log(t);
		if (clueStillHeld || l.carried <= 0)
		{
			return false;
		}
		add(l.times, l.carried * 600L);
		if (t.countsSteps() && l.stepsSoFar > 0)
		{
			add(l.steps, l.stepsSoFar);
		}
		l.seen++;
		l.carried = 0;
		l.stepsSoFar = 0;
		l.lastId = 0;
		return true;
	}

	/** The game said how many trails of a tier are done: one more casket opened this session. */
	public void count(Tier t, int count)
	{
		TierLog l = log(t);
		l.count = Math.max(l.count, count);
		if (session == null)
		{
			session = new EnumMap<>(Tier.class);
		}
		session.merge(t, 1, Integer::sum);
	}

	private static <T> void add(List<T> list, T value)
	{
		list.add(value);
		while (list.size() > SAMPLES)
		{
			list.remove(0);
		}
	}

	// ---- the pace ----

	/** The middle of the player's own times for a tier, ms, or -1 with none. */
	public long median(Tier t)
	{
		List<Long> times = new ArrayList<>(log(t).times);
		if (times.isEmpty())
		{
			return -1;
		}
		Collections.sort(times);
		int n = times.size();
		return n % 2 == 1 ? times.get(n / 2) : (times.get(n / 2 - 1) + times.get(n / 2)) / 2;
	}

	/** Caskets an hour at the player's own median pace, or -1. */
	public double perHour(Tier t)
	{
		long m = median(t);
		return m <= 0 ? -1 : 3_600_000.0 / m;
	}

	/** Mean steps a trail of this tier took, or -1. */
	public double steps(Tier t)
	{
		List<Integer> s = log(t).steps;
		if (s.isEmpty())
		{
			return -1;
		}
		double sum = 0;
		for (int v : s)
		{
			sum += v;
		}
		return sum / s.size();
	}

	// ---- saving ----

	public String write(Gson gson)
	{
		return gson.toJson(this);
	}

	/** Reads the saved clue book; whatever cannot be read starts empty. */
	public static Clues read(Gson gson, String saved)
	{
		Clues c = null;
		if (saved != null && !saved.isEmpty())
		{
			try
			{
				c = gson.fromJson(saved, Clues.class);
			}
			catch (RuntimeException e)
			{
				c = null;
			}
		}
		if (c == null)
		{
			c = new Clues();
		}
		if (c.floor == null)
		{
			c.floor = new ArrayList<>();
		}
		c.floor.removeIf(d -> d == null || d.piece == null || d.ticksLeft <= 0);
		Map<Tier, TierLog> clean = new EnumMap<>(Tier.class);
		if (c.log != null)
		{
			for (Map.Entry<Tier, TierLog> e : new LinkedHashMap<>(c.log).entrySet())
			{
				if (e.getKey() != null && e.getValue() != null)
				{
					TierLog l = e.getValue();
					if (l.times == null)
					{
						l.times = new ArrayList<>();
					}
					if (l.steps == null)
					{
						l.steps = new ArrayList<>();
					}
					clean.put(e.getKey(), l);
				}
			}
		}
		c.log = clean;
		return c;
	}
}
