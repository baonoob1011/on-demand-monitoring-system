package com.ondemandmonitoring.pricing.enums;

/** Role of a MEDIA pricing rule inside a pricing profile. Included and extra media are priced separately. */
public enum MediaPricingComponent {
    /** Flat price of one checklist (used by the custom-checklist profile). */
    CHECKLIST_BASE,
    /** Flat price of the included image allowance. */
    INCLUDED_IMAGE,
    /** Flat price of the included video allowance. */
    INCLUDED_VIDEO,
    /** One tier of the extra-image price list. */
    EXTRA_IMAGE,
    /** One tier of the extra-video price list. */
    EXTRA_VIDEO
}
