/**
 * Orders orchestration module.
 *
 * <p>Responsible for order lifecycle management and cross-module workflow orchestration. Permitted
 * to depend on shared, catalog, inventory, and payments modules.
 */
@ApplicationModule(
        displayName = "Orders",
        allowedDependencies = {"shared", "catalog", "inventory", "payments"})
package com.example.retail.orders;

import org.springframework.modulith.ApplicationModule;
