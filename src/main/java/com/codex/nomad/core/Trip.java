package com.codex.nomad.core;

import java.util.ArrayList;
import java.util.List;

/** A packing list: what a trip needs carried, by item, saved from a setup the player wore once. */
public final class Trip
{
	public String name;
	public long made;
	public List<Want> wants = new ArrayList<>();

	public Trip()
	{
	}

	public Trip(String name, long made)
	{
		this.name = name;
		this.made = made;
	}

	/** One line of the list. */
	public static final class Want
	{
		/** The item in its common form: unnoted, no placeholder. */
		public int item;
		public String name;
		public long qty;
		/** Worn rather than carried, so it needs no inventory slot. */
		public boolean worn;

		public Want()
		{
		}

		public Want(int item, String name, long qty, boolean worn)
		{
			this.item = item;
			this.name = name;
			this.qty = qty;
			this.worn = worn;
		}
	}
}
