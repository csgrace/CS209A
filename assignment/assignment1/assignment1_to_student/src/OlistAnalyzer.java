import java.io.BufferedReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.DoubleSummaryStatistics;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

public class OlistAnalyzer {
  private final Path baseDir;
  private final Path orderItemsCsv;
  private final Map<String, String> productIdToCategoryName = new HashMap<>();
  private final Map<String, String> categoryNameToEn = new HashMap<>();
  private final Map<String, Boolean> orderIdOnTime = new HashMap<>();
  private final Map<String, Double> orderIdToReviewSum = new HashMap<>();
  private final Map<String, Integer> orderIdToReviewCount = new HashMap<>();
  private final Map<String, Long> sellerTotalSalesCents = new HashMap<>();
  private final Map<String, Set<String>> sellerOrders = new HashMap<>();
  private final Map<String, Set<String>> sellerProducts = new HashMap<>();
  private final Map<String, Long> productSalesCount = new HashMap<>();
  private final Map<String, Set<String>> productOrders = new HashMap<>();
  private final Map<String, Long> productFirstPriceCents = new HashMap<>();
  private final Map<String, Long> productTotalPriceCents = new HashMap<>();

  public OlistAnalyzer(String datasetFolderPath) {
    this.baseDir = Paths.get(datasetFolderPath);

    this.orderItemsCsv = baseDir.resolve("olist_order_items_dataset.csv");
    readProducts(baseDir.resolve("olist_products_dataset.csv"));
    readCategoryTranslation(baseDir.resolve("product_category_name_translation.csv"));

    readOrders(orderItemsCsv);
    readDeliveries(baseDir.resolve("olist_orders_dataset.csv"));
    readReviews(baseDir.resolve("olist_order_reviews_dataset.csv"));
  }

  public Map<String, Integer> topSellingCategories() {
    Map<String, Long> categoryCount = new HashMap<>();
    try (BufferedReader br = Files.newBufferedReader(orderItemsCsv, StandardCharsets.UTF_8)) {
      String headerLine = br.readLine();
      if (headerLine == null) return Collections.emptyMap();
      String[] header = splitCsv(headerLine);

      int productIdIndex = indexOf(header, "product_id");
      if (productIdIndex < 0) return Collections.emptyMap();

      categoryCount = br.lines()
              .filter(line -> !line.isEmpty())
              .map(this::splitCsv)
              .filter(cells -> cells.length > productIdIndex)
              .map(cells -> cells[productIdIndex])
              .map(productIdToCategoryName::get)
              .filter(catPt -> !isBlank(catPt))
              .map(categoryNameToEn::get)
              .filter(catEn -> !isBlank(catEn))
              .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

      LinkedHashMap<String, Integer> out = new LinkedHashMap<>();
      categoryCount.entrySet().stream()
              .sorted(
                      Comparator
                              .comparing(Map.Entry<String, Long>::getValue)
                              .reversed()
                              .thenComparing(Map.Entry::getKey)
              )
              .limit(10)
              .forEach(e -> out.put(e.getKey(), e.getValue().intValue()));
      return out;

    } catch (Exception e) {
      e.printStackTrace();
      return Collections.emptyMap();
    }
  }

  public Map<String, Long> getPurchasePatternByHour() {
    Path ordersCsv = baseDir.resolve("olist_orders_dataset.csv");

    LinkedHashMap<String, Long> byHour = new LinkedHashMap<>();
    for (int i = 0; i < 24; i++) {
      byHour.put(String.format("%02d:00", i), 0L);
    }
    if (!Files.exists(ordersCsv)) {
      throw new IllegalArgumentException("File not found: " + ordersCsv);
    }
    try (BufferedReader br = Files.newBufferedReader(ordersCsv, StandardCharsets.UTF_8)) {
      String headerLine = br.readLine();
      if (headerLine == null) {
        throw new IllegalArgumentException("Empty file: " + ordersCsv);
      }
      String[] header = splitCsv(headerLine);
      int orderPurchaseTimestampIndex = indexOf(header, "order_purchase_timestamp");
      if (orderPurchaseTimestampIndex < 0) return byHour;

      Map<String, Long> counts = br.lines()
              .filter(line -> !line.isEmpty())
              .map(this::splitCsv)
              .filter(cells -> cells.length > orderPurchaseTimestampIndex)
              .map(cells -> cells[orderPurchaseTimestampIndex])
              .filter(ts -> !isBlank(ts) && ts.length() >= 13)
              .map(ts -> ts.substring(11, 13) + ":00")
              .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

      counts.forEach((k, v) -> byHour.put(k, byHour.getOrDefault(k, 0L) + v));
    } catch (Exception e) {
      e.printStackTrace();
    }
    return byHour;
  }

  public Map<String, Map<String, Long>> getPriceRangeDistribution() {
    Map<String, Map<String, Long>> tmp = productTotalPriceCents.entrySet().stream()
            .map(e -> {
              String productId = e.getKey();
              long totalCents = e.getValue();
              Long count = productSalesCount.get(productId);
              if (count == null || count <= 0) return null;

              long avgCents = Math.round(totalCents / (double) count);

              String catPt = productIdToCategoryName.get(productId);
              if (isBlank(catPt)) return null;
              String catEn = categoryNameToEn.get(catPt);
              if (isBlank(catEn)) return null;

              String bucket = toPriceBucketByCents(avgCents);
              if (bucket == null) return null;

              return new AbstractMap.SimpleEntry<>(catEn, bucket);
            })
            .filter(Objects::nonNull)
            .collect(Collectors.groupingBy(
                    Map.Entry::getKey,
                    Collectors.groupingBy(Map.Entry::getValue, Collectors.counting())
            ));

    Map<String, Map<String, Long>> result = new TreeMap<>();
    List<String> buckets = fixedBuckets();
    tmp.forEach((cat, bucketCounts) -> {
      LinkedHashMap<String, Long> inner = new LinkedHashMap<>();
      buckets.forEach(b -> inner.put(b, 0L));
      bucketCounts.forEach((b, c) -> inner.put(b, inner.getOrDefault(b, 0L) + c));
      result.put(cat, inner);
    });

    return result;
  }

  public Map<String, List<Double>> analyzeSellerPerformance(){
    List<Map.Entry<String, List<Double>>> rows = sellerOrders.entrySet().stream()
            .filter(e -> e.getValue() != null && e.getValue().size() >= 50)
            .map(entry -> {
              String sellerId = entry.getKey();
              Set<String> orders = entry.getValue();

              int orderCount = orders.size();

              long totalSalesCents = sellerTotalSalesCents.getOrDefault(sellerId, 0L);
              double totalSales = round2(totalSalesCents / 100.0);
              double avgOrderValue = round2(orderCount == 0 ? 0.0 : (totalSalesCents / 100.0) / orderCount);
              double uniqueProducts = (double) sellerProducts.getOrDefault(sellerId, Collections.emptySet()).size();

              double[] sumCnt = orders.stream()
                      .map(oid -> {
                        Double s = orderIdToReviewSum.get(oid);
                        Integer c = orderIdToReviewCount.get(oid);
                        if (s != null && c != null && c > 0) {
                          return new double[]{s, c.doubleValue()};
                        }
                        return new double[]{0.0, 0.0};
                      })
                      .reduce(new double[]{0.0, 0.0}, (a, b) -> new double[]{a[0] + b[0], a[1] + b[1]});
              double sellerReviewSum = sumCnt[0];
              double sellerReviewCount = sumCnt[1];
              double avgReview = round2(sellerReviewCount == 0 ? 0.0 : (sellerReviewSum / sellerReviewCount));

              long denom = orders.stream().map(orderIdOnTime::get).filter(Objects::nonNull).count();
              long ontime = orders.stream().map(orderIdOnTime::get).filter(Boolean.TRUE::equals).count();
              double onTimeRate = round2(denom == 0 ? 0.0 : (double) ontime / denom);

              List<Double> metrics = new ArrayList<>(5);
              metrics.add(totalSales);
              metrics.add(avgOrderValue);
              metrics.add(uniqueProducts);
              metrics.add(avgReview);
              metrics.add(onTimeRate);

              return new AbstractMap.SimpleEntry<>(sellerId, metrics);
            })
            .sorted((e1, e2) -> {
              double t1 = e1.getValue().get(0);
              double t2 = e2.getValue().get(0);
              int cmp = Double.compare(t2, t1);
              if (cmp != 0) return cmp;
              return e1.getKey().compareTo(e2.getKey());
            })
            .collect(Collectors.toList());

    LinkedHashMap<String, List<Double>> out = new LinkedHashMap<>();
    rows.forEach(e -> out.put(e.getKey(), e.getValue()));
    return out;
  }

  public Map<String, List<String>> recommendedProducts() {
    Map<String, List<ProductInformation>> byCategory = productSalesCount.entrySet().stream()
            .map(e -> {
              String productId = e.getKey();
              long sales = e.getValue() == null ? 0L : e.getValue();

              String catPt = productIdToCategoryName.get(productId);
              if (isBlank(catPt)) return null;
              String catEn = categoryNameToEn.get(catPt);
              if (isBlank(catEn)) return null;

              Set<String> orders = productOrders.getOrDefault(productId, Collections.emptySet());
              double[] sumCnt = orders.stream()
                      .map(oid -> {
                        Double s = orderIdToReviewSum.get(oid);
                        Integer c = orderIdToReviewCount.get(oid);
                        if (s != null && c != null && c > 0) {
                          return new double[]{s, c.doubleValue()};
                        }
                        return new double[]{0.0, 0.0};
                      })
                      .reduce(new double[]{0.0, 0.0}, (a, b) -> new double[]{a[0] + b[0], a[1] + b[1]});

              int reviewCnt = (int) Math.round(sumCnt[1]);
              if (sales < 10 || reviewCnt < 5) return null;

              double avgRating = reviewCnt == 0 ? 0.0 : (sumCnt[0] / reviewCnt);
              return new AbstractMap.SimpleEntry<>(catEn, new ProductInformation(productId, sales, reviewCnt, avgRating));
            })
            .filter(Objects::nonNull)
            .collect(Collectors.groupingBy(Map.Entry::getKey,
                    Collectors.mapping(Map.Entry::getValue, Collectors.toList())));

    Set<String> categoriesInData = productSalesCount.keySet().stream()
            .map(productIdToCategoryName::get)
            .filter(catPt -> !isBlank(catPt))
            .map(categoryNameToEn::get)
            .filter(catEn -> !isBlank(catEn))
            .collect(Collectors.toCollection(TreeSet::new));

    Map<String, List<String>> result = new LinkedHashMap<>();
    for (String cat : categoriesInData) {
      List<ProductInformation> list = byCategory.getOrDefault(cat, Collections.emptyList());
      if (list.isEmpty()) {
        result.put(cat, Collections.emptyList());
        continue;
      }

      DoubleSummaryStatistics salesStats = list.stream().mapToDouble(a -> a.sales).summaryStatistics();
      DoubleSummaryStatistics reviewStats = list.stream().mapToDouble(a -> a.reviewCount).summaryStatistics();
      DoubleSummaryStatistics ratingStats = list.stream().mapToDouble(a -> a.avgRating).summaryStatistics();

      double minSales = salesStats.getMin(), maxSales = salesStats.getMax();
      double minReviews = reviewStats.getMin(), maxReviews = reviewStats.getMax();
      double minRating = ratingStats.getMin(), maxRating = ratingStats.getMax();

      list.forEach(a -> {
        double salesScore = normalize(a.sales, minSales, maxSales);
        double reviewCountScore = normalize(a.reviewCount, minReviews, maxReviews);
        double avgRatingScore = normalize(a.avgRating, minRating, maxRating);
        a.score = 0.5 * salesScore + 0.3 * reviewCountScore + 0.2 * avgRatingScore;
      });

      List<String> top = list.stream()
              .sorted((a, b) -> {
                int cmp = Double.compare(b.score, a.score);
                if (cmp != 0) return cmp;
                return a.productId.compareTo(b.productId);
              })
              .limit(10)
              .map(a -> a.productId)
              .collect(Collectors.toList());

      result.put(cat, top);
    }

    return result;
  }

  private void readProducts(Path productsCsv){
    if(!Files.exists(productsCsv)){
      throw new IllegalArgumentException("File not found: " + productsCsv);
    }
    try(BufferedReader br = Files.newBufferedReader(productsCsv, StandardCharsets.UTF_8)){
      String headerLine = br.readLine();
      if(headerLine == null){
        throw new IllegalArgumentException("Empty file: " + productsCsv);
      }
      String[] header = splitCsv(headerLine);
      int productIdIndex = indexOf(header, "product_id");
      int categoryNameIndex = indexOf(header, "product_category_name");
      if (productIdIndex < 0 || categoryNameIndex < 0) return;

      br.lines()
              .filter(line -> !line.isEmpty())
              .map(this::splitCsv)
              .filter(cells -> cells.length > Math.max(productIdIndex, categoryNameIndex))
              .forEach(cells -> {
                String productId = cells[productIdIndex];
                String categoryName = cells[categoryNameIndex];
                if (!isBlank(productId) && !isBlank(categoryName)) {
                  productIdToCategoryName.put(productId, categoryName);
                }
              });
    } catch (Exception e){
      e.printStackTrace();
    }
  }

  private void readCategoryTranslation(Path translationCsv){
    if(!Files.exists(translationCsv)){
      throw new IllegalArgumentException("File not found: " + translationCsv);
    }
    try(BufferedReader br = Files.newBufferedReader(translationCsv, StandardCharsets.UTF_8)){
      String headerLine = br.readLine();
      if(headerLine == null){
        throw new IllegalArgumentException("Empty file: " + translationCsv);
      }
      String[] header = splitCsv(headerLine);
      int categoryNameIndex = indexOf(header, "product_category_name");
      int categoryNameEnIndex = indexOf(header, "product_category_name_english");
      if (categoryNameIndex < 0 || categoryNameEnIndex < 0) return;

      br.lines()
              .filter(line -> !line.isEmpty())
              .map(this::splitCsv)
              .filter(cells -> cells.length > Math.max(categoryNameIndex, categoryNameEnIndex))
              .forEach(cells -> {
                String categoryName = cells[categoryNameIndex];
                String categoryNameEn = cells[categoryNameEnIndex];
                if (!isBlank(categoryName) && !isBlank(categoryNameEn)) {
                  categoryNameToEn.put(categoryName, categoryNameEn);
                }
              });
    } catch (Exception e){
      e.printStackTrace();
    }
  }

  private static List<String> fixedBuckets() {
    return Arrays.asList("(0,50]", "(50,100]", "(100,200]", "(200,500]", "(500,)");
  }

  private static Double parsePriceStrict(String raw) {
    String s = raw.replace("\"", "").trim();
    if (s.isEmpty()) return null;
    try {
      return Double.parseDouble(s);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private static Long parseCents(String raw) {
    String s = raw.replace("\"", "").trim();
    if (s.isEmpty()) return null;
    try {
      BigDecimal bd = new BigDecimal(s);
      return bd.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
    } catch (Exception e) {
      return null;
    }
  }

  private static String toPriceBucketByCents(long cents) {
    if (cents <= 0) return null;
    if (cents <= 5000) return "(0,50]";
    if (cents <= 10000) return "(50,100]";
    if (cents <= 20000) return "(100,200]";
    if (cents <= 50000) return "(200,500]";
    return "(500,)";
  }

  private void readOrders(Path ordersCsv){
    if(!Files.exists(ordersCsv)){
      throw new IllegalArgumentException("File not found: " + ordersCsv);
    }
    try(BufferedReader br = Files.newBufferedReader(ordersCsv, StandardCharsets.UTF_8)){
      String headerLine = br.readLine();
      if(headerLine == null){
        throw new IllegalArgumentException("Empty file: " + ordersCsv);
      }
      String[] header = splitCsv(headerLine);
      int sellerIdIndex = indexOf(header, "seller_id");
      int orderIdIndex = indexOf(header, "order_id");
      int productIdIndex = indexOf(header, "product_id");
      int priceIndex = indexOf(header, "price");

      if (sellerIdIndex < 0 || orderIdIndex < 0 || productIdIndex < 0 || priceIndex < 0 ) return;

      br.lines()
              .filter(line -> !line.isEmpty())
              .map(this::splitCsv)
              .filter(cells -> cells.length > Math.max(Math.max(sellerIdIndex, orderIdIndex), Math.max(productIdIndex, priceIndex)))
              .forEach(cells -> {
                String sellerId = cells[sellerIdIndex];
                String orderId = cells[orderIdIndex];
                String productId = cells[productIdIndex];
                String priceRaw = cells[priceIndex];

                if (isBlank(orderId) || isBlank(sellerId) || isBlank(productId) || isBlank(priceRaw)) {
                  return;
                }

                Long cents = parseCents(priceRaw);
                if (cents == null) return;

                sellerTotalSalesCents.merge(sellerId, cents, Long::sum);
                sellerOrders.computeIfAbsent(sellerId, k -> new HashSet<>()).add(orderId);
                sellerProducts.computeIfAbsent(sellerId, k -> new HashSet<>()).add(productId);

                productSalesCount.merge(productId, 1L, Long::sum);
                productOrders.computeIfAbsent(productId, k -> new HashSet<>()).add(orderId);
                productFirstPriceCents.putIfAbsent(productId, cents);

                productTotalPriceCents.merge(productId, cents, Long::sum);
              });
    } catch (Exception e){
      e.printStackTrace();
    }
  }

  private void readDeliveries(Path deliveriesCsv){
    if(!Files.exists(deliveriesCsv)){
      throw new IllegalArgumentException("File not found: " + deliveriesCsv);
    }
    try(BufferedReader br = Files.newBufferedReader(deliveriesCsv, StandardCharsets.UTF_8)){
      String headerLine = br.readLine();
      if(headerLine == null){
        throw new IllegalArgumentException("Empty file: " + deliveriesCsv);
      }
      String[] header = splitCsv(headerLine);
      int orderIdIndex = indexOf(header, "order_id");
      int actualArrivalIndex = indexOf(header, "order_delivered_customer_date");
      int estimateArrivalIndex = indexOf(header, "order_estimated_delivery_date");
      if (orderIdIndex < 0 || actualArrivalIndex < 0 || estimateArrivalIndex < 0) return;

      DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

      br.lines()
              .filter(line -> !line.isEmpty())
              .map(this::splitCsv)
              .filter(cells -> cells.length > Math.max(orderIdIndex, Math.max(actualArrivalIndex, estimateArrivalIndex)))
              .forEach(cells -> {
                String orderId = cells[orderIdIndex];
                String actualArrival = cells[actualArrivalIndex];
                String estimateArrival = cells[estimateArrivalIndex];

                if (isBlank(orderId) || isBlank(actualArrival) || isBlank(estimateArrival)) {
                  return;
                }
                try{
                  LocalDateTime actual = LocalDateTime.parse(actualArrival, dtf);
                  LocalDateTime estimate = LocalDateTime.parse(estimateArrival, dtf);
                  orderIdOnTime.put(orderId, !actual.isAfter(estimate));
                } catch (Exception ignored){
                }
              });
    } catch (Exception e){
      e.printStackTrace();
    }
  }

  private void readReviews(Path reviewsCsv){
    if(!Files.exists(reviewsCsv)){
      throw new IllegalArgumentException("File not found: " + reviewsCsv);
    }
    try(BufferedReader br = Files.newBufferedReader(reviewsCsv, StandardCharsets.UTF_8)){
      String headerLine = br.readLine();
      if(headerLine == null){
        throw new IllegalArgumentException("Empty file: " + reviewsCsv);
      }
      String[] header = splitCsv(headerLine);
      int orderIdIndex = indexOf(header, "order_id");
      int reviewScoreIndex = indexOf(header, "review_score");
      if (orderIdIndex < 0 || reviewScoreIndex < 0) return;

      br.lines()
              .filter(line -> !line.isEmpty())
              .map(this::splitCsv)
              .filter(cells -> cells.length > Math.max(orderIdIndex, reviewScoreIndex))
              .forEach(cells -> {
                String orderId = cells[orderIdIndex];
                String reviewScore = cells[reviewScoreIndex];
                if (isBlank(orderId) || isBlank(reviewScore)) {
                  return;
                }
                Double score = parsePriceStrict(reviewScore);
                if (score == null) return;

                orderIdToReviewSum.merge(orderId, score, Double::sum);
                orderIdToReviewCount.merge(orderId, 1, Integer::sum);
              });
    } catch (Exception e){
      e.printStackTrace();
    }
  }

  private static double round2(double v) {
    return new BigDecimal(Double.toString(v)).setScale(2, RoundingMode.HALF_UP).doubleValue();
  }

  private String[] splitCsv(String line) {
    List<String> out = new ArrayList<>();
    StringBuilder sb = new StringBuilder();
    boolean inQuotes = false;
    for (int i = 0; i < line.length(); i++) {
      char c = line.charAt(i);
      if (c == '"') {
        inQuotes = !inQuotes;
      } else if (c == ',' && !inQuotes) {
        out.add(sb.toString());
        sb.setLength(0);
      } else {
        sb.append(c);
      }
    }
    out.add(sb.toString());
    return out.toArray(new String[0]);
  }

  private static int indexOf(String[] header, String colName) {
    for (int i = 0; i < header.length; i++) {
      String cell = header[i] == null ? "" : header[i].replace("\"", "");
      if (colName.equals(cell)) return i;
    }
    return -1;
  }

  private static double normalize(double v, double min, double max) {
    if (Double.isNaN(v) || Double.isInfinite(v)) return 0.0;
    if (max <= min) return 0.0;
    return (v - min) / (max - min);
  }

  private static boolean isBlank(String s) {
    return s == null || s.trim().isEmpty();
  }

  private static final class ProductInformation {
    final String productId;
    final double sales;
    final double reviewCount;
    final double avgRating;
    double score;

    ProductInformation(String productId, long sales, int reviewCount, double avgRating) {
      this.productId = productId;
      this.sales = sales;
      this.reviewCount = reviewCount;
      this.avgRating = avgRating;
      this.score = 0.0;
    }
  }
}