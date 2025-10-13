import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;


public class OlistAnalyzer {
  private final Path baseDir;
  private final Path orderItemsCsv;
  private final Map<String, String> productIdToCategoryName = new HashMap<>();
  private final Map<String, String> categoryNameToEn = new HashMap<>();

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
    然后在“product_category_name_translation”得到product_category_name_english和product_category_name */

    /* 第四问：
    从“olist_order_items_dataset.csv”得到seller_id，order_id, product_id和price，得到需要的1，2，3
    然后在“olist_orders_dataset.csv”得到order_id,order_delivered_customer_date

    然后在
    */
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
                            .comparing(Map.Entry<String, Long>::getValue) // 先按销量
                            .reversed()
                            .thenComparing(Map.Entry::getKey)             // 再按品类名
            )
            .limit(10)
            .collect(
                    LinkedHashMap::new,
                    (m, e) -> m.put(e.getKey(), e.getValue().intValue()),
                    LinkedHashMap::putAll
            );

    }catch(Exception e){
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
    // 1) 先扫 order_items：product_id -> 最低价格
    Map<String, Double> pidToMinPrice = new HashMap<>();
    try (BufferedReader br = Files.newBufferedReader(orderItemsCsv, StandardCharsets.UTF_8)) {
      String headerLine = br.readLine();
      if (headerLine == null) return Collections.emptyMap();
      String[] header = splitCsv(headerLine);

      int idxProductId = indexOf(header, "product_id");
      int idxPrice = indexOf(header, "price");
      if (idxProductId < 0 || idxPrice < 0) return Collections.emptyMap();

      for (String line; (line = br.readLine()) != null; ) {
        if (line.isEmpty()) continue;
        String[] cols = splitCsv(line);
        if (cols.length <= Math.max(idxProductId, idxPrice)) continue;

        String productId = cols[idxProductId];
        String priceRaw = cols[idxPrice];
        if (productId == null || productId.isBlank() || priceRaw == null || priceRaw.isBlank()) {
          continue;
        }

        Double price = parsePriceStrict(priceRaw);
        if (price == null || price <= 0) continue;

        // 取该 product 的最小成交价
        pidToMinPrice.merge(productId, price, Math::min);
      }
    } catch (Exception e) {
      e.printStackTrace();
    }

    // 2) 聚合到 英文品类 -> 区间计数（外层 TreeMap，内层 LinkedHashMap 固定顺序）
    Map<String, Map<String, Long>> result = new TreeMap<>();
    for (Map.Entry<String, Double> e : pidToMinPrice.entrySet()) {
      String productId = e.getKey();
      double price = e.getValue();

      String catPt = productIdToCategoryName.get(productId);
      if (catPt == null || catPt.isBlank()) continue;
      String catEn = categoryNameToEn.get(catPt);
      if (catEn == null || catEn.isBlank()) continue;

      String bucket = toPriceBucketStrict(price); // 严格边界
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

    return Map.of();
  }

  public Map<String, List<String>> recommendedProducts() {
    return Map.of();
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

  // 固定 5 桶顺序
  private static List<String> fixedBuckets() {
    return Arrays.asList("(0,50]", "(50,100]", "(100,200]", "(200,500]", "(500,)");
  }

  // 仅按小数点解析；没有逗号兼容逻辑
  private static Double parsePriceStrict(String raw) {
    String s = raw.replace("\"", "").trim();
    if (s.isEmpty()) return null;
    try {
      return Double.parseDouble(s);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  // 严格边界：0 < p <= 50; 50 < p <= 100; 100 < p <= 200; 200 < p <= 500; p > 500
  private static String toPriceBucketStrict(double p) {
    if (p <= 0) return null;
    if (p <= 50) return "(0,50]";
    if (p <= 100) return "(50,100]";
    if (p <= 200) return "(100,200]";
    if (p <= 500) return "(200,500]";
    return "(500,)";
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
}