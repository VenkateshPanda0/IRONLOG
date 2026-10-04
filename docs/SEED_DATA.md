# Bundled seed data

## Exercises

- Source: [free-exercise-db](https://github.com/yuhonas/free-exercise-db), Unlicense.
- Asset: `app/src/main/assets/seed/exercises.json`.
- Downloaded and validated on 2026-10-03: 876 records, 1,005,327 bytes.
- Exercise names, stable IDs, category, force, level, mechanic, equipment, primary/secondary muscles, instructions, and source image paths are retained.
- Demo images are bundled: `app/src/main/assets/exercises/<id>/0.webp` (start position) and `1.webp` (end position) for 873 of 876 exercises (the three Kettlebell Halo / overhead extension entries have no upstream images). Both free-exercise-db and its source, wrkout/exercises.json, are released into the public domain under the Unlicense, and free-exercise-db's README invites using the images. Frames are scaled to 480 px wide WebP (quality 62), 1,746 files, about 19.8 MB. The app alternates the two frames as a looping movement demo; it makes no image network requests.
- Rebuild or refresh the frames with `python3 tools/build_exercise_media.py` (needs Pillow); it skips files that already exist.
- About attribution: “Exercise data: free-exercise-db by yuhonas, released under the Unlicense.”

To refresh from the upstream source, run `tools/fetch_exercise_seed.ps1`, inspect the source changes, and update the count in this document.

## Generic foods and portions

- Source: [USDA FoodData Central downloadable datasets](https://fdc.nal.usda.gov/download-datasets/), SR Legacy April 2018, public-domain data (CC0 attribution per project brief).
- Downloaded archive: `FoodData_Central_sr_legacy_food_json_2018-04.zip`; it was unpacked outside the repository and was not committed.
- Assets: `app/src/main/assets/seed/foods.csv` and `food_servings.csv`.
- Regenerated on 2026-10-04: all 7,793 SR Legacy foods that have kcal, protein, carbohydrate and fat, with 14,449 portion rows labelled with their amount ("1 large", "0.33 cup"). The earlier 1,200-food keyword subset missed staples such as oats, eggs, white rice, bananas and milk. Missing required nutrients are excluded; missing fiber is left empty.
- `tools/build_food_subset.py` copies values from SR Legacy without estimation (`--limit` optionally caps the count). Seed version 4 replaces bundled portions on existing installs; user-created foods are untouched.
- About attribution: “Nutrition data: FoodData Central, U.S. Department of Agriculture.”

## Seeding

`SeedLoader` and `FoodSeedLoader` insert source records inside Room transactions. Seed IDs/source references and `IGNORE` conflict handling make reruns safe. DataStore stores the seed version; the root UI waits for both seed operations before showing the app. Users' custom exercise and food IDs are independent of USDA/exercise source IDs.

Seed counts reflect generated records, not a verified installed database. CSV parser fixtures and a Robolectric loader idempotency test are authored but NOT RUN. Android startup seeding remains unverified until the Android build/runtime gate can execute.
