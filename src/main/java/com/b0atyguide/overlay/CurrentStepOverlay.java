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
import com.b0atyguide.bank.WithdrawTracker;
import com.b0atyguide.data.Guide;
import com.b0atyguide.data.ItemRef;
import com.b0atyguide.data.QuestHelperSteps;
import com.b0atyguide.data.Step;
import com.b0atyguide.data.Teleport;
import com.b0atyguide.data.Target;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

/**
 * Shows the current step over the game, so the guide can be followed without
 * the side panel open.
 *
 * <p>Deliberately just the current step. An overlay that listed the next few
 * would need its own scrolling and its own idea of "next", and the panel
 * already does that better.
 */
public class CurrentStepOverlay extends OverlayPanel
{
	private static final int WIDTH = 220;
	/** A bank not yet visited is missing everything; the list has to stop somewhere. */
	private static final int MAX_MISSING_SHOWN = 5;

	private final SceneTracker tracker;
	private final B0atyGuideConfig config;
	private final Client client;
	private final WithdrawTracker withdrawTracker;

	/** Set once the guide is loaded; null before that and after shutdown. */
	private Guide guide;

	@Inject
	CurrentStepOverlay(SceneTracker tracker, B0atyGuideConfig config, Client client,
		WithdrawTracker withdrawTracker)
	{
		this.tracker = tracker;
		this.config = config;
		this.client = client;
		this.withdrawTracker = withdrawTracker;
		setPosition(OverlayPosition.TOP_LEFT);
		panelComponent.setPreferredSize(new Dimension(WIDTH, 0));
	}

	public void setGuide(Guide guide)
	{
		this.guide = guide;
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.showStepOverlay())
		{
			return null;
		}

		final Step step = tracker.getStep();
		if (step == null)
		{
			return null;
		}

		final String heading = tracker.getSectionLabel();
		panelComponent.getChildren().add(TitleComponent.builder()
			.text(heading == null ? "B0aty Guide" : heading)
			.color(config.highlightColor())
			.build());

		// LineComponent does not wrap, and step text routinely runs past the
		// panel width, so the wrapping is done here against the real font.
		for (String line : wrap(step.getText(), graphics.getFontMetrics(), WIDTH - 14))
		{
			panelComponent.getChildren().add(
				LineComponent.builder().left(line).build());
		}

		final Target target = step.getTarget();
		if (target != null && target.getName() != null)
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left(onScreen() ? "On screen" : "Looking for")
				.right(target.getName())
				.leftColor(Color.GRAY)
				.rightColor(onScreen() ? config.highlightColor() : Color.LIGHT_GRAY)
				.build());
		}

		renderTeleport(step);
		renderMissingItems(graphics);
		renderQuestStep(graphics, step);
		return super.render(graphics);
	}

	/**
	 * How the step says to travel: "Ardy Cloak", "Fairy Ring".
	 *
	 * <p>Shown even when it is not an item -- a fairy ring or a minecart is
	 * still the answer to "how do I get there", which the destination alone
	 * does not say.
	 */
	private void renderTeleport(Step step)
	{
		final Teleport teleport = step.getTeleport();
		if (teleport == null || teleport.getVia().isEmpty())
		{
			return;
		}
		panelComponent.getChildren().add(LineComponent.builder()
			.left("Travel by")
			.right(teleport.getVia())
			.leftColor(Color.GRAY)
			.rightColor(teleport.isItem() ? config.highlightColor() : Color.LIGHT_GRAY)
			.build());
	}

	/**
	 * What this bank's withdraw list asks for that is not being carried.
	 *
	 * <p>Capped, because a bank you have not visited yet is missing all of it
	 * and a twenty-line overlay is worse than none.
	 */
	private void renderMissingItems(Graphics2D graphics)
	{
		if (!config.highlightWithdrawItems())
		{
			return;
		}
		final List<ItemRef> missing = withdrawTracker.getMissing();
		if (missing.isEmpty())
		{
			return;
		}

		panelComponent.getChildren().add(LineComponent.builder()
			.left("Still need")
			.right(Integer.toString(missing.size()))
			.leftColor(Color.GRAY)
			.rightColor(Color.GRAY)
			.build());

		final int shown = Math.min(missing.size(), MAX_MISSING_SHOWN);
		for (int i = 0; i < shown; i++)
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left(missing.get(i).getName())
				.leftColor(Color.ORANGE)
				.build());
		}
		if (missing.size() > shown)
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left("and " + (missing.size() - shown) + " more")
				.leftColor(Color.GRAY)
				.build());
		}
	}

	/**
	 * What Quest Helper would say for this quest at the player's current
	 * progress.
	 *
	 * <p>The guide advances quests a step at a time in passing, so the step
	 * text alone ("Start X Marks the Spot on Veos") does not say what to do
	 * next once you are two steps in. The quest's own progress value does.
	 */
	private void renderQuestStep(Graphics2D graphics, Step step)
	{
		if (!config.showQuestSteps() || guide == null)
		{
			return;
		}

		final QuestHelperSteps helper = guide.questHelperFor(step);
		if (helper == null || helper.getVar() == null)
		{
			return;
		}

		final QuestHelperSteps.Var var = helper.getVar();
		final int value = var.isVarbit()
			? client.getVarbitValue(var.getId())
			: client.getVarpValue(var.getId());
		final QuestHelperSteps.Instruction instruction = helper.at(value);
		if (instruction == null)
		{
			return;
		}

		panelComponent.getChildren().add(LineComponent.builder()
			.left("Quest step")
			.right(instruction.isConditional() ? "(varies)" : "")
			.leftColor(Color.GRAY)
			.rightColor(Color.GRAY)
			.build());

		// A conditional default is Quest Helper's fallback branch, not the one
		// its conditions would pick, so it is dimmed rather than shown in the
		// same weight as an instruction we can stand behind.
		final Color colour = instruction.isConditional() ? Color.LIGHT_GRAY : Color.WHITE;
		for (String line : wrap(instruction.getText(), graphics.getFontMetrics(), WIDTH - 14))
		{
			panelComponent.getChildren().add(
				LineComponent.builder().left(line).leftColor(colour).build());
		}
	}

	private boolean onScreen()
	{
		return !tracker.getNpcs().isEmpty() || !tracker.getObjects().isEmpty();
	}

	/** Greedy word wrap. Words longer than the line get their own line rather than truncating. */
	private static List<String> wrap(String text, FontMetrics metrics, int width)
	{
		final List<String> lines = new ArrayList<>();
		final StringBuilder line = new StringBuilder();
		for (String word : text.split("\\s+"))
		{
			if (word.isEmpty())
			{
				continue;
			}
			final String candidate = line.length() == 0 ? word : line + " " + word;
			if (metrics.stringWidth(candidate) > width && line.length() > 0)
			{
				lines.add(line.toString());
				line.setLength(0);
				line.append(word);
			}
			else
			{
				line.setLength(0);
				line.append(candidate);
			}
		}
		if (line.length() > 0)
		{
			lines.add(line.toString());
		}
		return lines;
	}
}
