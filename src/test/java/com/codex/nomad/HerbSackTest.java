package com.codex.nomad;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import com.codex.nomad.core.Nomad;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

public class HerbSackTest
{
	@Test
	public void checkingListsAndPickingUpAdds()
	{
		HerbSack sack = new HerbSack(id -> id == ItemID.UNIDENTIFIED_RANARR ? "Grimy ranarr weed" : id == ItemID.UNIDENTIFIED_TORSTOL ? "Grimy torstol" : "Herb " + id);
		Nomad n = new Nomad();
		assertTrue(sack.heard("You look in your herb sack and see:", n, 1));
		assertTrue(sack.heard("12 x Grimy ranarr weed", n, 1));
		sack.tick(new HashMap<>(), new HashMap<>(), n, 2);
		assertTrue(sack.heard("You put the Grimy torstol herb into your herb sack.", n, 3));
		assertEquals(12L, (long) n.carried(HerbSack.NAME).get(ItemID.UNIDENTIFIED_RANARR));
		assertEquals(1L, (long) n.carried(HerbSack.NAME).get(ItemID.UNIDENTIFIED_TORSTOL));

		sack.heard("You rummage around to see if you can extract any herbs from your herb sack.", n, 4);
		Map<Integer, Long> out = new HashMap<>();
		out.put(ItemID.UNIDENTIFIED_RANARR, 5L);
		sack.tick(new HashMap<>(), out, n, 5);
		assertEquals(7L, (long) n.carried(HerbSack.NAME).get(ItemID.UNIDENTIFIED_RANARR));

		sack.heard("The herb sack is empty.", n, 6);
		assertTrue(n.carried(HerbSack.NAME).isEmpty());
	}
}
