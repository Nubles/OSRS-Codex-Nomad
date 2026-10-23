package com.codex.nomad;

import com.codex.nomad.core.Kind;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;

/**
 * Everything the game keeps count of by itself, so Nomad can read it at any time without the player
 * opening anything: the carried sacks, pouches, kits and books, the tool leprechaun, the Blast
 * Furnace, fossil storage and the coffers. Adapted from Dude, Where's My Stuff (BSD 2-Clause).
 */
final class Reads
{
	/** Reads game variables; the plugin passes the client, tests pass a map. */
	interface Vars
	{
		int bit(int id);

		int player(int id);
	}

	/** Something whose contents the game holds in variables. */
	static final class Read
	{
		final String key;
		final String name;
		/** Carried in the pack, or a store with this kind. */
		final boolean carried;
		final Kind kind;
		final int x;
		final int y;
		final int plane;
		final Set<Integer> bits = new HashSet<>();
		final Set<Integer> players = new HashSet<>();
		private final Reader reader;

		Read(String key, String name, boolean carried, Kind kind, int[] at, int[] bits, int[] players, Reader reader)
		{
			this.key = key;
			this.name = name;
			this.carried = carried;
			this.kind = kind;
			this.x = at == null ? 0 : at[0];
			this.y = at == null ? 0 : at[1];
			this.plane = at == null ? -1 : at[2];
			for (int b : bits)
			{
				this.bits.add(b);
			}
			for (int p : players)
			{
				this.players.add(p);
			}
			this.reader = reader;
		}

		Map<Integer, Long> read(Vars v)
		{
			Map<Integer, Long> out = new LinkedHashMap<>();
			reader.read(v, out);
			out.values().removeIf(q -> q == null || q <= 0);
			return out;
		}

		boolean watches(int bit, int player)
		{
			return bit >= 0 ? bits.contains(bit) : player >= 0 && players.contains(player);
		}
	}

	interface Reader
	{
		void read(Vars v, Map<Integer, Long> out);
	}

	/** A carried thing whose contents show in an item container when it is opened or checked. */
	static final class Opened
	{
		final int container;
		final String name;

		Opened(int container, String name)
		{
			this.container = container;
			this.name = name;
		}
	}

	static final List<Opened> OPENED = Collections.unmodifiableList(Arrays.asList(
		new Opened(InventoryID.SEED_BOX, "Seed box"),
		new Opened(InventoryID.TACKLE_BOX, "Tackle box"),
		new Opened(InventoryID.HUNTSMANS_KIT, "Huntsman's kit"),
		new Opened(InventoryID.FORESTRY_KIT, "Forestry kit"),
		new Opened(InventoryID.PREPOT_DEVICE_INV, "Chugging barrel")));

	// ---- what the variables hold ----

	private static final int[] PLANK_BITS = {
		VarbitID.PLANK_SACK_PLAIN, VarbitID.PLANK_SACK_OAK, VarbitID.PLANK_SACK_TEAK, VarbitID.PLANK_SACK_MAHOGANY,
		VarbitID.PLANK_SACK_CAMPHOR, VarbitID.PLANK_SACK_IRONWOOD, VarbitID.PLANK_SACK_ROSEWOOD};
	private static final int[] PLANKS = {
		ItemID.WOODPLANK, ItemID.PLANK_OAK, ItemID.PLANK_TEAK, ItemID.PLANK_MAHOGANY,
		ItemID.PLANK_CAMPHOR, ItemID.PLANK_IRONWOOD, ItemID.PLANK_ROSEWOOD};

	private static final int[] BOLT_SLOTS = {
		VarbitID.XBOWS_POUCH_SLOT1, VarbitID.XBOWS_POUCH_SLOT2, VarbitID.XBOWS_POUCH_SLOT3, VarbitID.XBOWS_POUCH_SLOT4};
	private static final int[] BOLT_COUNTS = {
		VarbitID.XBOWS_POUCH_NUM1, VarbitID.XBOWS_POUCH_NUM2, VarbitID.XBOWS_POUCH_NUM3, VarbitID.XBOWS_POUCH_NUM4};
	/** The bolt pouch's type values, in the game's order. */
	static final int[] BOLTS = {
		-1, ItemID.BOLT, ItemID.XBOWS_CROSSBOW_BOLTS_BLURITE, ItemID.XBOWS_CROSSBOW_BOLTS_IRON,
		ItemID.XBOWS_CROSSBOW_BOLTS_STEEL, ItemID.XBOWS_CROSSBOW_BOLTS_MITHRIL,
		ItemID.XBOWS_CROSSBOW_BOLTS_ADAMANTITE, ItemID.XBOWS_CROSSBOW_BOLTS_RUNITE,
		ItemID.XBOWS_CROSSBOW_BOLTS_SILVER, ItemID.POISON_BOLT, ItemID.XBOWS_CROSSBOW_BOLTS_BLURITE_POISONED,
		ItemID.XBOWS_CROSSBOW_BOLTS_IRON_POISONED, ItemID.XBOWS_CROSSBOW_BOLTS_STEEL_POISONED,
		ItemID.XBOWS_CROSSBOW_BOLTS_MITHRIL_POISONED, ItemID.XBOWS_CROSSBOW_BOLTS_ADAMANTITE_POISONED,
		ItemID.XBOWS_CROSSBOW_BOLTS_RUNITE_POISONED, ItemID.XBOWS_CROSSBOW_BOLTS_SILVER_POISONED,
		ItemID.POISON_BOLT_, ItemID.XBOWS_CROSSBOW_BOLTS_BLURITE_POISONED_,
		ItemID.XBOWS_CROSSBOW_BOLTS_IRON_POISONED_, ItemID.XBOWS_CROSSBOW_BOLTS_STEEL_POISONED_,
		ItemID.XBOWS_CROSSBOW_BOLTS_MITHRIL_POISONED_, ItemID.XBOWS_CROSSBOW_BOLTS_ADAMANTITE_POISONED_,
		ItemID.XBOWS_CROSSBOW_BOLTS_RUNITE_POISONED_, ItemID.XBOWS_CROSSBOW_BOLTS_SILVER_POISONED_,
		ItemID.POISON_BOLT__, ItemID.XBOWS_CROSSBOW_BOLTS_BLURITE_POISONED__,
		ItemID.XBOWS_CROSSBOW_BOLTS_IRON_POISONED__, ItemID.XBOWS_CROSSBOW_BOLTS_STEEL_POISONED__,
		ItemID.XBOWS_CROSSBOW_BOLTS_MITHRIL_POISONED__, ItemID.XBOWS_CROSSBOW_BOLTS_ADAMANTITE_POISONED__,
		ItemID.XBOWS_CROSSBOW_BOLTS_RUNITE_POISONED__, ItemID.XBOWS_CROSSBOW_BOLTS_SILVER_POISONED__,
		ItemID.OPAL_BOLT, ItemID.XBOWS_CROSSBOW_BOLTS_BLURITE_TIPPED_JADE, ItemID.PEARL_BOLT,
		ItemID.XBOWS_CROSSBOW_BOLTS_STEEL_TIPPED_REDTOPAZ, ItemID.XBOWS_CROSSBOW_BOLTS_MITHRIL_TIPPED_SAPPHIRE,
		ItemID.XBOWS_CROSSBOW_BOLTS_MITHRIL_TIPPED_EMERALD, ItemID.XBOWS_CROSSBOW_BOLTS_ADAMANTITE_TIPPED_RUBY,
		ItemID.XBOWS_CROSSBOW_BOLTS_ADAMANTITE_TIPPED_DIAMOND, ItemID.XBOWS_CROSSBOW_BOLTS_RUNITE_TIPPED_DRAGONSTONE,
		ItemID.XBOWS_CROSSBOW_BOLTS_RUNITE_TIPPED_ONYX, ItemID.XBOWS_CROSSBOW_BOLTS_BRONZE_TIPPED_OPAL_ENCHANTED,
		ItemID.XBOWS_CROSSBOW_BOLTS_BLURITE_TIPPED_JADE_ENCHANTED,
		ItemID.XBOWS_CROSSBOW_BOLTS_IRON_TIPPED_PEARL_ENCHANTED,
		ItemID.XBOWS_CROSSBOW_BOLTS_STEEL_TIPPED_REDTOPAZ_ENCHANTED,
		ItemID.XBOWS_CROSSBOW_BOLTS_MITHRIL_TIPPED_SAPPHIRE_ENCHANTED,
		ItemID.XBOWS_CROSSBOW_BOLTS_MITHRIL_TIPPED_EMERALD_ENCHANTED,
		ItemID.XBOWS_CROSSBOW_BOLTS_ADAMANTITE_TIPPED_RUBY_ENCHANTED,
		ItemID.XBOWS_CROSSBOW_BOLTS_ADAMANTITE_TIPPED_DIAMOND_ENCHANTED,
		ItemID.XBOWS_CROSSBOW_BOLTS_RUNITE_TIPPED_DRAGONSTONE_ENCHANTED,
		ItemID.XBOWS_CROSSBOW_BOLTS_RUNITE_TIPPED_ONYX_ENCHANTED, ItemID.XBOWS_GRAPPLE_TIP_BOLT_MITHRIL_ROPE,
		ItemID.BARBED_BOLT, ItemID.DTTD_BONE_CROSSBOW_BOLT, ItemID.SLAYER_BROAD_BOLT,
		ItemID.SLAYER_BROAD_BOLT_AMETHYST, ItemID.DRAGON_BOLTS, ItemID.DRAGON_BOLTS_P, ItemID.DRAGON_BOLTS_P_,
		ItemID.DRAGON_BOLTS_P__, ItemID.DRAGON_BOLTS_UNENCHANTED_OPAL, ItemID.DRAGON_BOLTS_UNENCHANTED_JADE,
		ItemID.DRAGON_BOLTS_UNENCHANTED_PEARL, ItemID.DRAGON_BOLTS_UNENCHANTED_TOPAZ,
		ItemID.DRAGON_BOLTS_UNENCHANTED_SAPPHIRE, ItemID.DRAGON_BOLTS_UNENCHANTED_EMERALD,
		ItemID.DRAGON_BOLTS_UNENCHANTED_RUBY, ItemID.DRAGON_BOLTS_UNENCHANTED_DIAMOND,
		ItemID.DRAGON_BOLTS_UNENCHANTED_DRAGONSTONE, ItemID.DRAGON_BOLTS_UNENCHANTED_ONYX,
		ItemID.DRAGON_BOLTS_ENCHANTED_OPAL, ItemID.DRAGON_BOLTS_ENCHANTED_JADE, ItemID.DRAGON_BOLTS_ENCHANTED_PEARL,
		ItemID.DRAGON_BOLTS_ENCHANTED_TOPAZ, ItemID.DRAGON_BOLTS_ENCHANTED_SAPPHIRE,
		ItemID.DRAGON_BOLTS_ENCHANTED_EMERALD, ItemID.DRAGON_BOLTS_ENCHANTED_RUBY,
		ItemID.DRAGON_BOLTS_ENCHANTED_DIAMOND, ItemID.DRAGON_BOLTS_ENCHANTED_DRAGONSTONE,
		ItemID.DRAGON_BOLTS_ENCHANTED_ONYX, ItemID.BARROWS_KARIL_AMMO};

	private static final int[] SCROLL_BITS = {
		VarbitID.BOOKOFSCROLLS_NARDAH, VarbitID.BOOKOFSCROLLS_DIGSITE, VarbitID.BOOKOFSCROLLS_FELDIP,
		VarbitID.BOOKOFSCROLLS_LUNARISLE, VarbitID.BOOKOFSCROLLS_MORTTON, VarbitID.BOOKOFSCROLLS_PESTCONTROL,
		VarbitID.BOOKOFSCROLLS_PISCATORIS, VarbitID.BOOKOFSCROLLS_TAIBWO, VarbitID.BOOKOFSCROLLS_ELF,
		VarbitID.BOOKOFSCROLLS_MOSLES, VarbitID.BOOKOFSCROLLS_LUMBERYARD, VarbitID.BOOKOFSCROLLS_ZULANDRA,
		VarbitID.BOOKOFSCROLLS_CERBERUS, VarbitID.BOOKOFSCROLLS_REVENANTS, VarbitID.BOOKOFSCROLLS_WATSON_LOWBITS,
		VarbitID.BOOKOFSCROLLS_GUTHIXIAN_TEMPLE, VarbitID.BOOKOFSCROLLS_SPIDERCAVE, VarbitID.BOOKOFSCROLLS_COLOSSAL_WYRM};
	private static final int[] SCROLLS = {
		ItemID.TELEPORTSCROLL_NARDAH, ItemID.TELEPORTSCROLL_DIGSITE, ItemID.TELEPORTSCROLL_FELDIP,
		ItemID.TELEPORTSCROLL_LUNARISLE, ItemID.TELEPORTSCROLL_MORTTON, ItemID.TELEPORTSCROLL_PESTCONTROL,
		ItemID.TELEPORTSCROLL_PISCATORIS, ItemID.TELEPORTSCROLL_TAIBWO, ItemID.TELEPORTSCROLL_ELF,
		ItemID.TELEPORTSCROLL_MOSLES, ItemID.TELEPORTSCROLL_LUMBERYARD, ItemID.TELEPORTSCROLL_ZULANDRA,
		ItemID.TELEPORTSCROLL_CERBERUS, ItemID.TELEPORTSCROLL_REVENANTS, ItemID.TELEPORTSCROLL_WATSON,
		ItemID.TELEPORTSCROLL_GUTHIXIAN_TEMPLE, ItemID.TELEPORTSCROLL_SPIDERCAVE, ItemID.TELEPORTSCROLL_COLOSSAL_WYRM};

	private static final int[] WATERING_CANS = {
		-1, ItemID.WATERING_CAN_0, ItemID.WATERING_CAN_1, ItemID.WATERING_CAN_2, ItemID.WATERING_CAN_3,
		ItemID.WATERING_CAN_4, ItemID.WATERING_CAN_5, ItemID.WATERING_CAN_6, ItemID.WATERING_CAN_7,
		ItemID.WATERING_CAN_8, ItemID.ZEAH_WATERINGCAN};

	private static final int[] FURNACE_BITS = {
		VarbitID.BLAST_FURNACE_COPPER_ORE, VarbitID.BLAST_FURNACE_TIN_ORE, VarbitID.BLAST_FURNACE_IRON_ORE,
		VarbitID.BLAST_FURNACE_COAL, VarbitID.BLAST_FURNACE_MITHRIL_ORE, VarbitID.BLAST_FURNACE_ADAMANTITE_ORE,
		VarbitID.BLAST_FURNACE_RUNITE_ORE, VarbitID.BLAST_FURNACE_SILVER_ORE, VarbitID.BLAST_FURNACE_GOLD_ORE,
		VarbitID.BLAST_FURNACE_BRONZE_BARS, VarbitID.BLAST_FURNACE_IRON_BARS, VarbitID.BLAST_FURNACE_STEEL_BARS,
		VarbitID.BLAST_FURNACE_MITHRIL_BARS, VarbitID.BLAST_FURNACE_ADAMANTITE_BARS, VarbitID.BLAST_FURNACE_RUNITE_BARS,
		VarbitID.BLAST_FURNACE_SILVER_BARS, VarbitID.BLAST_FURNACE_GOLD_BARS};
	private static final int[] FURNACE = {
		ItemID.COPPER_ORE, ItemID.TIN_ORE, ItemID.IRON_ORE, ItemID.COAL, ItemID.MITHRIL_ORE, ItemID.ADAMANTITE_ORE,
		ItemID.RUNITE_ORE, ItemID.SILVER_ORE, ItemID.GOLD_ORE, ItemID.BRONZE_BAR, ItemID.IRON_BAR, ItemID.STEEL_BAR,
		ItemID.MITHRIL_BAR, ItemID.ADAMANTITE_BAR, ItemID.RUNITE_BAR, ItemID.SILVER_BAR, ItemID.GOLD_BAR};

	private static final int[] FOSSIL_BITS = {
		VarbitID.FOSSIL_STORAGE_SMALL_UNID, VarbitID.FOSSIL_STORAGE_MEDIUM_UNID, VarbitID.FOSSIL_STORAGE_LARGE_UNID,
		VarbitID.FOSSIL_STORAGE_RARE_UNID, VarbitID.FOSSIL_STORAGE_SMALL_1, VarbitID.FOSSIL_STORAGE_SMALL_2,
		VarbitID.FOSSIL_STORAGE_SMALL_3, VarbitID.FOSSIL_STORAGE_SMALL_4, VarbitID.FOSSIL_STORAGE_SMALL_5,
		VarbitID.FOSSIL_STORAGE_MEDIUM_1, VarbitID.FOSSIL_STORAGE_MEDIUM_2, VarbitID.FOSSIL_STORAGE_MEDIUM_3,
		VarbitID.FOSSIL_STORAGE_MEDIUM_4, VarbitID.FOSSIL_STORAGE_MEDIUM_5, VarbitID.FOSSIL_STORAGE_LARGE_1,
		VarbitID.FOSSIL_STORAGE_LARGE_2, VarbitID.FOSSIL_STORAGE_LARGE_3, VarbitID.FOSSIL_STORAGE_LARGE_4,
		VarbitID.FOSSIL_STORAGE_LARGE_5, VarbitID.FOSSIL_STORAGE_PLANT_1, VarbitID.FOSSIL_STORAGE_PLANT_2,
		VarbitID.FOSSIL_STORAGE_PLANT_3, VarbitID.FOSSIL_STORAGE_PLANT_4, VarbitID.FOSSIL_STORAGE_PLANT_5,
		VarbitID.FOSSIL_STORAGE_RARE_1, VarbitID.FOSSIL_STORAGE_RARE_2, VarbitID.FOSSIL_STORAGE_RARE_3,
		VarbitID.FOSSIL_STORAGE_RARE_4, VarbitID.FOSSIL_STORAGE_RARE_5, VarbitID.FOSSIL_STORAGE_RARE_6};
	private static final int[] FOSSILS = {
		ItemID.FOSSIL_SMALL_UNID, ItemID.FOSSIL_MEDIUM_UNID, ItemID.FOSSIL_LARGE_UNID, ItemID.FOSSIL_RARE_UNID,
		ItemID.FOSSIL_SMALL_1, ItemID.FOSSIL_SMALL_2, ItemID.FOSSIL_SMALL_3, ItemID.FOSSIL_SMALL_4, ItemID.FOSSIL_SMALL_5,
		ItemID.FOSSIL_MEDIUM_1, ItemID.FOSSIL_MEDIUM_2, ItemID.FOSSIL_MEDIUM_3, ItemID.FOSSIL_MEDIUM_4, ItemID.FOSSIL_MEDIUM_5,
		ItemID.FOSSIL_LARGE_1, ItemID.FOSSIL_LARGE_2, ItemID.FOSSIL_LARGE_3, ItemID.FOSSIL_LARGE_4, ItemID.FOSSIL_LARGE_5,
		ItemID.FOSSIL_PLANT_1, ItemID.FOSSIL_PLANT_2, ItemID.FOSSIL_PLANT_3, ItemID.FOSSIL_PLANT_4, ItemID.FOSSIL_PLANT_5,
		ItemID.FOSSIL_RARE_1, ItemID.FOSSIL_RARE_2, ItemID.FOSSIL_RARE_3, ItemID.FOSSIL_RARE_4, ItemID.FOSSIL_RARE_5,
		ItemID.FOSSIL_RARE_6};

	/** The pickaxe statue's values, in the game's order; 0 is empty. */
	private static final int[] PICKAXES = {
		-1, ItemID.IRON_PICKAXE, ItemID.STEEL_PICKAXE, ItemID.BLACK_PICKAXE, ItemID.MITHRIL_PICKAXE,
		ItemID.ADAMANT_PICKAXE, ItemID.RUNE_PICKAXE, ItemID.TRAIL_GILDED_PICKAXE, ItemID.DRAGON_PICKAXE,
		ItemID.DRAGON_PICKAXE_PRETTY, ItemID.ZALCANO_PICKAXE, ItemID.TRAILBLAZER_PICKAXE_NO_INFERNAL,
		ItemID.INFERNAL_PICKAXE, ItemID.TRAILBLAZER_PICKAXE, ItemID.CRYSTAL_PICKAXE, ItemID.CRYSTAL_PICKAXE_INACTIVE,
		ItemID._3A_PICKAXE};

	private static final int[] LEPRECHAUN_BITS = {
		VarbitID.FARMING_TOOLS_RAKE, VarbitID.FARMING_TOOLS_EXTRARAKES, VarbitID.FARMING_TOOLS_DIBBER,
		VarbitID.FARMING_TOOLS_EXTRADIBBERS, VarbitID.FARMING_TOOLS_SPADE, VarbitID.FARMING_TOOLS_EXTRASPADES,
		VarbitID.FARMING_TOOLS_SECATEURS, VarbitID.FARMING_TOOLS_EXTRASECATEURS, VarbitID.FARMING_TOOLS_FAIRYSECATEURS,
		VarbitID.FARMING_TOOLS_WATERINGCAN, VarbitID.FARMING_TOOLS_TROWEL, VarbitID.FARMING_TOOLS_EXTRATROWELS,
		VarbitID.FARMING_TOOLS_PLANTCURE, VarbitID.FARMING_TOOLS_BUCKETS, VarbitID.FARMING_TOOLS_EXTRABUCKETS,
		VarbitID.FARMING_TOOLS_EXTRA2BUCKETS, VarbitID.FARMING_TOOLS_COMPOST, VarbitID.FARMING_TOOLS_EXTRACOMPOST,
		VarbitID.FARMING_TOOLS_SUPERCOMPOST, VarbitID.FARMING_TOOLS_EXTRASUPERCOMPOST, VarbitID.FARMING_TOOLS_ULTRACOMPOST,
		VarbitID.FARMING_TOOLS_BOTTOMLESS_BUCKET_TYPE, VarbitID.FARMING_TOOLS_BOTTOMLESS_BUCKET_QUANTITY};

	private static final int[] POUCH_COUNTS = {
		VarbitID.SMALL_ESSENCE_POUCH, VarbitID.MEDIUM_ESSENCE_POUCH, VarbitID.LARGE_ESSENCE_POUCH,
		VarbitID.GIANT_ESSENCE_POUCH, VarbitID.COLOSSAL_ESSENCE_POUCH};
	private static final int[] POUCH_TYPES = {
		VarbitID.SMALL_ESSENCE_POUCH_TYPE, VarbitID.MEDIUM_ESSENCE_POUCH_TYPE, VarbitID.LARGE_ESSENCE_POUCH_TYPE,
		VarbitID.GIANT_ESSENCE_POUCH_TYPE, VarbitID.COLOSSAL_ESSENCE_POUCH_TYPE};
	/** What an essence pouch's type value holds, as the game's own script 3197 reads it; 0 is empty. */
	static final int[] ESSENCES = {-1, ItemID.BLANKRUNE, ItemID.BLANKRUNE_HIGH, ItemID.BLANKRUNE_DAEYALT, ItemID.GOTR_GUARDIAN_ESSENCE};

	static final List<Read> ALL;
	private static final Map<Integer, List<Read>> BY_BIT = new HashMap<>();
	private static final Map<Integer, List<Read>> BY_PLAYER = new HashMap<>();

	static
	{
		int[] none = {};
		List<Read> all = new ArrayList<>();
		all.add(new Read("plank sack", "Plank sack", true, null, null, PLANK_BITS, none, (v, out) -> pairs(v, PLANK_BITS, PLANKS, 1, out)));
		all.add(new Read("bolt pouch", "Bolt pouch", true, null, null, concat(BOLT_SLOTS, BOLT_COUNTS), none, (v, out) ->
		{
			for (int i = 0; i < BOLT_SLOTS.length; i++)
			{
				int type = v.bit(BOLT_SLOTS[i]);
				int item = type > 0 && type < BOLTS.length ? BOLTS[type] : -1;
				if (item > 0)
				{
					out.merge(item, (long) v.bit(BOLT_COUNTS[i]), Long::sum);
				}
			}
		}));
		all.add(new Read("scroll book", "Master scroll book", true, null, null, SCROLL_BITS, none, (v, out) -> pairs(v, SCROLL_BITS, SCROLLS, 1, out)));
		all.add(new Read("spool", "Bow string spool", true, null, null, new int[]{VarbitID.BOWSTRING_SPOOL_CHARGES}, none,
			(v, out) -> put(out, ItemID.BOW_STRING, v.bit(VarbitID.BOWSTRING_SPOOL_CHARGES))));
		all.add(new Read("quiver", "Dizana's quiver", true, null, null, none,
			new int[]{VarPlayerID.DIZANAS_QUIVER_TEMP_AMMO, VarPlayerID.DIZANAS_QUIVER_TEMP_AMMO_AMOUNT},
			(v, out) -> put(out, v.player(VarPlayerID.DIZANAS_QUIVER_TEMP_AMMO), v.player(VarPlayerID.DIZANAS_QUIVER_TEMP_AMMO_AMOUNT))));

		all.add(new Read("essence", "Essence pouches", true, null, null, concat(POUCH_COUNTS, POUCH_TYPES), none, (v, out) ->
		{
			for (int i = 0; i < POUCH_COUNTS.length; i++)
			{
				int type = v.bit(POUCH_TYPES[i]);
				put(out, type > 0 && type < ESSENCES.length ? ESSENCES[type] : ItemID.BLANKRUNE_HIGH, v.bit(POUCH_COUNTS[i]));
			}
		}));

		all.add(new Read("kept:leprechaun", "Tool leprechaun", false, Kind.KEPT, null, LEPRECHAUN_BITS, none, Reads::leprechaun));
		all.add(new Read("kept:furnace", "Blast Furnace", false, Kind.KEPT, new int[]{1940, 4962, 0}, FURNACE_BITS, none,
			(v, out) -> pairs(v, FURNACE_BITS, FURNACE, 1, out)));
		all.add(new Read("kept:fossils", "Fossil storage", false, Kind.KEPT, new int[]{3741, 3803, 0}, FOSSIL_BITS, none,
			(v, out) -> pairs(v, FOSSIL_BITS, FOSSILS, 1, out)));
		all.add(new Read("kept:well", "Vyre well", false, Kind.KEPT, new int[]{3672, 3219, 0}, new int[]{VarbitID.TOB_LOBBY_WELL_CONTENTS}, none, (v, out) ->
		{
			int vials = v.bit(VarbitID.TOB_LOBBY_WELL_CONTENTS);
			put(out, ItemID.VIAL_BLOOD, vials);
			put(out, ItemID.BLOODRUNE, vials * 200);
		}));
		all.add(new Read("kept:elnock", "Elnock Inquisitor", false, Kind.KEPT, null,
			new int[]{VarbitID.II_STORED_NET, VarbitID.II_STORED_REPELLENT, VarbitID.II_STORED_IMPLING_JARS}, none, (v, out) ->
		{
			int net = v.bit(VarbitID.II_STORED_NET);
			put(out, net == 1 ? ItemID.HUNTING_BUTTERFLY_NET : ItemID.II_MAGIC_BUTTERFLY_NET, net == 0 ? 0 : 1);
			put(out, ItemID.II_IMP_REPELLENT, v.bit(VarbitID.II_STORED_REPELLENT));
			put(out, ItemID.II_IMPLING_JAR, v.bit(VarbitID.II_STORED_IMPLING_JARS));
		}));
		all.add(new Read("kept:annette", "Annette's drift nets", false, Kind.KEPT, null, new int[]{VarbitID.FOSSIL_DRIFTNET_STORE}, none,
			(v, out) -> put(out, ItemID.FOSSIL_DRIFT_NET, v.bit(VarbitID.FOSSIL_DRIFTNET_STORE))));
		all.add(new Read("kept:statue", "Pickaxe statue", false, Kind.KEPT, null, new int[]{VarbitID.TOA_PICKAXE_STORED}, none, (v, out) ->
		{
			int t = v.bit(VarbitID.TOA_PICKAXE_STORED);
			if (t > 0 && t < PICKAXES.length)
			{
				put(out, PICKAXES[t], 1);
			}
		}));

		all.add(coffer("coffer:nmz", "Nightmare Zone coffer", new int[]{2608, 3115, 0}, VarbitID.NZONE_CASH, 1000));
		all.add(coffer("coffer:furnace", "Blast Furnace coffer", new int[]{1940, 4962, 0}, VarbitID.BLAST_FURNACE_COFFER, 1));
		all.add(coffer("coffer:miscellania", "Miscellania coffer", new int[]{2501, 3859, 1}, VarbitID.MISC_COFFERS, 1));
		all.add(coffer("coffer:lms", "Last Man Standing coffer", new int[]{3138, 3635, 0}, VarbitID.BR_COFFER, 1000));
		all.add(new Read("coffer:scar", "Scar essence mine coffer", false, Kind.COFFER, null, none, new int[]{VarPlayerID.SCAR_ESSENCEMINE_COFFER},
			(v, out) -> put(out, ItemID.COINS, v.player(VarPlayerID.SCAR_ESSENCEMINE_COFFER))));
		ALL = Collections.unmodifiableList(all);
		for (Read r : ALL)
		{
			r.bits.forEach(b -> BY_BIT.computeIfAbsent(b, k -> new ArrayList<>()).add(r));
			r.players.forEach(p -> BY_PLAYER.computeIfAbsent(p, k -> new ArrayList<>()).add(r));
		}
	}

	private Reads()
	{
	}

	/** The reads a changed variable touches: a varbit id, or -1 and a varp id. */
	static List<Read> touched(int bit, int player)
	{
		List<Read> l = bit >= 0 ? BY_BIT.get(bit) : BY_PLAYER.get(player);
		return l == null ? Collections.emptyList() : l;
	}

	private static Read coffer(String key, String name, int[] at, int bit, int times)
	{
		return new Read(key, name, false, Kind.COFFER, at, new int[]{bit}, new int[]{},
			(v, out) -> put(out, ItemID.COINS, (long) v.bit(bit) * times));
	}

	private static void leprechaun(Vars v, Map<Integer, Long> out)
	{
		put(out, ItemID.RAKE, v.bit(VarbitID.FARMING_TOOLS_RAKE) + v.bit(VarbitID.FARMING_TOOLS_EXTRARAKES) * 2);
		put(out, ItemID.DIBBER, v.bit(VarbitID.FARMING_TOOLS_DIBBER) + v.bit(VarbitID.FARMING_TOOLS_EXTRADIBBERS) * 2);
		put(out, ItemID.SPADE, v.bit(VarbitID.FARMING_TOOLS_SPADE) + v.bit(VarbitID.FARMING_TOOLS_EXTRASPADES) * 2);
		put(out, v.bit(VarbitID.FARMING_TOOLS_FAIRYSECATEURS) == 1 ? ItemID.FAIRY_ENCHANTED_SECATEURS : ItemID.SECATEURS,
			v.bit(VarbitID.FARMING_TOOLS_SECATEURS) + v.bit(VarbitID.FARMING_TOOLS_EXTRASECATEURS) * 2);
		int can = v.bit(VarbitID.FARMING_TOOLS_WATERINGCAN);
		if (can > 0 && can < WATERING_CANS.length)
		{
			put(out, WATERING_CANS[can], 1);
		}
		put(out, ItemID.GARDENING_TROWEL, v.bit(VarbitID.FARMING_TOOLS_TROWEL) + v.bit(VarbitID.FARMING_TOOLS_EXTRATROWELS) * 2);
		put(out, ItemID.PLANT_CURE, v.bit(VarbitID.FARMING_TOOLS_PLANTCURE));
		put(out, ItemID.BUCKET_EMPTY, v.bit(VarbitID.FARMING_TOOLS_BUCKETS) + v.bit(VarbitID.FARMING_TOOLS_EXTRABUCKETS) * 32
			+ v.bit(VarbitID.FARMING_TOOLS_EXTRA2BUCKETS) * 256);
		put(out, ItemID.BUCKET_COMPOST, v.bit(VarbitID.FARMING_TOOLS_COMPOST) + v.bit(VarbitID.FARMING_TOOLS_EXTRACOMPOST) * 256);
		put(out, ItemID.BUCKET_SUPERCOMPOST, v.bit(VarbitID.FARMING_TOOLS_SUPERCOMPOST) + v.bit(VarbitID.FARMING_TOOLS_EXTRASUPERCOMPOST) * 256);
		put(out, ItemID.BUCKET_ULTRACOMPOST, v.bit(VarbitID.FARMING_TOOLS_ULTRACOMPOST));
		int bucket = v.bit(VarbitID.FARMING_TOOLS_BOTTOMLESS_BUCKET_TYPE);
		if (bucket > 0)
		{
			put(out, bucket == 1 ? ItemID.BOTTOMLESS_COMPOST_BUCKET : ItemID.BOTTOMLESS_COMPOST_BUCKET_FILLED, 1);
		}
	}

	private static void pairs(Vars v, int[] bits, int[] items, int times, Map<Integer, Long> out)
	{
		for (int i = 0; i < bits.length; i++)
		{
			put(out, items[i], (long) v.bit(bits[i]) * times);
		}
	}

	private static void put(Map<Integer, Long> out, int item, long qty)
	{
		if (item > 0 && qty > 0)
		{
			out.merge(item, qty, Long::sum);
		}
	}

	private static int[] concat(int[] a, int[] b)
	{
		int[] c = Arrays.copyOf(a, a.length + b.length);
		System.arraycopy(b, 0, c, a.length, b.length);
		return c;
	}
}
