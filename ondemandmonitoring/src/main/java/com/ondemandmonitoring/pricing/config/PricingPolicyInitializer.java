package com.ondemandmonitoring.pricing.config;

import com.ondemandmonitoring.pricing.domain.*;
import com.ondemandmonitoring.pricing.enums.*;
import com.ondemandmonitoring.pricing.repository.PricingPolicyRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class PricingPolicyInitializer implements ApplicationRunner {
    private final PricingPolicyRepository policies;

    @Override @Transactional
    public void run(ApplicationArguments args) {
        if (policies.findFirstByStatusOrderByPolicyVersionDesc(PricingPolicyStatus.ACTIVE).isPresent()) return;
        PricingPolicy p = new PricingPolicy();
        p.setName("Chính sách giá mặc định"); p.setPolicyVersion(1); p.setStatus(PricingPolicyStatus.ACTIVE);
        p.setCurrency("VND"); p.setEffectiveFrom(Instant.now());
        p.setManagerMaxDiscountPercent(new BigDecimal("25")); p.setManagerMaxMarkupPercent(new BigDecimal("25"));
        p.setHighRiskDiscountPercent(new BigDecimal("10")); p.setMinimumPriceRatio(new BigDecimal("0.75"));
        p.setMinimumPrice(new BigDecimal("100000"));
        var rules = new ArrayList<PricingRule>(); int i = 0;
        rules.add(rule(PricingCategory.DISTANCE, "UP_TO_5", "Quãng đường đến 5 km", PricingCalculationType.FIXED, null, "5", "0", null, null, null, i++));
        rules.add(rule(PricingCategory.DISTANCE, "UP_TO_20", "Phụ phí quãng đường từ 5 đến 20 km", PricingCalculationType.PERCENT_OF_BASE, "5", "20", null, "10", null, null, i++));
        rules.add(rule(PricingCategory.DISTANCE, "UP_TO_50", "Phụ phí quãng đường từ 20 đến 50 km", PricingCalculationType.PERCENT_OF_BASE, "20", "50", null, "30", null, null, i++));
        rules.add(rule(PricingCategory.DISTANCE, "UP_TO_100", "Chi phí nguồn lực cho quãng đường dài", PricingCalculationType.RESOURCE_BASED, "50", "100", "2400000", null, null, null, i++));
        rules.add(rule(PricingCategory.DISTANCE, "OVER_100", "Quãng đường trên 100 km cần đánh giá riêng", PricingCalculationType.MANUAL_QUOTE, "100", null, null, null, null, null, i++));
        rules.add(rule(PricingCategory.AREA, "UP_TO_10", "Diện tích đến 10 ha", PricingCalculationType.FIXED, null, "10", "0", null, null, null, i++));
        rules.add(rule(PricingCategory.AREA, "UP_TO_50", "Phụ phí theo mỗi 10 ha", PricingCalculationType.PER_BLOCK, "10", "50", "100000", null, "10", null, i++));
        PricingRule largeArea = rule(PricingCategory.AREA, "UP_TO_200", "Phụ phí diện tích lớn theo mỗi 10 ha", PricingCalculationType.TIERED_BLOCK, "50", "200", "80000", null, "10", null, i++);
        largeArea.setBaseAmount(new BigDecimal("400000")); rules.add(largeArea);
        rules.add(rule(PricingCategory.AREA, "OVER_200", "Diện tích trên 200 ha cần đánh giá riêng", PricingCalculationType.MANUAL_QUOTE, "200", null, null, null, null, null, i++));
        rules.add(fixed(PricingCategory.CHECKLIST, "BASIC", "Hạng mục cơ bản", "0", i++));
        rules.add(fixed(PricingCategory.CHECKLIST, "MEDIUM", "Hạng mục độ khó trung bình", "50000", i++));
        rules.add(fixed(PricingCategory.CHECKLIST, "HARD", "Hạng mục khó", "150000", i++));
        rules.add(fixed(PricingCategory.CHECKLIST, "VERY_HARD", "Hạng mục rất khó", "400000", i++));
        rules.add(fixed(PricingCategory.OUTPUT, "VIDEO", "Video theo yêu cầu", "300000", i++));
        rules.add(fixed(PricingCategory.OUTPUT, "PDF_REPORT", "Báo cáo PDF theo yêu cầu", "200000", i++));
        rules.add(fixed(PricingCategory.OUTPUT, "ORTHOMOSAIC", "Ảnh trực giao ghép theo yêu cầu", "500000", i++));
        rules.add(fixed(PricingCategory.OUTPUT, "MODEL_3D", "Mô hình 3D theo yêu cầu", "800000", i++));
        rules.add(rule(PricingCategory.OUTPUT, "HIGH_RESOLUTION_IMAGE", "Mỗi 10 ảnh độ phân giải cao", PricingCalculationType.PER_BLOCK, "0", null, "100000", null, "10", null, i++));
        rules.add(rule(PricingCategory.OUTPUT, "4K_VIDEO", "Mỗi video 4K theo yêu cầu", PricingCalculationType.PER_UNIT, null, null, "150000", null, null, null, i++));
        rules.add(fixed(PricingCategory.OUTPUT, "THERMAL", "Dữ liệu ảnh nhiệt", "500000", i++));
        rules.add(fixed(PricingCategory.OUTPUT, "MULTISPECTRAL", "Dữ liệu đa phổ", "700000", i++));
        rules.add(fixed(PricingCategory.OUTPUT, "AI_REPORT", "Báo cáo phân tích bằng AI", "400000", i++));
        rules.add(fixed(PricingCategory.RESOURCE, "CUSTOM_BASE", "Chi phí cơ sở cho công việc tùy chỉnh", "2000000", i++));
        rules.add(fixed(PricingCategory.RESOURCE, "PILOT", "Nhân sự điều khiển", "800000", i++));
        rules.add(fixed(PricingCategory.RESOURCE, "DRONE", "Thiết bị bay", "600000", i++));
        rules.add(fixed(PricingCategory.RESOURCE, "BATTERY", "Pin thiết bị bay", "100000", i++));
        rules.add(multiplier("FLAT", "Địa hình bằng phẳng", "1.0", i++)); rules.add(multiplier("MOUNTAIN", "Điều chỉnh do địa hình đồi núi", "1.2", i++));
        rules.add(multiplier("HIGH_RISE", "Điều chỉnh do khu vực nhà cao tầng", "1.2", i++)); rules.add(multiplier("DAM_RESERVOIR", "Điều chỉnh do khu vực đập hoặc hồ chứa", "1.3", i++));
        rules.add(multiplier("HARD_TO_ACCESS", "Điều chỉnh do khu vực khó tiếp cận", "1.3", i++));
        rules.add(rule(PricingCategory.CONDITION, "URGENT", "Yêu cầu thực hiện khẩn cấp", PricingCalculationType.PERCENT_OF_BASE, null, null, null, "15", null, null, i++));
        rules.add(fixed(PricingCategory.PERMIT, "REQUIRED", "Chi phí giấy phép hoặc vận hành đặc biệt", "1000000", i++));
        // Starter MEDIA profiles for a fresh installation. They are ordinary policy data: an Admin edits them in a new policy version.
        rules.add(media("DEFAULT", MediaPricingComponent.EXTRA_IMAGE, "0", "10", "10", "50000", i++));
        rules.add(media("DEFAULT", MediaPricingComponent.EXTRA_IMAGE, "10", null, "10", "40000", i++));
        rules.add(media("DEFAULT", MediaPricingComponent.EXTRA_VIDEO, "0", null, "1", "100000", i++));
        rules.add(media("CUSTOM", MediaPricingComponent.CHECKLIST_BASE, null, null, null, "200000", i++));
        PricingRule customImages = media("CUSTOM", MediaPricingComponent.INCLUDED_IMAGE, null, null, null, "0", i++);
        customImages.setIncludedQuantity(5); rules.add(customImages);
        rules.add(media("CUSTOM", MediaPricingComponent.EXTRA_IMAGE, "0", null, "10", "50000", i++));
        rules.add(media("CUSTOM", MediaPricingComponent.EXTRA_VIDEO, "0", null, "1", "100000", i));
        p.replaceRules(rules); policies.save(p);
    }

    private PricingRule media(String profile, MediaPricingComponent component, String min, String max, String block, String price, int priority) {
        boolean tier = component == MediaPricingComponent.EXTRA_IMAGE || component == MediaPricingComponent.EXTRA_VIDEO;
        PricingRule r = rule(PricingCategory.MEDIA, profile + ":" + component + (min == null ? "" : ":" + min), profile + " · " + component,
                tier ? PricingCalculationType.PER_BLOCK : PricingCalculationType.FIXED, min, max, price, null, block, null, priority);
        r.setPricingProfile(profile); r.setMediaComponent(component);
        if (tier) r.setRoundingMode(PricingRounding.CEILING);
        return r;
    }
    private PricingRule fixed(PricingCategory c, String key, String description, String amount, int priority) {
        return rule(c, key, description, PricingCalculationType.FIXED, null, null, amount, null, null, null, priority);
    }
    private PricingRule multiplier(String key, String description, String value, int priority) {
        return rule(PricingCategory.TERRAIN, key, description, PricingCalculationType.MULTIPLIER, null, null, null, null, null, value, priority);
    }
    private PricingRule rule(PricingCategory c, String key, String description, PricingCalculationType type,
            String min, String max, String fixed, String percent, String unit, String multiplier, int priority) {
        PricingRule r = new PricingRule(); r.setCategory(c); r.setRuleKey(key); r.setDescription(description);
        r.setCalculationType(type); r.setMinValue(bd(min)); r.setMaxValue(bd(max)); r.setFixedAmount(bd(fixed));
        r.setPercentage(bd(percent)); r.setUnitSize(bd(unit)); r.setMultiplier(bd(multiplier)); r.setPriority(priority); r.setEnabled(true); return r;
    }
    private BigDecimal bd(String value) { return value == null ? null : new BigDecimal(value); }
}
