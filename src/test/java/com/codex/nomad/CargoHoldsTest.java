package com.codex.nomad;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.codex.nomad.core.Kind;
import com.codex.nomad.core.Nomad;
import com.codex.nomad.core.Route;
import com.codex.nomad.core.StashBook;
import com.codex.nomad.core.Store;
import com.codex.nomad.ui.View;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.LinkedHashMap;
import java.util.Map;
import net.runelite.api.gameval.InventoryID;
import org.junit.Test;

/** Each boat's cargo hold is a place things are kept, as last seen, with the slots it showed. */
public class CargoHoldsTest
{
	private static final Gson GSON = new Gson();
	private static final int CANNONBALL = net.runelite.api.gameval.ItemID.MCANNONBALL;
	private static final int SWORDFISH = net.runelite.api.gameval.ItemID.SWORDFISH;

	private static Map<Integer, Long> items(long... pairs)
	{
		Map<Integer, Long> m = new LinkedHashMap<>();
		for (int i = 0; i < pairs.length; i += 2)
		{
			m.put((int) pairs[i], pairs[i + 1]);
		}
		return m;
	}

	@Test
	public void eachOfTheFiveBoatsHasItsOwnHold()
	{
		assertEquals(1, CargoHolds.boat(InventoryID.SAILING_BOAT_1_CARGOHOLD));
		assertEquals(2, CargoHolds.boat(InventoryID.SAILING_BOAT_2_CARGOHOLD));
		assertEquals(3, CargoHolds.boat(InventoryID.SAILING_BOAT_3_CARGOHOLD));
		assertEquals(4, CargoHolds.boat(InventoryID.SAILING_BOAT_4_CARGOHOLD));
		assertEquals(5, CargoHolds.boat(InventoryID.SAILING_BOAT_5_CARGOHOLD));
		assertEquals(0, CargoHolds.boat(InventoryID.INV));
		assertEquals(0, CargoHolds.boat(InventoryID.SAILING_TRAWLING_NET));
		assertEquals(0, CargoHolds.boat(-1));
		assertEquals(5, CargoHolds.CONTAINERS.length);
	}

	@Test
	public void aHoldIsRememberedAsLastSeenAndRefilledOnTheNextLook()
	{
		Nomad n = new Nomad();
		Store s = CargoHolds.record(n, 2, items(CANNONBALL, 400, SWORDFISH, 3), 1_000);
		assertEquals(Kind.CARGO, s.kind);
		assertEquals("cargo:2", s.key);
		assertEquals("Boat 2 cargo hold", s.name);
		assertEquals(1_000, s.seen);
		assertFalse(s.located());
		assertEquals(1, n.stores(Kind.CARGO).size());

		CargoHolds.record(n, 2, items(CANNONBALL, 150), 5_000);
		assertEquals(1, n.stores(Kind.CARGO).size());
		assertEquals(150, n.store("cargo:2").count(CANNONBALL));
		assertEquals(0, n.store("cargo:2").count(SWORDFISH));
		assertEquals(5_000, n.store("cargo:2").seen);

		// An emptied hold stays a known place, just empty.
		CargoHolds.record(n, 2, items(), 9_000);
		assertTrue(n.store("cargo:2").isEmpty());
		assertEquals(9_000, n.store("cargo:2").seen);
	}

	@Test
	public void holdsCountTowardsWhatTheAccountHolds()
	{
		Nomad n = new Nomad();
		n.carry(Nomad.INVENTORY, items(CANNONBALL, 100), 0);
		CargoHolds.record(n, 1, items(CANNONBALL, 400), 0);
		CargoHolds.record(n, 3, items(CANNONBALL, 50, SWORDFISH, 2), 0);
		assertEquals(550L, (long) n.holdings().get(CANNONBALL));
		assertEquals(2L, (long) n.holdings().get(SWORDFISH));
	}

	@Test
	public void theSlotsAreReadFromTheHoldWindowText()
	{
		Store s = new Store("cargo:1", Kind.CARGO, "Boat 1 cargo hold");
		assertTrue(CargoHolds.slots(s, "38", "90"));
		assertEquals(38, (int) s.used);
		assertEquals(90, (int) s.capacity);
		assertFalse("unchanged", CargoHolds.slots(s, "38", "90"));
		assertTrue(CargoHolds.slots(s, "<col=ffffff>40</col>", "/ 90"));
		assertEquals(40, (int) s.used);
		assertTrue(CargoHolds.slots(s, "12", "12/240"));
		assertEquals(240, (int) s.capacity);
		assertTrue(CargoHolds.slots(s, "0", "Capacity: 1,200"));
		assertEquals(1_200, (int) s.capacity);

		Store fresh = new Store("cargo:2", Kind.CARGO, "Boat 2 cargo hold");
		assertFalse(CargoHolds.slots(fresh, "", "90"));
		assertFalse(CargoHolds.slots(fresh, "38", null));
		assertFalse(CargoHolds.slots(fresh, "91", "90"));
		assertFalse(CargoHolds.slots(fresh, "1", "0"));
		assertFalse(CargoHolds.slots(fresh, "1", "99999999999"));
		assertFalse(CargoHolds.slots(null, "1", "90"));
		assertNull(fresh.used);
		assertNull(fresh.capacity);
	}

	@Test
	public void savesKeepTheHoldAndOldSavesStillRead()
	{
		Nomad n = new Nomad();
		Store s = CargoHolds.record(n, 4, items(CANNONBALL, 7), 1234);
		CargoHolds.slots(s, "1", "60");
		n.put(new Store("seedvault", Kind.SEED_VAULT, "Seed vault").at(1250, 3732, 0));
		String json = n.write(GSON);

		JsonObject stores = new JsonParser().parse(json).getAsJsonObject().getAsJsonObject("stores");
		assertFalse("other places gain no new fields", stores.getAsJsonObject("seedvault").has("used"));
		assertFalse(stores.getAsJsonObject("seedvault").has("capacity"));

		Nomad.Loaded back = Nomad.read(GSON, json);
		assertFalse(back.damaged);
		Store hold = back.nomad.store("cargo:4");
		assertEquals(Kind.CARGO, hold.kind);
		assertEquals(7, hold.count(CANNONBALL));
		assertEquals(1234, hold.seen);
		assertEquals(1, (int) hold.used);
		assertEquals(60, (int) hold.capacity);

		// A save from before cargo holds reads as it always did.
		String old = "{\"version\":1,\"account\":2,\"stores\":{\"seedvault\":{\"key\":\"seedvault\",\"kind\":\"SEED_VAULT\","
			+ "\"name\":\"Seed vault\",\"items\":{\"5318\":3},\"seen\":5,\"exact\":true,\"x\":1250,\"y\":3732,\"plane\":0}}}";
		Nomad.Loaded was = Nomad.read(GSON, old);
		assertFalse(was.damaged);
		assertNull(was.nomad.store("seedvault").used);
		assertTrue(was.nomad.stores(Kind.CARGO).isEmpty());
	}

	@Test
	public void theStoresViewShowsEachHoldWithItsSlots()
	{
		Nomad n = new Nomad();
		Store s = CargoHolds.record(n, 1, items(CANNONBALL, 400), 1_000);
		CargoHolds.slots(s, "1", "90");
		CargoHolds.record(n, 2, items(), 2_000);
		View v = Views.build(n, LOOK, StashBook.load(GSON), Route.load(), ACCOUNT, new int[]{3200, 3200, 0}, 0, Long.MAX_VALUE, 10_000);
		java.util.List<String> holds = new java.util.ArrayList<>();
		for (View.StoreView sv : v.stores)
		{
			if (sv.kind == Kind.CARGO)
			{
				holds.add(sv.key);
			}
		}
		assertEquals("in boat order, not by worth", java.util.Arrays.asList("cargo:1", "cargo:2"), holds);
		View.StoreView one = null;
		View.StoreView two = null;
		for (View.StoreView sv : v.stores)
		{
			one = "cargo:1".equals(sv.key) ? sv : one;
			two = "cargo:2".equals(sv.key) ? sv : two;
		}
		assertEquals(Kind.CARGO, one.kind);
		assertEquals(1, one.used);
		assertEquals(90, one.capacity);
		assertEquals(1, one.items.size());
		assertEquals(-1, one.distance);
		assertEquals(null, one.spot);
		assertEquals(-1, two.capacity);
		assertTrue(two.items.isEmpty());
	}

	private static final Route.Account ACCOUNT = new Route.Account()
	{
		@Override
		public boolean questDone(String quest)
		{
			return false;
		}

		@Override
		public int level(String skill)
		{
			return 1;
		}
	};

	private static final Views.Lookup LOOK = new Views.Lookup()
	{
		public String name(int id)
		{
			return id == CANNONBALL ? "Cannonball" : "Swordfish";
		}

		public long price(int id)
		{
			return 5;
		}

		public boolean stackable(int id)
		{
			return id == CANNONBALL;
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
}
