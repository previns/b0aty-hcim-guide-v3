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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The dev client's console has to be readable, and the shipped plugin has to
 * keep its opinions about logging to itself.
 *
 * <p>These run on the same classpath the {@code run} task uses, so they check
 * the real thing rather than the intent.
 */
public class LoggingTest
{
	@Test
	public void onlyThisPluginIsVerbose()
	{
		// Ours at DEBUG, so eleven log lines are visible...
		final Logger ours = LoggerFactory.getLogger("com.b0atyguide.Whatever");
		assertTrue("this plugin should log at DEBUG in the dev client",
			ours.isDebugEnabled());

		// ...and everyone else at INFO, so they are not buried under the cache
		// loader and the net layer. This is what --debug used to break.
		final Logger theirs = LoggerFactory.getLogger("net.runelite.client.RuneLite");
		assertFalse("only this plugin should be verbose", theirs.isDebugEnabled());
		assertTrue("the client should still log INFO and above", theirs.isInfoEnabled());
	}

	@Test
	public void theLoggingConfigNeverShips() throws Exception
	{
		// A plugin dictating logging to the client that hosts it would be
		// rejected, and rightly. The config is a development convenience, so it
		// belongs to the test source set and nowhere else.
		assertTrue("the dev-client logging config should exist",
			Files.exists(Paths.get("src/test/resources/logback-test.xml")));

		for (Path path : Files.walk(Paths.get("src/main")).toArray(Path[]::new))
		{
			assertFalse("logging config must not ship: " + path,
				path.getFileName().toString().startsWith("logback"));
		}
	}
}
