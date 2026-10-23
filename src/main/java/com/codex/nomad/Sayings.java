package com.codex.nomad;

import com.codex.nomad.core.Kind;
import com.codex.nomad.core.Nomad;
import com.codex.nomad.core.Store;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.api.gameval.ItemID;

/**
 * Places that keep things for you and only say so in words: Bert's Sandstorm machine in Yanille,
 * the balloon log crates, the Forestry log storage, Nulodion holding a decayed cannon and
 * Eyatlalli holding a frozen weapon. The wording follows Dude, Where's My Stuff (BSD 2-Clause).
 */
final class Sayings
{
	static final String SANDSTORM = "kept:sandstorm";
	static final String BALLOON = "kept:balloon";
	static final String FORESTRY = "kept:forestry";
	static final String NULODION = "kept:nulodion";
	static final String EYATLALLI = "kept:eyatlalli";

	private static final Pattern SAND_CHECK = Pattern.compile(
		"I have (\\d+) of your buckets and you've ground enough sandstone for (\\d+) buckets of sand");
	private static final Pattern SAND_STONE = Pattern.compile("sandstone (?:equivalent to|for) (\\d+|one) buckets? of sand");
	private static final Pattern SAND_BUCKETS = Pattern.compile("holding onto (\\d+|one) buckets? for ya");
	private static final Pattern CRATE_CHECK = Pattern.compile(
		"This crate currently contains (\\d+) logs,? (\\d+) oak logs,? (\\d+) willow logs,? (\\d+) yew logs and (\\d+) magic logs");
	private static final Pattern CRATE_PUT = Pattern.compile("You put the (.*) in the crate\\. You now have (\\d+) stored");
	private static final Pattern CRATE_LEFT = Pattern.compile("You have (\\d+) sets of (.*) left in storage");
	private static final Pattern FROZEN = Pattern.compile("Eyatlalli is holding onto your ([^.]+)\\.");
	private static final int[] CRATE_LOGS = {ItemID.LOGS, ItemID.OAK_LOGS, ItemID.WILLOW_LOGS, ItemID.YEW_LOGS, ItemID.MAGIC_LOGS};
	private static final String[] CRATE_NAMES = {"logs", "oak logs", "willow logs", "yew logs", "magic logs"};
	private static final Map<String, Integer> FORESTRY_LOGS = new LinkedHashMap<>();

	static
	{
		FORESTRY_LOGS.put("Oak", ItemID.OAK_LOGS);
		FORESTRY_LOGS.put("Willow", ItemID.WILLOW_LOGS);
		FORESTRY_LOGS.put("Yew", ItemID.YEW_LOGS);
		FORESTRY_LOGS.put("Maple", ItemID.MAPLE_LOGS);
		FORESTRY_LOGS.put("Magic", ItemID.MAGIC_LOGS);
		FORESTRY_LOGS.put("Teak", ItemID.TEAK_LOGS);
		FORESTRY_LOGS.put("Mahogany", ItemID.MAHOGANY_LOGS);
		FORESTRY_LOGS.put("Redwood", ItemID.REDWOOD_LOGS);
		FORESTRY_LOGS.put("Arctic pine", ItemID.ARCTIC_PINE_LOG);
	}

	private Sayings()
	{
	}

	private static String plain(String text)
	{
		return text.replace("<br>", " ").replaceAll("<[^>]+>", "").replace(",", "").replaceAll("\\s+", " ").trim();
	}

	private static long count(String s)
	{
		return s.equals("one") ? 1 : Long.parseLong(s);
	}

	/**
	 * Reads words shown in a dialogue or message box.
	 *
	 * @param gained what arrived in the inventory this tick, for a withdrawal from the Sandstorm
	 * @return true if a store changed
	 */
	static boolean shown(String text, Map<Integer, Long> gained, Nomad n, long now)
	{
		if (text == null || text.isEmpty())
		{
			return false;
		}
		String t = plain(text);
		Matcher m = SAND_CHECK.matcher(t);
		if (m.find())
		{
			return sand(n, now, Long.parseLong(m.group(1)), Long.parseLong(m.group(2)));
		}
		m = SAND_STONE.matcher(t);
		if (m.find())
		{
			return sand(n, now, held(n, SANDSTORM, ItemID.BUCKET_EMPTY), count(m.group(1)));
		}
		m = SAND_BUCKETS.matcher(t);
		if (m.find())
		{
			return sand(n, now, count(m.group(1)), held(n, SANDSTORM, ItemID.BUCKET_SAND));
		}
		if (t.startsWith("If ya need any more sand"))
		{
			long took = gained.getOrDefault(ItemID.BUCKET_SAND, 0L);
			return took > 0 && n.store(SANDSTORM) != null && sand(n, now,
				Math.max(0, held(n, SANDSTORM, ItemID.BUCKET_EMPTY) - took), Math.max(0, held(n, SANDSTORM, ItemID.BUCKET_SAND) - took));
		}
		m = CRATE_CHECK.matcher(t);
		if (m.find())
		{
			Map<Integer, Long> items = new LinkedHashMap<>();
			for (int i = 0; i < CRATE_LOGS.length; i++)
			{
				items.put(CRATE_LOGS[i], Long.parseLong(m.group(i + 1)));
			}
			return file(n, BALLOON, "Balloon log crates", items, now);
		}
		m = CRATE_PUT.matcher(t);
		if (m.find())
		{
			return crate(n, m.group(1), Long.parseLong(m.group(2)), now);
		}
		if (t.startsWith("Your log storage contains:"))
		{
			Map<Integer, Long> items = new LinkedHashMap<>();
			for (Map.Entry<String, Integer> e : FORESTRY_LOGS.entrySet())
			{
				Matcher l = Pattern.compile(e.getKey().replace(" ", "\\s+") + "\\s+logs?:\\s+(\\d+)").matcher(t);
				if (l.find())
				{
					items.put(e.getValue(), Long.parseLong(l.group(1)));
				}
			}
			return file(n, FORESTRY, "Forestry log storage", items, now);
		}
		return false;
	}

	/**
	 * Hears a game message.
	 *
	 * @param weapon the item in the weapon slot now, or -1
	 * @param byName finds an item by its exact name, or -1
	 * @return true if a store changed
	 */
	static boolean heard(String message, int weapon, Function<String, Integer> byName, Nomad n, long now)
	{
		Matcher m = CRATE_LEFT.matcher(message);
		if (m.find())
		{
			return crate(n, m.group(2), Long.parseLong(m.group(1)), now);
		}
		if (message.startsWith("Your cannon has decayed"))
		{
			Map<Integer, Long> parts = new LinkedHashMap<>();
			parts.put(ItemID.TWPART1, 1L);
			parts.put(ItemID.TWPART2, 1L);
			parts.put(ItemID.TWPART3, 1L);
			parts.put(ItemID.TWPART4, 1L);
			return file(n, NULODION, "Nulodion (decayed cannon)", parts, now);
		}
		if (message.startsWith("The dwarf gives you a new cannon"))
		{
			return file(n, NULODION, "Nulodion (decayed cannon)", new HashMap<>(), now);
		}
		if (message.contains("Your weapon freezes over."))
		{
			Map<Integer, Long> held = new HashMap<>();
			if (weapon > 0)
			{
				held.put(weapon, 1L);
			}
			return file(n, EYATLALLI, "Eyatlalli (frozen weapon)", held, now);
		}
		if (message.startsWith("Eyatlalli returns your lost weapon") || message.startsWith("Eyatlalli retrieves your weapon")
			|| message.startsWith("You retrieve your weapon") || message.startsWith("As the icicle bursts, your weapon"))
		{
			return file(n, EYATLALLI, "Eyatlalli (frozen weapon)", new HashMap<>(), now);
		}
		m = FROZEN.matcher(message);
		if (m.find())
		{
			int id = byName.apply(m.group(1));
			Store s = n.store(EYATLALLI);
			if (id <= 0 && s != null && !s.items.isEmpty())
			{
				return false;
			}
			Map<Integer, Long> held = new HashMap<>();
			if (id > 0)
			{
				held.put(id, 1L);
			}
			return file(n, EYATLALLI, "Eyatlalli (frozen weapon)", held, now);
		}
		return false;
	}

	private static long held(Nomad n, String key, int id)
	{
		Store s = n.store(key);
		return s == null ? 0 : s.items.getOrDefault(id, 0L);
	}

	private static boolean sand(Nomad n, long now, long buckets, long sand)
	{
		Map<Integer, Long> items = new LinkedHashMap<>();
		items.put(ItemID.BUCKET_EMPTY, buckets);
		items.put(ItemID.BUCKET_SAND, sand);
		return file(n, SANDSTORM, "Bert's Sandstorm", items, now);
	}

	private static boolean crate(Nomad n, String name, long qty, long now)
	{
		for (int i = 0; i < CRATE_NAMES.length; i++)
		{
			if (CRATE_NAMES[i].equalsIgnoreCase(name))
			{
				Store s = n.store(BALLOON);
				Map<Integer, Long> items = s == null ? new LinkedHashMap<>() : new LinkedHashMap<>(s.items);
				items.put(CRATE_LOGS[i], qty);
				return file(n, BALLOON, "Balloon log crates", items, now);
			}
		}
		return false;
	}

	/** Files what a place holds, dropping the place once it holds nothing. */
	private static boolean file(Nomad n, String key, String name, Map<Integer, Long> items, long now)
	{
		items.values().removeIf(q -> q == null || q <= 0);
		Store s = n.store(key);
		if (items.isEmpty())
		{
			if (s == null)
			{
				return false;
			}
			n.remove(key);
			return true;
		}
		if (s == null)
		{
			s = new Store(key, Kind.KEPT, name);
			n.put(s);
		}
		else if (items.equals(s.items))
		{
			s.seen = now;
			return false;
		}
		s.fill(items, now);
		return true;
	}
}
