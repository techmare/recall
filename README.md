# Recall

A modern-looking flashcard app for Android with Anki's brains: the FSRS scheduler and full `.apkg` import.

**Download:** [latest APK](../../releases/latest/download/Recall.apk) — open it on the phone to install or update.

## What it does
- **Imports Anki decks** (`.apkg`) from old and new Anki versions: cards, sub-decks, images, audio, cloze cards and your review progress. Open an `.apkg` from Files or share it to Recall, or use *Import .apkg* in the app.
- **FSRS scheduling** (the algorithm modern Anki uses), with learning steps of 1 and 10 minutes, a daily new-card limit and adjustable desired retention.
- **Review screen** with tap-to-flip, Again / Hard / Good / Easy showing the next interval, undo, and audio playback.
- **Stats**: retention, streak, reviews per day, a 12-week activity heatmap and a 7-day forecast.
- Make your own decks and cards, search, edit and delete.

## Notes
- Days roll over at 4 a.m., like Anki.
- Suspended Anki cards are skipped on import; re-importing the same deck doesn't create duplicates.
- Editing an imported card that has formatting or images keeps only its text.

## Build
`./gradlew assembleRelease` — GitHub Actions builds and publishes a release on every push to `main`.
