package com.v2board.api.util;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class V2boardPhpConfigLoaderTest {

    @Test
    void parseVarExportScalars_readsAppName() {
        String php = """
                <?php
                 return array (
                  'app_name' => 'MyProxySite',
                  'force_https' => 1,
                ) ;
                """;
        Map<String, Object> map = V2boardPhpConfigLoader.parseVarExportScalars(php);
        assertEquals("MyProxySite", map.get("app_name"));
        assertEquals(1L, map.get("force_https"));
    }
}
