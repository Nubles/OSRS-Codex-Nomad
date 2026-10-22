package com.codex.nomad.core;

/**
 * The kinds of place an ultimate ironman keeps things, in the order a trip should visit them:
 * what can be lost first comes first.
 */
public enum Kind
{
	DEATHPILE("Deathpile", "Lost when its timer runs out"),
	DEATHBANK("Deathbank", "Wiped by any unsafe death"),
	COSTUME("Costume room", "Safe in your house"),
	STASH("STASH unit", "Safe, one outfit per unit"),
	SEED_VAULT("Seed vault", "Safe at the Farming Guild"),
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

	/** True for places whose contents can be lost. */
	public boolean perilous()
	{
		return this == DEATHPILE || this == DEATHBANK;
	}
}
