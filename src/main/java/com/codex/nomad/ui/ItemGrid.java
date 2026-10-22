package com.codex.nomad.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.List;
import javax.swing.JComponent;

/** Items laid out in slots like the game's own inventory, each with its stack in the game's colours. */
class ItemGrid extends JComponent
{
	static final int CELL_W = Well.SIZE + 6;
	static final int CELL_H = Well.SIZE + 3;

	private final List<View.ItemLine> items;
	private final ItemIcons icons;
	private final Color rim;
	private final int columns;

	ItemGrid(List<View.ItemLine> items, ItemIcons icons, Color rim, int columns)
	{
		this.items = items;
		this.icons = icons;
		this.rim = rim;
		this.columns = columns;
		setOpaque(false);
		int rows = Math.max(1, (items.size() + columns - 1) / columns);
		setPreferredSize(new Dimension(columns * CELL_W, rows * CELL_H));
		setToolTipText("");
	}

	@Override
	public String getToolTipText(MouseEvent e)
	{
		int i = e.getY() / CELL_H * columns + e.getX() / CELL_W;
		if (e.getX() / CELL_W >= columns || i < 0 || i >= items.size())
		{
			return null;
		}
		View.ItemLine l = items.get(i);
		return "<html><b>" + Ui.escape(l.name) + (l.noted ? " (noted)" : "") + "</b><br>" + Format.amount(l.qty)
			+ (l.worth > 0 ? " · " + Format.coins(l.worth) + " gp" : "") + "</html>";
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		Graphics2D g = (Graphics2D) graphics.create();
		Theme.smooth(g);
		g.setFont(Theme.small());
		FontMetrics fm = g.getFontMetrics();
		for (int i = 0; i < items.size(); i++)
		{
			View.ItemLine l = items.get(i);
			int x = i % columns * CELL_W;
			int y = i / columns * CELL_H;
			BufferedImage icon = icons == null ? null : icons.icon(l.id, this::repaint);
			Well.paint(g, x, y, icon, rim);
			if (l.qty > 1)
			{
				String stack = Format.stack(l.qty);
				Ui.shadowed(g, stack, x + 3, y + fm.getAscent() + 1, Format.stackColor(l.qty));
			}
		}
		g.dispose();
	}
}
