/**
 * Identity persistence mappings. Flyway owns DDL; Hibernate validates these mappings.
 * UUID references deliberately avoid cross-feature entity graphs. Services/repositories
 * and credential operations will be introduced in subsequent identity tasks.
 */
package com.swiftdrop.entity.identity;
