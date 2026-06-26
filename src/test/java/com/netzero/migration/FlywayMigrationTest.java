package com.netzero.migration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@JdbcTest
class FlywayMigrationTest {
    @Autowired JdbcTemplate jdbc;

    @Test
    void allTablesCreated() {
        Integer n = jdbc.queryForObject(
            "select count(*) from information_schema.tables where upper(table_name) in " +
            "('STORE','ITEM_MASTER','ORDER_POLICY','SALES_RECORD','INVENTORY_SNAPSHOT'," +
            "'WEATHER_FORECAST','DEMAND_FORECAST','ORDER_RECOMMENDATION','CARBON_SAVING')", Integer.class);
        assertThat(n).isEqualTo(9);
    }
}
