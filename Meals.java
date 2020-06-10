import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

public class Meals {

    public static class Ingredient {
        public final String name;
        public final double amount;
        public final String unit;

        public Ingredient(String name, double amount, String unit) {
            this.name = name;
            this.amount = amount;
            this.unit = unit;
        }

        @Override
        public String toString() {
            String value = String.format("%.1f", amount);
            if (value.endsWith(".0"))
                value = value.substring(0, value.length() - 2);
            return value + " " + unit + " " + name;
        }

        public static Ingredient parse(String line) {
            List<String> parts = Arrays.asList(line.substring(2).split(" "));
            double amount;
            try {
                String raw = parts.get(0).replaceAll("[a-zA-Z/-]", "");
                boolean half = raw.contains("1/2");
                boolean quarter = raw.contains("1/4");
                String fractionless = raw.replaceAll("1/2", "").replaceAll("1/4", "");
                amount = (fractionless.isEmpty() ? (half || quarter ? 0 : 1) : Double.parseDouble(fractionless)) +
                        (half ? 0.5 : 0) +
                        (quarter ? 0.25 : 0);
            } catch (Exception e) {
                throw new RuntimeException("Couldn't parse ingredient: " + line, e);
            }
            String adjacentUnit = parts.get(0).replaceAll("[0-9\\.]", "");
            String unit = adjacentUnit.isEmpty() ? parts.get(1) : adjacentUnit;
            String name = (adjacentUnit.isEmpty() ? parts.stream().skip(2) : parts.stream().skip(1)).collect(Collectors.joining(" "));
            return new Ingredient(name, amount, unit);
        }
    }
    
    public static class Recipe {
        public final Path source;
        public final String name;
        public final int servings;
        public final List<Ingredient> ingredients;
        public final List<String> steps;
        public final List<String> tags;

        public Recipe(Path source, String name, int servings, List<Ingredient> ingredients, List<String> steps, List<String> tags) {
            this.source = source;
            this.name = name.trim();
            this.servings = servings;
            this.ingredients = ingredients;
            this.steps = steps;
            this.tags = tags;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public static Recipe parse(File in) {
        try {
            List<String> lines = Files.readAllLines(in.toPath())
                    .stream()
                    .map(String::trim)
                    .collect(Collectors.toList());
            String name = lines.get(0).trim()
                    .replaceFirst("title:", "")
                    .replaceFirst("#", "");

            List<String> tags = lines.stream()
                    .filter(x -> x.startsWith("tags:"))
                    .findFirst()
                    .map(x -> Arrays.stream(x.substring(5).trim()
                            .split(","))
                            .map(String::trim)
                            .collect(Collectors.toList()))
                    .orElse(Collections.emptyList());

            int servings = lines.stream()
                    .filter(x -> x.contains("Servings:"))
                    .map(x -> x.substring(x.indexOf(" ", x.indexOf(":"))).trim())
                    .map(Integer::parseInt)
                    .findFirst()
                    .get();

            int ingredientsStart = lines.indexOf("### Ingredients") + 1;
            int empty = (int) lines.stream().skip(ingredientsStart).takeWhile(String::isEmpty).count();

            List<Ingredient> ingredients = lines.stream().skip(ingredientsStart + empty)
                    .takeWhile(x -> !x.isEmpty())
                    .map(Ingredient::parse)
                    .collect(Collectors.toList());
            int methodStart = lines.indexOf("### Method") + 1;
            List<String> steps = lines.subList(methodStart, lines.size());

            return new Recipe(in.toPath(), name, servings, ingredients, steps, tags);
        } catch (Exception e) {
            throw new RuntimeException("Error parsing: " + in.getName(), e);
        }
    }

    private static List<Ingredient> combine(List<Ingredient> in) {
        Map<String, List<Ingredient>> grouped = in.stream().collect(Collectors.groupingBy(i -> i.unit + i.name));
        return grouped.values().stream()
                .map(same -> new Ingredient(same.get(0).name, same.stream().mapToDouble(x -> x.amount).sum(), same.get(0).unit))
                .collect(Collectors.toList());
    }

    public static void main(String[] args) {
        if (args.length < 3) {
            System.out.println("Usage: java Meals.java $recipe-dir $days $people $tag1.$tag2...$tagn");
            return;
        }
        Path recipesDir = Paths.get(args[0]);
        int days = Integer.parseInt(args[1]);
        int people = Integer.parseInt(args[2]);
        List<String> tags = args.length < 4 ? Collections.emptyList() : Arrays.asList(args[3].split(","));
        List<Recipe> all = Arrays.stream(recipesDir.toFile().listFiles())
                .filter(f -> f.getName().endsWith(".txt") || f.getName().endsWith(".md"))
                .map(Meals::parse)
                .collect(Collectors.toList());
        List<Recipe> candidates = all.stream()
                .filter(r -> tags.isEmpty() || r.tags.stream().anyMatch(t -> tags.stream().anyMatch(t2 -> t2.equals(t))))
                .collect(Collectors.toList());
        Collections.shuffle(candidates);

        // make shopping list
        int daysSoFar = 0;
        int index = 0;
        List<Ingredient> shoppingList = new ArrayList<>();
        while (index < candidates.size() && daysSoFar < days) {
            Recipe current = candidates.get(index);
            daysSoFar += (current.servings / people);
            index++;
            shoppingList.addAll(current.ingredients);
            // if we don't have enough recipes, duplicate
            if (index == candidates.size())
                candidates.addAll(candidates);
        }

        System.out.println("Meal plan:");
        System.out.println(candidates.subList(0, index).stream()
                .map(r -> r.name)
                .collect(Collectors.joining("\n")));
        System.out.println();
        System.out.println("Shopping list: ");
        System.out.println(combine(shoppingList)
                .stream()
                .map(Object::toString)
                .collect(Collectors.joining("\n")));
    }
}
