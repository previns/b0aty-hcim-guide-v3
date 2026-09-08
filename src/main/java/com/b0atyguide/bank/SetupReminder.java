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
package com.b0atyguide.bank;

import com.b0atyguide.B0atyGuideConfig;
import com.b0atyguide.data.Section;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.ChatMessageType;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;

/**
 * Says once per bank that the setup screenshot is the real answer.
 *
 * <p>The plugin can tell you what this bank's withdraw line asks for. It cannot
 * tell you what you should already be wearing, because the guide does not say:
 * it writes "deposit all" at 32 of its 211 banks, says nothing about depositing
 * at 165, and names worn gear in prose that resolves to an item on one step out
 * of twenty-nine. A running model of it was built, measured at sixty items in a
 * twenty-eight slot inventory, and thrown away.
 *
 * <p>So this points at the thing that does know. The bank's screenshot shows
 * the whole loadout, equipment included, and 169 of the 236 banks have one.
 *
 * <p>Once per bank, on arriving at it. A reminder that repeats is a reminder
 * people turn off.
 */
@Singleton
public class SetupReminder
{
	private final ChatMessageManager chat;
	private final B0atyGuideConfig config;

	/** The bank already mentioned, so it is said once rather than every tick. */
	private String toldAbout;

	@Inject
	SetupReminder(ChatMessageManager chat, B0atyGuideConfig config)
	{
		this.chat = chat;
		this.config = config;
	}

	/**
	 * Called as the current bank changes.
	 *
	 * @param section the bank now in progress, or null
	 */
	public void onBankChanged(Section section)
	{
		if (section == null)
		{
			return;
		}
		if (section.getId().equals(toldAbout))
		{
			return;
		}
		toldAbout = section.getId();

		if (!config.remindAboutSetup() || section.getImageUrls().isEmpty())
		{
			return;
		}

		final String message = new ChatMessageBuilder()
			.append(ChatColorType.HIGHLIGHT)
			.append(section.getDisplayLabel())
			.append(ChatColorType.NORMAL)
			.append(": check the setup image in the guide panel -- it shows what you "
				+ "should be wearing as well as carrying, which the withdraw list does not.")
			.build();

		chat.queue(QueuedMessage.builder()
			.type(ChatMessageType.CONSOLE)
			.runeLiteFormattedMessage(message)
			.build());
	}

	public void clear()
	{
		toldAbout = null;
	}
}
