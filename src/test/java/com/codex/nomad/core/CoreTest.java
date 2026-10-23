package com.codex.nomad.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.google.gson.Gson;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

public class CoreTest
{
	private static final Gson GSON = new com.google.gson.GsonBuilder().create();

	private static Map<Integer, Long> items(long... pairs)
	{
		Map<Integer, Long> m = new HashMap<>();
		for (int i = 0; i < pairs.length; i += 2)
		{
			m.put((int) pairs[i], pairs[i + 1]);
		}
		return m;
	}

	@Test
	public void savesAndReadsBack()
	{
		Nomad n = new Nomad();
		n.account = 2;
		n.played = 123_456;
		n.carry(Nomad.INVENTORY, items(385, 10, 995, 5000), 1);
		Store stash = new Store("stash:X", Kind.STASH, "Draynor").at(3083, 3254, 0);
		stash.fill(items(1007, 1), 2);
		n.put(stash);
		n.pile(items(4151, 1), 3200, 3200, 0, 3);
		Trip t = new Trip("Zulrah", 4);
		t.wants.add(new Trip.Want(385, "Shark", 10, false));
		n.save(t);
		n.active = "Zulrah";

		Nomad.Loaded back = Nomad.read(GSON, n.write(GSON));
		assertFalse(back.damaged);
		assertEquals(2, back.nomad.account);
		assertEquals(123_456, back.nomad.played);
		assertEquals(10L, (long) back.nomad.carried(Nomad.INVENTORY).get(385));
		assertEquals(1L, back.nomad.store("stash:X").count(1007));
		assertEquals(1, back.nomad.stores(Kind.DEATHPILE).size());
		assertEquals(Nomad.PILE_TICKS, back.nomad.stores(Kind.DEATHPILE).get(0).ticksLeft);
		assertNotNull(back.nomad.activeTrip());
	}

	@Test
	public void aDamagedPartCostsOnlyThatPart()
	{
		String saved = "{\"account\":2,\"stores\":{\"a\":{\"key\":\"a\",\"kind\":\"STASH\",\"name\":\"x\",\"items\":{\"1\":2}},"
			+ "\"b\":{\"key\":\"b\",\"kind\":\"NOPE\",\"items\":[]}},\"trips\":[{\"name\":\"T\",\"wants\":[]},7]}";
		Nomad.Loaded l = Nomad.read(GSON, saved);
		assertTrue(l.damaged);
		assertEquals(2, l.nomad.account);
		assertNotNull(l.nomad.store("a"));
		assertNull(l.nomad.store("b"));
		assertEquals(1, l.nomad.trips().size());
		assertTrue(Nomad.read(GSON, "not json at all {").damaged);
		assertFalse(Nomad.read(GSON, null).damaged);
	}

	@Test
	public void deathpilesRunDownOnlyByTicks()
	{
		Nomad n = new Nomad();
		Store pile = n.pile(items(4151, 1), 1, 1, 0, 0);
		pile.ticksLeft = 2;
		assertTrue(n.tick().isEmpty());
		List<Store> gone = n.tick();
		assertEquals(1, gone.size());
		assertTrue(n.stores(Kind.DEATHPILE).isEmpty());
	}

	@Test
	public void deathsAreToldApart()
	{
		Map<Integer, Long> before = items(4151, 1, 385, 10);
		assertEquals(Death.Kind.SAFE, Death.of(before, before, 12850, false).kind);
		Death zulrah = Death.of(before, items(4151, 1), 9007, true);
		assertEquals(Death.Kind.DEATHBANK, zulrah.kind);
		assertEquals("Zulrah", zulrah.place);
		assertEquals(10L, (long) zulrah.lost.get(385));
		assertEquals(Death.Kind.PILE, Death.of(before, Collections.emptyMap(), 12850, false).kind);
		assertEquals(Death.Kind.DEATHBANK, Death.of(before, Collections.emptyMap(), 1, true).kind);
	}

	@Test
	public void deathKeepsTheMostValuableThree()
	{
		Prices p = id -> id == 1 ? 1000 : id == 2 ? 10 : 1;
		Kept k = Kept.of(items(1, 2, 2, 5, 3, 100), p, 3);
		assertEquals(2L, (long) k.kept.get(1));
		assertEquals(1L, (long) k.kept.get(2));
		assertEquals(4L, (long) k.dropped.get(2));
		assertEquals(2010, k.keptWorth);
		assertEquals(140, k.droppedWorth);
		assertEquals(0, Kept.of(items(1, 1), p, 0).keptWorth);
	}

	@Test
	public void fetchVisitsPerishablePlacesFirstThenTheNearest()
	{
		Trip t = new Trip("Vorkath", 0);
		t.wants.add(new Trip.Want(385, "Shark", 10, false));
		t.wants.add(new Trip.Want(4151, "Whip", 1, true));
		t.wants.add(new Trip.Want(2444, "Ranging potion", 2, false));
		Store far = new Store("far", Kind.STASH, "Far").at(100, 100, 0);
		far.fill(items(385, 20), 0);
		Store near = new Store("near", Kind.STASH, "Near").at(5, 5, 0);
		near.fill(items(385, 3), 0);
		Store pile = new Store("pile", Kind.DEATHPILE, "Pile").at(500, 500, 0);
		pile.fill(items(385, 4), 0);
		Fetch f = Fetch.plan(t, items(385, 1, 4151, 1, 995, 100), items(2444, 1), Arrays.asList(far, near, pile),
			id -> id, id -> id == 995, new int[]{0, 0, 0}, id -> false);
		assertEquals("pile", f.next().store.key);
		assertEquals(4L, (long) f.next().take.get(385));
		assertEquals("near", f.stops.get(1).store.key);
		assertEquals("far", f.stops.get(2).store.key);
		assertEquals(2L, (long) f.stops.get(2).take.get(385));
		Fetch.Line potions = f.lines.get(2);
		assertEquals(1, potions.bagged);
		assertEquals(2, potions.missing);
		assertEquals(1, f.ready);
		assertEquals(12, f.slots);
		assertTrue(f.fits());
		assertEquals(100L, (long) f.extra.get(995));
	}

	@Test
	public void theNotaryKnowsWhoTakesWhat()
	{
		Notary.Advice coal = Notary.advise(453, "Coal", 20, false, new int[]{3200, 3900, 0});
		assertEquals("Piles", coal.best().who);
		assertEquals(19, coal.frees);
		Notary.Advice herbs = Notary.advise(199, "Grimy guam leaf", 5, false, null);
		assertEquals("Tool leprechaun", herbs.best().who);
		Notary.Advice bones = Notary.advise(537, "Dragon bones", 30, true, new int[]{3240, 3600, 0});
		assertEquals("Elder Chaos druid", bones.best().who);
		Notary.Advice planks = Notary.advise(961, "Plank", 30, true, new int[]{2950, 3210, 0});
		assertEquals("Phials", planks.best().who);
		assertNull(Notary.advise(1, "Abyssal whip", 2, false, null));
		assertNull(Notary.advise(453, "Coal", 1, false, null));
	}

	@Test
	public void deadWeightIsWhatSatUntouched()
	{
		Nomad n = new Nomad();
		Map<Integer, Long> inv = items(1, 1, 2, 5, 3, 1);
		n.touch(Collections.emptyMap(), inv);
		n.played = 4 * 3_600_000L;
		Map<Integer, Long> later = items(1, 1, 2, 4, 3, 1);
		n.touch(inv, later);
		List<Idle> idle = Idle.find(n, later, id -> false, id -> id == 3, 3_600_000L);
		assertEquals(1, idle.size());
		assertEquals(1, idle.get(0).item);
	}

	@Test
	public void theRouteReadsWhole()
	{
		Route r = Route.load();
		assertTrue(r.size() > 100);
		assertEquals(13, r.sections().size());
		Set<String> keys = new HashSet<>();
		for (Route.Step s : r.steps())
		{
			assertTrue(s.text, keys.add(s.key()));
			assertFalse(s.text.isEmpty());
		}
		Route.Account fresh = new Route.Account()
		{
			public boolean questDone(String quest)
			{
				return false;
			}

			public int level(String skill)
			{
				return 1;
			}
		};
		assertEquals(0, r.current(new HashSet<>(), fresh).index);
		Route.Account xmts = new Route.Account()
		{
			public boolean questDone(String quest)
			{
				return quest.equals("X Marks the Spot");
			}

			public int level(String skill)
			{
				return 1;
			}
		};
		// The steps before a quest the game says is done are passed, so the next step is the one beyond it.
		assertEquals("X Marks the Spot", r.steps().get(5).quests.get(0));
		assertTrue(r.passed(r.steps().get(0), xmts));
		assertEquals(6, r.current(new HashSet<>(), xmts).index);
		// Every step either checks itself or lies before one that does.
		for (Route.Step s : r.steps())
		{
			assertTrue(s.text, s.watched() || s.optional || r.steps().stream().anyMatch(o -> o.index > s.index && o.checkable()));
		}
	}

	@Test
	public void stepsInHandTickInTheMoment()
	{
		Route r = Route.parse(java.util.Arrays.asList(
			"A | Sell the starter kit | gone:Bronze dagger; item:Bread",
			"A | Go to Ferox with no coins | area:3120,3615,3160,3645,0; coins:<0",
			"A | Build a wardrobe | built:18784-18787; go:3000,3000,0",
			"A | A quest | quest:Cook's Assistant"));
		Route.Account none = new Route.Account()
		{
			public boolean questDone(String quest)
			{
				return false;
			}

			public int level(String skill)
			{
				return 1;
			}
		};
		int[] at = {3222, 3218, 0};
		long[] coins = {25};
		Set<String> held = new HashSet<>(java.util.Arrays.asList("bread", "bronze dagger"));
		Route.Moment m = new Route.Moment()
		{
			public boolean held(String name)
			{
				return held.contains(name);
			}

			public int[] here()
			{
				return at;
			}

			public List<String> inventory()
			{
				return new java.util.ArrayList<>(held);
			}

			public long coins()
			{
				return coins[0];
			}

			public boolean heard(String text)
			{
				return false;
			}

			public boolean deathbank()
			{
				return false;
			}

			public boolean sees(int[] objects)
			{
				return objects[0] == 18784;
			}
		};
		Set<String> ticked = new HashSet<>();
		assertTrue(r.observe(ticked, none, m).isEmpty());
		held.remove("bronze dagger");
		List<Route.Step> now = r.observe(ticked, none, m);
		assertEquals(1, now.size());
		ticked.add(now.get(0).key());
		at[0] = 3140;
		at[1] = 3630;
		assertTrue(r.observe(ticked, none, m).isEmpty());
		coins[0] = 0;
		// Ferox with no coins, then the wardrobe already in sight: both tick together.
		assertEquals(2, r.observe(ticked, none, m).size());
		assertEquals(4, r.steps().get(2).built.get(0).length);
		assertEquals(3000, r.target(r.steps().get(2), none)[0]);
	}

	@Test
	public void stepsSayWhatToClick()
	{
		Route r = Route.load();
		Route.Account fresh = new Route.Account()
		{
			public boolean questDone(String quest)
			{
				return false;
			}

			public int level(String skill)
			{
				return 1;
			}
		};
		int leads = 0;
		for (Route.Step s : r.steps())
		{
			Route.Lead l = r.lead(s, fresh);
			if (!l.isEmpty())
			{
				leads++;
			}
			if (s.text.startsWith("Buy ") && !s.items.isEmpty())
			{
				assertTrue(s.text, l.shop.containsAll(s.items));
			}
		}
		assertTrue("only " + leads + " steps say what to click", leads >= 250);
		Route.Step xmts = r.steps().get(5);
		assertFalse(r.lead(xmts, fresh).npcIds.isEmpty());
	}

	@Test
	public void everyStashUnitIsKnown()
	{
		StashBook b = StashBook.load(GSON);
		assertEquals(119, b.size());
		assertFalse(b.homes(10472).isEmpty());
	}
}
