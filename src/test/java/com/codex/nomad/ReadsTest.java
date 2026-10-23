package com.codex.nomad;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.codex.nomad.core.Kind;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import org.junit.Test;

public class ReadsTest
{
	private static Reads.Vars vars(Map<Integer, Integer> bits)
	{
		return new Reads.Vars()
		{
			@Override
			public int bit(int id)
			{
				return bits.getOrDefault(id, 0);
			}

			@Override
			public int player(int id)
			{
				return 0;
			}
		};
	}

	private static Reads.Read read(String key)
	{
		for (Reads.Read r : Reads.ALL)
		{
			if (r.key.equals(key))
			{
				return r;
			}
		}
		throw new AssertionError(key);
	}

	@Test
	public void everyReadHasAKeyAndSomethingToWatch()
	{
		Set<String> keys = new HashSet<>();
		for (Reads.Read r : Reads.ALL)
		{
			assertTrue(r.key, keys.add(r.key));
			assertFalse(r.key, r.bits.isEmpty() && r.players.isEmpty());
			assertTrue(r.key, r.carried == (r.kind == null));
			assertTrue(r.key, r.carried || r.kind.automatic());
			assertTrue(r.read(vars(new HashMap<>())).isEmpty());
		}
	}

	@Test
	public void theLeprechaunCountsExtrasAndCompost()
	{
		Map<Integer, Integer> b = new HashMap<>();
		b.put(VarbitID.FARMING_TOOLS_SPADE, 1);
		b.put(VarbitID.FARMING_TOOLS_EXTRASPADES, 1);
		b.put(VarbitID.FARMING_TOOLS_COMPOST, 10);
		b.put(VarbitID.FARMING_TOOLS_EXTRACOMPOST, 1);
		b.put(VarbitID.FARMING_TOOLS_WATERINGCAN, 9);
		Map<Integer, Long> got = read("kept:leprechaun").read(vars(b));
		assertEquals(3L, (long) got.get(ItemID.SPADE));
		assertEquals(266L, (long) got.get(ItemID.BUCKET_COMPOST));
		assertEquals(1L, (long) got.get(ItemID.WATERING_CAN_8));
		assertEquals(3, got.size());
	}

	@Test
	public void boltPouchAndCoffersReadTheirTypesAndScales()
	{
		Map<Integer, Integer> b = new HashMap<>();
		b.put(VarbitID.XBOWS_POUCH_SLOT1, 7);
		b.put(VarbitID.XBOWS_POUCH_NUM1, 250);
		b.put(VarbitID.NZONE_CASH, 12);
		assertEquals(250L, (long) read("bolt pouch").read(vars(b)).get(ItemID.XBOWS_CROSSBOW_BOLTS_RUNITE));
		assertEquals(12_000L, (long) read("coffer:nmz").read(vars(b)).get(ItemID.COINS));
		assertEquals(Kind.COFFER, read("coffer:nmz").kind);
		assertEquals(read("bolt pouch"), Reads.touched(VarbitID.XBOWS_POUCH_NUM1, -1).get(0));
	}
}
