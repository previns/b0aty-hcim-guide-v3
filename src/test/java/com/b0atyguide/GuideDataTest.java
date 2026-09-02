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
import com.b0atyguide.data.Target;
import com.b0atyguide.progress.Progress;
import com.google.gson.Gson;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Everything here runs without a game client. The loader and the progress
 * migration are the two pieces that must be right before anything is drawn on
 * screen, so they are covered here rather than by hand in the dev client.
 */
public class GuideDataTest
{
	private final GuideLoader loader = new GuideLoader(new Gson());

	private Guide parse(String json) throws Exception
	{
		return loader.load(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
	}

	private static String minimalGuide()
	{
		return "{\"schemaVersion\":1,\"sections\":[{\"id\":\"aaaaaaaaaa\",\"slug\":\"bank-1\","
			+ "\"ordinal\":0,\"title\":\"Bank 1\",\"bankNumber\":1,\"imageUrls\":[],"
			+ "\"steps\":[{\"id\":\"1111111111\",\"ordinal\":0,\"depth\":1,\"kind\":\"step\","
			+ "\"text\":\"Talk to Bob\"}]}],\"episodes\":[],\"migrations\":{}}";
	}

	// --- the shipped file --------------------------------------------------

	@Test
	public void bundledGuideLoads() throws Exception
	{
		Guide guide = loader.loadBundled();
		assertEquals(1, guide.getSchemaVersion());
		assertFalse(guide.getSections().isEmpty());
		assertTrue("expected the full guide, got " + guide.getStepCount() + " steps",
			guide.getStepCount() > 2000);
		assertNotNull(guide.getContentHash());
	}

	@Test
	public void bundledGuideHasUsableTargets() throws Exception
	{
		Guide guide = loader.loadBundled();
		int wikiBacked = 0;
		for (Section section : guide.getSections())
		{
			for (Step step : section.getSteps())
			{
				Target target = step.getTarget();
				if (target == null)
				{
					continue;
				}
				assertNotNull(target.getName());
				assertNotNull(target.getConfidence());
				if (target.isWikiBacked())
				{
					wikiBacked++;
				}
				else
				{
					// An unconfirmed name must never arrive carrying IDs, or the
					// plugin would present a guess as fact.
					assertTrue(target.getIds().isEmpty());
					assertTrue(target.getPoints().isEmpty());
				}
			}
		}
		assertTrue("expected wiki-backed targets, found " + wikiBacked, wikiBacked > 500);
	}

	@Test
	public void everyStepBelongsToExactlyOneSection() throws Exception
	{
		Guide guide = loader.loadBundled();
		Set<String> seen = new HashSet<>();
		for (Section section : guide.getSections())
		{
			for (Step step : section.getSteps())
			{
				assertTrue("duplicate step id " + step.getId(), seen.add(step.getId()));
			}
		}
	}

	// --- loader rejection --------------------------------------------------

	@Test
	public void rejectsFutureSchemaVersion()
	{
		try
		{
			parse("{\"schemaVersion\":99,\"sections\":[]}");
			fail("expected a GuideLoadException");
		}
		catch (Exception e)
		{
			assertTrue(e.getMessage().contains("schema version"));
		}
	}

	@Test
	public void rejectsEmptySections()
	{
		try
		{
			parse("{\"schemaVersion\":1,\"sections\":[]}");
			fail("expected a GuideLoadException");
		}
		catch (Exception e)
		{
			assertTrue(e.getMessage().contains("no sections"));
		}
	}

	@Test
	public void rejectsDuplicateStepIds()
	{
		String json = "{\"schemaVersion\":1,\"sections\":[{\"id\":\"a\",\"slug\":\"s\","
			+ "\"steps\":[{\"id\":\"dup\",\"text\":\"one\"},{\"id\":\"dup\",\"text\":\"two\"}]}]}";
		try
		{
			parse(json);
			fail("expected a GuideLoadException");
		}
		catch (Exception e)
		{
			assertTrue(e.getMessage().contains("duplicate step id"));
		}
	}

	@Test
	public void rejectsMalformedJson()
	{
		try
		{
			parse("{not json at all");
			fail("expected a GuideLoadException");
		}
		catch (Exception e)
		{
			assertTrue(e.getMessage().contains("valid JSON"));
		}
	}

	@Test
	public void missingOptionalFieldsBecomeEmptyNotNull() throws Exception
	{
		Guide guide = parse(minimalGuide());
		Step step = guide.getSections().get(0).getSteps().get(0);
		assertTrue(step.getItems().isEmpty());
		assertTrue(step.getTags().isEmpty());
		assertTrue(step.getDialogue().isEmpty());
		assertTrue(step.getVideoIds().isEmpty());
		assertNull(step.getTarget());
		assertTrue(guide.getPreamble().isEmpty());
	}

	// --- progress migration ------------------------------------------------

	@Test
	public void migrationRewritesASavedId()
	{
		Progress progress = new Progress(new HashSet<>(Arrays.asList("old1", "keep")), new HashSet<>());
		Map<String, String> migrations = new HashMap<>();
		migrations.put("old1", "new1");

		assertEquals(1, progress.applyMigrations(migrations));
		assertTrue(progress.isComplete("new1"));
		assertFalse(progress.isComplete("old1"));
		assertTrue("unrelated progress must survive", progress.isComplete("keep"));
	}

	@Test
	public void migrationFollowsAChainAcrossReleases()
	{
		Progress progress = new Progress(new HashSet<>(Arrays.asList("v1")), new HashSet<>());
		Map<String, String> migrations = new HashMap<>();
		migrations.put("v1", "v2");
		migrations.put("v2", "v3");

		progress.applyMigrations(migrations);
		assertTrue("a player skipping releases must reach the current id",
			progress.isComplete("v3"));
		assertEquals(1, progress.count());
	}

	@Test
	public void migrationCycleDoesNotHang()
	{
		Progress progress = new Progress(new HashSet<>(Arrays.asList("a")), new HashSet<>());
		Map<String, String> migrations = new HashMap<>();
		migrations.put("a", "b");
		migrations.put("b", "a");

		progress.applyMigrations(migrations);
		assertEquals(1, progress.count());
	}

	@Test
	public void migrationsCollapsingOntoOneStepDoNotDoubleCount()
	{
		Progress progress = new Progress(new HashSet<>(Arrays.asList("a", "b")), new HashSet<>());
		Map<String, String> migrations = new HashMap<>();
		migrations.put("a", "merged");
		migrations.put("b", "merged");

		progress.applyMigrations(migrations);
		assertEquals(1, progress.count());
		assertTrue(progress.isComplete("merged"));
	}

	@Test
	public void pruneDropsStepsTheGuideNoLongerHas() throws Exception
	{
		Guide guide = parse(minimalGuide());
		Progress progress = new Progress(new HashSet<>(Arrays.asList("1111111111", "deleted")), new HashSet<>());

		assertEquals(1, progress.prune(guide));
		assertTrue(progress.isComplete("1111111111"));
		assertFalse(progress.isComplete("deleted"));
	}

	// --- section helpers ---------------------------------------------------

	@Test
	public void firstIncompleteStepWalksInOrder() throws Exception
	{
		Guide guide = loader.loadBundled();
		Progress progress = new Progress();

		Step first = progress.firstIncompleteStep(guide);
		assertNotNull(first);
		assertEquals(guide.getSections().get(0).getSteps().get(0).getId(), first.getId());

		progress.setComplete(first.getId(), true);
		Step second = progress.firstIncompleteStep(guide);
		assertNotNull(second);
		assertFalse(first.getId().equals(second.getId()));
		assertNotNull(progress.sectionOf(guide, second));
	}

	@Test
	public void sectionCompletionCounts() throws Exception
	{
		Guide guide = parse(minimalGuide());
		Section section = guide.getSections().get(0);
		Progress progress = new Progress();

		assertFalse(progress.isSectionComplete(section));
		progress.setComplete("1111111111", true);
		assertTrue(progress.isSectionComplete(section));
		assertEquals(1, progress.completedIn(section));
	}

	@Test
	public void bankLabelsIncludeLetterSuffixes() throws Exception
	{
		Guide guide = loader.loadBundled();
		boolean sawSuffixed = false;
		for (Section section : guide.getSections())
		{
			if (section.getBankSuffix() != null)
			{
				assertTrue(section.getDisplayLabel().endsWith(section.getBankSuffix()));
				sawSuffixed = true;
			}
		}
		assertTrue("expected banks like 39A in the shipped guide", sawSuffixed);
	}

	// --- bulk completion vs manual ticks -----------------------------------

	@Test
	public void completingABankDoesNotMarkStepsManual() throws Exception
	{
		Guide guide = loader.loadBundled();
		Section section = guide.getSections().get(1);
		Progress progress = new Progress();

		assertTrue(progress.setSectionComplete(section, true));
		assertTrue(progress.isSectionComplete(section));
		for (Step step : section.getSteps())
		{
			assertTrue(progress.isComplete(step.getId()));
			assertFalse("bulk completion must not look like a manual tick",
				progress.isManual(step.getId()));
		}
	}

	@Test
	public void unCompletingABankKeepsManuallyTickedSteps() throws Exception
	{
		Guide guide = loader.loadBundled();
		Section section = guide.getSections().get(1);
		Progress progress = new Progress();

		// The player ticks two steps by hand, then hits the bank checkbox, then
		// hits it again by accident.
		String first = section.getSteps().get(0).getId();
		String second = section.getSteps().get(2).getId();
		progress.setComplete(first, true);
		progress.setComplete(second, true);

		progress.setSectionComplete(section, true);
		assertTrue(progress.isSectionComplete(section));

		progress.setSectionComplete(section, false);

		assertTrue("hand-ticked work must survive", progress.isComplete(first));
		assertTrue("hand-ticked work must survive", progress.isComplete(second));
		assertEquals(2, progress.completedIn(section));
		assertEquals(2, progress.manualIn(section));
	}

	@Test
	public void unCompletingABankWithNoManualTicksClearsIt() throws Exception
	{
		Guide guide = loader.loadBundled();
		Section section = guide.getSections().get(1);
		Progress progress = new Progress();

		progress.setSectionComplete(section, true);
		progress.setSectionComplete(section, false);
		assertEquals(0, progress.completedIn(section));
	}

	@Test
	public void unTickingAStepForgetsThatItWasManual() throws Exception
	{
		Guide guide = parse(minimalGuide());
        Section section = guide.getSections().get(0);
		Progress progress = new Progress();

		progress.setComplete("1111111111", true);
		assertTrue(progress.isManual("1111111111"));

		progress.setComplete("1111111111", false);
		assertFalse(progress.isManual("1111111111"));

		// So a later bulk complete/uncomplete round trip clears it again.
		progress.setSectionComplete(section, true);
		progress.setSectionComplete(section, false);
		assertEquals(0, progress.completedIn(section));
	}

	@Test
	public void manualIdsThatWereNeverCompletedAreDiscarded()
	{
		// Only reachable from hand-edited config, but it must not produce a
		// "manual" step that is not complete.
		Progress progress = new Progress(
			new HashSet<>(Arrays.asList("a")), new HashSet<>(Arrays.asList("a", "ghost")));
		assertTrue(progress.isManual("a"));
		assertFalse(progress.isManual("ghost"));
	}

	@Test
	public void migrationsMoveManualTicksToo()
	{
		Progress progress = new Progress(
			new HashSet<>(Arrays.asList("old1", "bulk")), new HashSet<>(Arrays.asList("old1")));
		Map<String, String> migrations = new HashMap<>();
		migrations.put("old1", "new1");

		progress.applyMigrations(migrations);

		assertTrue(progress.isComplete("new1"));
		assertTrue("a reworded step must stay manual", progress.isManual("new1"));
		assertFalse(progress.isManual("bulk"));
	}

	@Test
	public void pruneDropsManualIdsForDeletedSteps() throws Exception
	{
		Guide guide = parse(minimalGuide());
		Progress progress = new Progress(
			new HashSet<>(Arrays.asList("1111111111", "deleted")),
			new HashSet<>(Arrays.asList("1111111111", "deleted")));

		progress.prune(guide);
		assertTrue(progress.isManual("1111111111"));
		assertFalse(progress.isManual("deleted"));
	}
}
