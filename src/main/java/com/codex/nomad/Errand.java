package com.codex.nomad;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import net.runelite.api.coords.WorldPoint;

/**
 * The one thing to do next that is not a route step, guided the way Quest Helper guides a quest:
 * where to go, what to click there, and which items to take in the interface or off the floor.
 * Nomad picks it each refresh, most pressing first: save a deathpile, reclaim a deathbank, fetch
 * what the packing list needs. It goes away by itself once done, since it is worked out afresh.
 */
final class Errand
{
	enum Kind
	{
		PILE, DEATHBANK, FETCH
	}

	final Kind kind;
	/** The step in a few words, for the plaque: "Save your deathpile". */
	final String title;
	/** What to do there: "Take Abyssal whip first, then Dragon boots". */
	final String text;
	/** Where to go, or null when it is not known. */
	final WorldPoint where;
	/** Items to highlight in an open interface and on the floor, by common id. */
	final Set<Integer> take;
	/** For a pile, the order to pick it up in, by item id. */
	final List<Integer> order;
	/** A STASH unit's object id to outline, or -1. */
	final int object;
	/** A house furniture name to outline, such as "Armour case", or null. */
	final String furniture;

	Errand(Kind kind, String title, String text, WorldPoint where, Set<Integer> take, List<Integer> order, int object, String furniture)
	{
		this.kind = kind;
		this.title = title;
		this.text = text;
		this.where = where;
		this.take = take == null ? Collections.emptySet() : take;
		this.order = order == null ? Collections.emptyList() : order;
		this.object = object;
		this.furniture = furniture;
	}
}
