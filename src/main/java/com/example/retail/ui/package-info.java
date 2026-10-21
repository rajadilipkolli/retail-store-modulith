/**
 * UI presentation module.
 *
 * <p>Responsible for web presentation, user views, and UI controllers.
 */
@ApplicationModule(
    displayName = "UI",
    allowedDependencies = {"catalog", "inventory", "orders", "payments", "shared"}
)
package com.example.retail.ui;

import org.springframework.modulith.ApplicationModule;
