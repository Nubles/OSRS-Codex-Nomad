package com.codex.nomad;

import static org.junit.Assert.assertTrue;
import com.codex.nomad.core.Route;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.runelite.api.Quest;
import net.runelite.api.Skill;
import org.junit.Test;

/** Every quest and skill the route checks is one the game knows by that exact name. */
public class RouteNamesTest
{
	@Test
	public void routeNamesMatchTheGame()
	{
		Set<String> quests = new HashSet<>();
		for (Quest q : Quest.values())
		{
			quests.add(q.getName());
		}
		Set<String> skills = new HashSet<>();
		for (Skill s : Skill.values())
		{
			skills.add(s.getName());
		}
		for (Route.Step s : Route.load().steps())
		{
			List<String> named = new ArrayList<>(s.quests);
			named.addAll(s.started);
			for (String q : named)
			{
				assertTrue("unknown quest " + q, quests.contains(q));
				assertTrue("no start point for " + q, Route.load().questStart(q) != null);
			}
			for (String k : s.skills.keySet())
			{
				assertTrue("unknown skill " + k, skills.contains(k));
			}
		}
	}
}
