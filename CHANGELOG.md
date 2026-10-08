# 1.3.3 - Quest guidance and stutter fix

- Fix the stutter when scenery loads, worst near the Wilderness Ditch, where an unreachable destination searched the whole scene about a hundred times a tick.
- Follow Quest Helper through steps that depend on what you have already seen or said, which covers Murder Mystery's investigation.
- Stop pinning "Complete <quest>" steps to an NPC or item that shares the quest's name, so Restless Ghost moves on from the ghost.
- Highlight the inventory item a quest step uses, such as the skull for the coffin.
- Highlight both ingredients of an inventory recipe like soft clay, and finish the step once they are combined.
- Follow Elemental Workshop I, The Garden of Death, Alfred Grimhand's Barcrawl, The Path of Glouphrie and three Recipe for Disaster subquests.

# 1.3.2 - Restore bank-number search

- Handle guide bank searches directly so they work without Bank Tags and are not overridden by an active tag tab.
- Accept compact, spaced, and hash-prefixed bank numbers, regardless of letter case.
- Preserve ordinary item searches, unrelated tags, and bank layout slots.

# 1.3.1 ? Performance and travel completion

- Batch variable-driven completion checks once per game tick and preserve the route cache when scenery transforms have not changed.
- Resolve travel completion after destination and transport data, correcting the Grand Tree floor and adding plain teleport and home-teleport steps.
- Keep compound, conditional, quest and diary instructions out of proximity completion.
- Add a sidebar action to request the selected destination from the optional Shortest Path plugin.

# 1.3.0 — Quest puzzles and routing

- Show Quest Helper's puzzle answers instead of its "turn on solutions" message: chest codes, door passwords, the stone order on Death Plateau, which guards to mark in Children of the Sun.
- Solve combination locks on screen, marking each dial's arrow with the number of clicks and the Confirm button once they are all set.
- Highlight quest scenery that Quest Helper locates by tile rather than by id, which covers about a quarter of its object steps.
- Draw the line to the nearest of several possible places — a deposit box, a statue, a captain — instead of waiting for one to come into view.
- Follow Ribbiting Tale of a Lily Pad Dispute, and stop showing one puzzle's instructions on a different puzzle.
- Board the right ship for crossings the step does not name a boat for, such as Entrana and Brimhaven, and stop answering island teleports with a dock.
- Send "Head to Varlamore" to Regulus Cento outside Varrock.

# 1.2.0 — Bug fixes

- Keep guide progress separate for each RuneScape account; import existing shared progress once into the first account logged in.
- Fix progress saves stopping during a session and unwanted sidebar jumps.
- Improve quest dialogue, item and shop highlights, including Tribal Totem's combination lock.
- Show mid-quest errands alongside Quest Helper without interrupting its guidance.
- Correct travel boarding points, charter directions and departure-specific captains.
- Improve quest-branch coverage and keep navigation in sync with the current guide step.

# 1.1.0 — Bug fixes

## Following quests

- Quest steps now highlight what Quest Helper highlights: the NPC to talk to,
  the object to search, the raft to board, the rope to use.
- Quest interfaces are guided too, so screens like the Dwarf Cannon repair
  show you which part to click.
- "Start <quest>" steps tick when you start the quest, instead of waiting for
  something much later in it — and they still tick if you had already started.
- Steps that run "until you have X" now stop on the item itself, so the guide
  hands back exactly where it says it does.
- Training, gathering and diary steps no longer tick themselves because an
  unrelated quest happened to move.

## Getting there

- Better routing through closed doors, gates and fences, with the way in
  highlighted rather than a path that stops at a wall.

## Around the interface

- Bank withdrawals, shop stock and ground items are highlighted again.
- Shopkeepers and quest NPCs are marked, not just named in the step text.

## Under the hood

- The plugin repeats far less work each frame and each tick, which should help
  on lower-end machines.
