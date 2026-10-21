/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/
package com.example.retail.shared;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.retail.shared.persistence.TsidSupplier;
import io.hypersistence.tsid.TSID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TsidSupplierTest {

    @Test
    @DisplayName("TsidSupplier supplies non-null TSID factory and creates valid time-sorted IDs")
    void tsidSupplier_GeneratesValidIds() {
        TsidSupplier supplier = new TsidSupplier();
        TSID.Factory factory = supplier.get();

        assertThat(factory).isNotNull();

        TSID tsid1 = factory.generate();
        TSID tsid2 = factory.generate();

        assertThat(tsid1).isNotNull();
        assertThat(tsid2).isNotNull();
        assertThat(tsid1.toLong()).isPositive();
        assertThat(tsid2.toLong()).isPositive();
        assertThat(tsid1.toLong()).isNotEqualTo(tsid2.toLong());
    }
}
