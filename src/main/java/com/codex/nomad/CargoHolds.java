package com.codex.nomad;

import com.codex.nomad.core.Kind;
import com.codex.nomad.core.Nomad;
import com.codex.nomad.core.Store;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.api.gameval.InventoryID;

/**
 * The cargo hold of each Sailing boat. A player owns up to five boats, and the game keeps one
 * item container per boat's hold. Nomad can only read a hold while it is open, so each hold is
 * remembered as last seen, with when. The hold window also shows slots used and slots in all,
 * read from its text while it is open. Boats sail away, so a hold has no fixed place on the map.
 */
final class CargoHolds
{
	static final String PREFIX = "cargo:";
	/** The hold's container for boats 1 to 5, in order. */
	static final int[] CONTAINERS = {
		InventoryID.SAILING_BOAT_1_CARGOHOLD,
		InventoryID.SAILING_BOAT_2_CARGOHOLD,
		InventoryID.SAILING_BOAT_3_CARGOHOLD,
		InventoryID.SAILING_BOAT_4_CARGOHOLD,
		InventoryID.SAILING_BOAT_5_CARGOHOLD,
	};
	/** No hold has more slots than this; a larger figure is a misread. */
	static final int MOST_SLOTS = 10_000;
	private static final Pattern NUMBER = Pattern.compile("\\d[\\d,]*");

	private CargoHolds()
	{
	}

	/** Which boat, 1 to 5, a container is the hold of; 0 when it is not a hold. */
	static int boat(int container)
	{
		for (int i = 0; i < CONTAINERS.length; i++)
		{
			if (CONTAINERS[i] == container)
			{
				return i + 1;
			}
		}
		return 0;
	}

	static String key(int boat)
	{
		return PREFIX + boat;
	}

	static String name(int boat)
	{
		return "Boat " + boat + " cargo hold";
	}

	/** Notes what one boat's hold held when it was open. An empty hold is remembered as empty. */
	static Store record(Nomad nomad, int boat, Map<Integer, Long> items, long now)
	{
		Store s = nomad.store(key(boat));
		if (s == null)
		{
			s = new Store(key(boat), Kind.CARGO, name(boat));
			nomad.put(s);
		}
		s.fill(items, now);
		return s;
	}

	/**
	 * Notes the slots the open hold's window shows, when both figures read as whole numbers.
	 *
	 * @return true if anything changed
	 */
	static boolean slots(Store s, String usedText, String capacityText)
	{
		Integer used = number(usedText, false);
		Integer capacity = number(capacityText, true);
		if (s == null || used == null || capacity == null || capacity <= 0 || used > capacity)
		{
			return false;
		}
		if (used.equals(s.used) && capacity.equals(s.capacity))
		{
			return false;
		}
		s.used = used;
		s.capacity = capacity;
		return true;
	}

	/**
	 * A whole number in a line of game text, such as "90", "/ 90" or "38/90": the first one, or the
	 * last one when {@code last}. Null if there is none or it is too large to be a slot count.
	 */
	static Integer number(String text, boolean last)
	{
		if (text == null)
		{
			return null;
		}
		Matcher m = NUMBER.matcher(text.replaceAll("<[^>]*>", ""));
		String found = null;
		while (m.find())
		{
			found = m.group();
			if (!last)
			{
				break;
			}
		}
		if (found == null)
		{
			return null;
		}
		try
		{
			long n = Long.parseLong(found.replace(",", ""));
			return n > MOST_SLOTS ? null : (int) n;
		}
		catch (NumberFormatException ex)
		{
			return null;
		}
	}
}
