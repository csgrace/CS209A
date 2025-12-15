package cs209a.finalproject_demo.object;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SolvabilityComparison {
    private String feature;
    private String metric;
    private double solvableValue;
    private double hardValue;
    private double difference;
    private double differencePercentage;
}