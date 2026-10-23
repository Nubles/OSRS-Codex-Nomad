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
}
