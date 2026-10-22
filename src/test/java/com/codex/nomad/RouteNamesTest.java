package com.codex.nomad;

import static org.junit.Assert.assertTrue;
import com.codex.nomad.core.Route;
import java.util.HashSet;
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
			for (String q : s.quests)
			{
				assertTrue("unknown quest " + q, quests.contains(q));
			}
			for (String k : s.skills.keySet())
			{
				assertTrue("unknown skill " + k, skills.contains(k));
			}
		}
	}
}
