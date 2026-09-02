/*
 * Copyright (c) 2026, Previn <https://github.com/previns>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.b0atyguide;

import com.b0atyguide.data.Approach;
import com.b0atyguide.data.Guide;
import com.b0atyguide.data.GuideLoader;
import com.b0atyguide.data.Section;
import com.b0atyguide.data.Step;
import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Which staircase a step means.
 *
 * <p>The runtime fallback picks the nearest climbable object, which is wrong in
 * Lumbridge castle -- several staircases and ladders sit within a few tiles.
 * These pin the curated answer that is meant to beat it.
 */
public class ApproachTest
{
	private final GuideLoader loader = new GuideLoader(new Gson());

	private static List<Step> withApproach(Guide guide)
	{
		final List<Step> found = new ArrayList<>();
		for (Section section : guide.getSections())
		{
			for (Step step : section.getSteps())
			{
				if (step.getApproach() != null)
				{
					found.add(step);
				}
			}
		}
		return found;
	}

	@Test
	public void theDukeStepPointsAtTheCastleSpiralStairs() throws Exception
	{
		Step duke = null;
		for (Step step : withApproach(loader.loadBundled()))
		{
			if (step.getText().startsWith("Go upstairs and Talk to Duke Horacio"))
			{
				duke = step;
			}
		}
		assertNotNull("the Duke step should name its staircase", duke);

		final Approach approach = duke.getApproach();
		assertEquals("SPIRALSTAIRS", approach.getConstant());
		// Lumbridge castle's ground-floor spiral staircase.
		assertEquals(java.util.Arrays.asList(3205, 3208, 0), approach.getPoint());
		assertFalse(approach.getIds().isEmpty());
	}

	@Test
	public void everyApproachCarriesAnIdAndAPoint() throws Exception
	{
		final List<Step> steps = withApproach(loader.loadBundled());
		assertFalse("the guide should carry some approaches", steps.isEmpty());
		for (Step step : steps)
		{
			final Approach approach = step.getApproach();
			assertFalse(step.getText(), approach.getIds().isEmpty());
			assertEquals(step.getText(), 3, approach.getPoint().size());
			for (Integer id : approach.getIds())
			{
				assertTrue("object id should be real: " + id, id > 0);
			}
		}
	}

	@Test
	public void anApproachIsOnADifferentFloorFromItsTarget() throws Exception
	{
		// The whole point: it exists to bridge a plane change. One on the same
		// plane as the target would mean the geometry picked something useless.
		for (Step step : withApproach(loader.loadBundled()))
		{
			final List<List<Integer>> points = step.getTarget().getPoints();
			assertFalse(points.isEmpty());
			assertTrue(step.getText(),
				points.get(0).get(2) != step.getApproach().getPoint().get(2));
		}
	}

	@Test
	public void anApproachOnlyAppearsOnStepsTargetingAnUpperFloor() throws Exception
	{
		for (Step step : withApproach(loader.loadBundled()))
		{
			assertTrue(step.getText(), step.getTarget().getPoints().get(0).get(2) > 0);
		}
	}

	@Test
	public void mostStepsHaveNoApproach() throws Exception
	{
		final Guide guide = loader.loadBundled();
		assertTrue("an approach is a rare, specific thing",
			withApproach(guide).size() < guide.getStepCount() / 10);
	}
}
