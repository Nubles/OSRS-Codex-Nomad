package com.codex.nomad.core;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Works out what a death did with the player's things, from what was carried a tick before it and
 * what is carried after the respawn: nothing (a safe death), a deathpile where the player fell, or
 * an item retrieval service (a deathbank) in a boss's lair.
 */
public final class Death
{
	public enum Kind
	{
		SAFE, PILE, DEATHBANK
	}

	/** Lairs whose deaths go to an item retrieval service, by map region. */
	private static final Map<Integer, String> LAIRS = new HashMap<>();
	/** Who gives a lair's items back, and for what, as the OSRS Wiki's item retrieval service page has it. */
	private static final Map<String, String> CLAIMS = new HashMap<>();
	/** The fee in coins, where it is paid in coins. */
	private static final Map<String, Long> FEES = new HashMap<>();
	/** The same in a few words, for tight places: "Torfinn, 100k". */
	private static final Map<String, String> BRIEFS = new HashMap<>();

	static
	{
		lair("Zulrah", 9007, 9008);
		lair("Vorkath", 9023);
		lair("Alchemical Hydra", 5536);
		lair("Hespori", 5021);
		lair("The Nightmare", 15515);
		lair("Grotesque Guardians", 6727);
		lair("Nex", 11601);
		lair("Volcanic Mine", 15263, 15262);
		lair("Theatre of Blood", 12611, 12612, 12613, 12867, 12869, 13122, 13123, 13125, 13379);
		lair("Hallowed Sepulchre", 8797, 9051, 9052, 9053, 9054, 9309, 9563, 9565, 9821, 10074, 10075, 10077);
		claim("Zulrah", "Priestess Zul-Gwenwynig", 0, "free for ultimate ironmen");
		claim("Vorkath", "Torfinn", 100_000, null);
		claim("Alchemical Hydra", "Orrvor quo Maten", 100_000, null);
		claim("Hespori", "Arno", 25_000, null);
		claim("The Nightmare", "Shura", 60_000, null);
		claim("Grotesque Guardians", "the magical chest on the roof", 50_000, null);
		claim("Nex", "the chest past the frozen door", 100_000, null);
		claim("Volcanic Mine", "Petrified Pete", -1, "150 numulite");
		claim("Theatre of Blood", "the chest north of Ver Sinhaza's bank", 100_000, null);
		claim("Hallowed Sepulchre", "the Mysterious Stranger", 25_000, null);
	}

	private static void claim(String lair, String who, long fee, String note)
	{
		CLAIMS.put(lair, "Reclaim from " + who + (note != null ? ", " + note
			: fee > 0 ? " for " + (fee >= 1000 ? fee / 1000 + "k" : String.valueOf(fee)) : ""));
		if (fee >= 0)
		{
			FEES.put(lair, fee);
		}
		String who2 = who.startsWith("the ") ? Character.toUpperCase(who.charAt(4)) + who.substring(5) : who;
		BRIEFS.put(lair, who2.replaceAll(" (on|past|north of) .*", "") + ", "
			+ (fee > 0 ? (fee >= 1000 ? fee / 1000 + "k" : String.valueOf(fee)) : fee == 0 ? "free" : note));
	}

	/** Who holds a lair's deathbank and the price, in a few words, or null. */
	public static String brief(String lair)
	{
		return lair == null ? null : BRIEFS.get(lair);
	}

	/** How to get a lair's deathbank back: "Reclaim from Torfinn for 100k", or null. */
	public static String claim(String lair)
	{
		return lair == null ? null : CLAIMS.get(lair);
	}

	/** The fee in coins to reclaim a lair's deathbank, or -1 when unknown or not paid in coins. */
	public static long fee(String lair)
	{
		return lair == null ? -1 : FEES.getOrDefault(lair, -1L);
	}

	private static void lair(String name, int... regions)
	{
		for (int r : regions)
		{
			LAIRS.put(r, name);
		}
	}

	/** The lair a region belongs to, or null. */
	public static String lair(int region)
	{
		return LAIRS.get(region);
	}

	public final Kind kind;
	/** Where it went: the lair's name for a deathbank. */
	public final String place;
	public final Map<Integer, Long> lost;

	private Death(Kind kind, String place, Map<Integer, Long> lost)
	{
		this.kind = kind;
		this.place = place;
		this.lost = lost;
	}

	/**
	 * @param before everything carried a tick before the death, looting bag included
	 * @param after everything carried once respawned
	 * @param region the map region the player died in
	 * @param instanced true when the death was in an instance, whose deaths go to a retrieval service
	 */
	public static Death of(Map<Integer, Long> before, Map<Integer, Long> after, int region, boolean instanced)
	{
		Map<Integer, Long> lost = Items.minus(before, after);
		if (lost.isEmpty())
		{
			return new Death(Kind.SAFE, null, Collections.emptyMap());
		}
		String lair = lair(region);
		if (lair != null)
		{
			return new Death(Kind.DEATHBANK, lair, lost);
		}
		if (instanced)
		{
			return new Death(Kind.DEATHBANK, "an instance", lost);
		}
		return new Death(Kind.PILE, null, lost);
	}
}
