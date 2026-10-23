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
}
