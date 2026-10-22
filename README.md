# Codex Nomad

A RuneLite plugin for ultimate ironmen. A UIM has no bank, so Nomad keeps the books a bank would: everything you carry and everywhere you have stashed things, how long each deathpile has left, what to fetch before a trip and where to fetch it, where to note and unnote, and guards against the misclicks that cost a UIM the most.

<p align="center">
  <img src="docs/screenshots/pack.png" width="225" alt="The Pack tab">
  <img src="docs/screenshots/stores.png" width="225" alt="The Stores tab">
  <img src="docs/screenshots/trips.png" width="225" alt="The Trips tab">
  <img src="docs/screenshots/route.png" width="225" alt="The Route tab">
</p>

## What it does

- **Pack tab.** What you carry: inventory, worn items, looting bag and rune pouch, with free slots, worth, and the three (or four with Protect Item) items you would keep if you died now. Items you have not used for a while (*Dead weight after*, 3 hours by default) are marked so you know what could go into storage.
- **Stores tab.** Every place you keep things: deathpiles with their timers, your deathbank, the costume room in your house, STASH units, the seed vault and anything else you put away. Each store remembers what it holds, when it was last seen, and whether the count is exact or estimated.
- **Deathpiles that tell you when.** A deathpile lasts 60 minutes of logged-in time. Nomad counts it down, warns you in chat when it is close to gone (*Deathpile warning*, 10 minutes by default), and marks the tile on the world map.
- **Deaths, judged.** When you die, Nomad works out where your items went: kept, a deathpile on the floor, or a deathbank behind a boss (Zulrah, Vorkath, Hydra, Nightmare, Nex, the Volcanic Mine and more), and files them there. The deathbank count becomes exact as soon as you open the retrieval window.
- **Trips tab.** Save what you are wearing and carrying as a trip. When you start it, Nomad shows what you still need and the stops to fetch it from, nearest store first, plus what you carry that the trip does not need. The next stop can be marked on the ground (*Mark the next stop*).
- **Notes and unnotes.** For any item, where to note or unnote it near you: the tool leprechaun, Piles, Phials, the Pollnivneach banknote merchant, Tiles, the Elder Chaos druid, or your house servant, with the cost.
- **STASH reminders.** Emote clue items you carry are matched to the STASH units they belong in, so you can put them away and stop carrying them.
- **Guards.** Hides *Destroy* on the looting bag outside the Wilderness (*Looting bag Destroy*), and *Sell* on items worth more than a set amount in shops (*Shop Sell*, *Protect items over* 50,000 gp).
- **Peril plaque.** A small plaque in the game view with your free slots, the value at risk and the most urgent deathpile (*Peril plaque*).
- **Route tab.** The early steps of the OSRS Wiki's UIM walkthrough to Prifddinas, ticked off as you finish the quests, reach the levels and hold the items each step needs. The current step can also be shown on the plaque (*Route step on plaque*). All thirteen sections are in, from a fresh account to Song of the Elves: over 300 steps.

## Settings

- **Dead weight after** (3 hours): how long an item goes unused before it is dead weight.
- **Deathpile warning** (10 minutes): when to warn about a deathpile.
- **Share with Codex** (on): share what you hold with other Codex plugins.
- **Looting bag Destroy** and **Shop Sell** (on), **Protect items over** (50,000 gp): the guards.
- **Peril plaque**, **Mark the next stop**, **Route step on plaque** (on): what shows in the game view.

## With other Codex plugins

Nomad writes what you hold everywhere to `codexnomad/holdingsV1`, in the same shape as Codex Bank Steward's `codexbanksteward/bankV1`. On an ultimate ironman with *Share with Codex* on, it also writes `bankV1` itself (only if that entry is missing or was written by Nomad), so Codex Ironworks, Quartermaster and the rest treat your stores as your bank.

## How it works

The inventory, worn items, looting bag, seed vault, costume room and the deathbank retrieval window are read as they change, and the rune pouch from its varbits. Deaths come from `ActorDeath`, STASH deposits and the item retrieval service from chat messages, and the account type from the ironman varbit. Worth comes from RuneLite's item manager. Everything is stored per account in the RuneLite profile config. A damaged save never stops the panel: whatever still reads is kept.

It is guidance only. It does not automate any input, send any data off the client, or read or write files. The only menu changes are the two guards, which remove an option and never add or swap one.

## Credits

- STASH unit and costume room data is adapted from [Dude, Where's My Stuff](https://github.com/Thource/dude-wheres-my-stuff) (BSD 2-Clause).
- The route follows the OSRS Wiki's [Ultimate Ironman Walkthrough Guide To Prifddinas](https://oldschool.runescape.wiki/w/Guide:Ultimate_Ironman_Walkthrough_Guide_To_Prifddinas), paraphrased, under [CC BY-NC-SA 3.0](https://creativecommons.org/licenses/by-nc-sa/3.0/).

## Building

```
./gradlew build
```
