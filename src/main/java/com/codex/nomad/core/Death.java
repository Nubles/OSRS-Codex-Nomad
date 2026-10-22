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
