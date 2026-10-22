package com.codex.nomad;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.codex.nomad.core.Costume;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

/** The holdings Nomad writes read like Codex Bank Steward's bank, and never overwrite a real one. */
public class ContractTest
{
	private static final Gson GSON = new GsonBuilder().create();

	@Test
	public void holdingsHaveTheBankShape()
	{
		Map<Integer, Long> items = new HashMap<>();
		items.put(4151, 1L);
		items.put(995, 2_000_000L);
		items.put(-1, 5L);
		String json = Holdings.write(GSON, items, 1234L);
		JsonObject o = new JsonParser().parse(json).getAsJsonObject();
		assertEquals(1, o.get("version").getAsInt());
		assertEquals(1234L, o.get("savedAt").getAsLong());
		assertEquals(1, o.getAsJsonObject("items").get("4151").getAsLong());
		assertEquals(2_000_000L, o.getAsJsonObject("items").get("995").getAsLong());
		assertEquals(2, o.getAsJsonObject("items").size());
		assertFalse(o.get("truncated").getAsBoolean());
		assertTrue(Holdings.ours(GSON, json));
	}

	@Test
	public void aRealBankCopyIsNeverOverwritten()
	{
		assertTrue(Holdings.ours(GSON, null));
		assertTrue(Holdings.ours(GSON, ""));
		assertFalse(Holdings.ours(GSON, "{\"version\":1,\"savedAt\":5,\"items\":{\"1\":1},\"truncated\":false}"));
		assertFalse(Holdings.ours(GSON, "garbage{"));
	}

	@Test
	public void theCostumeRoomPieceIsTheOneMostItemsBelongTo()
	{
		Costume c = Costume.load(GSON);
		assertTrue(c.size() >= 10);
		Map<Integer, Long> shown = new HashMap<>();
		shown.put(8839, 1L);
		shown.put(8840, 1L);
		shown.put(11665, 1L);
		assertEquals("Armour case", c.piece(shown));
		assertEquals(3, c.held("Armour case", shown).size());
	}
}
