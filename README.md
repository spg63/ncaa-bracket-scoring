# NCAA Bracket Scorer

Family bracket pool scorer with traditional + custom seed-weighted scoring.

---

## Setup

Requires **JDK 17+** and **Gradle** (or use the included wrapper).

```bash
cd ncaa-bracket-scorer
./gradlew shadowJar          # builds a fat jar at build/libs/ncaa-bracket-scorer.jar
java -jar build/libs/ncaa-bracket-scorer.jar
```

Or run directly:
```bash
./gradlew run
```

---

## First Run Workflow

1. **Launch the app** → it will fetch the current tournament bracket from ESPN.
2. **Enter brackets** (option 1) for each of your 6 players.
   - The app shows each matchup and you press `1` or `2`.
   - Rounds 2–6 show matchups derived from your earlier picks.
   - Picks save to `brackets/<PlayerName>.json` after completion.
3. **Score** (option 2) anytime to see the leaderboard.
4. **Refresh** (option 5) before scoring to pull the latest results.

Brackets are saved locally and reused on every subsequent run — you only need to enter each person's bracket once.

---

## Player Names

Edit the `DEFAULT_PLAYERS` list at the top of `Main.kt` to match your family's names before building.

---

## Scoring

### Traditional (ESPN/CBS style)
Correct pick = flat points for that round:

| Round         | Points |
|---------------|--------|
| Round of 64   | 10     |
| Round of 32   | 20     |
| Sweet 16      | 40     |
| Elite 8       | 80     |
| Final Four    | 160    |
| Championship  | 320    |

### Custom (seed × multiplier)
Correct pick = **winner's seed × round multiplier**. Multipliers double each round:

| Round         | Multiplier | Example (12-seed correct) |
|---------------|------------|---------------------------|
| Round of 64   | ×1         | 12 pts                    |
| Round of 32   | ×2         | 24 pts                    |
| Sweet 16      | ×4         | 48 pts                    |
| Elite 8       | ×8         | 96 pts                    |
| Final Four    | ×16        | 192 pts                   |
| Championship  | ×32        | 384 pts                   |

Upsets are heavily rewarded — a correct 15-seed R64 pick outscores a correct 1-seed R64 pick 15:1.

---

## Files

```
ncaa-bracket-scorer/
├── brackets/              ← player bracket JSONs (auto-created)
│   ├── Sean.json
│   └── ...
├── .cache/
│   └── tournament_structure.json   ← cached ESPN data
└── src/main/kotlin/com/bracket/
    ├── Main.kt            ← main menu
    ├── models/Models.kt   ← data classes
    ├── entry/BracketEntry.kt  ← CLI entry flow
    ├── scoring/Scorer.kt  ← scoring engine
    ├── results/ResultsFetcher.kt  ← ESPN/NCAA API
    └── storage/Storage.kt ← JSON persistence
```

---

## If the ESPN API Breaks

ESPN's unofficial API endpoints change periodically. If you see errors fetching data:

1. Open `ResultsFetcher.kt`
2. The expected JSON format is documented in comments above `parseESPNTournament()` and `parseNCAABracket()`
3. Use your browser's devtools on `https://www.espn.com/mens-college-basketball/tournament/bracket` to inspect network requests and find the bracket data URL
4. Update the URL in `tryFetchESPN()` or `tryFetchNCAA()` accordingly
5. Adjust the JSON parser to match the new response format

The scoring engine and bracket entry are completely decoupled from the fetcher — only the `ResultsFetcher.kt` needs touching if the API changes.

---

## Data Format

Bracket picks are stored as simple JSON:

```json
{
  "playerName": "Sean",
  "year": 2026,
  "picks": [
    {
      "gameId": "R64_G1",
      "round": "ROUND_64",
      "pickedTeamName": "Duke Blue Devils",
      "pickedSeed": 1
    }
  ],
  "enteredAt": "2026-03-19T10:30:00"
}
```
