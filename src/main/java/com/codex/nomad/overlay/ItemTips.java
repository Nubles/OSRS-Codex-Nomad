package com.codex.nomad.overlay;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.IntUnaryOperator;
import java.util.function.Supplier;
import net.runelite.api.Client;
import net.runelite.api.MenuEntry;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.tooltip.Tooltip;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;

/**
 * A tooltip on an inventory item: every other place the account keeps more of it, so a UIM sees at
 * a glance what is lying in a deathpile, a STASH unit or the costume room.
 */
public class ItemTips extends Overlay
{
	private static final String GOLD = "<col=d6b26a>";
	private static final String END = "</col>";
	private static final int MOST = 4;

	private final Client client;
	private final TooltipManager tooltips;
	private final IntUnaryOperator canon;
	private final Supplier<Boolean> on;
	private volatile Map<Integer, List<String>> where = Collections.emptyMap();

	public ItemTips(Client client, TooltipManager tooltips, IntUnaryOperator canon, Supplier<Boolean> on)
	{
		this.client = client;
		this.tooltips = tooltips;
		this.canon = canon;
		this.on = on;
	}

	/** Every place each item is kept, by its common id: "Deathpile 14", "STASH, Draynor". */
	public void show(Map<Integer, List<String>> where)
	{
		this.where = where == null ? Collections.emptyMap() : where;
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!on.get() || client.isMenuOpen())
		{
			return null;
		}
		MenuEntry[] menu = client.getMenuEntries();
		if (menu.length == 0)
		{
			return null;
		}
		Widget widget = menu[menu.length - 1].getWidget();
		if (widget == null || widget.getId() != InterfaceID.Inventory.ITEMS || widget.getItemId() <= 0)
		{
			return null;
		}
		List<String> places = where.get(canon.applyAsInt(widget.getItemId()));
		if (places == null || places.isEmpty())
		{
			return null;
		}
		StringBuilder text = new StringBuilder(GOLD + "Also kept" + END);
		for (int i = 0; i < places.size() && i < MOST; i++)
		{
			text.append("</br>").append(places.get(i));
		}
		if (places.size() > MOST)
		{
			text.append("</br>and ").append(places.size() - MOST).append(" more");
		}
		tooltips.add(new Tooltip(text.toString()));
		return null;
	}
}
