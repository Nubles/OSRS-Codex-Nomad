package com.codex.nomad.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import javax.swing.JComponent;
import javax.swing.border.EmptyBorder;

/**
 * One painted row on a card: an item in its well (or none), a name, a figure on the right in its
 * colour, and a quieter line beneath.
 */
class Row extends Card
{
	static final int HEIGHT = 38;

	Row(int icon, long qty, ItemIcons icons, String name, String figure, Color figureColor, String note, Color accent)
	{
		super(new BorderLayout());
		setBorder(new EmptyBorder(3, 6, 3, 8));
		if (accent != null)
		{
			accent(accent);
		}
		add(new Body(icon, qty, icons, name, figure, figureColor, note), BorderLayout.CENTER);
		setToolTipText("<html><b>" + Ui.escape(name) + "</b>" + (figure == null ? "" : " " + Ui.escape(figure))
			+ (note == null ? "" : "<br>" + Ui.escape(note)) + "</html>");
	}

	private static final class Body extends JComponent
	{
		private final int icon;
		private final long qty;
		private final ItemIcons icons;
		private final String name;
		private final String figure;
		private final Color figureColor;
		private final String note;

		Body(int icon, long qty, ItemIcons icons, String name, String figure, Color figureColor, String note)
		{
			this.icon = icon;
			this.qty = qty;
			this.icons = icons;
			this.name = name;
			this.figure = figure;
			this.figureColor = figureColor;
			this.note = note;
			setOpaque(false);
			setPreferredSize(new Dimension(10, icon >= 0 ? Row.HEIGHT - 4 : note == null ? 18 : 32));
		}

		@Override
		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D) graphics.create();
			Theme.smooth(g);
			int w = getWidth();
			int x = 0;
			if (icon >= 0)
			{
				BufferedImage img = icons == null ? null : icons.icon(icon, this::repaint);
				Well.paint(g, 0, 1, img, null);
				if (qty > 1)
				{
					g.setFont(Theme.small());
					Ui.shadowed(g, Format.stack(qty), 2, 10, Format.stackColor(qty));
				}
				x = Well.SIZE + 10;
			}
			g.setFont(Theme.title());
			FontMetrics bold = g.getFontMetrics();
			int fw = figure == null ? 0 : bold.stringWidth(figure);
			if (figure != null)
			{
				Ui.shadowed(g, figure, w - fw, 14, figureColor);
			}
			g.setFont(Theme.body());
			FontMetrics body = g.getFontMetrics();
			Ui.shadowed(g, Ui.clip(name, body, w - x - fw - 6), x, 14, Theme.TEXT);
			if (note != null)
			{
				g.setFont(Theme.small());
				FontMetrics small = g.getFontMetrics();
				Ui.shadowed(g, Ui.clip(note, small, w - x), x, 29, Theme.TEXT_MUTED);
			}
			g.dispose();
		}
	}

	/** A title and figure with an optional quiet line, for use inside another card. */
	static final class Plain extends JComponent
	{
		private final String name;
		private final String figure;
		private final Color figureColor;
		private final String note;

		Plain(String name, String figure, Color figureColor, String note)
		{
			this.name = name;
			this.figure = figure == null || figure.isEmpty() ? null : figure;
			this.figureColor = figureColor;
			this.note = note == null || note.isEmpty() ? null : note;
			setOpaque(false);
			setPreferredSize(new Dimension(10, this.note == null ? 16 : 30));
			setToolTipText(this.note == null ? name : "<html><b>" + Ui.escape(name) + "</b><br>" + Ui.escape(this.note) + "</html>");
		}

		@Override
		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D) graphics.create();
			Theme.smooth(g);
			int w = getWidth();
			g.setFont(Theme.title());
			FontMetrics bold = g.getFontMetrics();
			int fw = figure == null ? 0 : bold.stringWidth(figure);
			if (figure != null)
			{
				Ui.shadowed(g, figure, w - fw, 13, figureColor);
			}
			g.setFont(Theme.body());
			Ui.shadowed(g, Ui.clip(name, g.getFontMetrics(), w - fw - 6), 0, 13, Theme.TEXT);
			if (note != null)
			{
				g.setFont(Theme.small());
				Ui.shadowed(g, Ui.clip(note, g.getFontMetrics(), w), 0, 27, Theme.TEXT_MUTED);
			}
			g.dispose();
		}
	}
}
