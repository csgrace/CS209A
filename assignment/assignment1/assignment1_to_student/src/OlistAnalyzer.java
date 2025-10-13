import java.io.BufferedReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

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
    this.baseDir = Path.of(datasetFolderPath);

    /* 第一问：
    从“olist_order_items_dataset.csv”得到product_id
    然后在“olist_products_dataset.csv”得到product_category_name
    然后在“product_category_name_translation”得到product_category_name_english */

    this.orderItemsCsv = baseDir.resolve("olist_order_items_dataset.csv");
    readProducts(baseDir.resolve("olist_products_dataset.csv"));
    readCategoryTranslation(baseDir.resolve("product_category_name_translation.csv"));

    /* 第三问：
    从“olist_order_items_dataset.csv”得到price和product_id
    然后在“olist_products_dataset.csv”得到product_id和product_category_name
    然后在“product_category_name_translation”得到product_category_name_english和product_category_name  */

    /* 第四问：
    从“olist_order_items_dataset.csv”得到seller_id，order_id, product_id和price，得到1，2，3
    然后在“olist_orders_dataset.csv”得到order_id,order_delivered_customer_date和order_estimated_delivery_date，得到5
    然后在“olist_order_reviews_dataset”得到review_score和order_id，得到4
    仅保留订单数（去重后）≥ 50 的卖家；最后按总销售额降序、seller_id 升序排序返回  */

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

      for (String line; (line = br.readLine()) != null; ) {
        if (line.isEmpty()) continue;
        String[] cells = splitCsv(line);
        if (cells.length <= productIdIndex) continue;
        String productId = cells[productIdIndex];
        String categoryName = productIdToCategoryName.get(productId);

        if (categoryName == null || categoryName.isBlank()) continue;

        String categoryNameEn = categoryNameToEn.get(categoryName);
        if (categoryNameEn == null || categoryNameEn.isBlank()) continue;

        categoryCount.merge(categoryNameEn, 1L, Long::sum);
      }

      return categoryCount.entrySet().stream()
              .sorted(
                      Comparator
                              .comparing(Map.Entry<String, Long>::getValue)
                              .reversed()
                              .thenComparing(Map.Entry::getKey)
              )
              .limit(10)
              .collect(
                      LinkedHashMap::new,
                      (m, e) -> m.put(e.getKey(), e.getValue().intValue()),
                      LinkedHashMap::putAll
              );

    } catch (Exception e) {
      e.printStackTrace();
      return Map.of();
    }
  }

  public Map<String, Long> getPurchasePatternByHour(){
    Path ordersCsv = baseDir.resolve("olist_orders_dataset.csv");

    LinkedHashMap<String,Long> ByHour = new LinkedHashMap<>();
    for (int i = 0; i < 24; i++) {
      ByHour.put(String.format("%02d:00",i),0L);
    }
    if(!Files.exists(ordersCsv)){
      throw new IllegalArgumentException("File not found: " + ordersCsv);
    }
    try(BufferedReader br = Files.newBufferedReader(ordersCsv, StandardCharsets.UTF_8)){
      String headerLine = br.readLine();
      if(headerLine == null){
        throw new IllegalArgumentException("Empty file: " + ordersCsv);
      }
      String[] header = splitCsv(headerLine);
      int orderStatusIndex = indexOf(header, "order_status");
      int orderPurchaseTimestampIndex = indexOf(header, "order_purchase_timestamp");
      if (orderStatusIndex < 0 || orderPurchaseTimestampIndex < 0) return ByHour;

      for(String line; (line = br.readLine()) != null; ){
        if (line.isEmpty()) continue;
        String[] cells = splitCsv(line);
        if(cells.length <= Math.max(orderStatusIndex, orderPurchaseTimestampIndex)) continue;
        String orderStatus = cells[orderStatusIndex];
        String orderPurchaseTimestamp = cells[orderPurchaseTimestampIndex];
        if (orderStatus != null && orderStatus.equals("delivered") && orderPurchaseTimestamp != null && !orderPurchaseTimestamp.isBlank()) {
          String hour = orderPurchaseTimestamp.substring(11,13);
          String hourKey = hour + ":00";
          ByHour.put(hourKey,ByHour.get(hourKey)+1);
        }
      }
    } catch (Exception e){
      e.printStackTrace();
    }
    return ByHour;
  }

  public Map<String, Map<String, Long>> getPriceRangeDistribution() {
    Map<String, Map<String, Long>> result = new TreeMap<>();
    for (Map.Entry<String, Long> e : productTotalPriceCents.entrySet()) {
      String productId = e.getKey();
      long totalCents = e.getValue();

      Long count = productSalesCount.get(productId);
      if (count == null || count <= 0) continue;

      long avgCents = Math.round(totalCents / (double) count);

      String catPt = productIdToCategoryName.get(productId);
      if (catPt == null || catPt.isBlank()) continue;
      String catEn = categoryNameToEn.get(catPt);
      if (catEn == null || catEn.isBlank()) continue;

      String bucket = toPriceBucketByCents(avgCents);
      if (bucket == null) continue;

      Map<String, Long> inner = result.computeIfAbsent(catEn, k -> {
        LinkedHashMap<String, Long> m = new LinkedHashMap<>();
        for (String b : fixedBuckets()) m.put(b, 0L);
        return m;
      });
      inner.put(bucket, inner.get(bucket) + 1);
    }

    return result;
  }

  public Map<String, List<Double>> analyzeSellerPerformance(){
    List<Map.Entry<String, List<Double>>> rows = new ArrayList<>();
    for (Map.Entry<String, Set<String>> entry : sellerOrders.entrySet()) {
      String sellerId = entry.getKey();
      Set<String> orders = entry.getValue();

      int orderCount = orders.size();
      if (orderCount < 50) continue;

      long totalSalesCents = sellerTotalSalesCents.getOrDefault(sellerId, 0L);
      double totalSales = round2(totalSalesCents / 100.0);
      double avgOrderValue = round2(orderCount == 0 ? 0.0 : (totalSalesCents / 100.0) / orderCount);
      double uniqueProducts = (double) sellerProducts.getOrDefault(sellerId, Collections.emptySet()).size();

      double sellerReviewSum = 0.0;
      int sellerReviewCount = 0;
      for (String oid : orders) {
        Double sum = orderIdToReviewSum.get(oid);
        Integer cnt = orderIdToReviewCount.get(oid);
        if (sum != null && cnt != null && cnt > 0) {
          sellerReviewSum += sum;
          sellerReviewCount += cnt;
        }
      }
      double avgReview = round2(sellerReviewCount == 0 ? 0.0 : (sellerReviewSum / sellerReviewCount));

      int denom = 0, ontime = 0;
      for (String oid : orders) {
        Boolean on = orderIdOnTime.get(oid);
        if (on != null) { denom++; if (on) ontime++; }
      }
      double onTimeRate = round2(denom == 0 ? 0.0 : (double) ontime / denom);

      List<Double> metrics = new ArrayList<>(5);
      metrics.add(totalSales);
      metrics.add(avgOrderValue);
      metrics.add(uniqueProducts);
      metrics.add(avgReview);
      metrics.add(onTimeRate);

      rows.add(Map.entry(sellerId, metrics));
    }

    rows.sort((e1, e2) -> {
      double t1 = e1.getValue().get(0);
      double t2 = e2.getValue().get(0);
      int cmp = Double.compare(t2, t1);
      if (cmp != 0) return cmp;
      return e1.getKey().compareTo(e2.getKey());
    });

    LinkedHashMap<String, List<Double>> out = new LinkedHashMap<>();
    for (Map.Entry<String, List<Double>> e : rows) {
      out.put(e.getKey(), e.getValue());
    }
    return out;
  }

  public Map<String, List<String>> recommendedProducts() {
    Map<String, List<ProductAgg>> byCategory = new HashMap<>();

    for (Map.Entry<String, Long> e : productSalesCount.entrySet()) {
      String productId = e.getKey();
      long sales = e.getValue() == null ? 0L : e.getValue();

      String catPt = productIdToCategoryName.get(productId);
      if (catPt == null || catPt.isBlank()) continue;
      String catEn = categoryNameToEn.get(catPt);
      if (catEn == null || catEn.isBlank()) continue;

      Set<String> orders = productOrders.getOrDefault(productId, Collections.emptySet());
      double reviewSum = 0.0;
      int reviewCnt = 0;
      for (String oid : orders) {
        Double s = orderIdToReviewSum.get(oid);
        Integer c = orderIdToReviewCount.get(oid);
        if (s != null && c != null && c > 0) {
          reviewSum += s;
          reviewCnt += c;
        }
      }
      if (sales < 10 || reviewCnt < 5) continue;

      double avgRating = reviewCnt == 0 ? 0.0 : (reviewSum / reviewCnt);

      ProductAgg agg = new ProductAgg(productId, sales, reviewCnt, avgRating);
      byCategory.computeIfAbsent(catEn, k -> new ArrayList<>()).add(agg);
    }

    Set<String> categoriesInData = new TreeSet<>();
    for (String pid : productSalesCount.keySet()) {
      String catPt = productIdToCategoryName.get(pid);
      if (catPt == null || catPt.isBlank()) continue;
      String catEn = categoryNameToEn.get(catPt);
      if (catEn == null || catEn.isBlank()) continue;
      categoriesInData.add(catEn);
    }

    Map<String, List<String>> result = new LinkedHashMap<>();
    for (String cat : categoriesInData) {
      List<ProductAgg> list = byCategory.getOrDefault(cat, Collections.emptyList());
      if (list.isEmpty()) {
        result.put(cat, List.of());
        continue;
      }

      double minSales = Double.POSITIVE_INFINITY, maxSales = Double.NEGATIVE_INFINITY;
      double minReviews = Double.POSITIVE_INFINITY, maxReviews = Double.NEGATIVE_INFINITY;
      double minRating = Double.POSITIVE_INFINITY, maxRating = Double.NEGATIVE_INFINITY;

      for (ProductAgg a : list) {
        minSales = Math.min(minSales, a.sales);
        maxSales = Math.max(maxSales, a.sales);
        minReviews = Math.min(minReviews, a.reviewCount);
        maxReviews = Math.max(maxReviews, a.reviewCount);
        minRating = Math.min(minRating, a.avgRating);
        maxRating = Math.max(maxRating, a.avgRating);
      }

      for (ProductAgg a : list) {
        double salesScore = normalize(a.sales, minSales, maxSales);
        double reviewCountScore = normalize(a.reviewCount, minReviews, maxReviews);
        double avgRatingScore = normalize(a.avgRating, minRating, maxRating);
        a.score = 0.5 * salesScore + 0.3 * reviewCountScore + 0.2 * avgRatingScore;
      }

      list.sort((a, b) -> {
        int cmp = Double.compare(b.score, a.score);
        if (cmp != 0) return cmp;
        return a.productId.compareTo(b.productId);
      });

      List<String> top = new ArrayList<>();
      for (int i = 0; i < Math.min(10, list.size()); i++) {
        top.add(list.get(i).productId);
      }
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

      for(String line; (line = br.readLine()) != null; ){
        if (line.isEmpty()) continue;
        String[] cells = splitCsv(line);
        if(cells.length <= Math.max(productIdIndex, categoryNameIndex)) continue;
        String productId = cells[productIdIndex];
        String categoryName = cells[categoryNameIndex];
        if (productId != null && !productId.isBlank() && categoryName != null && !categoryName.isBlank()) {
          productIdToCategoryName.put(productId, categoryName);
        }
      }
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

      for(String line; (line = br.readLine()) != null; ){
        if (line.isEmpty()) continue;
        String[] cells = splitCsv(line);
        if(cells.length <= Math.max(categoryNameIndex, categoryNameEnIndex)) continue;
        String categoryName = cells[categoryNameIndex];
        String categoryNameEn = cells[categoryNameEnIndex];
        if (categoryName != null && !categoryName.isBlank() && categoryNameEn != null && !categoryNameEn.isBlank()) {
          categoryNameToEn.put(categoryName, categoryNameEn);
        }
      }
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
      // 单价本身已有两位小数，这里稳妥起见仍做 HALF_UP 到分
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

      for(String line; (line = br.readLine()) != null; ){
        if (line.isEmpty()) continue;
        String[] cells = splitCsv(line);
        int maxIdx = Math.max(Math.max(sellerIdIndex, orderIdIndex), Math.max(productIdIndex, priceIndex));
        if(cells.length <= maxIdx) continue;

        String sellerId = cells[sellerIdIndex];
        String orderId = cells[orderIdIndex];
        String productId = cells[productIdIndex];
        String priceRaw = cells[priceIndex];

        if (orderId == null || orderId.isBlank() ||
                sellerId == null || sellerId.isBlank() ||
                productId == null || productId.isBlank() ||
                priceRaw == null || priceRaw.isBlank() ) {
          continue;
        }

        Long cents = parseCents(priceRaw);
        if (cents == null) continue;

        sellerTotalSalesCents.merge(sellerId, cents, Long::sum);
        sellerOrders.computeIfAbsent(sellerId, k -> new HashSet<>()).add(orderId);
        sellerProducts.computeIfAbsent(sellerId, k -> new HashSet<>()).add(productId);

        productSalesCount.merge(productId, 1L, Long::sum);
        productOrders.computeIfAbsent(productId, k -> new HashSet<>()).add(orderId);
        productFirstPriceCents.putIfAbsent(productId, cents);
        productTotalPriceCents.merge(productId, cents, Long::sum);
      }
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

      for(String line; (line = br.readLine()) != null; ){
        if (line.isEmpty()) continue;
        String[] cells = splitCsv(line);
        if(cells.length <= Math.max(orderIdIndex, Math.max(actualArrivalIndex, estimateArrivalIndex))) continue;
        String orderId = cells[orderIdIndex];
        String actualArrival = cells[actualArrivalIndex];
        String estimateArrival = cells[estimateArrivalIndex];

        if (orderId == null || orderId.isBlank() ||
                actualArrival == null || actualArrival.isBlank() ||
                estimateArrival == null || estimateArrival.isBlank()) {
          continue;
        }
        try{
          LocalDateTime actual = LocalDateTime.parse(actualArrival, dtf);
          LocalDateTime estimate = LocalDateTime.parse(estimateArrival, dtf);
          orderIdOnTime.put(orderId, !actual.isAfter(estimate));
        } catch (Exception e){
          // 解析失败则不记录该订单
        }
      }
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

      for(String line; (line = br.readLine()) != null; ){
        if (line.isEmpty()) continue;
        String[] cells = splitCsv(line);
        if(cells.length <= Math.max(orderIdIndex, reviewScoreIndex)) continue;
        String orderId = cells[orderIdIndex];
        String reviewScore = cells[reviewScoreIndex];
        if (orderId == null || orderId.isBlank() || reviewScore == null || reviewScore.isBlank()) {
          continue;
        }
        Double score = parsePriceStrict(reviewScore);
        if (score == null) continue;

        orderIdToReviewSum.merge(orderId, score, Double::sum);
        orderIdToReviewCount.merge(orderId, 1, Integer::sum);
      }
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

  private static final class ProductAgg {
    final String productId;
    final double sales;
    final double reviewCount;
    final double avgRating;
    double score;

    ProductAgg(String productId, long sales, int reviewCount, double avgRating) {
      this.productId = productId;
      this.sales = sales;
      this.reviewCount = reviewCount;
      this.avgRating = avgRating;
      this.score = 0.0;
    }
  }
}