package com.codex.nomad;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class ViewsTest
{
	@Test
	public void pilesSayWhetherTheyFitInYourPack()
	{
		assertEquals("Needs 21 slots; you have 3 free", Views.roomNote("Lost when its timer runs out", 21, 3));
		assertEquals("Lost when its timer runs out · needs 1 slot", Views.roomNote("Lost when its timer runs out", 1, 3));
		assertEquals("Lost when its timer runs out", Views.roomNote("Lost when its timer runs out", 0, 3));
	}

	@Test
	public void cheapestThingsAreDroppedFirstForAPile()
	{
		Views.Lookup look = new Views.Lookup()
		{
			public String name(int id)
			{
				return id == 1 ? "Logs" : id == 2 ? "Feather" : id == 3 ? "Shark" : "Rune platebody";
			}

			public long price(int id)
			{
				return id == 1 ? 50 : id == 2 ? 3 : id == 3 ? 900 : 38000;
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
		java.util.Map<Integer, Long> pile = new java.util.HashMap<>();
		pile.put(4, 3L);
		java.util.Map<Integer, Long> inv = new java.util.LinkedHashMap<>();
		inv.put(3, 5L);
		inv.put(1, 10L);
		inv.put(2, 100L);
		assertEquals(java.util.Arrays.asList("4 Logs"), Views.shed(pile, inv, look, 4));
		assertEquals(java.util.Arrays.asList("10 Logs", "Feather", "2 Shark"), Views.shed(pile, inv, look, 13));
		assertEquals("Needs 3 slots; you have 0 free · drop 3 Logs first",
			Views.shedNote("Needs 3 slots; you have 0 free", pile, inv, look, 3));
	}

	@Test
	public void theLoadoutSendsValuablesWithNowhereSafeToDeathStorageWithARoute()
	{
		Views.Lookup look = new Views.Lookup()
		{
			public String name(int id)
			{
				return id == net.runelite.api.gameval.ItemID.LAWRUNE ? "Law rune" : "Item " + id;
			}

			public long price(int id)
			{
				return id == 11802 ? 10_000_000 : 100;
			}

			public boolean stackable(int id)
			{
				return id == net.runelite.api.gameval.ItemID.LAWRUNE;
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
		com.codex.nomad.core.Nomad n = new com.codex.nomad.core.Nomad();
		java.util.Map<Integer, Long> inv = new java.util.LinkedHashMap<>();
		inv.put(385, 27L);
		inv.put(11802, 1L);
		inv.put(net.runelite.api.gameval.ItemID.BH_RUNE_POUCH, 1L);
		n.carry(com.codex.nomad.core.Nomad.INVENTORY, inv, 0);
		java.util.Map<Integer, Long> pouch = new java.util.HashMap<>();
		pouch.put(net.runelite.api.gameval.ItemID.LAWRUNE, 300L);
		n.carry(com.codex.nomad.core.Nomad.RUNE_POUCH, pouch, 0);
		com.codex.nomad.core.Trip t = new com.codex.nomad.core.Trip("Sharks", 0);
		t.wants.add(new com.codex.nomad.core.Trip.Want(385, "Shark", 27, false));
		t.wants.add(new com.codex.nomad.core.Trip.Want(net.runelite.api.gameval.ItemID.LAWRUNE, "Law rune", 100, false));
		com.codex.nomad.core.Travel atlas = com.codex.nomad.core.Travel.read(new com.google.gson.Gson(),
			"{\"version\":1,\"walkSecondsPerTile\":0.3,\"teleports\":[{\"name\":\"Zul-andra teleport\",\"x\":2196,\"y\":3056,\"plane\":0,\"seconds\":5,\"carried\":true}]}");
		com.codex.nomad.ui.View.LoadoutView l = Views.loadout(n, t, look, null, null, new int[]{3200, 3200, 0}, atlas, 50_000);
		// Pouch and sharks fill all 28; the law runes ride in the pouch.
		assertEquals(28, l.needed);
		assertEquals(1, l.pouch.size());
		assertEquals(1, l.leave.size());
		com.codex.nomad.ui.View.LeaveView godsword = l.leave.get(0);
		assertEquals("Death storage", godsword.home);
		assertEquals(true, godsword.must);
		assertEquals(2196, godsword.spot.x);
		assertEquals("~5s · Zul-andra teleport", godsword.travel);
		assertEquals(28, l.grid.size());
		assertEquals("CONTAINER", l.grid.get(0).role);
		assertEquals("LIST", l.grid.get(27).role);
	}
}
