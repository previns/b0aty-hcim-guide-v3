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
import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Which steps count as a trip to a bank.
 *
 * <p>The flag comes from the verb the guide used, not from the word appearing
 * somewhere in the text. Getting that wrong in either direction is visible: too
 * loose and every step mentioning a bank lights up the booths, too tight and
 * the one step that needed it does not.
 */
public class BankingStepTest
{
	private final GuideLoader loader = new GuideLoader(new Gson());

	private static List<Step> allSteps(Guide guide)
	{
		final List<Step> steps = new ArrayList<>();
		for (Section section : guide.getSections())
		{
			steps.addAll(section.getSteps());
		}
		return steps;
	}

	@Test
	public void theGuideMarksItsBankingSteps() throws Exception
	{
		final List<Step> banking = new ArrayList<>();
		for (Step step : allSteps(loader.loadBundled()))
		{
			if (step.isBanking())
			{
				banking.add(step);
			}
		}
		assertTrue("expected plenty of banking steps", banking.size() > 100);
	}

	@Test
	public void everyBankingStepActuallySaysBank() throws Exception
	{
		// The reverse of the next test: the flag should never appear somewhere
		// the word does not.
		for (Step step : allSteps(loader.loadBundled()))
		{
			if (step.isBanking())
			{
				assertTrue("flagged but never says bank: " + step.getText(),
					step.getText().toLowerCase(Locale.ROOT).contains("bank"));
			}
		}
	}

	@Test
	public void mentioningABankIsNotTheSameAsGoingToOne() throws Exception
	{
		// "Keep the Shrimps in your bank for later" is not a trip to a bank.
		// A text search would flag it; the verb grammar does not.
		Step mention = null;
		for (Step step : allSteps(loader.loadBundled()))
		{
			if (step.getText().startsWith("Keep the Shrimps in your bank"))
			{
				mention = step;
			}
		}
		assertNotNull(mention);
		assertFalse(mention.isBanking());
	}

	@Test
	public void aBankAtStepIsFlagged() throws Exception
	{
		Step bankAt = null;
		for (Step step : allSteps(loader.loadBundled()))
		{
			if (step.getText().startsWith("Bank at Draynor and Deposit all"))
			{
				bankAt = step;
			}
		}
		assertNotNull(bankAt);
		assertTrue(bankAt.isBanking());
	}

	@Test
	public void mostStepsAreNotBankingSteps() throws Exception
	{
		final Guide guide = loader.loadBundled();
		int banking = 0;
		for (Step step : allSteps(guide))
		{
			if (step.isBanking())
			{
				banking++;
			}
		}
		assertTrue("a banking step should be the exception",
			banking < guide.getStepCount() / 10);
	}
}
