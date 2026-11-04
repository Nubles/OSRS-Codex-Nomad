package com.codex.nomad.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.google.gson.Gson;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class CluesTest
{
	private static final int HARD_A = 2722;
	private static final int HARD_B = 2723;

	private static Map<Clues.Tier, Integer> holding(Clues.Tier t, int id)
	{
		Map<Clues.Tier, Integer> m = new EnumMap<>(Clues.Tier.class);
		m.put(t, id);
		return m;
	}

	private static Map<Clues.Tier, Integer> none()
	{
		return Collections.emptyMap();
	}

	@Test
	public void clueItemsAreToldByTheirNames()
	{
		Clues.Kind k = Clues.classify(HARD_A, "Clue scroll (hard)");
		assertEquals(Clues.Tier.HARD, k.tier);
		assertEquals(Clues.Piece.CLUE, k.piece);
		assertEquals("Hard clue", k.label());
		assertEquals(Clues.Piece.CHALLENGE, Clues.classify(1, "Challenge scroll (medium)").piece);
		assertEquals(Clues.Piece.SCROLL_BOX, Clues.classify(24364, "Scroll box (hard)").piece);
		assertEquals(Clues.Piece.REWARD, Clues.classify(20544, "Reward casket (hard)").piece);
		assertEquals(Clues.Piece.CASKET, Clues.classify(2724, "Casket (hard)").piece);
		assertEquals(Clues.Tier.BEGINNER, Clues.classify(23182, "Clue scroll (beginner)").tier);
		assertEquals("Mimic", Clues.classify(Clues.MIMIC, "Mimic").label());
		assertNull(Clues.classify(405, "Casket"));
		assertNull(Clues.classify(1, "Clue scroll (special)"));
		assertNull(Clues.classify(1, "Clue scroll"));
		assertNull(Clues.classify(1, "Clue box"));
		assertNull(Clues.classify(1, null));
	}

	@Test
	public void eachTierHasTheWikisStepsAndItsOwnCasket()
	{
		assertEquals(1, Clues.Tier.BEGINNER.minSteps);
		assertEquals(3, Clues.Tier.BEGINNER.maxSteps);
		assertEquals(4, Clues.Tier.HARD.minSteps);
		assertEquals(6, Clues.Tier.HARD.maxSteps);
		assertEquals(8, Clues.Tier.MASTER.maxSteps);
		assertEquals(20544, Clues.Tier.HARD.casket);
		assertEquals(23245, Clues.Tier.BEGINNER.casket);
		assertEquals(19836, Clues.Tier.MASTER.casket);
		assertFalse(Clues.Tier.BEGINNER.countsSteps());
		assertFalse(Clues.Tier.MASTER.countsSteps());
		assertTrue(Clues.Tier.ELITE.countsSteps());
	}

	@Test
	public void theGameSaysHowManyAreDone()
	{
		Map.Entry<Clues.Tier, Integer> e = Clues.completed("You have completed 12 hard Treasure Trails.");
		assertEquals(Clues.Tier.HARD, e.getKey());
		assertEquals(12, (int) e.getValue());
		assertEquals(1, (int) Clues.completed("You have completed 1 master Treasure Trail.").getValue());
		assertEquals(1204, (int) Clues.completed("You have completed 1,204 easy Treasure Trails.").getValue());
		assertNull(Clues.completed("You have completed 12 hard Treasure Hunts."));
		assertNull(Clues.completed("Well done, you've completed the Treasure Trail!"));

		Clues c = new Clues();
		c.count(Clues.Tier.HARD, 12);
		c.count(Clues.Tier.HARD, 13);
		assertEquals(13, c.log(Clues.Tier.HARD).count);
		assertEquals(2, c.session(Clues.Tier.HARD));
		assertEquals(0, c.session(Clues.Tier.EASY));
	}

	@Test
	public void aDroppedClueCountsDownOnlyWhileLoggedIn()
	{
		Clues c = new Clues();
		Clues.Dropped d = c.drop(HARD_A, Clues.classify(HARD_A, "Clue scroll (hard)"), 3200, 3200, 0, -1, 0);
		// With no timer from the game, the wiki's 61 minutes.
		assertEquals(6100, d.ticksLeft);
		assertFalse(d.exact);
		// Only logged-in ticks count: each tick() is one.
		for (int i = 0; i < 6099; i++)
		{
			assertTrue(c.tick(none()).isEmpty());
		}
		assertEquals(1, c.floor().size());
		List<Clues.Dropped> gone = c.tick(none());
		assertEquals(1, gone.size());
		assertTrue(c.floor().isEmpty());
	}

	@Test
	public void theGamesOwnTimerWinsAndIsReadAgainAfterALogin()
	{
		Clues c = new Clues();
		Clues.Dropped reward = c.drop(20544, Clues.classify(20544, "Reward casket (hard)"), 1, 2, 0, -1, 0);
		assertEquals(300, reward.ticksLeft);
		Clues.Dropped d = c.drop(HARD_A, Clues.classify(HARD_A, "Clue scroll (hard)"), 3200, 3200, 0, 6098, 0);
		assertTrue(d.exact);
		assertEquals(6098, d.ticksLeft);
		assertFalse(c.resync(HARD_A, 3200, 3201, 0, 4000));
		assertFalse(c.resync(HARD_A, 3200, 3200, 0, -1));
		assertTrue(c.resync(HARD_A, 3200, 3200, 0, 4000));
		assertEquals(4000, d.ticksLeft);
		assertEquals(reward, c.soonest());
		assertEquals(2, c.onFloor(Clues.Tier.HARD));
		assertEquals(0, c.onFloor(Clues.Tier.EASY));
	}

	@Test
	public void pickedUpIsToldFromRanOut()
	{
		Clues c = new Clues();
		c.drop(HARD_A, Clues.classify(HARD_A, "Clue scroll (hard)"), 10, 10, 0, 6100, 0);
		assertNull(c.lift(HARD_A, 11, 10, 0));
		Clues.Dropped d = c.lift(HARD_A, 10, 10, 0);
		assertNotNull(d);
		assertFalse(Clues.vanished(d));
		d.ticksLeft = 3;
		assertTrue(Clues.vanished(d));
		assertTrue(c.floor().isEmpty());
	}

	@Test
	public void theTrailIsTimedFromTakingTheClueToItsCasket()
	{
		Clues c = new Clues();
		// Four steps of a hard clue, 100 ticks each.
		for (int step = 0; step < 4; step++)
		{
			for (int i = 0; i < 100; i++)
			{
				c.tick(holding(Clues.Tier.HARD, HARD_A + step));
			}
		}
		// A reward casket picked up while the clue is still in hand closes nothing.
		assertFalse(c.casket(Clues.Tier.HARD, true));
		assertTrue(c.casket(Clues.Tier.HARD, false));
		assertEquals(240_000L, c.median(Clues.Tier.HARD));
		assertEquals(15.0, c.perHour(Clues.Tier.HARD), 1e-9);
		assertEquals(4.0, c.steps(Clues.Tier.HARD), 1e-9);
		assertEquals(1, c.log(Clues.Tier.HARD).seen);
		// Nothing being timed: a casket off the floor changes nothing.
		assertFalse(c.casket(Clues.Tier.HARD, false));
		assertEquals(-1, c.median(Clues.Tier.EASY));
		assertEquals(-1, c.perHour(Clues.Tier.EASY), 1e-9);
		assertEquals(-1, c.steps(Clues.Tier.EASY), 1e-9);
	}

	@Test
	public void droppingTheClueInHandStartsItsTimeAgain()
	{
		Clues c = new Clues();
		for (int i = 0; i < 500; i++)
		{
			c.tick(holding(Clues.Tier.HARD, HARD_A));
		}
		c.drop(HARD_A, Clues.classify(HARD_A, "Clue scroll (hard)"), 1, 1, 0, 6100, 0);
		assertEquals(0, c.log(Clues.Tier.HARD).carried);
		for (int i = 0; i < 100; i++)
		{
			c.tick(holding(Clues.Tier.HARD, HARD_B));
		}
		assertTrue(c.casket(Clues.Tier.HARD, false));
		assertEquals(60_000L, c.median(Clues.Tier.HARD));
		// Masters keep one id for every step, so their steps are not counted.
		for (int i = 0; i < 10; i++)
		{
			c.tick(holding(Clues.Tier.MASTER, Clues.MASTER_CLUE));
		}
		assertTrue(c.casket(Clues.Tier.MASTER, false));
		assertEquals(-1, c.steps(Clues.Tier.MASTER), 1e-9);
	}

	@Test
	public void thePaceIsTheMiddleTimeAndKeepsTheLatest()
	{
		Clues c = new Clues();
		long[] ticks = {100, 300, 200, 10_000};
		for (long t : ticks)
		{
			for (int i = 0; i < t; i++)
			{
				c.tick(holding(Clues.Tier.EASY, 1));
			}
			c.casket(Clues.Tier.EASY, false);
		}
		// One slow trail does not drag the pace: the middle of 1, 2, 3 and 100 minutes.
		assertEquals(150_000L, c.median(Clues.Tier.EASY));
		for (int n = 0; n < Clues.SAMPLES + 5; n++)
		{
			c.tick(holding(Clues.Tier.EASY, 1));
			c.casket(Clues.Tier.EASY, false);
		}
		assertEquals(Clues.SAMPLES, c.log(Clues.Tier.EASY).times.size());
		assertEquals(600L, c.median(Clues.Tier.EASY));
	}

	@Test
	public void whereSaysHowFarAndWhichWay()
	{
		Clues c = new Clues();
		Clues.Dropped d = c.drop(1, Clues.classify(1, "Clue scroll (easy)"), 3206, 3206, 0, 100, 0);
		assertEquals("6 tiles north-east", Clues.where(new int[]{3200, 3200, 0}, d));
		assertEquals("6 tiles north", Clues.where(new int[]{3205, 3200, 0}, d));
		assertEquals("1 tile west", Clues.where(new int[]{3207, 3206, 0}, d));
		assertEquals("under you", Clues.where(new int[]{3206, 3206, 0}, d));
		assertEquals("on the floor below", Clues.where(new int[]{3206, 3206, 1}, d));
		assertEquals("", Clues.where(null, d));
	}

	@Test
	public void theBookSavesAndADamagedOneStartsEmpty()
	{
		Gson gson = new Gson();
		Clues c = new Clues();
		c.drop(HARD_A, Clues.classify(HARD_A, "Clue scroll (hard)"), 10, 20, 1, 5000, 0);
		c.count(Clues.Tier.ELITE, 7);
		for (int i = 0; i < 50; i++)
		{
			c.tick(holding(Clues.Tier.ELITE, 3));
		}
		c.casket(Clues.Tier.ELITE, false);
		Clues back = Clues.read(gson, c.write(gson));
		assertEquals(1, back.floor().size());
		assertEquals(4950, back.floor().get(0).ticksLeft);
		assertEquals(Clues.Tier.HARD, back.floor().get(0).tier);
		assertEquals(7, back.log(Clues.Tier.ELITE).count);
		assertEquals(30_000L, back.median(Clues.Tier.ELITE));
		// The session is not saved.
		assertEquals(0, back.session(Clues.Tier.ELITE));

		assertTrue(Clues.read(gson, "{not json").floor().isEmpty());
		assertTrue(Clues.read(gson, null).floor().isEmpty());
		assertTrue(Clues.read(gson, "{\"floor\":[null,{\"item\":1}],\"log\":{\"HARD\":null}}").floor().isEmpty());
		assertEquals(-1, Clues.read(gson, "{\"log\":{\"HARD\":{\"count\":3,\"times\":null}}}").median(Clues.Tier.HARD));
	}
}
