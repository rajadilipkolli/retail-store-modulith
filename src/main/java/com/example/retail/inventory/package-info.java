/**
 * Inventory business module.
 *
 * <p>Responsible for stock availability, reservations, and releases.
 */
@ApplicationModule(
        displayName = "Inventory",
        allowedDependencies = {"shared"})
package com.example.retail.inventory;

import org.springframework.modulith.ApplicationModule;
