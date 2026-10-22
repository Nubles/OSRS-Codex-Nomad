package com.codex.nomad.core;

/** What one of an item is worth, in coins. */
public interface Prices
{
	long each(int item);

	Prices NONE = item -> 0;
}
