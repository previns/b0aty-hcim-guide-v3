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
package com.b0atyguide.overlay;

import com.b0atyguide.B0atyGuideConfig;
import com.b0atyguide.data.QuestHelperSteps;
import com.b0atyguide.data.Step;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;

/**
 * Colours the dialogue option the guide says to pick.
 *
 * <p>The guide writes the answers bare -- "Talk to Sedridor (1,2,1)" means
 * first option, then second, then first -- and the panel could only show that
 * as text, leaving the player counting rows in the chat box.
 *
 * <p>Recolours the option's own text rather than drawing a ring over it, which
 * is how Quest Helper does it. Better for two reasons beyond looking familiar:
 * it needs no widget geometry, so it cannot drift out of place when the chat
 * box is resized, and it survives the box being rebuilt between questions.
 *
 * <p>The mouse-leave listener is not decoration. Hovering an option resets its
 * colour, so without re-applying it the highlight disappears the moment the
 * player moves the cursor over the thing they are meant to click.
 *
 * <p>Runs on the client thread, from the game tick: mutating a widget from
 * anywhere else is not allowed.
 */
@Singleton
public class DialogueHighlighter
{
	/**
	 * A conversation is over once the option box has been shut this long.
	 *
	 * <p>It closes and reopens between every question, so "is it shut right
	 * now" cannot tell a finished conversation from the gap between two
	 * answers. Resetting on that gap made a (3,1) step colour 3, then 3 again.
	 */
	private static final int TICKS_UNTIL_OVER = 5;

	private final Client client;
	private final B0atyGuideConfig config;
	private final SceneTracker tracker;

	/** How many options this conversation has already answered. */
	private int answered;

	/** Ticks since the option box was last open. */
	private int shutFor = TICKS_UNTIL_OVER;

	/** The step the count belongs to; a different one starts over. */
	private String countingFor;

	@Inject
	DialogueHighlighter(Client client, B0atyGuideConfig config, SceneTracker tracker)
	{
		this.client = client;
		this.config = config;
		this.tracker = tracker;
	}

	/** Called each game tick, on the client thread. */
	public void onTick()
	{
		final Widget options = client.getWidget(InterfaceID.Chatmenu.OPTIONS);
		final boolean open = options != null && !options.isHidden();
		if (!open)
		{
			if (shutFor < TICKS_UNTIL_OVER)
			{
				shutFor++;
			}
			return;
		}

		if (!config.highlightDialogue())
		{
			shutFor = 0;
			return;
		}

		// Quest Helper's own words first, where it has them: it stores the text
		// on the option rather than its position, and matches whichever visible
		// option says it. That is why it never loses its place in a
		// conversation, and it is 2,789 options across the quests and diaries
		// this guide touches.
		final QuestHelperSteps.Instruction instruction = tracker.getInstruction();
		final List<String> said =
			instruction == null ? Collections.emptyList() : instruction.getDialogue();
		if (!said.isEmpty())
		{
			// Never both. The counter below has to be right about every answer
			// before it to be right about this one, so a question it did not
			// see would leave it off by one for the rest of the conversation.
			shutFor = 0;
			colourMatching(options, said);
			return;
		}

		final Step step = tracker.getStep();
		if (step == null || step.getDialogue().isEmpty())
		{
			shutFor = 0;
			return;
		}

		if (shutFor >= TICKS_UNTIL_OVER || !step.getId().equals(countingFor))
		{
			// A conversation that has been shut long enough is a new one.
			answered = 0;
			countingFor = step.getId();
		}
		else if (shutFor > 0)
		{
			// The box closed and came straight back: that is the next question,
			// so the answer before it has been given.
			//
			// Counting the player's clicks instead is what shipped first, and it
			// never advanced at all -- a dialogue option does not reliably
			// arrive as a menu click carrying a widget, so a (1,2,1,1) step sat
			// on the first answer and showed 1,1,1,1.
			answered++;
		}
		shutFor = 0;

		final Integer due = optionDue(step.getDialogue());
		if (due == null)
		{
			return;
		}

		final Widget[] rows = options.getDynamicChildren();
		if (rows == null || due < 0 || due >= rows.length)
		{
			return;
		}
		colour(rows[due]);
	}

	/**
	 * The option number due next, or null when the sequence is spent.
	 *
	 * <p>A step can carry several sequences, for a conversation the guide
	 * describes in parts. They are read as one run of answers.
	 */
	private Integer optionDue(List<List<Integer>> dialogue)
	{
		int seen = 0;
		for (List<Integer> sequence : dialogue)
		{
			for (Integer option : sequence)
			{
				if (seen == answered)
				{
					return option;
				}
				seen++;
			}
		}
		return null;
	}

	/**
	 * Colour the visible option whose text Quest Helper named.
	 *
	 * <p>An exact comparison, first match wins -- Quest Helper's own rule. A
	 * conversation that says none of them is left alone: better no highlight
	 * than one on the wrong line.
	 */
	private void colourMatching(Widget options, List<String> said)
	{
		// Quest Helper reads both, and some option boxes put their lines in the
		// nested set -- checkWidgets is called twice there for that reason.
		// Read those arrays directly instead of copying them into a list each
		// tick. Dynamic children must still win when both sets contain a match.
		if (!colourMatching(options.getDynamicChildren(), said))
		{
			colourMatching(options.getNestedChildren(), said);
		}
	}

	private boolean colourMatching(Widget[] rows, List<String> said)
	{
		if (rows == null)
		{
			return false;
		}
		for (Widget row : rows)
		{
			if (row == null || row.getText() == null)
			{
				continue;
			}
			final String shown = unnumbered(row.getText().trim());
			for (String wanted : said)
			{
				if (shown.equalsIgnoreCase(wanted.trim()))
				{
					colour(row);
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * An option's text with a leading option number taken off.
	 *
	 * <p>Quest Helper, when its numbering is on, rewrites the option it
	 * highlights to "[2] I'd like to mine in a different area." -- so a player
	 * running both plugins had this one match on the first tick and miss on
	 * every tick after, because by then the text was no longer what either
	 * plugin was looking for. It is also just the right reading: the number is
	 * not part of what the option says.
	 */
	static String unnumbered(String shown)
	{
		if (shown.length() < 4 || shown.charAt(0) != '[' || !Character.isDigit(shown.charAt(1)))
		{
			return shown;
		}
		final int close = shown.indexOf(']');
		return close < 0 ? shown : shown.substring(close + 1).trim();
	}

	private void colour(Widget row)
	{
		if (row == null || row.isHidden())
		{
			return;
		}
		final int rgb = config.highlightColor().getRGB();
		row.setTextColor(rgb);
		// Hovering resets the colour, so put it back on the way out.
		row.setOnMouseLeaveListener((JavaScriptCallback) e -> row.setTextColor(rgb));
	}

	/** Forget where we were in a conversation. */
	public void clear()
	{
		answered = 0;
		shutFor = TICKS_UNTIL_OVER;
		countingFor = null;
	}
}
