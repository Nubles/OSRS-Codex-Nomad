package com.codex.nomad;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.codex.nomad.core.Nomad;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

public class SayingsTest
{
	@Test
	public void bertsSandstormIsCountedFromWhatHeSays()
	{
		Nomad n = new Nomad();
		assertTrue(Sayings.shown("I have 84 of your buckets and you've ground<br>enough sandstone for 1,200 buckets of sand.", Collections.emptyMap(), n, 1));
		assertEquals(84L, (long) n.store(Sayings.SANDSTORM).items.get(ItemID.BUCKET_EMPTY));
		assertEquals(1200L, (long) n.store(Sayings.SANDSTORM).items.get(ItemID.BUCKET_SAND));
		Map<Integer, Long> took = new HashMap<>();
		took.put(ItemID.BUCKET_SAND, 84L);
		assertTrue(Sayings.shown("If ya need any more sand, just ask.", took, n, 2));
		assertNull(n.store(Sayings.SANDSTORM).items.get(ItemID.BUCKET_EMPTY));
		assertEquals(1116L, (long) n.store(Sayings.SANDSTORM).items.get(ItemID.BUCKET_SAND));
		assertFalse(Sayings.shown("Hello there.", Collections.emptyMap(), n, 3));
	}

	@Test
	public void logStoresCannonAndFrozenWeapon()
	{
		Nomad n = new Nomad();
		assertTrue(Sayings.shown("This crate currently contains 10 logs, 0 oak logs, 25 willow logs, 0 yew logs and 0 magic logs.",
			Collections.emptyMap(), n, 1));
		assertEquals(25L, (long) n.store(Sayings.BALLOON).items.get(ItemID.WILLOW_LOGS));
		assertTrue(Sayings.heard("You have 9 sets of logs left in storage.", -1, s -> -1, n, 2));
		assertEquals(9L, (long) n.store(Sayings.BALLOON).items.get(ItemID.LOGS));

		assertTrue(Sayings.shown("Your log storage contains:<br>Oak logs: 12, Teak logs: 3", Collections.emptyMap(), n, 3));
		assertEquals(3L, (long) n.store(Sayings.FORESTRY).items.get(ItemID.TEAK_LOGS));

		assertTrue(Sayings.heard("Your cannon has decayed. Speak to Nulodion to obtain a new one.", -1, s -> -1, n, 4));
		assertEquals(4, n.store(Sayings.NULODION).items.size());
		assertTrue(Sayings.heard("The dwarf gives you a new cannon.", -1, s -> -1, n, 5));
		assertNull(n.store(Sayings.NULODION));

		assertTrue(Sayings.heard("Your weapon freezes over. You're forced to let go!", ItemID.ABYSSAL_WHIP, s -> -1, n, 6));
		assertEquals(1L, (long) n.store(Sayings.EYATLALLI).items.get(ItemID.ABYSSAL_WHIP));
		assertTrue(Sayings.heard("Eyatlalli returns your lost weapon to you.", -1, s -> -1, n, 7));
		assertNull(n.store(Sayings.EYATLALLI));
	}
}
