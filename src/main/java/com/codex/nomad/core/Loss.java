package com.codex.nomad.core;

import java.util.LinkedHashMap;
import java.util.Map;

/** Something gone for good: a deathpile that ran out, a wiped deathbank, a floor that vanished. */
public final class Loss
{
	/** When, epoch ms. */
	public long at;
	/** What happened, in a few words: "Deathpile ran out". */
	public String cause;
	public Map<Integer, Long> items = new LinkedHashMap<>();
	/** What it was worth when it went. */
	public long worth;

	public Loss()
	{
	}

	public Loss(long at, String cause, Map<Integer, Long> items, long worth)
	{
		this.at = at;
		this.cause = cause;
		this.items = new LinkedHashMap<>(items);
		this.worth = worth;
	}
}
