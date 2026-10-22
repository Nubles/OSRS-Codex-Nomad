package com.codex.nomad;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Map;
import java.util.TreeMap;

/**
 * What the account holds, written for the rest of the Codex suite in the same shape as Codex Bank
 * Steward's bank: {version, savedAt, items{id: qty}, truncated}. An ultimate ironman has no bank,
 * so this stands in for one, marked with its source so a real bank copy is never overwritten.
 */
final class Holdings
{
	static final int VERSION = 1;
	static final int MAX_ITEMS = 2000;
	static final String SOURCE = "nomad";

	/** The written form. Field order is the contract's. */
	static final class Snapshot
	{
		int version = VERSION;
		long savedAt;
		Map<Integer, Long> items = new TreeMap<>();
		boolean truncated;
		String source = SOURCE;
	}

	private Holdings()
	{
	}

	static String write(Gson gson, Map<Integer, Long> items, long now)
	{
		Snapshot s = new Snapshot();
		s.savedAt = now;
		for (Map.Entry<Integer, Long> e : new TreeMap<>(items).entrySet())
		{
			if (e.getKey() <= 0 || e.getValue() <= 0)
			{
				continue;
			}
			if (s.items.size() >= MAX_ITEMS)
			{
				s.truncated = true;
				break;
			}
			s.items.put(e.getKey(), e.getValue());
		}
		return gson.toJson(s);
	}

	/** True when a saved bank copy is absent or one Nomad wrote, so Nomad may write over it. */
	static boolean ours(Gson gson, String saved)
	{
		if (saved == null || saved.trim().isEmpty())
		{
			return true;
		}
		try
		{
			JsonElement e = new JsonParser().parse(saved);
			if (!e.isJsonObject())
			{
				return false;
			}
			JsonObject o = e.getAsJsonObject();
			return o.has("source") && SOURCE.equals(o.get("source").getAsString());
		}
		catch (RuntimeException ex)
		{
			return false;
		}
	}
}
