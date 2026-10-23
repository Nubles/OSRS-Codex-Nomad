package com.codex.nomad.core;

/**
 * The kinds of place an ultimate ironman keeps things, in the order a trip should visit them:
 * what can be lost first comes first.
 */
public enum Kind
{
	FLOOR("On the floor", "Dropped items vanish when their timer runs out"),
	DEATHPILE("Deathpile", "Lost when its timer runs out"),
	DEATHBANK("Deathbank", "Wiped by any unsafe death"),
	COSTUME("Costume room", "Safe in your house"),
	STASH("STASH unit", "Safe, one outfit per unit"),
	SEED_VAULT("Seed vault", "Safe at the Farming Guild"),
	KEPT("Kept for you", "Safe; the game keeps the count, so Nomad reads it any time"),
	COFFER("Coffers", "Coins held for you; the game keeps the count"),
	OTHER("Elsewhere", "Remembered by hand");

	private final String label;
	private final String safety;

	Kind(String label, String safety)
	{
		this.label = label;
		this.safety = safety;
	}

	public String getLabel()
	{
		return label;
	}

	/** One line on how safe things are there. */
	public String getSafety()
	{
		return safety;
	}

	/** True for places the game counts by itself, so they are always exact. */
	public boolean automatic()
	{
		return this == KEPT || this == COFFER;
	}

	/** True for places whose contents can be lost. */
	public boolean perilous()
	{
		return this == FLOOR || this == DEATHPILE || this == DEATHBANK;
	}
}
