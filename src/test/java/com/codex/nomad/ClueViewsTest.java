package com.codex.nomad;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.codex.nomad.core.Clues;
import com.codex.nomad.core.Kind;
import com.codex.nomad.core.StashBook;
import com.codex.nomad.core.Store;
import com.codex.nomad.ui.View;
import com.google.gson.Gson;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.cluescrolls.clues.EmoteClue;
import net.runelite.client.plugins.cluescrolls.clues.emote.STASHUnit;
import org.junit.Test;

public class ClueViewsTest
{
	private static final Views.Lookup LOOK = new Views.Lookup()
	{
		public String name(int id)
		{
			switch (id)
			{
				case 1:
					return "Clue scroll (hard)";
				case 2:
					return "Scroll box (hard)";
				case 3:
					return "Clue scroll (easy)";
				case 1097:
					return "Studded chaps";
				default:
					return "Item " + id;
			}
		}

		public long price(int id)
		{
			return 0;
		}

		public boolean stackable(int id)
		{
			return id == 2;
		}

		public boolean noted(int id)
		{
			return false;
		}

		public int canon(int id)
		{
			return id;
		}
	};

	@Test
	public void everySTASHUnitRuneLiteKnowsIsInTheStashBook()
	{
		StashBook book = StashBook.load(new Gson());
		for (STASHUnit u : STASHUnit.values())
		{
			assertNotNull(u.name(), book.unit(u.name()));
		}
	}

	@Test
	public void anEmoteStepIsLinkedToItsStashUnit()
	{
		// One of RuneLite's own easy emote clues and the STASH unit it names.
		EmoteClue e = EmoteClue.forItemId(ItemID.TRAIL_CLUE_EASY_EMOTE012);
		assertNotNull(e);
		StashBook book = StashBook.load(new Gson());
		String unit = e.getStashUnit().name();
		Map<Integer, Long> carried = new HashMap<>();
		carried.put(book.unit(unit).needs.get(0).ids.get(0), 1L);
		View.StashStep s = ClueViews.stash(book, unit, "Easy", "from the clue you carry", null, carried, LOOK);
		assertNotNull(s);
		assertEquals(book.unit(unit).chart, s.chart);
		assertFalse(s.filled);
		boolean carriedOne = false;
		for (View.StashNeed n : s.needs)
		{
			carriedOne |= n.carried;
			assertFalse(n.stashed);
		}
		assertTrue(carriedOne);

		Store filled = new Store("stash:" + unit, Kind.STASH, "Draynor");
		Map<Integer, Long> in = new HashMap<>();
		for (StashBook.Need n : book.unit(unit).needs)
		{
			in.put(n.ids.get(0), 1L);
		}
		filled.fill(in, 0);
		View.StashStep f = ClueViews.stash(book, unit, "Easy", "x", filled, null, LOOK);
		assertTrue(f.filled);
		for (View.StashNeed n : f.needs)
		{
			assertTrue(n.name, n.stashed);
		}
		assertNull(ClueViews.stash(book, null, null, null, null, null, LOOK));
		assertNull(ClueViews.stash(book, "NO_SUCH_UNIT", null, null, null, null, LOOK));
	}

	@Test
	public void theLogShowsEveryTierWithWhatIsHeldAndOnTheFloor()
	{
		Map<Integer, Long> inv = new HashMap<>();
		inv.put(1, 1L);
		inv.put(2, 2L);
		inv.put(1097, 1L);
		ClueViews.Held held = ClueViews.held(inv, LOOK);
		Clues c = new Clues();
		c.drop(3, Clues.classify(3, "Clue scroll (easy)"), 3205, 3200, 0, 400, 0);
		c.drop(1, Clues.classify(1, "Clue scroll (hard)"), 3200, 3210, 0, 6000, 0);
		View.CluesView v = ClueViews.build(c, new int[]{3200, 3200, 0}, held, 500, null, null, null, null, null, inv, LOOK);
		assertEquals(6, v.tiers.size());
		View.TierLine hard = v.tiers.get(Clues.Tier.HARD.ordinal());
		assertEquals("Hard", hard.tier);
		assertEquals(1, hard.held);
		assertEquals(2, hard.boxes);
		assertEquals(1, hard.onFloor);
		assertEquals(4, hard.minSteps);
		assertEquals(2, v.floor.size());
		// Soonest first, with where it lies.
		assertEquals("Easy clue", v.floor.get(0).label);
		assertEquals("5 tiles east", v.floor.get(0).where);
		assertEquals("10 tiles north", v.floor.get(1).where);
		assertNull(v.stash);
	}
}
