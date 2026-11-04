package com.codex.nomad.core;

import com.google.gson.Gson;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.runelite.api.gameval.ItemID;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class LoadoutTest
{
	private static final int SHARK = ItemID.SHARK;
	private static final int WHIP = ItemID.ABYSSAL_WHIP;
	private static final int LAW = ItemID.LAWRUNE;
	private static final int NATURE = ItemID.NATURERUNE;
	private static final int DEATH = ItemID.DEATHRUNE;
	private static final int BLOOD = ItemID.BLOODRUNE;
	private static final int CHAOS = ItemID.CHAOSRUNE;
	private static final int CLUE_ITEM = 1119;
	private static final int DRAGON_PICKAXE = ItemID.DRAGON_PICKAXE;
	private static final int OAK_LOGS = ItemID.OAK_LOGS;

	private static Map<Integer, Long> items(long... pairs)
	{
		Map<Integer, Long> m = new HashMap<>();
		for (int i = 0; i < pairs.length; i += 2)
		{
			m.put((int) pairs[i], pairs[i + 1]);
		}
		return m;
	}

	/** Facts for a small world: runes and coins stack, the steel platebody goes in a STASH unit. */
	static Loadout.Facts facts(Map<Integer, Long> prices, Set<Integer> untradeable)
	{
		Set<Integer> runes = new HashSet<>(Arrays.asList(LAW, NATURE, DEATH, BLOOD, CHAOS));
		Set<Integer> containers = new HashSet<>(Arrays.asList(ItemID.BH_RUNE_POUCH, ItemID.DIVINE_RUNE_POUCH, ItemID.LOOTING_BAG));
		return new Loadout.Facts()
		{
			@Override
			public boolean stackable(int item)
			{
				return runes.contains(item) || item == ItemID.COINS;
			}

			@Override
			public boolean rune(int item)
			{
				return runes.contains(item);
			}

			@Override
			public boolean tradeable(int item)
			{
				return !untradeable.contains(item);
			}

			@Override
			public long price(int item)
			{
				return prices.getOrDefault(item, 100L);
			}

			@Override
			public boolean container(int item)
			{
				return containers.contains(item);
			}

			@Override
			public String stash(int item)
			{
				return item == CLUE_ITEM ? "Barbarian Outpost" : null;
			}

			@Override
			public boolean costume(int item)
			{
				return false;
			}
		};
	}

	private static Trip vorkath()
	{
		Trip t = new Trip("Vorkath", 0);
		t.wants.add(new Trip.Want(WHIP, "Abyssal whip", 1, true));
		t.wants.add(new Trip.Want(SHARK, "Shark", 20, false));
		t.wants.add(new Trip.Want(LAW, "Law rune", 200, false));
		t.wants.add(new Trip.Want(DEATH, "Death rune", 500, false));
		t.wants.add(new Trip.Want(BLOOD, "Blood rune", 300, false));
		t.wants.add(new Trip.Want(CHAOS, "Chaos rune", 100, false));
		return t;
	}

	@Test
	public void runesFillThePouchBiggestFirstAndTheRestRideInThePack()
	{
		Map<Integer, Long> inv = items(ItemID.BH_RUNE_POUCH, 1, SHARK, 20);
		Loadout l = Loadout.solve(vorkath(), inv, items(WHIP, 1), items(), items(), id -> id, facts(new HashMap<>(), new HashSet<>()), null, 50_000);
		assertTrue(l.hasPouch);
		assertEquals(3, l.pouchTypes);
		assertEquals(3, l.pouch.size());
		assertEquals(DEATH, l.pouch.get(0).item);
		assertEquals(BLOOD, l.pouch.get(1).item);
		assertEquals(LAW, l.pouch.get(2).item);
		// Pouch 1, chaos runes 1, sharks 20.
		assertEquals(22, l.needed);
		assertEquals(1, l.worn.size());
		assertTrue(l.fits());
		assertEquals(6, l.free());
		int[] g = l.grid();
		assertEquals(28, g.length);
		assertEquals(-1, g[27]);
	}

	@Test
	public void theDivinePouchTakesFourAndCapsEachAtSixteenThousand()
	{
		Trip t = new Trip("Barrage", 0);
		t.wants.add(new Trip.Want(DEATH, "Death rune", 20_000, false));
		t.wants.add(new Trip.Want(BLOOD, "Blood rune", 100, false));
		t.wants.add(new Trip.Want(LAW, "Law rune", 100, false));
		t.wants.add(new Trip.Want(CHAOS, "Chaos rune", 100, false));
		Loadout l = Loadout.solve(t, items(ItemID.DIVINE_RUNE_POUCH, 1), items(), items(), items(), id -> id,
			facts(new HashMap<>(), new HashSet<>()), null, 50_000);
		assertEquals(4, l.pouchTypes);
		assertEquals(4, l.pouch.size());
		assertEquals(16_000, l.pouch.get(0).qty);
		// The pouch, and the 4,000 death runes past the cap.
		assertEquals(2, l.needed);
	}

	@Test
	public void aListTooBigToCarrySaysWhatToCut()
	{
		Trip t = new Trip("Greedy", 0);
		t.wants.add(new Trip.Want(SHARK, "Shark", 26, false));
		t.wants.add(new Trip.Want(ItemID._4DOSEPRAYERRESTORE, "Prayer potion(4)", 6, false));
		Loadout l = Loadout.solve(t, items(), items(), items(), items(), id -> id, facts(new HashMap<>(), new HashSet<>()), null, 50_000);
		assertFalse(l.fits());
		assertEquals(4, l.over());
		assertEquals(1, l.trim.size());
		assertEquals(SHARK, l.trim.get(0).item);
		assertEquals(4, l.trim.get(0).cut);
	}

	@Test
	public void whatIsNotNeededGetsAHomeSafestFirst()
	{
		Trip t = new Trip("Woodcutting", 0);
		t.wants.add(new Trip.Want(ItemID.RUNE_AXE, "Rune axe", 1, false));
		t.wants.add(new Trip.Want(SHARK, "Shark", 25, false));
		Map<Integer, Long> prices = items(DRAGON_PICKAXE, 2_000_000, OAK_LOGS, 20, ItemID.RAKE, 5, CLUE_ITEM, 1_000);
		Map<Integer, Long> inv = items(ItemID.LOOTING_BAG, 1, ItemID.RUNE_AXE, 1, SHARK, 25, DRAGON_PICKAXE, 1, OAK_LOGS, 3,
			ItemID.RAKE, 1, CLUE_ITEM, 1);
		Set<Integer> untradeable = new HashSet<>();
		Loadout l = Loadout.solve(t, inv, items(), items(), items(), id -> id, facts(prices, untradeable), null, 50_000);
		// Bag 1 + axe 1 + sharks 25 = 27: one slot spare, which the pickaxe (most worth a slot) takes.
		assertEquals(27, l.needed);
		assertEquals(28, l.used);
		Loadout.Leave clue = find(l, CLUE_ITEM);
		assertEquals(Loadout.Home.STASH, clue.home);
		assertEquals("Barbarian Outpost", clue.where);
		assertFalse(clue.must);
		assertEquals(Loadout.Home.LEPRECHAUN, find(l, ItemID.RAKE).home);
		Loadout.Leave logs = find(l, OAK_LOGS);
		assertEquals(Loadout.Home.LOOTING_BAG, logs.home);
		assertTrue(logs.must);
		assertEquals(3, logs.slots);
		assertNull(find(l, DRAGON_PICKAXE));
		assertTrue(l.leave.get(0).must);

		// Without a looting bag, valuables go to death storage and the cheap is dropped.
		Map<Integer, Long> noBag = new HashMap<>(inv);
		noBag.remove(ItemID.LOOTING_BAG);
		noBag.put(SHARK, 27L);
		Trip more = new Trip("Woodcutting", 0);
		more.wants.add(new Trip.Want(ItemID.RUNE_AXE, "Rune axe", 1, false));
		more.wants.add(new Trip.Want(SHARK, "Shark", 27, false));
		Loadout l2 = Loadout.solve(more, noBag, items(), items(), items(), id -> id, facts(prices, untradeable), "Vorkath", 50_000);
		assertEquals(Loadout.Home.DEATH_STORAGE, find(l2, DRAGON_PICKAXE).home);
		assertEquals(Loadout.Home.DROP, find(l2, OAK_LOGS).home);
		assertEquals("Vorkath", l2.wipes);
	}

	@Test
	public void theLootingBagTakesOnlyTradeablesAndStacksItHasRoomFor()
	{
		Trip t = new Trip("Full", 0);
		t.wants.add(new Trip.Want(SHARK, "Shark", 27, false));
		Map<Integer, Long> bag = new HashMap<>();
		for (int i = 0; i < 27; i++)
		{
			bag.put(20_000 + i, 1L);
		}
		Map<Integer, Long> inv = items(ItemID.LOOTING_BAG, 1, SHARK, 27, OAK_LOGS, 1, DRAGON_PICKAXE, 1);
		Map<Integer, Long> prices = items(DRAGON_PICKAXE, 2_000_000, OAK_LOGS, 20);
		Set<Integer> untradeable = new HashSet<>(Arrays.asList(DRAGON_PICKAXE));
		Loadout l = Loadout.solve(t, inv, items(), items(), bag, id -> id, facts(prices, untradeable), null, 50_000);
		assertEquals(Loadout.Home.DEATH_STORAGE, find(l, DRAGON_PICKAXE).home);
		assertEquals(Loadout.Home.LOOTING_BAG, find(l, OAK_LOGS).home);
		assertEquals(0, l.bagFree);
	}

	@Test
	public void theLeprechaunTakesWhatTheWikiSays()
	{
		assertEquals(100, Loadout.leprechaun(ItemID.RAKE));
		assertEquals(100, Loadout.leprechaun(ItemID.SPADE));
		assertEquals(1, Loadout.leprechaun(ItemID.WATERING_CAN_8));
		assertEquals(1000, Loadout.leprechaun(ItemID.BUCKET_ULTRACOMPOST));
		assertEquals(0, Loadout.leprechaun(ItemID.SHARK));
	}

	@Test
	public void safeStopsGoQuickestFirstWithWayfinder()
	{
		// Standing in Lumbridge; a STASH unit 40 tiles away by foot, a costume room far off but a
		// carried house teleport lands on it. Wayfinder's atlas makes the house the first stop.
		Travel atlas = Travel.read(new Gson(), "{\"version\":1,\"walkSecondsPerTile\":0.3,\"teleports\":["
			+ "{\"name\":\"Teleport to House\",\"kind\":\"SPELL\",\"x\":2954,\"y\":3224,\"plane\":0,\"seconds\":4,\"carried\":true}]}");
		Trip t = new Trip("Trip", 0);
		t.wants.add(new Trip.Want(SHARK, "Shark", 4, false));
		Store unit = new Store("stash:a", Kind.STASH, "Near unit").at(3222, 3258, 0);
		unit.fill(items(SHARK, 2), 0);
		Store house = new Store("costume", Kind.COSTUME, "House").at(2955, 3224, 0);
		house.fill(items(SHARK, 2), 0);
		Store pile = new Store("pile", Kind.DEATHPILE, "Pile").at(3300, 3300, 0);
		pile.fill(items(SHARK, 1), 0);
		int[] here = {3222, 3218, 0};
		Fetch without = Fetch.plan(t, items(), items(), Arrays.asList(unit, house, pile), id -> id, id -> false, here, id -> false, null);
		assertEquals("pile", without.stops.get(0).store.key);
		assertEquals("costume", without.stops.get(1).store.key);
		Fetch with = Fetch.plan(t, items(), items(), Arrays.asList(unit, house, pile), id -> id, id -> false, here, id -> false, null, atlas);
		assertEquals("pile", with.stops.get(0).store.key);
		assertEquals("costume", with.stops.get(1).store.key);
		assertNotNull(with.stops.get(1).leg);
		assertEquals("Teleport to House", with.stops.get(1).leg.via);
		// Move the house out of reach of the teleport: the walk to the unit now wins.
		house.at(2700, 3400, 0);
		Fetch moved = Fetch.plan(t, items(), items(), Arrays.asList(unit, house, pile), id -> id, id -> false, here, id -> false, null, atlas);
		assertEquals("stash:a", moved.stops.get(1).store.key);
		assertNull(moved.stops.get(1).leg.via);
		assertEquals(12, moved.stops.get(1).leg.seconds);
	}

	private static Loadout.Leave find(Loadout l, int item)
	{
		for (Loadout.Leave x : l.leave)
		{
			if (x.item == item)
			{
				return x;
			}
		}
		return null;
	}
}
