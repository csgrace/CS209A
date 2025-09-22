import java.util.*;
import java.util.stream.Collectors;

@FunctionalInterface
public interface MyPredicate<T> {
    boolean test(T t);
}

