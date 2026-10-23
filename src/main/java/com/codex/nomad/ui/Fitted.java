package com.codex.nomad.ui;

import java.awt.Dimension;
import javax.swing.JComponent;

/**
 * A painted component whose height follows its width, so text can wrap instead of being cut.
 * Before it has a width it guesses one; once laid out at a different width it asks for another
 * layout at the height that width needs.
 */
abstract class Fitted extends JComponent
{
	/** The width guessed before the first layout: a card's inside in the side panel. */
	static final int GUESS = NomadPanel.WIDTH - 26;

	Fitted()
	{
		setOpaque(false);
	}

	/** The height the component needs at this width. */
	abstract int heightFor(int width);

	@Override
	public Dimension getPreferredSize()
	{
		if (isPreferredSizeSet())
		{
			return super.getPreferredSize();
		}
		int w = getWidth() > 0 ? getWidth() : GUESS;
		return new Dimension(10, heightFor(w));
	}

	@Override
	@SuppressWarnings("deprecation")
	public void reshape(int x, int y, int w, int h)
	{
		int was = getWidth();
		super.reshape(x, y, w, h);
		if (w > 0 && w != was && heightFor(w) != h)
		{
			revalidate();
		}
	}
}
