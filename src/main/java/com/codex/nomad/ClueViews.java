package com.codex.nomad;

import com.codex.nomad.core.Clues;
import com.codex.nomad.core.StashBook;
import com.codex.nomad.core.Store;
import com.codex.nomad.ui.View;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Builds the Clues page from the clue book, the pack and the STASH book. */
final class ClueViews
{
	private ClueViews()
	{
	}

	/** What the pack holds of each tier: open clues and scroll boxes. */
	static final class Held
	{
		final int[] clues = new int[Clues.Tier.values().length];
		final int[] boxes = new int[Clues.Tier.values().length];
	}

	/** Counts the clues and scroll boxes carried, by tier. */
	static Held held(Map<Integer, Long> carried, Views.Lookup look)
	{
		Held h = new Held();
		carried.forEach((id, q) ->
		{
			Clues.Kind k = Clues.classify(id, look.name(id));
			if (k == null || k.tier == null)
			{
				return;
			}
			if (k.piece.open())
			{
				h.clues[k.tier.ordinal()] += q.intValue();
			}
			else if (k.piece == Clues.Piece.SCROLL_BOX)
			{
				h.boxes[k.tier.ordinal()] += q.intValue();
			}
		});
		return h;
	}

	/**
	 * @param here the player's x, y and plane, or null
	 * @param stashUnit the STASH unit the emote step in hand needs (its RuneLite name), or null
	 * @param stashSource where that came from, for the card
	 * @param stashed the Nomad store for that unit, or null if never filled
	 */
	static View.CluesView build(Clues c, int[] here, Held held, long warnTicks, StashBook book, String stashUnit,
		String stashTier, String stashSource, Store stashed, Map<Integer, Long> carried, Views.Lookup look)
	{
		View.CluesView v = new View.CluesView();
		v.warnTicks = warnTicks;
		List<View.FloorClue> floor = new ArrayList<>();
		for (Clues.Dropped d : c.floor())
		{
			View.FloorClue f = new View.FloorClue();
			f.item = d.item;
			f.label = d.label();
			f.ticksLeft = d.ticksLeft;
			f.exact = d.exact;
			f.where = Clues.where(here, d);
			floor.add(f);
		}
		floor.sort((a, b) -> Long.compare(a.ticksLeft, b.ticksLeft));
		v.floor = floor;

		List<View.TierLine> tiers = new ArrayList<>();
		int session = 0;
		for (Clues.Tier t : Clues.Tier.values())
		{
			Clues.TierLog l = c.log(t);
			View.TierLine line = new View.TierLine();
			line.tier = t.label();
			line.casket = t.casket;
			line.count = l.count;
			line.timed = l.times.size();
			line.session = c.session(t);
			line.held = held == null ? 0 : held.clues[t.ordinal()];
			line.boxes = held == null ? 0 : held.boxes[t.ordinal()];
			line.onFloor = c.onFloor(t);
			line.medianMs = c.median(t);
			line.perHour = c.perHour(t);
			line.steps = t.countsSteps() ? c.steps(t) : -1;
			line.minSteps = t.minSteps;
			line.maxSteps = t.maxSteps;
			line.carriedMs = line.held > 0 ? l.carried * 600L : 0;
			session += line.session;
			tiers.add(line);
		}
		v.tiers = tiers;
		v.sessionCaskets = session;
		v.sessionMs = c.sessionTicks() * 600L;
		v.stash = stash(book, stashUnit, stashTier, stashSource, stashed, carried, look);
		return v;
	}

	static View.StashStep stash(StashBook book, String unit, String tier, String source, Store stashed, Map<Integer, Long> carried,
		Views.Lookup look)
	{
		StashBook.Unit u = unit == null || book == null ? null : book.unit(unit);
		if (u == null)
		{
			return null;
		}
		View.StashStep s = new View.StashStep();
		s.tier = tier;
		s.chart = u.chart;
		s.place = u.place;
		s.source = source;
		s.filled = stashed != null && !stashed.isEmpty();
		List<View.StashNeed> needs = new ArrayList<>();
		for (StashBook.Need n : u.needs)
		{
			View.StashNeed need = new View.StashNeed();
			need.name = n.name != null ? n.name : n.ids.isEmpty() ? "?" : look.name(n.ids.get(0));
			need.icon = n.ids.isEmpty() ? -1 : n.ids.get(0);
			for (int id : n.ids)
			{
				if (stashed != null && stashed.count(id) > 0)
				{
					need.stashed = true;
					need.icon = id;
				}
				if (carried != null && carried.getOrDefault(id, 0L) > 0)
				{
					need.carried = true;
					if (!need.stashed)
					{
						need.icon = id;
					}
				}
			}
			needs.add(need);
		}
		s.needs = needs;
		return s;
	}
}
