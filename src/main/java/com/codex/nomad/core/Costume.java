package com.codex.nomad.core;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The costume room's furniture and what each piece holds. The game shows every piece through the
 * same window, so the piece open is the one most of the shown items belong to.
 */
public final class Costume
{
	private final Map<String, Set<Integer>> pieces = new LinkedHashMap<>();

	Costume(Map<String, List<Integer>> data)
	{
		data.forEach((k, v) -> pieces.put(k, new HashSet<>(v)));
	}

	public static Costume load(Gson gson)
	{
		Type type = new TypeToken<LinkedHashMap<String, List<Integer>>>()
		{
		}.getType();
		try (InputStream in = Costume.class.getResourceAsStream("/com/codex/nomad/costume.json"))
		{
			Map<String, List<Integer>> data = in == null ? null : gson.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), type);
			return new Costume(data == null ? Collections.emptyMap() : data);
		}
		catch (Exception e)
		{
			return new Costume(Collections.emptyMap());
		}
	}

	public int size()
	{
		return pieces.size();
	}

	/**
	 * @param shown what the costume room window shows, by item
	 * @return the piece of furniture open, or null when nothing shown belongs to any
	 */
	public String piece(Map<Integer, Long> shown)
	{
		String best = null;
		int most = 0;
		for (Map.Entry<String, Set<Integer>> e : pieces.entrySet())
		{
			int n = 0;
			for (int id : shown.keySet())
			{
				if (e.getValue().contains(id))
				{
					n++;
				}
			}
			if (n > most)
			{
				most = n;
				best = e.getKey();
			}
		}
		return best;
	}

	/** What of the shown items the piece holds. */
	public Map<Integer, Long> held(String piece, Map<Integer, Long> shown)
	{
		Set<Integer> ids = pieces.get(piece);
		Map<Integer, Long> out = new LinkedHashMap<>();
		if (ids != null)
		{
			shown.forEach((k, v) ->
			{
				if (ids.contains(k))
				{
					out.put(k, v);
				}
			});
		}
		return out;
	}
}
