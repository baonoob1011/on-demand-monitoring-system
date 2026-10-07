BEGIN;

ALTER TABLE public.services
    ADD COLUMN IF NOT EXISTS base_price numeric(19,0);

UPDATE public.services
SET base_price = CASE name
    WHEN 'Giám sát Kho bãi / Logistics' THEN 2800000
    WHEN 'Giám sát Đập nước / Hồ chứa' THEN 4500000
    WHEN 'Giám sát Rừng / Điểm nhiệt' THEN 4800000
    WHEN 'Giám sát Nông nghiệp / Cây trồng' THEN 2600000
    WHEN 'Kiểm tra Sân bay / Đường băng' THEN 6500000
    WHEN 'Giám sát Kho công nghiệp / Nhà xưởng' THEN 3800000
    WHEN 'Giám sát Mặt nước / Dòng chảy' THEN 3000000
    WHEN 'Đo nhiệt độ / Điểm nhiệt' THEN 4200000
    WHEN 'Đo nhiệt độ / Áp suất' THEN 3600000
    WHEN 'Kiểm tra Công trình thủy lợi' THEN 3500000
    WHEN 'Giám sát Tiến độ Xây dựng' THEN 3200000
    WHEN 'Giám sát Sạt lở / Ngập lụt' THEN 5000000
    WHEN 'Kiểm tra Tháp viễn thông' THEN 4000000
    WHEN 'Giám sát Mục tiêu xa' THEN 4500000
    WHEN 'Giám sát Bãi đáp / Trạm drone' THEN 2400000
    ELSE COALESCE(base_price, 3200000)
END
WHERE base_price IS NULL;

ALTER TABLE public.services ALTER COLUMN base_price SET NOT NULL;
ALTER TABLE public.services DROP CONSTRAINT IF EXISTS ck_services_base_price_positive;
ALTER TABLE public.services ADD CONSTRAINT ck_services_base_price_positive CHECK (base_price > 0);

ALTER TABLE public.orders
    ADD COLUMN IF NOT EXISTS service_base_price_snapshot numeric(19,0);

UPDATE public.orders o
SET service_base_price_snapshot = s.base_price
FROM public.services s
WHERE o.service_id = s.id AND o.service_base_price_snapshot IS NULL;

COMMIT;
