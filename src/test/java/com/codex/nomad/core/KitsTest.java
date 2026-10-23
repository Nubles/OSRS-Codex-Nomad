package com.codex.nomad.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class KitsTest
{
	private static final String KITS = "{\"version\":1,\"active\":\"preset:vorkath\",\"kits\":["
		+ "{\"id\":\"kit:1\",\"name\":\"Konar\",\"preset\":false,\"needs\":["
		+ "{\"supply\":\"FOOD\",\"perTrip\":200,\"items\":{\"391\":22,\"385\":20}},"
		+ "{\"supply\":\"item:952\",\"perTrip\":0,\"items\":{\"952\":1}}]},"
		+ "{\"id\":\"preset:vorkath\",\"name\":\"Vorkath\",\"preset\":true,\"needs\":["
		+ "{\"supply\":\"PRAYER\",\"perTrip\":12,\"items\":{\"2434\":4,\"139\":3}}]},"
		+ "{\"id\":\"preset:zulrah\",\"name\":\"Zulrah\",\"preset\":true,\"needs\":[]}]}";

	@Test
	public void kitsBecomePackingListsFromWhatYouOwn()
	{
		Map<Integer, Long> owned = new HashMap<>();
		owned.put(385, 4L);
		owned.put(139, 2L);
		List<Trip> trips = Kits.read(KITS, owned, id -> "Item " + id);
		assertEquals(2, trips.size());
		Trip konar = trips.get(0);
		assertEquals("Quartermaster: Konar", konar.name);
		// 4 sharks you own cover 80 hp; the other 120 asks for 6 manta rays.
		assertEquals(385, konar.wants.get(0).item);
		assertEquals(4, konar.wants.get(0).qty);
		assertEquals(391, konar.wants.get(1).item);
		assertEquals(6, konar.wants.get(1).qty);
		assertEquals(952, konar.wants.get(2).item);
		assertEquals(1, konar.wants.get(2).qty);
		Trip vorkath = trips.get(1);
		assertEquals("Quartermaster: Vorkath", vorkath.name);
		assertEquals(139, vorkath.wants.get(0).item);
		assertEquals(2, vorkath.wants.get(0).qty);
		assertEquals(2434, vorkath.wants.get(1).item);
		assertEquals(2, vorkath.wants.get(1).qty);
	}

	@Test
	public void brokenOrMissingKitsGiveNothing()
	{
		assertTrue(Kits.read(null, Collections.emptyMap(), id -> "").isEmpty());
		assertTrue(Kits.read("{not json", Collections.emptyMap(), id -> "").isEmpty());
		Trip old = new Trip(Kits.PREFIX + "Gone", 0);
		assertEquals(Collections.singletonList(old.name), Kits.stale(Collections.singletonList(old), Collections.emptyList()));
	}
}
