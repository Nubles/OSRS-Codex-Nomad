package com.codex.nomad;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.MenuAction;
import net.runelite.api.Tile;
import net.runelite.api.TileObject;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.gameval.VarbitID;

/**
 * The floor of the Chambers of Xeric, which ultimate ironmen use as storage: what is dropped there
 * stays for the whole raid and is destroyed when the player leaves. Nomad keeps it as a store with no
 * timer, finds the ways out and pushes their left-click down while anything is left behind. The exit
 * objects follow UIM Utilities (BSD 2-Clause).
 */
class RaidFloor
{
	private static final Set<Integer> EXITS = new HashSet<>(Arrays.asList(
		49999, 50000, ObjectID.RAIDS_BOSSEXIT, ObjectID.RAIDS_EXIT_STEPS));
	/** The in-raid varbit reads 0 for a moment on each room load, so leaving takes this long to count. */
	private static final int TICKS_OUT = 5;

	private final Client client;
	private final List<TileObject> exits = new ArrayList<>();
	private boolean scanned;
	private int ticksOut;

	RaidFloor(Client client)
	{
		this.client = client;
	}

	boolean inRaid()
	{
		return client.getVarbitValue(VarbitID.RAIDS_CLIENT_INDUNGEON) == 1;
	}

	static boolean exit(int object)
	{
		return EXITS.contains(object);
	}

	static boolean objectAction(MenuAction action)
	{
		return action == MenuAction.GAME_OBJECT_FIRST_OPTION || action == MenuAction.GAME_OBJECT_SECOND_OPTION
			|| action == MenuAction.GAME_OBJECT_THIRD_OPTION || action == MenuAction.GAME_OBJECT_FOURTH_OPTION
			|| action == MenuAction.GAME_OBJECT_FIFTH_OPTION;
	}

	List<TileObject> exits()
	{
		return exits;
	}

	void spawned(GameObject o)
	{
		if (o != null && exit(o.getId()) && !exits.contains(o) && inRaid())
		{
			exits.add(o);
		}
	}

	void despawned(GameObject o)
	{
		exits.remove(o);
	}

	void sceneLoading()
	{
		exits.clear();
		scanned = false;
	}

	/**
	 * Called each tick. Reads the scene once per load for exits that spawned before the raid varbit
	 * was set.
	 *
	 * @return true on the tick the player is judged to have left the raid
	 */
	boolean tick()
	{
		if (inRaid())
		{
			ticksOut = 0;
			if (!scanned)
			{
				scanned = true;
				for (Tile[][] plane : client.getTopLevelWorldView().getScene().getTiles())
				{
					for (Tile[] column : plane)
					{
						for (Tile tile : column)
						{
							if (tile != null && tile.getGameObjects() != null)
							{
								for (GameObject o : tile.getGameObjects())
								{
									spawned(o);
								}
							}
						}
					}
				}
			}
			return false;
		}
		exits.clear();
		return ++ticksOut == TICKS_OUT;
	}
}
