-- Delivery/payment state is intentionally separate from operational mission state.
CREATE TABLE IF NOT EXISTS order_deliveries (
    id varchar(255) PRIMARY KEY,
    created_at timestamptz NOT NULL,
    updated_at timestamptz,
    version bigint NOT NULL DEFAULT 0,
    order_id varchar(255) NOT NULL,
    status varchar(40) NOT NULL,
    delivery_notes varchar(2000),
    preview_released_by varchar(255),
    preview_released_at timestamptz,
    customer_accepted_at timestamptz,
    revision_requested_at timestamptz,
    revision_reason varchar(2000),
    revision_count integer NOT NULL DEFAULT 0,
    final_payment_confirmed_at timestamptz,
    originals_released_by varchar(255),
    originals_released_at timestamptz,
    CONSTRAINT uk_order_delivery_order UNIQUE (order_id),
    CONSTRAINT fk_order_delivery_order FOREIGN KEY (order_id) REFERENCES orders(id),
    CONSTRAINT fk_order_delivery_preview_user FOREIGN KEY (preview_released_by) REFERENCES users(id),
    CONSTRAINT fk_order_delivery_release_user FOREIGN KEY (originals_released_by) REFERENCES users(id)
);

ALTER TABLE drone_media ADD COLUMN IF NOT EXISTS preview_selected boolean NOT NULL DEFAULT false;
