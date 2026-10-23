package com.codex.nomad.core;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The ultimate ironman route to Prifddinas, step by step, paraphrased from the OSRS Wiki's
 * walkthrough. Each step says how the game shows it is done:
 * <ul>
 * <li>lasting checks (quests, levels, quest points, unlocks, diaries, combat achievements) are read
 * from the game at any time;</li>
 * <li>moment checks (items held or owned, a place reached, an empty inventory, coins carried, a chat
 * line, a deathbank, where your house is) are watched while the step is in hand and tick it the
 * moment they all hold;</li>
 * <li>a step with no check of its own counts as done once the next step the game can confirm is
 * done, since the route is followed in order.</li>
 * </ul>
 * Each step can also name where to go and who to talk to, for the in-game guidance.
 */
public final class Route
{
	public static final String SOURCE = "https://oldschool.runescape.wiki/w/Guide:Ultimate_Ironman_Walkthrough_Guide_To_Prifddinas";

	/** A varbit or varp test: id, then the value it must reach (or equal, when exact). */
	public static final class Var
	{
		public final int id;
		public final int value;
		public final boolean exact;

		Var(int id, int value, boolean exact)
		{
			this.id = id;
			this.value = value;
			this.exact = exact;
		}

		boolean test(int now)
		{
			return exact ? now == value : now >= value;
		}
	}

	/** One step. */
	public static final class Step
	{
		public final int index;
		public final String section;
		public final String text;
		public final List<String> tips = new ArrayList<>();

		// lasting checks
		public final List<String> quests = new ArrayList<>();
		public final List<String> started = new ArrayList<>();
		/** Skill name to level. */
		public final Map<String, Integer> skills = new LinkedHashMap<>();
		public int questPoints;
		public int combat;
		public final List<Var> varbits = new ArrayList<>();
		public final List<Var> varps = new ArrayList<>();
		/** Combat achievement tier that must be complete: easy, medium... */
		public String tier;

		// moment checks
		public final List<String> items = new ArrayList<>();
		public final List<String> owned = new ArrayList<>();
		public final List<String> gone = new ArrayList<>();
		/** x, y, plane, reach. */
		public int[] at;
		/** x1, y1, x2, y2, plane (-1 any). */
		public int[] area;
		/** Inventory empty but for these names; null when not checked. */
		public List<String> emptyBut;
		/** Coins carried at least this many (or at most, when coinsAtMost). */
		public long coins = -1;
		public boolean coinsAtMost;
		public final List<String> chat = new ArrayList<>();
		/** True: a deathbank must exist; false: none may; null: not checked. */
		public Boolean deathbank;
		/** The POH_HOUSE_LOCATION value the house must be in, or -1. */
		public int house = -1;
		/** Furniture to see built in your house: each entry is a set of object ids, any of which will do. */
		public final List<int[]> built = new ArrayList<>();

		// guidance
		/** x, y, plane to head for, or null. */
		public int[] go;
		/** Head for your house portal, wherever the house is. */
		public boolean goHouse;
		public final List<String> npcs = new ArrayList<>();
		public final List<Integer> npcIds = new ArrayList<>();
		/** Objects to click, by id. */
		public final List<Integer> objects = new ArrayList<>();
		/** Items to click in the inventory, by name. */
		public final List<String> use = new ArrayList<>();
		/** Items to buy in a shop, by name. */
		public final List<String> shop = new ArrayList<>();
		/** Items to pick up off the ground, by name. */
		public final List<String> ground = new ArrayList<>();
		/** Chat options to choose, by their text. */
		public final List<String> say = new ArrayList<>();
		public boolean optional;

		Step(int index, String section, String text)
		{
			this.index = index;
			this.section = section;
			this.text = text;
		}

		/** A key that survives steps being added elsewhere in the route. */
		public String key()
		{
			return Integer.toHexString((section + "|" + text).toLowerCase(Locale.ENGLISH).hashCode());
		}

		/** True when the step has a check the game can read at any time. */
		public boolean lasting()
		{
			return !quests.isEmpty() || !started.isEmpty() || !skills.isEmpty() || questPoints > 0 || combat > 0
				|| !varbits.isEmpty() || !varps.isEmpty() || tier != null;
		}

		/** True when the step has a check that only holds in the moment. */
		public boolean moment()
		{
			return !items.isEmpty() || !owned.isEmpty() || !gone.isEmpty() || at != null || area != null || emptyBut != null
				|| coins >= 0 || !chat.isEmpty() || deathbank != null || house >= 0 || !built.isEmpty();
		}

		/** True when the game confirms the step for good, so it cannot be unticked. */
		public boolean checkable()
		{
			return lasting() && !moment();
		}

		/** True when the game ticks this step by any means. */
		public boolean watched()
		{
			return lasting() || moment();
		}
	}

	/** Everything to click for a step: people, objects, items in the inventory, a shop or on the ground, chat options. */
	public static final class Lead
	{
		public final List<String> npcs = new ArrayList<>();
		public final List<Integer> npcIds = new ArrayList<>();
		public final List<Integer> objects = new ArrayList<>();
		public final List<String> use = new ArrayList<>();
		public final List<String> shop = new ArrayList<>();
		public final List<String> ground = new ArrayList<>();
		public final List<String> say = new ArrayList<>();

		public boolean isEmpty()
		{
			return npcs.isEmpty() && npcIds.isEmpty() && objects.isEmpty() && use.isEmpty() && shop.isEmpty() && ground.isEmpty() && say.isEmpty();
		}
	}

	/** What the game says about the player for good. */
	public interface Account
	{
		boolean questDone(String quest);

		int level(String skill);

		default boolean questStarted(String quest)
		{
			return questDone(quest);
		}

		default int questPoints()
		{
			return 0;
		}

		default int combat()
		{
			return 0;
		}

		default int varbit(int id)
		{
			return 0;
		}

		default int varp(int id)
		{
			return 0;
		}

		default boolean tierDone(String tier)
		{
			return false;
		}
	}

	/** What is true this moment, for the checks watched while a step is in hand. */
	public interface Moment
	{
		/** Carried, worn, in the looting bag or rune pouch, by name in lower case. */
		boolean held(String name);

		/** Held or kept in any store Nomad knows. */
		default boolean owned(String name)
		{
			return held(name);
		}

		/** x, y, plane, or null when not logged in. */
		int[] here();

		/** Names in the inventory, in lower case. */
		List<String> inventory();

		long coins();

		/** True when a game message containing this text arrived lately. */
		boolean heard(String text);

		boolean deathbank();

		/** True when one of these objects is in the scene now. */
		default boolean sees(int[] objects)
		{
			return false;
		}
	}

	private final List<Step> steps;
	private final List<String> sections;
	/** For each step, the index of the next lasting step after it, or -1. */
	private final int[] nextLasting;
	private final Map<String, int[]> questStarts;
	/** Who or what starts each quest: npc or object ids, the first entry 0 for npcs and 1 for objects. */
	private final Map<String, int[]> questLeads;

	private Route(List<Step> steps, Map<String, int[]> questStarts, Map<String, int[]> questLeads)
	{
		this.steps = steps;
		this.questStarts = questStarts;
		this.questLeads = questLeads;
		List<String> s = new ArrayList<>();
		for (Step step : steps)
		{
			if (!s.contains(step.section))
			{
				s.add(step.section);
			}
		}
		sections = s;
		nextLasting = new int[steps.size()];
		int next = -1;
		for (int i = steps.size() - 1; i >= 0; i--)
		{
			nextLasting[i] = next;
			if (steps.get(i).checkable() && !steps.get(i).optional)
			{
				next = i;
			}
		}
	}

	public static Route load()
	{
		List<String> starts = lines("/com/codex/nomad/quest-starts.txt");
		return parse(lines("/com/codex/nomad/route.txt"), starts(starts), leads(starts));
	}

	private static List<String> lines(String resource)
	{
		List<String> lines = new ArrayList<>();
		try (InputStream in = Route.class.getResourceAsStream(resource))
		{
			if (in != null)
			{
				BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
				String line;
				while ((line = r.readLine()) != null)
				{
					lines.add(line);
				}
			}
		}
		catch (Exception ignored)
		{
			// A missing resource leaves the route empty.
		}
		return lines;
	}

	/** Lines of "quest | x,y,plane | npc:ids or obj:ids". */
	static Map<String, int[]> starts(List<String> lines)
	{
		Map<String, int[]> out = new HashMap<>();
		for (String line : lines)
		{
			String[] parts = line.split("\\|");
			if (line.startsWith("#") || parts.length < 2)
			{
				continue;
			}
			try
			{
				int[] p = ints(parts[1]);
				if (p.length == 3)
				{
					out.put(parts[0].trim(), p);
				}
			}
			catch (RuntimeException ignored)
			{
				// A malformed line names no place.
			}
		}
		return out;
	}

	static Map<String, int[]> leads(List<String> lines)
	{
		Map<String, int[]> out = new HashMap<>();
		for (String line : lines)
		{
			String[] parts = line.split("\\|");
			if (line.startsWith("#") || parts.length < 3)
			{
				continue;
			}
			String lead = parts[2].trim();
			int colon = lead.indexOf(':');
			try
			{
				int[] ids = ints(lead.substring(colon + 1));
				int[] entry = new int[ids.length + 1];
				entry[0] = lead.startsWith("obj") ? 1 : 0;
				System.arraycopy(ids, 0, entry, 1, ids.length);
				out.put(parts[0].trim(), entry);
			}
			catch (RuntimeException ignored)
			{
				// A malformed lead names no one.
			}
		}
		return out;
	}

	static Route parse(List<String> lines)
	{
		return parse(lines, Collections.emptyMap(), Collections.emptyMap());
	}

	static Route parse(List<String> lines, Map<String, int[]> questStarts, Map<String, int[]> questLeads)
	{
		List<Step> steps = new ArrayList<>();
		for (String raw : lines)
		{
			String line = raw.trim();
			if (line.isEmpty() || line.startsWith("#"))
			{
				continue;
			}
			if (line.startsWith("~"))
			{
				if (!steps.isEmpty())
				{
					steps.get(steps.size() - 1).tips.add(line.substring(1).trim());
				}
				continue;
			}
			String[] parts = line.split("\\|", -1);
			if (parts.length < 2)
			{
				continue;
			}
			Step step = new Step(steps.size(), parts[0].trim(), parts[1].trim());
			if (parts.length > 2)
			{
				for (String tag : parts[2].split(";"))
				{
					tag(step, tag.trim());
				}
			}
			steps.add(step);
		}
		return new Route(steps, questStarts, questLeads);
	}

	private static void tag(Step step, String tag)
	{
		int colon = tag.indexOf(':');
		String kind = colon < 0 ? tag : tag.substring(0, colon).trim();
		String value = colon < 0 ? "" : tag.substring(colon + 1).trim();
		try
		{
			switch (kind)
			{
				case "quest":
					step.quests.add(value);
					break;
				case "started":
					step.started.add(value);
					break;
				case "skill":
				{
					int space = value.lastIndexOf(' ');
					step.skills.put(value.substring(0, space).trim(), Integer.parseInt(value.substring(space + 1).trim()));
					break;
				}
				case "qp":
					step.questPoints = Integer.parseInt(value);
					break;
				case "combat":
					step.combat = Integer.parseInt(value);
					break;
				case "varbit":
					step.varbits.add(var(value));
					break;
				case "varp":
					step.varps.add(var(value));
					break;
				case "ca":
					step.tier = value.toLowerCase(Locale.ENGLISH);
					break;
				case "item":
					step.items.add(value);
					break;
				case "owned":
					step.owned.add(value);
					break;
				case "gone":
					step.gone.add(value);
					break;
				case "at":
				{
					int[] p = ints(value);
					step.at = new int[]{p[0], p[1], p.length > 2 ? p[2] : 0, p.length > 3 ? p[3] : 8};
					break;
				}
				case "area":
				{
					int[] p = ints(value);
					step.area = new int[]{Math.min(p[0], p[2]), Math.min(p[1], p[3]), Math.max(p[0], p[2]), Math.max(p[1], p[3]),
						p.length > 4 ? p[4] : -1};
					break;
				}
				case "empty":
				{
					List<String> allow = new ArrayList<>();
					for (String a : value.split(","))
					{
						if (!a.trim().isEmpty())
						{
							allow.add(a.trim().toLowerCase(Locale.ENGLISH));
						}
					}
					step.emptyBut = allow;
					break;
				}
				case "coins":
					step.coinsAtMost = value.startsWith("<");
					step.coins = Long.parseLong(value.replace("<", "").replace(">", "").replace("=", "").trim());
					break;
				case "chat":
					step.chat.add(value);
					break;
				case "deathbank":
					step.deathbank = true;
					break;
				case "nodeathbank":
					step.deathbank = false;
					break;
				case "built":
				{
					List<Integer> ids = new ArrayList<>();
					for (String part : value.split(","))
					{
						String[] range = part.trim().split("-");
						int from = Integer.parseInt(range[0].trim());
						int to = range.length > 1 ? Integer.parseInt(range[1].trim()) : from;
						for (int id = from; id <= to; id++)
						{
							ids.add(id);
						}
					}
					int[] set = new int[ids.size()];
					for (int i = 0; i < set.length; i++)
					{
						set[i] = ids.get(i);
					}
					step.built.add(set);
					break;
				}
				case "house":
					step.house = Integer.parseInt(value);
					break;
				case "go":
				{
					if (value.equals("house"))
					{
						step.goHouse = true;
						break;
					}
					int[] p = ints(value);
					step.go = new int[]{p[0], p[1], p.length > 2 ? p[2] : 0};
					break;
				}
				case "npc":
					step.npcs.add(value);
					break;
				case "npcid":
					for (int id : ids(value))
					{
						step.npcIds.add(id);
					}
					break;
				case "obj":
					for (int id : ids(value))
					{
						step.objects.add(id);
					}
					break;
				case "use":
					step.use.add(value);
					break;
				case "shop":
					step.shop.add(value);
					break;
				case "ground":
					step.ground.add(value);
					break;
				case "say":
					step.say.add(value);
					break;
				case "optional":
					step.optional = true;
					break;
				default:
					break;
			}
		}
		catch (RuntimeException ignored)
		{
			// A malformed tag checks nothing.
		}
	}

	private static Var var(String value)
	{
		String v = value.replace(" ", "");
		boolean exact = v.contains("=") && !v.contains(">=");
		String[] p = v.split(">=|=");
		return new Var(Integer.parseInt(p[0]), p.length > 1 ? Integer.parseInt(p[1]) : 1, exact);
	}

	/** Ids separated by commas, with ranges written a-b. */
	private static int[] ids(String value)
	{
		List<Integer> ids = new ArrayList<>();
		for (String part : value.split(","))
		{
			String[] range = part.trim().split("-");
			int from = Integer.parseInt(range[0].trim());
			int to = range.length > 1 ? Integer.parseInt(range[1].trim()) : from;
			for (int id = from; id <= to; id++)
			{
				ids.add(id);
			}
		}
		int[] out = new int[ids.size()];
		for (int i = 0; i < out.length; i++)
		{
			out[i] = ids.get(i);
		}
		return out;
	}

	private static int[] ints(String s)
	{
		String[] p = s.split(",");
		int[] out = new int[p.length];
		for (int i = 0; i < p.length; i++)
		{
			out[i] = Integer.parseInt(p[i].trim());
		}
		return out;
	}

	public List<Step> steps()
	{
		return Collections.unmodifiableList(steps);
	}

	public List<String> sections()
	{
		return Collections.unmodifiableList(sections);
	}

	public int size()
	{
		return steps.size();
	}

	/** True when every lasting check of a step holds; false for a step with none. */
	public static boolean lastingMet(Step s, Account a)
	{
		if (!s.lasting() || a == null)
		{
			return false;
		}
		for (String q : s.quests)
		{
			if (!a.questDone(q))
			{
				return false;
			}
		}
		for (String q : s.started)
		{
			if (!a.questStarted(q))
			{
				return false;
			}
		}
		for (Map.Entry<String, Integer> e : s.skills.entrySet())
		{
			if (a.level(e.getKey()) < e.getValue())
			{
				return false;
			}
		}
		if (s.questPoints > 0 && a.questPoints() < s.questPoints || s.combat > 0 && a.combat() < s.combat)
		{
			return false;
		}
		for (Var v : s.varbits)
		{
			if (!v.test(a.varbit(v.id)))
			{
				return false;
			}
		}
		for (Var v : s.varps)
		{
			if (!v.test(a.varp(v.id)))
			{
				return false;
			}
		}
		return s.tier == null || a.tierDone(s.tier);
	}

	/** True when the game confirms a step for good: lasting checks only, all holding. */
	public static boolean met(Step s, Account a)
	{
		return s.checkable() && lastingMet(s, a);
	}

	/** True when every check of a step holds right now. */
	public static boolean now(Step s, Account a, Moment m)
	{
		if (!s.watched() || m == null || s.lasting() && !lastingMet(s, a))
		{
			return false;
		}
		for (String i : s.items)
		{
			if (!m.held(i.toLowerCase(Locale.ENGLISH)))
			{
				return false;
			}
		}
		for (String i : s.owned)
		{
			if (!m.owned(i.toLowerCase(Locale.ENGLISH)))
			{
				return false;
			}
		}
		for (String i : s.gone)
		{
			if (m.held(i.toLowerCase(Locale.ENGLISH)))
			{
				return false;
			}
		}
		int[] here = m.here();
		if ((s.at != null || s.area != null) && here == null)
		{
			return false;
		}
		if (s.at != null && (here[2] != s.at[2] || Math.max(Math.abs(here[0] - s.at[0]), Math.abs(here[1] - s.at[1])) > s.at[3]))
		{
			return false;
		}
		if (s.area != null && (here[0] < s.area[0] || here[0] > s.area[2] || here[1] < s.area[1] || here[1] > s.area[3]
			|| s.area[4] >= 0 && here[2] != s.area[4]))
		{
			return false;
		}
		if (s.emptyBut != null)
		{
			for (String i : m.inventory())
			{
				if (!s.emptyBut.contains(i))
				{
					return false;
				}
			}
		}
		if (s.coins >= 0 && (s.coinsAtMost ? m.coins() > s.coins : m.coins() < s.coins))
		{
			return false;
		}
		for (String c : s.chat)
		{
			if (!m.heard(c))
			{
				return false;
			}
		}
		if (s.deathbank != null && m.deathbank() != s.deathbank)
		{
			return false;
		}
		for (int[] set : s.built)
		{
			if (!m.sees(set))
			{
				return false;
			}
		}
		return s.house < 0 || a != null && a.varbit(HOUSE_VARBIT) == s.house;
	}

	/** VarbitID.POH_HOUSE_LOCATION. */
	public static final int HOUSE_VARBIT = 2187;
	/** The house portal for each POH_HOUSE_LOCATION value. */
	static final Map<Integer, int[]> HOUSE_PORTALS = new HashMap<>();

	static
	{
		HOUSE_PORTALS.put(1, new int[]{2954, 3224, 0});
		HOUSE_PORTALS.put(2, new int[]{2894, 3465, 0});
		HOUSE_PORTALS.put(3, new int[]{3340, 3004, 0});
		HOUSE_PORTALS.put(4, new int[]{2670, 3632, 0});
		HOUSE_PORTALS.put(5, new int[]{2758, 3178, 0});
		HOUSE_PORTALS.put(6, new int[]{2544, 3096, 0});
		HOUSE_PORTALS.put(8, new int[]{1743, 3517, 0});
	}

	/** True when the step is done: ticked by hand or in the moment, met in game, or passed. */
	public boolean done(Step s, Set<String> ticked, Account a)
	{
		return ticked.contains(s.key()) || met(s, a) || passed(s, a);
	}

	/**
	 * True when a step the game cannot confirm for good lies behind one it has confirmed: the route
	 * runs in order, so the player is past it.
	 */
	public boolean passed(Step s, Account a)
	{
		if (s.checkable())
		{
			return false;
		}
		int next = nextLasting[s.index];
		return next >= 0 && met(steps.get(next), a);
	}

	/** The first step not yet done, skipping optional ones, or null when the route is finished. */
	public Step current(Set<String> ticked, Account a)
	{
		for (Step s : steps)
		{
			if (!s.optional && !done(s, ticked, a))
			{
				return s;
			}
		}
		return null;
	}

	public int doneCount(Set<String> ticked, Account a)
	{
		int n = 0;
		for (Step s : steps)
		{
			if (done(s, ticked, a))
			{
				n++;
			}
		}
		return n;
	}

	/**
	 * The steps in hand whose moment checks all hold now, in order: each ticks, and the one after it
	 * is tried in turn.
	 */
	public List<Step> observe(Set<String> ticked, Account a, Moment m)
	{
		List<Step> out = new ArrayList<>();
		java.util.HashSet<String> t = new java.util.HashSet<>(ticked);
		for (int guard = 0; guard < 8; guard++)
		{
			Step s = current(t, a);
			if (s == null || !s.moment() || !now(s, a, m))
			{
				break;
			}
			out.add(s);
			t.add(s.key());
		}
		return out;
	}

	/** Where to head for a step: its own place, or where the first quest it needs starts. */
	public int[] target(Step s, Account a)
	{
		if (s.go != null)
		{
			return s.go;
		}
		if (s.goHouse && a != null)
		{
			int[] portal = HOUSE_PORTALS.get(a.varbit(HOUSE_VARBIT));
			if (portal != null)
			{
				return portal;
			}
		}
		if (s.at != null)
		{
			return new int[]{s.at[0], s.at[1], s.at[2]};
		}
		if (s.area != null)
		{
			return new int[]{(s.area[0] + s.area[2]) / 2, (s.area[1] + s.area[3]) / 2, Math.max(0, s.area[4])};
		}
		List<String> qs = new ArrayList<>(s.started);
		qs.addAll(s.quests);
		for (String q : qs)
		{
			if (a != null && (s.started.contains(q) ? a.questStarted(q) : a.questDone(q)))
			{
				continue;
			}
			int[] p = questStarts.get(q);
			if (p != null)
			{
				return p;
			}
		}
		return null;
	}

	/**
	 * What to click for a step: what it names, the items it asks for (bought in a shop when the
	 * step is to buy them, picked up when it is to take them), and who starts the quest it needs.
	 */
	public Lead lead(Step s, Account a)
	{
		Lead l = new Lead();
		if (s == null)
		{
			return l;
		}
		l.npcs.addAll(s.npcs);
		l.npcIds.addAll(s.npcIds);
		l.objects.addAll(s.objects);
		l.use.addAll(s.use);
		l.shop.addAll(s.shop);
		l.ground.addAll(s.ground);
		l.say.addAll(s.say);
		String verb = s.text.toLowerCase(Locale.ENGLISH);
		if (verb.startsWith("buy ") || verb.contains(", buy ") || verb.contains(" and buy ") || verb.contains(" then buy "))
		{
			l.shop.addAll(s.items);
		}
		if (verb.startsWith("take ") || verb.startsWith("pick up "))
		{
			l.ground.addAll(s.items);
		}
		List<String> qs = new ArrayList<>(s.started);
		qs.addAll(s.quests);
		for (String q : qs)
		{
			if (a != null && a.questStarted(q))
			{
				continue;
			}
			int[] lead = questLeads.get(q);
			if (lead != null)
			{
				for (int i = 1; i < lead.length; i++)
				{
					(lead[0] == 1 ? l.objects : l.npcIds).add(lead[i]);
				}
				break;
			}
		}
		return l;
	}

	/** The quest a step needs that is under way but not done, or null: inside it, Quest Helper leads. */
	public static String inQuest(Step s, Account a)
	{
		if (s == null || a == null)
		{
			return null;
		}
		for (String q : s.quests)
		{
			if (a.questStarted(q) && !a.questDone(q))
			{
				return q;
			}
		}
		return null;
	}

	/** Where a quest starts, or null. */
	public int[] questStart(String quest)
	{
		return questStarts.get(quest);
	}

	/** How far through a section the player is: done and total. */
	public int[] section(String name, Set<String> ticked, Account a)
	{
		int done = 0;
		int all = 0;
		for (Step s : steps)
		{
			if (s.section.equals(name))
			{
				all++;
				if (done(s, ticked, a))
				{
					done++;
				}
			}
		}
		return new int[]{done, all};
	}
}
