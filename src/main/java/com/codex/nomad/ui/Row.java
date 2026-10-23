package com.codex.nomad.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.List;
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

	private static final class Body extends Fitted
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
		}

		@Override
		int heightFor(int width)
		{
			int h = lay(null, width, icon >= 0 ? Well.SIZE + 10 : 0, 14, 15);
			return icon >= 0 ? Math.max(Row.HEIGHT - 4, h) : h;
		}

		@Override
		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D) graphics.create();
			Theme.smooth(g);
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
			lay(g, getWidth(), x, 14, 15);
			g.dispose();
		}

		private int lay(Graphics2D g, int w, int x, int top, int gap)
		{
			return Row.lay(this, g, w, x, top, gap, name, figure, figureColor, note);
		}
	}

	/**
	 * Paints a name with its figure and a quieter note beneath, both wrapping to the width, or
	 * with no graphics only measures them.
	 *
	 * @return the height used
	 */
	static int lay(JComponent c, Graphics2D g, int w, int x, int top, int gap, String name, String figure, Color figureColor, String note)
	{
		FontMetrics bold = c.getFontMetrics(Theme.title());
		FontMetrics body = c.getFontMetrics(Theme.body());
		FontMetrics small = c.getFontMetrics(Theme.small());
		int fw = figure == null ? 0 : bold.stringWidth(figure);
		List<String> names = Ui.wrap(name, body, w - x - fw - 6);
		List<String> notes = note == null ? Collections.emptyList() : Ui.wrap(note, small, w - x);
		int noteTop = top + (names.size() - 1) * 13 + gap;
		int bottom = notes.isEmpty() ? top + (names.size() - 1) * 13 + 4 : noteTop + (notes.size() - 1) * 11 + 4;
		if (g != null)
		{
			if (figure != null)
			{
				g.setFont(Theme.title());
				Ui.shadowed(g, figure, w - fw, top, figureColor);
			}
			g.setFont(Theme.body());
			for (int i = 0; i < names.size(); i++)
			{
				Ui.shadowed(g, names.get(i), x, top + i * 13, Theme.TEXT);
			}
			g.setFont(Theme.small());
			for (int i = 0; i < notes.size(); i++)
			{
				Ui.shadowed(g, notes.get(i), x, noteTop + i * 11, Theme.TEXT_MUTED);
			}
		}
		return bottom;
	}

	/** A title and figure with an optional quiet line, for use inside another card. */
	static final class Plain extends Fitted
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
			setToolTipText(this.note == null ? name : "<html><b>" + Ui.escape(name) + "</b><br>" + Ui.escape(this.note) + "</html>");
		}

		@Override
		int heightFor(int width)
		{
			return Row.lay(this, null, width, 0, 13, 14, name, figure, figureColor, note);
		}

		@Override
		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D) graphics.create();
			Theme.smooth(g);
			Row.lay(this, g, getWidth(), 0, 13, 14, name, figure, figureColor, note);
			g.dispose();
		}
	}
}
