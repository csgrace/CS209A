package practice.lab4;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;


public class CityAnalysis {
    public static class City
    {
        private String name;
        private String state;
        private int population;

        public City(String name, String state, int population)
        {
            this.name = name;
            this.state = state;
            this.population = population;
        }

        public String getName()
        {
            return name;
        }

        public String getState()
        {
            return state;
        }
        public int getPopulation() {
            return population;
        }

        @Override
        public String toString() {
            return "City{name='"+ name + "',state='" + state + "',population= " + population + "}";
        }

    }

    public static Stream<City> readCities(String filename) throws IOException
    {
        return Files.lines(Paths.get(filename))
                .map(l -> l.split(", "))
                .map(a -> new City(a[0], a[1], Integer.parseInt(a[2])));
    }

    public static void main(String[] args) throws IOException {

        Stream<City> cities = readCities("D:\\E\\SUSTECH\\grade3\\3_up\\CS\\CS209A\\lab\\lab4\\lab4\\src\\cities.txt");
        // Q1: count how many cities there are for each state
        // TODO: Map<String, Long> cityCountPerState = ...
        Map<String, Long> cityCountPerState = cities.collect(
                Collectors.groupingBy(
                        City::getState,
                        Collectors.counting()
                )
        );
        System.out.println("Q1: # of cities per state: ");
        System.out.println(cityCountPerState);

        // Q2: count the total population for each state
        // TODO: Map<String, Integer> statePopulation = ...

        cities = readCities("D:\\E\\SUSTECH\\grade3\\3_up\\CS\\CS209A\\lab\\lab4\\lab4\\src\\cities.txt");
        Map<String, Integer> statePopulation = cities.collect(
                Collectors.groupingBy(
                        City::getState,
                        Collectors.summingInt(City::getPopulation)
                )
        );
        System.out.println("\nQ2: population per state: ");
        System.out.println(statePopulation);

        // Q3: for each state, get the city with the longest name
        // TODO: Map<String, String> longestCityNameByState = ...
        cities = readCities("D:\\E\\SUSTECH\\grade3\\3_up\\CS\\CS209A\\lab\\lab4\\lab4\\src\\cities.txt");
        Map<String, String> longestCityNameByState = cities.collect(
                Collectors.groupingBy(
                        City::getState,
                        Collectors.collectingAndThen(
                                Collectors.maxBy(Comparator.comparingInt(c -> c.getName().length())),
                                optCity -> optCity.map(City::getName).orElse(null)
                        )
                )
        );
        System.out.println("\nQ3: longest city per state: ");
        System.out.println(longestCityNameByState);


        // Q4: for each state, get the set of cities with >500,000 population, get top 3 states with largest number of such cities
        // TODO: Map<String, Set<City>> largeCitiesByState = ...
        cities = readCities("D:\\E\\SUSTECH\\grade3\\3_up\\CS\\CS209A\\lab\\lab4\\lab4\\src\\cities.txt");
        Map<String, Set<City>> largeCitiesByState = cities
                .filter(c -> c.getPopulation() > 500_000)
                .collect(Collectors.groupingBy(
                        City::getState,
                        Collectors.toSet()
                ));

        Map<String, Set<City>> top3LargeCities = largeCitiesByState.entrySet().stream()
                .sorted((e1, e2) -> Integer.compare(e2.getValue().size(), e1.getValue().size()))
                .limit(3)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (a, b) -> a,
                        LinkedHashMap::new
                ));
        System.out.println("\nQ4: cities with >500,000 population for each state (top 3 entries with the largest size of set):");
        for (Map.Entry<String, Set<City>> entry : top3LargeCities.entrySet()) {
            System.out.println(entry.getKey() + ": " + entry.getValue());
        }


        // Q5: for each state, get average, min, max population; show top 5 states sorted by state name
        // TODO: Map<String, List<Double>> top5ByState = ...
        cities = readCities("D:\\E\\SUSTECH\\grade3\\3_up\\CS\\CS209A\\lab\\lab4\\lab4\\src\\cities.txt");
        Map<String, List<Double>> top5ByState = cities.collect(
                Collectors.groupingBy(
                        City::getState,
                        Collectors.collectingAndThen(
                                Collectors.summarizingInt(City::getPopulation),
                                stats -> Arrays.asList(stats.getAverage(), (double)stats.getMin(), (double)stats.getMax())
                        )
                )
        );

        Map<String, List<Double>> top5Sorted = top5ByState.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .limit(5)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (a, b) -> a,
                        LinkedHashMap::new
                ));
        System.out.println("\nQ5: population statistics for pop 5 states (sorted by state name) :");
        for (Map.Entry<String, List<Double>> entry : top5Sorted.entrySet()) {
            List<Double> popStats = entry.getValue();
            System.out.printf("%s: avg=%.2f, min=%.0f, max=%.0f\n", entry.getKey(), popStats.get(0), popStats.get(1), popStats.get(2));
        }
    }
}