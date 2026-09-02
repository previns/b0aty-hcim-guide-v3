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
import com.b0atyguide.progress.Progress;
import com.b0atyguide.ui.GuidePanel;
import com.b0atyguide.ui.SectionPanel;
import com.google.gson.Gson;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JCheckBox;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import javax.swing.JTextArea;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;

/**
 * Lays the panel out at a fixed width and asserts what it looks like.
 *
 * <p>Every layout bug in this panel so far -- clipped text, a checkbox with no
 * room, rows wider than the sidebar -- was found by squinting at a screenshot
 * and fixed by guessing. Swing will lay out perfectly well without a display, so
 * these are the questions that should have been asked of the code rather than of
 * a screenshot.
 */
public class PanelLayoutTest
{
	private static final int SIDEBAR_WIDTH = 225;
	private static final int SIDEBAR_HEIGHT = 800;

	private static Guide guide;

	@BeforeClass
	public static void loadGuide() throws Exception
	{
		guide = new GuideLoader(new Gson()).loadBundled();
	}

	private Progress lastProgress;

	private GuidePanel laidOutPanel(int fontSize, boolean expandFirstBanks)
	{
		// A headless CI box can still lay out Swing; a box with no fonts at all
		// cannot, and there is nothing useful to assert there.
		assumeFalse("no font support available",
			java.awt.GraphicsEnvironment.isHeadless()
				&& System.getProperty("java.awt.headless") == null);

		final Progress progress = new Progress();
		lastProgress = progress;
		final GuidePanel panel = new GuidePanel();
		panel.init(
			guide,
			progress,
			(step, complete) -> progress.setComplete(step.getId(), complete),
			step -> { },
			(section, complete) -> progress.setSectionComplete(section, complete),
			() -> fontSize,
			() -> true,
			// No injector in a layout test, and no business fetching images.
			null, () -> false,
			// The feature list is not what these tests measure, and it would
			// change every row's position.
			() -> false, show -> { });

		if (expandFirstBanks)
		{
			// Nothing is expanded until asked, so force a few open to get real
			// step rows into the layout.
			panel.expandForTest(3);
		}

		panel.setSize(new Dimension(SIDEBAR_WIDTH, SIDEBAR_HEIGHT));
		// A wrapping JTextArea derives its preferred height from the width it has
		// been given, so the first pass sizes widths and the second settles
		// heights. Real Swing converges the same way, over repeated validate
		// cycles; here it has to be driven explicitly.
		for (int pass = 0; pass < 3; pass++)
		{
			layoutDeep(panel);
		}
		return panel;
	}

	/**
	 * Swing only lays out inside a realised window, so drive it directly. The
	 * parent's layout manager sets each child's bounds, so recursing after
	 * doLayout() gives every component its real size.
	 */
	private static void layoutDeep(Component component)
	{
		component.doLayout();
		if (component instanceof Container)
		{
			for (Component child : ((Container) component).getComponents())
			{
				if (child.isVisible())
				{
					layoutDeep(child);
				}
			}
		}
	}

	private static <T> List<T> findAll(Component root, Class<T> type)
	{
		final List<T> found = new ArrayList<>();
		collect(root, type, found);
		return found;
	}

	private static <T> void collect(Component component, Class<T> type, List<T> into)
	{
		if (type.isInstance(component))
		{
			into.add(type.cast(component));
		}
		if (component instanceof Container)
		{
			for (Component child : ((Container) component).getComponents())
			{
				collect(child, type, into);
			}
		}
	}

	/** Right edge of a component in the coordinate space of an ancestor. */
	private static int rightEdgeIn(Component component, Component ancestor)
	{
		int x = 0;
		Component walk = component;
		while (walk != null && walk != ancestor)
		{
			x += walk.getX();
			walk = walk.getParent();
		}
		return x + component.getWidth();
	}

	@Test
	public void checkboxesHaveRoomToDraw()
	{
		final GuidePanel panel = laidOutPanel(14, true);
		final List<JCheckBox> boxes = findAll(panel, JCheckBox.class);

		assertFalse("expected checkboxes in the panel", boxes.isEmpty());
		for (JCheckBox box : boxes)
		{
			assertTrue("a checkbox was laid out with no width",
				box.getPreferredSize().width > 0);
			assertTrue("a checkbox was laid out with no height",
				box.getPreferredSize().height > 0);
		}
	}

	@Test
	public void noStepTextIsWiderThanTheSidebar()
	{
		final GuidePanel panel = laidOutPanel(14, true);
		final List<JTextArea> texts = findAll(panel, JTextArea.class);

		assertFalse("expected step text in the panel", texts.isEmpty());
		for (JTextArea text : texts)
		{
			final int right = rightEdgeIn(text, panel);
			assertTrue(
				"step text runs " + (right - SIDEBAR_WIDTH) + "px past the sidebar: "
					+ text.getText().substring(0, Math.min(48, text.getText().length())),
				right <= SIDEBAR_WIDTH);
		}
	}

	@Test
	public void stepTextWrapsRatherThanRunningOn()
	{
		final GuidePanel panel = laidOutPanel(14, true);
		boolean sawWrapped = false;
		for (JTextArea text : findAll(panel, JTextArea.class))
		{
			if (text.getText().length() <= 90)
			{
				continue;
			}
			// getLineCount() counts newlines in the document, not wrapped rows,
			// so it reports 1 for any single-paragraph step however long. Height
			// is what actually says whether the text wrapped.
			final int lineHeight = text.getFontMetrics(text.getFont()).getHeight();
			assertTrue(
				"a long step occupies one line, so it is being clipped: "
					+ text.getText().substring(0, 48),
				text.getHeight() > lineHeight);
			sawWrapped = true;
		}
		assertTrue("expected at least one long step to exercise wrapping", sawWrapped);
	}

	@Test
	public void largerFontStillFitsTheSidebar()
	{
		final GuidePanel panel = laidOutPanel(22, true);
		for (JTextArea text : findAll(panel, JTextArea.class))
		{
			assertTrue("step text overflows at the largest configurable size",
				rightEdgeIn(text, panel) <= SIDEBAR_WIDTH);
		}
		for (JCheckBox box : findAll(panel, JCheckBox.class))
		{
			assertTrue(box.getPreferredSize().width > 0);
		}
	}

	@Test
	public void everyBankHasAHeaderCheckbox()
	{
		final GuidePanel panel = laidOutPanel(14, false);
		final int sections = guide.getSections().size();
		final List<JCheckBox> boxes = findAll(panel, JCheckBox.class);

		// Nothing is expanded, so every checkbox present is a bank header's.
		assertTrue("expected one header checkbox per bank, found " + boxes.size(),
			boxes.size() >= sections);
	}

	@Test
	public void collapsedPanelBuildsNoStepRows()
	{
		final GuidePanel panel = laidOutPanel(14, false);
		assertTrue("collapsed sections must not build their step rows",
			findAll(panel, JTextArea.class).isEmpty());
	}

	@Test
	public void firstBankStepsAreTheGuidesFirstSteps()
	{
		final GuidePanel panel = laidOutPanel(14, true);
		final Section first = guide.getSections().get(0);
		final Step firstStep = first.getSteps().get(0);
		final List<JTextArea> texts = findAll(panel, JTextArea.class);

		assertTrue("expected the first step to be rendered",
			texts.stream().anyMatch(t -> t.getText().equals(firstStep.getText())));
	}

	// --- clicking ----------------------------------------------------------

	private static void click(Component target, int button)
	{
		final MouseEvent event = new MouseEvent(
			target,
			MouseEvent.MOUSE_PRESSED,
			System.currentTimeMillis(),
			button == MouseEvent.BUTTON3 ? InputEvent.BUTTON3_DOWN_MASK : 0,
			3, 3, 1, false, button);
		for (java.awt.event.MouseListener listener : target.getMouseListeners())
		{
			listener.mousePressed(event);
		}
	}

	@Test
	public void clickingTheStepTextCompletesTheStep()
	{
		final GuidePanel panel = laidOutPanel(14, true);
		final JTextArea text = findAll(panel, JTextArea.class).get(0);
		final Step step = guide.getSections().get(0).getSteps().get(0);

		assertFalse(lastProgress.isComplete(step.getId()));
		click(text, MouseEvent.BUTTON1);
		assertTrue("clicking the text should tick the step",
			lastProgress.isComplete(step.getId()));
		assertTrue("a click is a manual tick, not a bulk one",
			lastProgress.isManual(step.getId()));
	}

	@Test
	public void clickingACompletedStepUnticksIt()
	{
		final GuidePanel panel = laidOutPanel(14, true);
		final JTextArea text = findAll(panel, JTextArea.class).get(0);
		final Step step = guide.getSections().get(0).getSteps().get(0);

		click(text, MouseEvent.BUTTON1);
		click(text, MouseEvent.BUTTON1);
		assertFalse("a second click should untick it",
			lastProgress.isComplete(step.getId()));
	}

	@Test
	public void rightClickingDoesNotChangeCompletion()
	{
		final GuidePanel panel = laidOutPanel(14, true);
		final JTextArea text = findAll(panel, JTextArea.class).get(0);
		final Step step = guide.getSections().get(0).getSteps().get(0);

		click(text, MouseEvent.BUTTON3);
		assertFalse("right-click targets a step, it does not complete it",
			lastProgress.isComplete(step.getId()));
	}

	@Test
	public void theCheckboxItselfDoesNotDoubleToggle()
	{
		// The row-wide click handler must not also be on the checkbox, or one
		// click would toggle twice and appear to do nothing. Counting listeners
		// proves nothing -- the look and feel installs its own -- so the check is
		// that none of ours is attached.
		final GuidePanel panel = laidOutPanel(14, true);
		for (JCheckBox box : findAll(panel, JCheckBox.class))
		{
			for (java.awt.event.MouseListener listener : box.getMouseListeners())
			{
				assertFalse(
					"a plugin mouse handler is attached to a checkbox: "
						+ listener.getClass().getName(),
					listener.getClass().getName().startsWith("com.b0atyguide"));
			}
		}
	}

	// --- scrolling ---------------------------------------------------------

	private static JViewport viewportOf(GuidePanel panel)
	{
		return findAll(panel, JScrollPane.class).get(0).getViewport();
	}

	/** Run every queued EDT task, since refresh() and scrollTo() both defer. */
	private static void drainEventQueue() throws Exception
	{
		for (int i = 0; i < 4; i++)
		{
			SwingUtilities.invokeAndWait(() -> { });
		}
	}

	@Test
	public void finishingABankPutsTheNextOneAtTheTop() throws Exception
	{
		final GuidePanel panel = laidOutPanel(14, false);
		final Section first = guide.getSections().get(0);
		final Section second = guide.getSections().get(1);

		lastProgress.setSectionComplete(first, true);
		panel.refresh(lastProgress.firstIncompleteStep(guide));
		drainEventQueue();
		for (int pass = 0; pass < 3; pass++)
		{
			layoutDeep(panel);
		}

		final JViewport viewport = viewportOf(panel);
		final Component view = viewport.getView();
		final SectionPanel target = panel.sectionPanelForTest(second.getId());

		final int sectionTop = SwingUtilities.convertPoint(target, 0, 0, view).y;
		final int viewTop = viewport.getViewPosition().y;

		assertEquals(
			"the next bank should be flush with the top of the viewport",
			sectionTop, viewTop);
	}

	@Test
	public void tickingOneStepDoesNotYankTheList() throws Exception
	{
		final GuidePanel panel = laidOutPanel(14, false);
		final Section first = guide.getSections().get(0);

		// Land on bank 1 first, then tick a single step inside it.
		panel.refresh(lastProgress.firstIncompleteStep(guide));
		drainEventQueue();
		final int before = viewportOf(panel).getViewPosition().y;

		lastProgress.setComplete(first.getSteps().get(0).getId(), true);
		panel.refresh(lastProgress.firstIncompleteStep(guide));
		drainEventQueue();

		assertEquals(
			"the view should only move when the current bank changes",
			before, viewportOf(panel).getViewPosition().y);
	}

	@Test
	public void completedBanksCollapse() throws Exception
	{
		final GuidePanel panel = laidOutPanel(14, false);
		final Section first = guide.getSections().get(0);

		lastProgress.setSectionComplete(first, true);
		panel.refresh(lastProgress.firstIncompleteStep(guide));
		drainEventQueue();

		assertFalse("a finished bank should fold away",
			panel.sectionPanelForTest(first.getId()).isExpandedForTest());
		assertTrue("the bank you are now on should be open",
			panel.sectionPanelForTest(guide.getSections().get(1).getId())
				.isExpandedForTest());
	}

	/**
	 * Width changes must not throw, and must be free for a bank whose rows were
	 * never built.
	 *
	 * <p>This does NOT cover the reported "jumbled text on a fresh client" bug.
	 * Two attempts at that both passed with the fix removed -- the harness lays
	 * out repeatedly, which settles the text either way, and a JTextArea
	 * recomputes its preferred size rather than caching it as assumed. The fix
	 * is plausible but unverified; it is confirmed only in a live client.
	 */
	@Test
	public void aWidthChangeIsSafeAndFreeForCollapsedBanks()
	{
		assumeFalse("no font support available",
			java.awt.GraphicsEnvironment.isHeadless()
				&& System.getProperty("java.awt.headless") == null);

		final GuidePanel panel = laidOutPanel(14, true);
		final List<SectionPanel> sections = findAll(panel, SectionPanel.class);
		assertFalse(sections.isEmpty());

		int collapsed = 0;
		for (SectionPanel section : sections)
		{
			if (!section.isExpandedForTest())
			{
				collapsed++;
			}
			section.onWidthChanged();
		}
		assertTrue("most banks start collapsed", collapsed > 0);

		// Still laid out sanely afterwards.
		layoutDeep(panel);
		for (JTextArea text : findAll(panel, JTextArea.class))
		{
			assertTrue("step text runs past the sidebar: " + text.getText(),
				rightEdgeIn(text, panel) <= SIDEBAR_WIDTH);
		}
	}

	/**
	 * The feature list is the only part of the panel a player is told rather
	 * than shown, so it has to be readable at the sidebar's real width -- the
	 * same clipping that broke the step rows would make it useless.
	 */
	@Test
	public void theFeatureListFitsTheSidebar()
	{
		assumeFalse("no font support available",
			java.awt.GraphicsEnvironment.isHeadless()
				&& System.getProperty("java.awt.headless") == null);

		final Progress progress = new Progress();
		final GuidePanel panel = new GuidePanel();
		final boolean[] dismissed = {false};
		panel.init(guide, progress,
			(step, complete) -> progress.setComplete(step.getId(), complete),
			step -> { },
			(section, complete) -> progress.setSectionComplete(section, complete),
			() -> 14,
			() -> true,
			null, () -> false,
			() -> true, show -> dismissed[0] = true);

		panel.setSize(new Dimension(SIDEBAR_WIDTH, SIDEBAR_HEIGHT));
		for (int pass = 0; pass < 3; pass++)
		{
			layoutDeep(panel);
		}

		final List<JTextArea> texts = findAll(panel, JTextArea.class);
		assertFalse("the feature list should render some text", texts.isEmpty());
		for (JTextArea text : texts)
		{
			assertTrue("feature text runs past the sidebar: " + text.getText(),
				rightEdgeIn(text, panel) <= SIDEBAR_WIDTH);
		}
	}

	@Test
	public void theFeatureListNamesTheBankSearch()
	{
		// The single least discoverable thing the plugin does. If this stops
		// being mentioned, the feature is effectively gone.
		assumeFalse("no font support available",
			java.awt.GraphicsEnvironment.isHeadless()
				&& System.getProperty("java.awt.headless") == null);

		final Progress progress = new Progress();
		final GuidePanel panel = new GuidePanel();
		panel.init(guide, progress,
			(step, complete) -> { },
			step -> { },
			(section, complete) -> { },
			() -> 14,
			() -> true,
			null, () -> false,
			() -> true, show -> { });
		layoutDeep(panel);

		final StringBuilder all = new StringBuilder();
		for (JTextArea text : findAll(panel, JTextArea.class))
		{
			all.append(text.getText()).append(System.lineSeparator());
		}
		assertTrue("the bank search should be explained",
			all.toString().contains("bank150"));
	}

	@Test
	public void theFeatureListCanBeTurnedOff()
	{
		assumeFalse("no font support available",
			java.awt.GraphicsEnvironment.isHeadless()
				&& System.getProperty("java.awt.headless") == null);

		final Progress progress = new Progress();
		final GuidePanel panel = new GuidePanel();
		panel.init(guide, progress,
			(step, complete) -> { },
			step -> { },
			(section, complete) -> { },
			() -> 14,
			() -> true,
			null, () -> false,
			() -> false, show -> { });
		layoutDeep(panel);

		final StringBuilder all = new StringBuilder();
		for (JTextArea text : findAll(panel, JTextArea.class))
		{
			all.append(text.getText()).append(System.lineSeparator());
		}
		assertFalse("nothing of it should render when it is off",
			all.toString().contains("bank150"));
	}

	/**
	 * Turning the plugin off has to let go of the guide.
	 *
	 * <p>This asserts the part that is observable without a client: the rows are
	 * dropped, and a second call is harmless -- RuneLite calls shutDown() even
	 * when startUp() failed part way. That deinit also stops the pending search
	 * debounce is the reason it exists, but a Swing timer firing on a detached
	 * panel cannot be observed from a test without reflection, so that half is
	 * unverified here.
	 */
	@Test
	public void deinitReleasesTheGuide()
	{
		final GuidePanel panel = laidOutPanel(14, true);
		assertFalse("fixture should have rows to release",
			findAll(panel, SectionPanel.class).isEmpty());

		panel.deinit();
		assertTrue("deinit should drop the section rows",
			findAll(panel, SectionPanel.class).isEmpty());

		// shutDown() runs even when startUp() threw, so this must not care.
		panel.deinit();
	}
}
