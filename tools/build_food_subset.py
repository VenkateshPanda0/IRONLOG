"""Create a sourced USDA SR Legacy food subset and portion table from JSON."""

import argparse
import csv
import json
import re
from pathlib import Path

MACROS = {
    "Protein": "protein",
    "Carbohydrate, by difference": "carbs",
    "Total lipid (fat)": "fat",
    "Fiber, total dietary": "fiber",
}
COMMON = re.compile(
    r"\b(apple|applesauce|apricot|avocado|banana|barley|bean|beef|berry|bread|"
    r"broccoli|cabbage|carrot|cashew|cauliflower|celery|cheese|chicken|chickpea|"
    r"chili|chocolate|cod|cottage cheese|corn|cracker|cream|cucumber|egg|eggplant|"
    r"fish|flour|grape|green bean|ham|honey|juice|kale|lentil|lettuce|milk|mushroom|"
    r"oat|oil|olive|onion|orange|pasta|peach|peanut|pear|pea|pepper|pork|potato|"
    r"pumpkin|quinoa|rice|salmon|sausage|shrimp|spinach|squash|strawberry|sweet potato|"
    r"tofu|tomato|tuna|turkey|walnut|watermelon|yogurt)\b",
    re.IGNORECASE,
)


def nutrient_values(food: dict) -> dict[str, float] | None:
    values: dict[str, float] = {}
    for item in food.get("foodNutrients", []):
        nutrient = item.get("nutrient", {})
        name = nutrient.get("name")
        amount = item.get("amount")
        if name == "Energy" and nutrient.get("unitName", "").lower() == "kcal":
            values["kcal"] = amount
        elif name in MACROS:
            values[MACROS[name]] = amount
    required = ("kcal", "protein", "carbs", "fat")
    if any(values.get(key) is None for key in required):
        return None
    if any(not isinstance(values[key], (int, float)) or values[key] < 0 for key in required):
        return None
    return values


def food_rows(source: Path, limit: int):
    data = json.loads(source.read_text(encoding="utf-8"))
    foods = data.get("SRLegacyFoods", data if isinstance(data, list) else [])
    candidates = []
    for food in foods:
        values = nutrient_values(food)
        if values is None:
            continue
        name = food.get("description", "").strip()
        if not name:
            continue
        score = len(COMMON.findall(name))
        score += 1 if re.search(r"\b(cooked|boiled|baked|roasted|steamed)\b", name, re.I) else 0
        candidates.append((score, name.casefold(), food, values))
    candidates.sort(key=lambda row: (-row[0], row[1], row[2].get("fdcId", 0)))
    selected = [row for row in candidates if row[0] > 0][:limit]
    if len(selected) < min(600, limit):
        selected = candidates[: min(limit, len(candidates))]
    return selected


def write_subset(source: Path, food_path: Path, serving_path: Path, limit: int) -> tuple[int, int]:
    selected = food_rows(source, limit)
    food_path.parent.mkdir(parents=True, exist_ok=True)
    serving_path.parent.mkdir(parents=True, exist_ok=True)
    servings = 0
    with food_path.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.writer(handle)
        writer.writerow(["name", "brand", "kcal", "protein", "carbs", "fat", "fiber", "sourceRef"])
        for _, name_key, food, values in selected:
            writer.writerow([
                food["description"], "", values["kcal"], values["protein"], values["carbs"],
                values["fat"], values.get("fiber", ""), food.get("fdcId", ""),
            ])
    with serving_path.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.writer(handle)
        writer.writerow(["sourceRef", "label", "grams"])
        selected_ids = {str(food.get("fdcId", "")) for _, _, food, _ in selected}
        for _, _, food, _ in selected:
            ref = str(food.get("fdcId", ""))
            for portion in food.get("foodPortions", []):
                grams = portion.get("gramWeight")
                label = portion.get("modifier") or portion.get("portionDescription") or ""
                if ref in selected_ids and label and isinstance(grams, (int, float)) and grams > 0:
                    writer.writerow([ref, label, grams])
                    servings += 1
    return len(selected), servings


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("source", type=Path, help="USDA SR Legacy JSON file")
    parser.add_argument("foods", type=Path, help="Output foods.csv")
    parser.add_argument("servings", type=Path, help="Output food_servings.csv")
    parser.add_argument("--limit", type=int, default=1200)
    args = parser.parse_args()
    count, serving_count = write_subset(args.source, args.foods, args.servings, args.limit)
    print(f"Wrote {count} USDA foods and {serving_count} portions")
    if count < 600:
        raise SystemExit("Fewer than 600 complete-macro foods were available; no values were fabricated.")


if __name__ == "__main__":
    main()
