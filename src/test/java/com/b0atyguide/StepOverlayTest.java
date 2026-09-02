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

import com.b0atyguide.data.Guide;
import com.b0atyguide.data.GuideLoader;
import com.b0atyguide.data.Section;
import com.b0atyguide.data.Step;
import com.b0atyguide.overlay.SceneTracker;
import com.google.gson.Gson;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

/**
 * The step overlay has to render on every step, including the great majority
 * that have nothing to outline. These cover the tracker state it reads; the
 * drawing itself needs a client and is checked by hand.
 */
public class StepOverlayTest
{
	private final GuideLoader loader = new GuideLoader(new Gson());

	private static Step firstStepWithoutTarget(Guide guide)
	{
		for (Section section : guide.getSections())
		{
			for (Step step : section.getSteps())
			{
				if (step.getTarget() == null)
				{
					return step;
				}
			}
		}
		throw new AssertionError("guide has no target-less step");
	}

	/**
	 * The bug this covers: setStep() used to null the step whenever it had
	 * nothing highlightable, so the overlay went blank on roughly half the
	 * guide -- including "Starting Out", the very first thing a player sees.
	 */
	@Test
	public void aStepWithNothingToOutlineIsStillTheCurrentStep() throws Exception
	{
		final Guide guide = loader.loadBundled();
		final Step step = firstStepWithoutTarget(guide);

		final SceneTracker tracker = new SceneTracker();
		tracker.setStep(step, "Bank 1");

		assertSame("the overlay needs the step even with no target", step, tracker.getStep());
		assertEquals("Bank 1", tracker.getSectionLabel());
		assertFalse("nothing to match means nothing to draw", tracker.isTracking());
		assertNotNull(tracker.getNpcs());
		assertNotNull(tracker.getObjects());
	}

	@Test
	public void clearingForgetsTheStep() throws Exception
	{
		final Guide guide = loader.loadBundled();
		final SceneTracker tracker = new SceneTracker();
		tracker.setStep(firstStepWithoutTarget(guide), "Bank 1");
		tracker.clear();

		assertNull(tracker.getStep());
		assertNull(tracker.getSectionLabel());
		assertFalse(tracker.isTracking());
	}

	@Test
	public void aNullStepClearsRatherThanThrowing()
	{
		final SceneTracker tracker = new SceneTracker();
		tracker.setStep(null, null);
		assertNull(tracker.getStep());
		assertFalse(tracker.isTracking());
	}
}
