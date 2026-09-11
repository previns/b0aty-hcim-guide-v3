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
