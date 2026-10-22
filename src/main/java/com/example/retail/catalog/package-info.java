/**
 * Catalog business module.
 *
 * <p>Responsible for product information and catalog operations.
 */
@ApplicationModule(
        displayName = "Catalog",
        allowedDependencies = {"shared", "inventory"})
package com.example.retail.catalog;

import org.springframework.modulith.ApplicationModule;
