package com.codex.nomad.ui;

import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.util.List;

/**
 * One quiet line: an optional coloured dot, a label, and a figure to the right. A label too long
 * for the width wraps onto further rows rather than being cut short.
 */
public class Line extends Fitted
{
	private final String label;
	private final String figure;
	private final Color dot;
	private final Color labelColor;
	private final Color figureColor;

	public Line(String label, String figure, Color dot)
	{
		this(label, figure, dot, Theme.TEXT_MUTED, Theme.TEXT);
	}

	public Line(String label, String figure, Color dot, Color labelColor, Color figureColor)
	{
		this.label = label == null ? "" : label;
		this.figure = figure == null || figure.isEmpty() ? null : figure;
		this.dot = dot;
		this.labelColor = labelColor;
		this.figureColor = figureColor;
		setFont(Theme.small());
		setToolTipText(this.figure == null ? this.label : this.label + ": " + this.figure);
	}

	/** Leading spaces indent every row of the label, not just the first. */
	private int indent(FontMetrics fm)
	{
		int n = 0;
		while (n < label.length() && label.charAt(n) == ' ')
		{
			n++;
		}
		return (dot != null ? 11 : 0) + fm.stringWidth(label.substring(0, n));
	}

	/** The distance between rows: 13 pixels for the small font, more for a larger one. */
	private static int row(FontMetrics fm)
	{
		return Math.max(13, fm.getAscent() + 4);
	}

	private List<String> rows(FontMetrics fm, int width)
	{
		int fw = figure == null ? 0 : fm.stringWidth(figure) + 6;
		return Ui.wrap(label.trim(), fm, width - indent(fm) - fw);
	}

	@Override
	int heightFor(int width)
	{
		FontMetrics fm = getFontMetrics(getFont());
		int n = rows(fm, width).size();
		int one = Math.max(15, fm.getAscent() + 6);
		return n == 1 ? one : n * row(fm) + 2;
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		Graphics2D g = (Graphics2D) graphics.create();
		Theme.smooth(g);
		g.setFont(getFont());
		FontMetrics fm = g.getFontMetrics();
		int w = getWidth();
		List<String> rows = rows(fm, w);
		int base = rows.size() == 1 ? (getHeight() + fm.getAscent()) / 2 - 1 : fm.getAscent();
		if (dot != null)
		{
			g.setColor(dot);
			g.fill(new Ellipse2D.Float(1, base - fm.getAscent() / 2f - 2, 6, 6));
		}
		if (figure != null)
		{
			Ui.shadowed(g, figure, w - fm.stringWidth(figure), base, figureColor);
		}
		int x = indent(fm);
		for (int i = 0; i < rows.size(); i++)
		{
			Ui.shadowed(g, rows.get(i), x, base + i * row(fm), labelColor);
		}
		g.dispose();
	}
}
