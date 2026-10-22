package com.codex.nomad.core;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The ultimate ironman route to Prifddinas, step by step, paraphrased from the OSRS Wiki's
 * walkthrough. Steps tied to a quest or a level tick themselves; steps tied to items tick when the
 * player first holds them while the step is in hand; the rest the player ticks.
 */
public final class Route
{
	public static final String SOURCE = "https://oldschool.runescape.wiki/w/Guide:Ultimate_Ironman_Walkthrough_Guide_To_Prifddinas";

	/** One step. */
	public static final class Step
	{
		public final int index;
		public final String section;
		public final String text;
		public final List<String> tips = new ArrayList<>();
		public final List<String> quests = new ArrayList<>();
		/** Skill name to level. */
		public final Map<String, Integer> skills = new LinkedHashMap<>();
		public final List<String> items = new ArrayList<>();

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

		/** True when the game itself can tell the step is done. */
		public boolean checkable()
		{
			return !quests.isEmpty() || !skills.isEmpty();
		}
	}

	/** What the game says about the player, for the steps that check themselves. */
	public interface Account
	{
		boolean questDone(String quest);

		int level(String skill);
	}

	private final List<Step> steps;
	private final List<String> sections;

	private Route(List<Step> steps)
	{
		this.steps = steps;
		List<String> s = new ArrayList<>();
		for (Step step : steps)
		{
			if (!s.contains(step.section))
			{
				s.add(step.section);
			}
		}
		sections = s;
	}

	public static Route load()
	{
		try (InputStream in = Route.class.getResourceAsStream("/com/codex/nomad/route.txt"))
		{
			if (in == null)
			{
				return new Route(Collections.emptyList());
			}
			BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
			List<String> lines = new ArrayList<>();
			String line;
			while ((line = r.readLine()) != null)
			{
				lines.add(line);
			}
			return parse(lines);
		}
		catch (Exception e)
		{
			return new Route(Collections.emptyList());
		}
	}

	static Route parse(List<String> lines)
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
					tag = tag.trim();
					int colon = tag.indexOf(':');
					if (colon < 0)
					{
						continue;
					}
					String kind = tag.substring(0, colon).trim();
					String value = tag.substring(colon + 1).trim();
					if (kind.equals("quest"))
					{
						step.quests.add(value);
					}
					else if (kind.equals("item"))
					{
						step.items.add(value);
					}
					else if (kind.equals("skill"))
					{
						int space = value.lastIndexOf(' ');
						try
						{
							step.skills.put(value.substring(0, space).trim(), Integer.parseInt(value.substring(space + 1).trim()));
						}
						catch (RuntimeException ignored)
						{
							// A skill with no level checks nothing.
						}
					}
				}
			}
			steps.add(step);
		}
		return new Route(steps);
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

	/** True when the game says every quest and level of a step is done. */
	public static boolean met(Step s, Account a)
	{
		if (!s.checkable() || a == null)
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
		for (Map.Entry<String, Integer> e : s.skills.entrySet())
		{
			if (a.level(e.getKey()) < e.getValue())
			{
				return false;
			}
		}
		return true;
	}

	/** True when the step is done: ticked by hand or met in game. */
	public boolean done(Step s, Set<String> ticked, Account a)
	{
		return ticked.contains(s.key()) || met(s, a);
	}

	/** The first step not yet done, or null when the route is finished. */
	public Step current(Set<String> ticked, Account a)
	{
		for (Step s : steps)
		{
			if (!done(s, ticked, a))
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
	 * Steps whose items are now all held and that are the step in hand: these tick themselves.
	 *
	 * @param held whether an item, by name, is carried
	 */
	public List<Step> heldNow(Set<String> ticked, Account a, Predicate<String> held)
	{
		List<Step> out = new ArrayList<>();
		Step s = current(ticked, a);
		if (s != null && !s.items.isEmpty() && !s.checkable())
		{
			boolean all = true;
			for (String item : s.items)
			{
				all &= held.test(item);
			}
			if (all)
			{
				out.add(s);
			}
		}
		return out;
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
