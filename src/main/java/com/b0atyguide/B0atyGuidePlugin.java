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

import com.b0atyguide.bank.GuideBankTags;
import com.b0atyguide.bank.WithdrawOverlay;
import com.b0atyguide.bank.WithdrawTracker;
import com.b0atyguide.data.Guide;
import com.b0atyguide.data.GuideLoader;
import com.b0atyguide.data.Section;
import com.b0atyguide.data.Step;
import com.b0atyguide.data.Tag;
import com.b0atyguide.overlay.ApproachTracker;
import com.b0atyguide.overlay.BankTracker;
import com.b0atyguide.overlay.CurrentStepOverlay;
import com.b0atyguide.overlay.HighlightOverlay;
import com.b0atyguide.overlay.MinimapOverlay;
import com.b0atyguide.overlay.PathOverlay;
import com.b0atyguide.overlay.GuideIcon;
import com.b0atyguide.overlay.SceneTracker;
import com.b0atyguide.overlay.SpellOverlay;
import com.b0atyguide.overlay.WorldMapMarker;
import com.b0atyguide.path.PathTracker;
import com.b0atyguide.progress.AutoTick;
import com.b0atyguide.progress.Progress;
import com.b0atyguide.progress.QuestProgress;
import com.b0atyguide.ui.GuidePanel;
import com.b0atyguide.ui.SectionImages;
import com.google.gson.Gson;
import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.GroundObjectSpawned;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.WallObjectSpawned;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;

/**
 * Follows the B0aty HCIM Guide V3 in game.
 *
 * <p>The plugin is a <b>renderer over one data file</b>. Every step, item id,
 * coordinate and quest reference lives in {@code guide.json}, which is built
 * from the OSRS Wiki in a separate repository. Nothing here decides what the
 * guide says.
 *
 * <p>Two rules are worth knowing before changing this class, because both are
 * invisible in the API and each has broken the plugin once:
 *
 * <ul>
 *   <li>{@code Quest.getState} runs a client script, and the client refuses to
 *       run one from inside another. {@code VarbitChanged} is posted from
 *       inside script execution, so the quest pass is deferred to
 *       {@code GameTick}.
 *   <li>{@code Client.getItemContainer} requires the client thread, and
 *       {@code startUp()} runs on the event thread. Reads of it are deferred.
 * </ul>
 */
@Slf4j
@PluginDescriptor(
	name = "B0aty HCIM Guide v3",
	description = "Follow the B0aty HCIM Guide V3 in game, synced from the OSRS Wiki",
	tags = {"ironman", "hcim", "guide", "quest", "skilling", "route", "b0aty", "checklist"}
)
public class B0atyGuidePlugin extends Plugin
{
	private static final String PROGRESS_KEY = "progress";
	private static final String MANUAL_KEY = "manualProgress";

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private ConfigManager configManager;

	@Inject
	private B0atyGuideConfig config;

	@Inject
	private Gson gson;

	@Inject
	private QuestProgress questProgress;

	@Inject
	private SectionImages sectionImages;

	@Inject
	private SceneTracker sceneTracker;

	@Inject
	private MinimapOverlay minimapOverlay;

	@Inject
	private PathOverlay pathOverlay;

	@Inject
	private SpellOverlay spellOverlay;

	@Inject
	private PathTracker pathTracker;

	@Inject
	private ApproachTracker approachTracker;

	@Inject
	private BankTracker bankTracker;

	@Inject
	private WorldMapMarker worldMapMarker;

	@Inject
	private GuideBankTags bankTags;

	@Inject
	private WithdrawTracker withdrawTracker;

	@Inject
	private WithdrawOverlay withdrawOverlay;

	@Inject
	private HighlightOverlay highlightOverlay;

	@Inject
	private CurrentStepOverlay currentStepOverlay;

	private GuidePanel panel;
	private NavigationButton navButton;
	private Guide guide;
	private Progress progress = new Progress();

	/**
	 * A varbit changed, so a quest may have completed -- but the check has to
	 * wait for a tick, because Quest.getState cannot run inside the script that
	 * posted the event.
	 */
	private boolean questCheckPending;

	@Provides
	B0atyGuideConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(B0atyGuideConfig.class);
	}

	@Override
	protected void startUp() throws Exception
	{
		guide = new GuideLoader(gson).loadBundled();
		// INFO, not DEBUG: this plugin's entire job is loading a data file, so
		// "which revision, how many steps" is the first thing anyone diagnosing
		// it needs, and it is one line per session.
		log.info("loaded guide revid {} ({} sections, {} steps, hash {})",
			guide.getSourceRevid(), guide.getSections().size(),
			guide.getStepCount(), guide.getContentHash());

		sectionImages.setExpectedHashes(guide::imageHashFor);
		loadProgress();

		panel = injector.getInstance(GuidePanel.class);
		panel.init(guide, progress, this::onStepToggled, this::onStepSelected,
			this::onSectionToggled, config::fontSize, config::collapseCompleted,
			sectionImages, config::showSectionImages,
			config::showFeatures, this::onFeaturesDismissed);

		// One 56x56 asset, resized per use: the sidebar wants a small square,
		// the world map wants something larger.
		final BufferedImage icon = ImageUtil.resizeImage(
			ImageUtil.loadImageResource(B0atyGuidePlugin.class, GuideIcon.RESOURCE),
			GuideIcon.SIDEBAR, GuideIcon.SIDEBAR);
		navButton = NavigationButton.builder()
			.tooltip("B0aty HCIM Guide v3")
			.icon(icon)
			.priority(6)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);
		overlayManager.add(highlightOverlay);
		currentStepOverlay.setGuide(guide);
		overlayManager.add(currentStepOverlay);
		overlayManager.add(minimapOverlay);
		overlayManager.add(pathOverlay);
		overlayManager.add(withdrawOverlay);
		overlayManager.add(spellOverlay);
		if (config.registerBankTags())
		{
			bankTags.register(guide);
		}

		selectCurrentStep();
	}

	@Override
	protected void shutDown()
	{
		// startUp() can fail part way -- a bad guide.json throws before the nav
		// button exists -- and RuneLite calls shutDown() regardless. Removing a
		// null navigation throws inside ClientUI on the EDT, which surfaces as
		// an uncaught exception with none of our frames in the trace.
		overlayManager.remove(highlightOverlay);
		overlayManager.remove(currentStepOverlay);
		currentStepOverlay.setGuide(null);
		overlayManager.remove(minimapOverlay);
		overlayManager.remove(pathOverlay);
		overlayManager.remove(withdrawOverlay);
		overlayManager.remove(spellOverlay);
		if (navButton != null)
		{
			clientToolbar.removeNavigation(navButton);
			navButton = null;
		}
		releaseTrackers();
		if (panel != null)
		{
			panel.deinit();
		}
		panel = null;
		guide = null;
	}

	// --- progress ----------------------------------------------------------

	/**
	 * Progress is stored per RuneLite profile, so an alt keeps its own ticks
	 * without the plugin needing the account hash before login.
	 */
	private void loadProgress()
	{
		progress = new Progress(parseIds(config.progress()), parseIds(config.manualProgress()));

		final int migrated = progress.applyMigrations(guide.getMigrations());
		final int pruned = progress.prune(guide);
		if (migrated > 0 || pruned > 0)
		{
			log.debug("progress: {} step(s) migrated, {} dropped", migrated, pruned);
			saveProgress();
		}
	}

	private static Set<String> parseIds(String stored)
	{
		final Set<String> ids = new LinkedHashSet<>();
		if (stored != null && !stored.isEmpty())
		{
			ids.addAll(Arrays.asList(stored.split(",")));
			ids.remove("");
		}
		return ids;
	}

	private void saveProgress()
	{
		configManager.setConfiguration(
			B0atyGuideConfig.GROUP, PROGRESS_KEY, String.join(",", progress.completedIds()));
		configManager.setConfiguration(
			B0atyGuideConfig.GROUP, MANUAL_KEY, String.join(",", progress.manualIds()));
	}

	/**
	 * Tick or untick a whole bank.
	 *
	 * <p>Un-ticking keeps the steps the player ticked individually, so pressing
	 * this by accident never loses work.
	 */
	private void onSectionToggled(Section section, boolean complete)
	{
		if (progress.setSectionComplete(section, complete))
		{
			saveProgress();
		}
		selectCurrentStep();
	}

	private void onStepToggled(Step step, boolean complete)
	{
		progress.setComplete(step.getId(), complete);
		saveProgress();
		selectCurrentStep();
	}

	/**
	 * The player closed the feature list. Written to config so it stays closed,
	 * and findable again under Panel in the settings.
	 */
	private void onFeaturesDismissed(boolean show)
	{
		configManager.setConfiguration(
			B0atyGuideConfig.GROUP, "showFeatures", Boolean.toString(show));
	}

	private void onStepSelected(Step step)
	{
		sceneTracker.setStep(step, labelFor(step));
		worldMapMarker.setStep(step);
		pathTracker.clear();
	}

	/** "Bank 12" for the overlay heading, or null when the step is not in a section. */
	private String labelFor(Step step)
	{
		final Section section = progress.sectionOf(guide, step);
		return section == null ? null : section.getDisplayLabel();
	}

	private void selectCurrentStep()
	{
		final Step current = progress.firstIncompleteStep(guide);
		sceneTracker.setStep(current, labelFor(current));
		worldMapMarker.setStep(current);
		withdrawTracker.update(guide, current);
		pathTracker.clear();
		if (panel != null)
		{
			panel.refresh(current);
		}
	}

	// --- auto-ticking ------------------------------------------------------

		/**
	 * Tick steps whose quest the client reports finished.
	 *
	 * <p><b>Never call this from inside a client script.</b> {@link
	 * Quest#getState} runs one, and the client refuses to run a script from
	 * within another -- it throws "scripts are not reentrant" and takes the
	 * client thread with it, which the player sees as the game hanging on
	 * "Please wait" at login. {@code VarbitChanged} in particular is posted
	 * from inside script execution; {@code GameTick} is not.
	 */
	private void syncQuestCompletion()
	{
		if (!config.autoTickQuests() || guide == null
			|| client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		boolean changed = false;
		for (Section section : guide.getSections())
		{
			for (Step step : section.getSteps())
			{
				if (progress.isComplete(step.getId()))
				{
					continue;
				}
				final Tag tag = step.getVerifiableQuest();
				if (tag == null)
				{
					continue;
				}
				final Quest quest = questFor(tag.getConstant());
				if (quest != null && quest.getState(client) == QuestState.FINISHED)
				{
					progress.setComplete(step.getId(), true);
					changed = true;
				}
			}
		}

		if (changed)
		{
			saveProgress();
			selectCurrentStep();
		}
	}

	/**
	 * Tick the current step when the quest it names moves forward.
	 *
	 * <p>Reads varbits and varplayers only, so it is safe from inside a client
	 * script -- unlike the quest-state check, which runs one.
	 */
	private void syncQuestProgress()
	{
		if (!config.autoTickQuestProgress() || guide == null
			|| client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		final Step current = progress.firstIncompleteStep(guide);
		if (questProgress.hasAdvanced(guide, current, client::getVarbitValue,
			client::getVarpValue))
		{
			progress.setComplete(current.getId(), true);
			questProgress.clear();
			saveProgress();
			selectCurrentStep();
			return;
		}
		questProgress.watch(guide, current, client::getVarbitValue, client::getVarpValue);
	}

	/**
	 * Tick steps whose skill target has been reached.
	 *
	 * <p>Reads a level, which is a plain lookup -- safe from inside a client
	 * script, like the diary bits and unlike the quest state.
	 */
	private void syncSkillCompletion()
	{
		if (!config.autoTickSkills() || guide == null
			|| client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		if (AutoTick.skills(guide, progress, this::realLevel) > 0)
		{
			saveProgress();
			selectCurrentStep();
		}
	}

	/**
	 * A real skill level by name, or 0 when the guide names one this client
	 * does not have -- which only happens if the data was built against a
	 * newer RuneLite.
	 */
	private int realLevel(String skillName)
	{
		try
		{
			return client.getRealSkillLevel(Skill.valueOf(skillName));
		}
		catch (IllegalArgumentException e)
		{
			log.debug("unknown skill {}", skillName);
			return 0;
		}
	}

	/**
	 * Tick diary steps whose bit the game has set.
	 *
	 * <p>Safe from anywhere, including inside a running script: reading a
	 * VarPlayer is an array lookup.
	 */
	private void syncDiaryCompletion()
	{
		if (!config.autoTickDiaries() || guide == null
			|| client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		if (AutoTick.diaries(guide, progress, client::getVarpValue) > 0)
		{
			saveProgress();
			selectCurrentStep();
		}
	}

	private Quest questFor(String constant)
	{
		try
		{
			return Quest.valueOf(constant);
		}
		catch (IllegalArgumentException e)
		{
			// The data was generated against a newer RuneLite than this build.
			// Not an error worth bothering the player about.
			log.debug("unknown quest constant {}", constant);
			return null;
		}
	}

	// --- events ------------------------------------------------------------

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		sceneTracker.onGameStateChanged(event);
		if (event.getGameState() == GameState.LOADING
			|| event.getGameState() == GameState.HOPPING)
		{
			// The scene is rebuilt, so every cached scan is stale.
			approachTracker.onSceneChanged();
			bankTracker.onSceneChanged();
			pathTracker.clear();
		}
		if (event.getGameState() == GameState.LOGGED_IN)
		{
			clientThread.invokeLater(() ->
			{
				syncQuestCompletion();
				syncDiaryCompletion();
				syncSkillCompletion();
				syncQuestProgress();
			});
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		// Order matters: the path and the arrows both ask the approach tracker
		// where they are really heading.
		approachTracker.update();
		bankTracker.update(sceneTracker.getStep());
		pathTracker.update();
		// Deferred out of onVarbitChanged, which runs inside a client script
		// where Quest.getState is not allowed. A tick's delay is invisible.
		if (questCheckPending)
		{
			questCheckPending = false;
			syncQuestCompletion();
		}
	}

	/** What the player is carrying changed, so the missing list has too. */
	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (guide != null)
		{
			withdrawTracker.update(guide, progress.firstIncompleteStep(guide));
		}
	}

	/** A level went up, which is the only thing that can finish a skill step. */
	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		syncSkillCompletion();
	}

	/**
	 * Diary bits, skill targets and quest vars all flip the moment the work is
	 * done, so waiting for the next login would leave a finished step un-ticked.
	 *
	 * <p>Quest completion is the exception: it is deferred to the next game tick
	 * because this runs inside a client script, where Quest.getState is not
	 * allowed.
	 */
	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		syncDiaryCompletion();
		syncSkillCompletion();
		syncQuestProgress();
		questCheckPending = true;
	}

	@Subscribe
	public void onNpcSpawned(NpcSpawned event)
	{
		sceneTracker.onNpcSpawned(event);
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned event)
	{
		sceneTracker.onNpcDespawned(event);
	}

	@Subscribe
	public void onGameObjectSpawned(GameObjectSpawned event)
	{
		sceneTracker.onGameObjectSpawned(event);
	}

	@Subscribe
	public void onGameObjectDespawned(GameObjectDespawned event)
	{
		sceneTracker.onGameObjectDespawned(event);
	}

	@Subscribe
	public void onWallObjectSpawned(WallObjectSpawned event)
	{
		sceneTracker.onWallObjectSpawned(event);
	}

	@Subscribe
	public void onGroundObjectSpawned(GroundObjectSpawned event)
	{
		sceneTracker.onGroundObjectSpawned(event);
	}

	/**
	 * Drops everything the trackers hold between sessions.
	 *
	 * <p>Each one caches references into the loaded scene -- NPCs, TileObjects,
	 * decoded images, quest baselines -- and a player can toggle the plugin off
	 * and on without the client restarting. Anything not released here is held
	 * for the rest of the session.
	 */
	private void releaseTrackers()
	{
		bankTags.clear();
		withdrawTracker.clear();
		pathTracker.clear();
		approachTracker.clear();
		bankTracker.clear();
		questProgress.clear();
		sectionImages.clear();
		worldMapMarker.clear();
		sceneTracker.clear();
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!B0atyGuideConfig.GROUP.equals(event.getGroup()))
		{
			return;
		}
		if ("registerBankTags".equals(event.getKey()))
		{
			if (config.registerBankTags())
			{
				bankTags.register(guide);
			}
			else
			{
				bankTags.clear();
			}
		}
		if (panel != null)
		{
			panel.refresh(progress.firstIncompleteStep(guide));
		}
	}
}
