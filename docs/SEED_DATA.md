# Bundled seed data

## Exercises

- Source: [free-exercise-db](https://github.com/yuhonas/free-exercise-db), Unlicense.
- Asset: `app/src/main/assets/seed/exercises.json`.
- Downloaded and validated on 2026-10-03: 876 records, 1,005,327 bytes.
- Exercise names, stable IDs, category, force, level, mechanic, equipment, primary/secondary muscles, instructions, and source image paths are retained.
- Images are not bundled. The source repository documents image paths, but their redistribution terms are not established here. There are no exercise image network requests in the app.
- About attribution: “Exercise data: free-exercise-db by yuhonas, released under the Unlicense.”

To refresh from the upstream source, run `tools/fetch_exercise_seed.ps1`, inspect the source changes, and update the count in this document.

## Generic foods and portions

- Source: [USDA FoodData Central downloadable datasets](https://fdc.nal.usda.gov/download-datasets/), SR Legacy April 2018, public-domain data (CC0 attribution per project brief).
- Downloaded archive: `FoodData_Central_sr_legacy_food_json_2018-04.zip`; it was unpacked outside the repository and was not committed.
- Assets: `app/src/main/assets/seed/foods.csv` and `food_servings.csv`.
- Generated on 2026-10-03: 1,200 foods with all four required macro fields, and 2,271 portion rows. Missing required nutrients are excluded; missing fiber is left empty.
- `tools/build_food_subset.py` selects records by common-food terms and cooked-preparation terms, ranks deterministically, and refuses to output fewer than 600 records. Values are copied from SR Legacy; none are estimated.
- About attribution: “Nutrition data: FoodData Central, U.S. Department of Agriculture.”

## Seeding

`SeedLoader` and `FoodSeedLoader` insert source records inside Room transactions. Seed IDs/source references and `IGNORE` conflict handling make reruns safe. DataStore stores the seed version; the root UI waits for both seed operations before showing the app. Users' custom exercise and food IDs are independent of USDA/exercise source IDs.

Seed counts reflect generated records, not a verified installed database. CSV parser fixtures and a Robolectric loader idempotency test are authored but NOT RUN. Android startup seeding remains unverified until the Android build/runtime gate can execute.
