package com.ondemandmonitoring.order.enums;

/**
 * Required media evidence type for an Order checklist item snapshot.
 * - IMAGE: only eligible IMAGE assets count.
 * - VIDEO: only eligible VIDEO assets count.
 * - IMAGE_OR_VIDEO: any combination of eligible images and videos counts.
 */
public enum RequiredEvidenceType {
    IMAGE,
    VIDEO,
    IMAGE_OR_VIDEO
}
