package com.codex.nomad.overlay;

import com.codex.nomad.ui.Sigil;
import com.codex.nomad.ui.Theme;
import com.codex.nomad.ui.Ui;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.geom.RoundRectangle2D;
import java.util.Collections;
import java.util.List;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * The peril plaque: a gilt card in the game view with each deathpile's time left, the deathbank at
 * risk, the next place to call at and the route step in hand. It draws what the plugin hands it.
 */
public class Plaque extends Overlay
{
	private static final int PAD = 7;
	private static final int ICON = 16;
	private static final int GAP = 12;

	private volatile Shown shown;

	/** One line of the plaque. */
	public static final class Row
	{
		final String name;
		final String figure;
		final String note;
		final Color color;

		/**
		 * @param name what it is, such as "Deathpile"
		 * @param figure the figure on the right, such as "41:12"
		 * @param note a second, quieter line, or null
		 */
		public Row(String name, String figure, String note, Color color)
		{
			this.name = name;
			this.figure = figure == null ? "" : figure;
			this.note = note;
			this.color = color;
		}
	}

	private static final class Shown
	{
		final String title;
		final Color tint;
		final List<Row> rows;

		Shown(String title, Color tint, List<Row> rows)
		{
			this.title = title;
			this.tint = tint;
			this.rows = rows;
		}
	}

	public Plaque()
	{
		setPosition(OverlayPosition.TOP_LEFT);
	}

	/** @param rows what to show, or null or empty to hide the plaque */
	public void show(String title, Color tint, List<Row> rows)
	{
		shown = rows == null || rows.isEmpty() ? null : new Shown(title, tint, Collections.unmodifiableList(rows));
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		Shown s = shown;
		if (s == null)
		{
			return null;
		}
		Theme.smooth(g);
		g.setFont(Theme.title());
		FontMetrics bold = g.getFontMetrics();
		g.setFont(Theme.small());
		FontMetrics small = g.getFontMetrics();

		int nameW = 0;
		int figureW = 0;
		int noteW = 0;
		for (Row r : s.rows)
		{
			nameW = Math.max(nameW, small.stringWidth(r.name));
			figureW = Math.max(figureW, small.stringWidth(r.figure));
			noteW = Math.max(noteW, r.note == null ? 0 : small.stringWidth(r.note));
		}
		int textW = Math.min(260, Math.max(bold.stringWidth(s.title), Math.max(nameW + GAP + figureW, noteW)));
		int width = PAD + ICON + 7 + textW + PAD;
		int height = PAD + bold.getHeight() + 2;
		for (Row r : s.rows)
		{
			height += small.getHeight() + 3 + small.getHeight() * lines(r.note, small, textW).size();
		}
		height += PAD - 2;

		RoundRectangle2D shape = new RoundRectangle2D.Float(0.5f, 0.5f, width - 1, height - 1, 10, 10);
		g.setPaint(new GradientPaint(0, 0, Theme.alpha(Theme.CARD_TOP, 230), 0, height, Theme.alpha(Theme.INK_DEEP, 230)));
		g.fill(shape);
		g.setColor(Theme.alpha(s.tint, 170));
		g.draw(shape);

		Sigil.paint(g, PAD, PAD, ICON);
		int x = PAD + ICON + 7;
		int y = PAD + bold.getAscent() - 2;
		g.setFont(Theme.title());
		Ui.shadowed(g, s.title, x, y, Theme.GOLD_BRIGHT);
		g.setFont(Theme.small());
		y += 2;
		for (Row r : s.rows)
		{
			g.setColor(Theme.alpha(Theme.GOLD, 50));
			g.drawLine(x, y + 2, x + textW, y + 2);
			y += small.getHeight() + 2;
			int fw = small.stringWidth(r.figure);
			Ui.shadowed(g, Ui.clip(r.name, small, textW - fw - 6), x, y, Theme.TEXT);
			Ui.shadowed(g, r.figure, x + textW - fw, y, r.color);
			for (String line : lines(r.note, small, textW))
			{
				y += small.getHeight();
				Ui.shadowed(g, line, x, y, Theme.TEXT_MUTED);
			}
			y += 1;
		}
		return new Dimension(width, height);
	}

	/** A note broken into up to three lines that fit, the last clipped. */
	private static List<String> lines(String note, FontMetrics fm, int width)
	{
		List<String> out = new java.util.ArrayList<>();
		if (note == null)
		{
			return out;
		}
		StringBuilder line = new StringBuilder();
		String[] words = note.split(" ");
		for (int i = 0; i < words.length; i++)
		{
			String next = line.length() == 0 ? words[i] : line + " " + words[i];
			if (fm.stringWidth(next) <= width || line.length() == 0)
			{
				line = new StringBuilder(next);
				continue;
			}
			if (out.size() == 2)
			{
				StringBuilder rest = new StringBuilder(line);
				for (int j = i; j < words.length; j++)
				{
					rest.append(' ').append(words[j]);
				}
				out.add(Ui.clip(rest.toString(), fm, width));
				return out;
			}
			out.add(line.toString());
			line = new StringBuilder(words[i]);
		}
		out.add(Ui.clip(line.toString(), fm, width));
		return out;
	}
}
