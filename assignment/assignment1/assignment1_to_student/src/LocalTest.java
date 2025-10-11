import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@SuppressWarnings({"unchecked"})
public class LocalTest {
  private static Class<?> analyzerClass;
  private static Object analyzerInstance;

  @BeforeAll
  static void setUp() {
    try {
      analyzerClass = Class.forName("OlistAnalyzer");
      checkDeclarations();
      Constructor<?> constructor = analyzerClass.getDeclaredConstructor(String.class);
      if (constructor.getModifiers() != Modifier.PUBLIC) {
        throw new NoSuchMethodException("The constructor from class OlistAnalyzer is not public!");
      }
      analyzerInstance = constructor.newInstance("resources/local_csv");
    } catch (ClassNotFoundException | InvocationTargetException | InstantiationException |
             IllegalAccessException | NoSuchMethodException e) {
      e.printStackTrace();
      fail();
    }
  }

  static void checkDeclarations() {
    MethodEntity[] analyzerMethods = {
        new MethodEntity("topSellingCategories", Map.class),
        new MethodEntity("getPurchasePatternByHour", Map.class),
        new MethodEntity("getPriceRangeDistribution", Map.class),
        new MethodEntity("analyzeSellerPerformance", Map.class),
        new MethodEntity("recommendedProducts", Map.class)
    };
    List<String> errorMessages = new ArrayList<>();
    for (MethodEntity m : analyzerMethods) {
      if (!m.checkForClass(analyzerClass)) {
        errorMessages.add("The method [" + m + "] from class OlistAnalyzer does not exist!");
      }
    }
    assertTrue(errorMessages.isEmpty(), String.join(System.lineSeparator(), errorMessages));
  }

  <K, V> String mapToString(Object obj) {
    Map<K, V> map = (Map<K, V>) obj;
    StringBuilder sb = new StringBuilder();
    for (Map.Entry<K, V> entry : map.entrySet()) {
      sb.append(entry.getKey());
      sb.append(" == ");
      sb.append(entry.getValue());
      sb.append("\n");
    }
    if (sb.length() == 0) return "";
    return sb.substring(0, sb.length() - 1).strip();
  }

  String listToString(Object obj) {
    List<String> list = (List<String>) obj;
    StringBuilder sb = new StringBuilder();
    for (String s : list) {
      sb.append(s);
      sb.append("\n");
    }
    if (sb.length() == 0) return "";
    return sb.substring(0, sb.length() - 1).strip();
  }

  String nestedMapToString(Object obj) {
    Map<String, Map<String, Long>> map = (Map<String, Map<String, Long>>) obj;
    StringBuilder sb = new StringBuilder();
    boolean isFirstOuter = true;

    for (Map.Entry<String, Map<String, Long>> outerEntry : map.entrySet()) {
      if (!isFirstOuter) {
        sb.append("\n");
      }
      isFirstOuter = false;

      sb.append(outerEntry.getKey()).append(":");

      Map<String, Long> innerMap = outerEntry.getValue();
      boolean isFirstInner = true;
      for (Map.Entry<String, Long> innerEntry : innerMap.entrySet()) {
        if (!isFirstInner) {
          sb.append(",");
        }
        isFirstInner = false;

        sb.append(innerEntry.getKey()).append("=").append(innerEntry.getValue());
      }
    }

    return sb.toString().strip();
  }

  String mapListDoubleToString(Object obj) {
    Map<String, List<Double>> map = (Map<String, List<Double>>) obj;
    StringBuilder sb = new StringBuilder();
    for (Map.Entry<String, List<Double>> entry : map.entrySet()) {
      sb.append(entry.getKey()).append(" == ").append(entry.getValue()).append("\n");
    }
    if (sb.length() == 0) return "";
    return sb.substring(0, sb.length() - 1).strip();
  }

  String mapListStringToString(Object obj) {
    Map<String, List<String>> map = (Map<String, List<String>>) obj;
    StringBuilder sb = new StringBuilder();
    for (Map.Entry<String, List<String>> entry : map.entrySet()) {
      sb.append(entry.getKey()).append(" == ").append(entry.getValue()).append("\n");
    }
    if (sb.length() == 0) return "";
    return sb.substring(0, sb.length() - 1).strip();
  }

  @Test
  void testTopSellingCategories() {
    try {
      Method method = analyzerClass.getMethod("topSellingCategories");
      Object res = method.invoke(analyzerInstance);
      assertTrue(res instanceof Map<?, ?>);
      String expected = Files.readString(Paths.get("resources", "local_answer", "Q1.txt"),
              StandardCharsets.UTF_8)
          .replace("\r", "").strip();
      assertEquals(expected, mapToString(res));
    } catch (NoSuchMethodException | InvocationTargetException |
             IllegalAccessException | IOException e) {
      e.printStackTrace();
      fail();
    }
  }

  @Test
  void testGetPurchasePatternByHour() {
    try {
      Method method = analyzerClass.getMethod("getPurchasePatternByHour");
      Object res = method.invoke(analyzerInstance);
      assertTrue(res instanceof Map<?, ?>);
      String expected = Files.readString(Paths.get("resources", "local_answer", "Q2.txt"),
              StandardCharsets.UTF_8)
          .replace("\r", "").strip();
      assertEquals(expected, mapToString(res));
    } catch (NoSuchMethodException | InvocationTargetException |
             IllegalAccessException | IOException e) {
      e.printStackTrace();
      fail();
    }
  }

  @Test
  void testGetPriceRangeDistribution() {
    try {
      Method method = analyzerClass.getMethod("getPriceRangeDistribution");
      Object res = method.invoke(analyzerInstance);
      assertTrue(res instanceof Map<?, ?>);
      String expected = Files.readString(Paths.get("resources", "local_answer", "Q3.txt"),
              StandardCharsets.UTF_8)
          .replace("\r", "").strip();
      assertEquals(expected, nestedMapToString(res));
    } catch (NoSuchMethodException | InvocationTargetException |
             IllegalAccessException | IOException e) {
      e.printStackTrace();
      fail();
    }
  }

  @Test
  void testAnalyzeSellerPerformance() {
    try {
      Method method = analyzerClass.getMethod("analyzeSellerPerformance");
      Object res = method.invoke(analyzerInstance);
      assertTrue(res instanceof Map<?, ?>);
      String expected = Files.readString(Paths.get("resources", "local_answer", "Q4.txt"),
              StandardCharsets.UTF_8)
          .replace("\r", "").strip();
      assertEquals(expected, mapListDoubleToString(res));
    } catch (NoSuchMethodException | InvocationTargetException |
             IllegalAccessException | IOException e) {
      e.printStackTrace();
      fail();
    }
  }

  @Test
  void testRecommendedProducts() {
    try {
      Method method = analyzerClass.getMethod("recommendedProducts");
      Object res = method.invoke(analyzerInstance);
      assertTrue(res instanceof Map<?, ?>);
      String expected = Files.readString(Paths.get("resources", "local_answer", "Q5.txt"),
              StandardCharsets.UTF_8)
          .replace("\r", "").strip();
      assertEquals(expected, mapListStringToString(res));
    } catch (NoSuchMethodException | InvocationTargetException |
             IllegalAccessException | IOException e) {
      e.printStackTrace();
      fail();
    }
  }

  private static class MethodEntity {
    String name;
    Class<?> returnType;
    Class<?>[] parameterTypes;

    MethodEntity(String name, Class<?> returnType, Class<?>... parameterTypes) {
      this.name = name;
      this.returnType = returnType;
      this.parameterTypes = parameterTypes;
    }

    boolean checkForClass(Class<?> clazz) {
      try {
        Method method = clazz.getMethod(name, parameterTypes);
        return method.getReturnType().equals(returnType);
      } catch (NoSuchMethodException e) {
        return false;
      }
    }

    @Override
    public String toString() {
      return name + "(" + Arrays.toString(parameterTypes) + ") : " + returnType;
    }
  }

  private static class Item<K, V> {
    K key;
    V value;

    Item(K key, V value) {
      this.key = key;
      this.value = value;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      Item<?, ?> item = (Item<?, ?>) o;
      return Objects.equals(key, item.key) && Objects.equals(value, item.value);
    }

    @Override
    public int hashCode() {
      return Objects.hash(key, value);
    }
  }
}