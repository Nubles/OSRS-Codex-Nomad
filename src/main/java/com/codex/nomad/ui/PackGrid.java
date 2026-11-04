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

/**
 * The solved inventory as the game lays it out, four across and seven down: what the packing list
 * needs ringed in gold, the bags and pouches in teal, spare room taken by things with nowhere
 * safer to go in faint gold, and empty slots left dark.
 */
class PackGrid extends JComponent
{
	static final int COLUMNS = 4;
	static final int ROWS = 7;
	static final int CELL_W = Well.SIZE + 10;
	static final int CELL_H = Well.SIZE + 3;

	private final List<View.SlotView> slots;
	private final ItemIcons icons;

	PackGrid(List<View.SlotView> slots, ItemIcons icons)
	{
		this.slots = slots;
		this.icons = icons;
		setOpaque(false);
		Dimension d = new Dimension(COLUMNS * CELL_W, ROWS * CELL_H);
		setPreferredSize(d);
		setMinimumSize(d);
		setMaximumSize(d);
		setToolTipText("");
	}

	static Color rim(String role)
	{
		if ("LIST".equals(role))
		{
			return Theme.GOLD;
		}
		if ("CONTAINER".equals(role))
		{
			return Theme.TEAL;
		}
		return Theme.alpha(Theme.GOLD_DIM, 150);
	}

	@Override
	public String getToolTipText(MouseEvent e)
	{
		int col = e.getX() / CELL_W;
		int i = e.getY() / CELL_H * COLUMNS + col;
		if (col >= COLUMNS || i < 0 || i >= slots.size() || slots.get(i).item == null)
		{
			return null;
		}
		View.SlotView s = slots.get(i);
		String why = "LIST".equals(s.role) ? "On the list" : "CONTAINER".equals(s.role) ? "Holds other things" : "Not needed; nowhere safer";
		return "<html><b>" + Ui.escape(s.item.name) + "</b><br>" + why + "</html>";
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		Graphics2D g = (Graphics2D) graphics.create();
		Theme.smooth(g);
		g.setFont(Theme.small());
		FontMetrics fm = g.getFontMetrics();
		for (int i = 0; i < COLUMNS * ROWS; i++)
		{
			int x = i % COLUMNS * CELL_W + 2;
			int y = i / COLUMNS * CELL_H;
			View.SlotView s = i < slots.size() ? slots.get(i) : null;
			if (s == null || s.item == null)
			{
				Well.paint(g, x, y, null, Theme.alpha(Theme.STROKE_SOFT, 120));
				continue;
			}
			BufferedImage icon = icons == null ? null : icons.icon(s.item.id, this::repaint);
			Well.paint(g, x, y, icon, rim(s.role));
			if (s.item.qty > 1)
			{
				Ui.shadowed(g, Format.stack(s.item.qty), x + 3, y + fm.getAscent() + 1, Format.stackColor(s.item.qty));
			}
		}
		g.dispose();
	}
}
