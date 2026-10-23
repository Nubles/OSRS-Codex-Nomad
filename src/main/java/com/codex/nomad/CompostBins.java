package com.codex.nomad;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;

/**
 * The farming compost bins, read from the game's bin varbit while you stand near one. Only
 * finished compost and rotten tomatoes count: a bin still filling or rotting holds neither yet.
 * Regions and values follow Dude, Where's My Stuff (BSD 2-Clause).
 */
final class CompostBins
{
	static final class Bin
	{
		final String key;
		final String name;
		final boolean big;
		final int varbit;

		Bin(String area, boolean big, int varbit)
		{
			this.key = "compost:" + area.toLowerCase().replace(' ', '-');
			this.name = "Compost bin, " + area;
			this.big = big;
			this.varbit = varbit;
		}
	}

	private static final Map<Integer, Bin> BY_REGION = new HashMap<>();

	static
	{
		bin(new Bin("Ardougne", false, VarbitID.FARMING_TRANSMIT_E), 10548);
		bin(new Bin("Catherby", false, VarbitID.FARMING_TRANSMIT_E), 11062);
		bin(new Bin("Falador", false, VarbitID.FARMING_TRANSMIT_E), 12083);
		bin(new Bin("Kourend", false, VarbitID.FARMING_TRANSMIT_E), 6967, 6711);
		bin(new Bin("Morytania", false, VarbitID.FARMING_TRANSMIT_E), 14391, 14390);
		bin(new Bin("Farming Guild", true, VarbitID.FARMING_TRANSMIT_N), 4922, 5177, 5178, 5179, 4921, 4923, 4665, 4666, 4667);
		bin(new Bin("Prifddinas", false, VarbitID.FARMING_TRANSMIT_D), 13151, 12895, 12894, 13150, 12994, 12993, 12737, 12738,
			12126, 12127, 13250);
	}

	private CompostBins()
	{
	}

	private static void bin(Bin b, int... regions)
	{
		for (int r : regions)
		{
			BY_REGION.put(r, b);
		}
	}

	/** The bin whose varbit is meaningful in this region, or null. */
	static Bin near(int region)
	{
		return BY_REGION.get(region);
	}

	/** What a bin holds for the varbit's value. */
	static Map<Integer, Long> read(boolean big, int v)
	{
		Map<Integer, Long> out = new LinkedHashMap<>();
		int compost = 0;
		int superCompost = 0;
		int ultra = 0;
		int tomatoes = 0;
		if (v >= 16 && v <= 30)
		{
			compost = v - 15;
		}
		else if (v >= 48 && v <= 62)
		{
			superCompost = v - 47;
		}
		else if (v >= 144 && v <= 158)
		{
			tomatoes = v - 143;
		}
		else if (big)
		{
			if (v >= 78 && v <= 92)
			{
				compost = 15 + v - 77;
			}
			else if (v == 93)
			{
				compost = 30;
			}
			else if (v == 99)
			{
				superCompost = 30;
			}
			else if (v >= 100 && v <= 114)
			{
				superCompost = 15 + v - 99;
			}
			else if (v >= 176 && v <= 205)
			{
				ultra = v - 175;
			}
			else if (v >= 207 && v <= 221)
			{
				tomatoes = 15 + v - 206;
			}
			else if (v == 222)
			{
				tomatoes = 30;
			}
		}
		else if (v == 94)
		{
			compost = 15;
		}
		else if (v == 126)
		{
			superCompost = 15;
		}
		else if (v == 160)
		{
			tomatoes = 15;
		}
		else if (v >= 176 && v <= 190)
		{
			ultra = v - 175;
		}
		put(out, ItemID.BUCKET_COMPOST, compost);
		put(out, ItemID.BUCKET_SUPERCOMPOST, superCompost);
		put(out, ItemID.BUCKET_ULTRACOMPOST, ultra);
		put(out, ItemID.ROTTEN_TOMATO, tomatoes);
		return out.isEmpty() ? Collections.emptyMap() : out;
	}

	private static void put(Map<Integer, Long> out, int id, int n)
	{
		if (n > 0)
		{
			out.put(id, (long) n);
		}
	}
}
