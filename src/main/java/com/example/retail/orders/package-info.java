/**
 * Orders orchestration module.
 *
 * <p>Responsible for order lifecycle management and cross-module workflow orchestration.
 * Permitted to depend on shared modules.
 */
@ApplicationModule(
    displayName = "Orders",
    allowedDependencies = {"shared"}
)
package com.example.retail.orders;

import org.springframework.modulith.ApplicationModule;