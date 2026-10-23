package com.codex.nomad;

import com.codex.nomad.core.Kind;
import com.codex.nomad.core.Nomad;
import com.codex.nomad.core.Store;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.api.gameval.ItemID;

/**
 * The bags the game only describes in words: the coal bag and gem bag from chat when checked,
 * filled or emptied, and the fish barrel and log basket from the box shown when they are checked.
 * The wording follows the Coal Bag, Gem Sack, Fish Barrel and Log Basket plugins (BSD 2-Clause).
 */
final class Bags
{
	static final String COAL = "Coal bag";
	static final String GEMS = "Gem bag";
	static final String FISH = "Fish barrel";
	static final String LOGS = "Log basket";
	static final String BUCKET = "Bottomless bucket";
	static final String MONEYBAG = "coffer:moneybag";
	static final String SHILO = "coffer:shilo";

	private static final Pattern COAL_EMPTY = Pattern.compile("^The coal bag is (?:now\\s)?empty\\.");
	private static final Pattern COAL_HOLDS = Pattern.compile("^The coal bag (?:still\\s)?contains ([\\d]+|one) pieces? of coal\\.");
	private static final Pattern USES = Pattern.compile("(\\d+) uses");
	private static final Pattern ENTRY = Pattern.compile("(\\d+)\\s*[×x]\\s*([^,]+)");
	private static final Map<String, Integer> GEM_NAMES = new HashMap<>();

	static
	{
		gem(ItemID.UNCUT_SAPPHIRE, "sapphire", "sapphires");
		gem(ItemID.UNCUT_EMERALD, "emerald", "emeralds");
		gem(ItemID.UNCUT_RUBY, "ruby", "rubies");
		gem(ItemID.UNCUT_DIAMOND, "diamond", "diamonds");
		gem(ItemID.UNCUT_DRAGONSTONE, "dragonstone", "dragonstones");
		gem(ItemID.UNCUT_OPAL, "opal", "opals");
		gem(ItemID.UNCUT_JADE, "jade", "jades");
		gem(ItemID.UNCUT_RED_TOPAZ, "red topaz", "red topazes");
	}

	private Bags()
	{
	}

	private static void gem(int id, String... names)
	{
		for (String n : names)
		{
			GEM_NAMES.put(n, id);
		}
	}

	/**
	 * Hears a game message about the coal bag or gem bag.
	 *
	 * @return true if a bag changed
	 */
	static boolean heard(String message, Nomad n, long now)
	{
		if (message.equals("You empty all of your containers into the bank."))
		{
			boolean changed = false;
			for (String bag : new String[]{COAL, GEMS, FISH, LOGS})
			{
				if (!n.carried(bag).isEmpty())
				{
					n.carry(bag, new HashMap<>(), now);
					changed = true;
				}
			}
			return changed;
		}
		if (message.startsWith("Your bottomless compost bucket has"))
		{
			return compost(message, n, now);
		}
		if (COAL_EMPTY.matcher(message).matches())
		{
			n.carry(COAL, new HashMap<>(), now);
			return true;
		}
		Matcher coal = COAL_HOLDS.matcher(message);
		if (coal.matches())
		{
			Map<Integer, Long> held = new HashMap<>();
			held.put(ItemID.COAL, coal.group(1).equals("one") ? 1L : Long.parseLong(coal.group(1)));
			n.carry(COAL, held, now);
			return true;
		}
		if (message.startsWith("The gem") && (message.contains("is now empty.") || message.contains("is empty.")))
		{
			n.carry(GEMS, new HashMap<>(), now);
			return true;
		}
		if (message.startsWith("Sapphires:") || message.startsWith("Opal:") || message.startsWith("Opals:"))
		{
			Map<Integer, Long> held = gems(message);
			if (!held.isEmpty() || message.contains(": 0"))
			{
				n.carry(GEMS, held, now);
				return true;
			}
		}
		return false;
	}

	/** Reads "Sapphires: 2 / Emeralds: 0 / Rubies: 1 Diamonds: 0 / Dragonstones: 0". */
	static Map<Integer, Long> gems(String message)
	{
		Map<Integer, Long> held = new LinkedHashMap<>();
		Matcher m = Pattern.compile("([A-Za-z ]+):\\s*(\\d+)").matcher(message.replace("<br>", " / "));
		while (m.find())
		{
			Integer id = GEM_NAMES.get(m.group(1).trim().toLowerCase(Locale.ENGLISH));
			long q = Long.parseLong(m.group(2));
			if (id != null && q > 0)
			{
				held.merge(id, q, Long::sum);
			}
		}
		return held;
	}

	/**
	 * Reads the box shown on checking the fish barrel or log basket.
	 *
	 * @param byName finds an item by its exact name, or -1
	 * @return true if a bag changed
	 */
	static boolean checked(String text, Function<String, Integer> byName, Nomad n, long now)
	{
		String plain = text.replaceAll("<br>", " ").replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
		if (plain.contains("compost bucket"))
		{
			return compost(plain, n, now);
		}
		if (plain.startsWith("The money bag "))
		{
			return coffer(MONEYBAG, "Servant's moneybag", plain, n, now);
		}
		String bag = plain.startsWith("The barrel") ? FISH : plain.startsWith("The basket") ? LOGS : null;
		if (bag == null)
		{
			return false;
		}
		if (plain.contains("is empty"))
		{
			n.carry(bag, new HashMap<>(), now);
			return true;
		}
		int colon = plain.indexOf("contains:");
		if (colon < 0)
		{
			return false;
		}
		Map<Integer, Long> held = new LinkedHashMap<>();
		Matcher m = ENTRY.matcher(plain.substring(colon + "contains:".length()));
		while (m.find())
		{
			int id = byName.apply(m.group(2).trim());
			if (id > 0)
			{
				held.merge(id, Long.parseLong(m.group(1)), Long::sum);
			}
		}
		if (held.isEmpty())
		{
			return false;
		}
		n.carry(bag, held, now);
		return true;
	}

	/**
	 * Reads the bottomless compost bucket from a message or the box shown on checking it: how
	 * many uses it holds, and of which compost.
	 */
	static boolean compost(String text, Nomad n, long now)
	{
		String t = text.replace(",", "");
		Map<Integer, Long> held = new HashMap<>();
		if (t.contains("currently empty") || t.contains("run out") || t.startsWith("You discard"))
		{
			n.carry(BUCKET, held, now);
			return true;
		}
		Matcher m = USES.matcher(t);
		long uses;
		if (m.find())
		{
			uses = Long.parseLong(m.group(1));
		}
		else if (t.contains("one use") || t.contains("single use"))
		{
			uses = 1;
		}
		else
		{
			return false;
		}
		held.put(t.contains("ultracompost") ? ItemID.BUCKET_ULTRACOMPOST
			: t.contains("supercompost") ? ItemID.BUCKET_SUPERCOMPOST : ItemID.BUCKET_COMPOST, uses);
		n.carry(BUCKET, held, now);
		return true;
	}

	/** Reads the Shilo Village furnace coffer from the title of its menu: "Furnace coffer: 1,200". */
	static boolean shilo(String title, Nomad n, long now)
	{
		return title != null && title.startsWith("Furnace coffer: ")
			&& coffer(SHILO, "Shilo Village furnace coffer", title, n, now);
	}

	/** Files the coins a coffer's words give, removing the coffer once it is empty. */
	private static boolean coffer(String key, String name, String text, Nomad n, long now)
	{
		String digits = text.replaceAll("<[^>]+>", "").replaceAll("\\D+", "");
		long coins = digits.isEmpty() || digits.length() > 12 ? 0 : Long.parseLong(digits);
		Store s = n.store(key);
		if (coins <= 0)
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
			s = new Store(key, Kind.COFFER, name);
			n.put(s);
		}
		Map<Integer, Long> held = new HashMap<>();
		held.put(ItemID.COINS, coins);
		s.fill(held, now);
		return true;
	}
}
