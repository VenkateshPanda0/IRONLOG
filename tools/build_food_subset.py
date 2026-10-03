"""Create a CSV subset from a user-provided USDA SR Legacy JSON download.

This script does not download data. Run it only after placing the USDA-provided
JSON file locally. It writes source values without estimating missing macros.
"""
import argparse
import csv
import json
from pathlib import Path

NUTRIENTS = {"Energy": "kcal", "Protein": "protein", "Carbohydrate, by difference": "carbs", "Total lipid (fat)": "fat", "Fiber, total dietary": "fiber"}

def rows(source: Path):
    data = json.loads(source.read_text(encoding="utf-8"))
    foods = data.get("FoundationFoods", data.get("SRLegacyFoods", data if isinstance(data, list) else []))
    for food in foods:
        found = {}
        for item in food.get("foodNutrients", []):
            nutrient = item.get("nutrient", {})
            name = nutrient.get("name") or item.get("name")
            key = NUTRIENTS.get(name)
            if key and item.get("amount") is not None:
                found[key] = item["amount"]
        if not all(key in found for key in ("kcal", "protein", "carbs", "fat")):
            continue
        yield [food.get("description", ""), "", found["kcal"], found["protein"], found["carbs"], found["fat"], found.get("fiber", ""), food.get("fdcId", "")]

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("source", type=Path)
    parser.add_argument("destination", type=Path)
    args = parser.parse_args()
    with args.destination.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.writer(handle)
        writer.writerow(["name", "brand", "kcal", "protein", "carbs", "fat", "fiber", "sourceRef"])
        writer.writerows(rows(args.source))

if __name__ == "__main__":
    main()
