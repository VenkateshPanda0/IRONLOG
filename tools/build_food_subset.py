"""Create the bundled USDA SR Legacy food table and portion table from the JSON export.

Every SR Legacy food with all four macro values is included (no keyword subset), so staples
such as oats, eggs, rice and milk are always present. Portion labels keep their amount, e.g.
"1 large" or "0.33 cup". Values are copied as published; nothing is estimated.
"""

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
# Kept for reference: the earlier keyword subset missed staples such as oats and eggs.
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
    selected = []
    for food in foods:
        values = nutrient_values(food)
        name = food.get("description", "").strip()
        if values is None or not name:
            continue
        selected.append((0, name.casefold(), food, values))
    selected.sort(key=lambda row: (row[1], row[2].get("fdcId", 0)))
    return selected[:limit] if limit > 0 else selected


def portion_label(portion: dict) -> str:
    """'1 large', '0.33 cup', or the description when there is no modifier."""
    modifier = (portion.get("modifier") or "").strip()
    description = (portion.get("portionDescription") or "").strip()
    amount = portion.get("amount")
    if modifier:
        return f"{amount:g} {modifier}" if isinstance(amount, (int, float)) and amount > 0 else modifier
    return description


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
                label = portion_label(portion)
                if ref in selected_ids and label and isinstance(grams, (int, float)) and grams > 0:
                    writer.writerow([ref, label, grams])
                    servings += 1
    return len(selected), servings


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("source", type=Path, help="USDA SR Legacy JSON file")
    parser.add_argument("foods", type=Path, help="Output foods.csv")
    parser.add_argument("servings", type=Path, help="Output food_servings.csv")
    parser.add_argument("--limit", type=int, default=0, help="Optional cap; 0 keeps every complete food")
    args = parser.parse_args()
    count, serving_count = write_subset(args.source, args.foods, args.servings, args.limit)
    print(f"Wrote {count} USDA foods and {serving_count} portions")
    if count < 600:
        raise SystemExit("Fewer than 600 complete-macro foods were available; no values were fabricated.")


if __name__ == "__main__":
    main()
