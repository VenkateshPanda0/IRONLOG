#!/usr/bin/env python3
"""Build the extra bundled food tables: Indian dishes, Indian packaged products, world dishes.

Outputs (app/src/main/assets/seed/):
  foods_indian.csv / servings_indian.csv       Indian Nutrient Databank (INDB) recipes
  foods_packaged_in.csv / servings_packaged_in.csv   Open Food Facts products of major Indian brands
  foods_world.csv / servings_world.csv         USDA FNDDS survey foods (mixed dishes, many cuisines)

Columns: name,brand,kcal,protein,carbs,fat,fiber,sourceRef,source,cuisine,popularity (per 100 g).
Values are copied from the sources; nothing is estimated. Rows missing any macro are skipped.

Usage:
  build_food_bundles.py indb   path/to/INDB.xlsx
  build_food_bundles.py fndds  path/to/surveyDownload.json
  build_food_bundles.py off-in            (network: search.openfoodfacts.org, ~1 request/second)
"""
import csv
import json
import re
import sys
import time
import urllib.parse
import urllib.request
from pathlib import Path

OUT = Path(__file__).resolve().parent.parent / "app/src/main/assets/seed"
HEADER = ["name", "brand", "kcal", "protein", "carbs", "fat", "fiber", "sourceRef", "source", "cuisine", "popularity"]


def write(stem: str, foods: list[list], servings: list[list]) -> None:
    with (OUT / f"foods_{stem}.csv").open("w", newline="", encoding="utf-8") as f:
        w = csv.writer(f, lineterminator="\n")
        w.writerow(HEADER)
        w.writerows(foods)
    with (OUT / f"servings_{stem}.csv").open("w", newline="", encoding="utf-8") as f:
        w = csv.writer(f, lineterminator="\n")
        w.writerow(["sourceRef", "label", "grams"])
        w.writerows(servings)
    print(f"{stem}: {len(foods)} foods, {len(servings)} servings")


def r(x: float, digits: int = 2) -> float:
    return round(float(x), digits)


# Upper bounds for one serving by unit. Some INDB servings inherit whole-recipe yields (e.g. "1 piece"
# of gulab jamun = 302 g); those are dropped rather than corrected, so the food is logged in grams.
SERVING_LIMITS = [
    (r"tablespoon|tbsp", 30), (r"teaspoon|tsp", 10),
    (r"tea cup|cup|glass|tall glass|mug", 450),
    (r"bowl|plate|dish|soup", 600),
]


def plausible_serving(label: str, grams: float) -> bool:
    lower = label.lower()
    for pattern, limit in SERVING_LIMITS:
        if re.search(pattern, lower):
            return grams <= limit
    return grams <= 250  # single items: piece, chapati, idli, samosa, ladoo, slice...


def indb(path: str) -> None:
    import openpyxl

    rows = list(openpyxl.load_workbook(path, read_only=True).worksheets[0].iter_rows(values_only=True))
    head = {k: i for i, k in enumerate(rows[0])}
    foods, servings = [], []
    for row in rows[1:]:
        get = lambda k: row[head[k]]
        values = [get(k) for k in ("energy_kcal", "protein_g", "carb_g", "fat_g")]
        if any(not isinstance(v, (int, float)) for v in values) or not get("food_name"):
            continue
        ref = "INDB:" + get("food_code")
        fiber = get("fibre_g")
        foods.append([get("food_name").strip(), "", r(values[0], 1), r(values[1]), r(values[2]), r(values[3]),
                      r(fiber) if isinstance(fiber, (int, float)) else "", ref, "INDB", "Indian", 0])
        # Serving grams follow from per-serving energy over per-100 g energy.
        unit = (get("servings_unit") or "").strip()
        per_serving = get("unit_serving_energy_kcal")
        if unit and isinstance(per_serving, (int, float)) and values[0] > 0:
            grams = per_serving / values[0] * 100
            if 1 <= grams and plausible_serving(unit, grams):
                servings.append([ref, f"1 {unit}", r(grams, 1)])
    write("indian", foods, servings)


WORLD = [
    ("Mexican", r"burrito|taco|enchilada|quesadilla|tamale|nacho|fajita|chimichanga|tostada|salsa|guacamole|refried"),
    ("Italian", r"pizza|lasagna|spaghetti|ravioli|risotto|fettuccine|alfredo|lasagne|manicotti|calzone|gnocchi|tortellini|parmigiana|marinara|pesto|minestrone"),
    ("Chinese", r"chow mein|lo mein|fried rice|sweet and sour|kung pao|egg roll|wonton|general tso|moo goo|chop suey|orange chicken|szechuan|dim sum"),
    ("Japanese", r"sushi|ramen|teriyaki|tempura|miso|udon|soba|sashimi|yakitori"),
    ("Thai", r"pad thai|thai|satay|tom yum"),
    ("Indian", r"curry|biryani|naan|samosa|tikka|masala|dal\b|paneer|chapati|roti"),
    ("Middle Eastern", r"hummus|falafel|shawarma|gyro|kebab|kabob|tabbouleh|baba ghanoush|pita"),
    ("Korean", r"kimchi|bulgogi|bibimbap"),
    ("Vietnamese", r"\bpho\b|banh mi|spring roll"),
    ("Spanish", r"paella|gazpacho|churro"),
    ("American", r"burger|hamburger|cheeseburger|hot dog|sandwich|fries|mac and cheese|macaroni and cheese|pancake|waffle|bagel|muffin|brownie|cookie|donut|doughnut|barbecue|bbq|chicken nuggets|meatloaf"),
]


def cuisine_for(name: str) -> str:
    lower = name.lower()
    for cuisine, pattern in WORLD:
        if re.search(pattern, lower):
            return cuisine
    return "World"


def fndds(path: str) -> None:
    foods_json = json.load(open(path, encoding="utf-8"))["SurveyFoods"]
    wanted = {"Energy": "kcal", "Protein": "protein", "Carbohydrate, by difference": "carbs", "Total lipid (fat)": "fat", "Fiber, total dietary": "fiber"}
    foods, servings = [], []
    for food in foods_json:
        values = {}
        for n in food.get("foodNutrients", []):
            name = n.get("nutrient", {}).get("name")
            unit = n.get("nutrient", {}).get("unitName", "").lower()
            if name in wanted and (name != "Energy" or unit == "kcal") and isinstance(n.get("amount"), (int, float)):
                values[wanted[name]] = n["amount"]
        if any(k not in values for k in ("kcal", "protein", "carbs", "fat")):
            continue
        ref = f"FNDDS:{food['fdcId']}"
        name = food["description"].strip()
        foods.append([name, "", r(values["kcal"], 1), r(values["protein"]), r(values["carbs"]), r(values["fat"]),
                      r(values["fiber"]) if "fiber" in values else "", ref, "FNDDS", cuisine_for(name), 0])
        for p in food.get("foodPortions", []):
            label, grams = (p.get("portionDescription") or "").strip(), p.get("gramWeight")
            if label and label.lower() != "quantity not specified" and isinstance(grams, (int, float)) and 0 < grams <= 2000:
                servings.append([ref, label, r(grams, 1)])
    write("world", foods, servings)


# Major Indian food brands (Open Food Facts brand tags). Global brands are kept only for products
# sold in India.
INDIAN_BRANDS = [
    "amul", "haldiram-s", "britannia", "parle", "mtr", "mother-dairy", "aashirvaad", "bikaji", "balaji", "bikano",
    "sunfeast", "bingo", "itc", "tata", "tata-sampann", "tata-tea", "fortune", "saffola", "patanjali", "dabur",
    "mdh", "everest", "catch", "kissan", "maggi", "yippee", "top-ramen", "ching-s-secret", "bournvita", "horlicks",
    "complan", "boost", "paper-boat", "epigamia", "nandini", "milky-mist", "gowardhan", "muscleblaze", "yogabar",
    "ritebite", "true-elements", "kellogg-s", "quaker", "mccain", "lays", "kurkure", "uncle-chipps", "cadbury",
    "nestle", "kwality-wall-s", "mapro", "priya", "ashoka", "gits", "id", "id-fresh", "chitale", "lijjat",
]
GLOBAL_BRANDS = {"maggi", "kellogg-s", "quaker", "mccain", "lays", "cadbury", "nestle", "kissan", "tata"}


def consistent(kcal: float, protein: float, carbs: float, fat: float) -> bool:
    """Crowd-sourced labels sometimes contradict themselves; require 4/4/9 energy within 35%."""
    if kcal < 50:
        return True
    return abs(protein * 4 + carbs * 4 + fat * 9 - kcal) / kcal <= 0.35


def clean_packaged() -> None:
    """Re-applies the consistency filter to an existing foods_packaged_in.csv without refetching."""
    path = OUT / "foods_packaged_in.csv"
    rows = list(csv.reader(path.open(encoding="utf-8")))
    kept = [rows[0]] + [r for r in rows[1:] if consistent(*(float(x) for x in r[2:6]))]
    with path.open("w", newline="", encoding="utf-8") as f:
        csv.writer(f, lineterminator="\n").writerows(kept)
    print(f"packaged_in: kept {len(kept) - 1} of {len(rows) - 1}")


def off_request(query: str, page: int) -> dict:
    url = "https://search.openfoodfacts.org/search?" + urllib.parse.urlencode({
        "q": query,
        "page": page,
        "page_size": 100,
        "fields": "code,product_name,product_name_en,brands,nutriments,countries_tags,serving_size,serving_quantity,unique_scans_n",
    })
    req = urllib.request.Request(url, headers={"User-Agent": "Ironlog/0.2 (offline seed build; https://github.com/VenkateshPanda0/IRONLOG)"})
    for attempt in range(4):
        try:
            with urllib.request.urlopen(req, timeout=60) as resp:
                return json.load(resp)
        except Exception as error:  # noqa: BLE001 - retried below
            if attempt == 3:
                raise
            time.sleep(2 ** attempt * 2)
    return {}


def off_india() -> None:
    seen, foods, servings = set(), [], []
    for brand in INDIAN_BRANDS:
        page, pages = 1, 1
        while page <= pages and page <= 10:
            data = off_request(f'brands_tags:"{brand}"', page)
            pages = data.get("page_count") or 1
            for hit in data.get("hits", []):
                code = hit.get("code")
                countries = hit.get("countries_tags") or []
                if not code or code in seen:
                    continue
                if brand in GLOBAL_BRANDS and "en:india" not in countries:
                    continue
                n = hit.get("nutriments") or {}
                values = [n.get(k) for k in ("energy-kcal_100g", "proteins_100g", "carbohydrates_100g", "fat_100g")]
                if any(not isinstance(v, (int, float)) or v < 0 for v in values) or values[0] > 950 or not consistent(*values):
                    continue
                name = (hit.get("product_name_en") or hit.get("product_name") or "").strip()
                if not name:
                    continue
                brands = hit.get("brands")
                brand_name = (brands[0] if isinstance(brands, list) and brands else str(brands or "").split(",")[0]).strip()
                ref = f"off:{code}"
                seen.add(code)
                fiber = n.get("fiber_100g")
                foods.append([name, brand_name, r(values[0], 1), r(values[1]), r(values[2]), r(values[3]),
                              r(fiber) if isinstance(fiber, (int, float)) and fiber >= 0 else "", ref, "OFF", "Indian (packaged)",
                              int(hit.get("unique_scans_n") or 0)])
                grams = hit.get("serving_quantity")
                try:
                    grams = float(grams)
                except (TypeError, ValueError):
                    grams = None
                if grams and 0 < grams <= 2000:
                    servings.append([ref, (hit.get("serving_size") or "1 serving").strip()[:60], r(grams, 1)])
            page += 1
            time.sleep(1)
        print(f"  {brand}: {len(foods)} total")
    write("packaged_in", foods, servings)


if __name__ == "__main__":
    command = sys.argv[1] if len(sys.argv) > 1 else ""
    if command == "indb":
        indb(sys.argv[2])
    elif command == "fndds":
        fndds(sys.argv[2])
    elif command == "off-in":
        off_india()
    elif command == "clean-off-in":
        clean_packaged()
    else:
        sys.exit(__doc__)
