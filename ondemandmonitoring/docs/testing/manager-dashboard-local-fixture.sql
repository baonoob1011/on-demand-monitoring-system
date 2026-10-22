-- Local-only Manager Dashboard fixture. Run after Hibernate has created the schema.
-- Safe to rerun: only rows whose IDs start with "dash-test-" are replaced.
BEGIN;

DELETE FROM mission_device_assignments WHERE id LIKE 'dash-test-%';
DELETE FROM mission_staff_assignments WHERE id LIKE 'dash-test-%';
DELETE FROM payments WHERE id LIKE 'dash-test-%';
DELETE FROM invoices WHERE id LIKE 'dash-test-%';
DELETE FROM missions WHERE id LIKE 'dash-test-%';
DELETE FROM quotes WHERE id LIKE 'dash-test-%';
DELETE FROM order_checklist_items WHERE id LIKE 'dash-test-%';
DELETE FROM orders WHERE id LIKE 'dash-test-%';

DO $fixture$
DECLARE
    customer_ids text[];
    service_ids text[];
    operator_ids text[];
    device_ids text[];
    manager_id text;
    preferred_time_id text;
    checklist_id text;
    order_id text;
    quote_id text;
    invoice_id text;
    mission_id text;
    created timestamptz;
    order_status text;
    pricing_status text;
    quote_status text;
    mission_status text;
    system_price numeric;
    approved_price numeric;
    quote_total numeric;
    i integer;
BEGIN
    SELECT array_agg(id ORDER BY id) INTO customer_ids
    FROM users WHERE email LIKE 'seed.customer%@odms.local';
    SELECT array_agg(id ORDER BY name) INTO service_ids FROM services WHERE is_active = true;
    SELECT array_agg(id ORDER BY id) INTO operator_ids
    FROM users WHERE email LIKE 'seed.drone.operator%@odms.local';
    SELECT array_agg(id ORDER BY device_code) INTO device_ids FROM devices;
    SELECT id INTO manager_id FROM users WHERE email = 'seed.manager@odms.local' LIMIT 1;
    SELECT id INTO preferred_time_id FROM preferred_times ORDER BY start_time LIMIT 1;
    SELECT id INTO checklist_id FROM checklist_definitions ORDER BY created_at LIMIT 1;

    IF coalesce(array_length(customer_ids, 1), 0) = 0
       OR coalesce(array_length(service_ids, 1), 0) = 0
       OR coalesce(array_length(operator_ids, 1), 0) = 0
       OR coalesce(array_length(device_ids, 1), 0) = 0
       OR manager_id IS NULL OR preferred_time_id IS NULL OR checklist_id IS NULL THEN
        RAISE EXCEPTION 'Base users/services/devices/checklists are missing. Start the application once before running this fixture.';
    END IF;

    FOR i IN 1..36 LOOP
        order_id := 'dash-test-order-' || lpad(i::text, 3, '0');
        created := now() - ((i * 2 - 2) || ' days')::interval
                       - ((i % 8) || ' hours')::interval;
        order_status := (ARRAY['PENDING','PENDING','NEEDS_INFO','APPROVED','IN_PROGRESS','COMPLETED','CANCELLED'])[((i - 1) % 7) + 1];

        INSERT INTO orders (
            id, version, created_at, updated_at, order_code, user_id, title,
            service_id, preferred_time_id, order_status, execution_plan_status,
            execution_plan_revision, data_retention_days,
            monitoring_authorization_confirmed, preferred_date_from,
            preferred_date_to, address, description, service_base_price_snapshot,
            review_by, review_at, legal_review_status
        ) VALUES (
            order_id, 0, created, created + interval '2 hours',
            'DASH-TEST-' || lpad(i::text, 3, '0'),
            customer_ids[((i - 1) % array_length(customer_ids, 1)) + 1],
            'Yêu cầu kiểm thử Dashboard #' || i,
            service_ids[((i - 1) % array_length(service_ids, 1)) + 1],
            preferred_time_id, order_status,
            CASE WHEN order_status IN ('APPROVED','IN_PROGRESS','COMPLETED') THEN 'FINALIZED' ELSE 'DRAFT' END,
            CASE WHEN order_status IN ('APPROVED','IN_PROGRESS','COMPLETED') THEN 1 ELSE 0 END,
            30, true, created::date + 2, created::date + 4,
            'Khu vực kiểm thử số ' || i || ', Thành phố Hồ Chí Minh',
            'Dữ liệu local để kiểm thử đầy đủ Manager Dashboard.',
            2500000 + (i % 4) * 500000,
            CASE WHEN order_status <> 'PENDING' THEN manager_id ELSE NULL END,
            CASE WHEN order_status <> 'PENDING' THEN created + interval '1 hour' ELSE NULL END,
            'APPROVED'
        );

        -- Template checklist is deliberately present so the dashboard can prove it is excluded
        -- from customer-added analytics.
        INSERT INTO order_checklist_items (
            id, version, created_at, updated_at, order_id, source_checklist_id,
            content, display_order, source_type, review_status, pricing_status,
            difficulty_snapshot, evidence_policy_version, minimum_evidence_count,
            required_evidence_type, manager_quote_required, system_calculated_price,
            manager_quoted_price, manager_approved_amount, final_amount
        ) VALUES (
            'dash-test-template-' || lpad(i::text, 3, '0'), 0, created, created,
            order_id, checklist_id, 'Checklist mặc định #' || i, 0,
            'SERVICE_TEMPLATE', 'INCLUDED', 'AUTO_PRICED', 'BASIC', 1, 1,
            'IMAGE_OR_VIDEO', false, 250000, 250000, 250000, 250000
        );

        IF i % 4 <> 0 OR i IN (32, 35) THEN
            pricing_status := (ARRAY[
                'NEEDS_MANAGER_REVIEW','APPROVED','ADJUSTED_AND_APPROVED',
                'DUPLICATE','REJECTED','BLOCKED_BY_POLICY'
            ])[((i - 1) % 6) + 1];
            system_price := 300000 + (i % 5) * 125000;
            approved_price := CASE
                WHEN pricing_status = 'APPROVED' THEN system_price
                WHEN pricing_status = 'ADJUSTED_AND_APPROVED' THEN system_price + 175000
                ELSE NULL END;

            INSERT INTO order_checklist_items (
                id, version, created_at, updated_at, order_id, source_checklist_id,
                content, display_order, source_type, review_status, pricing_status,
                difficulty_snapshot, evidence_policy_version, minimum_evidence_count,
                required_evidence_type, manager_quote_required, system_calculated_price,
                manager_quoted_price, manager_approved_amount, final_amount,
                price_difference_amount, price_difference_percent,
                manager_price_adjustment_reason
            ) VALUES (
                'dash-test-extra-' || lpad(i::text, 3, '0'), 0,
                created + interval '20 minutes', created + interval '90 minutes',
                order_id, NULL, 'Dịch vụ bổ sung do khách hàng yêu cầu #' || i, 1,
                'CUSTOMER_CUSTOM',
                CASE pricing_status WHEN 'DUPLICATE' THEN 'DUPLICATE' WHEN 'REJECTED' THEN 'REJECTED' ELSE 'ADDITIONAL' END,
                pricing_status, 'MEDIUM', 1, 2, 'IMAGE_AND_VIDEO', true,
                system_price, approved_price, approved_price, approved_price,
                CASE WHEN pricing_status = 'ADJUSTED_AND_APPROVED' THEN 175000 ELSE 0 END,
                CASE WHEN pricing_status = 'ADJUSTED_AND_APPROVED' THEN round(17500000 / system_price, 6) ELSE 0 END,
                CASE WHEN pricing_status = 'ADJUSTED_AND_APPROVED' THEN 'Điều chỉnh theo phạm vi công việc kiểm thử' ELSE NULL END
            );
        END IF;

        IF i <= 26 THEN
            quote_status := (ARRAY['APPROVED','ACCEPTED_BY_CUSTOMER','DRAFT','PENDING_REVIEW'])[((i - 1) % 4) + 1];
            quote_total := 3200000 + (i % 6) * 850000;

            IF i % 5 = 0 THEN
                INSERT INTO quotes (
                    id, version, created_at, updated_at, order_id, quote_version,
                    status, package_price, additional_amount, discount_amount,
                    adjustment_amount, total_amount, system_calculated_amount,
                    manager_adjustment_amount, pricing_policy_version,
                    pricing_approval_status, pricing_revision, pricing_risk_flags
                ) VALUES (
                    'dash-test-quote-old-' || lpad(i::text, 3, '0'), 0,
                    created + interval '2 hours', created + interval '3 hours', order_id, 1,
                    'SUPERSEDED', quote_total - 500000, 0, 0, 0,
                    quote_total - 500000, quote_total - 500000, 0, 1,
                    'NOT_REQUIRED', 0, '[]'::jsonb
                );
            END IF;

            quote_id := 'dash-test-quote-' || lpad(i::text, 3, '0');
            INSERT INTO quotes (
                id, version, created_at, updated_at, order_id, quote_version,
                status, package_price, additional_amount, discount_amount,
                adjustment_amount, total_amount, system_calculated_amount,
                manager_adjustment_amount, pricing_policy_version,
                pricing_approval_status, pricing_revision, pricing_risk_flags,
                approved_by, approved_at, accepted_at
            ) VALUES (
                quote_id, 0, created + interval '4 hours', created + interval '6 hours', order_id,
                CASE WHEN i % 5 = 0 THEN 2 ELSE 1 END, quote_status,
                quote_total - 500000, 500000, 0, 0, quote_total,
                quote_total, 0, 1, 'NOT_REQUIRED', 0, '[]'::jsonb,
                CASE WHEN quote_status IN ('APPROVED','ACCEPTED_BY_CUSTOMER') THEN manager_id ELSE NULL END,
                CASE WHEN quote_status IN ('APPROVED','ACCEPTED_BY_CUSTOMER') THEN created + interval '5 hours' ELSE NULL END,
                CASE WHEN quote_status = 'ACCEPTED_BY_CUSTOMER' THEN created + interval '6 hours' ELSE NULL END
            );

            IF quote_status = 'ACCEPTED_BY_CUSTOMER' THEN
                invoice_id := 'dash-test-invoice-' || lpad(i::text, 3, '0');
                INSERT INTO invoices (
                    id, version, created_at, updated_at, invoice_number, order_id,
                    quote_id, status, total_amount, deposit_amount, paid_amount,
                    remaining_amount, issued_at
                ) VALUES (
                    invoice_id, 0, created + interval '7 hours', created + interval '8 hours',
                    'INV-DASH-' || lpad(i::text, 3, '0'), order_id, quote_id,
                    CASE WHEN i % 8 = 2 THEN 'PAID' WHEN i % 8 = 6 THEN 'PARTIALLY_PAID' ELSE 'ISSUED' END,
                    quote_total, quote_total * 0.3,
                    CASE WHEN i % 8 = 2 THEN quote_total WHEN i % 8 = 6 THEN quote_total * 0.3 ELSE 0 END,
                    CASE WHEN i % 8 = 2 THEN 0 WHEN i % 8 = 6 THEN quote_total * 0.7 ELSE quote_total END,
                    created + interval '7 hours'
                );

                IF i % 8 = 2 THEN
                    INSERT INTO payments (
                        id, version, created_at, updated_at, invoice_id, payment_code,
                        payment_type, provider, status, amount, currency,
                        transaction_reference, paid_at
                    ) VALUES (
                        'dash-test-payment-ok-' || lpad(i::text, 3, '0'), 0,
                        created + interval '8 hours', created + interval '9 hours', invoice_id,
                        92026000000 + i, 'DEPOSIT', 'VNPAY', 'SUCCESS', quote_total * 0.3,
                        'VND', 'DASH-OK-' || lpad(i::text, 3, '0'), created + interval '9 hours'
                    );
                ELSIF i % 8 = 6 THEN
                    INSERT INTO payments (
                        id, version, created_at, updated_at, invoice_id, payment_code,
                        payment_type, provider, status, amount, currency,
                        transaction_reference, failure_reason
                    ) VALUES (
                        'dash-test-payment-fail-' || lpad(i::text, 3, '0'), 0,
                        created + interval '8 hours', created + interval '9 hours', invoice_id,
                        92027000000 + i, 'DEPOSIT', 'VNPAY', 'FAILED', quote_total * 0.3,
                        'VND', 'DASH-FAIL-' || lpad(i::text, 3, '0'), 'Giao dịch kiểm thử bị từ chối'
                    );
                END IF;
            END IF;
        END IF;

        IF i <= 22 THEN
            mission_status := (ARRAY[
                'SCHEDULED','READY_TO_FLY','IN_FLIGHT','RETURNING','POSTFLIGHT_CHECKING',
                'COMPLETED','FAILED','RESOURCE_ASSIGNING','PENDING_REVIEW','CANCELLED'
            ])[((i - 1) % 10) + 1];
            mission_id := 'dash-test-mission-' || lpad(i::text, 3, '0');
            INSERT INTO missions (
                id, version, created_at, updated_at, mission_code, order_id,
                status, flight_permit_status, scheduled_start_at, scheduled_end_at,
                actual_start_at, actual_end_at, completed_at, scope_label
            ) VALUES (
                mission_id, 0, created + interval '1 day', created + interval '1 day 4 hours',
                'MIS-DASH-' || lpad(i::text, 3, '0'), order_id, mission_status,
                'APPROVED', created + interval '2 days', created + interval '2 days 2 hours',
                CASE WHEN mission_status IN ('IN_FLIGHT','RETURNING','POSTFLIGHT_CHECKING','COMPLETED','FAILED','PENDING_REVIEW') THEN created + interval '2 days' ELSE NULL END,
                CASE WHEN mission_status IN ('COMPLETED','FAILED') THEN created + interval '2 days 2 hours' ELSE NULL END,
                CASE WHEN mission_status = 'COMPLETED' THEN created + interval '2 days 2 hours' ELSE NULL END,
                'Phạm vi kiểm thử Dashboard #' || i
            );

            -- Even-numbered missions have complete resources; odd-numbered planning/active
            -- missions intentionally exercise the missing-resource attention path.
            IF i % 2 = 0 THEN
                INSERT INTO mission_staff_assignments (
                    id, version, created_at, updated_at, mission_id, staff_id,
                    assigned_role, response_status, is_current, assigned_at, responded_at
                ) VALUES (
                    'dash-test-staff-' || lpad(i::text, 3, '0'), 0, created + interval '1 day',
                    created + interval '1 day 1 hour', mission_id,
                    operator_ids[((i - 1) % array_length(operator_ids, 1)) + 1],
                    'OPERATOR', 'ACCEPTED', true, created + interval '1 day', created + interval '1 day 1 hour'
                );
                INSERT INTO mission_device_assignments (
                    id, version, created_at, updated_at, mission_id, device_id,
                    device_role, checkup_status, is_current, assigned_at
                ) VALUES (
                    'dash-test-device-' || lpad(i::text, 3, '0'), 0, created + interval '1 day',
                    created + interval '1 day 1 hour', mission_id,
                    device_ids[((i - 1) % array_length(device_ids, 1)) + 1],
                    'MAIN', 'PASSED', true, created + interval '1 day'
                );
            END IF;
        END IF;
    END LOOP;
END
$fixture$;

COMMIT;

SELECT 'orders' AS metric, count(*)::text AS value FROM orders WHERE id LIKE 'dash-test-%'
UNION ALL SELECT 'customer_added_checklists', count(*)::text FROM order_checklist_items WHERE id LIKE 'dash-test-extra-%'
UNION ALL SELECT 'quotes_all_versions', count(*)::text FROM quotes WHERE id LIKE 'dash-test-%'
UNION ALL SELECT 'invoices', count(*)::text FROM invoices WHERE id LIKE 'dash-test-%'
UNION ALL SELECT 'payments', count(*)::text FROM payments WHERE id LIKE 'dash-test-%'
UNION ALL SELECT 'missions', count(*)::text FROM missions WHERE id LIKE 'dash-test-%'
ORDER BY metric;
