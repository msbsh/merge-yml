package org.cobbzilla.util.yml;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.io.File;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

public class YmlMergerTest {

    private static final Logger LOG = LoggerFactory.getLogger(YmlMergerTest.class);

    public static final String YML_1 = getResourceFile("test1.yml");
    public static final String YML_2 = getResourceFile("test2.yml");
    public static final String YML_NULL = getResourceFile("test-null.yml");
    public static final String YML_COLON = getResourceFile("test-colon.yml");
    public static final String MERGEYML_1 = getResourceFile("testlistmerge1.yml");
    public static final String MERGEYML_2 = getResourceFile("testlistmerge2.yml");
    
    
    private final Yaml yaml = new Yaml();
    private final YmlMerger merger = new YmlMerger("");

    @SuppressWarnings({ "unchecked" })
	@Test
    public void testMerge2Files () throws Exception {
        final Map<String, Object> merged = merger.merge(new String[]{YML_1, YML_2});
        Map<String, Object> dbconfig;
        dbconfig = (Map<String, Object>) merged.get("database");
        assertEquals("wrong user", dbconfig.get("user"), "alternate-user");
        assertEquals("wrong db url", dbconfig.get("url"), "jdbc:mysql://localhost:3306/some-db");

        final String mergedYmlString = merger.toString(merged);
        LOG.info("resulting YML=\n"+ mergedYmlString);
        final Map<String, Object> reloadedYaml = (Map<String, Object>) yaml.load(mergedYmlString);
        dbconfig = (Map<String, Object>) reloadedYaml.get("database");
        assertEquals("wrong user", dbconfig.get("user"), "alternate-user");
        assertEquals("wrong db url", dbconfig.get("url"), "jdbc:mysql://localhost:3306/some-db");
        Map<String, Object> dbProperties = (Map<String, Object>) dbconfig.get("properties");
        assertEquals("wrong db url", dbProperties.get("hibernate.dialect"), "org.hibernate.dialect.MySQL5InnoDBDialect");
    }

    @SuppressWarnings({ "unchecked" })
	@Test
    public void testMergeFileIntoSelf () throws Exception {
        final Map<String, Object> merged = merger.merge(new String[]{YML_1, YML_1});
        final Map<String, Object> dbconfig = (Map<String, Object>) merged.get("database");
        assertEquals("wrong user", dbconfig.get("user"), "some-user");
        assertEquals("wrong db url", dbconfig.get("url"), "jdbc:mysql://localhost:3306/some-db");
    }

    @Test
    public void testNullValue () throws Exception {
        final Map<String, Object> merged = merger.merge(new String[]{YML_NULL});
        assertNotNull(merged.get("prop1"));
        assertNull(merged.get("prop2"));
    }

    @SuppressWarnings({ "unchecked"})
	@Test
    public void testMerge2Lists () throws Exception {
        final Map<String, Object> merged = merger.merge(new String[]{MERGEYML_1, MERGEYML_2});
        Map<String, Object> hash1 = (Map<String, Object>) merged.get("hashlevel1");
        List<Object> list1 = (List<Object>) hash1.get("listlevel2");
        assertEquals("NotEnoughEntries", list1.size(), 2);
        Map<String, Object> optionSet1 = (Map<String, Object>) list1.get(0);
        Map<String, Object> optionSet2 = (Map<String, Object>) list1.get(1);
        assertEquals(optionSet1.get("namespace"), "namespace1");
        assertEquals(optionSet1.get("option_name"), "option1");
        assertEquals(optionSet2.get("namespace"), "namespace2");
        assertEquals(optionSet2.get("option_name"), "option2");
    }

    public static String getResourceFile(String file) {
        return new File(System.getProperty("user.dir")+"/src/test/resources/"+ file).getAbsolutePath();
    }

}
