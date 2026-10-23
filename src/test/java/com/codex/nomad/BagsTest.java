package com.codex.nomad;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.codex.nomad.core.Nomad;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

public class BagsTest
{
	@Test
	public void coalAndGemBagsReadTheirMessages()
	{
		Nomad n = new Nomad();
		assertTrue(Bags.heard("The coal bag contains 27 pieces of coal.", n, 1));
		assertEquals(27L, (long) n.carried(Bags.COAL).get(ItemID.COAL));
		assertTrue(Bags.heard("The coal bag still contains one piece of coal.", n, 2));
		assertEquals(1L, (long) n.carried(Bags.COAL).get(ItemID.COAL));
		assertTrue(Bags.heard("The coal bag is now empty.", n, 3));
		assertTrue(n.carried(Bags.COAL).isEmpty());

		assertTrue(Bags.heard("Sapphires: 2 / Emeralds: 0 / Rubies: 5<br>Diamonds: 1 / Dragonstones: 0", n, 4));
		assertEquals(2L, (long) n.carried(Bags.GEMS).get(ItemID.UNCUT_SAPPHIRE));
		assertEquals(5L, (long) n.carried(Bags.GEMS).get(ItemID.UNCUT_RUBY));
		assertEquals(1L, (long) n.carried(Bags.GEMS).get(ItemID.UNCUT_DIAMOND));
		assertTrue(Bags.heard("You empty all of your containers into the bank.", n, 5));
		assertTrue(n.carried(Bags.GEMS).isEmpty());
		assertFalse(Bags.heard("You swing your pick at the rock.", n, 6));
	}

	@Test
	public void theBarrelAndBasketBoxesRead()
	{
		Nomad n = new Nomad();
		String box = String.join("<br>", "The barrel contains:", "1 x Raw anglerfish, 2 x Raw monkfish, 3 x Raw", "shrimps");
		assertTrue(Bags.checked(box, name -> name.equals("Raw anglerfish") ? 13439 : name.equals("Raw monkfish") ? 7944
			: name.equals("Raw shrimps") ? 317 : -1, n, 1));
		assertEquals(3L, (long) n.carried(Bags.FISH).get(317));
		assertEquals(3, n.carried(Bags.FISH).size());
		assertTrue(Bags.checked("The basket contains:<br>28 x Yew logs", name -> name.equals("Yew logs") ? 1515 : -1, n, 2));
		assertEquals(28L, (long) n.carried(Bags.LOGS).get(1515));
		assertTrue(Bags.checked("The barrel is empty.", name -> -1, n, 3));
		assertTrue(n.carried(Bags.FISH).isEmpty());
		assertFalse(Bags.checked("Hello, World!", name -> -1, n, 4));
	}
}
