package com.codex.nomad;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

@ConfigGroup(CodexNomadConfig.GROUP)
public interface CodexNomadConfig extends Config
{
	String GROUP = "codexnomad";

	@ConfigSection(
		name = "Guards",
		description = "Menu options Nomad takes away so a misclick cannot lose items for good",
		position = 20
	)
	String GUARDS = "guards";

	@ConfigSection(
		name = "In the game",
		description = "What Nomad draws over the game",
		position = 30
	)
	String SCENE = "scene";

	@ConfigItem(
		keyName = "idleHours",
		name = "Dead weight after",
		description = "Carried items untouched for this many hours of play are shown as dead weight you could leave somewhere",
		position = 0
	)
	@Range(min = 1, max = 200)
	@Units(" hours")
	default int idleHours()
	{
		return 3;
	}

	@ConfigItem(
		keyName = "pileWarning",
		name = "Deathpile warning",
		description = "Say in the chat box when a deathpile has this many minutes of logged-in time left",
		position = 1
	)
	@Range(min = 0, max = 59)
	@Units(" min")
	default int pileWarning()
	{
		return 10;
	}

	@ConfigItem(
		keyName = "shareHoldings",
		name = "Share with Codex",
		description = "Let the other Codex plugins count what you carry and keep as your bank, since an ultimate ironman has none",
		position = 2
	)
	default boolean shareHoldings()
	{
		return true;
	}

	@ConfigItem(
		keyName = "guardLootingBag",
		name = "Looting bag Destroy",
		description = "Take Destroy off the looting bag outside the Wilderness, where destroying it loses everything inside",
		section = GUARDS,
		position = 0
	)
	default boolean guardLootingBag()
	{
		return true;
	}

	@ConfigItem(
		keyName = "guardSell",
		name = "Shop Sell",
		description = "Take Sell off items worth more than the amount below, so valuable gear cannot be sold to a shop by mistake",
		section = GUARDS,
		position = 1
	)
	default boolean guardSell()
	{
		return true;
	}

	@ConfigItem(
		keyName = "sellAbove",
		name = "Protect items over",
		description = "Items worth more than this, at Grand Exchange or high alchemy value, cannot be sold to shops",
		section = GUARDS,
		position = 2
	)
	@Range(min = 0)
	@Units(" gp")
	default int sellAbove()
	{
		return 50_000;
	}

	@ConfigItem(
		keyName = "plaque",
		name = "Peril plaque",
		description = "Show a plaque with each deathpile's time left and the deathbank at risk while either exists",
		section = SCENE,
		position = 0
	)
	default boolean plaque()
	{
		return true;
	}

	@ConfigItem(
		keyName = "pointer",
		name = "Mark the next stop",
		description = "Mark the next place to call at on the ground and the world map: a deathpile, or a STASH unit holding what a packing list needs",
		section = SCENE,
		position = 1
	)
	default boolean pointer()
	{
		return true;
	}

	@ConfigItem(
		keyName = "routeStep",
		name = "Route step on plaque",
		description = "Show the next step of the route to Prifddinas on the plaque while you follow it",
		section = SCENE,
		position = 2
	)
	default boolean routeStep()
	{
		return true;
	}
}
